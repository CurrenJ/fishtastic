package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;
import grill24.fishsim.harness.companion.CompanionRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Diagnostic for the curiosity drive (plan §10, step 2d): over 8 seeds, for each held item in the
 * {@code held_items} script, how long until the fish engages with it, how long it stays engaged,
 * and how much of that the owner sees; then how often dropped items and passing companions are
 * inspected. Diagnostics, not objectives. Run with -PsimStdout [-PsweepClass=BENTHIC].
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class CuriosityProbe {

    static final int SEEDS = 8, TICKS = 2400;

    /** Engaged with a held item: inspecting it (a swimmer) or on the hand nosing at it (a percher). */
    private static boolean engagedHeld(CompanionRun.Sample s) {
        return switch (s.pose()) {
            case "EXCITED", "BEG", "NOSE", "TILT", "MILD" -> true;
            default -> false;
        };
    }

    @Test
    void curiosity() {
        Locomotion c = Locomotion.valueOf(System.getProperty("sweepClass", "FREE_SWIM"));
        System.out.println("== " + c + " held_items: per held stretch, mean over " + SEEDS + " seeds");
        Map<String, double[]> acc = new LinkedHashMap<>(); // label -> {n, latency, engaged, seen, never, engagedRuns}
        for (int seed = 1; seed <= SEEDS; seed++) {
            CompanionRun run = new CompanionRun("held_items", seed, c, CompanionTunables.defaults(), true);
            List<CompanionWorld.Held> held = new ArrayList<>();
            for (int i = 0; i < TICKS; i++) {
                run.tick();
                held.add(run.world.held());
            }
            int start = 0;
            Map<CompanionWorld.Held, Integer> count = new LinkedHashMap<>();
            for (int i = 1; i <= TICKS; i++) {
                if (i < TICKS && held.get(i) == held.get(start)) continue;
                CompanionWorld.Held h = held.get(start);
                if (h != CompanionWorld.Held.NONE) {
                    int k = count.merge(h, 1, Integer::sum);
                    String label = h + "#" + k;
                    int latency = -1, engaged = 0, seen = 0;
                    for (int t = start; t < i; t++) {
                        CompanionRun.Sample s = run.samples.get(t);
                        if (engagedHeld(s)) {
                            if (latency < 0) latency = t - start;
                            engaged++;
                            if (s.seen()) seen++;
                        }
                    }
                    double[] a = acc.computeIfAbsent(label, x -> new double[6]);
                    a[0]++;
                    if (latency >= 0) {
                        a[1] += latency;
                        a[2] += engaged;
                        a[3] += seen;
                        a[5]++;
                    } else {
                        a[4]++;
                    }
                }
                start = i;
            }
        }
        acc.forEach((label, a) -> {
            double n = Math.max(1, a[5]);
            System.out.printf("  %-16s engaged in %d/%d  notice=%4.1fs  engaged=%4.1fs  seen while engaged=%3.0f%%%n",
                    label, (int) a[5], (int) a[0], a[1] / n / 20, a[2] / n / 20, a[2] > 0 ? 100 * a[3] / a[2] : 0);
        });

        for (String script : new String[]{"item_drops", "companion_passes", "hostile_approach", "cat_lounging"}) {
            double inspections = 0, inspectShare = 0, seen = 0;
            for (int seed = 1; seed <= SEEDS; seed++) {
                CompanionRun run = new CompanionRun(script, seed, c, CompanionTunables.defaults(), true);
                run.run(TICKS);
                inspections += run.agent.inspections() / (double) SEEDS;
                var m = run.metrics();
                inspectShare += m.goalShare().getOrDefault("INSPECT", 0.0) / SEEDS;
                seen += m.seenFraction() / SEEDS;
            }
            System.out.printf("== %-17s inspections/2min=%.1f inspect share=%.0f%% seen=%.0f%%%n",
                    script, inspections, inspectShare * 100, seen * 100);
        }
    }
}
