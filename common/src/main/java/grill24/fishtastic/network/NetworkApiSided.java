package grill24.fishtastic.network;

import dev.architectury.injectables.annotations.ExpectPlatform;
import grill24.fishtastic.network.codec.BufCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sends a {@link FishtasticPayload} over the network (B3.2). 1.20.1 has no unified S2C/C2S
 * payload packet — {@code ClientboundCustomPayloadPacket} here is the old plugin-channel packet,
 * {@code (ResourceLocation, FriendlyByteBuf)}, not a wrapper over any payload interface (see
 * {@link FishtasticPayload}'s own doc comment) — so every send has to go through each platform's
 * real networking layer (Fabric's {@code PacketType}/{@code FabricPacket}, Forge's
 * {@code SimpleChannel}), not a shared vanilla packet constructor.
 *
 * <p>Raw {@link BufCodec}, matching {@link FishtasticPackets#registerServerToClientCodecs}'s own
 * rationale: a generic {@code @ExpectPlatform} method's injected call sites can't bind a type
 * parameter the way a normal generic method call can.
 */
public class NetworkApiSided {
    @ExpectPlatform
    @SuppressWarnings("rawtypes")
    public static void sendToPlayer(ServerPlayer player, FishtasticPayload payload, BufCodec codec) {
        throw new AssertionError();
    }

    @ExpectPlatform
    @SuppressWarnings("rawtypes")
    public static void sendToServer(FishtasticPayload payload, BufCodec codec) {
        throw new AssertionError();
    }

    @ExpectPlatform
    @SuppressWarnings("rawtypes")
    public static void broadcast(MinecraftServer server, FishtasticPayload payload, BufCodec codec) {
        throw new AssertionError();
    }
}
