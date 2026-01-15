package grill24.fishtastic;

import grill24.fishtastic.architectury.RegistrationApiSided;
import grill24.fishtastic.block.FishTankBlock;
import net.minecraft.core.Holder;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassBlock;

public class FishtasticBlocks {
    public static Holder<Block> FISH_TANK;

    public static Holder<Block> CLEAR_BLUE_STAINED_GLASS;

    public static void registerBlocks() {
        CLEAR_BLUE_STAINED_GLASS = RegistrationApiSided.getInstance().registerBlock("clear_blue_stained_glass",
                loc -> new StainedGlassBlock(DyeColor.BLUE, Block.Properties.ofFullCopy(Blocks.BLUE_STAINED_GLASS)));

        FISH_TANK = RegistrationApiSided.getInstance().registerBlock("fish_tank",
            loc -> new FishTankBlock(Block.Properties.ofFullCopy(Blocks.GLASS)
                .noOcclusion()  // Allow transparent rendering
        ));
    }
}
