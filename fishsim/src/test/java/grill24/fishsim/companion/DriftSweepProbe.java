package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import grill24.fishsim.harness.companion.OwnerScript;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.function.Consumer;

/**
 * Diagnostic: a drifter's standard and drift metrics (docs/fish-companions-drift.md §9), per seed and
 * per script (mean and worst over the seeds), for equal-grounds A/B of a drifter change. Run with
 * -PsimStdout.
 *
 * <pre>
 *   -PdriftStyle=bell|jet|bob      the style (default bell)
 *   -Psweep=walk,forest            just these scripts (default: every owner script)
 *   -PtweakA=field=value,...       tunables for side A (default: defaults)
 *   -PtweakB=field=value,...       and side B; omit for a single report
 *   -PsweepTemperament=ghost       the temperament (default baseline)
 *   -PsweepSeeds=8 -PsweepTicks=2400
 *   -PsweepPerSeed=1               a line per seed as well
 * </pre>
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class DriftSweepProbe {

    @Test
    void sweep() {
        DriftStyle style = DriftStyle.named(System.getProperty("driftStyle", "bell"));
        String only = System.getProperty("sweep");
        String[] scripts = only == null || only.equals("all") ? OwnerScript.NAMES.toArray(new String[0]) : only.split(",");
        String a = System.getProperty("tweakA"), b = System.getProperty("tweakB");
        report("DRIFT " + style + " A " + (a == null ? "defaults" : a), style, scripts, SeedSweepProbe.tweak(a));
        if (b != null) report("DRIFT " + style + " B " + b, style, scripts, SeedSweepProbe.tweak(b));
    }

    static int seeds() {
        return Integer.parseInt(System.getProperty("sweepSeeds", "8"));
    }

    static void report(String label, DriftStyle style, String[] scripts, Consumer<CompanionTunables> tweak) {
        boolean perSeed = System.getProperty("sweepPerSeed") != null;
        int seeds = seeds();
        System.out.println("== " + label + " (" + seeds + " seeds x " + SeedSweepProbe.ticks() + " ticks; mean | worst)");
        System.out.println("  script           far(s)      seen%      contacts  impact      pen  jerk        tailF%     "
                + "over        over/min    reor(s)     reor/min    clear       low%        rev/min");
        for (String script : scripts) {
            Acc far = new Acc(), seen = new Acc(), contacts = new Acc(), impact = new Acc(), pen = new Acc(), jerk = new Acc(),
                    tail = new Acc(), over = new Acc(), overRate = new Acc(), reor = new Acc(), reorRate = new Acc(),
                    clear = new Acc(), low = new Acc(), rev = new Acc();
            for (int seed = 1; seed <= seeds; seed++) {
                CompanionTunables t = CompanionTunables.defaults();
                tweak.accept(t);
                CompanionRun run = CompanionRun.drifter(script, seed, style, SeedSweepProbe.temperament(), t, true);
                run.run(SeedSweepProbe.ticks());
                var m = run.metrics();
                var d = run.driftMetrics();
                far.add(m.longestFarSeconds(), true);
                seen.add(m.seenFraction() * 100, false);
                contacts.add(m.backstops(), true);
                impact.add(m.maxImpact(), true);
                pen.add(m.solidPenetrations(), true);
                jerk.add(m.maxJerk(), true);
                tail.add(m.tailFirstFraction() * 100, true);
                over.add(d.meanOvershoot(), true);
                overRate.add(d.overshootsPerMinute(), true);
                reor.add(d.meanReorientSeconds(), true);
                reorRate.add(d.reorientsPerMinute(), true);
                if (!Double.isNaN(d.minClearance())) clear.add(d.minClearance(), false);
                low.add(d.lowClearanceFraction() * 100, true);
                rev.add(d.reversalsPerMinute(), true);
                if (perSeed) {
                    System.out.printf("    %-15s #%d %s | %s | %s | %s%n", script, seed, m.summary(), d.summary(),
                            run.driftBodyMetrics().summary(), run.ownerOverlap().summary());
                }
            }
            System.out.printf("  %-15s %s %s %s %s %s %s %s %s %s %s %s %s %s %s%n", script,
                    far.fmt(1), seen.fmt(0), contacts.fmt(0), impact.fmt(2), pen.fmt(0), jerk.fmt(0), tail.fmt(1),
                    over.fmt(2), overRate.fmt(1), reor.fmt(2), reorRate.fmt(1), clear.fmt(2), low.fmt(1), rev.fmt(1));
        }
    }

    /** Mean and worst of one metric over the seeds; worst is the max, or the min where low is bad. */
    private static final class Acc {
        double sum, worst = Double.NaN;
        int n;

        void add(double v, boolean highIsBad) {
            sum += v;
            n++;
            worst = Double.isNaN(worst) ? v : highIsBad ? Math.max(worst, v) : Math.min(worst, v);
        }

        String fmt(int places) {
            if (n == 0) return String.format("%-11s", "-");
            String f = "%." + places + "f|%." + places + "f";
            return String.format("%-11s", String.format(f, sum / n, worst));
        }
    }
}
