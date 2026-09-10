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
 * <p>The startle is driven by the <b>watcher</b> — the one external point the host declares worth
 * hiding from, which on the Minecraft side is the local player's eye. Nothing inside the tank
 * startles an eel, and {@link #aTankFullOfFishNeverDisturbsTheEels} is there to keep it that way:
 * an earlier design reacted to passing fish, and in a well-stocked tank that meant a colony
 * permanently underground, because past some density there is always something in range.
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

    /** Runs the engine with the watcher held at one place — leaning over the tank, or gone. */
    private static void watch(FlockEngine engine, int ticks, Float l, Float y, Float d) {
        for (int tick = 0; tick < ticks; tick++) {
            if (l == null) {
                engine.setWatcher(false, 0f, 0f, 0f);
            } else {
                engine.setWatcher(true, l, y, d);
            }
            engine.step();
        }
    }

    /** Parks a fish exactly where the test wants it, overriding whatever its own model did. */
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
    void aWatcherLeaningOverTheTankStartlesAnEelAndItComesBackOut() {
        FlockEngine engine = colony(tank(null), eel(0.12f, 0));
        float eelL = engine.posL()[0], eelY = engine.posY()[0], eelD = engine.posD()[0];

        // Nobody there: fully out, and staying that way.
        watch(engine, 200, null, null, null);
        assertEquals(0f, retractOf(engine, 0), 1e-3f, "an unwatched eel was not fully out");

        // Someone walks up, and 0.3 s later the eel is most of the way into the sand.
        watch(engine, 6, eelL + 0.4f, eelY + 1.5f, eelD + 0.4f);
        float retracted = retractOf(engine, 0);
        assertTrue(retracted > 0.7f, "the eel barely flinched in 0.3 s: " + retracted);

        // They leave; the eel takes seconds rather than frames to come back out.
        watch(engine, 6, null, null, null);
        assertTrue(retractOf(engine, 0) > 0.5f, "the eel popped straight back out");
        watch(engine, 200, null, null, null);
        assertTrue(retractOf(engine, 0) < 0.1f, "the eel never came back out");
    }

    /**
     * Distance decides it, and it is measured from each burrow rather than from the tank — which
     * is what lets a colony spread down a long aquarium react where the watcher actually is.
     */
    @Test
    void aWatcherAcrossTheRoomStartlesNobody() {
        FlockEngine engine = colony(tank(null), eel(0.12f, 0));
        float eelL = engine.posL()[0], eelY = engine.posY()[0], eelD = engine.posD()[0];
        watch(engine, 600, eelL + 6f, eelY + 2f, eelD);
        assertEquals(0f, retractOf(engine, 0), 1e-3f, "an eel hid from someone across the room");
        watch(engine, 20, eelL + 0.4f, eelY + 1.5f, eelD);
        assertTrue(retractOf(engine, 0) > 0.7f, "and then failed to react to someone right there");
    }

    /**
     * Nothing that lives in the tank startles an eel — not a big fish, not a small one, not the
     * neighbour in the next burrow. A design statement rather than an omission: an eel sees its
     * tankmates all day, and a reaction to them is either constant or arbitrary depending only on
     * how well stocked the tank is. This is the regression for the version that reacted to fish
     * and left a busy tank's colony permanently underground.
     */
    @Test
    void aTankFullOfFishNeverDisturbsTheEels() {
        FlockEngine engine = colony(tank(null), eel(0.30f, 0),
                new FishSpec(0.10f, Locomotion.FREE_SWIM, false, 1),
                eel(0.40f, 0),
                new FishSpec(0.40f, Locomotion.STATIC, false, 2));
        float eelL = engine.posL()[0], eelY = engine.posY()[0], eelD = engine.posD()[0];
        for (int tick = 0; tick < 400; tick++) {
            // Every one of them right on top of the eel, tick after tick, with nobody watching.
            park(engine, 1, eelL, eelY, eelD + 0.05f);
            park(engine, 3, eelL, eelY, eelD - 0.05f);
            engine.setWatcher(false, 0f, 0f, 0f);
            engine.step();
            assertEquals(0f, retractOf(engine, 0), 1e-3f,
                    "the eel reacted to its own tankmates at tick " + tick);
        }
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
                eel(0.12f, 0),
                new FishSpec(0.10f, Locomotion.FREE_SWIM, false, 1),
        };
        FlockEngine engine = colony(tank(null), specs);
        float eelL = engine.posL()[0], eelY = engine.posY()[0], eelD = engine.posD()[0];
        watch(engine, 5, eelL + 0.4f, eelY + 1.5f, eelD);
        float retracted = retractOf(engine, 0);
        assertTrue(retracted > 0.5f, "the eel was not withdrawing when it was carried");

        engine.rebuildPreserving(new FishSpec[]{specs[0]}, new int[]{0}, 4242L, 0f,
                3, 0.35f, 0.3f, 20f, tank(null));
        assertEquals(eelL, engine.posL()[0], "a carried eel's burrow moved");
        assertEquals(eelD, engine.posD()[0], "a carried eel's burrow moved");
        assertEquals(retracted, retractOf(engine, 0), 1e-6f, "a carried eel popped back out");
    }

    /**
     * A long aquarium, an eel at each end, and someone standing at one of them. The near eel ducks
     * and the far one never notices — the property that makes this worth doing per burrow rather
     * than per tank, and the one a "is the player near this block entity" test would have thrown
     * away.
     */
    @Test
    void aWatcherAtOneEndOfTheAquariumLeavesTheOtherEndAlone() {
        boolean[][][] occupancy = new boolean[8][2][1];
        for (boolean[][] column : occupancy) {
            column[0][0] = true;
            column[1][0] = true;
        }
        FishSpec[] specs = {eel(0.30f, 0), eel(0.32f, 0)};
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        engine.rebuild(specs, 4242L, 0f, 20f, new VoxelDomain(occupancy));

        // The burrows are placed by hand rather than left to the scatter, which spaces a colony
        // by footprint and is perfectly entitled to put both eels at the same end.
        int near = 0, far = 1;
        park(engine, near, engine.domain().minLateral() + 0.5f, engine.posY()[near], 0f);
        park(engine, far, engine.domain().maxLateral() - 0.5f, engine.posY()[far], 0f);
        assertTrue(engine.posL()[far] - engine.posL()[near] > 3.5f,
                "the two burrows are not far enough apart for this to mean anything");

        // Twenty ticks, not four hundred: someone who stays gets one reaction and the eel then
        // comes back out past them (aWatcherWhoStaysGetsOneReactionNotAPermanentOne), so this has
        // to look while the reaction is actually happening.
        watch(engine, 20, engine.posL()[near] + 0.5f, engine.posY()[near] + 1.5f,
                engine.posD()[near]);
        assertTrue(retractOf(engine, near) > 0.7f, "the eel being leaned over did not react");
        assertEquals(0f, retractOf(engine, far), 1e-3f,
                "the eel at the far end of the aquarium reacted too");
    }

    /**
     * Someone walks up and <i>stays</i>: one reaction, then the eel comes out and gets on with its
     * life. Standing at a tank watching a colony duck over and over would read as a nervous tic,
     * not as an animal that has decided you are furniture — and it is exactly what the refractory
     * alone would produce, which is why the arming flag exists alongside it.
     *
     * <p>This is also where the crowded-tank bug landed after the trigger changed: the failure was
     * never really about fish, it was about a reaction that a stationary state could hold down
     * forever.
     */
    @Test
    void aWatcherWhoStaysGetsOneReactionNotAPermanentOne() {
        FlockEngine engine = colony(tank(null), eel(0.12f, 0));
        float eelL = engine.posL()[0], eelY = engine.posY()[0], eelD = engine.posD()[0];

        int ducks = 0;
        boolean wasHidden = false;
        int hidden = 0;
        for (int tick = 0; tick < 4_000; tick++) { // 200 s of someone standing at the glass
            engine.setWatcher(true, eelL + 0.4f, eelY + 1.5f, eelD);
            engine.step();
            boolean isHidden = retractOf(engine, 0) > 0.5f;
            if (isHidden && !wasHidden) ducks++;
            if (isHidden) hidden++;
            wasHidden = isHidden;
        }
        assertEquals(1, ducks, "an eel reacted more than once to someone who never left");
        assertTrue(hidden < 100, "the eel stayed down for " + hidden + " ticks of 4000");

        // And it can be startled again once they have gone away and come back.
        watch(engine, 400, null, null, null);
        watch(engine, 20, eelL + 0.4f, eelY + 1.5f, eelD);
        assertTrue(retractOf(engine, 0) > 0.7f, "the eel never reacted to a second approach");
    }

    /**
     * {@code toLocal} is the exact inverse of the rotation {@code interpolate} applies, at every
     * tank rotation — which is the only reason the adapter can hand this engine a player position
     * at all. Get it wrong and the eels react to somewhere that is not where anybody is standing:
     * a bug with no symptom except creatures ducking at nothing, which no other test here would
     * see, since every one of them works in local coordinates already.
     */
    @Test
    void toLocalInvertsTheFrameTheRenderScratchIsWrittenIn() {
        float[] out = new float[3];
        for (float rotation : new float[]{0f, 37f, 90f, 180f, 254f, 359f}) {
            FlockEngine engine = new FlockEngine(Tunables.DEFAULT);
            engine.rebuild(new FishSpec[]{eel(0.12f, 0), eel(0.14f, 0), eel(0.11f, 1)},
                    4242L, rotation, 3, 0.35f, 0.3f, 20f, tank(null));
            engine.step();
            engine.interpolate(1f);
            for (int i = 0; i < engine.count(); i++) {
                engine.toLocal(engine.renderX[i], engine.renderY[i], engine.renderZ[i], out);
                assertEquals(engine.posL()[i], out[0], 1e-5f, "lateral at rotation " + rotation);
                assertEquals(engine.posY()[i], out[1], 1e-5f, "vertical at rotation " + rotation);
                assertEquals(engine.posD()[i], out[2], 1e-5f, "depth at rotation " + rotation);
            }
        }
    }
}
