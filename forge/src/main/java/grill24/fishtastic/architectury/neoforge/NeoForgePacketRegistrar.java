package grill24.fishtastic.architectury.neoforge;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.network.FishtasticPackets;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * NeoForge-specific packet registration implementation
 */
public class NeoForgePacketRegistrar implements FishtasticPackets.IPacketRegistrar {
    private final PayloadRegistrar registrar;

    public NeoForgePacketRegistrar(PayloadRegistrar registrar) {
        this.registrar = registrar;
    }

    /**
     * Register all Fishtastic packets for NeoForge
     */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Fishtastic.MOD_ID)
                .versioned("1.0.0")
                .optional();

        NeoForgePacketRegistrar wrapper = new NeoForgePacketRegistrar(registrar);

        // Register client-to-server packets
        FishtasticPackets.registerClientToServerPackets(wrapper);

        // Register server-to-client packets
        FishtasticPackets.registerServerToClientPackets(wrapper);

        Fishtastic.LOGGER.info("Registered NeoForge network packets");
    }

    @Override
    public <T extends CustomPacketPayload> void registerClientToServer(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            FishtasticPackets.IPacketHandler<T> handler) {

        registrar.playToServer(
                type,
                codec,
                (payload, context) -> handler.handle(payload, new NeoForgePacketContext(context))
        );
    }

    @Override
    public <T extends CustomPacketPayload> void registerServerToClient(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            FishtasticPackets.IPacketHandler<T> handler) {

        registrar.playToClient(
                type,
                codec,
                (payload, context) -> handler.handle(payload, new NeoForgePacketContext(context))
        );
    }

    /**
     * NeoForge-specific packet context wrapper
     */
    private static class NeoForgePacketContext implements FishtasticPackets.IPacketContext {
        private final IPayloadContext context;

        NeoForgePacketContext(IPayloadContext context) {
            this.context = context;
        }

        @Override
        public net.minecraft.world.entity.player.Player getPlayer() {
            return context.player();
        }

        @Override
        public void enqueueWork(Runnable runnable) {
            context.enqueueWork(runnable);
        }
    }
}
