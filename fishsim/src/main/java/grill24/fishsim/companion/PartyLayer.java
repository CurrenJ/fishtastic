package grill24.fishsim.companion;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * One owner's party as the sim steps it (docs/fish-companions-party.md §6): the units {@link
 * PartyGrouping} forms (a solo, a school or a ground formation), stepped together. Each unit sees
 * the others' leaders as {@link PointOfInterest.Kind#COMPANION} points within {@link #MATE_RANGE},
 * the way ClientCompanions feeds them in game.
 *
 * <p>Phase 2b: no party rules yet, so this is the measured baseline. Later rules act on units, never
 * inside a formation, and each has an on/off switch.
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

    /**
     * @param groups      the party's units, from {@link PartyGrouping#group}
     * @param temperament each unit's temperament, from its formation leader (as ClientCompanion does)
     * @param drift       each unit's drift style, from its formation leader (used by DRIFT solos only)
     */
    public PartyLayer(CompanionTunables t, List<PartyGrouping.Group> groups,
                      Function<PartyGrouping.Member, Temperament> temperament,
                      Function<PartyGrouping.Member, DriftStyle> drift, CompanionWorld world) {
        List<Unit> out = new ArrayList<>(groups.size());
        for (PartyGrouping.Group g : groups) {
            out.add(new Unit(g, t, temperament.apply(g.leader()), drift.apply(g.leader()), world));
        }
        units = List.copyOf(out);
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
        int n = units.size();
        if (n == 1) {
            units.get(0).step(base);
            return;
        }
        PointOfInterest[] mates = new PointOfInterest[n];
        for (int i = 0; i < n; i++) mates[i] = units.get(i).asPoint(pointId(units.get(i).group()));
        for (int i = 0; i < n; i++) {
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
    static int pointId(PartyGrouping.Group g) {
        return -1 - g.lowestSlot();
    }
}
