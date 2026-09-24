package grill24.fishtastic.neoforge;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticBlockEntityTypes;
// PORT A5: import grill24.fishtastic.client.CosmeticCaptureClientState;
import grill24.fishtastic.client.EncyclopediaTutorialClientHandler;
import grill24.fishtastic.client.FishEncyclopediaClientCache;
import grill24.fishtastic.client.FishtasticItemProperties;
import grill24.fishtastic.client.QuestClientCache;
import grill24.fishtastic.client.QuestProgressNotificationManager;
import grill24.fishtastic.client.TutorialClientHandler;
import grill24.fishtastic.network.CosmeticCaptureSyncPacket;
import grill24.fishtastic.network.EncyclopediaTutorialSyncPacket;
import grill24.fishtastic.network.FishEncyclopediaSyncPacket;
import grill24.fishtastic.network.TutorialSyncPacket;
import grill24.fishtastic.itemeffect.ItemEffectManager;
import grill24.fishtastic.network.QuestSyncPacket;
import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.blockentity.FishPileBlockEntity;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.FishtasticParticleTypes;
// A4 needs this class for its menu-type accessors; A5 adds the item model types.
import grill24.fishtastic.client.FishtasticClientSetup;
import grill24.fishtastic.client.FishtasticKeyBinds;
// PORT A5.5: import grill24.fishtastic.client.particle.LavaBubbleParticle;
// PORT A5.5: import grill24.fishtastic.client.particle.LavaSplashParticle;
// PORT A5.5: import grill24.fishtastic.client.particle.LavaWakeParticle;
// PORT A5.5: import grill24.fishtastic.client.particle.MiniCampfireSmokeParticle;
// PORT A5.5: import grill24.fishtastic.client.particle.MiniFlameParticle;
// PORT A5.5: import grill24.fishtastic.client.particle.MiniSmokeParticle;
// PORT A5.5: import grill24.fishtastic.client.particle.TankBubbleParticle;
// PORT A5.5: import grill24.fishtastic.client.particle.TankBubblePopParticle;
// PORT A5.5: import grill24.fishtastic.client.particle.TankMicroBubbleParticle;
// PORT A5.3: import grill24.fishtastic.client.renderer.FishPileBlockEntityRenderer;
import grill24.fishtastic.client.renderer.FishTankBlockEntityRenderer;
import grill24.fishtastic.client.util.ClientTickHandler;
// PORT A5.1: import grill24.fishtastic.client.util.ClientTankFlocks;
import grill24.fishtastic.compat.GelatinScreensCompat;
// PORT A5: import grill24.fishtastic.client.CosmeticTransformLoader;
import grill24.fishtastic.client.TankCosmeticTooltip;
// PORT A5.2: import grill24.fishtastic.neoforge.fishtank.BlockstateModelReloadListener;
import grill24.fishtastic.client.tooltip.ClientFishTankMaterialsTooltip;
import grill24.fishtastic.client.tooltip.ClientRodGearTooltip;
import grill24.fishtastic.client.tooltip.FishTankMaterialsTooltip;
import grill24.fishtastic.client.tooltip.RodGearTooltip;
// PORT A5.2: import grill24.fishtastic.neoforge.fishtank.FishTankBlockStateModel;
// PORT A5.2: import grill24.fishtastic.neoforge.fishtank.FishTankModel;
import grill24.fishtastic.util.IGameRendererExtension;
import grill24.fishtastic.util.ItemActivationAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
// PORT A5: import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
// PORT A5.2: import net.neoforged.neoforge.client.event.RegisterBlockStateModels;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
// PORT A5.2: import net.neoforged.neoforge.client.resources.VanillaClientListeners;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

import static grill24.fishtastic.util.Utility.ft;

@Mod(value = Fishtastic.MOD_ID, dist = Dist.CLIENT)
public final class FishtasticNeoForgeClient {
    public FishtasticNeoForgeClient(IEventBus modEventBus) {
        // Try to register GelatinUI screens, if GelatinUI is present.
        GelatinScreensCompat.init();

        // Register quest sync packet client handler
        QuestSyncPacket.registerClientHandler(packet ->
                QuestClientCache.update(packet.questProgress(), packet.tokenBalance(), packet.triggeringItems(),
                        packet.purchaseCounts(), packet.cleanupGoal(), packet.serverGameTime(),
                        packet.baitDepletedItem(), packet.firstCatchItems(), packet.shopRefreshCount()));

        // Register tutorial sync packet client handler
        TutorialSyncPacket.registerClientHandler(TutorialClientHandler.PACKET_HANDLER);

        // Register encyclopedia tutorial sync packet client handler
        EncyclopediaTutorialSyncPacket.registerClientHandler(EncyclopediaTutorialClientHandler.PACKET_HANDLER);

        // Register fish encyclopedia sync packet client handler
        FishEncyclopediaSyncPacket.registerClientHandler(packet ->
                FishEncyclopediaClientCache.update(packet.personalCatchCounts(), packet.personalBestSizes(), packet.globalBestSizes(),
                        packet.claimedRewardKeys()));

        // PORT A5: cosmetic capture gizmos.
//        // Register cosmetic capture wand session sync packet client handler
//        CosmeticCaptureSyncPacket.registerClientHandler(CosmeticCaptureClientState::apply);

        // Register notification volume sync packet client handler
        grill24.fishtastic.network.NotificationVolumeSyncPacket.registerClientHandler(
                packet -> grill24.fishtastic.client.FishtasticClientConfig.setNotificationVolume(packet.volume()));

        // Register tank water fill toggle sync packet client handler
        grill24.fishtastic.network.TankWaterFillSyncPacket.registerClientHandler(
                packet -> grill24.fishtastic.client.FishtasticClientConfig.setTankWaterFillEnabled(packet.enabled()));

        // Install quest progress notification system
        QuestProgressNotificationManager.getInstance().install();

        // PORT A5: cosmetic transforms, blockstate redirects.
//        modEventBus.addListener(FishtasticNeoForgeClient::registerClientReloadListeners);
        // PORT A5.2: tank models.
//        modEventBus.addListener(FishtasticNeoForgeClient::registerModelLoaders);
//        modEventBus.addListener(FishtasticNeoForgeClient::registerBlockStateModels);
        // PORT A5.3: client item models.
//        modEventBus.addListener(FishtasticNeoForgeClient::onClientSetup);
        // PORT A5.1/A5.3: block entity renderers.
//        modEventBus.addListener(FishtasticNeoForgeClient::registerRenderers);
        // PORT A5.5: particles.
//        modEventBus.addListener(FishtasticNeoForgeClient::registerParticleProviders);
        modEventBus.addListener(FishtasticNeoForgeClient::registerKeyMappings);
        modEventBus.addListener(FishtasticNeoForgeClient::registerTooltipComponents);
        modEventBus.addListener(FishtasticNeoForgeClient::registerMenuScreens);
        modEventBus.addListener(FishtasticNeoForgeClient::registerItemProperties);

        // Clear the ItemEffect cache on world join and on tag sync (covers /reload without rejoin).
        NeoForge.EVENT_BUS.addListener(FishtasticNeoForgeClient::onPlayerJoin);
        NeoForge.EVENT_BUS.addListener(FishtasticNeoForgeClient::onPlayerLeave);
        NeoForge.EVENT_BUS.addListener(FishtasticNeoForgeClient::onTagsUpdated);
        // Mark every item usable as a tank cosmetic with a grey tooltip hint
        NeoForge.EVENT_BUS.addListener(FishtasticNeoForgeClient::onItemTooltip);

        // Register client tick event handler
        NeoForge.EVENT_BUS.addListener(FishtasticNeoForgeClient::onClientTick);

        // Register tutorial overlay — fires BEFORE the minigame bar so the bar appears on top
        NeoForge.EVENT_BUS.addListener(FishtasticNeoForgeClient::onRenderGuiPre);
        // Render tutorial text on top of quest/shop screen (fires after the screen renders)
        NeoForge.EVENT_BUS.addListener(FishtasticNeoForgeClient::onScreenRenderPost);
        // Register HUD render hook for the fishing minigame overlay
        NeoForge.EVENT_BUS.addListener(FishtasticNeoForgeClient::onRenderGui);
    }

    // PORT A5/A5.2: model loaders, blockstate models, reload listeners.
//    public static void registerModelLoaders(ModelEvent.RegisterLoaders event) {
//        event.register(ft("fish_tank"), FishTankModel.Loader.INSTANCE);
//        Fishtastic.LOGGER.info("Fishtastic model loaders registered.");
//    }

//    public static void registerBlockStateModels(RegisterBlockStateModels event) {
//        // Register the custom block state model type for the fish tank.
//        // Referenced in blockstates/fish_tank.json as "type": "fishtastic:fish_tank"
//        event.registerModel(ft("fish_tank"), FishTankBlockStateModel.CODEC);
//        Fishtastic.LOGGER.info("Fishtastic block state models registered.");
//    }

//    public static void registerClientReloadListeners(AddClientReloadListenersEvent event) {
//        ResourceLocation key = ft("blockstate_redirect");
//        event.addListener(key, BlockstateModelReloadListener.INSTANCE);
//        // Must complete before model baking so the redirect map is ready when FishTankBakedModel resolves textures.
//        event.addDependency(key, VanillaClientListeners.MODELS);

//        event.addListener(ft("cosmetic_transforms"), CosmeticTransformLoader.INSTANCE);
//    }

    // PORT A5.1/A5.3/A5.5: block entity renderers, particles.
//    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
//        event.registerBlockEntityRenderer(
//            (BlockEntityType<FishTankBlockEntity>) FishtasticBlockEntityTypes.FISH_TANK.value(),
//            FishTankBlockEntityRenderer::new
//        );
//        event.registerBlockEntityRenderer(
//            (BlockEntityType<FishPileBlockEntity>) FishtasticBlockEntityTypes.FISH_PILE.value(),
//            FishPileBlockEntityRenderer::new
//        );
//        Fishtastic.LOGGER.info("Fishtastic block entity renderers registered.");
//    }

//    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
//        event.registerSpriteSet(FishtasticParticleTypes.TANK_BUBBLE.value(), TankBubbleParticle.Provider::new);
//        event.registerSpriteSet(FishtasticParticleTypes.TINY_BUBBLE.value(), TankMicroBubbleParticle.TinyProvider::new);
//        event.registerSpriteSet(FishtasticParticleTypes.SMALL_BUBBLE.value(), TankMicroBubbleParticle.SmallProvider::new);
//        event.registerSpriteSet(FishtasticParticleTypes.MEDIUM_BUBBLE.value(), TankMicroBubbleParticle.MediumProvider::new);
//        event.registerSpriteSet(FishtasticParticleTypes.TANK_BUBBLE_POP.value(), TankBubblePopParticle.Provider::new);
//        event.registerSpriteSet(FishtasticParticleTypes.MINI_SMOKE.value(), MiniSmokeParticle.Provider::new);
//        event.registerSpriteSet(FishtasticParticleTypes.MINI_FLAME.value(), MiniFlameParticle.Provider::new);
//        event.registerSpriteSet(FishtasticParticleTypes.MINI_CAMPFIRE_SMOKE.value(), MiniCampfireSmokeParticle.Provider::new);
//        event.registerSpriteSet(FishtasticParticleTypes.LAVA_WAKE.value(), LavaWakeParticle.Provider::new);
//        event.registerSpriteSet(FishtasticParticleTypes.LAVA_BUBBLE.value(), LavaBubbleParticle.Provider::new);
//        event.registerSpriteSet(FishtasticParticleTypes.LAVA_SPLASH.value(), LavaSplashParticle.Provider::new);
//        Fishtastic.LOGGER.info("Fishtastic particle providers registered.");
//    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        FishtasticKeyBinds.init();
        event.register(FishtasticKeyBinds.fishingMinigameImpulse);
        event.register(FishtasticKeyBinds.openQuestLog);
        event.register(FishtasticKeyBinds.openFishEncyclopedia);
        event.register(FishtasticKeyBinds.openLeaderboards);
        Fishtastic.LOGGER.info("Fishtastic key mappings registered.");
    }

    public static void registerTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(RodGearTooltip.class, tooltip -> new ClientRodGearTooltip(tooltip.bait(), tooltip.hook(), tooltip.charm()));
        event.register(FishTankMaterialsTooltip.class, tooltip -> new ClientFishTankMaterialsTooltip(tooltip.frame(), tooltip.glass(), tooltip.sand()));
    }

    // TODO MC-26.1: Block color handlers need to be re-implemented using the new BlockTintSource system

    /** PORT-ONLY: the rod {@code cast} and book {@code has_alert} predicates the item models test. */
    public static void registerItemProperties(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> FishtasticItemProperties.register(ItemProperties::register));
    }

    // PORT A5.3: client item models.
//    public static void onClientSetup(final FMLClientSetupEvent event) {
//        // Register custom item model types
//        FishtasticClientSetup.registerItemModelTypes();
//        grill24.fishtastic.neoforge.fishtank.FishTankItemModel.register();

//        // TODO MC-26.1: ItemProperties.register is removed in 26.1
//        // The fishing rod "cast" property must now be defined via data-driven item models
//        Fishtastic.LOGGER.info("Fishtastic client setup complete.");
//    }

    public static void registerMenuScreens(final RegisterMenuScreensEvent event) {
        event.register(FishtasticClientSetup.fishTankAssemblyMenuType(), grill24.fishtastic.client.FishTankAssemblyScreen::new);
        event.register(FishtasticClientSetup.electricFishOrganizerMenuType(), grill24.fishtastic.client.ElectricFishOrganizerScreen::new);
        event.register(FishtasticClientSetup.fishTankBrowserMenuType(), grill24.fishtastic.client.FishTankBrowserScreen::new);
    }

    public static void onPlayerJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        ItemEffectManager.clearCache();
        // PORT A5.1: client flocks.
//        ClientTankFlocks.clear();
    }

    public static void onPlayerLeave(ClientPlayerNetworkEvent.LoggingOut event) {
        QuestClientCache.reset();
        TutorialClientHandler.reset();
        EncyclopediaTutorialClientHandler.reset();
        FishEncyclopediaClientCache.reset();
        grill24.fishtastic.network.SetDayRatePacket.resetClientRate();
        // PORT A5: cosmetic capture gizmos.
//        CosmeticCaptureClientState.reset();
        // PORT A5.1: client flocks.
//        ClientTankFlocks.clear();
    }

    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED) {
            ItemEffectManager.clearCache();
        }
    }

    public static void onItemTooltip(ItemTooltipEvent event) {
        TankCosmeticTooltip.append(event.getItemStack(), event.getToolTip());
    }

    public static void onClientTick(ClientTickEvent.Pre event) {
        // Update tick counter for animations
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && !mc.isPaused()) {
            ClientTickHandler.tick(1.0f);
            // PORT A5.1: client flocks.
//            ClientTankFlocks.tickAll();
            TutorialClientHandler.tick();
            // Handle key presses
            FishtasticKeyBinds.handleKeyPress(mc);
            // Tick quest progress notifications
            QuestProgressNotificationManager.getInstance().tick();
            // PORT A5: cosmetic capture gizmos.
//            // Draw the cosmetic-capture wand selection preview, if a session is active
//            CosmeticCaptureClientState.tickGizmos();
        }
    }

    public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        TutorialClientHandler.render(event.getGuiGraphics(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        TutorialClientHandler.renderScreenOverlay(event.getGuiGraphics(), event.getPartialTick());
        EncyclopediaTutorialClientHandler.render(event.getGuiGraphics(), event.getPartialTick());
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null) return;
        ItemActivationAnimation animation = ((IGameRendererExtension) mc.gameRenderer).fishtastic$getActiveAnimation();
        if (animation != null && animation.isActive()) {
            animation.render(mc, event.getGuiGraphics(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
        }
        // Render quest progress notifications (after fishing minigame)
        QuestProgressNotificationManager.getInstance().render(event.getGuiGraphics(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }
}
