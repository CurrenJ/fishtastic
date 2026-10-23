package grill24.fishsim.harness.companion;

import grill24.fishsim.companion.CompanionAgent;
import grill24.fishsim.companion.CompanionTunables;
import grill24.fishsim.companion.CompanionWorld;
import grill24.fishsim.companion.PartyLayer;
import grill24.fishsim.companion.Temperament;
import grill24.fishsim.companion.Vec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Diagnostic (docs/fish-companions.md, Phase 2f): where do two different-species ground
 * benthics overlap? Every inter-unit drawn overlap tick of the ground parties is binned by both
 * bodies' states (land mode and goal, or the beat under way) and by whether the owner walks, so
 * the candidate fix (a shared foot-trail queue while following, or a mixed-species line) can be
 * matched to where the overlap actually is. Run with -PsimStdout; -PsweepSeeds=N.
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class GroundOverlapProbe {

    @Test
    void where() {
        int seeds = Integer.parseInt(System.getProperty("sweepSeeds", "8"));
        for (PartyRun.Party p : PartyRun.PARTIES) {
            if (!p.name().contains("starfish") && !p.name().contains("octopus")) continue;
            Map<String, Integer> bins = new TreeMap<>();
            Map<String, Integer> byScript = new TreeMap<>();
            long overlapTicks = 0, groundBothTicks = 0, ticksAll = 0;
            for (String script : PartyRun.SCRIPTS) {
                for (long seed = 1; seed <= seeds; seed++) {
                    OwnerScript os = OwnerScript.named(script, seed);
                    CompanionWorld base = os.nextWorld();
                    PartyLayer layer = p.layer(CompanionTunables.defaults(), Temperament.BASELINE, p.groups(seed), base);
                    for (int k = 0; k < 2400; k++) {
                        base = os.nextWorld();
                        layer.step(base);
                        ticksAll++;
                        boolean walking = base.owner().horizontalSpeed() > 0.6;
                        boolean any = false, bothGround = false;
                        String key = null;
                        int n = layer.size();
                        for (int i = 0; i < n; i++) {
                            List<double[]> bi = PartyRun.bodies(layer.unit(i));
                            List<CompanionAgent> ai = agents(layer.unit(i), bi.size());
                            for (int j = i + 1; j < n; j++) {
                                List<double[]> bj = PartyRun.bodies(layer.unit(j));
                                List<CompanionAgent> aj = agents(layer.unit(j), bj.size());
                                for (int x = 0; x < bi.size(); x++) {
                                    for (int y = 0; y < bj.size(); y++) {
                                        double[] a = bi.get(x), b = bj.get(y);
                                        if (a[7] < 1f || b[7] < 1f) continue;
                                        CompanionAgent ca = ai.get(x), cb = aj.get(y);
                                        if (ca.onGround() && cb.onGround()) bothGround = true;
                                        if (seg(a, b) < PartyRun.BODY_HALF_WIDTH * (a[6] + b[6])) {
                                            any = true;
                                            String la = label(ca), lb = label(cb);
                                            key = (walking ? "walk  " : "still ") + (la.compareTo(lb) <= 0 ? la + " | " + lb : lb + " | " + la);
                                        }
                                    }
                                }
                            }
                        }
                        if (bothGround) groundBothTicks++;
                        if (any) {
                            overlapTicks++;
                            bins.merge(key, 1, Integer::sum);
                            byScript.merge(script, 1, Integer::sum);
                        }
                    }
                }
            }
            System.out.printf(Locale.ROOT, "%nPARTY %s: overlap ticks %d (%.2f%% of all, %.2f%% of both-on-ground)%n",
                    p.name(), overlapTicks, 100.0 * overlapTicks / ticksAll, 100.0 * overlapTicks / Math.max(1, groundBothTicks));
            List<Map.Entry<String, Integer>> es = new ArrayList<>(bins.entrySet());
            es.sort((a, b) -> b.getValue() - a.getValue());
            for (int i = 0; i < Math.min(18, es.size()); i++)
                System.out.printf(Locale.ROOT, "  %5.1f%%  %s%n", 100.0 * es.get(i).getValue() / overlapTicks, es.get(i).getKey());
            List<Map.Entry<String, Integer>> ss = new ArrayList<>(byScript.entrySet());
            ss.sort((a, b) -> b.getValue() - a.getValue());
            StringBuilder sb = new StringBuilder("  by script:");
            for (int i = 0; i < Math.min(10, ss.size()); i++)
                sb.append(String.format(Locale.ROOT, " %s %.0f%%", ss.get(i).getKey(), 100.0 * ss.get(i).getValue() / overlapTicks));
            System.out.println(sb);
        }
    }

    private static List<CompanionAgent> agents(PartyLayer.Unit u, int bodies) {
        List<CompanionAgent> out = new ArrayList<>(bodies);
        for (int i = 0; i < bodies; i++) out.add(u.ground() != null ? u.ground().member(i) : u.leader());
        return out;
    }

    private static String label(CompanionAgent a) {
        if (!a.onGround()) return a.landMode();
        if (a.beat() != null) return "BEAT_" + a.beat();
        return a.landMode() + "/" + a.goal();
    }

    private static double seg(double[] a, double[] b) {
        Vec p1 = new Vec(a[0], a[1], a[2]), q1 = new Vec(a[3], a[4], a[5]);
        Vec p2 = new Vec(b[0], b[1], b[2]), q2 = new Vec(b[3], b[4], b[5]);
        double best = Double.MAX_VALUE;
        for (int i = 0; i <= 8; i++) {
            Vec x = p1.add(q1.sub(p1).scale(i / 8.0));
            for (int j = 0; j <= 8; j++) best = Math.min(best, x.distanceTo(p2.add(q2.sub(p2).scale(j / 8.0))));
        }
        return best;
    }
}
