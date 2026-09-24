package grill24.fishtastic.neoforge;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticBlockEntityTypes;
import grill24.fishtastic.client.CosmeticCaptureClientState;
import grill24.fishtastic.client.EncyclopediaTutorialClientHandler;
import grill24.fishtastic.client.FishEncyclopediaClientCache;
import grill24.fishtastic.client.FishtasticBlockRenderLayers;
import grill24.fishtastic.client.FishtasticHudLayers;
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
import grill24.fishtastic.client.particle.LavaBubbleParticle;
import grill24.fishtastic.client.particle.LavaSplashParticle;
import grill24.fishtastic.client.particle.LavaWakeParticle;
import grill24.fishtastic.client.particle.MiniCampfireSmokeParticle;
import grill24.fishtastic.client.particle.MiniFlameParticle;
import grill24.fishtastic.client.particle.MiniSmokeParticle;
import grill24.fishtastic.client.particle.TankBubbleParticle;
import grill24.fishtastic.client.particle.TankBubblePopParticle;
import grill24.fishtastic.client.particle.TankMicroBubbleParticle;
import grill24.fishtastic.client.renderer.FishPileBlockEntityRenderer;
import grill24.fishtastic.client.renderer.FishTankBlockEntityRenderer;
import grill24.fishtastic.client.renderer.FishtasticShaders;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import grill24.fishtastic.client.util.ClientTickHandler;
import grill24.fishtastic.client.util.ClientTankFlocks;
import grill24.fishtastic.compat.GelatinScreensCompat;
import grill24.fishtastic.client.CosmeticTransformLoader;
import grill24.fishtastic.client.TankCosmeticTooltip;
import grill24.fishtastic.client.tooltip.ClientFishTankMaterialsTooltip;
import grill24.fishtastic.client.tooltip.ClientRodGearTooltip;
import grill24.fishtastic.client.tooltip.FishTankMaterialsTooltip;
import grill24.fishtastic.client.tooltip.RodGearTooltip;
import grill24.fishtastic.neoforge.fishtank.FishTankModel;
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
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
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

        // Register tank water fill toggle sync packet client handler
        grill24.fishtastic.network.TankWaterFillSyncPacket.registerClientHandler(
                packet -> grill24.fishtastic.client.FishtasticClientConfig.setTankWaterFillEnabled(packet.enabled()));

        // Install quest progress notification system
        QuestProgressNotificationManager.getInstance().install();

        modEventBus.addListener(FishtasticNeoForgeClient::registerClientReloadListeners);
        modEventBus.addListener(FishtasticNeoForgeClient::registerModelLoaders);
        // PORT-ONLY: the builtin/entity items (26.1's custom item model types, A5.3).
        modEventBus.addListener(FishtasticItemRendererNeoForge::register);
        modEventBus.addListener(FishtasticNeoForgeClient::registerRenderers);
        modEventBus.addListener(FishtasticNeoForgeClient::registerParticleProviders);
        modEventBus.addListener(FishtasticNeoForgeClient::registerShaders);
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
        // Draw the cosmetic-capture wand selection preview (PORT-ONLY: a world-render pass here)
        NeoForge.EVENT_BUS.addListener(FishtasticNeoForgeClient::onRenderLevelStage);
    }

    /**
     * The tank's model geometry, referenced from {@code models/block/fish_tank.json} as
     * {@code "loader": "fishtastic:fish_tank"}. (26.1.2 registers a blockstate-level model type
     * instead; 1.21.1 only has model-level loaders, and the blockstate redirect scan that 26.1.2
     * runs as a reload listener happens inside the tank's own bake, see FishTankGeometry.)
     */
    public static void registerModelLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(ft("fish_tank"), FishTankModel.Loader.INSTANCE);
        Fishtastic.LOGGER.info("Fishtastic model loaders registered.");
    }

    public static void registerClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(CosmeticTransformLoader.INSTANCE);
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
        event.registerSpriteSet(FishtasticParticleTypes.TINY_BUBBLE.value(), TankMicroBubbleParticle.TinyProvider::new);
        event.registerSpriteSet(FishtasticParticleTypes.SMALL_BUBBLE.value(), TankMicroBubbleParticle.SmallProvider::new);
        event.registerSpriteSet(FishtasticParticleTypes.MEDIUM_BUBBLE.value(), TankMicroBubbleParticle.MediumProvider::new);
        event.registerSpriteSet(FishtasticParticleTypes.TANK_BUBBLE_POP.value(), TankBubblePopParticle.Provider::new);
        event.registerSpriteSet(FishtasticParticleTypes.MINI_SMOKE.value(), MiniSmokeParticle.Provider::new);
        event.registerSpriteSet(FishtasticParticleTypes.MINI_FLAME.value(), MiniFlameParticle.Provider::new);
        event.registerSpriteSet(FishtasticParticleTypes.MINI_CAMPFIRE_SMOKE.value(), MiniCampfireSmokeParticle.Provider::new);
        event.registerSpriteSet(FishtasticParticleTypes.LAVA_WAKE.value(), LavaWakeParticle.Provider::new);
        event.registerSpriteSet(FishtasticParticleTypes.LAVA_BUBBLE.value(), LavaBubbleParticle.Provider::new);
        event.registerSpriteSet(FishtasticParticleTypes.LAVA_SPLASH.value(), LavaSplashParticle.Provider::new);
        Fishtastic.LOGGER.info("Fishtastic particle providers registered.");
    }

    /** PORT-ONLY: Fishtastic's core shader programs (26.1 builds RenderPipelines instead). */
    public static void registerShaders(RegisterShadersEvent event) {
        try {
            FishtasticShaders.registerAll((id, format, onLoad) ->
                    event.registerShader(new ShaderInstance(event.getResourceProvider(), id, format), onLoad));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("Failed to load Fishtastic shaders", e);
        }
    }

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
        // PORT-ONLY: the see-through blocks' chunk layers (26.1 derives them from texture alpha).
        // NeoForge 21.1 still honours the deprecated setRenderLayer for models with no render_type.
        event.enqueueWork(() -> FishtasticBlockRenderLayers.register(net.minecraft.client.renderer.ItemBlockRenderTypes::setRenderLayer));
    }

    public static void registerMenuScreens(final RegisterMenuScreensEvent event) {
        event.register(FishtasticClientSetup.fishTankAssemblyMenuType(), grill24.fishtastic.client.FishTankAssemblyScreen::new);
        event.register(FishtasticClientSetup.electricFishOrganizerMenuType(), grill24.fishtastic.client.ElectricFishOrganizerScreen::new);
        event.register(FishtasticClientSetup.fishTankBrowserMenuType(), grill24.fishtastic.client.FishTankBrowserScreen::new);
    }

    public static void onPlayerJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        ItemEffectManager.clearCache();
        ClientTankFlocks.clear();
    }

    public static void onPlayerLeave(ClientPlayerNetworkEvent.LoggingOut event) {
        QuestClientCache.reset();
        TutorialClientHandler.reset();
        EncyclopediaTutorialClientHandler.reset();
        FishEncyclopediaClientCache.reset();
        grill24.fishtastic.network.SetDayRatePacket.resetClientRate();
        CosmeticCaptureClientState.reset();
        ClientTankFlocks.clear();
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
        // PORT-ONLY: the rendering self-test (inert unless its marker file exists; see RenderSelfTest).
        grill24.fishtastic.client.selftest.RenderSelfTest.tick(mc, "neoforge");
        if (mc.level != null && !mc.isPaused()) {
            ClientTickHandler.tick(1.0f);
            ClientTankFlocks.tickAll();
            TutorialClientHandler.tick();
            // Handle key presses
            FishtasticKeyBinds.handleKeyPress(mc);
            // Tick quest progress notifications
            QuestProgressNotificationManager.getInstance().tick();
        }
    }

    /**
     * Draws the cosmetic-capture wand selection preview, if a session is active. PORT-ONLY: 1.21.1
     * has no per-tick gizmo collection (26.1's Gizmos), so this is a world-render pass.
     */
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        CosmeticCaptureClientState.render(event.getPoseStack(),
                Minecraft.getInstance().renderBuffers().bufferSource(), event.getCamera().getPosition());
    }

    public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        // PORT-ONLY: with no screen open, drawn after vanilla's toasts instead (FishtasticHudLayers).
        if (!FishtasticHudLayers.drawnInHudPass()) return;
        FishtasticHudLayers.renderTutorial(event.getGuiGraphics(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        TutorialClientHandler.renderScreenOverlay(event.getGuiGraphics(), event.getPartialTick());
        EncyclopediaTutorialClientHandler.render(event.getGuiGraphics(), event.getPartialTick());
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        // PORT-ONLY: with no screen open, drawn after vanilla's toasts instead (FishtasticHudLayers).
        if (!FishtasticHudLayers.drawnInHudPass()) return;
        // The fishing minigame, then the quest progress notifications over it
        FishtasticHudLayers.renderMinigameAndNotifications(event.getGuiGraphics(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }
}
