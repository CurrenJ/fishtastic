package grill24.fishtastic.mcp;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The sandbox bounding box the MCP bridge's mutating operations (place_block/fill_blocks) are
 * restricted to, set via {@code /fishtastic mcp region set <from> <to>}.
 */
public record McpRegion(BlockPos min, BlockPos max, ResourceKey<Level> dimension) {
    public boolean contains(BlockPos targetMin, BlockPos targetMax, ResourceKey<Level> targetDimension) {
        if (!dimension.equals(targetDimension)) {
            return false;
        }
        return targetMin.getX() >= min.getX() && targetMax.getX() <= max.getX()
                && targetMin.getY() >= min.getY() && targetMax.getY() <= max.getY()
                && targetMin.getZ() >= min.getZ() && targetMax.getZ() <= max.getZ();
    }
}
