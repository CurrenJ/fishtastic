package grill24.fishsim.ambient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Incremental, budgeted discovery of the loaded water around a point, and the stable patch keys
 * the ambient population is anchored to.
 *
 * <p><b>How it searches.</b> The window is swept as expanding square rings centred on the player's
 * own column, one column at a time through a {@link WaterSource}: every column costs one
 * {@link WaterSource#flags} call per cell in the scan band, plus a fluid and a collision lookup for
 * the cells that have them. The sweep stops the moment a per-tick budget of classified cells or
 * scanned columns is spent and resumes exactly where it stopped next tick, so a whole window is
 * covered over several seconds and never in one tick. A ring sweep — rather than a queue — is what
 * makes that safe: there is no frontier to grow, no column that can be re-enqueued forever, and
 * the nearest water is always examined first. Nothing here ever asks for a chunk to be loaded: an
 * unloaded column is simply not water this sweep, and is retried when the rings come round again.
 * A slice of the same cell budget is also spent re-reading a few known columns each tick (see
 * {@link #revalidatePass}), which is the only way an edit inside already-cached water is noticed.
 *
 * <p><b>Patches.</b> A patch is a set of discovered columns connected through <em>overlapping
 * water</em>: two columns are connected when their spans share a height — the rule for water that
 * can be swum through. Each patch gets a key and an origin decided once, from the column where it
 * was first found, and never changed afterwards, so a patch's local coordinate frame never moves
 * however the window slides (docs/fish-in-world.md §frame). Disconnected pools are separate
 * patches. Terrain that later <em>splits</em> a patch leaves both halves under the one key: the key
 * names the water a population belongs to; the geometry, not the key, is what keeps fish from
 * crossing dry land.
 *
 * <p><b>Eviction.</b> Columns outside a square window of radius {@link #radius()} +
 * {@link #evictSlack()} around the player are dropped — the same square the sweep discovers in,
 * grown by the slack, so the two can never fight over columns neither will yield (see
 * {@link #within}). With them goes the water they held; a patch with no columns left is no longer
 * reported as live, which is how the manager learns to retire its domain. Keys stay reserved for
 * {@link #PATCH_LIFETIME_TICKS} after their last column goes, so walking out of a pond and back
 * does not renumber it.
 *
 * <p>Single-threaded (client thread only). Not Minecraft-aware: everything it knows arrives
 * through {@link WaterSource}.
 */
public final class WaterDiscovery {

    /** Columns scanned per tick, before the cell budget also applies. */
    public static final int DEFAULT_COLUMN_BUDGET = 48;
    /** Classified cells per tick — the Phase 0 proposal (≤512 classifications/tick). */
    public static final int DEFAULT_CELL_BUDGET = 512;
    /** Horizontal discovery radius in blocks (Phase 0 proposal: 24). */
    public static final int DEFAULT_RADIUS = 24;
    /** Blocks above the player's feet the column scan looks for water. */
    public static final int DEFAULT_Y_ABOVE = 8;
    /** Blocks below the player's feet the column scan looks for water. */
    public static final int DEFAULT_Y_BELOW = 16;
    /** Hard cap on cached columns; the farthest are evicted past this. */
    public static final int DEFAULT_MAX_COLUMNS = 4096;
    /** Evict this far beyond the discovery radius — hysteresis so a sliding window stays quiet. */
    public static final int DEFAULT_EVICT_SLACK = 8;
    /** Known columns re-read per tick from the rotating cursor, from the tick's residual budget. */
    public static final int DEFAULT_REVALIDATE_COLUMNS_PER_TICK = 48;
    /** Consecutive non-water cells that end a column's water body. */
    public static final int GAP_CELLS = 4;
    /** Runs (intervals) kept per column; anything past this is dropped as detail nobody can reach. */
    public static final int MAX_SPANS = 6;
    /** How long a key stays reserved after its patch loses its last column (30 s at 20 tps). */
    public static final long PATCH_LIFETIME_TICKS = 600L;
    /** Blocks per patch-key quantum. */
    public static final int PATCH_QUANTUM = 16;

    private static final float MERGE_EPSILON = 1e-3f;
    /** Candidate columns examined per tick regardless of how many turn out to be known already. */
    private static final int CURSOR_STEPS_PER_TICK = 512;

    /** One discovered column and the patch it belongs to. */
    public record Column(int x, int z, WaterColumn water, long patchKey, int contextSignature) {}

    /**
     * A body of loaded water sharing one key. {@code origin*} is the patch's fixed local frame
     * origin; a local coordinate is a world coordinate minus it.
     */
    public record Patch(long key, int seedX, int seedZ, int originX, int originY, int originZ,
                        int columns, long lastSeenTick, long createdTick) {

        public boolean alive() {
            return columns > 0;
        }

        /** A patch with no columns but a reserved key. */
        public boolean reserved() {
            return columns == 0;
        }
    }

    /** Per-tick and cumulative counters, for the harness and the dev overlay. */
    public static final class Counters {
        public long columnsScanned;
        public long cellsClassified;
        public long unloadedScans;
        public long truncatedColumns;
        public long columnsEvicted;
        public long columnsChanged;
        public long patchMerges;
        /** Revalidations skipped because the player's band could not see the stored water. */
        public long revalidationsDeferred;
        public int scansThisTick;
        public int cellsThisTick;
        public int revalidatedThisTick;
        public int cursorStepsThisTick;

        void tickReset() {
            scansThisTick = 0;
            cellsThisTick = 0;
            revalidatedThisTick = 0;
            cursorStepsThisTick = 0;
        }

        public void reset() {
            columnsScanned = cellsClassified = unloadedScans = truncatedColumns = 0;
            columnsEvicted = columnsChanged = patchMerges = 0;
            revalidationsDeferred = 0;
            tickReset();
        }
    }

    /** One patch's columns and a version that moves whenever any of them changes. */
    private static final class PatchColumns {
        final java.util.Set<Entry> entries = new java.util.HashSet<>();
        long version;
    }

    /** Monotonic across patches, so a key that comes back never repeats an old version. */
    private long nextVersion = 1;

    private void attach(Entry e) {
        nearestDistances = null;
        PatchColumns pc = byPatch.computeIfAbsent(e.patchKey, k -> new PatchColumns());
        pc.entries.add(e);
        pc.version = nextVersion++;
    }

    private void detach(Entry e) {
        nearestDistances = null;
        PatchColumns pc = byPatch.get(e.patchKey);
        if (pc == null) return;
        pc.entries.remove(e);
        if (pc.entries.isEmpty()) byPatch.remove(e.patchKey);
        else pc.version = nextVersion++;
    }

    private void touch(long patchKey) {
        nearestDistances = null;
        PatchColumns pc = byPatch.get(patchKey);
        if (pc != null) pc.version = nextVersion++;
    }

    private void putColumn(Entry e) {
        columns.put(cellKey(e.x, e.z), e);
        attach(e);
        columnSnapshot = null;
    }

    private void removeColumn(Entry e) {
        columns.remove(cellKey(e.x, e.z));
        detach(e);
        columnSnapshot = null;
    }

    private static final class Entry {
        final int x;
        final int z;
        WaterColumn water;
        long patchKey;
        Object contextKey;

        Entry(int x, int z, WaterColumn water, long patchKey) {
            this.x = x;
            this.z = z;
            this.water = water;
            this.patchKey = patchKey;
        }
    }

    private final Map<Long, Entry> columns = new HashMap<>();
    private final Map<Long, Patch> patches = new HashMap<>();
    /**
     * Each patch's columns, kept in step with {@link #columns}, so per-patch queries never scan the
     * whole cache. Holds only patches with at least one column.
     */
    private final Map<Long, PatchColumns> byPatch = new HashMap<>();
    private Map<Long, Integer> nearestDistances;
    private int nearestX, nearestZ;
    /** {@link #columns}' values in iteration order, rebuilt only after a structural change. */
    private Object[] columnSnapshot;
    private final Counters counters = new Counters();
    private final float[] scratchLo = new float[96];
    private final float[] scratchHi = new float[96];
    private final float[] mergeBuf = new float[MAX_SPANS * 2];
    private final int[] ringCell = new int[2];

    /** Instance numbers start at one: package-private key {@code 0} means "no patch". */
    private int columnBudget = DEFAULT_COLUMN_BUDGET;
    private int cellBudget = DEFAULT_CELL_BUDGET;
    private int radius = DEFAULT_RADIUS;
    private int yAbove = DEFAULT_Y_ABOVE;
    private int yBelow = DEFAULT_Y_BELOW;
    private int maxColumns = DEFAULT_MAX_COLUMNS;
    private int evictSlack = DEFAULT_EVICT_SLACK;
    private int revalidateColumnsPerTick = DEFAULT_REVALIDATE_COLUMNS_PER_TICK;
    private int nextInstance = 1;

    // Ring sweep cursor, relative to the player's column. Never reset: a player who keeps moving
    // must not restart the sweep from the middle each tick, or the leading edge would never be
    // reached. The near pass below is what keeps water beside the player quick to appear.
    private int ring;
    private int ringIndex;
    private int nearCursor;
    private long lastEvictTick = Long.MIN_VALUE;
    private int revalidateCursor;
    private static final int NEAR_RADIUS = 2;
    /** Columns on a side of the near square. */
    private static final int NEAR_SIDE = 2 * NEAR_RADIUS + 1;

    // ------------------------------------------------------------------ configuration

    public void configure(int radius, int yAbove, int yBelow, int columnBudget, int cellBudget) {
        setRadius(radius);
        setVerticalWindow(yAbove, yBelow);
        setBudgets(columnBudget, cellBudget);
    }

    public void setBudgets(int columnBudget, int cellBudget) {
        this.columnBudget = Math.max(1, columnBudget);
        this.cellBudget = Math.max(1, cellBudget);
    }

    public int columnBudget() {
        return columnBudget;
    }

    public int cellBudget() {
        return cellBudget;
    }

    public void setRadius(int radius) {
        this.radius = Math.max(1, radius);
    }

    public int radius() {
        return radius;
    }

    public void setVerticalWindow(int yAbove, int yBelow) {
        this.yAbove = Math.max(0, yAbove);
        this.yBelow = Math.max(0, yBelow);
    }

    /** Blocks above the player's feet the scan band covers. */
    public int yAbove() {
        return yAbove;
    }

    /** Blocks below the player's feet the scan band covers. */
    public int yBelow() {
        return yBelow;
    }

    /**
     * Cells one scan can classify at most: the band, plus the one-cell probe above the band's top
     * (a column whose water fills the top cell is checked against the cell above it).
     */
    public int scanBandCells() {
        return yAbove + yBelow + 2;
    }

    public int maxColumns() {
        return maxColumns;
    }

    public void setMaxColumns(int maxColumns) {
        this.maxColumns = Math.max(16, maxColumns);
    }

    public void setEvictSlack(int slack) {
        this.evictSlack = Math.max(0, slack);
    }

    public void setRevalidateColumnsPerTick(int n) {
        this.revalidateColumnsPerTick = Math.max(0, n);
    }

    public int revalidateColumnsPerTick() {
        return revalidateColumnsPerTick;
    }

    public Counters counters() {
        return counters;
    }

    public void reset() {
        columns.clear();
        patches.clear();
        byPatch.clear();
        nearestDistances = null;
        columnSnapshot = null;
        counters.reset();
        nextInstance = 1; // never zero: a zero key means "no patch"
        ring = 0;
        ringIndex = 0;
        nearCursor = 0;
        lastEvictTick = Long.MIN_VALUE;
        revalidateCursor = 0;
    }

    // ------------------------------------------------------------------ queries

    public int columnCount() {
        return columns.size();
    }

    public int patchCount() {
        int n = 0;
        for (Patch p : patches.values()) {
            if (p.alive()) n++;
        }
        return n;
    }

    public WaterColumn columnAt(int x, int z) {
        Entry e = columns.get(cellKey(x, z));
        return e == null ? null : e.water;
    }

    public long patchKeyAt(int x, int z) {
        Entry e = columns.get(cellKey(x, z));
        return e == null ? 0L : e.patchKey;
    }

    /** Every discovered column, as a snapshot. */
    public List<Column> columns() {
        List<Column> out = new ArrayList<>(columns.size());
        for (Entry e : columns.values()) out.add(new Column(e.x, e.z, e.water, e.patchKey, java.util.Objects.hashCode(e.contextKey)));
        return out;
    }

    /**
     * Discovered columns of one patch, as a snapshot, in no particular order — read from the patch's
     * own index, so it costs the patch's size rather than the whole cache's.
     */
    public List<Column> patchColumns(long patchKey) {
        PatchColumns pc = byPatch.get(patchKey);
        if (pc == null) return new ArrayList<>();
        List<Column> out = new ArrayList<>(pc.entries.size());
        for (Entry e : pc.entries) out.add(new Column(e.x, e.z, e.water, e.patchKey, java.util.Objects.hashCode(e.contextKey)));
        return out;
    }

    /**
     * A number that changes whenever any column of the patch is added, removed, re-read with
     * different water or re-keyed — and is otherwise stable, so a caller can cache anything derived
     * from {@link #patchColumns} against it. Zero for a patch with no columns.
     */
    public long patchVersion(long patchKey) {
        PatchColumns pc = byPatch.get(patchKey);
        return pc == null ? 0L : pc.version;
    }

    /**
     * The squared horizontal distance from {@code (x, z)} to the nearest discovered column of every
     * patch that has one — the same figure a scan of {@link #columns()} would fold, without the copy.
     */
    public Map<Long, Integer> nearestSquaredDistances(int x, int z) {
        if (nearestDistances != null && nearestX == x && nearestZ == z) return nearestDistances;
        Map<Long, Integer> out = new HashMap<>(byPatch.size() * 2);
        for (Map.Entry<Long, PatchColumns> pe : byPatch.entrySet()) {
            int best = Integer.MAX_VALUE;
            for (Entry e : pe.getValue().entries) {
                int dx = e.x - x, dz = e.z - z;
                best = Math.min(best, dx * dx + dz * dz);
            }
            out.put(pe.getKey(), best);
        }
        nearestX = x;
        nearestZ = z;
        nearestDistances = Map.copyOf(out);
        return nearestDistances;
    }

    /** Discovered columns of one patch, as a snapshot, in the whole cache's iteration order. */
    public List<Column> columnsOf(long patchKey) {
        List<Column> out = new ArrayList<>();
        for (Entry e : columns.values()) {
            if (e.patchKey == patchKey) out.add(new Column(e.x, e.z, e.water, e.patchKey, java.util.Objects.hashCode(e.contextKey)));
        }
        return out;
    }

    /** Patches that currently hold at least one column. */
    public List<Patch> livePatches() {
        List<Patch> out = new ArrayList<>();
        for (Patch p : patches.values()) {
            if (p.alive()) out.add(p);
        }
        return out;
    }

    public Patch patch(long key) {
        return patches.get(key);
    }

    public int columnCount(long patchKey) {
        Patch p = patches.get(patchKey);
        return p == null ? 0 : p.columns();
    }

    /** Whether a key is still reserved — alive, or within its lifetime with no columns. */
    public boolean keyReserved(long patchKey) {
        return patches.containsKey(patchKey);
    }

    // ------------------------------------------------------------------ driving

    /**
     * Advances discovery by at most one budget of work: evicts when due, then continues the ring
     * sweep around the player's column, with a revalidation pass between the near pass and the
     * sweep so edits inside already-known water are noticed while the class is being driven.
     *
     * @param px player's block X — the centre of the discovered window
     * @param py player's feet Y — the centre of each column's scan band
     * @param pz player's block Z
     */
    public void tick(WaterSource source, int px, int py, int pz, long tick) {
        counters.tickReset();
        if (lastEvictTick == Long.MIN_VALUE || tick - lastEvictTick >= 10) {
            evict(px, pz, tick);
            lastEvictTick = tick;
        }
        nearPass(source, px, py, pz, tick);
        revalidatePass(source, py, tick);
        sweep(source, px, py, pz, tick);
        // The cap is enforced once per tick, after every scan that could have stored water — not on
        // the scan path, which would trim with a fake tick and re-sort the whole map per store.
        if (columns.size() > maxColumns) trim(px, pz, tick);
    }

    /**
     * Re-scans up to {@link #revalidateColumnsPerTick} known columns from the tick's residual cell
     * budget — what {@link #nearPass} left, above a floor reserved for {@link #sweep}.
     *
     * <p>This is the only path that ever re-reads a known column, so without it an edit inside
     * cached water is invisible: the sweep skips columns that are already known and the near pass
     * only rescans cells it has never stored. And the residual has to be taken <em>before</em> the
     * sweep, because the sweep spends every cell it can every tick wherever dry land keeps it busy
     * — a caller-side pass over "whatever is left" after {@link #tick} sees a leftover below one
     * column's band and never runs at all. The sweep floor is the other half of the trade: the
     * sweep is the only thing that finds new water, so revalidation may slow it, never starve it.
     */
    private void revalidatePass(WaterSource source, int py, long tick) {
        int band = scanBandCells();
        int spare = Math.max(0, cellBudget - counters.cellsThisTick);
        int allowance = Math.max(0, spare - cellBudget / 4) / band;
        int budget = Math.min(revalidateColumnsPerTick, allowance);
        if (budget > 0) revalidateSome(source, py, budget, tick);
    }

    /**
     * The columns immediately around the player, so water beside the player appears within a tick.
     * <p>
     * The near square is scanned on its own rotating cursor and may spend at most half the cell
     * budget: dry columns are not remembered, so a square that is re-scanned from its first cell
     * every tick would spend the whole budget on the same handful of cells and starve {@link
     * #sweep}, which is what actually expands discovery. Water further out than the square is only
     * ever found by the sweep.
     */
    private void nearPass(WaterSource source, int px, int py, int pz, long tick) {
        int nearBudget = cellBudget / 2;
        int band = scanBandCells();
        for (int i = 0; i < NEAR_SIDE * NEAR_SIDE; i++) {
            int cell = nearCursor;
            nearCursor = (nearCursor + 1) % (NEAR_SIDE * NEAR_SIDE);
            if (!canScan()) return;
            if (counters.cellsThisTick > 0 && counters.cellsThisTick + band > nearBudget) return;
            int x = px + Math.floorDiv(cell, NEAR_SIDE) - NEAR_RADIUS;
            int z = pz + Math.floorMod(cell, NEAR_SIDE) - NEAR_RADIUS;
            if (columns.containsKey(cellKey(x, z))) continue;
            scanInto(source, x, z, py, tick);
        }
    }

    private boolean canScan() {
        int band = scanBandCells();
        if (counters.scansThisTick >= columnBudget) return false;
        return counters.cellsThisTick == 0 || counters.cellsThisTick + band <= cellBudget;
    }

    private void sweep(WaterSource source, int px, int py, int pz, long tick) {
        int steps = 0;
        while (steps++ < CURSOR_STEPS_PER_TICK) {
            counters.cursorStepsThisTick++;
            if (ring > radius) {
                // The window is fully swept; start again so edits and newly loaded chunks are seen.
                ring = 0;
                ringIndex = 0;
                continue;
            }
            if (ringIndex >= ring * 8 && ring > 0) {
                ring++;
                ringIndex = 0;
                continue;
            }
            ringCell(ring, ringIndex, ringCell);
            int x = px + ringCell[0];
            int z = pz + ringCell[1];
            if (columns.containsKey(cellKey(x, z))) {
                advanceCursor();
                continue;
            }
            // The cursor only moves once the cell is done with: advancing first would drop the cell the
            // budget ran out on, leaving a hole no later tick of this round comes back for.
            if (!canScan()) return;
            advanceCursor();
            scanInto(source, x, z, py, tick);
        }
    }

    private void advanceCursor() {
        ringIndex++;
        if (ring == 0) {
            ring = 1;
            ringIndex = 0;
        } else if (ringIndex >= 8 * ring) {
            ring++;
            ringIndex = 0;
        }
    }

    /** Cell {@code i} of the square ring of radius {@code r} about the origin, into {@code out}. */
    static void ringCell(int r, int i, int[] out) {
        if (r <= 0) {
            out[0] = 0;
            out[1] = 0;
            return;
        }
        int side = Math.floorDiv(i, 2 * r);
        int k = Math.floorMod(i, 2 * r);
        switch (side) {
            case 0 -> {
                out[0] = -r + k;
                out[1] = -r;
            }
            case 1 -> {
                out[0] = r;
                out[1] = -r + k;
            }
            case 2 -> {
                out[0] = r - k;
                out[1] = r;
            }
            default -> {
                out[0] = -r;
                out[1] = r - k;
            }
        }
    }

    /** Scans one column and files it with its patch when it holds water. */
    private void scanInto(WaterSource source, int x, int z, int py, long tick) {
        WaterColumn col = scanColumn(source, x, z, py + yAbove, py - yBelow);
        counters.scansThisTick++;
        counters.columnsScanned++;
        if (col == null) {
            counters.unloadedScans++;
            return; // not loaded: stay unknown, try again when the rings come round
        }
        if (col.truncated()) counters.truncatedColumns++;
        if (col.isEmpty()) return; // dry: nothing to remember
        Object contextKey = source.contextKey(x, (int) Math.floor(col.surfaceY() - 1e-4), z);
        long patchKey = patchKeyFor(x, z, col, contextKey, tick);
        Entry entry = new Entry(x, z, col, patchKey);
        entry.contextKey = contextKey;
        putColumn(entry);
        bumpPatch(patchKey, 1, tick);
    }

    /**
     * The patch a newly discovered column belongs to: the merge of every overlapping discovered
     * neighbour's patch, or a brand-new patch when it shares water with none of them.
     */
    private long patchKeyFor(int x, int z, WaterColumn col, Object contextKey, long tick) {
        long best = 0L;
        long[] candidates = new long[4];
        int n = 0;
        for (int i = 0; i < 4; i++) {
            int nx = x + (i == 0 ? 1 : i == 1 ? -1 : 0);
            int nz = z + (i == 2 ? 1 : i == 3 ? -1 : 0);
            Entry e = columns.get(cellKey(nx, nz));
            if (e == null || !java.util.Objects.equals(contextKey, e.contextKey) || !col.overlaps(e.water)) continue;
            long pk = e.patchKey;
            if (contains(candidates, n, pk)) continue;
            candidates[n++] = pk;
            // Coordinates packed into a key are not age or priority. Expanding west used to
            // replace an established body's key with a newer negative key and retire its fish.
            if (best == 0L || olderPatch(pk, best)) best = pk;
        }
        if (best == 0L) return createPatch(x, z, col, tick);
        for (int i = 0; i < n; i++) {
            if (candidates[i] != best) mergeInto(candidates[i], best, tick);
        }
        return best;
    }

    private boolean olderPatch(long candidate, long incumbent) {
        Patch a = patches.get(candidate), b = patches.get(incumbent);
        return a.createdTick() < b.createdTick()
                || (a.createdTick() == b.createdTick() && keyInstance(candidate) < keyInstance(incumbent));
    }

    private static boolean contains(long[] arr, int n, long v) {
        for (int i = 0; i < n; i++) {
            if (arr[i] == v) return true;
        }
        return false;
    }

    private long createPatch(int x, int z, WaterColumn col, long tick) {
        int qx = Math.floorDiv(x, PATCH_QUANTUM);
        int qz = Math.floorDiv(z, PATCH_QUANTUM);
        long key = 0L;
        // Reuse a reserved key for the same quantum when one is free, so a key that has gone quiet
        // comes back instead of a new instance number being minted.
        for (Patch p : patches.values()) {
            if (!p.alive() && Math.floorDiv(p.seedX(), PATCH_QUANTUM) == qx
                    && Math.floorDiv(p.seedZ(), PATCH_QUANTUM) == qz) {
                key = p.key();
                break;
            }
        }
        if (key == 0L) key = packKey(qx, qz, nextInstance++);
        Patch prior = patches.get(key);
        if (prior != null) {
            // A reserved key comes back as itself: seed, origin and birth tick belong to the key,
            // not the incarnation. The population that stayed while the water went quiet keeps its
            // coordinate frame — which is the whole point of reserving the key at all.
            patches.put(key, new Patch(key, prior.seedX(), prior.seedZ(), prior.originX(),
                    prior.originY(), prior.originZ(), 0, tick, prior.createdTick()));
            return key;
        }
        float bed = col.bedY();
        int originY = Float.isNaN(bed) ? 0 : (int) Math.floor(bed) - 1;
        patches.put(key, new Patch(key, x, z, x, originY, z, 0, tick, tick));
        return key;
    }

    private void bumpPatch(long patchKey, int delta, long tick) {
        Patch p = patches.get(patchKey);
        if (p == null) return;
        patches.put(patchKey, new Patch(p.key(), p.seedX(), p.seedZ(), p.originX(), p.originY(),
                p.originZ(), Math.max(0, p.columns() + delta), tick, p.createdTick()));
    }

    /** Folds {@code loser} into {@code winner}: its columns are re-keyed and its record dropped. */
    private void mergeInto(long loser, long winner, long tick) {
        if (loser == winner || !patches.containsKey(loser)) return;
        counters.patchMerges++;
        Patch p = patches.get(loser);
        PatchColumns moved = byPatch.remove(loser);
        if (moved != null) {
            for (Entry e : moved.entries) {
                e.patchKey = winner;
                attach(e);
            }
        }
        patches.remove(loser);
        bumpPatch(winner, p == null ? 0 : p.columns(), tick);
    }

    /**
     * Re-scans one known column and updates it, returning whether anything changed. The manager
     * calls this on a rotation so block and fluid edits are noticed without a global rescan.
     *
     * <p>A column whose stored water lies outside the player's own scan band is left alone: the
     * band is the only view this class ever gets, and one that starts above the water or stops
     * above its bed reads "no water" where the water has merely left the window. Re-scanning
     * through such a band would delete live columns and the patch built on them, or re-write them
     * truncated against a band edge that is not a bed — so the reading is kept and tried again
     * when the band covers it.
     */
    public boolean revalidate(WaterSource source, int x, int z, int py, long tick) {
        Entry e = columns.get(cellKey(x, z));
        if (e == null) return false;
        int yFrom = py + yAbove;
        int yTo = py - yBelow;
        if (e.water.surfaceY() > yFrom + 1 + MERGE_EPSILON || e.water.bedY() < yTo - MERGE_EPSILON) {
            counters.revalidationsDeferred++;
            return false;
        }
        WaterColumn fresh = scanColumn(source, x, z, yFrom, yTo);
        counters.revalidatedThisTick++;
        if (fresh == null) {
            counters.unloadedScans++;
            return false; // chunk gone: keep the column and let eviction handle it
        }
        Object contextKey = fresh.isEmpty() ? e.contextKey
                : source.contextKey(x, (int) Math.floor(fresh.surfaceY() - 1e-4), z);
        boolean contextChanged = !java.util.Objects.equals(contextKey, e.contextKey);
        if (fresh.spansEqual(e.water) && !contextChanged) {
            bumpPatch(e.patchKey, 0, tick);
            return false;
        }
        counters.columnsChanged++;
        if (fresh.isEmpty()) {
            removeColumn(e);
            bumpPatch(e.patchKey, -1, tick);
            return true;
        }
        if (fresh.truncated()) counters.truncatedColumns++;
        if (contextChanged) {
            bumpPatch(e.patchKey, -1, tick);
            removeColumn(e);
            e.patchKey = patchKeyFor(x, z, fresh, contextKey, tick);
            e.contextKey = contextKey;
            putColumn(e);
            bumpPatch(e.patchKey, 1, tick);
        }
        e.water = fresh;
        touch(e.patchKey);
        bumpPatch(e.patchKey, 0, tick);
        return true;
    }

    /** Re-scans up to {@code budget} known columns on a rotating cursor; returns how many changed. */
    public int revalidateSome(WaterSource source, int py, int budget, long tick) {
        int n = columns.size();
        if (n == 0 || budget <= 0) return 0;
        if (columnSnapshot == null) columnSnapshot = columns.values().toArray();
        Object[] snapshot = columnSnapshot;
        int changed = 0;
        int count = Math.min(budget, n);
        for (int i = 0; i < count; i++) {
            Entry e = (Entry) snapshot[Math.floorMod(revalidateCursor + i, n)];
            if (revalidate(source, e.x, e.z, py, tick)) changed++;
        }
        revalidateCursor += count;
        return changed;
    }

    private void trim(int px, int pz, long tick) {
        if (columns.size() <= maxColumns) return;
        List<Map.Entry<Long, Entry>> order = new ArrayList<>(columns.entrySet());
        order.sort((a, b) -> Integer.compare(dist(b.getValue(), px, pz), dist(a.getValue(), px, pz)));
        int toDrop = columns.size() - maxColumns;
        for (int i = 0; i < toDrop && i < order.size(); i++) {
            Entry e = order.get(i).getValue();
            removeColumn(e);
            bumpPatch(e.patchKey, -1, tick);
            counters.columnsEvicted++;
        }
    }

    private static int dist(Entry e, int x, int z) {
        int dx = e.x - x;
        int dz = e.z - z;
        return dx * dx + dz * dz;
    }

    private void evict(int px, int pz, long tick) {
        int limit = radius + evictSlack;
        List<Entry> dropped = null;
        for (Entry e : columns.values()) {
            if (!within(px, pz, e.x, e.z, limit)) {
                if (dropped == null) dropped = new ArrayList<>();
                dropped.add(e);
            }
        }
        if (dropped != null) {
            for (Entry e : dropped) {
                removeColumn(e);
                bumpPatch(e.patchKey, -1, tick);
                counters.columnsEvicted++;
            }
        }
        expirePatches(tick);
    }

    private void expirePatches(long tick) {
        if (patches.isEmpty() || tick <= 0) return;
        List<Long> dead = null;
        for (Patch p : patches.values()) {
            if (!p.alive() && tick - p.lastSeenTick() > PATCH_LIFETIME_TICKS) {
                if (dead == null) dead = new ArrayList<>();
                dead.add(p.key());
            }
        }
        if (dead != null) {
            for (long k : dead) patches.remove(k);
        }
    }

    /**
     * The eviction window, shaped and sized to contain everything the sweep can discover: the same
     * Chebyshev square of side {@code radius + evictSlack}. A Euclidean disk here would be narrower
     * than the sweep square at the corners — the columns between the inscribed circle and the
     * square are discovered, evicted, discovered again, and the set never settles although the
     * world stood still (a watchful client reads that as terrain moving and re-requests its
     * context forever; caught by {@code aSaturatedWindowOfStillWaterKeepsOneSignatureTickAfterTick}).
     */
    private static boolean within(int cx, int cz, int x, int z, int r) {
        int dx = Math.abs(x - cx);
        int dz = Math.abs(z - cz);
        return dx <= r && dz <= r;
    }

    // ------------------------------------------------------------------ scanning

    /**
     * Scans the water in one world column from {@code yFrom} (inclusive, above) down to {@code yTo}
     * (inclusive, below), assembling spans that account for fractional fluid surfaces, collision
     * blocks inside the water, and gaps (caves, bridges, waterlogged obstacles) between runs.
     *
     * @return the column, or null when the chunk under it is not loaded anywhere in the band
     */
    public WaterColumn scanColumn(WaterSource source, int x, int z, int yFrom, int yTo) {
        if (yFrom < yTo || yFrom - yTo + 1 > scratchLo.length) return WaterColumn.NONE;
        int cells = 0;
        boolean anyWater = false;
        boolean truncated = false;
        int below = 0; // consecutive non-water cells under the last water seen
        for (int y = yFrom; y >= yTo; y--) {
            int f = source.flags(x, y, z);
            counters.cellsClassified++;
            counters.cellsThisTick++;
            cells++;
            if ((f & WaterSource.LOADED) == 0) {
                counters.unloadedScans++;
                return null;
            }
            if ((f & WaterSource.WATER) != 0) {
                if (cells == 1) {
                    // Water in the band's own top cell: the scan cannot see above it, so it asks
                    // the one cell just above the band instead of assuming. A cell with water (or
                    // one that is not loaded, so the answer is unknowable) means the water really
                    // continues; a dry cell means this was the surface all along — water that ends
                    // inside the band's top cell is open to the air, not cut by the scan.
                    int above = source.flags(x, yFrom + 1, z);
                    counters.cellsClassified++;
                    counters.cellsThisTick++;
                    if ((above & WaterSource.LOADED) == 0 || (above & WaterSource.WATER) != 0) {
                        truncated = true;
                    }
                }
                float lo = y;
                if ((f & WaterSource.COLLISION) != 0) lo += source.collisionTop(x, y, z);
                float hi = y + source.fluidTop(x, y, z);
                if (hi > lo) {
                    scratchLo[cells - 1] = lo;
                    scratchHi[cells - 1] = hi;
                } else {
                    scratchLo[cells - 1] = Float.NaN;
                }
                anyWater = true;
                below = 0;
            } else {
                scratchLo[cells - 1] = Float.NaN;
                if (anyWater && ++below >= GAP_CELLS) break;
            }
            if (y == yTo && anyWater && below == 0) truncated = true; // still in water at the band's floor
        }
        // Assemble ascending, merging contiguous or overlapping spans.
        int n = 0;
        for (int i = cells - 1; i >= 0; i--) {
            float lo = scratchLo[i];
            if (Float.isNaN(lo)) continue;
            float hi = scratchHi[i];
            if (n > 0 && lo <= mergeBuf[n * 2 - 1] + MERGE_EPSILON) {
                if (hi > mergeBuf[n * 2 - 1]) mergeBuf[n * 2 - 1] = hi;
            } else if (n < MAX_SPANS) {
                mergeBuf[n * 2] = lo;
                mergeBuf[n * 2 + 1] = hi;
                n++;
            } else {
                truncated = true;
            }
        }
        if (n == 0) return WaterColumn.of(null, truncated);
        float[] spans = new float[n * 2];
        System.arraycopy(mergeBuf, 0, spans, 0, n * 2);
        return WaterColumn.of(spans, truncated);
    }

    // ------------------------------------------------------------------ keys

    static long cellKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    /**
     * Packs a patch key from its quantized seed cell and an instance number. Zero is never a real
     * key — {@link #patchKeyAt} and the merge check both use it to mean "no patch" — which is why
     * instance numbers start at one.
     */
    public static long packKey(int qx, int qz, int instance) {
        return ((long) (qx & 0xFFFFFL) << 44) | ((long) (qz & 0xFFFFFL) << 24) | (instance & 0xFFFFFFL);
    }

    public static int keyQx(long key) {
        return signExtend20((int) ((key >>> 44) & 0xFFFFFL));
    }

    public static int keyQz(long key) {
        return signExtend20((int) ((key >>> 24) & 0xFFFFFL));
    }

    public static int keyInstance(long key) {
        return (int) (key & 0xFFFFFFL);
    }

    private static int signExtend20(int v) {
        return (v & 0x80000) != 0 ? v - 0x100000 : v;
    }
}
