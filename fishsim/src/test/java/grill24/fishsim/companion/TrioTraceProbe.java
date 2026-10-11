package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Diagnostic: a trio's spacing over a run. -PsimStdout -Ptrace=script:seed:from:to:every. */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class TrioTraceProbe {

    @Test
    void trace() {
        String[] a = System.getProperty("trace", "walk_turns:1:0:2400:20").split(":");
        CompanionRun run = CompanionRun.trio(a[0], Long.parseLong(a[1]), CompanionTunables.defaults(), true);
        int from = Integer.parseInt(a[2]), to = Integer.parseInt(a[3]), every = Integer.parseInt(a[4]);
        for (int t = 0; t < to; t++) {
            run.tick();
            var g = run.group;
            if (System.getProperty("tracePos") != null) {
                // Overlap ticks only: which pair, and how each was moving.
                Vec l = g.leader().position(), a0 = g.follower(0).position(), a1 = g.follower(1).position();
                double touch = 2 * g.follower(0).radius();
                String pair = l.distanceTo(a0) < touch ? "L-f0" : l.distanceTo(a1) < touch ? "L-f1" : a0.distanceTo(a1) < touch ? "f0-f1" : null;
                if (pair != null && t >= from) {
                    System.out.printf("t=%4d OVERLAP %-5s leader %-8s v=%s | f0 v=%s | f1 v=%s%n", t, pair, g.leader().goal(),
                            g.leader().velocity(), g.follower(0).velocity(), g.follower(1).velocity());
                }
                continue;
            }
            if (t < from || t % every != 0) continue;
            Vec l = g.leader().position();
            System.out.printf("t=%4d %-8s leader v=%5.2f | f0 d=%5.2f v=%5.2f app=%.2f | f1 d=%5.2f v=%5.2f app=%.2f | f0-f1=%.2f%n",
                    t, g.leader().goal(), g.leader().velocity().length(),
                    g.follower(0).position().distanceTo(l), g.follower(0).velocity().length(), g.follower(0).appear(),
                    g.follower(1).position().distanceTo(l), g.follower(1).velocity().length(), g.follower(1).appear(),
                    g.follower(0).position().distanceTo(g.follower(1).position()));
        }
    }
}
