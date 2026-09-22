package grill24.fishtastic.network;

import grill24.fishtastic.compat.GelatinOpenMenuCompat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sent <strong>client → server</strong> to open the Leaderboards screen (keybind path — the
 * Leaderboards Book opens it server-side on use instead). The screen then pulls its own rows
 * per tab with {@link RequestLeaderboardPacket}, so nothing needs syncing up front.
 */
public record RequestLeaderboardScreenPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestLeaderboardScreenPacket> TYPE =
            new CustomPacketPayload.Type<>(FishtasticPackets.REQUEST_LEADERBOARD_SCREEN_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestLeaderboardScreenPacket> STREAM_CODEC =
            StreamCodec.unit(new RequestLeaderboardScreenPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClientToServer(RequestLeaderboardScreenPacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.getPlayer() instanceof ServerPlayer serverPlayer) {
                GelatinOpenMenuCompat.openFishtasticMenu(serverPlayer);
            }
        });
    }
}
