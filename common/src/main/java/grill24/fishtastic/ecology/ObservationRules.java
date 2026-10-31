package grill24.fishtastic.ecology;

/**
 * The numbers and the geometry of spyglass observation (world fish exploration E7, E16, E17),
 * shared by the client detector and the server check so the two cannot drift. Pure: no level, no
 * entity, nothing but vectors and numbers.
 *
 * <h2>Cones</h2>
 * The client holds a fish while it is inside {@link #CLIENT_CONE_HALF_ANGLE_DEG} of the crosshair
 * (widened by the fish's own apparent size). A scoped spyglass shows roughly a 7 degree field, so
 * 3 degrees keeps the cone inside the picture while forgiving a small, fast fish. The server judges
 * the same direction later, from its own copy of the fish and the look the player has now, so it
 * allows {@link #SERVER_CONE_HALF_ANGLE_DEG} plus the angle that the position's uncertainty subtends.
 */
public final class ObservationRules {

    private ObservationRules() {
    }

    /** Half angle of the aim cone the client holds a fish in, degrees. */
    public static final double CLIENT_CONE_HALF_ANGLE_DEG = 3.0;
    /** Half angle the server accepts before position slack, degrees (a little over 2x the client's). */
    public static final double SERVER_CONE_HALF_ANGLE_DEG = 8.0;
    /** A fish's own radius for the client cone, blocks: a long fish is easier to hold than a small one. */
    public static final double FISH_RADIUS = 0.5;

    /** Continuous hold needed on one fish, ticks (1 second). */
    public static final int HOLD_TICKS = 20;
    /** Ticks the aim may leave the fish before the hold restarts (a spyglass hand is not steady). */
    public static final int HOLD_GRACE_TICKS = 4;
    /** Ticks before the client sends the same species again after an attempt (the server says nothing on a refusal). */
    public static final int RESEND_COOLDOWN_TICKS = 200;

    /** Farthest a fish may be and still be observed, blocks. */
    public static final double MAX_RANGE = 96.0;
    /** Extra range the server allows for the player moving and the fish having moved since, blocks. */
    public static final double RANGE_SLACK = 6.0;

    /** Visit members: positions on the server and the client copy differ by a few ticks of swimming. */
    public static final double VISIT_POSITION_SLACK = 3.0;
    /**
     * Ambient fish: clients agree on the home chunk but not the exact spot (E12), and a fish may
     * drift up to the leash margin outside its chunk. Half a chunk diagonal (11.4) plus the leash (8).
     */
    public static final double AMBIENT_POSITION_SLACK = 20.0;
    /** Ticks a lifespan may be outside the server's clock and still count (client and server clocks differ). */
    public static final long LIFESPAN_CLOCK_TOLERANCE_TICKS = 100L;

    /** Minimum ticks between observation packets from one player; faster ones are dropped. */
    public static final int MIN_INTERVAL_TICKS = 20;

    /**
     * The angle between {@code look} and the direction to a target, degrees. Neither vector needs to
     * be unit length; a zero vector gives 180 (never inside any cone).
     */
    public static double angleDeg(double lookX, double lookY, double lookZ, double toX, double toY, double toZ) {
        double lookLength = Math.sqrt(lookX * lookX + lookY * lookY + lookZ * lookZ);
        double toLength = Math.sqrt(toX * toX + toY * toY + toZ * toZ);
        if (lookLength < 1e-9 || toLength < 1e-9) return 180.0;
        double cos = (lookX * toX + lookY * toY + lookZ * toZ) / (lookLength * toLength);
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cos))));
    }

    /** The angle {@code radius} subtends at {@code distance}, degrees; 90 when the target is inside the radius. */
    public static double subtendedDeg(double radius, double distance) {
        if (distance <= radius) return 90.0;
        return Math.toDegrees(Math.asin(radius / distance));
    }

    /**
     * Whether a target is inside a cone around {@code look}: its direction is within
     * {@code halfAngleDeg} plus the angle its {@code radius} of uncertainty subtends at that distance.
     * A target closer than its radius is always inside (the uncertainty swallows the aim).
     */
    public static boolean inCone(double lookX, double lookY, double lookZ, double toX, double toY, double toZ,
                                 double halfAngleDeg, double radius) {
        double distance = Math.sqrt(toX * toX + toY * toY + toZ * toZ);
        if (distance <= radius) return true;
        return angleDeg(lookX, lookY, lookZ, toX, toY, toZ) <= halfAngleDeg + subtendedDeg(radius, distance);
    }

    /** The client's detector cone for a fish. */
    public static boolean clientCone(double lookX, double lookY, double lookZ, double toX, double toY, double toZ) {
        return inCone(lookX, lookY, lookZ, toX, toY, toZ, CLIENT_CONE_HALF_ANGLE_DEG, FISH_RADIUS);
    }

    /** The server's cone for a target whose position is only known to within {@code slack} blocks. */
    public static boolean serverCone(double lookX, double lookY, double lookZ, double toX, double toY, double toZ,
                                     double slack) {
        return inCone(lookX, lookY, lookZ, toX, toY, toZ, SERVER_CONE_HALF_ANGLE_DEG, slack);
    }

    /** Whether the server accepts a target at {@code distance}: inside {@link #MAX_RANGE} plus the slack. */
    public static boolean inRange(double distance, double positionSlack) {
        return distance <= MAX_RANGE + RANGE_SLACK + positionSlack;
    }

    /**
     * Whether a lifespan {@code [arrival, departure)} counts as present at the server's {@code now},
     * allowing {@link #LIFESPAN_CLOCK_TOLERANCE_TICKS} either side.
     */
    public static boolean lifespanPresent(long arrivalTick, long departureTick, long now) {
        return now >= arrivalTick - LIFESPAN_CLOCK_TOLERANCE_TICKS
                && now < departureTick + LIFESPAN_CLOCK_TOLERANCE_TICKS;
    }

    /**
     * Pure hold progress: given how many ticks the same fish has been continuously aimed at, the
     * fraction of the hold done in [0, 1].
     */
    public static float progress(int heldTicks) {
        return Math.max(0f, Math.min(1f, heldTicks / (float) HOLD_TICKS));
    }
}
