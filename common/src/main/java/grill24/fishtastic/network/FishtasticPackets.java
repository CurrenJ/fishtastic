package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Central registry for all Fishtastic network packets.
 * Packets are registered through platform-specific implementations.
 */
public class FishtasticPackets {
    // Packet IDs
    public static final Identifier START_FISHING_MINIGAME_ID = Fishtastic.id("start_fishing_minigame");
    public static final Identifier FINISH_FISHING_MINIGAME_ID = Fishtastic.id("finish_fishing_minigame");
    public static final Identifier REQUEST_LEADERBOARD_ID = Fishtastic.id("request_leaderboard");
    public static final Identifier LEADERBOARD_RESPONSE_ID = Fishtastic.id("leaderboard_response");
    public static final Identifier SET_CUSTOMIZATION_MODE_ID = Fishtastic.id("set_customization_mode");
    public static final Identifier COMPLETE_QUEST_ID = Fishtastic.id("complete_quest");
    public static final Identifier QUEST_SYNC_ID = Fishtastic.id("quest_sync");
    public static final Identifier REQUEST_QUEST_LOG_ID = Fishtastic.id("request_quest_log");

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
        registrar.registerClientToServer(
                RequestLeaderboardPacket.TYPE,
                RequestLeaderboardPacket.STREAM_CODEC,
                RequestLeaderboardPacket::handleClientToServer
        );
        registrar.registerClientToServer(
                SetCustomizationModePacket.TYPE,
                SetCustomizationModePacket.STREAM_CODEC,
                SetCustomizationModePacket::handleClientToServer
        );
        registrar.registerClientToServer(
                CompleteQuestPacket.TYPE,
                CompleteQuestPacket.STREAM_CODEC,
                CompleteQuestPacket::handleClientToServer
        );
        registrar.registerClientToServer(
                RequestQuestLogPacket.TYPE,
                RequestQuestLogPacket.STREAM_CODEC,
                RequestQuestLogPacket::handleClientToServer
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
        registrar.registerServerToClient(
                LeaderboardResponsePacket.TYPE,
                LeaderboardResponsePacket.STREAM_CODEC,
                LeaderboardResponsePacket::handleServerToClient
        );
        registrar.registerServerToClient(
                QuestSyncPacket.TYPE,
                QuestSyncPacket.STREAM_CODEC,
                QuestSyncPacket::handleServerToClient
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
