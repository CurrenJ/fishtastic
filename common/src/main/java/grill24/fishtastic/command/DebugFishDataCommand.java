package grill24.fishtastic.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import grill24.fishtastic.server.FishCatchSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

import static grill24.fishtastic.server.FishCatchSavedData.*;

/**
 * Debug command that dumps the top fish catch data for a player into chat.
 * Usage:
 *   /fishcatchdata            — show data for the executing player
 *   /fishcatchdata <player>   — show data for another player
 */
public class DebugFishDataCommand {

    private static final int MAX_ENTRIES = 5;

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("fishcatchdata")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> execute(ctx, null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> execute(ctx, EntityArgument.getPlayer(ctx, "player"))));
    }

    private static int execute(CommandContext<CommandSourceStack> ctx, ServerPlayer target) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        FishCatchSavedData db = FishCatchSavedData.getOrCreate(source.getServer());

        // Resolve target player for personal leaderboards
        ServerPlayer personalTarget = target;
        if (personalTarget == null && source.getEntity() instanceof ServerPlayer sp) {
            personalTarget = sp;
        }

        source.sendSuccess(() -> Component.literal("=== Fish Catch Debug Data ===")
                .withStyle(ChatFormatting.GOLD), false);

        if (personalTarget != null) {
            UUID uuid = personalTarget.getUUID();
            String name = personalTarget.getName().getString();

            // Personal best sizes
            sendHeader(source, "Personal Best Sizes: " + name, ChatFormatting.AQUA);
            List<PersonalBestSizeEntry> sizes = db.getPersonalBestSizes(uuid, PERSONAL_BEST_SIZE_DESC);
            if (sizes.isEmpty()) {
                sendGray(source, "(none)");
            } else {
                sizes.stream().limit(MAX_ENTRIES).forEach(e -> {
                    Component line = Component.literal(String.format("  %s  %.1f cm  [%s]",
                            e.fishType().getPath(), e.bestSize(), e.bestQuality().getSerializedName()))
                            .withStyle(ChatFormatting.WHITE);
                    source.sendSuccess(() -> line, false);
                });
            }

            // Personal catch counts
            sendHeader(source, "Personal Catch Counts: " + name, ChatFormatting.AQUA);
            List<PersonalCatchCountEntry> counts = db.getPersonalCatchCounts(uuid, PERSONAL_CATCH_COUNT_DESC);
            if (counts.isEmpty()) {
                sendGray(source, "(none)");
            } else {
                counts.stream().limit(MAX_ENTRIES).forEach(e -> {
                    Component line = Component.literal(String.format("  %s  x%d",
                            e.fishType().getPath(), e.totalCatches()))
                            .withStyle(ChatFormatting.WHITE);
                    source.sendSuccess(() -> line, false);
                });
            }
        } else {
            sendGray(source, "(run as a player or specify a target for personal data)");
        }

        // Global best sizes
        sendHeader(source, "Global Best Sizes (top " + MAX_ENTRIES + ")", ChatFormatting.YELLOW);
        List<GlobalBestSizeEntry> globalSizes = db.getGlobalBestSizes(GLOBAL_BEST_SIZE_DESC);
        if (globalSizes.isEmpty()) {
            sendGray(source, "(none)");
        } else {
            globalSizes.stream().limit(MAX_ENTRIES).forEach(e -> {
                Component line = Component.literal(String.format("  %s  %.1f cm  [%s]  by %s",
                        e.fishType().getPath(), e.bestSize(), e.bestQuality().getSerializedName(), e.playerName()))
                        .withStyle(ChatFormatting.WHITE);
                source.sendSuccess(() -> line, false);
            });
        }

        // Global catch counts
        sendHeader(source, "Global Catch Counts (top " + MAX_ENTRIES + ")", ChatFormatting.YELLOW);
        List<GlobalCatchCountEntry> globalCounts = db.getGlobalCatchCounts(GLOBAL_CATCH_COUNT_DESC);
        if (globalCounts.isEmpty()) {
            sendGray(source, "(none)");
        } else {
            globalCounts.stream().limit(MAX_ENTRIES).forEach(e -> {
                Component line = Component.literal(String.format("  %s  x%d total",
                        e.playerName(), e.totalCatches()))
                        .withStyle(ChatFormatting.WHITE);
                source.sendSuccess(() -> line, false);
            });
        }

        return 1;
    }

    private static void sendHeader(CommandSourceStack source, String text, ChatFormatting color) {
        source.sendSuccess(() -> Component.literal("-- " + text + " --").withStyle(color), false);
    }

    private static void sendGray(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal("  " + text).withStyle(ChatFormatting.GRAY), false);
    }
}
