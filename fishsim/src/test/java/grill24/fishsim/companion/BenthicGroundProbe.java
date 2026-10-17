package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import grill24.fishsim.harness.companion.OwnerScript;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Diagnostic for benthics on the ground (docs/fish-companions-benthic-follow.md §7): the general
 * metrics and the ground metrics per script and seed. Run with -PsimStdout. Options:
 * {@code -PsweepClass=CRAWL|RIPPLE} (gait), {@code -PsweepLength=0.3} (body length, blocks),
 * {@code -PsweepSeeds=3}, {@code -Psweep=all} (also the swimmers' scripts), {@code -PsweepTemperament=name}.
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class BenthicGroundProbe {

    @Test
    void ground() {
        GroundGait gait = GroundGait.valueOf(System.getProperty("sweepClass", "CRAWL"));
        float length = Float.parseFloat(System.getProperty("sweepLength", "0.3"));
        int seeds = Integer.parseInt(System.getProperty("sweepSeeds", "3"));
        Temperament tm = Temperament.named(System.getProperty("sweepTemperament", Temperament.BASELINE.name()));
        List<String> scripts = new ArrayList<>(OwnerScript.GROUND_NAMES);
        if ("all".equals(System.getProperty("sweep"))) scripts.addAll(OwnerScript.NAMES);
        String trace = System.getProperty("trace");
        if (trace != null) {
            // The swimmers' table runs a benthic at its class length (0.3) with the crawl.
            CompanionRun run = CompanionRun.benthic(trace, Long.parseLong(System.getProperty("tracePos", "1")), length, tm, gait,
                    CompanionTunables.defaults(), true);
            int from = Integer.parseInt(System.getProperty("sweepTicks", "0"));
            Vec prevAcc = null;
            boolean prevImpulse = false;
            for (int i = 0; i < 2400; i++) {
                run.tick();
                CompanionAgent a = run.agent;
                boolean spikes = from < 0;
                Vec acc = a.acceleration();
                double jerk = prevAcc == null ? 0 : acc.sub(prevAcc).length() / 0.05;
                boolean skip = a.onOwner() || a.impulse() || prevImpulse || a.backstopLastStep();
                prevImpulse = a.impulse();
                prevAcc = acc;
                if (spikes ? skip || (acc.length() < 34 && jerk < 700) : i < from) continue;
                Vec p = a.position(), f = run.owner.feet();
                String line = String.format("T %4d %-13s %-14s %-5s pos %6.2f %6.2f %6.2f v %5.2f vy %5.2f acc %5.1f lag %5.2f %s own %6.2f %6.2f %6.2f yaw %6.1f%s%s%n",
                        i, a.goal(), a.perchState(), a.groundPhase(), p.x(), p.y(), p.z(), a.velocity().horizontalLength(),
                        a.velocity().y(), a.acceleration().length(), a.groundLag(), a.groundReachable() ? "R" : "-",
                        f.x(), f.y(), f.z(), a.yaw(), a.impulse() ? " IMP" : "", a.backstopLastStep() ? " BS" : "")
                        ;
                System.out.print(line);
                System.out.printf("   u=%d close=%s seen=%s target=%s%n", a.perchUnseenTicks(), a.comingClose(), a.seen(), a.target());
            }
            return;
        }
        long start = System.nanoTime();
        for (String script : scripts) {
            for (long seed = 1; seed <= seeds; seed++) {
                CompanionRun run = CompanionRun.benthic(script, seed, length, tm, gait, CompanionTunables.defaults(), true);
                run.run(2400);
                System.out.printf("GP %-16s #%d %s%n    %s%n", script, seed, run.groundMetrics().summary(),
                        run.metrics().summary());
            }
        }
        System.out.printf("GP elapsed %.1fs%n", (System.nanoTime() - start) / 1e9);
    }
}
