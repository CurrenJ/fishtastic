package grill24.fishtastic.architectury.fabric;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.network.FishtasticPacketHandling;
import grill24.fishtastic.network.FishtasticPackets;
import grill24.fishtastic.network.FishtasticPayload;
import grill24.fishtastic.network.codec.BufCodec;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fabric-specific packet registration and send implementation, over FAPI 0.92's 1.20.1-era
 * networking API (B3.2): no {@code PayloadTypeRegistry} — that's 1.20.5+ FAPI. Every payload gets
 * a {@link PacketType} wrapping it in {@link FabricPayloadPacket}; {@code NetworkApiSidedImpl}
 * looks the type up by id to send (verified against the real
 * {@code fabric-networking-api-v1-1.3.15+0caff8d077} jar via {@code javap}, not memory: no
 * {@code IPayloadContext}-style object exists here, {@code receive} hands back
 * {@code (packet, player, PacketSender)} directly).
 */
@SuppressWarnings("rawtypes")
public class FabricPacketRegistrar implements FishtasticPacketHandling.IPacketRegistrar {

    // Raw PacketType: computeIfAbsent's lambda infers P from the *target* type (this map's value
    // type), not from packetType()'s own captured T, so a properly-parameterized
    // Map<ResourceLocation, PacketType<FabricPayloadPacket<?>>> would fight PacketType.create's own
    // inference (T vs. ?) inside the lambda. Raw sidesteps it — same trade the codebase already
    // makes in FishtasticPackets#registerServerToClientCodecs.
    static final Map<ResourceLocation, PacketType> TYPES = new ConcurrentHashMap<>();

    /**
     * Register all Fishtastic packets for Fabric (server-side).
     * Registers serverbound handlers AND clientbound codecs (so the
     * server knows how to encode outgoing server→client packets).
     */
    public static void registerServerReceiver() {
        FabricPacketRegistrar registrar = new FabricPacketRegistrar();

        FishtasticPackets.registerClientToServerPackets(registrar);

        // Register server-to-client packet types (codec only, no handler — those only exist on
        // the client). Raw types: see FishtasticPackets#registerServerToClientCodecs's own note.
        FishtasticPackets.registerServerToClientCodecs(FabricPacketRegistrar::packetType);

        Fishtastic.LOGGER.info("Registered Fabric server-side network packets");
    }

    /**
     * Register client-side packet receivers (should be called from client init)
     */
    public static void registerClientReceiver() {
        FabricPacketRegistrar registrar = new FabricPacketRegistrar();
        FishtasticPackets.registerServerToClientPackets(registrar);
        Fishtastic.LOGGER.info("Registered Fabric client-side network packets");
    }

    @Override
    public <T extends FishtasticPayload> void registerClientToServer(
            FishtasticPayload.PayloadType<T> type,
            Class<T> payloadClass,
            BufCodec<T> codec,
            FishtasticPacketHandling.IPacketHandler<T> handler) {
        ServerPlayNetworking.registerGlobalReceiver(packetType(type, codec),
                (packet, player, responseSender) -> handler.handle(packet.payload(), new FabricServerPacketContext(player)));
    }

    @Override
    public <T extends FishtasticPayload> void registerServerToClient(
            FishtasticPayload.PayloadType<T> type,
            Class<T> payloadClass,
            BufCodec<T> codec,
            FishtasticPacketHandling.IPacketHandler<T> handler) {
        ClientPlayNetworking.registerGlobalReceiver(packetType(type, codec),
                (packet, player, responseSender) -> handler.handle(packet.payload(), new FabricClientPacketContext(player)));
    }

    /**
     * Also used directly as the raw {@code BiConsumer<PayloadType, BufCodec>} method reference for
     * {@link FishtasticPackets#registerServerToClientCodecs} — a {@code BiConsumer} only needs
     * {@code accept}'s {@code void} shape, so this method's {@code PacketType} return is just
     * discarded there; erasure makes the raw and generic call sites bind to the same method.
     */
    @SuppressWarnings("unchecked")
    static <T extends FishtasticPayload> PacketType<FabricPayloadPacket<T>> packetType(
            FishtasticPayload.PayloadType<T> type, BufCodec<T> codec) {
        return (PacketType<FabricPayloadPacket<T>>) TYPES.computeIfAbsent(type.id(),
                id -> PacketType.create(id, buf -> new FabricPayloadPacket(codec.decode(buf), codec, id)));
    }

    /** Wraps a {@link FishtasticPayload} + its codec as a {@link FabricPacket}. */
    record FabricPayloadPacket<T extends FishtasticPayload>(T payload, BufCodec<T> codec, ResourceLocation id) implements FabricPacket {
        @Override
        public void write(FriendlyByteBuf buf) {
            codec.encode(buf, payload);
        }

        @Override
        public PacketType<?> getType() {
            return TYPES.get(id);
        }
    }

    private static class FabricServerPacketContext implements FishtasticPacketHandling.IPacketContext {
        private final ServerPlayer player;

        FabricServerPacketContext(ServerPlayer player) {
            this.player = player;
        }

        @Override
        public Player getPlayer() {
            return player;
        }

        @Override
        public void enqueueWork(Runnable runnable) {
            // 0.92's registerGlobalReceiver callbacks run on the network thread; jump to the
            // server thread before touching game state, same contract as the 1.21.1 IPayloadContext.
            player.server.execute(runnable);
        }
    }

    private static class FabricClientPacketContext implements FishtasticPacketHandling.IPacketContext {
        private final LocalPlayer player;

        FabricClientPacketContext(LocalPlayer player) {
            this.player = player;
        }

        @Override
        public Player getPlayer() {
            return player;
        }

        @Override
        public void enqueueWork(Runnable runnable) {
            Minecraft.getInstance().execute(runnable);
        }
    }
}
