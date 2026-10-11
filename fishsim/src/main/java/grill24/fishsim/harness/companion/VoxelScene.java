package grill24.fishsim.harness.companion;

import grill24.fishsim.companion.BlockQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A headless stand-in for the world around a companion's owner (docs/fish-companions-sim-plan.md
 * §4.1): solid ground at and below {@link #groundTop}, solid boxes on top, and air boxes carved
 * back out of them (a corridor is a solid block with a tunnel through it), then water boxes filling
 * whatever they cover that isn't solid. Partial blocks (slabs, carpets) and a door that opens and
 * closes are for things that walk on the ground (docs/fish-companions-benthic-follow.md).
 */
public final class VoxelScene implements BlockQuery {

    public static final List<String> NAMES = List.of(
            "open", "corridor", "doorway", "forest", "stairwell", "low_ceiling", "pond",
            "half_stairs", "slab_path", "terraces", "trench", "cliff", "door_wall", "benthic_park");

    public final String name;
    public final int groundTop;
    private final List<int[]> solids = new ArrayList<>();
    private final List<int[]> airs = new ArrayList<>();
    private final List<int[]> waters = new ArrayList<>();
    /** Solids placed inside carved air (pillars in a cellar): these win over air. */
    private final List<int[]> plugs = new ArrayList<>();
    /** Partial blocks: a box of cells solid only up to {@link #partialTops} of the way (slabs, carpets). */
    private final List<int[]> partials = new ArrayList<>();
    private final List<Float> partialTops = new ArrayList<>();
    /** A door that can be shut: solid while {@link #doorClosed}. */
    private int[] door;
    private boolean doorClosed;
    private int version;

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
        if (doorClosed && inside(door, x, y, z)) return true;
        for (int[] b : partials) if (inside(b, x, y, z)) return true;
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

    @Override
    public float top(int x, int y, int z) {
        if (doorClosed && inside(door, x, y, z)) return 1f;
        for (int i = 0; i < partials.size(); i++) if (inside(partials.get(i), x, y, z)) return partialTops.get(i);
        return 1f;
    }

    /** Whether this scene has a door that can close. */
    public boolean hasDoor() {
        return door != null;
    }

    public boolean doorClosed() {
        return doorClosed;
    }

    /** The door's cells: x0, y0, z0, x1, y1, z1 (exclusive), or null. */
    public int[] door() {
        return door;
    }

    /** Opens or shuts the door; a change bumps {@link #version} so cached views of the blocks refresh. */
    public void setDoorClosed(boolean closed) {
        if (door == null || closed == doorClosed) return;
        doorClosed = closed;
        version++;
    }

    private Boolean pendingDoor;

    /** Asks for the door to be shut or opened; {@link #applyDoor} does it once the doorway is clear. */
    public void requestDoor(boolean closed) {
        if (door != null) pendingDoor = closed;
    }

    /**
     * Applies a requested door change if {@code clear} says nothing is in the doorway (a door is
     * never shut on anything). Opening always goes ahead.
     */
    public void applyDoor(java.util.function.Predicate<int[]> clear) {
        if (pendingDoor == null) return;
        if (pendingDoor && !clear.test(door)) return;
        setDoorClosed(pendingDoor);
        pendingDoor = null;
    }

    /** Counts block changes, for anything caching what it has sampled. */
    public int version() {
        return version;
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

    /** A box of cells solid up to {@code top} (0.5 a slab), over carved air like a plug. */
    private VoxelScene partial(int x0, int y0, int z0, int x1, int y1, int z1, float top) {
        partials.add(new int[]{x0, y0, z0, x1, y1, z1});
        partialTops.add(top);
        return this;
    }

    /** A rise of solid ground, top at {@code height} blocks above the scene's ground (0.5 steps make slabs). */
    private VoxelScene raised(int x0, int z0, int x1, int z1, double height) {
        int base = groundTop + 1;
        int whole = (int) Math.floor(height + 1e-9);
        if (whole > 0) solid(x0, base, z0, x1, base + whole, z1);
        double frac = height - whole;
        if (frac > 1e-9) partial(x0, base + whole, z0, x1, base + whole + 1, z1, (float) frac);
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
            case "half_stairs" -> halfStairs(new VoxelScene("half_stairs", 63), 0, 0);
            case "slab_path" -> slabPath(new VoxelScene("slab_path", 63), 0, 0);
            case "terraces" -> terraces(new VoxelScene("terraces", 63), 0, 0);
            case "trench" -> trench(new VoxelScene("trench", 63), 0, 0);
            case "cliff" -> cliff(new VoxelScene("cliff", 63), 0, 0);
            case "door_wall" -> doorWall(new VoxelScene("door_wall", 63), 0, 0);
            case "benthic_park" -> benthicPark();
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

    // -- Ground scenes (docs/fish-companions-benthic-follow.md section 7) --

    /**
     * A flight of half-block steps (how stairs climb underfoot) going north from z=4, 3 wide
     * (x -1..1), eight steps up to a landing 4 blocks high at z 12..17, open at the sides.
     */
    static VoxelScene halfStairs(VoxelScene s, int ox, int oz) {
        for (int k = 0; k < 8; k++) s.raised(ox - 1, oz + 4 + k, ox + 2, oz + 5 + k, 0.5 * (k + 1));
        return s.raised(ox - 3, oz + 12, ox + 4, oz + 18, 4);
    }

    /**
     * A boardwalk north from z=3, 3 wide (x -1..1), of slabs and blocks: up half a block, a block,
     * a block and a half, back down by halves to the ground at z=22; and a strip of carpet at z -3..-1.
     */
    static VoxelScene slabPath(VoxelScene s, int ox, int oz) {
        s.raised(ox - 1, oz + 3, ox + 2, oz + 7, 0.5);
        s.raised(ox - 1, oz + 7, ox + 2, oz + 11, 1.0);
        s.raised(ox - 1, oz + 11, ox + 2, oz + 14, 1.5);
        s.raised(ox - 1, oz + 14, ox + 2, oz + 18, 1.0);
        s.raised(ox - 1, oz + 18, ox + 2, oz + 22, 0.5);
        return s.partial(ox - 2, s.groundTop + 1, oz - 3, ox + 3, s.groundTop + 2, oz, 1f / 16f);
    }

    /** Terraces a block apart going north from z=4, 5 wide (x -2..2): up three and back down two. */
    static VoxelScene terraces(VoxelScene s, int ox, int oz) {
        double[] heights = {1, 2, 3, 2, 1};
        for (int i = 0; i < heights.length; i++) {
            s.raised(ox - 2, oz + 4 + 4 * i, ox + 3, oz + 8 + 4 * i, heights[i]);
        }
        return s;
    }

    /** A trench two blocks wide (z 8..9) and six deep across x -6..6: jump it or go round. */
    static VoxelScene trench(VoxelScene s, int ox, int oz) {
        return s.air(ox - 6, s.groundTop - 5, oz + 8, ox + 7, s.groundTop + 1, oz + 10);
    }

    /** A cliff five blocks high from z=6 north, across x -8..8; the ladder up it is at x=0, z=5. */
    static VoxelScene cliff(VoxelScene s, int ox, int oz) {
        return s.raised(ox - 8, oz + 6, ox + 9, oz + 30, 5);
    }

    /** A wall across z=10 (x -12..12, four high) with a door at x=0 that can be shut. */
    static VoxelScene doorWall(VoxelScene s, int ox, int oz) {
        s.solid(ox - 12, s.groundTop + 1, oz + 10, ox + 13, s.groundTop + 5, oz + 11)
                .air(ox, s.groundTop + 1, oz + 10, ox + 1, s.groundTop + 3, oz + 11);
        s.door = new int[]{ox, s.groundTop + 1, oz + 10, ox + 1, s.groundTop + 3, oz + 11};
        return s;
    }

    /**
     * Everything a ground walker meets, side by side, to drive round in the viewer: half stairs at
     * x=0, the slab path at x=-8, terraces at x=8, a trench at x=-16 and a doorway at x=16.
     */
    static VoxelScene benthicPark() {
        VoxelScene s = new VoxelScene("benthic_park", 63);
        halfStairs(s, 0, 0);
        slabPath(s, -8, 0);
        terraces(s, 8, 0);
        s.air(-22, s.groundTop - 5, 8, -11, s.groundTop + 1, 10);
        s.solid(13, s.groundTop + 1, 10, 26, s.groundTop + 5, 11).air(16, s.groundTop + 1, 10, 17, s.groundTop + 3, 11);
        return s;
    }
}
