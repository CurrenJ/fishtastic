package grill24.fishtastic.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import grill24.FishtasticRegistries;
import grill24.fishtastic.data.MovementParams;
import grill24.fishtastic.data.PhaseRule;
import grill24.fishtastic.data.Temperament;
import grill24.fishtastic.server.FishingMinigameManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.List;

public class TemperamentCommand {

    private static final DynamicCommandExceptionType NOT_FOUND = new DynamicCommandExceptionType(
            id -> Component.literal("No temperament registered for '" + id + "'"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("temperament")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("id", IdentifierArgument.id())
                                .suggests((ctx, builder) -> temperamentSuggestions(ctx, builder))
                                .executes(TemperamentCommand::execute))
                        .then(Commands.literal("force")
                                .then(Commands.argument("id", IdentifierArgument.id())
                                        .suggests((ctx, builder) -> temperamentSuggestions(ctx, builder))
                                        .executes(TemperamentCommand::executeForce))
                                .then(Commands.literal("clear")
                                        .executes(TemperamentCommand::executeForceClear)))
        );
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> temperamentSuggestions(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        Registry<Temperament> registry = ctx.getSource().registryAccess()
                .lookupOrThrow(FishtasticRegistries.TEMPERAMENT_REGISTRY_KEY);
        return SharedSuggestionProvider.suggest(
                registry.keySet().stream().map(Identifier::toString).toList(), builder);
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Identifier id = IdentifierArgument.getId(ctx, "id");
        Registry<Temperament> registry = ctx.getSource().registryAccess()
                .lookupOrThrow(FishtasticRegistries.TEMPERAMENT_REGISTRY_KEY);

        ResourceKey<Temperament> key = ResourceKey.create(FishtasticRegistries.TEMPERAMENT_REGISTRY_KEY, id);
        Temperament t = registry.getOptional(key).orElseThrow(() -> NOT_FOUND.create(id));

        CommandSourceStack source = ctx.getSource();

        source.sendSuccess(() -> Component.literal("=== Temperament: " + id + " ===")
                .withStyle(ChatFormatting.GOLD), false);

        source.sendSuccess(() -> Component.literal(
                String.format("  difficulty  : %.2f – %.2f", t.difficultyMin(), t.difficultyMax()))
                .withStyle(ChatFormatting.WHITE), false);

        t.params().ifPresent(params -> {
            source.sendSuccess(() -> Component.literal("  global params:")
                    .withStyle(ChatFormatting.AQUA), false);
            printParams(source, params, "    ");
        });

        List<PhaseRule> phases = t.phases();
        source.sendSuccess(() -> Component.literal(
                String.format("  phases: (%d)", phases.size()))
                .withStyle(ChatFormatting.AQUA), false);

        for (int i = 0; i < phases.size(); i++) {
            PhaseRule phase = phases.get(i);
            String patternStr = phase.patterns().stream()
                    .map(p -> p.getSerializedName())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("(none)");
            final String line = String.format("    [%d] threshold=%.2f  patterns: %s", i, phase.threshold(), patternStr);
            source.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
            phase.params().ifPresent(params -> {
                source.sendSuccess(() -> Component.literal("        params:")
                        .withStyle(ChatFormatting.DARK_GRAY), false);
                printParams(source, params, "          ");
            });
        }

        return 1;
    }

    private static void printParams(CommandSourceStack source, MovementParams p, String indent) {
        // DRIFT
        if (p.driftIntervalMin().isPresent() || p.driftIntervalMax().isPresent()) {
            String lo = p.driftIntervalMin().map(v -> String.format("%.0f", v)).orElse("?");
            String hi = p.driftIntervalMax().map(v -> String.format("%.0f", v)).orElse("?");
            send(source, indent + String.format("drift_interval : [%s, %s] ticks", lo, hi));
        }
        if (p.driftSpeedMin().isPresent() || p.driftSpeedMax().isPresent()) {
            String lo = p.driftSpeedMin().map(v -> String.format("%.4f", v)).orElse("?");
            String hi = p.driftSpeedMax().map(v -> String.format("%.4f", v)).orElse("?");
            send(source, indent + String.format("drift_speed    : [%s, %s]", lo, hi));
        }
        // DART
        if (p.dartPauseMin().isPresent() || p.dartPauseMax().isPresent()) {
            String lo = p.dartPauseMin().map(v -> String.format("%.0f", v)).orElse("?");
            String hi = p.dartPauseMax().map(v -> String.format("%.0f", v)).orElse("?");
            send(source, indent + String.format("dart_pause     : [%s, %s] ticks", lo, hi));
        }
        p.dartBurstTicks().ifPresent(v ->
                send(source, indent + String.format("dart_burst     : %.0f ticks", v)));
        // OSCILLATE
        p.oscFrequency().ifPresent(v ->
                send(source, indent + String.format("osc_frequency  : %.4f", v)));
        if (p.oscRePeriodMin().isPresent() || p.oscRePeriodMax().isPresent()) {
            String lo = p.oscRePeriodMin().map(v -> String.format("%.0f", v)).orElse("?");
            String hi = p.oscRePeriodMax().map(v -> String.format("%.0f", v)).orElse("?");
            send(source, indent + String.format("osc_re_period  : [%s, %s] ticks", lo, hi));
        }
        if (p.oscAmplitudeMin().isPresent() || p.oscAmplitudeMax().isPresent()) {
            String lo = p.oscAmplitudeMin().map(v -> String.format("%.3f", v)).orElse("?");
            String hi = p.oscAmplitudeMax().map(v -> String.format("%.3f", v)).orElse("?");
            send(source, indent + String.format("osc_amplitude  : [%s, %s]", lo, hi));
        }
        // FLEE
        p.fleeThreshold().ifPresent(v ->
                send(source, indent + String.format("flee_threshold : %.3f", v)));
        p.fleeSpeed().ifPresent(v ->
                send(source, indent + String.format("flee_speed     : %.4f", v)));
        // CATCH PROGRESS
        p.catchProgressLoss().ifPresent(v ->
                send(source, indent + String.format("progress_loss  : %.4f", v)));
        p.initialCatchProgress().ifPresent(v ->
                send(source, indent + String.format("initial_prog   : %.2f", v)));
    }

    private static void send(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.GRAY), false);
    }

    private static int executeForce(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Identifier id = IdentifierArgument.getId(ctx, "id");
        Registry<Temperament> registry = ctx.getSource().registryAccess()
                .lookupOrThrow(FishtasticRegistries.TEMPERAMENT_REGISTRY_KEY);

        ResourceKey<Temperament> key = ResourceKey.create(FishtasticRegistries.TEMPERAMENT_REGISTRY_KEY, id);
        if (registry.getOptional(key).isEmpty()) throw NOT_FOUND.create(id);

        FishingMinigameManager.setForcedTemperament(ctx.getSource().getPlayerOrException().getUUID(), key);
        ctx.getSource().sendSuccess(() -> Component.literal("Forced temperament set to: " + id)
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int executeForceClear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        FishingMinigameManager.clearForcedTemperament(ctx.getSource().getPlayerOrException().getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal("Forced temperament cleared.")
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }
}
