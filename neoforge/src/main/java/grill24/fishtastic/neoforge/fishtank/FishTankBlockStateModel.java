package grill24.fishtastic.neoforge.fishtank;

import com.mojang.serialization.MapCodec;
import grill24.fishtastic.Fishtastic;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;

import static grill24.fishtastic.util.Utility.ft;

/**
 * Custom unbaked block state model for the fish tank.
 * <p>
 * Registered via {@code RegisterBlockStateModels} event and referenced in the
 * blockstate JSON with {@code "type": "fishtastic:fish_tank"}.
 * <p>
 * During baking, resolves all 192 base sub-models (64 frame + 64 sand + 64 glass)
 * and produces a {@link FishTankBakedModel} (a {@code DynamicBlockStateModel}) that
 * dynamically composites them at chunk-meshing time based on per-block-entity
 * {@link FishTankModelData}.
 */
public class FishTankBlockStateModel implements CustomUnbakedBlockStateModel {
    public static final MapCodec<FishTankBlockStateModel> CODEC =
            MapCodec.unit(FishTankBlockStateModel::new);

    private static final String FRAME_PREFIX = "block/fishtankbase/fish_tank_frame_";
    private static final String SAND_PREFIX  = "block/fishtankbase/fish_tank_sand_";
    private static final String GLASS_PREFIX = "block/fishtankbase/fish_tank_glass_";
    private static final int PERMUTATION_COUNT = 64;

    @Override
    public MapCodec<? extends CustomUnbakedBlockStateModel> codec() {
        return CODEC;
    }

    @Override
    public void resolveDependencies(Resolver resolver) {
        // Mark all 192 base models as dependencies so the model bakery loads them.
        for (int i = 0; i < PERMUTATION_COUNT; i++) {
            resolver.markDependency(ft(FRAME_PREFIX + i));
            resolver.markDependency(ft(SAND_PREFIX + i));
            resolver.markDependency(ft(GLASS_PREFIX + i));
        }
    }

    @Override
    public BlockStateModel bake(ModelBaker baker) {
        // Resolve all 192 base models from the bakery.
        ResolvedModel[] frameModels = new ResolvedModel[PERMUTATION_COUNT];
        ResolvedModel[] sandModels  = new ResolvedModel[PERMUTATION_COUNT];
        ResolvedModel[] glassModels = new ResolvedModel[PERMUTATION_COUNT];

        for (int i = 0; i < PERMUTATION_COUNT; i++) {
            frameModels[i] = baker.getModel(ft(FRAME_PREFIX + i));
            sandModels[i]  = baker.getModel(ft(SAND_PREFIX + i));
            glassModels[i] = baker.getModel(ft(GLASS_PREFIX + i));
        }

        Fishtastic.LOGGER.info("Fish Tank block state model baked — {} sub-models resolved.",
                PERMUTATION_COUNT * 3);

        return new FishTankBakedModel(baker, frameModels, sandModels, glassModels);
    }
}


