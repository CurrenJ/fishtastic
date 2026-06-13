package grill24.fishtastic.architectury.fabric;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.network.FishtasticPackets;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Fabric-specific packet registration implementation
 */
public class FabricPacketRegistrar implements FishtasticPackets.IPacketRegistrar {

    /**
     * Register all Fishtastic packets for Fabric (server-side).
     * Registers serverbound handlers AND clientbound codecs (so the
     * server knows how to encode server→client packets).
     */
    public static void registerServerReceiver() {
        FabricPacketRegistrar registrar = new FabricPacketRegistrar();

        // Register client-to-server packets (full: codec + handler)
        FishtasticPackets.registerClientToServerPackets(registrar);

        // Register server-to-client codecs (server needs these to encode outgoing packets).
        // No handlers are registered here — those only exist on the client.
        FishtasticPackets.registerServerToClientCodecs(
                (type, codec) -> PayloadTypeRegistry.clientboundPlay().register(type, codec));

        Fishtastic.LOGGER.info("Registered Fabric server-side network packets");
    }

    /**
     * Register client-side packet receivers (should be called from client init)
     */
    public static void registerClientReceiver() {
        FabricPacketRegistrar registrar = new FabricPacketRegistrar();

        // Register server-to-client packets
        FishtasticPackets.registerServerToClientPackets(registrar);

        Fishtastic.LOGGER.info("Registered Fabric client-side network packets");
    }

    @Override
    public <T extends CustomPacketPayload> void registerClientToServer(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            FishtasticPackets.IPacketHandler<T> handler) {

        // Register the payload type
        PayloadTypeRegistry.serverboundPlay().register(type, codec);

        // Register the handler
        ServerPlayNetworking.registerGlobalReceiver(
                type,
                (payload, context) -> handler.handle(payload, new FabricServerPacketContext(context))
        );
    }

    @Override
    public <T extends CustomPacketPayload> void registerServerToClient(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            FishtasticPackets.IPacketHandler<T> handler) {

        // Register the payload type. In an integrated (singleplayer) environment the
        // server-side init already registered this codec via registerServerToClientCodecs,
        // so tolerate double-registration.
        try {
            PayloadTypeRegistry.clientboundPlay().register(type, codec);
        } catch (IllegalArgumentException ignored) {
            // Already registered
        }

        // Register the client-side handler
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                type,
                (payload, context) -> handler.handle(payload, new FabricClientPacketContext(context))
        );
    }

    /**
     * Fabric server-side packet context wrapper
     */
    private static class FabricServerPacketContext implements FishtasticPackets.IPacketContext {
        private final ServerPlayNetworking.Context context;

        FabricServerPacketContext(ServerPlayNetworking.Context context) {
            this.context = context;
        }

        @Override
        public net.minecraft.world.entity.player.Player getPlayer() {
            return context.player();
        }

        @Override
        public void enqueueWork(Runnable runnable) {
            context.server().execute(runnable);
        }
    }

    /**
     * Fabric client-side packet context wrapper
     */
    private static class FabricClientPacketContext implements FishtasticPackets.IPacketContext {
        private final net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.Context context;

        FabricClientPacketContext(net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.Context context) {
            this.context = context;
        }

        @Override
        public net.minecraft.world.entity.player.Player getPlayer() {
            return context.player();
        }

        @Override
        public void enqueueWork(Runnable runnable) {
            context.client().execute(runnable);
        }
    }
}
