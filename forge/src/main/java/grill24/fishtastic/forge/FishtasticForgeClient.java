package grill24.fishtastic.forge;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticBlockEntityTypes;
import grill24.fishtastic.fishtank.CosmeticObstacles;
import grill24.fishtastic.client.CosmeticCaptureClientState;
import grill24.fishtastic.client.CosmeticPlacementPreview;
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
import grill24.fishtastic.client.particle.TankBubblePopParticle;
import grill24.fishtastic.client.particle.TankMicroBubbleParticle;
import grill24.fishtastic.client.renderer.FishPileBlockEntityRenderer;
import grill24.fishtastic.client.renderer.FishTankBlockEntityRenderer;
import grill24.fishtastic.client.renderer.FishtasticShaders;
import grill24.fishtastic.forge.fishtank.FishTankModel;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.client.event.RegisterShadersEvent;
import grill24.fishtastic.client.util.ClientTickHandler;
import grill24.fishtastic.client.util.ClientTankFlocks;
import grill24.fishtastic.compat.GelatinScreensCompat;
import grill24.fishtastic.client.CosmeticTransformLoader;
import grill24.fishtastic.client.TankCosmeticTooltip;
import grill24.fishtastic.client.tooltip.ClientFishTankMaterialsTooltip;
import grill24.fishtastic.client.tooltip.ClientRodGearTooltip;
import grill24.fishtastic.client.tooltip.FishTankMaterialsTooltip;
import grill24.fishtastic.client.tooltip.RodGearTooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Forge 47 client entrypoint. B5.3 deltas from NeoForge:
 * <ul>
 *   <li>constructor injection → {@code FMLJavaModLoadingContext.get().getModEventBus()}; the
 *       NeoForge game bus ({@code NeoForge.EVENT_BUS}) → {@code MinecraftForge.EVENT_BUS}.</li>
 *   <li>no {@code RegisterClientExtensionsEvent} — the builtin/entity item renderers are wired
 *       via reflection in {@link FishtasticItemRendererForge#register}, called from
 *       {@code FMLClientSetupEvent} instead of a mod-bus listener.</li>
 *   <li>no {@code RegisterMenuScreensEvent} — {@code MenuScreens.register} runs directly in
 *       {@code FMLClientSetupEvent#enqueueWork}.</li>
 *   <li>no {@code RenderGuiEvent}/{@code RegisterGuiLayersEvent} HUD hook — the tutorial and
 *       fishing-minigame HUD passes are registered as {@code IGuiOverlay}s through
 *       {@code RegisterGuiOverlaysEvent} instead (order preserved: tutorial registered first,
 *       so the minigame overlay draws above/after it, matching the old Pre-then-Post order).</li>
 *   <li>{@code ClientTickEvent} has no {@code Pre}/{@code Post} subtypes — phase-gated instead.</li>
 * </ul>
 *
 * <p>Not a {@code @Mod} class itself — Forge 47's {@code @Mod} has no {@code dist} filter (that's
 * NeoForge), so this is instantiated from {@link FishtasticForge}'s constructor behind an
 * {@code FMLEnvironment.dist == Dist.CLIENT} check instead, which keeps this class (and its
 * client-only imports) from ever being loaded on a dedicated server.
 */
public final class FishtasticForgeClient {
    public FishtasticForgeClient() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

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

        // Register tank interior light sync packet client handler
        grill24.fishtastic.network.TankInteriorLightSyncPacket.registerClientHandler(
                packet -> grill24.fishtastic.client.renderer.TankInteriorLight.set(packet.level()));

        // Register reduced celebration effects sync packet client handler
        grill24.fishtastic.network.ReducedEffectsSyncPacket.registerClientHandler(
                packet -> grill24.fishtastic.client.FishtasticClientConfig.setReducedCelebrationEffects(packet.reduced()));

        // Install quest progress notification system
        QuestProgressNotificationManager.getInstance().install();

        modEventBus.addListener(FishtasticForgeClient::registerClientReloadListeners);
        modEventBus.addListener(FishtasticForgeClient::registerModelLoaders);
        modEventBus.addListener(FishtasticForgeClient::registerRenderers);
        modEventBus.addListener(FishtasticForgeClient::registerParticleProviders);
        modEventBus.addListener(FishtasticForgeClient::registerShaders);
        modEventBus.addListener(FishtasticForgeClient::registerKeyMappings);
        modEventBus.addListener(FishtasticForgeClient::registerTooltipComponents);
        modEventBus.addListener(FishtasticForgeClient::registerGuiOverlays);
        modEventBus.addListener(FishtasticForgeClient::onClientSetup);

        // Clear the ItemEffect cache on world join and on tag sync (covers /reload without rejoin).
        MinecraftForge.EVENT_BUS.addListener(FishtasticForgeClient::onPlayerJoin);
        MinecraftForge.EVENT_BUS.addListener(FishtasticForgeClient::onPlayerLeave);
        MinecraftForge.EVENT_BUS.addListener(FishtasticForgeClient::onTagsUpdated);
        // Mark every item usable as a tank cosmetic with a grey tooltip hint
        MinecraftForge.EVENT_BUS.addListener(FishtasticForgeClient::onItemTooltip);

        // Register client tick event handler
        MinecraftForge.EVENT_BUS.addListener(FishtasticForgeClient::onClientTick);

        // Render tutorial text on top of quest/shop screen (fires after the screen renders)
        MinecraftForge.EVENT_BUS.addListener(FishtasticForgeClient::onScreenRenderPost);
        // Draw the cosmetic-capture wand selection preview (PORT-ONLY: a world-render pass here)
        MinecraftForge.EVENT_BUS.addListener(FishtasticForgeClient::onRenderLevelStage);
    }

    /**
     * The tank's model geometry, referenced from {@code models/block/fish_tank.json} as
     * {@code "loader": "fishtastic:fish_tank"}.
     */
    public static void registerModelLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register("fish_tank", FishTankModel.Loader.INSTANCE);
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

    /** PORT-ONLY: Fishtastic's core shader programs. */
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

    /**
     * The tutorial overlay draws first (registered first, so later registrations sit above it),
     * then the fishing minigame + notifications overlay — matching the old
     * {@code RenderGuiEvent.Pre}-then-{@code Post} order.
     */
    public static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("tutorial", (gui, graphics, partialTick, width, height) -> {
            // PORT-ONLY: with no screen open, drawn after vanilla's toasts instead (FishtasticHudLayers).
            if (!FishtasticHudLayers.drawnInHudPass()) return;
            FishtasticHudLayers.renderTutorial(graphics, partialTick);
        });
        event.registerAboveAll("minigame", (gui, graphics, partialTick, width, height) -> {
            // PORT-ONLY: with no screen open, drawn after vanilla's toasts instead (FishtasticHudLayers).
            if (!FishtasticHudLayers.drawnInHudPass()) return;
            // The fishing minigame, then the quest progress notifications over it
            FishtasticHudLayers.renderMinigameAndNotifications(graphics, partialTick);
        });
    }

    // TODO MC-26.1: Block color handlers need to be re-implemented using the new BlockTintSource system

    public static void onClientSetup(final FMLClientSetupEvent event) {
        // PORT-ONLY: the builtin/entity items (A5.3).
        event.enqueueWork(FishtasticItemRendererForge::register);

        // PORT-ONLY: the rod {@code cast} and book {@code has_alert} predicates the item models test.
        event.enqueueWork(() -> FishtasticItemProperties.register(ItemProperties::register));
        // PORT-ONLY: the see-through blocks' chunk layers.
        // Forge 47 still honours the deprecated setRenderLayer for models with no render_type.
        event.enqueueWork(() -> FishtasticBlockRenderLayers.register(net.minecraft.client.renderer.ItemBlockRenderTypes::setRenderLayer));

        // No RegisterMenuScreensEvent on Forge 47 — register screens directly here instead.
        event.enqueueWork(() -> {
            MenuScreens.register(FishtasticClientSetup.fishTankAssemblyMenuType(), grill24.fishtastic.client.FishTankAssemblyScreen::new);
            MenuScreens.register(FishtasticClientSetup.electricFishOrganizerMenuType(), grill24.fishtastic.client.ElectricFishOrganizerScreen::new);
            MenuScreens.register(FishtasticClientSetup.fishTankBrowserMenuType(), grill24.fishtastic.client.FishTankBrowserScreen::new);
        });
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
            CosmeticObstacles.clearCache();
        }
    }

    public static void onItemTooltip(ItemTooltipEvent event) {
        TankCosmeticTooltip.append(event.getItemStack(), event.getToolTip());
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        // Update tick counter for animations
        Minecraft mc = Minecraft.getInstance();
        // PORT-ONLY: the rendering self-test (inert unless its marker file exists; see RenderSelfTest).
        grill24.fishtastic.client.selftest.RenderSelfTest.tick(mc, "forge");
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
     * Draws the cosmetic-capture wand selection preview, if a session is active. PORT-ONLY: no
     * per-tick gizmo collection, so this is a world-render pass.
     */
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        CosmeticCaptureClientState.render(event.getPoseStack(),
                Minecraft.getInstance().renderBuffers().bufferSource(), event.getCamera().getPosition());
        // Highlight the tank cell a held cosmetic would be placed in
        CosmeticPlacementPreview.render(event.getPoseStack(),
                Minecraft.getInstance().renderBuffers().bufferSource(), event.getCamera().getPosition());
    }

    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        TutorialClientHandler.renderScreenOverlay(event.getGuiGraphics(), event.getPartialTick());
        EncyclopediaTutorialClientHandler.render(event.getGuiGraphics(), event.getPartialTick());
    }
}
