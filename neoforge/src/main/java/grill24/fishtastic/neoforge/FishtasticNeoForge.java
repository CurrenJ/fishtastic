package grill24.fishtastic.neoforge;

import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticBlockEntityTypes;
import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.FishtasticCreativeTabs;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.architectury.neoforge.NeoForgePacketRegistrar;
import grill24.fishtastic.compat.GelatinMenusCompat;
import grill24.fishtastic.itemeffect.ItemEffect;
import grill24.fishtastic.network.FishtasticPackets;
import grill24.fishtastic.server.ServerTickHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

import grill24.fishtastic.Fishtastic;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

@Mod(Fishtastic.MOD_ID)
public final class FishtasticNeoForge {
    public FishtasticNeoForge(IEventBus modEventBus, ModContainer container) {
        // Try to register GelatinUI menus, if GelatinUI is present.
        GelatinMenusCompat.init();

        // __BEGIN:item_registration:init_neoforge
        // Register data components first
        FishtasticDataComponents.registerDataComponents();
        FishtasticRegistriesNeoForge.DATA_COMPONENT_TYPES.register(modEventBus);

        // Call modloader-specific static method to register items to our deferred register
        FishtasticItems.registerItems();
        FishtasticRegistriesNeoForge.ITEMS.register(modEventBus);

        FishtasticBlocks.registerBlocks();
        FishtasticRegistriesNeoForge.BLOCKS.register(modEventBus);

        FishtasticBlockEntityTypes.registerBlockEntityTypes();
        FishtasticRegistriesNeoForge.BLOCK_ENTITY_TYPES.register(modEventBus);

        FishtasticCreativeTabs.registerCreativeTabs();
        FishtasticRegistriesNeoForge.CREATIVE_MODE_TABS.register(modEventBus);

        // Register our custom registries
        modEventBus.addListener(FishtasticRegistriesNeoForge::registerRegistries);

        // Register datapack registries
        modEventBus.addListener((DataPackRegistryEvent.NewRegistry event) -> {
            Fishtastic.LOGGER.info("Registering ItemEffect datapack registry");
            event.dataPackRegistry(FishtasticRegistries.ITEM_EFFECT_REGISTRY_KEY, ItemEffect.CODEC, ItemEffect.CODEC);
        });

        // Register network packets
        modEventBus.addListener(NeoForgePacketRegistrar::register);
        FishtasticPackets.init();

        // Register server tick handler
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            ServerTickHandler.onServerTick(event.getServer());
        });

        // Register config
        FishtasticConfig.register(container);
    }
}
