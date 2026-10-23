package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.OwnerScript;
import grill24.fishsim.harness.companion.TwoOwnerRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.Arrays;

/**
 * Diagnostic: the two-owner guard rails before any cross-player scene (docs/fish-companions.md
 * §28.3, step 3a-0), per pair and script, means over 8 seeds. The scene steps (3a-2) are judged
 * against this table. Run with -PsimStdout.
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class TwoOwnerBaselineProbe {

    static final int TICKS = 2400, SEEDS = 8;

    @Test
    void baseline() {
        System.out.printf("TWO %-26s %-20s %7s %6s %6s %6s %7s %6s %-14s %-14s %6s %5s%n", "pair", "script",
                "xOver%", "/min", "near%", "minSep", "ownerC%", "/min", "seenA%", "seenB%", "insp%", "fades");
        for (TwoOwnerRun.Pair pair : TwoOwnerRun.PAIRS) {
            for (String script : OwnerScript.PAIR_NAMES) {
                double over = 0, overRate = 0, near = 0, minSep = Double.MAX_VALUE, contact = 0, contactRate = 0, insp = 0;
                double[] seenA = null, seenB = null;
                int fades = 0;
                for (long seed = 1; seed <= SEEDS; seed++) {
                    TwoOwnerRun.Result r = TwoOwnerRun.measure(pair, script, seed, TICKS, CompanionTunables.defaults(),
                            Temperament.BASELINE);
                    over += r.crossOverlapPct() / SEEDS;
                    overRate += r.crossOverlapPerMin() / SEEDS;
                    near += r.crossNearPct() / SEEDS;
                    if (r.crossMinSep() >= 0) minSep = Math.min(minSep, r.crossMinSep());
                    contact += r.ownerContactPct() / SEEDS;
                    contactRate += r.ownerContactPerMin() / SEEDS;
                    insp += r.inspectPct() / SEEDS;
                    fades += r.fades();
                    seenA = add(seenA, r.seenA());
                    seenB = add(seenB, r.seenB());
                }
                System.out.printf("TWO %-26s %-20s %7.2f %6.2f %6.2f %6.2f %7.2f %6.2f %-14s %-14s %6.2f %5d%n",
                        pair.name(), script, over, overRate, near, minSep, contact, contactRate,
                        pct(seenA), pct(seenB), insp, fades);
            }
        }
    }

    private static double[] add(double[] sum, double[] v) {
        if (sum == null) sum = new double[v.length];
        for (int i = 0; i < v.length; i++) sum[i] += v[i] / SEEDS;
        return sum;
    }

    private static String pct(double[] v) {
        return Arrays.stream(v).mapToObj(x -> String.format("%.1f", x)).reduce((a, b) -> a + "/" + b).orElse("-");
    }
}
