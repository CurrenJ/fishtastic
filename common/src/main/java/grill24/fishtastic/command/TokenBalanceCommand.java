package grill24.fishtastic.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import grill24.fishtastic.network.QuestSyncPacket;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.server.PlayerQuestState;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Admin debug command for directly manipulating a player's quest-token balance,
 * for testing the shop without needing to complete quests.
 * Usage:
 *   /fishtastic tokens give <amount> [player]   — add (or, if negative, remove) tokens
 *   /fishtastic tokens set <amount> [player]    — set the balance to an exact value
 *   /fishtastic tokens refresh [player]         — force-refresh shop purchase limits
 */
public class TokenBalanceCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("tokens")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("give")
                        .then(Commands.argument("amount", IntegerArgumentType.integer())
                                .executes(ctx -> executeGive(ctx, null,
                                        IntegerArgumentType.getInteger(ctx, "amount")))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> executeGive(ctx,
                                                EntityArgument.getPlayer(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "amount"))))))
                .then(Commands.literal("set")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                .executes(ctx -> executeSet(ctx, null,
                                        IntegerArgumentType.getInteger(ctx, "amount")))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> executeSet(ctx,
                                                EntityArgument.getPlayer(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "amount"))))))
                .then(Commands.literal("refresh")
                        .executes(ctx -> executeRefresh(ctx, null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> executeRefresh(ctx, EntityArgument.getPlayer(ctx, "player")))));
    }

    private static int executeGive(CommandContext<CommandSourceStack> ctx, ServerPlayer explicit, int amount) throws CommandSyntaxException {
        ServerPlayer target = resolveTarget(ctx.getSource(), explicit);
        if (target == null) return 0;

        PlayerQuestState state = FishCatchSavedData.getOrCreate(ctx.getSource().getServer()).getOrCreateQuestState(target);
        state.addTokens(amount);

        return reportBalance(ctx, target, state,
                (amount >= 0 ? "Gave " + amount : "Removed " + -amount) + " tokens " + (amount >= 0 ? "to " : "from ")
                        + target.getName().getString() + ". Balance now ");
    }

    private static int executeSet(CommandContext<CommandSourceStack> ctx, ServerPlayer explicit, int amount) throws CommandSyntaxException {
        ServerPlayer target = resolveTarget(ctx.getSource(), explicit);
        if (target == null) return 0;

        PlayerQuestState state = FishCatchSavedData.getOrCreate(ctx.getSource().getServer()).getOrCreateQuestState(target);
        state.setTokenBalance(amount);

        return reportBalance(ctx, target, state,
                "Set token balance for " + target.getName().getString() + " to ");
    }

    private static int executeRefresh(CommandContext<CommandSourceStack> ctx, ServerPlayer explicit) {
        ServerPlayer target = resolveTarget(ctx.getSource(), explicit);
        if (target == null) return 0;

        CommandSourceStack source = ctx.getSource();
        FishCatchSavedData data = FishCatchSavedData.getOrCreate(source.getServer());
        data.forceRefreshShopPurchases(target);
        QuestSyncPacket.sendToPlayer(target, data);

        source.sendSuccess(() -> Component.literal(
                "Shop purchase limits refreshed for " + target.getName().getString() + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int reportBalance(CommandContext<CommandSourceStack> ctx, ServerPlayer target, PlayerQuestState state, String prefix) {
        CommandSourceStack source = ctx.getSource();
        FishCatchSavedData data = FishCatchSavedData.getOrCreate(source.getServer());
        data.setDirty();
        QuestSyncPacket.sendToPlayer(target, data);

        int balance = state.getTokenBalance();
        source.sendSuccess(() -> Component.literal(prefix)
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(String.valueOf(balance)).withStyle(ChatFormatting.YELLOW)), true);
        return balance;
    }

    private static ServerPlayer resolveTarget(CommandSourceStack source, ServerPlayer explicit) {
        if (explicit != null) return explicit;
        if (source.getEntity() instanceof ServerPlayer sp) return sp;
        source.sendFailure(Component.literal("Specify a player or run as a player."));
        return null;
    }
}
