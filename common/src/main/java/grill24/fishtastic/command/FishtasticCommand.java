package grill24.fishtastic.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import grill24.fishtastic.compat.CompatUtil;
import grill24.fishtastic.compat.GelatinOpenMenuCompat;
import grill24.fishtastic.env.DevEnvironmentCheck;
import grill24.fishtastic.mcp.McpBridgeCommand;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.ClickEvent;
import java.net.URI;

/**
 * Command to open the Fishtastic UI screen.
 */
public class FishtasticCommand {

    private static int spawnSchool(CommandContext<CommandSourceStack> context, net.minecraft.resources.Identifier species,
                                   @org.jetbrains.annotations.Nullable String quality) {
        grill24.fishtastic.component.FishQuality.Quality[] qualities = null;
        if ("mixed".equals(quality)) {
            qualities = grill24.fishtastic.component.FishQuality.Quality.values();
        } else if (quality != null) {
            for (var q : grill24.fishtastic.component.FishQuality.Quality.values()) {
                if (q.getSerializedName().equals(quality)) qualities = new grill24.fishtastic.component.FishQuality.Quality[]{q};
            }
            if (qualities == null) {
                context.getSource().sendFailure(Component.literal("Unknown quality " + quality
                        + ": common, uncommon, rare, epic, legendary or mixed."));
                return 0;
            }
        }
        String result = grill24.fishtastic.server.FishSchoolManager.get(context.getSource().getLevel())
                .spawnForDebug(net.minecraft.core.BlockPos.containing(context.getSource().getPosition()), species, qualities);
        context.getSource().sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("fishtastic")
                .executes(FishtasticCommand::openScreen)
                .then(Commands.literal("schools").executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal(
                            "Interactive fish schools: sparkling wild fish can approach when you cast a supported Fishtastic rod nearby. "
                                    + "The school fish replaces one normal target; catching it removes that member for everyone. "
                                    + "Local display: /fishtasticambient schools visible <true|false>."), false);
                    return 1;
                })
                        // Solo-playtest triggers: make a visit now, and read what its fish are doing.
                        .then(Commands.literal("spawn").requires(FishtasticPermissions.gamemaster())
                                .executes(context -> spawnSchool(context, null, null))
                                .then(Commands.argument("species", net.minecraft.commands.arguments.IdentifierArgument.id())
                                        .executes(context -> spawnSchool(context,
                                                net.minecraft.commands.arguments.IdentifierArgument.getId(context, "species"), null))
                                        // Every member that quality, or "mixed" for one of each in turn: the trail colours.
                                        .then(Commands.argument("quality", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                                        java.util.List.of("common", "uncommon", "rare", "epic", "legendary", "mixed"), b))
                                                .executes(context -> spawnSchool(context,
                                                        net.minecraft.commands.arguments.IdentifierArgument.getId(context, "species"),
                                                        com.mojang.brigadier.arguments.StringArgumentType.getString(context, "quality"))))))
                        .then(Commands.literal("debug").requires(FishtasticPermissions.gamemaster()).executes(context -> {
                            var schools = grill24.fishtastic.server.FishSchoolManager.get(context.getSource().getLevel());
                            String result = schools.debugNearest(context.getSource().getPosition());
                            ServerPlayer player = context.getSource().getPlayer();
                            String bobber = player == null ? "" : "\n" + schools.hookReport(player);
                            context.getSource().sendSuccess(() -> Component.literal(result + bobber), false);
                            return 1;
                        })
                                // Every approach's start, bite and failure (with why) to the server log.
                                .then(Commands.literal("log")
                                        .then(Commands.argument("enabled", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                                .executes(context -> {
                                                    boolean enabled = com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "enabled");
                                                    grill24.fishtastic.server.FishSchoolManager.logApproaches = enabled;
                                                    context.getSource().sendSuccess(() -> Component.literal("School approach logging "
                                                            + (enabled ? "on: see [schools] lines in the log." : "off.")), false);
                                                    return 1;
                                                })))))
                .then(CosmeticCommand.build())
                .then(SetItemSizeCommand.build())
                .then(SetFishQualityCommand.build())
                .then(SetTankShapeCommand.build())
                .then(ForceQualityCommand.build())
                .then(DebugFishDataCommand.build())
                .then(FishProfileCommand.build())
                .then(FishZoneCommand.build())
                .then(TemperamentCommand.build())
                .then(TestQuestNotifyCommand.build())
                .then(TutorialCommand.build())
                .then(CleanupGoalCommand.build())
                .then(QuestsCommand.build())
                .then(DebugEncyclopediaCommand.build())
                .then(DebugShapesCommand.build())
                .then(TokenBalanceCommand.build())
                .then(NotificationVolumeCommand.build())
                .then(TankWaterFillCommand.build())
                .then(TankInteriorLightCommand.build())
                .then(ReducedEffectsCommand.build())
                .then(SimulateFishingCommand.build())
                .then(BackupCommand.build())
                .then(FollowFishStubCommand.build());

        // Production builds exclude the whole grill24.fishtastic.mcp package from the jar (dev-only
        // tooling) - never reference McpBridgeCommand outside this guard, or the missing class gets
        // resolved and the server fails to start.
        if (DevEnvironmentCheck.isDevelopmentEnvironment()) {
            command.then(McpBridgeCommand.build());
            // Tuning tool for the catch celebration's timings — dev-only, and client-driven, so it
            // never reaches a dedicated server's command tree.
            command.then(CelebrationCommand.build());
            // Live-tunes the leaderboard/mannequin fisherman hang pose — same dev-only, client-driven pattern.
            command.then(PoseDebugCommand.build());
            // Switches the fishing minigame's bar/bobber art — same dev-only, client-state-driven pattern.
            command.then(FishingBarCommand.build());
            // Prints the client-side swim/hover verdict for the tank being looked at — same pattern,
            // and the only way to tell a budget-capped fish from a size-gated one on screen.
            command.then(TankSimDebugCommand.build());
            // Fish companion dev command (docs/fish-companions.md) — same dev-only pattern; minigame/stopgame are client-driven.
            command.then(CompanionCommand.build());
        }

        dispatcher.register(command);
    }

    private static int openScreen(CommandContext<CommandSourceStack> context) {
        // __BEGIN:gelatinui
        CommandSourceStack source = context.getSource();

        if (source.getEntity() instanceof ServerPlayer player) {
            if (!CompatUtil.isGelatinUiPresent()) {
                // Append dependency suggestion
                Component installMessage = Component.literal("Install ")
                    .append(Component.literal("Gelatin UI")
                        .withStyle(style -> style
                            .withColor(ChatFormatting.YELLOW)
                            .withClickEvent(new ClickEvent.OpenUrl(URI.create("https://www.curseforge.com/minecraft/mc-mods/gelatin-ui")))
                            .withUnderlined(true)))
                    .append(Component.literal(" for a visual display.")
                        .withStyle(ChatFormatting.YELLOW));
                source.sendSuccess(() -> installMessage, false);
                return 1;
            } else {
                GelatinOpenMenuCompat.openFishtasticMenu(player);
            }
        } else {
            source.sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }
        // __END:gelatinui

        return 1;
    }
}
