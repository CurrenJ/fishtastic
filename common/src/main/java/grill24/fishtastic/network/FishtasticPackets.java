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
    public static final Identifier TOGGLE_EDIT_MODE_ID = Fishtastic.id("toggle_edit_mode");
    public static final Identifier COMPLETE_QUEST_ID = Fishtastic.id("complete_quest");
    public static final Identifier QUEST_SYNC_ID = Fishtastic.id("quest_sync");
    public static final Identifier REQUEST_QUEST_LOG_ID = Fishtastic.id("request_quest_log");
    public static final Identifier PURCHASE_SHOP_ENTRY_ID = Fishtastic.id("purchase_shop_entry");
    public static final Identifier REFRESH_SHOP_ID = Fishtastic.id("refresh_shop");
    public static final Identifier TUTORIAL_SYNC_ID = Fishtastic.id("tutorial_sync");
    public static final Identifier TUTORIAL_ADVANCE_ID = Fishtastic.id("tutorial_advance");
    public static final Identifier REQUEST_FISH_ENCYCLOPEDIA_ID = Fishtastic.id("request_fish_encyclopedia");
    public static final Identifier FISH_ENCYCLOPEDIA_SYNC_ID = Fishtastic.id("fish_encyclopedia_sync");
    public static final Identifier CLAIM_ENCYCLOPEDIA_REWARD_ID = Fishtastic.id("claim_encyclopedia_reward");
    public static final Identifier COSMETIC_CAPTURE_SYNC_ID = Fishtastic.id("cosmetic_capture_sync");
    public static final Identifier ENCYCLOPEDIA_TUTORIAL_SYNC_ID = Fishtastic.id("encyclopedia_tutorial_sync");
    public static final Identifier ENCYCLOPEDIA_TUTORIAL_ADVANCE_ID = Fishtastic.id("encyclopedia_tutorial_advance");

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
                ToggleEditModePacket.TYPE,
                ToggleEditModePacket.STREAM_CODEC,
                ToggleEditModePacket::handleClientToServer
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
        registrar.registerClientToServer(
                PurchaseShopEntryPacket.TYPE,
                PurchaseShopEntryPacket.STREAM_CODEC,
                PurchaseShopEntryPacket::handleClientToServer
        );
        registrar.registerClientToServer(
                RefreshShopPacket.TYPE,
                RefreshShopPacket.STREAM_CODEC,
                RefreshShopPacket::handleClientToServer
        );
        registrar.registerClientToServer(
                TutorialAdvancePacket.TYPE,
                TutorialAdvancePacket.STREAM_CODEC,
                TutorialAdvancePacket::handleClientToServer
        );
        registrar.registerClientToServer(
                RequestFishEncyclopediaPacket.TYPE,
                RequestFishEncyclopediaPacket.STREAM_CODEC,
                RequestFishEncyclopediaPacket::handleClientToServer
        );
        registrar.registerClientToServer(
                ClaimEncyclopediaRewardPacket.TYPE,
                ClaimEncyclopediaRewardPacket.STREAM_CODEC,
                ClaimEncyclopediaRewardPacket::handleClientToServer
        );
        registrar.registerClientToServer(
                EncyclopediaTutorialAdvancePacket.TYPE,
                EncyclopediaTutorialAdvancePacket.STREAM_CODEC,
                EncyclopediaTutorialAdvancePacket::handleClientToServer
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
        registrar.registerServerToClient(
                TutorialSyncPacket.TYPE,
                TutorialSyncPacket.STREAM_CODEC,
                TutorialSyncPacket::handleServerToClient
        );
        registrar.registerServerToClient(
                FishEncyclopediaSyncPacket.TYPE,
                FishEncyclopediaSyncPacket.STREAM_CODEC,
                FishEncyclopediaSyncPacket::handleServerToClient
        );
        registrar.registerServerToClient(
                CosmeticCaptureSyncPacket.TYPE,
                CosmeticCaptureSyncPacket.STREAM_CODEC,
                CosmeticCaptureSyncPacket::handleServerToClient
        );
        registrar.registerServerToClient(
                EncyclopediaTutorialSyncPacket.TYPE,
                EncyclopediaTutorialSyncPacket.STREAM_CODEC,
                EncyclopediaTutorialSyncPacket::handleServerToClient
        );
    }

    /**
     * Register only the codecs (no handlers) for server→client packets.
     * Used by the Fabric server so it knows how to encode outgoing clientbound
     * payloads. Uses raw types internally because Java lambdas cannot implement
     * generic methods — the raw register call resolves correctly at runtime.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerServerToClientCodecs(
            java.util.function.BiConsumer<CustomPacketPayload.Type, net.minecraft.network.codec.StreamCodec> registrar) {
        registrar.accept(StartFishingMinigamePacket.TYPE, StartFishingMinigamePacket.STREAM_CODEC);
        registrar.accept(LeaderboardResponsePacket.TYPE, LeaderboardResponsePacket.STREAM_CODEC);
        registrar.accept(QuestSyncPacket.TYPE, QuestSyncPacket.STREAM_CODEC);
        registrar.accept(TutorialSyncPacket.TYPE, TutorialSyncPacket.STREAM_CODEC);
        registrar.accept(FishEncyclopediaSyncPacket.TYPE, FishEncyclopediaSyncPacket.STREAM_CODEC);
        registrar.accept(CosmeticCaptureSyncPacket.TYPE, CosmeticCaptureSyncPacket.STREAM_CODEC);
        registrar.accept(EncyclopediaTutorialSyncPacket.TYPE, EncyclopediaTutorialSyncPacket.STREAM_CODEC);
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
