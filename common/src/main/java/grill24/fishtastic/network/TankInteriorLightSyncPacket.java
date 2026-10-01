package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.codec.BufCodecs;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tells the invoking player's client to apply and persist a new fish tank interior light level
 * (see {@link grill24.fishtastic.client.renderer.TankInteriorLight}). The setting is purely
 * client-side, so the server never stores it — like {@link TankWaterFillSyncPacket}, this is
 * round-tripped from the command handler straight back to the same player.
 */
public record TankInteriorLightSyncPacket(int level) implements FishtasticPayload {

    public static final FishtasticPayload.PayloadType<TankInteriorLightSyncPacket> TYPE =
            new FishtasticPayload.PayloadType<>(Fishtastic.id("tank_interior_light_sync"));

    public static final BufCodec<TankInteriorLightSyncPacket> STREAM_CODEC =
            BufCodec.composite(
                    BufCodecs.VAR_INT,
                    TankInteriorLightSyncPacket::level,
                    TankInteriorLightSyncPacket::new
            );

    @Override
    public FishtasticPayload.PayloadType<?> type() {
        return TYPE;
    }

    public static ClientHandler clientHandler;

    public interface ClientHandler {
        void handle(TankInteriorLightSyncPacket packet);
    }

    public static void registerClientHandler(ClientHandler h) {
        clientHandler = h;
    }

    public static void handleServerToClient(TankInteriorLightSyncPacket packet, FishtasticPacketHandling.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (clientHandler != null) clientHandler.handle(packet);
        });
    }

    public static void sendToPlayer(ServerPlayer player, int level) {
        NetworkApiSided.sendToPlayer(player, new TankInteriorLightSyncPacket(level), STREAM_CODEC);
    }
}
