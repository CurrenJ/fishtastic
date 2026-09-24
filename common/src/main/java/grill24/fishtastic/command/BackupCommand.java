package grill24.fishtastic.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.config.FishtasticServerConfig;
import grill24.fishtastic.server.FishCatchBackups;
import grill24.fishtastic.server.FishCatchBackups.Entry;
import grill24.fishtastic.server.FishCatchBackups.Kind;
import grill24.fishtastic.server.FishCatchBackups.Pool;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.server.FishCatchSavedData.PlayerSummary;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Admin interface to {@link FishCatchBackups}: snapshot, inspect, and roll back the fish catch /
 * quest saved data without touching files by hand.
 *
 * <pre>
 *   /fishtastic backup create [label]                      - manual snapshot (never auto-pruned)
 *   /fishtastic backup list                                - every backup, grouped by pool, newest first
 *   /fishtastic backup inspect &lt;file&gt; [player]               - per-player headline stats inside a backup
 *   /fishtastic backup restore &lt;file&gt; [confirm]              - replace ALL live data with the backup
 *   /fishtastic backup restore &lt;file&gt; player &lt;name&gt; [confirm] - replace one player's data only
 *   /fishtastic backup delete &lt;file&gt;
 *   /fishtastic backup prune                               - apply retention now, report what went
 *   /fishtastic backup reload                              - re-read fishtastic-server.properties
 * </pre>
 *
 * <p>Both restore forms are two-step: the first run prints what would change and arms a
 * confirmation for that exact file+scope; re-running with {@code confirm} within
 * {@link #CONFIRM_WINDOW_MS} executes it. A restore always writes a {@code prerestore} backup
 * first, so it is itself reversible.
 */
public class BackupCommand {

    private static final long CONFIRM_WINDOW_MS = 60_000L;

    private static final DynamicCommandExceptionType NO_SUCH_BACKUP = new DynamicCommandExceptionType(
            name -> Component.literal("No backup named '" + name + "' (see /fishtastic backup list)."));
    private static final DynamicCommandExceptionType NO_SUCH_PLAYER = new DynamicCommandExceptionType(
            name -> Component.literal("No player '" + name + "' in that backup or online (tab-complete lists the backup's players)."));

    /** Armed confirmations keyed by command source name; a restore is only executed against a matching arm. */
    private record PendingRestore(String fileName, @Nullable UUID playerKey, long armedAtMillis) {}
    private static final Map<String, PendingRestore> pending = new HashMap<>();

    private static final SuggestionProvider<CommandSourceStack> BACKUP_NAMES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    FishCatchBackups.list(ctx.getSource().getServer()).stream().map(Entry::fileName), builder);

    /** Player names recorded in the chosen backup, plus everyone currently online. */
    private static final SuggestionProvider<CommandSourceStack> BACKUP_PLAYER_NAMES = (ctx, builder) -> {
        MinecraftServer server = ctx.getSource().getServer();
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        String file = StringArgumentType.getString(ctx, "file");
        FishCatchBackups.find(server, file).ifPresent(entry -> {
            try {
                FishCatchBackups.load(entry).summarizePlayers().forEach(s -> names.add(s.name()));
            } catch (IOException ignored) {
                // Unreadable backup: fall through to online names only.
            }
        });
        server.getPlayerList().getPlayers().forEach(p -> names.add(p.getName().getString()));
        return SharedSuggestionProvider.suggest(names.stream(), builder);
    };

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("backup")
                .requires(FishtasticPermissions.gamemaster())
                .then(Commands.literal("create")
                        .executes(ctx -> executeCreate(ctx, null))
                        .then(Commands.argument("label", StringArgumentType.greedyString())
                                .executes(ctx -> executeCreate(ctx, StringArgumentType.getString(ctx, "label")))))
                .then(Commands.literal("list")
                        .executes(BackupCommand::executeList))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("file", StringArgumentType.string()).suggests(BACKUP_NAMES)
                                .executes(ctx -> executeInspect(ctx, null))
                                .then(Commands.argument("player", StringArgumentType.word()).suggests(BACKUP_PLAYER_NAMES)
                                        .executes(ctx -> executeInspect(ctx, StringArgumentType.getString(ctx, "player"))))))
                .then(Commands.literal("restore")
                        .then(Commands.argument("file", StringArgumentType.string()).suggests(BACKUP_NAMES)
                                .executes(ctx -> executeRestore(ctx, null, false))
                                .then(Commands.literal("confirm")
                                        .executes(ctx -> executeRestore(ctx, null, true)))
                                .then(Commands.literal("player")
                                        .then(Commands.argument("player", StringArgumentType.word()).suggests(BACKUP_PLAYER_NAMES)
                                                .executes(ctx -> executeRestore(ctx, StringArgumentType.getString(ctx, "player"), false))
                                                .then(Commands.literal("confirm")
                                                        .executes(ctx -> executeRestore(ctx, StringArgumentType.getString(ctx, "player"), true)))))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("file", StringArgumentType.string()).suggests(BACKUP_NAMES)
                                .executes(BackupCommand::executeDelete)))
                .then(Commands.literal("prune")
                        .executes(BackupCommand::executePrune))
                .then(Commands.literal("reload")
                        .executes(BackupCommand::executeReload));
    }

    // -------------------------------------------------------------------------
    // create / list / delete / prune / reload
    // -------------------------------------------------------------------------

    private static int executeCreate(CommandContext<CommandSourceStack> ctx, @Nullable String label) {
        CommandSourceStack source = ctx.getSource();
        try {
            Optional<Entry> entry = FishCatchBackups.create(source.getServer(), Kind.MANUAL, label);
            if (entry.isEmpty()) {
                source.sendFailure(Component.literal("Backup was not written."));
                return 0;
            }
            source.sendSuccess(() -> Component.literal("Wrote backup ")
                    .append(fileLink(entry.get()))
                    .append(Component.literal(" (" + formatSize(FishCatchBackups.sizeBytes(entry.get())) + ")"))
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        } catch (IOException e) {
            Fishtastic.LOGGER.error("Manual fish catch backup failed", e);
            source.sendFailure(Component.literal("Backup failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        List<Entry> all = FishCatchBackups.list(source.getServer());
        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No backups in " + FishCatchBackups.directory(source.getServer()))
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("=== Fish catch backups (" + all.size() + ") ===")
                .withStyle(ChatFormatting.GOLD), false);
        for (Pool pool : Pool.values()) {
            List<Entry> inPool = all.stream().filter(e -> e.kind().pool == pool).toList();
            if (inPool.isEmpty()) continue;
            source.sendSuccess(() -> Component.literal("-- " + poolTitle(pool) + " (" + inPool.size() + ") --")
                    .withStyle(ChatFormatting.AQUA), false);
            for (Entry e : inPool) {
                Component line = Component.literal("  ")
                        .append(fileLink(e))
                        .append(Component.literal("  " + e.age() + " ago").withStyle(ChatFormatting.GRAY));
                source.sendSuccess(() -> line, false);
            }
        }
        return all.size();
    }

    private static int executeDelete(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Entry entry = requireBackup(ctx);
        if (!FishCatchBackups.delete(entry)) {
            source.sendFailure(Component.literal("Could not delete " + entry.fileName()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Deleted " + entry.fileName()).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int executePrune(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        int deleted = FishCatchBackups.prune(source.getServer());
        source.sendSuccess(() -> Component.literal("Pruned " + deleted + " backup" + (deleted == 1 ? "" : "s") + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return deleted;
    }

    private static int executeReload(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        FishtasticServerConfig.reload();
        source.sendSuccess(() -> Component.literal(String.format(
                "Reloaded server config: backups %s, every %d min; keep all %dh / 6-hourly %dd / daily %dd / weekly %dd; pre-command keep %d.",
                FishtasticServerConfig.backupsEnabled() ? "enabled" : "disabled",
                FishtasticServerConfig.backupIntervalMinutes(),
                FishtasticServerConfig.backupKeepAllHours(),
                FishtasticServerConfig.backupKeepSixHourlyDays(),
                FishtasticServerConfig.backupKeepDailyDays(),
                FishtasticServerConfig.backupKeepWeeklyDays(),
                FishtasticServerConfig.backupPreCommandKeep()))
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // -------------------------------------------------------------------------
    // inspect
    // -------------------------------------------------------------------------

    private static int executeInspect(CommandContext<CommandSourceStack> ctx, @Nullable String playerName) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Entry entry = requireBackup(ctx);
        FishCatchSavedData snapshot = loadOrFail(source, entry);
        if (snapshot == null) return 0;

        source.sendSuccess(() -> Component.literal("=== " + entry.fileName() + " (" + entry.age() + " ago) ===")
                .withStyle(ChatFormatting.GOLD), false);

        if (playerName == null) {
            List<PlayerSummary> players = snapshot.summarizePlayers();
            if (players.isEmpty()) {
                source.sendSuccess(() -> Component.literal("  (no players)").withStyle(ChatFormatting.GRAY), false);
                return 1;
            }
            for (PlayerSummary s : players) {
                source.sendSuccess(() -> summaryLine(s), false);
            }
            source.sendSuccess(() -> Component.literal("  cleanup goal total: " + snapshot.getCleanupGoalTotal())
                    .withStyle(ChatFormatting.GRAY), false);
            return players.size();
        }

        UUID key = resolvePlayerKey(source.getServer(), snapshot, playerName);
        if (key == null) throw NO_SUCH_PLAYER.create(playerName);

        FishCatchSavedData live = FishCatchSavedData.getOrCreate(source.getServer());
        PlayerSummary inBackup = snapshot.summarizePlayers().stream().filter(s -> s.key().equals(key)).findFirst().orElse(null);
        PlayerSummary inLive = live.summarizePlayers().stream().filter(s -> s.key().equals(key)).findFirst().orElse(null);

        source.sendSuccess(() -> Component.literal("-- in backup --").withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> inBackup != null ? summaryLine(inBackup)
                : Component.literal("  (not present - restoring would remove this player's data)").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("-- live now --").withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> inLive != null ? summaryLine(inLive)
                : Component.literal("  (not present)").withStyle(ChatFormatting.GRAY), false);

        if (inBackup != null) {
            source.sendSuccess(() -> Component.literal("-- per-species catches in backup --").withStyle(ChatFormatting.AQUA), false);
            snapshot.getPersonalCatchCounts(key, FishCatchSavedData.PERSONAL_CATCH_COUNT_DESC).forEach(e ->
                    source.sendSuccess(() -> Component.literal("  " + e.fishType().getPath() + "  x" + e.totalCatches())
                            .withStyle(ChatFormatting.WHITE), false));
        }
        return 1;
    }

    // -------------------------------------------------------------------------
    // restore
    // -------------------------------------------------------------------------

    private static int executeRestore(CommandContext<CommandSourceStack> ctx, @Nullable String playerName, boolean confirm)
            throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        Entry entry = requireBackup(ctx);
        FishCatchSavedData snapshot = loadOrFail(source, entry);
        if (snapshot == null) return 0;

        UUID key = null;
        if (playerName != null) {
            key = resolvePlayerKey(server, snapshot, playerName);
            if (key == null) throw NO_SUCH_PLAYER.create(playerName);
        }

        String armKey = source.getTextName();
        String scope = playerName != null ? "player " + playerName : "ALL players";

        if (!confirm) {
            // Arm, and show what would change so the admin can sanity-check the target.
            pending.put(armKey, new PendingRestore(entry.fileName(), key, System.currentTimeMillis()));
            FishCatchSavedData live = FishCatchSavedData.getOrCreate(server);
            source.sendSuccess(() -> Component.literal("Restore " + scope + " from " + entry.fileName()
                    + " (" + entry.age() + " ago)?").withStyle(ChatFormatting.YELLOW), false);
            if (key != null) {
                UUID k = key;
                PlayerSummary before = live.summarizePlayers().stream().filter(s -> s.key().equals(k)).findFirst().orElse(null);
                PlayerSummary after = snapshot.summarizePlayers().stream().filter(s -> s.key().equals(k)).findFirst().orElse(null);
                source.sendSuccess(() -> Component.literal("  now:    ").withStyle(ChatFormatting.GRAY)
                        .append(before != null ? summaryLine(before) : Component.literal("(no data)")), false);
                source.sendSuccess(() -> Component.literal("  after:  ").withStyle(ChatFormatting.GRAY)
                        .append(after != null ? summaryLine(after) : Component.literal("(no data - player will be removed)")), false);
            } else {
                int livePlayers = live.summarizePlayers().size();
                int backupPlayers = snapshot.summarizePlayers().size();
                source.sendSuccess(() -> Component.literal("  " + livePlayers + " player(s) live now -> "
                        + backupPlayers + " player(s) in backup. Every player's catch history, quests, tokens and purchases will be replaced.")
                        .withStyle(ChatFormatting.GRAY), false);
            }
            String confirmCmd = "/fishtastic backup restore \"" + entry.fileName() + "\""
                    + (playerName != null ? " player " + playerName : "") + " confirm";
            source.sendSuccess(() -> Component.literal("  A pre-restore backup is taken first. Within 60s, run: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(confirmCmd).withStyle(style -> style
                            .withColor(ChatFormatting.AQUA)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent.SuggestCommand(confirmCmd)))), false);
            return 1;
        }

        PendingRestore armed = pending.get(armKey);
        boolean matches = armed != null
                && armed.fileName().equals(entry.fileName())
                && java.util.Objects.equals(armed.playerKey(), key)
                && System.currentTimeMillis() - armed.armedAtMillis() <= CONFIRM_WINDOW_MS;
        if (!matches) {
            source.sendFailure(Component.literal("Nothing armed for that restore (or it expired). Run the command without 'confirm' first."));
            return 0;
        }
        pending.remove(armKey);

        try {
            if (key == null) {
                FishCatchBackups.restoreAll(server, entry);
            } else {
                FishCatchBackups.restorePlayer(server, entry, key);
            }
        } catch (IOException e) {
            Fishtastic.LOGGER.error("Restore from {} failed", entry.fileName(), e);
            source.sendFailure(Component.literal("Restore failed: " + e.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Restored " + scope + " from " + entry.fileName() + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static Entry requireBackup(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "file");
        return FishCatchBackups.find(ctx.getSource().getServer(), name).orElseThrow(() -> NO_SUCH_BACKUP.create(name));
    }

    @Nullable
    private static FishCatchSavedData loadOrFail(CommandSourceStack source, Entry entry) {
        try {
            return FishCatchBackups.load(entry);
        } catch (IOException e) {
            Fishtastic.LOGGER.error("Could not read backup {}", entry.fileName(), e);
            source.sendFailure(Component.literal("Could not read " + entry.fileName() + ": " + e.getMessage()));
            return null;
        }
    }

    /**
     * Turns a typed name into a saved-data key: a raw UUID, a name recorded in the backup, an
     * online player (through {@link FishCatchSavedData#resolvePlayerKey} so the singleplayer
     * sentinel is honoured), or a name recorded in the live data - in that order.
     */
    @Nullable
    private static UUID resolvePlayerKey(MinecraftServer server, FishCatchSavedData snapshot, String name) {
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException ignored) {
            // not a UUID
        }
        Optional<UUID> fromBackup = snapshot.findKeyByName(name);
        if (fromBackup.isPresent()) return fromBackup.get();
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        FishCatchSavedData live = FishCatchSavedData.getOrCreate(server);
        if (online != null) return live.resolvePlayerKey(online);
        return live.findKeyByName(name).orElse(null);
    }

    private static Component summaryLine(PlayerSummary s) {
        return Component.literal(String.format("  %s  %d catches / %d species, %d quests done (%d claimed), %d tokens",
                s.name(), s.totalCatches(), s.speciesDiscovered(), s.questsCompleted(), s.questsClaimed(), s.tokenBalance()))
                .withStyle(ChatFormatting.WHITE);
    }

    private static Component fileLink(Entry e) {
        String inspect = "/fishtastic backup inspect \"" + e.fileName() + "\"";
        return Component.literal(e.fileName()).withStyle(style -> style
                .withColor(ChatFormatting.WHITE)
                .withClickEvent(new ClickEvent.SuggestCommand(inspect))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to inspect"))));
    }

    private static String poolTitle(Pool pool) {
        return switch (pool) {
            case INTERVAL -> "Automatic (interval + server start, tiered retention)";
            case PRE_COMMAND -> "Pre-command (newest " + FishtasticServerConfig.backupPreCommandKeep() + " kept)";
            case MANUAL -> "Manual + pre-restore (never pruned)";
        };
    }

    private static String formatSize(long bytes) {
        if (bytes < 0) return "?";
        if (bytes < 1024) return bytes + " B";
        return String.format("%.1f KB", bytes / 1024.0);
    }
}
