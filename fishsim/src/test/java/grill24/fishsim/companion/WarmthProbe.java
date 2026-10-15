package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;
import grill24.fishsim.harness.companion.CompanionRun;
import grill24.fishsim.harness.companion.MoodScenes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Diagnostic: the warmth reaction tick by tick on the idle scripts (plan §5 Phase 5). -PsimStdout. */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class WarmthProbe {
    @Test
    void warmth() {
        for (Locomotion c : java.util.List.of(Locomotion.FREE_SWIM, Locomotion.DRIFT, Locomotion.BENTHIC)) {
            for (String script : java.util.List.of("idle_fixed", "idle_looking")) {
                for (long seed = 1; seed <= 3; seed++) {
                    CompanionRun run = new CompanionRun(script, seed, c, CompanionTunables.defaults(), true);
                    MoodScenes.apply(run, MoodScenes.named("warm"), run.owner.feet());
                    Vec fire = run.script.ambient.warmth().get(0);
                    int first = -1, baskTicks = 0, entries = 0;
                    double minD = 99;
                    boolean was = false;
                    StringBuilder spans = new StringBuilder();
                    int spanStart = 0;
                    for (int i = 0; i < 2400; i++) {
                        run.run(1);
                        boolean b = run.agent.basking();
                        if (b) {
                            baskTicks++;
                            minD = Math.min(minD, run.agent.position().distanceTo(fire));
                            if (first < 0) first = i;
                        }
                        if (b && !was) { entries++; spanStart = i; }
                        if (!b && was) spans.append(String.format("%.1f-%.1f ", spanStart / 20.0, i / 20.0));
                        was = b;
                    }
                    System.out.printf("WARM %-9s %-12s #%d first=%.1fs bask=%.0f%% entries=%d minDist=%.2f fireFromChest=%.2f spans=%s%n",
                            c, script, seed, first / 20.0, baskTicks / 24.0, entries, minD,
                            fire.distanceTo(run.owner.chest()), spans);
                }
            }
        }
    }
}
