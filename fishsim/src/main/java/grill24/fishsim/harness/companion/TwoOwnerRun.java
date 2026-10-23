package grill24.fishsim.harness.companion;

import grill24.fishsim.companion.CompanionAgent;
import grill24.fishsim.companion.CompanionTunables;
import grill24.fishsim.companion.CompanionWorld;
import grill24.fishsim.companion.CrossScene;
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
 * Two owners and their two parties in one world (docs/fish-companions.md, step 3a-0):
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
        /** This side's cross-player scene and the unit playing it, or null. */
        private CrossScene scene;
        private PartyLayer.Unit sceneUnit;

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

        /** This side's cross-player scene (whether or not it is still on), or null. */
        public CrossScene scene() { return scene; }

        public PartyLayer.Unit sceneUnit() { return sceneUnit; }

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

    /** The server's scene range (CompanionSceneManager.RANGE), its G2 floor, and its cooldown, as the scheduler uses them. */
    public static final double SCENE_RANGE = 6.0, G2_MIN_OWNER_DISTANCE = 2.5;
    public static final int SCENE_COOLDOWN_TICKS = 1200;

    /** The two parties, stepped one tick at a time. */
    public static final class Sim {
        public final Side a, b;
        private int tick;
        /** -1 with no scene yet; else the tick the latest began. */
        private int sceneStart = -1;
        private int busyUntil;
        private boolean autoScenes;
        private int scenesStarted;
        /** Ticks the given owner reaches this view late (a remote player, interpolated), 0 for exact. */
        private int lagA, lagB;
        private final java.util.ArrayDeque<CompanionWorld> queueA = new java.util.ArrayDeque<>(),
                queueB = new java.util.ArrayDeque<>();
        /** Ticks to start a scene at regardless of where the owners stand here (the server's, shared by every view). */
        private java.util.Set<Integer> sceneTicks = java.util.Set.of();

        public Sim(Pair pair, String script, long seed, CompanionTunables t, Temperament temperament) {
            OwnerScript[] owners = OwnerScript.pair(script, seed);
            a = new Side(OWNER_A, owners[0], pair.a(), seed, t, temperament);
            b = new Side(OWNER_B, owners[1], pair.b(), seedB(seed), t, temperament);
        }

        /**
         * Schedule scenes as the server does (CompanionSceneManager): with the owners within
         * {@link #SCENE_RANGE}, the best pairing plays, then the pair sits out the scene and a
         * cooldown. Off (the default) no scene ever starts, so the fingerprints hold.
         */
        public Sim autoScenes(boolean on) {
            autoScenes = on;
            return this;
        }

        public int tick() { return tick; }

        /**
         * One client's view (§28.3, D29's divergence probe): owner A reaches it {@code lagA} ticks
         * late and owner B {@code lagB} — client A sees its own player exactly and the other a few
         * ticks behind, as an interpolated remote player.
         */
        public Sim lag(int lagA, int lagB) {
            this.lagA = lagA;
            this.lagB = lagB;
            return this;
        }

        /** Start a scene at exactly these ticks (the server's schedule, the same in every view). */
        public Sim sceneAt(java.util.Set<Integer> ticks) {
            sceneTicks = ticks;
            return this;
        }

        public int scenesStarted() { return scenesStarted; }

        /** A scene on either side now. */
        public boolean sceneOn() {
            return (a.scene != null && a.scene.active()) || (b.scene != null && b.scene.active());
        }

        public void step() {
            a.base = lagged(queueA, a.script.nextWorld(), lagA);
            b.base = lagged(queueB, b.script.nextWorld(), lagB);
            if (sceneTicks.contains(tick)) startScene();
            if (autoScenes && tick >= busyUntil && !sceneOn()
                    && a.owner().feet().distanceTo(b.owner().feet()) <= SCENE_RANGE) startScene();
            stepSide(a, b, this);
            stepSide(b, a, this);
            tick++;
        }

        /**
         * Starts the best scene the server would choose now (§28.7: free swimmers only; one species
         * plays the ring, otherwise the eight with the owners 2.5 blocks apart), on both sides at
         * once. Returns false when no pairing qualifies.
         */
        public boolean startScene() {
            PartyLayer.Unit bestA = null, bestB = null;
            CrossScene.Kind best = null;
            double apart = a.owner().feet().sub(b.owner().feet()).horizontalLength();
            for (PartyLayer.Unit ua : a.layer.units()) {
                for (PartyLayer.Unit ub : b.layer.units()) {
                    if (ua.group().leader().locomotion() != Locomotion.FREE_SWIM
                            || ub.group().leader().locomotion() != Locomotion.FREE_SWIM) continue;
                    String sa = oneSpecies(ua.group()), sb = oneSpecies(ub.group());
                    CrossScene.Kind k = sa != null && sa.equals(sb) ? CrossScene.Kind.RING
                            : apart >= G2_MIN_OWNER_DISTANCE ? CrossScene.Kind.EIGHT : null;
                    if (k == null || (best != null && k.ordinal() >= best.ordinal())) continue;
                    best = k;
                    bestA = ua;
                    bestB = ub;
                }
            }
            if (best == null) return false;
            a.scene = new CrossScene(best, 0);
            b.scene = new CrossScene(best, 1);
            a.sceneUnit = a.layer.crossScene(bestA.group().leader().slot(), a.scene);
            b.sceneUnit = b.layer.crossScene(bestB.group().leader().slot(), b.scene);
            sceneStart = tick;
            busyUntil = tick + best.durationTicks + SCENE_COOLDOWN_TICKS;
            scenesStarted++;
            return true;
        }

        /** The world from {@code lag} ticks ago (the first one until the queue fills). */
        private static CompanionWorld lagged(java.util.ArrayDeque<CompanionWorld> q, CompanionWorld now, int lag) {
            if (lag == 0) return now;
            q.addLast(now);
            return q.size() > lag ? q.removeFirst() : q.peekFirst();
        }

        private static String oneSpecies(PartyGrouping.Group g) {
            String s = g.leader().species();
            for (PartyGrouping.Member m : g.members()) if (!m.species().equals(s)) return null;
            return s;
        }

        private static void stepSide(Side self, Side other, Sim sim) {
            if (self.scene != null && self.scene.active() || self.scene != null && self.scene.ticks() < 0) {
                // As a client feeds it: the shared clock, both owners in packet order (A is the
                // first), and the partner as this client's own sim of the other party has it.
                CompanionAgent partner = other.sceneUnit == null ? null : other.sceneUnit.leader();
                self.scene.update(sim.tick - sim.sceneStart, sim.a.owner().feet(), sim.b.owner().feet(),
                        partner != null && partner.appear() >= 1f ? partner.position() : null);
            }
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
                        : base.withPoints(pts);
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

    /**
     * Cross-player scenes on a two-owner run (§28.3), scheduled as the server does. Levels with
     * their rates where they're events:
     * <ul>
     *   <li>{@code joinSeconds}: from the end of the greeting to the scene leader first within
     *       {@link #JOINED} of its clock point (mean over joins); {@code joinedPct} of scene-sides
     *       that ever join;</li>
     *   <li>{@code pathError}: mean distance from the clock point once joined, blocks;</li>
     *   <li>{@code antipodalDeg}: G1 only, both on the path: how far the two fish are from
     *       opposite sides of the ring, degrees;</li>
     *   <li>{@code contactPct}/{@code contactPerMin}: scene ticks with a body of one party over a
     *       body of the other; {@code ownerContactPct}: a scene fish inside either owner;</li>
     *   <li>{@code seenPct}: scene leaders seen by their own owner during scene ticks;</li>
     *   <li>{@code peakTurnDeg}: the largest yaw change in one tick of a scene leader on the path
     *       (the cap is 7), and {@code atTurnCapPct} of path ticks within half a degree of it;</li>
     *   <li>{@code ends}: why each side's scene ended, and {@code left}: why a fish left early.</li>
     * </ul>
     */
    public record SceneResult(int scenes, double sceneTicks, double joinSeconds, double joinedPct, double pathError,
                              double antipodalDeg, double contactPct, double contactPerMin, double ownerContactPct,
                              double seenPct, double peakTurnDeg, double atTurnCapPct, java.util.Map<String, Integer> ends,
                              java.util.Map<String, Integer> left) {}

    /** A yaw change in one tick this near the swimmers' cap (7°) counts as turning flat out. */
    static final double TURN_CAP_NEAR = 6.5;

    /** A scene leader this close to its clock point has joined the path, blocks. */
    public static final double JOINED = 0.5;

    public static SceneResult measureScenes(Pair pair, String script, long seed, int ticks, CompanionTunables t,
                                            Temperament temperament) {
        Sim sim = new Sim(pair, script, seed, t, temperament).autoScenes(true);
        long atCap = 0;
        long sceneTicks = 0, contact = 0, ownerContact = 0, seen = 0, seenTicks = 0, pathTicks = 0, antiTicks = 0;
        double pathErr = 0, anti = 0, peakTurn = 0, joinSum = 0;
        int joins = 0, sides = 0, contactEpisodes = 0;
        boolean wasContact = false;
        java.util.Map<String, Integer> ends = new java.util.TreeMap<>(), left = new java.util.TreeMap<>();
        CrossScene lastA = null;
        List<CrossScene> history = new ArrayList<>();
        List<String> leftHistory = new ArrayList<>();
        CompanionAgent[] lastLeads = new CompanionAgent[2];
        boolean[] joined = new boolean[2], leftRecorded = new boolean[2];
        float[] prevYaw = new float[2];
        for (int k = 0; k < ticks; k++) {
            sim.step();
            CrossScene sa = sim.a.scene(), sb = sim.b.scene();
            if (sa != lastA && sa != null) {
                lastA = sa;
                leftRecorded[0] = leftRecorded[1] = false;
                history.add(sa);
                history.add(sb);
                lastLeads[0] = sim.a.sceneUnit() == null ? null : sim.a.sceneUnit().leader();
                lastLeads[1] = sim.b.sceneUnit() == null ? null : sim.b.sceneUnit().leader();
                joined[0] = joined[1] = false;
                sides += 2;
                prevYaw[0] = prevYaw[1] = Float.NaN;
            }
            boolean on = sim.sceneOn();
            if (!on) {
                wasContact = false;
                continue;
            }
            sceneTicks++;
            Side[] both = {sim.a, sim.b};
            CompanionAgent[] lead = new CompanionAgent[2];
            for (int i = 0; i < 2; i++) {
                Side side = both[i];
                CrossScene sc = side.scene();
                if (side.sceneUnit() == null || sc == null || !sc.active()) continue;
                CompanionAgent l = side.sceneUnit().leader();
                lead[i] = l;
                if (!leftRecorded[i] && l.sceneLeft() != null) {
                    leftRecorded[i] = true;
                    leftHistory.add(l.sceneLeft());
                }
                seenTicks++;
                if (l.appear() >= 1f && Visibility.visible(side.owner(), l.position(), l.voxels())) seen++;
                if (!l.inScene() || sc.greeting()) {
                    prevYaw[i] = Float.NaN;
                    continue;
                }
                double err = l.position().distanceTo(sc.pathPoint(sc.ticks()));
                if (!joined[i] && err < JOINED) {
                    joined[i] = true;
                    joins++;
                    joinSum += (sc.ticks() - CrossScene.GREET_TICKS) / 20.0;
                }
                if (joined[i]) {
                    pathErr += err;
                    pathTicks++;
                    if (!Float.isNaN(prevYaw[i])) {
                        float dy = Math.abs(((l.yaw() - prevYaw[i]) % 360 + 540) % 360 - 180);
                        peakTurn = Math.max(peakTurn, dy);
                        if (dy >= TURN_CAP_NEAR) atCap++;
                    }
                    prevYaw[i] = l.yaw();
                }
            }
            if (sa != null && sa.kind == CrossScene.Kind.RING && lead[0] != null && lead[1] != null && joined[0] && joined[1]
                    && lead[0].inScene() && lead[1].inScene()) {
                Vec c = sa.ringCentre();
                Vec da = lead[0].position().sub(c).horizontal(), db = lead[1].position().sub(c).horizontal();
                double cos = da.dot(db) / Math.max(1e-9, da.length() * db.length());
                anti += 180 - Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos))));
                antiTicks++;
            }
            List<double[]> ba = sim.a.bodies(), bb = sim.b.bodies();
            boolean any = false;
            for (double[] x : ba) {
                for (double[] y : bb) {
                    if (x[7] >= 1f && y[7] >= 1f && PartyRun.segDist(x, y) < PartyRun.BODY_HALF_WIDTH * (x[6] + y[6])) any = true;
                }
            }
            if (any) contact++;
            if (any && !wasContact) contactEpisodes++;
            wasContact = any;
            // Only the scene's own fish: a percher riding its owner's shoulder is inside them by design.
            List<double[]> scened = new ArrayList<>();
            if (lead[0] != null && lead[0].inScene()) scened.addAll(PartyRun.bodies(sim.a.sceneUnit()));
            if (lead[1] != null && lead[1].inScene()) scened.addAll(PartyRun.bodies(sim.b.sceneUnit()));
            if (inOwner(scened, sim.a.owner()) || inOwner(scened, sim.b.owner())) ownerContact++;
        }
        // Why each scene ended, per side (a scene still on at the end of the run is "running").
        for (CrossScene sc : history) {
            if (sc == null) continue;
            String r = sc.endReason();
            ends.merge(r == null ? "running" : r, 1, Integer::sum);
        }
        for (String r : leftHistory) left.merge(r, 1, Integer::sum);
        double minutes = Math.max(1, sceneTicks) / 1200.0;
        return new SceneResult(sim.scenesStarted(), sceneTicks, joins == 0 ? Double.NaN : joinSum / joins,
                sides == 0 ? 0 : 100.0 * joins / sides, pathTicks == 0 ? Double.NaN : pathErr / pathTicks,
                antiTicks == 0 ? Double.NaN : anti / antiTicks, sceneTicks == 0 ? 0 : 100.0 * contact / sceneTicks,
                contactEpisodes / minutes, sceneTicks == 0 ? 0 : 100.0 * ownerContact / sceneTicks,
                seenTicks == 0 ? 0 : 100.0 * seen / seenTicks, peakTurn, pathTicks == 0 ? 0 : 100.0 * atCap / pathTicks,
                ends, left);
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
