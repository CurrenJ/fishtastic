package grill24.fishsim;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.VoxelDomain;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The drift model's own invariants (docs/fish-sim-locomotion.md §3.2) — the set that replaces
 * {@code DRIFT}'s "never moves" row in {@link LocomotionTest} now that it has a motion model.
 *
 * <p>The load-bearing one is {@link #theVerticalCycleStaysCentred}: pulse-and-sink is an open
 * cycle whose two halves are tuned against each other, so the failure mode is not a crash but a
 * jellyfish that slowly climbs to the lid (or settles on the sand) and stays there — visible only
 * after minutes, which is exactly the kind of thing an eye-check misses and a test does not.
 */
class DriftTest {

    private static final int TICKS = 4_000; // 200 s — several dozen pulse cycles

    private static FishSpec jelly(float length, int species) {
        return new FishSpec(length, Locomotion.DRIFT, false, species);
    }

    /** A single tank: the legacy box model, whose vertical band is only 0.3 blocks tall here. */
    private static FlockEngine boxTank(FishSpec... specs) {
        FlockEngine engine = new FlockEngine(Tunables.DEFAULT);
        engine.rebuild(specs, 4242L, 0f, 3, 0.35f, 0.3f, 20f);
        return engine;
    }

    /** A 3×2×1 group aquarium: the planar/voxel path, with real headroom. */
    private static FlockEngine groupTank(FishSpec... specs) {
        boolean[][][] occupancy = new boolean[3][2][1];
        for (boolean[][] column : occupancy) {
            column[0][0] = true;
            column[1][0] = true;
        }
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        engine.rebuild(specs, 4242L, 0f, 20f, new VoxelDomain(occupancy));
        return engine;
    }

    /** A drifter never leaves the swim volume, and never needs the hard backstop to stay in it. */
    @Test
    void driftersStayInsideTheDomain() {
        for (FlockEngine engine : new FlockEngine[]{
                boxTank(jelly(0.10f, 0), jelly(0.12f, 0), jelly(0.09f, 1)),
                groupTank(jelly(0.30f, 0), jelly(0.36f, 0), jelly(0.28f, 1))}) {
            for (int tick = 0; tick < TICKS; tick++) {
                engine.step();
                for (int i = 0; i < engine.count(); i++) {
                    assertTrue(engine.domain().contains(engine.posL()[i], engine.posY()[i], engine.posD()[i]),
                            "a drifter left the domain at tick " + tick);
                }
            }
            assertEquals(0, engine.backstopEngagements(),
                    "soft containment let a drifter reach the hard backstop");
        }
    }

    /**
     * Pulse-and-sink neither climbs nor settles. Asserted over the whole run rather than at the
     * end, because a drifter that pins to the lid for a minute and comes back down would pass an
     * endpoint check.
     */
    @Test
    void theVerticalCycleStaysCentred() {
        FlockEngine engine = groupTank(jelly(0.30f, 0), jelly(0.36f, 0), jelly(0.28f, 1));
        float lo = engine.domain().minVertical(), hi = engine.domain().maxVertical();
        float span = hi - lo;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        double sum = 0;
        int samples = 0;
        for (int tick = 0; tick < TICKS; tick++) {
            engine.step();
            for (int i = 0; i < engine.count(); i++) {
                float y = engine.posY()[i];
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                sum += y;
                samples++;
            }
        }
        float mean = (float) (sum / samples);
        float centre = (lo + hi) * 0.5f;
        assertTrue(Math.abs(mean - centre) < span * 0.2f,
                "the pulse/sink balance has a net bias: mean Y " + mean + " vs centre " + centre);
        // And it is a cycle, not a hover: the creature visibly rises and falls.
        assertTrue(maxY - minY > span * 0.2f,
                "drifters barely moved vertically: swept " + (maxY - minY) + " of " + span);
    }

    /** The horizontal motion is a current, not a hover — a drifter is carried somewhere. */
    @Test
    void driftersAreCarriedHorizontally() {
        FlockEngine engine = groupTank(jelly(0.30f, 0), jelly(0.36f, 0), jelly(0.28f, 1));
        float[] startL = engine.posL().clone(), startD = engine.posD().clone();
        float furthest = 0f;
        for (int tick = 0; tick < TICKS; tick++) {
            engine.step();
            for (int i = 0; i < engine.count(); i++) {
                float dl = engine.posL()[i] - startL[i], dd = engine.posD()[i] - startD[i];
                furthest = Math.max(furthest, (float) Math.sqrt(dl * dl + dd * dd));
            }
        }
        assertTrue(furthest > 0.25f, "no drifter went anywhere horizontally: best " + furthest);
    }

    /** Bells push apart: two drifters never end up sharing a position. */
    @Test
    void driftersDoNotOverlap() {
        FlockEngine engine = groupTank(jelly(0.30f, 0), jelly(0.30f, 0), jelly(0.30f, 0), jelly(0.30f, 0));
        float worst = Float.MAX_VALUE;
        for (int tick = 0; tick < TICKS; tick++) {
            engine.step();
            for (int i = 0; i < engine.count(); i++) {
                for (int j = i + 1; j < engine.count(); j++) {
                    float dl = engine.posL()[i] - engine.posL()[j];
                    float dy = engine.posY()[i] - engine.posY()[j];
                    float dd = engine.posD()[i] - engine.posD()[j];
                    worst = Math.min(worst, (float) Math.sqrt(dl * dl + dy * dy + dd * dd));
                }
            }
        }
        assertTrue(worst > 0.1f, "two drifters overlapped: closest approach " + worst);
    }

    /**
     * The animation clock stays put. A drifter's pose is a bob and a spin on game time, so a
     * running {@code tailPhase} would double-drive it — this is the half of
     * {@code LocomotionTest.unsimulatedClassesDoNotAdvanceTheAnimationClock} that survives DRIFT
     * gaining a motion model, and the reason it moves here rather than being deleted.
     */
    @Test
    void driftingDoesNotAdvanceTheTailBeatClock() {
        FlockEngine engine = boxTank(
                new FishSpec(0.10f, Locomotion.FREE_SWIM, false, 0),
                jelly(0.10f, 1));
        for (int tick = 0; tick < TICKS; tick++) engine.step();
        engine.interpolate(0.5f);
        assertEquals(0f, engine.renderPhase[1], "a drifter advanced the tail-beat clock");
    }

    /**
     * A drifter drifts — it does not swim. §6 names this one explicitly, and it is the tuning
     * failure most likely to creep in: raise DRIFT_SPEED far enough to make jellyfish "look
     * lively" and they stop reading as passive at all.
     *
     * <p>Measured as displacement per second sustained over the run, not as a peak. A drifter
     * pressed against the glass takes a containment shove several times its own drift speed for a
     * tick or two, and that is the term working, not the creature swimming — a peak bound would
     * be a test of the wall margin wearing a drift test's name.
     */
    @Test
    void aDrifterNeverSwims() {
        FlockEngine engine = groupTank(jelly(0.30f, 0), jelly(0.36f, 0), jelly(0.28f, 1));
        int n = engine.count();
        double[] path = new double[n];
        float peak = 0f;
        for (int tick = 0; tick < TICKS; tick++) {
            float[] beforeL = engine.posL().clone(), beforeD = engine.posD().clone();
            engine.step();
            for (int i = 0; i < n; i++) {
                float dl = engine.posL()[i] - beforeL[i], dd = engine.posD()[i] - beforeD[i];
                path[i] += Math.sqrt(dl * dl + dd * dd);
                float vl = engine.velL()[i], vd = engine.velD()[i];
                peak = Math.max(peak, (float) Math.sqrt(vl * vl + vd * vd));
            }
        }
        float seconds = TICKS * Tunables.GROUP.dt();
        float cruise = Tunables.GROUP.cruiseSpeed();
        for (int i = 0; i < n; i++) {
            float mean = (float) (path[i] / seconds);
            assertTrue(mean < cruise * 0.5f, "drifter " + i + " averaged " + mean
                    + " blocks/s horizontally, half of cruise (" + cruise + ") — that reads as swimming");
        }
        // And even the containment shove stays well under a swimmer's cruise.
        assertTrue(peak < cruise * 3f, "a drifter peaked at " + peak + " blocks/s horizontally");
    }

    /** Vertical speed is bounded by the pulse itself: nothing in the model can outrun a contraction. */
    @Test
    void verticalSpeedStaysUnderThePulseCap() {
        FlockEngine engine = groupTank(jelly(0.30f, 0), jelly(0.36f, 0), jelly(0.28f, 1));
        float fastest = 0f;
        for (int tick = 0; tick < TICKS; tick++) {
            engine.step();
            for (int i = 0; i < engine.count(); i++) {
                fastest = Math.max(fastest, Math.abs(engine.velY()[i]));
            }
        }
        // The pulse peak, plus the trait jitter that scales it, plus the separation and avoidance
        // shoves that ride on top — generous, because the point is that it is bounded at all.
        assertTrue(fastest < 0.25f, "vertical speed reached " + fastest + " blocks/s");
    }

    /** The drift gate measures headroom: a creature with no room to pulse is STATIC. */
    @Test
    void theDriftGateMeasuresHeadroom() {
        // Box vertical extent here is 2 × max(0.1, 0.3 × 0.5) = 0.3 blocks.
        FlockEngine engine = boxTank(jelly(0.10f, 0), jelly(0.49f, 1), jelly(0.51f, 2));
        assertSame(Locomotion.DRIFT, engine.locomotion[0]);
        assertSame(Locomotion.DRIFT, engine.locomotion[1], "0.6 × 0.49 < 0.3 — still fits");
        assertSame(Locomotion.STATIC, engine.locomotion[2], "0.6 × 0.51 > 0.3 — no room to pulse");
    }
}
