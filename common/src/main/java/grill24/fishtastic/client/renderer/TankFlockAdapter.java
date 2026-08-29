package grill24.fishtastic.client.renderer;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.VoxelDomain;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.client.util.TankGroups;
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
        TankGroups.Group group = TankGroups.of(be, level);
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
            if (!changed && idx == count) return;
            rebuildSingle(be, blockPosHash, level);
            return;
        }

        boolean enteredGroupMode = !groupMode;
        groupMode = true;
        groupAnchor = be.getBlockPos().equals(group.anchor());

        boolean ownChanged = contentsChanged(be, ownSnapshot);
        boolean groupChanged = groupAnchor && groupContentsChanged(level, group);
        if (!membershipChanged && !enteredGroupMode && !ownChanged && !groupChanged) return;

        rebuildGroupMode(be, blockPosHash, level, group);
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
                    canSwim(newAnims[idx]),
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
                swarm.depthLayers(), swarm.xzSpread(), swarm.yRange(), swarm.rotationJitter());
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
                                  TankGroups.Group group) {
        VoxelDomain domain = new VoxelDomain(group.occupancy());
        float gateRun = domain.sizeGateRun();
        float gateFactor = Tunables.DEFAULT.gateFactor();
        ownSnapshot = snapshot(be);

        // This tank keeps its non-swimmers (floor-anchored, non-swim animations, and fish too big
        // even for the group's longest run), hovering exactly like today's gate failures. The
        // engine sees them as canSwim=false so they hold their scatter positions.
        SwarmConfig swarm = SwarmConfig.resolve(be.getFirstItem(), level);
        List<ItemStack> hoverStacks = new ArrayList<>();
        List<FishAnimationConfig> hoverAnims = new ArrayList<>();
        List<FishSpec> hoverSpecs = new ArrayList<>();
        List<Integer> hoverSlots = new ArrayList<>();
        for (int slot = 0; slot < FishTankBlockEntity.CONTAINER_SIZE; slot++) {
            ItemStack s = be.getItem(slot);
            if (s.isEmpty()) continue;
            ItemStack stack = s.copy();
            FishTankBlockEntityRenderer.ResolvedFishRender render =
                    FishTankBlockEntityRenderer.resolveFishRender(stack, level);
            float length = renderedLength(stack, render.renderCalibration());
            if (canSwim(render.animation()) && gateRun >= gateFactor * length) continue; // swims with the group
            hoverStacks.add(stack);
            hoverAnims.add(render.animation());
            hoverSlots.add(slot);
            hoverSpecs.add(new FishSpec(length, false, be.isItemMirrored(slot), speciesId(stack)));
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
                swarm.depthLayers(), swarm.xzSpread(), swarm.yRange(), swarm.rotationJitter());

        if (!groupAnchor) {
            groupEngine = null;
            return;
        }

        // Anchor: one voxel-domain engine over every member's free swimmers.
        List<ItemStack> swimStacks = new ArrayList<>();
        List<FishAnimationConfig> swimAnims = new ArrayList<>();
        List<FishSpec> swimSpecs = new ArrayList<>();
        List<ItemStack> allContents = new ArrayList<>();
        List<Long> swimKeyPos = new ArrayList<>();
        List<Integer> swimKeySlot = new ArrayList<>();
        for (BlockPos memberPos : group.members()) {
            if (!(level.getBlockEntity(memberPos) instanceof FishTankBlockEntity member)) continue;
            for (int slot = 0; slot < FishTankBlockEntity.CONTAINER_SIZE; slot++) {
                ItemStack s = member.getItem(slot);
                if (s.isEmpty()) continue;
                ItemStack stack = s.copy();
                allContents.add(stack);
                FishTankBlockEntityRenderer.ResolvedFishRender render =
                        FishTankBlockEntityRenderer.resolveFishRender(stack, level);
                float length = renderedLength(stack, render.renderCalibration());
                if (!canSwim(render.animation()) || gateRun < gateFactor * length) continue;
                swimStacks.add(stack);
                swimAnims.add(render.animation());
                swimKeyPos.add(memberPos.asLong());
                swimKeySlot.add(slot);
                swimSpecs.add(new FishSpec(length, true, member.isItemMirrored(slot), speciesId(stack)));
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

    private static boolean canSwim(FishAnimationConfig anim) {
        return !FishTankBlockEntityRenderer.isFloorAnchored(anim)
                && anim instanceof FishAnimationConfig.HorizontalSwim;
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
