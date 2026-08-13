package grill24.fishtastic.fishtank;

import grill24.fishtastic.FishtasticBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.EnumSet;
import java.util.Set;

/**
 * Immutable snapshot of the block-entity data that drives the fish tank's composite model:
 * which body geometry ({@code shape}) to use, which blocks to use for the frame, fill (sand),
 * and glass layers, and which faces are open (connected to an adjacent tank).
 *
 * <p>Platform adapters pass this around as Fabric render data or a NeoForge {@code ModelProperty} value.
 */
public record FishTankCompositeModelData(FishTankShape shape, Block frameBlock, Block sandBlock, Block glassBlock, Set<Direction> openFaces) {

    public static final FishTankCompositeModelData DEFAULT = new FishTankCompositeModelData(
            FishTankShape.STANDARD, Blocks.OAK_PLANKS, Blocks.SAND,
            FishtasticBlocks.CLEAR_STAINED_GLASS.get(DyeColor.BLUE).value(),
            EnumSet.noneOf(Direction.class)
    );

    public FishTankCompositeModelData(FishTankShape shape, Block frameBlock, Block sandBlock, Block glassBlock) {
        this(shape, frameBlock, sandBlock, glassBlock, EnumSet.noneOf(Direction.class));
    }

    /**
     * Returns a 0–63 bitmask index into the permutation model arrays,
     * where each bit corresponds to a {@link Direction} ordinal being open.
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
