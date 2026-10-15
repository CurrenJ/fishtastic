package grill24.fishsim.companion;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;

/**
 * One owner's party as the sim steps it (docs/fish-companions-party.md §6): the units {@link
 * PartyGrouping} forms (a solo, a school or a ground formation), stepped together. Each unit sees
 * the others' leaders as {@link PointOfInterest.Kind#COMPANION} points within {@link #MATE_RANGE},
 * the way ClientCompanions feeds them in game.
 *
 * <p>Party rules act on units, never inside a formation, and each has an on/off switch:
 * <ul>
 *   <li>Phase 2c: the mixed shoal (D13) is in {@link PartyGrouping}.</li>
 *   <li>Phase 2d ({@link CompanionTunables#partyHomes}, {@link CompanionTunables#partyLeases}):
 *       every percher gets its own home perch when the party is built, and a transient move takes
 *       a free perch or doesn't happen ({@link PerchLeases}).</li>
 *   <li>Phase 2f ({@link CompanionTunables#partyGroundSeparation}): ground walkers of different
 *       units push apart on the ground.</li>
 *   <li>D21 ({@link CompanionTunables#partySwimSeparation}): free-swimming units (a predator and its
 *       prey's shoal) push apart in the water.</li>
 * </ul>
 *
 * <p>Determinism: every unit reads its party-mates as they were at the end of the previous tick, so
 * the step order can't change results. A one-unit party adds no points, so it is bitwise a solo, a
 * school or a ground formation stepped on its own.
 */
public final class PartyLayer {

    /** Same range ClientCompanions uses for companion points of interest, blocks. */
    public static final double MATE_RANGE = 16.0;

    /** One stepped unit: exactly one of {@link #solo}, {@link #school} or {@link #ground} is set. */
    public static final class Unit {
        private final PartyGrouping.Group group;
        private final CompanionAgent solo;
        private final CompanionGroup school;
        private final BenthicGroup ground;

        Unit(PartyGrouping.Group group, CompanionTunables t, Temperament temperament, DriftStyle drift,
             CompanionWorld world) {
            this.group = group;
            PartyGrouping.Member m = group.leader();
            switch (group.kind()) {
                case SCHOOL -> {
                    school = group.school(t, temperament, world);
                    solo = null;
                    ground = null;
                }
                case GROUND -> {
                    ground = group.ground(t, temperament, world);
                    solo = null;
                    school = null;
                }
                default -> {
                    solo = new CompanionAgent(t, group.seed(), m.locomotion(), m.length(), temperament, m.gait(),
                            drift, world);
                    school = null;
                    ground = null;
                }
            }
        }

        public PartyGrouping.Group group() { return group; }
        public CompanionAgent solo() { return solo; }
        public CompanionGroup school() { return school; }
        public BenthicGroup ground() { return ground; }

        /** The unit's formation leader (its only fish for a solo). */
        public CompanionAgent leader() {
            return school != null ? school.leader() : ground != null ? ground.leader() : solo;
        }

        /** Every full agent in the unit: a ground formation's members, else the leader alone. */
        public List<CompanionAgent> agents() {
            if (ground == null) return List.of(leader());
            List<CompanionAgent> out = new ArrayList<>(ground.count());
            for (int i = 0; i < ground.count(); i++) out.add(ground.member(i));
            return out;
        }

        /** Fish drawn for this unit. */
        public int bodyCount() { return group.members().size(); }

        void step(CompanionWorld w) {
            if (school != null) school.step(w);
            else if (ground != null) ground.step(w);
            else solo.step(w);
        }

        public void interpolate(float alpha) {
            if (school != null) school.interpolate(alpha);
            else if (ground != null) ground.interpolate(alpha);
            else solo.interpolate(alpha);
        }

        public void invalidateBlocks() {
            if (school != null) school.invalidateBlocks();
            else if (ground != null) ground.invalidateBlocks();
            else solo.voxels().invalidate();
        }

        /** This unit's leader as a party-mate sees it, or null while it isn't fully there. */
        PointOfInterest asPoint(int id) {
            CompanionAgent l = leader();
            if (l.appear() < 1f) return null;
            return new PointOfInterest(PointOfInterest.Kind.COMPANION, id, l.position(), l.velocity(), l.radius());
        }
    }

    private final List<Unit> units;
    /** The party's perches (§6.1), or null: one unit, under two perchers, or leases off. */
    private final PerchLeases leases;
    private final HandClock handClock;
    private final CompanionTunables t;
    /** Units of ground walkers, for ground separation (§6.2; empty under two). */
    private final List<Unit> groundUnits;
    /** Free-swimming units (solos and schools), for swim separation (D21; empty under two). */
    private final List<Unit> swimUnits;
    /** A unit's point id as its party-mates see it, from its lowest slot. */
    private final IntUnaryOperator mateId;

    /**
     * @param groups      the party's units, from {@link PartyGrouping#group}
     * @param temperament each unit's temperament, from its formation leader (as ClientCompanion does)
     * @param drift       each unit's drift style, from its formation leader (used by DRIFT solos only)
     */
    public PartyLayer(CompanionTunables t, List<PartyGrouping.Group> groups,
                      Function<PartyGrouping.Member, Temperament> temperament,
                      Function<PartyGrouping.Member, DriftStyle> drift, CompanionWorld world) {
        this(t, groups, temperament, drift, world, PartyLayer::pointId);
    }

    /**
     * As above, with the point id each unit shows its party-mates, from its lowest slot. The game
     * keys it by owner too, so it can't collide with another party's ids (spec §5.2).
     */
    public PartyLayer(CompanionTunables t, List<PartyGrouping.Group> groups,
                      Function<PartyGrouping.Member, Temperament> temperament,
                      Function<PartyGrouping.Member, DriftStyle> drift, CompanionWorld world, IntUnaryOperator mateId) {
        this.mateId = mateId;
        List<Unit> out = new ArrayList<>(groups.size());
        for (PartyGrouping.Group g : groups) {
            out.add(new Unit(g, t, temperament.apply(g.leader()), drift.apply(g.leader()), world));
        }
        units = List.copyOf(out);
        leases = units.size() > 1 ? perches(t, world.owner()) : null;
        handClock = units.size() > 1 && t.handClock && t.partyHandClock ? sharedHandClock() : null;
        this.t = t;
        List<Unit> ground = new ArrayList<>();
        for (Unit u : units) if (u.leader().groundWalker()) ground.add(u);
        groundUnits = ground.size() > 1 ? List.copyOf(ground) : List.of();
        List<Unit> swim = new ArrayList<>();
        for (Unit u : units) {
            if (u.group().leader().locomotion() == grill24.fishsim.core.Locomotion.FREE_SWIM) swim.add(u);
        }
        swimUnits = swim.size() > 1 ? List.copyOf(swim) : List.of();
    }

    /** The party's one hand clock (HandClock), or null with under two perchers that can visit a hand. */
    private HandClock sharedHandClock() {
        List<PerchBody> bodies = new ArrayList<>();
        for (Unit u : units) {
            for (CompanionAgent a : u.agents()) {
                PerchBody p = a.perchBody();
                if (p != null && a.locomotion() != grill24.fishsim.core.Locomotion.ANCHORED) bodies.add(p);
            }
        }
        return bodies.size() < 2 ? null : HandClock.shared(bodies);
    }

    /** Where anchored fish look for a home (D5), and the order everyone else takes what's left in. */
    private static final List<PerchBody.Slot> ANCHORED_ORDER = List.of(PerchBody.Slot.HEAD,
            PerchBody.Slot.RIGHT_SHOULDER, PerchBody.Slot.LEFT_SHOULDER, PerchBody.Slot.BACK);
    private static final List<PerchBody.Slot> OTHER_ORDER = List.of(PerchBody.Slot.LEFT_SHOULDER,
            PerchBody.Slot.RIGHT_SHOULDER, PerchBody.Slot.HEAD, PerchBody.Slot.BACK);

    /**
     * Homes and leases for a party of more than one unit (§6.1). A formation keeps its members'
     * homes; then anchored fish choose (D5: head, then the shoulders), then the other perchers. A
     * fish keeps the home it would have alone if it is still free, and otherwise takes the next free
     * one in its order. Within each, the lower party slot chooses first. Four perches for at most three bodies, so homes never run out.
     *
     * @return the leases, or null when off or there's nothing to share
     */
    private PerchLeases perches(CompanionTunables t, OwnerState owner) {
        List<PerchBody> bodies = new ArrayList<>();
        List<CompanionAgent> anchored = new ArrayList<>(), others = new ArrayList<>();
        java.util.EnumSet<PerchBody.Slot> free = java.util.EnumSet.of(PerchBody.Slot.RIGHT_SHOULDER,
                PerchBody.Slot.LEFT_SHOULDER, PerchBody.Slot.HEAD, PerchBody.Slot.BACK);
        for (Unit u : units) {
            List<CompanionAgent> agents = u.agents();
            for (int i = 0; i < agents.size(); i++) {
                CompanionAgent a = agents.get(i);
                PerchBody p = a.perchBody();
                if (p == null) continue;
                p.partySlot = u.group().members().get(i).slot();
                bodies.add(p);
                if (u.ground() != null) free.remove(p.home);
                else (a.locomotion() == grill24.fishsim.core.Locomotion.ANCHORED ? anchored : others).add(a);
            }
        }
        if (bodies.size() < 2) return null;
        if (t.partyHomes) {
            // Party slot order, lead first, whatever order the units are listed in.
            java.util.Comparator<CompanionAgent> bySlot = java.util.Comparator.comparingInt(a -> a.perchBody().partySlot);
            anchored.sort(bySlot);
            others.sort(bySlot);
            for (CompanionAgent a : anchored) home(a, free, ANCHORED_ORDER, owner);
            for (CompanionAgent a : others) home(a, free, OTHER_ORDER, owner);
        }
        return t.partyLeases ? new PerchLeases(bodies) : null;
    }

    private static void home(CompanionAgent a, java.util.Set<PerchBody.Slot> free, List<PerchBody.Slot> order,
                             OwnerState owner) {
        PerchBody.Slot own = a.perchHome();
        if (free.remove(own)) return;
        for (PerchBody.Slot s : order) {
            if (free.remove(s)) {
                a.partyHome(s, owner);
                return;
            }
        }
    }

    public List<Unit> units() { return units; }

    public int size() { return units.size(); }

    public Unit unit(int i) { return units.get(i); }

    /** Fish drawn for the whole party (at most {@link PartyGrouping#SLOTS}). */
    public int bodyCount() {
        int n = 0;
        for (Unit u : units) n += u.bodyCount();
        return n;
    }

    /**
     * Steps every unit by one tick in {@code base}: its own points plus each other unit's leader, as
     * that leader stood at the end of the last tick, within {@link #MATE_RANGE}.
     */
    public void step(CompanionWorld base) {
        step(u -> base);
    }

    /**
     * As {@link #step(CompanionWorld)}, with each unit's own base world: in game, what the owner
     * holds reads differently per species ({@code SAME_SPECIES}), and dev triggers act on one unit.
     */
    public void step(Function<Unit, CompanionWorld> worldOf) {
        int n = units.size();
        if (n == 1) {
            units.get(0).step(worldOf.apply(units.get(0)));
            return;
        }
        if (leases != null) leases.snapshot();
        if (handClock != null) handClock.tick();
        if (!groundUnits.isEmpty()) separateGround();
        if (!swimUnits.isEmpty()) separateSwim();
        PointOfInterest[] mates = new PointOfInterest[n];
        for (int i = 0; i < n; i++) mates[i] = units.get(i).asPoint(mateId.applyAsInt(units.get(i).group().lowestSlot()));
        for (int i = 0; i < n; i++) {
            CompanionWorld base = worldOf.apply(units.get(i));
            Vec self = units.get(i).leader().position();
            List<PointOfInterest> pts = null;
            for (int j = 0; j < n; j++) {
                if (j == i || mates[j] == null || mates[j].pos().distanceTo(self) > MATE_RANGE) continue;
                if (pts == null) pts = new ArrayList<>(base.points());
                pts.add(mates[j]);
            }
            units.get(i).step(pts == null ? base
                    : new CompanionWorld(base.owner(), base.blocks(), pts, base.held(), base.hurt()));
        }
    }

    /**
     * Ground separation (§6.2, {@link CompanionTunables#partyGroundSeparation}): every ground walker
     * is pushed away from other units' ground walkers within crowd range, as a BenthicGroup crowd's
     * members are, from where they stood at the end of the last tick. Each keeps its own mind; a
     * shared trail queue and a mixed-species line were measured and lost (§20).
     */
    private void separateGround() {
        List<CompanionAgent> all = new ArrayList<>();
        List<Integer> unitOf = new ArrayList<>(), slotOf = new ArrayList<>();
        for (int u = 0; u < groundUnits.size(); u++) {
            List<CompanionAgent> agents = groundUnits.get(u).agents();
            for (int k = 0; k < agents.size(); k++) {
                all.add(agents.get(k));
                unitOf.add(u);
                slotOf.add(groundUnits.get(u).group().members().get(k).slot());
            }
        }
        // Every push from last tick's positions first, then applied: the order can't matter.
        Vec[] pushes = new Vec[all.size()];
        for (int i = 0; i < all.size(); i++) {
            CompanionAgent a = all.get(i);
            if (!a.onGround()) continue;
            for (int j = 0; j < all.size(); j++) {
                CompanionAgent b = all.get(j);
                if (unitOf.get(j).equals(unitOf.get(i)) || !b.onGround()
                        || Math.abs(a.position().y() - b.position().y()) > 0.5) continue;
                double range = Math.max(0.6 * (a.length() + b.length()), a.radius() + b.radius() + 0.04);
                Vec away = a.position().sub(b.position()).horizontal();
                double d = away.length();
                if (d >= range) continue;
                // Exactly on top of each other (they all start beside the owner): split by party slot.
                Vec dir = d < 1e-6 ? new Vec(slotOf.get(i) < slotOf.get(j) ? -1 : 1, 0, 0) : away.scale(1 / d);
                Vec p = dir.scale(4 * (1 - d / range));
                pushes[i] = pushes[i] == null ? p : pushes[i].add(p);
            }
        }
        for (int i = 0; i < all.size(); i++) all.get(i).partyPush(t.partyGroundSeparation ? pushes[i] : null);
    }

    /** Body gap two swimming units keep, in mean body lengths, beyond their radii (D21). */
    static final double SWIM_SEPARATION_LENGTHS = 1.2;
    /** Push at contact, blocks/s, as a school's own separation (CompanionGroup.SEPARATION_SPEED). */
    static final double SWIM_SEPARATION_SPEED = 2.0;

    /**
     * Swim separation (D21, {@link CompanionTunables#partySwimSeparation}): every body of a
     * free-swimming unit is pushed off the bodies of the other swimming units, as a school's
     * members are pushed off each other, from where they stood at the end of the last tick. A
     * school's leader carries its followers along; a follower is pushed too, so a predator can't
     * swim through the back of the shoal.
     */
    private void separateSwim() {
        List<Vec> pos = new ArrayList<>();
        List<Double> radius = new ArrayList<>(), length = new ArrayList<>();
        List<Integer> unitOf = new ArrayList<>();
        List<java.util.function.Consumer<Vec>> apply = new ArrayList<>();
        for (int u = 0; u < swimUnits.size(); u++) {
            Unit unit = swimUnits.get(u);
            CompanionAgent l = unit.leader();
            pos.add(l.position());
            radius.add(l.radius());
            length.add((double) l.length());
            unitOf.add(u);
            apply.add(l::swimPush);
            CompanionGroup school = unit.school();
            if (school == null) continue;
            for (int k = 0; k < school.followerCount(); k++) {
                CompanionGroup.Follower f = school.follower(k);
                pos.add(f.position());
                radius.add(f.radius());
                length.add((double) f.length());
                unitOf.add(u);
                apply.add(p -> f.partyPush = p);
            }
        }
        Vec[] pushes = new Vec[pos.size()];
        if (t.partySwimSeparation) {
            for (int i = 0; i < pos.size(); i++) {
                for (int j = 0; j < pos.size(); j++) {
                    if (unitOf.get(j).equals(unitOf.get(i))) continue;
                    double range = SWIM_SEPARATION_LENGTHS * 0.5 * (length.get(i) + length.get(j))
                            + radius.get(i) + radius.get(j);
                    Vec away = pos.get(i).sub(pos.get(j));
                    double d = away.length();
                    if (d >= range) continue;
                    // On top of each other (they all start beside the owner): split by unit order.
                    Vec dir = d < 1e-6 ? new Vec(unitOf.get(i) < unitOf.get(j) ? -1 : 1, 0, 0) : away.scale(1 / d);
                    Vec p = dir.scale(SWIM_SEPARATION_SPEED * (1 - d / range));
                    pushes[i] = pushes[i] == null ? p : pushes[i].add(p);
                }
            }
        }
        for (int i = 0; i < pos.size(); i++) apply.get(i).accept(pushes[i]);
    }

    public void interpolate(float alpha) {
        for (Unit u : units) u.interpolate(alpha);
    }

    public void invalidateBlocks() {
        for (Unit u : units) u.invalidateBlocks();
    }

    /**
     * A party-mate's point id, from its unit's identity (its lowest slot), as ClientCompanions keys
     * it: negative, so it never collides with an entity's (spec §5.2).
     */
    static int pointId(int lowestSlot) {
        return -1 - lowestSlot;
    }
}
