package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import grill24.fishsim.harness.companion.OwnerScript;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Diagnostic: how fast a drifter's drawn yaw turns. A bell is round, but its art is not, so a yaw
 * that jumps reads as the creature snapping round on the spot. Counts ticks whose yaw step exceeds
 * 20° and 90°, the largest step, and the tilt at the time. Run with -PsimStdout.
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class DriftYawSnapProbe {

    @Test
    void yawSnaps() {
        DriftStyle style = DriftStyle.named(System.getProperty("driftStyle", "bell"));
        int seeds = Integer.parseInt(System.getProperty("sweepSeeds", "4"));
        int ticks = Integer.parseInt(System.getProperty("sweepTicks", "1200"));
        System.out.println("== DRIFT yaw snaps " + style + "  (lean = old drawn yaw, face = drawn facing)");
        System.out.println("  script             lean>20 lean>90 leanMax | face>20 faceMax faceMeanDeg/s faceP99");
        long allLean = 0, allFace = 0;
        for (String script : OwnerScript.NAMES) {
            long leanOver = 0, leanOver90 = 0, faceOver = 0, n = 0;
            float leanMax = 0, faceMax = 0;
            double faceSum = 0;
            java.util.List<Float> steps = new java.util.ArrayList<>();
            for (long seed = 1; seed <= seeds; seed++) {
                CompanionTunables t = CompanionTunables.defaults();
                SeedSweepProbe.tweak(System.getProperty("tweakA")).accept(t);
                CompanionRun run = CompanionRun.drifter(script, seed, style, SeedSweepProbe.temperament(), t, true);
                float lastYaw = run.agent.yaw(), lastFace = run.agent.face();
                CompanionAgent.Fade lastFade = run.agent.fade();
                for (int k = 0; k < ticks; k++) {
                    run.tick();
                    float yaw = run.agent.yaw(), face = run.agent.face();
                    if (run.agent.fade() == lastFade && run.agent.appear() >= 1f) {
                        float dl = Math.abs(Body.wrap(yaw - lastYaw)), df = Math.abs(Body.wrap(face - lastFace));
                        n++;
                        if (dl > 20f) leanOver++;
                        if (dl > 90f) leanOver90++;
                        if (df > 20f) faceOver++;
                        leanMax = Math.max(leanMax, dl);
                        faceMax = Math.max(faceMax, df);
                        faceSum += df;
                        steps.add(df);
                    }
                    lastYaw = yaw;
                    lastFace = face;
                    lastFade = run.agent.fade();
                }
            }
            allLean += leanOver;
            allFace += faceOver;
            steps.sort(null);
            float p99 = steps.isEmpty() ? 0 : steps.get((int) (steps.size() * 0.99));
            System.out.printf("  %-18s %7d %7d %7.0f | %7d %7.1f %13.1f %7.2f%n", script, leanOver, leanOver90, leanMax,
                    faceOver, faceMax, n > 0 ? faceSum / n * 20 : 0, p99);
        }
        System.out.println("  TOTAL lean>20/t " + allLean + "  face>20/t " + allFace);
    }
}
