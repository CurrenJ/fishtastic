package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Diagnostic: a tick-by-tick trace of one run in the owner's frame (ahead, right, up of the fish
 * relative to the eye), with goal and visibility. -PsimStdout -Ptrace=script:seed:from:to:every
 * [-PsweepClass=GLIDE -PtweakA=field=value,...].
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class TraceProbe {

    @Test
    void trace() {
        String[] a = System.getProperty("trace", "about_face:1:0:600:5").split(":");
        CompanionTunables tun = CompanionTunables.defaults();
        SeedSweepProbe.tweak(System.getProperty("tweakA")).accept(tun);
        var c = grill24.fishsim.core.Locomotion.valueOf(System.getProperty("sweepClass", "FREE_SWIM"));
        CompanionRun run = new CompanionRun(a[0], Long.parseLong(a[1]), c, SeedSweepProbe.temperament(), tun, true);
        int from = Integer.parseInt(a[2]), to = Integer.parseInt(a[3]), every = Integer.parseInt(a[4]);
        for (int t = 0; t < to; t++) {
            run.tick();
            if (t < from || t % every != 0) continue;
            OwnerState o = run.owner;
            Vec d = run.agent.position().sub(o.eye());
            Vec f = o.flatForward(), r = o.flatRight();
            System.out.printf("t=%4d ownerV=%5.2f yaw=%6.1f %s fish ahead=%5.2f right=%5.2f up=%5.2f  v=%4.2f %-8s %s ang=%3.0f%n",
                    t, o.horizontalSpeed(), o.yawDeg(), run.agent.backstopLastStep() ? "HIT" : "   ", d.dot(f), d.dot(r), d.y(), run.agent.velocity().length(),
                    run.agent.goal() + (run.agent.perchState() != null ? "/" + run.agent.perchState() : ""), run.agent.seen() ? "SEEN" : "    ", Visibility.angleFromCenterDeg(o, run.agent.position()));
            if (System.getProperty("tracePos") != null && run.agent.locomotion() == grill24.fishsim.core.Locomotion.DRIFT)
                System.out.printf("    drift |v|=%.2f vy=%.2f tilt=%.0f pulse=%.2f thrust=%.1f effort=%.2f%n", run.agent.velocity().length(), run.agent.velocity().y(), run.agent.pitch() + 90, run.agent.pulse(), run.agent.thrust(), (run.agent.speedFactor() - 0.6f) / 0.9f);
            if (System.getProperty("tracePos") != null) System.out.printf("    pos=%s yaw=%.1f pitch=%.1f target=%s%n", run.agent.position(), run.agent.yaw(), run.agent.pitch(), run.agent.target());
        }
    }
}
