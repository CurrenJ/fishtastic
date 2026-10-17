package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import grill24.fishsim.harness.companion.OwnerScript;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Squid propulsion contracts, independent of the mind's choice of destination. */
class JetBodyTest {
    private final CompanionTunables t = CompanionTunables.defaults();
    private final LocalVoxels air = new LocalVoxels();

    private DriftBody squid() {
        air.update((x, y, z) -> false, new Vec(0, 3, 0));
        return new DriftBody(t, DriftStyle.JET, new Vec(0, 3, 0), 0, 0.15, 42);
    }

    @Test
    void finsHoverWithoutMantlePulses() {
        DriftBody b = squid();
        for (int i = 0; i < 400; i++) {
            b.step(Vec.ZERO, 0, Vec.ZERO, air, null);
            assertEquals(0, b.mantleThrust, 1e-9);
            assertFalse(b.strokeStart);
        }
        assertEquals(new Vec(0, 3, 0), b.pos);
        assertEquals(-90, b.pitch);
    }

    @Test
    void travelUsesMantleFirstStrokesAndCoastsBetweenThem() {
        DriftBody b = squid();
        int strokes = 0, coast = 0;
        Vec desired = new Vec(0, 0, 5);
        for (int i = 0; i < 400; i++) {
            b.step(desired, 0, Vec.ZERO, air, null);
            if (b.strokeStart) strokes++;
            if (b.mantleThrust > 1)
                assertTrue(Vec.fromYawPitch(b.yaw, b.pitch).dot(desired.normalize()) > 0.8);
            else coast++;
            assertTrue(b.tiltStep <= t.jetSlowTurnDegPerTick + 0.001);
        }
        assertTrue(strokes > 5);
        assertTrue(coast > 100);
        assertTrue(b.vel.z() > 4);
    }

    @Test
    void finsBrakeBeforeTheMantleHasTurnedAround() {
        DriftBody b = squid();
        b.vel = new Vec(0, 0, 6);
        b.step(new Vec(0, 0, -3), 0, Vec.ZERO, air, null);
        assertTrue(b.propulsion.z() < 0, "fins must counter the current travel immediately");
        assertTrue(b.vel.z() < 6);
        assertTrue(b.tiltStep <= t.jetSlowTurnDegPerTick);
    }

    @Test
    void aFadePlacementClearsThePendingJet() {
        DriftBody b = squid();
        for (int i = 0; i < 200 && b.mantleThrust == 0; i++)
            b.step(new Vec(0, 0, 6), 0, Vec.ZERO, air, null);
        assertTrue(b.mantleThrust > 0);
        b.placeAt(new Vec(0, 3, 0), 0);
        b.step(Vec.ZERO, 0, Vec.ZERO, air, null);
        assertEquals(0, b.mantleThrust);
        assertEquals(Vec.ZERO, b.vel);
        assertEquals(0, b.squeeze);
    }

    @Test
    void allOwnerScriptsKeepSquidFiniteAndOutsideSolids() {
        for (String script : OwnerScript.NAMES) {
            for (int seed = 1; seed <= 3; seed++) {
                CompanionRun run = CompanionRun.drifter(script, seed, DriftStyle.JET, Temperament.BASELINE, t, true);
                run.run(2400);
                var m = run.metrics();
                assertEquals(0, m.solidPenetrations(), script + "#" + seed);
                for (var s : run.samples) {
                    assertTrue(Double.isFinite(s.pos().lengthSqr()), script);
                    assertTrue(s.tiltRate() <= t.jetSlowTurnDegPerTick + 0.001, script);
                }
            }
        }
    }

    @Test
    void repeatedTurnsKeepTheSquidInView() {
        for (Temperament temperament : new Temperament[]{Temperament.BASELINE, Temperament.GHOST}) {
            double seen = 0;
            for (int seed = 1; seed <= 8; seed++) {
                CompanionRun run = run("about_face", seed, temperament);
                var m = run.metrics();
                assertTrue(m.seenFraction() >= 0.24, temperament.name() + "#" + seed + " " + m.summary());
                assertTrue(m.longestFarSeconds() <= 2, m.summary());
                seen += m.seenFraction();
            }
            assertTrue(seen / 8 >= 0.29, temperament.name() + " repeated-turn visibility=" + seen / 8);
        }
    }

    @Test
    void narrowScenesDoNotTradeKeepUpForRepeatedWallTouches() {
        for (Temperament temperament : new Temperament[]{Temperament.BASELINE, Temperament.GHOST}) {
            for (int seed = 1; seed <= 8; seed++) {
                CompanionRun door = run("doorway", seed, temperament);
                var d = door.metrics();
                assertTrue(d.backstops() <= 3, temperament.name() + "#" + seed + " " + d.summary());
                assertTrue(d.maxImpact() <= 0.5, d.summary());
                assertTrue(d.longestFarSeconds() <= 4, d.summary());
                var stairs = run("stairwell", seed, temperament).metrics();
                assertTrue(stairs.seenFraction() >= 0.06, stairs.summary());
                assertTrue(stairs.backstops() <= 3, stairs.summary());
            }
        }
    }

    @Test
    void sprintStopsNeverCoastIntoTheOwner() {
        for (Temperament temperament : new Temperament[]{Temperament.BASELINE, Temperament.GHOST}) {
            for (int seed = 1; seed <= 8; seed++) {
                CompanionRun run = run("sprint_stop", seed, temperament);
                assertEquals(0, run.ownerOverlap().stopEntries(), temperament.name() + "#" + seed);
                assertEquals(0, run.ownerOverlap().idleInsideFraction(), 1e-9);
                assertEquals(0, run.metrics().insideOwnerTicks(), run.metrics().summary());
            }
        }
    }

    private CompanionRun run(String script, int seed, Temperament temperament) {
        CompanionRun run = CompanionRun.drifter(script, seed, DriftStyle.JET, temperament, t, true);
        run.run(2400);
        return run;
    }
}
