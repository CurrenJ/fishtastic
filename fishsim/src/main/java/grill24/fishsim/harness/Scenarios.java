package grill24.fishsim.harness;

import grill24.fishsim.core.FishSpec;

import java.util.Arrays;
import java.util.Random;

/** Shared scenario factories for the headless runner and the viewer. */
public final class Scenarios {

    private Scenarios() {}

    /** Deterministic mixed swimmer population, matching the invariant tests' distribution. */
    public static FishSpec[] specs(int n, long seed) {
        Random r = new Random(seed * 31 + n);
        FishSpec[] specs = new FishSpec[n];
        for (int i = 0; i < n; i++) {
            specs[i] = new FishSpec(0.06f + r.nextFloat() * 0.2f, true, r.nextBoolean(), r.nextInt(3));
        }
        return specs;
    }

    /** Named domain occupancies: {@code L}, {@code 2x2slab}, or {@code WxHxD} full grids. */
    public static boolean[][][] occupancy(String name) {
        if (name.equals("L")) {
            boolean[][][] g = new boolean[3][1][3];
            for (int ix = 0; ix < 3; ix++) g[ix][0][0] = true;
            g[2][0][1] = true;
            g[2][0][2] = true;
            return g;
        }
        String dims = name.equals("2x2slab") ? "2x1x2" : name;
        String[] parts = dims.split("x");
        if (parts.length != 3) throw new IllegalArgumentException("Unknown domain: " + name);
        boolean[][][] g = new boolean[Integer.parseInt(parts[0])][Integer.parseInt(parts[1])][Integer.parseInt(parts[2])];
        for (boolean[][] a : g) for (boolean[] b : a) Arrays.fill(b, true);
        return g;
    }
}
