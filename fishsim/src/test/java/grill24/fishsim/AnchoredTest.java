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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The burrow's own invariants (docs/fish-sim-locomotion.md §3.4) — the set that replaces
 * {@code ANCHORED}'s "never moves" row in {@link LocomotionTest}, and the last class to get one.
 *
 * <p>The load-bearing one is {@link #anEelsFootprintNeverMoves}: the whole class is defined by the
 * position <i>not</i> changing, so the assertion is bitwise and runs alongside a live swimmer —
 * every other model here would have been perfectly happy to nudge it.
 *
 * <p>The threat trigger is driven by placing the threat by hand rather than by waiting for a
 * roaming swimmer to happen past. A test that waits measures the scatter, not the model.
 */
class AnchoredTest {

    private static final float SURFACE_Y = 0.125f - 0.5f;

    private static FishSpec eel(float length, int species) {
        return new FishSpec(length, Locomotion.ANCHORED, false, species);
    }

    private static FloorField tank(boolean[] blockedCells) {
        return FloorField.flat(1, 1, -0.5f, -0.5f, SURFACE_Y, blockedCells);
    }

    /** Marks floor cells (row-major over the 3×3 grid) as taken by a cosmetic. */
    private static boolean[] blocked(int... cells) {
        boolean[] mask = new boolean[FloorField.SUBCELLS * FloorField.SUBCELLS];
        for (int cell : cells) mask[cell] = true;
        return mask;
    }

    private static FlockEngine colony(FloorField floor, FishSpec... specs) {
        FlockEngine engine = new FlockEngine(Tunables.DEFAULT);
        engine.rebuild(specs, 4242L, 0f, 3, 0.35f, 0.3f, 20f, floor);
        return engine;
    }

    /** Parks the threat exactly where the test wants it, overriding whatever its own model did. */
    private static void park(FlockEngine engine, int i, float l, float y, float d) {
        engine.posL()[i] = l;
        engine.posY()[i] = y;
        engine.posD()[i] = d;
    }

    private static float retractOf(FlockEngine engine, int i) {
        engine.interpolate(1f);
        return engine.renderShape[i];
    }

    /**
     * The definition of the class. Bitwise, for the whole run, with a free swimmer alongside so
     * the eel is exposed to a live flock's separation term rather than sitting in an empty tank.
     */
    @Test
    void anEelsFootprintNeverMoves() {
        FlockEngine engine = colony(tank(null),
                new FishSpec(0.10f, Locomotion.FREE_SWIM, false, 0),
                eel(0.12f, 1),
                eel(0.14f, 1));
        int[] bits = new int[6];
        for (int i = 1; i <= 2; i++) {
            bits[(i - 1) * 3] = Float.floatToRawIntBits(engine.posL()[i]);
            bits[(i - 1) * 3 + 1] = Float.floatToRawIntBits(engine.posY()[i]);
            bits[(i - 1) * 3 + 2] = Float.floatToRawIntBits(engine.posD()[i]);
        }
        for (int tick = 0; tick < 2_000; tick++) {
            engine.step();
            for (int i = 1; i <= 2; i++) {
                assertEquals(bits[(i - 1) * 3], Float.floatToRawIntBits(engine.posL()[i]),
                        "an eel drifted laterally at tick " + tick);
                assertEquals(bits[(i - 1) * 3 + 1], Float.floatToRawIntBits(engine.posY()[i]),
                        "an eel drifted vertically at tick " + tick);
                assertEquals(bits[(i - 1) * 3 + 2], Float.floatToRawIntBits(engine.posD()[i]),
                        "an eel drifted in depth at tick " + tick);
            }
        }
        assertNotEquals(0f, engine.speed[0], "the control swimmer never moved");
    }

    /**
     * An eel stands on the sand, not in the water — and it does so whether or not it passed its
     * own gate, because the renderer stopped pinning {@code planted}'s Y in this phase and a
     * demoted eel left at its scatter height would simply hover there.
     */
    @Test
    void anEelIsPlacedOnTheFloorGateOrNoGate() {
        FlockEngine passing = colony(tank(null), eel(0.12f, 0), eel(0.14f, 0));
        for (int i = 0; i < passing.count(); i++) {
            assertSame(Locomotion.ANCHORED, passing.locomotion[i]);
            assertEquals(SURFACE_Y, passing.posY()[i], 1e-6f, "an eel was not standing on the sand");
        }

        // Eight of the nine floor cells taken: one burrow's worth of sand is left, which is far
        // less than a creature this size needs, so the gate demotes it — and it still stands in
        // the cell that is left.
        FlockEngine demoted = colony(tank(blocked(0, 1, 2, 3, 4, 5, 6, 7)), eel(0.5f, 0));
        assertSame(Locomotion.STATIC, demoted.locomotion[0], "the anchor gate never fired");
        assertEquals(SURFACE_Y, demoted.posY()[0], 1e-6f, "a demoted eel left the sand");
    }

    /**
     * Down fast, up slow, and all the way back out — the whole animation. The asymmetry is the
     * character of the thing: an eel that re-emerged as fast as it withdrew would read as a
     * flicker, and one that never came back out would read as a bug.
     */
    @Test
    void aPassingFishStartlesAnEelAndItComesBackOut() {
        // A small threat, deliberately: this box's swim gate demotes anything much larger to
        // STATIC, and a fish that is not moving is not what startles an eel.
        FlockEngine engine = colony(tank(null), eel(0.05f, 0),
                new FishSpec(0.10f, Locomotion.FREE_SWIM, false, 1));
        float eelL = engine.posL()[0], eelY = engine.posY()[0], eelD = engine.posD()[0];

        // Nothing nearby: fully emerged, and staying that way.
        for (int tick = 0; tick < 200; tick++) {
            engine.step();
            park(engine, 1, eelL + 5f, eelY + 5f, eelD + 5f);
        }
        assertEquals(0f, retractOf(engine, 0), 1e-3f, "an undisturbed eel was not fully out");

        // A big fish arrives, and 0.3 s later the eel is most of the way into the sand.
        for (int tick = 0; tick < 6; tick++) {
            park(engine, 1, eelL + 0.05f, eelY + 0.05f, eelD);
            engine.step();
        }
        float retracted = retractOf(engine, 0);
        assertTrue(retracted > 0.7f, "the eel barely flinched in 0.3 s: " + retracted);

        // It leaves; the eel takes seconds rather than frames to come back out.
        park(engine, 1, eelL + 5f, eelY + 5f, eelD + 5f);
        for (int tick = 0; tick < 6; tick++) {
            engine.step();
            park(engine, 1, eelL + 5f, eelY + 5f, eelD + 5f);
        }
        assertTrue(retractOf(engine, 0) > 0.5f, "the eel popped straight back out");
        for (int tick = 0; tick < 100; tick++) {
            engine.step();
            park(engine, 1, eelL + 5f, eelY + 5f, eelD + 5f);
        }
        assertTrue(retractOf(engine, 0) < 0.1f, "the eel never came back out");
    }

    /**
     * Only something bigger than the eel is a threat, and only something that moves. A colony
     * where each eel startled its neighbour would sit permanently retracted, which is the failure
     * this rules out structurally rather than by choosing a radius nothing happens to be inside.
     */
    @Test
    void neitherASmallFishNorAStillOneStartlesAnEel() {
        FlockEngine engine = colony(tank(null), eel(0.30f, 0),
                new FishSpec(0.10f, Locomotion.FREE_SWIM, false, 1),
                eel(0.40f, 0),
                new FishSpec(0.40f, Locomotion.STATIC, false, 2));
        float eelL = engine.posL()[0], eelY = engine.posY()[0], eelD = engine.posD()[0];
        for (int tick = 0; tick < 400; tick++) {
            // Every one of them right on top of the eel, tick after tick.
            park(engine, 1, eelL, eelY, eelD + 0.05f);
            park(engine, 3, eelL, eelY, eelD - 0.05f);
            engine.step();
            assertEquals(0f, retractOf(engine, 0), 1e-3f,
                    "the eel retracted from something harmless at tick " + tick);
        }
        // The second eel is bigger than the first and just as close: proof the size test is not
        // what is carrying this, and that anchored neighbours are excluded on their class.
        assertTrue(engine.lengths[2] > engine.lengths[0] * 1.2f);
    }

    /**
     * A carried eel keeps its burrow and the state of its withdrawal. Position matters most —
     * re-scattering a colony every time the tank's contents change would move every burrow — but
     * the retract rides along, so an eel that was halfway down does not pop back out on the tick a
     * fish was added.
     */
    @Test
    void aCarriedEelKeepsItsBurrowAndItsWithdrawal() {
        FishSpec[] specs = {
                eel(0.05f, 0),
                new FishSpec(0.10f, Locomotion.FREE_SWIM, false, 1),
        };
        FlockEngine engine = colony(tank(null), specs);
        float eelL = engine.posL()[0], eelY = engine.posY()[0], eelD = engine.posD()[0];
        for (int tick = 0; tick < 5; tick++) {
            park(engine, 1, eelL + 0.05f, eelY + 0.05f, eelD);
            engine.step();
        }
        float retracted = retractOf(engine, 0);
        assertTrue(retracted > 0.5f, "the eel was not withdrawing when it was carried");

        engine.rebuildPreserving(new FishSpec[]{specs[0]}, new int[]{0}, 4242L, 0f,
                3, 0.35f, 0.3f, 20f, tank(null));
        assertEquals(eelL, engine.posL()[0], "a carried eel's burrow moved");
        assertEquals(eelD, engine.posD()[0], "a carried eel's burrow moved");
        assertEquals(retracted, retractOf(engine, 0), 1e-6f, "a carried eel popped back out");
    }

    /**
     * The one that came from the game: in a big, well-stocked tank the eels sat permanently in the
     * sand and never came out.
     *
     * <p>The cause was the trigger being a <i>state</i> — "hold the retract up while something big
     * is nearby" — which is right in a quiet tank and wrong in a busy one, because past some
     * stocking density there is always a fish inside the radius. No radius or rate would have
     * fixed it: any threshold a crowded tank sits permanently above is the same bug at a different
     * fish count. The reaction is edge-triggered and habituates now, which bounds the duty cycle
     * structurally rather than by choosing a number.
     */
    @Test
    void aCrowdedTankDoesNotHoldTheEelsUnderground() {
        boolean[][][] occupancy = new boolean[4][2][3];
        for (boolean[][] column : occupancy) {
            for (boolean[] cell : column) {
                cell[0] = true;
                cell[1] = true;
                cell[2] = true;
            }
        }
        FishSpec[] specs = new FishSpec[22];
        specs[0] = eel(0.30f, 0);
        specs[1] = eel(0.32f, 0);
        for (int i = 2; i < specs.length; i++) {
            // Comfortably over both eels' threat threshold: what is being measured here is the
            // duty cycle, not which fish qualifies.
            specs[i] = new FishSpec(0.28f, Locomotion.FREE_SWIM, false, 1 + i % 3);
        }
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        engine.rebuild(specs, 4242L, 0f, 20f, new VoxelDomain(occupancy));

        int ticks = 6_000; // 5 minutes
        int[] hidden = new int[2];
        float[] mostOut = {1f, 1f};
        for (int tick = 0; tick < ticks; tick++) {
            engine.step();
            for (int i = 0; i < 2; i++) {
                float retract = retractOf(engine, i);
                if (retract > 0.5f) hidden[i]++;
                mostOut[i] = Math.min(mostOut[i], retract);
            }
        }
        for (int i = 0; i < 2; i++) {
            float duty = hidden[i] / (float) ticks;
            // Measured 16% and 29% here. The bound is the analytic worst case the two constants
            // allow at the unluckiest draw of their per-fish jitter (~36%), not the measurement —
            // a tighter number would be a test of this seed's timing jitter.
            assertTrue(duty < 0.40f, "eel " + i + " spent " + Math.round(duty * 100)
                    + "% of five minutes hidden in a crowded tank");
            assertTrue(duty > 0.02f, "eel " + i + " never reacted to a tank full of fish at all");
            assertTrue(mostOut[i] < 0.05f, "eel " + i + " never came fully out");
        }
    }

    /**
     * And the habituation is real: a threat that simply parks next to the burrow gets one reaction,
     * not a permanent one. This is the crowded-tank case reduced to two fish, where it is a
     * statement about the model rather than about a stocking density.
     */
    @Test
    void aThreatThatStaysGetsOneReactionNotAPermanentOne() {
        FlockEngine engine = colony(tank(null), eel(0.05f, 0),
                new FishSpec(0.10f, Locomotion.FREE_SWIM, false, 1));
        float eelL = engine.posL()[0], eelY = engine.posY()[0], eelD = engine.posD()[0];
        int hidden = 0;
        for (int tick = 0; tick < 2_000; tick++) {
            // Sitting right on top of the burrow for a hundred seconds, without ever leaving.
            park(engine, 1, eelL + 0.05f, eelY + 0.05f, eelD);
            engine.step();
            if (retractOf(engine, 0) > 0.5f) hidden++;
        }
        float duty = hidden / 2_000f;
        assertTrue(duty < 0.35f, "the eel stayed down for " + Math.round(duty * 100)
                + "% of the run with a threat parked on it");
        assertTrue(duty > 0.05f, "the parked threat startled it at most once");
    }
}
