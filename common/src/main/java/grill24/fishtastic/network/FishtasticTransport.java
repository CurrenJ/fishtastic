package grill24.fishtastic.network;

import net.minecraft.server.level.ServerPlayer;

/** Convenience send methods for the 1.20.1 platform payload shim. */
public final class FishtasticTransport {
    private FishtasticTransport() {}

    public static void sendToServer(ObserveFishPacket packet) {
        NetworkApiSided.sendToServer(packet, ObserveFishPacket.STREAM_CODEC);
    }

    public static void sendToPlayer(ServerPlayer player, FishSchoolSetupPacket packet) {
        NetworkApiSided.sendToPlayer(player, packet, FishSchoolSetupPacket.STREAM_CODEC);
    }

    public static void sendToPlayer(ServerPlayer player, FishSchoolStatePacket packet) {
        NetworkApiSided.sendToPlayer(player, packet, FishSchoolStatePacket.STREAM_CODEC);
    }

    public static void sendToPlayer(ServerPlayer player, FishSchoolInputPacket packet) {
        NetworkApiSided.sendToPlayer(player, packet, FishSchoolInputPacket.STREAM_CODEC);
    }
}