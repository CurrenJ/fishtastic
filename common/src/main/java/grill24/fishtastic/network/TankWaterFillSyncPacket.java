package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tells the invoking player's client to persist a new fish tank water fill toggle into
 * {@link grill24.fishtastic.client.FishtasticClientConfig}. The setting is purely client-side
 * (it only gates the water fill quads in the tank renderer), so the server never stores it —
 * like {@link NotificationVolumeSyncPacket}, this is round-tripped from the command handler
 * straight back to the same player.
 */
public record TankWaterFillSyncPacket(boolean enabled) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TankWaterFillSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(Fishtastic.id("tank_water_fill_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TankWaterFillSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    TankWaterFillSyncPacket::enabled,
                    TankWaterFillSyncPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static ClientHandler clientHandler;

    public interface ClientHandler {
        void handle(TankWaterFillSyncPacket packet);
    }

    public static void registerClientHandler(ClientHandler h) {
        clientHandler = h;
    }

    public static void handleServerToClient(TankWaterFillSyncPacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (clientHandler != null) clientHandler.handle(packet);
        });
    }

    public static void sendToPlayer(ServerPlayer player, boolean enabled) {
        player.connection.send(new ClientboundCustomPayloadPacket(new TankWaterFillSyncPacket(enabled)));
    }
}
