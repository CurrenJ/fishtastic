package grill24.fishtastic.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.util.FishQualityHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Command to set the quality of a fish item in the player's hand.
 * Usage: /setfishquality <quality>
 * Quality values: common, uncommon, rare, epic, legendary
 */
public class SetFishQualityCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("setfishquality")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)) // Requires operator permission
                        .then(Commands.argument("quality", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    // Add suggestions for all quality levels
                                    for (FishQuality.Quality quality : FishQuality.Quality.values()) {
                                        builder.suggest(quality.getSerializedName());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(SetFishQualityCommand::execute))
        );
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String qualityString = StringArgumentType.getString(context, "quality").toLowerCase(Locale.ROOT);

        // Parse quality
        FishQuality.Quality quality;
        try {
            quality = FishQuality.Quality.valueOf(qualityString.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Invalid quality! Valid values: common, uncommon, rare, epic, legendary"));
            return 0;
        }

        if (source.getEntity() instanceof Player player) {
            ItemStack heldItem = player.getMainHandItem();

            if (heldItem.isEmpty()) {
                source.sendFailure(Component.literal("You must be holding an item!"));
                return 0;
            }

            FishQualityHelper.setQuality(heldItem, quality);
            source.sendSuccess(() -> Component.literal("Set fish quality to ")
                    .append(Component.literal(quality.getDisplayName()).withStyle(quality.getColor())), true);
            return 1;
        }

        source.sendFailure(Component.literal("This command must be run by a player!"));
        return 0;
    }
}
