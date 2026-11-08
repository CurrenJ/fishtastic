package grill24.fishtastic.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import grill24.fishtastic.network.TankBorderlessGlassSyncPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Lets any player choose whether fish tanks built with vanilla glass or stained glass are drawn with
 * Fishtastic's borderless version of that glass (see
 * {@link grill24.fishtastic.client.renderer.TankGlassDisplay}). Like {@link TankInteriorLightCommand},
 * the setting is purely client-side, so this doesn't gate on operator permission. Shares the
 * {@code tank} literal with the other tank display commands; Brigadier merges them.
 *
 * Usage: /fishtastic tank borderlessglass <true|false>
 */
public class TankBorderlessGlassCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("tank")
                .then(Commands.literal("borderlessglass")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(TankBorderlessGlassCommand::execute)));
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        boolean enabled = BoolArgumentType.getBool(context, "enabled");

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }

        TankBorderlessGlassSyncPacket.sendToPlayer(player, enabled);

        source.sendSuccess(() -> Component.literal(enabled
                        ? "Tanks built with glass or stained glass now show it borderless."
                        : "Tanks now show their glass as it was built.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
}
