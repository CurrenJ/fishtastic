package grill24.fishtastic.mcp;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block read/write bodies for the MCP bridge. Every method here must be called on the owning server
 * thread (see {@link McpBridgeServer}'s use of {@code MinecraftServer.submit(...)}) - none of it is
 * thread-safe on its own, same as any other direct {@code ServerLevel} mutation.
 */
public final class McpBlockOps {
    /** Matches the live setblock/fill volume guard in vanilla; lower than capture's read-only 100,000
     *  cap since a live setBlock triggers lighting/chunk updates per block. */
    private static final long MAX_FILL_VOLUME = 50_000;

    private McpBlockOps() {}

    public static String placeBlock(ServerLevel level, BlockPos pos, String blockStateText) {
        requireInRegion(pos, pos, level);
        BlockState state = parseBlockState(level, blockStateText);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        return BlockStateParser.serialize(level.getBlockState(pos));
    }

    public static int fillBlocks(ServerLevel level, BlockPos from, BlockPos to, String blockStateText, String mode) {
        BlockPos min = new BlockPos(Math.min(from.getX(), to.getX()), Math.min(from.getY(), to.getY()), Math.min(from.getZ(), to.getZ()));
        BlockPos max = new BlockPos(Math.max(from.getX(), to.getX()), Math.max(from.getY(), to.getY()), Math.max(from.getZ(), to.getZ()));

        long volume = (long) (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
        if (volume > MAX_FILL_VOLUME) {
            throw new McpException("Region too large (" + volume + " blocks) - max " + MAX_FILL_VOLUME + " for fill_blocks.");
        }

        // Validated against the *entire* requested box up front, before any setBlock call, so a fill
        // never starts in-bounds and silently drifts out partway through.
        requireInRegion(min, max, level);
        BlockState state = parseBlockState(level, blockStateText);
        boolean keepExisting = "keep".equalsIgnoreCase(mode);

        int placed = 0;
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (keepExisting && !level.getBlockState(pos).isAir()) {
                        continue;
                    }
                    level.setBlock(pos, state, Block.UPDATE_ALL);
                    placed++;
                }
            }
        }
        return placed;
    }

    public static String getBlock(ServerLevel level, BlockPos pos) {
        return BlockStateParser.serialize(level.getBlockState(pos));
    }

    private static void requireInRegion(BlockPos min, BlockPos max, ServerLevel level) {
        McpRegion region = McpBridgeState.getRegion();
        if (region == null) {
            throw new McpException("No MCP region configured. Run /fishtastic mcp region set <from> <to> first.");
        }
        if (!region.contains(min, max, level.dimension())) {
            throw new McpException("Target position(s) fall outside the configured MCP region "
                    + region.min().toShortString() + " - " + region.max().toShortString()
                    + " in " + region.dimension().identifier() + ".");
        }
    }

    private static BlockState parseBlockState(ServerLevel level, String blockStateText) {
        HolderLookup<Block> lookup = level.getServer().registryAccess().lookupOrThrow(Registries.BLOCK);
        try {
            BlockStateParser.BlockResult result = BlockStateParser.parseForBlock(lookup, new StringReader(blockStateText), false);
            return result.blockState();
        } catch (CommandSyntaxException e) {
            throw new McpException("Invalid block state '" + blockStateText + "': " + e.getMessage());
        }
    }
}
