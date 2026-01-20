package grill24.fishtastic.fabric;

import grill24.fishtastic.FishtasticBlockEntityTypes;
import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.fabric.command.CommandRegistrationFabric;
import net.fabricmc.api.ModInitializer;

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
    }
}
