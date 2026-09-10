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
    private final float gridMinL, gridMinY, gridMinD;
    private final float minL, minY, minD, maxL, maxY, maxD;
    private final float[] layerDepths;
    private final float sizeGateRun;
    private final DistanceField field;
    private FloorField floor;

    public VoxelDomain(boolean[][][] occupancy) {
        this(occupancy, DEFAULT_INSET, 0f, null);
    }

    public VoxelDomain(boolean[][][] occupancy, float inset) {
        this(occupancy, inset, 0f, null);
    }

    /**
     * @param occupancy occupied cells indexed {@code [lateral][vertical][depth]}; the array's
     *                  bounds should be the occupied cells' bounding box (empty border planes
     *                  waste field samples but are harmless)
     */
    /**
     * @param floorSurfaceOffset height of the sand surface above a block's own bottom face
     * @param blockedFloorCells  obstacle flags over the {@link FloorField} grid — the tank's
     *                           cosmetic cells, so a crawler walks around a shipwreck instead of
     *                           through it; null for an open floor
     */
    public VoxelDomain(boolean[][][] occupancy, float inset, float floorSurfaceOffset,
                       boolean[] blockedFloorCells) {
        this.occupancy = occupancy;
        this.inset = inset;
        int sx = occupancy.length, sy = occupancy[0].length, sz = occupancy[0][0].length;

        this.gridMinL = -sx / 2f;
        this.gridMinY = -sy / 2f;
        this.gridMinD = -sz / 2f;
        float gridMinL = this.gridMinL, gridMinY = this.gridMinY, gridMinD = this.gridMinD;
        this.minL = gridMinL + inset;
        this.minY = gridMinY + inset;
        this.minD = gridMinD + inset;
        this.maxL = sx / 2f - inset;
        this.maxY = sy / 2f - inset;
        this.maxD = sz / 2f - inset;

        this.field = new DistanceField(occupancy, gridMinL, gridMinY, gridMinD, inset);
        this.sizeGateRun = new RunLengths(occupancy).longestRunInterior(inset);
        this.floor = FloorField.fromOccupancy(occupancy, gridMinL, gridMinY, gridMinD,
                floorSurfaceOffset, blockedFloorCells);

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

    @Override public FloorField floor() { return floor; }

    /**
     * Rebuilds just the floor. It is the one part of a domain that changes without membership
     * changing — a player placing a cosmetic moves no tanks — and rebuilding the whole domain for
     * that would re-incur the distance-field cost the shared per-epoch cache exists to avoid
     * (docs/fish-tank-group-scaling.md §5.3a). The floor is a cheap 2D pass over the same
     * occupancy, so it is recomputed in place instead.
     */
    public void rebuildFloor(float floorSurfaceOffset, boolean[] blockedFloorCells) {
        this.floor = FloorField.fromOccupancy(occupancy, gridMinL, gridMinY, gridMinD,
                floorSurfaceOffset, blockedFloorCells);
    }

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
        // KNOWN DEFECT, deliberately left in place: marginVertical is accepted and ignored, so
        // `margin` applies on all three axes where FlockDomain.Box gives the vertical its own,
        // much narrower zone (0.20 vs 0.05 at the GROUP tunables). Noted in
        // docs/fish-tank-group-scaling.md §1 and attempted with the rest of that document's fixes.
        //
        // It is not the one-line fix it looks like. Scaling the ramp by the gradient's verticality
        // (a vertical face keeps `margin`, a floor or ceiling gets `marginVertical`, corners blend)
        // is the right shape and does work — but at 0.05 the vertical zone is far too tight for
        // GROUP's speeds, which are nearly double the single-tank set's. Measured: the hard
        // backstop engaged 6 times in the L domain, i.e. a containment failure by the invariant
        // suite's definition, not a tuning nit. GROUP simply inherited DEFAULT.wallMarginVertical()
        // and has never had a value of its own, because nothing has ever read it.
        //
        // So the real fix is a tuning workstream — give GROUP its own wallMarginVertical and sweep
        // it against the invariant matrix — plus a deliberate regeneration of the voxel golden
        // fixture, since it visibly changes how close fish swim to floors and ceilings. That does
        // not belong bundled with an optimisation whose correctness gate is "the fixture must NOT
        // move".
        //
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
