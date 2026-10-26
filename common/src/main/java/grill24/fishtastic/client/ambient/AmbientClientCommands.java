package grill24.fishtastic.client.ambient;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
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
                .then(LiteralArgumentBuilder.<S>literal("schools")
                        .then(LiteralArgumentBuilder.<S>literal("visible")
                                .then(RequiredArgumentBuilder.<S, Boolean>argument("visible", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            boolean visible = BoolArgumentType.getBool(ctx, "visible");
                                            FishtasticClientConfig.setFishSchoolsVisible(visible);
                                            feedback.accept(ctx.getSource(), "Interactive fish schools "
                                                    + (visible ? "shown" : "hidden") + ". This changes only your display.");
                                            return 1;
                                        })))
                        .then(LiteralArgumentBuilder.<S>literal("arrivals")
                                .then(RequiredArgumentBuilder.<S, Boolean>argument("shown", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            boolean shown = BoolArgumentType.getBool(ctx, "shown");
                                            FishtasticClientConfig.setFishSchoolArrivalsShown(shown);
                                            feedback.accept(ctx.getSource(), "Ripples where a fish visit arrives "
                                                    + (shown ? "shown" : "hidden") + ".");
                                            return 1;
                                        })))
                        // Dev: recolour new trail motes, e.g. "tint FFC850 FF78B4"; "tint reset" restores the cyans.
                        .then(LiteralArgumentBuilder.<S>literal("tint")
                                .then(LiteralArgumentBuilder.<S>literal("reset").executes(ctx -> {
                                    grill24.fishtastic.client.FishSchoolClientRuntime.setMoteTintOverride(null);
                                    feedback.accept(ctx.getSource(), "School mote tints reset to the default cyans.");
                                    return 1;
                                }))
                                .then(RequiredArgumentBuilder.<S, String>argument("colors", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String[] parts = StringArgumentType.getString(ctx, "colors").trim().split("[\\s,]+");
                                            int[] tints = new int[parts.length];
                                            for (int i = 0; i < parts.length; i++) {
                                                String hex = parts[i].startsWith("#") ? parts[i].substring(1) : parts[i];
                                                if (!hex.matches("[0-9a-fA-F]{6}")) {
                                                    feedback.accept(ctx.getSource(), "Not an RRGGBB colour: " + parts[i]
                                                            + ". Try: tint FFC850 FF78B4");
                                                    return 0;
                                                }
                                                tints[i] = Integer.parseInt(hex, 16);
                                            }
                                            grill24.fishtastic.client.FishSchoolClientRuntime.setMoteTintOverride(tints);
                                            feedback.accept(ctx.getSource(), "New school motes now use " + tints.length
                                                    + " tint(s). 'tint reset' restores the defaults.");
                                            return 1;
                                        })))
                        // This client's lockstep copies: step against target, replays, fingerprint mismatches.
                        .then(LiteralArgumentBuilder.<S>literal("debug").executes(ctx -> {
                            feedback.accept(ctx.getSource(), grill24.fishtastic.client.FishSchoolClientRuntime.debugState());
                            return 1;
                        })))
                .then(LiteralArgumentBuilder.<S>literal("debug").executes(ctx -> {
                    feedback.accept(ctx.getSource(), ClientAmbientFish.natural().diagnostics().debugState());
                    feedback.accept(ctx.getSource(), AmbientContextClient.debugState());
                    return 1;
                })));
    }
}
