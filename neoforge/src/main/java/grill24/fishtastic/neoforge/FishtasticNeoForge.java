package grill24.fishtastic.neoforge;

import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticBlockEntityTypes;
import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.FishtasticCreativeTabs;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.FishtasticSounds;
import grill24.fishtastic.architectury.neoforge.NeoForgePacketRegistrar;
import grill24.fishtastic.compat.GelatinMenusCompat;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.data.ShopEntry;
import grill24.fishtastic.itemeffect.ItemEffect;
import grill24.fishtastic.network.FishtasticPackets;
import grill24.fishtastic.network.QuestSyncPacket;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.server.ServerTickHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

import grill24.fishtastic.Fishtastic;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.server.level.ServerLevel;
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

        FishtasticSounds.registerSounds();
        FishtasticRegistriesNeoForge.SOUND_EVENTS.register(modEventBus);

        // Register our custom registries
        modEventBus.addListener(FishtasticRegistriesNeoForge::registerRegistries);

        // Register datapack registries
        modEventBus.addListener((DataPackRegistryEvent.NewRegistry event) -> {
            Fishtastic.LOGGER.info("Registering ItemEffect datapack registry");
            event.dataPackRegistry(FishtasticRegistries.ITEM_EFFECT_REGISTRY_KEY, ItemEffect.CODEC, ItemEffect.CODEC);
            Fishtastic.LOGGER.info("Registering FishProfile datapack registry");
            event.dataPackRegistry(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, grill24.fishtastic.data.FishProfile.CODEC, grill24.fishtastic.data.FishProfile.CODEC);
            Fishtastic.LOGGER.info("Registering Temperament datapack registry");
            event.dataPackRegistry(FishtasticRegistries.TEMPERAMENT_REGISTRY_KEY, grill24.fishtastic.data.Temperament.CODEC, grill24.fishtastic.data.Temperament.CODEC);
            Fishtastic.LOGGER.info("Registering Quest datapack registry");
            event.dataPackRegistry(FishtasticRegistries.QUEST_REGISTRY_KEY, Quest.CODEC, Quest.CODEC);
            Fishtastic.LOGGER.info("Registering ShopEntry datapack registry");
            event.dataPackRegistry(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY, ShopEntry.CODEC, ShopEntry.CODEC);
            Fishtastic.LOGGER.info("Registering FishEncyclopediaEntry datapack registry");
            event.dataPackRegistry(FishtasticRegistries.FISH_ENCYCLOPEDIA_ENTRY_REGISTRY_KEY, grill24.fishtastic.data.FishEncyclopediaEntry.CODEC, grill24.fishtastic.data.FishEncyclopediaEntry.CODEC);
        });

        // Send quest and tutorial state to player on world join
        NeoForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) -> {
            if (event.getPlayer() == null) return;
            QuestSyncPacket.sendToPlayer(event.getPlayer(), FishCatchSavedData.getOrCreate(((ServerLevel) event.getPlayer().level()).getServer()));
            grill24.fishtastic.tutorial.TutorialManager.onPlayerJoin(event.getPlayer());
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

        // Generate game test structure files (./gradlew :neoforge:runData)
        modEventBus.addListener((net.neoforged.neoforge.data.event.GatherDataEvent.Server event) -> {
            event.getGenerator().addProvider(true,
                new grill24.fishtastic.neoforge.datagen.GameTestStructureProvider(event.getGenerator().getPackOutput()));
        });
    }
}
