package grill24.fishtastic.architectury.forge;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.network.FishtasticPacketHandling;
import grill24.fishtastic.network.FishtasticPackets;
import grill24.fishtastic.network.FishtasticPayload;
import grill24.fishtastic.network.codec.BufCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Forge-specific packet registration and send implementation, over 47.4's {@code SimpleChannel} /
 * indexed-message-codec API (B3.2): this line predates NeoForge's {@code PayloadRegistrar}/
 * {@code IPayloadContext} entirely — {@code NetworkRegistry.newSimpleChannel} +
 * {@code SimpleChannel#messageBuilder(Class, index, direction)} is the real 1.20.1 Forge shape
 * (verified against {@code forge-1.20.1-47.4.23-patched-sources}). Unlike Fabric's id-keyed
 * {@code PacketType} or NeoForge's payload-interface dispatch, this API dispatches by the
 * message's own runtime {@code Class}, so every payload's concrete record class is registered
 * explicitly (see {@link FishtasticPacketHandling.IPacketRegistrar}'s {@code payloadClass} note),
 * and each direction's messages get a unique index in {@code FishtasticPackets}' registration
 * order — the channel doesn't separate indices by direction.
 */
public class ForgePacketRegistrar implements FishtasticPacketHandling.IPacketRegistrar {
    private static final ResourceLocation CHANNEL_ID = Fishtastic.id("main");
    private static final String PROTOCOL_VERSION = "1.0.0";

    public static SimpleChannel CHANNEL;
    private static final AtomicInteger NEXT_INDEX = new AtomicInteger();

    /** Registers every Fishtastic packet on both directions. Called once from the mod entrypoint. */
    public static void register() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                CHANNEL_ID,
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
        );

        ForgePacketRegistrar registrar = new ForgePacketRegistrar();
        FishtasticPackets.registerClientToServerPackets(registrar);
        FishtasticPackets.registerServerToClientPackets(registrar);

        Fishtastic.LOGGER.info("Registered Forge network packets");
    }

    @Override
    public <T extends FishtasticPayload> void registerClientToServer(
            FishtasticPayload.PayloadType<T> type,
            Class<T> payloadClass,
            BufCodec<T> codec,
            FishtasticPacketHandling.IPacketHandler<T> handler) {
        register(payloadClass, codec, handler, NetworkDirection.PLAY_TO_SERVER);
    }

    @Override
    public <T extends FishtasticPayload> void registerServerToClient(
            FishtasticPayload.PayloadType<T> type,
            Class<T> payloadClass,
            BufCodec<T> codec,
            FishtasticPacketHandling.IPacketHandler<T> handler) {
        register(payloadClass, codec, handler, NetworkDirection.PLAY_TO_CLIENT);
    }

    private <T extends FishtasticPayload> void register(
            Class<T> payloadClass, BufCodec<T> codec, FishtasticPacketHandling.IPacketHandler<T> handler,
            NetworkDirection direction) {
        CHANNEL.messageBuilder(payloadClass, NEXT_INDEX.getAndIncrement(), direction)
                .encoder((msg, buf) -> codec.encode(buf, msg))
                .decoder(codec::decode)
                .consumerMainThread((payload, contextSupplier) ->
                        handler.handle(payload, new ForgePacketContext(contextSupplier.get())))
                .add();
    }

    private static class ForgePacketContext implements FishtasticPacketHandling.IPacketContext {
        private final NetworkEvent.Context context;

        ForgePacketContext(NetworkEvent.Context context) {
            this.context = context;
        }

        @Override
        public Player getPlayer() {
            // getSender() is server-side only (null on the client, per NetworkEvent.Context); the
            // client-side receive path resolves its own local player instead.
            Player sender = context.getSender();
            return sender != null ? sender : net.minecraft.client.Minecraft.getInstance().player;
        }

        @Override
        public void enqueueWork(Runnable runnable) {
            // No-op here: consumerMainThread (unlike consumerNetworkThread) already enqueues the
            // whole consumer onto the main thread and calls setPacketHandled before this runs, so
            // handler.handle's own body already executes off the network thread.
            runnable.run();
        }
    }
}
