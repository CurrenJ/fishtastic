package grill24.fishsim.ambient;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A tiny synthetic world for the discovery and domain tests: per column a body of water of a given
 * depth whose surface is a given fraction of the top cell, plus cell overrides for collision tops
 * (waterlogged slabs, stairs) and fluid tops (flowing water), and a set of unloaded columns.
 *
 * <p>Deterministic and Minecraft-free, which is the point: the discovery's budgets, patch keys and
 * scan assembly are all testable without a client.
 */
final class SyntheticWater implements WaterSource {

    private final int minX, maxX, minZ, maxZ;
    private final Map<Long, Column> columns = new HashMap<>();
    private final Map<Long, Float> cellCollision = new HashMap<>();
    private final Map<Long, Float> cellFluid = new HashMap<>();
    private final Set<Long> unloadedColumns = new HashSet<>();

    long flagsReads;
    long fluidReads;
    long collisionReads;
    int peakCellOverrides;

    /**
     * One column of water: one or more stacked bodies, each occupying cells
     * {@code [topY - depth + 1, topY]} with its surface at {@code topY + fraction} (a body with
     * water above it reads as full where {@link #fluidTop} is asked).
     */
    private static final class Column {
        final int[] topY;
        final int[] depth;
        final float fraction;

        Column(int[] topY, int[] depth, float fraction) {
            this.topY = topY;
            this.depth = depth;
            this.fraction = fraction;
        }

        boolean isWater(int y) {
            for (int i = 0; i < topY.length; i++) {
                if (y <= topY[i] && y > topY[i] - depth[i]) return true;
            }
            return false;
        }

        float fluidTop(int y) {
            for (int i = 0; i < topY.length; i++) {
                if (y <= topY[i] && y > topY[i] - depth[i]) return y < topY[i] ? 1f : fraction;
            }
            return 1f;
        }
    }

    SyntheticWater(int minX, int maxX, int minZ, int maxZ) {
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
    }

    SyntheticWater water(int x, int z, int topY, int depth) {
        return water(x, z, topY, depth, 8f / 9f);
    }

    SyntheticWater water(int x, int z, int topY, int depth, float fraction) {
        columns.put(columnKey(x, z), new Column(new int[]{topY}, new int[]{depth}, fraction));
        return this;
    }

    /** Adds another body to the same column — a cave pool under a pond, a waterfall under an arch. */
    SyntheticWater body(int x, int z, int topY, int depth) {
        Column old = columns.get(columnKey(x, z));
        if (old == null) return water(x, z, topY, depth);
        int n = old.topY.length;
        int[] top = java.util.Arrays.copyOf(old.topY, n + 1);
        int[] dep = java.util.Arrays.copyOf(old.depth, n + 1);
        top[n] = topY;
        dep[n] = depth;
        columns.put(columnKey(x, z), new Column(top, dep, old.fraction));
        return this;
    }

    SyntheticWater dry(int x, int z) {
        columns.remove(columnKey(x, z));
        return this;
    }

    SyntheticWater unload(int x, int z) {
        unloadedColumns.add(columnKey(x, z));
        return this;
    }

    SyntheticWater load(int x, int z) {
        unloadedColumns.remove(columnKey(x, z));
        return this;
    }

    /** Puts a collision top inside one water cell — a waterlogged slab (0.5), stair (1.0) or fence. */
    SyntheticWater cellCollision(int x, int y, int z, float collisionTop) {
        cellCollision.put(cellKey(x, y, z), collisionTop);
        return this;
    }

    /** Overrides one cell's fluid surface height — flowing water is a fraction of a block. */
    SyntheticWater cellFluid(int x, int y, int z, float height) {
        cellFluid.put(cellKey(x, y, z), height);
        return this;
    }

    boolean inBounds(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    @Override
    public int flags(int x, int y, int z) {
        flagsReads++;
        if (unloadedColumns.contains(columnKey(x, z))) return 0;
        int f = LOADED;
        Column c = columns.get(columnKey(x, z));
        if (c != null && c.isWater(y)) f |= WATER;
        if (cellCollision.containsKey(cellKey(x, y, z))) f |= COLLISION;
        return f;
    }

    @Override
    public float fluidTop(int x, int y, int z) {
        fluidReads++;
        Float override = cellFluid.get(cellKey(x, y, z));
        if (override != null) return override;
        Column c = columns.get(columnKey(x, z));
        return c == null ? 1f : c.fluidTop(y);
    }

    @Override
    public float collisionTop(int x, int y, int z) {
        collisionReads++;
        Float v = cellCollision.get(cellKey(x, y, z));
        return v == null ? 0f : v;
    }

    long totalReads() {
        return flagsReads + fluidReads + collisionReads;
    }

    void resetCounters() {
        flagsReads = 0;
        fluidReads = 0;
        collisionReads = 0;
    }

    static long columnKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    static long cellKey(int x, int y, int z) {
        return ((long) (x & 0xFFFFF) << 44) | ((long) (y & 0xFFFFF) << 24) | (z & 0xFFFFFF);
    }
}
