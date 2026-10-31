package grill24.fishtastic.ecology;

import java.util.UUID;

/**
 * Everything about a visit that is decided by hash rather than by chance at reveal time (world fish
 * exploration E2, E11; implementation plan Step 2): which water cell it starts at, which species the
 * natural pool yields, how many members it holds, their reward seeds and ids, and the seed for their
 * placement. All are pure functions of {@code (ecology seed, VisitId)}, so a visit revealed at its
 * window start and one revealed later by a late arrival are the same visit.
 *
 * <p>Member {@code i} of a visit is always the same fish: its index is also its bit in the depletion
 * ledger mask ({@link #bit}).
 */
public final class VisitDerivation {

    private static final long GAMMA = 0x9E3779B97F4A7C15L;

    /** Sub-streams of {@link EcologyHash#SALT_MEMBERS}. */
    private static final long STREAM_SPECIES = 1;
    private static final long STREAM_COUNT = 2;
    private static final long STREAM_POSITIONS = 3;
    private static final long STREAM_SCHOOL_ID = 4;
    private static final long STREAM_MEMBER_SEED = 1_000;
    private static final long STREAM_MEMBER_ID = 2_000;
    private static final long STREAM_DEPARTURE = 3_000;

    /** A ledger mask is one long, so a visit tracks at most this many members. */
    public static final int MAX_TRACKED_MEMBERS = Long.SIZE;

    private VisitDerivation() {
    }

    /** The visit's member-stream base hash. */
    public static long base(long seed, VisitId id) {
        return EcologyHash.hash(seed, id.chunkX(), id.chunkZ(), id.window(), EcologyHash.SALT_MEMBERS);
    }

    /** The hash that picks the start cell among the chunk's qualifying water cells (E2). */
    public static long startHash(long seed, VisitId id) {
        return EcologyHash.hash(seed, id.chunkX(), id.chunkZ(), id.window(), EcologyHash.SALT_START_CELL);
    }

    /** {@code startHash mod count}: the index into the chunk's fixed-order list of qualifying cells. */
    public static int startIndex(long startHash, int cellCount) {
        return EcologyHash.toBoundedInt(startHash, cellCount);
    }

    /** An independent value per {@code index} from one base hash. */
    public static long stream(long base, long index) {
        return EcologyHash.mix(base ^ EcologyHash.mix(index * GAMMA + GAMMA));
    }

    /**
     * The species draw: index of the winning entry of {@code weights} (in pool order), proportional to
     * weight, or -1 when there is nothing to draw from.
     */
    public static int pickWeighted(long base, int[] weights) {
        long total = 0;
        for (int w : weights) total += Math.max(0, w);
        if (total <= 0 || total > Integer.MAX_VALUE) return -1;
        int draw = EcologyHash.toBoundedInt(stream(base, STREAM_SPECIES), (int) total);
        for (int i = 0; i < weights.length; i++) {
            draw -= Math.max(0, weights[i]);
            if (draw < 0) return i;
        }
        return -1;
    }

    /** Member count in {@code [min, most]}. */
    public static int memberCount(long base, int min, int most) {
        if (most < min) throw new IllegalArgumentException("most < min: " + most + " < " + min);
        return min + EcologyHash.toBoundedInt(stream(base, STREAM_COUNT), most - min + 1);
    }

    public static long positionSeed(long base) {
        return stream(base, STREAM_POSITIONS);
    }

    public static long memberRewardSeed(long base, int member) {
        return stream(base, STREAM_MEMBER_SEED + member);
    }

    public static UUID schoolId(long base) {
        return new UUID(stream(base, STREAM_SCHOOL_ID), stream(base, STREAM_SCHOOL_ID + 7_777));
    }

    public static UUID memberId(long base, int member) {
        return new UUID(stream(base, STREAM_MEMBER_ID + 2L * member), stream(base, STREAM_MEMBER_ID + 2L * member + 1));
    }

    /**
     * The overworld clock reading at {@code windowStart}, reconstructed from the reading now: the clock runs
     * one tick per game tick, so {@code clockNow - (gameNow - windowStart)} is the same whenever it is asked.
     * It is a reconstruction, not a record: a {@code /time set} or a stopped daylight cycle since the window
     * began is not seen. That is true of every reveal alike, so they still agree with each other.
     */
    public static long clockAtWindowStart(long clockNow, long gameNow, long windowStart) {
        return clockNow - (gameNow - windowStart);
    }

    /** The moon phase index (0 = full moon) for an overworld clock reading: the 8-day cycle of {@code Timelines.MOON}. */
    public static int moonPhaseIndex(long clock) {
        return (int) Math.floorMod(Math.floorDiv(clock, 24_000L), 8L);
    }

    /**
     * The game tick member {@code member} leaves the visit (E11), seeded: uniform over the last nine tenths of the
     * visit's {@code lengthTicks}, so nobody has gone the moment a window opens and everybody has gone by its last
     * tick. A reveal at time {@code t} omits a member whose departure is at or before {@code t}; a live visit
     * sends the member away at that tick. The same for every reveal and every player.
     */
    public static long departureTick(long base, int member, long windowStart, long lengthTicks) {
        long earliest = lengthTicks / 10;
        long span = Math.max(1L, lengthTicks - earliest);
        int offset = EcologyHash.toBoundedInt(stream(base, STREAM_DEPARTURE + member), (int) Math.min(span, Integer.MAX_VALUE));
        return windowStart + earliest + offset;
    }

    public static boolean hasDeparted(long departureTick, long now) {
        return departureTick >= 0 && departureTick <= now;
    }

    /** How long a visit of a species with this {@code lifetime_scale} lasts from its window's start: {@code round(L * scale)}, at most one period. */
    public static long lifetimeTicks(long visitTicks, long periodTicks, double lifetimeScale) {
        return Math.max(1L, Math.min(periodTicks, Math.round(visitTicks * lifetimeScale)));
    }

    /**
     * How long into a window a reveal is still tried: as long as the longest-lived species' visit
     * ({@code maxLifetimeScale}). Which species is only known after the roll, which then refuses a reveal past
     * its own end, so a late arrival sees every species for exactly as long as its visit lasts.
     */
    public static long revealSpanTicks(long visitTicks, long periodTicks, double maxLifetimeScale) {
        return lifetimeTicks(visitTicks, periodTicks, maxLifetimeScale);
    }

    /** The ledger bit of member {@code member}. */
    public static long bit(int member) {
        return 1L << member;
    }

    public static boolean isCaught(long mask, int member) {
        return member >= 0 && member < MAX_TRACKED_MEMBERS && (mask & bit(member)) != 0;
    }

    /** Whether every one of {@code count} members is in {@code mask}. */
    public static boolean isDepleted(long mask, int count) {
        if (count <= 0) return false;
        long all = count >= MAX_TRACKED_MEMBERS ? -1L : (1L << count) - 1;
        return (mask & all) == all;
    }
}
