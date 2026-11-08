package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.codec.BufCodecs;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tells the invoking player's client to switch the borderless tank glass look on or off (see
 * {@link grill24.fishtastic.client.renderer.TankGlassDisplay}). The setting is purely client-side,
 * so the server never stores it — like {@link TankInteriorLightSyncPacket}, this is round-tripped
 * from the command handler straight back to the same player.
 */
public record TankBorderlessGlassSyncPacket(boolean enabled) implements FishtasticPayload {

    public static final FishtasticPayload.PayloadType<TankBorderlessGlassSyncPacket> TYPE =
            new FishtasticPayload.PayloadType<>(Fishtastic.id("tank_borderless_glass_sync"));

    public static final BufCodec<TankBorderlessGlassSyncPacket> STREAM_CODEC =
            BufCodec.composite(
                    BufCodecs.BOOL,
                    TankBorderlessGlassSyncPacket::enabled,
                    TankBorderlessGlassSyncPacket::new
            );

    @Override
    public FishtasticPayload.PayloadType<?> type() {
        return TYPE;
    }

    public static ClientHandler clientHandler;

    public interface ClientHandler {
        void handle(TankBorderlessGlassSyncPacket packet);
    }

    public static void registerClientHandler(ClientHandler h) {
        clientHandler = h;
    }

    public static void handleServerToClient(TankBorderlessGlassSyncPacket packet, FishtasticPacketHandling.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (clientHandler != null) clientHandler.handle(packet);
        });
    }

    public static void sendToPlayer(ServerPlayer player, boolean enabled) {
        NetworkApiSided.sendToPlayer(player, new TankBorderlessGlassSyncPacket(enabled), STREAM_CODEC);
    }
}
