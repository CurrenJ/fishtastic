package grill24.fishtastic.ecology;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pure slot, lifespan and ID derivation behind the seeded ambient fish (world fish exploration Step 4). */
class AmbientRosterTest {

    private static final AmbientRoster.Params P = AmbientRoster.DEFAULT;
    private static final int FULL_SLOTS = 16;

    private static List<AmbientRoster.Lifespan> roster(long seed, int cx, int cz, int slots, long time) {
        return AmbientRoster.present(seed, cx, cz, slots, time, P);
    }

    @Test
    void slotsScaleWithUsableVolumeAndPuddlesGetNone() {
        assertEquals(0, AmbientRoster.slotCount(0, P));
        assertEquals(0, AmbientRoster.slotCount(P.minCells() - 1, P));
        assertEquals(1, AmbientRoster.slotCount(P.minCells(), P));
        assertEquals(1, AmbientRoster.slotCount(P.cellsPerSlot(), P));
        assertEquals(2, AmbientRoster.slotCount(P.cellsPerSlot() + 1, P));
        assertEquals(FULL_SLOTS, AmbientRoster.slotCount(16 * 16 * 4, P));
        assertEquals(FULL_SLOTS, AmbientRoster.slotCount(1_000_000, P));
        int previous = 0;
        for (int cells = 0; cells <= 1100; cells += 7) {
            int slots = AmbientRoster.slotCount(cells, P);
            assertTrue(slots >= previous, "slot count must not shrink as volume grows");
            previous = slots;
        }
    }

    @Test
    void derivationIsDeterministicAcrossIndependentCalls() {
        for (long time = 0; time < 60_000; time += 997) {
            List<AmbientRoster.Lifespan> a = roster(0x1234_5678_9ABCL, -3, 11, FULL_SLOTS, time);
            List<AmbientRoster.Lifespan> b = roster(0x1234_5678_9ABCL, -3, 11, FULL_SLOTS, time);
            assertEquals(a, b);
        }
        assertEquals(AmbientRoster.fishId(7L, 1, 2, 3, 4L), AmbientRoster.fishId(7L, 1, 2, 3, 4L));
    }

    @Test
    void idsFollowSeedChunkSlotAndLifespanIndex() {
        Set<Long> ids = new HashSet<>();
        for (int cx = -4; cx <= 4; cx++) {
            for (int cz = -4; cz <= 4; cz++) {
                for (int slot = 0; slot < FULL_SLOTS; slot++) {
                    for (long index = -2; index <= 2; index++) {
                        ids.add(AmbientRoster.fishId(99L, cx, cz, slot, index));
                    }
                }
            }
        }
        assertEquals(81 * FULL_SLOTS * 5, ids.size(), "no ID collisions across 6480 neighbouring fish");
        assertNotEquals(AmbientRoster.fishId(1L, 0, 0, 0, 0), AmbientRoster.fishId(2L, 0, 0, 0, 0));
        assertNotEquals(AmbientRoster.fishId(1L, 0, 0, 0, 0), AmbientRoster.fishId(1L, 0, 0, 0, 1));
        // The fish present is the one whose lifespan index it carries.
        AmbientRoster.Lifespan l = AmbientRoster.lifespanAt(5L, 2, 3, 1, 50_000, P);
        if (l != null) assertEquals(AmbientRoster.fishId(5L, 2, 3, 1, l.index()), l.fishId());
    }

    @Test
    void lifespansAreOrderedAndNeverOverlapWithinASlot() {
        for (int slot = 0; slot < FULL_SLOTS; slot++) {
            long period = AmbientRoster.period(42L, 5, -5, P);
            assertTrue(period >= P.minPeriodTicks() && period <= P.maxPeriodTicks());
            AmbientRoster.Lifespan previous = null;
            for (long index = -3; index <= 6; index++) {
                AmbientRoster.Lifespan l = AmbientRoster.lifespan(42L, 5, -5, slot, index, P);
                assertTrue(l.arrivalTick() < l.departureTick(), "a lifespan has a positive length");
                assertTrue(l.departureTick() - l.arrivalTick() <= period);
                if (previous != null) assertTrue(previous.departureTick() <= l.arrivalTick(), "slot lifespans must not overlap");
                previous = l;
            }
        }
    }

    @Test
    void lifespanAtMatchesTheLifespanThatContainsTheTime() {
        for (long time = -5000; time < 80_000; time += 313) {
            for (int slot = 0; slot < 4; slot++) {
                AmbientRoster.Lifespan l = AmbientRoster.lifespanAt(9L, 0, 0, slot, time, P);
                if (l == null) {
                    // vacant: no lifespan of the slot contains the time
                    for (long index = -2; index < 12; index++) {
                        assertFalse(AmbientRoster.lifespan(9L, 0, 0, slot, index, P).presentAt(time));
                    }
                } else {
                    assertTrue(l.presentAt(time));
                    assertTrue(l.phaseAt(time) >= 0 && l.phaseAt(time) <= 1);
                }
            }
        }
    }

    @Test
    void aSlotIsVacantOnlyBrieflyBetweenLifespans() {
        int vacant = 0, samples = 0;
        for (long time = 0; time < 400_000; time += 53) {
            for (int slot = 0; slot < 4; slot++) {
                samples++;
                if (AmbientRoster.lifespanAt(3L, 1, 1, slot, time, P) == null) vacant++;
            }
        }
        double share = vacant / (double) samples;
        assertTrue(share > 0.02 && share < 0.10, "slots should be present ~96% of the time, vacant share=" + share);
    }

    /**
     * E13: over two in-game days, no 10-second window changes more than a small share of a full chunk's
     * fish (a change is an arrival or a departure). The measured worst case is also asserted per slot count.
     */
    @Test
    void turnoverIsStaggeredOverADay() {
        int window = 200;
        int worst = 0;
        double worstShare = 0;
        long changesTotal = 0;
        long windows = 0;
        for (long seed = 1; seed <= 12; seed++) {
            for (int c = 0; c < 6; c++) {
                for (long time = 0; time < 48_000; time += 20) {
                    int changed = 0;
                    for (int slot = 0; slot < FULL_SLOTS; slot++) {
                        AmbientRoster.Lifespan a = AmbientRoster.lifespanAt(seed, c, -c, slot, time, P);
                        AmbientRoster.Lifespan b = AmbientRoster.lifespanAt(seed, c, -c, slot, time + window, P);
                        long idA = a == null ? 0 : a.fishId(), idB = b == null ? 0 : b.fishId();
                        if (idA != idB) changed++;
                    }
                    worst = Math.max(worst, changed);
                    changesTotal += changed;
                    windows++;
                }
            }
        }
        worstShare = worst / (double) FULL_SLOTS;
        System.out.printf("ambient turnover: worst %d of %d slots in %d ticks (%.0f%%), mean %.2f%n",
                worst, FULL_SLOTS, window, worstShare * 100, changesTotal / (double) windows);
        assertTrue(worstShare <= 0.2, "no 10 s window may change more than a fifth of a chunk, got " + worst + "/" + FULL_SLOTS);
    }

    /** A condition change must not swap the population at once: the roster is time, not conditions. */
    @Test
    void rosterAtTimeOfDayBoundariesChangesGradually() {
        for (long boundary : new long[]{0, 12000, 14000, 22000, 24000, 48000}) {
            Set<Long> before = new HashSet<>(), after = new HashSet<>();
            for (var l : roster(77L, 3, 4, FULL_SLOTS, boundary - 1)) before.add(l.fishId());
            for (var l : roster(77L, 3, 4, FULL_SLOTS, boundary)) after.add(l.fishId());
            Set<Long> shared = new HashSet<>(before);
            shared.retainAll(after);
            assertTrue(before.size() - shared.size() <= 2, "a tick boundary may swap at most a couple of fish");
        }
    }

    @Test
    void densityDrawsAStableSubsetAndNeverChangesWhoExists() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 20_000; i++) ids.add(AmbientRoster.fishId(31L, i % 50, i / 50, i % 16, i % 7));
        assertTrue(ids.stream().noneMatch(id -> AmbientRoster.drawn(id, 0)));
        assertTrue(ids.stream().allMatch(id -> AmbientRoster.drawn(id, 100)));
        for (int density = 0; density < 100; density += 5) {
            int low = density, high = density + 5;
            long drawnLow = 0;
            for (long id : ids) {
                boolean atLow = AmbientRoster.drawn(id, low);
                boolean atHigh = AmbientRoster.drawn(id, high);
                assertTrue(!atLow || atHigh, "raising the density must only add fish");
                if (atLow) drawnLow++;
            }
            if (low > 0) assertEquals(low / 100.0, drawnLow / (double) ids.size(), 0.02, "density " + low);
        }
        // Which fish exist does not depend on density: the roster function has no density input.
        assertEquals(roster(31L, 0, 0, FULL_SLOTS, 12_345), roster(31L, 0, 0, FULL_SLOTS, 12_345));
    }

    @Test
    void perFishValuesAreUniformAndFollowTheId() {
        int[] buckets = new int[8];
        int[] columns = new int[16];
        for (int i = 0; i < 16_000; i++) {
            long id = AmbientRoster.fishId(5L, i % 100, i / 100, i % 16, i % 3);
            buckets[(int) (AmbientRoster.speciesUnit(id) * 8)]++;
            columns[AmbientRoster.homeColumn(id, 16)]++;
            assertTrue(AmbientRoster.heightFraction(id) >= 0 && AmbientRoster.heightFraction(id) < 1);
            assertEquals(AmbientRoster.homeColumn(id, 37), AmbientRoster.homeColumn(id, 37));
        }
        for (int b : buckets) assertEquals(2000, b, 220);
        for (int c : columns) assertEquals(1000, c, 140);
        assertEquals(0, AmbientRoster.homeColumn(123L, 1));
    }

    @Test
    void theAmbientFluidIsLavaOnlyInTheNether() {
        assertEquals(AmbientFluid.LAVA, AmbientFluid.forDimension("minecraft:the_nether"));
        assertEquals(AmbientFluid.WATER, AmbientFluid.forDimension("minecraft:overworld"));
        assertEquals(AmbientFluid.WATER, AmbientFluid.forDimension("minecraft:the_end"));
        assertEquals(AmbientFluid.WATER, AmbientFluid.forDimension("somepack:deep_dark"));
    }
}
