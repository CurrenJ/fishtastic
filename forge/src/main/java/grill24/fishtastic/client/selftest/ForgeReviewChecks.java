package grill24.fishtastic.client.selftest;

import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.client.compositemodel.TankCosmeticMesh;
import grill24.fishtastic.fishtank.FishTankCompositeModelData;
import grill24.fishtastic.fishtank.FishTankShape;
import grill24.fishtastic.fishtank.TankDiagonal;
import grill24.fishtastic.fishtank.TankEdgeDiagonal;
import grill24.fishtastic.forge.fishtank.FishTankBakedModel;
import grill24.fishtastic.forge.fishtank.FishTankModelData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dev-only black-box checks against the actual installed Forge tank model and ModelDataManager. */
public final class ForgeReviewChecks {
    private ForgeReviewChecks() {}

    public static Map<String, Boolean> check(Minecraft mc, BlockPos pos, boolean edited) {
        Map<String, Boolean> checks = new LinkedHashMap<>();
        var model = mc.getModelManager().getBlockStateModelSet()
                .get(FishtasticBlocks.FISH_TANK.value().defaultBlockState());
        checks.put("model_installed", model instanceof FishTankBakedModel);
        if (!(model instanceof FishTankBakedModel tank)) return checks;
        var live = mc.level.getModelDataManager().getAtOrEmpty(pos).get(FishTankModelData.DATA_PROPERTY);
        checks.put(edited ? "live_material_refresh" : "live_data_loaded",
                live != null && (!edited || live.frameBlock() == Blocks.DIAMOND_BLOCK));
        checks.put(edited ? "live_shape_refresh" : "live_shape_loaded",
                live != null && live.shape() == (edited ? FishTankShape.VITRINE : FishTankShape.STANDARD));
        if (edited) return checks;

        var defaults = FishTankCompositeModelData.DEFAULT;
        String standard = fingerprint(quads(tank, defaults));
        boolean allShapes = true, allPermutations = true, corners = true, edges = true;
        for (FishTankShape shape : FishTankShape.values()) {
            var closed = data(shape, 0, false, false, TankCosmeticMesh.Snapshot.EMPTY);
            if (shape != FishTankShape.STANDARD) allShapes &= !fingerprint(quads(tank, closed)).equals(standard);
            for (int permutation = 0; permutation < 64; permutation++) {
                for (BakedQuad quad : quads(tank, data(shape, permutation, false, false, TankCosmeticMesh.Snapshot.EMPTY))) {
                    allPermutations &= !quad.materialInfo().sprite().contents().name().toString().contains("missingno");
                }
            }
            int corner = (1 << Direction.NORTH.ordinal()) | (1 << Direction.EAST.ordinal());
            if (shape.hasDiagonalCornerFragments()) {
                corners &= !fingerprint(quads(tank, data(shape, corner, false, false, TankCosmeticMesh.Snapshot.EMPTY)))
                        .equals(fingerprint(quads(tank, data(shape, corner, true, false, TankCosmeticMesh.Snapshot.EMPTY))));
            }
            int edge = (1 << Direction.NORTH.ordinal()) | (1 << Direction.UP.ordinal());
            if (shape.hasEdgeDiagonalFragments()) {
                edges &= !fingerprint(quads(tank, data(shape, edge, false, false, TankCosmeticMesh.Snapshot.EMPTY)))
                        .equals(fingerprint(quads(tank, data(shape, edge, false, true, TankCosmeticMesh.Snapshot.EMPTY))));
            }
        }
        checks.put("all_shapes_distinct_from_standard", allShapes);
        checks.put("all_shapes_64_connections_no_missing_models", allPermutations);
        checks.put("corner_fragment_changes", corners);
        checks.put("edge_fragment_changes", edges);

        var leaves = new TankCosmeticMesh.Snapshot(List.of(new TankCosmeticMesh.Piece(
                Blocks.OAK_LEAVES.defaultBlockState(), new Matrix4f(), 0)));
        var stone = new TankCosmeticMesh.Snapshot(List.of(new TankCosmeticMesh.Piece(
                Blocks.STONE.defaultBlockState(), new Matrix4f(), 0)));
        var a = data(FishTankShape.STANDARD, 0, false, false, leaves);
        var b = data(FishTankShape.STANDARD, 0, false, false, stone);
        var first = quads(tank, a);
        String before = fingerprint(first);
        String other = fingerprint(quads(tank, b));
        checks.put("different_cosmetics_different_meshes", !before.equals(other));
        checks.put("cosmetic_mesh_survives_interleaving", before.equals(fingerprint(quads(tank, a))));
        checks.put("cosmetic_removal_rebuilds_mesh", !before.equals(fingerprint(quads(tank, defaults))));
        int expected = mc.getBlockColors().getTintSource(Blocks.OAK_LEAVES.defaultBlockState(), 0)
                .colorInWorld(Blocks.OAK_LEAVES.defaultBlockState(), mc.level, pos);
        var tinted = first.stream().filter(q -> q.materialInfo().isTinted()).toList();
        checks.put("foliage_quads_retained", !tinted.isEmpty());
        checks.put("foliage_original_biome_color", !tinted.isEmpty() && tinted.stream().allMatch(q -> {
            var source = mc.getBlockColors().getTintSource(FishtasticBlocks.FISH_TANK.value().defaultBlockState(), q.materialInfo().tintIndex());
            return source != null && source.colorInWorld(FishtasticBlocks.FISH_TANK.value().defaultBlockState(), mc.level, pos) == expected;
        }));
        return checks;
    }

    private static FishTankCompositeModelData data(FishTankShape shape, int permutation, boolean corner,
                                                   boolean edge, TankCosmeticMesh.Snapshot cosmetics) {
        var d = FishTankCompositeModelData.DEFAULT;
        return new FishTankCompositeModelData(shape, d.frameBlock(), d.sandBlock(), d.glassBlock(),
                FishTankCompositeModelData.openFacesFromIndex(permutation),
                corner ? EnumSet.of(TankDiagonal.NORTHEAST) : EnumSet.noneOf(TankDiagonal.class),
                edge ? EnumSet.of(TankEdgeDiagonal.NORTH_UP) : EnumSet.noneOf(TankEdgeDiagonal.class), cosmetics);
    }

    private static List<BakedQuad> quads(FishTankBakedModel model, FishTankCompositeModelData data) {
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RandomSource.create(42), parts,
                ModelData.builder().with(FishTankModelData.DATA_PROPERTY, data).build());
        List<BakedQuad> quads = new ArrayList<>();
        for (var part : parts) {
            quads.addAll(part.getQuads(null));
            for (Direction face : Direction.values()) quads.addAll(part.getQuads(face));
        }
        return quads;
    }

    private static String fingerprint(List<BakedQuad> quads) {
        return quads.stream().map(q -> q.position0() + ":" + q.position1() + ":" + q.position2() + ":" + q.position3()
                + ":" + q.materialInfo().sprite().contents().name() + ":" + q.materialInfo().tintIndex())
                .sorted().toList().toString();
    }
}
