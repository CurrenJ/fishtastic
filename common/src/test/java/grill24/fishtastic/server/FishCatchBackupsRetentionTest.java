package grill24.fishtastic.server;

import grill24.fishtastic.server.FishCatchBackups.Entry;
import grill24.fishtastic.server.FishCatchBackups.Kind;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the pure tiered-retention policy in {@link FishCatchBackups#pruneInterval} against a
 * synthetic six-month history of 30-minute snapshots, with the default config values
 * (24h / 7d / 30d / 180d).
 */
class FishCatchBackupsRetentionTest {

    private static final int KEEP_ALL_HOURS = 24;
    private static final int SIX_HOURLY_DAYS = 7;
    private static final int DAILY_DAYS = 30;
    private static final int WEEKLY_DAYS = 180;

    private static final Instant NOW = LocalDateTime.of(2026, 9, 20, 12, 0).atZone(ZoneId.systemDefault()).toInstant();

    private static Entry at(Instant instant, Kind kind) {
        LocalDateTime t = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        return new Entry(Path.of("fake-" + instant.getEpochSecond() + ".dat"), t, kind, null);
    }

    /** One snapshot every 30 min from {@code daysBack} days ago until now. */
    private static List<Entry> everyHalfHour(int daysBack) {
        List<Entry> out = new ArrayList<>();
        for (Instant t = NOW.minus(Duration.ofDays(daysBack)); !t.isAfter(NOW); t = t.plus(Duration.ofMinutes(30))) {
            out.add(at(t, Kind.INTERVAL));
        }
        return out;
    }

    private static List<Entry> survivors(List<Entry> pool, List<Entry> doomed) {
        Set<Entry> gone = new HashSet<>(doomed);
        return pool.stream().filter(e -> !gone.contains(e)).toList();
    }

    private static List<Entry> prune(List<Entry> pool) {
        return FishCatchBackups.pruneInterval(pool, NOW, KEEP_ALL_HOURS, SIX_HOURLY_DAYS, DAILY_DAYS, WEEKLY_DAYS);
    }

    @Test
    void everythingUnder24hIsKept() {
        List<Entry> pool = everyHalfHour(1);
        assertTrue(prune(pool).isEmpty(), "nothing younger than keepAllHours may be deleted");
    }

    @Test
    void olderThanLastTierIsDeleted() {
        List<Entry> pool = List.of(at(NOW.minus(Duration.ofDays(WEEKLY_DAYS + 1)), Kind.INTERVAL));
        assertEquals(pool, prune(pool));
    }

    @Test
    void sixMonthHistoryThinsToBoundedCount() {
        List<Entry> pool = everyHalfHour(200);
        List<Entry> kept = survivors(pool, prune(pool));

        // Dense recent day: 48 half-hourly + the one exactly at the boundary is >= 24h so not "all".
        long lastDay = kept.stream().filter(e -> Duration.between(e.instant(), NOW).compareTo(Duration.ofHours(24)) < 0).count();
        assertEquals(48, lastDay);

        // Bucketed tiers: one per bucket, so counts are bounded by the tier widths. Epoch-aligned
        // buckets mean a tier can straddle one extra bucket at each edge.
        long sixHourly = kept.stream().filter(e -> ageDays(e) >= 1 && ageDays(e) < SIX_HOURLY_DAYS).count();
        assertTrue(sixHourly >= 24 && sixHourly <= 26, "6-hourly tier held " + sixHourly);

        long daily = kept.stream().filter(e -> ageDays(e) >= SIX_HOURLY_DAYS && ageDays(e) < DAILY_DAYS).count();
        assertTrue(daily >= 23 && daily <= 25, "daily tier held " + daily);

        long weekly = kept.stream().filter(e -> ageDays(e) >= DAILY_DAYS && ageDays(e) < WEEKLY_DAYS).count();
        assertTrue(weekly >= 21 && weekly <= 23, "weekly tier held " + weekly);

        long ancient = kept.stream().filter(e -> ageDays(e) >= WEEKLY_DAYS).count();
        assertEquals(0, ancient);

        assertTrue(kept.size() < 130, "total survivors " + kept.size());
    }

    @Test
    void keeperPerBucketIsTheOldestAndStable() {
        // Two snapshots in the same 6h bucket, both in the 6-hourly tier.
        Instant older = NOW.minus(Duration.ofDays(3));
        Instant newer = older.plus(Duration.ofMinutes(30));
        // Align both to the same epoch bucket by construction: pick 'older' just after a bucket boundary.
        long bucket = Duration.ofHours(6).toSeconds();
        older = Instant.ofEpochSecond(Math.floorDiv(older.getEpochSecond(), bucket) * bucket + 60);
        newer = older.plus(Duration.ofMinutes(30));

        Entry a = at(older, Kind.INTERVAL);
        Entry b = at(newer, Kind.INTERVAL);
        List<Entry> doomed = prune(List.of(b, a)); // deliberately unsorted input
        assertEquals(List.of(b), doomed, "the newer of two snapshots in a bucket is dropped");

        // Re-running the policy on the survivors is a no-op (idempotent).
        assertTrue(prune(List.of(a)).isEmpty());
    }

    @Test
    void startBackupsShareTheIntervalPool() {
        Instant t = NOW.minus(Duration.ofDays(3));
        long bucket = Duration.ofHours(6).toSeconds();
        t = Instant.ofEpochSecond(Math.floorDiv(t.getEpochSecond(), bucket) * bucket + 60);
        Entry start = at(t, Kind.START);
        Entry auto = at(t.plus(Duration.ofMinutes(10)), Kind.INTERVAL);
        assertEquals(List.of(auto), prune(List.of(start, auto)));
    }

    @Test
    void preCommandPoolKeepsNewestN() {
        List<Entry> pool = new ArrayList<>();
        for (int i = 0; i < 10; i++) pool.add(at(NOW.minus(Duration.ofHours(i)), Kind.PRE_COMMAND));
        List<Entry> doomed = FishCatchBackups.pruneNewestN(pool, 3);
        assertEquals(7, doomed.size());
        assertTrue(doomed.stream().allMatch(e -> Duration.between(e.instant(), NOW).toHours() >= 3));
        assertTrue(FishCatchBackups.pruneNewestN(pool, 10).isEmpty());
        assertTrue(FishCatchBackups.pruneNewestN(pool, 50).isEmpty());
    }

    private static long ageDays(Entry e) {
        return Duration.between(e.instant(), NOW).toDays();
    }
}
