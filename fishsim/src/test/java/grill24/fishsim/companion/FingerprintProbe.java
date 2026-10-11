package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;

/**
 * Diagnostic: one hash per script × seed over every tick's position and heading, for proving a
 * refactor left a body's trajectories bitwise unchanged. Diff the output before and after.
 * Run with -PsimStdout.
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class FingerprintProbe {

    static final List<String> SCRIPTS = List.of(
            "idle_fixed", "idle_looking", "walk", "walk_turns", "sprint_stop", "camera_sweep",
            "corridor", "doorway", "forest", "teleport", "elytra");

    @Test
    void fingerprints() {
        for (String script : SCRIPTS) {
            for (long seed = 1; seed <= 3; seed++) {
                CompanionRun run = new CompanionRun(script, seed, CompanionTunables.defaults(), true);
                run.run(2400);
                long h = 1125899906842597L;
                for (CompanionRun.Sample s : run.samples) {
                    h = 31 * h + Double.doubleToLongBits(s.pos().x());
                    h = 31 * h + Double.doubleToLongBits(s.pos().y());
                    h = 31 * h + Double.doubleToLongBits(s.pos().z());
                    h = 31 * h + Float.floatToIntBits(s.yaw());
                    h = 31 * h + Float.floatToIntBits(s.pitch());
                }
                System.out.printf("FP %-13s #%d %016x%n", script, seed, h);
            }
        }
    }
}
