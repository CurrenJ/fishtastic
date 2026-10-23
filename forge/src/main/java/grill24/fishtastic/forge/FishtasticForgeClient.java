package grill24.fishtastic.forge;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticBlockEntityTypes;
import grill24.fishtastic.FishtasticParticleTypes;
import grill24.fishtastic.blockentity.FishPileBlockEntity;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.client.CompanionTutorialClientHandler;
import grill24.fishtastic.client.CosmeticCaptureClientState;
import grill24.fishtastic.client.CosmeticPlacementPreview;
import grill24.fishtastic.client.CosmeticTransformLoader;
import grill24.fishtastic.client.EncyclopediaTutorialClientHandler;
import grill24.fishtastic.client.FishEncyclopediaClientCache;
import grill24.fishtastic.client.FishtasticBlockRenderLayers;
import grill24.fishtastic.client.FishtasticClientSetup;
import grill24.fishtastic.client.FishtasticHudLayers;
import grill24.fishtastic.client.FishtasticItemProperties;
import grill24.fishtastic.client.FishtasticKeyBinds;
import grill24.fishtastic.client.QuestClientCache;
import grill24.fishtastic.client.QuestProgressNotificationManager;
import grill24.fishtastic.client.TankCosmeticTooltip;
import grill24.fishtastic.client.TutorialClientHandler;
import grill24.fishtastic.client.ambient.ClientAmbientFish;
import grill24.fishtastic.client.companion.ClientCompanions;
import grill24.fishtastic.client.companion.ClientCompanionsDev;
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
import grill24.fishtastic.client.tooltip.ClientFishTankMaterialsTooltip;
import grill24.fishtastic.client.tooltip.ClientRodGearTooltip;
import grill24.fishtastic.client.tooltip.FishTankMaterialsTooltip;
import grill24.fishtastic.client.tooltip.RodGearTooltip;
import grill24.fishtastic.client.util.ClientTankFlocks;
import grill24.fishtastic.client.util.ClientTickHandler;
import grill24.fishtastic.compat.GelatinScreensCompat;
import grill24.fishtastic.fishtank.CosmeticObstacles;
import grill24.fishtastic.forge.fishtank.FishTankModel;
import grill24.fishtastic.itemeffect.ItemEffectManager;
import grill24.fishtastic.network.CosmeticCaptureSyncPacket;
import grill24.fishtastic.network.EncyclopediaTutorialSyncPacket;
import grill24.fishtastic.network.FishEncyclopediaSyncPacket;
import grill24.fishtastic.network.QuestSyncPacket;
import grill24.fishtastic.network.TutorialSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Forge 52 client entrypoint. Instantiated from {@link FishtasticForge} only on the client. */
public final class FishtasticForgeClient {
    public FishtasticForgeClient() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        GelatinScreensCompat.init();
        grill24.fishtastic.architectury.forge.ForgePacketRegistrar.installClientPlayer(
                () -> net.minecraft.client.Minecraft.getInstance().player);

        QuestSyncPacket.registerClientHandler(packet ->
                QuestClientCache.update(packet.questProgress(), packet.tokenBalance(), packet.triggeringItems(),
                        packet.purchaseCounts(), packet.cleanupGoal(), packet.serverGameTime(),
                        packet.baitDepletedItem(), packet.firstCatchItems(), packet.shopRefreshCount(),
                        packet.shopRefreshNonce(), packet.freeShopRerollAvailable(),
                        packet.unlockedCompanions(), packet.newlyUnlockedCompanions()));
        TutorialSyncPacket.registerClientHandler(TutorialClientHandler.PACKET_HANDLER);
        EncyclopediaTutorialSyncPacket.registerClientHandler(EncyclopediaTutorialClientHandler.PACKET_HANDLER);
        grill24.fishtastic.network.CompanionTutorialSyncPacket.registerClientHandler(CompanionTutorialClientHandler.PACKET_HANDLER);
        grill24.fishtastic.network.CompanionFeedbackPacket.registerClientHandler(grill24.fishtastic.client.CompanionClientState.PACKET_HANDLER);
        grill24.fishtastic.network.CompanionScenePacket.registerClientHandler(packet ->
                ClientCompanions.onScenePacket(packet.scene(), packet.first(), packet.firstSlot(),
                        packet.second(), packet.secondSlot(), packet.startTime()));
        FishEncyclopediaSyncPacket.registerClientHandler(packet ->
                FishEncyclopediaClientCache.update(packet.personalCatchCounts(), packet.personalBestSizes(),
                        packet.globalBestSizes(), packet.claimedRewardKeys()));
        CosmeticCaptureSyncPacket.registerClientHandler(CosmeticCaptureClientState::apply);
        grill24.fishtastic.network.NotificationVolumeSyncPacket.registerClientHandler(
                packet -> grill24.fishtastic.client.FishtasticClientConfig.setNotificationVolume(packet.volume()));
        grill24.fishtastic.network.TankWaterFillSyncPacket.registerClientHandler(
                packet -> grill24.fishtastic.client.FishtasticClientConfig.setTankWaterFillEnabled(packet.enabled()));
        grill24.fishtastic.network.ReactionBoostSyncPacket.registerClientHandler(
                packet -> grill24.fishsim.core.FlockEngine.setTriggerRateBoost(packet.boost()));
        grill24.fishtastic.network.TankInteriorLightSyncPacket.registerClientHandler(
                packet -> grill24.fishtastic.client.renderer.TankInteriorLight.set(packet.level()));
        grill24.fishtastic.network.ReducedEffectsSyncPacket.registerClientHandler(
                packet -> grill24.fishtastic.client.FishtasticClientConfig.setReducedCelebrationEffects(packet.reduced()));
        grill24.fishtastic.network.AmbientContextResponsePacket.registerClientHandler(
                grill24.fishtastic.client.ambient.AmbientContextClient::handle);

        QuestProgressNotificationManager.getInstance().install();

        modEventBus.addListener(FishtasticForgeClient::registerClientReloadListeners);
        modEventBus.addListener(FishtasticForgeClient::registerModelLoaders);
        modEventBus.addListener(FishtasticForgeClient::registerRenderers);
        modEventBus.addListener(FishtasticForgeClient::registerParticleProviders);
        modEventBus.addListener(FishtasticForgeClient::registerShaders);
        modEventBus.addListener(FishtasticForgeClient::registerKeyMappings);
        modEventBus.addListener(FishtasticForgeClient::registerTooltipComponents);
        modEventBus.addListener(FishtasticForgeClient::registerGuiLayers);
        modEventBus.addListener(FishtasticForgeClient::onClientSetup);

        MinecraftForge.EVENT_BUS.addListener(FishtasticForgeClient::onPlayerJoin);
        MinecraftForge.EVENT_BUS.addListener(FishtasticForgeClient::onPlayerLeave);
        MinecraftForge.EVENT_BUS.addListener(FishtasticForgeClient::onTagsUpdated);
        MinecraftForge.EVENT_BUS.addListener(FishtasticForgeClient::onItemTooltip);
        MinecraftForge.EVENT_BUS.addListener(FishtasticForgeClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(FishtasticForgeClient::onScreenRenderPost);
        MinecraftForge.EVENT_BUS.addListener(FishtasticForgeClient::onRenderLevelStage);
        MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) ->
                grill24.fishtastic.client.ambient.AmbientClientCommands.register(event.getDispatcher(),
                        (source, message) -> source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(message), false)));
    }

    public static void registerModelLoaders(ModelEvent.RegisterGeometryLoaders event) {
        // Forge prefixes the active mod namespace itself; passing a full id produces
        // "fishtastic:fishtastic:fish_tank" and crashes client model loading.
        event.register("fish_tank", FishTankModel.Loader.INSTANCE);
        Fishtastic.LOGGER.info("Fishtastic model loaders registered.");
    }

    public static void registerClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(CosmeticTransformLoader.INSTANCE);
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager ->
                Minecraft.getInstance().execute(ClientAmbientFish::invalidate));
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                (BlockEntityType<FishTankBlockEntity>) FishtasticBlockEntityTypes.FISH_TANK.value(),
                FishTankBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(
                (BlockEntityType<FishPileBlockEntity>) FishtasticBlockEntityTypes.FISH_PILE.value(),
                FishPileBlockEntityRenderer::new);
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
        event.register(FishtasticKeyBinds.bondCompanion);
        Fishtastic.LOGGER.info("Fishtastic key mappings registered.");
    }

    public static void registerTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(RodGearTooltip.class, tooltip -> new ClientRodGearTooltip(tooltip.bait(), tooltip.hook(), tooltip.charm()));
        event.register(FishTankMaterialsTooltip.class, tooltip -> new ClientFishTankMaterialsTooltip(tooltip.frame(), tooltip.glass(), tooltip.sand()));
    }

    /** Forge 52 HUD layers replace NeoForge's RegisterGuiOverlaysEvent. */
    public static void registerGuiLayers(AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().add(Fishtastic.id("companion_dev"), (graphics, delta) ->
                ClientCompanionsDev.renderHud(graphics, delta.getGameTimeDeltaPartialTick(false)));
        event.getLayeredDraw().add(Fishtastic.id("tutorial"), (graphics, delta) -> {
            if (!FishtasticHudLayers.drawnInHudPass()) return;
            FishtasticHudLayers.renderTutorial(graphics, delta.getGameTimeDeltaPartialTick(false));
        });
        event.getLayeredDraw().add(Fishtastic.id("minigame"), (graphics, delta) -> {
            if (!FishtasticHudLayers.drawnInHudPass()) return;
            FishtasticHudLayers.renderMinigameAndNotifications(graphics, delta.getGameTimeDeltaPartialTick(false));
        });
    }

    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> FishtasticItemProperties.register(ItemProperties::register));
        event.enqueueWork(() -> FishtasticBlockRenderLayers.register(net.minecraft.client.renderer.ItemBlockRenderTypes::setRenderLayer));
        event.enqueueWork(() -> {
            MenuScreens.register(FishtasticClientSetup.companionMenuType(), grill24.fishtastic.client.CompanionScreen::new);
            MenuScreens.register(FishtasticClientSetup.fishTankAssemblyMenuType(), grill24.fishtastic.client.FishTankAssemblyScreen::new);
            MenuScreens.register(FishtasticClientSetup.electricFishOrganizerMenuType(), grill24.fishtastic.client.ElectricFishOrganizerScreen::new);
            MenuScreens.register(FishtasticClientSetup.fishTankBrowserMenuType(), grill24.fishtastic.client.FishTankBrowserScreen::new);
        });
    }

    public static void onPlayerJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        ItemEffectManager.clearCache();
        ClientTankFlocks.clear();
        ClientAmbientFish.clear();
    }

    public static void onPlayerLeave(ClientPlayerNetworkEvent.LoggingOut event) {
        QuestClientCache.reset();
        TutorialClientHandler.reset();
        EncyclopediaTutorialClientHandler.reset();
        CompanionTutorialClientHandler.reset();
        grill24.fishtastic.client.CompanionClientState.reset();
        FishEncyclopediaClientCache.reset();
        grill24.fishtastic.network.SetDayRatePacket.resetClientRate();
        CosmeticCaptureClientState.reset();
        ClientTankFlocks.clear();
        ClientAmbientFish.clear();
        grill24.fishtastic.companion.CompanionPartyClientCache.reset();
    }

    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED) {
            ItemEffectManager.clearCache();
            CosmeticObstacles.clearCache();
            ClientAmbientFish.invalidate();
        }
    }

    public static void onItemTooltip(ItemTooltipEvent event) {
        TankCosmeticTooltip.append(event.getItemStack(), event.getToolTip());
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        // Dev-only: the self-test class is excluded from the shipped jar. Resolved reflectively so
        // the release jar keeps no reference; the smoke build includes it and sets the property.
        if (grill24.fishtastic.env.DevEnvironmentCheck.isDevelopmentEnvironment()
                || Boolean.getBoolean("fishtastic.selftest")) {
            tickRenderSelfTest(mc);
        }
        ClientAmbientFish.tick();
        if (mc.level != null && !mc.isPaused()) {
            ClientTickHandler.tick(1.0f);
            ClientTankFlocks.tickAll();
            ClientCompanions.tick();
            TutorialClientHandler.tick();
            FishtasticKeyBinds.handleKeyPress(mc);
            QuestProgressNotificationManager.getInstance().tick();
        }
    }

    private static java.lang.reflect.Method renderSelfTestTick;
    private static boolean renderSelfTestMissing;

    private static void tickRenderSelfTest(Minecraft mc) {
        if (renderSelfTestMissing) return;
        if (renderSelfTestTick == null) {
            try {
                renderSelfTestTick = Class.forName("grill24.fishtastic.client.selftest.RenderSelfTest")
                        .getMethod("tick", Minecraft.class, String.class);
            } catch (ClassNotFoundException e) {
                renderSelfTestMissing = true;
                return;
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("RenderSelfTest.tick signature changed", e);
            }
        }
        try {
            renderSelfTestTick.invoke(null, mc, "forge");
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("RenderSelfTest failed", e);
        }
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        // Forge 52 passes the projection matrix as getPoseStack() at AFTER_ENTITIES.
        // LevelRenderer already applied the camera rotation to RenderSystem's model-view stack,
        // so these camera-relative vertices need an identity pose, just like vanilla entities.
        PoseStack poses = new PoseStack();
        Minecraft mc = Minecraft.getInstance();
        // Forge's event value is getRealtimeDeltaTicks(): frame duration, not tick interpolation.
        float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(false);
        CosmeticCaptureClientState.render(poses,
                Minecraft.getInstance().renderBuffers().bufferSource(), event.getCamera().getPosition());
        ClientCompanions.submitWorld(poses,
                mc.renderBuffers().bufferSource(), partialTick);
        ClientAmbientFish.submitWorld(poses, Minecraft.getInstance().renderBuffers().bufferSource(),
                event.getFrustum(), partialTick);
        CosmeticPlacementPreview.render(poses,
                Minecraft.getInstance().renderBuffers().bufferSource(), event.getCamera().getPosition());
    }

    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        TutorialClientHandler.renderScreenOverlay(event.getGuiGraphics(), event.getPartialTick());
        EncyclopediaTutorialClientHandler.render(event.getGuiGraphics(), event.getPartialTick());
        CompanionTutorialClientHandler.render(event.getGuiGraphics(), event.getPartialTick());
    }
}
