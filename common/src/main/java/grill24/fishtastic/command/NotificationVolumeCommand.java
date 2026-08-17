package grill24.fishtastic.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import grill24.fishtastic.network.NotificationVolumeSyncPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Lets any player set their own volume (0-100) for Fishtastic's notification banner sounds.
 * The setting is purely client-side, so this doesn't gate on operator permission like most
 * other subcommands here — it only ever affects the invoking player's own client.
 *
 * Usage: /fishtastic notifications volume <0-100>
 */
public class NotificationVolumeCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("notifications")
                .then(Commands.literal("volume")
                        .then(Commands.argument("volume", IntegerArgumentType.integer(0, 100))
                                .executes(NotificationVolumeCommand::execute)));
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int volume = IntegerArgumentType.getInteger(context, "volume");

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }

        NotificationVolumeSyncPacket.sendToPlayer(player, volume);

        source.sendSuccess(() -> Component.literal("Notification volume set to " + volume + "%.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
}
