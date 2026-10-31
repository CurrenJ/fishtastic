package grill24.fishtastic.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import grill24.FishtasticRegistries;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.network.FishEncyclopediaSyncPacket;
import grill24.fishtastic.server.FishCatchBackups;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.server.FishObservation;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Solo-playtest triggers for spyglass observation (docs/world-fish-exploration-implementation-plan.md, Step 5).
 *
 * <pre>
 * /fishtastic observe &lt;species&gt;   marks a species observed for you, as if you had held a spyglass on it (toast included)
 * /fishtastic observe clear      forgets all of your observations (takes a pre-command backup first)
 * </pre>
 */
public class ObserveCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("observe")
                .requires(FishtasticPermissions.gamemaster())
                .then(Commands.literal("clear").executes(ObserveCommand::clear))
                .then(Commands.argument("species", ResourceLocationArgument.id())
                        .suggests((ctx, builder) -> {
                            Registry<FishProfile> registry = ctx.getSource().registryAccess()
                                    .registryOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);
                            return SharedSuggestionProvider.suggest(
                                    registry.keySet().stream().map(ResourceLocation::toString).toList(), builder);
                        })
                        .executes(ObserveCommand::observe));
    }

    private static int observe(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        ResourceLocation species = ResourceLocationArgument.getId(ctx, "species");
        if (!FishObservation.isObservableSpecies(source.getLevel(), species)) {
            source.sendFailure(Component.literal("'" + species + "' is not an observable fish species."));
            return 0;
        }
        FishCatchSavedData data = FishCatchSavedData.getOrCreate(source.getServer());
        boolean added = FishObservation.grant(player, data, species);
        source.sendSuccess(() -> Component.literal(added
                ? "Observed " + species + "."
                : "You had already observed " + species + ".").withStyle(ChatFormatting.GREEN), false);
        return added ? 1 : 0;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        FishCatchSavedData data = FishCatchSavedData.getOrCreate(source.getServer());
        FishCatchBackups.beforeDestructiveCommand(source.getServer(), "observe_clear");
        int cleared = data.clearObserved(data.resolvePlayerKey(player));
        FishEncyclopediaSyncPacket.sendToPlayer(player, data);
        source.sendSuccess(() -> Component.literal("Forgot " + cleared + " observed species.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
