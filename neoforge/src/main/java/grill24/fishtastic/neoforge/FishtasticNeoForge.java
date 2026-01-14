package grill24.fishtastic.neoforge;

import grill24.fishtastic.FishtasticBlockEntityTypes;
import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.compat.GelatinMenusCompat;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

import grill24.fishtastic.Fishtastic;

@Mod(Fishtastic.MOD_ID)
public final class FishtasticNeoForge {
    public FishtasticNeoForge(IEventBus modEventBus, ModContainer container) {
        // Try to register GelatinUI menus, if GelatinUI is present.
        GelatinMenusCompat.init();

        // __BEGIN:item_registration:init_neoforge
        // Call modloader-specific static method to register items to our deferred register
        FishtasticItems.registerItems();
        FishtasticRegistriesNeoForge.ITEMS.register(modEventBus);

        FishtasticBlocks.registerBlocks();
        FishtasticRegistriesNeoForge.BLOCKS.register(modEventBus);

        FishtasticBlockEntityTypes.registerBlockEntityTypes();
        FishtasticRegistriesNeoForge.BLOCK_ENTITY_TYPES.register(modEventBus);

        // Register our custom registries
        modEventBus.addListener(FishtasticRegistriesNeoForge::registerRegistries);

        // Register config
        FishtasticConfig.register(container);
    }
}
