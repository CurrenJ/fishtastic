package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.Map;
import java.util.TreeMap;

/**
 * Diagnostic: how far a drifter's bell leans, and why. A tilt histogram for idle ticks
 * (body idling) and the rest, and for every tick leaning past 45°, what it was steering at: the
 * need's direction (down or level means it is braking or letting itself fall, not travelling),
 * its goal, calm, and the speed asked against the speed it had. -PsimStdout
 * -Psweep=idle_fixed,walk -PsweepSeeds=8 -PsweepTicks=2400 -PtweakA=...
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class DriftTiltProbe {

    static final int[] EDGES = {15, 30, 45, 60, 75, 91};

    @Test
    void tilt() {
        String[] scripts = System.getProperty("sweep", "idle_fixed,idle_looking,walk,walk_turns,sprint_stop,forest").split(",");
        int seeds = Integer.parseInt(System.getProperty("sweepSeeds", "8"));
        int ticks = Integer.parseInt(System.getProperty("sweepTicks", "2400"));
        System.out.println("== DRIFT tilt " + System.getProperty("tweakA", "defaults"));
        System.out.println("  script       state  ticks   0-15  15-30 30-45 45-60 60-75 75-90 |  >45: needDown needLevel(<30°up) calm>.5 ask<.5  meanAsk meanSpd  vy<-.2  goals");
        for (String script : scripts) {
            long[][] bins = new long[2][EDGES.length];
            long[] n = new long[2], hi = new long[2], down = new long[2], level = new long[2], calmHi = new long[2], askLo = new long[2], falling = new long[2];
            double[] askSum = new double[2], spdSum = new double[2];
            @SuppressWarnings("unchecked") Map<String, Integer>[] goals = new Map[]{new TreeMap<>(), new TreeMap<>()};
            for (long seed = 1; seed <= seeds; seed++) {
                CompanionTunables t = CompanionTunables.defaults();
                SeedSweepProbe.tweak(System.getProperty("tweakA")).accept(t);
                CompanionRun run = CompanionRun.drifter(script, seed, DriftStyle.BELL, SeedSweepProbe.temperament(), t, false);
                DriftBody b = (DriftBody) DriftStrokeVisibilityProbe.bodyOf(run.agent);
                for (int k = 0; k < ticks; k++) {
                    run.tick();
                    if (k < 40 || run.agent.appear() < 1f) continue;
                    int s = b.idling ? 0 : 1;
                    n[s]++;
                    int bin = 0;
                    while (b.tilt >= EDGES[bin]) bin++;
                    bins[s][bin]++;
                    if (b.tilt > 45) {
                        hi[s]++;
                        Vec need = b.need;
                        double up = Math.toDegrees(Math.atan2(need.y(), need.horizontalLength()));
                        if (up <= 0) down[s]++;
                        else if (up < 30) level[s]++;
                        if (b.calm > 0.5f) calmHi[s]++;
                        if (b.asked.length() < 0.5) askLo[s]++;
                        if (b.vel.y() < -0.2) falling[s]++;
                        askSum[s] += b.asked.length();
                        spdSum[s] += b.vel.length();
                        goals[s].merge(run.agent.goal(), 1, Integer::sum);
                    }
                }
            }
            for (int s = 0; s < 2; s++) {
                if (n[s] == 0) continue;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < EDGES.length; i++) sb.append(String.format(" %5.1f", 100.0 * bins[s][i] / n[s]));
                long h = Math.max(1, hi[s]);
                System.out.printf("  %-12s %-5s %6d %s | %5.1f%% %6.1f%%  %8.0f%% %7.0f%% %7.0f%% %7.2f %7.2f %6.0f%%  %s%n",
                        script, s == 0 ? "IDLE" : "move", n[s], sb, 100.0 * hi[s] / n[s],
                        100.0 * down[s] / h, 100.0 * level[s] / h, 100.0 * calmHi[s] / h, 100.0 * askLo[s] / h,
                        askSum[s] / h, spdSum[s] / h, 100.0 * falling[s] / h, goals[s]);
            }
        }
    }
}
