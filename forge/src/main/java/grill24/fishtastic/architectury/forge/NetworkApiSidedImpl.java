package grill24.fishtastic.architectury.forge;

import grill24.fishtastic.network.FishtasticPayload;
import grill24.fishtastic.network.codec.BufCodec;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** @ExpectPlatform target for {@code grill24.fishtastic.network.NetworkApiSided} (B3.2). */
@SuppressWarnings({"unchecked", "rawtypes"})
public class NetworkApiSidedImpl {
    public static void sendToPlayer(ServerPlayer player, FishtasticPayload payload, BufCodec codec) {
        ForgePacketRegistrar.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    public static void sendToServer(FishtasticPayload payload, BufCodec codec) {
        ForgePacketRegistrar.CHANNEL.sendToServer(payload);
    }

    public static void broadcast(MinecraftServer server, FishtasticPayload payload, BufCodec codec) {
        ForgePacketRegistrar.CHANNEL.send(PacketDistributor.ALL.noArg(), payload);
    }
}
