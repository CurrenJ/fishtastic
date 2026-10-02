package grill24.fishtastic.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import grill24.fishtastic.util.FishingBarStyles;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Dev command {@code /fishtastic fishingbar [style|auto]} — shows or forces the fishing minigame
 * bar/bobber style (see {@link FishingBarStyles}). By default the style follows the session's
 * server-resolved context (tall normally, lava in lava, classic in the End or a mushroom island);
 * naming a style pins an override so art can be eyeballed without hunting for the right biome, and
 * {@code auto} releases it. Single-player only, driving client state directly like
 * {@link PoseDebugCommand}. Registered as an ordinary server-tree subcommand (not a client command)
 * so vanilla's chat preview knows it and doesn't mark it as unparsed. Only registered in
 * development environments — see {@link FishtasticCommand}.
 */
public final class FishingBarCommand {
    /** Argument value that releases the override instead of naming a style. */
    private static final String AUTO = "auto";

    private FishingBarCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        // FishingBarStyles must not be touched while building the tree: it pulls in the client-only
        // renderer (FishingMinigameAnimation), and registration runs on the dedicated server too
        // (dev environments register this command), where that class cannot load. Every reference
        // below therefore stays inside an execution/suggestion lambda.
        return Commands.literal("fishingbar")
                .executes(ctx -> {
                    FishingBarStyles.Style forced = FishingBarStyles.override();
                    String current = forced != null ? forced.id() + " (forced)" : AUTO + " (context-driven)";
                    ctx.getSource().sendSuccess(() -> Component.literal("Fishing bar style: " + current
                            + " (available: " + String.join(", ", FishingBarStyles.ids()) + ", " + AUTO + ")"), false);
                    return 1;
                })
                .then(Commands.argument("style", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            List<String> suggestions = new ArrayList<>(FishingBarStyles.ids());
                            suggestions.add(AUTO);
                            return SharedSuggestionProvider.suggest(suggestions, builder);
                        })
                        .executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "style");
                            if (AUTO.equals(id)) {
                                FishingBarStyles.clearOverride();
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                        "Fishing bar style released to context."), false);
                                return 1;
                            }
                            if (!FishingBarStyles.setOverride(id)) {
                                ctx.getSource().sendFailure(Component.literal("Unknown style '" + id
                                        + "'. Available: " + String.join(", ", FishingBarStyles.ids())));
                                return 0;
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal("Fishing bar style forced to " + id + "."), false);
                            return 1;
                        }));
    }
}
