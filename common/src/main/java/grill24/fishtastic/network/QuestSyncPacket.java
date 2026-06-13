package grill24.fishtastic.network;

import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.server.PlayerQuestState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

public record QuestSyncPacket(
        Map<Identifier, PlayerQuestState.QuestProgress> questProgress,
        int tokenBalance,
        Map<Identifier, ItemStack> triggeringItems,
        Map<Identifier, Integer> purchaseCounts
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
                    QuestSyncPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Send a sync packet without triggering items (e.g. initial sync on join). */
    public static void sendToPlayer(ServerPlayer player, FishCatchSavedData data) {
        sendToPlayer(player, data, Map.of());
    }

    /** Send a sync packet with the items that triggered quest progress. */
    public static void sendToPlayer(ServerPlayer player, FishCatchSavedData data,
                                    Map<Identifier, ItemStack> triggeringItems) {
        PlayerQuestState state = data.getOrCreateQuestState(player);
        QuestSyncPacket packet = new QuestSyncPacket(
                state.getProgressSnapshot(), state.getTokenBalance(),
                triggeringItems, state.getPurchaseCountSnapshot());
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
