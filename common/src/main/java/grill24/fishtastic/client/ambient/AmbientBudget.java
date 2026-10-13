package grill24.fishtastic.client.ambient;

/** One density policy bounds discovery, contexts, simulation and rendering together. */
public record AmbientBudget(int density, int radius, int columnsPerTick, int cellsPerTick,
                            int domains, int individuals) {
    public static final int MAX_INDIVIDUALS = 64;
    public static AmbientBudget forDensity(int value) {
        int d = Math.max(0, Math.min(100, value));
        if (d == 0) return new AmbientBudget(0, 0, 0, 0, 0, 0);
        return new AmbientBudget(d, Math.max(8, (int) Math.ceil(24 * Math.sqrt(d / 100.0))),
                Math.max(4, 48 * d / 100), Math.max(64, 512 * d / 100),
                Math.max(1, (4 * d + 99) / 100), Math.max(1, (MAX_INDIVIDUALS * d + 99) / 100));
    }

    /** Approximate usable volume; representability and whole-body placement impose stricter gates. */
    public int target(double waterVolume) {
        if (!Double.isFinite(waterVolume) || waterVolume <= 0 || density == 0) return 0;
        return Math.min(individuals, Math.max(1,
                (int) Math.floor(waterVolume * density / 1200.0)));
    }
}
