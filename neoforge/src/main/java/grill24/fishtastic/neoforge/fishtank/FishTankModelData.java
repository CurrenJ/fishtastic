package grill24.fishtastic.neoforge.fishtank;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import java.util.EnumSet;
import java.util.Set;

public record FishTankModelData(Block frameBlock, Block sandBlock, Block glassBlock, Set<Direction> openFaces) {
    public static final ModelProperty<FishTankModelData> DATA_PROPERTY = new ModelProperty<>();

    public static final FishTankModelData DEFAULT = new FishTankModelData(Blocks.OAK_PLANKS, Blocks.SAND, Blocks.BLUE_STAINED_GLASS, EnumSet.noneOf(Direction.class));

    /**
     * Create a FishTankModelData with all faces closed (standalone tank)
     */
    public FishTankModelData(Block frameBlock, Block sandBlock, Block glassBlock) {
        this(frameBlock, sandBlock, glassBlock, EnumSet.noneOf(Direction.class));
    }

    /**
     * Get the permutation index for this configuration (0-63)
     * Each of the 6 faces can be open or closed, giving 2^6 = 64 combinations
     */
    public int getPermutationIndex() {
        int index = 0;
        for (Direction dir : Direction.values()) {
            if (openFaces.contains(dir)) {
                index |= (1 << dir.ordinal());
            }
        }
        return index;
    }

    /**
     * Create a FishTankModelData from a permutation index (0-63)
     */
    public static Set<Direction> openFacesFromIndex(int index) {
        Set<Direction> openFaces = EnumSet.noneOf(Direction.class);
        for (Direction dir : Direction.values()) {
            if ((index & (1 << dir.ordinal())) != 0) {
                openFaces.add(dir);
            }
        }
        return openFaces;
    }
}
