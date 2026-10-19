package grill24.fishtastic.client.ambient;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import grill24.fishtastic.client.FishtasticClientConfig;
import grill24.FishtasticRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.resources.Identifier;
import java.util.function.BiConsumer;

/** Local preferences and filming commands; these never change the server. */
public final class AmbientClientCommands {
    private AmbientClientCommands() {}

    public static <S> void register(CommandDispatcher<S> dispatcher, BiConsumer<S, String> feedback) {
        dispatcher.register(LiteralArgumentBuilder.<S>literal("fishtasticambient")
                .then(LiteralArgumentBuilder.<S>literal("enabled")
                        .then(RequiredArgumentBuilder.<S, Boolean>argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                    FishtasticClientConfig.setAmbientFishEnabled(enabled);
                                    ClientAmbientFish.settingsChanged();
                                    feedback.accept(ctx.getSource(), "Ambient fish " + (enabled ? "enabled" : "disabled") + ".");
                                    return 1;
                                })))
                .then(LiteralArgumentBuilder.<S>literal("density")
                        .then(RequiredArgumentBuilder.<S, Integer>argument("density", IntegerArgumentType.integer(0, 100))
                                .executes(ctx -> {
                                    int density = IntegerArgumentType.getInteger(ctx, "density");
                                    FishtasticClientConfig.setAmbientFishDensity(density);
                                    ClientAmbientFish.settingsChanged();
                                    feedback.accept(ctx.getSource(), "Ambient fish density: " + density + "%.");
                                    return 1;
                                })))
                .then(LiteralArgumentBuilder.<S>literal("debug")
                        .then(LiteralArgumentBuilder.<S>literal("spawn")
                                .then(RequiredArgumentBuilder.<S, Identifier>argument("species", IdentifierArgument.id())
                                        .suggests((ctx, builder) -> {
                                            var level = Minecraft.getInstance().level;
                                            return SharedSuggestionProvider.suggestResource(level == null
                                                    ? java.util.stream.Stream.empty()
                                                    : level.registryAccess().lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY)
                                                            .keySet().stream(), builder);
                                        })
                                        .executes(ctx -> spawn(ctx.getSource(), ctx.getArgument("species", Identifier.class), 1, feedback))
                                        .then(RequiredArgumentBuilder.<S, Integer>argument("count", IntegerArgumentType.integer(1, AmbientFishRuntime.MAX_INDIVIDUALS))
                                                .executes(ctx -> spawn(ctx.getSource(), ctx.getArgument("species", Identifier.class),
                                                        IntegerArgumentType.getInteger(ctx, "count"), feedback)))))
                        .then(LiteralArgumentBuilder.<S>literal("clear").executes(ctx -> {
                            int removed = ClientAmbientFish.natural().clearDebugFish();
                            feedback.accept(ctx.getSource(), "Removed " + removed + " debug ambient fish.");
                            return removed;
                        }))
                        .executes(ctx -> {
                            feedback.accept(ctx.getSource(), ClientAmbientFish.natural().diagnostics().debugState());
                            feedback.accept(ctx.getSource(), AmbientContextClient.debugState());
                            return 1;
                        })));
    }

    private static <S> int spawn(S source, Identifier species, int count, BiConsumer<S, String> feedback) {
        var result = ClientAmbientFish.natural().spawnDebugFish(species, count);
        feedback.accept(source, result.message());
        return result.count();
    }
}
