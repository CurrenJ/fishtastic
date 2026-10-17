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
        // The same float with a flare that brakes nothing: only plain drag slows it.
        t.driftFlareDrag = 0;
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

    /** Settled by a still owner (§16), a small gap behind it is drifted to, not turned round for. */
    @Test
    void calmDriftsToSmallGapsWithoutTurningTheSail() {
        DriftBody b = float_();
        b.idling = true;
        for (int i = 0; i < 200; i++) b.step(Vec.ZERO, 0, Vec.ZERO, air, null);
        assertEquals(1f, b.calm, 1e-6);
        float sail0 = b.tiltYaw, face0 = b.face;
        Vec behind = Vec.fromYawPitch(sail0, 0).scale(-0.25);
        for (int i = 0; i < 120; i++) {
            float faceBefore = b.face;
            b.step(behind, 0, Vec.ZERO, air, null);
            assertTrue(Math.abs(Body.wrap(b.face - faceBefore)) <= t.bobCalmFaceDegPerTick + 1e-3);
        }
        assertTrue(Math.abs(Body.wrap(b.tiltYaw - sail0)) < 10, "sail held: " + Body.wrap(b.tiltYaw - sail0));
        assertTrue(Math.abs(Body.wrap(b.face - face0)) < 10);
        assertTrue(b.vel.dot(behind) > 0.5 * behind.dot(behind), "drifts the asked way: " + b.vel);
    }

    /** Once its owner sets off it is the Phase 3 bob again within half a second: full sail rate, no drift. */
    @Test
    void calmLetsGoWhenItStopsIdling() {
        DriftBody b = float_();
        b.idling = true;
        for (int i = 0; i < 200; i++) b.step(Vec.ZERO, 0, Vec.ZERO, air, null);
        b.idling = false;
        Vec go = Vec.fromYawPitch(b.tiltYaw + 180, 0).scale(4.3);
        float fastest = 0;
        for (int i = 0; i < 40; i++) {
            b.step(go, 0, Vec.ZERO, air, null);
            if (i >= 10) assertEquals(0f, b.calm, 1e-6);
            fastest = Math.max(fastest, b.tiltStep);
        }
        assertTrue(fastest > t.bobCalmSailTurnDegPerTick + 1, "turns at the travelling rate: " + fastest);
    }

    /** The owner's complaint, measured: by a still owner the drawn float no longer spins round (§16). */
    @Test
    void calmIdleFacingIsCalmerThanPhase3() {
        double calm = idleFaceRate(true), phase3 = idleFaceRate(false);
        assertTrue(calm < 15, "calm idle turns " + calm + " deg/s");
        assertTrue(calm < 0.5 * phase3, "calm " + calm + " vs Phase 3 " + phase3);
    }

    private static double idleFaceRate(boolean bobCalm) {
        double sum = 0;
        int n = 0;
        for (int seed = 1; seed <= 4; seed++) {
            CompanionTunables t = CompanionTunables.defaults();
            t.bobCalm = bobCalm;
            CompanionRun run = CompanionRun.drifter("idle_fixed", seed, DriftStyle.BOB, Temperament.AGGRESSIVE, t, false);
            float last = run.agent.face();
            int still = 0;
            for (int i = 0; i < 2400; i++) {
                run.tick();
                still = run.owner.horizontalSpeed() < 0.2 ? still + 1 : 0;
                float f = run.agent.face();
                if (still >= 20 && run.agent.appear() >= 1f) {
                    sum += Math.abs(Body.wrap(f - last)) / CompanionAgent.DT;
                    n++;
                }
                last = f;
            }
        }
        return sum / Math.max(1, n);
    }
}
