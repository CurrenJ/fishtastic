package grill24.fishtastic.fabric;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticBlockEntityTypes;
import grill24.fishtastic.FishtasticParticleTypes;
// PORT A5: import grill24.fishtastic.client.CosmeticCaptureClientState;
import grill24.fishtastic.client.EncyclopediaTutorialClientHandler;
import grill24.fishtastic.client.FishEncyclopediaClientCache;
import grill24.fishtastic.client.FishtasticBlockRenderLayers;
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
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.architectury.fabric.FabricPacketRegistrar;
import grill24.fishtastic.blockentity.FishPileBlockEntity;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.client.CosmeticTransformLoader;
// A4 needs this class for its menu-type accessors; A5 adds the item model types.
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
import grill24.fishtastic.client.particle.TankBubblePopParticle;
import grill24.fishtastic.client.particle.TankMicroBubbleParticle;
import grill24.fishtastic.client.renderer.FishPileBlockEntityRenderer;
import grill24.fishtastic.client.renderer.FishTankBlockEntityRenderer;
import grill24.fishtastic.client.renderer.FishtasticShaders;
import grill24.fishtastic.client.util.ClientTickHandler;
import grill24.fishtastic.client.util.ClientTankFlocks;
import grill24.fishtastic.client.tooltip.ClientFishTankMaterialsTooltip;
import grill24.fishtastic.client.tooltip.ClientRodGearTooltip;
import grill24.fishtastic.client.tooltip.FishTankMaterialsTooltip;
import grill24.fishtastic.client.tooltip.RodGearTooltip;
import grill24.fishtastic.fabric.fishtank.FishTankModelFabric;
import grill24.fishtastic.util.IGameRendererExtension;
import grill24.fishtastic.util.ItemActivationAnimation;
import grill24.fishtastic.util.Ids;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.server.packs.PackType;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.level.block.entity.BlockEntityType;

import static grill24.fishtastic.util.Utility.ft;

public final class FishtasticFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Register visual tooltip renderer for rod bait/hook/charm slots
        TooltipComponentCallback.EVENT.register(component -> {
            if (component instanceof RodGearTooltip tooltip) {
                return new ClientRodGearTooltip(tooltip.bait(), tooltip.hook(), tooltip.charm());
            }
            return null;
        });

        // Register visual tooltip renderer for fish tank frame/glass/sand material slots
        TooltipComponentCallback.EVENT.register(component -> {
            if (component instanceof FishTankMaterialsTooltip tooltip) {
                return new ClientFishTankMaterialsTooltip(tooltip.frame(), tooltip.glass(), tooltip.sand());
            }
            return null;
        });

        // Mark every item usable as a tank cosmetic with a grey tooltip hint
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> TankCosmeticTooltip.append(stack, lines));

        // Load cosmetic transforms from assets/<namespace>/cosmetic_transforms/*.json. PORT-ONLY:
        // Fabric API 0.116 takes an IdentifiableResourceReloadListener (26.1's ResourceLoader takes
        // the id separately), so the shared loader is wrapped with its id here.
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new IdentifiableResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return CosmeticTransformLoader.ID;
            }

            @Override
            public java.util.concurrent.CompletableFuture<Void> reload(PreparationBarrier barrier,
                    net.minecraft.server.packs.resources.ResourceManager manager,
                    net.minecraft.util.profiling.ProfilerFiller preparationsProfiler,
                    net.minecraft.util.profiling.ProfilerFiller reloadProfiler,
                    java.util.concurrent.Executor backgroundExecutor, java.util.concurrent.Executor gameExecutor) {
                return CosmeticTransformLoader.INSTANCE.reload(barrier, manager, preparationsProfiler,
                        reloadProfiler, backgroundExecutor, gameExecutor);
            }
        });

        // The fish tank's block and item model (26.1.2 registers a blockstate model type and an item
        // model type instead; the blockstate redirect scan runs inside the tank's own bake, see
        // FishTankGeometry).
        ModelLoadingPlugin.register(FishTankModelFabric.PLUGIN);

        // PORT A5.3: item model types.
//        // Register custom item model types
//        FishtasticClientSetup.registerItemModelTypes();

        // Register the Fish Tank Assembly menu screen
        net.minecraft.client.gui.screens.MenuScreens.register(
                FishtasticClientSetup.fishTankAssemblyMenuType(), grill24.fishtastic.client.FishTankAssemblyScreen::new);

        // Register the Electric Fish Organizer menu screen
        net.minecraft.client.gui.screens.MenuScreens.register(
                FishtasticClientSetup.electricFishOrganizerMenuType(), grill24.fishtastic.client.ElectricFishOrganizerScreen::new);

        // Register the Fish Tank Browser menu screen
        net.minecraft.client.gui.screens.MenuScreens.register(
                FishtasticClientSetup.fishTankBrowserMenuType(), grill24.fishtastic.client.FishTankBrowserScreen::new);

        // Register network packets (client-side)
        FabricPacketRegistrar.registerClientReceiver();

        // PORT-ONLY: Fishtastic's core shader programs (26.1 builds RenderPipelines instead).
        CoreShaderRegistrationCallback.EVENT.register(context -> FishtasticShaders.registerAll(context::register));

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

        // Initialize and register key bindings
        FishtasticKeyBinds.init();
        KeyBindingHelper.registerKeyBinding(FishtasticKeyBinds.fishingMinigameImpulse);
        KeyBindingHelper.registerKeyBinding(FishtasticKeyBinds.openQuestLog);
        KeyBindingHelper.registerKeyBinding(FishtasticKeyBinds.openFishEncyclopedia);
        KeyBindingHelper.registerKeyBinding(FishtasticKeyBinds.openLeaderboards);

        // Register block entity renderer
        BlockEntityRendererRegistry.register(
            (BlockEntityType<FishTankBlockEntity>) FishtasticBlockEntityTypes.FISH_TANK.value(),
            FishTankBlockEntityRenderer::new
        );
        BlockEntityRendererRegistry.register(
            (BlockEntityType<FishPileBlockEntity>) FishtasticBlockEntityTypes.FISH_PILE.value(),
            FishPileBlockEntityRenderer::new
        );

        // Register tank bubble particle provider
        ParticleFactoryRegistry.getInstance().register(FishtasticParticleTypes.TANK_BUBBLE.value(), TankBubbleParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(FishtasticParticleTypes.TINY_BUBBLE.value(), TankMicroBubbleParticle.TinyProvider::new);
        ParticleFactoryRegistry.getInstance().register(FishtasticParticleTypes.SMALL_BUBBLE.value(), TankMicroBubbleParticle.SmallProvider::new);
        ParticleFactoryRegistry.getInstance().register(FishtasticParticleTypes.MEDIUM_BUBBLE.value(), TankMicroBubbleParticle.MediumProvider::new);
        ParticleFactoryRegistry.getInstance().register(FishtasticParticleTypes.TANK_BUBBLE_POP.value(), TankBubblePopParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(FishtasticParticleTypes.MINI_SMOKE.value(), MiniSmokeParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(FishtasticParticleTypes.MINI_FLAME.value(), MiniFlameParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(FishtasticParticleTypes.MINI_CAMPFIRE_SMOKE.value(), MiniCampfireSmokeParticle.Provider::new);

        // Register lava fishing bite-cycle particle providers
        ParticleFactoryRegistry.getInstance().register(FishtasticParticleTypes.LAVA_WAKE.value(), LavaWakeParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(FishtasticParticleTypes.LAVA_BUBBLE.value(), LavaBubbleParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(FishtasticParticleTypes.LAVA_SPLASH.value(), LavaSplashParticle.Provider::new);

        // Clear caches on world join
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ItemEffectManager.clearCache();
            ClientTankFlocks.clear();
        });
        // Reset quest client cache and tutorial overlay on disconnect so stale data/UI doesn't persist across worlds
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            QuestClientCache.reset();
            TutorialClientHandler.reset();
            EncyclopediaTutorialClientHandler.reset();
            FishEncyclopediaClientCache.reset();
            grill24.fishtastic.network.SetDayRatePacket.resetClientRate();
            // PORT A5: cosmetic capture gizmos.
//            CosmeticCaptureClientState.reset();
            ClientTankFlocks.clear();
        });
        CommonLifecycleEvents.TAGS_LOADED.register((registries, isClient) -> {
            if (isClient) ItemEffectManager.clearCache();
        });

        // PORT-ONLY: the rendering self-test (inert unless its marker file exists; see RenderSelfTest).
        ClientTickEvents.END_CLIENT_TICK.register(client -> grill24.fishtastic.client.selftest.RenderSelfTest.tick(client, "fabric"));

        // Register client tick event handler for animations
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != null && !client.isPaused()) {
                ClientTickHandler.tick(1.0f);
                ClientTankFlocks.tickAll();
                TutorialClientHandler.tick();
                // Handle key presses
                FishtasticKeyBinds.handleKeyPress(client);
                // Tick quest progress notifications
                QuestProgressNotificationManager.getInstance().tick();
                // PORT A5: cosmetic capture gizmos.
//                // Draw the cosmetic-capture wand selection preview, if a session is active
//                CosmeticCaptureClientState.tickGizmos();
            }
        });

        // PORT-ONLY: 1.21.1's Fabric API has one HUD hook (HudRenderCallback) rather than 26.1's
        // ordered HudElementRegistry, so all three layers are drawn from a single callback in the
        // order they were registered there: tutorial, then the minigame bar over it, then the
        // quest notifications on top. That relative order is what those registrations existed for.
        HudRenderCallback.EVENT.register((graphics, deltaTracker) -> {
            float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
            TutorialClientHandler.render(graphics, partialTick);

            Minecraft mc = Minecraft.getInstance();
            if (mc.gameRenderer != null) {
                ItemActivationAnimation animation = ((IGameRendererExtension) mc.gameRenderer).fishtastic$getActiveAnimation();
                if (animation != null && animation.isActive()) {
                    animation.render(mc, graphics, partialTick);
                }
            }

            QuestProgressNotificationManager.getInstance().render(graphics, partialTick);
        });

        // Render tutorial text on top of the quest/shop screen (fires after the screen itself renders)
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, tickProgress) -> {
                TutorialClientHandler.renderScreenOverlay(graphics, tickProgress);
                EncyclopediaTutorialClientHandler.render(graphics, tickProgress);
            });
        });

        // PORT-ONLY: the rod cast and book has_alert predicates the item models test (Fabric API's
        // transitive access widener makes vanilla's ItemProperties.register public).
        FishtasticItemProperties.register(ItemProperties::register);

        // PORT-ONLY: the see-through blocks' chunk layers (26.1 derives them from texture alpha).
        FishtasticBlockRenderLayers.register(BlockRenderLayerMap.INSTANCE::putBlock);

        // TODO MC-26.1: Block color handlers need to be re-implemented using the new BlockTintSource system
        // ColorProviderRegistry.BLOCK is removed; use BlockColorRegistry with BlockTintSource instead
    }
}
