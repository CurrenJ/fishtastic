package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.util.List;

/** Equal-seed recovery A/B. -PsimStdout -Psweep=0,1 -PsweepSeeds=60; optional -Ptrace=low_ceiling. */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class AlongsideRecoveryProbe {
    record Cfg(String script, float length) {}
    @Test
    void ab() {
        int seeds = Integer.parseInt(System.getProperty("sweepSeeds", "20"));
        String[] modes = System.getProperty("sweep", "0,3").split(",");
        String only = System.getProperty("trace");
        boolean saved = GroundMind.alongsideRecoveryEnabled;
        for (Cfg c : List.of(new Cfg("low_ceiling", 0.3f), new Cfg("jumping", 0.3f), new Cfg("walk_flat", 0.3f),
                new Cfg("walk_meander", 0.3f), new Cfg("stairs_up_down", 0.3f), new Cfg("low_ceiling", 0.6f),
                new Cfg("jumping", 0.6f), new Cfg("walk_flat", 0.6f), new Cfg("walk_meander", 0.6f),
                new Cfg("stairs_up_down", 0.6f))) {
            if (only != null && !only.equals(c.script())) continue;
            int[] baseSeen = new int[seeds + 1];
            for (String mode : modes) {
                GroundMind.alongsideRecoveryEnabled = Integer.parseInt(mode.trim()) != 0;
                int fades = 0, leash = 0, insideOver = 0, unseenOver = 0, up = 0, down = 0;
                long backstops = 0, seenWalk = 0, insideTicks = 0;
                double inside = 0, unseen = 0;
                for (int seed = 1; seed <= seeds; seed++) {
                    CompanionRun run = CompanionRun.benthic(c.script(), seed, c.length(), Temperament.BASELINE,
                            GroundGait.CRAWL, CompanionTunables.defaults(), true);
                    run.run(2400);
                    CompanionRun.Metrics m = run.metrics();
                    double share = m.insideOwnerTicks() / (double) m.ticks();
                    fades += (int) run.groundMetrics().fades();
                    leash += m.leashEvents() > 0 ? 1 : 0;
                    backstops += m.backstops();
                    insideTicks += m.insideOwnerTicks();
                    inside = Math.max(inside, share);
                    insideOver += share > 0.01 ? 1 : 0;
                    unseen = Math.max(unseen, m.longestUnseenSeconds());
                    unseenOver += m.longestUnseenSeconds() > 30 ? 1 : 0;
                    int sw = 0;
                    for (CompanionRun.Sample s : run.samples) if (s.owner().horizontalSpeed() > 1 && s.seen()) sw++;
                    seenWalk += sw;
                    if (only != null) System.out.printf("  SEED m=%s %-12s #%d unseen %.1fs inside %d walkSeen %d%n",
                            mode, c.script(), seed, m.longestUnseenSeconds(), m.insideOwnerTicks(), sw);
                    if (mode.equals(modes[0])) baseSeen[seed] = sw;
                    else if (sw > baseSeen[seed]) up++;
                    else if (sw < baseSeen[seed]) down++;
                }
                System.out.printf("AB m=%s %-15s len=%.2f inside ticks=%4d max=%.3f runs>1%%=%d | unseen max=%5.1fs runs>30s=%d"
                                + " | fades=%d leashRuns=%d backstops=%3d | walkSeen=%d (seeds up %d down %d)%n",
                        mode, c.script(), c.length(), insideTicks, inside, insideOver, unseen, unseenOver,
                        fades, leash, backstops, seenWalk, up, down);
            }
        }
        GroundMind.alongsideRecoveryEnabled = saved;
    }
}
