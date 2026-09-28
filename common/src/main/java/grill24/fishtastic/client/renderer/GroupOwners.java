package grill24.fishtastic.client.renderer;

import net.minecraft.core.BlockPos;

import java.util.Arrays;
import java.util.List;

/**
 * Which member tank owns each cell of a multi-tank group's bounding box — the partition that lets
 * every member draw the group fish that are inside it, instead of one anchor drawing them all.
 *
 * <p>A block entity renderer only runs while its own chunk section is on screen (see
 * {@link TankGroupFlock}), so an anchor that draws fish belonging to other tanks loses them the
 * moment the anchor block itself leaves the frustum. Owning each fish from the tank it is in makes
 * that impossible by construction: the fish and its renderer stand in the same place, so vanilla's
 * own section culling decides the two together.
 *
 * <p>The grid is over {@code group.occupancy()}'s dimensions, anchored at the group's {@code min}
 * corner. A member's own cell maps to that member's index in {@code group.members()}; every other
 * cell is filled by a breadth-first pass outward from the member cells, so it is owned by the
 * <b>nearest</b> member. That keeps the property the fix rests on — an owner is never more than a
 * cell or two from the fish it owns — and makes the lookup total: there is no "no owner" case to
 * handle, so a fish can never end up drawn by nobody.
 *
 * <p>Deliberately free of level and client types: positions in, owner index out, so the partition
 * can be tested headlessly (see {@code GroupOwnersTest}).
 */
final class GroupOwners {

    private final BlockPos min;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    /** Flat {@code [(x * sizeY + y) * sizeZ + z]} → member index, or {@code -1} where unclaimed. */
    private final int[] owner;

    private GroupOwners(BlockPos min, int sizeX, int sizeY, int sizeZ, int[] owner) {
        this.min = min;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.owner = owner;
    }

    /**
     * @param members group members in {@code group.members()} order — a member's index here is the
     *                owner index this grid reports, and the index a bucket is addressed by
     * @param min     the group bounding box's minimum corner ({@code group.min()})
     */
    static GroupOwners build(List<BlockPos> members, BlockPos min, int sizeX, int sizeY, int sizeZ) {
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            return new GroupOwners(min, Math.max(sizeX, 0), Math.max(sizeY, 0), Math.max(sizeZ, 0), new int[0]);
        }

        int volume = sizeX * sizeY * sizeZ;
        int[] owner = new int[volume];
        Arrays.fill(owner, -1);

        // One BFS over the box with every member cell as a source, in member order. A cell is claimed
        // the first time any source reaches it; because sources all start in the queue at distance 0,
        // the first to reach a cell is a nearest one, and member order breaks ties deterministically.
        int[] queue = new int[volume];
        int head = 0;
        int tail = 0;
        for (int i = 0; i < members.size(); i++) {
            BlockPos p = members.get(i);
            int x = p.getX() - min.getX();
            int y = p.getY() - min.getY();
            int z = p.getZ() - min.getZ();
            if (x < 0 || x >= sizeX || y < 0 || y >= sizeY || z < 0 || z >= sizeZ) continue;
            int idx = flat(x, y, z, sizeY, sizeZ);
            if (owner[idx] != -1) continue; // two members on one cell cannot happen; first one wins if it does
            owner[idx] = i;
            queue[tail++] = idx;
        }

        int layer = sizeY * sizeZ;
        while (head < tail) {
            int idx = queue[head++];
            int claim = owner[idx];
            int x = idx / layer;
            int withinLayer = idx % layer;
            int y = withinLayer / sizeZ;
            int z = withinLayer % sizeZ;

            if (x > 0 && owner[idx - layer] == -1) { owner[idx - layer] = claim; queue[tail++] = idx - layer; }
            if (x < sizeX - 1 && owner[idx + layer] == -1) { owner[idx + layer] = claim; queue[tail++] = idx + layer; }
            if (y > 0 && owner[idx - sizeZ] == -1) { owner[idx - sizeZ] = claim; queue[tail++] = idx - sizeZ; }
            if (y < sizeY - 1 && owner[idx + sizeZ] == -1) { owner[idx + sizeZ] = claim; queue[tail++] = idx + sizeZ; }
            if (z > 0 && owner[idx - 1] == -1) { owner[idx - 1] = claim; queue[tail++] = idx - 1; }
            if (z < sizeZ - 1 && owner[idx + 1] == -1) { owner[idx + 1] = claim; queue[tail++] = idx + 1; }
        }

        return new GroupOwners(min, sizeX, sizeY, sizeZ, owner);
    }

    /**
     * Member index owning the cell containing this world position. The cell is clamped into the
     * group's bounding box, so a straggler just outside it is owned by the nearest edge member
     * rather than by nobody.
     *
     * @return a valid member index, or {@code -1} only for a degenerate empty group
     */
    int ownerOf(float worldX, float worldY, float worldZ) {
        if (owner.length == 0) return -1;
        int x = clamp((int) Math.floor(worldX) - min.getX(), sizeX);
        int y = clamp((int) Math.floor(worldY) - min.getY(), sizeY);
        int z = clamp((int) Math.floor(worldZ) - min.getZ(), sizeZ);
        return owner[flat(x, y, z, sizeY, sizeZ)];
    }

    private static int flat(int x, int y, int z, int sizeY, int sizeZ) {
        return (x * sizeY + y) * sizeZ + z;
    }

    private static int clamp(int v, int size) {
        return v < 0 ? 0 : Math.min(v, size - 1);
    }
}
