package grill24.fishsim.harness.companion;

import grill24.fishsim.companion.BlockQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A headless stand-in for the world around a companion's owner (docs/fish-companions-sim-plan.md
 * §4.1): solid ground at and below {@link #groundTop}, solid boxes on top, and air boxes carved
 * back out of them (a corridor is a solid block with a tunnel through it).
 */
public final class VoxelScene implements BlockQuery {

    public static final List<String> NAMES = List.of("open", "corridor", "doorway", "forest");

    public final String name;
    public final int groundTop;
    private final List<int[]> solids = new ArrayList<>();
    private final List<int[]> airs = new ArrayList<>();

    private VoxelScene(String name, int groundTop) {
        this.name = name;
        this.groundTop = groundTop;
    }

    /** The feet height of anyone standing on the ground. */
    public double feetY() {
        return groundTop + 1;
    }

    @Override
    public boolean solid(int x, int y, int z) {
        for (int[] b : airs) if (inside(b, x, y, z)) return false;
        if (y <= groundTop) return true;
        for (int[] b : solids) if (inside(b, x, y, z)) return true;
        return false;
    }

    private static boolean inside(int[] b, int x, int y, int z) {
        return x >= b[0] && y >= b[1] && z >= b[2] && x < b[3] && y < b[4] && z < b[5];
    }

    private VoxelScene solid(int x0, int y0, int z0, int x1, int y1, int z1) {
        solids.add(new int[]{x0, y0, z0, x1, y1, z1});
        return this;
    }

    private VoxelScene air(int x0, int y0, int z0, int x1, int y1, int z1) {
        airs.add(new int[]{x0, y0, z0, x1, y1, z1});
        return this;
    }

    public static VoxelScene named(String name) {
        return switch (name) {
            case "open" -> open();
            case "corridor" -> corridor();
            case "doorway" -> doorway();
            case "forest" -> forest();
            default -> throw new IllegalArgumentException("unknown scene " + name + " (have " + NAMES + ")");
        };
    }

    /** Flat ground, nothing else. */
    public static VoxelScene open() {
        return new VoxelScene("open", 63);
    }

    /**
     * A 1-wide, 2-tall tunnel with a roof: north along x=0 from z=2 to z=29, then an L turn east
     * along z=29 to x=21, where it opens out.
     */
    public static VoxelScene corridor() {
        return new VoxelScene("corridor", 63)
                .solid(-2, 64, 2, 3, 67, 31)
                .solid(0, 64, 27, 24, 67, 32)
                .air(0, 64, 0, 1, 66, 30)
                .air(0, 64, 29, 24, 66, 30);
    }

    /** A wall across z=10 with a single door at x=0. */
    public static VoxelScene doorway() {
        return new VoxelScene("doorway", 63)
                .solid(-12, 64, 10, 13, 68, 11)
                .air(0, 64, 10, 1, 66, 11);
    }

    /** Tree trunks on a jittered grid with leaf canopies, leaving the centre clear. */
    public static VoxelScene forest() {
        VoxelScene s = new VoxelScene("forest", 63);
        Random r = new Random(7);
        for (int gx = -24; gx <= 24; gx += 5) {
            for (int gz = -24; gz <= 24; gz += 5) {
                int x = gx + r.nextInt(3) - 1, z = gz + r.nextInt(3) - 1;
                if (Math.abs(x) < 2 && Math.abs(z) < 2) continue;
                int h = 4 + r.nextInt(2);
                s.solid(x, 64, z, x + 1, 64 + h, z + 1);
                s.solid(x - 1, 64 + h - 1, z - 1, x + 2, 64 + h + 1, z + 2);
            }
        }
        return s;
    }
}
