package grill24.fishtastic.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Command to set the size of an item in the player's hand.
 * Usage: /setitemsize <size>
 */
public class SetItemSizeCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("setitemsize")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)) // Requires operator permission
                        .then(Commands.argument("size", FloatArgumentType.floatArg(0.01f, 1000.0f))
                                .executes(SetItemSizeCommand::execute))
        );
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        float size = FloatArgumentType.getFloat(context, "size");

        if (source.getEntity() instanceof Player player) {
            ItemStack heldItem = player.getMainHandItem();

            if (heldItem.isEmpty()) {
                source.sendFailure(Component.literal("You must be holding an item!"));
                return 0;
            }

            ItemSizeHelper.setSize(heldItem, size);
            source.sendSuccess(() -> Component.literal("Set item size to " + String.format("%.2f", size)), true);
            return 1;
        }

        source.sendFailure(Component.literal("This command must be run by a player!"));
        return 0;
    }
}
