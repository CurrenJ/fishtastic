package grill24.fishsim.domain;

/**
 * Horizontal run analysis of a voxel occupancy grid: the maximal consecutive occupied spans along
 * <b>either</b> horizontal axis (lateral rows and depth columns are fully symmetric — the planar
 * swim model treats them identically). The size gate reads the domain's longest run — a fish that
 * has one long corridor to swim in free-swims even if the cross arm is short.
 */
public final class RunLengths {

    private final int longestRunCells;

    public RunLengths(boolean[][][] occupancy) {
        int sx = occupancy.length, sy = occupancy[0].length, sz = occupancy[0][0].length;
        int longest = 0;
        for (int iy = 0; iy < sy; iy++) {
            // Lateral runs (along x, per depth column)…
            for (int iz = 0; iz < sz; iz++) {
                int run = 0;
                for (int ix = 0; ix < sx; ix++) {
                    if (occupancy[ix][iy][iz]) {
                        run++;
                        if (run > longest) longest = run;
                    } else {
                        run = 0;
                    }
                }
            }
            // …and depth runs (along z, per lateral column).
            for (int ix = 0; ix < sx; ix++) {
                int run = 0;
                for (int iz = 0; iz < sz; iz++) {
                    if (occupancy[ix][iy][iz]) {
                        run++;
                        if (run > longest) longest = run;
                    } else {
                        run = 0;
                    }
                }
            }
        }
        this.longestRunCells = longest;
    }

    public int longestRunCells() {
        return longestRunCells;
    }

    /** The longest run's usable interior length in blocks, after the wall inset on both ends. */
    public float longestRunInterior(float inset) {
        return longestRunCells - 2f * inset;
    }
}
