package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.OwnerScript;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Diagnostic (spec §23): where and why a group member fades on stairs. Prints each fade's onset with
 * the member's ground state over the 3 s before it. -PsimStdout -Ptrace=script -PsweepSeeds=3
 * -PsweepClass=LINE|CROWD -PsweepLength=0.4.
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class StairsFadeProbe {

    @Test
    void fades() {
        String script = System.getProperty("trace", "stairs_up_down");
        int seeds = Integer.parseInt(System.getProperty("sweepSeeds", "3"));
        BenthicGroup.Formation f = BenthicGroup.Formation.valueOf(System.getProperty("sweepClass", "LINE"));
        float length = Float.parseFloat(System.getProperty("sweepLength", f == BenthicGroup.Formation.LINE ? "0.4" : "0.12"));
        for (long seed = 1; seed <= seeds; seed++) {
            OwnerScript owner = OwnerScript.named(script, seed);
            CompanionWorld world = owner.nextWorld();
            BenthicGroup g = new BenthicGroup(CompanionTunables.defaults(), seed, length, Temperament.BASELINE,
                    GroundGait.CRAWL, 3, f, world);
            int n = g.count();
            String[][] hist = new String[n][60];
            CompanionAgent.Fade[] last = new CompanionAgent.Fade[n];
            for (int tick = 0; tick < 2400; tick++) {
                if (tick > 0) world = owner.nextWorld();
                g.step(world);
                for (int i = 0; i < n; i++) {
                    CompanionAgent a = g.member(i);
                    GroundMind m = a.groundMind();
                    Vec p = a.position(), o = world.owner().feet();
                    Vec ahead = i > 0 ? g.member(i - 1).position() : o;
                    hist[i][tick % 60] = String.format(
                            "t=%4d %-12s %-14s pos %6.2f %6.2f %6.2f ahead %6.2f %6.2f %6.2f own %6.2f %6.2f %6.2f lag %5.2f %s strand %2d walk %s tgt %s",
                            tick, a.landMode(), a.perchState(), p.x(), p.y(), p.z(), ahead.x(), ahead.y(), ahead.z(),
                            o.x(), o.y(), o.z(), m == null ? -1 : m.lag, m != null && m.reachable ? "R" : "-",
                            m == null ? 0 : m.strandedTicks,
                            m == null || m.target == null ? "-" : String.format("%.2f %.2f %.2f", m.target.x(), m.target.y(), m.target.z()),
                            m == null || m.formationTarget == null ? "-" : String.format("%.2f %.2f %.2f",
                                    m.formationTarget.x(), m.formationTarget.y(), m.formationTarget.z()));
                    int from = Integer.parseInt(System.getProperty("sweepTicks", "-1"));
                    if (from >= 0 && seed == 1 && tick >= from && tick < from + 90 && tick % 3 == 0)
                        System.out.println("W m" + i + " " + hist[i][tick % 60]);
                    if (last[i] == CompanionAgent.Fade.NONE && a.fade() == CompanionAgent.Fade.OUT) {
                        System.out.printf("FADE seed=%d member=%d tick=%d%n", seed, i, tick);
                        for (int k = 59; k >= 0; k -= 6) {
                            String h = hist[i][Math.floorMod(tick - k, 60)];
                            if (h != null) System.out.println("   " + h);
                        }
                    }
                    last[i] = a.fade();
                }
            }
        }
    }
}
