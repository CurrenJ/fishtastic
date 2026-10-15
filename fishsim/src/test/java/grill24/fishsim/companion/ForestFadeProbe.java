package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;
import grill24.fishsim.harness.companion.CompanionRun;
import grill24.fishsim.harness.companion.MoodScenes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Diagnostic: a benthic's fades in the forest, baseline on fresh seeds vs moods, and when they happen. -PsimStdout. */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class ForestFadeProbe {
    @Test
    void forest() {
        long base = 0;
        for (long seed = 41; seed <= 200; seed++) {
            CompanionRun r = new CompanionRun("forest", seed, Locomotion.BENTHIC, CompanionTunables.defaults(), true);
            r.run(2400);
            base += r.agent.leashEvents();
        }
        System.out.println("FOREST baseline seeds 41-200: " + base);
        for (String name : new String[]{"sleepy", "glum"}) {
            for (long seed = 1; seed <= 40; seed++) {
                CompanionRun r = new CompanionRun("forest", seed, Locomotion.BENTHIC, CompanionTunables.defaults(), true);
                MoodScenes.apply(r, MoodScenes.named(name), r.owner.feet());
                long prev = 0;
                for (int i = 0; i < 2400; i++) {
                    r.run(1);
                    if (r.agent.leashEvents() != prev) {
                        prev = r.agent.leashEvents();
                        CompanionAgent a = r.agent;
                        System.out.printf("FOREST %s #%d fade at %.1fs goal=%s rest=%.2f resting=%s ownerMoving=%s %s%n", name, seed,
                                i / 20.0, a.goal(), a.rest(), a.resting(), a.ownerMoving(), a.moodDescription());
                    }
                }
            }
        }
    }
}
