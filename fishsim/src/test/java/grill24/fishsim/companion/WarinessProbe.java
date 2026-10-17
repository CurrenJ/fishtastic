package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;
import grill24.fishsim.harness.companion.CompanionRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Diagnostic for the wariness drive (plan §10, step 2e), 8 seeds per script: how quickly the fish
 * takes cover, how much of its alarmed time it spends on the owner's far side, whether it ever sits
 * nearer the threat than its owner once in cover, and how wariness wears off over a script (a cat
 * lounging, a hostile keeping its distance). Diagnostics, not objectives. Run with -PsimStdout
 * [-PsweepClass=BENTHIC].
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class WarinessProbe {

    static final int SEEDS = 8, TICKS = 2400, BUCKET = 100;

    @Test
    void wariness() {
        Locomotion c = Locomotion.valueOf(System.getProperty("sweepClass", "FREE_SWIM"));
        for (String script : new String[]{"hostile_approach", "cat_lounging", "held_items"}) {
            double reaction = 0, covered = 0, alarmed = 0, exposed = 0;
            int reactions = 0;
            double[] timeline = new double[TICKS / BUCKET];
            for (int seed = 1; seed <= SEEDS; seed++) {
                CompanionRun run = new CompanionRun(script, seed, c, CompanionTunables.defaults(), true);
                int alarmStart = -1, coverTicks = 0;
                for (int t = 0; t < TICKS; t++) {
                    run.tick();
                    CompanionAgent a = run.agent;
                    timeline[t / BUCKET] += a.wariness() / (BUCKET * (double) SEEDS);
                    boolean alarm = a.wariness() >= 0.4f && a.threat() != null;
                    if (!alarm) {
                        alarmStart = -1;
                        coverTicks = 0;
                        continue;
                    }
                    alarmed++;
                    if (alarmStart < 0) alarmStart = t;
                    boolean hiding = a.goal().equals("HIDE") || a.poseHint() == PoseHint.HIDE || a.poseHint() == PoseHint.DUCK;
                    if (hiding && coverTicks == 0) {
                        reaction += (t - alarmStart) / 20.0;
                        reactions++;
                    }
                    Vec chest = run.owner.chest();
                    Vec toThreat = a.threat().sub(chest).horizontal();
                    Vec toFish = a.position().sub(chest).horizontal();
                    boolean farSide = toThreat.dot(toFish) < 0;
                    if (farSide) covered++;
                    coverTicks = hiding ? coverTicks + 1 : 0;
                    // Once it has had 1.5 s to get there, it should never sit nearer the threat than its owner.
                    if (coverTicks > 30 && a.position().distanceTo(a.threat()) < chest.distanceTo(a.threat())) exposed++;
                }
            }
            System.out.printf("== %s %-17s alarmed=%5.1fs/run  reaction=%.2fs  far side=%3.0f%%  nearer than owner (after 1.5 s)=%.1f%%%n",
                    c, script, alarmed / 20 / SEEDS, reactions == 0 ? 0 : reaction / reactions,
                    alarmed == 0 ? 0 : 100 * covered / alarmed, alarmed == 0 ? 0 : 100 * exposed / alarmed);
            StringBuilder b = new StringBuilder("   wariness per 5 s:");
            for (double w : timeline) b.append(String.format(" %.2f", w));
            System.out.println(b);
        }
    }
}
