package grill24.fishtastic.client.util;

import grill24.fishsim.domain.VoxelDomain;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.client.renderer.TankGroupFlock;
import grill24.fishtastic.fishtank.TankGroups;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/**
 * One discovered group per structure, shared by every member — the §5.1 and §5.3(a) fixes from
 * docs/fish-tank-group-scaling.md.
 *
 * <p>Before this, {@code TankFlockAdapter.sync} ran {@code TankGroups.of} on every visible tank on
 * every frame: an allocating flood fill per block entity, so O(G) work × G tanks = O(G²) per
 * frame, measured at 4 ms/frame at a cap of only 64. Group membership changes when a tank is
 * placed, broken, or its open faces move — never per frame — so the walk belongs behind a cache
 * keyed on {@link TankGroups#membershipEpoch()}.
 *
 * <p>It is also the home of each group's shared {@link VoxelDomain} and of the group's shared flock
 * runtime ({@link TankGroupFlock}) — one aquarium's simulation and the partition deciding which
 * member draws which of its fish. Both belong to the group rather than to any one tank, and both
 * outlive a membership epoch while their group keeps a familiar member.
 *
 * <p>The cache also holds each group's {@link VoxelDomain}. Building one is superlinear in the
 * bounding-box volume (§3.4: 26 ms for a 9-cube, 109 ms for a 12-cube), and {@code sync} used to
 * rebuild it whenever a tank's <em>contents</em> changed — so adding a single fish rebuilt the
 * whole distance field even though the occupancy grid had not moved. The field depends only on
 * occupancy, so it lives and dies with the membership epoch too.
 *
 * <p><b>Why the walk claims positions.</b> A structure larger than
 * {@link TankGroups#RENDER_MAX_GROUP_SIZE} cannot be one group, and the tanks the cap truncated
 * away will start walks of their own. Letting those overlap would give two anchors that each
 * simulate and render the same fish, so a walk treats already-claimed positions as absent and the
 * groups come out as a partition. Which partition depends on which member is rendered first, and
 * it then stays put for the life of the epoch.
 *
 * <p>Client-only by use, not by type: keyed on {@link BlockPos} with no level identity, exactly
 * like {@link ClientTankFlocks}, so {@link #clear()} must run on world join/disconnect.
 */
public final class ClientTankGroups {

    /**
     * A discovered group, the distance field built from its occupancy, and the group's shared flock
     * runtime ({@code null} for a lone tank, which never touches a voxel domain or a group engine).
     */
    public record Entry(TankGroups.Group group, VoxelDomain domain, TankGroupFlock flock) {}

    private static final Map<BlockPos, Entry> BY_MEMBER = new HashMap<>();
    private static int cachedEpoch = Integer.MIN_VALUE;
    /**
     * Group runtimes carried across a membership epoch, keyed by <em>every</em> member position of
     * the group they came from, so the next group to claim any of those positions inherits the
     * engine — and with it every fish's position and animation phase — instead of re-seeding the
     * shoal because a tank was placed at the far end of the build. Claims remove their entry, so two
     * groups split out of one cannot both take it; whatever is left when the epoch's discovery
     * settles is dropped with the map.
     */
    private static final Map<BlockPos, TankGroupFlock> ADOPTABLE = new HashMap<>();

    private ClientTankGroups() {}

    /**
     * The group containing this tank, discovering it (and its domain) only on a cache miss.
     * Every member of a discovered group is registered, so one walk serves the whole structure.
     */
    public static Entry get(FishTankBlockEntity be, Level level) {
        int epoch = TankGroups.membershipEpoch();
        if (epoch != cachedEpoch) {
            // Every member position of every old group points at that group's runtime, so the next
            // group to discover any of those positions inherits it (see ADOPTABLE).
            ADOPTABLE.clear();
            for (Entry old : BY_MEMBER.values()) {
                if (old.flock() == null) continue;
                for (BlockPos member : old.group().members()) {
                    ADOPTABLE.putIfAbsent(member.immutable(), old.flock());
                }
            }
            BY_MEMBER.clear();
            cachedEpoch = epoch;
        }

        BlockPos key = be.getBlockPos();
        Entry cached = BY_MEMBER.get(key);
        if (cached != null) return cached;

        // The live key set, not a copy: TankGroups.of only reads it, and the map is not touched
        // until the walk has finished. Copying here would make N separate structures coming into
        // view cost O(N^2) in cache misses alone.
        TankGroups.Group group =
                TankGroups.of(be, level, TankGroups.RENDER_MAX_GROUP_SIZE, BY_MEMBER.keySet());
        // A lone tank takes the legacy single-tank Box path and never touches a voxel domain, so
        // building one for it would be pure waste.
        // The floor carries every member's cosmetics, so a crawler walks around them; it is
        // refreshed in place by the group's flock when they change (see TankGroupFlock), because
        // cosmetics move without membership moving and rebuilding the domain for that would cost
        // the whole distance field.
        VoxelDomain domain = group.isMultiTank()
                ? new VoxelDomain(group.occupancy(), VoxelDomain.DEFAULT_INSET,
                        TankFloors.GROUP_SURFACE_OFFSET, TankFloors.groupBlockedCells(group, level),
                        group.blockedX(), group.blockedY(), group.blockedZ())
                : null;
        // A lone tank keeps the legacy path with no group runtime at all. A group inherits the
        // runtime of any group it shares a member with; claiming is destructive so a split gives the
        // engine to exactly one of the halves, and the other starts a fresh shoal.
        TankGroupFlock flock = null;
        if (group.isMultiTank()) {
            flock = new TankGroupFlock();
            for (BlockPos member : group.members()) {
                TankGroupFlock inherited = ADOPTABLE.remove(member);
                if (inherited != null) {
                    flock = inherited;
                    break;
                }
            }
        }
        Entry entry = new Entry(group, domain, flock);
        for (BlockPos member : group.members()) {
            BY_MEMBER.putIfAbsent(member.immutable(), entry);
        }
        return entry;
    }

    /** Drops every cached group — call on world join/disconnect alongside {@link ClientTankFlocks#clear()}. */
    public static void clear() {
        BY_MEMBER.clear();
        ADOPTABLE.clear();
        cachedEpoch = Integer.MIN_VALUE;
    }
}
