package grill24.fishtastic.neoforge.fishtank;

import grill24.fishtastic.Fishtastic;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;

import com.mojang.datafixers.util.Either;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static grill24.fishtastic.util.Utility.ft;

public final class FishTankBakedModel extends BakedModelWrapper<BakedModel> {
    // Cache structure to hold both frame and sand models
    static class CompositeModelData {
        final BakedModel frameModel;
        final BakedModel sandModel;

        CompositeModelData(BakedModel frameModel, BakedModel sandModel) {
            this.frameModel = frameModel;
            this.sandModel = sandModel;
        }
    }

    private final Map<FishTankModelData, CompositeModelData> bakedFishTankModels;
    // Thread-safe cache for on-demand generated models
    private final ConcurrentHashMap<FishTankModelData, CompositeModelData> onDemandCache = new ConcurrentHashMap<>();

    // Context for on-demand model generation
    private final Function<ResourceLocation, UnbakedModel> modelGetter;
    private final IGeometryBakingContext context;
    private final ModelBaker bakery;
    private final Function<Material, TextureAtlasSprite> spriteGetter;
    private final ModelState modelState;

    private final ItemOverrides itemOverrides;

    FishTankBakedModel(
            Map<FishTankModelData, CompositeModelData> bakedFishTankModels,
            Function<ResourceLocation, UnbakedModel> modelGetter,
            IGeometryBakingContext context,
            ModelBaker bakery,
            Function<Material, TextureAtlasSprite> spriteGetter,
            ModelState modelState) {
        super(Objects.requireNonNull(bakedFishTankModels.get(FishTankModelData.DEFAULT)).frameModel); //default model
        this.bakedFishTankModels = bakedFishTankModels;
        this.modelGetter = modelGetter;
        this.context = context;
        this.bakery = bakery;
        this.spriteGetter = spriteGetter;
        this.modelState = modelState;

        this.itemOverrides = new ItemOverrides() {
            @Override
            public BakedModel resolve(BakedModel pModel, ItemStack pStack, ClientLevel pLevel, LivingEntity pEntity, int pSeed) {
                // TODO: Read frame block from item NBT when implemented
                return FishTankBakedModel.this;
            }
        };
    }

    private CompositeModelData getCompositeModelFor(ModelData modelData) {
        var data = Objects.requireNonNullElse(modelData.get(FishTankModelData.DATA_PROPERTY), FishTankModelData.DEFAULT);
        return getOrGenerateCompositeModel(data);
    }

    /**
     * Get a composite model, generating it on-demand if it doesn't exist yet.
     */
    private CompositeModelData getOrGenerateCompositeModel(FishTankModelData data) {
        // Check if we already have it pre-baked
        CompositeModelData composite = bakedFishTankModels.get(data);
        if (composite != null) {
            Fishtastic.LOGGER.info("Found pre-baked composite model for frame={}, sand={}",
                BuiltInRegistries.BLOCK.getKey(data.frameBlock()),
                BuiltInRegistries.BLOCK.getKey(data.sandBlock()));
            return composite;
        }

        // Check on-demand cache
        composite = onDemandCache.get(data);
        if (composite != null) {
            Fishtastic.LOGGER.info("Found cached on-demand composite model for frame={}, sand={}",
                BuiltInRegistries.BLOCK.getKey(data.frameBlock()),
                BuiltInRegistries.BLOCK.getKey(data.sandBlock()));
            return composite;
        }

        // Generate on-demand
        Fishtastic.LOGGER.info("Generating composite model on-demand for frame={}, sand={}",
            BuiltInRegistries.BLOCK.getKey(data.frameBlock()),
            BuiltInRegistries.BLOCK.getKey(data.sandBlock()));
        try {
            composite = generateCompositeModel(data);
            if (composite != null) {
                onDemandCache.put(data, composite);
                return composite;
            }
        } catch (Exception e) {
            Fishtastic.LOGGER.error("Failed to generate Fish Tank composite model on-demand for frame={}, sand={}",
                BuiltInRegistries.BLOCK.getKey(data.frameBlock()),
                BuiltInRegistries.BLOCK.getKey(data.sandBlock()), e);
        }

        // Fallback to default
        Fishtastic.LOGGER.warn("Using fallback default composite model for frame={}, sand={}",
            BuiltInRegistries.BLOCK.getKey(data.frameBlock()),
            BuiltInRegistries.BLOCK.getKey(data.sandBlock()));
        return bakedFishTankModels.get(FishTankModelData.DEFAULT);
    }

    /**
     * Generate a composite model with both frame and sand models.
     */
    private CompositeModelData generateCompositeModel(FishTankModelData data) {
        Block frameBlock = data.frameBlock();
        Block sandBlock = data.sandBlock();

        Fishtastic.LOGGER.info("Generating Fish Tank composite model on-demand for frame={}, sand={}",
            BuiltInRegistries.BLOCK.getKey(frameBlock), BuiltInRegistries.BLOCK.getKey(sandBlock));

        // Generate frame model
        Fishtastic.LOGGER.info("Generating FRAME model for block {}", BuiltInRegistries.BLOCK.getKey(frameBlock));
        BakedModel frameModel = generateModelForBlock(frameBlock, "frame");

        // Generate sand model
        Fishtastic.LOGGER.info("Generating SAND model for block {}", BuiltInRegistries.BLOCK.getKey(sandBlock));
        BakedModel sandModel = generateModelForBlock(sandBlock, "sand");

        if (frameModel == null || sandModel == null) {
            Fishtastic.LOGGER.error("Failed to generate one or both models: frame={}, sand={}",
                frameModel != null, sandModel != null);
            return null;
        }

        Fishtastic.LOGGER.info("Successfully created composite model with frame={} and sand={}",
            frameModel.getClass().getSimpleName(), sandModel.getClass().getSimpleName());
        return new CompositeModelData(frameModel, sandModel);
    }

    /**
     * Generate a baked model for a specific block with a given label.
     */
    private BakedModel generateModelForBlock(Block block, String modelType) {
        try {
            var blockModel = (BlockModel) modelGetter.apply(
                ModelLocationUtils.getModelLocation(block));

            if (blockModel.getParentLocation() != null) {
                Fishtastic.LOGGER.warn("Block model {} has parent - may not work correctly for Fish Tank {}",
                    BuiltInRegistries.BLOCK.getKey(block), modelType);
            }

            var textureMap = buildTextureMap(blockModel.textureMap);

            // Create new BlockModel with parent and textures - select parent based on model type
            ResourceLocation parent = getBaseModel(modelType);
            if (parent == null) return null;

            var unbakedModel = new BlockModel(parent, List.of(), textureMap,
                context.useAmbientOcclusion(), null, context.getTransforms(), List.of());
            unbakedModel.name = context.getModelName() + "[" + modelType + ":" + BuiltInRegistries.BLOCK.getKey(block) + "]";
            unbakedModel.resolveParents(modelGetter);

            // Bake the model
            return unbakedModel.bake(bakery, spriteGetter, modelState);
        } catch (Exception e) {
            Fishtastic.LOGGER.error("Failed to generate {} model for block {}",
                modelType, BuiltInRegistries.BLOCK.getKey(block), e);
            return null;
        }
    }

    public static @org.jetbrains.annotations.Nullable ResourceLocation getBaseModel(String modelType) {
        ResourceLocation parent;
        switch (modelType) {
            case "frame" -> parent = ft("block/fish_tank_frame");
            case "sand" -> parent = ft("block/fish_tank_sand");
            default -> {
                Fishtastic.LOGGER.error("Unknown model type '{}' for Fish Tank model generation", modelType);
                return null;
            }
        }
        return parent;
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
        map.put("all", Either.left(new Material(InventoryMenu.BLOCK_ATLAS, blockTexture)));

        return map;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData extraData, @Nullable RenderType renderType) {
        CompositeModelData composite = getCompositeModelFor(extraData);

        // Combine quads from both frame and sand models
        List<BakedQuad> combinedQuads = new java.util.ArrayList<>();
        combinedQuads.addAll(composite.frameModel.getQuads(state, side, rand, extraData, renderType));
        combinedQuads.addAll(composite.sandModel.getQuads(state, side, rand, extraData, renderType));

        return combinedQuads;
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        // Use frame model for particle
        return getCompositeModelFor(data).frameModel.getParticleIcon(data);
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        CompositeModelData composite = getCompositeModelFor(data);
        // Combine render types from both models
        return ChunkRenderTypeSet.union(
            composite.frameModel.getRenderTypes(state, rand, data),
            composite.sandModel.getRenderTypes(state, rand, data)
        );
    }

    @Override
    public ItemOverrides getOverrides() {
        return itemOverrides;
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        return modelData;
    }
}
