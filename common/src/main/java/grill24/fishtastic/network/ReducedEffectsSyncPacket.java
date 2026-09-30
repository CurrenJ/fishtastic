package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.network.codec.BufCodecs;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.FishtasticPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tells the invoking player's client to persist the reduced celebration effects setting into
 * {@link grill24.fishtastic.client.FishtasticClientConfig}. The setting is purely client-side (it
 * only changes how catch celebrations are drawn), so the server never stores it - like
 * {@link TankWaterFillSyncPacket}, this is round-tripped from the command handler straight back to
 * the same player.
 */
public record ReducedEffectsSyncPacket(boolean reduced) implements FishtasticPayload {

    public static final FishtasticPayload.PayloadType<ReducedEffectsSyncPacket> TYPE =
            new FishtasticPayload.PayloadType<>(Fishtastic.id("reduced_effects_sync"));

    public static final BufCodec<ReducedEffectsSyncPacket> STREAM_CODEC =
            BufCodec.composite(
                    BufCodecs.BOOL,
                    ReducedEffectsSyncPacket::reduced,
                    ReducedEffectsSyncPacket::new
            );

    @Override
    public FishtasticPayload.PayloadType<?> type() {
        return TYPE;
    }

    public static ClientHandler clientHandler;

    public interface ClientHandler {
        void handle(ReducedEffectsSyncPacket packet);
    }

    public static void registerClientHandler(ClientHandler h) {
        clientHandler = h;
    }

    public static void handleServerToClient(ReducedEffectsSyncPacket packet, FishtasticPacketHandling.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (clientHandler != null) clientHandler.handle(packet);
        });
    }

    public static void sendToPlayer(ServerPlayer player, boolean reduced) {
        NetworkApiSided.sendToPlayer(player, new ReducedEffectsSyncPacket(reduced), STREAM_CODEC);
    }
}
