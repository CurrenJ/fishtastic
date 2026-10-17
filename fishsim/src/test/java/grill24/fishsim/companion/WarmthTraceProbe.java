package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;
import grill24.fishsim.harness.companion.CompanionRun;
import grill24.fishsim.harness.companion.MoodScenes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Diagnostic: one warmth run, a line a second. -PsimStdout. */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class WarmthTraceProbe {
    @Test
    void trace() {
        CompanionRun run = new CompanionRun("idle_fixed", 3, Locomotion.FREE_SWIM, CompanionTunables.defaults(), true);
        MoodScenes.apply(run, MoodPresets.named("warm"), run.owner.feet());
        Vec fire = run.script.ambient.warmth().get(0);
        for (int i = 0; i < 2400; i++) {
            run.run(1);
            if (i % 20 != 0) continue;
            CompanionAgent a = run.agent;
            Drives d = a.drivesForTest();
            System.out.printf("WT %5.1f goal=%-8s %s cur=%.2f tgt=%s fireD=%.2f rest=%.2f%n", i / 20.0, a.goal(),
                    a.moodDescription(), d.curiosity, d.target == null ? "-" : String.valueOf(d.target.kind),
                    a.position().distanceTo(fire), d.rest);
        }
    }
}
