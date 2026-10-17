package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Diagnostic: every block contact in a run, with the ticks just before it — what the body was
 * doing when it hit. -PsimStdout -PsweepClass=BENTHIC -Ptrace=script:seed.
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class ImpactProbe {

    @Test
    void impacts() {
        String[] a = System.getProperty("trace", "stairwell:1").split(":");
        var c = grill24.fishsim.core.Locomotion.valueOf(System.getProperty("sweepClass", "FREE_SWIM"));
        CompanionRun run = new CompanionRun(a[0], Long.parseLong(a[1]), c, SeedSweepProbe.temperament(), CompanionTunables.defaults(), true);
        String[] recent = new String[4];
        for (int t = 0; t < 2400; t++) {
            run.tick();
            OwnerState o = run.owner;
            String line = String.format("t=%4d %-26s |v|=%5.2f vOwner=(%5.2f,%5.2f) fishRelEye=%s",
                    t, run.agent.perchState(), run.agent.velocity().length(), o.horizontalSpeed(), o.velocity().y(),
                    run.agent.position().sub(o.eye()));
            if (System.getProperty("tracePos") != null && !run.agent.onOwner()
                    && Mind.insideOwner(o, run.agent.position(), 0)) System.out.println("INSIDE " + line);
            if (run.agent.backstopLastStep()) {
                System.out.printf("HIT impact=%.2f%n", run.agent.lastImpact());
                for (String r : recent) if (r != null) System.out.println("   " + r);
                System.out.println(" > " + line);
            }
            System.arraycopy(recent, 1, recent, 0, recent.length - 1);
            recent[recent.length - 1] = line;
        }
    }
}
