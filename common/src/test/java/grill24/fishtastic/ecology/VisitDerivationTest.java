package grill24.fishtastic.ecology;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisitDerivationTest {

    private static final long SEED = 0x1234_5678_9ABC_DEF0L;
    private static final VisitId ID = new VisitId("minecraft:overworld", 12, -7, 42);

    @Test
    void everythingIsAPureFunctionOfSeedAndVisitId() {
        long base = VisitDerivation.base(SEED, ID);
        assertEquals(base, VisitDerivation.base(SEED, new VisitId("minecraft:the_nether", 12, -7, 42)),
                "the dimension is already folded into the seed, so the id's label does not change the hashes");
        assertEquals(VisitDerivation.schoolId(base), VisitDerivation.schoolId(VisitDerivation.base(SEED, ID)));
        assertEquals(VisitDerivation.memberRewardSeed(base, 3), VisitDerivation.memberRewardSeed(base, 3));
        assertEquals(VisitDerivation.memberCount(base, 2, 6), VisitDerivation.memberCount(base, 2, 6));
        assertNotEquals(base, VisitDerivation.base(SEED + 1, ID));
        assertNotEquals(base, VisitDerivation.base(SEED, new VisitId("minecraft:overworld", 12, -7, 43)));
    }

    @Test
    void memberStreamsAreIndependent() {
        long base = VisitDerivation.base(SEED, ID);
        Set<Long> seeds = new HashSet<>();
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 64; i++) {
            assertTrue(seeds.add(VisitDerivation.memberRewardSeed(base, i)));
            assertTrue(ids.add(VisitDerivation.memberId(base, i)));
        }
        assertTrue(ids.add(VisitDerivation.schoolId(base)));
        assertNotEquals(VisitDerivation.positionSeed(base), VisitDerivation.memberRewardSeed(base, 0));
    }

    @Test
    void startIndexStaysInRangeAndSpreads() {
        int[] hist = new int[8];
        for (int w = 0; w < 8000; w++) {
            int i = VisitDerivation.startIndex(VisitDerivation.startHash(SEED, new VisitId("d", 3, 4, w)), 8);
            assertTrue(i >= 0 && i < 8);
            hist[i]++;
        }
        for (int h : hist) assertTrue(h > 800 && h < 1200, "start cells should take turns evenly: " + h);
        assertEquals(0, VisitDerivation.startIndex(0xFFFF_FFFF_FFFF_FFFFL, 1));
    }

    @Test
    void weightedPickFollowsWeightsAndIgnoresZeros() {
        int[] weights = {10, 0, 30, 60};
        int[] hits = new int[weights.length];
        for (int w = 0; w < 20_000; w++) {
            int pick = VisitDerivation.pickWeighted(VisitDerivation.base(SEED, new VisitId("d", 0, 0, w)), weights);
            assertTrue(pick >= 0);
            hits[pick]++;
        }
        assertEquals(0, hits[1], "a zero-weight species is never drawn");
        assertEquals(0.1, hits[0] / 20_000.0, 0.02);
        assertEquals(0.3, hits[2] / 20_000.0, 0.02);
        assertEquals(0.6, hits[3] / 20_000.0, 0.02);
        assertEquals(-1, VisitDerivation.pickWeighted(1L, new int[]{0, 0}));
        assertEquals(-1, VisitDerivation.pickWeighted(1L, new int[0]));
    }

    @Test
    void memberCountIsInRangeAndHitsEveryValue() {
        boolean[] seen = new boolean[7];
        for (int w = 0; w < 2000; w++) {
            int n = VisitDerivation.memberCount(VisitDerivation.base(SEED, new VisitId("d", 1, 1, w)), 2, 5);
            assertTrue(n >= 2 && n <= 5);
            seen[n] = true;
        }
        assertTrue(seen[2] && seen[3] && seen[4] && seen[5]);
        assertEquals(3, VisitDerivation.memberCount(5L, 3, 3));
        assertThrows(IllegalArgumentException.class, () -> VisitDerivation.memberCount(5L, 3, 2));
    }

    @Test
    void ledgerMaskTracksMembersByIndex() {
        long mask = 0;
        assertFalse(VisitDerivation.isCaught(mask, 0));
        mask |= VisitDerivation.bit(0);
        mask |= VisitDerivation.bit(2);
        assertTrue(VisitDerivation.isCaught(mask, 0));
        assertFalse(VisitDerivation.isCaught(mask, 1));
        assertTrue(VisitDerivation.isCaught(mask, 2));
        assertFalse(VisitDerivation.isDepleted(mask, 3));
        mask |= VisitDerivation.bit(1);
        assertTrue(VisitDerivation.isDepleted(mask, 3));
        assertTrue(VisitDerivation.isDepleted(mask, 2), "a smaller roster is depleted once its members are all in the mask");
        assertFalse(VisitDerivation.isDepleted(0, 0), "an empty roster is not depleted");
        assertTrue(VisitDerivation.isDepleted(-1L, 64));
        assertFalse(VisitDerivation.isCaught(-1L, 64));
    }

    @Test
    void windowStartClockDoesNotDependOnWhenItIsAsked() {
        long start = 10_000;
        // The clock tracks game time 1:1, so asking at the start or 1500 ticks later gives the same reading.
        long clockAtStart = 123_456;
        assertEquals(clockAtStart, VisitDerivation.clockAtWindowStart(clockAtStart, start, start));
        assertEquals(clockAtStart, VisitDerivation.clockAtWindowStart(clockAtStart + 1500, start + 1500, start));
        assertEquals(clockAtStart, VisitDerivation.clockAtWindowStart(clockAtStart + 2399, start + 2399, start));
    }

    @Test
    void moonPhaseIsTheEightDayCycleFromFullMoon() {
        assertEquals(0, VisitDerivation.moonPhaseIndex(0));
        assertEquals(0, VisitDerivation.moonPhaseIndex(23_999));
        assertEquals(1, VisitDerivation.moonPhaseIndex(24_000));
        assertEquals(7, VisitDerivation.moonPhaseIndex(7 * 24_000L + 5));
        assertEquals(0, VisitDerivation.moonPhaseIndex(8 * 24_000L));
        assertEquals(7, VisitDerivation.moonPhaseIndex(-1));
    }

    @Test
    void visitIdIsTheWindowOfTheSite() {
        VisitWindows.Params params = VisitWindows.Params.DEFAULT;
        long start = VisitWindows.windowStart(SEED, 5, 6, 9, params);
        VisitId early = VisitId.at("d", SEED, 5, 6, start, params);
        VisitId late = VisitId.at("d", SEED, 5, 6, start + params.visitTicks() - 1, params);
        assertEquals(early, late, "any time inside the window is the same visit");
        assertEquals(9, early.window());
        assertEquals(start, early.start(SEED, params));
        assertEquals(start + params.visitTicks(), early.end(SEED, params));
    }

    @Test
    void departuresAreSeededInsideTheLastNineTenthsOfTheVisit() {
        long start = 50_000, length = 2400;
        long base = VisitDerivation.base(SEED, ID);
        long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
        for (int w = 0; w < 4000; w++) {
            long b = VisitDerivation.base(SEED, new VisitId("d", 2, 3, w));
            long d = VisitDerivation.departureTick(b, w % 8, start, length);
            assertEquals(d, VisitDerivation.departureTick(b, w % 8, start, length), "pure");
            assertTrue(d >= start + length / 10 && d < start + length, "inside [start + L/10, start + L): " + (d - start));
            min = Math.min(min, d);
            max = Math.max(max, d);
        }
        assertTrue(min - start < length / 10 + 30 && start + length - max < 30, "the whole span is used");
        assertNotEquals(VisitDerivation.departureTick(base, 0, start, length), VisitDerivation.departureTick(base, 1, start, length));
    }

    @Test
    void departureIsMonotoneInTimeAndNobodyHasGoneAtTheStart() {
        long start = 9_000, length = 1800;
        long base = VisitDerivation.base(SEED, ID);
        for (int m = 0; m < 8; m++) {
            long d = VisitDerivation.departureTick(base, m, start, length);
            assertFalse(VisitDerivation.hasDeparted(d, start), "the window opens with everyone there");
            assertFalse(VisitDerivation.hasDeparted(d, d - 1));
            assertTrue(VisitDerivation.hasDeparted(d, d));
            assertTrue(VisitDerivation.hasDeparted(d, start + length - 1), "everyone has gone by the last tick");
        }
        assertFalse(VisitDerivation.hasDeparted(-1L, 1_000_000L), "a member with no seeded departure never leaves on its own");
    }

    @Test
    void halfwayRevealLeavesAboutHalfOfAFullRoster() {
        long start = 0, length = 2400;
        int present = 0, total = 0;
        for (int w = 0; w < 4000; w++) {
            long b = VisitDerivation.base(SEED, new VisitId("d", 5, 5, w));
            for (int m = 0; m < 4; m++) {
                total++;
                if (!VisitDerivation.hasDeparted(VisitDerivation.departureTick(b, m, start, length), start + length / 2)) present++;
            }
        }
        // P(departure > 50%) is (1 - 0.5) / 0.9 for a uniform draw over [10%, 100%).
        assertEquals(0.5 / 0.9, present / (double) total, 0.03);
    }

    @Test
    void lifetimeAndRevealSpanFollowTheScaleUpToOnePeriod() {
        assertEquals(2400, VisitDerivation.lifetimeTicks(2400, 3600, 1.0));
        assertEquals(600, VisitDerivation.lifetimeTicks(2400, 3600, 0.25));
        assertEquals(3600, VisitDerivation.lifetimeTicks(2400, 3600, 2.0), "capped at one period");
        assertEquals(3600, VisitDerivation.revealSpanTicks(2400, 3600, 2.0));
        assertEquals(4000, VisitDerivation.revealSpanTicks(2000, 9000, 2.0));
        assertEquals(1, VisitDerivation.lifetimeTicks(1, 10, 0.25));
    }
}
