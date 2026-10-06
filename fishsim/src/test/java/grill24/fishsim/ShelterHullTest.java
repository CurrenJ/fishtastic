package grill24.fishsim;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Locomotion;
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
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shelter hulls are solid to swimmers (docs/fish-shelters.md §4): the hull invariant — a swimmer
 * not using a shelter is never inside its hull — over the voxel matrix with a Hollow Log in each
 * domain, alongside the containment, dynamics and stuck bounds {@link VoxelDomainTest} holds every
 * shelter-less domain to. The hard backstop does not know about hulls, so zero engagements here
 * still means the soft terms alone kept every fish both in the water and out of the log.
 */
class ShelterHullTest {

    // The same bounds as VoxelDomainTest's matrix: a hull may not loosen any of them.
    private static final float SPEED_TOLERANCE = 1.001f;
    private static final float ACCEL_TOLERANCE = 1.06f;
    private static final float MAX_JERK = 12f;
    private static final float MIN_PAIRWISE_FLOOR = 0.035f;
    private static final float MIN_WINDOW_DISPLACEMENT = 0.05f;
    private static final int WINDOW_TICKS = 600;
    private static final int TICKS = 10_000;

    private record Config(String domain, int fishCount, long seed) {
        String name() {
            return domain + " n=" + fishCount + " seed=" + seed;
        }

        /**
         * Too many fish for the water the log leaves, so the pairwise floor cannot hold — a fact
         * about stocking, not about hulls. The log takes about half of a one-block tank, and the
         * same tank with no log already breaks the floor at 25 fish (min 0.017, 2026-10-02; the
         * voxel matrix stops at 12). With the log, 12 fish measured 0.021–0.024 and 25 fish
         * 0.005–0.010, while in 3×1×1, where the log is a small share of the water, it changed
         * nothing (0.067–0.072 at 25 either way). Every other bound still applies here.
         */
        boolean overcrowded() {
            return domain.equals("1x1x1+log") && fishCount >= 12;
        }
    }

    private static List<Config> matrix() {
        List<Config> list = new ArrayList<>();
        for (String domain : new String[]{"1x1x1+log", "3x1x1+log", "L+log", "2x2slab+log", "4x1x2+log"}) {
            for (int n : new int[]{6, 12, 25}) {
                for (long seed : new long[]{12345L, -987654321L}) {
                    list.add(new Config(domain, n, seed));
                }
            }
        }
        return list;
    }

    private static FishSpec[] swimmerSpecs(int n, long seed) {
        Random r = new Random(seed * 31 + n);
        FishSpec[] specs = new FishSpec[n];
        for (int i = 0; i < n; i++) {
            specs[i] = new FishSpec(0.06f + r.nextFloat() * 0.2f, Locomotion.FREE_SWIM, r.nextBoolean(), r.nextInt(3));
        }
        return specs;
    }

    @TestFactory
    List<DynamicTest> swimmersStayOutOfHullsAcrossTheMatrix() {
        Tunables t = Tunables.GROUP;
        List<DynamicTest> tests = new ArrayList<>();
        for (Config c : matrix()) {
            tests.add(DynamicTest.dynamicTest(c.name(), () -> {
                VoxelDomain domain = Scenarios.domain(c.domain());
                List<Shelter> shelters = domain.shelters();
                assertEquals(1, shelters.size());
                FlockEngine engine = new FlockEngine(t);
                engine.rebuild(swimmerSpecs(c.fishCount(), c.seed()), c.seed(), 30f, 20f, domain);
                Metrics m = new Metrics(engine, t, 200);

                int n = c.fishCount();
                float[] winL = new float[n], winY = new float[n], winD = new float[n];
                float[] windowMax = new float[n];
                float minWindowDisp = Float.MAX_VALUE;
                int hullEntries = 0;
                String firstEntry = null;

                for (int tick = 0; tick < TICKS; tick++) {
                    if (tick % WINDOW_TICKS == 0) {
                        for (int i = 0; i < n; i++) {
                            if (tick > 0 && windowMax[i] < minWindowDisp) minWindowDisp = windowMax[i];
                            winL[i] = engine.posL()[i];
                            winY[i] = engine.posY()[i];
                            winD[i] = engine.posD()[i];
                            windowMax[i] = 0f;
                        }
                    }
                    engine.step();
                    m.sample();
                    for (int i = 0; i < n; i++) {
                        float l = engine.posL()[i], y = engine.posY()[i], d = engine.posD()[i];
                        for (int s = 0; s < shelters.size(); s++) {
                            if (engine.shelterUsing(i) == s) continue;
                            if (shelters.get(s).hull().contains(l, y, d)) {
                                hullEntries++;
                                if (firstEntry == null) firstEntry = "fish " + i + " at tick " + tick;
                            }
                        }
                        float dx = l - winL[i], dy = y - winY[i], dz = d - winD[i];
                        float disp = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (disp > windowMax[i]) windowMax[i] = disp;
                    }
                }

                assertEquals(0, hullEntries, "fish-ticks inside a hull; first: " + firstEntry);
                assertEquals(0, m.wallPenetrations(), "wall penetrations");
                assertEquals(0, m.hardClampContacts(), "hard backstop engagements");

                float speedCeiling = t.maxSpeed() * (1f + t.traitJitter());
                assertTrue(m.maxObservedSpeed() <= speedCeiling * SPEED_TOLERANCE, "speed " + m.maxObservedSpeed());
                assertTrue(m.maxObservedAccel() <= t.maxForce() * ACCEL_TOLERANCE, "accel " + m.maxObservedAccel());
                assertTrue(m.maxObservedJerk() <= MAX_JERK, "jerk " + m.maxObservedJerk());
                if (!c.overcrowded()) {
                    assertTrue(m.minPairwiseDist() >= MIN_PAIRWISE_FLOOR, "min pairwise " + m.minPairwiseDist());
                }
                assertTrue(minWindowDisp >= MIN_WINDOW_DISPLACEMENT,
                        "a fish moved only " + minWindowDisp + " blocks in a " + WINDOW_TICKS + "-tick window");
            }));
        }
        return tests;
    }

    /** No fish is ever scattered into a hull at rebuild. */
    @Test
    void scatterNeverPlacesAFishInAHull() {
        for (String name : new String[]{"1x1x1+log", "3x1x1+log"}) {
            VoxelDomain domain = Scenarios.domain(name);
            Shelter.OrientedBox hull = domain.shelters().get(0).hull();
            for (long seed = 1; seed <= 40; seed++) {
                FlockEngine engine = new FlockEngine(Tunables.GROUP);
                engine.rebuild(swimmerSpecs(25, seed), seed, 0f, 20f, domain);
                for (int i = 0; i < engine.count(); i++) {
                    assertTrue(!hull.contains(engine.posL()[i], engine.posY()[i], engine.posD()[i]),
                            name + " seed " + seed + ": fish " + i + " scattered inside the hull");
                }
            }
        }
    }

    /** A fish caught inside a hull (a log placed on top of it) is pushed out, never left there. */
    @Test
    void aFishInsideAHullIsPushedOut() {
        VoxelDomain empty = Scenarios.domain("3x1x1");
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        FishSpec[] specs = swimmerSpecs(6, 7L);
        engine.rebuild(specs, 7L, 0f, 20f, empty);
        // Drop the log onto fish 0 by teleporting the fish into where the log will be.
        Shelter log = Scenarios.domain("3x1x1+log").shelters().get(0);
        engine.posL()[0] = log.hull().centerL();
        engine.posY()[0] = log.hull().centerY() + 0.05f;
        engine.posD()[0] = log.hull().centerD();
        empty.rebuildShelters(List.of(log));

        int inside = 0;
        for (int tick = 0; tick < 200; tick++) {
            engine.step();
            if (log.hull().contains(engine.posL()[0], engine.posY()[0], engine.posD()[0])) inside = tick + 1;
        }
        assertTrue(inside < 100, "fish 0 was still inside the hull after " + inside + " ticks");
        assertEquals(0, engine.backstopEngagements(), "pushing it out cost a backstop engagement");
    }
}
