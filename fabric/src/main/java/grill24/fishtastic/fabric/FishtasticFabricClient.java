package grill24.fishtastic.fabric;

import grill24.fishtastic.FishtasticBlockEntityTypes;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.architectury.fabric.FabricPacketRegistrar;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.client.FishtasticKeyBinds;
import grill24.fishtastic.client.renderer.FishTankBlockEntityRenderer;
import grill24.fishtastic.client.util.ClientTickHandler;
import grill24.fishtastic.util.IGameRendererExtension;
import grill24.fishtastic.util.ItemActivationAnimation;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class FishtasticFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Register network packets (client-side)
        FabricPacketRegistrar.registerClientReceiver();

        // Initialize and register key bindings
        FishtasticKeyBinds.init();
        KeyMappingHelper.registerKeyMapping(FishtasticKeyBinds.fishingMinigameImpulse);

        // Register block entity renderer
        BlockEntityRendererRegistry.register(
            (BlockEntityType<FishTankBlockEntity>) FishtasticBlockEntityTypes.FISH_TANK.value(),
            FishTankBlockEntityRenderer::new
        );

        // Register client tick event handler for animations
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != null && !client.isPaused()) {
                ClientTickHandler.tick(1.0f);
                // Handle key presses
                FishtasticKeyBinds.handleKeyPress(client);
            }
        });

        // Register HUD render hook for the fishing minigame overlay
        HudElementRegistry.addLast(Identifier.of("fishtastic", "fishing_minigame"), (graphics, deltaTracker) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gameRenderer == null) return;
            ItemActivationAnimation animation = ((IGameRendererExtension) mc.gameRenderer).fishtastic$getActiveAnimation();
            if (animation != null && animation.isActive()) {
                animation.render(mc, graphics, deltaTracker.getGameTimeDeltaPartialTick(false));
            }
        });

        // TODO MC-26.1: ItemProperties.register is removed in 26.1
        // The fishing rod "cast" property must now be defined via data-driven item models

        // TODO MC-26.1: Block color handlers need to be re-implemented using the new BlockTintSource system
        // ColorProviderRegistry.BLOCK is removed; use BlockColorRegistry with BlockTintSource instead
    }
}
