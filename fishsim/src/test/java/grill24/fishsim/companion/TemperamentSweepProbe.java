package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;
import grill24.fishsim.harness.companion.CompanionRun;
import grill24.fishsim.harness.companion.OwnerScript;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Diagnostic (plan §10.5): is a temperament systematically harder on the invariants than the
 * baseline, or does it just land on the baseline's own tails with different trajectories? For the
 * baseline and each temperament, over 8 seeds × every script, how many runs break each of the
 * invariant table's main bounds, plus a few totals. Run with -PsimStdout [-PsweepClass=BENTHIC]
 * [-PsweepTemperament=ghost to run just one against the baseline] [-PsweepSeeds=40] [-Psweep=forest,...].
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class TemperamentSweepProbe {

    static final int TICKS = 2400;
    static final Set<String> OBSTACLES = Set.of("corridor", "doorway", "forest", "stairwell", "low_ceiling", "pond_dive", "wading");
    static final Set<String> LEASH = Set.of("teleport", "elytra", "creative_flight");

    @Test
    void sweep() {
        int SEEDS = Integer.parseInt(System.getProperty("sweepSeeds", "8"));
        List<String> scripts = System.getProperty("sweep") != null ? List.of(System.getProperty("sweep").split(",")) : OwnerScript.NAMES;
        Locomotion c = Locomotion.valueOf(System.getProperty("sweepClass", "FREE_SWIM"));
        List<Temperament> temps = new ArrayList<>();
        temps.add(Temperament.BASELINE);
        String only = System.getProperty("sweepTemperament");
        if (only != null) temps.add(Temperament.named(only)); else temps.addAll(Temperament.ALL);
        System.out.printf("== %s, runs over each bound out of %d (%d seeds x %d scripts)%n", c, SEEDS * scripts.size(),
                SEEDS, scripts.size());
        System.out.println("   temperament   contacts impact inOwner nearClip centre unseen  far leash | sum(inOwner) sum(nearClip) sum(contacts) maxCentre");
        for (Temperament tm : temps) {
            int contacts = 0, impact = 0, inOwner = 0, nearClip = 0, centre = 0, unseen = 0, far = 0, leash = 0;
            long sumIn = 0, sumClip = 0, sumContacts = 0;
            int maxCentre = 0;
            List<String> worst = new ArrayList<>(), named = new ArrayList<>();
            for (String script : scripts) {
                for (int seed = 1; seed <= SEEDS; seed++) {
                    CompanionRun run = new CompanionRun(script, seed, c, tm, CompanionTunables.defaults(), true);
                    run.run(TICKS);
                    var m = run.metrics();
                    double minutes = m.ticks() / 20.0 / 60.0;
                    boolean obstacle = OBSTACLES.contains(script), tunnel = script.equals("corridor");
                    sumIn += m.insideOwnerTicks();
                    sumClip += m.nearClipTicks();
                    sumContacts += m.backstops();
                    maxCentre = Math.max(maxCentre, m.longestCentreDwellTicks());
                    if (obstacle && m.backstops() / minutes > 8) contacts++;
                    if (obstacle && m.maxImpact() > 3) impact++;
                    if (!tunnel && m.insideOwnerTicks() / (double) m.ticks() > 0.01) inOwner++;
                    if (!tunnel && m.nearClipTicks() / (double) m.ticks() > (script.equals("stairwell") ? 0.02 : 0.01)) nearClip++;
                    if (m.longestCentreDwellTicks() > 40) {
                        centre++;
                        if (m.longestCentreDwellTicks() > 63) worst.add(script + "#" + seed + ":" + m.longestCentreDwellTicks());
                    }
                    if (!tunnel && c != Locomotion.ANCHORED && (m.longestUnseenSeconds() > 30 || m.seenFraction() < 0.05)) unseen++;
                    if (!LEASH.contains(script) && m.longestFarSeconds() > 8) far++;
                    if (!LEASH.contains(script) && m.leashEvents() > (tunnel ? 1 : 0)) {
                        leash++;
                        named.add("leash " + script + "#" + seed);
                    }
                    if (!tunnel && m.insideOwnerTicks() / (double) m.ticks() > 0.01) named.add("inOwner " + script + "#" + seed);
                    if (!tunnel && c != Locomotion.ANCHORED && (m.longestUnseenSeconds() > 30 || m.seenFraction() < 0.05)) {
                        named.add("unseen " + script + "#" + seed + String.format(":%.0fs", m.longestUnseenSeconds()));
                    }
                    if (obstacle && m.backstops() / minutes > 8) named.add("contacts " + script + "#" + seed);
                }
            }
            System.out.printf("   %-12s %8d %6d %7d %8d %6d %6d %4d %5d | %12d %13d %13d %9d%n", tm.name(), contacts, impact, inOwner,
                    nearClip, centre, unseen, far, leash, sumIn, sumClip, sumContacts, maxCentre);
            if (!worst.isEmpty()) System.out.println("      centre over baseline's worst (63): " + worst);
            if (System.getProperty("tracePos") != null && !named.isEmpty()) System.out.println("      " + named);
        }
    }
}
