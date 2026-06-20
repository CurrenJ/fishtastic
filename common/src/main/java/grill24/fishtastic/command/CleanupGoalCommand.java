package grill24.fishtastic.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import grill24.fishtastic.server.CleanupGoalTracker;
import grill24.fishtastic.server.FishCatchSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Debug command for simulating contributions to the shared "Clean Up the Waters" goal
 * without needing to actually fish up trash. Goes through the real CleanupGoalTracker
 * path, so threshold crossings still pay out tokens and broadcast the milestone banner
 * to every online player.
 */
public class CleanupGoalCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("cleanupgoal")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("contribute")
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .executes(ctx -> executeContribute(ctx, null,
                            IntegerArgumentType.getInteger(ctx, "amount")))
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(ctx -> executeContribute(ctx,
                                EntityArgument.getPlayer(ctx, "player"),
                                IntegerArgumentType.getInteger(ctx, "amount"))))))
                .then(Commands.literal("status")
                    .executes(CleanupGoalCommand::executeStatus))
        );
    }

    private static int executeContribute(CommandContext<CommandSourceStack> ctx, ServerPlayer target, int amount)
            throws CommandSyntaxException {
        ServerPlayer player = resolveTarget(ctx.getSource(), target);
        if (player == null) return 0;

        CleanupGoalTracker.onTrashCatch(ctx.getSource().getServer(), player, amount);

        FishCatchSavedData data = FishCatchSavedData.getOrCreate(ctx.getSource().getServer());
        int total = data.getCleanupGoalTotal();
        int threshold = data.getCleanupGoalThreshold();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Simulated " + amount + " trash from " + player.getName().getString() + ". Goal now at ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(total + " / " + threshold).withStyle(ChatFormatting.YELLOW)), false);
        return 1;
    }

    private static int executeStatus(CommandContext<CommandSourceStack> ctx) {
        FishCatchSavedData data = FishCatchSavedData.getOrCreate(ctx.getSource().getServer());
        int total = data.getCleanupGoalTotal();
        int threshold = data.getCleanupGoalThreshold();

        ctx.getSource().sendSuccess(() -> Component.literal(
                "Clean Up the Waters: " + total + " / " + threshold + " toward the next milestone")
                .withStyle(ChatFormatting.YELLOW), false);

        List<FishCatchSavedData.GlobalCatchCountEntry> contributors =
                data.getCleanupGoalContributors(FishCatchSavedData.GLOBAL_CATCH_COUNT_DESC);
        if (contributors.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No contributors this period.")
                    .withStyle(ChatFormatting.GRAY), false);
        } else {
            for (FishCatchSavedData.GlobalCatchCountEntry entry : contributors) {
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "  " + entry.playerName() + ": " + entry.totalCatches())
                        .withStyle(ChatFormatting.GRAY), false);
            }
        }
        return 1;
    }

    private static ServerPlayer resolveTarget(CommandSourceStack source, ServerPlayer explicit) {
        if (explicit != null) return explicit;
        if (source.getEntity() instanceof ServerPlayer sp) return sp;
        source.sendFailure(Component.literal("Specify a player or run as a player."));
        return null;
    }
}
