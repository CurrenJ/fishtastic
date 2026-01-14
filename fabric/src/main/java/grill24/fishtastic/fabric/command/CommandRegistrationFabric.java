package grill24.fishtastic.fabric.command;

import grill24.fishtastic.command.FishtasticCommand;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * Registers commands for Fabric.
 */
public class CommandRegistrationFabric {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            FishtasticCommand.register(dispatcher);
        });
    }
}
