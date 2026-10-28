package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.OwnerScript;
import grill24.fishsim.harness.companion.PartyRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Diagnostic (owner feedback 2026-10-28, "benthics spend a lot of time in my hands and keep jumping
 * between shoulders and hands"): where a solo benthic spends its time (ground, hand, a shoulder,
 * the head, in the air, swimming), how much of each is on screen, how often it changes place and
 * between which places, how long it stays, and why it went to the hand ({@link PerchBody.HandWhy}).
 * Real species settings: hellbender, plaice, starfish (gait, temperament, rendered length).
 *
 * <p>Run with -PsimStdout; -PsweepSeeds=N changes the seed count (default 4 for the 2-minute
 * suite, the play sessions use 10 minutes and N+2 seeds).
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class PositionSplitProbe {

    static final int TPS = 20;

    enum Place { GROUND, HAND, SHOULDER_R, SHOULDER_L, HEAD, BACK, HOP, SWIM }

    record Species(String name, GroundGait gait, Temperament tm, float length) {}

    static final List<Species> SPECIES = List.of(
            new Species("hellbender (crawl, ambusher, 0.54)", GroundGait.CRAWL, Temperament.AMBUSHER, 0.50f * 1.07f),
            new Species("plaice (ripple, wily, 0.39)", GroundGait.RIPPLE, Temperament.WILY, 0.35f * 1.10f),
            new Species("starfish (crawl, docile, 0.43)", GroundGait.CRAWL, Temperament.DOCILE, 0.40f * 1.07f));

    static Place place(CompanionAgent a) {
        PerchBody p = a.perchBody();
        return switch (p.mode) {
            case SWIM -> Place.SWIM;
            case GROUND, GROUND_CROUCH -> Place.GROUND;
            case HOP -> Place.HOP;
            default -> switch (p.slot) {
                case RIGHT_HAND, LEFT_HAND -> Place.HAND;
                case RIGHT_SHOULDER -> Place.SHOULDER_R;
                case LEFT_SHOULDER -> Place.SHOULDER_L;
                case HEAD -> Place.HEAD;
                case BACK -> Place.BACK;
            };
        };
    }

    static String shortName(Place p) {
        return switch (p) {
            case GROUND -> "ground";
            case HAND -> "hand";
            case SHOULDER_R -> "shR";
            case SHOULDER_L -> "shL";
            case HEAD -> "head";
            case BACK -> "back";
            case HOP -> "hop";
            case SWIM -> "swim";
        };
    }

    static final class Tally {
        long ticks, lookDownTicks, peeks, faceTurns;
        final long[] at = new long[Place.values().length];
        final long[] seenAt = new long[Place.values().length];
        final long[] why = new long[PerchBody.HandWhy.values().length];
        /** Place changes, "from>to" (hops through the air collapsed; "x>x" is a hop back onto the same place). */
        final Map<String, Long> moves = new TreeMap<>();
        final Map<Place, List<Double>> stays = new EnumMap<>(Place.class);
        /** Shoulder>hand>shoulder and ground>hand>ground round trips under 10 s apart: the back-and-forth. */
        long quickReturns;
        /** Seconds between arrivals on the hand. */
        final List<Double> handGaps = new ArrayList<>();

        long changes() {
            return moves.values().stream().mapToLong(Long::longValue).sum();
        }

        double perMin(long v) {
            return ticks == 0 ? 0 : v * 60.0 * TPS / ticks;
        }

        void print(String label) {
            long seen = Arrays.stream(seenAt).sum();
            System.out.printf(Locale.ROOT, "%n%s  (%.0f min simulated; owner looking down >50deg %.1f%% of time)%n",
                    label, ticks / (60.0 * TPS), 100.0 * lookDownTicks / ticks);
            StringBuilder time = new StringBuilder(), vis = new StringBuilder();
            for (Place p : Place.values()) {
                if (at[p.ordinal()] == 0) continue;
                time.append(String.format(Locale.ROOT, " %s %.1f%%", shortName(p), 100.0 * at[p.ordinal()] / ticks));
                if (seenAt[p.ordinal()] > 0) {
                    vis.append(String.format(Locale.ROOT, " %s %.0f%%", shortName(p), 100.0 * seenAt[p.ordinal()] / Math.max(1, seen)));
                }
            }
            System.out.printf(Locale.ROOT, "  time at:%s%n", time);
            System.out.printf(Locale.ROOT, "  on screen %.1f%% of time; of that on-screen time, where:%s%n", 100.0 * seen / ticks, vis);
            System.out.printf(Locale.ROOT, "  place changes/min %.2f; hand arrivals/min %.2f (look-down %.2f, held item %.2f, unseen timer %.2f, other %.2f); peeks/min %.2f%n",
                    perMin(changes()), perMin(Arrays.stream(why).sum()),
                    perMin(why[0]), perMin(why[1]), perMin(why[2]), perMin(why[3]), perMin(peeks));
            System.out.printf(Locale.ROOT, "  quick returns to the hand (<10 s after leaving it)/min %.2f%n", perMin(quickReturns));
            StringBuilder mv = new StringBuilder();
            moves.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue(), x.getValue()))
                    .forEach(e -> mv.append(String.format(Locale.ROOT, " %s %.2f;", e.getKey(), perMin(e.getValue()))));
            System.out.printf(Locale.ROOT, "  moves/min:%s%n", mv);
            StringBuilder st = new StringBuilder();
            for (Map.Entry<Place, List<Double>> e : stays.entrySet()) {
                double[] d = e.getValue().stream().mapToDouble(Double::doubleValue).sorted().toArray();
                if (d.length == 0) continue;
                st.append(String.format(Locale.ROOT, " %s n=%d med %.1f p90 %.1f;", shortName(e.getKey()), d.length,
                        d[d.length / 2], d[Math.min(d.length - 1, (int) (d.length * 0.9))]));
            }
            System.out.printf(Locale.ROOT, "  stay length s:%s%n", st);
            double[] g = handGaps.stream().mapToDouble(Double::doubleValue).sorted().toArray();
            if (g.length > 0) {
                System.out.printf(Locale.ROOT, "  gap between hand arrivals s: p10 %.1f | median %.1f | p90 %.1f (n=%d)%n",
                        g[(int) (g.length * 0.1)], g[g.length / 2], g[Math.min(g.length - 1, (int) (g.length * 0.9))], g.length);
            }
        }
    }

    /** One fish through one script, added to {@code t}. */
    static void run(Species sp, String script, long seed, int ticks, Tally t, Tally perScript) {
        run(CompanionTunables.defaults(), sp, script, seed, ticks, t, perScript);
    }

    /** The look-down switches as they were before 2026-10-28: every look down of 0.75 s calls it up. */
    static CompanionTunables before() {
        CompanionTunables t = CompanionTunables.defaults();
        t.lookDownFaceOnGround = false;
        t.lookDownGapSeconds = 0;
        t.lookDownSeconds = 0.75f;
        return t;
    }

    static void run(CompanionTunables tun, Species sp, String script, long seed, int ticks, Tally t, Tally perScript) {
        OwnerScript os = OwnerScript.named(script, seed);
        CompanionWorld w = os.nextWorld();
        CompanionAgent a = new CompanionAgent(tun, seed, grill24.fishsim.core.Locomotion.BENTHIC,
                sp.length, sp.tm, sp.gait, w);
        Place rest = place(a);
        int restSince = 0, lastHandArrive = -1, lastHandLeave = -100000;
        int lastHops = 0;
        for (int k = 0; k < ticks; k++) {
            CompanionWorld wk = os.nextWorld();
            a.step(wk);
            Place p = place(a);
            int hops = a.perchBody().hops;
            boolean seen = a.appear() >= 1f && a.seen();
            boolean down = wk.owner().pitchDeg() > 50f;
            for (Tally x : new Tally[]{t, perScript}) {
                x.ticks++;
                x.at[p.ordinal()]++;
                if (seen) x.seenAt[p.ordinal()]++;
                if (down) x.lookDownTicks++;
            }
            boolean hopBack = p != Place.HOP && p == rest && hops > lastHops && a.perchBody().modeTicks <= 1
                    && rest != Place.GROUND;
            if (p != Place.HOP && (p != rest || hopBack)) {
                String key = shortName(rest) + ">" + shortName(p);
                double stay = (k - restSince) / (double) TPS;
                for (Tally x : new Tally[]{t, perScript}) {
                    x.moves.merge(key, 1L, Long::sum);
                    x.stays.computeIfAbsent(rest, q -> new ArrayList<>()).add(stay);
                }
                if (rest == Place.HAND) lastHandLeave = k;
                if (p == Place.HAND) {
                    if (k - lastHandLeave < 10 * TPS) {
                        t.quickReturns++;
                        perScript.quickReturns++;
                    }
                    if (lastHandArrive >= 0) {
                        t.handGaps.add((k - lastHandArrive) / (double) TPS);
                        perScript.handGaps.add((k - lastHandArrive) / (double) TPS);
                    }
                    lastHandArrive = k;
                }
                rest = p;
                restSince = k;
            }
            if (p != Place.HOP) lastHops = hops;
        }
        int[] why = a.handVisits();
        int turns = a.perchBody().gmind == null ? 0 : a.perchBody().gmind.faceTurns;
        for (Tally x : new Tally[]{t, perScript}) {
            x.faceTurns += turns;
            for (int i = 0; i < why.length; i++) x.why[i] += why[i];
            x.peeks += a.peeks();
        }
    }

    static int seeds() {
        return Integer.parseInt(System.getProperty("sweepSeeds", "4"));
    }

    /** The 2-minute script suite the hand-visit probe uses, plus the two standing-about ground scripts. */
    @Test
    void suite() {
        List<String> scripts = new ArrayList<>(PartyRun.SCRIPTS);
        scripts.addAll(List.of("mine_in_place", "pace_chest"));
        for (Species sp : SPECIES) {
            Tally all = new Tally();
            Map<String, Tally> by = new TreeMap<>();
            for (String s : scripts) {
                for (long seed = 1; seed <= seeds(); seed++) run(sp, s, seed, 2400, all, by.computeIfAbsent(s, x -> new Tally()));
            }
            all.print("SUITE " + sp.name + " (" + scripts.size() + " scripts x " + seeds() + " seeds x 2 min)");
            System.out.println("  per script: hand% / visible-on-hand% of on-screen / changes/min / hand arrivals/min");
            for (Map.Entry<String, Tally> e : by.entrySet()) {
                Tally x = e.getValue();
                long seen = Arrays.stream(x.seenAt).sum();
                System.out.printf(Locale.ROOT, "    %-22s %5.1f%%  %5.0f%%  %5.2f  %5.2f%n", e.getKey(),
                        100.0 * x.at[Place.HAND.ordinal()] / x.ticks,
                        seen == 0 ? 0 : 100.0 * x.seenAt[Place.HAND.ordinal()] / seen, x.perMin(x.changes()),
                        x.perMin(Arrays.stream(x.why).sum()));
            }
        }
    }

    /** Dose-response: N short looks at the feet a minute, standing and walking (10-minute runs). */
    @Test
    void lookDownSweep() {
        System.out.println("\nLOOK-DOWN SWEEP: script | species | hand% | ground% | perched% | hand share of on-screen | changes/min | hand arrivals/min (look-down) | peeks/min");
        for (String kind : List.of("still", "walk")) {
            for (int n : new int[]{0, 1, 2, 4, 8}) {
                for (Species sp : SPECIES) {
                    Tally x = new Tally();
                    for (long seed = 1; seed <= seeds() + 2; seed++) run(sp, "lookdown_" + kind + "_" + n, seed, 12000, x, new Tally());
                    long seen = Arrays.stream(x.seenAt).sum();
                    long perched = x.at[Place.SHOULDER_L.ordinal()] + x.at[Place.SHOULDER_R.ordinal()]
                            + x.at[Place.HEAD.ordinal()] + x.at[Place.BACK.ordinal()];
                    System.out.printf(Locale.ROOT, "  %-18s %-10s %5.1f%% %5.1f%% %5.1f%%  %4.0f%%  %5.2f  %5.2f (%5.2f)  %5.2f%n",
                            "lookdown_" + kind + "_" + n, sp.name.substring(0, sp.name.indexOf(' ')),
                            100.0 * x.at[Place.HAND.ordinal()] / x.ticks, 100.0 * x.at[Place.GROUND.ordinal()] / x.ticks,
                            100.0 * perched / x.ticks, seen == 0 ? 0 : 100.0 * x.seenAt[Place.HAND.ordinal()] / seen,
                            x.perMin(x.changes()), x.perMin(Arrays.stream(x.why).sum()), x.perMin(x.why[0]), x.perMin(x.peeks));
                }
            }
        }
    }

    /**
     * The look-down change, equal grounds: the same scripts and seeds with the switches as before
     * ({@link #before}) and as now.
     */
    @Test
    void lookDownAB() {
        List<String> suite = new ArrayList<>(PartyRun.SCRIPTS);
        suite.addAll(List.of("mine_in_place", "pace_chest"));
        System.out.println("\nLOOK-DOWN A/B: run | species | arm | ground% | hand% | perched% | hop% | on-screen% | hand share of on-screen"
                + " | changes/min | hand/min (look-down) | face turns/min | peeks/min");
        List<String> rows = new ArrayList<>(List.of("play_building", "play_fishing", "play_exploring",
                "lookdown_still_2", "lookdown_still_8", "lookdown_walk_0", "lookdown_walk_8", "SUITE"));
        for (String row : rows) {
            for (Species sp : SPECIES) {
                for (boolean after : new boolean[]{false, true}) {
                    Tally x = new Tally();
                    CompanionTunables tun = after ? CompanionTunables.defaults() : before();
                    if (row.equals("SUITE")) {
                        for (String s : suite) for (long seed = 1; seed <= seeds(); seed++) run(tun, sp, s, seed, 2400, x, new Tally());
                    } else {
                        for (long seed = 1; seed <= seeds() + 2; seed++) run(tun, sp, row, seed, 12000, x, new Tally());
                    }
                    long seen = Arrays.stream(x.seenAt).sum();
                    long perched = x.at[Place.SHOULDER_L.ordinal()] + x.at[Place.SHOULDER_R.ordinal()]
                            + x.at[Place.HEAD.ordinal()] + x.at[Place.BACK.ordinal()];
                    System.out.printf(Locale.ROOT, "  %-17s %-10s %-6s %5.1f %5.1f %5.1f %5.1f  %5.1f  %4.0f%%  %5.2f  %5.2f (%5.2f)  %5.2f  %5.2f%n",
                            row, sp.name.substring(0, sp.name.indexOf(' ')), after ? "after" : "before",
                            100.0 * x.at[Place.GROUND.ordinal()] / x.ticks, 100.0 * x.at[Place.HAND.ordinal()] / x.ticks,
                            100.0 * perched / x.ticks, 100.0 * x.at[Place.HOP.ordinal()] / x.ticks, 100.0 * seen / x.ticks,
                            seen == 0 ? 0 : 100.0 * x.seenAt[Place.HAND.ordinal()] / seen,
                            x.perMin(x.changes()), x.perMin(Arrays.stream(x.why).sum()), x.perMin(x.why[0]),
                            x.perMin(x.faceTurns), x.perMin(x.peeks));
                }
            }
        }
    }

    /** Ten-minute play-like sessions: fishing (rod, standing), building (looking down), exploring. */
    @Test
    void play() {
        for (String s : List.of("play_fishing", "play_building", "play_exploring")) {
            for (Species sp : SPECIES) {
                Tally all = new Tally();
                for (long seed = 1; seed <= seeds() + 2; seed++) run(sp, s, seed, 12000, all, new Tally());
                all.print("PLAY " + s + "  " + sp.name);
            }
        }
    }
}
