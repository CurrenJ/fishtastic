package grill24.fishtastic.ecology;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisitWindowsTest {

    private static final VisitWindows.Params PARAMS = new VisitWindows.Params(3600L, 2400L, 0.2, 2);
    private static final long SEED = 0xFEEDBEEFL;

    @Test
    void phaseIsStableInRangeAndSpreadAcrossSites() {
        long t = PARAMS.periodTicks();
        int buckets = 10;
        int[] hist = new int[buckets];
        int n = 20_000;
        for (int i = 0; i < n; i++) {
            long phase = VisitWindows.phase(SEED, i % 200, i / 200, PARAMS);
            assertTrue(phase >= 0 && phase < t);
            assertEquals(phase, VisitWindows.phase(SEED, i % 200, i / 200, PARAMS));
            hist[(int) (phase * buckets / t)]++;
        }
        for (int count : hist) assertTrue(Math.abs(count - n / buckets) < 250, "phase histogram " + count);
        assertTrue(VisitWindows.phase(SEED, 1, 2, PARAMS) != VisitWindows.phase(SEED + 1, 1, 2, PARAMS)
                || VisitWindows.phase(SEED, 3, 2, PARAMS) != VisitWindows.phase(SEED + 1, 3, 2, PARAMS));
    }

    @Test
    void windowIndexAtBoundariesFollowsTheSitePhase() {
        long t0 = VisitWindows.phase(SEED, 4, 5, PARAMS);
        assertEquals(0, VisitWindows.windowIndex(SEED, 4, 5, t0, PARAMS));
        assertEquals(0, VisitWindows.windowIndex(SEED, 4, 5, t0 + 3599, PARAMS));
        assertEquals(1, VisitWindows.windowIndex(SEED, 4, 5, t0 + 3600, PARAMS));
        assertEquals(2, VisitWindows.windowIndex(SEED, 4, 5, t0 + 7200, PARAMS));
        assertEquals(-1, VisitWindows.windowIndex(SEED, 4, 5, t0 - 1, PARAMS));
        assertEquals(-1, VisitWindows.windowIndex(SEED, 4, 5, t0 - 3600, PARAMS));
        assertEquals(-2, VisitWindows.windowIndex(SEED, 4, 5, t0 - 3601, PARAMS));
    }

    @Test
    void windowSpanIsStartInclusiveEndExclusive() {
        int cx = -3, cz = 8;
        long t0 = VisitWindows.phase(SEED, cx, cz, PARAMS);
        long w = 5;
        long start = VisitWindows.windowStart(SEED, cx, cz, w, PARAMS);
        assertEquals(t0 + 18000, start);
        assertEquals(start + 2400, VisitWindows.windowEnd(SEED, cx, cz, w, PARAMS));
        assertFalse(VisitWindows.isOpenAt(SEED, cx, cz, start - 1, w, PARAMS));
        assertTrue(VisitWindows.isOpenAt(SEED, cx, cz, start, w, PARAMS));
        assertTrue(VisitWindows.isOpenAt(SEED, cx, cz, start + 2399, w, PARAMS));
        assertFalse(VisitWindows.isOpenAt(SEED, cx, cz, start + 2400, w, PARAMS));
        for (long t : new long[]{-7200, -1, 0, 1, 3599, 3600, 99999}) {
            long idx = VisitWindows.windowIndex(SEED, cx, cz, t, PARAMS);
            assertTrue(t >= VisitWindows.windowStart(SEED, cx, cz, idx, PARAMS));
            assertTrue(t < VisitWindows.windowStart(SEED, cx, cz, idx + 1, PARAMS));
        }
    }

    @Test
    void windowsOverlappingFindsExactlyTheOverlappingWindows() {
        Random random = new Random(11);
        for (int i = 0; i < 2000; i++) {
            int cx = random.nextInt(50) - 25, cz = random.nextInt(50) - 25;
            long from = random.nextInt(40_000) - 10_000;
            long to = from + 1 + random.nextInt((int) PARAMS.visitTicks());
            long[] range = VisitWindows.windowsOverlapping(SEED, cx, cz, from, to, PARAMS);
            assertTrue(range[1] - range[0] <= 1, "at most two windows");
            for (long k = range[0] - 2; k <= range[1] + 2; k++) {
                long s = VisitWindows.windowStart(SEED, cx, cz, k, PARAMS);
                boolean overlaps = s < to && s + PARAMS.visitTicks() > from;
                assertEquals(overlaps, k >= range[0] && k <= range[1], "k=" + k);
            }
        }
    }

    @Test
    void sitesDoNotOpenOnTheSameTick() {
        // With per-site phases the number of sites open at a tick is flat over the period (no global pulse).
        int sites = 4000;
        VisitWindows.Params all = new VisitWindows.Params(3600L, 2400L, 1.0, 0);
        int min = Integer.MAX_VALUE, max = 0;
        for (long t = 0; t < 3600; t += 300) {
            int open = 0;
            for (int i = 0; i < sites; i++) {
                int cx = i % 64, cz = i / 64;
                long w = VisitWindows.windowIndex(SEED, cx, cz, t, all);
                if (VisitWindows.isOpenAt(SEED, cx, cz, t, w, all) && VisitWindows.isActive(SEED, cx, cz, w, all)) open++;
            }
            min = Math.min(min, open);
            max = Math.max(max, open);
        }
        // L/T = 2/3 of 4000 = 2667 expected; binomial sigma ~30
        assertTrue(min > 2500 && max < 2830, "min=" + min + " max=" + max);
    }

    @Test
    void paramsValidate() {
        assertThrows(IllegalArgumentException.class, () -> new VisitWindows.Params(0, 1, 0.5, 1));
        assertThrows(IllegalArgumentException.class, () -> new VisitWindows.Params(10, 11, 0.5, 1));
        assertThrows(IllegalArgumentException.class, () -> new VisitWindows.Params(10, 5, 1.5, 1));
        assertThrows(IllegalArgumentException.class, () -> new VisitWindows.Params(10, 5, Double.NaN, 1));
        assertThrows(IllegalArgumentException.class, () -> new VisitWindows.Params(10, 5, 0.5, -1));
    }

    @Test
    void activeFractionMatchesTheChance() {
        int active = 0;
        int n = 100_000;
        for (int i = 0; i < n; i++) {
            if (VisitWindows.isActive(SEED, i % 500, i / 500, 3L, PARAMS)) active++;
        }
        assertTrue(Math.abs(active - 0.2 * n) < 0.2 * n * 0.05, "active=" + active);
        assertFalse(VisitWindows.isActive(SEED, 1, 1, 1L, new VisitWindows.Params(10, 5, 0.0, 0)));
        assertTrue(VisitWindows.isActive(SEED, 1, 1, 1L, new VisitWindows.Params(10, 5, 1.0, 0)));
    }

    @Test
    void suppressionIsAPureFunctionOfSeedChunkWindowAndParams() {
        // Evaluate the same chunks in forward, reversed and shuffled order: identical answers. Nothing is
        // loaded or cached between calls, so "which chunks are loaded" cannot enter the result.
        List<int[]> chunks = new ArrayList<>();
        for (int x = -15; x <= 15; x++) for (int z = -15; z <= 15; z++) chunks.add(new int[]{x, z});
        boolean[] forward = evaluate(chunks);
        List<int[]> reversed = new ArrayList<>(chunks);
        Collections.reverse(reversed);
        boolean[] back = evaluate(reversed);
        List<int[]> shuffled = new ArrayList<>(chunks);
        Collections.shuffle(shuffled, new Random(5));
        boolean[] mixed = evaluate(shuffled);
        for (int i = 0; i < chunks.size(); i++) {
            int[] c = chunks.get(i);
            assertEquals(forward[i], back[reversed.indexOf(c)]);
            assertEquals(forward[i], mixed[shuffled.indexOf(c)]);
        }
    }

    private static boolean[] evaluate(List<int[]> chunks) {
        boolean[] out = new boolean[chunks.size()];
        for (int i = 0; i < out.length; i++) {
            int[] c = chunks.get(i);
            long w = VisitWindows.windowIndex(SEED, c[0], c[1], 40_000L, PARAMS);
            out[i] = VisitWindows.hostsVisit(SEED, c[0], c[1], w, PARAMS);
        }
        return out;
    }

    @Test
    void noTwoVisitsOpenAtOnceWithinTheSuppressionRadius() {
        int radius = PARAMS.suppressionRadiusChunks();
        for (long t : new long[]{500L, 17_000L, 123_456L}) {
            List<int[]> hosts = new ArrayList<>();
            for (int x = -40; x <= 40; x++) {
                for (int z = -40; z <= 40; z++) {
                    long w = VisitWindows.windowIndex(SEED, x, z, t, PARAMS);
                    if (VisitWindows.isOpenAt(SEED, x, z, t, w, PARAMS) && VisitWindows.hostsVisit(SEED, x, z, w, PARAMS)) {
                        hosts.add(new int[]{x, z});
                    }
                }
            }
            assertTrue(hosts.size() > 20, "need a real sample, got " + hosts.size());
            for (int i = 0; i < hosts.size(); i++) {
                for (int j = i + 1; j < hosts.size(); j++) {
                    int dx = hosts.get(i)[0] - hosts.get(j)[0];
                    int dz = hosts.get(i)[1] - hosts.get(j)[1];
                    assertTrue(dx * dx + dz * dz > radius * radius, "hosts too close: " + dx + "," + dz);
                }
            }
        }
    }

    @Test
    void suppressionSeesNeighbourWindowsOfEitherSideInTime() {
        // A neighbour's previous and next windows can both overlap ours; a brute-force scan must agree.
        VisitWindows.Params p = new VisitWindows.Params(3600L, 3000L, 0.3, 1);
        int checked = 0;
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                for (long w = -1; w <= 2; w++) {
                    if (!VisitWindows.isActive(SEED, x, z, w, p)) continue;
                    boolean expected = false;
                    long s = VisitWindows.windowStart(SEED, x, z, w, p);
                    long span = VisitWindows.hostedSpanTicks(p);
                    for (int dx = -1; dx <= 1 && !expected; dx++) {
                        for (int dz = -1; dz <= 1 && !expected; dz++) {
                            if ((dx == 0 && dz == 0) || dx * dx + dz * dz > 1) continue;
                            for (long k = -5; k <= 6 && !expected; k++) {
                                long ns = VisitWindows.windowStart(SEED, x + dx, z + dz, k, p);
                                boolean overlap = ns < s + span && ns + span > s;
                                expected = overlap && VisitWindows.isActive(SEED, x + dx, z + dz, k, p)
                                        && VisitWindows.outranks(SEED, x + dx, z + dz, k, x, z, w);
                            }
                        }
                    }
                    assertEquals(expected, VisitWindows.isSuppressed(SEED, x, z, w, p), x + "," + z + "," + w);
                    checked++;
                }
            }
        }
        assertTrue(checked > 100, "checked=" + checked);
    }

    @Test
    void suppressionCoversAVisitStretchedToTheLongestLifetime() {
        // A long-lived species keeps its visit up to min(T, 2L); no two hosts within R may be live then either.
        VisitWindows.Params p = new VisitWindows.Params(7200L, 6000L, 0.3, 2);
        long span = VisitWindows.hostedSpanTicks(p);
        assertEquals(7200L, span);
        int radius = p.suppressionRadiusChunks();
        List<long[]> hosts = new ArrayList<>();
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                for (long w = 0; w <= 3; w++) {
                    if (VisitWindows.hostsVisit(SEED, x, z, w, p)) {
                        hosts.add(new long[]{x, z, VisitWindows.windowStart(SEED, x, z, w, p)});
                    }
                }
            }
        }
        assertTrue(hosts.size() > 50, "need a real sample, got " + hosts.size());
        for (int i = 0; i < hosts.size(); i++) {
            for (int j = i + 1; j < hosts.size(); j++) {
                long[] a = hosts.get(i), b = hosts.get(j);
                long dx = a[0] - b[0], dz = a[1] - b[1];
                if (dx * dx + dz * dz > (long) radius * radius) continue;
                boolean overlap = a[2] < b[2] + span && b[2] < a[2] + span;
                assertFalse(overlap, "live together within R: " + dx + "," + dz);
            }
        }
    }

    @Test
    void suppressionRemovesSomeAndNeverAddsAny() {
        int active = 0, hosted = 0;
        for (int x = -30; x <= 30; x++) {
            for (int z = -30; z <= 30; z++) {
                boolean a = VisitWindows.isActive(SEED, x, z, 2L, PARAMS);
                boolean h = VisitWindows.hostsVisit(SEED, x, z, 2L, PARAMS);
                if (a) active++;
                if (h) hosted++;
                assertTrue(a || !h);
            }
        }
        assertTrue(hosted < active, "hosted=" + hosted + " active=" + active);
        assertTrue(hosted > 0);
    }

    @Test
    void zeroRadiusDisablesSuppression() {
        VisitWindows.Params none = new VisitWindows.Params(3600, 2400, 0.2, 0);
        for (int x = 0; x < 30; x++) {
            assertFalse(VisitWindows.isSuppressed(SEED, x, 0, 1L, none));
            assertEquals(VisitWindows.isActive(SEED, x, 0, 1L, none), VisitWindows.hostsVisit(SEED, x, 0, 1L, none));
        }
    }

    @Test
    void outranksIsAStrictOrder() {
        assertFalse(VisitWindows.outranks(SEED, 3, 4, 1L, 3, 4, 1L));
        for (int x = 0; x < 20; x++) {
            boolean ab = VisitWindows.outranks(SEED, x, 0, 1L, x + 1, 5, 2L);
            boolean ba = VisitWindows.outranks(SEED, x + 1, 5, 2L, x, 0, 1L);
            assertTrue(ab != ba);
        }
    }

    @Test
    void windowsOfOneSiteAreIndependent() {
        int both = 0;
        int n = 50_000;
        for (int i = 0; i < n; i++) {
            boolean a = VisitWindows.isActive(SEED, 7, 9, i, PARAMS);
            boolean b = VisitWindows.isActive(SEED, 7, 9, i + 1, PARAMS);
            if (a && b) both++;
        }
        // p^2 = 0.04 -> 2000 expected, sigma ~44
        assertTrue(Math.abs(both - 0.04 * n) < 300, "both=" + both);
    }
}
