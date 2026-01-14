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
import net.minecraft.world.level.block.Blocks;
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

public final class FishTankBakedModel extends BakedModelWrapper<BakedModel> {
    private final Map<FishTankModelData, BakedModel> bakedFishTankModels;
    // Thread-safe cache for on-demand generated models
    private final ConcurrentHashMap<FishTankModelData, BakedModel> onDemandCache = new ConcurrentHashMap<>();

    // Context for on-demand model generation
    private final Function<ResourceLocation, UnbakedModel> modelGetter;
    private final IGeometryBakingContext context;
    private final ModelBaker bakery;
    private final Function<Material, TextureAtlasSprite> spriteGetter;
    private final ModelState modelState;

    private final ItemOverrides itemOverrides;

    FishTankBakedModel(
            Map<FishTankModelData, BakedModel> bakedFishTankModels,
            Function<ResourceLocation, UnbakedModel> modelGetter,
            IGeometryBakingContext context,
            ModelBaker bakery,
            Function<Material, TextureAtlasSprite> spriteGetter,
            ModelState modelState) {
        super(Objects.requireNonNull(bakedFishTankModels.get(FishTankModelData.DEFAULT))); //default model
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
                var data = FishTankModelData.DEFAULT;
                return getOrGenerateModel(data);
            }
        };
    }

    private BakedModel getModelFor(ModelData modelData) {
        var data = Objects.requireNonNullElse(modelData.get(FishTankModelData.DATA_PROPERTY), FishTankModelData.DEFAULT);
        return getOrGenerateModel(data);
    }

    /**
     * Get a model, generating it on-demand if it doesn't exist yet.
     */
    private BakedModel getOrGenerateModel(FishTankModelData data) {
        // Check if we already have it pre-baked
        BakedModel model = bakedFishTankModels.get(data);
        if (model != null) {
            return model;
        }

        // Check on-demand cache
        model = onDemandCache.get(data);
        if (model != null) {
            return model;
        }

        // Generate on-demand
        try {
            model = generateModelForBlock(data);
            if (model != null) {
                onDemandCache.put(data, model);
                return model;
            }
        } catch (Exception e) {
            Fishtastic.LOGGER.error("Failed to generate Fish Tank model on-demand for block {}",
                BuiltInRegistries.BLOCK.getKey(data.frameBlock()), e);
        }

        // Fallback to default
        return bakedFishTankModels.get(FishTankModelData.DEFAULT);
    }

    /**
     * Generate a baked model for a specific block on-demand.
     */
    private BakedModel generateModelForBlock(FishTankModelData data) {
        Block block = data.frameBlock();

        Fishtastic.LOGGER.info("Generating Fish Tank model on-demand for block {}",
            BuiltInRegistries.BLOCK.getKey(block));

        var frameSourceBlockModel = (BlockModel) modelGetter.apply(
            ModelLocationUtils.getModelLocation(block));

        if (frameSourceBlockModel.getParentLocation() != null) {
            Fishtastic.LOGGER.warn("Block model {} has parent - may not work correctly for Fish Tank frame",
                BuiltInRegistries.BLOCK.getKey(block));
        }

        var textureMap = buildTextureMap(frameSourceBlockModel.textureMap);

        // Create new BlockModel with parent and textures
        final ResourceLocation parent = ResourceLocation.withDefaultNamespace("block/stairs"); // TODO: TEMP parent model
        var unbakedModel = new BlockModel(parent, List.of(), textureMap,
            context.useAmbientOcclusion(), null, context.getTransforms(), List.of());
        unbakedModel.name = context.getModelName() + "[" + BuiltInRegistries.BLOCK.getKey(block) + "]";
        unbakedModel.resolveParents(modelGetter);

        // Bake the model
        return unbakedModel.bake(bakery, spriteGetter, modelState);
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

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData extraData, @Nullable RenderType renderType) {
        return getModelFor(extraData).getQuads(state, side, rand, extraData, renderType);
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        return getModelFor(data).getParticleIcon(data);
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        return getModelFor(data).getRenderTypes(state, rand, data);
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
