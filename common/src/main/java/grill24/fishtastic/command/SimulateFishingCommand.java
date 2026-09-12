package grill24.fishtastic.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import grill24.fishtastic.server.FishingMinigameManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Admin/dev bulk-grant command: instantly plays {@code count} fishing games for a player using
 * whatever fishtastic rod/bait/hook/charm they currently have equipped, and grants every reward
 * as if each game had actually been played through — no minigame, no waiting.
 *
 * <pre>
 *   /fishtastic simulatefishing 50            — 50 games for the running player
 *   /fishtastic simulatefishing 50 Steve      — 50 games for Steve, using Steve's own gear
 * </pre>
 *
 * <p>Delegates to {@link FishingMinigameManager#simulateFishingGames}, which shares the same
 * reward-granting path as a real minigame completion — see that method's doc for how bait/gear
 * durability is handled across the batch.
 */
public class SimulateFishingCommand {

    private static final int MAX_GAMES = 1000;

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("simulatefishing")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, MAX_GAMES))
                        .executes(ctx -> execute(ctx, null, IntegerArgumentType.getInteger(ctx, "count")))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> execute(ctx, EntityArgument.getPlayer(ctx, "player"),
                                        IntegerArgumentType.getInteger(ctx, "count")))));
    }

    private static int execute(CommandContext<CommandSourceStack> ctx, ServerPlayer explicit, int count)
            throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer target = explicit != null ? explicit
                : source.getEntity() instanceof ServerPlayer sp ? sp : null;
        if (target == null) {
            source.sendFailure(Component.literal("Specify a player or run as a player."));
            return 0;
        }

        if (!(target.level() instanceof ServerLevel level)) {
            source.sendFailure(Component.literal("Player is not in a valid level."));
            return 0;
        }

        FishingMinigameManager.SimulationResult result =
                FishingMinigameManager.get(level).simulateFishingGames(target, count, 1.0f);

        if (result.gamesPlayed() == 0) {
            source.sendFailure(Component.literal(target.getName().getString()
                    + " has no eligible fish pool for a cast right now (check zone/held rod) — no games could be simulated."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Simulated ")
                .append(Component.literal(String.valueOf(result.gamesPlayed())).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" fishing game" + (result.gamesPlayed() == 1 ? "" : "s") + " for "))
                .append(Component.literal(target.getName().getString()).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(": granted "))
                .append(Component.literal(String.valueOf(result.rewards().size())).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" items, "))
                .append(Component.literal(String.valueOf(result.xpAwarded())).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" xp"
                        + (result.trashCaught() > 0 ? ", " + result.trashCaught() + " trash" : "")
                        + (result.firstCatchCount() > 0 ? ", " + result.firstCatchCount() + " first catch(es)" : "") + "."))
                .withStyle(ChatFormatting.GREEN), true);
        return result.gamesPlayed();
    }
}
