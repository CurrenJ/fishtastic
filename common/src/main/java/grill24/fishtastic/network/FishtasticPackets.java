package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import net.minecraft.resources.ResourceLocation;

/**
 * Central registry for all Fishtastic network packets.
 * Packets are registered through platform-specific implementations.
 */
public class FishtasticPackets {
    // Packet IDs
    public static final ResourceLocation START_FISHING_MINIGAME_ID = Fishtastic.id("start_fishing_minigame");
    public static final ResourceLocation FINISH_FISHING_MINIGAME_ID = Fishtastic.id("finish_fishing_minigame");
    public static final ResourceLocation REQUEST_LEADERBOARD_ID = Fishtastic.id("request_leaderboard");
    public static final ResourceLocation LEADERBOARD_RESPONSE_ID = Fishtastic.id("leaderboard_response");
    public static final ResourceLocation COMPLETE_QUEST_ID = Fishtastic.id("complete_quest");
    public static final ResourceLocation QUEST_SYNC_ID = Fishtastic.id("quest_sync");
    public static final ResourceLocation REQUEST_QUEST_LOG_ID = Fishtastic.id("request_quest_log");
    public static final ResourceLocation REQUEST_LEADERBOARD_SCREEN_ID = Fishtastic.id("request_leaderboard_screen");
    public static final ResourceLocation PURCHASE_SHOP_ENTRY_ID = Fishtastic.id("purchase_shop_entry");
    public static final ResourceLocation REFRESH_SHOP_ID = Fishtastic.id("refresh_shop");
    public static final ResourceLocation TUTORIAL_SYNC_ID = Fishtastic.id("tutorial_sync");
    public static final ResourceLocation TUTORIAL_ADVANCE_ID = Fishtastic.id("tutorial_advance");
    public static final ResourceLocation REQUEST_FISH_ENCYCLOPEDIA_ID = Fishtastic.id("request_fish_encyclopedia");
    public static final ResourceLocation FISH_ENCYCLOPEDIA_SYNC_ID = Fishtastic.id("fish_encyclopedia_sync");
    public static final ResourceLocation CLAIM_ENCYCLOPEDIA_REWARD_ID = Fishtastic.id("claim_encyclopedia_reward");
    public static final ResourceLocation COSMETIC_CAPTURE_SYNC_ID = Fishtastic.id("cosmetic_capture_sync");
    public static final ResourceLocation ENCYCLOPEDIA_TUTORIAL_SYNC_ID = Fishtastic.id("encyclopedia_tutorial_sync");
    public static final ResourceLocation ENCYCLOPEDIA_TUTORIAL_ADVANCE_ID = Fishtastic.id("encyclopedia_tutorial_advance");
    public static final ResourceLocation SET_ASSEMBLY_SHAPE_ID = Fishtastic.id("set_assembly_shape");
    public static final ResourceLocation NOTIFICATION_VOLUME_SYNC_ID = Fishtastic.id("notification_volume_sync");
    public static final ResourceLocation SET_ORGANIZER_SORT_ID = Fishtastic.id("set_organizer_sort");
    public static final ResourceLocation REMOVE_TANK_ENTRY_ID = Fishtastic.id("remove_tank_entry");
    public static final ResourceLocation SET_DAY_RATE_ID = Fishtastic.id("set_day_rate");

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
    public static void registerClientToServerPackets(FishtasticPacketHandling.IPacketRegistrar registrar) {
        registrar.registerClientToServer(
                FinishFishingMinigamePacket.TYPE,
                FinishFishingMinigamePacket.class,
                FinishFishingMinigamePacket.STREAM_CODEC,
                FinishFishingMinigamePacket::handleClientToServer
        );
        registrar.registerClientToServer(
                RequestLeaderboardPacket.TYPE,
                RequestLeaderboardPacket.class,
                RequestLeaderboardPacket.STREAM_CODEC,
                RequestLeaderboardPacket::handleClientToServer
        );
        registrar.registerClientToServer(
                CompleteQuestPacket.TYPE,
                CompleteQuestPacket.class,
                CompleteQuestPacket.STREAM_CODEC,
                CompleteQuestPacket::handleClientToServer
        );
        registrar.registerClientToServer(
                RequestQuestLogPacket.TYPE,
                RequestQuestLogPacket.class,
                RequestQuestLogPacket.STREAM_CODEC,
                RequestQuestLogPacket::handleClientToServer
        );
        registrar.registerClientToServer(
                RequestLeaderboardScreenPacket.TYPE,
                RequestLeaderboardScreenPacket.class,
                RequestLeaderboardScreenPacket.STREAM_CODEC,
                RequestLeaderboardScreenPacket::handleClientToServer
        );
        registrar.registerClientToServer(
                PurchaseShopEntryPacket.TYPE,
                PurchaseShopEntryPacket.class,
                PurchaseShopEntryPacket.STREAM_CODEC,
                PurchaseShopEntryPacket::handleClientToServer
        );
        registrar.registerClientToServer(
                RefreshShopPacket.TYPE,
                RefreshShopPacket.class,
                RefreshShopPacket.STREAM_CODEC,
                RefreshShopPacket::handleClientToServer
        );
        registrar.registerClientToServer(
                TutorialAdvancePacket.TYPE,
                TutorialAdvancePacket.class,
                TutorialAdvancePacket.STREAM_CODEC,
                TutorialAdvancePacket::handleClientToServer
        );
        registrar.registerClientToServer(
                RequestFishEncyclopediaPacket.TYPE,
                RequestFishEncyclopediaPacket.class,
                RequestFishEncyclopediaPacket.STREAM_CODEC,
                RequestFishEncyclopediaPacket::handleClientToServer
        );
        registrar.registerClientToServer(
                ClaimEncyclopediaRewardPacket.TYPE,
                ClaimEncyclopediaRewardPacket.class,
                ClaimEncyclopediaRewardPacket.STREAM_CODEC,
                ClaimEncyclopediaRewardPacket::handleClientToServer
        );
        registrar.registerClientToServer(
                EncyclopediaTutorialAdvancePacket.TYPE,
                EncyclopediaTutorialAdvancePacket.class,
                EncyclopediaTutorialAdvancePacket.STREAM_CODEC,
                EncyclopediaTutorialAdvancePacket::handleClientToServer
        );
        registrar.registerClientToServer(
                SetAssemblyShapePacket.TYPE,
                SetAssemblyShapePacket.class,
                SetAssemblyShapePacket.STREAM_CODEC,
                SetAssemblyShapePacket::handleClientToServer
        );
        registrar.registerClientToServer(
                SetOrganizerSortPacket.TYPE,
                SetOrganizerSortPacket.class,
                SetOrganizerSortPacket.STREAM_CODEC,
                SetOrganizerSortPacket::handleClientToServer
        );
        registrar.registerClientToServer(
                RemoveTankEntryPacket.TYPE,
                RemoveTankEntryPacket.class,
                RemoveTankEntryPacket.STREAM_CODEC,
                RemoveTankEntryPacket::handleClientToServer
        );
    }

    /**
     * Register server-to-client packets
     */
    public static void registerServerToClientPackets(FishtasticPacketHandling.IPacketRegistrar registrar) {
        registrar.registerServerToClient(
                StartFishingMinigamePacket.TYPE,
                StartFishingMinigamePacket.class,
                StartFishingMinigamePacket.STREAM_CODEC,
                StartFishingMinigamePacket::handle
        );
        registrar.registerServerToClient(
                LeaderboardResponsePacket.TYPE,
                LeaderboardResponsePacket.class,
                LeaderboardResponsePacket.STREAM_CODEC,
                LeaderboardResponsePacket::handleServerToClient
        );
        registrar.registerServerToClient(
                QuestSyncPacket.TYPE,
                QuestSyncPacket.class,
                QuestSyncPacket.STREAM_CODEC,
                QuestSyncPacket::handleServerToClient
        );
        registrar.registerServerToClient(
                TutorialSyncPacket.TYPE,
                TutorialSyncPacket.class,
                TutorialSyncPacket.STREAM_CODEC,
                TutorialSyncPacket::handleServerToClient
        );
        registrar.registerServerToClient(
                FishEncyclopediaSyncPacket.TYPE,
                FishEncyclopediaSyncPacket.class,
                FishEncyclopediaSyncPacket.STREAM_CODEC,
                FishEncyclopediaSyncPacket::handleServerToClient
        );
        registrar.registerServerToClient(
                CosmeticCaptureSyncPacket.TYPE,
                CosmeticCaptureSyncPacket.class,
                CosmeticCaptureSyncPacket.STREAM_CODEC,
                CosmeticCaptureSyncPacket::handleServerToClient
        );
        registrar.registerServerToClient(
                EncyclopediaTutorialSyncPacket.TYPE,
                EncyclopediaTutorialSyncPacket.class,
                EncyclopediaTutorialSyncPacket.STREAM_CODEC,
                EncyclopediaTutorialSyncPacket::handleServerToClient
        );
        registrar.registerServerToClient(
                NotificationVolumeSyncPacket.TYPE,
                NotificationVolumeSyncPacket.class,
                NotificationVolumeSyncPacket.STREAM_CODEC,
                NotificationVolumeSyncPacket::handleServerToClient
        );
        registrar.registerServerToClient(
                TankWaterFillSyncPacket.TYPE,
                TankWaterFillSyncPacket.class,
                TankWaterFillSyncPacket.STREAM_CODEC,
                TankWaterFillSyncPacket::handleServerToClient
        );
        registrar.registerServerToClient(
                SetDayRatePacket.TYPE,
                SetDayRatePacket.class,
                SetDayRatePacket.STREAM_CODEC,
                SetDayRatePacket::handleServerToClient
        );
        registrar.registerServerToClient(
                TankInteriorLightSyncPacket.TYPE,
                TankInteriorLightSyncPacket.class,
                TankInteriorLightSyncPacket.STREAM_CODEC,
                TankInteriorLightSyncPacket::handleServerToClient
        );
        registrar.registerServerToClient(
                ReducedEffectsSyncPacket.TYPE,
                ReducedEffectsSyncPacket.class,
                ReducedEffectsSyncPacket.STREAM_CODEC,
                ReducedEffectsSyncPacket::handleServerToClient
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
            java.util.function.BiConsumer<FishtasticPayload.PayloadType, grill24.fishtastic.network.codec.BufCodec> registrar) {
        registrar.accept(StartFishingMinigamePacket.TYPE, StartFishingMinigamePacket.STREAM_CODEC);
        registrar.accept(LeaderboardResponsePacket.TYPE, LeaderboardResponsePacket.STREAM_CODEC);
        registrar.accept(QuestSyncPacket.TYPE, QuestSyncPacket.STREAM_CODEC);
        registrar.accept(TutorialSyncPacket.TYPE, TutorialSyncPacket.STREAM_CODEC);
        registrar.accept(FishEncyclopediaSyncPacket.TYPE, FishEncyclopediaSyncPacket.STREAM_CODEC);
        registrar.accept(CosmeticCaptureSyncPacket.TYPE, CosmeticCaptureSyncPacket.STREAM_CODEC);
        registrar.accept(EncyclopediaTutorialSyncPacket.TYPE, EncyclopediaTutorialSyncPacket.STREAM_CODEC);
        registrar.accept(NotificationVolumeSyncPacket.TYPE, NotificationVolumeSyncPacket.STREAM_CODEC);
        registrar.accept(TankWaterFillSyncPacket.TYPE, TankWaterFillSyncPacket.STREAM_CODEC);
        registrar.accept(SetDayRatePacket.TYPE, SetDayRatePacket.STREAM_CODEC);
        registrar.accept(TankInteriorLightSyncPacket.TYPE, TankInteriorLightSyncPacket.STREAM_CODEC);
        registrar.accept(ReducedEffectsSyncPacket.TYPE, ReducedEffectsSyncPacket.STREAM_CODEC);
    }

}
