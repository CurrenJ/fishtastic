package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;
import grill24.fishsim.harness.companion.PartyRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Diagnostic (docs/fish-companions-party.md §6, §8; Phase 2 review): a mixed party run exactly as
 * the game runs it today, with no party layer. Each group is its own unit, and each unit sees the
 * others' leaders as COMPANION points of interest (ClientCompanions). Every member is also run solo
 * on the same script and seed, so each measure is "in the party" against "alone".
 *
 * <p>Answers, per party: do units overlap, fight for a perch, crowd the view, starve one another
 * of view time, or inspect each other? Run with -PsimStdout; -PsweepSeeds=N changes the seed count;
 * -PtweakA=noShoal groups by the pre-D13 rule (different-species free swimmers stay separate).
 * The parties, the stepping ({@link PartyLayer}) and the measures live in {@link PartyRun}, the
 * Phase 2b metric suite; this prints them.
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class PartyBaselineProbe {

    static final int TICKS = 2400;

    private static int seeds() {
        return Integer.parseInt(System.getProperty("sweepSeeds", "8"));
    }

    static PartyRun.Result run(PartyRun.Party p, String script, long seed) {
        return PartyRun.measure(p, script, seed, TICKS, CompanionTunables.defaults(), Temperament.BASELINE,
                !"noShoal".equals(System.getProperty("tweakA")),
                "homes".equals(System.getProperty("tweakB")) ? PartyBaselineProbe::assignHomes : null);
    }

    /**
     * Candidate (-PtweakB=homes): the party gives every percher its own home perch, once, the way
     * BenthicGroup already does inside a formation. Anchored fish ask first (D5: head, then the
     * shoulders); the rest take what's left, left shoulder, right shoulder, head, back. Solo
     * shoulder-wander is left as it is, so this measures homes alone.
     */
    static void assignHomes(PartyLayer layer) {
        java.util.EnumSet<PerchBody.Slot> free = java.util.EnumSet.of(PerchBody.Slot.HEAD,
                PerchBody.Slot.RIGHT_SHOULDER, PerchBody.Slot.LEFT_SHOULDER, PerchBody.Slot.BACK);
        List<CompanionAgent> anchored = new ArrayList<>(), others = new ArrayList<>();
        for (PartyLayer.Unit u : layer.units()) {
            if (u.ground() != null) {
                // A formation's members already hold distinct homes: take them out of the pool.
                for (CompanionAgent a : u.agents()) free.remove(a.perchSlot());
                continue;
            }
            CompanionAgent a = u.leader();
            if (a.perchSlot() == null) continue;
            (a.locomotion() == Locomotion.ANCHORED ? anchored : others).add(a);
        }
        List<PerchBody.Slot> anchoredOrder = List.of(PerchBody.Slot.HEAD, PerchBody.Slot.RIGHT_SHOULDER,
                PerchBody.Slot.LEFT_SHOULDER, PerchBody.Slot.BACK);
        List<PerchBody.Slot> otherOrder = List.of(PerchBody.Slot.LEFT_SHOULDER, PerchBody.Slot.RIGHT_SHOULDER,
                PerchBody.Slot.HEAD, PerchBody.Slot.BACK);
        for (CompanionAgent a : anchored) setHome(a, take(free, anchoredOrder));
        for (CompanionAgent a : others) setHome(a, take(free, otherOrder));
    }

    private static PerchBody.Slot take(java.util.Set<PerchBody.Slot> free, List<PerchBody.Slot> order) {
        for (PerchBody.Slot s : order) if (free.remove(s)) return s;
        return null;
    }

    private static void setHome(CompanionAgent a, PerchBody.Slot s) {
        if (s == null) return;
        try {
            var f = CompanionAgent.class.getDeclaredField("perch");
            f.setAccessible(true);
            PerchBody p = (PerchBody) f.get(a);
            p.home = s;
            p.slot = s;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Candidate: three different free swimmers as one school (the trio formation) instead of three
     * independent units. Same lengths as "3 free swimmers"; compare its overlap and seen % with
     * that party's baseline line.
     */
    @Test
    void shoal() {
        if (System.getProperty("sweepClass") != null) return;
        float[] lengths = {0.25f, 0.30f, 0.40f};
        int runs = 0;
        double overlap = 0, worst = 0, seen = 0, straggle = 0;
        String worstKey = "";
        for (String script : PartyRun.SCRIPTS) {
            for (long seed = 1; seed <= seeds(); seed++) {
                var r = grill24.fishsim.harness.companion.CompanionRun.school(script, seed, lengths,
                        Temperament.BASELINE, CompanionTunables.defaults(), true);
                r.run(TICKS);
                var fm = r.followerMetrics();
                var m = r.metrics();
                runs++;
                double o = fm.bodyOverlapFraction() * 100;
                overlap += o;
                if (o > worst) { worst = o; worstKey = script + "#" + seed; }
                seen += m.seenFraction() * 100;
                straggle = Math.max(straggle, fm.longestStraggleSeconds());
            }
        }
        System.out.printf(Locale.ROOT, "%nSHOAL 25/30/40 as one school (%d runs): drawn overlap %% mean %.2f | worst %.2f (%s);"
                + " leader seen %% %.1f; worst straggle %.1f s%n", runs, overlap / runs, worst, worstKey, seen / runs, straggle);
    }

    @Test
    void baseline() {
        String only = System.getProperty("sweepClass");
        for (PartyRun.Party p : PartyRun.PARTIES) {
            if (only != null && !p.name().contains(only)) continue;
            int runs = 0, clashRuns = 0, clashTicks = 0, fadesP = 0, fadesS = 0;
            double[] slotSeen = new double[PartyGrouping.SLOTS];
            double allPct = 0, allWorst = 0, allRate = 0, drawnRate = 0, clashRate = 0, drawn = 0, drawnWorst = 0, near = 0, two = 0, three = 0, threeWorst = 0, mates = 0, matesWorst = 0;
            double minSep = Double.MAX_VALUE;
            String drawnWorstKey = "", threeWorstKey = "", matesWorstKey = "";
            int units = -1;
            double[] seenP = null, seenS = null, inspP = null, inspS = null, cP = null, cS = null;
            double[] seenDropWorst = null;
            String[] seenDropKey = null;
            for (String script : PartyRun.SCRIPTS) {
                for (long seed = 1; seed <= seeds(); seed++) {
                    PartyRun.Result r = run(p, script, seed);
                    String key = script + "#" + seed;
                    if (units < 0) {
                        units = r.units();
                        seenP = new double[units]; seenS = new double[units]; inspP = new double[units];
                        inspS = new double[units]; cP = new double[units]; cS = new double[units];
                        seenDropWorst = new double[units]; seenDropKey = new String[units];
                    }
                    runs++;
                    drawn += r.drawnOverlapPct();
                    if (r.drawnOverlapPct() > drawnWorst) { drawnWorst = r.drawnOverlapPct(); drawnWorstKey = key; }
                    for (int sl = 0; sl < slotSeen.length; sl++) slotSeen[sl] += r.seenBySlot()[sl];
                    allPct += r.allOverlapPct();
                    allWorst = Math.max(allWorst, r.allOverlapPct());
                    allRate += r.allPerMin();
                    drawnRate += r.drawnPerMin();
                    clashRate += r.clashPerMin();
                    near += r.nearPct();
                    if (r.minSep() >= 0) minSep = Math.min(minSep, r.minSep());
                    two += r.twoInViewPct();
                    three += r.threeInViewPct();
                    if (r.threeInViewPct() > threeWorst) { threeWorst = r.threeInViewPct(); threeWorstKey = key; }
                    if (r.perchClashTicks() > 0) clashRuns++;
                    clashTicks += r.perchClashTicks();
                    fadesP += r.unitFadesParty();
                    fadesS += r.unitFadesSolo();
                    mates += r.partyInspectOfMates();
                    if (r.partyInspectOfMates() > matesWorst) { matesWorst = r.partyInspectOfMates(); matesWorstKey = key; }
                    for (int i = 0; i < units; i++) {
                        seenP[i] += r.seenParty()[i]; seenS[i] += r.seenSolo()[i];
                        inspP[i] += r.inspParty()[i]; inspS[i] += r.inspSolo()[i];
                        cP[i] += r.catchParty()[i]; cS[i] += r.catchSolo()[i];
                        double drop = r.seenSolo()[i] - r.seenParty()[i];
                        if (drop > seenDropWorst[i]) { seenDropWorst[i] = drop; seenDropKey[i] = key; }
                    }
                }
            }
            System.out.printf(Locale.ROOT, "%nPARTY %s  (%d units, %d runs)%n", p.name(), units, runs);
            System.out.printf(Locale.ROOT,
                    "  inter-unit drawn overlap %%: mean %.2f | worst %.2f (%s); within 0.25 b %%: %.2f; min sep %s b%n",
                    drawn / runs, drawnWorst, drawnWorstKey, near / runs,
                    minSep == Double.MAX_VALUE ? "-" : String.format(Locale.ROOT, "%.3f", minSep));
            System.out.printf(Locale.ROOT, "  rates /min: drawn overlap episodes %.2f; perch clash episodes %.2f%n",
                    drawnRate / runs, clashRate / runs);
            System.out.printf(Locale.ROOT, "  party-wide overlap (every body pair) %%: mean %.2f | worst %.2f; episodes /min %.2f%n",
                    allPct / runs, allWorst, allRate / runs);
            StringBuilder bySlot = new StringBuilder();
            for (int sl = 0; sl < slotSeen.length; sl++) {
                if (!Double.isNaN(slotSeen[sl])) bySlot.append(String.format(Locale.ROOT, " slot %d %.1f", sl + 1, slotSeen[sl] / runs));
            }
            System.out.printf(Locale.ROOT, "  seen %% by slot:%s%n", bySlot);
            System.out.printf(Locale.ROOT, "  >=2 units in view %%: %.1f; all 3 in view %%: %.1f | worst %.1f (%s)%n",
                    two / runs, three / runs, threeWorst, threeWorstKey);
            System.out.printf(Locale.ROOT, "  perch clash: %d runs, %d ticks; leash fades party %d vs solo %d%n",
                    clashRuns, clashTicks, fadesP, fadesS);
            System.out.printf(Locale.ROOT, "  INSPECT with no scripted points (= at party-mates) %%: mean %.2f | worst %.2f (%s)%n",
                    mates / runs, matesWorst, matesWorstKey);
            for (int i = 0; i < units; i++) {
                System.out.printf(Locale.ROOT,
                        "  unit %d: seen %% party %.1f vs solo %.1f (worst drop %.1f %s); INSPECT %% %.1f vs %.1f; CATCH_UP %% %.1f vs %.1f%n",
                        i, seenP[i] / runs, seenS[i] / runs, seenDropWorst[i], seenDropKey[i], inspP[i] / runs,
                        inspS[i] / runs, cP[i] / runs, cS[i] / runs);
            }
        }
    }
}
