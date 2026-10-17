package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;
import grill24.fishsim.harness.companion.CompanionRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Diagnostic (plan §10.5): does each temperament's own behaviour happen, against the baseline on the
 * same 8 seeds? One line per temperament, measured on the script that brings it out. Run with
 * -PsimStdout [-PsweepClass=BENTHIC].
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class TemperamentFeatureProbe {

    static final int SEEDS = 8;

    private interface Measure {
        double of(CompanionRun run);
    }

    private static double mean(String script, Temperament tm, Locomotion c, int ticks, Measure m) {
        double sum = 0;
        for (int seed = 1; seed <= SEEDS; seed++) {
            CompanionRun run = new CompanionRun(script, seed, c, tm, CompanionTunables.defaults(), true);
            run.run(ticks);
            sum += m.of(run);
        }
        return sum / SEEDS;
    }

    private static double poseShare(CompanionRun run, String pose) {
        return run.samples.stream().filter(s -> s.pose().equals(pose)).count() / (double) run.samples.size();
    }

    private static double goalShare(CompanionRun run, String goal) {
        return run.metrics().goalShare().getOrDefault(goal, 0.0);
    }

    private static void line(String what, String script, Locomotion c, int ticks, Temperament tm, Measure m, String fmt) {
        double base = mean(script, Temperament.BASELINE, c, ticks, m), temp = mean(script, tm, c, ticks, m);
        System.out.printf("   %-11s %-17s %-44s baseline " + fmt + "  ->  " + fmt + "%n", tm.name(), script, what, base, temp);
    }

    @Test
    void features() {
        Locomotion c = Locomotion.valueOf(System.getProperty("sweepClass", "FREE_SWIM"));
        System.out.println("== " + c + " (mean of " + SEEDS + " seeds)");
        line("nuzzles per 10 min", "idle_fixed", c, 12000, Temperament.DOCILE, r -> r.agent.nuzzles(), "%.1f");
        line("time nuzzling", "idle_fixed", c, 12000, Temperament.DOCILE, r -> poseShare(r, "NUZZLE") * 100, "%.1f%%");
        line("sprinting ticks ridden >1 block ahead", "sprint_stop", c, 2400, Temperament.SPRINTER, r -> {
            int sprint = 0, ahead = 0;
            for (CompanionRun.Sample s : r.samples) {
                if (!s.owner().sprinting()) continue;
                sprint++;
                Vec dir = s.owner().velocity().horizontal().normalize();
                if (s.pos().sub(s.owner().chest()).dot(dir) > 1.0) ahead++;
            }
            return sprint == 0 ? 0 : 100.0 * ahead / sprint;
        }, "%.0f%%");
        line("watching (stalk + hold) share", "item_drops", c, 2400, Temperament.AMBUSHER, r -> poseShare(r, "WATCH") * 100, "%.1f%%");
        line("top speed while inspecting", "item_drops", c, 2400, Temperament.AMBUSHER, r -> {
            double top = 0;
            for (CompanionRun.Sample s : r.samples) if (s.goal().equals("INSPECT")) top = Math.max(top, s.vel().length());
            return top;
        }, "%.2f b/s");
        line("guarding share", "hostile_approach", c, 2400, Temperament.AGGRESSIVE, r -> goalShare(r, "GUARD") * 100, "%.0f%%");
        line("bites (ticks)", "hostile_approach", c, 2400, Temperament.AGGRESSIVE, r -> poseShare(r, "BITE") * r.samples.size(), "%.0f");
        line("guarding: between owner and threat", "hostile_approach", c, 2400, Temperament.AGGRESSIVE, r -> {
            int n = 0, between = 0;
            for (CompanionRun.Sample s : r.samples) {
                if (!s.goal().equals("GUARD") && !s.pose().equals("PUFF")) continue;
                n++;
                if (s.pos().distanceTo(s.owner().chest()) < 2.5) between++;
            }
            return n == 0 ? 0 : 100.0 * between / n;
        }, "%.0f%%");
        line("hiding share", "hostile_approach", c, 2400, Temperament.SKITTISH, r -> (goalShare(r, "HIDE") + poseShare(r, "HIDE")) * 100, "%.0f%%");
        line("peeking share", "hostile_approach", c, 2400, Temperament.SKITTISH, r -> poseShare(r, "PEEK") * 100, "%.1f%%");
        line("mean speed", "idle_fixed", c, 2400, Temperament.GHOST, r -> r.metrics().meanSpeed(), "%.2f b/s");
        line("time settled", "idle_fixed", c, 2400, Temperament.GHOST, r -> goalShare(r, "SETTLE") * 100, "%.0f%%");
        line("goal switches per minute", "walk_turns", c, 2400, Temperament.ERRATIC, r -> r.metrics().goalSwitchesPerMinute(), "%.1f");
        line("trembling share", "idle_fixed", c, 2400, Temperament.TREMBLER, r -> poseShare(r, "TREMBLE") * 100, "%.0f%%");
        line("spread of linger lengths (CV)", "idle_looking", c, 2400, Temperament.METRONOME, r -> {
            List<Integer> lengths = new ArrayList<>();
            int run = 0;
            String prev = "";
            for (CompanionRun.Sample s : r.samples) {
                boolean lingering = s.vel().length() < 0.35 && (s.goal().equals("LOITER") || s.goal().equals("SETTLE"));
                if (lingering) run++;
                else if (run > 10) {
                    lengths.add(run);
                    run = 0;
                } else run = 0;
            }
            if (lengths.size() < 2) return 0;
            double m = lengths.stream().mapToInt(i -> i).average().orElse(0);
            double v = lengths.stream().mapToDouble(i -> (i - m) * (i - m)).average().orElse(0);
            return Math.sqrt(v) / m;
        }, "%.2f");
    }
}
