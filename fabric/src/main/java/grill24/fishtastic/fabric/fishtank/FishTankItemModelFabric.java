package grill24.fishtastic.fabric.fishtank;

import com.mojang.serialization.MapCodec;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.client.compositemodel.BlockModelPathResolver;
import grill24.fishtastic.client.compositemodel.CompositeTextureHelper;
import grill24.fishtastic.component.FishTankMaterials;
import grill24.fishtastic.fishtank.FishTankShape;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static grill24.fishtastic.util.Utility.ft;

/**
 * Fabric counterpart of {@code FishTankItemModel} (NeoForge): renders a Fish Tank item
 * stack as a live composite of its {@link FishTankMaterials} component, reusing the
 * same permutation-0 sub-models the in-world block uses, retextured per combo and cached.
 */
public class FishTankItemModelFabric implements ItemModel {
    private final ModelBaker baker;
    private final Map<FishTankShape, ResolvedModel> frameModels;
    private final Map<FishTankShape, ResolvedModel> sandModels;
    private final Map<FishTankShape, ResolvedModel> glassModels;
    private final Matrix4fc transformation;

    private final ConcurrentHashMap<CacheKey, CachedRender> cache = new ConcurrentHashMap<>();

    private record CacheKey(FishTankMaterials materials, FishTankShape shape) {}

    private record CachedRender(QuadCollection quads, Vector3fc[] extents, ModelRenderProperties properties) {}

    public FishTankItemModelFabric(ModelBaker baker, Map<FishTankShape, ResolvedModel> frameModels,
                                    Map<FishTankShape, ResolvedModel> sandModels,
                                    Map<FishTankShape, ResolvedModel> glassModels, Matrix4fc transformation) {
        this.baker = baker;
        this.frameModels = frameModels;
        this.sandModels = sandModels;
        this.glassModels = glassModels;
        this.transformation = transformation;
    }

    @Override
    public void update(ItemStackRenderState output, ItemStack item, ItemModelResolver resolver,
                        ItemDisplayContext displayContext, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        FishTankShape shape = item.getOrDefault(FishtasticDataComponents.FISH_TANK_SHAPE.value(), FishTankShape.STANDARD);
        FishTankMaterials materials = item.getOrDefault(FishtasticDataComponents.FISH_TANK_MATERIALS.value(), FishTankMaterials.defaultMaterials());
        output.appendModelIdentityElement(this);
        output.appendModelIdentityElement(materials);
        output.appendModelIdentityElement(shape);

        CachedRender render = cache.computeIfAbsent(new CacheKey(materials, shape), this::generate);
        if (render == null) return;

        ItemStackRenderState.LayerRenderState layer = output.newLayer();
        layer.setExtents(render::extents);
        layer.setLocalTransform(transformation);
        render.properties().applyToLayer(layer, displayContext);
        layer.prepareQuadList().addAll(render.quads().getAll());
    }

    @Nullable
    private CachedRender generate(CacheKey key) {
        FishTankMaterials materials = key.materials();
        try {
            ResolvedModel frameModel = frameModels.get(key.shape());
            ResolvedModel sandModel = sandModels.get(key.shape());
            ResolvedModel glassModel = glassModels.get(key.shape());
            if (frameModel == null || sandModel == null || glassModel == null) {
                Fishtastic.LOGGER.warn("[FishTankItemModelFabric] no sub-models loaded for shape={} — skipping render.", key.shape());
                return null;
            }

            Material frameTex = CompositeTextureHelper.resolveBlockTexture(materials.frame(), baker, BlockModelPathResolver.getModelLocations(materials.frame()));
            Material sandTex = CompositeTextureHelper.resolveBlockTexture(materials.sand(), baker, BlockModelPathResolver.getModelLocations(materials.sand()));
            Material glassTex = CompositeTextureHelper.resolveBlockTexture(materials.glass(), baker, BlockModelPathResolver.getModelLocations(materials.glass()));
            if (frameTex == null || sandTex == null || glassTex == null) {
                Fishtastic.LOGGER.warn("[FishTankItemModelFabric] could not resolve texture(s) for {}", materials);
                return null;
            }

            TextureSlots frameSlots = CompositeTextureHelper.overrideAllTexture(frameTex, frameModel);
            TextureSlots sandSlots = CompositeTextureHelper.overrideAllTexture(sandTex, sandModel);
            TextureSlots glassSlots = CompositeTextureHelper.overrideAllTexture(glassTex, glassModel);

            QuadCollection frameQuads = bakeGeometry(frameModel, frameSlots);
            QuadCollection sandQuads = bakeGeometry(sandModel, sandSlots);
            QuadCollection glassQuads = bakeGeometry(glassModel, glassSlots);
            if (frameQuads == null || sandQuads == null || glassQuads == null) return null;

            QuadCollection.Builder builder = new QuadCollection.Builder();
            builder.addAll(frameQuads);
            builder.addAll(sandQuads);
            builder.addAll(glassQuads);
            QuadCollection composite = builder.build();

            ModelRenderProperties properties = ModelRenderProperties.fromResolvedModel(baker, frameModel, frameSlots);
            return new CachedRender(composite, computeExtents(composite.getAll()), properties);
        } catch (Exception e) {
            Fishtastic.LOGGER.error("[FishTankItemModelFabric] error generating composite render for {}", materials, e);
            return null;
        }
    }

    /**
     * Bypasses {@link ResolvedModel#bakeTopGeometry} intentionally — its per-ModelState
     * cache ignores the {@link TextureSlots} we pass here on repeat calls.
     */
    @Nullable
    private QuadCollection bakeGeometry(ResolvedModel model, TextureSlots slots) {
        try {
            return model.getTopGeometry().bake(slots, baker, BlockModelRotation.IDENTITY, model);
        } catch (Exception e) {
            Fishtastic.LOGGER.error("[FishTankItemModelFabric] error baking geometry for {}", model.debugName(), e);
            return null;
        }
    }

    private static Vector3fc[] computeExtents(List<BakedQuad> quads) {
        Set<Vector3fc> result = new HashSet<>();
        for (BakedQuad quad : quads) {
            for (int v = 0; v < 4; v++) result.add(quad.position(v));
        }
        return result.toArray(Vector3fc[]::new);
    }

    /** Registers this item model type into vanilla's private {@code ItemModels.ID_MAPPER} via reflection. */
    @SuppressWarnings("unchecked")
    public static void register() {
        try {
            Field field = ItemModels.class.getDeclaredField("ID_MAPPER");
            field.setAccessible(true);
            ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends ItemModel.Unbaked>> idMapper =
                    (ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends ItemModel.Unbaked>>) field.get(null);
            idMapper.put(ft("fish_tank_composite"), Unbaked.MAP_CODEC);
            Fishtastic.LOGGER.info("Registered fish_tank_composite item model type.");
        } catch (ReflectiveOperationException e) {
            Fishtastic.LOGGER.error("Failed to register fish_tank_composite item model type!", e);
        }
    }

    public record Unbaked() implements ItemModel.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            for (FishTankShape shape : FishTankShape.values()) {
                resolver.markDependency(modelLocation(shape, "frame"));
                resolver.markDependency(modelLocation(shape, "sand"));
                resolver.markDependency(modelLocation(shape, "glass"));
            }
        }

        @Override
        public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transformation) {
            ModelBaker baker = context.blockModelBaker();
            Map<FishTankShape, ResolvedModel> frameModels = new EnumMap<>(FishTankShape.class);
            Map<FishTankShape, ResolvedModel> sandModels = new EnumMap<>(FishTankShape.class);
            Map<FishTankShape, ResolvedModel> glassModels = new EnumMap<>(FishTankShape.class);
            for (FishTankShape shape : FishTankShape.values()) {
                frameModels.put(shape, baker.getModel(modelLocation(shape, "frame")));
                sandModels.put(shape, baker.getModel(modelLocation(shape, "sand")));
                glassModels.put(shape, baker.getModel(modelLocation(shape, "glass")));
            }
            return new FishTankItemModelFabric(baker, frameModels, sandModels, glassModels, transformation);
        }
    }

    /** Permutation-0 sub-model id for a shape's part (the "fully closed" tank the item renders). */
    private static Identifier modelLocation(FishTankShape shape, String part) {
        return ft("block/" + shape.modelPathPrefix() + "/fish_tank_" + part + "_0");
    }
}
