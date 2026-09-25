package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.network.codec.BufCodecs;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.FishtasticPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tells the invoking player's client to persist a new notification volume (0-100) into
 * {@link grill24.fishtastic.client.FishtasticClientConfig}. The setting is purely client-side
 * (it only scales the notification banner's sound playback), so the server never stores it —
 * this packet is round-tripped from the command handler straight back to the same player, the
 * same way other sync packets push server-authoritative state down, just without a
 * server-side value backing it.
 */
public record NotificationVolumeSyncPacket(int volume) implements FishtasticPayload {

    public static final FishtasticPayload.PayloadType<NotificationVolumeSyncPacket> TYPE =
            new FishtasticPayload.PayloadType<>(Fishtastic.id("notification_volume_sync"));

    public static final BufCodec<NotificationVolumeSyncPacket> STREAM_CODEC =
            BufCodec.composite(
                    BufCodecs.VAR_INT,
                    NotificationVolumeSyncPacket::volume,
                    NotificationVolumeSyncPacket::new
            );

    @Override
    public FishtasticPayload.PayloadType<?> type() {
        return TYPE;
    }

    public static ClientHandler clientHandler;

    public interface ClientHandler {
        void handle(NotificationVolumeSyncPacket packet);
    }

    public static void registerClientHandler(ClientHandler h) {
        clientHandler = h;
    }

    public static void handleServerToClient(NotificationVolumeSyncPacket packet, FishtasticPacketHandling.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (clientHandler != null) clientHandler.handle(packet);
        });
    }

    public static void sendToPlayer(ServerPlayer player, int volume) {
        NetworkApiSided.sendToPlayer(player, new NotificationVolumeSyncPacket(volume), STREAM_CODEC);
    }
}
