package grill24.fishtastic.network;

import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.server.PlayerQuestState;
import grill24.fishtastic.network.codec.BufCodecs;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.FishtasticPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record QuestSyncPacket(
        Map<ResourceLocation, PlayerQuestState.QuestProgress> questProgress,
        int tokenBalance,
        Map<ResourceLocation, ItemStack> triggeringItems,
        Map<ResourceLocation, Integer> purchaseCounts,
        CleanupGoalProgress cleanupGoal,
        long serverGameTime,
        ItemStack baitDepletedItem,
        List<ItemStack> firstCatchItems,
        int shopRefreshCount
) implements FishtasticPayload {

    public static final FishtasticPayload.PayloadType<QuestSyncPacket> TYPE =
            new FishtasticPayload.PayloadType<>(FishtasticPackets.QUEST_SYNC_ID);

    public static final BufCodec<QuestSyncPacket> STREAM_CODEC =
            BufCodec.composite(
                    BufCodecs.map(HashMap::new, BufCodecs.RESOURCE_LOCATION, PlayerQuestState.QuestProgress.STREAM_CODEC),
                    QuestSyncPacket::questProgress,
                    BufCodecs.VAR_INT,
                    QuestSyncPacket::tokenBalance,
                    BufCodecs.map(HashMap::new, BufCodecs.RESOURCE_LOCATION, BufCodecs.ITEM_STACK),
                    QuestSyncPacket::triggeringItems,
                    BufCodecs.map(HashMap::new, BufCodecs.RESOURCE_LOCATION, BufCodecs.VAR_INT),
                    QuestSyncPacket::purchaseCounts,
                    CleanupGoalProgress.STREAM_CODEC,
                    QuestSyncPacket::cleanupGoal,
                    BufCodecs.VAR_LONG,
                    QuestSyncPacket::serverGameTime,
                    ItemStack.OPTIONAL_STREAM_CODEC,
                    QuestSyncPacket::baitDepletedItem,
                    BufCodecs.ITEM_STACK.apply(BufCodecs.list()),
                    QuestSyncPacket::firstCatchItems,
                    BufCodecs.VAR_INT,
                    QuestSyncPacket::shopRefreshCount,
                    QuestSyncPacket::new
            );

    @Override
    public FishtasticPayload.PayloadType<?> type() {
        return TYPE;
    }

    /** Send a sync packet without triggering items (e.g. initial sync on join). */
    public static void sendToPlayer(ServerPlayer player, FishCatchSavedData data) {
        sendToPlayer(player, data, Map.of(), 0);
    }

    /** Send a sync packet with the items that triggered quest progress. */
    public static void sendToPlayer(ServerPlayer player, FishCatchSavedData data,
                                    Map<ResourceLocation, ItemStack> triggeringItems) {
        sendToPlayer(player, data, triggeringItems, 0);
    }

    /**
     * Send a sync packet, optionally announcing a freshly crossed global cleanup-goal
     * threshold. Pass 0 for milestoneReached when this sync isn't a milestone announcement.
     */
    public static void sendToPlayer(ServerPlayer player, FishCatchSavedData data,
                                    Map<ResourceLocation, ItemStack> triggeringItems, int milestoneReached) {
        sendToPlayer(player, data, triggeringItems, milestoneReached, ItemStack.EMPTY, List.of());
    }

    /**
     * Send a sync packet, optionally announcing a depleted bait stack and/or fish species
     * caught for the first time (both drive one-shot HUD banners on the client — see
     * QuestClientCache#update and QuestProgressNotificationManager#install).
     */
    public static void sendToPlayer(ServerPlayer player, FishCatchSavedData data,
                                    Map<ResourceLocation, ItemStack> triggeringItems, int milestoneReached,
                                    ItemStack baitDepletedItem, List<ItemStack> firstCatchItems) {
        PlayerQuestState state = data.getOrCreateQuestState(player);
        List<CleanupGoalProgress.Contributor> topContributors = data
                .getCleanupGoalContributors(FishCatchSavedData.GLOBAL_CATCH_COUNT_DESC).stream()
                .limit(10)
                .map(e -> new CleanupGoalProgress.Contributor(e.playerUuid(), e.playerName(), e.totalCatches()))
                .toList();
        CleanupGoalProgress cleanupGoal = new CleanupGoalProgress(
                data.getCleanupGoalTotal(), data.getCleanupGoalThreshold(), milestoneReached, topContributors);
        long gameTime = ((ServerLevel) player.level()).getServer().overworld().getGameTime();
        QuestSyncPacket packet = new QuestSyncPacket(
                state.getProgressSnapshot(), state.getTokenBalance(),
                triggeringItems, state.getPurchaseCountSnapshot(), cleanupGoal, gameTime,
                baitDepletedItem, firstCatchItems, state.getShopRefreshCount());
        NetworkApiSided.sendToPlayer(player, packet, STREAM_CODEC);
    }

    public static ClientHandler clientHandler;

    public interface ClientHandler {
        void handle(QuestSyncPacket packet);
    }

    public static void registerClientHandler(ClientHandler h) {
        clientHandler = h;
    }

    public static void handleServerToClient(QuestSyncPacket packet, FishtasticPacketHandling.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (clientHandler != null) clientHandler.handle(packet);
        });
    }
}
