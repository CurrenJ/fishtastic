package grill24.fishtastic.neoforge.fishtank;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.model.data.ModelProperty;

public record FishTankModelData(Block frameBlock, Block sandBlock) {
    public static final ModelProperty<FishTankModelData> DATA_PROPERTY = new ModelProperty<>();

    public static final FishTankModelData DEFAULT = new FishTankModelData(Blocks.OAK_PLANKS, Blocks.SAND);
}
