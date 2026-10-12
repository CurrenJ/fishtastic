package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Diagnostic: the ground shape envelope the renderer deforms by (push-off), per gait and size,
 * moving against standing. -PsimStdout.
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class BenthicShapeProbe {

    @Test
    void shape() {
        Object[][] cfgs = {{"crawl 0.12", GroundGait.CRAWL, 0.12f}, {"crawl 0.3", GroundGait.CRAWL, 0.3f},
                {"crawl 0.6", GroundGait.CRAWL, 0.6f}, {"ripple 0.35", GroundGait.RIPPLE, 0.35f}};
        for (Object[] c : cfgs) {
            for (String script : new String[] {"walk_meander", "walk_flat"}) {
                double sumMove = 0, maxMove = 0, sumIdle = 0;
                int nMove = 0, nIdle = 0, over = 0;
                for (long seed = 1; seed <= 3; seed++) {
                    CompanionRun run = CompanionRun.benthic(script, seed, (Float) c[2], Temperament.BASELINE,
                            (GroundGait) c[1], CompanionTunables.defaults(), true);
                    for (int i = 0; i < 2400; i++) {
                        run.tick();
                        if (!run.agent.onGround()) continue;
                        double s = run.agent.groundShape();
                        if (run.agent.velocity().horizontalLength() > 0.3) {
                            sumMove += s; nMove++; maxMove = Math.max(maxMove, s);
                            if (s > 0.3) over++;
                        } else { sumIdle += s; nIdle++; }
                    }
                }
                System.out.printf("SHAPE %-11s %-12s moving mean=%.3f max=%.2f >0.3:%4.1f%%  still mean=%.3f%n",
                        c[0], script, sumMove / Math.max(1, nMove), maxMove, 100.0 * over / Math.max(1, nMove),
                        sumIdle / Math.max(1, nIdle));
            }
        }
    }
}
