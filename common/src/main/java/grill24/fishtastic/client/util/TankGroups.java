package grill24.fishtastic.client.util;

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

/**
 * Client-side discovery of connected fish tank groups from the existing {@code openFaces}
 * adjacency (docs/fish-sim-engine-handoff.md Task 9). Preview-only: the server-side lock model is
 * a separate workstream — this just asks "which tanks does this one currently share water with",
 * flood-filling through faces both sides agree are open.
 */
public final class TankGroups {

    /** Safety cap on flood-fill size — matches the bubble-column walk's paranoia bound. */
    private static final int MAX_GROUP_SIZE = 64;

    private TankGroups() {}

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

    /** Flood-fills the connected group containing this tank. A lone tank yields a 1-member group. */
    public static Group of(FishTankBlockEntity start, Level level) {
        BlockPos startPos = start.getBlockPos();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<FishTankBlockEntity> frontier = new ArrayDeque<>();
        visited.add(startPos);
        frontier.add(start);

        while (!frontier.isEmpty() && visited.size() < MAX_GROUP_SIZE) {
            FishTankBlockEntity tank = frontier.poll();
            Set<Direction> open = tank.getOpenFaces();
            for (Direction dir : open) {
                BlockPos next = tank.getBlockPos().relative(dir);
                if (visited.contains(next)) continue;
                if (level.getBlockEntity(next) instanceof FishTankBlockEntity neighbor
                        && neighbor.getOpenFaces().contains(dir.getOpposite())) {
                    visited.add(next);
                    frontier.add(neighbor);
                }
            }
        }

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
