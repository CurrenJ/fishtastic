package grill24.fishtastic.network;

import grill24.fishtastic.compat.GelatinOpenMenuCompat;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.FishtasticPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public record RequestQuestLogPacket() implements FishtasticPayload {

    public static final FishtasticPayload.PayloadType<RequestQuestLogPacket> TYPE =
            new FishtasticPayload.PayloadType<>(FishtasticPackets.REQUEST_QUEST_LOG_ID);

    public static final BufCodec<RequestQuestLogPacket> STREAM_CODEC =
            BufCodec.unit(new RequestQuestLogPacket());

    @Override
    public FishtasticPayload.PayloadType<?> type() {
        return TYPE;
    }

    public static void handleClientToServer(RequestQuestLogPacket packet, FishtasticPacketHandling.IPacketContext ctx) {
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
