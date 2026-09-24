package grill24.fishtastic.client.compositemodel;

import com.mojang.datafixers.util.Either;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.fishtank.FishTankCompositeModelData;
import grill24.fishtastic.fishtank.FishTankShape;
import grill24.fishtastic.fishtank.TankDiagonal;
import grill24.fishtastic.fishtank.TankEdgeDiagonal;
import grill24.fishtastic.util.Ids;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * PORT-ONLY: the fish tank body's composite geometry, shared by both loaders' tank models
 * (NeoForge {@code FishTankBakedModel}, Fabric {@code FishTankBakedModelFabric}).
 *
 * <p>26.1.2 keeps a {@code ModelBaker} and bakes each frame/sand/glass configuration lazily at
 * chunk-meshing time. That can't be done on 1.21.1: {@code ModelBakery} loads models into plain
 * {@code HashMap}s that are only safe to touch during the reload's (single-threaded) bake phase.
 * So everything that needs the bakery happens once, in {@link #bake}: every fragment model is
 * loaded and parent-resolved, and every block's texture is resolved. What's left for meshing
 * threads ({@link #composite}) only reads those immutable maps and runs vanilla's
 * {@code FaceBakery} (through {@link BlockModel#bake}), which touches no shared state. See
 * docs/backport-pass2/track-a5-rendering-1.21.1.md (A5.2).
 *
 * <p>The composition itself (which permutation, which corner/edge fragments, which glass fills)
 * is 26.1.2's {@code FishTankBakedModel.generateCompositeModel}, unchanged.
 */
public final class FishTankGeometry {
    private static final int PERMUTATION_COUNT = 64;
    private static final String[] TEXTURE_SLOT_PRIORITY = {"all", "top", "side", "front", "end", "particle"};
    /** 0-5: quads culled against that {@link Direction}; 6: unculled. */
    private static final int SIDES = 7;
    private static final Direction[] DIRECTIONS = Direction.values();

    /** Which of the tank's three materials a group of quads is textured with. */
    public enum Layer { FRAME, SAND, GLASS }

    /**
     * One layer of a composite: the quads textured with {@code source}'s texture, bucketed by
     * cull face like {@code SimpleBakedModel}. The loaders pick the chunk layer / blend mode from
     * {@code source}'s own render type.
     */
    public record LayerQuads(Layer layer, Block source, List<BakedQuad>[] bySide) {
        public List<BakedQuad> get(@Nullable Direction side) {
            return bySide[side == null ? 6 : side.ordinal()];
        }
    }

    /** A fully baked tank body for one configuration. */
    public record Composite(List<LayerQuads> layers, TextureAtlasSprite particle) {}

    private record CacheKey(FishTankShape shape, Block frame, Block sand, Block glass, int permutation,
                            Set<TankDiagonal> diagonalOverrides, Set<TankEdgeDiagonal> edgeDiagonalOverrides) {
        static CacheKey of(FishTankCompositeModelData data) {
            return new CacheKey(data.shape(), data.frameBlock(), data.sandBlock(), data.glassBlock(),
                    data.getPermutationIndex(), data.getDiagonalOverrideMask(), data.getEdgeDiagonalOverrideMask());
        }
    }

    private final Map<ResourceLocation, BlockModel> fragments;
    private final Map<Block, Material> textures;
    private final Function<Material, TextureAtlasSprite> spriteGetter;
    private final ItemTransforms blockItemTransforms;
    private final ConcurrentHashMap<CacheKey, Composite> cache = new ConcurrentHashMap<>();
    private final Composite fallback;

    // One instance per model reload: the block model and the item model both bake through it.
    private static Object lastBakery;
    private static FishTankGeometry lastGeometry;

    private FishTankGeometry(Map<ResourceLocation, BlockModel> fragments, Map<Block, Material> textures,
                             Function<Material, TextureAtlasSprite> spriteGetter, ItemTransforms blockItemTransforms) {
        this.fragments = fragments;
        this.textures = textures;
        this.spriteGetter = spriteGetter;
        this.blockItemTransforms = blockItemTransforms;
        Composite def = generate(FishTankCompositeModelData.DEFAULT);
        if (def == null) {
            Fishtastic.LOGGER.error("Fish Tank: failed to generate the default model — tanks will render empty.");
            def = new Composite(List.of(), spriteGetter.apply(new Material(
                    net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS,
                    net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation())));
        }
        this.fallback = def;
        cache.put(CacheKey.of(FishTankCompositeModelData.DEFAULT), def);
    }

    // ── Reload-time loading ───────────────────────────────────────────────

    /**
     * Loads everything the tank needs from the bakery. Must be called from the model bake phase
     * (the loaders' {@code bake}); repeated calls within one reload return the same instance.
     *
     * @param modelLocations the loader's block → candidate model paths lookup (NeoForge adds config overrides)
     */
    public static synchronized FishTankGeometry bake(ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter,
                                                     Function<Block, List<ResourceLocation>> modelLocations) {
        // The missing model is created once per ModelBakery, so it identifies this reload.
        Object bakery = baker.getModel(ModelBakery.MISSING_MODEL_LOCATION);
        if (bakery == lastBakery && lastGeometry != null) return lastGeometry;

        long start = System.nanoTime();
        Function<ResourceLocation, UnbakedModel> getter = baker::getModel;
        ResourceManager resources = Minecraft.getInstance().getResourceManager();

        // Blockstate redirects first: texture resolution below reads them. Resources are already
        // swapped to the new packs by the time the bakery runs.
        BlockstateRedirectRegistry.update(BlockstateModelScanner.buildRedirectMap(resources));

        Map<ResourceLocation, BlockModel> fragments = new HashMap<>();
        int missing = 0;
        for (ResourceLocation id : fragmentIds()) {
            UnbakedModel model = getter.apply(id);
            if (model instanceof BlockModel blockModel && model != bakery) {
                blockModel.resolveParents(getter);
                fragments.put(id, blockModel);
            } else {
                missing++;
            }
        }

        Map<Block, Material> textures = new IdentityHashMap<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            Material texture = resolveBlockTexture(block, getter, bakery, resources, modelLocations.apply(block));
            if (texture != null) textures.put(block, texture);
        }

        ItemTransforms transforms = ItemTransforms.NO_TRANSFORMS;
        if (getter.apply(Ids.withDefaultNamespace("block/block")) instanceof BlockModel blockBase) {
            blockBase.resolveParents(getter);
            transforms = blockBase.getTransforms();
        }

        FishTankGeometry geometry = new FishTankGeometry(Map.copyOf(fragments), Collections.unmodifiableMap(textures),
                spriteGetter, transforms);
        Fishtastic.LOGGER.info("Fish Tank geometry loaded: {} fragment models ({} missing), textures for {} blocks, in {} ms",
                fragments.size(), missing, textures.size(), (System.nanoTime() - start) / 1_000_000);
        lastBakery = bakery;
        lastGeometry = geometry;
        return geometry;
    }

    /**
     * Every fragment model the tank can use, per {@link FishTankShape}: the 64 frame/sand/glass
     * permutations and the diagonal/edge fragments. 26.1.2's {@code FishTankBlockStateModel.resolveDependencies}.
     */
    public static List<ResourceLocation> fragmentIds() {
        List<ResourceLocation> ids = new ArrayList<>();
        for (FishTankShape shape : FishTankShape.values()) {
            for (int i = 0; i < PERMUTATION_COUNT; i++) {
                ids.add(modelLocation(shape, "frame", i));
                ids.add(modelLocation(shape, "sand", i));
                ids.add(modelLocation(shape, "glass", i));
            }
            if (shape.hasDiagonalCornerFragments()) {
                for (TankDiagonal diagonal : TankDiagonal.values()) {
                    for (int capState = 0; capState < 4; capState++) {
                        ids.add(cornerFragmentLocation(shape, diagonal, capState));
                    }
                }
            }
            if (shape.hasCornerGlassFillFragments()) {
                for (TankDiagonal diagonal : TankDiagonal.values()) {
                    for (int capState = 0; capState < 4; capState++) {
                        ids.add(cornerGlassFillLocation(shape, diagonal, capState));
                    }
                }
            }
            if (shape.hasEdgeDiagonalFragments()) {
                for (TankEdgeDiagonal edge : TankEdgeDiagonal.values()) {
                    ids.add(edgeFragmentLocation(shape, edge));
                    for (TankDiagonal corner : edge.endDiagonals()) {
                        ids.add(edgeGlassFillLocation(shape, edge, corner));
                    }
                }
            }
        }
        return ids;
    }

    /**
     * 26.1.2's {@code CompositeTextureHelper.resolveBlockTexture}: the first usable texture slot of
     * the first of {@code locations} that has a model. Locations without a model JSON are skipped
     * <em>before</em> asking the bakery, which would otherwise log "Unable to load model" for them.
     */
    @Nullable
    private static Material resolveBlockTexture(Block block, Function<ResourceLocation, UnbakedModel> getter, Object missingModel,
                                                ResourceManager resources, List<ResourceLocation> locations) {
        for (ResourceLocation location : locations) {
            if (resources.getResource(location.withPath(path -> "models/" + path + ".json")).isEmpty()) continue;
            try {
                if (!(getter.apply(location) instanceof BlockModel model) || model == missingModel) continue;
                model.resolveParents(getter);
                for (String slot : TEXTURE_SLOT_PRIORITY) {
                    if (model.hasTexture(slot)) return model.getMaterial(slot);
                }
            } catch (Exception e) {
                Fishtastic.LOGGER.debug("[FishTankGeometry] could not resolve model {} for block {}: {}",
                        location, BuiltInRegistries.BLOCK.getKey(block), e.getMessage());
            }
        }
        return null;
    }

    // ── Meshing-time composition ─────────────────────────────────────────

    /** The composite for {@code data}, baked on first use; the default tank if it can't be built. */
    public Composite composite(@Nullable FishTankCompositeModelData data) {
        if (data == null) return fallback;
        CacheKey key = CacheKey.of(data);
        Composite cached = cache.get(key);
        if (cached != null) return cached;
        Composite generated = generate(data);
        if (generated == null) {
            // Not cached, so the next re-mesh retries (26.1.2 does the same).
            Fishtastic.LOGGER.warn("[FishTankGeometry] Could not generate model for shape={} {}/{}/{} perm={}; using the default.",
                    key.shape(), BuiltInRegistries.BLOCK.getKey(key.frame()), BuiltInRegistries.BLOCK.getKey(key.sand()),
                    BuiltInRegistries.BLOCK.getKey(key.glass()), key.permutation());
            return fallback;
        }
        Composite raced = cache.putIfAbsent(key, generated);
        return raced != null ? raced : generated;
    }

    /** Display transforms for the tank item: vanilla's {@code block/block}, as any block item. */
    public ItemTransforms blockItemTransforms() {
        return blockItemTransforms;
    }

    @Nullable
    private Composite generate(FishTankCompositeModelData data) {
        int perm = data.getPermutationIndex();
        FishTankShape shape = data.shape();
        Material frameTex = textures.get(data.frameBlock());
        Material sandTex = textures.get(data.sandBlock());
        Material glassTex = textures.get(data.glassBlock());
        if (frameTex == null || sandTex == null || glassTex == null) {
            Fishtastic.LOGGER.warn(
                    "Fish Tank: could not resolve texture(s) for frame={} sand={} glass={} — skipping cache.",
                    frameTex == null ? BuiltInRegistries.BLOCK.getKey(data.frameBlock()) : "ok",
                    sandTex == null ? BuiltInRegistries.BLOCK.getKey(data.sandBlock()) : "ok",
                    glassTex == null ? BuiltInRegistries.BLOCK.getKey(data.glassBlock()) : "ok");
            return null;
        }

        try {
            List<BakedQuad>[] frame = newBuckets();
            List<BakedQuad>[] sand = newBuckets();
            List<BakedQuad>[] glass = newBuckets();
            if (!bakeInto(frame, modelLocation(shape, "frame", perm), frameTex)
                    || !bakeInto(sand, modelLocation(shape, "sand", perm), sandTex)
                    || !bakeInto(glass, modelLocation(shape, "glass", perm), glassTex)) {
                return null;
            }

            boolean ceilingClosed = !data.openFaces().contains(Direction.UP);
            boolean floorClosed = !data.openFaces().contains(Direction.DOWN);
            int capState = (ceilingClosed ? 2 : 0) | (floorClosed ? 1 : 0);

            // Diagonal-aware corner posts: composite a small fragment back in for each corner whose
            // orthogonal faces are both open but its diagonal neighbor cell is empty (see
            // FishTankCompositeModelData#getDiagonalOverrideMask). Absent for shapes with no
            // combined-face corner gate to begin with.
            if (shape.hasDiagonalCornerFragments()) {
                for (TankDiagonal diagonal : data.getDiagonalOverrideMask()) {
                    bakeInto(frame, cornerFragmentLocation(shape, diagonal, capState), frameTex);
                }
            }

            // Corner glass fill: restore the small notch the base glass bake carves out of an
            // eligible corner's pane, when that corner's post does NOT render (its diagonal cell is
            // filled instead — see FishTankCompositeModelData#getDiagonalGlassFillMask, the inverse
            // of the post's own override mask). Only present for the four shapes whose ceiling/floor
            // is a glass-paned ring.
            if (shape.hasCornerGlassFillFragments()) {
                for (TankDiagonal diagonal : data.getDiagonalGlassFillMask()) {
                    bakeInto(glass, cornerGlassFillLocation(shape, diagonal, capState), glassTex);
                }
            }

            // Edge-diagonal frame beams: composite a small fragment back in for each edge whose
            // horizontal and vertical faces are both open but its edge-diagonal neighbor cell is
            // empty (see FishTankCompositeModelData#getEdgeDiagonalOverrideMask).
            // Edge-diagonal glass fill: restore the small flush glass sliver the base glass bake
            // omits at an eligible edge's cap band, when that edge's beam does NOT render. Each of
            // the edge's two end corners is independently gated on its "wall" face actually being
            // closed — a corner cell only has glass to restore at all when that perpendicular wall exists.
            if (shape.hasEdgeDiagonalFragments()) {
                for (TankEdgeDiagonal edge : data.getEdgeDiagonalOverrideMask()) {
                    bakeInto(frame, edgeFragmentLocation(shape, edge), frameTex);
                }
                for (TankEdgeDiagonal edge : data.getEdgeDiagonalGlassFillMask()) {
                    for (TankDiagonal corner : edge.endDiagonals()) {
                        if (data.openFaces().contains(edge.wallFace(corner))) continue; // wall open — no pane to restore
                        bakeInto(glass, edgeGlassFillLocation(shape, edge, corner), glassTex);
                    }
                }
            }

            List<LayerQuads> layers = List.of(
                    new LayerQuads(Layer.FRAME, data.frameBlock(), freeze(frame)),
                    new LayerQuads(Layer.SAND, data.sandBlock(), freeze(sand)),
                    new LayerQuads(Layer.GLASS, data.glassBlock(), freeze(glass)));
            return new Composite(layers, spriteGetter.apply(frameTex));
        } catch (Exception e) {
            Fishtastic.LOGGER.error("Fish Tank: error generating composite model for {}", data, e);
            return null;
        }
    }

    /**
     * Bakes fragment {@code id} retextured with {@code texture} and appends its quads. A fresh
     * {@link BlockModel} whose parent is the fragment and whose {@code all}/{@code particle} slots
     * are the texture (26.1.2's {@code overrideAllTexture}), baked with vanilla's own
     * {@link BlockModel#bake}: with no item overrides, that is {@code FaceBakery} over the
     * fragment's elements and never calls back into the baker.
     */
    private boolean bakeInto(List<BakedQuad>[] buckets, ResourceLocation id, Material texture) {
        BlockModel fragment = fragments.get(id);
        if (fragment == null) {
            Fishtastic.LOGGER.warn("Fish Tank: fragment model {} was not loaded", id);
            return false;
        }
        Map<String, Either<Material, String>> slots = Map.of("all", Either.left(texture), "particle", Either.left(texture));
        // AO disabled: the tank shell is assembled from many noOcclusion() blocks, so vanilla
        // ambient occlusion compounds at internal seams and darkens the interior of large tanks.
        BlockModel retextured = new BlockModel(id, List.of(), slots, false, null, ItemTransforms.NO_TRANSFORMS, List.of());
        retextured.resolveParents(fragments::get);
        BakedModel baked = retextured.bake(NO_BAKER, spriteGetter, (ModelState) BlockModelRotation.X0_Y0);
        if (baked == null) return false;
        RandomSource random = RandomSource.create(42L);
        for (int side = 0; side < SIDES; side++) {
            buckets[side].addAll(baked.getQuads(null, side == 6 ? null : DIRECTIONS[side], random));
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static List<BakedQuad>[] newBuckets() {
        List<BakedQuad>[] buckets = new List[SIDES];
        for (int i = 0; i < SIDES; i++) buckets[i] = new ArrayList<>();
        return buckets;
    }

    private static List<BakedQuad>[] freeze(List<BakedQuad>[] buckets) {
        for (int i = 0; i < SIDES; i++) buckets[i] = List.copyOf(buckets[i]);
        return buckets;
    }

    /** Runtime baking must never reach the bakery (see the class doc); fail loudly if it does. */
    private static final ModelBaker NO_BAKER = new ModelBaker() {
        @Override
        public UnbakedModel getModel(ResourceLocation id) {
            throw new IllegalStateException("Fish Tank runtime bake asked the model bakery for " + id);
        }

        @Override
        public BakedModel bake(ResourceLocation id, ModelState state) {
            throw new IllegalStateException("Fish Tank runtime bake asked the model bakery to bake " + id);
        }
    };

    // ── Fragment ids (26.1.2's FishTankBlockStateModel) ──────────────────

    private static ResourceLocation modelLocation(FishTankShape shape, String part, int permutation) {
        return Ids.of(Fishtastic.MOD_ID, "block/" + shape.modelPathPrefix() + "/fish_tank_" + part + "_" + permutation);
    }

    private static ResourceLocation cornerFragmentLocation(FishTankShape shape, TankDiagonal diagonal, int capState) {
        return Ids.of(Fishtastic.MOD_ID, "block/" + shape.modelPathPrefix() + "/fish_tank_frame_corner_" + cornerSuffix(diagonal) + "_" + capState);
    }

    private static ResourceLocation cornerGlassFillLocation(FishTankShape shape, TankDiagonal diagonal, int capState) {
        return Ids.of(Fishtastic.MOD_ID, "block/" + shape.modelPathPrefix() + "/fish_tank_glass_fill_corner_" + cornerSuffix(diagonal) + "_" + capState);
    }

    private static ResourceLocation edgeFragmentLocation(FishTankShape shape, TankEdgeDiagonal edge) {
        return Ids.of(Fishtastic.MOD_ID, "block/" + shape.modelPathPrefix() + "/fish_tank_frame_edge_" + edgeSuffix(edge));
    }

    private static ResourceLocation edgeGlassFillLocation(FishTankShape shape, TankEdgeDiagonal edge, TankDiagonal corner) {
        return Ids.of(Fishtastic.MOD_ID, "block/" + shape.modelPathPrefix() + "/fish_tank_glass_fill_" + edgeSuffix(edge) + "_" + cornerSuffix(corner));
    }

    /** Matches {@code TankCorner.name().toLowerCase()} in {@code tools/tank-shape-gen}'s datagen naming. */
    private static String cornerSuffix(TankDiagonal diagonal) {
        return switch (diagonal) {
            case NORTHWEST -> "nw";
            case NORTHEAST -> "ne";
            case SOUTHWEST -> "sw";
            case SOUTHEAST -> "se";
        };
    }

    /** Matches {@code TankEdge.name().toLowerCase()} in {@code tools/tank-shape-gen}'s datagen naming. */
    private static String edgeSuffix(TankEdgeDiagonal edge) {
        return switch (edge) {
            case NORTH_UP -> "north_up";
            case NORTH_DOWN -> "north_down";
            case SOUTH_UP -> "south_up";
            case SOUTH_DOWN -> "south_down";
            case EAST_UP -> "east_up";
            case EAST_DOWN -> "east_down";
            case WEST_UP -> "west_up";
            case WEST_DOWN -> "west_down";
        };
    }
}
