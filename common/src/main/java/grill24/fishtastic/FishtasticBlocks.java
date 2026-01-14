package grill24.fishtastic;

import grill24.fishtastic.architectury.RegistrationApiSided;
import grill24.fishtastic.block.FishTankBlock;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Block;

public class FishtasticBlocks {
    public static Holder<Block> FISH_TANK;

    public static void registerBlocks() {
        FISH_TANK = RegistrationApiSided.getInstance().registerBlock("fish_tank", loc -> new FishTankBlock(Block.Properties.of()));
    }
}
