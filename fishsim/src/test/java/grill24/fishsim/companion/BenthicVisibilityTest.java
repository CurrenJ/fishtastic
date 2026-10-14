package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenthicVisibilityTest {
    @Test
    void straightWalkingSeenShareMeetsThePerchedBaseline() {
        // Ground follow against riding the shoulder, as designed: with the hand clock off. With it
        // on, the percher peeks and the walker can't, and the owner accepted the gap (2026-10-14);
        // those figures are printed, not asserted.
        CompanionTunables t = CompanionTunables.defaults();
        t.handClock = false;
        run(t, true);
        run(CompanionTunables.defaults(), false);
    }

    private static void run(CompanionTunables t, boolean assertBaseline) {
        for (String script : List.of("walk_flat", "walk_meander")) {
            int groundSeen = 0, perchSeen = 0, walking = 0;
            for (long seed = 1; seed <= 3; seed++) {
                CompanionRun ground = CompanionRun.benthic(script, seed, 0.3f, Temperament.BASELINE,
                        GroundGait.CRAWL, t, true);
                CompanionRun perch = CompanionRun.benthic(script, seed, 0.3f, Temperament.BASELINE,
                        null, t, true);
                ground.run(2400);
                perch.run(2400);
                for (int tick = 0; tick < 2400; tick++) {
                    if (ground.samples.get(tick).owner().horizontalSpeed() <= 1) continue;
                    walking++;
                    if (ground.samples.get(tick).seen()) groundSeen++;
                    if (perch.samples.get(tick).seen()) perchSeen++;
                }
            }
            System.out.printf("BENTHIC_SEEN clock=" + t.handClock + " %s walking=%d ground=%.2f%% perch=%.2f%%%n",
                    script, walking, 100.0 * groundSeen / walking, 100.0 * perchSeen / walking);
            if (assertBaseline && script.equals("walk_flat")) assertTrue(groundSeen >= perchSeen,
                    "straight walking ground=" + groundSeen + " baseline=" + perchSeen + " ticks=" + walking);
        }
    }
}
