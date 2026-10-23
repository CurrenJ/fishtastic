package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.function.Consumer;

/**
 * Diagnostic: equal-ground, per-seed A/B for the calm idle (docs/fish-companions-drift.md).
 * Side A is {@code -PtweakA}; each entry of {@code -PabList=over1/over2/...} is side B = A plus that
 * override. For every idle script it compares the idle numbers of {@link DriftIdleProbe}, and for
 * every travel script the standard ones, seed by seed: "+b/-w" is seeds better / worse beyond a
 * noise floor, then the means A → B. {@code -PabTravel=jumping,...} runs just those travel scripts.
 * Run with -PsimStdout.
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class DriftCalmABProbe {

    static final String[] TRAVEL = {"walk", "walk_turns", "sprint_stop", "about_face", "doorway", "forest", "stairwell"};

    /** name, lower-is-better, noise floor. */
    private record M(String name, boolean lowBetter, double floor) {}

    private static final M[] IDLE_M = {
            new M("bSpeed", true, 0.01), new M("bP90", true, 0.02), new M("bStrokes/min", true, 0.5),
            new M("bTilt°/min", true, 20), new M("moves/min", true, 0.2), new M("path/min", true, 0.5),
            new M("seen%", false, 1), new M("inOwner", true, 2)};
    private static final M[] TRAVEL_M = {
            new M("far s", true, 0.25), new M("unseen s", true, 0.5), new M("seen%", false, 1),
            new M("impact", true, 0.1), new M("contacts", true, 1), new M("inOwner", true, 2)};

    @Test
    void ab() {
        String a = System.getProperty("tweakA");
        String list = System.getProperty("abList", "");
        for (String over : list.split("/")) {
            String b = a == null || a.isBlank() ? over : a + "," + over;
            System.out.println("== A " + (a == null ? "defaults" : a));
            System.out.println("   B = A + " + over + "   [" + SeedSweepProbe.temperament().name() + ", "
                    + DriftSweepProbe.seeds() + " seeds x " + SeedSweepProbe.ticks() + " ticks]");
            String travel = System.getProperty("abTravel");
            if (travel == null) compare(DriftIdleProbe.IDLE, true, SeedSweepProbe.tweak(a), SeedSweepProbe.tweak(b));
            compare(travel == null ? TRAVEL : travel.split(","), false, SeedSweepProbe.tweak(a), SeedSweepProbe.tweak(b));
        }
    }

    private static void compare(String[] scripts, boolean idle, Consumer<CompanionTunables> ta, Consumer<CompanionTunables> tb) {
        M[] ms = idle ? IDLE_M : TRAVEL_M;
        int seeds = DriftSweepProbe.seeds(), ticks = SeedSweepProbe.ticks();
        int[] better = new int[ms.length], worse = new int[ms.length];
        double[] sumA = new double[ms.length], sumB = new double[ms.length];
        int n = 0;
        for (String script : scripts) {
            for (int seed = 1; seed <= seeds; seed++) {
                double[] va = measure(script, seed, idle, ta, ticks), vb = measure(script, seed, idle, tb, ticks);
                for (int k = 0; k < ms.length; k++) {
                    double d = vb[k] - va[k];
                    if (!ms[k].lowBetter) d = -d;
                    if (d < -ms[k].floor) better[k]++;
                    else if (d > ms[k].floor) worse[k]++;
                    sumA[k] += va[k];
                    sumB[k] += vb[k];
                }
                n++;
            }
        }
        StringBuilder sb = new StringBuilder(idle ? "   idle   " : "   travel ");
        for (int k = 0; k < ms.length; k++) {
            sb.append(String.format(" %s +%d/-%d (%.2f→%.2f)", ms[k].name, better[k], worse[k], sumA[k] / n, sumB[k] / n));
        }
        System.out.println(sb);
    }

    private static double[] measure(String script, int seed, boolean idle, Consumer<CompanionTunables> tweak, int ticks) {
        CompanionTunables t = CompanionTunables.defaults();
        tweak.accept(t);
        CompanionRun run = CompanionRun.drifter(script, seed, DriftStyle.BELL, SeedSweepProbe.temperament(), t, true);
        if (idle) {
            DriftIdleProbe.Idle m = DriftIdleProbe.measure(run, ticks);
            return new double[]{m.baseSpeed(), m.baseP90(), m.baseStrokesPerMin(), m.baseTiltSwing(), m.movesPerMin(),
                    m.pathPerMin(), m.seenPct(), m.inOwner()};
        }
        run.run(ticks);
        var m = run.metrics();
        return new double[]{m.longestFarSeconds(), m.longestUnseenSeconds(), m.seenFraction() * 100, m.maxImpact(),
                m.backstops(), m.insideOwnerTicks()};
    }
}
