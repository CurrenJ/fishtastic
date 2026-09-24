package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tells clients how fast day time currently advances (1.0 = vanilla), so their local day-time
 * prediction between the server's 20-tick time packets matches the server. 1.21.1 has no world
 * clocks, so the Sunset Postcard's slowdown ({@code SunsetExtensionHandler}) is applied by
 * {@code ServerLevelTickTimeMixin} on the server and {@code ClientLevelTickTimeMixin} here.
 * 26.1.2 syncs the same rate through its world clock instead.
 */
public record SetDayRatePacket(float rate) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetDayRatePacket> TYPE =
            new CustomPacketPayload.Type<>(Fishtastic.id("set_day_rate"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetDayRatePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT,
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
    public Type<? extends CustomPacketPayload> type() {
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
