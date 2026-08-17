package grill24.fishtastic.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import grill24.fishtastic.compat.CompatUtil;
import grill24.fishtastic.compat.GelatinOpenMenuCompat;
import grill24.fishtastic.env.DevEnvironmentCheck;
import grill24.fishtastic.mcp.McpBridgeCommand;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.ClickEvent;
import java.net.URI;

/**
 * Command to open the Fishtastic UI screen.
 */
public class FishtasticCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("fishtastic")
                .executes(FishtasticCommand::openScreen)
                .then(CosmeticCommand.build())
                .then(SetItemSizeCommand.build())
                .then(SetFishQualityCommand.build())
                .then(SetTankShapeCommand.build())
                .then(ForceQualityCommand.build())
                .then(DebugFishDataCommand.build())
                .then(FishProfileCommand.build())
                .then(FishZoneCommand.build())
                .then(TemperamentCommand.build())
                .then(TestQuestNotifyCommand.build())
                .then(TutorialCommand.build())
                .then(CleanupGoalCommand.build())
                .then(QuestsCommand.build())
                .then(DebugEncyclopediaCommand.build())
                .then(TokenBalanceCommand.build())
                .then(NotificationVolumeCommand.build());

        // Production builds exclude the whole grill24.fishtastic.mcp package from the jar (dev-only
        // tooling) - never reference McpBridgeCommand outside this guard, or the missing class gets
        // resolved and the server fails to start.
        if (DevEnvironmentCheck.isDevelopmentEnvironment()) {
            command.then(McpBridgeCommand.build());
            // Tuning tool for the catch celebration's timings — dev-only, and client-driven, so it
            // never reaches a dedicated server's command tree.
            command.then(CelebrationCommand.build());
        }

        dispatcher.register(command);
    }

    private static int openScreen(CommandContext<CommandSourceStack> context) {
        // __BEGIN:gelatinui
        CommandSourceStack source = context.getSource();

        if (source.getEntity() instanceof ServerPlayer player) {
            if (!CompatUtil.isGelatinUiPresent()) {
                // Append dependency suggestion
                Component installMessage = Component.literal("Install ")
                    .append(Component.literal("Gelatin UI")
                        .withStyle(style -> style
                            .withColor(ChatFormatting.YELLOW)
                            .withClickEvent(new ClickEvent.OpenUrl(URI.create("https://www.curseforge.com/minecraft/mc-mods/gelatin-ui")))
                            .withUnderlined(true)))
                    .append(Component.literal(" for a visual display.")
                        .withStyle(ChatFormatting.YELLOW));
                source.sendSuccess(() -> installMessage, false);
                return 1;
            } else {
                GelatinOpenMenuCompat.openFishtasticMenu(player);
            }
        } else {
            source.sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }
        // __END:gelatinui

        return 1;
    }
}
