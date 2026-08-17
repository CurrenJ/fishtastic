package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tells the invoking player's client to persist a new notification volume (0-100) into
 * {@link grill24.fishtastic.client.FishtasticClientConfig}. The setting is purely client-side
 * (it only scales the notification banner's sound playback), so the server never stores it —
 * this packet is round-tripped from the command handler straight back to the same player, the
 * same way other sync packets push server-authoritative state down, just without a
 * server-side value backing it.
 */
public record NotificationVolumeSyncPacket(int volume) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<NotificationVolumeSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(Fishtastic.id("notification_volume_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NotificationVolumeSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    NotificationVolumeSyncPacket::volume,
                    NotificationVolumeSyncPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static ClientHandler clientHandler;

    public interface ClientHandler {
        void handle(NotificationVolumeSyncPacket packet);
    }

    public static void registerClientHandler(ClientHandler h) {
        clientHandler = h;
    }

    public static void handleServerToClient(NotificationVolumeSyncPacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (clientHandler != null) clientHandler.handle(packet);
        });
    }

    public static void sendToPlayer(ServerPlayer player, int volume) {
        player.connection.send(new ClientboundCustomPayloadPacket(new NotificationVolumeSyncPacket(volume)));
    }
}
