package grill24.fishsim;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.Shelter;
import grill24.fishsim.domain.VoxelDomain;
import grill24.fishsim.harness.Metrics;
import grill24.fishsim.harness.Scenarios;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Skittish fish bolt for cover when the watcher walks up (docs/fish-shelters.md §5.3, trigger 2).
 * The trigger is the garden eels' own, so this mirrors {@code AnchoredTest}'s transients: the
 * watcher who arrives, the watcher who stays, the watcher signal that drops out, the watcher who
 * stands on the radius, and the watcher who was already there — every one of which, written as a
 * state instead of an event, once made the eels misbehave in game.
 */
class ShelterStartleTest {

    /** The log in a 3×1×1 group; its mouth faces +lateral. */
    private static final String DOMAIN = "3x1x1+log";

    private static FlockEngine skittishTank(VoxelDomain domain, int n, long seed) {
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        engine.rebuild(Scenarios.cast("skittish", n, seed), seed, 0f, 0f, domain);
        return engine;
    }

    /** Puts fish #{@code i} on its log's axis just outside the staging point, nose to the mouth. */
    private static void lineUp(FlockEngine engine, Shelter log, int i) {
        Shelter.Mouth mouth = log.mouths().get(0);
        float out = engine.lengths[i] + 0.12f;
        engine.posL()[i] = mouth.centerL() - mouth.normalL() * out;
        engine.posY()[i] = mouth.centerY();
        engine.posD()[i] = mouth.centerD() - mouth.normalD() * out;
        engine.velL()[i] = mouth.normalL() * 0.05f;
        engine.velY()[i] = 0f;
        engine.velD()[i] = mouth.normalD() * 0.05f;
        engine.yawDeg[i] = (float) Math.toDegrees(Math.atan2(-mouth.normalD(), mouth.normalL()));
    }

    /** Watcher far off down the lateral axis — present, so it arms the fish. */
    private static void far(FlockEngine engine) {
        engine.setWatcher(true, 8f, 0f, 0f);
    }

    /** Watcher at the glass by the log. */
    private static void near(FlockEngine engine) {
        engine.setWatcher(true, 1.6f, 0f, 0f);
    }

    private static int countStartles(FlockEngine engine, int[] prev, int[] startles) {
        int total = 0;
        for (int i = 0; i < engine.count(); i++) {
            int st = engine.shelterState(i);
            if (st == FlockEngine.SHELTER_APPROACH && prev[i] != FlockEngine.SHELTER_APPROACH && engine.startled(i)) {
                startles[i]++;
            }
            prev[i] = st;
            total += startles[i];
        }
        return total;
    }

    @Test
    void aWatcherWalkingUpSendsASkittishFishForCover() {
        VoxelDomain domain = Scenarios.domain(DOMAIN);
        FlockEngine engine = skittishTank(domain, 4, 3L);
        far(engine);
        for (int t = 0; t < 60; t++) engine.step();
        assertTrue(engine.startleArmed(0), "the watcher was seen far away, so fish 0 should be armed");

        lineUp(engine, domain.shelters().get(0), 0);
        near(engine);
        boolean bolted = false, inside = false;
        for (int t = 0; t < 400 && !inside; t++) {
            engine.step();
            if (engine.shelterState(0) == FlockEngine.SHELTER_APPROACH && engine.startled(0)) bolted = true;
            inside = engine.shelterState(0) == FlockEngine.SHELTER_INSIDE;
        }
        assertTrue(bolted, "fish 0 did not bolt when the watcher arrived");
        assertTrue(inside, "fish 0 bolted but never reached cover");
        assertFalse(engine.startleArmed(0), "the reaction must disarm the fish");
        assertEquals(0, engine.backstopEngagements());
    }

    @Test
    void aWatcherWhoStaysGetsOneReactionAndIsThenFurniture() {
        VoxelDomain domain = Scenarios.domain(DOMAIN);
        FlockEngine engine = skittishTank(domain, 6, 4L);
        far(engine);
        for (int t = 0; t < 60; t++) engine.step();
        lineUp(engine, domain.shelters().get(0), 0);
        near(engine);

        int[] prev = new int[engine.count()], startles = new int[engine.count()];
        int insideTicks = 0;
        boolean cameOut = false;
        for (int t = 0; t < 2_400; t++) { // two minutes of a player standing at the glass
            engine.step();
            countStartles(engine, prev, startles);
            // The startle's own stay only: skittish fish visit on their own clock too, later.
            if (!cameOut && engine.shelterState(0) == FlockEngine.SHELTER_INSIDE) insideTicks++;
            if (insideTicks > 0 && engine.shelterState(0) == FlockEngine.SHELTER_ROAMING) cameOut = true;
        }
        for (int i = 0; i < engine.count(); i++) {
            assertTrue(startles[i] <= 1, "fish " + i + " was startled " + startles[i] + " times by one approach");
        }
        assertEquals(1, startles[0], "fish 0 should have bolted once");
        assertTrue(cameOut, "a watcher standing still should let the fish come back out");
        // Out after its dwell (5–10 s) — the watcher stopped approaching the moment it stopped.
        assertTrue(insideTicks <= (int) (10f / 0.05f) + 1, "stayed hidden " + insideTicks + " ticks for a watcher standing still");
        assertEquals(0, engine.backstopEngagements());
    }

    @Test
    void aWatcherWhoWasAlreadyThereStartlesNobody() {
        VoxelDomain domain = Scenarios.domain(DOMAIN);
        FlockEngine engine = skittishTank(domain, 6, 5L);
        lineUp(engine, domain.shelters().get(0), 0);
        near(engine); // from the very first tick: the tank loaded with the player at the glass
        int[] prev = new int[engine.count()], startles = new int[engine.count()];
        for (int t = 0; t < 1_200; t++) {
            engine.step();
            assertEquals(0, countStartles(engine, prev, startles), "a startle at tick " + t + " with no approach");
        }
    }

    @Test
    void losingTheWatcherSignalIsNotTheWatcherLeaving() {
        VoxelDomain domain = Scenarios.domain(DOMAIN);
        FlockEngine engine = skittishTank(domain, 6, 6L);
        far(engine);
        for (int t = 0; t < 60; t++) engine.step();
        lineUp(engine, domain.shelters().get(0), 0);
        near(engine);
        int[] prev = new int[engine.count()], startles = new int[engine.count()];
        for (int t = 0; t < 600; t++) {
            engine.step();
            countStartles(engine, prev, startles);
        }
        assertEquals(1, startles[0]);

        // The signal drops (a rebuild, a frame with no player known) and comes back, still near.
        engine.setWatcher(false, 0f, 0f, 0f);
        for (int t = 0; t < 200; t++) {
            engine.step();
            countStartles(engine, prev, startles);
        }
        lineUp(engine, domain.shelters().get(0), 0);
        near(engine);
        for (int t = 0; t < 600; t++) {
            engine.step();
            countStartles(engine, prev, startles);
        }
        assertEquals(1, startles[0], "a dropped signal re-armed the fish as if the watcher had left");
    }

    @Test
    void aDroppedSignalHoldsAStartledFishOnlyForItsHold() {
        VoxelDomain domain = Scenarios.domain(DOMAIN);
        FlockEngine engine = skittishTank(domain, 4, 7L);
        far(engine);
        for (int t = 0; t < 60; t++) engine.step();
        lineUp(engine, domain.shelters().get(0), 0);
        near(engine);
        int t = 0;
        while (engine.shelterState(0) != FlockEngine.SHELTER_INSIDE && t++ < 400) engine.step();
        assertEquals(FlockEngine.SHELTER_INSIDE, engine.shelterState(0), "bad probe: fish 0 never got inside");

        engine.setWatcher(false, 0f, 0f, 0f); // unknown is not "stopped approaching"...
        int insideTicks = 0;
        while (engine.shelterState(0) == FlockEngine.SHELTER_INSIDE && insideTicks < 2_000) {
            engine.step();
            insideTicks++;
        }
        // ...but the hold is bounded: dwell (≤ 10 s) plus the hold (10 s).
        assertTrue(insideTicks >= (int) (5f / 0.05f), "left after " + insideTicks + " ticks: before its dwell");
        assertTrue(insideTicks <= (int) (20f / 0.05f) + 1, "held " + insideTicks + " ticks: past dwell plus hold");
    }

    @Test
    void aWatcherOnTheRadiusDoesNotStrobe() {
        VoxelDomain domain = Scenarios.domain(DOMAIN);
        FlockEngine engine = skittishTank(domain, 6, 8L);
        far(engine);
        for (int t = 0; t < 60; t++) engine.step();
        int[] prev = new int[engine.count()], startles = new int[engine.count()];
        for (int t = 0; t < 2_400; t++) {
            // Swaying back and forth across the startle radius of every fish in the tank.
            float sway = (float) Math.sin(t * 0.3) * 0.4f;
            engine.setWatcher(true, 3.0f + sway, 0f, 0f);
            engine.step();
            countStartles(engine, prev, startles);
        }
        for (int i = 0; i < engine.count(); i++) {
            assertTrue(startles[i] <= 1, "fish " + i + " strobed: " + startles[i] + " startles on the radius");
        }
    }

    @Test
    void visitorsIgnoreTheWatcher() {
        VoxelDomain domain = Scenarios.domain(DOMAIN);
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        FishSpec[] specs = Scenarios.cast("loaches", 6, 9L);
        engine.rebuild(specs, 9L, 0f, 0f, domain);
        far(engine);
        for (int t = 0; t < 60; t++) engine.step();
        near(engine);
        for (int t = 0; t < 600; t++) {
            engine.step();
            for (int i = 0; i < engine.count(); i++) {
                assertFalse(engine.startled(i) && engine.shelterState(i) != FlockEngine.SHELTER_ROAMING,
                        "visitor " + i + " was startled");
            }
        }
    }

    /**
     * A player pacing up to the glass and away again every half minute, with a skittish shoal: the
     * visit invariants and the dynamics bounds still hold under repeated startles.
     */
    @Test
    void aPacingWatcherKeepsEveryBound() {
        Tunables t = Tunables.GROUP;
        for (long seed : new long[]{12345L, -987654321L}) {
            VoxelDomain domain = Scenarios.domain(DOMAIN);
            FlockEngine engine = skittishTank(domain, 8, seed);
            Metrics m = new Metrics(engine, t, 200);
            int[] prev = new int[engine.count()], startles = new int[engine.count()];
            for (int tick = 0; tick < 12_000; tick++) {
                boolean close = (tick / 600) % 2 == 1;
                engine.setWatcher(true, close ? 1.6f : 8f, 0f, 0f);
                engine.step();
                m.sample();
                countStartles(engine, prev, startles);
                int hidden = 0;
                for (int i = 0; i < engine.count(); i++) {
                    int st = engine.shelterState(i);
                    if (st == FlockEngine.SHELTER_ENTER || st == FlockEngine.SHELTER_INSIDE) hidden++;
                    Shelter log = domain.shelters().get(0);
                    if (engine.shelterUsing(i) != 0) {
                        assertFalse(log.hull().contains(engine.posL()[i], engine.posY()[i], engine.posD()[i]),
                                "fish " + i + " in the hull without using it, tick " + tick);
                    }
                }
                assertTrue(hidden <= engine.hiddenBudget(), hidden + " hidden against " + engine.hiddenBudget());
            }
            int total = 0;
            for (int s : startles) total += s;
            assertTrue(total > 0, "seed " + seed + ": ten approaches startled nobody");
            assertEquals(0, m.hardClampContacts(), "hard backstop engagements");
            assertTrue(m.maxObservedAccel() <= t.maxForce() * 1.06f, "accel " + m.maxObservedAccel());
            assertTrue(m.maxObservedJerk() <= 12f, "jerk " + m.maxObservedJerk());
        }
    }
}
