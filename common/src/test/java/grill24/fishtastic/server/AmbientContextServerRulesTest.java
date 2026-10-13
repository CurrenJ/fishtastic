package grill24.fishtastic.server;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the pure server-side policy pieces of the ambient context exchange (Fish in World,
 * Phase 4): the response-size cap's selection rule and the tracked-player table's bound. The
 * world-facing validation order (dimension, distance, loaded chunk, water) is covered by the
 * GameTests, which have a real level to validate against.
 */
class AmbientContextServerRulesTest {

    @Test
    void underTheCapEveryIndexIsKeptInPoolOrder() {
        int[] weights = {5, 3, 9};
        assertArrayEquals(new int[]{0, 1, 2}, AmbientContextServer.topWeightIndices(weights, 96));
    }

    @Test
    void overTheCapTheHighestWeightsSurviveAndPoolOrderIsRestored() {
        // Six candidates, keep three: the top three weights are 9 (index 3), 8 (index 1) and
        // 7 (index 5); the survivors come back ascending.
        int[] weights = {1, 8, 2, 9, 3, 7};
        assertArrayEquals(new int[]{1, 3, 5}, AmbientContextServer.topWeightIndices(weights, 3));
    }

    @Test
    void tiesKeepPoolOrderInTheRanking() {
        // Four equal weights, keep two: the earliest two indices win, ascending.
        int[] weights = {4, 4, 4, 4};
        assertArrayEquals(new int[]{0, 1}, AmbientContextServer.topWeightIndices(weights, 2));
        // Ties at the cutoff prefer the earlier index too.
        int[] mixed = {9, 5, 5, 5};
        assertArrayEquals(new int[]{0, 1}, AmbientContextServer.topWeightIndices(mixed, 2));
    }

    @Test
    void theCapIsExact() {
        int n = 200;
        int[] weights = new int[n];
        for (int i = 0; i < n; i++) {
            weights[i] = n - i; // descending: the first `cap` indices are the heaviest
        }
        int cap = 96;
        int[] kept = AmbientContextServer.topWeightIndices(weights, cap);
        assertEquals(cap, kept.length);
        for (int i = 0; i < cap; i++) {
            assertEquals(i, kept[i]);
        }
        assertTrue(kept[kept.length - 1] < n);
    }

    /**
     * The tracked-player table is bounded at {@link AmbientContextServer#MAX_TRACKED_PLAYERS} and
     * a crowded table evicts without clearing live buckets: an active player's burst must survive
     * a stranger arriving at the cap.
     */
    @Test
    void theCrowdedTableStaysAtItsCapAndNeverClearsLiveBuckets() {
        AmbientContextServer.resetForTest();
        try {
            UUID active = new UUID(1, 1);
            AmbientRequestThrottle activeBucket = AmbientContextServer.throttleFor(active, 0L);
            assertTrue(activeBucket.tryAcquire(0L), "sanity: the active bucket must start with its burst");

            for (int i = 0; i < AmbientContextServer.MAX_TRACKED_PLAYERS - 1; i++) {
                AmbientContextServer.throttleFor(new UUID(2, i), 0L);
            }
            assertEquals(AmbientContextServer.MAX_TRACKED_PLAYERS, AmbientContextServer.trackedThrottleCount(),
                    "sanity: the table should be exactly at its cap before the crowded insert");

            // One more player arrives at the cap: the table must not grow past it...
            AmbientContextServer.throttleFor(new UUID(3, 3), 0L);
            assertTrue(AmbientContextServer.trackedThrottleCount() <= AmbientContextServer.MAX_TRACKED_PLAYERS,
                    "the tracked-player table must never exceed its cap, got "
                            + AmbientContextServer.trackedThrottleCount());

            // ...and the recently used bucket must not have been evicted or mass-reset by the
            // pruning: its spent token stays spent.
            assertEquals(1, AmbientContextServer.throttleFor(active, 0L).accepted(),
                    "a live player's bucket must survive a crowded-table prune (only a clear() or "
                            + "an eviction of the active entry could reset it)");

            // A second stranger at the cap takes the same path — evict the least recently used,
            // still never the active bucket, and still never a wholesale clear.
            AmbientContextServer.throttleFor(new UUID(4, 4), 0L);
            assertTrue(AmbientContextServer.trackedThrottleCount() <= AmbientContextServer.MAX_TRACKED_PLAYERS,
                    "the table must still be at its cap after a second crowded insert, got "
                            + AmbientContextServer.trackedThrottleCount());
            assertEquals(1, AmbientContextServer.throttleFor(active, 0L).accepted(),
                    "the active bucket must survive repeated crowded inserts");
        } finally {
            AmbientContextServer.resetForTest();
        }
    }
}
