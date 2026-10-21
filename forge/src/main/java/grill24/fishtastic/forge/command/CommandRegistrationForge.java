package grill24.fishtastic.forge.command;

import com.mojang.brigadier.CommandDispatcher;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.command.FishtasticCommand;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Fishtastic.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CommandRegistrationForge {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        FishtasticCommand.register(dispatcher);
    }
}
