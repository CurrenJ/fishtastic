package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.OwnerScript;
import grill24.fishsim.harness.companion.TwoOwnerRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.HashSet;
import java.util.Set;

/**
 * Diagnostic (docs/fish-companions.md, D29, step 3a-3): the same scenes as two clients
 * see them. Each client runs both parties; its own player is exact and the other player arrives a
 * few ticks late (an interpolated remote player). The server's schedule is the same in both. Per
 * pair and script, over 8 seeds:
 * <ul>
 *   <li>how far apart one fish is in the two views (mean, 95th percentile, max), while on its path;</li>
 *   <li>scenes played in one view only (one fish left early, or a different scene chosen);</li>
 *   <li>each view on its own: path error and G1's antipodal error (the gate: both self-consistent).</li>
 * </ul>
 * Run with -PsimStdout; {@code LAG} is the remote player's delay.
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class CrossSceneDivergenceProbe {

    static final int TICKS = 2400, SEEDS = 8;
    static final int[] LAGS = {2, 4};

    @Test
    void divergence() {
        System.out.printf("DIV %-26s %-20s %3s %6s %6s %6s %5s %7s %7s %7s %7s%n", "pair", "script", "lag",
                "mean", "p95", "max", "1view", "pathA", "pathB", "antiA", "antiB");
        for (int lag : LAGS) {
            for (TwoOwnerRun.Pair pair : TwoOwnerRun.PAIRS) {
                if (pair.name().equals("crab vs crab")) continue;
                for (String script : OwnerScript.PAIR_NAMES) {
                    if (script.equals("pair_corridor") || script.equals("pair_side_by_side") || script.equals("pair_close")) continue;
                    run(pair, script, lag);
                }
            }
        }
    }

    private static void run(TwoOwnerRun.Pair pair, String script, int lag) {
        java.util.List<Double> gaps = new java.util.ArrayList<>();
        int oneView = 0;
        double[] path = new double[2], anti = new double[2];
        long[] pathN = new long[2], antiN = new long[2];
        for (long seed = 1; seed <= SEEDS; seed++) {
            // The server's schedule, from the true positions.
            TwoOwnerRun.Sim ref = new TwoOwnerRun.Sim(pair, script, seed, CompanionTunables.defaults(), Temperament.BASELINE)
                    .autoScenes(true);
            Set<Integer> starts = new HashSet<>();
            for (int k = 0; k < TICKS; k++) {
                int before = ref.scenesStarted();
                ref.step();
                if (ref.scenesStarted() > before) starts.add(k);
            }
            TwoOwnerRun.Sim[] views = {
                    new TwoOwnerRun.Sim(pair, script, seed, CompanionTunables.defaults(), Temperament.BASELINE).lag(0, lag).sceneAt(starts),
                    new TwoOwnerRun.Sim(pair, script, seed, CompanionTunables.defaults(), Temperament.BASELINE).lag(lag, 0).sceneAt(starts)};
            CrossScene[] lastScene = new CrossScene[2];
            boolean[][] left = new boolean[2][2];
            for (int k = 0; k < TICKS; k++) {
                for (TwoOwnerRun.Sim v : views) v.step();
                for (int vi = 0; vi < 2; vi++) {
                    CrossScene s = views[vi].a.scene();
                    if (s != lastScene[vi]) {
                        if (lastScene[vi] != null && left[vi][0] != left[1 - vi][0] | left[vi][1] != left[1 - vi][1]) oneView++;
                        lastScene[vi] = s;
                        left[vi][0] = left[vi][1] = false;
                    }
                }
                if (lastScene[0] == null || lastScene[1] == null) continue;
                if ((lastScene[0].kind != lastScene[1].kind)) continue;
                for (int side = 0; side < 2; side++) {
                    CompanionAgent[] same = new CompanionAgent[2];
                    for (int vi = 0; vi < 2; vi++) {
                        TwoOwnerRun.Side sd = side == 0 ? views[vi].a : views[vi].b;
                        if (sd.sceneUnit() == null) continue;
                        CompanionAgent l = sd.sceneUnit().leader();
                        if (l.sceneLeft() != null) left[vi][side] = true;
                        if (l.inScene() && !sd.scene().greeting()) same[vi] = l;
                        // Self-consistency in this view: the fish against its own clock point.
                        if (l.inScene() && !sd.scene().greeting() && sd.scene().ticks() > CrossScene.GREET_TICKS + 40) {
                            path[vi] += l.position().distanceTo(sd.scene().pathPoint(sd.scene().ticks()));
                            pathN[vi]++;
                        }
                    }
                    if (same[0] != null && same[1] != null) gaps.add(same[0].position().distanceTo(same[1].position()));
                }
                for (int vi = 0; vi < 2; vi++) {
                    TwoOwnerRun.Sim v = views[vi];
                    CrossScene s = v.a.scene();
                    if (s.kind != CrossScene.Kind.RING || !s.active() || s.greeting() || s.ticks() < CrossScene.GREET_TICKS + 40
                            || v.a.sceneUnit() == null || v.b.sceneUnit() == null) continue;
                    CompanionAgent a = v.a.sceneUnit().leader(), b = v.b.sceneUnit().leader();
                    if (!a.inScene() || !b.inScene()) continue;
                    Vec c = s.ringCentre();
                    Vec da = a.position().sub(c).horizontal(), db = b.position().sub(c).horizontal();
                    double cos = da.dot(db) / Math.max(1e-9, da.length() * db.length());
                    anti[vi] += 180 - Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos))));
                    antiN[vi]++;
                }
            }
        }
        gaps.sort(Double::compare);
        double mean = gaps.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        double p95 = gaps.isEmpty() ? Double.NaN : gaps.get((int) (gaps.size() * 0.95));
        double max = gaps.isEmpty() ? Double.NaN : gaps.get(gaps.size() - 1);
        System.out.printf("DIV %-26s %-20s %3d %6.2f %6.2f %6.2f %5d %7s %7s %7s %7s%n", pair.name(), script, lag,
                mean, p95, max, oneView, f(path[0], pathN[0]), f(path[1], pathN[1]), f(anti[0], antiN[0]), f(anti[1], antiN[1]));
    }

    private static String f(double sum, long n) {
        return n == 0 ? "-" : String.format("%.2f", sum / n);
    }
}
