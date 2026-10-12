package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenthicVisibilityTest {
    @Test
    void straightWalkingSeenShareMeetsThePerchedBaseline() {
        for (String script : List.of("walk_flat", "walk_meander")) {
            int groundSeen = 0, perchSeen = 0, walking = 0;
            for (long seed = 1; seed <= 3; seed++) {
                CompanionRun ground = CompanionRun.benthic(script, seed, 0.3f, Temperament.BASELINE,
                        GroundGait.CRAWL, CompanionTunables.defaults(), true);
                CompanionRun perch = CompanionRun.benthic(script, seed, 0.3f, Temperament.BASELINE,
                        null, CompanionTunables.defaults(), true);
                ground.run(2400);
                perch.run(2400);
                for (int tick = 0; tick < 2400; tick++) {
                    if (ground.samples.get(tick).owner().horizontalSpeed() <= 1) continue;
                    walking++;
                    if (ground.samples.get(tick).seen()) groundSeen++;
                    if (perch.samples.get(tick).seen()) perchSeen++;
                }
            }
            System.out.printf("BENTHIC_SEEN %s walking=%d ground=%.2f%% perch=%.2f%%%n",
                    script, walking, 100.0 * groundSeen / walking, 100.0 * perchSeen / walking);
            if (script.equals("walk_flat")) assertTrue(groundSeen >= perchSeen,
                    "straight walking ground=" + groundSeen + " baseline=" + perchSeen + " ticks=" + walking);
        }
    }
}
