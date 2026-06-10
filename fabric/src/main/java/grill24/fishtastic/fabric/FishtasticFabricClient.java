package grill24.fishtastic.fabric;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticBlockEntityTypes;
import grill24.fishtastic.client.QuestClientCache;
import grill24.fishtastic.client.QuestProgressNotificationManager;
import grill24.fishtastic.itemeffect.ItemEffectManager;
import grill24.fishtastic.network.QuestSyncPacket;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.architectury.fabric.FabricPacketRegistrar;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.client.FishtasticClientSetup;
import grill24.fishtastic.client.FishtasticKeyBinds;
import grill24.fishtastic.client.renderer.FishTankBlockEntityRenderer;
import grill24.fishtastic.client.util.ClientTickHandler;
import grill24.fishtastic.client.tooltip.ClientRodBaitTooltip;
import grill24.fishtastic.client.tooltip.RodBaitTooltip;
import grill24.fishtastic.fabric.fishtank.BlockstateModelRedirectPlugin;
import grill24.fishtastic.fabric.fishtank.FishTankBlockStateModelFabric;
import grill24.fishtastic.fabric.fishtank.FishTankModelFabric;
import grill24.fishtastic.util.IGameRendererExtension;
import grill24.fishtastic.util.ItemActivationAnimation;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.model.loading.v1.CustomUnbakedBlockStateModel;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.UnbakedModelDeserializer;
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
        // Register visual tooltip renderer for rod bait slot
        ClientTooltipComponentCallback.EVENT.register(component -> {
            if (component instanceof RodBaitTooltip tooltip) {
                return new ClientRodBaitTooltip(tooltip.bait());
            }
            return null;
        });

        // Build blockstate → model path redirect map before baking starts
        PreparableModelLoadingPlugin.register(BlockstateModelRedirectPlugin.LOADER, BlockstateModelRedirectPlugin.PLUGIN);

        // Register custom block state model type for fish tank
        CustomUnbakedBlockStateModel.register(ft("fish_tank"), FishTankBlockStateModelFabric.CODEC);

        // Register custom model loader for the fish tank item model
        UnbakedModelDeserializer.register(ft("fish_tank"), FishTankModelFabric.Loader.INSTANCE);

        // Register custom item model types
        FishtasticClientSetup.registerItemModelTypes();

        // Register network packets (client-side)
        FabricPacketRegistrar.registerClientReceiver();

        // Register quest sync packet client handler
        QuestSyncPacket.registerClientHandler(packet ->
                QuestClientCache.update(packet.questProgress(), packet.tokenBalance(), packet.triggeringItems()));

        // Install quest progress notification system
        QuestProgressNotificationManager.getInstance().install();

        // Initialize and register key bindings
        FishtasticKeyBinds.init();
        KeyMappingHelper.registerKeyMapping(FishtasticKeyBinds.fishingMinigameImpulse);
        KeyMappingHelper.registerKeyMapping(FishtasticKeyBinds.openQuestLog);

        // Register block entity renderer
        BlockEntityRendererRegistry.register(
            (BlockEntityType<FishTankBlockEntity>) FishtasticBlockEntityTypes.FISH_TANK.value(),
            FishTankBlockEntityRenderer::new
        );

        // Clear caches on world join
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ItemEffectManager.clearCache());
        // Reset quest client cache on disconnect so stale data doesn't persist across worlds
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> QuestClientCache.reset());
        CommonLifecycleEvents.TAGS_LOADED.register((registries, isClient) -> {
            if (isClient) ItemEffectManager.clearCache();
        });

        // Register client tick event handler for animations
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != null && !client.isPaused()) {
                ClientTickHandler.tick(1.0f);
                // Handle key presses
                FishtasticKeyBinds.handleKeyPress(client);
                // Tick quest progress notifications
                QuestProgressNotificationManager.getInstance().tick();
            }
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

        // TODO MC-26.1: ItemProperties.register is removed in 26.1
        // The fishing rod "cast" property must now be defined via data-driven item models

        // TODO MC-26.1: Block color handlers need to be re-implemented using the new BlockTintSource system
        // ColorProviderRegistry.BLOCK is removed; use BlockColorRegistry with BlockTintSource instead
    }
}
