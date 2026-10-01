package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tells the invoking player's client to apply and persist a new fish tank interior light level
 * (see {@link grill24.fishtastic.client.renderer.TankInteriorLight}). The setting is purely
 * client-side, so the server never stores it — like {@link TankWaterFillSyncPacket}, this is
 * round-tripped from the command handler straight back to the same player.
 */
public record TankInteriorLightSyncPacket(int level) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TankInteriorLightSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(Fishtastic.id("tank_interior_light_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TankInteriorLightSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    TankInteriorLightSyncPacket::level,
                    TankInteriorLightSyncPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static ClientHandler clientHandler;

    public interface ClientHandler {
        void handle(TankInteriorLightSyncPacket packet);
    }

    public static void registerClientHandler(ClientHandler h) {
        clientHandler = h;
    }

    public static void handleServerToClient(TankInteriorLightSyncPacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (clientHandler != null) clientHandler.handle(packet);
        });
    }

    public static void sendToPlayer(ServerPlayer player, int level) {
        player.connection.send(new ClientboundCustomPayloadPacket(new TankInteriorLightSyncPacket(level)));
    }
}
