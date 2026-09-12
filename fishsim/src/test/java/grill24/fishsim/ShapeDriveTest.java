package grill24.fishsim;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.FloorField;
import grill24.fishsim.domain.VoxelDomain;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The silhouette envelope that drives squash-and-stretch (docs/fish-sim-locomotion.md §3.6).
 *
 * <p>Everything here is plumbing rather than behaviour — the amplitude itself is an eye call, like
 * the {@code CRAWL_*} and {@code DRIFT_*} constants — but the plumbing has one genuinely
 * load-bearing property, {@link #theShapeEnvelopeRelaxesFasterThanTheDriveThatTriggersIt}: the
 * envelope that makes the <i>movement</i> read right is not the one that makes the
 * <i>silhouette</i> read right. Drift's drive decays over ~3.7 s because that is the coast; a bell
 * mapped straight onto it would snap shut and take four seconds to refill, which is not what a
 * jellyfish does. If the two envelopes ever collapse into one, this test is what says so.
 */
class ShapeDriveTest {

    private static FishSpec jelly(float length, int species) {
        return new FishSpec(length, Locomotion.DRIFT, false, species);
    }

    private static FishSpec crab(float length, int species) {
        return new FishSpec(length, Locomotion.BENTHIC, false, species);
    }

    /** A 3×2×1 group aquarium, as {@code DriftTest} uses: the planar path, with real headroom. */
    private static FlockEngine driftTank(FishSpec... specs) {
        boolean[][][] occupancy = new boolean[3][2][1];
        for (boolean[][] column : occupancy) {
            column[0][0] = true;
            column[1][0] = true;
        }
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        engine.rebuild(specs, 4242L, 0f, 20f, new VoxelDomain(occupancy));
        return engine;
    }

    private static FlockEngine crawlTank(FishSpec... specs) {
        FlockEngine engine = new FlockEngine(Tunables.DEFAULT);
        engine.rebuild(specs, 4242L, 0f, 3, 0.35f, 0.3f, 20f,
                FloorField.flat(1, 1, -0.5f, -0.5f, 0.125f - 0.5f, null));
        return engine;
    }

    /** The tick value of the envelope: {@code interpolate(1)} is the current tick by definition. */
    private static float driveOf(FlockEngine engine, int i) {
        engine.interpolate(1f);
        return engine.renderShape[i];
    }

    /**
     * A scale factor is multiplied into a creature's size, so a drive outside [0, 1] is not a
     * subtle error — it is a jellyfish turning inside out. The integrator can only leave the range
     * if a rate ever exceeds 1/dt, which is a constant nobody would think to re-check.
     */
    @Test
    void theEnvelopeStaysInsideItsRange() {
        for (FlockEngine engine : new FlockEngine[]{
                driftTank(jelly(0.30f, 0), jelly(0.36f, 0), jelly(0.28f, 1)),
                crawlTank(crab(0.12f, 0), crab(0.10f, 1))}) {
            for (int tick = 0; tick < 4_000; tick++) {
                engine.step();
                engine.interpolate(0.5f);
                for (int i = 0; i < engine.count(); i++) {
                    float d = engine.renderShape[i];
                    assertTrue(d >= 0f && d <= 1f,
                            "shape drive left [0,1] at tick " + tick + ": " + d);
                }
            }
        }
    }

    /**
     * The whole reason the shape has an envelope of its own. Measured as the time each takes to
     * fall back through half its peak after a pulse ends: if the shape's is not clearly the
     * shorter, the second envelope is not earning its arrays and something has quietly pointed it
     * back at the motion rates.
     */
    @Test
    void theShapeEnvelopeRelaxesFasterThanTheDriveThatTriggersIt() {
        FlockEngine engine = driftTank(jelly(0.30f, 0));
        // Ride one pulse up. Both envelopes share the attack, so they peak on the same tick; what
        // is being measured is what each of them does once the trigger lets go.
        float peak = 0f;
        for (int tick = 0; tick < 400 && peak < 0.8f; tick++) {
            engine.step();
            peak = driveOf(engine, 0);
        }
        assertTrue(peak >= 0.8f, "no pulse fired in 20 s; the harness, not the model, is wrong");

        // Time the decay from the tick the envelope actually turns over, not from the tick it
        // first read high: the trigger is still held for the rest of the contraction, and counting
        // that in would measure the pulse's duty cycle wearing a decay rate's name.
        float previous = peak;
        int falling = 0;
        for (int tick = 0; tick < 200 && falling == 0; tick++) {
            engine.step();
            float d = driveOf(engine, 0);
            if (d < previous) {
                peak = previous;
                falling = tick;
            }
            previous = d;
        }

        int halfLife = -1;
        for (int tick = 1; tick <= 200 && halfLife < 0; tick++) {
            engine.step();
            if (driveOf(engine, 0) < peak * 0.5f) halfLife = tick;
        }
        assertTrue(halfLife > 0, "the shape envelope never relaxed");
        // DRIFT_PULSE_DECAY_RATE 0.9/s puts the motion drive's own half-life near 15 ticks; the
        // shape's is bounded well under that rather than at a literal number, so tuning either
        // rate by eye does not have to come back here.
        assertTrue(halfLife < 10,
                "the shape envelope decays no faster than the motion drive (half-life "
                        + halfLife + " ticks)");
    }

    /**
     * The interpolated mirror is the whole point of putting this in the engine: a raw tick value
     * strobes at 20 Hz against the frame rate and reads as a stutter rather than a squeeze. At
     * partial tick 0 and 1 it must land exactly on the two tick values it is bridging.
     */
    @Test
    void theRenderMirrorBridgesExactlyTheTwoTickValues() {
        FlockEngine engine = driftTank(jelly(0.30f, 0), jelly(0.36f, 0));
        engine.step();
        engine.interpolate(1f);
        float[] previous = engine.renderShape.clone();
        for (int tick = 0; tick < 120; tick++) {
            engine.step();
            engine.interpolate(1f);
            float[] current = engine.renderShape.clone();

            engine.interpolate(0f);
            for (int i = 0; i < engine.count(); i++) {
                assertEquals(previous[i], engine.renderShape[i],
                        "partial tick 0 is not last tick's value for fish " + i);
            }
            engine.interpolate(0.5f);
            for (int i = 0; i < engine.count(); i++) {
                assertEquals((previous[i] + current[i]) * 0.5f, engine.renderShape[i], 1e-6f,
                        "the midpoint is not halfway between the two ticks for fish " + i);
            }
            previous = current;
        }
    }

    /**
     * A fish that survives a rebuild keeps its deformation, for the same reason it keeps its
     * position: resetting the envelope would pop the creature back to its undeformed size on the
     * tick the tank's contents changed. {@code RebuildCarryTest} covers the motion drive; this is
     * the mirror that rides alongside it.
     */
    @Test
    void aCarriedDrifterKeepsItsDeformation() {
        FishSpec[] specs = {
                new FishSpec(0.10f, Locomotion.FREE_SWIM, false, 0),
                new FishSpec(0.30f, Locomotion.DRIFT, false, 1),
                new FishSpec(0.36f, Locomotion.DRIFT, true, 1),
        };
        boolean[][][] occupancy = new boolean[3][2][1];
        for (boolean[][] column : occupancy) {
            column[0][0] = true;
            column[1][0] = true;
        }
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        engine.rebuild(specs, 4242L, 0f, 20f, new VoxelDomain(occupancy));

        // Settle onto a tick where at least one bell is mid-pulse; carrying a relaxed one proves
        // nothing, since a reset envelope is also relaxed.
        float before = 0f;
        for (int tick = 0; tick < 400 && before < 0.2f; tick++) {
            engine.step();
            before = driveOf(engine, 1);
        }
        assertTrue(before >= 0.2f, "no bell was mid-pulse to carry");

        FishSpec[] remaining = {specs[1], specs[2]};
        engine.rebuildPreserving(remaining, new int[]{1, 2}, 4242L, 0f, 20f, new VoxelDomain(occupancy));
        assertEquals(before, driveOf(engine, 0), 1e-6f, "a carried bell lost its deformation");
    }

    /** A class with no shape envelope of its own never gains one — it would deform on nothing. */
    @Test
    void aFreeSwimmerNeverDeforms() {
        FlockEngine engine = driftTank(new FishSpec(0.10f, Locomotion.FREE_SWIM, false, 0),
                jelly(0.30f, 1));
        for (int tick = 0; tick < 600; tick++) {
            engine.step();
            engine.interpolate(0.5f);
            assertEquals(0f, engine.renderShape[0], "a free swimmer picked up a shape envelope");
        }
        assertNotEquals(0f, driveOf(engine, 1), "the drifter alongside it never pulsed either");
    }
}
