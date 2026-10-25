package grill24.fishsim;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.Shelter;
import grill24.fishsim.domain.WaterDomain;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Host-sent triggers ({@link Shelter.Trigger#hostSent}, {@link FlockEngine#sendToTrigger}): how an
 * interactive school's server sends the member it reserved to a bobber. The swim, the touch, the
 * hold and the giving up are the clocks' nose errand; what is new is only who decides, and that
 * the errand survives the changes a shared school makes round it — another bobber arriving or
 * leaving, another member leaving for its fight.
 */
class HostTriggerTest {

    private static final long[] SEEDS = {12345L, -987654321L, 7L};
    private static final int BED = 62;
    private static final int ORIGIN_Y = BED - 1;
    private static final float SURFACE = 8f / 9f;
    private static final float HOLD = 4f;

    /** A school region at its cap: 8 × 8 columns, 4 deep. */
    private static WaterDomain pond() {
        List<WaterDomain.Column> cols = new ArrayList<>();
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                cols.add(new WaterDomain.Column(x, z, new float[]{BED, BED + 3 + SURFACE}, false));
            }
        }
        WaterDomain d = WaterDomain.build(cols, 0, ORIGIN_Y, 0, 0.35f, null);
        assertNotNull(d);
        return d;
    }

    private static FlockEngine school(WaterDomain domain, long seed, int members) {
        FishSpec[] specs = new FishSpec[members];
        for (int i = 0; i < members; i++) specs[i] = new FishSpec(0.3f, Locomotion.FREE_SWIM, (i & 1) == 0, 7);
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        engine.rebuild(specs, seed, 0f, 20f, domain);
        for (int t = 0; t < 60; t++) engine.step();
        return engine;
    }

    /** An anchor at {@code (l, y, d)}, approached along the line from fish {@code i}. */
    private static Shelter anchorFor(FlockEngine engine, int i, float l, float y, float d, Object key) {
        return Shelter.noseAnchor(l, y, d, l - engine.posL()[i], d - engine.posD()[i], HOLD, key);
    }

    /** The far corner from fish {@code i}, well inside the walls, so the swim there is a long one. */
    private static float[] farPoint(FlockEngine engine, int i) {
        float l = engine.posL()[i] < 4f ? 6.2f : 1.8f;
        float d = engine.posD()[i] < 4f ? 6.2f : 1.8f;
        return new float[]{l, 2.8f, d};
    }

    private record Touch(int tick, FlockEngine.TriggerEvent event) {}

    private static List<Touch> run(FlockEngine engine, int ticks, int from) {
        List<Touch> events = new ArrayList<>();
        for (int t = from; t < from + ticks; t++) {
            engine.step();
            final int now = t;
            engine.drainTriggerEvents(e -> events.add(new Touch(now, e)));
        }
        return events;
    }

    @Test
    void aSentFishSwimsAcrossThePoolNosesTheAnchorAndHolds() {
        for (long seed : SEEDS) {
            WaterDomain domain = pond();
            FlockEngine engine = school(domain, seed, 4);
            float[] p = farPoint(engine, 0);
            float distance = (float) Math.hypot(p[0] - engine.posL()[0], p[2] - engine.posD()[0]);
            assertTrue(distance > 3f, "the test needs a swim longer than the clocks' reach: " + distance);
            domain.rebuildShelters(List.of(anchorFor(engine, 0, p[0], p[1], p[2], "bobber")));
            assertTrue(engine.sendToTrigger(0, 0), "seed " + seed);
            assertEquals(0, engine.triggerFish(0));
            // The clocks' approach timeout is sized for their 2.5-block reach and would give up on
            // this swim long before the fish arrives; the host's errand is the host's to give up.
            assertEquals(Float.POSITIVE_INFINITY, engine.approachTimeoutSeconds(0));

            Shelter.Mouth face = domain.shelters().get(0).mouths().get(0);
            int touchedAt = -1, endedAt = -1;
            for (int t = 0; t < 2400 && endedAt < 0; t++) {
                engine.step();
                final int now = t;
                List<FlockEngine.TriggerEvent> events = new ArrayList<>();
                engine.drainTriggerEvents(events::add);
                for (FlockEngine.TriggerEvent e : events) {
                    assertEquals("bobber", e.key());
                    assertEquals(0, e.fish());
                    if (e.start()) {
                        touchedAt = now;
                        float along = (engine.posL()[0] - face.centerL()) * face.normalL()
                                + (engine.posD()[0] - face.centerD()) * face.normalD();
                        assertTrue(along >= -0.45f * 0.3f - 0.02f && along <= 0.02f,
                                "touched from " + along + ", seed " + seed);
                        assertEquals(FlockEngine.SHELTER_INSIDE, engine.shelterState(0));
                    } else {
                        endedAt = now;
                    }
                }
            }
            assertTrue(touchedAt >= 0, "the sent fish never reached the anchor, seed " + seed);
            assertTrue(endedAt > touchedAt, "the hold never ended, seed " + seed);
            float held = (endedAt - touchedAt) * engine.tunables().dt();
            assertTrue(held >= HOLD - 0.1f, "held only " + held + " s, seed " + seed);
        }
    }

    @Test
    void noClockEverSendsAFishToAHostAnchor() {
        for (long seed : SEEDS) {
            WaterDomain domain = pond();
            FlockEngine engine = school(domain, seed, 6);
            domain.rebuildShelters(List.of(Shelter.noseAnchor(4f, 2.8f, 4f, 1f, 0f, HOLD, "bobber")));
            for (int t = 0; t < 12_000; t++) {
                engine.step();
                engine.drainTriggerEvents(e -> {
                    throw new AssertionError("an unsent reaction: " + e);
                });
                for (int i = 0; i < engine.count(); i++) {
                    assertTrue(engine.shelterState(i) == FlockEngine.SHELTER_ROAMING,
                            "fish " + i + " went to a host anchor unsent, tick " + t + ", seed " + seed);
                }
            }
        }
    }

    @Test
    void anotherBobberArrivingOrLeavingKeepsTheErrand() {
        for (long seed : SEEDS) {
            WaterDomain domain = pond();
            FlockEngine engine = school(domain, seed, 4);
            float[] p = farPoint(engine, 0);
            Shelter a = anchorFor(engine, 0, p[0], p[1], p[2], "a");
            domain.rebuildShelters(List.of(a));
            assertTrue(engine.sendToTrigger(0, 0));
            List<Touch> events = run(engine, 10, 0);

            // A second bobber lands, ahead of the first in the list, and its member is sent too.
            float[] q = farPoint(engine, 1);
            q[0] = 8f - q[0];
            Shelter b = anchorFor(engine, 1, q[0], q[1], q[2], "b");
            domain.rebuildShelters(List.of(b, a));
            assertTrue(engine.sendToTrigger(0, 1), "seed " + seed);
            assertEquals(0, engine.triggerFish(1), "a's errand followed its anchor to index 1, seed " + seed);
            assertEquals(FlockEngine.SHELTER_APPROACH, engine.shelterState(0));
            events.addAll(run(engine, 10, 10));

            // Then b's player reels in: a's member keeps going.
            domain.rebuildShelters(List.of(a));
            events.addAll(run(engine, 2400, 20));
            assertTrue(events.stream().anyMatch(e -> e.event().start() && e.event().key().equals("a")),
                    "a's fish was called back by b coming and going, seed " + seed);
        }
    }

    @Test
    void aMemberLeavingForItsFightRenumbersTheErrand() {
        for (long seed : SEEDS) {
            WaterDomain domain = pond();
            FlockEngine engine = school(domain, seed, 4);
            float[] p = farPoint(engine, 2);
            domain.rebuildShelters(List.of(anchorFor(engine, 2, p[0], p[1], p[2], "bobber")));
            assertTrue(engine.sendToTrigger(0, 2));
            run(engine, 10, 0);

            // Member 0 is hooked elsewhere and leaves the engine: member 2 is index 1 now.
            FishSpec spec = new FishSpec(0.3f, Locomotion.FREE_SWIM, false, 7);
            engine.rebuildPreserving(new FishSpec[]{spec, spec, spec}, new int[]{1, 2, 3}, seed, 0f, 20f, domain);
            assertEquals(1, engine.triggerFish(0), "seed " + seed);
            assertEquals(FlockEngine.SHELTER_APPROACH, engine.shelterState(1));

            List<Touch> events = run(engine, 2400, 10);
            assertTrue(events.stream().anyMatch(e -> e.event().start() && e.event().fish() == 1),
                    "the renumbered member never reached its bobber, seed " + seed);
        }
    }

    @Test
    void theFishTakenOutEndsItsErrand() {
        WaterDomain domain = pond();
        FlockEngine engine = school(domain, 7L, 3);
        float[] p = farPoint(engine, 1);
        domain.rebuildShelters(List.of(anchorFor(engine, 1, p[0], p[1], p[2], "bobber")));
        assertTrue(engine.sendToTrigger(0, 1));
        FishSpec spec = new FishSpec(0.3f, Locomotion.FREE_SWIM, false, 7);
        engine.rebuildPreserving(new FishSpec[]{spec, spec}, new int[]{0, 2}, 7L, 0f, 20f, domain);
        assertEquals(-1, engine.triggerFish(0));
        assertTrue(engine.sendToTrigger(0, 0), "the anchor is free for another member");
    }

    @Test
    void reelingInCallsTheFishOff() {
        WaterDomain domain = pond();
        FlockEngine engine = school(domain, 7L, 3);
        float[] p = farPoint(engine, 0);
        domain.rebuildShelters(List.of(anchorFor(engine, 0, p[0], p[1], p[2], "bobber")));
        assertTrue(engine.sendToTrigger(0, 0));
        run(engine, 10, 0);
        domain.rebuildShelters(List.of());
        List<Touch> events = run(engine, 400, 10);
        assertEquals(FlockEngine.SHELTER_ROAMING, engine.shelterState(0));
        assertTrue(events.stream().noneMatch(e -> e.event().start()), "touched an anchor that was gone");
    }

    @Test
    void whatCannotGoIsRefused() {
        WaterDomain domain = pond();
        FlockEngine engine = school(domain, 7L, 3);
        domain.rebuildShelters(List.of(
                Shelter.noseAnchor(4f, 2.8f, 4f, 1f, 0f, HOLD, "a"),
                Shelter.noseAnchor(30f, 2.8f, 4f, 1f, 0f, HOLD, "dry land"),
                new Shelter(Shelter.OrientedBox.ofBounds(3f, 2.7f, 3f, 3.1f, 2.9f, 3.1f),
                        Shelter.OrientedBox.ofBounds(3f, 2.7f, 3f, 3.1f, 2.9f, 3.1f),
                        Shelter.noseAnchor(3f, 2.8f, 3f, 1f, 0f, HOLD, "x").mouths(), 1, 0.05f,
                        Shelter.Kind.TRIGGER, 0f, new Shelter.Trigger(20f, HOLD, "clam"))));
        assertFalse(engine.sendToTrigger(1, 0), "an anchor out of the water");
        assertFalse(engine.sendToTrigger(2, 0), "a clock's trigger");
        assertFalse(engine.sendToTrigger(0, 5), "no such fish");
        assertFalse(engine.sendToTrigger(7, 0), "no such shelter");
        assertTrue(engine.sendToTrigger(0, 0));
        assertFalse(engine.sendToTrigger(0, 1), "the anchor already has its fish");

        FlockEngine crabs = new FlockEngine(Tunables.GROUP);
        WaterDomain crabWater = pond();
        crabs.rebuild(new FishSpec[]{new FishSpec(0.3f, Locomotion.BENTHIC, false, 7)}, 7L, 0f, 20f, crabWater);
        crabWater.rebuildShelters(List.of(Shelter.noseAnchor(4f, 2.8f, 4f, 1f, 0f, HOLD, "a")));
        assertFalse(crabs.sendToTrigger(0, 0), "only free swimmers run errands");
    }

    /** Mean speed of fish 0 over its approach (send to touch), blocks/s; NaN if it never touched. */
    private static float approachSpeed(long seed, float speedScale, float[] distanceOut) {
        WaterDomain domain = pond();
        FlockEngine engine = school(domain, seed, 4);
        float[] p = farPoint(engine, 0);
        distanceOut[0] = (float) Math.hypot(p[0] - engine.posL()[0], p[2] - engine.posD()[0]);
        domain.rebuildShelters(List.of(anchorFor(engine, 0, p[0], p[1], p[2], "bobber")));
        assertTrue(engine.sendToTrigger(0, 0, speedScale));
        float path = 0;
        for (int t = 0; t < 2400; t++) {
            float l = engine.posL()[0], y = engine.posY()[0], d = engine.posD()[0];
            engine.step();
            float dl = engine.posL()[0] - l, dy = engine.posY()[0] - y, dd = engine.posD()[0] - d;
            path += (float) Math.sqrt(dl * dl + dy * dy + dd * dd);
            boolean[] touched = new boolean[1];
            engine.drainTriggerEvents(e -> touched[0] |= e.start());
            if (touched[0]) return path / ((t + 1) * engine.tunables().dt());
        }
        return Float.NaN;
    }

    @Test
    void anEagerErrandApproachesFasterAndStillNosesTheAnchor() {
        for (long seed : SEEDS) {
            float[] dist = new float[1];
            float calm = approachSpeed(seed, 1f, dist);
            float eager = approachSpeed(seed, 2f, dist);
            System.out.printf("[host-trigger] seed %d: %.1f blocks, approach %.3f b/s calm, %.3f b/s at 2x%n",
                    seed, dist[0], calm, eager);
            assertTrue(Float.isFinite(calm) && Float.isFinite(eager), "an approach never touched, seed " + seed);
            assertTrue(eager > calm * 1.5f, "2x approached at " + eager + " vs " + calm + ", seed " + seed);
        }
    }

    @Test
    void aSpeedScaleBelowOneIsRefused() {
        WaterDomain domain = pond();
        FlockEngine engine = school(domain, 7L, 3);
        domain.rebuildShelters(List.of(Shelter.noseAnchor(4f, 2.8f, 4f, 1f, 0f, HOLD, "a")));
        assertFalse(engine.sendToTrigger(0, 0, 0.5f));
        assertFalse(engine.sendToTrigger(0, 0, Float.NaN));
        assertFalse(engine.sendToTrigger(0, 0, Float.POSITIVE_INFINITY));
        assertEquals(-1, engine.triggerFish(0));
    }
}
