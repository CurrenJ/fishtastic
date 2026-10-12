package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Diagnostic: every tick of a drifter in a window: squeeze, thrust, velocity, flare, tilt, position step. -PsimStdout -Ptrace -PsweepSeeds(seed) -PtracePos=from,to */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class DriftTickTraceProbe {
    @Test
    void ticks() {
        String script = System.getProperty("trace", "idle_fixed");
        long seed = Long.parseLong(System.getProperty("sweepSeeds", "1"));
        String[] w = System.getProperty("tracePos", "940,990").split(",");
        int from = Integer.parseInt(w[0]), to = Integer.parseInt(w[1]);
        CompanionTunables t = CompanionTunables.defaults();
        SeedSweepProbe.tweak(System.getProperty("tweakA")).accept(t);
        CompanionRun run = CompanionRun.drifter(script, seed, DriftStyle.BELL, SeedSweepProbe.temperament(), t, false);
        DriftBody b = (DriftBody) DriftStrokeVisibilityProbe.bodyOf(run.agent);
        Vec lastPos = b.pos;
        System.out.println("  tick idle pulse  thr  |v|    vx    vy    vz  |step|/DT flare tilt calm goal back");
        for (int k = 0; k <= to; k++) {
            run.tick();
            boolean onlyHits = Boolean.parseBoolean(System.getProperty("sweepPerSeed", "false"));
            if (k >= from && (!onlyHits || b.backstopLastStep)) System.out.printf("  %4d %s %5.2f %5.2f %4.2f %5.2f %5.2f %5.2f %5.2f %4.2f %4.0f %4.2f %s %s%s%n", k, b.idling ? "I" : "-", b.pulse, b.lastThrust,
                    b.vel.length(), b.vel.x(), b.vel.y(), b.vel.z(), b.pos.sub(lastPos).length() * 20, b.flare, b.tilt, b.calm, run.agent.goal(),
                    b.backstopLastStep ? "B" : "", (b.strokeStart ? " STROKE" : "") + String.format(" needUp %.0f° ask %.2f y %.2f", Math.toDegrees(Math.atan2(b.need.y(), b.need.horizontalLength())), b.asked.length(), b.pos.y()));
            lastPos = b.pos;
        }
    }
}
