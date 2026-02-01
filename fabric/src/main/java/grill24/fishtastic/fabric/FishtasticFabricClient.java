package grill24.fishtastic.fabric;

import grill24.fishtastic.FishtasticBlockEntityTypes;
import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.architectury.fabric.FabricPacketRegistrar;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.client.FishtasticKeyBinds;
import grill24.fishtastic.client.renderer.FishTankBlockEntityRenderer;
import grill24.fishtastic.client.util.ClientTickHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class FishtasticFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Register network packets (client-side)
        FabricPacketRegistrar.registerClientReceiver();

        // Register custom shaders
        CoreShaderRegistrationCallback.EVENT.register(context -> {

        });

        // Initialize and register key bindings
        FishtasticKeyBinds.init();
        KeyBindingHelper.registerKeyBinding(FishtasticKeyBinds.fishingMinigameImpulse);

        // Register block entity renderer
        BlockEntityRendererRegistry.register(
            (BlockEntityType<FishTankBlockEntity>) FishtasticBlockEntityTypes.FISH_TANK.value(),
            FishTankBlockEntityRenderer::new
        );

        // Set render layer for all borderless and clear stained glass blocks
        for (var entry : FishtasticBlocks.BORDERLESS_STAINED_GLASS.entrySet()) {
            BlockRenderLayerMap.INSTANCE.putBlock(entry.getValue().value(), RenderType.translucent());
        }
        for (var entry : FishtasticBlocks.CLEAR_STAINED_GLASS.entrySet()) {
            BlockRenderLayerMap.INSTANCE.putBlock(entry.getValue().value(), RenderType.translucent());
        }

        // Register client tick event handler for animations
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != null && !client.isPaused()) {
                ClientTickHandler.tick(1.0f);
                // Handle key presses
                FishtasticKeyBinds.handleKeyPress(client);
            }
        });

        // Register cast property for copper fishing rod
        ItemProperties.register(FishtasticItems.COPPER_FISHING_ROD.value(), ResourceLocation.withDefaultNamespace("cast"),
                (stack, level, entity, seed) ->
                        entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
    }
}
