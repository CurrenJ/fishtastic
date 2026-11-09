package grill24.fishtastic.network;

import net.minecraft.server.level.ServerPlayer;

/** Convenience send methods for the 1.20.1 platform payload shim. */
public final class FishtasticTransport {
    private FishtasticTransport() {}

    public static void sendToServer(ObserveFishPacket packet) {
        NetworkApiSided.sendToServer(packet, ObserveFishPacket.STREAM_CODEC);
    }

    public static void sendToServer(EquipCompanionPacket packet) {
        NetworkApiSided.sendToServer(packet, EquipCompanionPacket.STREAM_CODEC);
    }

    public static void sendToServer(UnequipCompanionPacket packet) {
        NetworkApiSided.sendToServer(packet, UnequipCompanionPacket.STREAM_CODEC);
    }

    public static void sendToServer(MoveCompanionPacket packet) {
        NetworkApiSided.sendToServer(packet, MoveCompanionPacket.STREAM_CODEC);
    }

    public static void sendToServer(RenameCompanionPacket packet) {
        NetworkApiSided.sendToServer(packet, RenameCompanionPacket.STREAM_CODEC);
    }

    public static void sendToServer(OpenCompanionMenuPacket packet) {
        NetworkApiSided.sendToServer(packet, OpenCompanionMenuPacket.STREAM_CODEC);
    }

    public static void sendToServer(RequestFishEncyclopediaPacket packet) {
        NetworkApiSided.sendToServer(packet, RequestFishEncyclopediaPacket.STREAM_CODEC);
    }

    public static void sendToServer(RequestQuestLogPacket packet) {
        NetworkApiSided.sendToServer(packet, RequestQuestLogPacket.STREAM_CODEC);
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