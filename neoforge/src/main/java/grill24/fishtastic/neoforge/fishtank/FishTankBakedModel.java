package grill24.fishtastic.neoforge.fishtank;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.client.compositemodel.CompositeTextureHelper;
import grill24.fishtastic.fishtank.FishTankCompositeModelData;
import grill24.fishtastic.fishtank.FishTankShape;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.neoforged.neoforge.client.extensions.ResolvedModelExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import net.neoforged.neoforge.model.data.ModelData;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime block state model for the Fish Tank that dynamically composites
 * frame, sand, and glass sub-models with retextured faces based on per-block-entity
 * {@link FishTankCompositeModelData}.
 * <p>
 * Implements {@link DynamicBlockStateModel} so that {@code collectParts} receives
 * world context (level + position), from which it reads the block entity's
 * {@link ModelData} to determine the correct sub-model textures and permutation.
 */
public class FishTankBakedModel implements DynamicBlockStateModel {

    // ── Resolved sub-models (pre-loaded at bake time) ─────────────────────

    private final ModelBaker baker;
    private final Map<FishTankShape, ResolvedModel[]> frameModels;   // shape -> [0..63]
    private final Map<FishTankShape, ResolvedModel[]> sandModels;    // shape -> [0..63]
    private final Map<FishTankShape, ResolvedModel[]> glassModels;   // shape -> [0..63]

    // ── Default (fallback) model ──────────────────────────────────────────

    private final List<BlockStateModelPart> defaultParts;
    private final Material.Baked defaultParticleMaterial;
    @BakedQuad.MaterialFlags
    private final int defaultMaterialFlags;

    // ── Cache of per-configuration composite parts ────────────────────────

    private final ConcurrentHashMap<CacheKey, CachedModel> modelCache = new ConcurrentHashMap<>();

    // ── Synchronisation lock for lazy baking (baker may not be thread-safe) ─
    private final Object bakeLock = new Object();

    // ── Cache key record ──────────────────────────────────────────────────

    private record CacheKey(FishTankShape shape, Block frame, Block sand, Block glass, int permutation) {}

    private record CachedModel(List<BlockStateModelPart> parts, Material.Baked particleMaterial,
                                @BakedQuad.MaterialFlags int materialFlags) {}

    // ── Constructor ───────────────────────────────────────────────────────

    public FishTankBakedModel(ModelBaker baker,
                              Map<FishTankShape, ResolvedModel[]> frameModels,
                              Map<FishTankShape, ResolvedModel[]> sandModels,
                              Map<FishTankShape, ResolvedModel[]> glassModels) {
        this.baker = baker;
        this.frameModels = frameModels;
        this.sandModels = sandModels;
        this.glassModels = glassModels;

        // Pre-bake the default model (permutation 0, default textures).
        FishTankCompositeModelData defaultData = FishTankCompositeModelData.DEFAULT;
        CachedModel defaultModel = generateCompositeModel(defaultData);

        if (defaultModel != null) {
            CacheKey defaultKey = new CacheKey(
                    defaultData.shape(), defaultData.frameBlock(), defaultData.sandBlock(),
                    defaultData.glassBlock(), defaultData.getPermutationIndex());
            modelCache.put(defaultKey, defaultModel);
            this.defaultParts = defaultModel.parts();
            this.defaultParticleMaterial = defaultModel.particleMaterial();
            this.defaultMaterialFlags = defaultModel.materialFlags();
        } else {
            // Should never happen with DEFAULT (vanilla oak_planks / sand / blue glass),
            // but guard against a broken baking environment at startup.
            Fishtastic.LOGGER.error("Fish Tank: failed to pre-generate default model — rendering will fall back to missing.");
            ResolvedModel fallbackModel = frameModels.get(FishTankShape.STANDARD)[0];
            TextureSlots fallbackSlots = fallbackModel.getTopTextureSlots();
            this.defaultParticleMaterial = fallbackModel.resolveParticleMaterial(fallbackSlots, baker);
            this.defaultMaterialFlags = 0;
            this.defaultParts = List.of(baker.missingBlockModelPart());
        }
    }

    // ── DynamicBlockStateModel ────────────────────────────────────────────

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state,
                             RandomSource random, List<BlockStateModelPart> parts) {

        // Read the block entity's model data.
        ModelData modelData = level.getModelData(pos);
        FishTankCompositeModelData data = modelData.get(FishTankModelData.DATA_PROPERTY);

        if (data == null) {
            data = FishTankCompositeModelData.DEFAULT;
        }

        CacheKey key = new CacheKey(
                data.shape(), data.frameBlock(), data.sandBlock(),
                data.glassBlock(), data.getPermutationIndex());

        // Fast path: check cache without locking.
        CachedModel cached = modelCache.get(key);
        if (cached == null) {
            // Slow path: generate the model under a lock.
            final FishTankCompositeModelData finalData = data;
            synchronized (bakeLock) {
                cached = modelCache.get(key);
                if (cached == null) {
                    CachedModel generated = generateCompositeModel(finalData);
                    if (generated != null) {
                        // Only cache successful generations — a null result means texture lookup
                        // failed (e.g. unresolved mod block). Allow retry on the next chunk re-mesh.
                        modelCache.put(key, generated);
                        cached = generated;
                    } else {
                        Fishtastic.LOGGER.warn(
                                "[FishTankBakedModel] Could not generate model for key shape={} {}/{}/{} perm={}; "
                                        + "using default fallback this frame.",
                                key.shape(),
                                BuiltInRegistries.BLOCK.getKey(key.frame()),
                                BuiltInRegistries.BLOCK.getKey(key.sand()),
                                BuiltInRegistries.BLOCK.getKey(key.glass()),
                                key.permutation());
                        cached = new CachedModel(defaultParts, defaultParticleMaterial, defaultMaterialFlags);
                        // Intentionally NOT stored in modelCache so the next remesh retries.
                    }
                }
            }
        }

        parts.addAll(cached.parts());
    }

    @Override
    @Deprecated
    public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
        // Fallback without world context — use the default model.
        parts.addAll(defaultParts);
    }

    @Override
    public Material.Baked particleMaterial() {
        return defaultParticleMaterial;
    }

    @Override
    public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        ModelData modelData = level.getModelData(pos);
        FishTankCompositeModelData data = modelData.get(FishTankModelData.DATA_PROPERTY);
        if (data == null) return defaultParticleMaterial;

        CacheKey key = new CacheKey(
                data.shape(), data.frameBlock(), data.sandBlock(),
                data.glassBlock(), data.getPermutationIndex());
        CachedModel cached = modelCache.get(key);
        return cached != null ? cached.particleMaterial() : defaultParticleMaterial;
    }

    @Override
    @BakedQuad.MaterialFlags
    public int materialFlags() {
        return defaultMaterialFlags;
    }

    @Override
    @BakedQuad.MaterialFlags
    public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        ModelData modelData = level.getModelData(pos);
        FishTankCompositeModelData data = modelData.get(FishTankModelData.DATA_PROPERTY);
        if (data == null) return defaultMaterialFlags;

        CacheKey key = new CacheKey(
                data.shape(), data.frameBlock(), data.sandBlock(),
                data.glassBlock(), data.getPermutationIndex());
        CachedModel cached = modelCache.get(key);
        return cached != null ? cached.materialFlags() : defaultMaterialFlags;
    }

    @Override
    @Nullable
    public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state,
                                    RandomSource random) {
        ModelData modelData = level.getModelData(pos);
        FishTankCompositeModelData data = modelData.get(FishTankModelData.DATA_PROPERTY);
        if (data == null) data = FishTankCompositeModelData.DEFAULT;
        return new CacheKey(
                data.shape(), data.frameBlock(), data.sandBlock(),
                data.glassBlock(), data.getPermutationIndex());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    @Nullable
    private Material getBlockTexture(Block block) {
        return CompositeTextureHelper.resolveBlockTexture(block, baker, BlockModelPathResolver.getModelLocations(block));
    }

    // ── Model generation ──────────────────────────────────────────────────

    /**
     * Generates a composite model for the given fish tank configuration.
     *
     * @return the freshly baked {@link CachedModel}, or {@code null} if any
     *         texture could not be resolved or any geometry bake failed.
     */
    @Nullable
    private CachedModel generateCompositeModel(FishTankCompositeModelData data) {
        int perm = data.getPermutationIndex();

        try {
            ResolvedModel[] frameModelsForShape = frameModels.get(data.shape());
            ResolvedModel[] sandModelsForShape  = sandModels.get(data.shape());
            ResolvedModel[] glassModelsForShape = glassModels.get(data.shape());
            if (frameModelsForShape == null || sandModelsForShape == null || glassModelsForShape == null) {
                Fishtastic.LOGGER.warn("Fish Tank: no models loaded for shape={} — skipping cache.", data.shape());
                return null;
            }

            Material frameTex = getBlockTexture(data.frameBlock());
            Material sandTex  = getBlockTexture(data.sandBlock());
            Material glassTex = getBlockTexture(data.glassBlock());

            if (frameTex == null || sandTex == null || glassTex == null) {
                Fishtastic.LOGGER.warn(
                        "Fish Tank: could not resolve texture(s) for frame={} sand={} glass={} — skipping cache.",
                        frameTex == null ? BuiltInRegistries.BLOCK.getKey(data.frameBlock()) : "ok",
                        sandTex  == null ? BuiltInRegistries.BLOCK.getKey(data.sandBlock())  : "ok",
                        glassTex == null ? BuiltInRegistries.BLOCK.getKey(data.glassBlock()) : "ok");
                return null;
            }

            TextureSlots frameSlots = CompositeTextureHelper.overrideAllTexture(frameTex, frameModelsForShape[perm]);
            TextureSlots sandSlots  = CompositeTextureHelper.overrideAllTexture(sandTex,  sandModelsForShape[perm]);
            TextureSlots glassSlots = CompositeTextureHelper.overrideAllTexture(glassTex, glassModelsForShape[perm]);

            QuadCollection frameQuads = bakeGeometry(frameModelsForShape[perm], frameSlots);
            QuadCollection sandQuads  = bakeGeometry(sandModelsForShape[perm],  sandSlots);
            QuadCollection glassQuads = bakeGeometry(glassModelsForShape[perm], glassSlots);

            if (frameQuads == null || sandQuads == null || glassQuads == null) {
                return null;
            }

            QuadCollection.Builder compositeBuilder = new QuadCollection.Builder();
            compositeBuilder.addAll(frameQuads);
            compositeBuilder.addAll(sandQuads);
            compositeBuilder.addAll(glassQuads);
            QuadCollection composite = compositeBuilder.build();

            Material.Baked particleMat = ResolvedModel.resolveParticleMaterial(frameSlots, baker, frameModelsForShape[perm]);

            // AO disabled: the tank shell is assembled from many noOcclusion() blocks, so vanilla
            // ambient occlusion compounds at internal seams and darkens the interior of large tanks.
            BlockStateModelPart part = new SimpleModelWrapper(composite, false, particleMat);
            int flags = frameQuads.materialFlags() | sandQuads.materialFlags() | glassQuads.materialFlags();

            return new CachedModel(List.of(part), particleMat, flags);

        } catch (Exception e) {
            Fishtastic.LOGGER.error("Fish Tank: error generating composite model for {}", data, e);
            return null;
        }
    }

    /**
     * Bakes the geometry of {@code model} using the supplied {@link TextureSlots}.
     *
     * <p>Bypasses {@link ResolvedModel#bakeTopGeometry} intentionally — {@code ModelWrapper}
     * has internal caches keyed by {@code ModelState} only, completely ignoring
     * {@code TextureSlots}. Calling {@code model.getTopGeometry().bake()} directly always
     * produces fresh quads using the {@code TextureSlots} we actually want.
     */
    @Nullable
    private QuadCollection bakeGeometry(ResolvedModel model, TextureSlots slots) {
        try {
            return model.getTopGeometry().bake(slots, baker, BlockModelRotation.IDENTITY, model,
                    ResolvedModelExtension.findTopAdditionalProperties(model));
        } catch (Exception e) {
            Fishtastic.LOGGER.error("Fish Tank: error baking geometry for model {}", model.debugName(), e);
            return null;
        }
    }
}
