package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.network.codec.BufCodecs;
import grill24.fishtastic.network.codec.BufCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import grill24.fishtastic.network.FishtasticPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tells clients how fast day time currently advances (1.0 = vanilla), so their local day-time
 * prediction between the server's 20-tick time packets matches the server. 1.21.1 has no world
 * clocks, so the Sunset Postcard's slowdown ({@code SunsetExtensionHandler}) is applied by
 * {@code ServerLevelTickTimeMixin} on the server and {@code ClientLevelTickTimeMixin} here.
 * 26.1.2 syncs the same rate through its world clock instead.
 */
public record SetDayRatePacket(float rate) implements FishtasticPayload {

    public static final FishtasticPayload.PayloadType<SetDayRatePacket> TYPE =
            new FishtasticPayload.PayloadType<>(Fishtastic.id("set_day_rate"));

    public static final BufCodec<SetDayRatePacket> STREAM_CODEC =
            BufCodec.composite(
                    BufCodecs.FLOAT,
                    SetDayRatePacket::rate,
                    SetDayRatePacket::new
            );

    /** The rate the client applies to its own day time; read by {@code ClientLevelTickTimeMixin}. */
    private static volatile float clientRate = 1.0f;

    public static float clientRate() {
        return clientRate;
    }

    /** Back to vanilla speed, e.g. when leaving a world. */
    public static void resetClientRate() {
        clientRate = 1.0f;
    }

    @Override
    public FishtasticPayload.PayloadType<?> type() {
        return TYPE;
    }

    public static void handleServerToClient(SetDayRatePacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> clientRate = packet.rate());
    }

    public static void sendToPlayer(ServerPlayer player, float rate) {
        player.connection.send(new ClientboundCustomPayloadPacket(new SetDayRatePacket(rate)));
    }

    public static void broadcast(MinecraftServer server, float rate) {
        server.getPlayerList().broadcastAll(new ClientboundCustomPayloadPacket(new SetDayRatePacket(rate)));
    }
}
