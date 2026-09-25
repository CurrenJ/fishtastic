package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.network.codec.BufCodecs;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.FishtasticPayload;

import java.util.List;

/**
 * Sent <strong>server → client</strong> in response to a {@link RequestLeaderboardPacket}.
 *
 * <p>The client receives {@code leaderboardType} and {@code ascending} so it knows how the data is
 * structured, then renders {@code entries} in the appropriate leaderboard UI.
 *
 * <h2>Handling client-side</h2>
 * Register a listener via {@link #registerClientHandler} so the packet can reach your UI code
 * without a hard compile-time dependency from common code to client-only code.
 */
public record LeaderboardResponsePacket(
        LeaderboardType leaderboardType,
        boolean ascending,
        List<LeaderboardEntry> entries
) implements FishtasticPayload {

    public static final FishtasticPayload.PayloadType<LeaderboardResponsePacket> TYPE =
            new FishtasticPayload.PayloadType<>(FishtasticPackets.LEADERBOARD_RESPONSE_ID);

    public static final BufCodec<LeaderboardResponsePacket> STREAM_CODEC =
            BufCodec.composite(
                    LeaderboardType.STREAM_CODEC,
                    LeaderboardResponsePacket::leaderboardType,
                    BufCodecs.BOOL,
                    LeaderboardResponsePacket::ascending,
                    LeaderboardEntry.STREAM_CODEC.apply(BufCodecs.list()),
                    LeaderboardResponsePacket::entries,
                    LeaderboardResponsePacket::new
            );

    @Override
    public FishtasticPayload.PayloadType<LeaderboardResponsePacket> type() {
        return TYPE;
    }

    // -------------------------------------------------------------------------
    // Client handler registration
    // -------------------------------------------------------------------------

    /** Functional interface for receiving leaderboard responses on the client. */
    @FunctionalInterface
    public interface ClientHandler {
        void handle(LeaderboardResponsePacket packet);
    }

    private static ClientHandler clientHandler = null;

    /**
     * Register the client-side handler that receives leaderboard responses.
     * Call this from your client initialisation code (Fabric/NeoForge client entrypoint).
     *
     * <pre>{@code
     * LeaderboardResponsePacket.registerClientHandler(packet -> {
     *     MyLeaderboardScreen.handleResponse(packet);
     * });
     * }</pre>
     */
    public static void registerClientHandler(ClientHandler handler) {
        clientHandler = handler;
    }

    /** Handles the packet on the client side. */
    public static void handleServerToClient(LeaderboardResponsePacket packet,
                                             FishtasticPacketHandling.IPacketContext context) {
        context.enqueueWork(() -> {
            Fishtastic.LOGGER.debug("Received leaderboard response: type={}, entries={}",
                    packet.leaderboardType(), packet.entries().size());
            if (clientHandler != null) {
                clientHandler.handle(packet);
            } else {
                Fishtastic.LOGGER.warn("No client handler registered for LeaderboardResponsePacket");
            }
        });
    }
}
