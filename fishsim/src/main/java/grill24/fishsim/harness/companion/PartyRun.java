package grill24.fishsim.harness.companion;

import grill24.fishsim.companion.BenthicGroup;
import grill24.fishsim.companion.CompanionAgent;
import grill24.fishsim.companion.CompanionGroup;
import grill24.fishsim.companion.CompanionTunables;
import grill24.fishsim.companion.CompanionWorld;
import grill24.fishsim.companion.DriftStyle;
import grill24.fishsim.companion.GroundGait;
import grill24.fishsim.companion.OwnerState;
import grill24.fishsim.companion.PartyGrouping;
import grill24.fishsim.companion.PartyLayer;
import grill24.fishsim.companion.Temperament;
import grill24.fishsim.companion.Vec;
import grill24.fishsim.companion.Visibility;
import grill24.fishsim.core.Locomotion;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The party metric suite (docs/fish-companions-party.md §8, Phase 2b): a party stepped through
 * {@link PartyLayer} on an owner script, measured against each of its units run alone on the same
 * script and seed. Each measure is a level paired with a rate (episodes per minute), so a change
 * that trades a long overlap for many short flickers still shows.
 *
 * <p>{@link #PARTIES} are the representative parties §8 names; {@code PartyBaselineProbe} prints
 * them, and the viewer's party classes are built from the same specs.
 */
public final class PartyRun {
    private PartyRun() {}

    /** Drawn-body half width per length, as CompanionRun measures school overlap. */
    public static final double BODY_HALF_WIDTH = 0.12;
    /** Drawn-body half length per length (CompanionRun.BODY_HALF_LENGTH). */
    public static final double BODY_HALF_LENGTH = 0.4;
    /** "Near" contact distance between two units' bodies, blocks. */
    public static final double NEAR = 0.25;

    /** One party member as a test party names it. */
    public record Spec(String species, Locomotion loco, GroundGait gait, BenthicGroup.Formation formation, float length,
                       DriftStyle drift, boolean predator) {
        public static Spec of(String species, Locomotion loco, float length) {
            return new Spec(species, loco, loco == Locomotion.BENTHIC ? GroundGait.CRAWL : null, null, length,
                    DriftStyle.BELL, false);
        }

        public Spec withDrift(DriftStyle d) { return new Spec(species, loco, gait, formation, length, d, predator); }

        /** A benthic that never walks the ground: a pure percher (gait null). */
        public Spec percher() { return new Spec(species, loco, null, formation, length, drift, predator); }

        /** A predator fish (D21: it never shoals with a non-predator). */
        public Spec asPredator() { return new Spec(species, loco, gait, formation, length, drift, true); }
    }

    public record Party(String name, List<Spec> members) {
        public Party {
            members = List.copyOf(members);
        }

        /** The party's members with distinct stacks per slot, as three caught fish would be. */
        public List<PartyGrouping.Member> sim(long seed) {
            List<PartyGrouping.Member> out = new ArrayList<>();
            for (int s = 0; s < members.size(); s++) {
                Spec m = members.get(s);
                out.add(new PartyGrouping.Member(m.species, m.loco, m.gait, m.formation, m.length,
                        seed * 31 + s * 7919L, s, m.predator));
            }
            return out;
        }

        public List<PartyGrouping.Group> groups(long seed) {
            return groups(seed, true);
        }

        /** {@code mixedShoal} off is the pre-D13 grouping, for A/B. */
        public List<PartyGrouping.Group> groups(long seed, boolean mixedShoal) {
            return PartyGrouping.group(sim(seed), mixedShoal);
        }

        /** Builds the party's layer; drift styles come from each unit leader's spec. */
        public PartyLayer build(CompanionTunables t, Temperament temperament, long seed, CompanionWorld world) {
            return build(t, temperament, seed, world, true);
        }

        public PartyLayer build(CompanionTunables t, Temperament temperament, long seed, CompanionWorld world,
                                boolean mixedShoal) {
            return layer(t, temperament, groups(seed, mixedShoal), world);
        }

        public PartyLayer layer(CompanionTunables t, Temperament temperament, List<PartyGrouping.Group> groups,
                                CompanionWorld world) {
            return new PartyLayer(t, groups, m -> temperament, m -> members.get(m.slot()).drift, world);
        }

        /** As above, with the game's owner-keyed mate ids (two parties in one world, §28.3). */
        public PartyLayer layer(CompanionTunables t, Temperament temperament, List<PartyGrouping.Group> groups,
                                CompanionWorld world, java.util.function.IntUnaryOperator mateId) {
            return new PartyLayer(t, groups, m -> temperament, m -> members.get(m.slot()).drift, world, mateId);
        }
    }

    /** The representative parties of §8 and the Phase 2 review. */
    public static final List<Party> PARTIES = List.of(
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
                    Spec.of("j2", Locomotion.DRIFT, 0.35f).withDrift(DriftStyle.JET),
                    Spec.of("ray", Locomotion.GLIDE, 0.70f))),
            // §8's "school pair + percher": a benthic that only rides the owner.
            new Party("school pair + percher", List.of(
                    Spec.of("blue", Locomotion.FREE_SWIM, 0.16f), Spec.of("blue", Locomotion.FREE_SWIM, 0.24f),
                    Spec.of("horse", Locomotion.BENTHIC, 0.20f).percher())),
            // D21: a predator swims apart from its prey's shoal.
            new Party("predator + 2 prey", List.of(
                    Spec.of("pike", Locomotion.FREE_SWIM, 0.50f).asPredator(), Spec.of("blue", Locomotion.FREE_SWIM, 0.20f),
                    Spec.of("perch", Locomotion.FREE_SWIM, 0.25f))));

    /** The scripts the suite runs: every swimmer owner script plus the ground scripts. */
    public static final List<String> SCRIPTS;
    static {
        List<String> s = new ArrayList<>(OwnerScript.NAMES);
        s.addAll(List.of("walk_flat", "walk_meander", "stairs_up_down", "long_idle_then_walk", "sprint_away",
                "group_passers", "walk_backward"));
        SCRIPTS = List.copyOf(s);
    }

    /** Per-unit tallies over one run. */
    static final class Tally {
        long ticks, seen, inspect, catchUp;
        int fades;
        CompanionAgent.Fade prevFade = CompanionAgent.Fade.NONE;

        void tick(CompanionAgent a, OwnerState o) {
            ticks++;
            if (a.appear() >= 1f && Visibility.visible(o, a.position(), a.voxels())) seen++;
            String g = a.goal();
            if ("INSPECT".equals(g)) inspect++;
            if ("CATCH_UP".equals(g)) catchUp++;
            if (prevFade == CompanionAgent.Fade.NONE && a.fade() == CompanionAgent.Fade.OUT) fades++;
            prevFade = a.fade();
        }

        double pct(long n) { return ticks == 0 ? 0 : 100.0 * n / ticks; }
    }

    /**
     * One party run's measures. Percentages are of the run's ticks except the overlap measures, which
     * are of the ticks every body is fully there; rates are episodes (false-to-true starts) per minute.
     * {@code drawnOverlapPct} counts body pairs of different units only; {@code allOverlapPct} counts
     * every pair of bodies in the party, inside a school too, so it compares fairly across groupings
     * (a mixed shoal against the same fish as separate units). {@code seenBySlot} is each body's seen
     * %, indexed by party slot (NaN for an empty slot): a follower is judged with its leader's voxels.
     */
    public record Result(double[] seenParty, double[] seenSolo, double[] inspParty, double[] inspSolo,
                         double[] catchParty, double[] catchSolo, double drawnOverlapPct, double drawnPerMin,
                         double nearPct, double minSep, double twoInViewPct, double threeInViewPct,
                         int perchClashTicks, double clashPerMin, int unitFadesParty, int unitFadesSolo,
                         double partyInspectOfMates, double allOverlapPct, double allPerMin, double[] seenBySlot,
                         double refusedPerMin) {
        public int units() { return seenParty.length; }
    }

    /** Runs {@code p} on {@code script}; {@code afterBuild} (may be null) is a test hook on the fresh layer. */
    public static Result measure(Party p, String script, long seed, int ticks, CompanionTunables t,
                                 Temperament temperament, Consumer<PartyLayer> afterBuild) {
        return measure(p, script, seed, ticks, t, temperament, true, afterBuild);
    }

    /** As above; {@code mixedShoal} off groups the party by the pre-D13 rule, for A/B. */
    public static Result measure(Party p, String script, long seed, int ticks, CompanionTunables t,
                                 Temperament temperament, boolean mixedShoal, Consumer<PartyLayer> afterBuild) {
        OwnerScript os = OwnerScript.named(script, seed);
        CompanionWorld base = os.nextWorld();
        List<PartyGrouping.Group> groups = p.groups(seed, mixedShoal);
        PartyLayer layer = p.layer(t, temperament, groups, base);
        if (afterBuild != null) afterBuild.accept(layer);
        int n = layer.size();
        Tally[] party = new Tally[n];
        for (int i = 0; i < n; i++) party[i] = new Tally();
        long together = 0, drawn = 0, near = 0, two = 0, three = 0, mateInspect = 0;
        long bodiesIn = 0, allDrawn = 0;
        long[] slotSeen = new long[PartyGrouping.SLOTS];
        int clash = 0, drawnEpisodes = 0, clashEpisodes = 0, allEpisodes = 0;
        boolean wasDrawn = false, wasClash = false, wasAll = false;
        double minSep = Double.MAX_VALUE;
        for (int k = 0; k < ticks; k++) {
            base = os.nextWorld();
            layer.step(base);
            OwnerState o = base.owner();
            int inView = 0;
            for (int i = 0; i < n; i++) {
                PartyLayer.Unit u = layer.unit(i);
                CompanionAgent l = u.leader();
                party[i].tick(l, o);
                if ("INSPECT".equals(l.goal()) && base.points().isEmpty() && base.held() == CompanionWorld.Held.NONE)
                    mateInspect++;
                for (CompanionAgent a : u.agents()) {
                    if (a.appear() >= 1f && Visibility.visible(o, a.position(), a.voxels())) {
                        inView++;
                        break;
                    }
                }
            }
            if (inView >= 2) two++;
            if (inView >= 3) three++;
            // Perch clashes: two units riding the same slot.
            int clashNow = 0;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    for (CompanionAgent a : layer.unit(i).agents()) {
                        for (CompanionAgent b : layer.unit(j).agents()) {
                            if (a.riding() && b.riding() && a.perchSlot() == b.perchSlot()) clashNow++;
                        }
                    }
                }
            }
            clash += clashNow;
            if (clashNow > 0 && !wasClash) clashEpisodes++;
            wasClash = clashNow > 0;
            // Body overlap between units (never within one; that's the group's own metric).
            List<List<double[]>> bodies = new ArrayList<>(n);
            boolean allIn = true;
            for (PartyLayer.Unit u : layer.units()) {
                List<double[]> bs = bodies(u);
                for (double[] b : bs) allIn &= b[7] >= 1f;
                bodies.add(bs);
            }
            boolean anyDrawn = false;
            if (allIn && n > 1) {
                together++;
                boolean anyNear = false;
                for (int i = 0; i < n; i++) {
                    for (int j = i + 1; j < n; j++) {
                        for (double[] a : bodies.get(i)) {
                            for (double[] b : bodies.get(j)) {
                                double sep = Math.sqrt(sq(a[8] - b[8]) + sq(a[9] - b[9]) + sq(a[10] - b[10]));
                                minSep = Math.min(minSep, sep);
                                double seg = segDist(a, b);
                                if (seg < BODY_HALF_WIDTH * (a[6] + b[6])) anyDrawn = true;
                                if (seg < NEAR) anyNear = true;
                            }
                        }
                    }
                }
                if (anyDrawn) drawn++;
                if (anyNear) near++;
            }
            if (anyDrawn && !wasDrawn) drawnEpisodes++;
            wasDrawn = anyDrawn;
            for (int i = 0; i < n; i++) {
                PartyLayer.Unit u = layer.unit(i);
                int[] slots = u.group().slots();
                List<double[]> bs = bodies.get(i);
                for (int b = 0; b < bs.size(); b++) {
                    double[] body = bs.get(b);
                    CompanionAgent eye = u.ground() != null ? u.ground().member(b) : u.leader();
                    if (body[7] >= 1f && Visibility.visible(o, new Vec(body[8], body[9], body[10]), eye.voxels()))
                        slotSeen[slots[b]]++;
                }
            }
            // Every pair of bodies, whichever units they belong to.
            List<double[]> flat = new ArrayList<>();
            for (List<double[]> bs : bodies) flat.addAll(bs);
            boolean anyAll = false;
            if (allIn && flat.size() > 1) {
                bodiesIn++;
                for (int i = 0; i < flat.size() && !anyAll; i++) {
                    for (int j = i + 1; j < flat.size(); j++) {
                        double[] a = flat.get(i), b = flat.get(j);
                        if (segDist(a, b) < BODY_HALF_WIDTH * (a[6] + b[6])) {
                            anyAll = true;
                            break;
                        }
                    }
                }
                if (anyAll) allDrawn++;
            }
            if (anyAll && !wasAll) allEpisodes++;
            wasAll = anyAll;
        }

        // Each unit alone, same script and seed, no party-mates.
        double[] seenSolo = new double[n], inspSolo = new double[n], catchSolo = new double[n];
        int fadesSolo = 0;
        for (int i = 0; i < n; i++) {
            OwnerScript so = OwnerScript.named(script, seed);
            CompanionWorld w = so.nextWorld();
            PartyLayer alone = p.layer(t, temperament, List.of(groups.get(i)), w);
            Tally tl = new Tally();
            for (int k = 0; k < ticks; k++) {
                w = so.nextWorld();
                alone.step(w);
                tl.tick(alone.unit(0).leader(), w.owner());
            }
            seenSolo[i] = tl.pct(tl.seen);
            inspSolo[i] = tl.pct(tl.inspect);
            catchSolo[i] = tl.pct(tl.catchUp);
            fadesSolo += tl.fades;
        }
        double[] seenParty = new double[n], inspParty = new double[n], catchParty = new double[n];
        int fadesParty = 0;
        for (int i = 0; i < n; i++) {
            seenParty[i] = party[i].pct(party[i].seen);
            inspParty[i] = party[i].pct(party[i].inspect);
            catchParty[i] = party[i].pct(party[i].catchUp);
            fadesParty += party[i].fades;
        }
        double minutes = ticks / (20.0 * 60.0);
        int refused = 0;
        for (PartyLayer.Unit u : layer.units()) for (CompanionAgent a : u.agents()) refused += a.leaseRefusals();
        return new Result(seenParty, seenSolo, inspParty, inspSolo, catchParty, catchSolo,
                together == 0 ? 0 : 100.0 * drawn / together, drawnEpisodes / minutes,
                together == 0 ? 0 : 100.0 * near / together, minSep == Double.MAX_VALUE ? -1 : minSep,
                100.0 * two / ticks, 100.0 * three / ticks, clash, clashEpisodes / minutes, fadesParty, fadesSolo,
                100.0 * mateInspect / (ticks * (double) n), bodiesIn == 0 ? 0 : 100.0 * allDrawn / bodiesIn,
                allEpisodes / minutes, seenBySlot(groups, slotSeen, ticks), refused / minutes);
    }

    private static double[] seenBySlot(List<PartyGrouping.Group> groups, long[] seen, int ticks) {
        double[] out = new double[PartyGrouping.SLOTS];
        java.util.Arrays.fill(out, Double.NaN);
        for (PartyGrouping.Group g : groups) for (int s : g.slots()) out[s] = 100.0 * seen[s] / ticks;
        return out;
    }

    /** Every body of a unit as a drawn segment: ends, length, appear, centre (slot order of {@code group().slots()}). */
    static List<double[]> bodies(PartyLayer.Unit u) {
        List<double[]> out = new ArrayList<>();
        CompanionAgent l = u.leader();
        CompanionGroup school = u.school();
        BenthicGroup ground = u.ground();
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

    private static void add(List<double[]> out, Vec p, float yaw, float pitch, float len, float appear) {
        Vec d = Vec.fromYawPitch(yaw, pitch).scale(BODY_HALF_LENGTH * len);
        Vec a = p.sub(d), b = p.add(d);
        out.add(new double[]{a.x(), a.y(), a.z(), b.x(), b.y(), b.z(), len, appear, p.x(), p.y(), p.z()});
    }

    private static double sq(double v) { return v * v; }

    /** Closest distance between two segments a[0..2]-a[3..5] and b[0..2]-b[3..5]. */
    static double segDist(double[] a, double[] b) {
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
}
