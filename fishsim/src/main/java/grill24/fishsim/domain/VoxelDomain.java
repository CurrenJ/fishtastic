package grill24.fishsim.domain;

/**
 * A locked multi-tank aquarium: a rectilinear union of blocks as a voxel occupancy grid, with a
 * precomputed {@link DistanceField} for wall avoidance and {@link RunLengths} for the size gate
 * (docs/fish-sim-engine-plan.md §2.2). Local coordinates put the grid's bounding-box center at
 * the origin; each cell is one block; the swimmable interior is the union shrunk by a uniform
 * {@code inset} from every wall face (per-shape insets can refine this later).
 *
 * <p>A single tank is the degenerate 1×1×1 domain: bounds ±(0.5 − inset) = ±0.35 with the
 * default inset, size-gate run 0.7, and depth planes {−0.25, 0, 0.25} — matching the legacy
 * single-tank Box (the in-game single-tank path still uses {@link FlockDomain.Box} for exact
 * bitwise parity; this class is for connected groups).
 *
 * <p>Home depth planes generalize the old fixed {@code LAYER_Z}: planes spaced
 * {@link #LAYER_SPACING} blocks, centered across the local depth extent.
 */
public final class VoxelDomain implements FlockDomain {

    /** Interior inset from every wall face — 0.5 − inset = the legacy 0.35 tank half-extent. */
    public static final float DEFAULT_INSET = 0.15f;

    /** Spacing between home depth planes, blocks (the legacy LAYER_Z spacing). */
    public static final float LAYER_SPACING = 0.25f;

    private final boolean[][][] occupancy;
    private final float inset;
    private final float minL, minY, minD, maxL, maxY, maxD;
    private final float[] layerDepths;
    private final float sizeGateRun;
    private final DistanceField field;

    public VoxelDomain(boolean[][][] occupancy) {
        this(occupancy, DEFAULT_INSET);
    }

    /**
     * @param occupancy occupied cells indexed {@code [lateral][vertical][depth]}; the array's
     *                  bounds should be the occupied cells' bounding box (empty border planes
     *                  waste field samples but are harmless)
     */
    public VoxelDomain(boolean[][][] occupancy, float inset) {
        this.occupancy = occupancy;
        this.inset = inset;
        int sx = occupancy.length, sy = occupancy[0].length, sz = occupancy[0][0].length;

        float gridMinL = -sx / 2f, gridMinY = -sy / 2f, gridMinD = -sz / 2f;
        this.minL = gridMinL + inset;
        this.minY = gridMinY + inset;
        this.minD = gridMinD + inset;
        this.maxL = sx / 2f - inset;
        this.maxY = sy / 2f - inset;
        this.maxD = sz / 2f - inset;

        this.field = new DistanceField(occupancy, gridMinL, gridMinY, gridMinD, inset);
        this.sizeGateRun = new RunLengths(occupancy).longestRunInterior(inset);

        // Depth planes at LAYER_SPACING across the interior depth extent, centered. The 1-block
        // case (extent 0.7) yields exactly the legacy {−0.25, 0, 0.25}.
        float depthExtent = maxD - minD;
        int planeCount = (int) Math.floor(depthExtent / LAYER_SPACING + 1e-4f) + 1;
        this.layerDepths = new float[planeCount];
        float center = (minD + maxD) * 0.5f;
        for (int i = 0; i < planeCount; i++) {
            layerDepths[i] = center + (i - (planeCount - 1) * 0.5f) * LAYER_SPACING;
        }
    }

    public boolean[][][] occupancy() { return occupancy; }

    public float inset() { return inset; }

    public DistanceField field() { return field; }

    @Override public float minLateral() { return minL; }
    @Override public float maxLateral() { return maxL; }
    @Override public float minVertical() { return minY; }
    @Override public float maxVertical() { return maxY; }
    @Override public float minDepth() { return minD; }
    @Override public float maxDepth() { return maxD; }
    @Override public float[] layerDepths() { return layerDepths; }
    @Override public float sizeGateRun() { return sizeGateRun; }

    @Override
    public boolean contains(float l, float y, float d) {
        return field.distance(l, y, d) > 0f;
    }

    @Override
    public void avoidance(float l, float y, float d, float margin, float marginVertical, float[] out) {
        float dist = field.distance(l, y, d);
        if (dist >= margin) {
            out[0] = out[1] = out[2] = 0f;
            return;
        }
        // Same ramp as the box's per-axis formula — proximity weight (margin − dist)/margin,
        // pointed along the field gradient (away from the nearest wall, concave corners included).
        float w = (margin - dist) / margin;
        field.gradient(l, y, d, out);
        out[0] *= w;
        out[1] *= w;
        out[2] *= w;
    }

    @Override
    public void constrain(float prevL, float prevY, float prevD, float[] pos) {
        if (contains(pos[0], pos[1], pos[2])) return;
        if (!contains(prevL, prevY, prevD)) {
            // Previous position was already outside (shouldn't happen) — hold in place rather
            // than guessing; the soft avoidance recovers it over the next ticks.
            pos[0] = prevL;
            pos[1] = prevY;
            pos[2] = prevD;
            return;
        }
        // Bisect between the known-inside previous position and the escaped one; keep the last
        // inside point. Deterministic, allocation-free, and — per the invariant tests — never
        // actually reached in practice.
        float inL = prevL, inY = prevY, inD = prevD;
        float outL = pos[0], outY = pos[1], outD = pos[2];
        for (int iter = 0; iter < 16; iter++) {
            float midL = (inL + outL) * 0.5f;
            float midY = (inY + outY) * 0.5f;
            float midD = (inD + outD) * 0.5f;
            if (contains(midL, midY, midD)) {
                inL = midL; inY = midY; inD = midD;
            } else {
                outL = midL; outY = midY; outD = midD;
            }
        }
        pos[0] = inL;
        pos[1] = inY;
        pos[2] = inD;
    }

    @Override
    public float wallDistance(float l, float y, float d) {
        return field.distance(l, y, d);
    }
}
