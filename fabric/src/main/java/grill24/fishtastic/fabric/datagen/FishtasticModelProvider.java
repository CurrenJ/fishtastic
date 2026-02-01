package grill24.fishtastic.fabric.datagen;

import grill24.fishtastic.FishtasticBlocks;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.minecraft.core.Holder;
import net.minecraft.data.models.BlockModelGenerators;
import net.minecraft.data.models.ItemModelGenerators;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;

public class FishtasticModelProvider extends FabricModelProvider {
    public FishtasticModelProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generateBlockStateModels(BlockModelGenerators blockModelGenerators) {
        // Generate models for borderless stained glass (all colors)
        for (DyeColor color : DyeColor.values()) {
            Holder<Block> borderlessBlock = FishtasticBlocks.BORDERLESS_STAINED_GLASS.get(color);
            blockModelGenerators.createTrivialCube(borderlessBlock.value());
        }

        // Generate models for clear stained glass (all colors)
        for (DyeColor color : DyeColor.values()) {
            Holder<Block> clearBlock = FishtasticBlocks.CLEAR_STAINED_GLASS.get(color);
            blockModelGenerators.createTrivialCube(clearBlock.value());
        }
    }

    @Override
    public void generateItemModels(ItemModelGenerators itemModelGenerators) {
    }
}
