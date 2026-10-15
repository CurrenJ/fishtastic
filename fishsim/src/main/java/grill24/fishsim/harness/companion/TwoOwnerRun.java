package grill24.fishsim.harness.companion;

import grill24.fishsim.companion.CompanionAgent;
import grill24.fishsim.companion.CompanionTunables;
import grill24.fishsim.companion.CompanionWorld;
import grill24.fishsim.companion.OwnerState;
import grill24.fishsim.companion.PartyGrouping;
import grill24.fishsim.companion.PartyLayer;
import grill24.fishsim.companion.PointOfInterest;
import grill24.fishsim.companion.Temperament;
import grill24.fishsim.companion.Vec;
import grill24.fishsim.companion.Visibility;
import grill24.fishsim.core.Locomotion;

import java.util.ArrayList;
import java.util.List;

/**
 * Two owners and their two parties in one world (docs/fish-companions-party.md §28.3, step 3a-0):
 * the headless twin of one client running two players' parties. {@link Sim} steps them the way
 * {@code ClientCompanions.tick} does, and {@link #measure} runs the cross-party guard rails. There
 * are no scenes yet; with none, each party sees the other only as the game shows it.
 *
 * <p>What a party sees of the other, as in game:
 * <ul>
 *   <li>each other unit's leader as a {@code COMPANION} point, within {@link #COMPANION_RANGE} of
 *       the seeing unit's leader, id {@link #pointId} (owner entity id and lowest slot);</li>
 *   <li>the other owner's feet as a {@code PLAYER} point within {@link #PLAYER_RANGE} of its own
 *       owner's feet (the adapter's {@code distanceToSqr > 16});</li>
 *   <li>points in the adapter's order: the script's own, then companions, then players.</li>
 * </ul>
 * Both owners move first, then party A is stepped against B as it stood, then B against A as it
 * now stands: the game steps parties one after another in its map's order, so one always sees the
 * other's fresh positions. Here the order is fixed, A first.
 */
public final class TwoOwnerRun {
    private TwoOwnerRun() {}

    /** The owners' entity ids, as the game would number two players. */
    public static final int OWNER_A = 1, OWNER_B = 2;
    /** ClientCompanions.COMPANION_RANGE. */
    public static final double COMPANION_RANGE = 16.0;
    /** CompanionWorldAdapter.points: another player within 4 blocks of the owner. */
    public static final double PLAYER_RANGE = 4.0;
    /** A standing player's half width (bbWidth / 2). */
    public static final double PLAYER_RADIUS = 0.3;
    /** A standing player's height, for fish-to-owner contact. */
    public static final double PLAYER_HEIGHT = 1.8;

    /** ClientCompanions.pointId for a real owner (party spec §5.2). */
    public static int pointId(int ownerId, int lowestSlot) {
        return -1 - (ownerId * PartyGrouping.SLOTS + lowestSlot);
    }

    /** Two parties that meet: the cross-player cases §28 names. */
    public record Pair(String name, PartyRun.Party a, PartyRun.Party b) {}

    private static PartyRun.Party party(String name, PartyRun.Spec... members) {
        return new PartyRun.Party(name, List.of(members));
    }

    /** The representative pairs (§28.2): what plays G1, what plays G2, and what plays nothing. */
    public static final List<Pair> PAIRS = List.of(
            // G2: two different free swimmers (the two-client spike's bluegill and discus).
            new Pair("two free swimmers",
                    party("bluegill", PartyRun.Spec.of("blue", Locomotion.FREE_SWIM, 0.20f)),
                    party("discus", PartyRun.Spec.of("discus", Locomotion.FREE_SWIM, 0.20f))),
            // G1: one species, one each.
            new Pair("same species",
                    party("bluegill 20", PartyRun.Spec.of("blue", Locomotion.FREE_SWIM, 0.20f)),
                    party("bluegill 24", PartyRun.Spec.of("blue", Locomotion.FREE_SWIM, 0.24f))),
            // G1 led by a school against a solo of its species (D26: followers keep formation).
            new Pair("school vs solo",
                    party("school of 3", PartyRun.Spec.of("blue", Locomotion.FREE_SWIM, 0.16f),
                            PartyRun.Spec.of("blue", Locomotion.FREE_SWIM, 0.20f),
                            PartyRun.Spec.of("blue", Locomotion.FREE_SWIM, 0.24f)),
                    party("bluegill", PartyRun.Spec.of("blue", Locomotion.FREE_SWIM, 0.20f))),
            // D28: a party with a predator scene of its own meets a free swimmer.
            new Pair("predator party vs swimmer",
                    party("predator + 2 prey", PartyRun.Spec.of("pike", Locomotion.FREE_SWIM, 0.50f).asPredator(),
                            PartyRun.Spec.of("blue", Locomotion.FREE_SWIM, 0.20f),
                            PartyRun.Spec.of("perch", Locomotion.FREE_SWIM, 0.25f)),
                    party("discus", PartyRun.Spec.of("discus", Locomotion.FREE_SWIM, 0.20f))),
            // D25: same-species ground walkers play nothing.
            new Pair("crab vs crab",
                    party("crab", PartyRun.Spec.of("crab", Locomotion.BENTHIC, 0.30f)),
                    party("crab", PartyRun.Spec.of("crab", Locomotion.BENTHIC, 0.30f))),
            // Two full mixed parties: perchers, ground walkers and swimmers on both sides.
            new Pair("mixed vs mixed",
                    party("school pair + crab", PartyRun.Spec.of("blue", Locomotion.FREE_SWIM, 0.16f),
                            PartyRun.Spec.of("blue", Locomotion.FREE_SWIM, 0.24f),
                            PartyRun.Spec.of("crab", Locomotion.BENTHIC, 0.30f)),
                    party("3 free swimmers", PartyRun.Spec.of("a", Locomotion.FREE_SWIM, 0.40f),
                            PartyRun.Spec.of("b", Locomotion.FREE_SWIM, 0.30f),
                            PartyRun.Spec.of("c", Locomotion.FREE_SWIM, 0.25f))));

    /** Party B's seed from the run's: its stacks must differ from A's even for the same species. */
    public static long seedB(long seed) {
        return seed ^ 0x9E3779B97F4A7C15L;
    }

    /** One owner, its script and its party. */
    public static final class Side {
        public final int ownerId;
        public final OwnerScript script;
        public final PartyLayer layer;
        /** This tick's world from the script alone (no other party in it). */
        private CompanionWorld base;
        /** The world each unit saw this tick, by unit index. */
        private final CompanionWorld[] seen;

        Side(int ownerId, OwnerScript script, PartyRun.Party party, long seed, CompanionTunables t, Temperament temperament) {
            this.ownerId = ownerId;
            this.script = script;
            this.base = script.nextWorld();
            List<PartyGrouping.Group> groups = party.groups(seed);
            this.layer = party.layer(t, temperament, groups, base, slot -> pointId(ownerId, slot));
            this.seen = new CompanionWorld[layer.size()];
            java.util.Arrays.fill(seen, base);
        }

        public OwnerState owner() { return base.owner(); }

        public CompanionWorld base() { return base; }

        /** The world unit {@code i} saw on the last step: the script's points plus the other party. */
        public CompanionWorld seen(int i) { return seen[i]; }

        /** Every body of the party, as PartyRun draws them. */
        public List<double[]> bodies() {
            List<double[]> out = new ArrayList<>();
            for (PartyLayer.Unit u : layer.units()) out.addAll(PartyRun.bodies(u));
            return out;
        }
    }

    /** The two parties, stepped one tick at a time. */
    public static final class Sim {
        public final Side a, b;

        public Sim(Pair pair, String script, long seed, CompanionTunables t, Temperament temperament) {
            OwnerScript[] owners = OwnerScript.pair(script, seed);
            a = new Side(OWNER_A, owners[0], pair.a(), seed, t, temperament);
            b = new Side(OWNER_B, owners[1], pair.b(), seedB(seed), t, temperament);
        }

        public void step() {
            a.base = a.script.nextWorld();
            b.base = b.script.nextWorld();
            stepSide(a, b);
            stepSide(b, a);
        }

        private static void stepSide(Side self, Side other) {
            int n = self.layer.size();
            List<PointOfInterest> fish = new ArrayList<>();
            for (PartyLayer.Unit u : other.layer.units()) {
                PointOfInterest p = u.asPoint(pointId(other.ownerId, u.group().lowestSlot()));
                if (p != null) fish.add(p);
            }
            OwnerState o = self.owner(), them = other.owner();
            PointOfInterest player = them.feet().distanceTo(o.feet()) > PLAYER_RANGE ? null
                    : new PointOfInterest(PointOfInterest.Kind.PLAYER, other.ownerId, them.feet(),
                            them.teleported() ? Vec.ZERO : them.velocity(), PLAYER_RADIUS);
            CompanionWorld base = self.base;
            for (int i = 0; i < n; i++) {
                Vec at = self.layer.unit(i).leader().position();
                List<PointOfInterest> pts = null;
                for (PointOfInterest p : fish) {
                    if (p.pos().distanceTo(at) > COMPANION_RANGE) continue;
                    if (pts == null) pts = new ArrayList<>(base.points());
                    pts.add(p);
                }
                if (player != null) {
                    if (pts == null) pts = new ArrayList<>(base.points());
                    pts.add(player);
                }
                self.seen[i] = pts == null ? base
                        : new CompanionWorld(base.owner(), base.blocks(), pts, base.held(), base.hurt());
            }
            self.layer.step(u -> self.seen[self.layer.units().indexOf(u)]);
        }
    }

    /**
     * One two-owner run's guard rails, each a level paired with a rate (episodes per minute):
     * <ul>
     *   <li>{@code crossOverlapPct}: ticks a body of A's party overlaps one of B's (drawn bodies),
     *       of the ticks every body of both is fully there; {@code crossNearPct} within
     *       {@link PartyRun#NEAR}; {@code crossMinSep} the closest centres;</li>
     *   <li>{@code ownerContactPct}: ticks a fish is inside the other owner's body;</li>
     *   <li>{@code seenA}/{@code seenB}: each unit leader's seen % by its own owner;</li>
     *   <li>{@code inspectPct}: unit-ticks spent inspecting with nothing scripted around: a
     *       party-mate or the other party's fish (§7's curiosity, which D20 and D27 leave as is);</li>
     *   <li>{@code fades}: fade-outs of every unit leader, both parties.</li>
     * </ul>
     */
    public record Result(double crossOverlapPct, double crossOverlapPerMin, double crossNearPct, double crossMinSep,
                         double ownerContactPct, double ownerContactPerMin, double[] seenA, double[] seenB,
                         double inspectPct, int fades) {}

    public static Result measure(Pair pair, String script, long seed, int ticks, CompanionTunables t,
                                 Temperament temperament) {
        Sim sim = new Sim(pair, script, seed, t, temperament);
        PartyRun.Tally[] ta = tallies(sim.a.layer.size()), tb = tallies(sim.b.layer.size());
        long together = 0, overlap = 0, near = 0, contact = 0, inspect = 0, unitTicks = 0;
        int overlapEpisodes = 0, contactEpisodes = 0;
        boolean wasOverlap = false, wasContact = false;
        double minSep = Double.MAX_VALUE;
        for (int k = 0; k < ticks; k++) {
            sim.step();
            for (Side s : List.of(sim.a, sim.b)) {
                PartyRun.Tally[] tally = s == sim.a ? ta : tb;
                for (int i = 0; i < s.layer.size(); i++) {
                    CompanionAgent l = s.layer.unit(i).leader();
                    tally[i].tick(l, s.owner());
                    unitTicks++;
                    if ("INSPECT".equals(l.goal()) && s.base().held() == CompanionWorld.Held.NONE
                            && s.base().points().isEmpty()) inspect++;
                }
            }
            List<double[]> ba = sim.a.bodies(), bb = sim.b.bodies();
            boolean allIn = true;
            for (double[] x : ba) allIn &= x[7] >= 1f;
            for (double[] x : bb) allIn &= x[7] >= 1f;
            boolean anyOverlap = false, anyNear = false;
            if (allIn) {
                together++;
                for (double[] x : ba) {
                    for (double[] y : bb) {
                        minSep = Math.min(minSep, Math.sqrt(sq(x[8] - y[8]) + sq(x[9] - y[9]) + sq(x[10] - y[10])));
                        double seg = PartyRun.segDist(x, y);
                        if (seg < PartyRun.BODY_HALF_WIDTH * (x[6] + y[6])) anyOverlap = true;
                        if (seg < PartyRun.NEAR) anyNear = true;
                    }
                }
                if (anyOverlap) overlap++;
                if (anyNear) near++;
            }
            if (anyOverlap && !wasOverlap) overlapEpisodes++;
            wasOverlap = anyOverlap;
            boolean anyContact = inOwner(ba, sim.b.owner()) || inOwner(bb, sim.a.owner());
            if (anyContact) contact++;
            if (anyContact && !wasContact) contactEpisodes++;
            wasContact = anyContact;
        }
        double minutes = ticks / (20.0 * 60.0);
        int fades = 0;
        for (PartyRun.Tally x : ta) fades += x.fades;
        for (PartyRun.Tally x : tb) fades += x.fades;
        return new Result(together == 0 ? 0 : 100.0 * overlap / together, overlapEpisodes / minutes,
                together == 0 ? 0 : 100.0 * near / together, minSep == Double.MAX_VALUE ? -1 : minSep,
                100.0 * contact / ticks, contactEpisodes / minutes, seen(ta), seen(tb),
                unitTicks == 0 ? 0 : 100.0 * inspect / unitTicks, fades);
    }

    /** A drawn body's centre inside a standing player's box, widened by the fish's half width. */
    private static boolean inOwner(List<double[]> bodies, OwnerState o) {
        Vec f = o.feet();
        for (double[] x : bodies) {
            if (x[7] < 1f) continue;
            double r = PLAYER_RADIUS + PartyRun.BODY_HALF_WIDTH * x[6];
            if (Math.abs(x[8] - f.x()) < r && Math.abs(x[10] - f.z()) < r
                    && x[9] > f.y() && x[9] < f.y() + PLAYER_HEIGHT) return true;
        }
        return false;
    }

    private static PartyRun.Tally[] tallies(int n) {
        PartyRun.Tally[] out = new PartyRun.Tally[n];
        for (int i = 0; i < n; i++) out[i] = new PartyRun.Tally();
        return out;
    }

    private static double[] seen(PartyRun.Tally[] t) {
        double[] out = new double[t.length];
        for (int i = 0; i < t.length; i++) out[i] = t[i].pct(t[i].seen);
        return out;
    }

    private static double sq(double v) { return v * v; }
}
