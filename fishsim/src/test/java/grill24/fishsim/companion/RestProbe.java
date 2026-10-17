package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;
import grill24.fishsim.harness.companion.CompanionRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Diagnostic for the rest drive (plan §10, step 2f), 8 seeds: over a 10-minute idle, when it first
 * settles, how much of the time it is settled, how fast it moves settled vs not, and how much the
 * owner sees it; then, over scripts where an idle owner sets off, how quickly it wakes (leaves
 * SETTLE). Run with -PsimStdout [-PsweepClass=BENTHIC] [-PsweepTemperament=...].
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class RestProbe {

    static final int SEEDS = 8;

    @Test
    void rest() {
        Locomotion c = Locomotion.valueOf(System.getProperty("sweepClass", "FREE_SWIM"));
        Temperament temper = SeedSweepProbe.temperament();
        double firstSettle = 0, settledShare = 0, seen = 0, vSettled = 0, vAwake = 0;
        int nSettled = 0, nAwake = 0;
        for (int seed = 1; seed <= SEEDS; seed++) {
            CompanionRun run = new CompanionRun("idle_fixed", seed, c, temper, CompanionTunables.defaults(), true);
            int first = -1, settled = 0;
            for (int t = 0; t < 12000; t++) {
                run.tick();
                if (run.agent.resting()) {
                    settled++;
                    if (first < 0) first = t;
                }
                double v = run.agent.velocity().length();
                if (run.agent.goal().equals("SETTLE")) {
                    vSettled += v;
                    nSettled++;
                } else if (run.agent.goal().equals("LOITER")) {
                    vAwake += v;
                    nAwake++;
                }
            }
            firstSettle += first / 20.0 / SEEDS;
            settledShare += settled / 12000.0 / SEEDS;
            seen += run.metrics().seenFraction() / SEEDS;
        }
        System.out.printf("== %s %s idle_fixed 10 min: first settles %.0f s in, settled %.0f%%, speed settled %.2f vs loiter %.2f b/s, seen %.0f%%%n",
                c, temper.name(), firstSettle, settledShare * 100, nSettled == 0 ? 0 : vSettled / nSettled,
                nAwake == 0 ? 0 : vAwake / nAwake, seen * 100);

        Map<String, double[]> wake = new LinkedHashMap<>();
        for (String script : new String[]{"held_items", "cat_lounging", "companion_passes", "item_drops"}) {
            for (int seed = 1; seed <= SEEDS; seed++) {
                CompanionRun run = new CompanionRun(script, seed, c, temper, CompanionTunables.defaults(), true);
                boolean wasResting = false;
                int setOff = -1;
                for (int t = 0; t < 2400; t++) {
                    run.tick();
                    boolean moving = run.owner.horizontalSpeed() > 1.0;
                    if (moving && setOff < 0 && wasResting) setOff = t;
                    if (setOff >= 0 && !run.agent.resting()) {
                        double[] w = wake.computeIfAbsent(script, k -> new double[2]);
                        w[0] += (t - setOff) / 20.0;
                        w[1]++;
                        setOff = -1;
                    }
                    wasResting = run.agent.resting() && !moving;
                }
            }
        }
        wake.forEach((s, w) -> System.out.printf("   wakes when the owner sets off (%s): %.2f s over %d wakings%n",
                s, w[0] / w[1], (int) w[1]));
    }
}
