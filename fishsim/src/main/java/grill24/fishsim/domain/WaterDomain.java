package grill24.fishsim.domain;

import java.util.List;

/**
 * Natural water: a bounded, world-anchored volume built from discovered water columns instead of a
 * union of full unit cubes (docs/fish-in-world.md §domain).
 *
 * <p><b>Why not {@link VoxelDomain}.</b> A voxel domain is a rectilinear union of whole blocks, and
 * natural water is not: a still source cell's surface is 8/9 of a block and a flowing cell's is
 * lower still, so a body-union domain lets a fish surface-clip or swim in air; a waterlogged slab,
 * stair or fence inside a cell has collision geometry a full-cube union cannot express; and the
 * synchronous {@link DistanceField} it builds for avoidance is a per-rebuild latency spike whose
 * size grows with the domain — the exact thing Phase 2 has to bound. This domain stores, per
 * column, the vertical water <em>spans</em> (fractional surfaces, partial collision at the bottom
 * of a span, gaps where water is interrupted) and answers containment and avoidance analytically,
 * with no field and no per-rebuild cost beyond one pass over the columns.
 *
 * <p><b>Coordinates.</b> Local coordinates are the world's, minus the host's fixed origin: a local
 * lateral equals its world X minus the origin X, and so on. The origin belongs to the water patch,
 * not to the window, so it never moves while the patch lives — which is what makes a rebuild carry
 * positions straight through with no rebasing (docs/fish-in-world.md §frame). Callers that
 * anchor a patch to the world pass their patch's origin and use {@code baseRotationDeg = 0}.
 *
 * <p><b>Clearance.</b> {@link #contains} is centre containment — it keeps a fish's simulated point
 * inside water, and on the vertical it also keeps the point a body's height below every local
 * surface. {@link #bodyFits} is the stronger test the spawn path uses: the whole animated body
 * (a ball of the individual's worst-case radius, plus the head-room its pose and bob need above
 * the centre and below it) must be inside water at nine sampled points around the centre. An
 * individual that does not fit is omitted rather than shrunk or left hovering.
 */
public final class WaterDomain implements FlockDomain {

    /** Runs kept per column — must match the discovery's cap. */
    public static final int MAX_SPANS = 6;
    /** Spacing between home depth planes, blocks (matches {@link VoxelDomain}). */
    public static final float LAYER_SPACING = VoxelDomain.LAYER_SPACING;
    /** Interior inset the size gate measures with (matches {@link VoxelDomain}). */
    public static final float DEFAULT_INSET = VoxelDomain.DEFAULT_INSET;
    /** Extra clearance the soft avoidance keeps from a bank, on top of the engine's margin. */
    public static final float DEFAULT_BANK_CUSHION = 0.10f;
    /**
     * How close a fish may come to an <em>open</em> face — one where the water continues past what
     * the patch knows — before it is retired.
     *
     * <p>Wider than the avoidance zone any locomotion set can ask for plus the bank cushion that
     * widens it further (0.25 + 0.10 = 0.35, GLIDE's margin, the widest), because the client fades
     * a retiring fish by its distance to the face: {@code (d / OPEN_FACE_MARGIN)²}. At the widest
     * push distance that leaves the fish at a third of its size, and at half the band a quarter, so
     * it is well gone before any engine can start turning it against a wall that is not there.
     */
    public static final float OPEN_FACE_MARGIN = 0.60f;
    /** Hard cap on columns a domain may hold, matching the discovery's own cap. */
    public static final int MAX_COLUMNS = 4096;
    /**
     * Hard cap on grid <em>cells</em> (bounding-box width times depth, in blocks) a build may
     * allocate. Twice {@link #MAX_COLUMNS} on purpose: the bounding box of a scattered patch is
     * mostly dry land, and a live patch at the column cap can still span a ~65x65 rectangle (the
     * eviction disc's own bbox), so a cap on columns would reject water that is legal to host.
     * What a build actually allocates is the grid, so that is what is bounded.
     */
    public static final long MAX_GRID_CELLS = MAX_COLUMNS * 2L;

    /** Face indices for {@code openFaces}. */
    public static final int FACE_MIN_L = 0;
    public static final int FACE_MAX_L = 1;
    public static final int FACE_MIN_Y = 2;
    public static final int FACE_MAX_Y = 3;
    public static final int FACE_MIN_D = 4;
    public static final int FACE_MAX_D = 5;

    /**
     * One column of world water, in world coordinates.
     *
     * @param spans alternating {@code lo, hi} world-Y pairs, ascending
     * @param truncated the scan was cut by a search bound (not a bed or a surface), so the water
     *                  really continues in that direction and this column's edge is not geometry.
     *                  A caller that sets this because the water continues <em>below</em> the
     *                  scanned band is saying the bed is unknown, and {@link #build} gives the
     *                  column no floor rather than inventing one at the band edge.
     */
    public record Column(int worldX, int worldZ, float[] spans, boolean truncated) {}

    /**
     * The volume one individual's animated body sweeps, worst case, around its simulated centre:
     * {@code half} is the horizontal radius (everything a yaw, bank and surf rotation can reach),
     * {@code above}/{@code below} the extents the pose and bob need.
     */
    public record BodyBox(float half, float above, float below) {

        /**
         * The whole volume one individual's animated body can occupy, worst case: the horizontal
         * radius a yaw, bank and surf rotation can reach, plus the vertical room the pose and bob
         * need above and below its centre. A floor-walker stands on the floor, so nothing is needed
         * below it — and so does an anchored species (a garden eel stands in its burrow and sways
         * from there).
         */
        public static BodyBox of(grill24.fishsim.core.Locomotion loco, float length) {
            float reach = 0.7071f * length;
            return switch (loco) {
                case BENTHIC, ANCHORED -> new BodyBox(reach, length, 0f);
                case GLIDE -> new BodyBox(reach, reach + 0.08f, reach + 0.08f);
                case DRIFT -> new BodyBox(reach, reach + 0.05f, reach + 0.05f);
                default -> new BodyBox(reach, reach + 0.125f, reach + 0.125f);
            };
        }
    }

    private final int sx, sz;
    private final float originY;
    private final float gridMinL, gridMinD;
    private final float minL, maxL, minY, maxY, minD, maxD;
    private final float[] spanData;   // per column: MAX_SPANS pairs of local-Y lo/hi
    private final byte[] spanCount;   // per column
    private final float[] bed;        // per column: local bed Y, or NaN
    private final boolean[] truncated;
    private final boolean[] openFaces;
    private final float topMargin;
    private final float bankCushion;
    private final float[] layerDepths;
    private final float sizeGateRun;
    private final FloorField floor;
    private List<Shelter> shelters = List.of();

    private WaterDomain(int sx, int sz, float originY, float gridMinL, float gridMinD,
                        float[] spanData, byte[] spanCount, float[] bed, boolean[] truncated,
                        boolean[] openFaces, float topMargin, float bankCushion) {
        this.sx = sx;
        this.sz = sz;
        this.originY = originY;
        this.gridMinL = gridMinL;
        this.gridMinD = gridMinD;
        this.spanData = spanData;
        this.spanCount = spanCount;
        this.bed = bed;
        this.truncated = truncated;
        this.openFaces = openFaces;
        this.topMargin = topMargin;
        this.bankCushion = bankCushion;

        this.minL = gridMinL;
        this.maxL = gridMinL + sx;
        this.minD = gridMinD;
        this.maxD = gridMinD + sz;
        float lo = Float.POSITIVE_INFINITY, hi = Float.NEGATIVE_INFINITY;
        for (int c = 0; c < sx * sz; c++) {
            int n = spanCount[c];
            for (int s = 0; s < n; s++) {
                lo = Math.min(lo, spanData[(c * MAX_SPANS + s) * 2]);
                hi = Math.max(hi, spanData[(c * MAX_SPANS + s) * 2 + 1]);
            }
        }
        this.minY = Float.isFinite(lo) ? lo : 0f;
        this.maxY = Float.isFinite(hi) ? hi : 0f;

        float depthExtent = maxD - minD;
        int planeCount = (int) Math.floor(depthExtent / LAYER_SPACING + 1e-4f) + 1;
        this.layerDepths = new float[planeCount];
        float center = (minD + maxD) * 0.5f;
        for (int i = 0; i < planeCount; i++) {
            layerDepths[i] = center + (i - (planeCount - 1) * 0.5f) * LAYER_SPACING;
        }

        boolean[][][] occupancy = new boolean[sx][(int) Math.ceil(maxY - minY) + 2][sz];
        for (int ix = 0; ix < sx; ix++) {
            for (int iz = 0; iz < sz; iz++) {
                int c = index(ix, iz);
                for (int iy = 0; iy < occupancy[ix].length; iy++) {
                    float mid = minY + iy + 0.5f;
                    occupancy[ix][iy][iz] = covers(c, mid, mid);
                }
            }
        }
        this.sizeGateRun = new RunLengths(occupancy).longestRunInterior(DEFAULT_INSET);

        float[] bedByBlock = new float[sx * sz];
        for (int c = 0; c < sx * sz; c++) {
            bedByBlock[c] = spanCount[c] == 0 ? Float.NaN : bed[c];
        }
        this.floor = FloorField.fromColumnHeights(sx, sz, gridMinL, gridMinD, bedByBlock, null);
    }

    /**
     * Builds a domain from the discovered columns of one patch.
     *
     * @param columns     world columns, at least one with water
     * @param originX/Y/Z the patch's fixed world origin
     * @param topMargin   head-room above a fish's centre the vertical containment keeps — the
     *                    tallest hosted body's {@link BodyBox#above()}
     * @param openFaces   six flags ({@link #FACE_MIN_L}…{@link #FACE_MAX_D}) marking faces where
     *                    the water continues past what the patch knows; null for all closed
     * @return the domain, or null when no column holds water
     */
    public static WaterDomain build(List<Column> columns, int originX, int originY, int originZ,
                                    float topMargin, boolean[] openFaces) {
        if (columns == null || columns.isEmpty()) return null;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        int wet = 0;
        for (Column c : columns) {
            if (c.spans() == null || c.spans().length == 0) continue;
            wet++;
            minX = Math.min(minX, c.worldX());
            maxX = Math.max(maxX, c.worldX());
            minZ = Math.min(minZ, c.worldZ());
            maxZ = Math.max(maxZ, c.worldZ());
        }
        if (wet == 0) return null;
        int sx = maxX - minX + 1;
        int sz = maxZ - minZ + 1;
        if ((long) sx * sz > MAX_GRID_CELLS) return null;

        float gridMinL = minX - originX;
        float gridMinD = minZ - originZ;
        float[] spanData = new float[sx * sz * MAX_SPANS * 2];
        byte[] spanCount = new byte[sx * sz];
        float[] bed = new float[sx * sz];
        boolean[] truncated = new boolean[sx * sz];

        for (Column c : columns) {
            float[] spans = c.spans();
            if (spans == null || spans.length == 0) continue;
            int ix = c.worldX() - minX;
            int iz = c.worldZ() - minZ;
            int cell = ix * sz + iz;
            int n = Math.min(MAX_SPANS, spans.length / 2);
            for (int s = 0; s < n; s++) {
                spanData[(cell * MAX_SPANS + s) * 2] = spans[s * 2] - originY;
                spanData[(cell * MAX_SPANS + s) * 2 + 1] = spans[s * 2 + 1] - originY;
            }
            spanCount[cell] = (byte) n;
            // A truncated column's lowest span may sit at the scan's own band edge rather than on
            // real ground, so it has no floor: a crawler must never stand on a bed that was never
            // seen. Where the water below is known (a surface-truncated column), the bed is real
            // and stays — the caller distinguishes the two.
            bed[cell] = c.truncated() ? Float.NaN : spans[0] - originY;
            truncated[cell] = c.truncated();
        }
        boolean[] faces = new boolean[6];
        if (openFaces != null) {
            System.arraycopy(openFaces, 0, faces, 0, Math.min(6, openFaces.length));
        }
        return new WaterDomain(sx, sz, originY, gridMinL, gridMinD, spanData, spanCount, bed,
                truncated, faces, Math.max(0f, topMargin), DEFAULT_BANK_CUSHION);
    }

    /**
     * Whether a column set's grid would fit the cap {@link #build} allocates for — the reason a
     * build refused, separated from the reasons that are about the water itself. A caller that
     * gets null from {@code build} can tell "this water is not hostable" (false) apart from "no
     * water here" (also false from build, but true here: no grid is needed for nothing).
     */
    public static boolean gridFits(List<Column> columns) {
        if (columns == null) return true;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (Column c : columns) {
            if (c.spans() == null || c.spans().length == 0) continue;
            minX = Math.min(minX, c.worldX());
            maxX = Math.max(maxX, c.worldX());
            minZ = Math.min(minZ, c.worldZ());
            maxZ = Math.max(maxZ, c.worldZ());
        }
        if (minX > maxX) return true; // nothing wet: no grid to allocate
        return (long) (maxX - minX + 1) * (maxZ - minZ + 1) <= MAX_GRID_CELLS;
    }

    // ------------------------------------------------------------------ shape queries

    public int columnsLateral() {
        return sx;
    }

    public int columnsDepth() {
        return sz;
    }

    public int columnCount() {
        int n = 0;
        for (int c = 0; c < spanCount.length; c++) {
            if (spanCount[c] != 0) n++;
        }
        return n;
    }

    /** World Y of this domain's local zero. */
    public float originY() {
        return originY;
    }

    public int spanCountAt(int ix, int iz) {
        if (ix < 0 || ix >= sx || iz < 0 || iz >= sz) return 0;
        return spanCount[index(ix, iz)];
    }

    public float spanLo(int ix, int iz, int span) {
        return spanData[(index(ix, iz) * MAX_SPANS + span) * 2];
    }

    public float spanHi(int ix, int iz, int span) {
        return spanData[(index(ix, iz) * MAX_SPANS + span) * 2 + 1];
    }

    /** Whether this column's scan was cut by a search bound rather than by real geometry. */
    public boolean columnTruncated(int ix, int iz) {
        return ix >= 0 && ix < sx && iz >= 0 && iz < sz && truncated[index(ix, iz)];
    }

    /** Whether a face of the domain is open (the water continues past what the patch knows). */
    public boolean openFace(int face) {
        return face >= 0 && face < 6 && openFaces[face];
    }

    public float topMargin() {
        return topMargin;
    }

    /** The walkable bed of the column holding a position, or NaN — the floor a crawler stands on. */
    public float bedAt(float l, float d) {
        int ix = (int) Math.floor(l - gridMinL);
        int iz = (int) Math.floor(d - gridMinD);
        if (ix < 0 || ix >= sx || iz < 0 || iz >= sz) return Float.NaN;
        int c = index(ix, iz);
        return spanCount[c] == 0 ? Float.NaN : bed[c];
    }

    /** Lowest known water plane, even where the real sea bed lies below the discovery band. */
    public float bottomAt(float l, float d) {
        int ix = (int) Math.floor(l - gridMinL), iz = (int) Math.floor(d - gridMinD);
        if (ix < 0 || ix >= sx || iz < 0 || iz >= sz) return Float.NaN;
        int cell = index(ix, iz);
        return spanCount[cell] == 0 ? Float.NaN : spanData[cell * MAX_SPANS * 2];
    }

    /** The water surface of the column holding a position, or NaN — the highest span's top. */
    public float surfaceAt(float l, float d) {
        int ix = (int) Math.floor(l - gridMinL);
        int iz = (int) Math.floor(d - gridMinD);
        if (ix < 0 || ix >= sx || iz < 0 || iz >= sz) return Float.NaN;
        int c = index(ix, iz);
        int n = spanCount[c];
        return n == 0 ? Float.NaN : spanData[(c * MAX_SPANS + n - 1) * 2 + 1];
    }

    /** Whether the water in one column covers the whole vertical interval {@code [lo, hi]}. */
    private boolean covers(int cell, float lo, float hi) {
        int n = spanCount[cell];
        for (int s = 0; s < n; s++) {
            float sLo = spanData[(cell * MAX_SPANS + s) * 2];
            float sHi = spanData[(cell * MAX_SPANS + s) * 2 + 1];
            if (lo >= sLo && hi <= sHi) return true;
        }
        return false;
    }

    @Override
    public boolean contains(float l, float y, float d) {
        int ix = (int) Math.floor(l - gridMinL);
        int iz = (int) Math.floor(d - gridMinD);
        if (ix < 0 || ix >= sx || iz < 0 || iz >= sz) return false;
        int cell = index(ix, iz);
        int n = spanCount[cell];
        for (int s = 0; s < n; s++) {
            float sLo = spanData[(cell * MAX_SPANS + s) * 2];
            float sHi = spanData[(cell * MAX_SPANS + s) * 2 + 1];
            if (y >= sLo && y <= sHi - topMargin) return true;
        }
        return false;
    }

    /**
     * Whether a whole animated body fits centred here: water covers {@code [y − below, y + above]}
     * at the centre and at eight points on a circle of radius {@code body.half()} around it, so no
     * yaw, bank, surf rotation or floor pose can put any part of the sprite outside a surface, a
     * bank or an obstacle.
     *
     * <p>Conservative by construction: the eight-point ring is a sample, not a sweep, so a body
     * wider than a sliver of water that runs exactly between two samples could pass. The failure
     * mode is a corner overlap of a few centimetres, and the alternative — testing the true swept
     * silhouette for every pose — buys that back at a cost no spawn path needs.
     *
     * <p>Always implies {@link #contains} at the centre: the point itself must be in the water it is
     * judged against. Without that, a body whose own head-room is smaller than the pool's would pass
     * here while failing {@code contains} — and {@code contains} is the rule the engine steps a fish
     * under, so a fish spawned into that gap would start outside its own water.
     */
    public boolean bodyFits(float l, float y, float d, BodyBox body) {
        if (!contains(l, y, d)) return false;
        if (body == null) return true;
        float half = body.half();
        float lo = y - body.below();
        float hi = y + body.above();
        if (!sampleCovers(l, lo, hi, d)) return false;
        float diag = half * 0.70710678f;
        return sampleCovers(l + half, lo, hi, d) && sampleCovers(l - half, lo, hi, d)
                && sampleCovers(l, lo, hi, d + half) && sampleCovers(l, lo, hi, d - half)
                && sampleCovers(l + diag, lo, hi, d + diag) && sampleCovers(l - diag, lo, hi, d - diag)
                && sampleCovers(l + diag, lo, hi, d - diag) && sampleCovers(l - diag, lo, hi, d + diag);
    }

    private boolean sampleCovers(float l, float lo, float hi, float d) {
        int ix = (int) Math.floor(l - gridMinL);
        int iz = (int) Math.floor(d - gridMinD);
        if (ix < 0 || ix >= sx || iz < 0 || iz >= sz) return false;
        return covers(index(ix, iz), lo, hi);
    }

    /** Whether the water in one column covers the interval centred on {@code y} for a body. */
    public boolean columnFits(int ix, int iz, float y, BodyBox body) {
        if (ix < 0 || ix >= sx || iz < 0 || iz >= sz) return false;
        float lo = y - (body == null ? 0f : body.below());
        float hi = y + (body == null ? 0f : body.above());
        return covers(index(ix, iz), lo, hi);
    }

    /**
     * How far a position is from the nearest open face, or {@link Float#MAX_VALUE} when every face
     * is real geometry. Conservative: the whole face plane counts, not only the columns where the
     * water actually reaches it, which retires a fish slightly early near an open face and never
     * late.
     */
    public float openFaceDistance(float l, float y, float d) {
        float best = Float.MAX_VALUE;
        if (openFaces[FACE_MIN_L]) best = Math.min(best, l - minL);
        if (openFaces[FACE_MAX_L]) best = Math.min(best, maxL - l);
        if (openFaces[FACE_MIN_D]) best = Math.min(best, d - minD);
        if (openFaces[FACE_MAX_D]) best = Math.min(best, maxD - d);
        if (openFaces[FACE_MIN_Y]) best = Math.min(best, y - minY);
        if (openFaces[FACE_MAX_Y]) best = Math.min(best, maxY - y);
        return best;
    }

    @Override public float minLateral() { return minL; }
    @Override public float maxLateral() { return maxL; }
    @Override public float minVertical() { return minY; }
    @Override public float maxVertical() { return maxY; }
    @Override public float minDepth() { return minD; }
    @Override public float maxDepth() { return maxD; }
    @Override public float[] layerDepths() { return layerDepths; }
    @Override public float sizeGateRun() { return sizeGateRun; }
    @Override public FloorField floor() { return floor; }
    @Override public List<Shelter> shelters() { return shelters; }

    /**
     * Replaces the shelters — for open water, the host's nose anchors ({@link Shelter#noseAnchor}),
     * such as an interactive school's bobbers. Swapped in place, as {@link VoxelDomain} swaps a
     * tank's, so that adding or removing one never costs a domain rebuild.
     */
    public void rebuildShelters(List<Shelter> shelters) {
        this.shelters = List.copyOf(shelters);
    }

    // ------------------------------------------------------------------ steering

    @Override
    public void avoidance(float l, float y, float d, float margin, float marginVertical, float[] out) {
        out[0] = out[1] = out[2] = 0f;
        int ix = (int) Math.floor(l - gridMinL);
        int iz = (int) Math.floor(d - gridMinD);
        if (ix < 0 || ix >= sx || iz < 0 || iz >= sz) {
            // Outside the water entirely: point back at the nearest water in the grid, at full
            // strength. Only reachable for a fish that a rebuild has already retired or re-placed.
            pushTowardWater(l, y, d, ix, iz, out);
            return;
        }
        int cell = index(ix, iz);
        float flat = margin + bankCushion;
        float up = marginVertical + topMargin;
        // Horizontal: the first column along each axis that does not hold water at this height is
        // the wall in that direction; the bounding faces are walls when the patch has no water
        // beyond them. Scanned only as far as the ramp can still reach.
        int reach = Math.max(1, (int) Math.ceil(flat) + 1);
        float dPlusL = axisWall(ix, iz, 1, true, y, l - gridMinL, reach);
        float dMinusL = axisWall(ix, iz, -1, true, y, l - gridMinL, reach);
        float dPlusD = axisWall(ix, iz, 1, false, y, d - gridMinD, reach);
        float dMinusD = axisWall(ix, iz, -1, false, y, d - gridMinD, reach);
        out[0] += ramp(dMinusL, flat) - ramp(dPlusL, flat);
        out[2] += ramp(dMinusD, flat) - ramp(dPlusD, flat);
        // Vertical: the span the fish is in gives the surface above and the bed below directly.
        // The surface keeps the body's own head-room as cushion; the bed does not, because a
        // crawler is supposed to rest on it.
        int n = spanCount[cell];
        for (int s = 0; s < n; s++) {
            float sLo = spanData[(cell * MAX_SPANS + s) * 2];
            float sHi = spanData[(cell * MAX_SPANS + s) * 2 + 1];
            if (y < sLo || y > sHi) continue;
            out[1] += ramp(y - sLo, marginVertical) - ramp(sHi - y, up);
            break;
        }
    }

    /** Proximity weight of a wall {@code dist} away, along the low→high convention of the box ramp. */
    private static float ramp(float dist, float margin) {
        if (margin <= 0f || dist >= margin) return 0f;
        if (dist < 0f) dist = 0f;
        return (margin - dist) / margin;
    }

    /** Whether the column at (ix, iz) holds water at height {@code y}. */
    private boolean holdsWater(int ix, int iz, float y) {
        if (ix < 0 || ix >= sx || iz < 0 || iz >= sz) return false;
        int c = index(ix, iz);
        int n = spanCount[c];
        for (int s = 0; s < n; s++) {
            if (y >= spanData[(c * MAX_SPANS + s) * 2] && y <= spanData[(c * MAX_SPANS + s) * 2 + 1]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Distance from this position to the first wall along one axis: the first column that holds no
     * water at {@code y}, or the domain's bounding face when no such column lies inside the grid.
     *
     * @param step     +1 or −1 along the axis
     * @param lateral  true for the lateral axis, false for the depth axis
     * @param along    position on that axis in grid-local units ({@code [0, extent)})
     * @param maxSteps how far out to look; further than this returns a distance past any ramp
     */
    private float axisWall(int ix, int iz, int step, boolean lateral, float y, float along, int maxSteps) {
        int limit = lateral ? sx : sz;
        int coord = lateral ? ix : iz;
        int steps = Math.max(1, Math.min(maxSteps, limit + 1));
        for (int k = 1; k <= steps; k++) {
            int nc = coord + step * k;
            if (nc < 0 || nc >= limit) {
                // Ran off the grid: the domain's own face is the wall.
                return step > 0 ? (limit - along) : along;
            }
            if (!holdsWater(lateral ? nc : ix, lateral ? iz : nc, y)) {
                // The wall is the plane on the far side of this column, grid-local (coord + k) for
                // a positive step and (coord − k + 1) for a negative one.
                return step > 0 ? (coord + k) - along : along - (coord - k + 1);
            }
        }
        return Float.MAX_VALUE;
    }

    /**
     * Full-strength push toward the nearest column that holds water at this height. Reached only
     * when a position is outside the grid entirely, so it searches a bounded neighbourhood rather
     * than the whole domain.
     */
    private void pushTowardWater(float l, float y, float d, int ix, int iz, float[] out) {
        int cx0 = Math.max(0, ix);
        int cz0 = Math.max(0, iz);
        for (int radius = 0; radius <= 8; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int cx = cx0 + dx;
                    int cz = cz0 + dz;
                    if (!holdsWater(cx, cz, y)) continue;
                    float dxw = (gridMinL + cx + 0.5f) - l;
                    float dzw = (gridMinD + cz + 0.5f) - d;
                    float len = (float) Math.sqrt(dxw * dxw + dzw * dzw);
                    if (len < 1e-4f) return;
                    out[0] = dxw / len;
                    out[2] = dzw / len;
                    return;
                }
            }
        }
    }

    @Override
    public void constrain(float prevL, float prevY, float prevD, float[] pos) {
        if (contains(pos[0], pos[1], pos[2])) return;
        if (!contains(prevL, prevY, prevD)) {
            pos[0] = prevL;
            pos[1] = prevY;
            pos[2] = prevD;
            return;
        }
        float inL = prevL, inY = prevY, inD = prevD;
        float outL = pos[0], outY = pos[1], outD = pos[2];
        for (int iter = 0; iter < 16; iter++) {
            float midL = (inL + outL) * 0.5f;
            float midY = (inY + outY) * 0.5f;
            float midD = (inD + outD) * 0.5f;
            if (contains(midL, midY, midD)) {
                inL = midL;
                inY = midY;
                inD = midD;
            } else {
                outL = midL;
                outY = midY;
                outD = midD;
            }
        }
        pos[0] = inL;
        pos[1] = inY;
        pos[2] = inD;
    }

    @Override
    public float wallDistance(float l, float y, float d) {
        int ix = (int) Math.floor(l - gridMinL);
        int iz = (int) Math.floor(d - gridMinD);
        if (ix < 0 || ix >= sx || iz < 0 || iz >= sz) {
            return -Math.min(Math.min(Math.abs(l - minL), Math.abs(maxL - l)),
                    Math.min(Math.abs(d - minD), Math.abs(maxD - d)));
        }
        int cell = index(ix, iz);
        boolean inside = false;
        float best = Float.MAX_VALUE;
        int n = spanCount[cell];
        for (int s = 0; s < n; s++) {
            float sLo = spanData[(cell * MAX_SPANS + s) * 2];
            float sHi = spanData[(cell * MAX_SPANS + s) * 2 + 1];
            if (y >= sLo && y <= sHi) {
                inside = true;
                best = Math.min(best, Math.min(y - sLo, sHi - y));
                break;
            }
        }
        int reach = Math.max(sx, sz) + 1;
        float uL = l - gridMinL;
        float uD = d - gridMinD;
        best = Math.min(best, Math.min(
                Math.min(axisWall(ix, iz, 1, true, y, uL, reach), axisWall(ix, iz, -1, true, y, uL, reach)),
                Math.min(axisWall(ix, iz, 1, false, y, uD, reach), axisWall(ix, iz, -1, false, y, uD, reach))));
        if (best == Float.MAX_VALUE) best = Math.max(sx, sz);
        return inside ? Math.max(best, 0f) : -Math.max(best, 0f);
    }

    private int index(int ix, int iz) {
        return ix * sz + iz;
    }
}
