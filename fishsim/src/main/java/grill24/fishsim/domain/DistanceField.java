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
        // (L,Y,D) order skipping the face axis. Bucketed by the occupied cell each face belongs
        // to, so the distance pass below can search outward from a sample's own cell instead of
        // scanning every face in the domain (that brute force is quadratic in group size once
        // several tanks join — see the fix for the placement-freeze bug).
        @SuppressWarnings("unchecked")
        java.util.List<float[]>[] faceBuckets = new java.util.List[sx * sy * sz];
        for (int ix = 0; ix < sx; ix++) {
            for (int iy = 0; iy < sy; iy++) {
                for (int iz = 0; iz < sz; iz++) {
                    if (!occupancy[ix][iy][iz]) continue;
                    float x0 = minL + ix, y0 = minY + iy, z0 = minD + iz;
                    java.util.List<float[]> bucket = new java.util.ArrayList<>(6);
                    if (!occupied(occupancy, ix - 1, iy, iz)) bucket.add(new float[]{0, x0, y0, y0 + 1, z0, z0 + 1});
                    if (!occupied(occupancy, ix + 1, iy, iz)) bucket.add(new float[]{0, x0 + 1, y0, y0 + 1, z0, z0 + 1});
                    if (!occupied(occupancy, ix, iy - 1, iz)) bucket.add(new float[]{1, y0, x0, x0 + 1, z0, z0 + 1});
                    if (!occupied(occupancy, ix, iy + 1, iz)) bucket.add(new float[]{1, y0 + 1, x0, x0 + 1, z0, z0 + 1});
                    if (!occupied(occupancy, ix, iy, iz - 1)) bucket.add(new float[]{2, z0, x0, x0 + 1, y0, y0 + 1});
                    if (!occupied(occupancy, ix, iy, iz + 1)) bucket.add(new float[]{2, z0 + 1, x0, x0 + 1, y0, y0 + 1});
                    if (!bucket.isEmpty()) faceBuckets[(ix * sy + iy) * sz + iz] = bucket;
                }
            }
        }

        int total = nx * ny * nz;
        dist = new float[total];
        gx = new float[total];
        gy = new float[total];
        gz = new float[total];

        // Distance pass: exact point-to-rectangle distance, signed by occupancy. Searches faces in
        // expanding shells of cells around the sample's own cell instead of the whole face list —
        // same faceDistance() values as brute force (every face closer than the current best is
        // still visited; only faces provably farther than the best-so-far are skipped), just found
        // without rescanning faces from tanks the sample is nowhere near.
        for (int i = 0; i < nx; i++) {
            float px = minL + i * h;
            int cx = clampIndex((int) Math.floor(px - minL), sx - 1);
            for (int j = 0; j < ny; j++) {
                float py = minY + j * h;
                int cy = clampIndex((int) Math.floor(py - minY), sy - 1);
                for (int k = 0; k < nz; k++) {
                    float pz = minD + k * h;
                    int cz = clampIndex((int) Math.floor(pz - minD), sz - 1);
                    float best = nearestFaceDistance(faceBuckets, sx, sy, sz, cx, cy, cz, px, py, pz);
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

    /**
     * Nearest face distance to (px,py,pz), searching outward in cube shells from cell
     * (cx,cy,cz). A shell at radius r can hold no face closer than (r-1) blocks — cells are
     * contiguous unit cubes, so anything r cells away is separated by at least r-1 full cells —
     * so once that bound exceeds the best found so far, no farther shell can improve it.
     */
    private static float nearestFaceDistance(java.util.List<float[]>[] buckets, int sx, int sy, int sz,
                                              int cx, int cy, int cz, float px, float py, float pz) {
        float best = Float.MAX_VALUE;
        int maxRadius = Math.max(sx, Math.max(sy, sz));
        for (int r = 0; r <= maxRadius; r++) {
            if (r >= 1 && (float) (r - 1) > best) break;
            int loX = Math.max(0, cx - r), hiX = Math.min(sx - 1, cx + r);
            int loY = Math.max(0, cy - r), hiY = Math.min(sy - 1, cy + r);
            int loZ = Math.max(0, cz - r), hiZ = Math.min(sz - 1, cz + r);
            for (int ix = loX; ix <= hiX; ix++) {
                boolean xShell = ix == cx - r || ix == cx + r;
                for (int iy = loY; iy <= hiY; iy++) {
                    boolean yShell = iy == cy - r || iy == cy + r;
                    for (int iz = loZ; iz <= hiZ; iz++) {
                        boolean zShell = iz == cz - r || iz == cz + r;
                        if (r > 0 && !xShell && !yShell && !zShell) continue; // visited at a smaller r
                        java.util.List<float[]> bucket = buckets[(ix * sy + iy) * sz + iz];
                        if (bucket == null) continue;
                        for (float[] face : bucket) {
                            float d = faceDistance(face, px, py, pz);
                            if (d < best) best = d;
                        }
                    }
                }
            }
        }
        return best;
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
