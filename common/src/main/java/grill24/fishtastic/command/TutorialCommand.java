package grill24.fishtastic.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import grill24.fishtastic.network.QuestSyncPacket;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.server.PlayerQuestState;
import grill24.fishtastic.tutorial.TutorialManager;
import grill24.fishtastic.tutorial.TutorialStep;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class TutorialCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("tutorial")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("status")
                    .executes(ctx -> executeStatus(ctx, null))
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> executeStatus(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("reset")
                    .executes(ctx -> executeReset(ctx, null))
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> executeReset(ctx, EntityArgument.getPlayer(ctx, "player")))))
        );
    }

    private static int executeStatus(CommandContext<CommandSourceStack> ctx, ServerPlayer target)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = resolveTarget(ctx.getSource(), target);
        if (player == null) return 0;

        TutorialStep step = TutorialManager.getStep(player);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Tutorial step for " + player.getName().getString() + ": ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(step.name()).withStyle(ChatFormatting.WHITE)), false);
        return 1;
    }

    private static int executeReset(CommandContext<CommandSourceStack> ctx, ServerPlayer target)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = resolveTarget(ctx.getSource(), target);
        if (player == null) return 0;

        FishCatchSavedData data = FishCatchSavedData.getOrCreate(ctx.getSource().getServer());
        data.setTutorialStep(player, TutorialStep.WAITING_FOR_CAST);

        PlayerQuestState questState = data.getOrCreateQuestState(player);
        questState.resetProgress(TutorialManager.TUTORIAL_QUEST_KEY);

        // Sync both tutorial step and quest state to client
        grill24.fishtastic.network.TutorialSyncPacket.sendToPlayer(player, TutorialStep.WAITING_FOR_CAST);
        QuestSyncPacket.sendToPlayer(player, data);

        ctx.getSource().sendSuccess(() -> Component.literal(
                "Tutorial reset for " + player.getName().getString() + ".")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static ServerPlayer resolveTarget(CommandSourceStack source, ServerPlayer explicit) {
        if (explicit != null) return explicit;
        if (source.getEntity() instanceof ServerPlayer sp) return sp;
        source.sendFailure(Component.literal("Specify a player or run as a player."));
        return null;
    }
}
