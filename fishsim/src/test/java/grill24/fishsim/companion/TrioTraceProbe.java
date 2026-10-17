package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Diagnostic: a trio's spacing over a run. -PsimStdout -Ptrace=script:seed:from:to:every;
 * -PtracePos=1 prints overlap ticks only, -PtracePos=2 adds where each fish is relative to the
 * owner's chest (x, z) and how far each follower's straight line to the leader passes from the
 * owner's body axis.
 */
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
            if ("1".equals(System.getProperty("tracePos"))) {
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
            if ("2".equals(System.getProperty("tracePos"))) {
                Vec c = run.owner.chest();
                System.out.printf("    rel chest: L(%5.2f,%5.2f) f0(%5.2f,%5.2f) f1(%5.2f,%5.2f)  line-to-owner: f0 %.2f f1 %.2f%n",
                        l.x() - c.x(), l.z() - c.z(),
                        g.follower(0).position().x() - c.x(), g.follower(0).position().z() - c.z(),
                        g.follower(1).position().x() - c.x(), g.follower(1).position().z() - c.z(),
                        lineGap(g.follower(0).position(), l, c), lineGap(g.follower(1).position(), l, c));
            }
        }
    }

    /** Horizontal distance from the owner's axis at {@code c} to the segment {@code a}–{@code b}. */
    private static double lineGap(Vec a, Vec b, Vec c) {
        Vec ab = b.sub(a).horizontal(), ac = c.sub(a).horizontal();
        double s = ab.lengthSqr() < 1e-9 ? 0 : Math.max(0, Math.min(1, ac.dot(ab) / ab.lengthSqr()));
        return a.horizontal().add(ab.scale(s)).sub(c.horizontal()).length();
    }
}