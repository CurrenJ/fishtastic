package grill24.fishtastic.fabric.fishtank;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.client.compositemodel.BlockModelPathResolver;
import grill24.fishtastic.client.compositemodel.CompositeTextureHelper;
import grill24.fishtastic.fishtank.FishTankCompositeModelData;
import net.fabricmc.fabric.api.blockgetter.v2.FabricBlockGetter;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModelPart;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Runtime block state model for the Fish Tank on Fabric that dynamically composites
 * frame, sand, and glass sub-models with retextured faces based on per-block-entity data.
 * <p>
 * Implements {@link FabricBlockStateModel} so that {@code emitQuads} receives world context
 * (level + position), from which it reads the block entity's customization data to determine
 * the correct sub-model textures and permutation.
 */
public class FishTankBakedModelFabric implements BlockStateModel, FabricBlockStateModel {

    private final ModelBaker baker;
    private final ResolvedModel[] frameModels;
    private final ResolvedModel[] sandModels;
    private final ResolvedModel[] glassModels;

    private final List<BlockStateModelPart> defaultParts;
    private final Material.Baked defaultParticleMaterial;
    private final int defaultMaterialFlags;

    private final ConcurrentHashMap<CacheKey, CachedModel> modelCache = new ConcurrentHashMap<>();
    private final Object bakeLock = new Object();

    private record CacheKey(Block frame, Block sand, Block glass, int permutation) {}

    private record CachedModel(List<BlockStateModelPart> parts, Material.Baked particleMaterial, int materialFlags) {}

    public FishTankBakedModelFabric(ModelBaker baker,
                                    ResolvedModel[] frameModels,
                                    ResolvedModel[] sandModels,
                                    ResolvedModel[] glassModels) {
        this.baker = baker;
        this.frameModels = frameModels;
        this.sandModels = sandModels;
        this.glassModels = glassModels;

        FishTankCompositeModelData defaultData = FishTankCompositeModelData.DEFAULT;
        CachedModel defaultModel = generateCompositeModel(defaultData);

        if (defaultModel != null) {
            CacheKey defaultKey = new CacheKey(
                    defaultData.frameBlock(), defaultData.sandBlock(),
                    defaultData.glassBlock(), defaultData.getPermutationIndex());
            modelCache.put(defaultKey, defaultModel);
            this.defaultParts = defaultModel.parts();
            this.defaultParticleMaterial = defaultModel.particleMaterial();
            this.defaultMaterialFlags = defaultModel.materialFlags();
        } else {
            Fishtastic.LOGGER.error("Fish Tank (Fabric): failed to pre-generate default model — rendering will fall back to missing.");
            TextureSlots fallbackSlots = frameModels[0].getTopTextureSlots();
            this.defaultParticleMaterial = frameModels[0].resolveParticleMaterial(fallbackSlots, baker);
            this.defaultMaterialFlags = 0;
            this.defaultParts = List.of(baker.missingBlockModelPart());
        }
    }

    // ── FabricBlockStateModel (world-context emitQuads) ───────────────────

    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state,
                          RandomSource random, Predicate<Direction> cullTest) {
        FishTankCompositeModelData data = readBlockEntityData(level, pos);
        CacheKey key = new CacheKey(
                data.frameBlock(), data.sandBlock(),
                data.glassBlock(), data.getPermutationIndex());

        CachedModel cached = modelCache.get(key);
        if (cached == null) {
            synchronized (bakeLock) {
                cached = modelCache.get(key);
                if (cached == null) {
                    CachedModel generated = generateCompositeModel(data);
                    if (generated != null) {
                        modelCache.put(key, generated);
                        cached = generated;
                    } else {
                        Fishtastic.LOGGER.warn(
                                "[FishTankBakedModelFabric] Could not generate model for {}/{}/{} perm={}; using default fallback.",
                                BuiltInRegistries.BLOCK.getKey(key.frame()),
                                BuiltInRegistries.BLOCK.getKey(key.sand()),
                                BuiltInRegistries.BLOCK.getKey(key.glass()),
                                key.permutation());
                        cached = new CachedModel(defaultParts, defaultParticleMaterial, defaultMaterialFlags);
                        // Intentionally NOT stored — allow retry on next chunk re-mesh.
                    }
                }
            }
        }

        for (BlockStateModelPart part : cached.parts()) {
            ((FabricBlockStateModelPart) part).emitQuads(emitter, cullTest);
        }
    }

    @Override
    public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        FishTankCompositeModelData data = readBlockEntityData(level, pos);
        return new CacheKey(
                data.frameBlock(), data.sandBlock(),
                data.glassBlock(), data.getPermutationIndex());
    }

    @Override
    public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        FishTankCompositeModelData data = readBlockEntityData(level, pos);
        CacheKey key = new CacheKey(
                data.frameBlock(), data.sandBlock(),
                data.glassBlock(), data.getPermutationIndex());
        CachedModel cached = modelCache.get(key);
        return cached != null ? cached.particleMaterial() : defaultParticleMaterial;
    }

    @Override
    public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        FishTankCompositeModelData data = readBlockEntityData(level, pos);
        CacheKey key = new CacheKey(
                data.frameBlock(), data.sandBlock(),
                data.glassBlock(), data.getPermutationIndex());
        CachedModel cached = modelCache.get(key);
        return cached != null ? cached.materialFlags() : defaultMaterialFlags;
    }

    // ── Vanilla BlockStateModel fallback (no world context) ───────────────

    @Override
    public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
        parts.addAll(defaultParts);
    }

    @Override
    public Material.Baked particleMaterial() {
        return defaultParticleMaterial;
    }

    @Override
    public int materialFlags() {
        return defaultMaterialFlags;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private FishTankCompositeModelData readBlockEntityData(BlockAndTintGetter level, BlockPos pos) {
        Object renderData = ((FabricBlockGetter) level).getBlockEntityRenderData(pos);
        if (renderData instanceof FishTankCompositeModelData data) {
            return data;
        }
        return FishTankCompositeModelData.DEFAULT;
    }

    @Nullable
    private Material getBlockTexture(Block block) {
        return CompositeTextureHelper.resolveBlockTexture(block, baker, BlockModelPathResolver.getModelLocations(block));
    }

    // ── Model generation ──────────────────────────────────────────────────

    @Nullable
    private CachedModel generateCompositeModel(FishTankCompositeModelData data) {
        int perm = data.getPermutationIndex();
        try {
            Material frameTex = getBlockTexture(data.frameBlock());
            Material sandTex  = getBlockTexture(data.sandBlock());
            Material glassTex = getBlockTexture(data.glassBlock());

            if (frameTex == null || sandTex == null || glassTex == null) {
                Fishtastic.LOGGER.warn(
                        "Fish Tank (Fabric): could not resolve texture(s) for frame={} sand={} glass={} — skipping cache.",
                        frameTex == null ? BuiltInRegistries.BLOCK.getKey(data.frameBlock()) : "ok",
                        sandTex  == null ? BuiltInRegistries.BLOCK.getKey(data.sandBlock())  : "ok",
                        glassTex == null ? BuiltInRegistries.BLOCK.getKey(data.glassBlock()) : "ok");
                return null;
            }

            TextureSlots frameSlots = CompositeTextureHelper.overrideAllTexture(frameTex, frameModels[perm]);
            TextureSlots sandSlots  = CompositeTextureHelper.overrideAllTexture(sandTex,  sandModels[perm]);
            TextureSlots glassSlots = CompositeTextureHelper.overrideAllTexture(glassTex, glassModels[perm]);

            QuadCollection frameQuads = bakeGeometry(frameModels[perm], frameSlots);
            QuadCollection sandQuads  = bakeGeometry(sandModels[perm],  sandSlots);
            QuadCollection glassQuads = bakeGeometry(glassModels[perm], glassSlots);

            if (frameQuads == null || sandQuads == null || glassQuads == null) {
                return null;
            }

            QuadCollection.Builder compositeBuilder = new QuadCollection.Builder();
            compositeBuilder.addAll(frameQuads);
            compositeBuilder.addAll(sandQuads);
            compositeBuilder.addAll(glassQuads);
            QuadCollection composite = compositeBuilder.build();

            Material.Baked particleMat = frameModels[perm].resolveParticleMaterial(frameSlots, baker);
            // AO disabled: the tank shell is assembled from many noOcclusion() blocks, so vanilla
            // ambient occlusion compounds at internal seams and darkens the interior of large tanks.
            BlockStateModelPart part = new SimpleModelWrapper(composite, false, particleMat);
            int flags = frameQuads.materialFlags() | sandQuads.materialFlags() | glassQuads.materialFlags();

            return new CachedModel(List.of(part), particleMat, flags);
        } catch (Exception e) {
            Fishtastic.LOGGER.error("Fish Tank (Fabric): error generating composite model for {}", data, e);
            return null;
        }
    }

    /**
     * Bakes geometry using the supplied {@link TextureSlots}, bypassing {@link ResolvedModel#bakeTopGeometry}
     * to avoid its internal ModelWrapper caches which ignore TextureSlots after the first call.
     */
    @Nullable
    private QuadCollection bakeGeometry(ResolvedModel model, TextureSlots slots) {
        try {
            return model.getTopGeometry().bake(slots, baker, BlockModelRotation.IDENTITY, model);
        } catch (Exception e) {
            Fishtastic.LOGGER.error("Fish Tank (Fabric): error baking geometry for model {}", model.debugName(), e);
            return null;
        }
    }
}
