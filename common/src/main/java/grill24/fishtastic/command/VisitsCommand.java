package grill24.fishtastic.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import grill24.fishtastic.server.FishSchoolManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Solo-playtest triggers for the deterministic visit windows (docs/world-fish-exploration-implementation-plan.md,
 * Step 2).
 *
 * <pre>
 * /fishtastic visits windows   the sites near you: window, rank, active, suppressed, depleted, live
 * /fishtastic visits weatherlog   the recent weather used at window start, and any missing history
 * /fishtastic visits reveal    FORCES the nearest site's current window to reveal now (testing override)
 * </pre>
 */
public class VisitsCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("visits")
                .requires(FishtasticPermissions.gamemaster())
                .then(Commands.literal("windows").executes(context -> {
                    String report = FishSchoolManager.get(context.getSource().getLevel())
                            .visitWindowsReport(context.getSource().getPosition());
                    context.getSource().sendSuccess(() -> Component.literal(report), false);
                    return 1;
                }))
                .then(Commands.literal("weatherlog").executes(context -> {
                    String report = FishSchoolManager.get(context.getSource().getLevel()).weatherLogReport();
                    context.getSource().sendSuccess(() -> Component.literal(report), false);
                    return 1;
                }))
                .then(Commands.literal("reveal").executes(context -> {
                    String result = FishSchoolManager.get(context.getSource().getLevel())
                            .revealNearestForDebug(context.getSource().getPosition());
                    context.getSource().sendSuccess(() -> Component.literal(result), false);
                    return 1;
                }));
    }
}
