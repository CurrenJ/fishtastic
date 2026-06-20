package grill24.fishtastic.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import grill24.FishtasticRegistries;
import grill24.fishtastic.data.FishProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

public class FishProfileCommand {

    private static final DynamicCommandExceptionType NOT_FOUND = new DynamicCommandExceptionType(
            id -> Component.literal("No fish profile registered for '" + id + "'"));

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("fishprofile")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("id", IdentifierArgument.id())
                                .suggests((ctx, builder) -> {
                                    Registry<FishProfile> registry = ctx.getSource().registryAccess()
                                            .lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);
                                    return SharedSuggestionProvider.suggest(
                                            registry.keySet().stream().map(Identifier::toString).toList(),
                                            builder);
                                })
                                .executes(FishProfileCommand::execute));
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Identifier id = IdentifierArgument.getId(ctx, "id");
        Registry<FishProfile> registry = ctx.getSource().registryAccess()
                .lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);

        ResourceKey<FishProfile> key = ResourceKey.create(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, id);
        FishProfile profile = registry.getOptional(key).orElseThrow(() -> NOT_FOUND.create(id));

        CommandSourceStack source = ctx.getSource();

        source.sendSuccess(() -> Component.literal("=== Fish Profile: " + id + " ===")
                .withStyle(ChatFormatting.GOLD), false);

        source.sendSuccess(() -> Component.literal(String.format("  base_weight : %d", profile.baseWeight()))
                .withStyle(ChatFormatting.WHITE), false);

        source.sendSuccess(() -> Component.literal(String.format("  size        : mean=%.1f cm, std_dev=%.1f cm",
                profile.size().mean(), profile.size().stdDev()))
                .withStyle(ChatFormatting.WHITE), false);

        if (!profile.biomeWeights().isEmpty()) {
            source.sendSuccess(() -> Component.literal("  biome_weights:")
                    .withStyle(ChatFormatting.AQUA), false);
            for (FishProfile.BiomeWeight bw : profile.biomeWeights()) {
                source.sendSuccess(() -> Component.literal(
                        String.format("    %-40s  %.2fx", bw.biome().location(), bw.multiplier()))
                        .withStyle(ChatFormatting.GRAY), false);
            }
        }

        if (!profile.timeWeights().isEmpty()) {
            source.sendSuccess(() -> Component.literal("  time_weights:")
                    .withStyle(ChatFormatting.AQUA), false);
            for (FishProfile.TimeWeight tw : profile.timeWeights()) {
                source.sendSuccess(() -> Component.literal(
                        String.format("    %-10s  %.2fx", tw.time().getSerializedName(), tw.multiplier()))
                        .withStyle(ChatFormatting.GRAY), false);
            }
        }

        if (!profile.weatherWeights().isEmpty()) {
            source.sendSuccess(() -> Component.literal("  weather_weights:")
                    .withStyle(ChatFormatting.AQUA), false);
            for (FishProfile.WeatherWeight ww : profile.weatherWeights()) {
                source.sendSuccess(() -> Component.literal(
                        String.format("    %-10s  %.2fx", ww.weather().getSerializedName(), ww.multiplier()))
                        .withStyle(ChatFormatting.GRAY), false);
            }
        }

        return 1;
    }
}
