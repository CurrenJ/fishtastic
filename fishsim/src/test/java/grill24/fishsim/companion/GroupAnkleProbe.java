package grill24.fishsim.companion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;

/**
 * Diagnostic (spec §23): the formation edge-drop guard against the old motor, per seed, for lines
 * and crowds. -PsimStdout -PsweepSeeds=12 [-Ptrace=script].
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class GroupAnkleProbe {

    @Test
    void perSeed() {
        int seeds = Integer.parseInt(System.getProperty("sweepSeeds", "12"));
        String only = System.getProperty("trace");
        List<String> scripts = only != null ? List.of(only.split(",")) : List.of("stairs_up_down", "walk_flat",
                "walk_meander", "group_passers", "walk_backward", "climb_cycles");
        for (BenthicGroup.Formation f : BenthicGroup.Formation.values()) {
            float length = f == BenthicGroup.Formation.LINE ? 0.4f : 0.12f;
            for (String script : scripts) {
                long[] tot = new long[10];
                long maxDeep = 0;
                int better = 0, worse = 0;
                StringBuilder rows = new StringBuilder();
                for (long seed = 1; seed <= seeds; seed++) {
                    long[][] r = new long[2][];
                    for (int k = 0; k < 2; k++) {
                        GroundMind.formationDropGuard = k == 1;
                        GroupRun g = new GroupRun(script, seed, length, Temperament.BASELINE, f).run(null);
                        r[k] = new long[] {g.fades, g.overlapTicks, g.orderTicks, g.underfootTicks, g.deepTicks};
                    }
                    maxDeep = Math.max(maxDeep, r[1][4]);
                    for (int j = 0; j < 5; j++) { tot[j] += r[0][j]; tot[5 + j] += r[1][j]; }
                    if (r[1][0] < r[0][0]) better++;
                    if (r[1][0] > r[0][0]) worse++;
                    rows.append(String.format(" %d:%d>%d", seed, r[0][0], r[1][0]));
                }
                System.out.println("MAXDEEP " + f + " " + script + " " + maxDeep);
                System.out.printf("EDGE %-5s %-15s fades %3d->%3d (seeds better %d worse %d) overlap %5d->%5d order %5d->%5d underfoot %4d->%4d deep %4d->%4d |%s%n",
                        f, script, tot[0], tot[5], better, worse, tot[1], tot[6], tot[2], tot[7], tot[3], tot[8], tot[4], tot[9], rows);
            }
        }
        GroundMind.formationDropGuard = true;
    }
}
