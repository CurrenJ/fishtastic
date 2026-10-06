package grill24.fishsim;

import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.VoxelDomain;
import grill24.fishsim.harness.Scenarios;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A shelter with a mouth at each end is passed through (docs/fish-shelters.md §12.4): a fish goes
 * in one end of the Clay Pipe and out of the other, rather than turning round inside and backing
 * out. A one-mouthed log is still left the way it was entered. ShelterVisitTest's matrix holds
 * every other visit invariant for the pipe.
 */
class ShelterPassThroughTest {

    /** Visits that reached INSIDE and then left, counted by whether they left by the far mouth. */
    private static int[] visits(String domainName, String cast, long seed) {
        VoxelDomain domain = Scenarios.domain(domainName);
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        engine.rebuild(Scenarios.cast(cast, 8, seed), seed, 0f, 0f, domain);
        int n = engine.count();
        int[] entry = new int[n], prev = new int[n];
        boolean[] wasInside = new boolean[n];
        int through = 0, back = 0;
        for (int tick = 0; tick < 12_000; tick++) {
            engine.step();
            for (int i = 0; i < n; i++) {
                int st = engine.shelterState(i);
                if (st == FlockEngine.SHELTER_ENTER && prev[i] != st) {
                    entry[i] = engine.shelterMouth(i);
                    wasInside[i] = false;
                }
                if (st == FlockEngine.SHELTER_INSIDE) wasInside[i] = true;
                if (st == FlockEngine.SHELTER_ROAMING && prev[i] == FlockEngine.SHELTER_EXIT && wasInside[i]) {
                    if (engine.shelterMouth(i) != entry[i]) through++;
                    else back++;
                    wasInside[i] = false;
                }
                prev[i] = st;
            }
        }
        return new int[]{through, back};
    }

    @Test
    void aPipeIsSwumThrough() {
        for (String domain : new String[]{"3x1x1+pipe", "4x1x2+pipe"}) {
            for (long seed : new long[]{12345L, -987654321L}) {
                int[] v = visits(domain, "loaches", seed);
                String what = domain + " seed " + seed + ": " + v[0] + " through, " + v[1] + " back";
                assertTrue(v[0] >= 3, "too few visits to judge, " + what);
                assertEquals(0, v[1], "a fish backed out of a pipe it could have swum through, " + what);
            }
        }
    }

    @Test
    void aLogIsStillBackedOutOf() {
        int[] v = visits("3x1x1+log", "loaches", 12345L);
        assertEquals(0, v[0], "a fish left a one-mouthed log by a mouth it doesn't have");
        assertTrue(v[1] >= 3, "too few visits to judge: " + v[1]);
    }
}
