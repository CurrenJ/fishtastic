package grill24.fishtastic.server;

import java.util.Locale;

/**
 * Dev/self-test counters for the interactive-school performance plan
 * (docs/interactive-fish-schools-performance-plan.md, step 0). Timers run only while
 * {@link #enabled}; the plain counters always increment (a static field add). Server thread only.
 */
public final class FishSchoolPerf {
    /** Set by the {@code fishschoolperf} self-test. */
    public static boolean enabled;

    /**
     * The top-level parts of {@link FishSchoolManager#tick}; {@link #REGION} is counted inside them
     * too. Timed ticks are those of levels with a player or a school (see {@link #skipLevel}).
     */
    public enum Phase { SCHEDULE, ADVANCE, VISITS, HOOKS, SYNC, REGION }

    private static final long[] NANOS = new long[Phase.values().length];
    private static final long[] MAX_NANOS = new long[Phase.values().length];
    private static long ticks, tickNanos, maxTickNanos;
    /** {@code validWaterCell} calls, everywhere. */
    static long waterCellChecks;
    /** Connected-water searches, the cells they returned and the biome/zone checks they made. */
    static long regionScans, regionCells, zoneChecks;
    /** Reachability searches on hook checks, the cells they visited, and the most one visited. */
    static long pathSearches, pathCells, maxPathCells;
    /** {@code Water} inputs applied to a school's sim (each one a domain rebuild on every copy). */
    static long waterInputs;

    private FishSchoolPerf() {}

    /**
     * Set per {@link FishSchoolManager#tick} for levels with no players and no schools: every
     * dimension's manager ticks, and the empty ones would otherwise dilute each mean ~3x.
     */
    static boolean skipLevel;

    static long start() {
        return enabled && !skipLevel ? System.nanoTime() : 0L;
    }

    static void end(Phase phase, long startedAt) {
        if (!enabled || startedAt == 0L) return;
        long spent = System.nanoTime() - startedAt;
        NANOS[phase.ordinal()] += spent;
        MAX_NANOS[phase.ordinal()] = Math.max(MAX_NANOS[phase.ordinal()], spent);
    }

    static void endTick(long startedAt) {
        if (!enabled || startedAt == 0L) return;
        long spent = System.nanoTime() - startedAt;
        ticks++;
        tickNanos += spent;
        maxTickNanos = Math.max(maxTickNanos, spent);
    }

    static void path(int visited) {
        if (!enabled) return;
        pathSearches++;
        pathCells += visited;
        maxPathCells = Math.max(maxPathCells, visited);
    }

    public static void reset() {
        java.util.Arrays.fill(NANOS, 0L);
        java.util.Arrays.fill(MAX_NANOS, 0L);
        ticks = tickNanos = maxTickNanos = 0L;
        waterCellChecks = regionScans = regionCells = zoneChecks = 0L;
        pathSearches = pathCells = maxPathCells = 0L;
        waterInputs = 0L;
    }

    public static long waterInputs() { return waterInputs; }
    public static long regionScans() { return regionScans; }

    /** One line: mean and worst per tick for the whole tick and each phase, then the counters per tick. */
    public static String report() {
        long n = Math.max(1, ticks);
        StringBuilder out = new StringBuilder(String.format(Locale.ROOT,
                "ticks=%d tick mean=%.1fus max=%.1fus", ticks, tickNanos / 1e3 / n, maxTickNanos / 1e3));
        for (Phase phase : Phase.values()) {
            out.append(String.format(Locale.ROOT, " %s mean=%.1fus max=%.1fus", phase.name().toLowerCase(Locale.ROOT),
                    NANOS[phase.ordinal()] / 1e3 / n, MAX_NANOS[phase.ordinal()] / 1e3));
        }
        out.append(String.format(Locale.ROOT,
                " | per tick: waterCellChecks=%.0f regionScans=%.3f regionCells=%.0f zoneChecks=%.0f"
                        + " pathSearches=%.3f pathCells=%.1f | maxPathCells=%d waterInputs=%d",
                waterCellChecks / (double) n, regionScans / (double) n, regionCells / (double) n,
                zoneChecks / (double) n, pathSearches / (double) n, pathCells / (double) n, maxPathCells, waterInputs));
        return out.toString();
    }
}
