package grill24.fishtastic.client.ambient;

import grill24.fishsim.core.Locomotion;
import grill24.fishtastic.ecology.EcologyHash;
import grill24.fishtastic.school.VisitShape;

/**
 * Ambient groups (docs/ambient-fish-schools-design-spec.md §4). A roster lifespan
 * ({@link grill24.fishtastic.ecology.AmbientRoster}) is a <em>group</em>: one species, one home cell
 * and N members whose count comes from the species' authored visit shape. Pure: every value is a
 * hash of the group ID, so two clients derive the same members.
 *
 * <p>Member 0 carries the group ID itself, so a solitary fish keeps the ID the roster gives it and the
 * server's observation check ({@code FishObservation}) still recognises every group by that ID.
 */
public final class AmbientGroups {
    private AmbientGroups() {}

    /**
     * A chunk earns one group slot per this many of its old per-fish slots. Measured against the
     * fixture mix (about 3.5 fish per group under the fish-weighted sampler), so the water holds
     * about as many fish as before, in groups.
     */
    public static final int SLOT_DIVISOR = 2;

    private static final long SALT_SIZE = 0x0A12_0001L;
    private static final long SALT_MEMBER = 0x0A12_0002L;
    private static final long SALT_OFFSET = 0x0A12_0003L;

    /**
     * Group slots of a chunk that earned {@code fishSlots} per-fish slots. Small water keeps up to two
     * slots, so a pond is not all-or-nothing on one group's lifespan; larger water gets one group
     * slot per {@link #SLOT_DIVISOR} fish slots.
     */
    public static int groupSlots(int fishSlots) {
        if (fishSlots <= 0) return 0;
        return Math.max(Math.min(fishSlots, 2), (fishSlots + SLOT_DIVISOR - 1) / SLOT_DIVISOR);
    }

    /** The shape an ambient group of this locomotion takes: only free swimmers school (spec §4.1). */
    public static VisitShape shapeFor(VisitShape authored, Locomotion locomotion) {
        if (authored == VisitShape.SCHOOL && locomotion != Locomotion.FREE_SWIM) return VisitShape.LOOSE_GROUP;
        return authored;
    }

    /** Smallest ambient group of a shape (D1: schools reach 12). */
    public static int minMembers(VisitShape shape) {
        return switch (shape) {
            case SOLITARY -> 1;
            case PAIR -> 2;
            case LOOSE_GROUP -> 3;
            case SCHOOL -> 6;
        };
    }

    public static int maxMembers(VisitShape shape) {
        return switch (shape) {
            case SOLITARY -> 1;
            case PAIR -> 2;
            case LOOSE_GROUP -> 5;
            case SCHOOL -> 12;
        };
    }

    /** Expected members of a shape's group, for the fish-weighted species draw (D2). */
    public static double meanMembers(VisitShape shape) {
        return (minMembers(shape) + maxMembers(shape)) / 2.0;
    }

    /**
     * Members of group {@code groupId}: uniform in the shape's range, then capped by the chunk's
     * per-fish allowance so a puddle never hosts a school of twelve.
     */
    public static int size(long groupId, VisitShape shape, int allowance) {
        int lo = minMembers(shape), hi = maxMembers(shape);
        int n = lo + EcologyHash.toBoundedInt(EcologyHash.mix(groupId ^ SALT_SIZE), hi - lo + 1);
        return Math.max(1, Math.min(n, Math.max(1, allowance)));
    }

    /** Member {@code i}'s fish ID; member 0 is the group ID. */
    public static long memberId(long groupId, int i) {
        return i == 0 ? groupId : EcologyHash.mix(EcologyHash.mix(groupId ^ SALT_MEMBER) + i);
    }

    /** Spacing between neighbours at spawn, in body lengths (spec §4.4). */
    public static float spacingLengths(VisitShape shape) {
        return switch (shape) {
            case SCHOOL, PAIR -> 1.5f;
            case LOOSE_GROUP -> 3.5f;
            case SOLITARY -> 0f;
        };
    }

    /** Whether members share one initial heading: a school or a pair travels together, a loose group does not. */
    public static boolean sharedHeading(VisitShape shape) {
        return shape == VisitShape.SCHOOL || shape == VisitShape.PAIR;
    }

    /** A hashed unit draw in [0, 1) for member placement attempt {@code k} on {@code axis}. */
    public static double offsetUnit(long memberId, int k, int axis) {
        return EcologyHash.toUnitDouble(EcologyHash.mix(EcologyHash.mix(memberId ^ SALT_OFFSET) + k * 3L + axis));
    }
}
