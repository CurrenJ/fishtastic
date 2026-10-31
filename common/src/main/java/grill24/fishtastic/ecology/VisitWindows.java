package grill24.fishtastic.ecology;

import grill24.fishtastic.data.FishProfile;

/**
 * Deterministic visit windows (world fish exploration E1, E2, E5, planning decision P1). Every chunk
 * is a site; window {@code k} of a site spans game time {@code [t0 + k * T, t0 + k * T + L)}, where the
 * per-site phase {@code t0} in {@code [0, T)} is hashed (E1), so sites do not all open on one tick. Whether it is
 * active, where it ranks and whether a neighbour suppresses it depend only on
 * {@code (seed, chunk, time or window, params)} - never on which chunks are loaded or hold water, because
 * every neighbour chunk counts as a site (P1). So the server and every client agree, and the
 * evaluation order is irrelevant.
 *
 * <p>Suppression (E5): an active window loses when a different chunk within
 * {@link Params#suppressionRadiusChunks} holds an active window overlapping it in time with a higher
 * rank. A suppressed window still suppresses its own weaker neighbours, which keeps the rule a pure
 * function of the hashes (no chains to resolve).
 */
public final class VisitWindows {

    /**
     * The tunable numbers of the model. Shared by the Step 0 calculator and the Step 2 scheduler.
     *
     * @param periodTicks      T: ticks between window starts
     * @param visitTicks       L: ticks a window stays open ({@code <= periodTicks})
     * @param visitChance      probability that a site's window is active, before suppression
     * @param suppressionRadiusChunks R: neighbour radius, in chunks (Euclidean), 0 disables suppression
     */
    public record Params(long periodTicks, long visitTicks, double visitChance, int suppressionRadiusChunks) {

        /**
         * Rate-tuning candidate after the owner liked the five-minute duration (2026-10-30): T = 7200 ticks
         * (6 minutes), L = 6000 ticks (5 minutes), chance 0.024, R = 2 chunks. Under Step 0 assumptions this
         * yields about 2.7 starts/hour stationary, 29.5/56.2 travel passes/hour at 2.5/5 blocks/s.
         * The server reads live values from {@code fishtastic-server.properties} ({@code visits.*}); these
         * are the source defaults for those keys.
         */
        public static final Params DEFAULT = new Params(7200L, 6000L, 0.024, 2);

        public Params {
            if (periodTicks <= 0) throw new IllegalArgumentException("periodTicks must be positive: " + periodTicks);
            if (visitTicks <= 0 || visitTicks > periodTicks) {
                throw new IllegalArgumentException("visitTicks must be in (0, periodTicks]: " + visitTicks);
            }
            if (!(visitChance >= 0.0 && visitChance <= 1.0)) {
                throw new IllegalArgumentException("visitChance must be in [0, 1]: " + visitChance);
            }
            if (suppressionRadiusChunks < 0) {
                throw new IllegalArgumentException("suppressionRadiusChunks must be >= 0: " + suppressionRadiusChunks);
            }
        }
    }

    private VisitWindows() {
    }

    /**
     * The site's phase t0 in {@code [0, T)}: its windows start at {@code t0 + k * T}. Hashed per site
     * (window slot fixed at 0) so sites open and close at scattered ticks instead of pulsing together.
     */
    public static long phase(long seed, int chunkX, int chunkZ, Params params) {
        return Long.remainderUnsigned(
                EcologyHash.hash(seed, chunkX, chunkZ, 0L, EcologyHash.SALT_PHASE), params.periodTicks());
    }

    /** The site's window containing {@code gameTime}; floors, so negative and zero times are well defined. */
    public static long windowIndex(long seed, int chunkX, int chunkZ, long gameTime, Params params) {
        return Math.floorDiv(gameTime - phase(seed, chunkX, chunkZ, params), params.periodTicks());
    }

    public static long windowStart(long seed, int chunkX, int chunkZ, long window, Params params) {
        return phase(seed, chunkX, chunkZ, params) + window * params.periodTicks();
    }

    /** First tick after the window (exclusive end). */
    public static long windowEnd(long seed, int chunkX, int chunkZ, long window, Params params) {
        return windowStart(seed, chunkX, chunkZ, window, params) + params.visitTicks();
    }

    /** Whether {@code gameTime} lies inside the site's window {@code window} (start inclusive, end exclusive). */
    public static boolean isOpenAt(long seed, int chunkX, int chunkZ, long gameTime, long window, Params params) {
        long start = windowStart(seed, chunkX, chunkZ, window, params);
        return gameTime >= start && gameTime < start + params.visitTicks();
    }

    /**
     * Inclusive range {@code [first, last]} of the site's windows whose span overlaps
     * {@code [fromTick, toTick)}; empty ({@code first > last}) when none does. Because
     * {@code L <= T}, a span no longer than {@code L} overlaps at most two windows of a site.
     */
    public static long[] windowsOverlapping(long seed, int chunkX, int chunkZ, long fromTick, long toTick, Params params) {
        return windowsOverlapping(seed, chunkX, chunkZ, fromTick, toTick, params.visitTicks(), params);
    }

    /** As above, for windows taken to last {@code spanTicks} from their start (at most one period). */
    public static long[] windowsOverlapping(long seed, int chunkX, int chunkZ, long fromTick, long toTick, long spanTicks,
                                            Params params) {
        long t0 = phase(seed, chunkX, chunkZ, params);
        long first = Math.floorDiv(fromTick - spanTicks - t0, params.periodTicks()) + 1;
        long last = Math.floorDiv(toTick - 1 - t0, params.periodTicks());
        return new long[]{first, last};
    }

    public static long activityHash(long seed, int chunkX, int chunkZ, long window) {
        return EcologyHash.hash(seed, chunkX, chunkZ, window, EcologyHash.SALT_VISIT_ACTIVITY);
    }

    /** The chance test alone, before suppression. */
    public static boolean isActive(long seed, int chunkX, int chunkZ, long window, Params params) {
        return EcologyHash.toUnitDouble(activityHash(seed, chunkX, chunkZ, window)) < params.visitChance();
    }

    /** The window's rank (compare as unsigned); higher wins suppression. */
    public static long rank(long seed, int chunkX, int chunkZ, long window) {
        return EcologyHash.hash(seed, chunkX, chunkZ, window, EcologyHash.SALT_RANK);
    }

    /** Whether window aw of chunk a beats window bw of chunk b: rank, then x, then z, so it is a strict total order. */
    static boolean outranks(long seed, int ax, int az, long aw, int bx, int bz, long bw) {
        int byRank = Long.compareUnsigned(rank(seed, ax, az, aw), rank(seed, bx, bz, bw));
        if (byRank != 0) return byRank > 0;
        if (ax != bx) return ax > bx;
        if (az != bz) return az > bz;
        return aw > bw;
    }

    /**
     * Whether an active window at this chunk is knocked out by a higher-ranked active window of a
     * neighbour within R that overlaps it in time (at most two windows per neighbour). Every chunk in
     * range is evaluated, loaded or not, wet or not (P1).
     *
     * <p>Overlap is judged over {@link #hostedSpanTicks}, not L: a species' lifetime scale can keep its
     * visit up to a whole period, and the species is only known after this answer, so every window is
     * taken to last as long as the longest-lived visit could (E5: never two live visits within R).
     */
    public static boolean isSuppressed(long seed, int chunkX, int chunkZ, long window, Params params) {
        int radius = params.suppressionRadiusChunks();
        if (radius == 0) return false;
        long radiusSq = (long) radius * radius;
        long from = windowStart(seed, chunkX, chunkZ, window, params);
        long span = hostedSpanTicks(params);
        long to = from + span;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if ((dx == 0 && dz == 0) || (long) dx * dx + (long) dz * dz > radiusSq) continue;
                int nx = chunkX + dx;
                int nz = chunkZ + dz;
                long[] range = windowsOverlapping(seed, nx, nz, from, to, span, params);
                for (long k = range[0]; k <= range[1]; k++) {
                    if (isActive(seed, nx, nz, k, params)
                            && outranks(seed, nx, nz, k, chunkX, chunkZ, window)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** How long after its start a hosted visit can still be live: L stretched by the largest species lifetime scale, capped at T. */
    public static long hostedSpanTicks(Params params) {
        return VisitDerivation.revealSpanTicks(params.visitTicks(), params.periodTicks(),
                FishProfile.WorldEncounter.MAX_LIFETIME_SCALE);
    }

    /** Active and not suppressed: the window hosts a visit (depletion, water and loading are the caller's checks). */
    public static boolean hostsVisit(long seed, int chunkX, int chunkZ, long window, Params params) {
        return isActive(seed, chunkX, chunkZ, window, params)
                && !isSuppressed(seed, chunkX, chunkZ, window, params);
    }
}
