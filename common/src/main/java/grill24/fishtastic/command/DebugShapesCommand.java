package grill24.fishtastic.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import grill24.FishtasticRegistries;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.data.QuestReward;
import grill24.fishtastic.fishtank.FishTankShape;
import grill24.fishtastic.network.QuestSyncPacket;
import grill24.fishtastic.server.FishCatchBackups;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.server.PlayerQuestState;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Debug command to bulk-unlock every fish tank shape for a player.
 *
 * <p>Shapes have no dedicated unlock save-data of their own — a shape is unlocked whenever the
 * player has claimed any one of its unlocking quests (see {@link FishTankShape#isUnlockedFor}).
 * This command force-completes and claims the first unlocking quest per gated shape, mirroring
 * {@link QuestsCommand}'s {@code debug claim} pattern.
 *
 * <p>Usage:
 *   /fishtastic shapes debug unlockall [player]   — unlock every fish tank shape
 */
public class DebugShapesCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("shapes")
                .requires(FishtasticPermissions.gamemaster())
                .then(Commands.literal("debug")
                        .then(Commands.literal("unlockall")
                                .executes(ctx -> executeUnlockAll(ctx, null))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> executeUnlockAll(ctx, EntityArgument.getPlayer(ctx, "player"))))));
    }

    private static int executeUnlockAll(CommandContext<CommandSourceStack> ctx, ServerPlayer explicit) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer target = resolveTarget(source, explicit);
        if (target == null) return 0;

        MinecraftServer server = source.getServer();
        Registry<Quest> questRegistry = server.registryAccess().registryOrThrow(FishtasticRegistries.QUEST_REGISTRY_KEY);
        FishCatchSavedData data = FishCatchSavedData.getOrCreate(server);
        FishCatchBackups.beforeDestructiveCommand(server, "shapes_unlockall");
        PlayerQuestState state = data.getOrCreateQuestState(target);
        long currentDay = server.overworld().getGameTime() / 24000L;

        int count = 0;
        for (FishTankShape shape : FishTankShape.values()) {
            List<ResourceKey<Quest>> unlockQuests = shape.unlockQuests(questRegistry);
            if (unlockQuests.isEmpty()) continue;
            if (unlockQuests.stream().anyMatch(quest -> state.getProgress(quest).claimed())) continue;

            ResourceKey<Quest> questKey = unlockQuests.get(0);
            Quest quest = questRegistry.getOptional(questKey).orElse(null);
            if (quest == null) continue;

            int targetCount = quest.objective().effectiveTargetCount(server.registryAccess());
            state.setProgress(questKey, targetCount, targetCount, currentDay);
            for (QuestReward.RewardItem item : quest.reward().items()) {
                target.getInventory().add(item.toStack());
            }
            state.claim(questKey, quest.reward().questTokens());
            count++;
        }

        data.setDirty();
        QuestSyncPacket.sendToPlayer(target, data);

        int finalCount = count;
        source.sendSuccess(() -> Component.literal(
                "Unlocked " + finalCount + " fish tank shape(s) for " + target.getName().getString() + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return count;
    }

    private static ServerPlayer resolveTarget(CommandSourceStack source, ServerPlayer explicit) {
        if (explicit != null) return explicit;
        if (source.getEntity() instanceof ServerPlayer sp) return sp;
        source.sendFailure(Component.literal("Specify a player or run as a player."));
        return null;
    }
}
