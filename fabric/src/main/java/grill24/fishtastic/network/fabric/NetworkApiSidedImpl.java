package grill24.fishtastic.network.fabric;

import grill24.fishtastic.architectury.fabric.FabricPacketRegistrar;
import grill24.fishtastic.network.FishtasticPayload;
import grill24.fishtastic.network.codec.BufCodec;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** @ExpectPlatform target for {@code grill24.fishtastic.network.NetworkApiSided} (B3.2). */
@SuppressWarnings({"unchecked", "rawtypes"})
public class NetworkApiSidedImpl {
    public static void sendToPlayer(ServerPlayer player, FishtasticPayload payload, BufCodec codec) {
        ServerPlayNetworking.send(player, wrap(payload, codec));
    }

    public static void sendToServer(FishtasticPayload payload, BufCodec codec) {
        ClientPlayNetworking.send(wrap(payload, codec));
    }

    public static void broadcast(MinecraftServer server, FishtasticPayload payload, BufCodec codec) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendToPlayer(player, payload, codec);
        }
    }

    private static FabricPacketRegistrar.FabricPayloadPacket wrap(FishtasticPayload payload, BufCodec codec) {
        return new FabricPacketRegistrar.FabricPayloadPacket(payload, codec, payload.type().id());
    }
}
