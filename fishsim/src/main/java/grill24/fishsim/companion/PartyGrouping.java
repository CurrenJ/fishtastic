package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Which of an owner's party members form a group (docs/fish-companions-party.md §3). The party is up
 * to {@link #SLOTS} bonded fish, each one body; same-species duplicates group up:
 *
 * <ul>
 *   <li>2–3 of one FREE_SWIM species: a {@link Kind#SCHOOL} ({@link CompanionGroup}).</li>
 *   <li>2–3 of one ground-walking BENTHIC species (a ground gait): a {@link Kind#GROUND} formation
 *       ({@link BenthicGroup}), line or crowd from the species.</li>
 *   <li>Everything else is a {@link Kind#SOLO}: different species, and duplicates of GLIDE, DRIFT,
 *       ANCHORED or a benthic with no ground gait (a pure percher) (D7).</li>
 * </ul>
 *
 * <p>Size never decides grouping (D8): the old trio rule ({@link CompanionGroup#isTrio}) and a
 * profile's {@code companion_group.count} are retired as sources of groups. A group's formation
 * leader is picked by size, never by the player (D9): the smallest member leads and the rest follow
 * smallest first, ties going to the lower slot, so identical members keep the lowest slot in front.
 * The party lead (slot 0) is a separate idea: it stays the party's buddy wherever it swims in a
 * group.
 *
 * <p>Pure: no Minecraft types. Slots are 0-based here (the spec's "slot 1" is index 0).
 */
public final class PartyGrouping {
    private PartyGrouping() {}

    /** Party size (docs/fish-companions-party.md R1). */
    public static final int SLOTS = 3;

    public enum Kind { SOLO, SCHOOL, GROUND }

    /**
     * One party member as the sim sees it.
     *
     * @param species    the species id (e.g. {@code fishtastic:bluegill}); equality decides grouping
     * @param locomotion the species' locomotion class
     * @param gait       a benthic's ground gait, or null for a pure percher and for every other class
     * @param formation  a ground benthic's formation style from its profile, or null (defaults to LINE)
     * @param length     this fish's own body length, blocks
     * @param stackSeed  the seed derived from the bonded stack, exactly as a lone companion's is today
     * @param slot       the party slot, 0-based
     */
    public record Member(String species, Locomotion locomotion, GroundGait gait, BenthicGroup.Formation formation,
                         float length, long stackSeed, int slot) {
        public Member {
            Objects.requireNonNull(species);
            Objects.requireNonNull(locomotion);
            if (slot < 0 || slot >= SLOTS) throw new IllegalArgumentException("slot must be 0.." + (SLOTS - 1));
        }

        /** This member's sim seed: {@link #memberSeed} of its stack seed and slot. */
        public long seed() {
            return memberSeed(stackSeed, slot);
        }

        boolean groundWalker() {
            return locomotion == Locomotion.BENTHIC && gait != null;
        }
    }

    /**
     * One unit the client steps and draws: a solo agent, a school or a ground formation. {@code
     * members} are in formation order: the leader first, then followers smallest first (D9).
     */
    public record Group(Kind kind, List<Member> members) {
        public Group {
            members = List.copyOf(members);
            if (members.isEmpty()) throw new IllegalArgumentException("empty group");
            if (kind == Kind.SOLO && members.size() != 1) throw new IllegalArgumentException("a solo has one member");
            if (kind != Kind.SOLO && (members.size() < 2 || members.size() > 3))
                throw new IllegalArgumentException("a group has 2 or 3 members");
        }

        public Member leader() { return members.get(0); }

        /**
         * The formation leader's party slot: the smallest member's (D9), or a crowd's biggest under
         * {@link CompanionTunables#crowdBiggestMiddle}. Not necessarily the lowest.
         */
        public int leaderSlot() { return leader().slot(); }

        /** The group's lowest party slot: where it sits in the party, and its order among groups. */
        public int lowestSlot() {
            int lowest = SLOTS;
            for (Member m : members) lowest = Math.min(lowest, m.slot());
            return lowest;
        }

        /** The leader's seed: the whole group's randomness derives from it. */
        public long seed() { return leader().seed(); }

        /** The slots this group occupies, in formation order (leader first). */
        public int[] slots() {
            return members.stream().mapToInt(Member::slot).toArray();
        }

        /** Each member's body length, leader first. */
        public float[] lengths() {
            float[] l = new float[members.size()];
            for (int i = 0; i < l.length; i++) l[i] = members.get(i).length();
            return l;
        }

        /** A ground formation's style: the leader's, LINE when the species names none. */
        public BenthicGroup.Formation formation() {
            BenthicGroup.Formation f = leader().formation();
            return f == null ? BenthicGroup.Formation.LINE : f;
        }

        /** The school, for a {@link Kind#SCHOOL}. */
        public CompanionGroup school(CompanionTunables t, Temperament temperament, CompanionWorld world) {
            if (kind != Kind.SCHOOL) throw new IllegalStateException(kind + " is not a school");
            return new CompanionGroup(t, seed(), lengths(), temperament, world);
        }

        /** The ground formation, for a {@link Kind#GROUND}. */
        public BenthicGroup ground(CompanionTunables t, Temperament temperament, CompanionWorld world) {
            if (kind != Kind.GROUND) throw new IllegalStateException(kind + " is not a ground formation");
            return new BenthicGroup(t, seed(), lengths(), temperament, leader().gait(), formation(), world);
        }
    }

    /**
     * Groups a party. Members may arrive in any order and with empty slots missing; the result is
     * ordered by each group's lowest slot, and each group's members are in formation order (D9).
     */
    public static List<Group> group(List<Member> party) {
        return group(party, CompanionTunables.defaults());
    }

    /**
     * {@link #group(List)} under {@code t}'s ordering switches: with {@link
     * CompanionTunables#crowdBiggestMiddle}, a three-member crowd's biggest member takes the centre
     * (§14.1).
     */
    public static List<Group> group(List<Member> party, CompanionTunables t) {
        if (party.size() > SLOTS) throw new IllegalArgumentException("a party has at most " + SLOTS + " members");
        List<Member> sorted = new ArrayList<>(party);
        sorted.sort(Comparator.comparingInt(Member::slot));
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).slot() == sorted.get(i - 1).slot())
                throw new IllegalArgumentException("two members in slot " + sorted.get(i).slot());
        }
        List<Group> out = new ArrayList<>();
        boolean[] used = new boolean[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            if (used[i]) continue;
            Member lead = sorted.get(i);
            used[i] = true;
            Kind kind = lead.locomotion() == Locomotion.FREE_SWIM ? Kind.SCHOOL
                    : lead.groundWalker() ? Kind.GROUND : Kind.SOLO;
            List<Member> members = new ArrayList<>();
            members.add(lead);
            if (kind != Kind.SOLO) {
                for (int j = i + 1; j < sorted.size(); j++) {
                    if (!used[j] && sameKind(lead, sorted.get(j))) {
                        members.add(sorted.get(j));
                        used[j] = true;
                    }
                }
            }
            members.sort(FORMATION_ORDER);
            if (t.crowdBiggestMiddle && kind == Kind.GROUND && members.size() == 3
                    && lead.formation() == BenthicGroup.Formation.CROWD) {
                biggestToCentre(members);
            }
            out.add(new Group(members.size() == 1 ? Kind.SOLO : kind, members));
        }
        return out;
    }

    /**
     * Moves the biggest member to the front of a smallest-first crowd. A crowd's first member is its
     * centre: the anchor the others stand either side of. Ties go to the lower slot, so identical
     * members keep their order.
     */
    private static void biggestToCentre(List<Member> members) {
        int big = 0;
        for (int i = 1; i < members.size(); i++) {
            Member m = members.get(i), b = members.get(big);
            if (m.length() > b.length() || (m.length() == b.length() && m.slot() < b.slot())) big = i;
        }
        members.add(0, members.remove(big));
    }

    /** Smallest first, ties to the lower slot (D9). */
    static final Comparator<Member> FORMATION_ORDER =
            Comparator.comparingDouble(Member::length).thenComparingInt(Member::slot);

    /** Same species, and both of the grouping class (a species' class and gait never differ in practice). */
    private static boolean sameKind(Member a, Member b) {
        return a.species().equals(b.species()) && a.locomotion() == b.locomotion()
                && a.groundWalker() == b.groundWalker();
    }

    /**
     * A member's seed from its stack's: slot 0 keeps the stack seed exactly (today's lone companion is
     * unchanged), other slots mix the slot in, so identical stacks in different slots still swim
     * different paths.
     */
    public static long memberSeed(long stackSeed, int slot) {
        if (slot == 0) return stackSeed;
        long z = stackSeed + slot * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
