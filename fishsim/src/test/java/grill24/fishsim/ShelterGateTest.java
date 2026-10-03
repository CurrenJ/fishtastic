package grill24.fishsim;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.Shelter;
import grill24.fishsim.domain.VoxelDomain;
import grill24.fishsim.harness.Scenarios;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gates (docs/fish-shelters.md §12.4): an opening swum straight through, by any swimmer that fits,
 * only in the direction it is already going, without stopping, and never as cover.
 */
class ShelterGateTest {

    private static final long[] SEEDS = {12345L, -987654321L, 7L};

    @Test
    void aGateIsSwumStraightThroughByFishThatNeverHide() {
        int through = 0;
        for (long seed : SEEDS) {
            VoxelDomain domain = Scenarios.domain("3x1x3+arch");
            Shelter gate = domain.shelters().get(0);
            FlockEngine engine = new FlockEngine(Tunables.GROUP);
            // Scenarios.specs: no species opts into shelters, and a gate needs none to.
            FishSpec[] specs = Scenarios.specs(10, seed);
            engine.rebuild(specs, seed, 0f, 0f, domain);
            int n = engine.count();
            int[] entry = new int[n], prev = new int[n];
            for (int tick = 0; tick < 12_000; tick++) {
                engine.step();
                for (int i = 0; i < n; i++) {
                    int st = engine.shelterState(i);
                    assertFalse(st == FlockEngine.SHELTER_INSIDE || st == FlockEngine.SHELTER_RESTING,
                            "fish " + i + " stopped in a gate, seed " + seed);
                    if (st == FlockEngine.SHELTER_APPROACH && prev[i] == FlockEngine.SHELTER_ROAMING) {
                        // Taken in the direction of travel: within 45° of straight through.
                        Shelter.Mouth m = gate.mouths().get(engine.shelterMouth(i));
                        double yr = Math.toRadians(engine.yawDeg[i]);
                        double along = Math.cos(yr) * m.normalL() - Math.sin(yr) * m.normalD();
                        assertTrue(along >= 0.64, "fish " + i + " turned " + Math.toDegrees(Math.acos(along))
                                + "° to take a gate, seed " + seed);
                    }
                    if (st == FlockEngine.SHELTER_ENTER && prev[i] != st) entry[i] = engine.shelterMouth(i);
                    if (st == FlockEngine.SHELTER_EXIT && prev[i] == FlockEngine.SHELTER_ENTER) {
                        assertTrue(engine.shelterMouth(i) != entry[i], "fish " + i + " backed out of a gate, seed " + seed);
                        through++;
                    }
                    prev[i] = st;
                }
            }
        }
        assertTrue(through >= 5, "too few passes to judge: " + through);
    }

    @Test
    void aGateIsNeverCover() {
        VoxelDomain domain = Scenarios.domain("3x1x3+arch");
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        engine.rebuild(Scenarios.skittish(8, 12345L), 12345L, 0f, 0f, domain);
        int dashes = 0, lunges = 0;
        for (int tick = 0; tick < 6_000; tick++) {
            // Step up to the glass and back every 10 s: a lunge each time.
            boolean near = (tick / 200) % 2 == 1;
            engine.setWatcher(true, 0f, 0f, near ? 2.0f : 6f);
            if (near && tick % 200 == 0) lunges++;
            engine.step();
            for (int i = 0; i < engine.count(); i++) {
                if (engine.dashing(i)) dashes++;
            }
        }
        assertTrue(lunges > 10, "the watcher never lunged");
        assertEquals(0, dashes, "a startled fish dashed for a gate, which hides nobody");
    }
}
