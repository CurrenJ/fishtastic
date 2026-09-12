package grill24.fishsim.core;

import grill24.fishsim.domain.FlockDomain;

import java.util.Arrays;

/**
 * Uniform-grid spatial index over the planar model's fish positions — the O(n²) → O(n) fix from
 * docs/fish-tank-group-scaling.md §5.2.
 *
 * <p>Both of {@code stepFishPlanar}'s brute-force passes (species-aware separation with its
 * anticipatory extension, and {@code findNearestSwimmers}) are radius-limited, and §3.2 of that
 * document measured the number of interactions a fish actually has as essentially constant in
 * {@code n} — set by local density, not by how many fish exist. This class turns "scan every
 * fish" into "scan the cells that could possibly hold one in range".
 *
 * <p><b>The candidate list is emitted in ascending fish index, and that is load-bearing.</b> The
 * two loops accumulate float sums, so their result depends on summation order; the neighbour
 * search's insertion keeps the earliest index on a distance tie. Emitting candidates in any other
 * order would perturb the last bits of every trajectory and move
 * {@code VoxelGoldenTrajectoryTest}'s fixture — and per §5.2 an index that changes behaviour is
 * not an optimisation. Hence the bitset: a counting sort keeps each cell's members ascending, and
 * walking the set bits of a per-query bitset merges the scanned cells back into global ascending
 * order for O(candidates + n/64) rather than the O(m log m) a sort would cost.
 *
 * <p>Skipped fish must contribute <em>exactly</em> zero to both loops, which is why the caller
 * sizes {@link #configure} with an interaction radius that bounds the anticipatory separation
 * term as well as the plain distance terms — see {@code FlockEngine.interactionRadius()}.
 */
final class NeighborGrid {

    /** Set when the grid is unusable (unbounded neighbour range, empty domain) — caller brute-forces. */
    private boolean active;

    private float cell;
    private float minL, minY, minD;
    private int nx, ny, nz;

    /** Per-cell start offsets into {@link #items}, prefix-summed; length {@code cells + 1}. */
    private int[] cellStart = new int[1];
    /** Scratch cursor for the placement pass, and the per-cell counts before the prefix sum. */
    private int[] cursor = new int[1];
    /** Fish indices bucketed by cell, ascending within each cell. */
    private int[] items = new int[0];
    /** Cell index each fish landed in, memoized by {@link #build}. */
    private int[] cellOf = new int[0];

    /** Candidate membership for the query in flight; always fully cleared again by {@link #gather}. */
    private long[] bits = new long[0];
    /** The emitted candidate list, ascending. */
    private int[] out = new int[0];

    /** How far a query reaches: the interaction radius plus one tick of travel (see {@link #build}). */
    private float reach;

    boolean active() {
        return active;
    }

    int[] candidates() {
        return out;
    }

    /**
     * Sizes the grid for a domain and fish count. Called on rebuild and whenever the tunables
     * change the interaction radius — never per step.
     *
     * @param radius the interaction radius: no fish farther than this may affect another
     * @param slack  the farthest a fish can travel in one step, added to every query's reach
     *               because {@link #build} indexes start-of-step positions while the step loop
     *               reads live ones (fish before {@code i} in the loop have already moved)
     */
    void configure(FlockDomain domain, float radius, float slack, int count) {
        if (!(radius > 0f) || radius == Float.MAX_VALUE || Float.isInfinite(radius) || count <= 0) {
            active = false;
            return;
        }
        this.cell = radius;
        this.reach = radius + slack;
        this.minL = domain.minLateral();
        this.minY = domain.minVertical();
        this.minD = domain.minDepth();
        this.nx = axisCells(domain.maxLateral() - minL);
        this.ny = axisCells(domain.maxVertical() - minY);
        this.nz = axisCells(domain.maxDepth() - minD);

        int cells = nx * ny * nz;
        if (cellStart.length != cells + 1) {
            cellStart = new int[cells + 1];
            cursor = new int[cells];
        }
        if (items.length != count) {
            items = new int[count];
            cellOf = new int[count];
            out = new int[count];
        }
        int words = (count + 63) >>> 6;
        if (bits.length != words) bits = new long[words];
        active = true;
    }

    private int axisCells(float extent) {
        return Math.max(1, (int) Math.ceil(extent / cell));
    }

    /**
     * Buckets every fish by cell, in ascending index order (a counting sort — stable, so each
     * cell's slice comes out ascending, which is what lets {@link #gather} restore a global
     * ascending order cheaply).
     *
     * <p>Positions are the step's <em>start</em> positions; see {@code slack} on
     * {@link #configure} for why that is safe.
     */
    void build(float[] pl, float[] py, float[] pd, int count) {
        if (!active) return;
        int cells = nx * ny * nz;
        Arrays.fill(cursor, 0, cells, 0);
        for (int i = 0; i < count; i++) {
            int c = cellIndex(pl[i], py[i], pd[i]);
            cellOf[i] = c;
            cursor[c]++;
        }
        int running = 0;
        for (int c = 0; c < cells; c++) {
            cellStart[c] = running;
            running += cursor[c];
            cursor[c] = cellStart[c];
        }
        cellStart[cells] = running;
        for (int i = 0; i < count; i++) {
            items[cursor[cellOf[i]]++] = i;
        }
    }

    /**
     * Collects every fish that could be within the interaction radius of this position into
     * {@link #candidates()}, ascending, and returns how many.
     *
     * <p>The cell range is derived from {@code position ± reach} rather than being a fixed 3×3×3
     * block, which covers 2 or 3 cells per axis depending on where in its own cell the query sits
     * — about 15.6 cells on average instead of 27, for the same guarantee.
     */
    int gather(float l, float y, float d) {
        if (!active) return -1;
        int loX = axisIndex(l - reach, minL, nx), hiX = axisIndex(l + reach, minL, nx);
        int loY = axisIndex(y - reach, minY, ny), hiY = axisIndex(y + reach, minY, ny);
        int loZ = axisIndex(d - reach, minD, nz), hiZ = axisIndex(d + reach, minD, nz);

        int minWord = Integer.MAX_VALUE, maxWord = -1;
        for (int ix = loX; ix <= hiX; ix++) {
            for (int iy = loY; iy <= hiY; iy++) {
                int base = (ix * ny + iy) * nz;
                int from = cellStart[base + loZ], to = cellStart[base + hiZ + 1];
                for (int p = from; p < to; p++) {
                    int j = items[p];
                    int w = j >>> 6;
                    bits[w] |= 1L << j;
                    if (w < minWord) minWord = w;
                    if (w > maxWord) maxWord = w;
                }
            }
        }

        int m = 0;
        for (int w = minWord; w <= maxWord; w++) {
            long bit = bits[w];
            if (bit == 0L) continue;
            bits[w] = 0L;
            int base = w << 6;
            while (bit != 0L) {
                out[m++] = base + Long.numberOfTrailingZeros(bit);
                bit &= bit - 1L;
            }
        }
        return m;
    }

    private int cellIndex(float l, float y, float d) {
        int ix = axisIndex(l, minL, nx);
        int iy = axisIndex(y, minY, ny);
        int iz = axisIndex(d, minD, nz);
        return (ix * ny + iy) * nz + iz;
    }

    private int axisIndex(float v, float origin, int n) {
        int i = (int) Math.floor((v - origin) / cell);
        return i < 0 ? 0 : Math.min(i, n - 1);
    }
}
