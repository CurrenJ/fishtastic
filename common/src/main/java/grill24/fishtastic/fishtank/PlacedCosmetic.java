package grill24.fishtastic.fishtank;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A cosmetic decoration placed at a specific grid cell in a fish tank.
 * Stores the full BlockState so properties like sea-pickle count are preserved.
 * {@code height} is the number of stacked segments for multi-block cosmetics (e.g. kelp).
 * Transform (offset, rotation, scale) is looked up from CosmeticTransforms by block type.
 */
public record PlacedCosmetic(BlockState blockState, int height) {
    public PlacedCosmetic(BlockState blockState) {
        this(blockState, 1);
    }

    public Block block() {
        return blockState.getBlock();
    }
}
