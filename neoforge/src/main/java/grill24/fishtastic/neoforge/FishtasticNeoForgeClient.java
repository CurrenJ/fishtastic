package grill24.fishtastic.neoforge;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticBlockEntityTypes;
import grill24.fishtastic.client.QuestClientCache;
import grill24.fishtastic.client.QuestProgressNotificationManager;
import grill24.fishtastic.itemeffect.ItemEffectManager;
import grill24.fishtastic.network.QuestSyncPacket;
import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.client.FishtasticClientSetup;
import grill24.fishtastic.client.FishTankCustomizationHandler;
import grill24.fishtastic.client.FishtasticKeyBinds;
import grill24.fishtastic.client.renderer.FishTankBlockEntityRenderer;
import grill24.fishtastic.client.util.ClientTickHandler;
import grill24.fishtastic.compat.GelatinScreensCompat;
import grill24.fishtastic.client.CosmeticTransformLoader;
import grill24.fishtastic.neoforge.fishtank.BlockstateModelReloadListener;
import grill24.fishtastic.client.tooltip.ClientRodBaitTooltip;
import grill24.fishtastic.client.tooltip.RodBaitTooltip;
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
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterBlockStateModels;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.resources.VanillaClientListeners;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

import static grill24.fishtastic.util.Utility.ft;

@Mod(value = Fishtastic.MOD_ID, dist = Dist.CLIENT)
public final class FishtasticNeoForgeClient {
    public FishtasticNeoForgeClient(IEventBus modEventBus) {
        // Try to register GelatinUI screens, if GelatinUI is present.
        GelatinScreensCompat.init();

        // Register quest sync packet client handler
        QuestSyncPacket.registerClientHandler(packet ->
                QuestClientCache.update(packet.questProgress(), packet.tokenBalance(), packet.triggeringItems(), packet.purchaseCounts()));

        // Install quest progress notification system
        QuestProgressNotificationManager.getInstance().install();

        modEventBus.addListener(FishtasticNeoForgeClient::registerClientReloadListeners);
        modEventBus.addListener(FishtasticNeoForgeClient::registerModelLoaders);
        modEventBus.addListener(FishtasticNeoForgeClient::registerBlockStateModels);
        modEventBus.addListener(FishtasticNeoForgeClient::onClientSetup);
        modEventBus.addListener(FishtasticNeoForgeClient::registerRenderers);
        modEventBus.addListener(FishtasticNeoForgeClient::registerKeyMappings);
        modEventBus.addListener(FishtasticNeoForgeClient::registerTooltipComponents);

        // Clear the ItemEffect cache on world join and on tag sync (covers /reload without rejoin).
        NeoForge.EVENT_BUS.addListener(FishtasticNeoForgeClient::onPlayerJoin);
        NeoForge.EVENT_BUS.addListener(FishtasticNeoForgeClient::onPlayerLeave);
        NeoForge.EVENT_BUS.addListener(FishtasticNeoForgeClient::onTagsUpdated);

        // Register client tick event handler
        NeoForge.EVENT_BUS.addListener(FishtasticNeoForgeClient::onClientTick);

        // Register mouse scroll event handler for fish tank customization
        NeoForge.EVENT_BUS.addListener(FishtasticNeoForgeClient::onMouseScroll);

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
        Fishtastic.LOGGER.info("Fishtastic block entity renderers registered.");
    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        FishtasticKeyBinds.init();
        event.register(FishtasticKeyBinds.fishingMinigameImpulse);
        event.register(FishtasticKeyBinds.openQuestLog);
        event.register(FishtasticKeyBinds.toggleFishTankEditMode);
        Fishtastic.LOGGER.info("Fishtastic key mappings registered.");
    }

    public static void registerTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(RodBaitTooltip.class, tooltip -> new ClientRodBaitTooltip(tooltip.bait()));
    }

    // TODO MC-26.1: Block color handlers need to be re-implemented using the new BlockTintSource system
    // TODO MC-26.1: ItemProperties is removed - fishing rod "cast" property needs data-driven item model

    public static void onClientSetup(final FMLClientSetupEvent event) {
        // Register custom item model types
        FishtasticClientSetup.registerItemModelTypes();

        // TODO MC-26.1: ItemProperties.register is removed in 26.1
        // The fishing rod "cast" property must now be defined via data-driven item models
        Fishtastic.LOGGER.info("Fishtastic client setup complete.");
    }

    public static void onPlayerJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        ItemEffectManager.clearCache();
    }

    public static void onPlayerLeave(ClientPlayerNetworkEvent.LoggingOut event) {
        QuestClientCache.reset();
    }

    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED) {
            ItemEffectManager.clearCache();
        }
    }

    public static void onClientTick(ClientTickEvent.Pre event) {
        // Update tick counter for animations
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && !mc.isPaused()) {
            ClientTickHandler.tick(1.0f);
            // Handle key presses
            FishtasticKeyBinds.handleKeyPress(mc);
            // Tick quest progress notifications
            QuestProgressNotificationManager.getInstance().tick();
        }
    }

    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (FishTankCustomizationHandler.handleMouseScroll(event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
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
