package grill24.fishtastic.fabric.command;

import grill24.fishtastic.command.DebugFishDataCommand;
import grill24.fishtastic.command.FishProfileCommand;
import grill24.fishtastic.command.TemperamentCommand;
import grill24.fishtastic.command.FishtasticCommand;
import grill24.fishtastic.command.SetFishQualityCommand;
import grill24.fishtastic.command.SetItemSizeCommand;
import grill24.fishtastic.command.TestQuestNotifyCommand;
import grill24.fishtastic.command.TutorialCommand;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * Registers commands for Fabric.
 */
public class CommandRegistrationFabric {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            FishtasticCommand.register(dispatcher);
            SetItemSizeCommand.register(dispatcher);
            SetFishQualityCommand.register(dispatcher);
            DebugFishDataCommand.register(dispatcher);
            FishProfileCommand.register(dispatcher);
            TemperamentCommand.register(dispatcher);
            TestQuestNotifyCommand.register(dispatcher);
            TutorialCommand.register(dispatcher);
        });
    }
}
