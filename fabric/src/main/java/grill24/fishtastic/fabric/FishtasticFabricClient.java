package grill24.fishtastic.fabric;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticBlockEntityTypes;
import grill24.fishtastic.FishtasticParticleTypes;
// PORT A5: import grill24.fishtastic.client.CosmeticCaptureClientState;
// PORT A4: import grill24.fishtastic.client.EncyclopediaTutorialClientHandler;
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
import grill24.fishtastic.blockentity.FishPileBlockEntity;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
// PORT A5: import grill24.fishtastic.client.CosmeticTransformLoader;
// PORT A5: import grill24.fishtastic.client.FishtasticClientSetup;
import grill24.fishtastic.client.FishtasticKeyBinds;
import grill24.fishtastic.client.TankCosmeticTooltip;
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
// PORT A4: import grill24.fishtastic.client.tooltip.ClientFishTankMaterialsTooltip;
// PORT A4: import grill24.fishtastic.client.tooltip.ClientRodGearTooltip;
import grill24.fishtastic.client.tooltip.FishTankMaterialsTooltip;
import grill24.fishtastic.client.tooltip.RodGearTooltip;
// PORT A5.2f: import grill24.fishtastic.fabric.fishtank.BlockstateModelRedirectPlugin;
// PORT A5.2f: import grill24.fishtastic.fabric.fishtank.FishTankBlockStateModelFabric;
// PORT A5.2f: import grill24.fishtastic.fabric.fishtank.FishTankModelFabric;
import grill24.fishtastic.util.IGameRendererExtension;
import grill24.fishtastic.util.ItemActivationAnimation;
import grill24.fishtastic.util.Ids;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
// PORT A5: import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.server.packs.PackType;
// PORT A5.2f: import net.fabricmc.fabric.api.client.model.loading.v1.CustomUnbakedBlockStateModel;
// PORT A5.2f: import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
// PORT A5.2f: import net.fabricmc.fabric.api.client.model.loading.v1.UnbakedModelDeserializer;
// PORT A5.5: import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
// PORT A4: import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
// PORT A4: import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BlockEntityType;

import static grill24.fishtastic.util.Utility.ft;

public final class FishtasticFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // PORT A4: client tooltip components.
//        // Register visual tooltip renderer for rod bait/hook/charm slots
//        ClientTooltipComponentCallback.EVENT.register(component -> {
//            if (component instanceof RodGearTooltip tooltip) {
//                return new ClientRodGearTooltip(tooltip.bait(), tooltip.hook(), tooltip.charm());
//            }
//            return null;
//        });

//        // Register visual tooltip renderer for fish tank frame/glass/sand material slots
//        ClientTooltipComponentCallback.EVENT.register(component -> {
//            if (component instanceof FishTankMaterialsTooltip tooltip) {
//                return new ClientFishTankMaterialsTooltip(tooltip.frame(), tooltip.glass(), tooltip.sand());
//            }
//            return null;
//        });

        // Mark every item usable as a tank cosmetic with a grey tooltip hint
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> TankCosmeticTooltip.append(stack, lines));

        // PORT A5: cosmetic transforms.
//        // Load cosmetic transforms from assets/<namespace>/cosmetic_transforms/*.json
//        ResourceLoader.get(PackType.CLIENT_RESOURCES)
//            .registerReloadListener(CosmeticTransformLoader.ID, CosmeticTransformLoader.INSTANCE);

        // PORT A5.2f/A5.3: tank models, item model types.
//        // Build blockstate → model path redirect map before baking starts
//        PreparableModelLoadingPlugin.register(BlockstateModelRedirectPlugin.LOADER, BlockstateModelRedirectPlugin.PLUGIN);

//        // Register custom block state model type for fish tank
//        CustomUnbakedBlockStateModel.register(ft("fish_tank"), FishTankBlockStateModelFabric.CODEC);

//        // Register custom model loader for the fish tank item model
//        UnbakedModelDeserializer.register(ft("fish_tank"), FishTankModelFabric.Loader.INSTANCE);

//        // Register custom item model types
//        FishtasticClientSetup.registerItemModelTypes();
//        grill24.fishtastic.fabric.fishtank.FishTankItemModelFabric.register();

        // PORT A4: menu screens.
//        // Register the Fish Tank Assembly menu screen
//        net.minecraft.client.gui.screens.MenuScreens.register(
//                FishtasticClientSetup.fishTankAssemblyMenuType(), grill24.fishtastic.client.FishTankAssemblyScreen::new);

//        // Register the Electric Fish Organizer menu screen
//        net.minecraft.client.gui.screens.MenuScreens.register(
//                FishtasticClientSetup.electricFishOrganizerMenuType(), grill24.fishtastic.client.ElectricFishOrganizerScreen::new);

//        // Register the Fish Tank Browser menu screen
//        net.minecraft.client.gui.screens.MenuScreens.register(
//                FishtasticClientSetup.fishTankBrowserMenuType(), grill24.fishtastic.client.FishTankBrowserScreen::new);

        // Register network packets (client-side)
        FabricPacketRegistrar.registerClientReceiver();

        // Register quest sync packet client handler
        QuestSyncPacket.registerClientHandler(packet ->
                QuestClientCache.update(packet.questProgress(), packet.tokenBalance(), packet.triggeringItems(),
                        packet.purchaseCounts(), packet.cleanupGoal(), packet.serverGameTime(),
                        packet.baitDepletedItem(), packet.firstCatchItems(), packet.shopRefreshCount()));

        // PORT A4: tutorial overlay.
//        // Register tutorial sync packet client handler
//        TutorialSyncPacket.registerClientHandler(TutorialClientHandler.PACKET_HANDLER);

        // PORT A4: encyclopedia tutorial.
//        // Register encyclopedia tutorial sync packet client handler
//        EncyclopediaTutorialSyncPacket.registerClientHandler(EncyclopediaTutorialClientHandler.PACKET_HANDLER);

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

        // PORT A4: quest toasts.
//        // Install quest progress notification system
//        QuestProgressNotificationManager.getInstance().install();

        // Initialize and register key bindings
        FishtasticKeyBinds.init();
        KeyBindingHelper.registerKeyBinding(FishtasticKeyBinds.fishingMinigameImpulse);
        KeyBindingHelper.registerKeyBinding(FishtasticKeyBinds.openQuestLog);
        KeyBindingHelper.registerKeyBinding(FishtasticKeyBinds.openFishEncyclopedia);
        KeyBindingHelper.registerKeyBinding(FishtasticKeyBinds.openLeaderboards);

        // PORT A5.1/A5.3: block entity renderers.
//        // Register block entity renderer
//        BlockEntityRendererRegistry.register(
//            (BlockEntityType<FishTankBlockEntity>) FishtasticBlockEntityTypes.FISH_TANK.value(),
//            FishTankBlockEntityRenderer::new
//        );
//        BlockEntityRendererRegistry.register(
//            (BlockEntityType<FishPileBlockEntity>) FishtasticBlockEntityTypes.FISH_PILE.value(),
//            FishPileBlockEntityRenderer::new
//        );

        // PORT A5.5: particles.
//        // Register tank bubble particle provider
//        ParticleProviderRegistry.getInstance().register(FishtasticParticleTypes.TANK_BUBBLE.value(), TankBubbleParticle.Provider::new);
//        ParticleProviderRegistry.getInstance().register(FishtasticParticleTypes.TINY_BUBBLE.value(), TankMicroBubbleParticle.TinyProvider::new);
//        ParticleProviderRegistry.getInstance().register(FishtasticParticleTypes.SMALL_BUBBLE.value(), TankMicroBubbleParticle.SmallProvider::new);
//        ParticleProviderRegistry.getInstance().register(FishtasticParticleTypes.MEDIUM_BUBBLE.value(), TankMicroBubbleParticle.MediumProvider::new);
//        ParticleProviderRegistry.getInstance().register(FishtasticParticleTypes.TANK_BUBBLE_POP.value(), TankBubblePopParticle.Provider::new);
//        ParticleProviderRegistry.getInstance().register(FishtasticParticleTypes.MINI_SMOKE.value(), MiniSmokeParticle.Provider::new);
//        ParticleProviderRegistry.getInstance().register(FishtasticParticleTypes.MINI_FLAME.value(), MiniFlameParticle.Provider::new);
//        ParticleProviderRegistry.getInstance().register(FishtasticParticleTypes.MINI_CAMPFIRE_SMOKE.value(), MiniCampfireSmokeParticle.Provider::new);

//        // Register lava fishing bite-cycle particle providers
//        ParticleProviderRegistry.getInstance().register(FishtasticParticleTypes.LAVA_WAKE.value(), LavaWakeParticle.Provider::new);
//        ParticleProviderRegistry.getInstance().register(FishtasticParticleTypes.LAVA_BUBBLE.value(), LavaBubbleParticle.Provider::new);
//        ParticleProviderRegistry.getInstance().register(FishtasticParticleTypes.LAVA_SPLASH.value(), LavaSplashParticle.Provider::new);

        // Clear caches on world join
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ItemEffectManager.clearCache();
            // PORT A5.1: client flocks.
//            ClientTankFlocks.clear();
        });
        // Reset quest client cache and tutorial overlay on disconnect so stale data/UI doesn't persist across worlds
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            QuestClientCache.reset();
            // PORT A4: tutorial overlays.
//            TutorialClientHandler.reset();
//            EncyclopediaTutorialClientHandler.reset();
            FishEncyclopediaClientCache.reset();
            grill24.fishtastic.network.SetDayRatePacket.resetClientRate();
            // PORT A5: cosmetic capture gizmos.
//            CosmeticCaptureClientState.reset();
            // PORT A5.1: client flocks.
//            ClientTankFlocks.clear();
        });
        CommonLifecycleEvents.TAGS_LOADED.register((registries, isClient) -> {
            if (isClient) ItemEffectManager.clearCache();
        });

        // Register client tick event handler for animations
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != null && !client.isPaused()) {
                ClientTickHandler.tick(1.0f);
                // PORT A5.1: client flocks.
//                ClientTankFlocks.tickAll();
                // PORT A4: tutorial overlay.
//                TutorialClientHandler.tick();
                // Handle key presses
                FishtasticKeyBinds.handleKeyPress(client);
                // PORT A4: quest toasts.
//                // Tick quest progress notifications
//                QuestProgressNotificationManager.getInstance().tick();
                // PORT A5: cosmetic capture gizmos.
//                // Draw the cosmetic-capture wand selection preview, if a session is active
//                CosmeticCaptureClientState.tickGizmos();
            }
        });

        // PORT A4: HUD and screen overlays (Fabric HUD API names change too).
//        // Register tutorial overlay — must render BEFORE the minigame bar so the bar appears on top
//        HudElementRegistry.addFirst(Ids.of(Fishtastic.MOD_ID, "tutorial_overlay"), (graphics, deltaTracker) -> {
//            TutorialClientHandler.render(graphics, deltaTracker.getGameTimeDeltaPartialTick(false));
//        });

//        // Register HUD render hook for the fishing minigame overlay
//        HudElementRegistry.addLast(Ids.of(Fishtastic.MOD_ID, "fishing_minigame"), (graphics, deltaTracker) -> {
//            Minecraft mc = Minecraft.getInstance();
//            if (mc.gameRenderer == null) return;
//            ItemActivationAnimation animation = ((IGameRendererExtension) mc.gameRenderer).fishtastic$getActiveAnimation();
//            if (animation != null && animation.isActive()) {
//                animation.render(mc, graphics, deltaTracker.getGameTimeDeltaPartialTick(false));
//            }
//        });

//        // Register HUD render hook for quest progress notifications (renders after fishing minigame)
//        HudElementRegistry.addLast(Ids.of(Fishtastic.MOD_ID, "quest_progress_notification"), (graphics, deltaTracker) -> {
//            QuestProgressNotificationManager.getInstance().render(graphics, deltaTracker.getGameTimeDeltaPartialTick(false));
//        });

//        // Render tutorial text on top of the quest/shop screen (fires after the screen itself renders)
//        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
//            ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, tickProgress) -> {
//                TutorialClientHandler.renderScreenOverlay(graphics, tickProgress);
//                EncyclopediaTutorialClientHandler.render(graphics, tickProgress);
//            });
//        });

        // TODO MC-26.1: ItemProperties.register is removed in 26.1
        // The fishing rod "cast" property must now be defined via data-driven item models

        // TODO MC-26.1: Block color handlers need to be re-implemented using the new BlockTintSource system
        // ColorProviderRegistry.BLOCK is removed; use BlockColorRegistry with BlockTintSource instead
    }
}
