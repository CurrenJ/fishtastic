package grill24.fishtastic.fishtank;

import java.util.ArrayList;
import java.util.List;

/**
 * The solid parts of a cosmetic structure as a list of boxes fish steer around
 * (docs/fish-shelters.md §12.3), worked out in the structure's own build units — the grid its parts
 * were captured on, before {@code scale}, facing south — so they are turned and placed at
 * placement exactly like a shelter ({@link ShelterGeometry}).
 *
 * <p>Pure geometry with no Minecraft types: the caller hands over where each part's block stands,
 * its block shape as boxes in the unit block, and whether the part is soft. Every solid part is
 * rasterised onto one grid of {@link #RES} voxels per build unit, so a fence post is a post and not
 * a cube, and the voxels are greedy-merged into boxes.
 *
 * <p><b>Exact, never coarsened.</b> The boxes cover the filled voxels and nothing else, so an
 * opening in a structure — an arch, a doorway, the gap a pass-through swims — stays exactly as open
 * as it is drawn. There is no cap on the count: a fish reads only the boxes binned near it, so a
 * long list costs it nothing, while merging boxes to shorten the list would fill those openings.
 *
 * <p><b>Positions are not rounded.</b> A part stands where the renderer draws it, which for a
 * hand-authored structure can be half a cell off the grid (the Dynamic Duo's two blocks are), so
 * parts are placed by their exact build-unit position, not by {@link CosmeticStructure#partCells}.
 * A voxel is filled if a part's shape overlaps it at all, so the boxes never let a fish through
 * geometry it can see; a sliver thinner than a voxel grows to one.
 */
public final class ObstacleGeometry {

    /** Voxels per build unit along each axis. */
    public static final int RES = 4;

    /**
     * One part: the min corner of its unit block in build units, its block shape as unit-block boxes
     * ({@code minX, minY, minZ, maxX, maxY, maxZ}), and whether fish may brush through it.
     */
    public record Part(float x, float y, float z, List<float[]> shape, boolean soft) {}

    /** A merged box in voxels of {@code 1 / RES} build units, max exclusive. */
    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        /** Build-unit bounds: {@code minX, minY, minZ, maxX, maxY, maxZ}. */
        public float[] buildBounds() {
            float r = RES;
            return new float[]{minX / r, minY / r, minZ / r, maxX / r, maxY / r, maxZ / r};
        }
    }

    private ObstacleGeometry() {}

    /** Overlap below this many voxels is a shared face, not an overlap. */
    private static final float TOUCH = 1e-3f;

    /** The filled voxels of every solid part, as a dense grid with its voxel origin. */
    public record Voxels(boolean[][][] filled, int originX, int originY, int originZ) {}

    public static Voxels rasterise(List<Part> parts) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        List<int[]> spans = new ArrayList<>();
        for (Part p : parts) {
            if (p.soft()) continue;
            for (float[] a : p.shape()) {
                int[] s = {lo(p.x() + a[0]), lo(p.y() + a[1]), lo(p.z() + a[2]),
                        hi(p.x() + a[3]), hi(p.y() + a[4]), hi(p.z() + a[5])};
                if (s[0] >= s[3] || s[1] >= s[4] || s[2] >= s[5]) continue;
                spans.add(s);
                minX = Math.min(minX, s[0]); minY = Math.min(minY, s[1]); minZ = Math.min(minZ, s[2]);
                maxX = Math.max(maxX, s[3]); maxY = Math.max(maxY, s[4]); maxZ = Math.max(maxZ, s[5]);
            }
        }
        if (spans.isEmpty()) return new Voxels(new boolean[0][0][0], 0, 0, 0);
        boolean[][][] grid = new boolean[maxX - minX][maxY - minY][maxZ - minZ];
        for (int[] s : spans) {
            for (int x = s[0]; x < s[3]; x++)
                for (int y = s[1]; y < s[4]; y++)
                    for (int z = s[2]; z < s[5]; z++) grid[x - minX][y - minY][z - minZ] = true;
        }
        return new Voxels(grid, minX, minY, minZ);
    }

    /** First voxel a shape starting at build-unit {@code v} overlaps. */
    private static int lo(float v) {
        return (int) Math.floor(v * RES + TOUCH);
    }

    /** One past the last voxel a shape ending at build-unit {@code v} overlaps. */
    private static int hi(float v) {
        return (int) Math.ceil(v * RES - TOUCH);
    }

    /**
     * Empties every voxel wholly inside build cells {@code min..max} (inclusive, the
     * {@link ShelterGeometry} convention: a cell spans ±½ about its centre horizontally and
     * {@code [y, y + 1]} vertically) — a shelter's hull, which the shelter carries itself, so that a
     * fish using the shelter can ignore it (§12.3).
     */
    public static void carve(Voxels v, ShelterGeometry.Cell min, ShelterGeometry.Cell max) {
        boolean[][][] g = v.filled();
        if (g.length == 0) return;
        int x0 = hi(min.x() - 0.5f), x1 = lo(max.x() + 0.5f);
        int y0 = hi(min.y()), y1 = lo(max.y() + 1f);
        int z0 = hi(min.z() - 0.5f), z1 = lo(max.z() + 0.5f);
        for (int x = Math.max(x0, v.originX()); x < Math.min(x1, v.originX() + g.length); x++)
            for (int y = Math.max(y0, v.originY()); y < Math.min(y1, v.originY() + g[0].length); y++)
                for (int z = Math.max(z0, v.originZ()); z < Math.min(z1, v.originZ() + g[0][0].length); z++) {
                    g[x - v.originX()][y - v.originY()][z - v.originZ()] = false;
                }
    }

    /**
     * The voxel grid grown to include a margin of open water round the structure — {@code pad}
     * voxels on every side and on top, never below the sand (build {@code y = 0}), where there is
     * no water to grow into.
     */
    private static Voxels padded(Voxels v, int pad) {
        boolean[][][] g = v.filled();
        int bottom = Math.min(0, v.originY());
        int ox = v.originX() - pad, oy = bottom, oz = v.originZ() - pad;
        boolean[][][] out = new boolean[g.length + 2 * pad][v.originY() - bottom + g[0].length + pad][g[0][0].length + 2 * pad];
        for (int x = 0; x < g.length; x++)
            for (int y = 0; y < g[0].length; y++)
                for (int z = 0; z < g[0][0].length; z++) {
                    out[x + v.originX() - ox][y + v.originY() - oy][z + v.originZ() - oz] = g[x][y][z];
                }
        return new Voxels(out, ox, oy, oz);
    }

    /**
     * Fills every pocket a fish could not get into: the open water that is not within
     * {@code clearance} voxels of somewhere a fish fits — a point with {@code clearance} voxels of
     * open water all round it — that connects to the water round the structure. That is sealed
     * cavities (a geode's hollow, a closed clam), which a scatter could otherwise drop a fish into
     * for good, and nooks whose only way out is narrower than a fish: one coral block wide in a
     * reef, measured holding a fish for a whole ten-minute run. Openings fish do swim through —
     * an arch, a doorway, a shelter's mouth — are wider than {@code 2 × clearance} and are kept.
     *
     * <p>The water under the sand is not water, so a pocket opening only downward stays a pocket,
     * as in {@link ShelterGeometry}. Returns the grid, padded by {@code clearance + 1}.
     */
    public static Voxels fillPockets(Voxels v, int clearance) {
        if (v.filled().length == 0) return v;
        Voxels p = padded(v, clearance + 1);
        boolean[][][] g = p.filled();
        int sx = g.length, sy = g[0].length, sz = g[0][0].length;
        int sandY = -p.originY(); // grid index of build y = 0; below it is sand
        // Prefix sums of "not water" (a part, or under the sand), to test boxes of voxels at once.
        int[][][] solid = new int[sx + 1][sy + 1][sz + 1];
        for (int x = 0; x < sx; x++)
            for (int y = 0; y < sy; y++)
                for (int z = 0; z < sz; z++) {
                    int here = g[x][y][z] || y < sandY ? 1 : 0;
                    solid[x + 1][y + 1][z + 1] = here + solid[x][y + 1][z + 1] + solid[x + 1][y][z + 1] + solid[x + 1][y + 1][z]
                            - solid[x][y][z + 1] - solid[x][y + 1][z] - solid[x + 1][y][z] + solid[x][y][z];
                }
        // Where a fish fits: open water all round, the grid's outside counting as open (it is the
        // padding's job to be water) except under the sand.
        boolean[][][] fits = new boolean[sx][sy][sz];
        for (int x = 0; x < sx; x++)
            for (int y = 0; y < sy; y++)
                for (int z = 0; z < sz; z++) {
                    if (y - clearance < sandY) continue;
                    fits[x][y][z] = boxSum(solid, x - clearance, y - clearance, z - clearance,
                            x + clearance, y + clearance, z + clearance) == 0;
                }
        // Flood the places a fish fits from the padding's sides and top.
        boolean[][][] reached = new boolean[sx][sy][sz];
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        for (int x = 0; x < sx; x++)
            for (int y = 0; y < sy; y++)
                for (int z = 0; z < sz; z++) {
                    boolean edge = x == 0 || z == 0 || x == sx - 1 || z == sz - 1 || y == sy - 1;
                    if (edge && fits[x][y][z]) {
                        reached[x][y][z] = true;
                        queue.add(new int[]{x, y, z});
                    }
                }
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int[] d : ShelterGeometry.DIRECTIONS) {
                int x = c[0] + d[0], y = c[1] + d[1], z = c[2] + d[2];
                if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) continue;
                if (reached[x][y][z] || !fits[x][y][z]) continue;
                reached[x][y][z] = true;
                queue.add(new int[]{x, y, z});
            }
        }
        // Water within clearance of a reached point is where fish swim; the rest is filled.
        int[][][] near = new int[sx + 1][sy + 1][sz + 1];
        for (int x = 0; x < sx; x++)
            for (int y = 0; y < sy; y++)
                for (int z = 0; z < sz; z++) {
                    int here = reached[x][y][z] ? 1 : 0;
                    near[x + 1][y + 1][z + 1] = here + near[x][y + 1][z + 1] + near[x + 1][y][z + 1] + near[x + 1][y + 1][z]
                            - near[x][y][z + 1] - near[x][y + 1][z] - near[x + 1][y][z] + near[x][y][z];
                }
        for (int x = 0; x < sx; x++)
            for (int y = sandY; y < sy; y++)
                for (int z = 0; z < sz; z++) {
                    if (g[x][y][z]) continue;
                    if (boxSum(near, x - clearance, y - clearance, z - clearance,
                            x + clearance, y + clearance, z + clearance) == 0) g[x][y][z] = true;
                }
        return p;
    }

    /** Sum of a prefix-sum grid over voxels {@code lo..hi} (inclusive), clamped onto the grid. */
    private static int boxSum(int[][][] s, int x0, int y0, int z0, int x1, int y1, int z1) {
        int mx = s.length - 1, my = s[0].length - 1, mz = s[0][0].length - 1;
        x0 = Math.max(x0, 0); y0 = Math.max(y0, 0); z0 = Math.max(z0, 0);
        x1 = Math.min(x1 + 1, mx); y1 = Math.min(y1 + 1, my); z1 = Math.min(z1 + 1, mz);
        if (x0 >= x1 || y0 >= y1 || z0 >= z1) return 0;
        return s[x1][y1][z1] - s[x0][y1][z1] - s[x1][y0][z1] - s[x1][y1][z0]
                + s[x0][y0][z1] + s[x0][y1][z0] + s[x1][y0][z0] - s[x0][y0][z0];
    }

    /**
     * Greedy merge: from each unclaimed filled voxel in x-y-z order, grow along x, then y while the
     * whole row is filled and unclaimed, then z while the whole slab is. Exact: the boxes cover the
     * filled voxels and nothing else.
     */
    public static List<Box> merge(Voxels v) {
        boolean[][][] g = v.filled();
        List<Box> out = new ArrayList<>();
        if (g.length == 0) return out;
        int sx = g.length, sy = g[0].length, sz = g[0][0].length;
        boolean[][][] used = new boolean[sx][sy][sz];
        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    if (!g[x][y][z] || used[x][y][z]) continue;
                    int x1 = x + 1;
                    while (x1 < sx && g[x1][y][z] && !used[x1][y][z]) x1++;
                    int y1 = y + 1;
                    grow:
                    while (y1 < sy) {
                        for (int i = x; i < x1; i++) if (!g[i][y1][z] || used[i][y1][z]) break grow;
                        y1++;
                    }
                    int z1 = z + 1;
                    grow:
                    while (z1 < sz) {
                        for (int i = x; i < x1; i++)
                            for (int j = y; j < y1; j++) if (!g[i][j][z1] || used[i][j][z1]) break grow;
                        z1++;
                    }
                    for (int i = x; i < x1; i++)
                        for (int j = y; j < y1; j++)
                            for (int k = z; k < z1; k++) used[i][j][k] = true;
                    out.add(new Box(v.originX() + x, v.originY() + y, v.originZ() + z,
                            v.originX() + x1, v.originY() + y1, v.originZ() + z1));
                }
            }
        }
        return out;
    }
}
