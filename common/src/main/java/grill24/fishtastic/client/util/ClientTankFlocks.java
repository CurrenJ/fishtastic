package grill24.fishtastic.client.util;

import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.client.renderer.TankBubbleEmitter;
import grill24.fishtastic.client.renderer.TankFlockAdapter;
import grill24.fishtastic.client.renderer.TankGroupFlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side registry of per-tank flock simulations, keyed by {@code BlockPos}.
 *
 * <p>Entries are created lazily on first extract (i.e. the first frame a tank is actually
 * rendered), stepped once per client tick from {@link #tickAll()}, and evicted after a tank has
 * gone un-rendered for {@link #EVICT_AFTER_TICKS} — so tanks the player walked away from stop
 * simulating, and re-enter by re-seeding from the deterministic {@code blockPosHash} scatter.
 *
 * <p>Lives in {@code client/util} beside {@link ClientTickHandler} so both loader client
 * initializers already import from a single client package; the simulation itself stays in
 * {@code client/renderer} where the renderer can reach it without crossing packages.
 */
public final class ClientTankFlocks {

    /** How long a tank may go without being extracted before its flock is discarded. */
    private static final long EVICT_AFTER_TICKS = 20L * 30L; // 30 s

    private static final Map<BlockPos, TankFlockAdapter> FLOCKS = new HashMap<>();
    private static long tickCounter = 0;

    private ClientTankFlocks() {}

    /**
     * The client tick this frame belongs to. Shared with the per-tank and per-group simulations so
     * they can all key their "once per tick" and "once per frame" work off one monotonic clock.
     */
    public static long tickCounter() {
        return tickCounter;
    }

    /**
     * Client tick at which the tank at {@code tankPos} was last extracted — i.e. last handed to its
     * renderer — or {@link Long#MIN_VALUE} if it has no flock at all.
     *
     * <p>Whether a given tank's block entity is being dispatched is otherwise invisible from
     * outside: it is decided by whether its chunk section survived vanilla's frustum and occlusion
     * tests, not by anything this mod can see. The render self-test uses this to prove its
     * multi-tank scene really is reproducing the configuration it claims to (§9.8 of
     * docs/fish-tank-group-scaling.md), rather than assuming it from camera geometry.
     */
    public static long lastExtractTickOf(BlockPos tankPos) {
        TankFlockAdapter flock = FLOCKS.get(tankPos);
        return flock == null ? Long.MIN_VALUE : flock.lastExtractTick();
    }

    /**
     * Returns the flock for this tank, creating and syncing it on first call. Called from
     * {@code FishTankBlockEntityRenderer.extractRenderState} every frame.
     */
    public static TankFlockAdapter getOrCreate(FishTankBlockEntity blockEntity, int blockPosHash) {
        BlockPos key = blockEntity.getBlockPos();
        TankFlockAdapter flock = FLOCKS.get(key);
        if (flock == null) {
            flock = new TankFlockAdapter();
            FLOCKS.put(key, flock);
        }
        flock.touch(tickCounter);
        flock.sync(blockEntity, blockPosHash, blockEntity.getLevel());
        return flock;
    }

    /**
     * Advances every warm flock by one fixed 20 Hz step, then evicts tanks that have gone cold.
     * Call once per client tick (both loaders).
     */
    public static void tickAll() {
        // Client ticks keep running while the world is frozen (/tick freeze), which the level does
        // not; the shoal has to freeze with it, and advance on /tick step like everything else.
        // Frame-exact capture (cool-cam) depends on this: both passes of a frame must see one world.
        if (Minecraft.getInstance().level instanceof ClientLevel frozenCheck && !frozenCheck.tickRateManager().runsNormally()) return;
        tickCounter++;
        FLOCKS.entrySet().removeIf(e -> tickCounter - e.getValue().lastExtractTick() > EVICT_AFTER_TICKS);

        // The one thing outside a tank that its inhabitants know about: whoever is standing in
        // front of it. Read once per tick rather than per tank — it is the same player — and
        // handed to each flock in that tank's own coordinates before it steps.
        Player player = Minecraft.getInstance().player;
        Vec3 eye = player == null ? null : player.getEyePosition();
        ClientLevel clientLevel = Minecraft.getInstance().level instanceof ClientLevel level ? level : null;
        for (Map.Entry<BlockPos, TankFlockAdapter> entry : FLOCKS.entrySet()) {
            TankFlockAdapter flock = entry.getValue();
            if (eye == null) {
                flock.setWatcher(entry.getKey(), Double.NaN, 0, 0);
            } else {
                flock.setWatcher(entry.getKey(), eye.x, eye.y, eye.z);
            }
            flock.step();

            // The group this tank belongs to is one aquarium: it is stepped, pointed at the player
            // and bubbled from whichever member is warm, not from the group's anchor. On a large
            // build the anchor is regularly the part of the tank that is not on screen, and it used
            // to be the only thing keeping the whole shoal simulating. Step and bubble are stamped
            // on the tick, so every other warm member of the same group is a no-op here.
            TankGroupFlock group = flock.groupFlock();
            if (group != null) {
                group.touch(tickCounter);
                group.stepIfDue(tickCounter);
                if (eye == null) {
                    group.clearWatcher();
                } else {
                    group.setWatcher(eye.x, eye.y, eye.z);
                }
                if (clientLevel != null && flock.lastExtractTick() == tickCounter - 1
                        && group.claimBubbleTick(tickCounter)) {
                    TankBubbleEmitter.emitGroup(clientLevel, group, entry.getKey(), eye);
                }
            }

            // Bubbles are an observer of the step just taken, and only for tanks actually on
            // screen — a warm-but-unseen flock keeps simulating without spending particles.
            if (flock.lastExtractTick() == tickCounter - 1 && clientLevel != null) {
                TankBubbleEmitter.emit(clientLevel, entry.getKey(), flock, eye);
            }
        }
    }

    /**
     * True if {@code tankPos} has a fish at {@code fishIndex} — counting both its own local
     * ("hover") fish and, if it's part of a multi-tank group, the group's shared swimmers.
     */
    public static boolean hasFish(Level level, BlockPos tankPos, int fishIndex) {
        return fishIndex >= 0 && fishIndex < fishCount(level, tankPos);
    }

    /**
     * Total followable fish "belonging" to this tank: its own local fish, plus — if it's part of
     * a multi-tank group (see docs/fish-tank-group-scaling.md) — the group's shared swimmers, which
     * the group simulates as one shoal and which are followable from every member. 0 if the tank
     * isn't warm/rendered or has no block entity.
     */
    public static int fishCount(Level level, BlockPos tankPos) {
        if (!(level.getBlockEntity(tankPos) instanceof FishTankBlockEntity be)) return 0;
        TankFlockAdapter flock = FLOCKS.get(tankPos);
        int local = flock == null ? 0 : flock.count();

        TankGroupFlock group = warmGroupFlock(be, level);
        return local + (group == null ? 0 : group.count());
    }

    /**
     * The group runtime behind this tank, or null if the group isn't warm — i.e. no member of it has
     * been rendered for {@link #EVICT_AFTER_TICKS}. The group's fish are then reported for as long
     * as the group is simulated at all, so a camera following one no longer loses it merely because
     * the member it is standing in happens to be off screen.
     */
    private static TankGroupFlock warmGroupFlock(FishTankBlockEntity be, Level level) {
        TankGroupFlock group = ClientTankGroups.get(be, level).flock();
        if (group == null || tickCounter - group.lastWarmTick() > EVICT_AFTER_TICKS) return null;
        return group;
    }

    /**
     * World-space position of one followable fish (see {@link #fishCount}), as of the flock's
     * last render-frame interpolation — not re-derived for an arbitrary partial tick, so callers
     * driving their own render loop (e.g. a third-party camera mod) get a position that is at
     * most one frame stale. Includes the cyclic bob the renderer adds on top of the flock's swim
     * position (see {@link TankFlockAdapter#localFishWorldPosition}), so a camera tracking this
     * position moves with the fish's sprite rather than with the smoothed point underneath it.
     *
     * @param gameTimeTicks {@code level.getGameTime() + partialTick} — the same clock the renderer
     *                      poses non-swimming fish on, needed to reproduce their bob exactly
     */
    public static @Nullable Vec3 worldPositionOf(Level level, BlockPos tankPos, int fishIndex, float gameTimeTicks) {
        if (fishIndex < 0 || !(level.getBlockEntity(tankPos) instanceof FishTankBlockEntity be)) return null;
        TankFlockAdapter flock = FLOCKS.get(tankPos);
        int local = flock == null ? 0 : flock.count();
        if (fishIndex < local) return flock.localFishWorldPosition(tankPos, fishIndex, gameTimeTicks);

        TankGroupFlock group = warmGroupFlock(be, level);
        if (group == null) return null;
        return group.fishWorldPosition(fishIndex - local, gameTimeTicks);
    }

    /**
     * Opaque per-species id of one followable fish (see {@link #fishCount}) — the same id
     * {@code FlockEngine} uses for species-aware separation/schooling (stable item-registry index
     * for the client session). {@code -1} if there's no such fish.
     */
    public static int speciesIdOf(Level level, BlockPos tankPos, int fishIndex) {
        if (fishIndex < 0 || !(level.getBlockEntity(tankPos) instanceof FishTankBlockEntity be)) return -1;
        TankFlockAdapter flock = FLOCKS.get(tankPos);
        int local = flock == null ? 0 : flock.count();
        if (fishIndex < local) return flock.fishSpecies(fishIndex);

        TankGroupFlock group = warmGroupFlock(be, level);
        return group == null ? -1 : group.fishSpecies(fishIndex - local);
    }

    /** Drops all flocks — call on world join/disconnect so block positions never leak across worlds. */
    public static void clear() {
        FLOCKS.clear();
        ClientTankGroups.clear();
    }
}
