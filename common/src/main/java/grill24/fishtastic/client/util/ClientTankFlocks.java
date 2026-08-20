package grill24.fishtastic.client.util;

import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.client.renderer.TankFlockSimulation;
import net.minecraft.core.BlockPos;

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

    private static final Map<BlockPos, TankFlockSimulation> FLOCKS = new HashMap<>();
    private static long tickCounter = 0;

    private ClientTankFlocks() {}

    /**
     * Returns the flock for this tank, creating and syncing it on first call. Called from
     * {@code FishTankBlockEntityRenderer.extractRenderState} every frame.
     */
    public static TankFlockSimulation getOrCreate(FishTankBlockEntity blockEntity, int blockPosHash) {
        BlockPos key = blockEntity.getBlockPos();
        TankFlockSimulation flock = FLOCKS.get(key);
        if (flock == null) {
            flock = new TankFlockSimulation();
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
        tickCounter++;
        FLOCKS.entrySet().removeIf(e -> tickCounter - e.getValue().lastExtractTick() > EVICT_AFTER_TICKS);
        for (TankFlockSimulation flock : FLOCKS.values()) {
            flock.step();
        }
    }

    /** Drops all flocks — call on world join/disconnect so block positions never leak across worlds. */
    public static void clear() {
        FLOCKS.clear();
    }
}
