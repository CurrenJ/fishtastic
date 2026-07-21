package grill24.fishtastic.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import grill24.FishtasticRegistries;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.network.FishEncyclopediaSyncPacket;
import grill24.fishtastic.server.FishCatchSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;

/**
 * Debug command to toggle reveal of all encyclopedia fish entries and their details.
 * Usage:
 *   /fishtastic encyclopedia toggle           — toggle reveal for yourself
 *   /fishtastic encyclopedia toggle [player]  — toggle reveal for another player
 *
 * When all fish are hidden (any fish has 0 catches), sets every registered fish
 * species to a high catch count, fully revealing names, stats, types, spawn
 * conditions, lore, and removing silhouettes.  When all fish are already revealed,
 * resets all catch counts to 0, hiding everything.
 */
public class DebugEncyclopediaCommand {

    /** Catch count set when revealing — high enough to satisfy every unlock threshold. */
    private static final int REVEAL_CATCH_COUNT = 999;

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("encyclopedia")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("toggle")
                        .executes(ctx -> executeToggle(ctx, null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> executeToggle(ctx, EntityArgument.getPlayer(ctx, "player")))));
    }

    private static int executeToggle(CommandContext<CommandSourceStack> ctx, ServerPlayer explicit) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer target = resolveTarget(source, explicit);
        if (target == null) return 0;

        FishCatchSavedData data = FishCatchSavedData.getOrCreate(source.getServer());
        Registry<FishProfile> profileRegistry = source.getServer().registryAccess()
                .lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);

        // Build a quick lookup of current catch counts from saved data
        Map<net.minecraft.resources.Identifier, Integer> currentCounts = new HashMap<>();
        for (var e : data.getPersonalCatchCounts(target.getUUID(), FishCatchSavedData.PERSONAL_CATCH_COUNT_ASC)) {
            currentCounts.put(e.fishType(), e.totalCatches());
        }

        // Toggle: if any registered fish has 0 catches, reveal all; otherwise hide all
        boolean anyHidden = false;
        for (var entry : profileRegistry.entrySet()) {
            if (currentCounts.getOrDefault(entry.getKey().identifier(), 0) <= 0) {
                anyHidden = true;
                break;
            }
        }

        String playerName = target.getName().getString();
        int count = 0;
        if (anyHidden) {
            for (var entry : profileRegistry.entrySet()) {
                data.setCatchCount(target.getUUID(), playerName, entry.getKey().identifier(), REVEAL_CATCH_COUNT);
                count++;
            }
            int finalCount = count;
            source.sendSuccess(() -> Component.literal(
                    "Encyclopedia revealed for " + playerName + " — " + finalCount + " fish entries fully unlocked.")
                    .withStyle(ChatFormatting.GREEN), true);
        } else {
            for (var entry : profileRegistry.entrySet()) {
                data.setCatchCount(target.getUUID(), playerName, entry.getKey().identifier(), 0);
                count++;
            }
            int finalCount = count;
            source.sendSuccess(() -> Component.literal(
                    "Encyclopedia hidden for " + playerName + " — reset " + finalCount + " fish entries.")
                    .withStyle(ChatFormatting.GREEN), true);
        }

        FishEncyclopediaSyncPacket.sendToPlayer(target, data);
        return 1;
    }

    private static ServerPlayer resolveTarget(CommandSourceStack source, ServerPlayer explicit) {
        if (explicit != null) return explicit;
        if (source.getEntity() instanceof ServerPlayer sp) return sp;
        source.sendFailure(Component.literal("Specify a player or run as a player."));
        return null;
    }
}
