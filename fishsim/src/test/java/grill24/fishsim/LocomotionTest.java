package grill24.fishsim;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.Tunables;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Phase 0 of docs/fish-sim-locomotion.md: the locomotion class replaces the {@code canSwim}
 * boolean, and <b>nothing about behaviour changes</b>. The trajectory goldens and the parity
 * suite prove that for free swimmers; these tests pin the parts of the split those suites cannot
 * see — that every class without a motion model is still perfectly frozen, that the size gate
 * demotes to {@link Locomotion#STATIC} rather than to a second overlapping state, and that
 * {@code swimmers[]} stays exactly the derived view of {@code locomotion[]} that every consumer
 * reads it as.
 *
 * <p>These are also the tests that <b>fail loudly</b> when a phase gives a class its motion model:
 * the "does not move" assertions below are a statement about today, and each class left them as it
 * gained one — see {@link BenthicTest}, {@link DriftTest}, {@link GlideTest} and
 * {@link AnchoredTest}. All five phases have landed, so {@link Locomotion#STATIC} is the only class
 * left in them, and every class now has a size gate of its own (each asserted with its own model).
 */
class LocomotionTest {

    private static final int TICKS = 400;

    private static FlockEngine boxEngine(FishSpec... specs) {
        FlockEngine engine = new FlockEngine(Tunables.DEFAULT);
        engine.rebuild(specs, 4242L, 15f, 3, 0.35f, 0.3f, 20f);
        return engine;
    }

    /**
     * Every class with no motion model holds its scatter position exactly — bit for bit. Since
     * Phase 4 that is {@link Locomotion#STATIC} alone, which is the point: it is the one explicit
     * "does not move" state, and it is also where every gate demotes to.
     */
    @Test
    void unsimulatedClassesNeverMove() {
        for (Locomotion locomotion : Locomotion.values()) {
            if (locomotion.simulated()) continue;

            // A companion free swimmer, so the unsimulated fish is exposed to a live flock's
            // separation term rather than sitting in an empty tank.
            FlockEngine engine = boxEngine(
                    new FishSpec(0.10f, Locomotion.FREE_SWIM, false, 0),
                    new FishSpec(0.10f, locomotion, false, 1));

            int bitsL = Float.floatToRawIntBits(engine.posL()[1]);
            int bitsY = Float.floatToRawIntBits(engine.posY()[1]);
            int bitsD = Float.floatToRawIntBits(engine.posD()[1]);
            for (int tick = 0; tick < TICKS; tick++) {
                engine.step();
                assertEquals(bitsL, Float.floatToRawIntBits(engine.posL()[1]),
                        locomotion + " moved laterally at tick " + tick);
                assertEquals(bitsY, Float.floatToRawIntBits(engine.posY()[1]),
                        locomotion + " moved vertically at tick " + tick);
                assertEquals(bitsD, Float.floatToRawIntBits(engine.posD()[1]),
                        locomotion + " moved in depth at tick " + tick);
            }
            // The companion is the control: if it didn't move either, the assertions above are
            // passing for the wrong reason.
            assertNotEquals(0f, engine.speed[0], locomotion + ": the control swimmer never moved");
        }
    }

    /**
     * The animation clock only runs for a fish whose pose is driven by its own swimming — anything
     * else is animated open-loop against game time by the renderer, and a drifting
     * {@code tailPhase} would double-drive it. That is a property of the pose, not of whether the
     * engine moves the fish, which is why {@code DRIFT} keeps it after gaining a motion model
     * ({@link DriftTest#driftingDoesNotAdvanceTheTailBeatClock}) and why {@code ANCHORED} — stepped
     * every tick since Phase 4 — is still the case checked here.
     */
    @Test
    void aPoseWithNoTailBeatDoesNotAdvanceTheAnimationClock() {
        FlockEngine engine = boxEngine(
                new FishSpec(0.10f, Locomotion.FREE_SWIM, false, 0),
                new FishSpec(0.10f, Locomotion.ANCHORED, false, 1));
        for (int tick = 0; tick < TICKS; tick++) engine.step();
        engine.interpolate(0.5f);

        assertEquals(0f, engine.renderPhase[1], "ANCHORED advanced the tail-beat clock");
        assertNotEquals(0f, engine.renderPhase[0], "the control swimmer's clock never advanced");
    }

    /** {@code swimmers[]} is exactly "effective class is FREE_SWIM", for every class and either side of the gate. */
    @Test
    void swimmersIsTheDerivedViewOfLocomotion() {
        FishSpec[] specs = {
                new FishSpec(0.10f, Locomotion.FREE_SWIM, false, 0),
                new FishSpec(0.45f, Locomotion.FREE_SWIM, true, 0),  // over the gate
                new FishSpec(0.10f, Locomotion.GLIDE, false, 1),
                new FishSpec(0.45f, Locomotion.GLIDE, false, 1),     // over the gate
                new FishSpec(0.10f, Locomotion.DRIFT, false, 2),
                new FishSpec(0.10f, Locomotion.BENTHIC, false, 3),
                new FishSpec(0.10f, Locomotion.ANCHORED, false, 4),
                new FishSpec(0.10f, Locomotion.STATIC, false, 5),
        };
        FlockEngine engine = boxEngine(specs);
        for (int i = 0; i < specs.length; i++) {
            assertEquals(engine.locomotion[i] == Locomotion.FREE_SWIM, engine.swimmers[i],
                    "swimmers[] disagrees with locomotion[] at fish " + i);
        }
    }

    /**
     * The swim gate demotes to {@link Locomotion#STATIC} — one state, not a "gate-failed swimmer"
     * that the old boolean could not distinguish from a species that never swam. It applies to
     * {@link Locomotion#GLIDE} too, which shares the swimmer's straight-run gate: a giant ray in a
     * one-block tank has nowhere to glide, and freezing it is the honest answer.
     */
    @Test
    void theSwimGateDemotesToStatic() {
        FlockEngine engine = boxEngine(
                new FishSpec(0.10f, Locomotion.FREE_SWIM, false, 0),
                new FishSpec(0.45f, Locomotion.FREE_SWIM, false, 0),
                new FishSpec(0.10f, Locomotion.GLIDE, false, 1),
                new FishSpec(0.45f, Locomotion.GLIDE, false, 1));

        assertSame(Locomotion.FREE_SWIM, engine.locomotion[0]);
        assertSame(Locomotion.STATIC, engine.locomotion[1], "a fish too big for the run is STATIC");
        assertSame(Locomotion.GLIDE, engine.locomotion[2], "GLIDE passes the same gate a swimmer does");
        assertSame(Locomotion.STATIC, engine.locomotion[3], "an over-gate glider is STATIC");
    }

}
