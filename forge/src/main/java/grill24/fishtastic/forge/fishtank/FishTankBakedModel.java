package grill24.fishtastic.forge.fishtank;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.client.compositemodel.CompositeTextureHelper;
import grill24.fishtastic.client.compositemodel.TankCosmeticMesh;
import grill24.fishtastic.client.renderer.TankInteriorLight;
import grill24.fishtastic.fishtank.FishTankCompositeModelData;
import grill24.fishtastic.fishtank.FishTankShape;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.extensions.IForgeBlockStateModel;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static grill24.fishtastic.util.Utility.ft;

/**
 * Minimal Forge 26.1.2 dynamic tank model. Uses Forge's ModelData extension point on a vanilla
 * BlockStateModel; the full shape-fragment port can grow from this seam.
 */
public final class FishTankBakedModel implements BlockStateModel, IForgeBlockStateModel {
    private static final int PERMUTATION_COUNT = 64;

    private final ModelBaker baker;
    private final ResolvedModel[] frameModels = new ResolvedModel[PERMUTATION_COUNT];
    private final ResolvedModel[] sandModels = new ResolvedModel[PERMUTATION_COUNT];
    private final ResolvedModel[] glassModels = new ResolvedModel[PERMUTATION_COUNT];

    private final Map<CacheKey, CachedModel> cache = new ConcurrentHashMap<>();
    private final BlockStateModelPart defaultPart;
    private final Material.Baked defaultParticle;
    private final int defaultFlags;
    private final Object bakeLock = new Object();

    private record CacheKey(FishTankShape shape, int permutation, Block frame, Block sand, Block glass,
                            int interiorLight) {}

    private record CachedModel(List<BlockStateModelPart> parts, Material.Baked particle, int flags) {}

    public FishTankBakedModel(ModelBaker baker) {
        this.baker = baker;
        for (int i = 0; i < PERMUTATION_COUNT; i++) {
            frameModels[i] = baker.getModel(ft("block/fishtankbase/fish_tank_frame_" + i));
            sandModels[i] = baker.getModel(ft("block/fishtankbase/fish_tank_sand_" + i));
            glassModels[i] = baker.getModel(ft("block/fishtankbase/fish_tank_glass_" + i));
        }

        CachedModel fallback = generate(FishTankCompositeModelData.DEFAULT);
        if (fallback != null) {
            defaultPart = fallback.parts().get(0);
            defaultParticle = fallback.particle();
            defaultFlags = fallback.flags();
        } else {
            Fishtastic.LOGGER.error("Fish Tank: default Forge composite failed to bake");
            defaultPart = baker.missingBlockModelPart();
            defaultParticle = frameModels[0].resolveParticleMaterial(
                    frameModels[0].getTopTextureSlots(), baker);
            defaultFlags = 0;
        }
    }

    @Override
    public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
        parts.add(defaultPart);
    }

    @Override
    public void collectParts(RandomSource random, List<BlockStateModelPart> parts, ModelData modelData) {
        FishTankCompositeModelData data = modelData.get(FishTankModelData.DATA_PROPERTY);
        if (data == null) data = FishTankCompositeModelData.DEFAULT;
        CachedModel model = cache.get(key(data));
        if (model == null) {
            synchronized (bakeLock) {
                model = cache.get(key(data));
                if (model == null) {
                    model = generate(data);
                    if (model != null) cache.put(key(data), model);
                }
            }
        }
        if (model == null) {
            parts.add(defaultPart);
        } else {
            parts.addAll(model.parts());
        }
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        return modelData;
    }

    @Override
    public Material.Baked particleMaterial() {
        return defaultParticle;
    }

    @Override
    public Material.Baked particleMaterial(ModelData modelData) {
        FishTankCompositeModelData data = modelData.get(FishTankModelData.DATA_PROPERTY);
        CachedModel model = data == null ? null : cache.get(key(data));
        return model == null ? defaultParticle : model.particle();
    }

    @Override
    public int materialFlags() {
        return defaultFlags;
    }

    private CacheKey key(FishTankCompositeModelData data) {
        return new CacheKey(data.shape(), data.getPermutationIndex(),
                data.frameBlock(), data.sandBlock(), data.glassBlock(),
                TankInteriorLight.level());
    }

    @Nullable
    private CachedModel generate(FishTankCompositeModelData data) {
        int perm = data.getPermutationIndex();
        ResolvedModel frameModel = frameModels[perm];
        ResolvedModel sandModel = sandModels[perm];
        ResolvedModel glassModel = glassModels[perm];

        Material frameTex = CompositeTextureHelper.resolveBlockTexture(
                data.frameBlock(), baker, BlockModelPathResolver.getModelLocations(data.frameBlock()));
        Material sandTex = CompositeTextureHelper.resolveBlockTexture(
                data.sandBlock(), baker, BlockModelPathResolver.getModelLocations(data.sandBlock()));
        Material glassTex = CompositeTextureHelper.resolveBlockTexture(
                data.glassBlock(), baker, BlockModelPathResolver.getModelLocations(data.glassBlock()));
        if (frameTex == null || sandTex == null || glassTex == null) return null;

        TextureSlots frameSlots = CompositeTextureHelper.overrideAllTexture(frameTex, frameModel);
        TextureSlots sandSlots = CompositeTextureHelper.overrideAllTexture(sandTex, sandModel);
        TextureSlots glassSlots = CompositeTextureHelper.overrideAllTexture(glassTex, glassModel);

        QuadCollection frame = bakeGeometry(frameModel, frameSlots);
        QuadCollection sand = bakeGeometry(sandModel, sandSlots);
        QuadCollection glass = bakeGeometry(glassModel, glassSlots);
        if (frame == null || sand == null || glass == null) return null;

        QuadCollection.Builder body = new QuadCollection.Builder();
        body.addAll(frame);
        body.addAll(withLightEmission(sand, TankInteriorLight.level()));
        body.addAll(withLightEmission(glass, TankInteriorLight.level()));

        TankCosmeticMesh.Baked cosmetics = TankCosmeticMesh.bake(data.cosmetics());
        if (!cosmetics.untinted().getAll().isEmpty()) {
            body.addAll(withLightEmission(cosmetics.untinted(), TankInteriorLight.level()));
        }
        // Forge 26.1.2's vanilla BakedQuad has no per-vertex colour channel, so tinted cosmetics are
        // deferred until the Forge tint-source adapter is proven.

        QuadCollection composite = body.build();
        Material.Baked particle = frameModel.resolveParticleMaterial(frameSlots, baker);
        BlockStateModelPart part = new SimpleModelWrapper(composite, false, particle);
        return new CachedModel(List.of(part), particle,
                frame.materialFlags() | sand.materialFlags() | glass.materialFlags());
    }

    @Nullable
    private QuadCollection bakeGeometry(ResolvedModel model, TextureSlots slots) {
        try {
            return model.getTopGeometry().bake(slots, baker, BlockModelRotation.IDENTITY, model);
        } catch (Exception e) {
            Fishtastic.LOGGER.error("Fish Tank: failed to bake geometry {}", model.debugName(), e);
            return null;
        }
    }

    private static QuadCollection withLightEmission(QuadCollection quads, int level) {
        if (level <= 0) return quads;
        QuadCollection.Builder builder = new QuadCollection.Builder();
        for (BakedQuad quad : quads.getQuads(null)) builder.addUnculledFace(withLightEmission(quad, level));
        for (Direction direction : Direction.values()) {
            for (BakedQuad quad : quads.getQuads(direction)) {
                builder.addCulledFace(direction, withLightEmission(quad, level));
            }
        }
        return builder.build();
    }

    private static BakedQuad withLightEmission(BakedQuad quad, int level) {
        BakedQuad.MaterialInfo m = quad.materialInfo();
        if (m.lightEmission() >= level) return quad;
        return new BakedQuad(quad.position0(), quad.position1(), quad.position2(), quad.position3(),
                quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(), quad.direction(),
                new BakedQuad.MaterialInfo(m.sprite(), m.layer(), m.itemRenderType(), m.tintIndex(),
                        m.shade(), level));
    }
}
