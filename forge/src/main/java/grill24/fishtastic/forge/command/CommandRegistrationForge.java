package grill24.fishtastic.forge.command;

import com.mojang.brigadier.CommandDispatcher;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.command.FishtasticCommand;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Registers commands for Forge. {@code RegisterCommandsEvent} fires on the game bus, so this is
 * subscribed with the default {@code Bus.FORGE} (B5.3: NeoForge's constructor-injected {@code
 * IEventBus} game bus is Forge's static {@code MinecraftForge.EVENT_BUS}, wired here through the
 * annotation instead of an explicit {@code addListener} call).
 */
@Mod.EventBusSubscriber(modid = Fishtastic.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CommandRegistrationForge {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        FishtasticCommand.register(dispatcher);
    }
}
