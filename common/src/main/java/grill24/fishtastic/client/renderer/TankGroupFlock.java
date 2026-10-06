package grill24.fishtastic.client.renderer;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.VoxelDomain;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.client.util.ClientTankGroups;
import grill24.fishtastic.client.util.TankFloors;
import grill24.fishtastic.client.util.TankShelters;
import grill24.fishtastic.data.FishAnimationConfig;
import grill24.fishtastic.data.SwarmConfig;
import grill24.fishtastic.fishtank.TankGroups;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A multi-tank group's shared flock runtime: one {@linkplain VoxelDomain voxel-domain} engine over
 * every member's swimmers, the per-fish descriptors that draw it, and the partition that decides
 * which member draws which fish.
 *
 * <p><b>Why it is not owned by the anchor any more.</b> It used to live on the elected anchor
 * tank's {@link TankFlockAdapter}, and the anchor drew every fish in the group. But a block entity
 * renderer only runs while its own chunk section is on screen — {@code LevelRenderer} dispatches
 * block entities section by section, and 26.1.2's {@code BlockEntityRenderer} has no
 * {@code getRenderBoundingBox} to override — so the whole group's swimmers vanished the moment the
 * anchor block left the frustum, which on a large build is most of the time something else is being
 * looked at. Fish that belong to other tanks were also lit by the anchor block's light, and the
 * group engine did not exist at all until the anchor had been rendered once.
 *
 * <p>Owning the runtime here, one per group, fixes the cause rather than the symptom: each member
 * draws the fish inside itself ({@link GroupOwners}), so a fish is drawn by a renderer standing in
 * the same chunk section it is, and vanilla's own culling decides the two together. A visible fish
 * is always submitted, an off-screen one costs nothing, and each fish is lit by its own tank.
 *
 * <p>The runtime is keyed by anchor position in {@link ClientTankGroups}, exactly where a group's
 * shared {@link VoxelDomain} already lives, so like the domain it survives a membership epoch while
 * its anchor does — which is the same lifetime the old anchor-owned engine had, carry included.
 * It is delved into by whichever member is rendered; a group whose members are all cold simply
 * stops being stepped and drawn.
 */
public final class TankGroupFlock {

    private final FlockEngine engine = new FlockEngine(Tunables.GROUP);

    // ── Per-fish descriptors, indexed by engine fish index ────────────────────
    ItemStack[] stacks = new ItemStack[0];
    FishAnimationConfig[] anims = new FishAnimationConfig[0];
    /** Group-engine fish identity: the owning tank's packed position plus its container slot. */
    private long[] keyPos = new long[0];
    private int[] keySlot = new int[0];

    // ── Frame geometry ──────────────────────────────────────────────────────
    /** The group's anchor block — the origin the engine's own offsets are expressed from. */
    BlockPos anchorPos = BlockPos.ZERO;
    /** Offset from the anchor's block origin to the group bounding box's centre (see {@code Group}). */
    float offsetX, offsetY, offsetZ;

    // ── Ownership ───────────────────────────────────────────────────────────
    private GroupOwners owners;
    /** Members count and their indices, both in {@code group.members()} order — the bucket addressing. */
    private int memberCount;
    private Map<BlockPos, Integer> memberIndices = Map.of();
    /** Per member index, the group fish it draws — see {@link #bucket(int)}. Reused, grown amortised. */
    private int[][] buckets = new int[0][];
    private int[] bucketCounts = new int[0];

    // ── Change detection and cadence ────────────────────────────────────────
    private List<BlockPos> cachedMembers = List.of();
    /** The domain the engine was built against — a new epoch hands us a new one and forces a rebuild. */
    private VoxelDomain builtDomain;
    /** Snapshot of every member's contents, concatenated in member order. */
    private ItemStack[] contentsSnapshot = new ItemStack[0];
    private int cosmeticFingerprint = Integer.MIN_VALUE;
    private long lastSyncTick = Long.MIN_VALUE;
    private long lastStepTick = Long.MIN_VALUE;
    private long lastInterpolateTick = Long.MIN_VALUE;
    private float lastInterpolatePartial = Float.NaN;
    private long lastWarmTick = Long.MIN_VALUE;
    private long lastBubbleTick = Long.MIN_VALUE;
    /** Authoritative bubble-throttle memory for the group engine (docs/fish-tank-bubbles.md). */
    final TankBubbleEmitter.State bubbleEmitter = new TankBubbleEmitter.State();
    /** Scratch for {@link #setWatcher}, so the per-tick mapping allocates nothing. */
    private final float[] watcherScratch = new float[3];

    FlockEngine engine() {
        return engine;
    }

    public int count() {
        return engine.count();
    }

    /** The tick at which this group last had a member on screen, for the caller's eviction policy. */
    public long lastWarmTick() {
        return lastWarmTick;
    }

    /** Marks this group as having a member on screen this tick. */
    public void touch(long tick) {
        this.lastWarmTick = tick;
    }

    /**
     * True the first time it is asked in a given tick — how the group's bubbles stay a single
     * emission per step even though every warm member offers to emit them.
     */
    public boolean claimBubbleTick(long tick) {
        if (lastBubbleTick == tick) return false;
        lastBubbleTick = tick;
        return true;
    }

    /** This member's bucket: group fish indices it must draw. Valid until the next frame's refresh. */
    int[] bucket(int memberIndex) {
        return buckets[memberIndex];
    }

    int bucketSize(int memberIndex) {
        return bucketCounts[memberIndex];
    }

    /**
     * How the group's fish are currently distributed across its members — the partition's own
     * account of itself, for the render self-test ({@code client/selftest/RenderSelfTest}, the
     * {@code pertank} scene). Both ways this can go wrong are invisible in any rendered frame: a
     * fish claimed by nobody is simply never drawn, and one claimed twice has its second submission
     * overwrite the first's shared render state, so the frame still looks plausible. The harness
     * asserts on these counts instead of on pixels.
     */
    public OwnershipAudit auditOwnership() {
        int n = count();
        int[] claimed = new int[n];
        for (int member = 0; member < memberCount; member++) {
            int[] bucket = buckets[member];
            for (int k = 0; k < bucketCounts[member]; k++) {
                int fish = bucket[k];
                if (fish >= 0 && fish < n) claimed[fish]++;
            }
        }
        int drawn = 0;
        int twice = 0;
        int never = 0;
        for (int i = 0; i < n; i++) {
            if (claimed[i] == 1) drawn++;
            else if (claimed[i] > 1) twice++;
            else never++;
        }
        int drawing = 0;
        for (int member = 0; member < memberCount; member++) {
            if (bucketCounts[member] > 0) drawing++;
        }
        return new OwnershipAudit(n, drawn, twice, never, drawing, memberCount);
    }

    /**
     * @param fish         fish in the group engine
     * @param drawn        fish in exactly one member's bucket
     * @param claimedTwice fish in more than one — drawn twice, the second overwriting the first
     * @param claimedNever fish in none — never drawn at all
     * @param membersDrawing members with a non-empty bucket
     */
    public record OwnershipAudit(int fish, int drawn, int claimedTwice, int claimedNever,
                                 int membersDrawing, int members) {
        /** True when every fish is drawn exactly once. */
        public boolean isPartition() {
            return fish > 0 && drawn == fish && claimedTwice == 0 && claimedNever == 0;
        }
    }

    /** Index of {@code pos} in the group's member list, or {@code -1} if it is not a member. */
    int memberIndexOf(BlockPos pos) {
        Integer index = memberIndices.get(pos);
        return index == null ? -1 : index;
    }

    /**
     * Model-space offset from a member's block origin to the group's engine origin. The group's fish
     * are drawn in the <em>drawing</em> tank's model space, so this is the anchor-relative offset
     * rebased onto that member (see {@code FishTankBlockEntityRenderer.submitGroupSwimmers}).
     */
    float localOffsetX(BlockPos memberPos) {
        return offsetX + anchorPos.getX() - memberPos.getX();
    }

    float localOffsetY(BlockPos memberPos) {
        return offsetY + anchorPos.getY() - memberPos.getY();
    }

    float localOffsetZ(BlockPos memberPos) {
        return offsetZ + anchorPos.getZ() - memberPos.getZ();
    }

    // ── Simulation ──────────────────────────────────────────────────────────

    /**
     * Rebuilds the group's fish descriptors if anything they depend on changed, and refreshes the
     * domain floor when cosmetics moved. Called from the first member rendered each tick: the walk
     * this does spans every member, so it must not run once per member per frame.
     */
    void syncIfDue(ClientTankGroups.Entry entry, Level level, long tick) {
        if (lastSyncTick == tick) return;
        lastSyncTick = tick;

        TankGroups.Group group = entry.group();
        boolean membershipChanged = !group.members().equals(cachedMembers);
        cachedMembers = group.members();
        // A membership epoch hands out a fresh domain; the engine holds the old one directly, so a
        // new object identity is the signal that the group it was built for is gone.
        boolean domainChanged = entry.domain() != builtDomain;

        boolean contentsChanged = groupContentsChanged(level, group);

        boolean cosmeticsChanged = false;
        int cosmetics = TankFloors.groupFingerprint(group, level);
        if (cosmetics != cosmeticFingerprint) {
            cosmeticFingerprint = cosmetics;
            cosmeticsChanged = true;
            // Cheap 2D pass, unlike the domain around it — see VoxelDomain.setFloor.
            entry.domain().rebuildFloor(TankFloors.GROUP_SURFACE_OFFSET,
                    TankFloors.groupBlockedCells(group, level));
            entry.domain().rebuildShelters(TankShelters.group(group, level));
        }

        if (!membershipChanged && !domainChanged && !contentsChanged && !cosmeticsChanged) return;
        rebuild(entry, level);
    }

    /** Advances the group engine by one fixed 20 Hz step. Runs on the client tick, never at render. */
    public void stepIfDue(long tick) {
        if (lastStepTick == tick) return;
        lastStepTick = tick;
        engine.step();
    }

    /**
     * Writes interpolated world-space offsets for this frame's {@code partialTick} into the engine's
     * render scratch, then re-derives ownership from them. Called every frame — read-only with
     * respect to simulation time.
     *
     * <p>The stamp skips the duplicate calls a group with several members on screen produces within
     * one frame. Skipping is safe by construction: {@code interpolate} is a pure function of the
     * engine's state and the partial tick, so an identical {@code (tick, partialTick)} pair can only
     * ever produce the result already written.
     */
    void interpolateIfDue(long tick, float partialTick) {
        if (tick == lastInterpolateTick && partialTick == lastInterpolatePartial) return;
        lastInterpolateTick = tick;
        lastInterpolatePartial = partialTick;
        engine.interpolate(partialTick);
        // Ownership follows the positions actually being drawn this frame, never a stale tick's, so
        // a fish can never be handed to a tank far from where it is about to appear.
        refreshBuckets();
    }

    /** Tells the engine there is no player to react to. */
    public void clearWatcher() {
        engine.setWatcher(false, 0f, 0f, 0f);
    }

    /** Points the engine at the player, in the group's own frame (docs/fish-sim-locomotion.md §3.4). */
    public void setWatcher(double px, double py, double pz) {
        // The draw loop translates by the group's offsets alone, in the anchor block's own frame,
        // and the group engine carries no rotation — so this is a plain subtraction and toLocal is
        // the identity. Called through it anyway: "the group engine is unrotated" is a fact about
        // today's rebuild call, not a law.
        engine.toLocal((float) (px - anchorPos.getX()) - offsetX,
                (float) (py - anchorPos.getY()) - offsetY,
                (float) (pz - anchorPos.getZ()) - offsetZ, watcherScratch);
        engine.setWatcher(true, watcherScratch[0], watcherScratch[1], watcherScratch[2]);
    }

    /** World-space position of group fish #{@code i}, mirroring the draw loop's offset, bob included. */
    public Vec3 fishWorldPosition(int i, float gameTimeTicks) {
        float yBob = animatedYBob(engine, anims[i], i, gameTimeTicks);
        return new Vec3(
                anchorPos.getX() + offsetX + engine.renderX[i],
                anchorPos.getY() + offsetY + engine.renderY[i] + yBob,
                anchorPos.getZ() + offsetZ + engine.renderZ[i]);
    }

    /** Opaque per-species id of group fish #{@code i}, or {@code -1} if there is no such fish. */
    public int fishSpecies(int i) {
        return engine.species[i];
    }

    /**
     * The bob {@link FishAnimator#apply} adds on top of {@code eng.renderY[i]} purely for drawing.
     * Uses its own {@link Random} seeded exactly as the renderer seeds {@code fishRandom} before
     * posing this fish, so the draws that produce the bob come out identical.
     */
    private static float animatedYBob(FlockEngine eng, FishAnimationConfig anim, int i, float gameTimeTicks) {
        Random random = new Random(eng.seeds[i]);
        return eng.swimmers[i]
                ? FishAnimator.yBob(anim, random, eng.renderPhase[i], eng.speedFactor(i))
                : FishAnimator.yBob(anim, random, gameTimeTicks, 1f);
    }

    // ── Ownership ───────────────────────────────────────────────────────────

    /**
     * Re-derives each fish's owner from the current render positions. O(fish) with no allocation in
     * the steady state — buckets only ever grow, and each member keeps its own.
     *
     * <p>Filled in {@link FlockEngine#order} sequence rather than index order, so a member's bucket
     * arrives already sorted back-to-front. Fish are translucent sprites, so the draw order within a
     * tank is what keeps nearer ones blended over farther ones; taking the engine's ordering keeps
     * that identical to the single-tank path.
     */
    void refreshBuckets() {
        int members = memberCount;
        if (buckets.length != members) {
            buckets = new int[members][];
            bucketCounts = new int[members];
            for (int i = 0; i < members; i++) buckets[i] = new int[0];
        }
        for (int i = 0; i < members; i++) bucketCounts[i] = 0;
        int n = count();
        if (owners == null || n == 0) return;

        int[] order = engine.order;
        for (int k = 0; k < n; k++) {
            int i = order[k];
            int owner = owners.ownerOf(
                    anchorPos.getX() + offsetX + engine.renderX[i],
                    anchorPos.getY() + offsetY + engine.renderY[i],
                    anchorPos.getZ() + offsetZ + engine.renderZ[i]);
            if (owner < 0 || owner >= members) continue;
            int at = bucketCounts[owner];
            if (at == buckets[owner].length) {
                buckets[owner] = Arrays.copyOf(buckets[owner], Math.max(8, at * 2));
            }
            buckets[owner][at] = i;
            bucketCounts[owner] = at + 1;
        }
    }

    // ── Rebuild ─────────────────────────────────────────────────────────────

    /**
     * Collects every member's swimmers into one engine over the group's domain, carrying each fish's
     * position and animation phase across the rebuild. The split rule is {@link GroupSplit} — shared
     * with the members' own hover pass, so the two cannot disagree about a slot.
     */
    private void rebuild(ClientTankGroups.Entry entry, Level level) {
        TankGroups.Group group = entry.group();
        VoxelDomain domain = entry.domain();
        float gateRun = domain.sizeGateRun();
        float gateFactor = Tunables.DEFAULT.gateFactor();
        int quota = TankGroups.perTankFishQuota(group.members().size());

        // The group inherits the anchor's swarm shape, exactly as it did when the anchor built it.
        // Members whose block entity is not loaded are skipped by the collection loop below; an
        // anchor that is gone falls back to the default swarm rather than dropping the group.
        ItemStack anchorItem = level.getBlockEntity(group.anchor()) instanceof FishTankBlockEntity anchor
                ? anchor.getFirstItem() : ItemStack.EMPTY;
        SwarmConfig swarm = SwarmConfig.resolve(anchorItem, level);

        List<ItemStack> swimStacks = new ArrayList<>();
        List<FishAnimationConfig> swimAnims = new ArrayList<>();
        List<FishSpec> swimSpecs = new ArrayList<>();
        List<ItemStack> allContents = new ArrayList<>();
        List<Long> swimKeyPos = new ArrayList<>();
        List<Integer> swimKeySlot = new ArrayList<>();
        for (BlockPos memberPos : group.members()) {
            if (!(level.getBlockEntity(memberPos) instanceof FishTankBlockEntity member)) continue;
            // Mirrors the per-member split in TankFlockAdapter exactly — same rule object, so the two
            // passes cannot drift apart. Disagree on one slot and a fish is drawn twice or not at all.
            GroupSplit memberSplit = new GroupSplit(gateRun, gateFactor, quota);
            for (int slot = 0; slot < FishTankBlockEntity.CONTAINER_SIZE; slot++) {
                ItemStack s = member.getItem(slot);
                if (s.isEmpty()) continue;
                ItemStack stack = s.copy();
                allContents.add(stack);
                FishTankBlockEntityRenderer.ResolvedFishRender render =
                        FishTankBlockEntityRenderer.resolveFishRender(stack, level);
                float length = TankFlockAdapter.renderedLength(stack, render.renderCalibration());
                Locomotion locomotion = TankFlockAdapter.locomotionOf(render.animation());
                if (!memberSplit.joins(locomotion, length)) continue; // stays on its own tank's engine
                swimStacks.add(stack);
                swimAnims.add(render.animation());
                swimKeyPos.add(memberPos.asLong());
                swimKeySlot.add(slot);
                swimSpecs.add(new FishSpec(length, locomotion, member.isItemMirrored(slot),
                        TankFlockAdapter.speciesId(stack)));
            }
        }
        contentsSnapshot = allContents.toArray(new ItemStack[0]);

        int n = swimStacks.size();
        int[] swimCarry = new int[n];
        long[] newKeyPos = new long[n];
        int[] newKeySlot = new int[n];
        for (int i = 0; i < n; i++) {
            newKeyPos[i] = swimKeyPos.get(i);
            newKeySlot[i] = swimKeySlot.get(i);
            swimCarry[i] = findCarry(newKeyPos[i], newKeySlot[i], swimStacks.get(i));
        }
        stacks = swimStacks.toArray(new ItemStack[0]);
        anims = swimAnims.toArray(new FishAnimationConfig[0]);
        keyPos = newKeyPos;
        keySlot = newKeySlot;

        setGroupGeometry(group.members(), group.min(),
                group.occupancy().length, group.occupancy()[0].length, group.occupancy()[0][0].length,
                group.anchor(), group.offsetX(), group.offsetY(), group.offsetZ());

        engine.rebuildPreserving(swimSpecs.toArray(new FishSpec[0]), swimCarry,
                group.anchor().hashCode(), 0f, swarm.rotationJitter(), domain);
        builtDomain = domain;
        // After the engine, so ownership is derived from the positions the rebuild just left behind.
        // This frame's interpolate re-derives it again from the positions actually being drawn.
        refreshBuckets();
    }

    /**
     * Points the runtime at a group's geometry: its members, whose order defines the bucket indices;
     * the bounding box the ownership grid spans; and the anchor-relative origin every fish position
     * is measured from. Split out of {@link #rebuild} because it is the whole of what the partition
     * depends on, which lets {@code GroupOwnersTest} exercise buckets without a level.
     */
    void setGroupGeometry(List<BlockPos> members, BlockPos min, int sizeX, int sizeY, int sizeZ,
                          BlockPos anchor, float offsetX, float offsetY, float offsetZ) {
        this.anchorPos = anchor;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.owners = GroupOwners.build(members, min, sizeX, sizeY, sizeZ);
        this.memberCount = members.size();
        Map<BlockPos, Integer> indices = new HashMap<>();
        for (int i = 0; i < memberCount; i++) indices.put(members.get(i), i);
        this.memberIndices = Map.copyOf(indices);
        this.buckets = new int[0][];
        this.bucketCounts = new int[0];
    }

    /** {@link #findCarry} for the group engine, where a fish's identity is owning tank + slot. */
    private int findCarry(long posKey, int slot, ItemStack stack) {
        int limit = Math.min(stacks.length, Math.min(keyPos.length, keySlot.length));
        for (int i = 0; i < limit; i++) {
            if (keyPos[i] == posKey && keySlot[i] == slot
                    && FishtasticItemData.isSameItemSameData(stacks[i], stack)) {
                return i;
            }
        }
        return -1;
    }

    private boolean groupContentsChanged(Level level, TankGroups.Group group) {
        int idx = 0;
        for (BlockPos memberPos : group.members()) {
            if (!(level.getBlockEntity(memberPos) instanceof FishTankBlockEntity member)) return true;
            for (int slot = 0; slot < FishTankBlockEntity.CONTAINER_SIZE; slot++) {
                ItemStack s = member.getItem(slot);
                if (s.isEmpty()) continue;
                if (idx >= contentsSnapshot.length
                        || !FishtasticItemData.isSameItemSameData(s, contentsSnapshot[idx])) {
                    return true;
                }
                idx++;
            }
        }
        return idx != contentsSnapshot.length;
    }

    /**
     * The rule deciding whether a fish joins the group engine or stays on its own tank's — applied
     * once per member (deciding what that tank keeps, in {@link TankFlockAdapter}) and once here
     * (collecting what the group takes). The two passes walk the same slots in the same order and
     * <b>must agree on every one of them</b> — disagree and a fish is drawn twice or not at all — so
     * they share this, quota counters included. One instance per tank; a fresh one per member here.
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
}
