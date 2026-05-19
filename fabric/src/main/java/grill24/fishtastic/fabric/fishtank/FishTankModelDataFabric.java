package grill24.fishtastic.fabric.fishtank;

import grill24.fishtastic.FishtasticBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.EnumSet;
import java.util.Set;

public record FishTankModelDataFabric(Block frameBlock, Block sandBlock, Block glassBlock, Set<Direction> openFaces) {

    public static final FishTankModelDataFabric DEFAULT = new FishTankModelDataFabric(
            Blocks.OAK_PLANKS, Blocks.SAND,
            FishtasticBlocks.CLEAR_STAINED_GLASS.get(DyeColor.BLUE).value(),
            EnumSet.noneOf(Direction.class)
    );

    public int getPermutationIndex() {
        int index = 0;
        for (Direction dir : Direction.values()) {
            if (openFaces.contains(dir)) {
                index |= (1 << dir.ordinal());
            }
        }
        return index;
    }
}
