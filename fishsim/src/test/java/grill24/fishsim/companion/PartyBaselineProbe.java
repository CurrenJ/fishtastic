package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;
import grill24.fishsim.harness.companion.OwnerScript;
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
 * of view time, or inspect each other? Run with -PsimStdout; -PsweepSeeds=N changes the seed count.
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class PartyBaselineProbe {

    static final int TICKS = 2400;
    /** Same range ClientCompanions uses for companion points of interest. */
    static final double COMPANION_RANGE = 16.0;
    /** Drawn-body half width per length, as CompanionRun measures school overlap. */
    static final double BODY_HALF_WIDTH = 0.12;

    private static int seeds() {
        return Integer.parseInt(System.getProperty("sweepSeeds", "8"));
    }

    record Spec(String species, Locomotion loco, GroundGait gait, BenthicGroup.Formation formation, float length,
                DriftStyle drift) {
        static Spec of(String species, Locomotion loco, float length) {
            return new Spec(species, loco, loco == Locomotion.BENTHIC ? GroundGait.CRAWL : null, null, length,
                    DriftStyle.BELL);
        }
    }

    record Party(String name, List<Spec> members) {}

    /** One stepped unit: a solo agent, a school or a ground formation. */
    static final class Unit {
        final PartyGrouping.Group group;
        final Spec spec;
        CompanionAgent solo;
        CompanionGroup school;
        BenthicGroup ground;

        Unit(PartyGrouping.Group group, Spec spec) {
            this.group = group;
            this.spec = spec;
        }

        void build(CompanionTunables t, CompanionWorld w) {
            switch (group.kind()) {
                case SCHOOL -> school = group.school(t, Temperament.BASELINE, w);
                case GROUND -> ground = group.ground(t, Temperament.BASELINE, w);
                default -> solo = new CompanionAgent(t, group.seed(), spec.loco, spec.length, Temperament.BASELINE,
                        spec.gait, spec.drift, w);
            }
        }

        void step(CompanionWorld w) {
            if (school != null) school.step(w);
            else if (ground != null) ground.step(w);
            else solo.step(w);
        }

        CompanionAgent leader() {
            return school != null ? school.leader() : ground != null ? ground.leader() : solo;
        }

        /** Every body: position, length, appear. */
        List<double[]> bodies() {
            List<double[]> out = new ArrayList<>();
            CompanionAgent l = leader();
            if (school != null) {
                add(out, l.position(), l.yaw(), l.pitch(), school.leaderLength(), l.appear());
                for (int i = 0; i < school.followerCount(); i++) {
                    CompanionGroup.Follower f = school.follower(i);
                    add(out, f.position(), f.yaw(), f.pitch(), f.length(), f.appear());
                }
            } else if (ground != null) {
                for (int i = 0; i < ground.count(); i++) {
                    CompanionAgent a = ground.member(i);
                    add(out, a.position(), a.yaw(), a.pitch(), ground.length(i), a.appear());
                }
            } else {
                add(out, l.position(), l.yaw(), l.pitch(), l.length(), l.appear());
            }
            return out;
        }

        List<CompanionAgent> agents() {
            List<CompanionAgent> out = new ArrayList<>();
            if (ground != null) for (int i = 0; i < ground.count(); i++) out.add(ground.member(i));
            else out.add(leader());
            return out;
        }

        private static void add(List<double[]> out, Vec p, float yaw, float pitch, float len, float appear) {
            Vec d = Vec.fromYawPitch(yaw, pitch).scale(0.4 * len); // CompanionRun.BODY_HALF_LENGTH
            Vec a = p.sub(d), b = p.add(d);
            out.add(new double[]{a.x(), a.y(), a.z(), b.x(), b.y(), b.z(), len, appear, p.x(), p.y(), p.z()});
        }
    }

    /** Per-unit tallies over one run. */
    static final class Tally {
        long ticks, seen, inspect, catchUp, showOff, fades, riding;

        void tick(CompanionAgent a, OwnerState o) {
            ticks++;
            if (a.appear() >= 1f && Visibility.visible(o, a.position(), a.voxels())) seen++;
            String g = a.goal();
            if ("INSPECT".equals(g)) inspect++;
            if ("CATCH_UP".equals(g)) catchUp++;
            if ("SHOW_OFF".equals(g)) showOff++;
            if (a.riding()) riding++;
        }

        double pct(long n) { return ticks == 0 ? 0 : 100.0 * n / ticks; }
    }

    static List<Unit> buildUnits(Party p, long seed) {
        List<PartyGrouping.Member> members = new ArrayList<>();
        for (int s = 0; s < p.members.size(); s++) {
            Spec m = p.members.get(s);
            // Distinct stacks per slot, as three caught fish would be.
            members.add(new PartyGrouping.Member(m.species, m.loco, m.gait, m.formation, m.length,
                    seed * 31 + s * 7919L, s));
        }
        List<Unit> units = new ArrayList<>();
        for (PartyGrouping.Group g : PartyGrouping.group(members)) {
            units.add(new Unit(g, p.members.get(g.leaderSlot())));
        }
        return units;
    }

    /** Party-run measures, one script and seed. */
    record Result(double[] seenParty, double[] seenSolo, double[] inspParty, double[] inspSolo,
                  double[] catchParty, double[] catchSolo, double drawnOverlapPct, double nearPct, double minSep,
                  double twoInViewPct, double threeInViewPct, int perchClashTicks, int unitFadesParty,
                  int unitFadesSolo, double partyInspectOfMates) {}

    static Result run(Party p, String script, long seed) {
        CompanionTunables t = CompanionTunables.defaults();
        List<Unit> units = buildUnits(p, seed);
        int n = units.size();

        // The party, stepped together.
        OwnerScript os = OwnerScript.named(script, seed);
        CompanionWorld base = os.nextWorld();
        for (Unit u : units) u.build(t, base);
        if ("homes".equals(System.getProperty("tweakB"))) assignHomes(units);
        Tally[] party = new Tally[n];
        for (int i = 0; i < n; i++) party[i] = new Tally();
        long together = 0, drawn = 0, near = 0, two = 0, three = 0;
        int clash = 0;
        double minSep = Double.MAX_VALUE;
        long mateInspect = 0;
        long[] fadesBefore = new long[n];
        int[] fadeCount = new int[n];
        CompanionAgent.Fade[] prevFade = new CompanionAgent.Fade[n];
        for (int k = 0; k < TICKS; k++) {
            base = os.nextWorld();
            // Snapshot of every unit's leader as last tick left it (no order dependence).
            PointOfInterest[] asPoint = new PointOfInterest[n];
            for (int i = 0; i < n; i++) {
                CompanionAgent l = units.get(i).leader();
                if (l.appear() >= 1f) {
                    asPoint[i] = new PointOfInterest(PointOfInterest.Kind.COMPANION, -1 - i, l.position(),
                            l.velocity(), l.radius());
                }
            }
            for (int i = 0; i < n; i++) {
                List<PointOfInterest> pts = new ArrayList<>(base.points());
                for (int j = 0; j < n; j++) {
                    if (j == i || asPoint[j] == null) continue;
                    if (asPoint[j].pos().distanceTo(units.get(i).leader().position()) > COMPANION_RANGE) continue;
                    pts.add(asPoint[j]);
                }
                units.get(i).step(new CompanionWorld(base.owner(), base.blocks(), pts, base.held(), base.hurt()));
            }
            OwnerState o = base.owner();
            int inView = 0;
            for (int i = 0; i < n; i++) {
                CompanionAgent l = units.get(i).leader();
                party[i].tick(l, o);
                if ("INSPECT".equals(l.goal()) && base.points().isEmpty() && base.held() == CompanionWorld.Held.NONE) mateInspect++;
                boolean anyIn = false;
                for (CompanionAgent a : units.get(i).agents()) {
                    if (a.appear() >= 1f && Visibility.visible(o, a.position(), a.voxels())) anyIn = true;
                }
                if (anyIn) inView++;
                if (prevFade[i] == CompanionAgent.Fade.NONE && l.fade() == CompanionAgent.Fade.OUT) fadeCount[i]++;
                prevFade[i] = l.fade();
            }
            if (inView >= 2) two++;
            if (inView >= 3) three++;
            // Perch clashes: two units riding the same slot.
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    for (CompanionAgent a : units.get(i).agents()) {
                        for (CompanionAgent b : units.get(j).agents()) {
                            if (a.riding() && b.riding() && a.perchSlot() == b.perchSlot()) clash++;
                        }
                    }
                }
            }
            // Body overlap between units (never within one; that's the group's own metric).
            boolean anyDrawn = false, anyNear = false, allIn = true;
            List<List<double[]>> bodies = new ArrayList<>();
            for (Unit u : units) bodies.add(u.bodies());
            for (List<double[]> bs : bodies) for (double[] b : bs) allIn &= b[7] >= 1f;
            if (allIn && n > 1) {
                together++;
                for (int i = 0; i < n; i++) {
                    for (int j = i + 1; j < n; j++) {
                        for (double[] a : bodies.get(i)) {
                            for (double[] b : bodies.get(j)) {
                                double sep = Math.sqrt(sq(a[8] - b[8]) + sq(a[9] - b[9]) + sq(a[10] - b[10]));
                                minSep = Math.min(minSep, sep);
                                double seg = segDist(a, b);
                                if (seg < BODY_HALF_WIDTH * (a[6] + b[6])) anyDrawn = true;
                                if (seg < 0.25) anyNear = true;
                            }
                        }
                    }
                }
                if (anyDrawn) drawn++;
                if (anyNear) near++;
            }
        }

        // Each unit alone, same script and seed, no party-mates.
        double[] seenSolo = new double[n], inspSolo = new double[n], catchSolo = new double[n];
        int fadesSolo = 0;
        for (int i = 0; i < n; i++) {
            OwnerScript so = OwnerScript.named(script, seed);
            CompanionWorld w = so.nextWorld();
            Unit alone = new Unit(units.get(i).group, units.get(i).spec);
            alone.build(t, w);
            Tally tl = new Tally();
            CompanionAgent.Fade pf = CompanionAgent.Fade.NONE;
            for (int k = 0; k < TICKS; k++) {
                w = so.nextWorld();
                alone.step(w);
                CompanionAgent l = alone.leader();
                tl.tick(l, w.owner());
                if (pf == CompanionAgent.Fade.NONE && l.fade() == CompanionAgent.Fade.OUT) fadesSolo++;
                pf = l.fade();
            }
            seenSolo[i] = tl.pct(tl.seen);
            inspSolo[i] = tl.pct(tl.inspect);
            catchSolo[i] = tl.pct(tl.catchUp);
        }
        double[] seenParty = new double[n], inspParty = new double[n], catchParty = new double[n];
        int fadesParty = 0;
        for (int i = 0; i < n; i++) {
            seenParty[i] = party[i].pct(party[i].seen);
            inspParty[i] = party[i].pct(party[i].inspect);
            catchParty[i] = party[i].pct(party[i].catchUp);
            fadesParty += fadeCount[i];
        }
        return new Result(seenParty, seenSolo, inspParty, inspSolo, catchParty, catchSolo,
                together == 0 ? 0 : 100.0 * drawn / together, together == 0 ? 0 : 100.0 * near / together,
                minSep == Double.MAX_VALUE ? -1 : minSep, 100.0 * two / TICKS, 100.0 * three / TICKS, clash,
                fadesParty, fadesSolo, 100.0 * mateInspect / (TICKS * (double) n));
    }

    /**
     * Candidate (-PtweakB=homes): the party gives every percher its own home perch, once, the way
     * BenthicGroup already does inside a formation. Anchored fish ask first (D5: head, then the
     * shoulders); the rest take what's left, left shoulder, right shoulder, head, back. Solo
     * shoulder-wander is left as it is, so this measures homes alone.
     */
    static void assignHomes(List<Unit> units) {
        java.util.EnumSet<PerchBody.Slot> free = java.util.EnumSet.of(PerchBody.Slot.HEAD,
                PerchBody.Slot.RIGHT_SHOULDER, PerchBody.Slot.LEFT_SHOULDER, PerchBody.Slot.BACK);
        List<CompanionAgent> anchored = new ArrayList<>(), others = new ArrayList<>();
        for (Unit u : units) {
            if (u.ground != null) {
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

    private static double sq(double v) { return v * v; }

    /** Closest distance between two segments a[0..2]-a[3..5] and b[0..2]-b[3..5]. */
    private static double segDist(double[] a, double[] b) {
        Vec p1 = new Vec(a[0], a[1], a[2]), q1 = new Vec(a[3], a[4], a[5]);
        Vec p2 = new Vec(b[0], b[1], b[2]), q2 = new Vec(b[3], b[4], b[5]);
        double best = Double.MAX_VALUE;
        for (int i = 0; i <= 8; i++) {
            Vec x = p1.add(q1.sub(p1).scale(i / 8.0));
            for (int j = 0; j <= 8; j++) {
                Vec y = p2.add(q2.sub(p2).scale(j / 8.0));
                best = Math.min(best, x.distanceTo(y));
            }
        }
        return best;
    }

    static final List<Party> PARTIES = List.of(
            new Party("3 free swimmers (40/30/25)", List.of(
                    Spec.of("a", Locomotion.FREE_SWIM, 0.40f), Spec.of("b", Locomotion.FREE_SWIM, 0.30f),
                    Spec.of("c", Locomotion.FREE_SWIM, 0.25f))),
            new Party("school pair + free swimmer", List.of(
                    Spec.of("blue", Locomotion.FREE_SWIM, 0.16f), Spec.of("blue", Locomotion.FREE_SWIM, 0.24f),
                    Spec.of("x", Locomotion.FREE_SWIM, 0.40f))),
            new Party("school pair + crab", List.of(
                    Spec.of("blue", Locomotion.FREE_SWIM, 0.16f), Spec.of("blue", Locomotion.FREE_SWIM, 0.24f),
                    Spec.of("crab", Locomotion.BENTHIC, 0.30f))),
            new Party("crab + drifter + glider", List.of(
                    Spec.of("crab", Locomotion.BENTHIC, 0.30f), Spec.of("jelly", Locomotion.DRIFT, 0.35f),
                    Spec.of("ray", Locomotion.GLIDE, 0.70f))),
            new Party("starfish pair + crab", List.of(
                    Spec.of("star", Locomotion.BENTHIC, 0.30f), Spec.of("star", Locomotion.BENTHIC, 0.45f),
                    Spec.of("crab", Locomotion.BENTHIC, 0.30f))),
            new Party("crab + octopus + slug", List.of(
                    Spec.of("crab", Locomotion.BENTHIC, 0.30f), Spec.of("octo", Locomotion.BENTHIC, 0.40f),
                    Spec.of("slug", Locomotion.BENTHIC, 0.12f))),
            new Party("3 anchored", List.of(
                    Spec.of("eel1", Locomotion.ANCHORED, 0.45f), Spec.of("eel2", Locomotion.ANCHORED, 0.45f),
                    Spec.of("eel3", Locomotion.ANCHORED, 0.45f))),
            new Party("2 drifters + glider", List.of(
                    Spec.of("j1", Locomotion.DRIFT, 0.35f),
                    new Spec("j2", Locomotion.DRIFT, null, null, 0.35f, DriftStyle.JET),
                    Spec.of("ray", Locomotion.GLIDE, 0.70f))));

    static final List<String> SCRIPTS;
    static {
        List<String> s = new ArrayList<>(OwnerScript.NAMES);
        s.addAll(MixedGroupProbe.GROUND_SCRIPTS);
        SCRIPTS = List.copyOf(s);
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
        for (String script : SCRIPTS) {
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
        for (Party p : PARTIES) {
            if (only != null && !p.name.contains(only)) continue;
            int runs = 0, clashRuns = 0, clashTicks = 0, fadesP = 0, fadesS = 0;
            double drawn = 0, drawnWorst = 0, near = 0, two = 0, three = 0, threeWorst = 0, mates = 0, matesWorst = 0;
            double minSep = Double.MAX_VALUE;
            String drawnWorstKey = "", threeWorstKey = "", matesWorstKey = "";
            int units = -1;
            double[] seenP = null, seenS = null, inspP = null, inspS = null, cP = null, cS = null;
            double[] seenDropWorst = null;
            String[] seenDropKey = null;
            for (String script : SCRIPTS) {
                for (long seed = 1; seed <= seeds(); seed++) {
                    Result r = run(p, script, seed);
                    String key = script + "#" + seed;
                    if (units < 0) {
                        units = r.seenParty.length;
                        seenP = new double[units]; seenS = new double[units]; inspP = new double[units];
                        inspS = new double[units]; cP = new double[units]; cS = new double[units];
                        seenDropWorst = new double[units]; seenDropKey = new String[units];
                    }
                    runs++;
                    drawn += r.drawnOverlapPct;
                    if (r.drawnOverlapPct > drawnWorst) { drawnWorst = r.drawnOverlapPct; drawnWorstKey = key; }
                    near += r.nearPct;
                    if (r.minSep >= 0) minSep = Math.min(minSep, r.minSep);
                    two += r.twoInViewPct;
                    three += r.threeInViewPct;
                    if (r.threeInViewPct > threeWorst) { threeWorst = r.threeInViewPct; threeWorstKey = key; }
                    if (r.perchClashTicks > 0) clashRuns++;
                    clashTicks += r.perchClashTicks;
                    fadesP += r.unitFadesParty;
                    fadesS += r.unitFadesSolo;
                    mates += r.partyInspectOfMates;
                    if (r.partyInspectOfMates > matesWorst) { matesWorst = r.partyInspectOfMates; matesWorstKey = key; }
                    for (int i = 0; i < units; i++) {
                        seenP[i] += r.seenParty[i]; seenS[i] += r.seenSolo[i];
                        inspP[i] += r.inspParty[i]; inspS[i] += r.inspSolo[i];
                        cP[i] += r.catchParty[i]; cS[i] += r.catchSolo[i];
                        double drop = r.seenSolo[i] - r.seenParty[i];
                        if (drop > seenDropWorst[i]) { seenDropWorst[i] = drop; seenDropKey[i] = key; }
                    }
                }
            }
            System.out.printf(Locale.ROOT, "%nPARTY %s  (%d units, %d runs)%n", p.name, units, runs);
            System.out.printf(Locale.ROOT,
                    "  inter-unit drawn overlap %%: mean %.2f | worst %.2f (%s); within 0.25 b %%: %.2f; min sep %.3f b%n",
                    drawn / runs, drawnWorst, drawnWorstKey, near / runs, minSep);
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
