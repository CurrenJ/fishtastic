package grill24.fishtastic.fishtank;

import grill24.fishtastic.FishtasticBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * A structure's solid parts as the boxes fish steer round (docs/fish-shelters.md §12.3), in its
 * own build grid, unrotated — the Minecraft half of {@link ObstacleGeometry}: it reads each part's
 * block shape and whether the part is {@link FishtasticBlockTags#SOFT_COSMETIC soft}.
 *
 * <p>Pockets no fish could get into are filled, so a fish is never scattered into a sealed
 * hollow or caught in a nook it cannot find its way out of. A shelter's hull is then carved out. The shelter carries that box itself, and a fish using the
 * shelter ignores it; every part outside the hull stays solid to every fish.
 */
public final class CosmeticObstacles {

    private CosmeticObstacles() {}

    private static final Map<CosmeticStructure, List<ObstacleGeometry.Box>> CACHE =
            Collections.synchronizedMap(new IdentityHashMap<>());

    /**
     * Open water a fish's centre must have all round it to get somewhere, blocks: a pocket inside a
     * structure whose way out is narrower than twice this is filled (see
     * {@link ObstacleGeometry#fillPockets}). About the half-margin fish keep from a box.
     */
    static final float POCKET_CLEARANCE = 0.06f;

    /** {@link #POCKET_CLEARANCE} in this structure's voxels, at least one. */
    static int clearanceVoxels(CosmeticStructure structure) {
        return Math.max(1, Math.round(POCKET_CLEARANCE * ObstacleGeometry.RES / structure.scale()));
    }

    /** {@link #derive} with the soft tag, cached per structure instance (a datapack reload makes new ones). */
    public static List<ObstacleGeometry.Box> of(CosmeticStructure structure) {
        if (CACHE.size() > 256) CACHE.clear();
        return CACHE.computeIfAbsent(structure, s -> derive(s, state -> state.is(FishtasticBlockTags.SOFT_COSMETIC)));
    }

    /**
     * Drops the cache — on a tag reload, since which parts are soft is a tag. A structure keeps its
     * identity across a {@code /reload} (only a new login makes new ones), so nothing else would.
     * Tanks pick the change up the next time their cosmetics change.
     */
    public static void clearCache() {
        CACHE.clear();
    }

    /** Whether any part of the structure is solid to fish. */
    public static boolean hasSolid(CosmeticStructure structure) {
        return !of(structure).isEmpty();
    }

    public static List<ObstacleGeometry.Box> derive(CosmeticStructure structure, Predicate<BlockState> soft) {
        // Build units per stored offset, as in CosmeticStructure.partCells, but not rounded: a span's
        // offsets are build blocks already; a floor structure's horizontal ones were compressed by
        // scale / CELL_WIDTH at capture. Either way the stored offset is the block's horizontal
        // centre (a span's build cell 0 is centred on its build origin) and its bottom.
        float xzRatio = structure.span().isPresent() ? 1f : structure.scale() / (float) CosmeticGridCell.CELL_WIDTH;
        List<ObstacleGeometry.Part> parts = new ArrayList<>(structure.parts().size());
        for (CosmeticStructure.StructurePart part : structure.parts()) {
            BlockState state = part.state();
            List<float[]> shape = new ArrayList<>();
            // The outline shape, which is what the model draws: a collision shape can fall back to
            // a full cube, or to nothing for blocks a player walks through.
            for (AABB a : state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs()) {
                shape.add(new float[]{(float) a.minX, (float) a.minY, (float) a.minZ,
                        (float) a.maxX, (float) a.maxY, (float) a.maxZ});
            }
            parts.add(new ObstacleGeometry.Part(part.offsetX() / xzRatio - 0.5f, part.offsetY(),
                    part.offsetZ() / xzRatio - 0.5f, shape, soft.test(state)));
        }
        ObstacleGeometry.Voxels voxels = ObstacleGeometry.fillPockets(ObstacleGeometry.rasterise(parts), clearanceVoxels(structure));
        Optional<ShelterGeometry.Shape> shelter = structure.shelterShape();
        shelter.ifPresent(shape -> ObstacleGeometry.carve(voxels, shape.hullMin(), shape.hullMax()));
        return List.copyOf(ObstacleGeometry.merge(voxels));
    }
}
