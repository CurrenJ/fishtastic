package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Measurement: fast turn reversals (a weave) and yaw-rate spectrum peak, per goal. A limit cycle
 * shows up as many quick left-right reversals at one period. Run with -PsimStdout.
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class WobbleProbe {
    @Test
    void probe() {
        for (String script : new String[]{"walk", "idle_fixed", "sprint_stop", "walk_turns"}) {
            CompanionRun run = new CompanionRun(script, 1, CompanionTunables.defaults(), true);
            run.run(6000);
            java.util.Map<String, int[]> perGoal = new java.util.TreeMap<>();
            int lastSign = 0, lastFlip = -1000;
            for (int i = 0; i < run.samples.size(); i++) {
                var s = run.samples.get(i);
                int[] c = perGoal.computeIfAbsent(s.goal(), k -> new int[2]);
                c[1]++;
                int sign = Math.abs(s.turn()) > 1.0f ? (int) Math.signum(s.turn()) : 0;
                if (sign != 0) {
                    if (lastSign != 0 && sign != lastSign && i - lastFlip < 15) c[0]++;
                    if (sign != lastSign) lastFlip = i;
                    lastSign = sign;
                }
            }
            StringBuilder b = new StringBuilder(script + ":");
            perGoal.forEach((g, c) -> b.append(String.format(" %s %.1f fast-reversals/min (%.0fs)", g, c[0] / (c[1] / 1200.0), c[1] / 20.0)));
            System.out.println(b);
        }
    }
}
