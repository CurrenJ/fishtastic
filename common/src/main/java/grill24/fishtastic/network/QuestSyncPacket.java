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

import java.util.HashMap;
import java.util.Map;

public record QuestSyncPacket(
        Map<Identifier, PlayerQuestState.QuestProgress> questProgress,
        int tokenBalance
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<QuestSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(FishtasticPackets.QUEST_SYNC_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.map(HashMap::new, Identifier.STREAM_CODEC, PlayerQuestState.QuestProgress.STREAM_CODEC),
                    QuestSyncPacket::questProgress,
                    ByteBufCodecs.VAR_INT,
                    QuestSyncPacket::tokenBalance,
                    QuestSyncPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendToPlayer(ServerPlayer player, FishCatchSavedData data) {
        PlayerQuestState state = data.getOrCreateQuestState(player.getUUID());
        QuestSyncPacket packet = new QuestSyncPacket(state.getProgressSnapshot(), state.getTokenBalance());
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
