package grill24.fishtastic.mcp;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Runs exactly one whitelisted command shape over the bridge: {@code fishtastic cosmetic capture <from>
 * <to> <anchor> <name> [scale]} - see the class docs on {@link McpBridgeServer} for why nothing else
 * (setblock/fill/clone/execute/data/etc.) is exposed here instead of via the structured block RPCs.
 */
public final class McpCommandRunner {
    /** First three tokens must be literally "fishtastic cosmetic capture"; the 4th token must not be one
     *  of the interactive wand-session subcommands, which need a real player physically clicking blocks
     *  and are meaningless over HTTP. */
    private static final Pattern ALLOWED = Pattern.compile(
            "^fishtastic\\s+cosmetic\\s+capture\\s+(?!start\\b|mode\\b|status\\b|cancel\\b|finish\\b)\\S.*$");

    private McpCommandRunner() {}

    public static String run(MinecraftServer server, String command) {
        String trimmed = command.trim();
        if (!ALLOWED.matcher(trimmed).matches()) {
            throw new McpException("Command not permitted via the MCP bridge. Only "
                    + "'fishtastic cosmetic capture <from> <to> <anchor> <name> [scale]' is allowed.");
        }

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new McpException("No player is present in the world to run the command as.");
        }
        ServerPlayer player = players.get(0);

        List<String> feedback = new ArrayList<>();
        CommandSource capturing = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                feedback.add(message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };

        CommandSourceStack source = player.createCommandSourceStack().withSource(capturing);

        try {
            ParseResults<CommandSourceStack> parsed = server.getCommands().getDispatcher().parse(trimmed, source);
            server.getCommands().getDispatcher().execute(parsed);
        } catch (CommandSyntaxException e) {
            throw new McpException("Command failed: " + e.getMessage());
        }

        return String.join("\n", feedback);
    }
}
