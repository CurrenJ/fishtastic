package grill24.fishtastic.school;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The breach path: it picks up the swim it starts from, is smooth throughout, ballistic in the
 * air, pitched along its own velocity, and settles under the surface ahead of where it went in.
 */
class SurfaceJumpArcTest {

    private static final double SURFACE = 3.0, APEX = 0.5;
    /** A fish 1.1 blocks short of its launch point and 0.4 under, swimming at it at 0.08 blocks a tick. */
    private static final double PL = -1.1, PY = SURFACE - 0.4, PD = 0.0, VL = 0.08, VY = 0.01, VD = 0.0;

    private static SurfaceJumpArc arc(double reach) {
        return SurfaceJumpArc.of(PL, PY, PD, VL, VY, VD, 0.0, SURFACE, 0.0, 1.0, 0.0, APEX, reach, 1.0);
    }

    private static double[] at(SurfaceJumpArc arc, double tau) {
        return arc.sample(tau, new double[7]);
    }

    @Test
    void itStartsOnTheSwimItTakesOver() {
        SurfaceJumpArc arc = arc(1.0);
        double[] start = at(arc, 0.0);
        assertEquals(PL, start[0], 1e-9);
        assertEquals(PY, start[1], 1e-9);
        assertEquals(0.0, start[3], 1e-9, "no pitch the engine was not already drawing");
        assertEquals(0.0, start[5], 1e-9, "the engine's own yaw still rules");
        double h = 1e-5;
        double[] a = at(arc, -h), b = at(arc, h);
        assertEquals(VL, (b[0] - a[0]) / (2 * h), 1e-4, "it keeps the speed it was swimming at");
        assertEquals(VY, (b[1] - a[1]) / (2 * h), 1e-4);
    }

    @Test
    void itLeavesAndReentersAtTheSurfaceAndPeaksAtTheApex() {
        SurfaceJumpArc arc = arc(1.0);
        double[] exit = at(arc, arc.exitTick());
        assertEquals(0.0, exit[0], 1e-9, "it breaks the surface at the launch point");
        assertEquals(SURFACE, exit[1], 1e-9);
        assertEquals(SURFACE, at(arc, arc.entryTick() - 1e-9)[1], 1e-6);
        double mid = (arc.exitTick() + arc.entryTick()) / 2;
        assertEquals(SURFACE + APEX, at(arc, mid)[1], 1e-9, "the apex is the breach height above the surface");
        assertEquals(0.0, at(arc, mid)[3], 1e-9, "level at the apex");
        assertTrue(arc.durationTicks() < 64, "fits the server's budget: " + arc.durationTicks());
        assertEquals(arc.crossing(true)[0], at(arc, arc.entryTick())[0], 1e-6);
    }

    @Test
    void itTiltsBackGraduallyAndNosesDownGoingIn() {
        SurfaceJumpArc arc = arc(1.0);
        assertEquals(60.0, at(arc, arc.exitTick() - 1e-9)[3], 1e-3, "fully leaning back at the surface");
        double last = 0.0, worstStep = 0.0;
        for (double t = 0.0; t < arc.exitTick(); t += 0.25) {
            double pitch = at(arc, t)[3];
            worstStep = Math.max(worstStep, Math.abs(pitch - last));
            last = pitch;
        }
        assertTrue(worstStep < 8.0, "the lean grows smoothly through the run-in, worst step " + worstStep);
        assertEquals(-60.0, at(arc, arc.entryTick() + 1e-9)[3], 1e-3);
        for (double t = arc.entryTick(); t <= arc.durationTicks(); t += 0.5) {
            assertTrue(at(arc, t)[3] <= 1e-9, "never noses back up after landing, t=" + t);
        }
        assertEquals(0.0, at(arc, arc.durationTicks())[3], 3.0, "level when handed back");
    }

    @Test
    void positionAndVelocityAreContinuous() {
        SurfaceJumpArc arc = arc(1.0);
        double h = 1e-6;
        for (double edge : new double[]{0.0, arc.exitTick(), arc.entryTick()}) {
            double[] before = at(arc, edge - h), after = at(arc, edge + h);
            double[] b2 = at(arc, edge - 2 * h), a2 = at(arc, edge + 2 * h);
            for (int axis = 0; axis < 3; axis++) {
                assertEquals(before[axis], after[axis], 1e-5, "position axis " + axis + " at " + edge);
                assertEquals((before[axis] - b2[axis]) / h, (a2[axis] - after[axis]) / h, 1e-3,
                        "speed axis " + axis + " at " + edge);
            }
        }
    }

    @Test
    void itBuildsSpeedIntoTheLeapAndBleedsItAfterLanding() {
        SurfaceJumpArc arc = arc(1.0);
        double start = speed(arc, 0.01), exit = speed(arc, arc.exitTick() - 0.01);
        assertTrue(exit > 2 * start, "accelerates through the run-in: " + start + " -> " + exit);
        double entry = speed(arc, arc.entryTick() + 0.01), end = speed(arc, arc.durationTicks() - 0.01);
        assertTrue(end < 0.35 * entry, "slows after re-entry: " + entry + " -> " + end);
        double settled = SURFACE - at(arc, arc.durationTicks())[1];
        assertTrue(settled > 0.3 && settled < 0.7, "levels out a little under the surface: " + settled);
        assertTrue(at(arc, arc.durationTicks())[0] > arc.crossing(true)[0], "and still ahead of where it went in");
    }

    @Test
    void aShorterReachIsSteeperAndShorter() {
        SurfaceJumpArc full = arc(1.0), shorter = arc(0.3);
        assertTrue(at(shorter, shorter.durationTicks())[0] < at(full, full.durationTicks())[0]);
        assertTrue(at(shorter, shorter.exitTick() - 1e-9)[3] > at(full, full.exitTick() - 1e-9)[3]);
        assertEquals(SurfaceJumpArc.airReach(APEX), full.crossing(true)[0], 1e-9);
    }

    private static double speed(SurfaceJumpArc arc, double tau) {
        double[] a = at(arc, tau - 0.005), b = at(arc, tau + 0.005);
        return Math.sqrt((b[0] - a[0]) * (b[0] - a[0]) + (b[1] - a[1]) * (b[1] - a[1])
                + (b[2] - a[2]) * (b[2] - a[2])) / 0.01;
    }
}
