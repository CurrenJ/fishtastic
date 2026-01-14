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
    private Map<FishTankModelData, BakedModel> generateModelsForConfiguredTags(
            ModelBaker bakery,
            Function<Material, TextureAtlasSprite> spriteGetter,
            ModelState modelState) {

        var bakedModels = new HashMap<FishTankModelData, BakedModel>();

        // Pre-generate models for blocks in configured tags
        for (Config entry : FishtasticConfig.STARTUP.customFishTankFrameTypes.get()) {
            if (entry.isEmpty())
                continue;

            String blocksStr = entry.get("blocks");
            if (blocksStr == null || !blocksStr.startsWith("#"))
                continue;

            String idStr = entry.get("id");
            var tagKey = TagKey.create(Registries.BLOCK, ResourceLocation.parse(blocksStr.substring(1)));
            var tagEntries = BuiltInRegistries.BLOCK.getTagOrEmpty(tagKey);

            // Generate models for all blocks in this tag
            for (var blockHolder : tagEntries) {
                Block block = blockHolder.value();

                Fishtastic.LOGGER.info("Pre-generating Fish Tank model for tag {} - block {}",
                    idStr, BuiltInRegistries.BLOCK.getKey(block));

                BakedModel bakedModel = generateModelForBlock(block, bakery, spriteGetter, modelState);
                if (bakedModel != null) {
                    bakedModels.put(new FishTankModelData(block), bakedModel);
                }
            }
        }

        // Ensure DEFAULT model exists
        if (!bakedModels.containsKey(FishTankModelData.DEFAULT)) {
            Fishtastic.LOGGER.info("Pre-generating default Fish Tank model for oak planks");
            BakedModel defaultModel = generateModelForBlock(Blocks.OAK_PLANKS, bakery, spriteGetter, modelState);
            if (defaultModel != null) {
                bakedModels.put(FishTankModelData.DEFAULT, defaultModel);
            } else {
                // Ultimate fallback
                Fishtastic.LOGGER.error("Failed to generate default model! Using cube fallback.");
                var fallbackModel = modelGetter.apply(ResourceLocation.withDefaultNamespace("block/cube"));
                bakedModels.put(FishTankModelData.DEFAULT, fallbackModel.bake(bakery, spriteGetter, modelState));
            }
        }

        return bakedModels;
    }

    /**
     * Generate a baked model for a specific block.
     */
    BakedModel generateModelForBlock(Block block, ModelBaker bakery,
                                     Function<Material, TextureAtlasSprite> spriteGetter,
                                     ModelState modelState) {
        try {
            var blockModel = (BlockModel) modelGetter.apply(ModelLocationUtils.getModelLocation(block));

            if (blockModel.getParentLocation() != null) {
                Fishtastic.LOGGER.warn("Block model {} has parent - may not work correctly for Fish Tank frame",
                    BuiltInRegistries.BLOCK.getKey(block));
            }

            var textureMap = buildTextureMap(blockModel.textureMap);

            // Create new BlockModel with parent and textures
            final ResourceLocation parent = ResourceLocation.withDefaultNamespace("block/stairs"); // TODO: TEMP parent model
            var unbakedModel = new BlockModel(parent, List.of(), textureMap,
                context.useAmbientOcclusion(), null, context.getTransforms(), List.of());
            unbakedModel.name = context.getModelName() + "[" + BuiltInRegistries.BLOCK.getKey(block) + "]";
            unbakedModel.resolveParents(modelGetter);

            // Bake the model
            return unbakedModel.bake(bakery, spriteGetter, modelState);
        } catch (Exception e) {
            Fishtastic.LOGGER.error("Failed to generate Fish Tank model for block {}",
                BuiltInRegistries.BLOCK.getKey(block), e);
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

}
