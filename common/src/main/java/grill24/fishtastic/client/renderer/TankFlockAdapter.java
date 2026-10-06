package grill24.fishtastic.client.renderer;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.ShelterUse;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.VoxelDomain;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.client.util.ClientTankFlocks;
import grill24.fishtastic.client.util.ClientTankGroups;
import grill24.fishtastic.client.util.TankFloors;
import grill24.fishtastic.client.util.TankObstacles;
import grill24.fishtastic.client.util.TankShelters;
import grill24.fishtastic.fishtank.TankGroups;
import grill24.fishtastic.data.FishAnimationConfig;
import grill24.fishtastic.data.SwarmConfig;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The only class that knows both worlds (docs/fish-sim-engine-plan.md §1): it maps tank contents
 * down to Minecraft-free {@link FishSpec}s for the {@link FlockEngine}, and holds everything the
 * engine deliberately lost in the extraction — the {@link ItemStack}s themselves and their
 * animation configs. (1.21.1 draws each fish immediately with {@code ItemRenderer.renderStatic},
 * so there is no per-fish render state to keep, unlike 26.1's deferred {@code submit}.)
 *
 * <p>Replaces the pre-extraction {@code TankFlockSimulation}'s MC-facing half. A per-tank adapter
 * is held in {@code ClientTankFlocks} (registry keyed by {@code BlockPos}, 20 Hz tick, 30 s
 * eviction) exactly as the sim used to be; the renderer reads interpolated state through
 * {@link #engine()}. The extract path never advances simulation time — only
 * {@code ClientTankFlocks.tickAll()} steps.
 *
 * <p><b>What this adapter is not.</b> Everything a tank shares with the tanks it is connected to
 * belongs to the group, not to a member: the group's engine, its fish descriptors, and the decision
 * about which member draws which fish all live in {@link TankGroupFlock}, reached through
 * {@link #groupFlock()}. This adapter keeps only what is genuinely per-tank: the fish it holds
 * itself, and the split that decides which of them stay here (see {@link TankGroupFlock.GroupSplit},
 * which both halves apply so they cannot disagree).
 */
public final class TankFlockAdapter {

    private final FlockEngine engine = new FlockEngine(Tunables.DEFAULT);

    // ── MC-side descriptor arrays for this tank's own fish — fixed between syncs ─
    ItemStack[] stacks = new ItemStack[0];
    FishAnimationConfig[] anims = new FishAnimationConfig[0];
    /**
     * Container slot each entry of {@link #stacks} came from — the fish's identity across rebuilds.
     * Slots are stable under the tank's own add (first empty slot) and take (last occupied slot)
     * paths, so keying on them lets {@code FlockEngine.rebuildPreserving} recognise the fish that
     * didn't move and leave their positions alone.
     */
    private int[] slots = new int[0];

    // ── Group state (multi-tank) ────────────────────────────────────────────
    private boolean groupMode;
    /** This tank's group runtime, or null while it stands alone. Refreshed every sync. */
    private @Nullable TankGroupFlock groupFlock;
    /** Snapshot of this tank's full contents, for change detection while stacks[] is hover-filtered. */
    private ItemStack[] ownSnapshot = new ItemStack[0];

    private int count;
    private long lastExtractTick = Long.MIN_VALUE;
    /** Bubble-emitter edge memory for this tank's own engine (docs/fish-tank-bubbles.md). */
    final TankBubbleEmitter.State loneEmitter = new TankBubbleEmitter.State();

    public FlockEngine engine() {
        return engine;
    }

    public int count() {
        return count;
    }

    /**
     * This tank's group runtime, or null when it stands alone. Fish the group simulates are drawn by
     * whichever member they are inside — including this one — so the renderer asks this for the
     * bucket to draw rather than being handed every fish in the tank.
     */
    public @Nullable TankGroupFlock groupFlock() {
        return groupFlock;
    }

    public long lastExtractTick() {
        return lastExtractTick;
    }

    /**
     * World-space position of fish #{@code i} in this tank's own engine (the local/"hover" fish —
     * see {@link #count()}), mirroring the offset {@code FishTankBlockEntityRenderer} draws it at —
     * including the cyclic vertical bob {@link FishAnimator#apply} adds at render time, which
     * {@link #engine}'s own {@code renderY} never carries (that's the flock's swim/settle height;
     * the bob is a per-frame pose detail layered on top of it purely for drawing). Omitting it here
     * is what used to make a camera tracking this position visibly lag the sprite's actual bob.
     *
     * @param gameTimeTicks {@code level.getGameTime() + partialTick}, matching the {@code t} the
     *                      renderer's non-swimmer poses animate on
     */
    public Vec3 localFishWorldPosition(BlockPos tankPos, int i, float gameTimeTicks) {
        float yBob = animatedYBob(engine, anims[i], i, gameTimeTicks);
        return new Vec3(
            tankPos.getX() + 0.5 + engine.renderX[i],
            tankPos.getY() + FishTankBlockEntityRenderer.ITEM_BASELINE_Y + engine.renderY[i] + yBob,
            tankPos.getZ() + 0.5 + engine.renderZ[i]
        );
    }

    /**
     * Species id of fish #{@code i} in this tank's own engine — see {@link #speciesId(ItemStack)},
     * the opaque per-item-type id the engine carries per fish.
     */
    public int fishSpecies(int i) {
        return engine.species[i];
    }

    /**
     * The bob {@link FishAnimator#apply} adds on top of {@code eng.renderY[i]} purely for drawing —
     * see {@link #localFishWorldPosition}. Uses its own {@link Random} seeded exactly as the
     * renderer seeds {@code fishRandom} before posing this fish, so the draws that produce the bob
     * come out identical; nothing about that instance is shared with the renderer's.
     */
    private static float animatedYBob(FlockEngine eng, FishAnimationConfig anim,
                                       int i, float gameTimeTicks) {
        Random random = new Random(eng.seeds[i]);
        return eng.swimmers[i]
                ? FishAnimator.yBob(anim, random, eng.renderPhase[i], eng.speedFactor(i), eng.renderBobRoom[i], eng.lengths[i])
                : FishAnimator.yBob(anim, random, gameTimeTicks, 1f);
    }

    /** Marks this flock as having been extracted this client tick (drives eviction). */
    public void touch(long tick) {
        this.lastExtractTick = tick;
    }

    /**
     * Tells this tank's own engine where the player is, so anchored creatures can react to being
     * looked at (docs/fish-sim-locomotion.md §3.4). Called once per client tick, before
     * {@link #step()}. The group engine is pointed at the player by {@link TankGroupFlock}, which
     * translates the same way in the group's own frame.
     *
     * <p>The lone tank's engine is <b>rotated</b> by the yaw the tank recorded from the player who
     * placed it and its origin is the block's centre at the item baseline; the inverse of what the
     * draw loop does to get from the engine's numbers to a position on screen — if a fish is drawn
     * at {@code origin + render}, then the player is at {@code player − origin} in render space, and
     * {@code toLocal} takes it the rest of the way.
     *
     * @param pos this tank's block position
     * @param px,py,pz the player's eye position in world space, or NaN in {@code px} for "no
     *                 player", which clears the watcher
     */
    public void setWatcher(BlockPos pos, double px, double py, double pz) {
        if (Double.isNaN(px)) {
            engine.setWatcher(false, 0f, 0f, 0f);
            return;
        }
        // Lone tank: the draw loop translates by ITEM_POSITION_OFFSET (the block's centre at the
        // item baseline) plus the engine's rotated render offset.
        float x = (float) (px - pos.getX()) - 0.5f;
        float y = (float) (py - pos.getY()) - FishTankBlockEntityRenderer.ITEM_BASELINE_Y;
        float z = (float) (pz - pos.getZ()) - 0.5f;
        float[] scratch = watcherScratch;
        engine.toLocal(x, y, z, scratch);
        engine.setWatcher(true, scratch[0], scratch[1], scratch[2]);
    }

    /** Scratch for {@link #setWatcher}, so the per-tick mapping allocates nothing. */
    private final float[] watcherScratch = new float[3];

    /** Advances this tank's own engine by one fixed 20 Hz step. Runs on the client tick, never at render. */
    public void step() {
        engine.step();
    }

    /**
     * Writes interpolated world-space offsets for this frame's {@code partialTick} into the engine's
     * render scratch, and does the same for the group runtime this tank belongs to. Called from
     * {@code extractRenderState} every frame — read-only with respect to simulation time.
     */
    void interpolate(float partialTick) {
        engine.interpolate(partialTick);
        if (groupFlock != null) {
            groupFlock.interpolateIfDue(ClientTankFlocks.tickCounter(), partialTick);
        }
    }

    /**
     * Rebuilds the fish descriptors if the tank's contents (or its group) changed since the last
     * sync, and leaves the sim state untouched otherwise. Called every extract (per frame) — the
     * checks are allocation-light scans; the rebuilds only run on a real change.
     */
    public void sync(FishTankBlockEntity be, int blockPosHash, Level level) {
        ClientTankGroups.Entry entry = ClientTankGroups.get(be, level);
        TankGroups.Group group = entry.group();
        boolean membershipChanged = !group.members().equals(cachedMembers);
        cachedMembers = group.members();

        if (!group.isMultiTank()) {
            boolean leftGroupMode = groupMode;
            groupMode = false;
            groupFlock = null;
            // Legacy single-tank path — behavior identical to the pre-extraction sim.
            int idx = 0;
            boolean changed = leftGroupMode || membershipChanged;
            for (int slot = 0; slot < FishTankBlockEntity.CONTAINER_SIZE && !changed; slot++) {
                ItemStack s = be.getItem(slot);
                if (!s.isEmpty()) {
                    changed = idx >= count || !FishtasticItemData.isSameItemSameData(s, stacks[idx]);
                    idx++;
                }
            }
            int cosmetics = TankFloors.fingerprint(be);
            if (cosmetics != cosmeticFingerprint) {
                cosmeticFingerprint = cosmetics;
                changed = true;
            }
            if (!changed && idx == count) return;
            rebuildSingle(be, blockPosHash, level);
            return;
        }

        boolean enteredGroupMode = !groupMode;
        groupMode = true;

        // The group's own fish, descriptors and partition belong to the group runtime; this tank
        // only decides which of its fish stay home. Both halves of that decision use one rule
        // (TankGroupFlock.GroupSplit) so they cannot disagree about a slot.
        groupFlock = entry.flock();
        if (groupFlock != null) {
            long tick = ClientTankFlocks.tickCounter();
            groupFlock.touch(tick);
            groupFlock.syncIfDue(entry, level, tick);
        }

        boolean ownChanged = contentsChanged(be, ownSnapshot);
        if (!membershipChanged && !enteredGroupMode && !ownChanged) return;
        rebuildGroupMode(be, blockPosHash, level, entry);
    }

    /** Last-seen member list, so a group that gained or lost a tank re-splits this tank's fish. */
    private List<BlockPos> cachedMembers = List.of();
    /**
     * Last-seen cosmetic layout of this tank's own floor. Cosmetics are terrain for crawlers, so a
     * moved one has to re-shape the floor and re-place anything standing where it landed — but
     * nothing else announces that they moved, so it is watched by fingerprint.
     */
    private int cosmeticFingerprint = Integer.MIN_VALUE;

    // ── Single-tank path ────────────────────────────────────────────────────

    private void rebuildSingle(FishTankBlockEntity be, int blockPosHash, Level level) {
        SwarmConfig swarm = SwarmConfig.resolve(be.getFirstItem(), level);
        int maxCount = swarm.count();

        int n = 0;
        for (int slot = 0; slot < FishTankBlockEntity.CONTAINER_SIZE && n < maxCount; slot++) {
            if (!be.getItem(slot).isEmpty()) n++;
        }

        // Collected before touching stacks[]/slots[]: allocate() reuses those arrays whenever the
        // count is unchanged, so the previous layout has to be read out first to map identities.
        int[] newSlots = new int[n];
        ItemStack[] newStacks = new ItemStack[n];
        FishAnimationConfig[] newAnims = new FishAnimationConfig[n];
        FishSpec[] specs = new FishSpec[n];
        int idx = 0;
        for (int slot = 0; slot < FishTankBlockEntity.CONTAINER_SIZE && idx < n; slot++) {
            ItemStack s = be.getItem(slot);
            if (s.isEmpty()) continue;

            ItemStack stack = s.copy();
            FishTankBlockEntityRenderer.ResolvedFishRender render =
                    FishTankBlockEntityRenderer.resolveFishRender(stack, level);

            newSlots[idx] = slot;
            newStacks[idx] = stack;
            newAnims[idx] = render.animation();

            specs[idx] = new FishSpec(
                    renderedLength(stack, render.renderCalibration()),
                    locomotionOf(newAnims[idx]),
                    be.isItemMirrored(slot),
                    speciesId(stack),
                    shelterUseOf(stack, level));
            idx++;
        }

        int[] carryFrom = new int[n];
        for (int i = 0; i < n; i++) {
            carryFrom[i] = findCarry(newSlots[i], newStacks[i], slots, stacks, count);
        }

        count = n;
        allocate(n);
        System.arraycopy(newStacks, 0, stacks, 0, n);
        System.arraycopy(newAnims, 0, anims, 0, n);
        slots = newSlots;

        if (wantsShelters(be, specs, level) || wantsObstacles(be, specs, level)) {
            rebuildPromoted(be, specs, carryFrom, blockPosHash, swarm, level);
            return;
        }
        if (engine.tunables() != Tunables.DEFAULT) engine.setTunables(Tunables.DEFAULT);
        engine.rebuildPreserving(specs, carryFrom, blockPosHash, be.getFirstItemRotation(),
                swarm.depthLayers(), swarm.xzSpread(), swarm.yRange(), swarm.rotationJitter(),
                TankFloors.single(be));
    }

    /**
     * Whether this lone tank runs the planar engine instead of the binary one
     * (docs/fish-shelters.md §7): it holds a shelter <i>and</i> a fish whose species uses
     * shelters. Every other lone tank keeps the bitwise-locked binary model — the shelter is only
     * worth the switch when something in the tank will swim into it.
     */
    private static boolean wantsShelters(FishTankBlockEntity be, FishSpec[] fish, Level level) {
        boolean anyUser = false;
        for (FishSpec spec : fish) {
            if (spec.shelterUse() != ShelterUse.NONE) {
                anyUser = true;
                break;
            }
        }
        return anyUser && TankShelters.hasShelter(be, level);
    }

    /**
     * Whether this lone tank runs the planar engine so its swimmers steer round its cosmetics
     * (docs/fish-shelters.md §12.8, open question 6): it holds a structure with a solid part
     * <i>and</i> a fish that swims. The binary model cannot learn obstacles, and a fish swimming
     * through a castle wall reads as a bug wherever players keep it.
     */
    private static boolean wantsObstacles(FishTankBlockEntity be, FishSpec[] fish, Level level) {
        boolean anySwimmer = false;
        for (FishSpec spec : fish) {
            if (spec.locomotion() == Locomotion.FREE_SWIM || spec.locomotion() == Locomotion.GLIDE) {
                anySwimmer = true;
                break;
            }
        }
        return anySwimmer && TankObstacles.hasSolid(be, level);
    }

    /**
     * How this fish's species uses shelters (its profile's {@code swarm.shelter}), mapped down to
     * the engine's enum — {@link ShelterUse#NONE} unless the species opts in.
     */
    static ShelterUse shelterUseOf(ItemStack stack, Level level) {
        return SwarmConfig.resolve(stack, level).shelter().map(behaviour -> switch (behaviour) {
            case VISITOR -> ShelterUse.VISITOR;
            case SKITTISH -> ShelterUse.SKITTISH;
            case LURKER -> ShelterUse.LURKER;
        }).orElse(ShelterUse.NONE);
    }

    /**
     * The promoted lone tank: the same engine switched onto the planar model over a one-block
     * voxel domain with the group tunables — what a group of one would be — in the very frame the
     * binary model was using (the tank's first-item rotation, origin at the block's centre on the
     * item baseline). Keeping the frame is what lets {@code rebuildPreserving} carry every fish
     * across the switch where it is, instead of re-scattering the tank; the domain, floor and
     * shelters are all expressed in that same frame.
     */
    private void rebuildPromoted(FishTankBlockEntity be, FishSpec[] specs, int[] carryFrom, int blockPosHash,
                                 SwarmConfig swarm, Level level) {
        if (engine.tunables() != Tunables.GROUP) engine.setTunables(Tunables.GROUP);
        float yaw = be.getFirstItemRotation();
        VoxelDomain domain = new VoxelDomain(new boolean[][][]{{{true}}}, VoxelDomain.DEFAULT_INSET,
                TankFloors.GROUP_SURFACE_OFFSET, TankFloors.blockedCells(be));
        domain.rebuildShelters(TankShelters.single(be, level, yaw));
        domain.rebuildObstacles(TankObstacles.single(be, level, yaw));
        engine.rebuildPreserving(specs, carryFrom, blockPosHash, yaw, swarm.rotationJitter(), domain);
    }

    /**
     * Index this fish held in the previous rebuild, or {@code -1} if it wasn't there. The stack
     * has to match as well as the slot: a player swapping one species into a freed slot must get
     * a fresh fish, not the departed one's momentum and animation phase.
     */
    private static int findCarry(int slot, ItemStack stack, int[] prevSlots, ItemStack[] prevStacks, int prevCount) {
        int limit = Math.min(prevCount, Math.min(prevSlots.length, prevStacks.length));
        for (int i = 0; i < limit; i++) {
            if (prevSlots[i] == slot && prevStacks[i] != null
                    && FishtasticItemData.isSameItemSameData(prevStacks[i], stack)) {
                return i;
            }
        }
        return -1;
    }

    // ── Group path ──────────────────────────────────────────────────────────

    /**
     * Rebuilds what this tank keeps for itself while it is part of a group. Everything the group
     * takes is {@link TankGroupFlock}'s; this pass uses the identical rule over the identical slots,
     * so the two agree on every one of them — see {@link TankGroupFlock.GroupSplit}.
     */
    private void rebuildGroupMode(FishTankBlockEntity be, int blockPosHash, Level level,
                                  ClientTankGroups.Entry entry) {
        TankGroups.Group group = entry.group();
        float gateRun = entry.domain().sizeGateRun();
        float gateFactor = Tunables.DEFAULT.gateFactor();
        ownSnapshot = snapshot(be);

        // Whatever this tank does not hand to the group stays here on its local engine: creatures
        // whose class has no motion model yet, and the ones the group's gates or quotas turned
        // away. A crawler that stays keeps crawling, on its own tank's sand; everything else holds
        // its scatter position exactly as it always has.
        SwarmConfig swarm = SwarmConfig.resolve(be.getFirstItem(), level);
        List<ItemStack> hoverStacks = new ArrayList<>();
        List<FishAnimationConfig> hoverAnims = new ArrayList<>();
        List<FishSpec> hoverSpecs = new ArrayList<>();
        List<Integer> hoverSlots = new ArrayList<>();
        // Fish past the group's fish budget stay here instead of joining. The group runtime's
        // collection pass applies the identical rule to the identical slots — and this pass reads
        // the very budget that pass computed — so every fish is rendered exactly once. See
        // TankGroups.perTankFishCap and TankFishBudget.
        TankFishBudget budget = entry.flock() != null ? entry.flock().budget() : TankFishBudget.unlimited();
        TankGroupFlock.GroupSplit split = new TankGroupFlock.GroupSplit(gateRun, gateFactor, budget);
        for (int slot = 0; slot < FishTankBlockEntity.CONTAINER_SIZE; slot++) {
            ItemStack s = be.getItem(slot);
            if (s.isEmpty()) continue;
            ItemStack stack = s.copy();
            FishTankBlockEntityRenderer.ResolvedFishRender render =
                    FishTankBlockEntityRenderer.resolveFishRender(stack, level);
            float length = renderedLength(stack, render.renderCalibration());
            Locomotion locomotion = locomotionOf(render.animation());
            if (split.joins(locomotion, length)) continue; // simulated with the group

            hoverStacks.add(stack);
            hoverAnims.add(render.animation());
            hoverSlots.add(slot);
            hoverSpecs.add(new FishSpec(length, TankGroupFlock.GroupSplit.stayingHomeAs(locomotion),
                    be.isItemMirrored(slot), speciesId(stack)));
        }
        int hoverCount = hoverStacks.size();
        int[] hoverCarry = new int[hoverCount];
        int[] newHoverSlots = new int[hoverCount];
        for (int i = 0; i < hoverCount; i++) {
            newHoverSlots[i] = hoverSlots.get(i);
            hoverCarry[i] = findCarry(newHoverSlots[i], hoverStacks.get(i), slots, stacks, count);
        }
        count = hoverCount;
        allocate(count);
        for (int i = 0; i < count; i++) {
            stacks[i] = hoverStacks.get(i);
            anims[i] = hoverAnims.get(i);
        }
        slots = newHoverSlots;
        engine.rebuildPreserving(hoverSpecs.toArray(new FishSpec[0]), hoverCarry,
                blockPosHash, be.getFirstItemRotation(),
                swarm.depthLayers(), swarm.xzSpread(), swarm.yRange(), swarm.rotationJitter(),
                TankFloors.single(be));
    }

    // ── Change detection helpers ────────────────────────────────────────────

    private ItemStack[] snapshot(FishTankBlockEntity be) {
        List<ItemStack> contents = new ArrayList<>();
        for (int slot = 0; slot < FishTankBlockEntity.CONTAINER_SIZE; slot++) {
            ItemStack s = be.getItem(slot);
            if (!s.isEmpty()) contents.add(s.copy());
        }
        return contents.toArray(new ItemStack[0]);
    }

    private static boolean contentsChanged(FishTankBlockEntity be, ItemStack[] snapshot) {
        int idx = 0;
        for (int slot = 0; slot < FishTankBlockEntity.CONTAINER_SIZE; slot++) {
            ItemStack s = be.getItem(slot);
            if (s.isEmpty()) continue;
            if (idx >= snapshot.length || !FishtasticItemData.isSameItemSameData(s, snapshot[idx])) return true;
            idx++;
        }
        return idx != snapshot.length;
    }

    // ── Shared helpers ──────────────────────────────────────────────────────

    /**
     * The default pose → locomotion mapping (docs/fish-sim-locomotion.md §2.1). Pose and
     * locomotion are independent concepts; this says what each render pose most likely implies
     * about how the creature moves, so no data file has to declare anything. The mapping lives
     * here rather than on {@link FishAnimationConfig} to keep this the only class that knows both
     * worlds.
     */
    static Locomotion locomotionOf(FishAnimationConfig anim) {
        return switch (anim) {
            case FishAnimationConfig.HorizontalSwim ignored -> Locomotion.FREE_SWIM;
            case FishAnimationConfig.BellyDown      ignored -> Locomotion.GLIDE;
            case FishAnimationConfig.UprightFloat   ignored -> Locomotion.DRIFT;
            case FishAnimationConfig.FloorSit       ignored -> Locomotion.BENTHIC;
            case FishAnimationConfig.UprightSit     ignored -> Locomotion.BENTHIC;
            case FishAnimationConfig.Planted        ignored -> Locomotion.ANCHORED;
        };
    }

    /**
     * Opaque per-species id for the engine's species-aware separation/schooling: fish of the same
     * item type school together, different items keep the wider cross-species distance. The item
     * registry id is stable for the client session, which is all the engine needs.
     */
    static int speciesId(ItemStack stack) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(stack.getItem());
    }

    private void allocate(int n) {
        if (stacks.length == n) return;
        stacks = new ItemStack[n];
        anims = new FishAnimationConfig[n];
    }

    static float renderedLength(ItemStack stack, float calibration) {
        // Matches the render scale exactly: unrolled fish render at a flat 0.5 (never length 0,
        // which would let an unmeasured species free-swim when it shouldn't).
        if (ItemSizeHelper.hasSize(stack)) {
            return (ItemSizeHelper.getSize(stack) / 100f) * calibration;
        }
        return 0.5f;
    }
}
