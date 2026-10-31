package grill24.fishtastic.ecology;

/**
 * The identity of one visit: the site (dimension and chunk) and its window index. A pure function of
 * the schedule, so the same visit has the same id whenever and by whoever it is revealed; depletion is
 * keyed by it (world fish exploration E1, E14).
 */
public record VisitId(String dimension, int chunkX, int chunkZ, long window) {

    /** The id of the window of {@code (chunkX, chunkZ)} that contains {@code gameTime}. */
    public static VisitId at(String dimension, long seed, int chunkX, int chunkZ, long gameTime, VisitWindows.Params params) {
        return new VisitId(dimension, chunkX, chunkZ, VisitWindows.windowIndex(seed, chunkX, chunkZ, gameTime, params));
    }

    public long start(long seed, VisitWindows.Params params) {
        return VisitWindows.windowStart(seed, chunkX, chunkZ, window, params);
    }

    /** First tick after the window's open span (exclusive end of the L-tick window). */
    public long end(long seed, VisitWindows.Params params) {
        return VisitWindows.windowEnd(seed, chunkX, chunkZ, window, params);
    }

    @Override
    public String toString() {
        return dimension + "@" + chunkX + "," + chunkZ + "#" + window;
    }
}
