package grill24.fishsim;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.Shelter;
import grill24.fishsim.domain.VoxelDomain;
import grill24.fishsim.harness.Scenarios;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Triggers (docs/fish-shelters.md §12.13): a structure's own rare clock sends a fish to nose its
 * anchor, or through its locked gate; one reaction at a time per domain, a rest after each, and
 * nobody goes of its own accord.
 */
class ShelterTriggerTest {

    private static final long[] SEEDS = {12345L, -987654321L, 7L};
    private static final float DT = 1f / 20f;

    /** The sand in a one-storey voxel domain, which is centred on the origin. */
    private static final float FLOOR = -0.5f + Scenarios.SAND_SURFACE;

    /** A rock on the sand in the middle of a 3x1x3, with an anchor on its front face (+depth). */
    private static VoxelDomain clamDomain(float meanSeconds) {
        VoxelDomain domain = Scenarios.domain("3x1x3");
        Shelter.OrientedBox rock = Shelter.OrientedBox.ofBounds(-0.2f, FLOOR, -0.15f, 0.2f, FLOOR + 0.3f, 0.15f);
        domain.rebuildObstacles(List.of(rock));
        float y = FLOOR + 0.2f;
        Shelter.OrientedBox anchor = Shelter.OrientedBox.ofBounds(-0.05f, y - 0.05f, 0.1f, 0.05f, y + 0.05f, 0.15f);
        Shelter.Mouth face = new Shelter.Mouth(0f, y, 0.15f, 0f, 0f, -1f, 1f, 0f, 0f, 0.05f, 0.05f);
        domain.rebuildShelters(List.of(new Shelter(anchor, anchor, List.of(face), 1, 0.05f, Shelter.Kind.TRIGGER, 0f,
                new Shelter.Trigger(meanSeconds, 2f, "clam"))));
        return domain;
    }

    private record Event(int tick, FlockEngine.TriggerEvent event) {}

    @Test
    void aStructureSendsAFishToNoseItsAnchor() {
        int touches = 0;
        for (long seed : SEEDS) {
            VoxelDomain domain = clamDomain(20f);
            Shelter clam = domain.shelters().get(0);
            Shelter.Mouth face = clam.mouths().get(0);
            FlockEngine engine = new FlockEngine(Tunables.GROUP);
            FishSpec[] specs = Scenarios.specs(10, seed);
            engine.rebuild(specs, seed, 0f, 0f, domain);
            List<Event> events = new ArrayList<>();
            for (int tick = 0; tick < 12_000; tick++) {
                engine.step();
                final int now = tick;
                engine.drainTriggerEvents(e -> events.add(new Event(now, e)));
                for (int i = 0; i < engine.count(); i++) {
                    // Nobody goes to a trigger of its own accord.
                    if (engine.shelterState(i) != FlockEngine.SHELTER_ROAMING && engine.shelterIndex(i) == 0) {
                        assertEquals(i, engine.triggerFish(0), "fish " + i + " went to the clam unsent, seed " + seed);
                    }
                }
                if (!events.isEmpty() && events.get(events.size() - 1).tick() == tick && events.get(events.size() - 1).event().start()) {
                    // At the touch, the fish holds its nose on the face, lined up on the anchor.
                    int f = events.get(events.size() - 1).event().fish();
                    float along = (engine.posL()[f] - face.centerL()) * face.normalL() + (engine.posY()[f] - face.centerY()) * face.normalY()
                            + (engine.posD()[f] - face.centerD()) * face.normalD();
                    float len = engine.lengths[f];
                    assertTrue(along >= -0.45f * len - 0.02f && along <= 0.02f,
                            "fish " + f + " touched from " + along + " (length " + len + "), seed " + seed);
                    assertEquals(FlockEngine.SHELTER_INSIDE, engine.shelterState(f));
                    assertEquals("clam", events.get(events.size() - 1).event().key());
                    touches++;
                }
            }
            // Starts and ends alternate, and after each end the domain rests before the next start.
            int lastEnd = -1_000_000;
            for (int k = 0; k < events.size(); k++) {
                assertEquals(k % 2 == 0, events.get(k).event().start(), "events out of order, seed " + seed);
                if (k % 2 == 0) {
                    assertTrue((events.get(k).tick() - lastEnd) * DT >= 30f - 1e-3f,
                            "a reaction started " + (events.get(k).tick() - lastEnd) * DT + " s after the last, seed " + seed);
                } else {
                    lastEnd = events.get(k).tick();
                }
            }
        }
        assertTrue(touches >= 6, "too few touches to judge: " + touches);
    }

    @Test
    void aLockedGateLetsThroughOnlyTheFishItSends() {
        int passes = 0, starts = 0;
        for (long seed : SEEDS) {
            VoxelDomain domain = Scenarios.domain("3x1x3+arch");
            Shelter gate = domain.shelters().get(0).withTrigger(new Shelter.Trigger(15f, 0f, "portcullis"));
            domain.rebuildShelters(List.of(gate));
            FlockEngine engine = new FlockEngine(Tunables.GROUP);
            engine.rebuild(Scenarios.specs(10, seed), seed, 0f, 0f, domain);
            int n = engine.count();
            int[] prev = new int[n];
            int open = 0;
            for (int tick = 0; tick < 12_000; tick++) {
                engine.step();
                int[] delta = new int[1];
                engine.drainTriggerEvents(e -> delta[0] += e.start() ? 1 : -1);
                if (delta[0] > 0) starts++;
                open += delta[0];
                assertTrue(open == 0 || open == 1, "two reactions at once, seed " + seed);
                for (int i = 0; i < n; i++) {
                    int st = engine.shelterState(i);
                    if (engine.triggerFish(0) != i) {
                        // Shut: nobody else so much as reaches into the doorway.
                        assertTrue(!gate.interior().contains(engine.posL()[i], engine.posY()[i], engine.posD()[i]),
                                "fish " + i + " is in a locked gate it was not sent through, tick " + tick + ", seed " + seed);
                    }
                    if (st == FlockEngine.SHELTER_EXIT && prev[i] == FlockEngine.SHELTER_ENTER) passes++;
                    prev[i] = st;
                }
            }
        }
        assertTrue(starts >= 6, "too few sendings to judge: " + starts);
        assertTrue(passes >= starts * 0.7, "only " + passes + " of " + starts + " sent fish got through");
    }
}
