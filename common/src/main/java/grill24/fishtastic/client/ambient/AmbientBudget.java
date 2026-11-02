package grill24.fishtastic.client.ambient;

/**
 * One density policy bounds discovery, contexts, simulation and rendering together.
 *
 * <p>The discovery budget ({@link #columnsPerTick}, {@link #cellsPerTick}) does not scale with
 * density: how far ahead water is found must keep up with how fast the player travels, whatever
 * the population. Measured (ambientswim, open ocean): 512 cells a tick keeps the water found to
 * the full lead at 10 b/s for ~0.15 ms a tick; at 256 a 10 b/s swim fell to a 15-block lead.
 *
 * <p>Nor does the {@link #radius}: density sets how many fish there are, the range sets where
 * they can be. Measured sweep (ambientswim, 5.6 b/s, fish per area held constant): radius 17 kept
 * 43% of the stationary fish in view while swimming and 174 spawns were seen appearing; 24 kept
 * 73% (151 seen); 32 kept ~105% with ~10 seen, spawns landing ~33 blocks out in the murk; 40 and
 * 48 fell back (68%, 54%) because the leading edge outgrew discovery. At 32 and density 50 the
 * ambient tick costs ~0.9 ms and allocates ~0.6 MB a tick.
 */
public record AmbientBudget(int density, int radius, int columnsPerTick, int cellsPerTick,
                            int domains, int individuals) {
    /** Fish alive at density 100 (112 at the default 50): the old 64 at radius 24, held per area at {@link #RADIUS}. */
    public static final int MAX_INDIVIDUALS = 224;
    /**
     * Bodies of water simulated at once at density 100 (4 at 50). Was 4 at a 24-block range; a
     * 32-block window holds more separate water, and each slot costs one engine step a tick.
     */
    public static final int MAX_DOMAINS = 8;
    /** Horizontal range of ambient fish, in blocks, at every density. */
    public static final int RADIUS = 32;
    public static final int DISCOVERY_COLUMNS_PER_TICK = 48;
    public static final int DISCOVERY_CELLS_PER_TICK = 512;
    public static AmbientBudget forDensity(int value) {
        int d = Math.max(0, Math.min(100, value));
        if (d == 0) return new AmbientBudget(0, 0, 0, 0, 0, 0);
        return new AmbientBudget(d, RADIUS, DISCOVERY_COLUMNS_PER_TICK, DISCOVERY_CELLS_PER_TICK,
                Math.max(1, (MAX_DOMAINS * d + 99) / 100), Math.max(1, (MAX_INDIVIDUALS * d + 99) / 100));
    }

    /** Approximate usable volume; representability and whole-body placement impose stricter gates. */
    public int target(double waterVolume) {
        if (!Double.isFinite(waterVolume) || waterVolume <= 0 || density == 0) return 0;
        return Math.min(individuals, Math.max(1,
                (int) Math.floor(waterVolume * density / 1200.0)));
    }
}
