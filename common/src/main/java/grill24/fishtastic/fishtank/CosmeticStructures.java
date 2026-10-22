package grill24.fishtastic.fishtank;

import grill24.fishtastic.blockentity.FishTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rotation helpers for {@link CosmeticStructure} footprint cells and part offsets, all pivoting
 * around the structure's local origin (its anchor cell, {@code (0,0,0)}).
 *
 * <p>A floor structure's footprint may run off its anchor tank's grid onto a neighbour's, across
 * an open seam: footprint cells are counted on one grid continuing tank to tank, so cell
 * {@code GRID_SIZE} east of the anchor tank's cell 0 is the east neighbour's cell 0. The anchor
 * tank stores the structure; each neighbour it reaches records the cells it covers there as
 * {@link FishTankBlockEntity.StructureLink}s.
 */
public final class CosmeticStructures {
    private CosmeticStructures() {}

    /** How far, in tanks, a floor structure's footprint can reach from its anchor tank. */
    public static final int MAX_FLOOR_REACH = 1;

    /**
     * Rotates a footprint cell offset around the local origin. Delegates to vanilla's own
     * structure-rotation math ({@link StructureTemplate#transform}) rather than a second hand-rolled
     * formula, with no mirroring and a zero pivot.
     */
    public static CosmeticStructure.GridOffset rotateFootprintCell(Rotation rotation, CosmeticStructure.GridOffset cell) {
        BlockPos rotated = StructureTemplate.transform(new BlockPos(cell.dx(), 0, cell.dz()), Mirror.NONE, rotation, BlockPos.ZERO);
        return new CosmeticStructure.GridOffset(rotated.getX(), rotated.getZ());
    }

    /**
     * Rotates a continuous (x, z) part offset around the local origin. {@link StructureTemplate}'s own
     * {@code Vec3} transform bakes in a +1 term meant for in-block entity positions ([0,1) space), which
     * doesn't apply to plain center-relative float offsets — so this mirrors the same rotation directions
     * as {@link #rotateFootprintCell} by hand instead.
     */
    public static float[] rotateOffset(Rotation rotation, float x, float z) {
        return switch (rotation) {
            case CLOCKWISE_90 -> new float[]{-z, x};
            case CLOCKWISE_180 -> new float[]{-x, -z};
            case COUNTERCLOCKWISE_90 -> new float[]{z, -x};
            default -> new float[]{x, z};
        };
    }

    /** The tank (offset from the anchor tank, y always 0) holding the cell {@code offset} away from {@code anchor}. */
    public static BlockPos tankOf(CosmeticGridCell anchor, CosmeticStructure.GridOffset offset) {
        return new BlockPos(Math.floorDiv(anchor.gridX() + offset.dx(), CosmeticGridCell.GRID_SIZE), 0,
                Math.floorDiv(anchor.gridZ() + offset.dz(), CosmeticGridCell.GRID_SIZE));
    }

    /** The cell, within its own tank (see {@link #tankOf}), {@code offset} away from {@code anchor}. */
    public static CosmeticGridCell cellOf(CosmeticGridCell anchor, CosmeticStructure.GridOffset offset) {
        return new CosmeticGridCell(Math.floorMod(anchor.gridX() + offset.dx(), CosmeticGridCell.GRID_SIZE),
                Math.floorMod(anchor.gridZ() + offset.dz(), CosmeticGridCell.GRID_SIZE));
    }

    /**
     * A floor structure's occupied cells ({@link CosmeticStructure#occupied}), rotated and laid
     * from {@code anchor}, grouped by the tank they fall in (offset from the anchor tank).
     */
    public static Map<BlockPos, List<CosmeticGridCell>> footprintByTank(CosmeticStructure structure, Rotation rotation,
                                                                       CosmeticGridCell anchor) {
        Map<BlockPos, List<CosmeticGridCell>> out = new LinkedHashMap<>();
        for (CosmeticStructure.GridOffset offset : structure.occupied()) {
            CosmeticStructure.GridOffset rotated = rotateFootprintCell(rotation, offset);
            out.computeIfAbsent(tankOf(anchor, rotated), k -> new ArrayList<>()).add(cellOf(anchor, rotated));
        }
        return out;
    }

    /**
     * Removes every link neighbouring tanks hold to the floor structure anchored at {@code anchor}
     * in {@code owner}. Looks at every tank in reach rather than re-deriving the footprint, so it
     * still cleans up after a structure whose definition a datapack has since dropped.
     */
    public static void clearNeighbourLinks(Level level, FishTankBlockEntity owner, CosmeticGridCell anchor) {
        BlockPos pos = owner.getBlockPos();
        for (int dx = -MAX_FLOOR_REACH; dx <= MAX_FLOOR_REACH; dx++) {
            for (int dz = -MAX_FLOOR_REACH; dz <= MAX_FLOOR_REACH; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (level.getBlockEntity(pos.offset(dx, 0, dz)) instanceof FishTankBlockEntity tank
                        && !tank.getStructureLinks().isEmpty()) {
                    tank.clearStructureLinks(new FishTankBlockEntity.StructureLink(new BlockPos(-dx, 0, -dz), anchor));
                }
            }
        }
    }
}
