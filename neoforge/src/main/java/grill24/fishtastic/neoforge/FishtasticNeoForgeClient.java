package grill24.fishtastic.neoforge;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticBlockEntityTypes;
import grill24.fishtastic.client.CosmeticCaptureClientState;
import grill24.fishtastic.env.DevEnvironmentCheck;
import grill24.fishtastic.mcp.client.McpOrbitPreviewOverlay;
import grill24.fishtastic.client.EncyclopediaTutorialClientHandler;
import grill24.fishtastic.client.FishEncyclopediaClientCache;
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
import grill24.fishtastic.client.FishtasticClientSetup;
import grill24.fishtastic.client.FishtasticKeyBinds;
import grill24.fishtastic.client.particle.LavaBubbleParticle;
import grill24.fishtastic.client.particle.LavaSplashParticle;
import grill24.fishtastic.client.particle.LavaWakeParticle;
import grill24.fishtastic.client.particle.MiniCampfireSmokeParticle;
import grill24.fishtastic.client.particle.MiniFlameParticle;
import grill24.fishtastic.client.particle.MiniSmokeParticle;
import grill24.fishtastic.client.particle.TankBubbleParticle;
import grill24.fishtastic.client.renderer.FishPileBlockEntityRenderer;
import grill24.fishtastic.client.renderer.FishTankBlockEntityRenderer;
import grill24.fishtastic.client.util.ClientTickHandler;
import grill24.fishtastic.compat.GelatinScreensCompat;
import grill24.fishtastic.client.CosmeticTransformLoader;
import grill24.fishtastic.client.TankCosmeticTooltip;
import grill24.fishtastic.neoforge.fishtank.BlockstateModelReloadListener;
import grill24.fishtastic.client.tooltip.ClientFishTankMaterialsTooltip;
import grill24.fishtastic.client.tooltip.ClientRodGearTooltip;
import grill24.fishtastic.client.tooltip.FishTankMaterialsTooltip;
import grill24.fishtastic.client.tooltip.RodGearTooltip;
import grill24.fishtastic.neoforge.fishtank.FishTankBlockStateModel;
import grill24.fishtastic.neoforge.fishtank.FishTankModel;
import grill24.fishtastic.util.IGameRendererExtension;
import grill24.fishtastic.util.ItemActivationAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterBlockStateModels;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.resources.VanillaClientListeners;
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

        // Register cosmetic capture wand session sync packet client handler
        CosmeticCaptureSyncPacket.registerClientHandler(CosmeticCaptureClientState::apply);

        // Register notification volume sync packet client handler
        grill24.fishtastic.network.NotificationVolumeSyncPacket.registerClientHandler(
                packet -> grill24.fishtastic.client.FishtasticClientConfig.setNotificationVolume(packet.volume()));

        // Install quest progress notification system
        QuestProgressNotificationManager.getInstance().install();

        modEventBus.addListener(FishtasticNeoForgeClient::registerClientReloadListeners);
        modEventBus.addListener(FishtasticNeoForgeClient::registerModelLoaders);
        modEventBus.addListener(FishtasticNeoForgeClient::registerBlockStateModels);
        modEventBus.addListener(FishtasticNeoForgeClient::onClientSetup);
        modEventBus.addListener(FishtasticNeoForgeClient::registerRenderers);
        modEventBus.addListener(FishtasticNeoForgeClient::registerParticleProviders);
        modEventBus.addListener(FishtasticNeoForgeClient::registerKeyMappings);
        modEventBus.addListener(FishtasticNeoForgeClient::registerTooltipComponents);
        modEventBus.addListener(FishtasticNeoForgeClient::registerMenuScreens);

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

    public static void registerModelLoaders(ModelEvent.RegisterLoaders event) {
        event.register(ft("fish_tank"), FishTankModel.Loader.INSTANCE);
        Fishtastic.LOGGER.info("Fishtastic model loaders registered.");
    }

    public static void registerBlockStateModels(RegisterBlockStateModels event) {
        // Register the custom block state model type for the fish tank.
        // Referenced in blockstates/fish_tank.json as "type": "fishtastic:fish_tank"
        event.registerModel(ft("fish_tank"), FishTankBlockStateModel.CODEC);
        Fishtastic.LOGGER.info("Fishtastic block state models registered.");
    }

    public static void registerClientReloadListeners(AddClientReloadListenersEvent event) {
        Identifier key = ft("blockstate_redirect");
        event.addListener(key, BlockstateModelReloadListener.INSTANCE);
        // Must complete before model baking so the redirect map is ready when FishTankBakedModel resolves textures.
        event.addDependency(key, VanillaClientListeners.MODELS);

        event.addListener(ft("cosmetic_transforms"), CosmeticTransformLoader.INSTANCE);
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
            (BlockEntityType<FishTankBlockEntity>) FishtasticBlockEntityTypes.FISH_TANK.value(),
            FishTankBlockEntityRenderer::new
        );
        event.registerBlockEntityRenderer(
            (BlockEntityType<FishPileBlockEntity>) FishtasticBlockEntityTypes.FISH_PILE.value(),
            FishPileBlockEntityRenderer::new
        );
        Fishtastic.LOGGER.info("Fishtastic block entity renderers registered.");
    }

    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(FishtasticParticleTypes.TANK_BUBBLE.value(), TankBubbleParticle.Provider::new);
        event.registerSpriteSet(FishtasticParticleTypes.MINI_SMOKE.value(), MiniSmokeParticle.Provider::new);
        event.registerSpriteSet(FishtasticParticleTypes.MINI_FLAME.value(), MiniFlameParticle.Provider::new);
        event.registerSpriteSet(FishtasticParticleTypes.MINI_CAMPFIRE_SMOKE.value(), MiniCampfireSmokeParticle.Provider::new);
        event.registerSpriteSet(FishtasticParticleTypes.LAVA_WAKE.value(), LavaWakeParticle.Provider::new);
        event.registerSpriteSet(FishtasticParticleTypes.LAVA_BUBBLE.value(), LavaBubbleParticle.Provider::new);
        event.registerSpriteSet(FishtasticParticleTypes.LAVA_SPLASH.value(), LavaSplashParticle.Provider::new);
        Fishtastic.LOGGER.info("Fishtastic particle providers registered.");
    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        FishtasticKeyBinds.init();
        event.register(FishtasticKeyBinds.fishingMinigameImpulse);
        event.register(FishtasticKeyBinds.openQuestLog);
        event.register(FishtasticKeyBinds.toggleFishTankEditMode);
        event.register(FishtasticKeyBinds.openFishEncyclopedia);
        Fishtastic.LOGGER.info("Fishtastic key mappings registered.");
    }

    public static void registerTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(RodGearTooltip.class, tooltip -> new ClientRodGearTooltip(tooltip.bait(), tooltip.hook(), tooltip.charm()));
        event.register(FishTankMaterialsTooltip.class, tooltip -> new ClientFishTankMaterialsTooltip(tooltip.frame(), tooltip.glass(), tooltip.sand()));
    }

    // TODO MC-26.1: Block color handlers need to be re-implemented using the new BlockTintSource system
    // TODO MC-26.1: ItemProperties is removed - fishing rod "cast" property needs data-driven item model

    public static void onClientSetup(final FMLClientSetupEvent event) {
        // Register custom item model types
        FishtasticClientSetup.registerItemModelTypes();
        grill24.fishtastic.neoforge.fishtank.FishTankItemModel.register();

        // TODO MC-26.1: ItemProperties.register is removed in 26.1
        // The fishing rod "cast" property must now be defined via data-driven item models
        Fishtastic.LOGGER.info("Fishtastic client setup complete.");
    }

    public static void registerMenuScreens(final RegisterMenuScreensEvent event) {
        event.register(FishtasticClientSetup.fishTankAssemblyMenuType(), grill24.fishtastic.client.FishTankAssemblyScreen::new);
    }

    public static void onPlayerJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        ItemEffectManager.clearCache();
    }

    public static void onPlayerLeave(ClientPlayerNetworkEvent.LoggingOut event) {
        QuestClientCache.reset();
        TutorialClientHandler.reset();
        EncyclopediaTutorialClientHandler.reset();
        FishEncyclopediaClientCache.reset();
        CosmeticCaptureClientState.reset();
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
        // Deliberately outside the paused/level guard below - the MCP orbit preview has to be able to
        // release its texture while the player sits in a menu, which is when the HUD isn't drawing.
        // Production builds exclude grill24.fishtastic.mcp from the jar (dev-only tooling).
        if (DevEnvironmentCheck.isDevelopmentEnvironment()) {
            McpOrbitPreviewOverlay.tick();
        }

        if (mc.level != null && !mc.isPaused()) {
            ClientTickHandler.tick(1.0f);
            TutorialClientHandler.tick();
            // Handle key presses
            FishtasticKeyBinds.handleKeyPress(mc);
            // Tick quest progress notifications
            QuestProgressNotificationManager.getInstance().tick();
            // Draw the cosmetic-capture wand selection preview, if a session is active
            CosmeticCaptureClientState.tickGizmos();
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
        // Dev tooling: brief preview of the MCP bridge's stitched orbit sheet. Production builds exclude
        // grill24.fishtastic.mcp from the jar entirely, hence the guard.
        if (DevEnvironmentCheck.isDevelopmentEnvironment()) {
            McpOrbitPreviewOverlay.render(event.getGuiGraphics());
        }
    }
}
