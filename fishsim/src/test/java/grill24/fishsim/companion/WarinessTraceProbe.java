package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Diagnostic: the ticks a hiding fish sits nearer its threat than its owner, with the owner's and
 * threat's motion. -PsimStdout -Ptrace=script:seed.
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class WarinessTraceProbe {

    @Test
    void trace() {
        String[] a = System.getProperty("trace", "hostile_approach:1").split(":");
        CompanionRun run = new CompanionRun(a[0], Long.parseLong(a[1]), CompanionTunables.defaults(), true);
        int cover = 0;
        for (int t = 0; t < 2400; t++) {
            run.tick();
            CompanionAgent ag = run.agent;
            boolean hiding = ag.goal().equals("HIDE");
            cover = hiding ? cover + 1 : 0;
            if (cover <= 30 || ag.threat() == null) continue;
            Vec chest = run.owner.chest();
            double df = ag.position().distanceTo(ag.threat()), dc = chest.distanceTo(ag.threat());
            if (df >= dc) continue;
            Vec rel = ag.position().sub(chest);
            System.out.printf("t=%4d cover=%3d ownerV=%4.2f yaw=%6.1f threat=%s fishRel=(%5.2f,%5.2f,%5.2f) dFish=%.2f dOwner=%.2f target=%s v=%.2f%n",
                    t, cover, run.owner.horizontalSpeed(), run.owner.yawDeg(), ag.threat(), rel.x(), rel.y(), rel.z(), df, dc,
                    ag.target(), ag.velocity().length());
        }
    }
}
