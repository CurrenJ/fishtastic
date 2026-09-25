package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.network.codec.BufCodecs;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.FishtasticPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tells the invoking player's client to persist a new fish tank water fill toggle into
 * {@link grill24.fishtastic.client.FishtasticClientConfig}. The setting is purely client-side
 * (it only gates the water fill quads in the tank renderer), so the server never stores it —
 * like {@link NotificationVolumeSyncPacket}, this is round-tripped from the command handler
 * straight back to the same player.
 */
public record TankWaterFillSyncPacket(boolean enabled) implements FishtasticPayload {

    public static final FishtasticPayload.PayloadType<TankWaterFillSyncPacket> TYPE =
            new FishtasticPayload.PayloadType<>(Fishtastic.id("tank_water_fill_sync"));

    public static final BufCodec<TankWaterFillSyncPacket> STREAM_CODEC =
            BufCodec.composite(
                    BufCodecs.BOOL,
                    TankWaterFillSyncPacket::enabled,
                    TankWaterFillSyncPacket::new
            );

    @Override
    public FishtasticPayload.PayloadType<?> type() {
        return TYPE;
    }

    public static ClientHandler clientHandler;

    public interface ClientHandler {
        void handle(TankWaterFillSyncPacket packet);
    }

    public static void registerClientHandler(ClientHandler h) {
        clientHandler = h;
    }

    public static void handleServerToClient(TankWaterFillSyncPacket packet, FishtasticPacketHandling.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (clientHandler != null) clientHandler.handle(packet);
        });
    }

    public static void sendToPlayer(ServerPlayer player, boolean enabled) {
        NetworkApiSided.sendToPlayer(player, new TankWaterFillSyncPacket(enabled), STREAM_CODEC);
    }
}
