package grill24.fishtastic.fabric.datagen;

import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.FishtasticItems;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.minecraft.data.models.BlockModelGenerators;
import net.minecraft.data.models.ItemModelGenerators;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.data.models.model.ModelTemplates;
import net.minecraft.data.models.model.TextureMapping;
import net.minecraft.data.models.model.TextureSlot;
import net.minecraft.world.item.Items;

public class FishtasticModelProvider extends FabricModelProvider {
    public FishtasticModelProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generateBlockStateModels(BlockModelGenerators blockModelGenerators) {
        // Example block model generation for TEST_BLOCK
//        TextureMapping testBlockTexMap = new TextureMapping().put(TextureSlot.ALL, TextureMapping.getBlockTexture(Blocks.COBBLESTONE));
//        blockModelGenerators.createTrivialBlock(FishtasticBlocks.FISH_TANK.value(), testBlockTexMap, ModelTemplates.CUBE_ALL);
    }

    @Override
    public void generateItemModels(ItemModelGenerators itemModelGenerators) {
        // Model generation for COPPER_FISHING_ROD
        itemModelGenerators.generateFishingRod(FishtasticItems.COPPER_FISHING_ROD.value());
    }
}
