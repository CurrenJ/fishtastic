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
    public static final Identifier COMPLETE_QUEST_ID = Fishtastic.id("complete_quest");
    public static final Identifier QUEST_SYNC_ID = Fishtastic.id("quest_sync");
    public static final Identifier COMPANION_PARTY_SYNC_ID = Fishtastic.id("companion_party_sync");
    public static final Identifier REQUEST_QUEST_LOG_ID = Fishtastic.id("request_quest_log");
    public static final Identifier REQUEST_LEADERBOARD_SCREEN_ID = Fishtastic.id("request_leaderboard_screen");
    public static final Identifier PURCHASE_SHOP_ENTRY_ID = Fishtastic.id("purchase_shop_entry");
    public static final Identifier REFRESH_SHOP_ID = Fishtastic.id("refresh_shop");
    public static final Identifier TUTORIAL_SYNC_ID = Fishtastic.id("tutorial_sync");
    public static final Identifier TUTORIAL_ADVANCE_ID = Fishtastic.id("tutorial_advance");
    public static final Identifier REQUEST_FISH_ENCYCLOPEDIA_ID = Fishtastic.id("request_fish_encyclopedia");
    public static final Identifier FISH_ENCYCLOPEDIA_SYNC_ID = Fishtastic.id("fish_encyclopedia_sync");
    public static final Identifier CLAIM_ENCYCLOPEDIA_REWARD_ID = Fishtastic.id("claim_encyclopedia_reward");
    public static final Identifier CLAIM_ALL_ENCYCLOPEDIA_REWARDS_ID = Fishtastic.id("claim_all_encyclopedia_rewards");
    public static final Identifier COSMETIC_CAPTURE_SYNC_ID = Fishtastic.id("cosmetic_capture_sync");
    public static final Identifier ENCYCLOPEDIA_TUTORIAL_SYNC_ID = Fishtastic.id("encyclopedia_tutorial_sync");
    public static final Identifier ENCYCLOPEDIA_TUTORIAL_ADVANCE_ID = Fishtastic.id("encyclopedia_tutorial_advance");
    public static final Identifier SET_ASSEMBLY_SHAPE_ID = Fishtastic.id("set_assembly_shape");
    public static final Identifier NOTIFICATION_VOLUME_SYNC_ID = Fishtastic.id("notification_volume_sync");
    public static final Identifier SET_ORGANIZER_SORT_ID = Fishtastic.id("set_organizer_sort");
    public static final Identifier REMOVE_TANK_ENTRY_ID = Fishtastic.id("remove_tank_entry");
    public static final Identifier BOND_COMPANION_ID = Fishtastic.id("bond_companion");
    public static final Identifier UNEQUIP_COMPANION_ID = Fishtastic.id("unequip_companion");
    public static final Identifier RENAME_COMPANION_ID = Fishtastic.id("rename_companion");
    public static final Identifier TAKE_PILE_FISH_ID = Fishtastic.id("take_pile_fish");
    public static final Identifier COMPANION_FEEDBACK_ID = Fishtastic.id("companion_feedback");
    public static final Identifier COMPANION_TUTORIAL_ADVANCE_ID = Fishtastic.id("companion_tutorial_advance");
    public static final Identifier COMPANION_FISHING_EVENT_ID = Fishtastic.id("companion_fishing_event");
    public static final Identifier COMPANION_SCENE_ID = Fishtastic.id("companion_scene");
    public static final Identifier AMBIENT_CONTEXT_REQUEST_ID = Fishtastic.id("ambient_context_request");
    public static final Identifier AMBIENT_CONTEXT_RESPONSE_ID = Fishtastic.id("ambient_context_response");

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
        registrar.registerClientToServer(TutorialActionPacket.TYPE, TutorialActionPacket.STREAM_CODEC,
                TutorialActionPacket::handleClientToServer);
        registrar.registerClientToServer(OpenCompanionMenuPacket.TYPE, OpenCompanionMenuPacket.STREAM_CODEC,
                OpenCompanionMenuPacket::handleClientToServer);
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
                RequestLeaderboardScreenPacket.TYPE,
                RequestLeaderboardScreenPacket.STREAM_CODEC,
                RequestLeaderboardScreenPacket::handleClientToServer
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
                ClaimAllEncyclopediaRewardsPacket.TYPE,
                ClaimAllEncyclopediaRewardsPacket.STREAM_CODEC,
                ClaimAllEncyclopediaRewardsPacket::handleClientToServer
        );
        registrar.registerClientToServer(
                EncyclopediaTutorialAdvancePacket.TYPE,
                EncyclopediaTutorialAdvancePacket.STREAM_CODEC,
                EncyclopediaTutorialAdvancePacket::handleClientToServer
        );
        registrar.registerClientToServer(
                SetAssemblyShapePacket.TYPE,
                SetAssemblyShapePacket.STREAM_CODEC,
                SetAssemblyShapePacket::handleClientToServer
        );
        registrar.registerClientToServer(
                SetOrganizerSortPacket.TYPE,
                SetOrganizerSortPacket.STREAM_CODEC,
                SetOrganizerSortPacket::handleClientToServer
        );
        registrar.registerClientToServer(
                RemoveTankEntryPacket.TYPE,
                RemoveTankEntryPacket.STREAM_CODEC,
                RemoveTankEntryPacket::handleClientToServer
        );
        registrar.registerClientToServer(
                BondCompanionPacket.TYPE,
                BondCompanionPacket.STREAM_CODEC,
                BondCompanionPacket::handleClientToServer
        );
        registrar.registerClientToServer(
                RenameCompanionPacket.TYPE,
                RenameCompanionPacket.STREAM_CODEC,
                RenameCompanionPacket::handleClientToServer
        );
        registrar.registerClientToServer(
                TakePileFishPacket.TYPE,
                TakePileFishPacket.STREAM_CODEC,
                TakePileFishPacket::handleClientToServer
        );
        registrar.registerClientToServer(
                CompanionTutorialAdvancePacket.TYPE,
                CompanionTutorialAdvancePacket.STREAM_CODEC,
                CompanionTutorialAdvancePacket::handleClientToServer
        );
        registrar.registerClientToServer(
                AmbientContextRequestPacket.TYPE,
                AmbientContextRequestPacket.STREAM_CODEC,
                AmbientContextRequestPacket::handleClientToServer
        );
    }

    /**
     * Register server-to-client packets
     */
    public static void registerServerToClientPackets(IPacketRegistrar registrar) {
        registrar.registerServerToClient(QuestActionResultPacket.TYPE, QuestActionResultPacket.STREAM_CODEC,
                QuestActionResultPacket::handleServerToClient);
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
        registrar.registerServerToClient(
                NotificationVolumeSyncPacket.TYPE,
                NotificationVolumeSyncPacket.STREAM_CODEC,
                NotificationVolumeSyncPacket::handleServerToClient
        );
        registrar.registerServerToClient(
                TankWaterFillSyncPacket.TYPE,
                TankWaterFillSyncPacket.STREAM_CODEC,
                TankWaterFillSyncPacket::handleServerToClient
        );
        registrar.registerServerToClient(
                TankInteriorLightSyncPacket.TYPE,
                TankInteriorLightSyncPacket.STREAM_CODEC,
                TankInteriorLightSyncPacket::handleServerToClient
        );
        registrar.registerServerToClient(
                ReactionBoostSyncPacket.TYPE,
                ReactionBoostSyncPacket.STREAM_CODEC,
                ReactionBoostSyncPacket::handleServerToClient
        );
        registrar.registerServerToClient(
                ReducedEffectsSyncPacket.TYPE,
                ReducedEffectsSyncPacket.STREAM_CODEC,
                ReducedEffectsSyncPacket::handleServerToClient
        );
        registrar.registerServerToClient(
                CompanionFeedbackPacket.TYPE,
                CompanionFeedbackPacket.STREAM_CODEC,
                CompanionFeedbackPacket::handleServerToClient
        );
        registrar.registerServerToClient(
                CompanionTutorialSyncPacket.TYPE,
                CompanionTutorialSyncPacket.STREAM_CODEC,
                CompanionTutorialSyncPacket::handleServerToClient
        );
        registrar.registerServerToClient(
                CompanionScenePacket.TYPE,
                CompanionScenePacket.STREAM_CODEC,
                CompanionScenePacket::handleServerToClient
        );
        registrar.registerServerToClient(
                AmbientContextResponsePacket.TYPE,
                AmbientContextResponsePacket.STREAM_CODEC,
                AmbientContextResponsePacket::handleServerToClient
        );
        registrar.registerServerToClient(
                FishSchoolSetupPacket.TYPE,
                FishSchoolSetupPacket.STREAM_CODEC,
                FishSchoolSetupPacket::handleServerToClient
        );
        registrar.registerServerToClient(
                FishSchoolInputPacket.TYPE,
                FishSchoolInputPacket.STREAM_CODEC,
                FishSchoolInputPacket::handleServerToClient
        );
        registrar.registerServerToClient(
                FishSchoolStatePacket.TYPE,
                FishSchoolStatePacket.STREAM_CODEC,
                FishSchoolStatePacket::handleServerToClient
        );
        registrar.registerServerToClient(
                CompanionPartySyncPacket.TYPE,
                CompanionPartySyncPacket.STREAM_CODEC,
                CompanionPartySyncPacket::handleServerToClient
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
        registrar.accept(NotificationVolumeSyncPacket.TYPE, NotificationVolumeSyncPacket.STREAM_CODEC);
        registrar.accept(TankWaterFillSyncPacket.TYPE, TankWaterFillSyncPacket.STREAM_CODEC);
        registrar.accept(TankInteriorLightSyncPacket.TYPE, TankInteriorLightSyncPacket.STREAM_CODEC);
        registrar.accept(ReducedEffectsSyncPacket.TYPE, ReducedEffectsSyncPacket.STREAM_CODEC);
        registrar.accept(ReactionBoostSyncPacket.TYPE, ReactionBoostSyncPacket.STREAM_CODEC);
        registrar.accept(CompanionFeedbackPacket.TYPE, CompanionFeedbackPacket.STREAM_CODEC);
        registrar.accept(CompanionTutorialSyncPacket.TYPE, CompanionTutorialSyncPacket.STREAM_CODEC);
        registrar.accept(CompanionScenePacket.TYPE, CompanionScenePacket.STREAM_CODEC);
        registrar.accept(CompanionPartySyncPacket.TYPE, CompanionPartySyncPacket.STREAM_CODEC);
        registrar.accept(AmbientContextResponsePacket.TYPE, AmbientContextResponsePacket.STREAM_CODEC);
        registrar.accept(FishSchoolSetupPacket.TYPE, FishSchoolSetupPacket.STREAM_CODEC);
        registrar.accept(FishSchoolInputPacket.TYPE, FishSchoolInputPacket.STREAM_CODEC);
        registrar.accept(FishSchoolStatePacket.TYPE, FishSchoolStatePacket.STREAM_CODEC);
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
