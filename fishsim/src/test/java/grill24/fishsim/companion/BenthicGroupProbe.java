package grill24.fishsim.companion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;

/**
 * Diagnostic for benthic formations (docs/fish-companions.md, §20): line order,
 * overlap, line wave and spacing, crowd spread, underfoot ticks, per script and seed. Run with
 * -PsimStdout; {@code -PsweepSeeds=3}, {@code -Psweep=script,script} to choose scripts.
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class BenthicGroupProbe {

    static final List<String> SCRIPTS = List.of("walk_flat", "walk_meander", "stairs_up_down",
            "long_idle_then_walk", "sprint_away", "group_passers", "walk_backward", "climb_cycles",
            "cat_lounging", "hostile_approach");

    @Test
    void formations() {
        int seeds = Integer.parseInt(System.getProperty("sweepSeeds", "3"));
        String only = System.getProperty("sweep");
        for (BenthicGroup.Formation formation : BenthicGroup.Formation.values()) {
            float length = formation == BenthicGroup.Formation.LINE ? 0.4f : 0.12f;
            for (String script : SCRIPTS) {
                if (only != null && !List.of(only.split(",")).contains(script)) continue;
                for (long seed = 1; seed <= seeds; seed++) {
                    GroupRun r = new GroupRun(script, seed, length, Temperament.BASELINE, formation).run(null);
                    System.out.printf("GROUP %-5s %-20s #%d %s%n", formation, script, seed, r.summary());
                }
            }
        }
    }
}
