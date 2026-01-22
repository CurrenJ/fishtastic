package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Central registry for all Fishtastic network packets.
 * Packets are registered through platform-specific implementations.
 */
public class FishtasticPackets {
    // Packet IDs
    public static final ResourceLocation START_FISHING_MINIGAME_ID = Fishtastic.id("start_fishing_minigame");
    public static final ResourceLocation FINISH_FISHING_MINIGAME_ID = Fishtastic.id("finish_fishing_minigame");

    /**
     * Initialize packet registration. Called during mod initialization.
     * Platform-specific implementations handle the actual registration.
     */
    public static void init() {
        Fishtastic.LOGGER.info("Initializing Fishtastic network packets");
    }

    /**
     * Register client-to-server packets
     */
    public static void registerClientToServerPackets(IPacketRegistrar registrar) {
        registrar.registerClientToServer(
                FinishFishingMinigamePacket.TYPE,
                FinishFishingMinigamePacket.STREAM_CODEC,
                FinishFishingMinigamePacket::handleClientToServer
        );
    }

    /**
     * Register server-to-client packets
     */
    public static void registerServerToClientPackets(IPacketRegistrar registrar) {
        registrar.registerServerToClient(
                StartFishingMinigamePacket.TYPE,
                StartFishingMinigamePacket.STREAM_CODEC,
                StartFishingMinigamePacket::handle
        );
    }

    /**
     * Interface for platform-specific packet registration
     */
    public interface IPacketRegistrar {
        <T extends CustomPacketPayload> void registerClientToServer(
                CustomPacketPayload.Type<T> type,
                net.minecraft.network.codec.StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf, T> codec,
                IPacketHandler<T> handler
        );

        <T extends CustomPacketPayload> void registerServerToClient(
                CustomPacketPayload.Type<T> type,
                net.minecraft.network.codec.StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf, T> codec,
                IPacketHandler<T> handler
        );
    }

    /**
     * Platform-agnostic packet handler interface
     */
    @FunctionalInterface
    public interface IPacketHandler<T extends CustomPacketPayload> {
        void handle(T packet, IPacketContext context);
    }

    /**
     * Context information for packet handling
     */
    public interface IPacketContext {
        net.minecraft.world.entity.player.Player getPlayer();
        void enqueueWork(Runnable runnable);
    }
}
