package grill24.fishtastic.fishtank;

import grill24.fishtastic.blockentity.FishTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Discovery of connected fish tank groups from the existing {@code openFaces} adjacency
 * (docs/fish-sim-engine-handoff.md Task 9). Works against any {@link Level} (client or server) —
 * flood-fills through faces both sides agree are open.
 */
public final class TankGroups {

    /**
     * Safety cap for the swarm renderer's live simulation — bounds how large a connected
     * structure the client will simulate as one shoal.
     *
     * <p>Raised from 64 once the three costs in docs/fish-tank-group-scaling.md were fixed: group
     * discovery is cached per structure rather than re-flood-filled per block entity per frame
     * (§5.1), the voxel distance field is cached alongside it instead of being rebuilt whenever a
     * player adds a fish (§5.3a), and {@code FlockEngine}'s two brute-force passes now run through
     * a uniform-grid spatial index, which took the step from O(n²) to O(n) (§5.2).
     *
     * <p><b>This number is not what bounds simulation cost — {@link #RENDER_MAX_GROUP_FISH} is.</b>
     * Every measured cost scales with fish, and a tank holds {@code CONTAINER_SIZE} of them, so a
     * cap on tanks alone bounds nothing (§4). Raise the two together or not at all.
     *
     * <p>Never use this for a gameplay query (GUI listing, capacity fallback, removal validation):
     * those must see the *same* full group regardless of which member the query started from, and
     * any cap makes the visited subset anchor-dependent — clicking two different segments of an
     * over-cap structure would silently compute two different member sets (see
     * docs/fish-tank-interaction-redesign.md).
     */
    public static final int RENDER_MAX_GROUP_SIZE = 512;

    /**
     * Safety cap for gameplay queries that must be anchor-independent. Large enough that no
     * realistically buildable structure ever hits it — it exists only to bound a pathological
     * flood-fill, not to trim normal results the way {@link #RENDER_MAX_GROUP_SIZE} does.
     */
    public static final int GAMEPLAY_MAX_GROUP_SIZE = 8192;

    /**
     * Ceiling on how many fish one group simulates and renders at once — the bound that actually
     * protects the frame (docs/fish-tank-group-scaling.md §5.4).
     *
     * <p>Measured against the post-§5.2 engine on the development machine, at the ≤4 fish/block
     * densities §4 calls plausible: 1.7 µs/fish in an 8-cube, 2.3 µs/fish in a 6-cube — so this
     * budget costs 1.7–2.4 ms/tick, which is the document's ≤2 ms/tick working budget.
     *
     * <p><b>Known worst case, recorded rather than designed around.</b> Density, not fish count,
     * drives the per-fish cost, and the spatial index cannot help when the neighbours really are
     * there: a small build with all 27 slots of every tank filled (27 fish/block) measures
     * 6.4 µs/fish, so this budget costs ~6.5 ms/tick there against ~2 ms in a normal build. That
     * is a buildable shape, just an extreme one; the honest fix is a density-aware budget or the
     * distance/frustum LOD in §5.4(2), neither of which this change attempts.
     *
     * <p>Fish beyond the budget keep their items and their place in the tank — they simply hover
     * in their own tank instead of joining the group shoal, exactly like a fish that fails the
     * size gate.
     *
     * <p>Absolute milliseconds are machine-specific; if this is ever retuned, retune it against a
     * measurement rather than by reasoning about the tank count.
     */
    public static final int RENDER_MAX_GROUP_FISH = 1024;

    /**
     * Bumped whenever any tank's group membership could have changed — a tank placed or broken, or
     * its open faces recomputed. Caches of discovered groups key on this and drop everything when
     * it moves; content changes (a fish added or taken) deliberately do <em>not</em> bump it,
     * which is what keeps a player adding a fish from rebuilding the whole distance field.
     */
    private static final AtomicInteger MEMBERSHIP_EPOCH = new AtomicInteger();

    private TankGroups() {}

    /** The current membership generation — see {@link #MEMBERSHIP_EPOCH}. */
    public static int membershipEpoch() {
        return MEMBERSHIP_EPOCH.get();
    }

    /** Invalidates every cached group. Called from the block entity's adjacency paths. */
    public static void bumpMembershipEpoch() {
        MEMBERSHIP_EPOCH.incrementAndGet();
    }

    /**
     * How many of one member tank's fish may join the group shoal, so that the whole group stays
     * inside {@link #RENDER_MAX_GROUP_FISH}.
     *
     * <p>A flat per-tank quota rather than "take fish until the budget runs out": every member has
     * to reach the same verdict about the same fish <em>independently</em>. The anchor decides who
     * swims, but each member separately decides who hovers in its own tank, and the two passes run
     * in different block entities with no shared state between them. A quota is a pure function of
     * the member count, so they cannot disagree — whereas a running total would depend on where in
     * the walk a tank sat, and any mismatch would leave a fish rendered twice or not at all (the
     * failure mode docs/fish-tank-group-scaling.md §1 describes).
     *
     * <p>It also spreads the shoal over the whole structure instead of filling the first tanks in
     * sort order and starving the far corner.
     *
     * <p>With the shipped constants this binds only past ~38 tanks
     * ({@code RENDER_MAX_GROUP_FISH / CONTAINER_SIZE}); every smaller group is unaffected.
     */
    public static int perTankFishQuota(int memberCount) {
        return memberCount <= 0 ? RENDER_MAX_GROUP_FISH : Math.max(1, RENDER_MAX_GROUP_FISH / memberCount);
    }

    /**
     * A connected tank group in a form the sim can consume directly.
     *
     * @param members   every member position, sorted (deterministic order = stable fish slots)
     * @param anchor    the elected simulation owner — the smallest member position
     * @param occupancy voxel grid over the members' bounding box, indexed [x][y][z] from {@code min}
     * @param min       world position of occupancy cell (0,0,0)
     */
    public record Group(List<BlockPos> members, BlockPos anchor, boolean[][][] occupancy, BlockPos min) {

        public boolean isMultiTank() {
            return members.size() > 1;
        }

        /**
         * Offset from the anchor's block origin to the group bounding box's center — the origin
         * of the engine's local (lateral, vertical, depth) frame, in the anchor's model space.
         */
        public float offsetX() { return min.getX() - anchor.getX() + occupancy.length / 2f; }
        public float offsetY() { return min.getY() - anchor.getY() + occupancy[0].length / 2f; }
        public float offsetZ() { return min.getZ() - anchor.getZ() + occupancy[0][0].length / 2f; }
    }

    /**
     * Flood-fills the connected group containing this tank. A lone tank yields a 1-member group.
     *
     * @param maxGroupSize safety cap on visited members — pass {@link #RENDER_MAX_GROUP_SIZE} for
     *                      the live swarm renderer, {@link #GAMEPLAY_MAX_GROUP_SIZE} for anything
     *                      that needs the same result no matter which member it started from.
     */
    public static Group of(FishTankBlockEntity start, Level level, int maxGroupSize) {
        return of(start, level, maxGroupSize, Set.of());
    }

    /**
     * {@link #of(FishTankBlockEntity, Level, int)} with positions the walk must treat as absent.
     *
     * <p>Used by the render-side group cache to partition an over-cap structure: once one group has
     * claimed a set of tanks, a later walk starting from a tank the cap truncated away must not
     * re-claim them, or two anchors would each simulate — and render — the same fish.
     */
    public static Group of(FishTankBlockEntity start, Level level, int maxGroupSize, Set<BlockPos> excluded) {
        BlockPos startPos = start.getBlockPos();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<FishTankBlockEntity> frontier = new ArrayDeque<>();
        visited.add(startPos);
        frontier.add(start);

        // The cap is tested before each *admission*, not before each poll. Testing it at the poll
        // let the node being polled expand all six of its faces afterward, so a capped walk
        // overshot by up to five members (docs/fish-tank-group-scaling.md §1).
        walk:
        while (!frontier.isEmpty()) {
            FishTankBlockEntity tank = frontier.poll();
            BlockPos tankPos = tank.getBlockPos();
            // Direction.values() is the same order EnumSet<Direction> iterates in, so the walk
            // visits faces exactly as before — and skips getOpenFaces()'s defensive EnumSet copy,
            // which at this cap would otherwise allocate once per member plus once per neighbour.
            for (Direction dir : Direction.values()) {
                if (!tank.isFaceOpen(dir)) continue;
                if (visited.size() >= maxGroupSize) break walk;
                BlockPos next = tankPos.relative(dir);
                if (visited.contains(next) || excluded.contains(next)) continue;
                if (level.getBlockEntity(next) instanceof FishTankBlockEntity neighbor
                        && neighbor.isFaceOpen(dir.getOpposite())) {
                    visited.add(next);
                    frontier.add(neighbor);
                }
            }
        }

        return ofMembers(visited);
    }

    /** Builds the group record (sorted members, anchor, occupancy grid) from a member set. */
    private static Group ofMembers(Set<BlockPos> visited) {
        List<BlockPos> members = new ArrayList<>(visited);
        Collections.sort(members);
        BlockPos anchor = members.getFirst();

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : members) {
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
        }
        BlockPos min = new BlockPos(minX, minY, minZ);
        boolean[][][] occupancy = new boolean[maxX - minX + 1][maxY - minY + 1][maxZ - minZ + 1];
        for (BlockPos p : members) {
            occupancy[p.getX() - minX][p.getY() - minY][p.getZ() - minZ] = true;
        }
        return new Group(members, anchor, occupancy, min);
    }
}
