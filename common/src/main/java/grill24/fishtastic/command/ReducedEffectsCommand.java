package grill24.fishtastic.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import grill24.fishtastic.network.ReducedEffectsSyncPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Lets any player switch catch celebrations to their reduced (accessibility) form: no screen
 * flash, shake, dimming, confetti, sparkles, spinning rays or hopping text. Like
 * {@link TankWaterFillCommand}, the setting is purely client-side, so this doesn't gate on
 * operator permission - it only ever affects the invoking player's own client.
 *
 * Usage: /fishtastic reducedeffects <true|false>
 */
public class ReducedEffectsCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("reducedeffects")
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(ReducedEffectsCommand::execute));
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        boolean enabled = BoolArgumentType.getBool(context, "enabled");

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }

        ReducedEffectsSyncPacket.sendToPlayer(player, enabled);

        source.sendSuccess(() -> Component.literal("Reduced celebration effects " + (enabled ? "enabled" : "disabled") + ".")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
}
