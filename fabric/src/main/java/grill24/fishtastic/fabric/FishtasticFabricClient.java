package grill24.fishtastic.fabric;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticBlockEntityTypes;
import grill24.fishtastic.FishtasticParticleTypes;
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
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.architectury.fabric.FabricPacketRegistrar;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.client.CosmeticTransformLoader;
import grill24.fishtastic.client.FishtasticClientSetup;
import grill24.fishtastic.client.FishtasticKeyBinds;
import grill24.fishtastic.client.TankCosmeticTooltip;
import grill24.fishtastic.client.particle.LavaBubbleParticle;
import grill24.fishtastic.client.particle.LavaSplashParticle;
import grill24.fishtastic.client.particle.LavaWakeParticle;
import grill24.fishtastic.client.particle.MiniCampfireSmokeParticle;
import grill24.fishtastic.client.particle.MiniFlameParticle;
import grill24.fishtastic.client.particle.MiniSmokeParticle;
import grill24.fishtastic.client.particle.TankBubbleParticle;
import grill24.fishtastic.client.renderer.FishTankBlockEntityRenderer;
import grill24.fishtastic.client.util.ClientTickHandler;
import grill24.fishtastic.client.tooltip.ClientRodGearTooltip;
import grill24.fishtastic.client.tooltip.RodGearTooltip;
import grill24.fishtastic.fabric.fishtank.BlockstateModelRedirectPlugin;
import grill24.fishtastic.fabric.fishtank.FishTankBlockStateModelFabric;
import grill24.fishtastic.fabric.fishtank.FishTankModelFabric;
import grill24.fishtastic.util.IGameRendererExtension;
import grill24.fishtastic.util.ItemActivationAnimation;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.server.packs.PackType;
import net.fabricmc.fabric.api.client.model.loading.v1.CustomUnbakedBlockStateModel;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.UnbakedModelDeserializer;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;

import static grill24.fishtastic.util.Utility.ft;

public final class FishtasticFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Register visual tooltip renderer for rod bait/hook/charm slots
        ClientTooltipComponentCallback.EVENT.register(component -> {
            if (component instanceof RodGearTooltip tooltip) {
                return new ClientRodGearTooltip(tooltip.bait(), tooltip.hook(), tooltip.charm());
            }
            return null;
        });

        // Mark every item usable as a tank cosmetic with a grey tooltip hint
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> TankCosmeticTooltip.append(stack, lines));

        // Load cosmetic transforms from assets/<namespace>/cosmetic_transforms/*.json
        ResourceLoader.get(PackType.CLIENT_RESOURCES)
            .registerReloadListener(CosmeticTransformLoader.ID, CosmeticTransformLoader.INSTANCE);

        // Build blockstate → model path redirect map before baking starts
        PreparableModelLoadingPlugin.register(BlockstateModelRedirectPlugin.LOADER, BlockstateModelRedirectPlugin.PLUGIN);

        // Register custom block state model type for fish tank
        CustomUnbakedBlockStateModel.register(ft("fish_tank"), FishTankBlockStateModelFabric.CODEC);

        // Register custom model loader for the fish tank item model
        UnbakedModelDeserializer.register(ft("fish_tank"), FishTankModelFabric.Loader.INSTANCE);

        // Register custom item model types
        FishtasticClientSetup.registerItemModelTypes();
        grill24.fishtastic.fabric.fishtank.FishTankItemModelFabric.register();

        // Register the Fish Tank Assembly menu screen
        net.minecraft.client.gui.screens.MenuScreens.register(
                FishtasticClientSetup.fishTankAssemblyMenuType(), grill24.fishtastic.client.FishTankAssemblyScreen::new);

        // Register network packets (client-side)
        FabricPacketRegistrar.registerClientReceiver();

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

        // Install quest progress notification system
        QuestProgressNotificationManager.getInstance().install();

        // Initialize and register key bindings
        FishtasticKeyBinds.init();
        KeyMappingHelper.registerKeyMapping(FishtasticKeyBinds.fishingMinigameImpulse);
        KeyMappingHelper.registerKeyMapping(FishtasticKeyBinds.openQuestLog);
        KeyMappingHelper.registerKeyMapping(FishtasticKeyBinds.toggleFishTankEditMode);
        KeyMappingHelper.registerKeyMapping(FishtasticKeyBinds.openFishEncyclopedia);

        // Register block entity renderer
        BlockEntityRendererRegistry.register(
            (BlockEntityType<FishTankBlockEntity>) FishtasticBlockEntityTypes.FISH_TANK.value(),
            FishTankBlockEntityRenderer::new
        );

        // Register tank bubble particle provider
        ParticleProviderRegistry.getInstance().register(FishtasticParticleTypes.TANK_BUBBLE.value(), TankBubbleParticle.Provider::new);
        ParticleProviderRegistry.getInstance().register(FishtasticParticleTypes.MINI_SMOKE.value(), MiniSmokeParticle.Provider::new);
        ParticleProviderRegistry.getInstance().register(FishtasticParticleTypes.MINI_FLAME.value(), MiniFlameParticle.Provider::new);
        ParticleProviderRegistry.getInstance().register(FishtasticParticleTypes.MINI_CAMPFIRE_SMOKE.value(), MiniCampfireSmokeParticle.Provider::new);

        // Register lava fishing bite-cycle particle providers
        ParticleProviderRegistry.getInstance().register(FishtasticParticleTypes.LAVA_WAKE.value(), LavaWakeParticle.Provider::new);
        ParticleProviderRegistry.getInstance().register(FishtasticParticleTypes.LAVA_BUBBLE.value(), LavaBubbleParticle.Provider::new);
        ParticleProviderRegistry.getInstance().register(FishtasticParticleTypes.LAVA_SPLASH.value(), LavaSplashParticle.Provider::new);

        // Clear caches on world join
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ItemEffectManager.clearCache());
        // Reset quest client cache and tutorial overlay on disconnect so stale data/UI doesn't persist across worlds
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            QuestClientCache.reset();
            TutorialClientHandler.reset();
            EncyclopediaTutorialClientHandler.reset();
            FishEncyclopediaClientCache.reset();
            CosmeticCaptureClientState.reset();
        });
        CommonLifecycleEvents.TAGS_LOADED.register((registries, isClient) -> {
            if (isClient) ItemEffectManager.clearCache();
        });

        // Register client tick event handler for animations
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Deliberately outside the paused/level guard below - the MCP orbit preview has to be able to
            // release its texture while the player sits in a menu, which is when the HUD isn't drawing.
            // Production builds exclude grill24.fishtastic.mcp from the jar (dev-only tooling).
            if (DevEnvironmentCheck.isDevelopmentEnvironment()) {
                McpOrbitPreviewOverlay.tick();
            }

            if (client.level != null && !client.isPaused()) {
                ClientTickHandler.tick(1.0f);
                TutorialClientHandler.tick();
                // Handle key presses
                FishtasticKeyBinds.handleKeyPress(client);
                // Tick quest progress notifications
                QuestProgressNotificationManager.getInstance().tick();
                // Draw the cosmetic-capture wand selection preview, if a session is active
                CosmeticCaptureClientState.tickGizmos();
            }
        });

        // Register tutorial overlay — must render BEFORE the minigame bar so the bar appears on top
        HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath(Fishtastic.MOD_ID, "tutorial_overlay"), (graphics, deltaTracker) -> {
            TutorialClientHandler.render(graphics, deltaTracker.getGameTimeDeltaPartialTick(false));
        });

        // Register HUD render hook for the fishing minigame overlay
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(Fishtastic.MOD_ID, "fishing_minigame"), (graphics, deltaTracker) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gameRenderer == null) return;
            ItemActivationAnimation animation = ((IGameRendererExtension) mc.gameRenderer).fishtastic$getActiveAnimation();
            if (animation != null && animation.isActive()) {
                animation.render(mc, graphics, deltaTracker.getGameTimeDeltaPartialTick(false));
            }
        });

        // Register HUD render hook for quest progress notifications (renders after fishing minigame)
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(Fishtastic.MOD_ID, "quest_progress_notification"), (graphics, deltaTracker) -> {
            QuestProgressNotificationManager.getInstance().render(graphics, deltaTracker.getGameTimeDeltaPartialTick(false));
        });

        // Dev tooling: brief preview of the MCP bridge's stitched orbit sheet. Not registered at all in
        // production builds, which exclude grill24.fishtastic.mcp from the jar entirely.
        if (DevEnvironmentCheck.isDevelopmentEnvironment()) {
            HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(Fishtastic.MOD_ID, "mcp_orbit_preview"), (graphics, deltaTracker) -> {
                McpOrbitPreviewOverlay.render(graphics);
            });
        }

        // Render tutorial text on top of the quest/shop screen (fires after the screen itself renders)
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, tickProgress) -> {
                TutorialClientHandler.renderScreenOverlay(graphics, tickProgress);
                EncyclopediaTutorialClientHandler.render(graphics, tickProgress);
            });
        });

        // TODO MC-26.1: ItemProperties.register is removed in 26.1
        // The fishing rod "cast" property must now be defined via data-driven item models

        // TODO MC-26.1: Block color handlers need to be re-implemented using the new BlockTintSource system
        // ColorProviderRegistry.BLOCK is removed; use BlockColorRegistry with BlockTintSource instead
    }
}
