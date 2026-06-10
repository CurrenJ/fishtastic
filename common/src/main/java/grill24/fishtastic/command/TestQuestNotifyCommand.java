package grill24.fishtastic.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import grill24.fishtastic.client.QuestProgressEvent;
import grill24.fishtastic.client.QuestProgressNotificationManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import static grill24.fishtastic.util.Utility.ft;

/**
 * Debug command to trigger test quest progress notification banners.
 * Works in single-player (the notification manager is client-side).
 *
 * Usage:
 *   /testquestnotify                                    — normal progress (2→3 of 5, "Bluegill Novice")
 *   /testquestnotify complete                           — completion (5→5 of 5, "Bluegill Novice")
 *   /testquestnotify <oldCount> <newCount> <targetCount> [name]
 */
public class TestQuestNotifyCommand {

    private static final String DEFAULT_NAME = "Bluegill Novice";
    private static final Identifier DEFAULT_ID = ft("mastery/bluegill_novice");

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("testquestnotify")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                // No args: default normal progress
                .executes(ctx -> execute(ctx, 2, 3, 5, DEFAULT_NAME, false))
                // "complete" sub: default completion
                .then(Commands.literal("complete")
                        .executes(ctx -> execute(ctx, 5, 5, 5, DEFAULT_NAME, true)))
                // Explicit counts
                .then(Commands.argument("oldCount", IntegerArgumentType.integer(0))
                        .then(Commands.argument("newCount", IntegerArgumentType.integer(1))
                                .then(Commands.argument("targetCount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> execute(ctx,
                                                IntegerArgumentType.getInteger(ctx, "oldCount"),
                                                IntegerArgumentType.getInteger(ctx, "newCount"),
                                                IntegerArgumentType.getInteger(ctx, "targetCount"),
                                                DEFAULT_NAME,
                                                IntegerArgumentType.getInteger(ctx, "newCount") >= IntegerArgumentType.getInteger(ctx, "targetCount")))
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> execute(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "oldCount"),
                                                        IntegerArgumentType.getInteger(ctx, "newCount"),
                                                        IntegerArgumentType.getInteger(ctx, "targetCount"),
                                                        StringArgumentType.getString(ctx, "name"),
                                                        IntegerArgumentType.getInteger(ctx, "newCount") >= IntegerArgumentType.getInteger(ctx, "targetCount")))))));

        dispatcher.register(root);
    }

    private static int execute(CommandContext<CommandSourceStack> ctx,
                               int oldCount, int newCount, int targetCount,
                               String displayName, boolean completed) {
        CommandSourceStack source = ctx.getSource();
        Identifier questId = DEFAULT_ID;

        QuestProgressEvent event = new QuestProgressEvent(questId, oldCount, newCount, targetCount, completed, ItemStack.EMPTY);
        QuestProgressNotificationManager.getInstance().enqueue(event);

        String type = completed ? "completion" : "progress";
        source.sendSuccess(() -> Component.literal(
                        String.format("Triggered test %s notification: \"%s\" %d→%d/%d",
                                type, displayName, oldCount, newCount, targetCount))
                .withStyle(ChatFormatting.GREEN), false);

        return 1;
    }
}
