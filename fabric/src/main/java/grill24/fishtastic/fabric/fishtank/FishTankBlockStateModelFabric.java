package grill24.fishtastic.fabric.fishtank;

import com.mojang.serialization.MapCodec;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.fishtank.FishTankShape;
import net.fabricmc.fabric.api.client.model.loading.v1.CustomUnbakedBlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.resources.Identifier;

import java.util.EnumMap;
import java.util.Map;

import static grill24.fishtastic.util.Utility.ft;

/**
 * Custom unbaked block state model for the fish tank (Fabric).
 * <p>
 * Registered via {@link CustomUnbakedBlockStateModel#register} and referenced in the
 * blockstate JSON with {@code "type": "fishtastic:fish_tank"}.
 * <p>
 * During baking, resolves all 192 base sub-models (64 frame + 64 sand + 64 glass) per registered
 * {@link FishTankShape} and produces a {@link FishTankBakedModelFabric} that dynamically
 * composites them at chunk-meshing time based on per-block-entity data.
 */
public class FishTankBlockStateModelFabric implements CustomUnbakedBlockStateModel {

    public static final MapCodec<FishTankBlockStateModelFabric> CODEC =
            MapCodec.unit(FishTankBlockStateModelFabric::new);

    private static final int PERMUTATION_COUNT = 64;

    @Override
    public MapCodec<? extends CustomUnbakedBlockStateModel> codec() {
        return CODEC;
    }

    @Override
    public void resolveDependencies(ResolvableModel.Resolver resolver) {
        for (FishTankShape shape : FishTankShape.values()) {
            for (int i = 0; i < PERMUTATION_COUNT; i++) {
                resolver.markDependency(modelLocation(shape, "frame", i));
                resolver.markDependency(modelLocation(shape, "sand", i));
                resolver.markDependency(modelLocation(shape, "glass", i));
            }
        }
    }

    @Override
    public BlockStateModel bake(ModelBaker baker) {
        Map<FishTankShape, ResolvedModel[]> frameModels = new EnumMap<>(FishTankShape.class);
        Map<FishTankShape, ResolvedModel[]> sandModels  = new EnumMap<>(FishTankShape.class);
        Map<FishTankShape, ResolvedModel[]> glassModels = new EnumMap<>(FishTankShape.class);

        for (FishTankShape shape : FishTankShape.values()) {
            ResolvedModel[] frame = new ResolvedModel[PERMUTATION_COUNT];
            ResolvedModel[] sand  = new ResolvedModel[PERMUTATION_COUNT];
            ResolvedModel[] glass = new ResolvedModel[PERMUTATION_COUNT];
            for (int i = 0; i < PERMUTATION_COUNT; i++) {
                frame[i] = baker.getModel(modelLocation(shape, "frame", i));
                sand[i]  = baker.getModel(modelLocation(shape, "sand", i));
                glass[i] = baker.getModel(modelLocation(shape, "glass", i));
            }
            frameModels.put(shape, frame);
            sandModels.put(shape, sand);
            glassModels.put(shape, glass);
        }

        Fishtastic.LOGGER.info("Fish Tank block state model baked (Fabric) — {} sub-models resolved across {} shape(s).",
                PERMUTATION_COUNT * 3 * FishTankShape.values().length, FishTankShape.values().length);

        return new FishTankBakedModelFabric(baker, frameModels, sandModels, glassModels);
    }

    private static Identifier modelLocation(FishTankShape shape, String part, int permutation) {
        return ft("block/" + shape.modelPathPrefix() + "/fish_tank_" + part + "_" + permutation);
    }
}
