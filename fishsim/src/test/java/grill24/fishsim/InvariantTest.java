package grill24.fishsim;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.harness.Metrics;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The smoothness guarantees as numbers (docs/fish-sim-engine-plan.md §3.1) — invariants 1–6, 8,
 * and 9 over a seeds × counts matrix on the single-box domain. Invariant 7 (L-domain traversal)
 * lands with the voxel domain; invariant 10 (drift guard) is {@link GoldenTrajectoryTest}.
 *
 * <p>Thresholds were calibrated by measuring current behavior over 10k-tick runs (20 config
 * combinations, 2026-08-22) and then tightened to sit just above the observed envelope — a
 * regression that reintroduces the flip-storm (was: flips every 2–3 s ≈ 3–5 per 10 s) or the
 * wall-jitter bug clears these bounds by an order of magnitude.
 */
class InvariantTest {

    // Observed envelope → bound. (max observed across the calibration matrix in parentheses.)
    private static final float SPEED_TOLERANCE = 1.001f;          // float rounding on the clamp (0.12000001)
    private static final float ACCEL_TOLERANCE = 1.02f;           // maxAccel stayed < maxForce (0.495)
    private static final float MAX_JERK = 12f;                    // blocks/s³ (8.45)
    private static final double MAX_FLIP_RATE_PER_10S = 2.5;      // flips/fish/10 s (1.98)
    private static final double MAX_NEAR_WALL_VELY_VARIANCE = 1.5e-3; // (6.6e-4)
    private static final double NN_MEAN_MIN = 0.08, NN_MEAN_MAX = 0.32; // (0.130 .. 0.271)
    private static final float MIN_PAIRWISE_FLOOR = 0.04f;        // (0.057)

    private record Config(String name, int fishCount, long seed) {}

    private static List<Config> matrix() {
        List<Config> list = new ArrayList<>();
        for (int n : new int[]{2, 6, 12, 25}) {
            for (long seed : new long[]{12345L, -987654321L, 31337L}) {
                list.add(new Config("n=" + n + " seed=" + seed, n, seed));
            }
        }
        return list;
    }

    private static FishSpec[] swimmerSpecs(int n, long seed) {
        Random r = new Random(seed * 31 + n);
        FishSpec[] specs = new FishSpec[n];
        for (int i = 0; i < n; i++) {
            specs[i] = new FishSpec(0.06f + r.nextFloat() * 0.2f, true, r.nextBoolean(), 0);
        }
        return specs;
    }

    private static FlockEngine run(int n, long seed, int ticks, Metrics metrics) {
        FlockEngine engine = new FlockEngine(Tunables.DEFAULT);
        engine.rebuild(swimmerSpecs(n, seed), seed, 30f, 3, 0.35f, 0.3f, 20f);
        for (int t = 0; t < ticks; t++) {
            engine.step();
            if (metrics != null) metrics.sample();
        }
        return engine;
    }

    /** Invariants 1–4 + 6 in one measured 10k-tick pass per config (metrics are cheap; runs are not). */
    @TestFactory
    List<DynamicTest> measuredInvariantsAcrossMatrix() {
        Tunables t = Tunables.DEFAULT;
        List<DynamicTest> tests = new ArrayList<>();
        for (Config c : matrix()) {
            tests.add(DynamicTest.dynamicTest(c.name(), () -> {
                FlockEngine probe = new FlockEngine(Tunables.DEFAULT);
                Metrics m = new Metrics(probe, t, 200);
                // Metrics needs the same engine it samples — rebuild it here.
                probe.rebuild(swimmerSpecs(c.fishCount(), c.seed()), c.seed(), 30f, 3, 0.35f, 0.3f, 20f);
                for (int tick = 0; tick < 10_000; tick++) {
                    probe.step();
                    m.sample();
                }

                // 1 — containment: zero penetrations, and the hard backstop clamp never engaged
                // (soft containment must be doing all the work — invariant 9 of the handoff).
                assertEquals(0, m.wallPenetrations(), "wall penetrations");
                assertEquals(0, m.hardClampContacts(), "hard clamp engagements (soft containment must stay soft)");

                // 2 — bounded dynamics: no darting, no snapping.
                assertTrue(m.maxObservedSpeed() <= t.maxSpeed() * SPEED_TOLERANCE,
                        "speed " + m.maxObservedSpeed() + " > " + t.maxSpeed() * SPEED_TOLERANCE);
                assertTrue(m.maxObservedAccel() <= t.maxForce() * ACCEL_TOLERANCE,
                        "accel " + m.maxObservedAccel() + " > " + t.maxForce() * ACCEL_TOLERANCE);
                assertTrue(m.maxObservedJerk() <= MAX_JERK,
                        "jerk " + m.maxObservedJerk() + " > " + MAX_JERK);

                // 3 — the flip-storm regression guard: bounded mirror-flip rate, hysteresis intact.
                assertTrue(m.maxFlipRatePer10s() <= MAX_FLIP_RATE_PER_10S,
                        "flip rate " + m.maxFlipRatePer10s() + "/10s > " + MAX_FLIP_RATE_PER_10S);
                assertEquals(0, m.deadzoneViolations(), "heading changed inside the deadzone");

                // 4 — the wall-jitter regression guard: vertical calm while cruising along glass.
                assertTrue(m.nearWallSamples() > 100, "config never got near a wall — bad probe");
                assertTrue(m.nearWallVelYVariance() <= MAX_NEAR_WALL_VELY_VARIANCE,
                        "near-wall velY variance " + m.nearWallVelYVariance() + " > " + MAX_NEAR_WALL_VELY_VARIANCE);

                // 6 — shoal shape: neither clumped nor scattered after warmup.
                double nn = m.meanNearestNeighborDist();
                assertTrue(nn >= NN_MEAN_MIN && nn <= NN_MEAN_MAX,
                        "mean NN dist " + nn + " outside [" + NN_MEAN_MIN + ", " + NN_MEAN_MAX + "]");
                assertTrue(m.minPairwiseDist() >= MIN_PAIRWISE_FLOOR,
                        "min pairwise " + m.minPairwiseDist() + " < " + MIN_PAIRWISE_FLOOR);
            }));
        }
        return tests;
    }

    /** Invariant 5 — determinism: identical seed + tunables ⇒ bitwise-identical trajectories. */
    @Test
    void identicalSeedsProduceBitwiseIdenticalTrajectories() {
        FlockEngine a = run(12, 424242L, 10_000, null);
        FlockEngine b = run(12, 424242L, 10_000, null);
        for (int i = 0; i < a.count(); i++) {
            assertEquals(Float.floatToRawIntBits(a.posL()[i]), Float.floatToRawIntBits(b.posL()[i]), "posL[" + i + "]");
            assertEquals(Float.floatToRawIntBits(a.posY()[i]), Float.floatToRawIntBits(b.posY()[i]), "posY[" + i + "]");
            assertEquals(Float.floatToRawIntBits(a.posD()[i]), Float.floatToRawIntBits(b.posD()[i]), "posD[" + i + "]");
        }
    }

    /**
     * Invariant 8 — the size-gate table: spec length × domain run decides swims/hovers exactly.
     * The single-box domain's shortest run is {@code 2 × tankHalfExtent}; a fish swims iff its
     * species can swim at all and {@code run ≥ gateFactor × length} — evaluated with the exact
     * float expression the engine uses, including the boundary case.
     */
    @Test
    void sizeGateMatchesTheTable() {
        Tunables t = Tunables.DEFAULT;
        float run = t.tankHalfExtent() * 2f; // 0.7
        float boundary = run / t.gateFactor(); // longest length that still swims

        FishSpec[] specs = {
                new FishSpec(0.05f, true, false, 0),                     // tiny → swims
                new FishSpec(boundary, true, false, 0),                  // exactly at the gate → swims
                new FishSpec(Math.nextUp(boundary), true, false, 0),     // one ulp over → hovers
                new FishSpec(0.5f, true, false, 0),                      // unmeasured-stack default → hovers
                new FishSpec(0.05f, false, false, 0),                    // tiny but hover-only species → hovers
        };
        boolean[] expected = {true, run >= t.gateFactor() * boundary, false, false, false};

        FlockEngine engine = new FlockEngine(t);
        engine.rebuild(specs, 99L, 0f, 3, 0.35f, 0.3f, 20f);
        for (int i = 0; i < specs.length; i++) {
            assertEquals(expected[i], engine.swimmers[i],
                    "gate verdict for length=" + specs[i].length() + " canSwim=" + specs[i].canSwim());
        }
    }

    /**
     * Invariant 9 — frame-rate independence: {@code interpolate} is pure with respect to sim time.
     * A run with many interleaved interpolate calls (several per step, like a 144 Hz client) is
     * bitwise-identical to a run with none, and interpolate itself never mutates sim state.
     */
    @Test
    void interpolateNeverAdvancesSimTime() {
        FlockEngine noInterp = run(8, 777L, 1_000, null);

        FlockEngine interp = new FlockEngine(Tunables.DEFAULT);
        interp.rebuild(swimmerSpecs(8, 777L), 777L, 30f, 3, 0.35f, 0.3f, 20f);
        Random alphas = new Random(1);
        for (int tick = 0; tick < 1_000; tick++) {
            interp.step();
            for (int f = 0; f < 7; f++) { // ~144 fps against 20 Hz ticks
                interp.interpolate(alphas.nextFloat());
            }
        }

        for (int i = 0; i < noInterp.count(); i++) {
            assertEquals(Float.floatToRawIntBits(noInterp.posL()[i]), Float.floatToRawIntBits(interp.posL()[i]),
                    "interpolate() advanced sim state at fish " + i);
            assertEquals(Float.floatToRawIntBits(noInterp.velL()[i]), Float.floatToRawIntBits(interp.velL()[i]),
                    "interpolate() mutated velocity at fish " + i);
        }
    }

    /** Invariant 9 corollary: hover fish (gate failures) never move — pixel-identical to today. */
    @Test
    void hoverFishNeverMove() {
        FishSpec[] specs = {
                new FishSpec(0.10f, true, false, 0),
                new FishSpec(0.45f, true, true, 0),   // gate failure → hovers
                new FishSpec(0.10f, false, false, 0), // floor-anchored → hovers
                new FishSpec(0.12f, true, true, 0),
        };
        FlockEngine engine = new FlockEngine(Tunables.DEFAULT);
        engine.rebuild(specs, 4242L, 15f, 3, 0.35f, 0.3f, 20f);

        assertFalse(engine.swimmers[1]);
        assertFalse(engine.swimmers[2]);
        float[] hoverL = {engine.posL()[1], engine.posL()[2]};
        float[] hoverY = {engine.posY()[1], engine.posY()[2]};
        float[] hoverD = {engine.posD()[1], engine.posD()[2]};
        boolean[] hoverMirror = {engine.hoverMirrored[1], engine.hoverMirrored[2]};

        for (int t = 0; t < 2_000; t++) engine.step();

        assertEquals(Float.floatToRawIntBits(hoverL[0]), Float.floatToRawIntBits(engine.posL()[1]));
        assertEquals(Float.floatToRawIntBits(hoverY[0]), Float.floatToRawIntBits(engine.posY()[1]));
        assertEquals(Float.floatToRawIntBits(hoverD[0]), Float.floatToRawIntBits(engine.posD()[1]));
        assertEquals(Float.floatToRawIntBits(hoverL[1]), Float.floatToRawIntBits(engine.posL()[2]));
        assertEquals(Float.floatToRawIntBits(hoverY[1]), Float.floatToRawIntBits(engine.posY()[2]));
        assertEquals(Float.floatToRawIntBits(hoverD[1]), Float.floatToRawIntBits(engine.posD()[2]));
        assertEquals(hoverMirror[0], engine.hoverMirrored[1]);
        assertEquals(hoverMirror[1], engine.hoverMirrored[2]);
    }
}
