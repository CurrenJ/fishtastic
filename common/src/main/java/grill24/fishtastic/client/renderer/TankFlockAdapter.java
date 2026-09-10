package grill24.fishtastic.client.renderer;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.VoxelDomain;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.client.util.ClientTankGroups;
import grill24.fishtastic.client.util.TankFloors;
import grill24.fishtastic.fishtank.TankGroups;
import grill24.fishtastic.data.FishAnimationConfig;
import grill24.fishtastic.data.SwarmConfig;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * The only class that knows both worlds (docs/fish-sim-engine-plan.md §1): it maps tank contents
 * down to Minecraft-free {@link FishSpec}s for the {@link FlockEngine}, and holds everything the
 * engine deliberately lost in the extraction — the {@link ItemStack}s themselves, their animation
 * configs, and the per-fish {@link ItemStackRenderState}s (still one per fish: {@code submit}
 * defers to the end of the frame, so a single shared state would render every fish as the last
 * one submitted).
 *
 * <p>Replaces the pre-extraction {@code TankFlockSimulation}'s MC-facing half. A per-tank adapter
 * is held in {@code ClientTankFlocks} (registry keyed by {@code BlockPos}, 20 Hz tick, 30 s
 * eviction) exactly as the sim used to be; the renderer reads interpolated state through
 * {@link #engine()}. The extract path never advances simulation time — only
 * {@code ClientTankFlocks.tickAll()} steps.
 *
 * <p><b>Multi-tank preview (Task 9).</b> When this tank shares open faces with neighbours, the
 * connected group elects the smallest member position as anchor. Every member keeps rendering its
 * own non-swimming fish (floor-anchored species, non-swim animations, group-gate failures)
 * through its local engine exactly like today's hover path; the anchor additionally runs one
 * {@linkplain VoxelDomain voxel-domain} engine over every member's free swimmers and renders them
 * across the shared volume. A lone tank takes the legacy single-tank path unchanged. Known
 * preview artifacts (frustum culling at the anchor, anchor-block lighting) are accepted — the
 * server-side lock model is a separate workstream.
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
    // One render state per fish (persistent, reused across frames) — see the class javadoc.
    ItemStackRenderState[] itemRenderStates = new ItemStackRenderState[0];

    // ── Group state (multi-tank preview) ────────────────────────────────────
    private List<BlockPos> cachedMembers = List.of();
    private boolean groupMode;
    private boolean groupAnchor;
    /** Snapshot of this tank's full contents, for change detection while stacks[] is hover-filtered. */
    private ItemStack[] ownSnapshot = new ItemStack[0];
    /** Anchor only: snapshot of every member's contents, concatenated in member order. */
    private ItemStack[] groupSnapshot = new ItemStack[0];

    private FlockEngine groupEngine;
    ItemStack[] groupStacks = new ItemStack[0];
    FishAnimationConfig[] groupAnims = new FishAnimationConfig[0];
    /** Group-engine fish identity: the owning tank's packed position plus its container slot. */
    private long[] groupKeyPos = new long[0];
    private int[] groupKeySlot = new int[0];
    ItemStackRenderState[] groupRenderStates = new ItemStackRenderState[0];
    float groupOffsetX, groupOffsetY, groupOffsetZ;

    private int count;
    private long lastExtractTick = Long.MIN_VALUE;
    /**
     * Last-seen cosmetic layout, own tank and (anchor only) group-wide. Cosmetics are terrain for
     * crawlers, so moving one has to re-shape the floor and re-place anything standing where it
     * landed — but nothing else announces that they moved, so it is watched by fingerprint.
     */
    private int cosmeticFingerprint = Integer.MIN_VALUE;
    private int groupCosmeticFingerprint = Integer.MIN_VALUE;

    public FlockEngine engine() {
        return engine;
    }

    public int count() {
        return count;
    }

    /** The anchor's group-wide swimmer engine, or null when not simulating a group here. */
    public FlockEngine groupEngine() {
        return groupMode && groupAnchor ? groupEngine : null;
    }

    public long lastExtractTick() {
        return lastExtractTick;
    }

    /** Marks this flock as having been extracted this client tick (drives eviction). */
    public void touch(long tick) {
        this.lastExtractTick = tick;
    }

    /** Advances the engine(s) by one fixed 20 Hz step. Runs on the client tick, never at render. */
    public void step() {
        engine.step();
        if (groupMode && groupAnchor && groupEngine != null) {
            groupEngine.step();
        }
    }

    /**
     * Writes interpolated world-space offsets for this frame's {@code partialTick} into the
     * engine's render scratch. Called from {@code extractRenderState} every frame — read-only
     * with respect to simulation time.
     */
    void interpolate(float partialTick) {
        engine.interpolate(partialTick);
        if (groupMode && groupAnchor && groupEngine != null) {
            groupEngine.interpolate(partialTick);
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
            groupAnchor = false;
            // Legacy single-tank path — behavior identical to the pre-extraction sim.
            int idx = 0;
            boolean changed = leftGroupMode || membershipChanged;
            for (int slot = 0; slot < FishTankBlockEntity.CONTAINER_SIZE && !changed; slot++) {
                ItemStack s = be.getItem(slot);
                if (!s.isEmpty()) {
                    changed = idx >= count || !ItemStack.isSameItemSameComponents(s, stacks[idx]);
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
        groupAnchor = be.getBlockPos().equals(group.anchor());

        boolean ownChanged = contentsChanged(be, ownSnapshot);
        boolean groupChanged = groupAnchor && groupContentsChanged(level, group);
        boolean cosmeticsChanged = false;
        if (groupAnchor) {
            int cosmetics = TankFloors.groupFingerprint(group, level);
            cosmeticsChanged = cosmetics != groupCosmeticFingerprint;
            if (cosmeticsChanged) {
                groupCosmeticFingerprint = cosmetics;
                // Cheap 2D pass, unlike the domain around it — see VoxelDomain.setFloor.
                entry.domain().rebuildFloor(TankFloors.GROUP_SURFACE_OFFSET,
                        TankFloors.groupBlockedCells(group, level));
            }
        }
        if (!membershipChanged && !enteredGroupMode && !ownChanged && !groupChanged && !cosmeticsChanged) return;

        rebuildGroupMode(be, blockPosHash, level, entry);
    }

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
                    speciesId(stack));
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

        engine.rebuildPreserving(specs, carryFrom, blockPosHash, be.getFirstItemRotation(),
                swarm.depthLayers(), swarm.xzSpread(), swarm.yRange(), swarm.rotationJitter(),
                TankFloors.single(be));
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
                    && ItemStack.isSameItemSameComponents(prevStacks[i], stack)) {
                return i;
            }
        }
        return -1;
    }

    // ── Group path ──────────────────────────────────────────────────────────

    private void rebuildGroupMode(FishTankBlockEntity be, int blockPosHash, Level level,
                                  ClientTankGroups.Entry entry) {
        TankGroups.Group group = entry.group();
        // Shared with every other member and rebuilt only when membership changes — building one
        // here per content change was a 26-109 ms hitch every time a player added a fish
        // (docs/fish-tank-group-scaling.md §5.3a).
        VoxelDomain domain = entry.domain();
        float gateRun = domain.sizeGateRun();
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
        // Fish past this tank's share of the group budget stay here instead of joining. The
        // anchor's collection pass below applies the identical rule to the identical slots, so
        // every fish is rendered exactly once — see TankGroups.perTankFishQuota.
        int quota = TankGroups.perTankFishQuota(group.members().size());
        GroupSplit split = new GroupSplit(gateRun, gateFactor, quota);
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
            hoverSpecs.add(new FishSpec(length, GroupSplit.stayingHomeAs(locomotion),
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

        if (!groupAnchor) {
            groupEngine = null;
            return;
        }

        // Anchor: one voxel-domain engine over every member's swimmers, crawlers and drifters.
        List<ItemStack> swimStacks = new ArrayList<>();
        List<FishAnimationConfig> swimAnims = new ArrayList<>();
        List<FishSpec> swimSpecs = new ArrayList<>();
        List<ItemStack> allContents = new ArrayList<>();
        List<Long> swimKeyPos = new ArrayList<>();
        List<Integer> swimKeySlot = new ArrayList<>();
        for (BlockPos memberPos : group.members()) {
            if (!(level.getBlockEntity(memberPos) instanceof FishTankBlockEntity member)) continue;
            // Mirrors the per-member split above exactly — same rule object, so the two passes
            // cannot drift apart. Disagree on one slot and a fish is drawn twice or not at all.
            GroupSplit memberSplit = new GroupSplit(gateRun, gateFactor, quota);
            for (int slot = 0; slot < FishTankBlockEntity.CONTAINER_SIZE; slot++) {
                ItemStack s = member.getItem(slot);
                if (s.isEmpty()) continue;
                ItemStack stack = s.copy();
                allContents.add(stack);
                FishTankBlockEntityRenderer.ResolvedFishRender render =
                        FishTankBlockEntityRenderer.resolveFishRender(stack, level);
                float length = renderedLength(stack, render.renderCalibration());
                Locomotion locomotion = locomotionOf(render.animation());
                if (!memberSplit.joins(locomotion, length)) continue; // stays on its own tank's engine
                swimStacks.add(stack);
                swimAnims.add(render.animation());
                swimKeyPos.add(memberPos.asLong());
                swimKeySlot.add(slot);
                swimSpecs.add(new FishSpec(length, locomotion, member.isItemMirrored(slot), speciesId(stack)));
            }
        }
        groupSnapshot = allContents.toArray(new ItemStack[0]);

        int n = swimStacks.size();
        int[] swimCarry = new int[n];
        long[] newKeyPos = new long[n];
        int[] newKeySlot = new int[n];
        for (int i = 0; i < n; i++) {
            newKeyPos[i] = swimKeyPos.get(i);
            newKeySlot[i] = swimKeySlot.get(i);
            swimCarry[i] = findGroupCarry(newKeyPos[i], newKeySlot[i], swimStacks.get(i));
        }
        groupStacks = swimStacks.toArray(new ItemStack[0]);
        groupAnims = swimAnims.toArray(new FishAnimationConfig[0]);
        groupKeyPos = newKeyPos;
        groupKeySlot = newKeySlot;
        groupRenderStates = new ItemStackRenderState[n];
        for (int i = 0; i < n; i++) groupRenderStates[i] = new ItemStackRenderState();
        groupOffsetX = group.offsetX();
        groupOffsetY = group.offsetY();
        groupOffsetZ = group.offsetZ();

        boolean freshEngine = groupEngine == null;
        if (freshEngine) groupEngine = new FlockEngine(Tunables.GROUP);
        // A brand-new engine has nothing to carry. Membership changes re-shape the domain itself,
        // but the carry still holds every fish that stayed put, which is the point.
        groupEngine.rebuildPreserving(swimSpecs.toArray(new FishSpec[0]), swimCarry,
                group.anchor().hashCode(), 0f, swarm.rotationJitter(), domain);
    }

    /**
     * The single rule deciding whether a fish joins the anchor's group engine or stays behind on
     * its own tank's — and, for the ones that stay, what class they keep (docs/fish-sim-locomotion.md
     * §3.5).
     *
     * <p>It exists as an object rather than as two copies of an {@code if} because
     * {@link #rebuildGroupMode} applies it twice: once per member deciding what that tank keeps,
     * and once at the anchor collecting what the group takes. The two passes walk the same slots
     * in the same order and <b>must agree on every one of them</b> — disagree and a fish is drawn
     * twice or not at all — so they share this, quota counters included. One instance per tank; a
     * fresh one per member in the anchor's pass.
     *
     * <p>Each simulated class counts against its <b>own</b> copy of the quota. They compete for
     * different resources — water volume, floor area, and the vertical column a jellyfish pulses
     * through — so a tank full of one must not evict a creature the group has ample room for.
     */
    static final class GroupSplit {
        private final float gateRun;
        private final float gateFactor;
        private final int quota;
        private final int[] taken = new int[Locomotion.values().length];

        GroupSplit(float gateRun, float gateFactor, int quota) {
            this.gateRun = gateRun;
            this.gateFactor = gateFactor;
            this.quota = quota;
        }

        /** Whether this fish joins the group engine, consuming a slot of its class's quota if so. */
        boolean joins(Locomotion locomotion, float length) {
            boolean eligible = switch (locomotion) {
                // The group's size gate, applied here as well as in the engine, for the two
                // classes that measure a straight run: a fish the group is too cramped for stays
                // home and hovers in its own tank, which is the behaviour it has always had, and
                // it spends none of the group's budget on the way.
                case FREE_SWIM, GLIDE -> gateRun >= gateFactor * length;
                // Crawlers, drifters and eels are admitted ungated and let the engine's own
                // per-class gate demote them if it must: a demoted one still belongs in group
                // space, on the group's sand or hanging in its water, which is where the player
                // sees it.
                case BENTHIC, DRIFT, ANCHORED -> true;
                // The one class with no motion model: renders out of its own tank, frozen.
                case STATIC -> false;
            };
            if (!eligible) return false;
            int idx = locomotion.ordinal();
            if (taken[idx] >= quota) return false;
            taken[idx]++;
            return true;
        }

        /**
         * The class a fish that stayed behind runs under on its own tank's engine. A class that
         * can move on its own in a lone tank keeps it — a crawler walks its tank's floor, a
         * drifter drifts its own water, a glider that only lost the quota draw still glides.
         * Everything else is pinned {@link Locomotion#STATIC}, which includes the swimmers and
         * gliders the <em>group's</em> size gate turned away: those must not start swimming
         * locally just because this member's own box is individually big enough. That is the
         * asymmetry — the gate is applied in {@link #joins}, so anything reaching here having
         * failed it is already unable to pass its own tank's copy of the same gate.
         */
        static Locomotion stayingHomeAs(Locomotion locomotion) {
            return switch (locomotion) {
                case BENTHIC, DRIFT, GLIDE, ANCHORED -> locomotion;
                default -> Locomotion.STATIC;
            };
        }
    }

    /** {@link #findCarry} for the group engine, where a fish's identity is owning tank + slot. */
    private int findGroupCarry(long posKey, int slot, ItemStack stack) {
        int limit = Math.min(groupStacks.length, Math.min(groupKeyPos.length, groupKeySlot.length));
        for (int i = 0; i < limit; i++) {
            if (groupKeyPos[i] == posKey && groupKeySlot[i] == slot
                    && ItemStack.isSameItemSameComponents(groupStacks[i], stack)) {
                return i;
            }
        }
        return -1;
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
            if (idx >= snapshot.length || !ItemStack.isSameItemSameComponents(s, snapshot[idx])) return true;
            idx++;
        }
        return idx != snapshot.length;
    }

    private boolean groupContentsChanged(Level level, TankGroups.Group group) {
        int idx = 0;
        for (BlockPos memberPos : group.members()) {
            if (!(level.getBlockEntity(memberPos) instanceof FishTankBlockEntity member)) return true;
            for (int slot = 0; slot < FishTankBlockEntity.CONTAINER_SIZE; slot++) {
                ItemStack s = member.getItem(slot);
                if (s.isEmpty()) continue;
                if (idx >= groupSnapshot.length || !ItemStack.isSameItemSameComponents(s, groupSnapshot[idx])) return true;
                idx++;
            }
        }
        return idx != groupSnapshot.length;
    }

    // ── Shared helpers ──────────────────────────────────────────────────────

    /**
     * The default pose → locomotion mapping (docs/fish-sim-locomotion.md §2.1). Pose and
     * locomotion are independent concepts; this says what each render pose most likely implies
     * about how the creature moves, so no data file has to declare anything. The mapping lives
     * here rather than on {@link FishAnimationConfig} to keep this the only class that knows both
     * worlds.
     */
    private static Locomotion locomotionOf(FishAnimationConfig anim) {
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
    private static int speciesId(ItemStack stack) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(stack.getItem());
    }

    private void allocate(int n) {
        if (stacks.length == n) return;
        stacks = new ItemStack[n];
        anims = new FishAnimationConfig[n];
        itemRenderStates = new ItemStackRenderState[n];
        for (int i = 0; i < n; i++) itemRenderStates[i] = new ItemStackRenderState();
    }

    private static float renderedLength(ItemStack stack, float calibration) {
        // Matches the render scale exactly: unrolled fish render at a flat 0.5 (never length 0,
        // which would let an unmeasured species free-swim when it shouldn't).
        if (ItemSizeHelper.hasSize(stack)) {
            return (ItemSizeHelper.getSize(stack) / 100f) * calibration;
        }
        return 0.5f;
    }
}
