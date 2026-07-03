package grill24.fishtastic.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import grill24.fishtastic.network.FishEncyclopediaSyncPacket;
import grill24.fishtastic.network.QuestSyncPacket;
import grill24.fishtastic.server.FishCatchSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Admin debug command that wipes quest progress (including token balance and shop
 * purchases) and catch history.
 * Usage:
 *   /fishtastic questreset player            — reset the executing player
 *   /fishtastic questreset player <player>   — reset a specific player
 *   /fishtastic questreset all               — reset every player
 */
public class QuestResetCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("questreset")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("player")
                        .executes(ctx -> executePlayer(ctx, null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> executePlayer(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("all")
                        .executes(QuestResetCommand::executeAll));
    }

    private static int executePlayer(CommandContext<CommandSourceStack> ctx, ServerPlayer explicit) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer target = explicit;
        if (target == null && source.getEntity() instanceof ServerPlayer sp) {
            target = sp;
        }
        if (target == null) {
            source.sendFailure(Component.literal("Specify a player or run as a player."));
            return 0;
        }

        FishCatchSavedData data = FishCatchSavedData.getOrCreate(source.getServer());
        data.resetPlayerProgress(target);

        QuestSyncPacket.sendToPlayer(target, data);
        FishEncyclopediaSyncPacket.sendToPlayer(target, data);

        ServerPlayer finalTarget = target;
        source.sendSuccess(() -> Component.literal(
                "Reset quest progress and catch history for " + finalTarget.getName().getString() + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int executeAll(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        FishCatchSavedData data = FishCatchSavedData.getOrCreate(source.getServer());
        data.resetAllProgress();

        for (ServerPlayer online : source.getServer().getPlayerList().getPlayers()) {
            QuestSyncPacket.sendToPlayer(online, data);
            FishEncyclopediaSyncPacket.sendToPlayer(online, data);
        }

        source.sendSuccess(() -> Component.literal(
                "Reset quest progress and catch history for all players.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
