package grill24.fishtastic.fabric;

import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticBlockEntityTypes;
import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.FishtasticCreativeTabs;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.FishtasticMenuTypes;
import grill24.fishtastic.FishtasticParticleTypes;
import grill24.fishtastic.FishtasticSounds;
import grill24.fishtastic.architectury.fabric.FabricPacketRegistrar;
import grill24.fishtastic.fabric.command.CommandRegistrationFabric;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.data.ShopEntry;
import grill24.fishtastic.itemeffect.ItemEffect;
import grill24.fishtastic.network.FishtasticPackets;
import grill24.fishtastic.network.QuestSyncPacket;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.server.ServerTickHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public final class FishtasticFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // Set up mod initialization logic here
        CommandRegistrationFabric.register();

        // Register synced datapack registries
        DynamicRegistries.registerSynced(FishtasticRegistries.ITEM_EFFECT_REGISTRY_KEY, ItemEffect.CODEC);
        DynamicRegistries.registerSynced(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, grill24.fishtastic.data.FishProfile.CODEC);
        DynamicRegistries.registerSynced(FishtasticRegistries.TEMPERAMENT_REGISTRY_KEY, grill24.fishtastic.data.Temperament.CODEC);
        DynamicRegistries.registerSynced(FishtasticRegistries.QUEST_REGISTRY_KEY, Quest.CODEC);
        DynamicRegistries.registerSynced(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY, ShopEntry.CODEC);
        DynamicRegistries.registerSynced(FishtasticRegistries.FISH_ENCYCLOPEDIA_ENTRY_REGISTRY_KEY, grill24.fishtastic.data.FishEncyclopediaEntry.CODEC);
        DynamicRegistries.registerSynced(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY, grill24.fishtastic.fishtank.CosmeticStructure.CODEC);

        // Modloader-common item registration call, registers items using modloader-specific registration method
        FishtasticDataComponents.registerDataComponents();
        FishtasticItems.registerItems();
        FishtasticBlocks.registerBlocks();
        FishtasticBlockEntityTypes.registerBlockEntityTypes();
        FishtasticMenuTypes.registerMenuTypes();
        FishtasticCreativeTabs.registerCreativeTabs();
        FishtasticSounds.registerSounds();
        FishtasticParticleTypes.registerParticleTypes();

        // Register network packets (server-side)
        FabricPacketRegistrar.registerServerReceiver();
        FishtasticPackets.init();

        // Register server tick handler
        ServerTickEvents.END_SERVER_TICK.register(ServerTickHandler::onServerTick);

        // Send quest and tutorial state to player on world join
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            QuestSyncPacket.sendToPlayer(handler.getPlayer(), FishCatchSavedData.getOrCreate(server));
            grill24.fishtastic.tutorial.TutorialManager.onPlayerJoin(handler.getPlayer());
        });
    }
}
