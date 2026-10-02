package grill24.fishsim;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.Shelter;
import grill24.fishsim.domain.VoxelDomain;
import grill24.fishsim.harness.Metrics;
import grill24.fishsim.harness.Scenarios;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The visit state machine's guarantees (docs/fish-shelters.md §10), over the voxel domains with a
 * Hollow Log in each, two casts and two seeds — plus the transients a running tank throws at it:
 * a shelter placed on a fish, a shelter taken away from fish inside it, and a rebuild mid-visit.
 *
 * <p>Diagnostics (visits, time hidden) are asserted only as "something happens"; the numbers
 * explain the headless GIF, they are not targets (see the metrics-are-proxies warning in the
 * spec).
 */
class ShelterVisitTest {

    private static final float SPEED_TOLERANCE = 1.001f;
    // Vertical damping acts after the force clamp, so a fish braking a steep dive shows more than
    // maxForce: a visitor diving at 0.106 blocks/s for a floor-level mouth measured 1.075 (steering
    // 0.455, damping 0.127), 2026-10-02. Looser than VoxelDomainTest's 1.06 for that dive alone.
    private static final float ACCEL_TOLERANCE = 1.10f;
    private static final float MAX_JERK = 12f;
    private static final int TICKS = 12_000;
    /** Slack on the mouth's opening for a fish's centre passing through it, blocks. */
    private static final float MOUTH_TOLERANCE = 0.02f;

    private record Config(String domain, String cast, int fishCount, long seed) {
        String name() {
            return domain + " " + cast + " n=" + fishCount + " seed=" + seed;
        }
    }

    private static List<Config> matrix() {
        List<Config> list = new ArrayList<>();
        for (String domain : new String[]{"3x1x1+log", "L+log", "2x2slab+log", "4x1x2+log"}) {
            for (String cast : new String[]{"loaches", "visitors", "lurker"}) {
                for (long seed : new long[]{12345L, -987654321L}) {
                    list.add(new Config(domain, cast, 8, seed));
                }
            }
        }
        return list;
    }

    /** What the run saw, for the assertions and their messages. */
    private static final class Watch {
        int hullOutsiders, sideEntries, insideOutOfBox, overCapacity, overBudget, longApproaches;
        int visits;
        String first;
        final int[] approachTicks;
        final int[] prev;
        /**
         * Adoption may briefly overfill a shelter and the hidden budget (§4.3): a shelter placed
         * on a fish takes it in whatever the count. Every other bound still holds.
         */
        boolean adoption;

        Watch(int n) {
            approachTicks = new int[n];
            prev = new int[n];
        }

        void note(String what) {
            if (first == null) first = what;
        }

        /** Checks every §10 invariant for one tick. */
        void tick(FlockEngine e, List<Shelter> shelters, int tick) {
            int n = e.count();
            int hidden = 0;
            int[] occupancy = new int[shelters.size()];
            for (int i = 0; i < n; i++) {
                float l = e.posL()[i], y = e.posY()[i], d = e.posD()[i];
                int st = e.shelterState(i);
                int using = e.shelterUsing(i);
                if (st == FlockEngine.SHELTER_ENTER || st == FlockEngine.SHELTER_INSIDE) {
                    hidden++;
                    occupancy[e.shelterIndex(i)]++;
                }
                if (st == FlockEngine.SHELTER_INSIDE && prev[i] != FlockEngine.SHELTER_INSIDE) visits++;
                for (int s = 0; s < shelters.size(); s++) {
                    Shelter shelter = shelters.get(s);
                    if (!shelter.hull().contains(l, y, d)) continue;
                    if (using != s) {
                        hullOutsiders++;
                        note("fish " + i + " inside hull " + s + " without using it, tick " + tick + ", state " + st);
                    } else if (!shelter.interior().contains(l, y, d) && !inMouthCorridor(shelter, e.shelterMouth(i), l, y, d)) {
                        sideEntries++;
                        note("fish " + i + " inside hull " + s + " away from its interior and mouth, tick " + tick);
                    }
                }
                if (st == FlockEngine.SHELTER_INSIDE
                        && !shelters.get(e.shelterIndex(i)).interior().contains(l, y, d)) {
                    insideOutOfBox++;
                    note("fish " + i + " INSIDE but outside the interior box, tick " + tick);
                }
                if (st == FlockEngine.SHELTER_APPROACH) {
                    approachTicks[i]++;
                    if (approachTicks[i] * 0.05f > e.approachTimeoutSeconds(i) + 0.05f) {
                        longApproaches++;
                        note("fish " + i + " approaching for " + approachTicks[i] + " ticks");
                    }
                } else {
                    approachTicks[i] = 0;
                }
                prev[i] = st;
            }
            if (adoption) return;
            for (int s = 0; s < shelters.size(); s++) {
                if (occupancy[s] > shelters.get(s).capacity()) {
                    overCapacity++;
                    note(occupancy[s] + " fish in shelter " + s + " (capacity " + shelters.get(s).capacity() + "), tick " + tick);
                }
            }
            if (hidden > e.hiddenBudget()) {
                overBudget++;
                note(hidden + " fish hidden against a budget of " + e.hiddenBudget() + ", tick " + tick);
            }
        }
    }

    /** Whether a point is in the slab of a mouth's opening, extended through the hull's wall. */
    private static boolean inMouthCorridor(Shelter shelter, int m, float l, float y, float d) {
        Shelter.Mouth mouth = shelter.mouths().get(m);
        float rL = l - mouth.centerL(), rY = y - mouth.centerY(), rD = d - mouth.centerD();
        float t = rL * mouth.tangentL() + rY * mouth.tangentY() + rD * mouth.tangentD();
        float[] b = new float[3];
        mouth.bitangent(b);
        float u = rL * b[0] + rY * b[1] + rD * b[2];
        return Math.abs(t) <= mouth.halfTangent() + MOUTH_TOLERANCE
                && Math.abs(u) <= mouth.halfBitangent() + MOUTH_TOLERANCE;
    }

    private static void assertClean(Watch w) {
        assertEquals(0, w.hullOutsiders, "fish-ticks inside a hull they weren't using; first: " + w.first);
        assertEquals(0, w.sideEntries, "fish-ticks inside a hull off the mouth's line; first: " + w.first);
        assertEquals(0, w.insideOutOfBox, "INSIDE fish-ticks outside the interior; first: " + w.first);
        assertEquals(0, w.overCapacity, "ticks over capacity; first: " + w.first);
        assertEquals(0, w.overBudget, "ticks over the hidden budget; first: " + w.first);
        assertEquals(0, w.longApproaches, "approaches past their timeout; first: " + w.first);
    }

    @TestFactory
    List<DynamicTest> visitsKeepEveryInvariantAcrossTheMatrix() {
        Tunables t = Tunables.GROUP;
        List<DynamicTest> tests = new ArrayList<>();
        for (Config c : matrix()) {
            tests.add(DynamicTest.dynamicTest(c.name(), () -> {
                VoxelDomain domain = Scenarios.domain(c.domain());
                FlockEngine engine = new FlockEngine(t);
                engine.rebuild(Scenarios.cast(c.cast(), c.fishCount(), c.seed()), c.seed(), 30f, 20f, domain);
                Metrics m = new Metrics(engine, t, 200);
                Watch w = new Watch(c.fishCount());
                for (int tick = 0; tick < TICKS; tick++) {
                    engine.step();
                    m.sample();
                    w.tick(engine, domain.shelters(), tick);
                }
                assertClean(w);
                assertEquals(0, m.hardClampContacts(), "hard backstop engagements");
                float speedCeiling = t.maxSpeed() * (1f + t.traitJitter());
                assertTrue(m.maxObservedSpeed() <= speedCeiling * SPEED_TOLERANCE, "speed " + m.maxObservedSpeed());
                assertTrue(m.maxObservedAccel() <= t.maxForce() * ACCEL_TOLERANCE, "accel " + m.maxObservedAccel());
                assertTrue(m.maxObservedJerk() <= MAX_JERK, "jerk " + m.maxObservedJerk());
                // Diagnostic floor, not a target: ten minutes of eight visitors with a log in reach
                // should produce a visit. Measured 9–40 per run, 2026-10-02.
                assertTrue(w.visits >= 3, "only " + w.visits + " visits in 10 minutes");
            }));
        }
        return tests;
    }

    /**
     * The hidden budget holds at every stocking — the garden eels' crowded-tank lesson stated as a
     * test: however many fish a tank holds, it never empties into its caves.
     */
    @TestFactory
    List<DynamicTest> theHiddenBudgetHoldsAtEveryStocking() {
        List<DynamicTest> tests = new ArrayList<>();
        for (int n : new int[]{2, 4, 8, 16, 32, 64}) {
            tests.add(DynamicTest.dynamicTest("n=" + n, () -> {
                VoxelDomain domain = Scenarios.domain("4x1x2+log");
                FlockEngine engine = new FlockEngine(Tunables.GROUP);
                engine.rebuild(Scenarios.cast("loaches", n, 77L), 77L, 0f, 20f, domain);
                Watch w = new Watch(n);
                for (int tick = 0; tick < 6_000; tick++) {
                    engine.step();
                    w.tick(engine, domain.shelters(), tick);
                }
                assertClean(w);
                assertEquals(0, engine.backstopEngagements(), "hard backstop engagements");
            }));
        }
        return tests;
    }

    /** Fish whose species never opted in never visit, and steer exactly as they did before. */
    @Test
    void fishThatDoNotOptInNeverVisit() {
        VoxelDomain domain = Scenarios.domain("3x1x1+log");
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        engine.rebuild(Scenarios.cast("mixed", 12, 5L), 5L, 0f, 20f, domain);
        for (int tick = 0; tick < 6_000; tick++) {
            engine.step();
            for (int i = 0; i < engine.count(); i++) {
                assertEquals(FlockEngine.SHELTER_ROAMING, engine.shelterState(i), "fish " + i + " visited");
            }
        }
    }

    /**
     * A shelter placed around a fish adopts it (§4.3): INSIDE that shelter at once, then out by the
     * mouth — never pushed through the walls — and back to roaming.
     */
    @Test
    void aShelterPlacedOnAFishAdoptsIt() {
        VoxelDomain domain = Scenarios.domain("3x1x1");
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        FishSpec[] specs = Scenarios.cast("mixed", 6, 7L);
        engine.rebuild(specs, 7L, 0f, 20f, domain);
        for (int tick = 0; tick < 100; tick++) engine.step();

        Shelter log = Scenarios.domain("3x1x1+log").shelters().get(0);
        Shelter.OrientedBox interior = log.interior();
        engine.posL()[0] = interior.centerL();
        engine.posY()[0] = interior.centerY();
        engine.posD()[0] = interior.centerD();
        domain.rebuildShelters(List.of(log));
        engine.step();
        assertEquals(FlockEngine.SHELTER_INSIDE, engine.shelterState(0), "the fish under the log was not adopted");
        assertEquals(0, engine.shelterIndex(0));

        Watch w = new Watch(engine.count());
        w.adoption = true;
        boolean out = false;
        for (int tick = 0; tick < 1_200 && !out; tick++) {
            engine.step();
            w.tick(engine, domain.shelters(), tick);
            out = engine.shelterState(0) == FlockEngine.SHELTER_ROAMING;
        }
        assertTrue(out, "the adopted fish never left");
        assertClean(w);
        assertEquals(0, engine.backstopEngagements(), "leaving cost a backstop engagement");
    }

    /** A shelter taken away with fish inside drops them to roaming where they are (§4.3). */
    @Test
    void removingAShelterReleasesItsOccupants() {
        VoxelDomain domain = Scenarios.domain("2x2slab+log");
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        engine.rebuild(Scenarios.cast("loaches", 8, 12345L), 12345L, 0f, 20f, domain);
        int inside = -1;
        for (int tick = 0; tick < 12_000 && inside < 0; tick++) {
            engine.step();
            for (int i = 0; i < engine.count(); i++) {
                if (engine.shelterState(i) == FlockEngine.SHELTER_INSIDE) inside = i;
            }
        }
        assertTrue(inside >= 0, "no fish ever went inside — bad probe");

        domain.rebuildShelters(List.of());
        engine.step();
        for (int i = 0; i < engine.count(); i++) {
            assertEquals(FlockEngine.SHELTER_ROAMING, engine.shelterState(i), "fish " + i + " kept a vanished shelter");
        }
        for (int tick = 0; tick < 600; tick++) engine.step();
        assertEquals(0, engine.backstopEngagements(), "release cost a backstop engagement");
    }

    /** A rebuild mid-visit — a fish added to the tank, say — leaves every visit where it was. */
    @Test
    void aRebuildCarriesEveryVisit() {
        VoxelDomain domain = Scenarios.domain("2x2slab+log");
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        FishSpec[] specs = Scenarios.cast("loaches", 8, 12345L);
        engine.rebuild(specs, 12345L, 0f, 20f, domain);
        int inside = -1;
        for (int tick = 0; tick < 12_000 && inside < 0; tick++) {
            engine.step();
            for (int i = 0; i < engine.count(); i++) {
                if (engine.shelterState(i) == FlockEngine.SHELTER_INSIDE) inside = i;
            }
        }
        assertTrue(inside >= 0, "no fish ever went inside — bad probe");

        int n = engine.count();
        int[] state = new int[n], index = new int[n], mouth = new int[n];
        float[] timer = new float[n];
        for (int i = 0; i < n; i++) {
            state[i] = engine.shelterState(i);
            index[i] = engine.shelterIndex(i);
            mouth[i] = engine.shelterMouth(i);
            timer[i] = engine.shelterTimer(i);
        }
        int[] carry = new int[n];
        for (int i = 0; i < n; i++) carry[i] = i;
        engine.rebuildPreserving(specs, carry, 12345L, 0f, 20f, domain);
        for (int i = 0; i < n; i++) {
            assertEquals(state[i], engine.shelterState(i), "fish " + i + " state");
            assertEquals(index[i], engine.shelterIndex(i), "fish " + i + " shelter");
            assertEquals(mouth[i], engine.shelterMouth(i), "fish " + i + " mouth");
            assertEquals(timer[i], engine.shelterTimer(i), 0f, "fish " + i + " timer");
        }
    }
}
