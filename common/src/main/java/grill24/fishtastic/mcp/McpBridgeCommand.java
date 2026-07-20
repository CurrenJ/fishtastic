package grill24.fishtastic.mcp;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * {@code /fishtastic mcp start|stop|status|region set|region clear} - controls the {@link McpBridgeServer}.
 * See that class's docs for the full design (dev-only, loopback, token-guarded, region-restricted).
 */
public final class McpBridgeCommand {
    private static final int DEFAULT_PORT = 25599;

    private McpBridgeCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("mcp")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("start")
                        .executes(ctx -> start(ctx, DEFAULT_PORT))
                        .then(Commands.argument("port", IntegerArgumentType.integer(1, 65535))
                                .executes(ctx -> start(ctx, IntegerArgumentType.getInteger(ctx, "port")))))
                .then(Commands.literal("stop").executes(McpBridgeCommand::stop))
                .then(Commands.literal("status").executes(McpBridgeCommand::status))
                .then(Commands.literal("region")
                        .then(Commands.literal("set")
                                .then(Commands.argument("from", BlockPosArgument.blockPos())
                                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                .executes(ctx -> regionSet(ctx,
                                                        BlockPosArgument.getLoadedBlockPos(ctx, "from"),
                                                        BlockPosArgument.getLoadedBlockPos(ctx, "to"))))))
                        .then(Commands.literal("clear").executes(McpBridgeCommand::regionClear)));
    }

    private static int start(CommandContext<CommandSourceStack> ctx, int port) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();

        if (server.isDedicatedServer()) {
            source.sendFailure(Component.literal("The MCP bridge only runs on a singleplayer/integrated server."));
            return 0;
        }
        if (!(source.getEntity() instanceof ServerPlayer player) || !server.isSingleplayerOwner(player.nameAndId())) {
            source.sendFailure(Component.literal("The MCP bridge can only be started by the singleplayer world owner."));
            return 0;
        }
        if (!McpEnvironmentCheck.isDevelopmentEnvironment()) {
            source.sendFailure(Component.literal("The MCP bridge is only available in a development environment build."));
            return 0;
        }

        if (McpBridgeState.isRunning()) {
            McpBridgeState.getActiveServer().stop();
        }

        try {
            McpBridgeServer.start(server, port);
            String token = McpBridgeState.getToken();
            source.sendSuccess(() -> Component.literal("MCP bridge started on port " + McpBridgeState.getPort() + ". Token: ")
                    .append(Component.literal(token)
                            .withStyle(style -> style
                                    .withColor(ChatFormatting.AQUA)
                                    .withUnderlined(true)
                                    .withClickEvent(new ClickEvent.CopyToClipboard(token)))), false);
            return 1;
        } catch (IOException e) {
            source.sendFailure(Component.literal("Failed to start MCP bridge on port " + port + ": " + e.getMessage()
                    + " - try a different port with /fishtastic mcp start <port>."));
            return 0;
        }
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!McpBridgeState.isRunning()) {
            source.sendFailure(Component.literal("The MCP bridge is not running."));
            return 0;
        }
        McpBridgeState.getActiveServer().stop();
        source.sendSuccess(() -> Component.literal("MCP bridge stopped."), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!McpBridgeState.isRunning()) {
            source.sendSuccess(() -> Component.literal("MCP bridge is not running."), false);
            return 1;
        }
        Duration uptime = Duration.between(McpBridgeState.getStartedAt(), Instant.now());
        McpRegion region = McpBridgeState.getRegion();
        String regionText = region == null ? "not set"
                : region.min().toShortString() + " to " + region.max().toShortString() + " in " + region.dimension().identifier();
        source.sendSuccess(() -> Component.literal("MCP bridge running on port " + McpBridgeState.getPort()
                + " (up " + uptime.toSeconds() + "s). Region: " + regionText), false);
        return 1;
    }

    private static int regionSet(CommandContext<CommandSourceStack> ctx, BlockPos from, BlockPos to) {
        CommandSourceStack source = ctx.getSource();
        BlockPos min = new BlockPos(Math.min(from.getX(), to.getX()), Math.min(from.getY(), to.getY()), Math.min(from.getZ(), to.getZ()));
        BlockPos max = new BlockPos(Math.max(from.getX(), to.getX()), Math.max(from.getY(), to.getY()), Math.max(from.getZ(), to.getZ()));
        var dimension = source.getLevel().dimension();
        McpBridgeState.setRegion(new McpRegion(min, max, dimension));
        source.sendSuccess(() -> Component.literal("MCP region set to " + min.toShortString() + " - " + max.toShortString()
                + " in " + dimension.identifier()), false);
        return 1;
    }

    private static int regionClear(CommandContext<CommandSourceStack> ctx) {
        McpBridgeState.setRegion(null);
        ctx.getSource().sendSuccess(() -> Component.literal("MCP region cleared."), false);
        return 1;
    }
}
