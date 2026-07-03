package grill24.fishtastic.fishtank;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Rotation helpers for {@link CosmeticStructure} footprint cells and part offsets, all pivoting
 * around the structure's local origin (its anchor cell, {@code (0,0,0)}).
 */
public final class CosmeticStructures {
    private CosmeticStructures() {}

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
}
