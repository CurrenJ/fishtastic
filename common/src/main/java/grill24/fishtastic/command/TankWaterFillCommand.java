package grill24.fishtastic.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import grill24.fishtastic.network.TankWaterFillSyncPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Lets any player toggle the animated water fill drawn behind their fish tank glass. Like
 * {@link NotificationVolumeCommand}, the setting is purely client-side, so this doesn't gate on
 * operator permission — it only ever affects the invoking player's own client, and takes effect
 * on the next rendered frame.
 *
 * Usage: /fishtastic tank waterfill <true|false>
 */
public class TankWaterFillCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("tank")
                .then(Commands.literal("waterfill")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(TankWaterFillCommand::execute)));
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        boolean enabled = BoolArgumentType.getBool(context, "enabled");

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }

        TankWaterFillSyncPacket.sendToPlayer(player, enabled);

        source.sendSuccess(() -> Component.literal("Tank water fill " + (enabled ? "enabled" : "disabled") + ".")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
}
