package grill24.fishtastic.gametest;

import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.config.FishtasticServerConfig;
import grill24.fishtastic.server.FishCatchBackups;
import grill24.fishtastic.server.FishCatchBackups.Entry;
import grill24.fishtastic.server.FishCatchBackups.Kind;
import grill24.fishtastic.server.FishCatchBackups.Pool;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.server.FishCatchSavedData.PlayerSummary;
import grill24.fishtastic.util.FishQualityHelper;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * End-to-end coverage of {@link FishCatchBackups} against the game-test server's real saved data
 * and world directory: encode → file → decode round-trips, the interval content-hash skip, both
 * restore scopes, retention on real files, the pre-command hook, the scheduler, and the
 * {@link FishCatchSavedData} replace/summary API that restore is built on.
 *
 * <p>Every test that touches the live {@link FishCatchSavedData} does so under a fresh random
 * player key and removes it again (via {@link FishCatchSavedData#replacePlayerFrom} from an empty
 * snapshot) so tests stay independent even though they share one server. Every backup a test
 * writes is deleted at the end.
 */
public final class FishCatchBackupsGameTests {

    private FishCatchBackupsGameTests() {}

    private static final Identifier BLUEGILL = Identifier.fromNamespaceAndPath("fishtastic", "bluegill");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    private static MinecraftServer server(GameTestHelper helper) {
        return helper.getLevel().getServer();
    }

    private static FishCatchSavedData live(GameTestHelper helper) {
        return FishCatchSavedData.getOrCreate(server(helper));
    }

    private static void removePlayer(GameTestHelper helper, UUID key) {
        live(helper).replacePlayerFrom(new FishCatchSavedData(), key);
    }

    private static ItemStack bluegill(float size, FishQuality.Quality quality) {
        ItemStack stack = new ItemStack(FishtasticItems.BLUEGILL.value());
        ItemSizeHelper.setSize(stack, size);
        FishQualityHelper.setQuality(stack, quality);
        return stack;
    }

    private static Entry createOrFail(GameTestHelper helper, Kind kind, String label) {
        try {
            Optional<Entry> e = FishCatchBackups.create(server(helper), kind, label);
            helper.assertTrue(e.isPresent(), "create(" + kind + ", " + label + ") wrote nothing");
            return e.get();
        } catch (IOException ex) {
            throw new AssertionError("create failed", ex);
        }
    }

    private static FishCatchSavedData loadOrFail(Entry e) {
        try {
            return FishCatchBackups.load(e);
        } catch (IOException ex) {
            throw new AssertionError("load failed", ex);
        }
    }

    private static PlayerSummary summaryOf(FishCatchSavedData data, UUID key) {
        return data.summarizePlayers().stream().filter(s -> s.key().equals(key)).findFirst().orElse(null);
    }

    private static long count(GameTestHelper helper, Pool pool, String label) {
        return FishCatchBackups.list(server(helper)).stream()
                .filter(e -> e.kind().pool == pool && (label == null || label.equals(e.label())))
                .count();
    }

    // -------------------------------------------------------------------------
    // create / load / list / find
    // -------------------------------------------------------------------------

    /** A manual backup lands in the world's backup dir, parses back, and decodes to the same data. */
    public static void manualBackupRoundTrips(GameTestHelper helper) {
        UUID key = UUID.randomUUID();
        live(helper).recordCatch(key, "RoundTrip", bluegill(33.5f, FishQuality.Quality.RARE));
        live(helper).recordCatch(key, "RoundTrip", bluegill(10f, FishQuality.Quality.COMMON));
        live(helper).getOrCreateQuestState(key).setTokenBalance(17);

        Entry entry = createOrFail(helper, Kind.MANUAL, "gt_roundtrip");
        try {
            helper.assertTrue(Files.isRegularFile(entry.path()), "backup file must exist");
            helper.assertTrue(entry.path().getParent().equals(FishCatchBackups.directory(server(helper))),
                    "backup must live in the backup directory");
            helper.assertTrue(entry.kind() == Kind.MANUAL, "kind");
            helper.assertTrue("gt_roundtrip".equals(entry.label()), "label");
            helper.assertTrue(FishCatchBackups.sizeBytes(entry) > 0, "non-empty file");
            helper.assertTrue(!Files.exists(entry.path().resolveSibling(entry.fileName() + ".tmp")), "temp file cleaned up");

            FishCatchSavedData decoded = loadOrFail(entry);
            helper.assertTrue(decoded.getCatchCount(key, BLUEGILL) == 2, "catch count survives round-trip");
            var best = decoded.getPersonalBestSizes(key, FishCatchSavedData.PERSONAL_BEST_SIZE_DESC);
            helper.assertTrue(best.size() == 1 && best.get(0).bestSize() == 33.5f
                    && best.get(0).bestQuality() == FishQuality.Quality.RARE, "best size/quality survive round-trip");
            helper.assertTrue(decoded.getOrCreateQuestState(key).getTokenBalance() == 17, "tokens survive round-trip");
            helper.assertTrue("RoundTrip".equals(decoded.displayNameForKey(key)), "name survives round-trip");

            helper.assertTrue(FishCatchBackups.find(server(helper), entry.fileName()).isPresent(), "find by file name");
            helper.assertTrue(FishCatchBackups.list(server(helper)).contains(entry), "list contains it");
        } finally {
            FishCatchBackups.delete(entry);
            removePlayer(helper, key);
        }
        helper.assertTrue(FishCatchBackups.find(server(helper), entry.fileName()).isEmpty(), "gone after delete");
        helper.succeed();
    }

    /** User-supplied labels are sanitised into the file-name grammar. */
    public static void labelIsSanitisedIntoFileName(GameTestHelper helper) {
        Entry entry = createOrFail(helper, Kind.MANUAL, "gt weird/../label!");
        try {
            String name = entry.fileName();
            helper.assertTrue(!name.contains("/") && !name.contains(" ") && !name.contains("!"), "unsafe chars stripped: " + name);
            helper.assertTrue(entry.label() != null && entry.label().startsWith("gt_weird"), "label kept recognisable: " + entry.label());
            helper.assertTrue(entry.path().getParent().equals(FishCatchBackups.directory(server(helper))),
                    "label cannot escape the backup directory");
        } finally {
            FishCatchBackups.delete(entry);
        }
        helper.succeed();
    }

    /** {@code find} never resolves outside the backup directory and ignores unknown names. */
    public static void findRejectsTraversalAndUnknownNames(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        helper.assertTrue(FishCatchBackups.find(server, "../level.dat").isEmpty(), "parent traversal");
        helper.assertTrue(FishCatchBackups.find(server, "..\\level.dat").isEmpty(), "windows traversal");
        helper.assertTrue(FishCatchBackups.find(server, "sub/20260101-000000-auto.dat").isEmpty(), "subdir");
        helper.assertTrue(FishCatchBackups.find(server, "20260101-000000-auto.dat").isEmpty(), "nonexistent");
        helper.succeed();
    }

    /** Listing is newest-first and skips files that aren't backups. */
    public static void listIsNewestFirstAndIgnoresJunk(GameTestHelper helper) throws IOException {
        Entry first = createOrFail(helper, Kind.MANUAL, "gt_order_a");
        Entry second = createOrFail(helper, Kind.MANUAL, "gt_order_b");
        Path junk = FishCatchBackups.directory(server(helper)).resolve("README.txt");
        Files.writeString(junk, "not a backup");
        try {
            List<Entry> all = FishCatchBackups.list(server(helper));
            int ia = all.indexOf(first), ib = all.indexOf(second);
            helper.assertTrue(ia >= 0 && ib >= 0, "both listed");
            helper.assertTrue(ib < ia, "second (newer) listed before first");
            helper.assertTrue(all.stream().noneMatch(e -> e.fileName().equals("README.txt")), "junk ignored");
        } finally {
            FishCatchBackups.delete(first);
            FishCatchBackups.delete(second);
            Files.deleteIfExists(junk);
        }
        helper.succeed();
    }

    /** A corrupt/foreign .dat with a valid name is reported as unreadable, not silently restored. */
    public static void loadRejectsFileWithoutDataCompound(GameTestHelper helper) throws IOException {
        Path dir = FishCatchBackups.directory(server(helper));
        Files.createDirectories(dir);
        Path bogus = dir.resolve("20200101-000000-manual-gt_bogus.dat");
        net.minecraft.nbt.NbtIo.writeCompressed(new net.minecraft.nbt.CompoundTag(), bogus);
        try {
            Entry entry = FishCatchBackups.find(server(helper), bogus.getFileName().toString()).orElseThrow();
            boolean threw = false;
            try {
                FishCatchBackups.load(entry);
            } catch (IOException expected) {
                threw = true;
            }
            helper.assertTrue(threw, "load must throw IOException for a file without a data compound");
        } finally {
            Files.deleteIfExists(bogus);
        }
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Interval content-hash skip
    // -------------------------------------------------------------------------

    /** Interval-pool snapshots are skipped while the data is unchanged and written again once it changes. */
    public static void intervalBackupSkipsIdenticalContent(GameTestHelper helper) throws IOException {
        MinecraftServer server = server(helper);
        UUID key = UUID.randomUUID();
        live(helper).setCatchCount(key, "HashSkip", BLUEGILL, 1);
        Entry a = createOrFail(helper, Kind.INTERVAL, null);
        Entry b = null;
        try {
            helper.assertTrue(FishCatchBackups.create(server, Kind.INTERVAL, null).isEmpty(),
                    "identical content must be skipped");
            helper.assertTrue(FishCatchBackups.create(server, Kind.START, null).isEmpty(),
                    "START shares the interval pool's skip");
            live(helper).setCatchCount(key, "HashSkip", BLUEGILL, 2);
            Optional<Entry> after = FishCatchBackups.create(server, Kind.INTERVAL, null);
            helper.assertTrue(after.isPresent(), "changed content must be written");
            b = after.get();
            // Manual backups never skip, even when identical.
            Entry manual = createOrFail(helper, Kind.MANUAL, "gt_hash_manual");
            FishCatchBackups.delete(manual);
        } finally {
            FishCatchBackups.delete(a);
            if (b != null) FishCatchBackups.delete(b);
            removePlayer(helper, key);
        }
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Restore
    // -------------------------------------------------------------------------

    /** Per-player restore reverts exactly one player and writes a pre-restore safety backup first. */
    public static void restorePlayerOnlyTouchesThatPlayer(GameTestHelper helper) throws IOException {
        MinecraftServer server = server(helper);
        FishCatchSavedData live = live(helper);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        live.recordCatch(a, "PlayerA", bluegill(20f, FishQuality.Quality.COMMON));
        live.recordCatch(b, "PlayerB", bluegill(25f, FishQuality.Quality.COMMON));
        live.getOrCreateQuestState(a).setTokenBalance(5);
        live.getOrCreateQuestState(b).setTokenBalance(7);

        Entry snapshot = createOrFail(helper, Kind.MANUAL, "gt_restore_player");
        List<Entry> before = FishCatchBackups.list(server);
        try {
            // "Cheat" both players.
            live.setCatchCount(a, "PlayerA", BLUEGILL, 500);
            live.setCatchCount(b, "PlayerB", BLUEGILL, 700);
            live.recordCatch(a, "PlayerA", bluegill(99f, FishQuality.Quality.LEGENDARY));
            live.getOrCreateQuestState(a).setTokenBalance(5000);
            live.getOrCreateQuestState(b).setTokenBalance(7000);

            FishCatchBackups.restorePlayer(server, snapshot, a);

            helper.assertTrue(live.getCatchCount(a, BLUEGILL) == 1, "A's catch count reverted, got " + live.getCatchCount(a, BLUEGILL));
            var bestA = live.getPersonalBestSizes(a, FishCatchSavedData.PERSONAL_BEST_SIZE_DESC);
            helper.assertTrue(bestA.get(0).bestSize() == 20f && bestA.get(0).bestQuality() == FishQuality.Quality.COMMON,
                    "A's best size/quality reverted");
            helper.assertTrue(live.getOrCreateQuestState(a).getTokenBalance() == 5, "A's tokens reverted");

            helper.assertTrue(live.getCatchCount(b, BLUEGILL) == 700, "B untouched (count)");
            helper.assertTrue(live.getOrCreateQuestState(b).getTokenBalance() == 7000, "B untouched (tokens)");

            List<Entry> after = FishCatchBackups.list(server);
            helper.assertTrue(after.stream().anyMatch(e -> e.kind() == Kind.PRE_RESTORE && !before.contains(e)),
                    "a fresh pre-restore backup was written");
            // Round-trip through the pre-restore backup: it must hold the cheated state.
            Entry pre = after.stream().filter(e -> e.kind() == Kind.PRE_RESTORE && !before.contains(e)).findFirst().orElseThrow();
            helper.assertTrue(loadOrFail(pre).getCatchCount(a, BLUEGILL) == 501, "pre-restore backup captured the pre-restore state");
            FishCatchBackups.delete(pre);
        } finally {
            FishCatchBackups.delete(snapshot);
            removePlayer(helper, a);
            removePlayer(helper, b);
        }
        helper.succeed();
    }

    /** Restoring a player who is absent from the backup removes them entirely. */
    public static void restorePlayerAbsentFromBackupRemovesThem(GameTestHelper helper) throws IOException {
        MinecraftServer server = server(helper);
        Entry snapshot = createOrFail(helper, Kind.MANUAL, "gt_restore_absent");
        List<Entry> before = FishCatchBackups.list(server);
        UUID key = UUID.randomUUID();
        try {
            live(helper).setCatchCount(key, "Newcomer", BLUEGILL, 3);
            live(helper).getOrCreateQuestState(key).setTokenBalance(9);
            helper.assertTrue(live(helper).hasPlayer(key), "present before restore");

            FishCatchBackups.restorePlayer(server, snapshot, key);
            helper.assertTrue(!live(helper).hasPlayer(key), "absent after restore");
            helper.assertTrue(live(helper).getCatchCount(key, BLUEGILL) == 0, "no catch count");
            helper.assertTrue(live(helper).getOrCreateQuestState(key).getTokenBalance() == 0, "no tokens");
        } finally {
            FishCatchBackups.list(server).stream().filter(e -> !before.contains(e)).forEach(FishCatchBackups::delete);
            FishCatchBackups.delete(snapshot);
            removePlayer(helper, key);
        }
        helper.succeed();
    }

    /** Per-player restore also reverts the player's cleanup-goal share and recomputes the goal total. */
    public static void restorePlayerRevertsCleanupContribution(GameTestHelper helper, Supplier<ServerPlayer> playerFactory) throws IOException {
        MinecraftServer server = server(helper);
        FishCatchSavedData live = live(helper);
        ServerPlayer player = playerFactory.get();
        UUID key = live.resolvePlayerKey(player);
        int baseTotal = live.getCleanupGoalTotal();

        live.recordTrashContribution(player, 3);
        Entry snapshot = createOrFail(helper, Kind.MANUAL, "gt_restore_cleanup");
        List<Entry> before = FishCatchBackups.list(server);
        try {
            live.recordTrashContribution(player, 4);
            helper.assertTrue(live.getCleanupGoalTotal() == baseTotal + 7, "contribution accumulated");

            FishCatchBackups.restorePlayer(server, snapshot, key);

            int share = live.getCleanupGoalContributors(FishCatchSavedData.GLOBAL_CATCH_COUNT_DESC).stream()
                    .filter(e -> e.playerUuid().equals(key)).mapToInt(FishCatchSavedData.GlobalCatchCountEntry::totalCatches).sum();
            helper.assertTrue(share == 3, "share reverted to 3, got " + share);
            helper.assertTrue(live.getCleanupGoalTotal() == baseTotal + 3, "goal total recomputed, got " + live.getCleanupGoalTotal());
        } finally {
            FishCatchBackups.list(server).stream().filter(e -> !before.contains(e)).forEach(FishCatchBackups::delete);
            FishCatchBackups.delete(snapshot);
            removePlayer(helper, key);
        }
        helper.succeed();
    }

    /** Whole-server restore replaces every record, dropping players added since the snapshot. */
    public static void restoreAllReplacesEverything(GameTestHelper helper) throws IOException {
        MinecraftServer server = server(helper);
        FishCatchSavedData live = live(helper);
        UUID keeper = UUID.randomUUID(), newcomer = UUID.randomUUID();
        live.setCatchCount(keeper, "Keeper", BLUEGILL, 4);

        Entry snapshot = createOrFail(helper, Kind.MANUAL, "gt_restore_all");
        List<Entry> before = FishCatchBackups.list(server);
        try {
            live.setCatchCount(keeper, "Keeper", BLUEGILL, 400);
            live.setCatchCount(newcomer, "Newcomer", BLUEGILL, 1);

            FishCatchBackups.restoreAll(server, snapshot);

            helper.assertTrue(live.getCatchCount(keeper, BLUEGILL) == 4, "keeper reverted");
            helper.assertTrue(!live.hasPlayer(newcomer), "newcomer removed");
            helper.assertTrue(FishCatchBackups.list(server).stream().anyMatch(e -> e.kind() == Kind.PRE_RESTORE && !before.contains(e)),
                    "pre-restore backup written");
            // The live object must be the same instance the server hands out (mutated in place, not swapped).
            helper.assertTrue(FishCatchSavedData.getOrCreate(server) == live, "live instance identity preserved");
            helper.assertTrue(live.isDirty(), "restore marks data dirty so vanilla persists it");
        } finally {
            FishCatchBackups.list(server).stream().filter(e -> !before.contains(e)).forEach(FishCatchBackups::delete);
            FishCatchBackups.delete(snapshot);
            removePlayer(helper, keeper);
            removePlayer(helper, newcomer);
        }
        helper.succeed();
    }

    /** Restored objects are deep copies: mutating the decoded snapshot afterwards can't leak into live data. */
    public static void restoreDoesNotAliasSnapshotObjects(GameTestHelper helper) {
        FishCatchSavedData live = new FishCatchSavedData();
        FishCatchSavedData snapshot = new FishCatchSavedData();
        UUID key = UUID.randomUUID();
        snapshot.setCatchCount(key, "Alias", BLUEGILL, 2);
        snapshot.getOrCreateQuestState(key).setTokenBalance(11);

        live.replacePlayerFrom(snapshot, key);
        snapshot.setCatchCount(key, "Alias", BLUEGILL, 999);
        snapshot.getOrCreateQuestState(key).setTokenBalance(999);
        helper.assertTrue(live.getCatchCount(key, BLUEGILL) == 2, "catch data deep-copied");
        helper.assertTrue(live.getOrCreateQuestState(key).getTokenBalance() == 11, "quest state deep-copied");

        FishCatchSavedData all = new FishCatchSavedData();
        all.replaceAllFrom(snapshot);
        snapshot.setCatchCount(key, "Alias", BLUEGILL, 5);
        helper.assertTrue(all.getCatchCount(key, BLUEGILL) == 999, "replaceAllFrom deep-copied");
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Summary / lookup API the command layer relies on
    // -------------------------------------------------------------------------

    public static void summaryAndLookupApi(GameTestHelper helper) {
        FishCatchSavedData data = new FishCatchSavedData();
        UUID fisher = UUID.randomUUID(), questOnly = UUID.randomUUID();
        data.recordCatch(fisher, "Fisher", bluegill(12f, FishQuality.Quality.COMMON));
        data.recordCatch(fisher, "Fisher", bluegill(14f, FishQuality.Quality.COMMON));
        data.recordCatch(fisher, "Fisher", bluegill(1f, FishQuality.Quality.COMMON));
        data.getOrCreateQuestState(questOnly).setTokenBalance(3);

        PlayerSummary f = summaryOf(data, fisher);
        helper.assertTrue(f != null && f.totalCatches() == 3 && f.speciesDiscovered() == 1
                && "Fisher".equals(f.name()) && f.tokenBalance() == 0, "fisher summary: " + f);
        PlayerSummary q = summaryOf(data, questOnly);
        helper.assertTrue(q != null && q.totalCatches() == 0 && q.tokenBalance() == 3, "quest-only player still summarised: " + q);

        helper.assertTrue(data.findKeyByName("fisher").orElseThrow().equals(fisher), "name lookup is case-insensitive");
        helper.assertTrue(data.findKeyByName("nobody").isEmpty(), "unknown name");
        helper.assertTrue(data.hasPlayer(questOnly), "hasPlayer sees quest-only keys");
        helper.assertTrue(!data.hasPlayer(UUID.randomUUID()), "hasPlayer false for strangers");
        helper.assertTrue(data.displayNameForKey(questOnly).equals(questOnly.toString()), "no-name key shows its UUID");
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Pre-command hook, retention on real files, scheduler
    // -------------------------------------------------------------------------

    /** Destructive commands' hook writes a PRE_COMMAND backup labelled with the command. */
    public static void preCommandHookWritesLabelledBackup(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        List<Entry> before = FishCatchBackups.list(server);
        FishCatchBackups.beforeDestructiveCommand(server, "gt_hook");
        List<Entry> added = FishCatchBackups.list(server).stream().filter(e -> !before.contains(e)).toList();
        try {
            helper.assertTrue(added.size() == 1, "exactly one backup written, got " + added.size());
            helper.assertTrue(added.get(0).kind() == Kind.PRE_COMMAND && "gt_hook".equals(added.get(0).label()),
                    "kind/label: " + added.get(0).fileName());
        } finally {
            added.forEach(FishCatchBackups::delete);
        }
        helper.succeed();
    }

    /** The pre-command pool is capped at the configured count, oldest dropped first. */
    public static void preCommandPoolIsCapped(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        int keep = FishtasticServerConfig.backupPreCommandKeep();
        List<Entry> before = FishCatchBackups.list(server);
        try {
            for (int i = 0; i < keep + 3; i++) {
                FishCatchBackups.beforeDestructiveCommand(server, "gt_cap");
            }
            long pool = count(helper, Pool.PRE_COMMAND, null);
            helper.assertTrue(pool == keep, "pre-command pool capped at " + keep + ", got " + pool);
            // Every survivor is one of ours (newest), since ours are newer than anything pre-existing.
            helper.assertTrue(count(helper, Pool.PRE_COMMAND, "gt_cap") == keep, "survivors are the newest");
        } finally {
            FishCatchBackups.list(server).stream().filter(e -> !before.contains(e)).forEach(FishCatchBackups::delete);
        }
        helper.succeed();
    }

    /** Retention actually deletes real files by their parsed timestamp, and never touches the manual pool. */
    public static void pruneDeletesAncientIntervalFilesOnDisk(GameTestHelper helper) throws IOException {
        MinecraftServer server = server(helper);
        Path dir = FishCatchBackups.directory(server);
        Files.createDirectories(dir);
        LocalDateTime now = LocalDateTime.now();
        int weeklyDays = FishtasticServerConfig.backupKeepWeeklyDays();

        Path ancientAuto = dir.resolve(STAMP.format(now.minusDays(weeklyDays + 5)) + "-auto.dat");
        Path ancientManual = dir.resolve(STAMP.format(now.minusDays(weeklyDays + 5)) + "-manual-gt_prune.dat");
        Path recentAuto = dir.resolve(STAMP.format(now.minusMinutes(5)) + "-auto.dat");
        // Real, loadable content so the files are indistinguishable from genuine backups.
        Entry template = createOrFail(helper, Kind.MANUAL, "gt_prune_template");
        Files.copy(template.path(), ancientAuto);
        Files.copy(template.path(), ancientManual);
        Files.copy(template.path(), recentAuto);
        try {
            FishCatchBackups.prune(server);
            helper.assertTrue(!Files.exists(ancientAuto), "ancient interval backup deleted");
            helper.assertTrue(Files.exists(ancientManual), "manual backup never pruned");
            helper.assertTrue(Files.exists(recentAuto), "recent interval backup kept");
        } finally {
            Files.deleteIfExists(ancientAuto);
            Files.deleteIfExists(ancientManual);
            Files.deleteIfExists(recentAuto);
            FishCatchBackups.delete(template);
        }
        helper.succeed();
    }

    /** First scheduler call on a server writes a START backup; the next after the interval writes INTERVAL. */
    public static void schedulerWritesStartThenInterval(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        int minutes = FishtasticServerConfig.backupIntervalMinutes();
        helper.assertTrue(FishtasticServerConfig.backupsEnabled() && minutes > 0,
                "test assumes default config (backups enabled, interval > 0)");
        UUID key = UUID.randomUUID();
        List<Entry> before = FishCatchBackups.list(server);
        long t0 = System.currentTimeMillis();
        try {
            live(helper).setCatchCount(key, "Sched", BLUEGILL, 1); // ensure content differs from any prior snapshot
            FishCatchBackups.resetSchedule();
            FishCatchBackups.runSchedule(server, t0);
            List<Entry> afterStart = FishCatchBackups.list(server).stream().filter(e -> !before.contains(e)).toList();
            helper.assertTrue(afterStart.size() == 1 && afterStart.get(0).kind() == Kind.START,
                    "start snapshot written on first tick, got " + afterStart);

            live(helper).setCatchCount(key, "Sched", BLUEGILL, 2);
            FishCatchBackups.runSchedule(server, t0 + (minutes - 1) * 60_000L);
            helper.assertTrue(FishCatchBackups.list(server).stream().filter(e -> !before.contains(e)).count() == 1,
                    "nothing written before the interval elapses");

            FishCatchBackups.runSchedule(server, t0 + (minutes + 1) * 60_000L);
            List<Entry> afterInterval = FishCatchBackups.list(server).stream().filter(e -> !before.contains(e)).toList();
            helper.assertTrue(afterInterval.size() == 2 && afterInterval.stream().anyMatch(e -> e.kind() == Kind.INTERVAL),
                    "interval snapshot written once the interval elapses, got " + afterInterval);

            // Unchanged data: the next interval is skipped by the content hash.
            FishCatchBackups.runSchedule(server, t0 + (2L * minutes + 2) * 60_000L);
            helper.assertTrue(FishCatchBackups.list(server).stream().filter(e -> !before.contains(e)).count() == 2,
                    "identical content skipped on the following interval");
        } finally {
            FishCatchBackups.list(server).stream().filter(e -> !before.contains(e)).forEach(FishCatchBackups::delete);
            removePlayer(helper, key);
            // Leave the scheduler tracking the live server as it was before the test.
            FishCatchBackups.resetSchedule();
            FishCatchBackups.runSchedule(server, System.currentTimeMillis());
            FishCatchBackups.list(server).stream().filter(e -> !before.contains(e)).forEach(FishCatchBackups::delete);
        }
        helper.succeed();
    }

    /** The server config file is materialised with defaults and reload() re-reads it. */
    public static void serverConfigWritesDefaultsAndReloads(GameTestHelper helper) {
        FishtasticServerConfig.reload();
        helper.assertTrue(FishtasticServerConfig.backupIntervalMinutes() >= 0, "interval non-negative");
        Path file = FishtasticServerConfig.getConfigDirectory().resolve("fishtastic-server.properties");
        helper.assertTrue(Files.isRegularFile(file), "config file written on first read: " + file);
        helper.assertTrue(FishtasticServerConfig.backupKeepAllHours() >= 0
                && FishtasticServerConfig.backupKeepSixHourlyDays() >= 0
                && FishtasticServerConfig.backupKeepDailyDays() >= 0
                && FishtasticServerConfig.backupKeepWeeklyDays() >= 0
                && FishtasticServerConfig.backupPreCommandKeep() >= 0, "all retention values non-negative");
        helper.succeed();
    }
}
