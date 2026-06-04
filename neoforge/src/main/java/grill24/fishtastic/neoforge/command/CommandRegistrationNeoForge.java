package grill24.fishtastic.neoforge.command;

import com.mojang.brigadier.CommandDispatcher;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.command.DebugFishDataCommand;
import grill24.fishtastic.command.FishProfileCommand;
import grill24.fishtastic.command.FishtasticCommand;
import grill24.fishtastic.command.SetFishQualityCommand;
import grill24.fishtastic.command.SetItemSizeCommand;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Registers commands for NeoForge.
 */
@EventBusSubscriber(modid = Fishtastic.MOD_ID)
public class CommandRegistrationNeoForge {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        FishtasticCommand.register(dispatcher);
        SetItemSizeCommand.register(dispatcher);
        SetFishQualityCommand.register(dispatcher);
        DebugFishDataCommand.register(dispatcher);
        FishProfileCommand.register(dispatcher);
    }
}
