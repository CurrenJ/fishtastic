package grill24.fishtastic.server;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.config.FishtasticServerConfig;
import grill24.fishtastic.network.FishEncyclopediaSyncPacket;
import grill24.fishtastic.network.QuestSyncPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Point-in-time snapshots of {@link FishCatchSavedData} - the mod's entire per-player progression
 * (catch history, quests, tokens, shop purchases, tutorial state, cleanup goal) - written to
 * {@code <world>/data/fishtastic_backups/} so an admin can roll a player (or the whole server)
 * back after a bad command, a bug, or a cheat.
 *
 * <p>Snapshots are encoded from the <em>live</em> object via its codec, not copied from the
 * {@code .dat} on disk: vanilla only flushes saved data on autosave, so the file can lag the true
 * state by minutes.
 *
 * <p>Five kinds of backup, in three retention pools (all tunable in {@code fishtastic-server.properties}):
 * <ul>
 *   <li>{@link Kind#INTERVAL} / {@link Kind#START} - automatic, on a real-time timer and once at
 *       server start. Thinned by age (see {@link #pruneInterval}) so recent history is dense while
 *       a bounded file count still reaches months back. A snapshot whose contents are identical to
 *       the newest one is skipped.</li>
 *   <li>{@link Kind#PRE_COMMAND} - automatic, taken by destructive admin commands right before
 *       they mutate anything. Newest N kept.</li>
 *   <li>{@link Kind#MANUAL} / {@link Kind#PRE_RESTORE} - explicit {@code /fishtastic backup create},
 *       and the safety snapshot every restore takes first. Never pruned.</li>
 * </ul>
 *
 * <p>File name is the sole source of a backup's identity: {@code yyyyMMdd-HHmmss-<kind>[-label].dat}.
 * The timestamp is local server time. Inside is a compressed compound with a {@code data} child
 * holding the codec-encoded saved data plus a little metadata.
 */
public final class FishCatchBackups {

    public enum Kind {
        INTERVAL("auto", Pool.INTERVAL),
        START("start", Pool.INTERVAL),
        PRE_COMMAND("pre", Pool.PRE_COMMAND),
        MANUAL("manual", Pool.MANUAL),
        PRE_RESTORE("prerestore", Pool.MANUAL);

        final String token;
        public final Pool pool;

        Kind(String token, Pool pool) {
            this.token = token;
            this.pool = pool;
        }

        static Optional<Kind> fromToken(String token) {
            return Arrays.stream(values()).filter(k -> k.token.equals(token)).findFirst();
        }
    }

    public enum Pool { INTERVAL, PRE_COMMAND, MANUAL }

    /** One backup file, parsed from its name. {@code label} is the free-form suffix, if any. */
    public record Entry(Path path, LocalDateTime time, Kind kind, @Nullable String label) {
        public String fileName() {
            return path.getFileName().toString();
        }

        public Instant instant() {
            return time.atZone(ZoneId.systemDefault()).toInstant();
        }

        /** Human-readable age relative to now, e.g. "3h 12m" or "5d 4h". */
        public String age() {
            Duration d = Duration.between(instant(), Instant.now());
            long days = d.toDays();
            long hours = d.toHoursPart();
            long mins = d.toMinutesPart();
            if (days > 0) return days + "d " + hours + "h";
            if (hours > 0) return hours + "h " + mins + "m";
            return mins + "m";
        }
    }

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);
    private static final Pattern NAME = Pattern.compile(
            "^(\\d{8}-\\d{6})-(auto|start|manual|prerestore|pre)(?:-([A-Za-z0-9_.]+))?\\.dat$");
    private static final Pattern LABEL_CHARS = Pattern.compile("[^A-Za-z0-9_.]+");
    private static final int MAX_LABEL_LENGTH = 40;
    private static final String DATA_KEY = "data";
    private static final int TICK_CHECK_INTERVAL = 20;

    // The file-name timestamp only has second resolution, so two backups created within the same
    // second (e.g. a pre-command snapshot immediately followed by a manual one) tie on Entry::time
    // alone; break the tie with the file's actual last-modified time, which is far finer-grained
    // and reliably reflects real creation order.
    private static final Comparator<Entry> NEWEST_FIRST = Comparator
            .comparing(Entry::time)
            .thenComparing(e -> lastModifiedInstant(e.path()))
            .reversed();

    // Per-server-instance timer state. Singleplayer creates a fresh MinecraftServer per world, so
    // tracking the last-seen instance doubles as our "server started" detection.
    private static MinecraftServer trackedServer;
    private static long lastIntervalBackupMillis;
    @Nullable private static String lastIntervalContentHash;

    private FishCatchBackups() {}

    // -------------------------------------------------------------------------
    // Scheduling
    // -------------------------------------------------------------------------

    /** Called every server tick; runs the start-of-server and interval backups on schedule. */
    public static void tick(MinecraftServer server) {
        if (server == trackedServer && server.getTickCount() % TICK_CHECK_INTERVAL != 0) return;
        runSchedule(server, System.currentTimeMillis());
    }

    /**
     * The scheduler proper, with the wall clock injected so tests can drive it. The first call
     * for a given server instance is treated as "server started": it arms the interval timer and
     * writes a {@link Kind#START} snapshot. Later calls write an {@link Kind#INTERVAL} snapshot
     * once {@code backups.intervalMinutes} have elapsed since the previous automatic one.
     */
    public static void runSchedule(MinecraftServer server, long nowMillis) {
        if (server != trackedServer) {
            trackedServer = server;
            lastIntervalBackupMillis = nowMillis;
            lastIntervalContentHash = null;
            if (FishtasticServerConfig.backupsEnabled()) {
                createAutomatic(server, Kind.START, null);
            }
            return;
        }
        if (!FishtasticServerConfig.backupsEnabled()) return;

        int minutes = FishtasticServerConfig.backupIntervalMinutes();
        if (minutes <= 0) return;
        if (nowMillis - lastIntervalBackupMillis < minutes * 60_000L) return;
        lastIntervalBackupMillis = nowMillis;
        createAutomatic(server, Kind.INTERVAL, null);
    }

    /** Forgets the tracked server so the next {@link #runSchedule} behaves like a server start. Test hook. */
    public static void resetSchedule() {
        trackedServer = null;
        lastIntervalContentHash = null;
    }

    /**
     * Snapshot hook for destructive admin commands: call right before mutating progression. Cheap
     * enough to run unconditionally; a no-op when automatic backups are disabled.
     *
     * @param commandName short token identifying the caller, becomes the file's label
     */
    public static void beforeDestructiveCommand(MinecraftServer server, String commandName) {
        if (!FishtasticServerConfig.backupsEnabled()) return;
        createAutomatic(server, Kind.PRE_COMMAND, commandName);
    }

    private static void createAutomatic(MinecraftServer server, Kind kind, @Nullable String label) {
        try {
            Optional<Entry> written = create(server, kind, label);
            written.ifPresent(e -> Fishtastic.LOGGER.info("Wrote fish catch backup {}", e.fileName()));
            prune(server);
        } catch (IOException e) {
            Fishtastic.LOGGER.error("Failed to write automatic fish catch backup ({})", kind, e);
        }
    }

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    /**
     * Writes a snapshot of the live saved data. For the interval pool, returns empty (and writes
     * nothing) when the contents match the most recent interval backup.
     */
    public static Optional<Entry> create(MinecraftServer server, Kind kind, @Nullable String label) throws IOException {
        FishCatchSavedData live = FishCatchSavedData.getOrCreate(server);
        CompoundTag data = encode(live);

        if (kind.pool == Pool.INTERVAL) {
            String hash = contentHash(data);
            if (lastIntervalContentHash == null) {
                lastIntervalContentHash = newestOf(server, Pool.INTERVAL).map(FishCatchBackups::hashOfFile).orElse(null);
            }
            if (hash.equals(lastIntervalContentHash)) {
                return Optional.empty();
            }
            lastIntervalContentHash = hash;
        }

        CompoundTag root = new CompoundTag();
        root.putString("mod", "fishtastic");
        root.putString("kind", kind.name());
        root.putLong("created_epoch_ms", System.currentTimeMillis());
        if (label != null) root.putString("label", label);
        root.put(DATA_KEY, data);

        Path dir = directory(server);
        Files.createDirectories(dir);
        LocalDateTime now = LocalDateTime.now();
        String cleanLabel = sanitizeLabel(label);
        Path target = uniquePath(dir, now, kind, cleanLabel);

        // Write to a sibling temp file first, then move: a crash mid-write can't leave a
        // truncated .dat that later looks like a valid (empty) backup.
        Path tmp = dir.resolve(target.getFileName() + ".tmp");
        NbtIo.writeCompressed(root, tmp);
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return parse(target);
    }

    static Path uniquePath(Path dir, LocalDateTime time, Kind kind, @Nullable String label) {
        String suffix = label != null ? "-" + label : "";
        Path p = dir.resolve(STAMP.format(time) + "-" + kind.token + suffix + ".dat");
        // Two snapshots in the same second (e.g. pre-command then manual) - bump the seconds so
        // the name stays unique and still parses.
        int bump = 1;
        while (Files.exists(p)) {
            p = dir.resolve(STAMP.format(time.plusSeconds(bump++)) + "-" + kind.token + suffix + ".dat");
        }
        return p;
    }

    @Nullable
    static String sanitizeLabel(@Nullable String label) {
        if (label == null) return null;
        String clean = LABEL_CHARS.matcher(label.trim()).replaceAll("_");
        if (clean.length() > MAX_LABEL_LENGTH) clean = clean.substring(0, MAX_LABEL_LENGTH);
        clean = clean.replaceAll("^_+|_+$", "");
        return clean.isEmpty() ? null : clean;
    }

    private static CompoundTag encode(FishCatchSavedData data) {
        Tag tag = FishCatchSavedData.CODEC.encodeStart(NbtOps.INSTANCE, data)
                .getOrThrow(msg -> new IllegalStateException("Failed to encode fish catch data: " + msg));
        if (!(tag instanceof CompoundTag compound)) {
            throw new IllegalStateException("Fish catch data did not encode to a compound");
        }
        return compound;
    }

    private static String contentHash(CompoundTag data) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.write(data, new DataOutputStream(bytes));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException e) {
            // Unhashable: never equal to anything, so we fall back to always writing.
            return UUID.randomUUID().toString();
        }
    }

    @Nullable
    private static String hashOfFile(Entry entry) {
        try {
            return contentHash(readDataTag(entry));
        } catch (IOException e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // List / read
    // -------------------------------------------------------------------------

    public static Path directory(MinecraftServer server) {
        // data/fishtastic_backups. LevelResource's constructor is private before 1.21.2.
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("fishtastic_backups");
    }

    /** Every parseable backup, newest first. Unrecognised files in the directory are ignored. */
    public static List<Entry> list(MinecraftServer server) {
        Path dir = directory(server);
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(FishCatchBackups::parse)
                    .flatMap(Optional::stream)
                    .sorted(NEWEST_FIRST)
                    .toList();
        } catch (IOException e) {
            Fishtastic.LOGGER.warn("Could not list fish catch backups in {}", dir, e);
            return List.of();
        }
    }

    public static Optional<Entry> find(MinecraftServer server, String fileName) {
        // Names come from user input; only ever resolve inside the backup directory.
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) return Optional.empty();
        Path p = directory(server).resolve(fileName);
        if (!Files.isRegularFile(p)) return Optional.empty();
        return parse(p);
    }

    private static Optional<Entry> newestOf(MinecraftServer server, Pool pool) {
        return list(server).stream().filter(e -> e.kind().pool == pool).findFirst();
    }

    static Optional<Entry> parse(Path path) {
        Matcher m = NAME.matcher(path.getFileName().toString());
        if (!m.matches()) return Optional.empty();
        Optional<Kind> kind = Kind.fromToken(m.group(2));
        if (kind.isEmpty()) return Optional.empty();
        try {
            LocalDateTime time = LocalDateTime.parse(m.group(1), STAMP);
            return Optional.of(new Entry(path, time, kind.get(), m.group(3)));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static CompoundTag readDataTag(Entry entry) throws IOException {
        CompoundTag root = NbtIo.readCompressed(entry.path(), NbtAccounter.unlimitedHeap());
        if (!root.contains(DATA_KEY, Tag.TAG_COMPOUND)) {
            throw new IOException(entry.fileName() + " has no '" + DATA_KEY + "' compound - not a fishtastic backup");
        }
        return root.getCompound(DATA_KEY);
    }

    /** Decodes a backup into a detached (not registered, never auto-saved) saved-data instance. */
    public static FishCatchSavedData load(Entry entry) throws IOException {
        CompoundTag data = readDataTag(entry);
        return FishCatchSavedData.CODEC.parse(NbtOps.INSTANCE, data)
                .getOrThrow(msg -> new IOException("Failed to decode " + entry.fileName() + ": " + msg));
    }

    public static long sizeBytes(Entry entry) {
        try {
            return Files.size(entry.path());
        } catch (IOException e) {
            return -1;
        }
    }

    public static boolean delete(Entry entry) {
        try {
            return Files.deleteIfExists(entry.path());
        } catch (IOException e) {
            Fishtastic.LOGGER.warn("Could not delete fish catch backup {}", entry.fileName(), e);
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Restore
    // -------------------------------------------------------------------------

    /**
     * Replaces the whole live dataset with {@code entry}'s. Takes a {@link Kind#PRE_RESTORE}
     * snapshot first so even a restore is reversible, then re-syncs every online player.
     */
    public static void restoreAll(MinecraftServer server, Entry entry) throws IOException {
        FishCatchSavedData snapshot = load(entry);
        create(server, Kind.PRE_RESTORE, null);
        FishCatchSavedData live = FishCatchSavedData.getOrCreate(server);
        live.replaceAllFrom(snapshot);
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            sync(online, live);
        }
        Fishtastic.LOGGER.warn("Restored ALL fish catch data from backup {}", entry.fileName());
    }

    /**
     * Replaces only {@code key}'s records with those in {@code entry} (a key missing from the
     * backup is removed from the live data). Same pre-restore snapshot and re-sync as
     * {@link #restoreAll}, scoped to that one player.
     */
    public static void restorePlayer(MinecraftServer server, Entry entry, UUID key) throws IOException {
        FishCatchSavedData snapshot = load(entry);
        create(server, Kind.PRE_RESTORE, null);
        FishCatchSavedData live = FishCatchSavedData.getOrCreate(server);
        live.replacePlayerFrom(snapshot, key);
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (live.resolvePlayerKey(online).equals(key)) {
                sync(online, live);
            }
        }
        Fishtastic.LOGGER.warn("Restored fish catch data for player key {} from backup {}", key, entry.fileName());
    }

    private static void sync(ServerPlayer player, FishCatchSavedData data) {
        QuestSyncPacket.sendToPlayer(player, data);
        FishEncyclopediaSyncPacket.sendToPlayer(player, data);
    }

    // -------------------------------------------------------------------------
    // Retention
    // -------------------------------------------------------------------------

    /** Applies every pool's retention policy. Returns the number of files deleted. */
    public static int prune(MinecraftServer server) {
        List<Entry> all = list(server);
        List<Entry> doomed = new ArrayList<>();
        doomed.addAll(pruneInterval(all.stream().filter(e -> e.kind().pool == Pool.INTERVAL).toList(), Instant.now()));
        doomed.addAll(pruneNewestN(all.stream().filter(e -> e.kind().pool == Pool.PRE_COMMAND).toList(),
                FishtasticServerConfig.backupPreCommandKeep()));
        int deleted = 0;
        for (Entry e : doomed) {
            if (delete(e)) {
                deleted++;
                Fishtastic.LOGGER.debug("Pruned fish catch backup {}", e.fileName());
            }
        }
        return deleted;
    }

    /**
     * Tiered thinning of the interval pool. Each snapshot is assigned a tier by age, and within
     * a tier the timeline is cut into fixed, epoch-aligned buckets (6h / 1d / 7d) of which only
     * the <em>oldest</em> snapshot survives - so a bucket's keeper is decided once and never
     * changes as newer files age into it. Everything younger than {@code keepAllHours} is kept
     * outright; everything older than the last tier is dropped.
     *
     * @return entries the policy says to delete
     */
    static List<Entry> pruneInterval(List<Entry> intervalPool, Instant now) {
        return pruneInterval(intervalPool, now,
                FishtasticServerConfig.backupKeepAllHours(),
                FishtasticServerConfig.backupKeepSixHourlyDays(),
                FishtasticServerConfig.backupKeepDailyDays(),
                FishtasticServerConfig.backupKeepWeeklyDays());
    }

    /** Pure function of its inputs - unit-testable without a server or config file. */
    static List<Entry> pruneInterval(List<Entry> intervalPool, Instant now, int keepAllHours,
                                     int sixHourlyDays, int dailyDays, int weeklyDays) {
        Duration keepAll = Duration.ofHours(keepAllHours);
        Duration sixHourly = Duration.ofDays(sixHourlyDays);
        Duration daily = Duration.ofDays(dailyDays);
        Duration weekly = Duration.ofDays(weeklyDays);

        // bucket key -> oldest entry seen in that bucket
        Map<String, Entry> keepers = new HashMap<>();
        List<Entry> doomed = new ArrayList<>();

        // Oldest first so the first entry we see in a bucket is the one that survives.
        List<Entry> ascending = intervalPool.stream().sorted(Comparator.comparing(Entry::time)).toList();
        for (Entry e : ascending) {
            Duration age = Duration.between(e.instant(), now);
            if (age.compareTo(keepAll) < 0) continue;

            long bucketSeconds;
            String tier;
            if (age.compareTo(sixHourly) < 0) {
                bucketSeconds = Duration.ofHours(6).toSeconds();
                tier = "6h";
            } else if (age.compareTo(daily) < 0) {
                bucketSeconds = Duration.ofDays(1).toSeconds();
                tier = "1d";
            } else if (age.compareTo(weekly) < 0) {
                bucketSeconds = Duration.ofDays(7).toSeconds();
                tier = "7d";
            } else {
                doomed.add(e);
                continue;
            }
            String key = tier + ":" + Math.floorDiv(e.instant().getEpochSecond(), bucketSeconds);
            if (keepers.putIfAbsent(key, e) != null) {
                doomed.add(e);
            }
        }
        return doomed;
    }

    /** Keep the newest {@code keep} entries of a pool; return the rest. */
    static List<Entry> pruneNewestN(List<Entry> pool, int keep) {
        List<Entry> descending = pool.stream().sorted(NEWEST_FIRST).toList();
        if (descending.size() <= keep) return List.of();
        return descending.subList(keep, descending.size());
    }

    private static Instant lastModifiedInstant(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException e) {
            return Instant.EPOCH;
        }
    }
}
