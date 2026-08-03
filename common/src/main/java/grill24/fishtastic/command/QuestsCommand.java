package grill24.fishtastic.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import grill24.FishtasticRegistries;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.data.QuestReward;
import grill24.fishtastic.network.FishEncyclopediaSyncPacket;
import grill24.fishtastic.network.QuestSyncPacket;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.server.PlayerQuestState;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * Root command for quest administration.
 * Usage:
 *   /fishtastic quests reset player [player]                  — wipe quest progress, catch history, tokens, and shop purchases
 *   /fishtastic quests reset all                               — same, for every player
 *   /fishtastic quests refresh [player]                        — force-refresh daily quests (resets progress + re-rolls active pool)
 *   /fishtastic quests debug complete <questId|all> [player]   — force a quest to its completed (unclaimed) state, for testing the claim UI
 *   /fishtastic quests debug claim <questId|all> [player]      — force-complete AND claim, granting the reward immediately
 *   /fishtastic quests debug progress <questId> <amount> [player] — set an exact progress count on one quest
 */
public class QuestsCommand {

    private static final DynamicCommandExceptionType ERROR_UNKNOWN_QUEST =
            new DynamicCommandExceptionType(id -> Component.literal("Unknown quest: " + id));

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("quests")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("reset")
                        .then(Commands.literal("player")
                                .executes(ctx -> executeResetPlayer(ctx, null))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> executeResetPlayer(ctx, EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("all")
                                .executes(QuestsCommand::executeResetAll)))
                .then(Commands.literal("refresh")
                        .executes(ctx -> executeRefresh(ctx, null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> executeRefresh(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("debug")
                        .then(Commands.literal("complete")
                                .then(Commands.literal("all")
                                        .executes(ctx -> executeDebugAll(ctx, null, false))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> executeDebugAll(ctx, EntityArgument.getPlayer(ctx, "player"), false))))
                                .then(Commands.argument("questId", ResourceKeyArgument.key(FishtasticRegistries.QUEST_REGISTRY_KEY))
                                        .executes(ctx -> executeDebugOne(ctx, null, false))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> executeDebugOne(ctx, EntityArgument.getPlayer(ctx, "player"), false)))))
                        .then(Commands.literal("claim")
                                .then(Commands.literal("all")
                                        .executes(ctx -> executeDebugAll(ctx, null, true))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> executeDebugAll(ctx, EntityArgument.getPlayer(ctx, "player"), true))))
                                .then(Commands.argument("questId", ResourceKeyArgument.key(FishtasticRegistries.QUEST_REGISTRY_KEY))
                                        .executes(ctx -> executeDebugOne(ctx, null, true))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> executeDebugOne(ctx, EntityArgument.getPlayer(ctx, "player"), true)))))
                        .then(Commands.literal("progress")
                                .then(Commands.argument("questId", ResourceKeyArgument.key(FishtasticRegistries.QUEST_REGISTRY_KEY))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                .executes(ctx -> executeSetProgress(ctx, null))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(ctx -> executeSetProgress(ctx, EntityArgument.getPlayer(ctx, "player"))))))));
    }

    // -------------------------------------------------------------------------
    // reset
    // -------------------------------------------------------------------------

    private static int executeResetPlayer(CommandContext<CommandSourceStack> ctx, ServerPlayer explicit) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer target = resolveTarget(source, explicit);
        if (target == null) return 0;

        FishCatchSavedData data = FishCatchSavedData.getOrCreate(source.getServer());
        data.resetPlayerProgress(target);

        QuestSyncPacket.sendToPlayer(target, data);
        FishEncyclopediaSyncPacket.sendToPlayer(target, data);

        source.sendSuccess(() -> Component.literal(
                "Reset quest progress and catch history for " + target.getName().getString() + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int executeResetAll(CommandContext<CommandSourceStack> ctx) {
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

    // -------------------------------------------------------------------------
    // refresh
    // -------------------------------------------------------------------------

    private static int executeRefresh(CommandContext<CommandSourceStack> ctx, ServerPlayer explicit) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer target = resolveTarget(source, explicit);
        if (target == null) return 0;

        FishCatchSavedData data = FishCatchSavedData.getOrCreate(source.getServer());
        long currentDay = source.getServer().overworld().getGameTime() / 24000L;
        data.forceRefreshDailyQuests(source.getServer(), target, currentDay);
        data.forceRefreshShopPurchases(target);
        QuestSyncPacket.sendToPlayer(target, data);

        source.sendSuccess(() -> Component.literal(
                "Daily quests refreshed for " + target.getName().getString() + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // -------------------------------------------------------------------------
    // debug complete / claim
    // -------------------------------------------------------------------------

    private static int executeDebugOne(CommandContext<CommandSourceStack> ctx, ServerPlayer explicit, boolean claim) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer target = resolveTarget(source, explicit);
        if (target == null) return 0;

        ResourceKey<Quest> questKey = ResourceKeyArgument.getRegistryKey(
                ctx, "questId", FishtasticRegistries.QUEST_REGISTRY_KEY, ERROR_UNKNOWN_QUEST);
        Registry<Quest> questRegistry = source.getServer().registryAccess().lookupOrThrow(FishtasticRegistries.QUEST_REGISTRY_KEY);
        Quest quest = questRegistry.getOptional(questKey).orElse(null);
        if (quest == null) {
            source.sendFailure(Component.literal("Unknown quest: " + questKey.identifier()));
            return 0;
        }

        FishCatchSavedData data = FishCatchSavedData.getOrCreate(source.getServer());
        PlayerQuestState state = data.getOrCreateQuestState(target);
        long currentDay = source.getServer().overworld().getGameTime() / 24000L;

        applyDebugCompletion(source.getServer(), target, state, questKey, quest, currentDay, claim);
        data.setDirty();
        QuestSyncPacket.sendToPlayer(target, data);

        source.sendSuccess(() -> Component.literal(
                (claim ? "Force-claimed" : "Force-completed") + " quest " + questKey.identifier()
                        + " for " + target.getName().getString() + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int executeDebugAll(CommandContext<CommandSourceStack> ctx, ServerPlayer explicit, boolean claim) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer target = resolveTarget(source, explicit);
        if (target == null) return 0;

        Registry<Quest> questRegistry = source.getServer().registryAccess().lookupOrThrow(FishtasticRegistries.QUEST_REGISTRY_KEY);
        FishCatchSavedData data = FishCatchSavedData.getOrCreate(source.getServer());
        PlayerQuestState state = data.getOrCreateQuestState(target);
        long currentDay = source.getServer().overworld().getGameTime() / 24000L;

        int count = 0;
        for (Map.Entry<ResourceKey<Quest>, Quest> entry : questRegistry.entrySet()) {
            applyDebugCompletion(source.getServer(), target, state, entry.getKey(), entry.getValue(), currentDay, claim);
            count++;
        }

        data.setDirty();
        QuestSyncPacket.sendToPlayer(target, data);

        int finalCount = count;
        source.sendSuccess(() -> Component.literal(
                (claim ? "Force-claimed " : "Force-completed ") + finalCount + " quest(s) for "
                        + target.getName().getString() + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return count;
    }

    private static void applyDebugCompletion(MinecraftServer server, ServerPlayer target,
            PlayerQuestState state, ResourceKey<Quest> questKey, Quest quest, long currentDay, boolean claim) {
        int targetCount = quest.objective().effectiveTargetCount(server.registryAccess());
        state.setProgress(questKey, targetCount, targetCount, currentDay);
        if (claim) {
            for (QuestReward.RewardItem item : quest.reward().items()) {
                target.getInventory().add(item.toStack());
            }
            state.claim(questKey, quest.reward().questTokens());
        }
    }

    // -------------------------------------------------------------------------
    // debug progress
    // -------------------------------------------------------------------------

    private static int executeSetProgress(CommandContext<CommandSourceStack> ctx, ServerPlayer explicit) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer target = resolveTarget(source, explicit);
        if (target == null) return 0;

        ResourceKey<Quest> questKey = ResourceKeyArgument.getRegistryKey(
                ctx, "questId", FishtasticRegistries.QUEST_REGISTRY_KEY, ERROR_UNKNOWN_QUEST);
        int amount = IntegerArgumentType.getInteger(ctx, "amount");

        Registry<Quest> questRegistry = source.getServer().registryAccess().lookupOrThrow(FishtasticRegistries.QUEST_REGISTRY_KEY);
        Quest quest = questRegistry.getOptional(questKey).orElse(null);
        if (quest == null) {
            source.sendFailure(Component.literal("Unknown quest: " + questKey.identifier()));
            return 0;
        }

        FishCatchSavedData data = FishCatchSavedData.getOrCreate(source.getServer());
        PlayerQuestState state = data.getOrCreateQuestState(target);
        long currentDay = source.getServer().overworld().getGameTime() / 24000L;

        int targetCount = quest.objective().effectiveTargetCount(source.getServer().registryAccess());
        state.setProgress(questKey, amount, targetCount, currentDay);
        data.setDirty();
        QuestSyncPacket.sendToPlayer(target, data);

        source.sendSuccess(() -> Component.literal(
                "Set progress for quest " + questKey.identifier() + " to " + amount + "/"
                        + targetCount + " for " + target.getName().getString() + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return amount;
    }

    // -------------------------------------------------------------------------
    // shared
    // -------------------------------------------------------------------------

    private static ServerPlayer resolveTarget(CommandSourceStack source, ServerPlayer explicit) {
        if (explicit != null) return explicit;
        if (source.getEntity() instanceof ServerPlayer sp) return sp;
        source.sendFailure(Component.literal("Specify a player or run as a player."));
        return null;
    }
}
