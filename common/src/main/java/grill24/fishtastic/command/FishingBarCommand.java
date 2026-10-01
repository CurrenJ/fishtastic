package grill24.fishtastic.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import grill24.fishtastic.util.FishingBarStyles;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/**
 * Dev command {@code /fishtastic fishingbar [style]} — shows or switches the active fishing
 * minigame bar/bobber style (see {@link FishingBarStyles}). Single-player only, driving client
 * state directly like {@link PoseDebugCommand}. Registered as an ordinary server-tree subcommand
 * (not a client command) so vanilla's chat preview knows it and doesn't mark it as unparsed.
 * Only registered in development environments — see {@link FishtasticCommand}.
 */
public final class FishingBarCommand {
    private FishingBarCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("fishingbar")
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal("Active fishing bar style: "
                            + FishingBarStyles.active().id() + " (available: " + String.join(", ", FishingBarStyles.ids()) + ")"), false);
                    return 1;
                })
                .then(Commands.argument("style", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(FishingBarStyles.ids(), builder))
                        .executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "style");
                            if (!FishingBarStyles.setActive(id)) {
                                ctx.getSource().sendFailure(Component.literal("Unknown style '" + id
                                        + "'. Available: " + String.join(", ", FishingBarStyles.ids())));
                                return 0;
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal("Fishing bar style set to " + id + "."), false);
                            return 1;
                        }));
    }
}
