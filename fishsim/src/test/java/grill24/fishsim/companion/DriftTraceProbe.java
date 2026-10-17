package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Diagnostic: one drifter run, a line every few ticks — goal, where it rides in the owner's frame
 * (along their travel, to their right, above the chest), its speed against the ask, the bell's tilt,
 * thrust, flare and whether it's seen (docs/fish-companions-drift.md §13). Run with -PsimStdout.
 *
 * <pre>
 *   -Ptrace=walk  -PdriftStyle=bell  -PsweepSeeds=1 (the seed)  -PsweepTicks=600
 *   -PtweakA=field=value,...   -PtraceEvery=5
 * </pre>
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class DriftTraceProbe {

    @Test
    void trace() {
        String script = System.getProperty("trace", "walk");
        DriftStyle style = DriftStyle.named(System.getProperty("driftStyle", "bell"));
        long seed = Long.parseLong(System.getProperty("sweepSeeds", "1"));
        int ticks = Integer.parseInt(System.getProperty("sweepTicks", "600"));
        int every = Integer.parseInt(System.getProperty("traceEvery", "5"));
        CompanionTunables t = CompanionTunables.defaults();
        SeedSweepProbe.tweak(System.getProperty("tweakA")).accept(t);
        CompanionRun run = CompanionRun.drifter(script, seed, style, SeedSweepProbe.temperament(), t, true);
        run.run(ticks);
        System.out.println("== DRIFT trace " + style + " " + script + " seed " + seed);
        System.out.println("  tick goal       dist  along  right    up |  spd  ask  own | askF  askR  velF  velR | tilt  thr  flare seen bs impact in");
        for (int k = 0; k < run.samples.size(); k += every) {
            var s = run.samples.get(k);
            var o = s.owner();
            Vec f = o.velocity().horizontalLength() > 0.5 ? o.velocity().horizontal().normalize() : o.flatForward();
            Vec r = f.cross(Vec.UP);
            Vec rel = s.pos().sub(o.chest());
            System.out.printf("  %4d %-10s %5.2f %6.2f %6.2f %5.2f | %4.1f %4.1f %4.1f | %5.1f %5.1f %5.1f %5.1f | %4.0f %4.1f %5.2f %s %s %.2f %s%n",
                    k, s.goal(), s.ownerDist(), rel.dot(f), rel.dot(r), rel.y(),
                    s.vel().length(), s.asked().length(), o.velocity().length(),
                    s.asked().dot(f), s.asked().dot(r), s.vel().dot(f), s.vel().dot(r),
                    s.tilt(), s.thrust(), s.flare(), s.seen() ? "S" : ".", s.backstop() ? "B" : "", s.impact(),
                    CompanionRun.insideBody(o, s.pos()) ? "IN" : (s.pos().distanceTo(o.camera()) < 0.3 + run.agent.radius() ? "CLIP" : ""));
        }
    }
}
