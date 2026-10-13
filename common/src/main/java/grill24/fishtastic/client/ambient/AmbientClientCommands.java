package grill24.fishtastic.client.ambient;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import grill24.fishtastic.client.FishtasticClientConfig;
import java.util.function.BiConsumer;

/** Local commands: preferences never round-trip to, or change, the server. */
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
                .then(LiteralArgumentBuilder.<S>literal("debug").executes(ctx -> {
                    feedback.accept(ctx.getSource(), ClientAmbientFish.natural().debugState());
                    feedback.accept(ctx.getSource(), AmbientContextClient.debugState());
                    return 1;
                })));
    }
}
