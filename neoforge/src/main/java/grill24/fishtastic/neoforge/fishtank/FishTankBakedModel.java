package grill24.fishtastic.neoforge.fishtank;

import grill24.fishtastic.Fishtastic;
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
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import net.neoforged.neoforge.model.data.ModelData;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime block state model for the Fish Tank that dynamically composites
 * frame, sand, and glass sub-models with retextured faces based on per-block-entity
 * {@link FishTankModelData}.
 * <p>
 * Implements {@link DynamicBlockStateModel} so that {@code collectParts} receives
 * world context (level + position), from which it reads the block entity's
 * {@link ModelData} to determine the correct sub-model textures and permutation.
 */
public class FishTankBakedModel implements DynamicBlockStateModel {

    // ── Identity ModelState (all defaults return identity transforms) ──────
    // NOTE: Do NOT use this with bakeTopGeometry — ModelWrapper caches by ModelState identity,
    // ignoring TextureSlots. Use BlockModelRotation.IDENTITY directly with getTopGeometry().bake().

    // ── Resolved sub-models (pre-loaded at bake time) ─────────────────────

    private final ModelBaker baker;
    private final ResolvedModel[] frameModels;   // [0..63]
    private final ResolvedModel[] sandModels;    // [0..63]
    private final ResolvedModel[] glassModels;   // [0..63]

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

    private record CacheKey(Block frame, Block sand, Block glass, int permutation) {}

    private record CachedModel(List<BlockStateModelPart> parts, Material.Baked particleMaterial,
                                @BakedQuad.MaterialFlags int materialFlags) {}

    // ── Constructor ───────────────────────────────────────────────────────

    public FishTankBakedModel(ModelBaker baker,
                              ResolvedModel[] frameModels,
                              ResolvedModel[] sandModels,
                              ResolvedModel[] glassModels) {
        this.baker = baker;
        this.frameModels = frameModels;
        this.sandModels = sandModels;
        this.glassModels = glassModels;

        // Pre-bake the default model (permutation 0, default textures).
        FishTankModelData defaultData = FishTankModelData.DEFAULT;
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
            // Should never happen with DEFAULT (vanilla oak_planks / sand / blue glass),
            // but guard against a broken baking environment at startup.
            Fishtastic.LOGGER.error("Fish Tank: failed to pre-generate default model — rendering will fall back to missing.");
            TextureSlots fallbackSlots = frameModels[0].getTopTextureSlots();
            this.defaultParticleMaterial = frameModels[0].resolveParticleMaterial(fallbackSlots, baker);
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
        FishTankModelData data = modelData.get(FishTankModelData.DATA_PROPERTY);

        Fishtastic.LOGGER.info("[FishTankBakedModel.collectParts] pos={}, modelData={}, fishTankData={}",
                pos,
                modelData != ModelData.EMPTY ? "present" : "EMPTY",
                data != null ? String.format("frame=%s,sand=%s,glass=%s,perm=%d",
                        BuiltInRegistries.BLOCK.getKey(data.frameBlock()),
                        BuiltInRegistries.BLOCK.getKey(data.sandBlock()),
                        BuiltInRegistries.BLOCK.getKey(data.glassBlock()),
                        data.getPermutationIndex()) : "NULL (using default)");

        if (data == null) {
            data = FishTankModelData.DEFAULT;
        }

        CacheKey key = new CacheKey(
                data.frameBlock(), data.sandBlock(),
                data.glassBlock(), data.getPermutationIndex());

        // Fast path: check cache without locking.
        CachedModel cached = modelCache.get(key);
        boolean wasCacheHit = cached != null;
        if (cached == null) {
            // Slow path: generate the model under a lock.
            final FishTankModelData finalData = data;
            synchronized (bakeLock) {
                cached = modelCache.get(key);
                if (cached == null) {
                    CachedModel generated = generateCompositeModel(finalData);
                    if (generated != null) {
                        // ── FIX: only cache successful generations ──────────────
                        // A null result means texture lookup failed (e.g. unresolved
                        // mod block).  Don't poison the cache — allow retry on the
                        // next chunk re-mesh once models are available.
                        modelCache.put(key, generated);
                        cached = generated;
                    } else {
                        Fishtastic.LOGGER.warn(
                                "[FishTankBakedModel] Could not generate model for key {}/{}/{} perm={}; "
                                        + "using default fallback this frame.",
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

        Fishtastic.LOGGER.info("[FishTankBakedModel.collectParts] pos={}, cacheHit={}, partsCount={}",
                pos, wasCacheHit, cached.parts().size());

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
        FishTankModelData data = modelData.get(FishTankModelData.DATA_PROPERTY);
        if (data == null) return defaultParticleMaterial;

        CacheKey key = new CacheKey(
                data.frameBlock(), data.sandBlock(),
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
        FishTankModelData data = modelData.get(FishTankModelData.DATA_PROPERTY);
        if (data == null) return defaultMaterialFlags;

        CacheKey key = new CacheKey(
                data.frameBlock(), data.sandBlock(),
                data.glassBlock(), data.getPermutationIndex());
        CachedModel cached = modelCache.get(key);
        return cached != null ? cached.materialFlags() : defaultMaterialFlags;
    }

    @Override
    @Nullable
    public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state,
                                    RandomSource random) {
        ModelData modelData = level.getModelData(pos);
        FishTankModelData data = modelData.get(FishTankModelData.DATA_PROPERTY);
        if (data == null) data = FishTankModelData.DEFAULT;
        CacheKey key = new CacheKey(
                data.frameBlock(), data.sandBlock(),
                data.glassBlock(), data.getPermutationIndex());
        Fishtastic.LOGGER.info("[FishTankBakedModel.createGeometryKey] pos={}, key=CacheKey[frame={},sand={},glass={},perm={}]",
                pos,
                BuiltInRegistries.BLOCK.getKey(key.frame()),
                BuiltInRegistries.BLOCK.getKey(key.sand()),
                BuiltInRegistries.BLOCK.getKey(key.glass()),
                key.permutation());
        return key;
    }

    // ── Model generation ──────────────────────────────────────────────────

    /**
     * Generates a composite model for the given fish tank configuration.
     * <p>
     * Resolves all three block textures first; if any lookup fails the method
     * returns {@code null} so the caller can use a fallback <em>without</em>
     * caching the result (preventing cache poisoning).
     *
     * @return the freshly baked {@link CachedModel}, or {@code null} if any
     *         texture could not be resolved or any geometry bake failed.
     */
    @Nullable
    private CachedModel generateCompositeModel(FishTankModelData data) {
        int perm = data.getPermutationIndex();

        try {
            Material frameTex = getBlockTexture(data.frameBlock());
            Material sandTex  = getBlockTexture(data.sandBlock());
            Material glassTex = getBlockTexture(data.glassBlock());

            Fishtastic.LOGGER.info("[FishTankBakedModel.generateCompositeModel] frame={} → frameTex={} | sand={} → sandTex={} | glass={} → glassTex={}",
                    BuiltInRegistries.BLOCK.getKey(data.frameBlock()), frameTex,
                    BuiltInRegistries.BLOCK.getKey(data.sandBlock()),  sandTex,
                    BuiltInRegistries.BLOCK.getKey(data.glassBlock()), glassTex);

            if (frameTex == null || sandTex == null || glassTex == null) {
                Fishtastic.LOGGER.warn(
                        "Fish Tank: could not resolve texture(s) for frame={} sand={} glass={} — skipping cache.",
                        frameTex == null ? BuiltInRegistries.BLOCK.getKey(data.frameBlock()) : "ok",
                        sandTex  == null ? BuiltInRegistries.BLOCK.getKey(data.sandBlock())  : "ok",
                        glassTex == null ? BuiltInRegistries.BLOCK.getKey(data.glassBlock()) : "ok");
                return null;
            }

            // ── FIX: build TextureSlots that chain through the sub-model's own
            //         texture hierarchy as a fallback for any non-overridden slots.
            TextureSlots frameSlots = overrideAllTexture(frameTex, frameModels[perm]);
            TextureSlots sandSlots  = overrideAllTexture(sandTex,  sandModels[perm]);
            TextureSlots glassSlots = overrideAllTexture(glassTex, glassModels[perm]);

            QuadCollection frameQuads = bakeGeometry(frameModels[perm], frameSlots);
            QuadCollection sandQuads  = bakeGeometry(sandModels[perm],  sandSlots);
            QuadCollection glassQuads = bakeGeometry(glassModels[perm], glassSlots);

            if (frameQuads == null || sandQuads == null || glassQuads == null) {
                return null;
            }

            // Combine quads into a single QuadCollection.
            QuadCollection.Builder compositeBuilder = new QuadCollection.Builder();
            compositeBuilder.addAll(frameQuads);
            compositeBuilder.addAll(sandQuads);
            compositeBuilder.addAll(glassQuads);
            QuadCollection composite = compositeBuilder.build();

            // Use the STATIC resolveParticleMaterial to bypass ModelWrapper's KEY_PARTICLE_SPRITE
            // cache, which also ignores TextureSlots after the first call.
            Material.Baked particleMat = ResolvedModel.resolveParticleMaterial(frameSlots, baker, frameModels[perm]);

            BlockStateModelPart part = new SimpleModelWrapper(composite, true, particleMat);
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
     * <p><b>IMPORTANT:</b> We intentionally bypass {@link ResolvedModel#bakeTopGeometry} here.
     * {@code ModelWrapper.bakeTopGeometry} (the concrete MC implementation) has two internal
     * caches keyed by {@link ModelState} only — completely ignoring {@code TextureSlots}:
     * <ul>
     *   <li>{@code bakeDefaultState} stores the result for {@link BlockModelRotation#IDENTITY}
     *       permanently in a fixed slot on the first call.  Our constructor bakes the default
     *       oak_planks model first, permanently poisoning that slot.</li>
     *   <li>{@code modelBakeCache.computeIfAbsent(state, …)} caches by {@code ModelState}
     *       object identity.  Any reused static {@code ModelState} would return the first
     *       result forever.</li>
     * </ul>
     * Calling {@code model.getTopGeometry().bake()} directly bypasses both caches and always
     * produces fresh quads using the {@code TextureSlots} we actually want.
     *
     * @return the baked quads, or {@code null} if an exception was thrown.
     */
    @Nullable
    private QuadCollection bakeGeometry(ResolvedModel model, TextureSlots slots) {
        try {
            // Call the underlying geometry bake directly — no ModelWrapper cache involved.
            return model.getTopGeometry().bake(slots, baker, BlockModelRotation.IDENTITY, model,
                    ResolvedModelExtension.findTopAdditionalProperties(model));
        } catch (Exception e) {
            Fishtastic.LOGGER.error("Fish Tank: error baking geometry for model {}", model.debugName(), e);
            return null;
        }
    }

    /**
     * Gets the primary texture {@link Material} from a block's model.
     * Tries {@code "all"} first (cube_all), then common multi-texture slot names.
     */
    @Nullable
    private Material getBlockTexture(Block block) {
        List<Identifier> locations = BlockModelPathResolver.getModelLocations(block);
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);

        for (Identifier location : locations) {
            try {
                ResolvedModel blockModel = baker.getModel(location);
                TextureSlots slots = blockModel.getTopTextureSlots();

                Fishtastic.LOGGER.info("[FishTankBakedModel.getBlockTexture] block={} → location={} → model.debugName={}",
                        blockId, location, blockModel.debugName());

                // Try "all" first (cube_all style).
                Material mat = slots.getMaterial("all");
                if (mat != null) {
                    Fishtastic.LOGGER.info("[FishTankBakedModel.getBlockTexture] block={} matched slot 'all' → {}", blockId, mat);
                    return mat;
                }

                // Try common slot names as fallbacks.
                for (String slotName : new String[]{"top", "side", "front", "end", "particle"}) {
                    mat = slots.getMaterial(slotName);
                    if (mat != null) {
                        Fishtastic.LOGGER.info("[FishTankBakedModel.getBlockTexture] block={} matched slot '{}' → {}", blockId, slotName, mat);
                        return mat;
                    }
                }

                Fishtastic.LOGGER.warn("[FishTankBakedModel.getBlockTexture] block={} location={} model has no usable texture slot!", blockId, location);

            } catch (Exception e) {
                Fishtastic.LOGGER.debug("Fish Tank: could not resolve model {} for texture lookup: {}",
                        location, e.getMessage());
            }
        }

        Fishtastic.LOGGER.warn("[FishTankBakedModel.getBlockTexture] block={} — no texture found across {} locations", blockId, locations);
        return null;
    }

    /**
     * Creates a {@link TextureSlots} that overrides {@code "all"} and
     * {@code "particle"} with {@code texture}, while chaining through
     * {@code baseModel}'s full texture hierarchy as a fallback for any other
     * slots the geometry may reference.
     *
     * <p>Previously this method created an <em>isolated</em> TextureSlots
     * containing only "all" and "particle".  That worked by accident because all
     * current fish-tank sub-models reference only {@code #all}, but it would
     * silently break any sub-model that introduces additional texture variables.
     */
    private static TextureSlots overrideAllTexture(Material texture, ResolvedModel baseModel) {
        TextureSlots.Data.Builder builder = new TextureSlots.Data.Builder();
        builder.addTexture("all", texture);
        builder.addTexture("particle", texture);

        TextureSlots.Resolver resolver = new TextureSlots.Resolver().addFirst(builder.build());

        for (ResolvedModel m = baseModel; m != null; m = m.parent()) {
            resolver.addLast(m.wrapped().textureSlots());
        }

        TextureSlots result = resolver.resolve(() -> "fish_tank_override");

        // Log the resolved "all" and "particle" slots so we can verify the override took effect.
        Fishtastic.LOGGER.info("[FishTankBakedModel.overrideAllTexture] baseModel={} | input texture={} | resolved all={} | resolved particle={}",
                baseModel.debugName(), texture,
                result.getMaterial("all"),
                result.getMaterial("particle"));

        return result;
    }
}
