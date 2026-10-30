package grill24.fishtastic.forge;

import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.forge.fishtank.FishTankBakedModel;
import grill24.fishtastic.forge.fishtank.FishTankItemModel;
import grill24.fishtastic.util.Ids;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.ModelEvent;
import org.joml.Vector3fc;

import java.lang.reflect.Field;
import java.util.Map;

/** Minimal Forge 26.1.2 client entrypoint; F06 model seam and the self-test tick. */
public final class FishtasticForgeClient {
    private FishtasticForgeClient() {}

    public static void init() {
        grill24.fishtastic.client.FishtasticClientSetup.registerItemModelTypes();
        FishTankItemModel.register();
        net.minecraftforge.client.event.RegisterColorHandlersEvent.Block.BUS.addListener(event ->
                event.register(grill24.fishtastic.forge.fishtank.FishTankCosmeticTints.sources(),
                        FishtasticBlocks.FISH_TANK.value()));
        grill24.fishtastic.architectury.forge.ForgePacketRegistrar.installClientPlayer(
                () -> Minecraft.getInstance().player);
        // Gelatin owns the menu types; FishTastic supplies the screen factories.
        grill24.fishtastic.compat.GelatinScreensCompat.init();
        grill24.fishtastic.client.QuestProgressNotificationManager.getInstance().install();
        ModelEvent.BakingCompleted.BUS.addListener(FishtasticForgeClient::bakingCompleted);
        net.minecraftforge.event.TickEvent.ClientTickEvent.Post.BUS.addListener(event ->
                clientTick(Minecraft.getInstance()));

        var modBus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModBusGroup();
        net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent.getBus(modBus).addListener(event ->
                event.enqueueWork(() -> {
                    net.minecraft.client.gui.screens.MenuScreens.register(
                            grill24.fishtastic.client.FishtasticClientSetup.companionMenuType(),
                            grill24.fishtastic.client.CompanionScreen::new);
                    net.minecraft.client.gui.screens.MenuScreens.register(
                            grill24.fishtastic.client.FishtasticClientSetup.fishTankAssemblyMenuType(),
                            grill24.fishtastic.client.FishTankAssemblyScreen::new);
                    net.minecraft.client.gui.screens.MenuScreens.register(
                            grill24.fishtastic.client.FishtasticClientSetup.electricFishOrganizerMenuType(),
                            grill24.fishtastic.client.ElectricFishOrganizerScreen::new);
                    net.minecraft.client.gui.screens.MenuScreens.register(
                            grill24.fishtastic.client.FishtasticClientSetup.fishTankBrowserMenuType(),
                            grill24.fishtastic.client.FishTankBrowserScreen::new);
                }));

        net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers.BUS.addListener(event -> {
            event.registerBlockEntityRenderer(
                    (net.minecraft.world.level.block.entity.BlockEntityType<grill24.fishtastic.blockentity.FishTankBlockEntity>)
                            grill24.fishtastic.FishtasticBlockEntityTypes.FISH_TANK.value(),
                    grill24.fishtastic.client.renderer.FishTankBlockEntityRenderer::new);
            event.registerBlockEntityRenderer(
                    (net.minecraft.world.level.block.entity.BlockEntityType<grill24.fishtastic.blockentity.FishPileBlockEntity>)
                            grill24.fishtastic.FishtasticBlockEntityTypes.FISH_PILE.value(),
                    grill24.fishtastic.client.renderer.FishPileBlockEntityRenderer::new);
        });

        net.minecraftforge.client.event.RegisterParticleProvidersEvent.BUS.addListener(event -> {
            event.registerSpriteSet(grill24.fishtastic.FishtasticParticleTypes.TANK_BUBBLE.value(),
                    grill24.fishtastic.client.particle.TankBubbleParticle.Provider::new);
            event.registerSpriteSet(grill24.fishtastic.FishtasticParticleTypes.TINY_BUBBLE.value(),
                    grill24.fishtastic.client.particle.TankMicroBubbleParticle.TinyProvider::new);
            event.registerSpriteSet(grill24.fishtastic.FishtasticParticleTypes.SMALL_BUBBLE.value(),
                    grill24.fishtastic.client.particle.TankMicroBubbleParticle.SmallProvider::new);
            event.registerSpriteSet(grill24.fishtastic.FishtasticParticleTypes.MEDIUM_BUBBLE.value(),
                    grill24.fishtastic.client.particle.TankMicroBubbleParticle.MediumProvider::new);
            event.registerSpriteSet(grill24.fishtastic.FishtasticParticleTypes.TANK_BUBBLE_POP.value(),
                    grill24.fishtastic.client.particle.TankBubblePopParticle.Provider::new);
            event.registerSpriteSet(grill24.fishtastic.FishtasticParticleTypes.MINI_SMOKE.value(),
                    grill24.fishtastic.client.particle.MiniSmokeParticle.Provider::new);
            event.registerSpriteSet(grill24.fishtastic.FishtasticParticleTypes.MINI_FLAME.value(),
                    grill24.fishtastic.client.particle.MiniFlameParticle.Provider::new);
            event.registerSpriteSet(grill24.fishtastic.FishtasticParticleTypes.MINI_CAMPFIRE_SMOKE.value(),
                    grill24.fishtastic.client.particle.MiniCampfireSmokeParticle.Provider::new);
            event.registerSpriteSet(grill24.fishtastic.FishtasticParticleTypes.LAVA_WAKE.value(),
                    grill24.fishtastic.client.particle.LavaWakeParticle.Provider::new);
            event.registerSpriteSet(grill24.fishtastic.FishtasticParticleTypes.LAVA_BUBBLE.value(),
                    grill24.fishtastic.client.particle.LavaBubbleParticle.Provider::new);
            event.registerSpriteSet(grill24.fishtastic.FishtasticParticleTypes.LAVA_SPLASH.value(),
                    grill24.fishtastic.client.particle.LavaSplashParticle.Provider::new);
        });

        net.minecraftforge.client.event.RegisterKeyMappingsEvent.BUS.addListener(event -> {
            grill24.fishtastic.client.FishtasticKeyBinds.init();
            event.register(grill24.fishtastic.client.FishtasticKeyBinds.fishingMinigameImpulse);
            event.register(grill24.fishtastic.client.FishtasticKeyBinds.openQuestLog);
            event.register(grill24.fishtastic.client.FishtasticKeyBinds.openFishEncyclopedia);
            event.register(grill24.fishtastic.client.FishtasticKeyBinds.openLeaderboards);
            event.register(grill24.fishtastic.client.FishtasticKeyBinds.bondCompanion);
        });

        net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent.BUS.addListener(event -> {
            event.register(grill24.fishtastic.client.tooltip.RodGearTooltip.class,
                    tooltip -> new grill24.fishtastic.client.tooltip.ClientRodGearTooltip(
                            tooltip.bait(), tooltip.hook(), tooltip.charm()));
            event.register(grill24.fishtastic.client.tooltip.FishTankMaterialsTooltip.class,
                    tooltip -> new grill24.fishtastic.client.tooltip.ClientFishTankMaterialsTooltip(
                            tooltip.frame(), tooltip.glass(), tooltip.sand()));
        });

        net.minecraftforge.client.event.RegisterClientCommandsEvent.BUS.addListener(event ->
                grill24.fishtastic.client.ambient.AmbientClientCommands.register(event.getDispatcher(),
                        (source, message) -> source.sendSuccess(
                                () -> net.minecraft.network.chat.Component.literal(message), false)));

        net.minecraftforge.client.event.AddGuiOverlayLayersEvent.BUS.addListener(event -> {
            event.getLayeredDraw().add(Ids.of("fishtastic", "legendary_breach_hud"), (graphics, delta) ->
                    grill24.fishtastic.client.FishSchoolClientRuntime.renderHud(
                            graphics, delta.getGameTimeDeltaPartialTick(false)));
            event.getLayeredDraw().add(Ids.of("fishtastic", "tutorial_hud"), (graphics, delta) -> {
                float partial = delta.getGameTimeDeltaPartialTick(false);
                grill24.fishtastic.client.TutorialClientHandler.render(graphics, partial);
                grill24.fishtastic.client.CompanionTutorialClientHandler.renderHud(graphics, partial);
            });
            event.getLayeredDraw().add(Ids.of("fishtastic", "minigame_hud"), (graphics, delta) -> {
                float partial = delta.getGameTimeDeltaPartialTick(false);
                var mc = Minecraft.getInstance();
                if (mc.gameRenderer != null) {
                    var animation = ((grill24.fishtastic.util.IGameRendererExtension) mc.gameRenderer)
                            .fishtastic$getActiveAnimation();
                    if (animation != null && animation.isActive()) {
                        animation.render(mc, graphics, partial);
                    }
                }
                grill24.fishtastic.client.companion.ClientCompanionsDev.renderHud(graphics, partial);
                grill24.fishtastic.client.QuestProgressNotificationManager.getInstance()
                        .render(graphics, partial);
            });
        });

        net.minecraftforge.client.event.ScreenEvent.Render.Post.BUS.addListener(event -> {
            grill24.fishtastic.client.TutorialClientHandler.renderScreenOverlay(
                    event.getGuiGraphics(), event.getPartialTick());
            grill24.fishtastic.client.EncyclopediaTutorialClientHandler.render(
                    event.getGuiGraphics(), event.getPartialTick());
            grill24.fishtastic.client.CompanionTutorialClientHandler.render(
                    event.getGuiGraphics(), event.getPartialTick());
        });

        net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn.BUS.addListener(event -> {
            grill24.fishtastic.itemeffect.ItemEffectManager.clearCache();
            grill24.fishtastic.client.util.ClientTankFlocks.clear();
            grill24.fishtastic.client.ambient.ClientAmbientFish.clear();
        });
        net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> {
            grill24.fishtastic.client.QuestClientCache.reset();
            grill24.fishtastic.client.TutorialClientHandler.reset();
            grill24.fishtastic.client.EncyclopediaTutorialClientHandler.reset();
            grill24.fishtastic.client.CompanionTutorialClientHandler.reset();
            grill24.fishtastic.client.CompanionClientState.reset();
            grill24.fishtastic.client.FishEncyclopediaClientCache.reset();
            grill24.fishtastic.client.CosmeticCaptureClientState.reset();
            grill24.fishtastic.client.util.ClientTankFlocks.clear();
            grill24.fishtastic.client.ambient.ClientAmbientFish.clear();
            grill24.fishtastic.companion.CompanionPartyClientCache.reset();
        });

        net.minecraftforge.event.TagsUpdatedEvent.BUS.addListener(event -> {
            if (event.getUpdateCause() == net.minecraftforge.event.TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED) {
                grill24.fishtastic.itemeffect.ItemEffectManager.clearCache();
                grill24.fishtastic.fishtank.CosmeticObstacles.clearCache();
                grill24.fishtastic.client.ambient.ClientAmbientFish.invalidate();
            }
        });

        net.minecraftforge.client.event.RegisterClientReloadListenersEvent.BUS.addListener(event -> {
            event.registerReloadListener(grill24.fishtastic.client.CosmeticTransformLoader.INSTANCE);
            event.registerReloadListener(
                    (net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager ->
                            Minecraft.getInstance().execute(
                                    grill24.fishtastic.client.ambient.ClientAmbientFish::invalidate));
        });

        net.minecraftforge.client.event.RenderTooltipEvent.GatherComponents.BUS.addListener(event -> {
            if (!grill24.fishtastic.client.TankCosmeticTooltip.isEligible(event.getItemStack())) return;
            event.getTooltipElements().add(1, com.mojang.datafixers.util.Either.left(
                    net.minecraft.network.chat.Component
                            .translatable("tooltip.fishtastic.tank_cosmetic")
                            .withStyle(net.minecraft.ChatFormatting.GRAY)));
        });

        registerClientHandlers();
    }

    /**
     * The shared per-tick client work. Mirrors {@code FishtasticNeoForgeClient#onClientTick}: the
     * self-test runs unguarded (it creates its own world) and everything else needs a live,
     * unpaused level. Without this, synced companion parties and ambient fish never advance past
     * their caches and nothing renders in the world.
     */
    private static void clientTick(Minecraft mc) {
        if (grill24.fishtastic.env.DevEnvironmentCheck.isDevelopmentEnvironment()
                || Boolean.getBoolean("fishtastic.selftest")) {
            tickRenderSelfTest(mc);
        }

        grill24.fishtastic.client.ambient.ClientAmbientFish.tick();
        if (mc.level != null && !mc.isPaused()) {
            grill24.fishtastic.client.util.ClientTickHandler.tick(1.0f);
            grill24.fishtastic.client.util.ClientTankFlocks.tickAll();
            grill24.fishtastic.client.companion.ClientCompanions.tick();
            grill24.fishtastic.client.TutorialClientHandler.tick();
            grill24.fishtastic.client.FishtasticKeyBinds.handleKeyPress(mc);
            grill24.fishtastic.client.QuestProgressNotificationManager.getInstance().tick();
            grill24.fishtastic.client.CosmeticCaptureClientState.tickGizmos();
            grill24.fishtastic.client.CosmeticPlacementPreview.tickGizmos();
        }
    }

    /**
     * Runs the dev-only rendering self-test when present. Resolved reflectively so the shipped jar
     * keeps no reference to the excluded class; a smoke build includes the class and sets
     * {@code -Dfishtastic.selftest=true}.
     */
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

    private static void registerClientHandlers() {
        grill24.fishtastic.network.QuestSyncPacket.registerClientHandler(packet ->
                grill24.fishtastic.client.QuestClientCache.update(packet.questProgress(), packet.tokenBalance(),
                        packet.triggeringItems(), packet.purchaseCounts(), packet.cleanupGoal(),
                        packet.serverGameTime(), packet.baitDepletedItem(), packet.firstCatchItems(),
                        packet.shopRefreshCount(), packet.shopRefreshNonce(), packet.freeShopRerollAvailable(),
                        packet.unlockedCompanions(), packet.newlyUnlockedCompanions()));
        grill24.fishtastic.network.TutorialSyncPacket.registerClientHandler(
                grill24.fishtastic.client.TutorialClientHandler.PACKET_HANDLER);
        grill24.fishtastic.network.EncyclopediaTutorialSyncPacket.registerClientHandler(
                grill24.fishtastic.client.EncyclopediaTutorialClientHandler.PACKET_HANDLER);
        grill24.fishtastic.network.CompanionTutorialSyncPacket.registerClientHandler(
                grill24.fishtastic.client.CompanionTutorialClientHandler.PACKET_HANDLER);
        grill24.fishtastic.network.CompanionFeedbackPacket.registerClientHandler(
                grill24.fishtastic.client.CompanionClientState.PACKET_HANDLER);
        grill24.fishtastic.network.CompanionScenePacket.registerClientHandler(packet ->
                grill24.fishtastic.client.companion.ClientCompanions.onScenePacket(
                        packet.scene(), packet.first(), packet.firstSlot(), packet.second(),
                        packet.secondSlot(), packet.startTime()));
        grill24.fishtastic.network.FishEncyclopediaSyncPacket.registerClientHandler(packet ->
                grill24.fishtastic.client.FishEncyclopediaClientCache.update(packet));
        grill24.fishtastic.network.CosmeticCaptureSyncPacket.registerClientHandler(
                grill24.fishtastic.client.CosmeticCaptureClientState::apply);
        grill24.fishtastic.network.NotificationVolumeSyncPacket.registerClientHandler(packet ->
                grill24.fishtastic.client.FishtasticClientConfig.setNotificationVolume(packet.volume()));
        grill24.fishtastic.network.TankWaterFillSyncPacket.registerClientHandler(packet ->
                grill24.fishtastic.client.FishtasticClientConfig.setTankWaterFillEnabled(packet.enabled()));
        grill24.fishtastic.network.ReactionBoostSyncPacket.registerClientHandler(packet ->
                grill24.fishsim.core.FlockEngine.setTriggerRateBoost(packet.boost()));
        grill24.fishtastic.network.TankInteriorLightSyncPacket.registerClientHandler(packet ->
                grill24.fishtastic.client.renderer.TankInteriorLight.set(packet.level()));
        grill24.fishtastic.network.ReducedEffectsSyncPacket.registerClientHandler(packet ->
                grill24.fishtastic.client.FishtasticClientConfig.setReducedCelebrationEffects(packet.reduced()));
    }

    private static void bakingCompleted(ModelEvent.BakingCompleted event) {
        FishTankBakedModel tank = grill24.fishtastic.forge.fishtank.FishTankModelResources.bake(ForgeModelBaker.of(event.getModelBakery()));
        Minecraft.getInstance().execute(() -> installBlockModel(tank));
    }

    private static void installBlockModel(FishTankBakedModel tank) {
        BlockStateModelSet set = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
        try {
            Field field = BlockStateModelSet.class.getDeclaredField("modelByState");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<net.minecraft.world.level.block.state.BlockState,
                    net.minecraft.client.renderer.block.dispatch.BlockStateModel> map =
                    (Map<net.minecraft.world.level.block.state.BlockState,
                            net.minecraft.client.renderer.block.dispatch.BlockStateModel>) field.get(set);
            for (var state : FishtasticBlocks.FISH_TANK.value().getStateDefinition().getPossibleStates()) {
                map.put(state, tank);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not install the Forge fish tank model", e);
        }
    }

    private static final class ForgeModelBaker implements ModelBaker {
        private final Map<Identifier, ResolvedModel> models;
        private final ResolvedModel missing;
        private final MaterialBaker materials;

        private ForgeModelBaker(Map<Identifier, ResolvedModel> models, ResolvedModel missing, SpriteGetter sprites) {
            this.models = models;
            this.missing = missing;
            this.materials = new MaterialBaker() {
                @Override
                public Material.Baked get(Material material, ModelDebugName debugName) {
                    return new Material.Baked(
                            sprites.get(new SpriteId(TextureAtlas.LOCATION_BLOCKS, material.sprite())),
                            material.forceTranslucent());
                }

                @Override
                public Material.Baked reportMissingReference(String reference, ModelDebugName debugName) {
                    return get(new Material(Ids.withDefaultNamespace("block/oak_planks")), debugName);
                }
            };
        }

        static ModelBaker of(ModelBakery bakery) {
            try {
                Field modelsField = ModelBakery.class.getDeclaredField("resolvedModels");
                Field missingField = ModelBakery.class.getDeclaredField("missingModel");
                Field spritesField = ModelBakery.class.getDeclaredField("sprites");
                modelsField.setAccessible(true);
                missingField.setAccessible(true);
                spritesField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<Identifier, ResolvedModel> models =
                        (Map<Identifier, ResolvedModel>) modelsField.get(bakery);
                return new ForgeModelBaker(models, (ResolvedModel) missingField.get(bakery),
                        (SpriteGetter) spritesField.get(bakery));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Could not access Forge model bakery internals", e);
            }
        }

        @Override
        public ResolvedModel getModel(Identifier id) {
            return models.getOrDefault(id, missing);
        }

        @Override
        public net.minecraft.client.renderer.block.dispatch.BlockStateModelPart missingBlockModelPart() {
            return new SimpleModelWrapper(new QuadCollection.Builder().build(), false,
                    missing.resolveParticleMaterial(missing.getTopTextureSlots(), this));
        }

        @Override public MaterialBaker materials() { return materials; }

        @Override
        public Interner interner() {
            return new Interner() {
                @Override public Vector3fc vector(Vector3fc vector) { return vector; }
                @Override public BakedQuad.MaterialInfo materialInfo(BakedQuad.MaterialInfo info) { return info; }
            };
        }

        @Override public <T> T compute(SharedOperationKey<T> key) { return null; }
    }
}
