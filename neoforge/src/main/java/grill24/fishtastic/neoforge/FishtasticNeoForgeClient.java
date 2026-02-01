package grill24.fishtastic.neoforge;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticBlockEntityTypes;
import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.client.FishTankCustomizationHandler;
import grill24.fishtastic.client.FishtasticKeyBinds;
import grill24.fishtastic.client.renderer.FishTankBlockEntityRenderer;
import grill24.fishtastic.client.util.ClientTickHandler;
import grill24.fishtastic.compat.GelatinScreensCompat;
import grill24.fishtastic.neoforge.fishtank.FishTankModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.io.IOException;

import static grill24.fishtastic.util.Utility.ft;

@Mod(value = Fishtastic.MOD_ID, dist = Dist.CLIENT)
public final class FishtasticNeoForgeClient {
    public FishtasticNeoForgeClient(IEventBus modEventBus) {
        // Try to register GelatinUI screens, if GelatinUI is present.
        GelatinScreensCompat.init();

        modEventBus.addListener(FishtasticNeoForgeClient::registerModelLoaders);
        modEventBus.addListener(FishtasticNeoForgeClient::onClientSetup);
        modEventBus.addListener(FishtasticNeoForgeClient::registerRenderers);
        modEventBus.addListener(FishtasticNeoForgeClient::registerKeyMappings);
        modEventBus.addListener((RegisterShadersEvent event) -> {
            try {
                registerShaders(event);
                Fishtastic.LOGGER.info("Fishtastic shaders registered.");
            } catch (IOException e) {
                Fishtastic.LOGGER.error("Failed to register shaders", e);
            }
        });

        // Register client tick event handler
        NeoForge.EVENT_BUS.addListener(FishtasticNeoForgeClient::onClientTick);

        // Register mouse scroll event handler for fish tank customization
        NeoForge.EVENT_BUS.addListener(FishtasticNeoForgeClient::onMouseScroll);
    }

    public static void registerModelLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(ft("fish_tank"), FishTankModel.Loader.INSTANCE);
        Fishtastic.LOGGER.info("Fishtastic model loaders registered.");
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
        Fishtastic.LOGGER.info("Fishtastic key mappings registered.");
    }

    public static void registerShaders(RegisterShadersEvent event) throws IOException {

    }

    public static void onClientSetup(final FMLClientSetupEvent event) {
        // Ensure this runs on the client thread
        event.enqueueWork(() -> {
            // Set render layer for all borderless and clear stained glass blocks
            for (var entry : FishtasticBlocks.BORDERLESS_STAINED_GLASS.entrySet()) {
                ItemBlockRenderTypes.setRenderLayer(entry.getValue().value(), RenderType.translucent());
            }
            for (var entry : FishtasticBlocks.CLEAR_STAINED_GLASS.entrySet()) {
                ItemBlockRenderTypes.setRenderLayer(entry.getValue().value(), RenderType.translucent());
            }

            // Register cast property for copper fishing rod
            ItemProperties.register(FishtasticItems.COPPER_FISHING_ROD.value(), ResourceLocation.withDefaultNamespace("cast"), (stack, level, entity, seed) -> {
                if (entity == null) {
                    return 0.0F;
                } else {
                    boolean bl = entity.getMainHandItem() == stack;
                    boolean bl2 = entity.getOffhandItem() == stack;
                    if (entity.getMainHandItem().getItem() instanceof FishingRodItem) {
                        bl2 = false;
                    }

                    return (bl || bl2) && entity instanceof Player && ((Player)entity).fishing != null ? 1.0F : 0.0F;
                }
            });
        });
    }

    public static void onClientTick(ClientTickEvent.Pre event) {
        // Update tick counter for animations
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && !mc.isPaused()) {
            ClientTickHandler.tick(1.0f);
            // Handle key presses
            FishtasticKeyBinds.handleKeyPress(mc);
        }
    }

    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (FishTankCustomizationHandler.handleMouseScroll(event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }
}
