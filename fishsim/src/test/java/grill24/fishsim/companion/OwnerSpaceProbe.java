package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;
import grill24.fishsim.harness.companion.CompanionRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.Map;
import java.util.TreeMap;

/**
 * Diagnostic: which goals the near-clip and screen-centre ticks of one run happen in, and the
 * longest centre stretch with its goals — for finding what put a fish in its owner's face.
 * -PsimStdout -Ptrace=script:seed [-PsweepClass=GLIDE].
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class OwnerSpaceProbe {

    @Test
    void ownerSpace() {
        String[] a = System.getProperty("trace", "idle_fixed:1").split(":");
        Locomotion c = Locomotion.valueOf(System.getProperty("sweepClass", "FREE_SWIM"));
        CompanionRun run = new CompanionRun(a[0], Long.parseLong(a[1]), c, SeedSweepProbe.temperament(),
                CompanionTunables.defaults(), true);
        run.run(2400);
        Map<String, Integer> clip = new TreeMap<>(), centre = new TreeMap<>();
        int runLen = 0, best = 0, bestEnd = -1;
        StringBuilder goals = new StringBuilder();
        for (int i = 0; i < run.samples.size(); i++) {
            CompanionRun.Sample s = run.samples.get(i);
            double r = run.agent.radius();
            if (s.pos().distanceTo(s.owner().camera()) < Visibility.NEAR + r && !s.riding()) clip.merge(s.goal(), 1, Integer::sum);
            boolean centred = s.pos().distanceTo(s.owner().camera()) < 2.5 && Visibility.angleFromCenterDeg(s.owner(), s.pos()) < 8;
            if (centred) centre.merge(s.goal(), 1, Integer::sum);
            runLen = centred ? runLen + 1 : 0;
            if (runLen > best) {
                best = runLen;
                bestEnd = i;
            }
        }
        if (bestEnd >= 0) {
            for (int i = bestEnd - best + 1; i <= bestEnd; i += 5) {
                CompanionRun.Sample s = run.samples.get(i);
                goals.append(String.format("%n    t=%d %s d=%.2f ang=%.1f v=%.2f ownerV=%.2f", i, s.goal(),
                        s.pos().distanceTo(s.owner().camera()), Visibility.angleFromCenterDeg(s.owner(), s.pos()),
                        s.vel().length(), s.owner().horizontalSpeed()));
            }
        }
        int worstAcc = -1;
        double maxAcc = 0;
        for (int i = 2; i < run.samples.size(); i++) {
            CompanionRun.Sample s = run.samples.get(i), p = run.samples.get(i - 1);
            if (s.backstop() || p.backstop() || s.fade() != CompanionAgent.Fade.NONE || s.riding()) continue;
            if (s.acc().length() > maxAcc) {
                maxAcc = s.acc().length();
                worstAcc = i;
            }
        }
        if (worstAcc >= 0) {
            for (int i = Math.max(0, worstAcc - 6); i <= Math.min(run.samples.size() - 1, worstAcc + 2); i++) {
                CompanionRun.Sample s = run.samples.get(i);
                System.out.printf("  acc t=%d %s pose=%s |acc|=%.2f v=%.2f yaw=%.1f turn=%.2f%n", i, s.goal(), s.pose(),
                        s.acc().length(), s.vel().length(), s.yaw(), s.turn());
            }
        }
        System.out.printf("%s %s#%s near-clip ticks by goal %s, centre ticks by goal %s, longest centre stretch %d ticks:%s%n",
                c, a[0], a[1], clip, centre, best, goals);
    }
}
