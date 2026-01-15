package grill24.fishtastic.neoforge;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.compat.GelatinScreensCompat;
import grill24.fishtastic.neoforge.fishtank.FishTankModel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterNamedRenderTypesEvent;

import static grill24.fishtastic.util.Utility.ft;

@Mod(value = Fishtastic.MOD_ID, dist = Dist.CLIENT)
public final class FishtasticNeoForgeClient {
    public FishtasticNeoForgeClient(IEventBus modEventBus) {
        // Try to register GelatinUI screens, if GelatinUI is present.
        GelatinScreensCompat.init();

        modEventBus.addListener(FishtasticNeoForgeClient::registerModelLoaders);
        modEventBus.addListener(FishtasticNeoForgeClient::onClientSetup);
    }

    public static void registerModelLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(ft("fish_tank"), FishTankModel.Loader.INSTANCE);
        Fishtastic.LOGGER.info("Fishtastic model loaders registered.");
    }

    public static void onClientSetup(final FMLClientSetupEvent event) {
        // Ensure this runs on the client thread
        event.enqueueWork(() -> {
            // Example: Make a block render with cutout transparency
            ItemBlockRenderTypes.setRenderLayer(FishtasticBlocks.CLEAR_BLUE_STAINED_GLASS.value(), RenderType.translucent());
        });
    }
}
