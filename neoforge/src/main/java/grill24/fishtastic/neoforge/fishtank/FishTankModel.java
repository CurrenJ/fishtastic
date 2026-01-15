package grill24.fishtastic.neoforge.fishtank;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import com.mojang.datafixers.util.Either;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.neoforge.FishtasticConfig;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import com.electronwill.nightconfig.core.Config;

public final class FishTankModel implements IUnbakedGeometry<FishTankModel> {
    // Store context needed for on-demand model generation
    private Function<ResourceLocation, UnbakedModel> modelGetter;
    private IGeometryBakingContext context;

    private FishTankModel() {
    }

    @Override
    public BakedModel bake(IGeometryBakingContext ctx, ModelBaker bakery, Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState, ItemOverrides overrides) {
        // Generate initial models now that tags are available
        var bakedModels = generateModelsForConfiguredTags(bakery, spriteGetter, modelState);

        // Pass the necessary context to the baked model for on-demand generation
        return new FishTankBakedModel(bakedModels, modelGetter, ctx, bakery, spriteGetter, modelState);
    }

    @Override
    public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, IGeometryBakingContext ctx) {
        // Just store the context - don't generate models yet as tags aren't ready
        this.modelGetter = modelGetter;
        this.context = ctx;
    }

    /**
     * Generate baked models for blocks from configured tags. This is called during bake() when tags are available.
     */
    private Map<FishTankModelData, FishTankBakedModel.CompositeModelData> generateModelsForConfiguredTags(
            ModelBaker bakery,
            Function<Material, TextureAtlasSprite> spriteGetter,
            ModelState modelState) {

        var bakedModels = new HashMap<FishTankModelData, FishTankBakedModel.CompositeModelData>();

        // Pre-generate models for blocks in configured tags
        for (Config entry : FishtasticConfig.STARTUP.customFishTankFrameTypes.get()) {
            if (entry.isEmpty())
                continue;

            String blocksStr = entry.get("blocks");
            if (blocksStr == null || !blocksStr.startsWith("#"))
                continue;

            String idStr = entry.get("id");
            var tagKey = TagKey.create(Registries.BLOCK, ResourceLocation.parse(blocksStr.substring(1)));
            var frameTagEntries = BuiltInRegistries.BLOCK.getTagOrEmpty(tagKey);

            // Generate models for all blocks in this tag
            for (var blockHolder : frameTagEntries) {
                Block frameBlock = blockHolder.value();

                Fishtastic.LOGGER.info("Pre-generating Fish Tank models for tag {} - block {}",
                    idStr, BuiltInRegistries.BLOCK.getKey(frameBlock));

                // Generate all 64 permutations of frame models for this block
                for (int permutation = 0; permutation < 64; permutation++) {
                    BakedModel frameModel = generateFrameModelForBlock(frameBlock, Blocks.BLUE_STAINED_GLASS, bakery, spriteGetter, modelState, permutation);
                    // For pre-generation, use default sand block (can be changed at runtime)
                    BakedModel sandModel = generateModelForBlock(Blocks.SAND, bakery, spriteGetter, modelState, "sand");

                    if (frameModel != null && sandModel != null) {
                        var composite = new FishTankBakedModel.CompositeModelData(frameModel, sandModel);
                        var openFaces = FishTankModelData.openFacesFromIndex(permutation);
                        bakedModels.put(new FishTankModelData(frameBlock, Blocks.SAND, Blocks.BLUE_STAINED_GLASS, openFaces), composite);
                    }
                }
            }
        }

        // Ensure DEFAULT model exists (permutation 0 - all faces closed)
        if (!bakedModels.containsKey(FishTankModelData.DEFAULT)) {
            Fishtastic.LOGGER.info("Pre-generating default Fish Tank model");
            BakedModel defaultFrameModel = generateFrameModelForBlock(Blocks.OAK_PLANKS, Blocks.BLUE_STAINED_GLASS, bakery, spriteGetter, modelState, 0);
            BakedModel defaultSandModel = generateModelForBlock(Blocks.SAND, bakery, spriteGetter, modelState, "sand");

            if (defaultFrameModel != null && defaultSandModel != null) {
                var composite = new FishTankBakedModel.CompositeModelData(defaultFrameModel, defaultSandModel);
                bakedModels.put(FishTankModelData.DEFAULT, composite);
            } else {
                // Ultimate fallback
                Fishtastic.LOGGER.error("Failed to generate default model! Using cube fallback.");
                var fallbackModel = modelGetter.apply(ResourceLocation.withDefaultNamespace("block/cube"));
                var bakedFallback = fallbackModel.bake(bakery, spriteGetter, modelState);
                var composite = new FishTankBakedModel.CompositeModelData(bakedFallback, bakedFallback);
                bakedModels.put(FishTankModelData.DEFAULT, composite);
            }
        }

        return bakedModels;
    }

    /**
     * Generate a frame model for a specific block with a given permutation index.
     */
    private static final String FRAME_MODELS_LOCATION = "block/fishtankbase/fish_tank_frame_";
    BakedModel generateFrameModelForBlock(Block frameBlock, Block glassBlock, ModelBaker bakery,
                                          Function<Material, TextureAtlasSprite> spriteGetter,
                                          ModelState modelState, int permutationIndex) {
        try {
            var frameBlockModel = (BlockModel) modelGetter.apply(ModelLocationUtils.getModelLocation(frameBlock));
            var glassBlockModel = (BlockModel) modelGetter.apply(ModelLocationUtils.getModelLocation(glassBlock));

            var textureMap = buildFrameAndGlassTextureMap(frameBlockModel.textureMap, glassBlockModel.textureMap);

            // Use the permutation-specific frame model
            ResourceLocation parent = ResourceLocation.fromNamespaceAndPath(Fishtastic.MOD_ID, FRAME_MODELS_LOCATION + permutationIndex);

            var unbakedModel = new BlockModel(parent, List.of(), textureMap,
                context.useAmbientOcclusion(), null, context.getTransforms(), List.of());
            unbakedModel.name = context.getModelName() + "[frame:" + BuiltInRegistries.BLOCK.getKey(frameBlock) +
                "_glass:" + BuiltInRegistries.BLOCK.getKey(glassBlock) + "_p" + permutationIndex + "]";
            unbakedModel.resolveParents(modelGetter);

            // Bake the model
            return unbakedModel.bake(bakery, spriteGetter, modelState);
        } catch (Exception e) {
            Fishtastic.LOGGER.error("Failed to generate Fish Tank frame model for block {} with glass {} and permutation {}",
                BuiltInRegistries.BLOCK.getKey(frameBlock), BuiltInRegistries.BLOCK.getKey(glassBlock), permutationIndex, e);
            return null;
        }
    }

    /**
     * Generate a baked model for a specific block with a label.
     */
    BakedModel generateModelForBlock(Block block, ModelBaker bakery,
                                     Function<Material, TextureAtlasSprite> spriteGetter,
                                     ModelState modelState, String modelType) {
        try {
            var blockModel = (BlockModel) modelGetter.apply(ModelLocationUtils.getModelLocation(block));

            var textureMap = buildTextureMap(blockModel.textureMap);

            // Create new BlockModel with parent and textures
            ResourceLocation parent = FishTankBakedModel.getBaseModel(modelType);
            var unbakedModel = new BlockModel(parent, List.of(), textureMap,
                context.useAmbientOcclusion(), null, context.getTransforms(), List.of());
            unbakedModel.name = context.getModelName() + "[" + modelType + ":" + BuiltInRegistries.BLOCK.getKey(block) + "]";
            unbakedModel.resolveParents(modelGetter);

            // Bake the model
            return unbakedModel.bake(bakery, spriteGetter, modelState);
        } catch (Exception e) {
            Fishtastic.LOGGER.error("Failed to generate Fish Tank {} model for block {}",
                modelType, BuiltInRegistries.BLOCK.getKey(block), e);
            return null;
        }
    }

    public enum Loader implements IGeometryLoader<FishTankModel> {
        INSTANCE;

        @Override
        public FishTankModel read(JsonObject jsonObject, JsonDeserializationContext deserializationContext) throws JsonParseException {
            return new FishTankModel();
        }
    }

    private Map<String, Either<Material, String>> buildTextureMap(Map<String, Either<Material, String>> textureMapping) {
        // Build the texture map that would normally come from JSON
        Map<String, Either<Material, String>> map = new HashMap<>();

        // Get the block texture from the source block model
        Either<Material, String> blockTextureEither = textureMapping.get("all");
        if (blockTextureEither == null) {
            Fishtastic.LOGGER.error("Error building Fish Tank model: Source block model is missing 'all' texture");
            return map;
        }
        ResourceLocation blockTexture;
        if (blockTextureEither.left().isPresent()) {
            blockTexture = blockTextureEither.left().get().texture();
        } else {
            blockTexture = ResourceLocation.withDefaultNamespace(blockTextureEither.right().get());
        }

        // Convert ResourceLocations to Materials (the format BlockModel expects)
        map.put("side", Either.left(new Material(InventoryMenu.BLOCK_ATLAS, blockTexture)));
        map.put("bottom", Either.left(new Material(InventoryMenu.BLOCK_ATLAS, blockTexture)));
        map.put("top", Either.left(new Material(InventoryMenu.BLOCK_ATLAS, blockTexture)));

        return map;
    }

    private Map<String, Either<Material, String>> buildFrameAndGlassTextureMap(
            Map<String, Either<Material, String>> frameTextureMapping,
            Map<String, Either<Material, String>> glassTextureMapping) {
        // Build the texture map with both frame and glass textures
        Map<String, Either<Material, String>> map = new HashMap<>();

        // Get the frame texture from the source block model
        Either<Material, String> frameTextureEither = frameTextureMapping.get("all");
        if (frameTextureEither == null) {
            Fishtastic.LOGGER.error("Error building Fish Tank model: Frame block model is missing 'all' texture");
            return map;
        }
        ResourceLocation frameTexture;
        if (frameTextureEither.left().isPresent()) {
            frameTexture = frameTextureEither.left().get().texture();
        } else {
            frameTexture = ResourceLocation.withDefaultNamespace(frameTextureEither.right().get());
        }

        // Get the glass texture from the source block model
        Either<Material, String> glassTextureEither = glassTextureMapping.get("all");
        if (glassTextureEither == null) {
            Fishtastic.LOGGER.error("Error building Fish Tank model: Glass block model is missing 'all' texture");
            return map;
        }
        ResourceLocation glassTexture;
        if (glassTextureEither.left().isPresent()) {
            glassTexture = glassTextureEither.left().get().texture();
        } else {
            glassTexture = ResourceLocation.withDefaultNamespace(glassTextureEither.right().get());
        }

        // Convert ResourceLocations to Materials (the format BlockModel expects)
        map.put("frame", Either.left(new Material(InventoryMenu.BLOCK_ATLAS, frameTexture)));
        map.put("glass", Either.left(new Material(InventoryMenu.BLOCK_ATLAS, glassTexture)));

        return map;
    }

}
