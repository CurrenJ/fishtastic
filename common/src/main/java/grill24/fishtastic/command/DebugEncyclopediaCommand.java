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

/**
 * Debug command to bulk-reveal or reset all encyclopedia fish entries.
 * Usage:
 *   /fishtastic encyclopedia complete [player]   — reveal all fish entries
 *   /fishtastic encyclopedia reset [player]      — hide all fish entries
 */
public class DebugEncyclopediaCommand {

    /** Catch count set when revealing — high enough to satisfy every unlock threshold. */
    private static final int REVEAL_CATCH_COUNT = 999;

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("encyclopedia")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("complete")
                        .executes(ctx -> executeReveal(ctx, null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> executeReveal(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("reset")
                        .executes(ctx -> executeReset(ctx, null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> executeReset(ctx, EntityArgument.getPlayer(ctx, "player")))));
    }

    private static int executeReveal(CommandContext<CommandSourceStack> ctx, ServerPlayer explicit) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer target = resolveTarget(source, explicit);
        if (target == null) return 0;

        FishCatchSavedData data = FishCatchSavedData.getOrCreate(source.getServer());
        Registry<FishProfile> profileRegistry = source.getServer().registryAccess()
                .lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);

        String playerName = target.getName().getString();
        java.util.UUID key = data.resolvePlayerKey(target);
        for (var entry : profileRegistry.entrySet()) {
            data.setCatchCount(key, playerName, entry.getKey().identifier(), REVEAL_CATCH_COUNT);
        }

        int count = profileRegistry.size();
        FishEncyclopediaSyncPacket.sendToPlayer(target, data);

        int finalCount = count;
        source.sendSuccess(() -> Component.literal(
                "Encyclopedia revealed for " + playerName + " — " + finalCount + " fish entries fully unlocked.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int executeReset(CommandContext<CommandSourceStack> ctx, ServerPlayer explicit) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer target = resolveTarget(source, explicit);
        if (target == null) return 0;

        FishCatchSavedData data = FishCatchSavedData.getOrCreate(source.getServer());
        Registry<FishProfile> profileRegistry = source.getServer().registryAccess()
                .lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);

        String playerName = target.getName().getString();
        java.util.UUID key = data.resolvePlayerKey(target);
        for (var entry : profileRegistry.entrySet()) {
            data.setCatchCount(key, playerName, entry.getKey().identifier(), 0);
        }

        int count = profileRegistry.size();
        FishEncyclopediaSyncPacket.sendToPlayer(target, data);

        int finalCount = count;
        source.sendSuccess(() -> Component.literal(
                "Encyclopedia hidden for " + playerName + " — reset " + finalCount + " fish entries.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static ServerPlayer resolveTarget(CommandSourceStack source, ServerPlayer explicit) {
        if (explicit != null) return explicit;
        if (source.getEntity() instanceof ServerPlayer sp) return sp;
        source.sendFailure(Component.literal("Specify a player or run as a player."));
        return null;
    }
}
