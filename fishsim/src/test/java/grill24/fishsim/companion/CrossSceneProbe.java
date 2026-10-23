package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.OwnerScript;
import grill24.fishsim.harness.companion.TwoOwnerRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.Map;
import java.util.TreeMap;

/**
 * Diagnostic: cross-player scenes (docs/fish-companions.md, step 3a-2) on every pair and
 * two-owner script, scheduled as the server does, 8 seeds × 2 min. Run with -PsimStdout; -PtweakA
 * applies a tunable change (SeedSweepProbe syntax) for A/B.
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class CrossSceneProbe {

    static final int TICKS = 2400, SEEDS = 8;

    @Test
    void scenes() {
        System.out.printf("SCN %-26s %-20s %4s %6s %6s %6s %6s %6s %6s %6s %6s %6s %5s %5s  %s%n", "pair", "script", "n",
                "secs", "join s", "join%", "pathE", "antiD", "xC%", "/min", "ownC%", "seen%", "turn", "cap%", "ends | left");
        for (TwoOwnerRun.Pair pair : TwoOwnerRun.PAIRS) {
            for (String script : OwnerScript.PAIR_NAMES) {
                int n = 0, joinN = 0, pathN = 0, antiN = 0;
                double secs = 0, join = 0, joined = 0, path = 0, anti = 0, xc = 0, rate = 0, own = 0, seen = 0, turn = 0, cap = 0;
                Map<String, Integer> ends = new TreeMap<>(), left = new TreeMap<>();
                for (long seed = 1; seed <= SEEDS; seed++) {
                    CompanionTunables t = CompanionTunables.defaults();
                    SeedSweepProbe.tweak(System.getProperty("tweakA")).accept(t);
                    TwoOwnerRun.SceneResult r = TwoOwnerRun.measureScenes(pair, script, seed, TICKS, t, Temperament.BASELINE);
                    n += r.scenes();
                    secs += r.sceneTicks() / 20.0 / SEEDS;
                    if (!Double.isNaN(r.joinSeconds())) { join += r.joinSeconds(); joinN++; }
                    joined += r.joinedPct() / SEEDS;
                    if (!Double.isNaN(r.pathError())) { path += r.pathError(); pathN++; }
                    if (!Double.isNaN(r.antipodalDeg())) { anti += r.antipodalDeg(); antiN++; }
                    xc += r.contactPct() / SEEDS;
                    rate += r.contactPerMin() / SEEDS;
                    own += r.ownerContactPct() / SEEDS;
                    seen += r.seenPct() / SEEDS;
                    turn = Math.max(turn, r.peakTurnDeg());
                    cap += r.atTurnCapPct() / SEEDS;
                    r.ends().forEach((k, v) -> ends.merge(k, v, Integer::sum));
                    r.left().forEach((k, v) -> left.merge(k, v, Integer::sum));
                }
                System.out.printf("SCN %-26s %-20s %4d %6.1f %6s %6.0f %6s %6s %6.2f %6.2f %6.2f %6.1f %5.1f %5.2f  %s | %s%n",
                        pair.name(), script, n, secs, fmt(join, joinN), joined, fmt(path, pathN), fmt(anti, antiN),
                        xc, rate, own, seen, turn, cap, ends, left);
            }
        }
    }

    private static String fmt(double sum, int n) {
        return n == 0 ? "-" : String.format("%.2f", sum / n);
    }
}
