package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Diagnostic: a tick-by-tick trace of one run in the owner's frame (ahead, right, up of the fish
 * relative to the eye), with goal and visibility. -PsimStdout -Dtrace=script:seed:from:to:every.
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class TraceProbe {

    @Test
    void trace() {
        String[] a = System.getProperty("trace", "about_face:1:0:600:5").split(":");
        CompanionRun run = new CompanionRun(a[0], Long.parseLong(a[1]), CompanionTunables.defaults(), true);
        int from = Integer.parseInt(a[2]), to = Integer.parseInt(a[3]), every = Integer.parseInt(a[4]);
        for (int t = 0; t < to; t++) {
            run.tick();
            if (t < from || t % every != 0) continue;
            OwnerState o = run.owner;
            Vec d = run.agent.position().sub(o.eye());
            Vec f = o.flatForward(), r = o.flatRight();
            System.out.printf("t=%4d ownerV=%5.2f yaw=%6.1f  fish ahead=%5.2f right=%5.2f up=%5.2f  v=%4.2f %-8s %s ang=%3.0f%n",
                    t, o.horizontalSpeed(), o.yawDeg(), d.dot(f), d.dot(r), d.y(), run.agent.velocity().length(),
                    run.agent.goal(), run.agent.seen() ? "SEEN" : "    ", Visibility.angleFromCenterDeg(o, run.agent.position()));
        }
    }
}
