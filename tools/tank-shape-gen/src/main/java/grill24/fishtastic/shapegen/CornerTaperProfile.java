package grill24.fishtastic.shapegen;

import java.util.ArrayList;
import java.util.List;

/**
 * A per-row corner-post width, read directly off a reference pixel-mapped image (see the
 * {@code tank-shape-image-to-datagen} skill): each 16×16 tank sub-image's rows 1-14 (row 0 is
 * always the ceiling cap, row 15 always the floor cap — both stay a fixed 1px, unaffected by
 * this profile) give the corner post's reach-in from the true corner at that height, read as the
 * count of leading/trailing frame-colored pixels on that row.
 *
 * <p>{@code rowWidths} is ordered top-to-bottom exactly like the image: index 0 is the row
 * immediately under the ceiling (image row 1), index 13 is the row immediately above the floor
 * (image row 14, which doubles as the sand row — see {@link SandGeometryGenerator}).
 *
 * <p>Every band stays flush at the same true corner and only its reach changes — so stacked
 * bands of different sizes always share that corner and touch with zero gap: the wider band's
 * own face already covers the "shelf," no bridging element needed.
 */
public record CornerTaperProfile(int[] rowWidths) {

    /** Matches today's shipped tank: constant 1px corner posts, no taper. */
    public static final CornerTaperProfile STANDARD = uniform(1);

    /**
     * STANDARD's chunky hard-edged sibling: a uniform 2px mid-body (no taper) capped by the same
     * full-width {@code 16} rows — chamfered octagonal cap rings — as {@link #FACETED}/{@link #BASTION}.
     */
    public static final CornerTaperProfile STURDY =
            new CornerTaperProfile(new int[]{16, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 16});

    /**
     * Read from {@code docs/tank-shapes/new_tank_shapes.png} tank slot 1 (0-indexed): image rows 1-14 top-to-bottom.
     * A modest 3→1 taper — a light corner brace rather than heavy reinforcement.
     */
    public static final CornerTaperProfile TRIMMED =
            new CornerTaperProfile(new int[]{3, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 3});

    /**
     * Read from {@code docs/tank-shapes/new_tank_shapes.png} tank slot 2 (0-indexed): image rows 1-14 top-to-bottom.
     * A deeper 5→1 taper — chunkier corner brackets than {@link #TRIMMED}.
     */
    public static final CornerTaperProfile REINFORCED =
            new CornerTaperProfile(new int[]{5, 3, 2, 2, 1, 1, 1, 1, 1, 1, 2, 2, 3, 5});

    /**
     * Read from {@code docs/tank-shapes/new_tank_shapes.png} tank slot 3 (0-indexed): image rows 1-14 top-to-bottom.
     * BASTION's finer-stepped sibling: the same {@code 16} (= full-width) end rows — 2px-thick
     * ceiling/floor caps rendered as chamfered octagonal rings — and the same 2px minimum mid-body
     * width (a continuous 2-wide frame border), but with a smaller 4→3→2 step sequence. Its sand is
     * a stepped octagon inset by the taper's 2px minimum in the middle.
     */
    public static final CornerTaperProfile FACETED =
            new CornerTaperProfile(new int[]{16, 4, 3, 2, 2, 2, 2, 2, 2, 2, 2, 3, 4, 16});

    /**
     * Read from {@code docs/tank-shapes/new_tank_shapes.png} tank slot 4 (0-indexed): image rows 1-14 top-to-bottom.
     * A 16→2 taper with {@code 16} (= full-width) end rows — i.e. 2px-thick ceiling/floor caps —
     * whose sand is a stepped octagon inset by the taper's 2px minimum in the middle.
     * The {@code 16} rows are *not* plain full slabs at the sand level: the base is a
     * chamfered octagonal ring (see the tank-shape-image-to-datagen skill) so the stepped-octagon
     * sand stays visible.
     */
    public static final CornerTaperProfile BASTION =
            new CornerTaperProfile(new int[]{16, 6, 4, 3, 3, 2, 2, 2, 2, 3, 3, 4, 6, 16});

    /**
     * Read from {@code thicker_chamfer_shape_set.png} tank slot 0 (0-indexed): image rows 1-14
     * top-to-bottom. REINFORCED's deeper sibling — a 6→1 taper with no full-width rows, same
     * hard-edged square sand family (no chamfered cap rings).
     */
    public static final CornerTaperProfile HONED =
            new CornerTaperProfile(new int[]{6, 4, 3, 2, 2, 1, 1, 1, 1, 2, 2, 3, 4, 6});

    /**
     * Read from {@code thicker_chamfer_shape_set.png} tank slot 1 (0-indexed): image rows 1-14
     * top-to-bottom. BASTION's deeper sibling — a 16→2 taper with full-width {@code 16} end rows
     * (chamfered octagonal cap rings), tapering one pixel further out (7 vs BASTION's 6) for a
     * tighter frame circle.
     */
    public static final CornerTaperProfile RAMPART =
            new CornerTaperProfile(new int[]{16, 7, 5, 4, 3, 3, 2, 2, 3, 3, 4, 5, 7, 16});

    public static final int ROW_COUNT = 14;

    public CornerTaperProfile {
        if (rowWidths.length != ROW_COUNT) {
            throw new IllegalArgumentException("rowWidths must have exactly " + ROW_COUNT + " entries (image rows 1-14), got " + rowWidths.length);
        }
    }

    public static CornerTaperProfile uniform(int width) {
        int[] rows = new int[ROW_COUNT];
        java.util.Arrays.fill(rows, width);
        return new CornerTaperProfile(rows);
    }

    /** The profile's steady-state width away from either cap — the value a tapered end falls
     * back to when that end's cap (ceiling/floor) is open and there's nothing to taper into. */
    public int baseWidth() {
        return rowWidths[ROW_COUNT / 2];
    }

    /**
     * A single contiguous run of equal-width rows, in Minecraft element Y coordinates
     * (0-16 space) rather than image-row indices — {@code yFrom < yTo}, {@code width} in pixels.
     */
    public record Run(double yFrom, double yTo, int width) {}

    /**
     * Effective per-row widths after accounting for an open leading/trailing cap: when a cap is
     * open there's nothing for that end's taper to flare into, so the leading/trailing run that
     * differs from {@link #baseWidth()} is replaced with the base width instead (the post just
     * runs straight at base width up to the open boundary). Assumes a monotonic taper converging
     * to the base width in the middle, which is true of every profile this skill produces — see
     * its "Limitations" note.
     *
     * <p>Axis-agnostic: {@link #runs} applies this to the vertical ceiling/floor taper, and
     * {@link SteppedSandGeometryGenerator}/{@link ShellFrameGeometryGenerator} reuse it for the
     * horizontal north/south taper on the stepped-octagon shapes (FACETED/BASTION), since both are
     * the same "cap opens, taper has nothing to flare into" situation on a different axis.
     */
    public int[] effectiveRowWidths(boolean leadingCapClosed, boolean trailingCapClosed) {
        int[] result = rowWidths.clone();
        int base = baseWidth();
        if (!leadingCapClosed) {
            int i = 0;
            while (i < result.length && result[i] != base) {
                result[i] = base;
                i++;
            }
        }
        if (!trailingCapClosed) {
            int i = result.length - 1;
            while (i >= 0 && result[i] != base) {
                result[i] = base;
                i--;
            }
        }
        return result;
    }

    /**
     * Merges consecutive equal-width image rows into runs and converts each to Minecraft element
     * Y coordinates. Image row {@code r} (1-14) occupies Minecraft Y-band {@code [15-r, 16-r]} —
     * row 1 (just under the ceiling) is Y 14-15, row 14 (just above the floor) is Y 1-2. When the
     * ceiling/floor cap is <em>open</em> there's nothing to stop the post at y=15 / y=1, so the
     * run adjacent to that open cap is extended to the block boundary (y=16 / y=0) — keeping
     * stacked tanks' corner posts and glass flush at the seam, exactly like the shipped STANDARD
     * tank. The cap bands y=15..16 and y=0..1 only exist when the cap is closed, so extending
     * into them when open never double-renders.
     */
    public List<Run> runs(boolean ceilingClosed, boolean floorClosed) {
        int[] widths = effectiveRowWidths(ceilingClosed, floorClosed);
        List<Run> runs = new ArrayList<>();

        int runStartRow = 1; // image row (1-14) the current run started at
        int runWidth = widths[0];
        for (int i = 1; i <= ROW_COUNT; i++) {
            int imageRow = i + 1; // widths[i-1] corresponds to image row i, so the *next* row is i+1
            int width = i < ROW_COUNT ? widths[i] : Integer.MIN_VALUE; // sentinel to flush the last run
            if (width != runWidth) {
                int runEndRow = imageRow - 1; // last image row included in the run just ending
                runs.add(new Run(15 - runEndRow, 16 - runStartRow, runWidth));
                runStartRow = imageRow;
                runWidth = width;
            }
        }

        // Extend the run bordering an open cap into that cap's Y-band so the post/glass reaches
        // the block boundary at the seam (a stacked tank's shared face) instead of stopping 1px
        // short of it. runs() is ordered top-down (index 0 hugs the ceiling, the last entry hugs
        // the floor), so the ceiling-open extension targets the first run and the floor-open one
        // the last.
        if (!ceilingClosed && !runs.isEmpty()) {
            Run top = runs.get(0);
            runs.set(0, new Run(top.yFrom(), 16, top.width()));
        }
        if (!floorClosed && !runs.isEmpty()) {
            Run bottom = runs.get(runs.size() - 1);
            runs.set(runs.size() - 1, new Run(0, bottom.yTo(), bottom.width()));
        }
        return runs;
    }
}
