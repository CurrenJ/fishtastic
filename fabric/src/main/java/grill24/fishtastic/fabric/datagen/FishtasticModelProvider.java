package grill24.fishtastic.fabric.datagen;

import grill24.fishtastic.FishtasticBlocks;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.minecraft.core.Holder;
import net.minecraft.data.models.BlockModelGenerators;
import net.minecraft.data.models.ItemModelGenerators;
import net.minecraft.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.data.models.blockstates.Variant;
import net.minecraft.data.models.blockstates.VariantProperties;
import net.minecraft.data.models.model.ModelTemplates;
import net.minecraft.data.models.model.TextureMapping;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;

import java.util.Map;
import java.util.Optional;

public class FishtasticModelProvider extends FabricModelProvider {
    public FishtasticModelProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generateBlockStateModels(BlockModelGenerators blockModelGenerators) {
        // Generate models for borderless stained glass (all colors)
        generateGlassModels(blockModelGenerators, FishtasticBlocks.BORDERLESS_STAINED_GLASS);

        // Generate models for clear stained glass (all colors)
        generateGlassModels(blockModelGenerators, FishtasticBlocks.CLEAR_STAINED_GLASS);
    }

    private void generateGlassModels(BlockModelGenerators blockModelGenerators, Map<DyeColor, Holder<Block>> glassBlocks) {
        for (DyeColor color : DyeColor.values()) {
            Holder<Block> block = glassBlocks.get(color);
            ResourceLocation textureLoc = getGlassTextureLoc(block);
            ResourceLocation modelLoc = getGlassModelLoc(block);

            generateModelsForGlassBlock(blockModelGenerators, modelLoc, textureLoc, block);
        }
    }

    private static void generateModelsForGlassBlock(BlockModelGenerators blockModelGenerators, ResourceLocation modelLoc, ResourceLocation textureLoc, Holder<Block> block) {
        // Create block model in glass/ subdirectory
        ModelTemplates.CUBE_ALL.create(modelLoc, TextureMapping.cube(textureLoc), blockModelGenerators.modelOutput);

        // Create blockstate pointing to the model in glass subdirectory
        blockModelGenerators.blockStateOutput.accept(
            MultiVariantGenerator.multiVariant(block.value(),
                Variant.variant().with(VariantProperties.MODEL, modelLoc))
        );

        // Create item model in default directory (required by Minecraft's hardcoded item model lookup)
        // The item model will reference the block model in the glass subdirectory
        // This changes in a later Minecraft version, but is required for 1.21.1.
        blockModelGenerators.delegateItemModel(block.value(), modelLoc);
    }

    @Override
    public void generateItemModels(ItemModelGenerators itemModelGenerators) {
    }

    private static ResourceLocation getGlassTextureLoc(Holder<Block> block) {
        Optional<ResourceKey<Block>> key = block.unwrapKey();
        if (key.isPresent()) {
            return key.get().location().withPrefix("block/glass/");
        } else {
            throw new RuntimeException("Failed to access block holder.");
        }
    }

    private static ResourceLocation getGlassModelLoc(Holder<Block> block) {
        Optional<ResourceKey<Block>> key = block.unwrapKey();
        if (key.isPresent()) {
            return key.get().location().withPrefix("block/glass/");
        } else {
            throw new RuntimeException("Failed to access block holder.");
        }
    }
}
