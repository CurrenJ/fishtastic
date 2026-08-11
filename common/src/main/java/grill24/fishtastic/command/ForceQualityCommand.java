package grill24.fishtastic.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.server.FishingMinigameManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * Operator debug command that pins the quality of every fish the running player catches, so rare
 * outcomes can be watched in a real cast instead of waited for.
 *
 * <pre>
 *   /fishtastic forcequality legendary   — every fish caught from now on is Legendary
 *   /fishtastic forcequality clear       — back to normal rolls
 * </pre>
 *
 * <p>The override is per-player and sticky until cleared, matching the existing forced temperament
 * and difficulty overrides in {@link FishingMinigameManager}. Species selection is untouched — only
 * quality is pinned — and the forced quality feeds the usual difficulty boost, so a forced
 * Legendary is as hard to land as a real one.
 */
public class ForceQualityCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("forcequality")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("clear").executes(ForceQualityCommand::executeClear));

        // One literal per quality rather than a string argument, so the tier names autocomplete
        // and a typo is a parse error instead of a silent no-op.
        for (FishQuality.Quality quality : FishQuality.Quality.values()) {
            command.then(Commands.literal(quality.getSerializedName())
                    .executes(ctx -> executeSet(ctx, quality)));
        }
        return command;
    }

    private static int executeSet(CommandContext<CommandSourceStack> ctx, FishQuality.Quality quality)
            throws CommandSyntaxException {
        UUID playerId = ctx.getSource().getPlayerOrException().getUUID();
        FishingMinigameManager.setForcedQuality(playerId, quality);

        ctx.getSource().sendSuccess(() -> Component.literal("Forcing all caught fish to ")
                .append(Component.literal(quality.getDisplayName()).withStyle(quality.getColor()))
                .append(Component.literal(". Use /fishtastic forcequality clear to stop."))
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int executeClear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        UUID playerId = ctx.getSource().getPlayerOrException().getUUID();
        FishingMinigameManager.clearForcedQuality(playerId);

        ctx.getSource().sendSuccess(() -> Component.literal("Forced fish quality cleared.")
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }
}
