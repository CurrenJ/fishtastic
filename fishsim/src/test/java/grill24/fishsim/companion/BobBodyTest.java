package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import grill24.fishsim.harness.companion.OwnerScript;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Man o' war (bob) propulsion contracts, independent of the mind's choice of destination (drift spec §6, §15). */
class BobBodyTest {
    private final CompanionTunables t = CompanionTunables.defaults();
    private final LocalVoxels air = new LocalVoxels();

    private DriftBody float_() {
        air.update((x, y, z) -> false, new Vec(0, 3, 0));
        return new DriftBody(t, DriftStyle.BOB, new Vec(0, 3, 0), 0, 0.15, 42);
    }

    @Test
    void hoversBobbingInPlaceWithoutShoving() {
        DriftBody b = float_();
        double lo = 3, hi = 3;
        for (int i = 0; i < 400; i++) {
            b.step(Vec.ZERO, 0, Vec.ZERO, air, null);
            assertEquals(0, b.lastThrust, 1e-9);
            assertEquals(0, b.pos.horizontal().length(), 1e-9);
            lo = Math.min(lo, b.pos.y());
            hi = Math.max(hi, b.pos.y());
        }
        assertTrue(hi - lo > 0.04, "it bobs: " + (hi - lo));
        assertTrue(hi - lo < 0.3, "only a little: " + (hi - lo));
        assertTrue(Math.abs(b.pos.y() - 3) < 0.15);
    }

    @Test
    void downstrokesShoveAlongTheSailAndNeverLift() {
        DriftBody b = float_();
        Vec desired = new Vec(0, 0, 4.3);
        int strokes = 0, coast = 0;
        double y0 = b.pos.y();
        double late = 0;
        for (int i = 0; i < 400; i++) {
            b.step(desired, 0, Vec.ZERO, air, null);
            if (i >= 360) late += b.vel.z() / 40;
            if (b.strokeStart) strokes++;
            assertEquals(0, b.propulsion.y(), 1e-12, "a shove is horizontal");
            if (b.lastThrust > 1e-6) {
                assertTrue(b.vel.y() < 0.5, "shoves come on the way down, vy=" + b.vel.y());
                assertTrue(Vec.fromYawPitch(b.yaw, 0).dot(b.propulsion.normalize()) > 0.999);
            } else coast++;
            assertTrue(b.tilt <= t.bobTiltMaxDeg + 1e-3);
            assertTrue(Math.abs(b.bank) <= t.bobTiltMaxDeg + 1e-3);
        }
        assertTrue(strokes > 10);
        assertTrue(coast > 100);
        assertTrue(late > 4.0, "holds a walk: " + late);
        assertTrue(b.pos.y() - y0 < 0.2, "no climb it didn't ask for: " + (b.pos.y() - y0));
    }

    @Test
    void aSprintOutrunsIt() {
        DriftBody b = float_();
        double late = 0;
        for (int i = 0; i < 400; i++) {
            b.step(new Vec(0, 0, 7), 0, Vec.ZERO, air, null);
            if (i >= 360) late += b.vel.z() / 40;
        }
        assertTrue(late > 4.8 && late < 6.2, "top speed " + late);
    }

    @Test
    void theSailTurnsAtACappedRateAndTheFloatRocksIntoIt() {
        DriftBody b = float_();
        for (int i = 0; i < 100; i++) b.step(new Vec(0, 0, 3), 0, Vec.ZERO, air, null);
        float maxBank = 0;
        for (int i = 0; i < 60; i++) {
            b.step(new Vec(3, 0, 0), 0, Vec.ZERO, air, null);
            assertTrue(b.tiltStep <= t.bobSailTurnDegPerTick + 1e-3);
            maxBank = Math.max(maxBank, Math.abs(b.bank));
        }
        assertTrue(maxBank > 3, "rocks into the turn: " + maxBank);
        assertTrue(Vec.fromYawPitch(b.yaw, 0).dot(new Vec(1, 0, 0)) > 0.95);
    }

    @Test
    void itsTentaclesBrakeWithoutShovingBack() {
        DriftBody b = float_();
        b.vel = new Vec(0, 0, 5);
        float maxFlare = 0;
        for (int i = 0; i < 20; i++) {
            b.step(Vec.ZERO, 0, Vec.ZERO, air, null);
            maxFlare = Math.max(maxFlare, b.flare);
            assertTrue(b.propulsion.z() <= 1e-9);
        }
        assertTrue(maxFlare > 0.5);
        t.driftFlare = false;
        DriftBody free = float_();
        free.vel = new Vec(0, 0, 5);
        for (int i = 0; i < 20; i++) free.step(Vec.ZERO, 0, Vec.ZERO, air, null);
        assertTrue(b.vel.z() < free.vel.z() - 1, "flared " + b.vel.z() + " vs " + free.vel.z());
    }

    @Test
    void aFadePlacementClearsThePendingShove() {
        DriftBody b = float_();
        for (int i = 0; i < 200 && b.lastThrust == 0; i++) b.step(new Vec(0, 0, 4), 0, Vec.ZERO, air, null);
        assertTrue(b.lastThrust > 0);
        b.placeAt(new Vec(0, 3, 0), 0);
        b.step(Vec.ZERO, 0, Vec.ZERO, air, null);
        assertEquals(0, b.lastThrust);
        assertEquals(0, b.squeeze);
    }

    @Test
    void disablingBobRestoresTheBellBitwise() {
        t.driftBob = false;
        DriftBody bob = float_();
        DriftBody bell = new DriftBody(t, DriftStyle.BELL, bob.pos, 0, 0.15, 42);
        for (int i = 0; i < 300; i++) {
            Vec ask = Vec.fromYawPitch(i * 2, Math.sin(i * 0.1) * 20).scale(3);
            bob.step(ask, 0, Vec.ZERO, air, null);
            bell.step(ask, 0, Vec.ZERO, air, null);
            assertEquals(bell.pos, bob.pos);
            assertEquals(bell.vel, bob.vel);
            assertEquals(bell.yaw, bob.yaw);
            assertEquals(bell.pitch, bob.pitch);
        }
    }

    @Test
    void allOwnerScriptsKeepTheFloatFiniteUprightAndOutsideSolids() {
        for (String script : OwnerScript.NAMES) {
            for (int seed = 1; seed <= 3; seed++) {
                CompanionRun run = CompanionRun.drifter(script, seed, DriftStyle.BOB, Temperament.BASELINE, t, true);
                run.run(2400);
                assertEquals(0, run.metrics().solidPenetrations(), script + "#" + seed);
                for (var s : run.samples) {
                    assertTrue(Double.isFinite(s.pos().lengthSqr()), script);
                    assertTrue(s.pitch() + 90 <= t.bobTiltMaxDeg + 1e-3, script + " tilt " + (s.pitch() + 90));
                }
            }
        }
    }
}
