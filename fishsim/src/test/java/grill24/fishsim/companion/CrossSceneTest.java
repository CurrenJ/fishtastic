package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.PartyRun;
import grill24.fishsim.harness.companion.TwoOwnerRun;
import grill24.fishsim.core.Locomotion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-player scenes (docs/fish-companions-party.md §28, step 3a-2): the path geometry both
 * clients compute, the scene's own end rules, and the scene played by two parties headless —
 * greeting, path, the D25 refusal, the D28 hand-over from a predator scene, a corridor with no
 * room, and the owner clearance that keeps swimmers out of the other player.
 */
class CrossSceneTest {

    private static final Vec A = new Vec(0.5, 64, 0.5);

    private static CrossScene fed(CrossScene.Kind kind, int role, Vec first, Vec second, int ticks) {
        CrossScene s = new CrossScene(kind, role);
        s.update(ticks, first, second, null);
        return s;
    }

    private static TwoOwnerRun.Pair pair(String name) {
        return TwoOwnerRun.PAIRS.stream().filter(p -> p.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void ringRadiusKeepsClearOfTheOwners() {
        assertEquals(1.1, CrossScene.ringRadius(6), 1e-9, "far apart: the plain ring");
        assertEquals(1.0, CrossScene.ringRadius(3), 1e-9, "3 apart: shrinks inside the owners");
        assertEquals(1.75, CrossScene.ringRadius(2.5), 1e-9, "2.5 apart: too small inside, so outside");
        assertEquals(1.25, CrossScene.ringRadius(1.5), 1e-9, "close: outside both");
        for (double d = 0.5; d <= 6; d += 0.25) {
            double r = CrossScene.ringRadius(d);
            assertTrue(Math.abs(r - d / 2) >= CrossScene.RING_OWNER_CLEARANCE - 1e-9, "ring over an owner at d=" + d);
            assertTrue(r >= CrossScene.RING_MIN_RADIUS - 1e-9, "ring too tight at d=" + d);
        }
    }

    @Test
    void ringRolesAreOppositeAndAboveTheEyes() {
        Vec b = A.add(3, 0, 1);
        for (int k = 0; k < 400; k += 7) {
            CrossScene r0 = fed(CrossScene.Kind.RING, 0, A, b, 0), r1 = fed(CrossScene.Kind.RING, 1, A, b, 0);
            Vec c = r0.ringCentre();
            Vec p0 = r0.pathPoint(k), p1 = r1.pathPoint(k);
            assertEquals(0, p0.add(p1).scale(0.5).distanceTo(c), 1e-9, "roles not opposite at " + k);
            assertEquals(A.y() + OwnerState.EYE_HEIGHT + CrossScene.RING_ABOVE_EYE, p0.y(), 1e-9);
        }
    }

    @Test
    void eightIsSmoothSplitAtTheCrossingAndClearOfTheOwners() {
        Vec b = A.add(4, 0, 0);
        CrossScene e0 = fed(CrossScene.Kind.EIGHT, 0, A, b, 0), e1 = fed(CrossScene.Kind.EIGHT, 1, A, b, 0);
        double lap = 4 * Math.PI * CrossScene.lobeRadius(4) / CrossScene.PATH_SPEED * 20;
        Vec prevDir = null;
        for (double k = CrossScene.GREET_TICKS; k < CrossScene.GREET_TICKS + lap; k += 0.5) {
            Vec p = e0.pathPoint(k), q = e0.pathPoint(k + 0.5);
            Vec dir = q.sub(p).horizontal().normalize();
            if (prevDir != null) assertTrue(prevDir.dot(dir) > 0.95, "the eight kinks at tick " + k);
            prevDir = dir;
            for (Vec owner : List.of(A, b)) {
                double h = p.sub(owner).horizontalLength();
                assertTrue(h >= CrossScene.EIGHT_MIN_RADIUS - 1e-6, "the eight passes " + h + " from an owner");
            }
        }
        // Both leave the crossing together, a layer apart, on opposite lobes.
        Vec s0 = e0.pathPoint(CrossScene.GREET_TICKS), s1 = e1.pathPoint(CrossScene.GREET_TICKS);
        assertEquals(2 * CrossScene.EIGHT_CROSS_SPLIT, s0.y() - s1.y(), 1e-9);
        assertEquals(0, s0.horizontal().distanceTo(s1.horizontal()), 1e-9);
        Vec a0 = e0.pathPoint(CrossScene.GREET_TICKS + 40), a1 = e1.pathPoint(CrossScene.GREET_TICKS + 40);
        assertTrue(a0.distanceTo(b) < a0.distanceTo(A) && a1.distanceTo(A) < a1.distanceTo(b), "not one round each owner");
    }

    @Test
    void sceneEndsOnTimeOrWhenAnOwnerWalksOffOrLeaves() {
        Vec b = A.add(3, 0, 0);
        CrossScene ring = new CrossScene(CrossScene.Kind.RING, 0);
        for (int k = 0; k <= CrossScene.Kind.RING.durationTicks; k++) ring.update(k, A, b, null);
        assertEquals("done", ring.endReason());

        CrossScene walk = new CrossScene(CrossScene.Kind.RING, 0);
        Vec a = A;
        for (int k = 0; k < 60 && walk.active() || k == 0; k++) {
            walk.update(k, a, b, null);
            a = a.add(0, 0, 0.1); // 2 blocks/s
        }
        assertEquals("owner walked off", walk.endReason());

        CrossScene gone = new CrossScene(CrossScene.Kind.EIGHT, 1);
        gone.update(0, A, b, null);
        gone.update(1, A.add(0, 0, 20), b, null);
        assertEquals("owner left", gone.endReason());

        // G2 runs on to finish one whole eight with its owners far apart.
        CrossScene far = fed(CrossScene.Kind.EIGHT, 0, A, A.add(6, 0, 0), 0);
        assertTrue(far.endTicks() > CrossScene.Kind.EIGHT.durationTicks && far.endTicks() <= CrossScene.EIGHT_MAX_TICKS,
                "far eight ends at " + far.endTicks());
    }

    @Test
    void sameSpeciesPlayTheRingTogether() {
        TwoOwnerRun.Sim sim = new TwoOwnerRun.Sim(pair("same species"), "pair_approach_stand", 2,
                CompanionTunables.defaults(), Temperament.BASELINE).autoScenes(true);
        boolean bothOnPath = false;
        for (int k = 0; k < 600; k++) {
            sim.step();
            if (sim.a.scene() != null && sim.a.sceneUnit().leader().inScene() && sim.b.sceneUnit().leader().inScene()
                    && !sim.a.scene().greeting()) bothOnPath = true;
        }
        assertEquals(1, sim.scenesStarted());
        assertEquals(CrossScene.Kind.RING, sim.a.scene().kind);
        assertTrue(bothOnPath, "both fish never on the ring together");
        assertEquals("done", sim.a.scene().endReason());
        assertNull(sim.a.sceneUnit().leader().sceneLeft());
        TwoOwnerRun.SceneResult r = TwoOwnerRun.measureScenes(pair("same species"), "pair_approach_stand", 2, 2400,
                CompanionTunables.defaults(), Temperament.BASELINE);
        assertTrue(r.pathError() < 0.3, "path error " + r.pathError());
        assertTrue(r.antipodalDeg() < 15, "antipodal error " + r.antipodalDeg());
    }

    @Test
    void differentSwimmersPlayTheEightAndFinishIt() {
        TwoOwnerRun.SceneResult r = TwoOwnerRun.measureScenes(pair("two free swimmers"), "pair_far", 1, 2400,
                CompanionTunables.defaults(), Temperament.BASELINE);
        assertTrue(r.scenes() >= 1);
        assertEquals(100, r.joinedPct(), 1e-9);
        assertTrue(r.pathError() < 0.3, "path error " + r.pathError());
        assertTrue(r.ends().getOrDefault("done", 0) >= 2, "ends " + r.ends());
    }

    @Test
    void aCorridorHasNoRoomSoTheFishSitItOut() {
        TwoOwnerRun.Sim sim = new TwoOwnerRun.Sim(pair("same species"), "pair_corridor", 1,
                CompanionTunables.defaults(), Temperament.BASELINE).autoScenes(true);
        for (int k = 0; k < 200; k++) sim.step();
        assertNotNull(sim.a.scene(), "a scene should be asked for");
        assertEquals("no room", sim.a.sceneUnit().leader().sceneLeft());
    }

    @Test
    void onlyAFreeSwimmerTakesAScene() {
        PartyRun.Party crab = new PartyRun.Party("crab", List.of(PartyRun.Spec.of("crab", Locomotion.BENTHIC, 0.3f)));
        TwoOwnerRun.Sim sim = new TwoOwnerRun.Sim(new TwoOwnerRun.Pair("crab", crab, crab), "pair_far", 1,
                CompanionTunables.defaults(), Temperament.BASELINE);
        assertNull(sim.a.layer.crossScene(0, new CrossScene(CrossScene.Kind.RING, 0)), "D25: a crab takes no scene");
        assertTrue(!sim.startScene(), "the scheduler finds nothing for two crabs");
    }

    @Test
    void aCrossSceneEndsThePartysPredatorScene() {
        TwoOwnerRun.Sim sim = new TwoOwnerRun.Sim(pair("predator party vs swimmer"), "pair_far", 3,
                CompanionTunables.defaults(), Temperament.BASELINE);
        String started = "";
        for (int k = 0; k < 600 && !started.startsWith("Predator scene"); k++) {
            sim.step();
            if (k >= 60) started = sim.a.layer.devPredatorScene();
        }
        assertTrue(started.startsWith("Predator scene"), "the predator scene should start: " + started);
        for (int k = 0; k < 20; k++) sim.step();
        assertTrue(sim.a.layer.sceneLine().contains("on,"), sim.a.layer.sceneLine());
        assertTrue(sim.startScene());
        sim.step();
        assertTrue(!sim.a.layer.sceneLine().contains("on,"), "D28: still on: " + sim.a.layer.sceneLine());
        for (int k = 0; k < 200; k++) {
            sim.step();
            assertTrue(!sim.a.layer.sceneLine().contains("on,"), "D28: restarted mid-scene: " + sim.a.layer.sceneLine());
        }
    }

    /** D29: two clients, each with the other player 3 ticks late, see the same fish within a few cm on average. */
    @Test
    void twoClientsSeeTheRingAlike() {
        TwoOwnerRun.Pair p = pair("same species");
        TwoOwnerRun.Sim ref = new TwoOwnerRun.Sim(p, "pair_approach_stand", 1, CompanionTunables.defaults(),
                Temperament.BASELINE).autoScenes(true);
        java.util.Set<Integer> starts = new java.util.HashSet<>();
        for (int k = 0; k < 600; k++) {
            int before = ref.scenesStarted();
            ref.step();
            if (ref.scenesStarted() > before) starts.add(k);
        }
        TwoOwnerRun.Sim va = new TwoOwnerRun.Sim(p, "pair_approach_stand", 1, CompanionTunables.defaults(),
                Temperament.BASELINE).lag(0, 3).sceneAt(starts);
        TwoOwnerRun.Sim vb = new TwoOwnerRun.Sim(p, "pair_approach_stand", 1, CompanionTunables.defaults(),
                Temperament.BASELINE).lag(3, 0).sceneAt(starts);
        double sum = 0;
        int n = 0;
        for (int k = 0; k < 600; k++) {
            va.step();
            vb.step();
            if (va.a.scene() == null || !va.a.sceneUnit().leader().inScene() || !vb.a.sceneUnit().leader().inScene()
                    || va.a.scene().greeting()) continue;
            sum += va.a.sceneUnit().leader().position().distanceTo(vb.a.sceneUnit().leader().position());
            n++;
        }
        assertTrue(n > 100, "the ring never played in both views");
        assertTrue(sum / n < 0.3, "views apart by " + sum / n + " on average");
    }

    @Test
    void swimmersKeepOutOfTheOtherPlayer() {
        TwoOwnerRun.Result on = TwoOwnerRun.measure(pair("two free swimmers"), "pair_close", 1, 2400,
                CompanionTunables.defaults(), Temperament.BASELINE);
        CompanionTunables off = CompanionTunables.defaults();
        off.otherPlayerClearance = false;
        TwoOwnerRun.Result without = TwoOwnerRun.measure(pair("two free swimmers"), "pair_close", 1, 2400, off,
                Temperament.BASELINE);
        assertTrue(on.ownerContactPct() < 1.0, "inside the other player " + on.ownerContactPct() + "%");
        assertTrue(on.ownerContactPct() < without.ownerContactPct(), "clearance didn't help: " + on.ownerContactPct()
                + " vs " + without.ownerContactPct());
    }
}
