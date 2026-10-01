package grill24.fishtastic.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import grill24.fishtastic.network.TankInteriorLightSyncPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Lets any player set the light level fish tank interiors are drawn with (0 = off). Like
 * {@link TankWaterFillCommand}, the setting is purely client-side, so this doesn't gate on operator
 * permission — it only ever affects the invoking player's own client. Shares the {@code tank}
 * literal with {@link TankWaterFillCommand}; Brigadier merges the two.
 *
 * Usage: /fishtastic tank interiorlight <0-15>
 */
public class TankInteriorLightCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("tank")
                .then(Commands.literal("interiorlight")
                        .then(Commands.argument("level", IntegerArgumentType.integer(0, 15))
                                .executes(TankInteriorLightCommand::execute)));
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int level = IntegerArgumentType.getInteger(context, "level");

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }

        TankInteriorLightSyncPacket.sendToPlayer(player, level);

        source.sendSuccess(() -> Component.literal(level == 0
                        ? "Tank interior light disabled."
                        : "Tank interior light set to " + level + ".")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
}
