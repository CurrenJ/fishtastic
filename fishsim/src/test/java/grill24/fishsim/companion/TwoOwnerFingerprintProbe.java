package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.OwnerScript;
import grill24.fishsim.harness.companion.TwoOwnerRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Diagnostic: one hash per two-owner pair × script × seed over every tick's bodies of both parties
 * (docs/fish-companions-party.md §28.3). Recorded before the cross-player scene goals land; with no
 * scene requested it must stay bitwise. Diff the output before and after. Run with -PsimStdout.
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class TwoOwnerFingerprintProbe {

    static final int TICKS = 2400;

    @Test
    void fingerprints() {
        for (TwoOwnerRun.Pair pair : TwoOwnerRun.PAIRS) {
            for (String script : OwnerScript.PAIR_NAMES) {
                for (long seed = 1; seed <= 3; seed++) {
                    System.out.printf("FP2 %-26s %-20s #%d %016x%n", pair.name(), script, seed,
                            hash(pair, script, seed));
                }
            }
        }
    }

    static long hash(TwoOwnerRun.Pair pair, String script, long seed) {
        TwoOwnerRun.Sim sim = new TwoOwnerRun.Sim(pair, script, seed, CompanionTunables.defaults(), Temperament.BASELINE);
        long h = 1125899906842597L;
        for (int k = 0; k < TICKS; k++) {
            sim.step();
            for (TwoOwnerRun.Side s : new TwoOwnerRun.Side[]{sim.a, sim.b}) {
                for (double[] body : s.bodies()) {
                    for (double v : body) h = 31 * h + Double.doubleToLongBits(v);
                }
            }
        }
        return h;
    }
}
