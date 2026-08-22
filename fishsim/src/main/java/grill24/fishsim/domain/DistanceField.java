package grill24.fishsim.domain;

/**
 * A precomputed signed-distance field over a voxel occupancy grid's bounding box: at each sample
 * (~4 per block per axis), the distance to the nearest wall of the rectilinear union (positive
 * inside, negative outside) minus the shape's interior inset, plus the distance gradient (unit
 * direction away from the nearest wall). Rebuilt only on domain change — rare, build/lock time.
 *
 * <p>Wall avoidance becomes one trilinear lookup per fish per tick, and concave corners (an
 * L-bend) come out right — which AABB-union per-axis signed distance gets wrong at the seam:
 * distance is measured to the true boundary <em>faces</em> of the union, so a face shared by two
 * occupied cells is open water, not a wall.
 */
public final class DistanceField {

    /** Samples per block per axis. A tunables-adjacent constant — tune with the L-domain tests watching. */
    public static final int SAMPLES_PER_BLOCK = 4;

    private final float minL, minY, minD; // local position of sample (0,0,0)
    private final int nx, ny, nz;         // sample counts per axis
    private final float h;                // sample spacing (blocks)
    private final float[] dist;           // signed distance minus inset, flattened x-major
    private final float[] gx, gy, gz;     // unit gradient (direction of increasing distance)

    /**
     * @param occupancy occupied cells, indexed {@code [ix][iy][iz]}
     * @param minL      local coordinate of the grid's low corner (lateral)
     * @param inset     interior inset from every wall face, blocks
     */
    public DistanceField(boolean[][][] occupancy, float minL, float minY, float minD, float inset) {
        int sx = occupancy.length, sy = occupancy[0].length, sz = occupancy[0][0].length;
        this.minL = minL;
        this.minY = minY;
        this.minD = minD;
        this.h = 1f / SAMPLES_PER_BLOCK;
        this.nx = sx * SAMPLES_PER_BLOCK + 1;
        this.ny = sy * SAMPLES_PER_BLOCK + 1;
        this.nz = sz * SAMPLES_PER_BLOCK + 1;

        // Enumerate the union's boundary faces: any cell face whose neighbour is missing. Each is
        // a unit rectangle on an axis-aligned plane, in local coordinates.
        // face = {axis(0/1/2), planeCoord, u0, u1, v0, v1} with (u, v) the other two axes in
        // (L,Y,D) order skipping the face axis.
        java.util.List<float[]> faces = new java.util.ArrayList<>();
        for (int ix = 0; ix < sx; ix++) {
            for (int iy = 0; iy < sy; iy++) {
                for (int iz = 0; iz < sz; iz++) {
                    if (!occupancy[ix][iy][iz]) continue;
                    float x0 = minL + ix, y0 = minY + iy, z0 = minD + iz;
                    if (!occupied(occupancy, ix - 1, iy, iz)) faces.add(new float[]{0, x0, y0, y0 + 1, z0, z0 + 1});
                    if (!occupied(occupancy, ix + 1, iy, iz)) faces.add(new float[]{0, x0 + 1, y0, y0 + 1, z0, z0 + 1});
                    if (!occupied(occupancy, ix, iy - 1, iz)) faces.add(new float[]{1, y0, x0, x0 + 1, z0, z0 + 1});
                    if (!occupied(occupancy, ix, iy + 1, iz)) faces.add(new float[]{1, y0 + 1, x0, x0 + 1, z0, z0 + 1});
                    if (!occupied(occupancy, ix, iy, iz - 1)) faces.add(new float[]{2, z0, x0, x0 + 1, y0, y0 + 1});
                    if (!occupied(occupancy, ix, iy, iz + 1)) faces.add(new float[]{2, z0 + 1, x0, x0 + 1, y0, y0 + 1});
                }
            }
        }

        int total = nx * ny * nz;
        dist = new float[total];
        gx = new float[total];
        gy = new float[total];
        gz = new float[total];

        // Distance pass: exact point-to-rectangle distance over all boundary faces (domains are a
        // handful of blocks — brute force is microseconds at build time), signed by occupancy.
        for (int i = 0; i < nx; i++) {
            float px = minL + i * h;
            for (int j = 0; j < ny; j++) {
                float py = minY + j * h;
                for (int k = 0; k < nz; k++) {
                    float pz = minD + k * h;
                    float best = Float.MAX_VALUE;
                    for (float[] face : faces) {
                        float d = faceDistance(face, px, py, pz);
                        if (d < best) best = d;
                    }
                    boolean inside = sampleInside(occupancy, px - minL, py - minY, pz - minD, sx, sy, sz);
                    dist[idx(i, j, k)] = (inside ? best : -best) - inset;
                }
            }
        }

        // Gradient pass: central differences of the signed distance, normalized.
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < ny; j++) {
                for (int k = 0; k < nz; k++) {
                    float dX = (at(Math.min(i + 1, nx - 1), j, k) - at(Math.max(i - 1, 0), j, k))
                            / (h * (Math.min(i + 1, nx - 1) - Math.max(i - 1, 0)));
                    float dY = (at(i, Math.min(j + 1, ny - 1), k) - at(i, Math.max(j - 1, 0), k))
                            / (h * (Math.min(j + 1, ny - 1) - Math.max(j - 1, 0)));
                    float dZ = (at(i, j, Math.min(k + 1, nz - 1)) - at(i, j, Math.max(k - 1, 0)))
                            / (h * (Math.min(k + 1, nz - 1) - Math.max(k - 1, 0)));
                    float len = (float) Math.sqrt(dX * dX + dY * dY + dZ * dZ);
                    int id = idx(i, j, k);
                    if (len > 1e-6f) {
                        gx[id] = dX / len;
                        gy[id] = dY / len;
                        gz[id] = dZ / len;
                    }
                }
            }
        }
    }

    private static boolean occupied(boolean[][][] occ, int ix, int iy, int iz) {
        return ix >= 0 && iy >= 0 && iz >= 0
                && ix < occ.length && iy < occ[0].length && iz < occ[0][0].length
                && occ[ix][iy][iz];
    }

    private static boolean sampleInside(boolean[][][] occ, float relX, float relY, float relZ,
                                        int sx, int sy, int sz) {
        int ix = Math.min((int) relX, sx - 1);
        int iy = Math.min((int) relY, sy - 1);
        int iz = Math.min((int) relZ, sz - 1);
        return occ[ix][iy][iz];
    }

    private static float faceDistance(float[] face, float px, float py, float pz) {
        int axis = (int) face[0];
        float p, u, v;
        switch (axis) {
            case 0 -> { p = px; u = py; v = pz; }
            case 1 -> { p = py; u = px; v = pz; }
            default -> { p = pz; u = px; v = py; }
        }
        float dp = p - face[1];
        float du = u < face[2] ? face[2] - u : (u > face[3] ? u - face[3] : 0f);
        float dv = v < face[4] ? face[4] - v : (v > face[5] ? v - face[5] : 0f);
        return (float) Math.sqrt(dp * dp + du * du + dv * dv);
    }

    private int idx(int i, int j, int k) {
        return (i * ny + j) * nz + k;
    }

    private float at(int i, int j, int k) {
        return dist[idx(i, j, k)];
    }

    /**
     * Trilinearly interpolated signed distance (positive = inside, already inset-adjusted) at a
     * local position. Zero allocation.
     */
    public float distance(float l, float y, float d) {
        return sampleTrilinear(dist, l, y, d);
    }

    /** Writes the interpolated (unnormalized-after-blend) gradient into {@code out[0..2]}. */
    public void gradient(float l, float y, float d, float[] out) {
        out[0] = sampleTrilinear(gx, l, y, d);
        out[1] = sampleTrilinear(gy, l, y, d);
        out[2] = sampleTrilinear(gz, l, y, d);
    }

    private float sampleTrilinear(float[] grid, float l, float y, float d) {
        float fx = (l - minL) / h, fy = (y - minY) / h, fz = (d - minD) / h;
        int i = clampIndex((int) Math.floor(fx), nx - 2);
        int j = clampIndex((int) Math.floor(fy), ny - 2);
        int k = clampIndex((int) Math.floor(fz), nz - 2);
        float tx = clamp01(fx - i), ty = clamp01(fy - j), tz = clamp01(fz - k);

        float c000 = grid[idx(i, j, k)], c100 = grid[idx(i + 1, j, k)];
        float c010 = grid[idx(i, j + 1, k)], c110 = grid[idx(i + 1, j + 1, k)];
        float c001 = grid[idx(i, j, k + 1)], c101 = grid[idx(i + 1, j, k + 1)];
        float c011 = grid[idx(i, j + 1, k + 1)], c111 = grid[idx(i + 1, j + 1, k + 1)];

        float c00 = c000 + tx * (c100 - c000);
        float c10 = c010 + tx * (c110 - c010);
        float c01 = c001 + tx * (c101 - c001);
        float c11 = c011 + tx * (c111 - c011);
        float c0 = c00 + ty * (c10 - c00);
        float c1 = c01 + ty * (c11 - c01);
        return c0 + tz * (c1 - c0);
    }

    private static int clampIndex(int v, int max) {
        return v < 0 ? 0 : Math.min(v, max);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }
}
