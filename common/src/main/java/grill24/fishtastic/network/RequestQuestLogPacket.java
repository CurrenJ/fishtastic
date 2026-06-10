package grill24.fishtastic.network;

import grill24.fishtastic.compat.GelatinOpenMenuCompat;
import grill24.fishtastic.server.FishCatchSavedData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public record RequestQuestLogPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestQuestLogPacket> TYPE =
            new CustomPacketPayload.Type<>(FishtasticPackets.REQUEST_QUEST_LOG_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestQuestLogPacket> STREAM_CODEC =
            StreamCodec.unit(new RequestQuestLogPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClientToServer(RequestQuestLogPacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.getPlayer();
            if (player instanceof ServerPlayer serverPlayer) {
                // Sync fresh quest progress before opening the log, so the UI always
                // reflects current server state even if the initial join sync was missed.
                var server = ((ServerLevel) serverPlayer.level()).getServer();
                if (server != null) {
                    QuestSyncPacket.sendToPlayer(serverPlayer,
                            FishCatchSavedData.getOrCreate(server));
                }
                GelatinOpenMenuCompat.openQuestLogMenu(serverPlayer);
            }
        });
    }
}
