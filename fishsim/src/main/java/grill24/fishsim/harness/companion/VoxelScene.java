package grill24.fishsim.harness.companion;

import grill24.fishsim.companion.BlockQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A headless stand-in for the world around a companion's owner (docs/fish-companions-sim-plan.md
 * §4.1): solid ground at and below {@link #groundTop}, solid boxes on top, and air boxes carved
 * back out of them (a corridor is a solid block with a tunnel through it), then water boxes filling
 * whatever they cover that isn't solid.
 */
public final class VoxelScene implements BlockQuery {

    public static final List<String> NAMES = List.of(
            "open", "corridor", "doorway", "forest", "stairwell", "low_ceiling", "pond");

    public final String name;
    public final int groundTop;
    private final List<int[]> solids = new ArrayList<>();
    private final List<int[]> airs = new ArrayList<>();
    private final List<int[]> waters = new ArrayList<>();
    /** Solids placed inside carved air (pillars in a cellar): these win over air. */
    private final List<int[]> plugs = new ArrayList<>();

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
        for (int[] b : plugs) if (inside(b, x, y, z)) return true;
        for (int[] b : airs) if (inside(b, x, y, z)) return false;
        if (y <= groundTop) return true;
        for (int[] b : solids) if (inside(b, x, y, z)) return true;
        return false;
    }

    @Override
    public boolean water(int x, int y, int z) {
        if (waters.isEmpty() || solid(x, y, z)) return false;
        for (int[] b : waters) if (inside(b, x, y, z)) return true;
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

    private VoxelScene plug(int x0, int y0, int z0, int x1, int y1, int z1) {
        plugs.add(new int[]{x0, y0, z0, x1, y1, z1});
        return this;
    }

    private VoxelScene water(int x0, int y0, int z0, int x1, int y1, int z1) {
        waters.add(new int[]{x0, y0, z0, x1, y1, z1});
        return this;
    }

    /** An open column of air from feet height {@code feet} up to (not including) {@code ceiling}. */
    private VoxelScene column(int x0, int z0, int x1, int z1, int feet, int ceiling) {
        return air(x0, feet, z0, x1, ceiling, z1);
    }

    public static VoxelScene named(String name) {
        return switch (name) {
            case "open" -> open();
            case "corridor" -> corridor();
            case "doorway" -> doorway();
            case "forest" -> forest();
            case "stairwell" -> stairwell();
            case "low_ceiling" -> lowCeiling();
            case "pond" -> pond();
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

    /**
     * A switchback stairwell, 3 wide with 3 blocks of headroom that follows the steps. The bottom
     * landing (x 0..2, z 0..3, feet 64) opens south through a door at z=−1; flight one climbs north
     * along x 0..2 to a landing at feet 70 (z 10..12, full width), and flight two climbs back south
     * along x 4..6 to a dead-end top landing at feet 76 (z 0..3). A wall at x=3 divides the flights.
     */
    public static VoxelScene stairwell() {
        VoxelScene s = new VoxelScene("stairwell", 63).solid(-1, 64, -1, 8, 84, 14);
        s.column(0, -1, 3, 0, 64, 67);                       // door
        s.column(0, 0, 3, 4, 64, 67);                        // bottom landing
        for (int k = 1; k <= 6; k++) s.column(0, 3 + k, 3, 4 + k, 64 + k, 67 + k);
        s.column(0, 10, 7, 13, 70, 73);                      // middle landing
        for (int k = 1; k <= 6; k++) s.column(4, 10 - k, 7, 11 - k, 70 + k, 73 + k);
        s.column(4, 0, 7, 4, 76, 79);                        // top landing
        return s;
    }

    /**
     * A 15×15 cellar with two blocks of headroom (the least a player fits under) and four pillars
     * at (±4, ±4).
     */
    public static VoxelScene lowCeiling() {
        VoxelScene s = new VoxelScene("low_ceiling", 63).solid(-8, 64, -8, 9, 68, 9);
        s.air(-7, 64, -7, 8, 66, 8);
        for (int x : new int[]{-4, 4}) {
            for (int z : new int[]{-4, 4}) s.plug(x, 64, z, x + 1, 66, z + 1);
        }
        return s;
    }

    /**
     * A pond dug into flat ground, filled to the brim (water surface at y=64): a one-deep shelf at
     * z 4..6 and five deep beyond it at z 7..16, across x −6..6.
     */
    public static VoxelScene pond() {
        return new VoxelScene("pond", 63)
                .air(-6, 63, 4, 7, 64, 7).water(-6, 63, 4, 7, 64, 7)
                .air(-6, 59, 7, 7, 64, 17).water(-6, 59, 7, 7, 64, 17);
    }
}
