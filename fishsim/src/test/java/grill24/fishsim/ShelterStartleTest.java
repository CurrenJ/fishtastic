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
 * Skittish fish dash for cover when the watcher lunges at them (docs/fish-shelters.md §5.3,
 * trigger 2). This mirrors {@code AnchoredTest}'s transients: the watcher who arrives, the watcher
 * who stays, the watcher signal that drops out, the watcher who sways, and the watcher who was
 * already there. Every one of them, written as a state instead of an event, once made the eels
 * misbehave in game. On top: the watcher who stays by the tank and keeps stepping up to it, which
 * the eels' rule never re-armed for, and the dash itself in the owner's two-storey test tank.
 */
class ShelterStartleTest {

    /** The log in a 3×1×1 group; its mouth faces +lateral. */
    private static final String DOMAIN = "3x1x1+log";

    private static FlockEngine skittishTank(VoxelDomain domain, int n, long seed) {
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        engine.rebuild(Scenarios.cast("skittish", n, seed), seed, 0f, 0f, domain);
        return engine;
    }

    /**
     * Puts fish #{@code i} on its log's axis just outside the staging point, nose to the mouth.
     * First lets it finish any visit it is on: a startle is for a roaming fish, and visits are
     * common enough that fish 0 is sometimes already on its way to the log.
     */
    private static void lineUp(FlockEngine engine, Shelter log, int i) {
        for (int t = 0; t < 2_000 && engine.shelterState(i) != FlockEngine.SHELTER_ROAMING; t++) engine.step();
        assertEquals(FlockEngine.SHELTER_ROAMING, engine.shelterState(i), "bad probe: fish " + i + " never came back out");
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

    /** Watcher far off down the lateral axis — present, so a step from here to {@link #near} is a lunge. */
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
            // Out of ROAMING on a startle. Not "into APPROACH": a fish already lined up at a mouth
            // goes on to ENTER within the same tick.
            if (st != FlockEngine.SHELTER_ROAMING && prev[i] == FlockEngine.SHELTER_ROAMING && engine.startled(i)) {
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
        assertEquals(0f, engine.startleRefractory(0), "nothing has startled fish 0 yet");

        lineUp(engine, domain.shelters().get(0), 0);
        near(engine);
        boolean bolted = false, inside = false;
        for (int t = 0; t < 400 && !inside; t++) {
            engine.step();
            if (engine.shelterState(0) != FlockEngine.SHELTER_ROAMING && engine.startled(0)) bolted = true;
            inside = engine.shelterState(0) == FlockEngine.SHELTER_INSIDE;
        }
        assertTrue(bolted, "fish 0 did not bolt when the watcher arrived");
        assertTrue(inside, "fish 0 bolted but never reached cover");
        assertTrue(engine.startleRefractory(0) > 0f, "the reaction must start the fish's refractory");
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
    void aSwayingWatcherStartlesNobody() {
        VoxelDomain domain = Scenarios.domain(DOMAIN);
        FlockEngine engine = skittishTank(domain, 6, 8L);
        engine.setWatcher(true, 1.8f, 0f, 0f);
        for (int t = 0; t < 60; t++) engine.step();
        int[] prev = new int[engine.count()], startles = new int[engine.count()];
        for (int t = 0; t < 2_400; t++) {
            // A player at the glass rocking from foot to foot: 0.2 blocks either way, every 2 s.
            float sway = (float) Math.sin(t * Math.PI / 20.0) * 0.2f;
            engine.setWatcher(true, 1.8f + sway, 0f, 0f);
            engine.step();
            countStartles(engine, prev, startles);
        }
        for (int i = 0; i < engine.count(); i++) {
            assertEquals(0, startles[i], "fish " + i + " was startled by a watcher who never stepped toward it");
        }
    }

    @Test
    void aLungingWatcherStartlesNoMoreOftenThanTheRefractoryAllows() {
        VoxelDomain domain = Scenarios.domain(DOMAIN);
        FlockEngine engine = skittishTank(domain, 6, 8L);
        engine.setWatcher(true, 3.5f, 0f, 0f);
        for (int t = 0; t < 60; t++) engine.step();
        int[] prev = new int[engine.count()], startles = new int[engine.count()];
        int ticks = 2_400;
        for (int t = 0; t < ticks; t++) {
            // A step in to the glass and back out every 2 s: a lunge every time.
            engine.setWatcher(true, (t / 20) % 2 == 0 ? 3.0f : 1.5f, 0f, 0f);
            engine.step();
            countStartles(engine, prev, startles);
        }
        int allowed = 1 + (int) (ticks * 0.05f / 15f);
        for (int i = 0; i < engine.count(); i++) {
            assertTrue(startles[i] <= allowed, "fish " + i + " startled " + startles[i] + " times, refractory allows " + allowed);
        }
    }

    /**
     * The bug the lunge fixed: a player who stays by the tank, stepping up to the glass and back,
     * never went 4.5 blocks away, so the eels' rule never re-armed and nothing was ever startled.
     */
    @Test
    void aWatcherWhoStaysByTheTankKeepsStartlingFish() {
        VoxelDomain domain = Scenarios.domain(DOMAIN);
        FlockEngine engine = skittishTank(domain, 8, 11L);
        int n = engine.count();
        // Startles fired, not dashes: with 8 fish only 2 may hide at once, so most startles find
        // the hidden budget spent. That is the budget working, not the trigger failing.
        int[] startles = new int[n];
        float[] refractory = new float[n];
        float at = 3.5f;
        for (int t = 0; t < 2_400; t++) {
            at = walk(at, (t / 200) % 2 == 1 ? 1.1f : 3.5f);
            engine.setWatcher(true, domain.maxLateral() + at, 0f, 0f);
            for (int i = 0; i < n; i++) refractory[i] = engine.startleRefractory(i);
            engine.step();
            for (int i = 0; i < n; i++) if (engine.startleRefractory(i) > refractory[i]) startles[i]++;
        }
        int total = 0, repeated = 0;
        for (int s : startles) {
            total += s;
            if (s >= 2) repeated++;
        }
        assertTrue(repeated >= engine.count() / 2,
                "only " + repeated + " of " + engine.count() + " fish were startled more than once (" + total + " startles)");
    }

    /**
     * The owner's test tank: a 5x2x1 with two Hollow Logs a block apart and 11 fish, the watcher
     * stepping up to the glass and back. Before the dash, 0.5-10% of startles found cover in
     * reach. Every bound holds through the dashes, with the dash's own speed cap.
     */
    @Test
    void dashesReachCoverInATwoStoreyTank() {
        Tunables t = Tunables.GROUP;
        for (long seed : new long[]{12345L, -987654321L}) {
            VoxelDomain domain = Scenarios.domain("5x2x1+logpair");
            FlockEngine engine = skittishTank(domain, 11, seed);
            Metrics m = new Metrics(engine, t, 200);
            int n = engine.count();
            int[] prev = new int[n];
            boolean[] dashing = new boolean[n];
            int fired = 0, dashes = 0, covered = 0;
            float[] refractory = new float[n];
            float at = 3.5f;
            for (int tick = 0; tick < 12_000; tick++) {
                at = walk(at, (tick / 200) % 2 == 1 ? 1.1f : 3.5f);
                engine.setWatcher(true, domain.maxLateral() + at, 0f, 0f);
                for (int i = 0; i < n; i++) refractory[i] = engine.startleRefractory(i);
                engine.step();
                m.sample();
                for (int i = 0; i < n; i++) {
                    int st = engine.shelterState(i);
                    if (engine.startleRefractory(i) > refractory[i]) fired++;
                    if (st != FlockEngine.SHELTER_ROAMING && prev[i] == FlockEngine.SHELTER_ROAMING && engine.startled(i)) {
                        dashes++;
                        dashing[i] = true;
                    }
                    if (dashing[i] && st == FlockEngine.SHELTER_INSIDE) {
                        covered++;
                        dashing[i] = false;
                    } else if (dashing[i] && st == FlockEngine.SHELTER_ROAMING) {
                        dashing[i] = false;
                    }
                    prev[i] = st;
                }
            }
            String what = "seed " + seed + ": " + fired + " startles, " + dashes + " dashes, " + covered + " reached cover";
            System.out.println("dashesReachCoverInATwoStoreyTank " + what);
            assertTrue(dashes > 0 && covered >= 0.6f * dashes, what);
            assertEquals(0, m.hardClampContacts(), "hard backstop engagements, " + what);
            float dashCeiling = FlockEngine.STARTLE_DASH_SPEED * (1f + t.traitJitter());
            assertTrue(m.maxObservedSpeed() <= dashCeiling * 1.001f, "speed " + m.maxObservedSpeed());
            assertTrue(m.maxObservedAccel() <= t.maxForce() * 1.10f, "accel " + m.maxObservedAccel());
            assertTrue(m.maxObservedJerk() <= 12f, "jerk " + m.maxObservedJerk());
        }
    }

    /**
     * Places in cover go nearest first (§5.3): on a lunge, every fish that dashes is at least as
     * near its cover as every startled fish that doesn't. Index order used to decide it, so a fish
     * across the tank could take the place of one at the mouth.
     */
    @Test
    void placesInCoverGoNearestFirst() {
        for (long seed : new long[]{21L, 22L, 23L, 24L}) {
            VoxelDomain domain = Scenarios.domain(DOMAIN);
            FlockEngine engine = skittishTank(domain, 10, seed);
            far(engine);
            for (int t = 0; t < 200; t++) engine.step();
            Shelter.Mouth mouth = domain.shelters().get(0).mouths().get(0);
            int n = engine.count();
            float[] toCover = new float[n];
            for (int i = 0; i < n; i++) {
                float stage = engine.lengths[i] + 0.02f;
                float dl = engine.posL()[i] - (mouth.centerL() - mouth.normalL() * stage);
                float dy = engine.posY()[i] - (mouth.centerY() - mouth.normalY() * stage);
                float dd = engine.posD()[i] - (mouth.centerD() - mouth.normalD() * stage);
                toCover[i] = (float) Math.sqrt(dl * dl + dy * dy + dd * dd);
            }
            near(engine);
            engine.step();
            float farthestDash = -1f, nearestMiss = Float.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                if (engine.startleRefractory(i) <= 0f) continue; // not lunged at this tick
                if (engine.startled(i) && engine.shelterState(i) != FlockEngine.SHELTER_ROAMING) {
                    farthestDash = Math.max(farthestDash, toCover[i]);
                } else {
                    nearestMiss = Math.min(nearestMiss, toCover[i]);
                }
            }
            assertTrue(farthestDash >= 0f, "seed " + seed + ": nobody dashed");
            assertTrue(farthestDash <= nearestMiss + 1e-4f,
                    "seed " + seed + ": a fish " + farthestDash + " from cover dashed, one " + nearestMiss + " from it didn't");
        }
    }

    /**
     * A fish lunged at that gets no place in cover flinches (§5.3.2): it darts away from the
     * watcher, then settles back to cruising speed. None of them simply ignores the lunge.
     */
    @Test
    void theRestFlinchAwayAndSettle() {
        Tunables t = Tunables.GROUP;
        VoxelDomain domain = Scenarios.domain(DOMAIN);
        FlockEngine engine = skittishTank(domain, 10, 31L);
        far(engine);
        for (int tick = 0; tick < 200; tick++) engine.step();
        engine.setWatcher(true, domain.maxLateral() + 1.0f, 0f, 0f);
        int n = engine.count();
        // Where each fish was as the lunge landed: the engine judges "away" from there.
        float[] atL = engine.posL().clone(), atD = engine.posD().clone();
        engine.step();
        boolean[] flinched = new boolean[n];
        int lunged = 0, flinchers = 0;
        for (int i = 0; i < n; i++) {
            if (engine.startleRefractory(i) <= 0f) continue;
            lunged++;
            boolean hid = engine.startled(i) && engine.shelterState(i) != FlockEngine.SHELTER_ROAMING;
            assertTrue(hid || engine.flinching(i), "fish " + i + " was lunged at and neither hid nor flinched");
            if (engine.flinching(i)) {
                flinched[i] = true;
                flinchers++;
            }
        }
        assertTrue(lunged >= 5 && flinchers >= 3, lunged + " lunged at, " + flinchers + " flinched");
        // Every flinch is aimed no more than a quarter turn off straight away from the watcher
        // (who is at +lateral), and no flincher ends up much nearer it. Not every one ends
        // farther: a fish at the log's open end, with the log between it and straight away, darts
        // sideways along the glass, and the log's own avoidance pushes it out of the log's margin,
        // which there is toward the watcher (measured 0.11 blocks nearer, seed 31).
        float wl = domain.maxLateral() + 1.0f;
        float[] dir = new float[2], before = new float[n];
        for (int i = 0; i < n; i++) {
            before[i] = (float) Math.hypot(wl - engine.posL()[i], engine.posD()[i]);
            if (!flinched[i]) continue;
            engine.flinchDirection(i, dir);
            float awayL = atL[i] - wl, awayD = atD[i];
            float len = (float) Math.hypot(awayL, awayD);
            assertTrue((dir[0] * awayL + dir[1] * awayD) / len >= -1e-4f,
                    "fish " + i + " flinched toward the watcher: " + dir[0] + ", " + dir[1]);
        }
        for (int tick = 0; tick < 32; tick++) engine.step();
        int away = 0;
        for (int i = 0; i < n; i++) {
            if (!flinched[i]) continue;
            float after = (float) Math.hypot(wl - engine.posL()[i], engine.posD()[i]);
            assertTrue(after > before[i] - 0.15f, "fish " + i + " ended " + (before[i] - after) + " nearer the watcher");
            if (after > before[i]) away++;
        }
        assertTrue(away * 2 >= flinchers, "only " + away + " of " + flinchers + " flinchers ended farther from the watcher");
        // Five seconds on, nobody is still flinching or faster than an ordinary cruiser.
        for (int tick = 0; tick < 78; tick++) engine.step();
        float ceiling = t.maxSpeed() * (1f + t.traitJitter()) * 1.001f;
        for (int i = 0; i < n; i++) {
            if (!flinched[i]) continue;
            assertFalse(engine.flinching(i), "fish " + i + " still flinching");
            float v = (float) Math.sqrt(engine.velL()[i] * engine.velL()[i] + engine.velY()[i] * engine.velY()[i]
                    + engine.velD()[i] * engine.velD()[i]);
            assertTrue(v <= ceiling, "fish " + i + " still at " + v + " after its flinch");
        }
        assertEquals(0, engine.backstopEngagements());
    }

    /** One tick of a player walking (4.3 blocks/s) from {@code at} toward {@code target}. */
    private static float walk(float at, float target) {
        float step = 4.3f * 0.05f;
        return at + Math.max(-step, Math.min(step, target - at));
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
