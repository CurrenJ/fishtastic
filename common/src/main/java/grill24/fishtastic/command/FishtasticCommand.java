package grill24.fishtastic.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import grill24.fishtastic.compat.CompatUtil;
import grill24.fishtastic.compat.GelatinOpenMenuCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.ClickEvent;

/**
 * Command to open the Fishtastic UI screen.
 */
public class FishtasticCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("fishtastic")
                .executes(FishtasticCommand::openScreen)
        );
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
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://www.curseforge.com/minecraft/mc-mods/gelatin-ui"))
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
