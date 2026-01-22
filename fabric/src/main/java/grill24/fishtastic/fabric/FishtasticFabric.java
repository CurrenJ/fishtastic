package grill24.fishtastic.fabric;

import grill24.fishtastic.FishtasticBlockEntityTypes;
import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.architectury.fabric.FabricPacketRegistrar;
import grill24.fishtastic.fabric.command.CommandRegistrationFabric;
import grill24.fishtastic.network.FishtasticPackets;
import grill24.fishtastic.server.ServerTickHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public final class FishtasticFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // Set up mod initialization logic here
        CommandRegistrationFabric.register();

        // Modloader-common item registration call, registers items using modloader-specific registration method
        FishtasticDataComponents.registerDataComponents();
        FishtasticItems.registerItems();
        FishtasticBlocks.registerBlocks();
        FishtasticBlockEntityTypes.registerBlockEntityTypes();

        // Register network packets (server-side)
        FabricPacketRegistrar.registerServerReceiver();
        FishtasticPackets.init();

        // Register server tick handler
        ServerTickEvents.END_SERVER_TICK.register(ServerTickHandler::onServerTick);
    }
}
