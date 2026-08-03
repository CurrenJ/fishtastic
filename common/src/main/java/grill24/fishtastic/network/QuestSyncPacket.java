package grill24.fishtastic.network;

import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.server.PlayerQuestState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record QuestSyncPacket(
        Map<Identifier, PlayerQuestState.QuestProgress> questProgress,
        int tokenBalance,
        Map<Identifier, ItemStack> triggeringItems,
        Map<Identifier, Integer> purchaseCounts,
        CleanupGoalProgress cleanupGoal,
        long serverGameTime,
        ItemStack baitDepletedItem,
        List<ItemStack> firstCatchItems,
        int shopRefreshCount
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<QuestSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(FishtasticPackets.QUEST_SYNC_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.map(HashMap::new, Identifier.STREAM_CODEC, PlayerQuestState.QuestProgress.STREAM_CODEC),
                    QuestSyncPacket::questProgress,
                    ByteBufCodecs.VAR_INT,
                    QuestSyncPacket::tokenBalance,
                    ByteBufCodecs.map(HashMap::new, Identifier.STREAM_CODEC, ItemStack.STREAM_CODEC),
                    QuestSyncPacket::triggeringItems,
                    ByteBufCodecs.map(HashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.VAR_INT),
                    QuestSyncPacket::purchaseCounts,
                    CleanupGoalProgress.STREAM_CODEC,
                    QuestSyncPacket::cleanupGoal,
                    ByteBufCodecs.VAR_LONG,
                    QuestSyncPacket::serverGameTime,
                    ItemStack.OPTIONAL_STREAM_CODEC,
                    QuestSyncPacket::baitDepletedItem,
                    ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()),
                    QuestSyncPacket::firstCatchItems,
                    ByteBufCodecs.VAR_INT,
                    QuestSyncPacket::shopRefreshCount,
                    QuestSyncPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Send a sync packet without triggering items (e.g. initial sync on join). */
    public static void sendToPlayer(ServerPlayer player, FishCatchSavedData data) {
        sendToPlayer(player, data, Map.of(), 0);
    }

    /** Send a sync packet with the items that triggered quest progress. */
    public static void sendToPlayer(ServerPlayer player, FishCatchSavedData data,
                                    Map<Identifier, ItemStack> triggeringItems) {
        sendToPlayer(player, data, triggeringItems, 0);
    }

    /**
     * Send a sync packet, optionally announcing a freshly crossed global cleanup-goal
     * threshold. Pass 0 for milestoneReached when this sync isn't a milestone announcement.
     */
    public static void sendToPlayer(ServerPlayer player, FishCatchSavedData data,
                                    Map<Identifier, ItemStack> triggeringItems, int milestoneReached) {
        sendToPlayer(player, data, triggeringItems, milestoneReached, ItemStack.EMPTY, List.of());
    }

    /**
     * Send a sync packet, optionally announcing a depleted bait stack and/or fish species
     * caught for the first time (both drive one-shot HUD banners on the client — see
     * QuestClientCache#update and QuestProgressNotificationManager#install).
     */
    public static void sendToPlayer(ServerPlayer player, FishCatchSavedData data,
                                    Map<Identifier, ItemStack> triggeringItems, int milestoneReached,
                                    ItemStack baitDepletedItem, List<ItemStack> firstCatchItems) {
        PlayerQuestState state = data.getOrCreateQuestState(player);
        CleanupGoalProgress cleanupGoal = new CleanupGoalProgress(
                data.getCleanupGoalTotal(), data.getCleanupGoalThreshold(), milestoneReached);
        long gameTime = ((ServerLevel) player.level()).getServer().overworld().getGameTime();
        QuestSyncPacket packet = new QuestSyncPacket(
                state.getProgressSnapshot(), state.getTokenBalance(),
                triggeringItems, state.getPurchaseCountSnapshot(), cleanupGoal, gameTime,
                baitDepletedItem, firstCatchItems, state.getShopRefreshCount());
        player.connection.send(new ClientboundCustomPayloadPacket(packet));
    }

    public static ClientHandler clientHandler;

    public interface ClientHandler {
        void handle(QuestSyncPacket packet);
    }

    public static void registerClientHandler(ClientHandler h) {
        clientHandler = h;
    }

    public static void handleServerToClient(QuestSyncPacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (clientHandler != null) clientHandler.handle(packet);
        });
    }
}
