package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;
import grill24.fishsim.harness.companion.OwnerScript;
import grill24.fishsim.harness.companion.PartyRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Diagnostic (owner feedback 2026-10-14, "the hand visit is too frequent"): how often a percher
 * comes to the owner's hand, why ({@link PerchBody.HandWhy}), how long between visits and how much
 * of the time a fish sits on a hand. Solo for each percher kind, then per party: what the player
 * sees is any fish on a hand, so the party figure sums its members.
 *
 * <p>Run with -PsimStdout; -PsweepSeeds=N changes the seed count (default 4); -PtweakA=noHandClock
 * is the pre-HandClock behaviour, {@code soloClock} keeps the clock but each party member its own.
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class HandVisitProbe {

    static final int TICKS = 2400;
    static final int TPS = 20;

    static CompanionTunables tunables() {
        CompanionTunables t = PartyBaselineProbe.tunables();
        switch (System.getProperty("tweakA", "")) {
            case "noHandClock" -> t.handClock = false;
            case "soloClock" -> t.partyHandClock = false;
            case "noIdleInView" -> t.idleInView = false;
            case "noPeek" -> t.shoulderPeek = false;
            case "noBeats" -> t.moreGroundBeats = false;
            case "none" -> { t.idleInView = false; t.moreGroundBeats = false; }
            default -> { }
        }
        if (System.getProperty("tweakList") != null) {
            String[] g = System.getProperty("tweakList").split(",");
            t.handGapMinSeconds = Float.parseFloat(g[0]);
            t.handGapMaxSeconds = Float.parseFloat(g[1]);
        }
        return t;
    }

    private static int seeds() {
        return Integer.parseInt(System.getProperty("sweepSeeds", "4"));
    }

    /** Totals over a set of runs. */
    static final class Tally {
        long ticks, handTicks, beats, peeks;
        final long[] why = new long[PerchBody.HandWhy.values().length];
        final List<Double> gaps = new ArrayList<>();
        /** Per script: visits and ticks. */
        final Map<String, long[]> byScript = new TreeMap<>();

        long visits() { return Arrays.stream(why).sum(); }

        double perMin(long v, long t) { return t == 0 ? 0 : v * 60.0 * TPS / t; }

        void print(String label) {
            long v = visits();
            double[] g = gaps.stream().mapToDouble(Double::doubleValue).sorted().toArray();
            StringBuilder w = new StringBuilder();
            for (PerchBody.HandWhy h : PerchBody.HandWhy.values()) {
                w.append(String.format(Locale.ROOT, " %s %.2f", h, perMin(why[h.ordinal()], ticks)));
            }
            System.out.printf(Locale.ROOT, "%n%s  (%.0f min simulated)%n", label, ticks / (60.0 * TPS));
            System.out.printf(Locale.ROOT, "  visits/min %.2f  (by why:%s)%n", perMin(v, ticks), w);
            System.out.printf(Locale.ROOT, "  ground beats/min %.2f; shoulder peeks/min %.2f%n", perMin(beats, ticks), perMin(peeks, ticks));
            System.out.printf(Locale.ROOT, "  on a hand %% of time: %.1f; mean stay %.1f s%n",
                    100.0 * handTicks / ticks, v == 0 ? 0 : handTicks / (double) v / TPS);
            if (g.length > 0) {
                System.out.printf(Locale.ROOT, "  gap between visits s: p10 %.1f | median %.1f | p90 %.1f (n=%d)%n",
                        g[(int) (g.length * 0.1)], g[g.length / 2], g[Math.min(g.length - 1, (int) (g.length * 0.9))], g.length);
            }
            StringBuilder s = new StringBuilder();
            byScript.entrySet().stream()
                    .sorted((a, b) -> Double.compare(perMin(b.getValue()[0], b.getValue()[1]), perMin(a.getValue()[0], a.getValue()[1])))
                    .forEach(e -> s.append(String.format(Locale.ROOT, " %s %.1f;", e.getKey(), perMin(e.getValue()[0], e.getValue()[1]))));
            System.out.printf(Locale.ROOT, "  per script visits/min:%s%n", s);
        }
    }

    /** Follows one agent's counters through a run. */
    static final class Watch {
        final CompanionAgent a;
        int last;
        int lastVisitTick = -1;

        Watch(CompanionAgent a) { this.a = a; }

        /** Visits this tick, recording the gap since the previous one. */
        int tick(int k, Tally t) {
            int now = Arrays.stream(a.handVisits()).sum();
            int d = now - last;
            last = now;
            if (d > 0) {
                if (lastVisitTick >= 0) t.gaps.add((k - lastVisitTick) / (double) TPS);
                lastVisitTick = k;
            }
            return d;
        }
    }

    record Kind(String name, Locomotion loco, GroundGait gait, float length) {}

    static final List<Kind> SOLOS = List.of(
            new Kind("slug 12 cm (crawl)", Locomotion.BENTHIC, GroundGait.CRAWL, 0.12f),
            new Kind("crab 30 cm (crawl)", Locomotion.BENTHIC, GroundGait.CRAWL, 0.30f),
            new Kind("starfish 45 cm (crawl)", Locomotion.BENTHIC, GroundGait.CRAWL, 0.45f),
            new Kind("seahorse 20 cm (pure percher)", Locomotion.BENTHIC, null, 0.20f));

    @Test
    void solo() {
        for (Kind kd : SOLOS) {
            Tally t = new Tally();
            long seen = 0, seenOnHand = 0;
            Map<String, Long> scriptSeen = new TreeMap<>();
            for (String script : PartyRun.SCRIPTS) {
                for (long seed = 1; seed <= seeds(); seed++) {
                    OwnerScript os = OwnerScript.named(script, seed);
                    CompanionWorld w = os.nextWorld();
                    CompanionAgent a = new CompanionAgent(tunables(), seed, kd.loco, kd.length,
                            Temperament.BASELINE, kd.gait, null, w);
                    Watch watch = new Watch(a);
                    long visits = 0;
                    for (int k = 0; k < TICKS; k++) {
                        CompanionWorld wk = os.nextWorld();
                        a.step(wk);
                        visits += watch.tick(k, t);
                        if (a.appear() >= 1f && Visibility.visible(wk.owner(), a.position(), a.voxels())) {
                            seen++;
                            if (a.riding() && a.perchSlot().hand()) seenOnHand++;
                            scriptSeen.merge(script, 1L, Long::sum);
                        }
                    }
                    int[] why = a.handVisits();
                    for (int i = 0; i < why.length; i++) t.why[i] += why[i];
                    t.handTicks += a.handTicks();
                    t.beats += a.overtakes() + a.roadsides();
                    t.peeks += a.peeks();
                    t.ticks += TICKS;
                    long[] s = t.byScript.computeIfAbsent(script, x -> new long[2]);
                    s[0] += visits;
                    s[1] += TICKS;
                }
            }
            t.print("SOLO " + kd.name);
            System.out.printf(Locale.ROOT, "  seen %% of time %.1f, of which on a hand %.0f%%%n",
                    100.0 * seen / t.ticks, seen == 0 ? 0 : 100.0 * seenOnHand / seen);
            StringBuilder ss = new StringBuilder();
            for (String sc : List.of("idle_fixed", "idle_looking", "long_idle_then_walk", "stop_and_go", "sprint_stop", "walk_flat")) {
                ss.append(String.format(Locale.ROOT, " %s %.1f;", sc, 100.0 * scriptSeen.getOrDefault(sc, 0L) / (TICKS * (double) seeds())));
            }
            System.out.printf(Locale.ROOT, "  seen %% on:%s%n", ss);
        }
    }

    @Test
    void party() {
        for (PartyRun.Party p : PartyRun.PARTIES) {
            if (p.members().stream().noneMatch(m -> m.loco() == Locomotion.BENTHIC)) continue;
            Tally t = new Tally();
            double[] seenBy = null;
            for (String script : PartyRun.SCRIPTS) {
                for (long seed = 1; seed <= seeds(); seed++) {
                    OwnerScript os = OwnerScript.named(script, seed);
                    CompanionWorld base = os.nextWorld();
                    PartyLayer layer = p.build(tunables(), Temperament.BASELINE, seed, base);
                    List<Watch> watches = new ArrayList<>();
                    for (PartyLayer.Unit u : layer.units()) for (CompanionAgent a : u.agents()) watches.add(new Watch(a));
                    // The party-wide gap: any fish arriving on a hand after any other did.
                    int lastAny = -1;
                    long visits = 0;
                    if (seenBy == null) seenBy = new double[watches.size()];
                    for (int k = 0; k < TICKS; k++) {
                        CompanionWorld wk = os.nextWorld();
                        layer.step(wk);
                        for (int i = 0; i < watches.size(); i++) {
                            CompanionAgent a = watches.get(i).a;
                            if (a.appear() >= 1f && Visibility.visible(wk.owner(), a.position(), a.voxels())) seenBy[i]++;
                        }
                        int d = 0;
                        for (Watch wa : watches) d += wa.tick(k, new Tally());
                        if (d > 0) {
                            if (lastAny >= 0) t.gaps.add((k - lastAny) / (double) TPS);
                            lastAny = k;
                        }
                        visits += d;
                        boolean onHand = false;
                        for (Watch wa : watches) {
                            onHand |= wa.a.riding() && wa.a.perchSlot() != null && wa.a.perchSlot().hand();
                        }
                        if (onHand) t.handTicks++;
                    }
                    for (Watch wa : watches) {
                        int[] why = wa.a.handVisits();
                        for (int i = 0; i < why.length; i++) t.why[i] += why[i];
                        t.beats += wa.a.overtakes() + wa.a.roadsides();
                        t.peeks += wa.a.peeks();
                    }
                    t.ticks += TICKS;
                    long[] s = t.byScript.computeIfAbsent(script, x -> new long[2]);
                    s[0] += visits;
                    s[1] += TICKS;
                }
            }
            t.print("PARTY " + p.name() + " (any member; on-a-hand % = some fish on a hand)");
            StringBuilder sb = new StringBuilder();
            for (double v : seenBy) sb.append(String.format(Locale.ROOT, " %.1f", 100.0 * v / t.ticks));
            System.out.printf(Locale.ROOT, "  seen %% per body (party order):%s%n", sb);
        }
    }
}
