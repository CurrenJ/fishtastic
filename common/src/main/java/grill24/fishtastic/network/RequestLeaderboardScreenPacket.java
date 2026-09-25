package grill24.fishtastic.network;

import grill24.fishtastic.compat.GelatinOpenMenuCompat;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.FishtasticPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sent <strong>client → server</strong> to open the Leaderboards screen (keybind path — the
 * Leaderboards Book opens it server-side on use instead). The screen then pulls its own rows
 * per tab with {@link RequestLeaderboardPacket}, so nothing needs syncing up front.
 */
public record RequestLeaderboardScreenPacket() implements FishtasticPayload {

    public static final FishtasticPayload.PayloadType<RequestLeaderboardScreenPacket> TYPE =
            new FishtasticPayload.PayloadType<>(FishtasticPackets.REQUEST_LEADERBOARD_SCREEN_ID);

    public static final BufCodec<RequestLeaderboardScreenPacket> STREAM_CODEC =
            BufCodec.unit(new RequestLeaderboardScreenPacket());

    @Override
    public FishtasticPayload.PayloadType<?> type() {
        return TYPE;
    }

    public static void handleClientToServer(RequestLeaderboardScreenPacket packet, FishtasticPacketHandling.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.getPlayer() instanceof ServerPlayer serverPlayer) {
                GelatinOpenMenuCompat.openFishtasticMenu(serverPlayer);
            }
        });
    }
}
