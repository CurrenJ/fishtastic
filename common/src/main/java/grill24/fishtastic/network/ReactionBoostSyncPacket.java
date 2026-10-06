package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.codec.BufCodecs;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tells the invoking player's client how much faster cosmetic reaction clocks should run
 * (docs/fish-shelters.md §12.13): the fish simulation, and so every reaction, lives on the client.
 * A dev setting, not persisted; round-tripped from {@code /fishtastic cosmetic reactionboost} to the
 * same player, like {@link TankInteriorLightSyncPacket}.
 */
public record ReactionBoostSyncPacket(float boost) implements FishtasticPayload {

    public static final FishtasticPayload.PayloadType<ReactionBoostSyncPacket> TYPE =
            new FishtasticPayload.PayloadType<>(Fishtastic.id("reaction_boost_sync"));

    public static final BufCodec<ReactionBoostSyncPacket> STREAM_CODEC =
            BufCodec.composite(
                    BufCodecs.FLOAT,
                    ReactionBoostSyncPacket::boost,
                    ReactionBoostSyncPacket::new
            );

    @Override
    public FishtasticPayload.PayloadType<?> type() {
        return TYPE;
    }

    public static ClientHandler clientHandler;

    public interface ClientHandler {
        void handle(ReactionBoostSyncPacket packet);
    }

    public static void registerClientHandler(ClientHandler h) {
        clientHandler = h;
    }

    public static void handleServerToClient(ReactionBoostSyncPacket packet, FishtasticPacketHandling.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (clientHandler != null) clientHandler.handle(packet);
        });
    }

    public static void sendToPlayer(ServerPlayer player, float boost) {
        NetworkApiSided.sendToPlayer(player, new ReactionBoostSyncPacket(boost), STREAM_CODEC);
    }
}
