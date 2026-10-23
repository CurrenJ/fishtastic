package grill24.fishsim.ambient;

import java.util.Arrays;

/**
 * The water found in one vertical world column, as far as the bounded scan looked: a sorted list
 * of vertical spans in world Y (block units), each a swimmable stretch between a solid surface
 * and a fluid surface.
 *
 * <p>This is the natural-water equivalent of a tank's occupancy column, with the two things a
 * block-union grid cannot express kept explicitly: the top of the <em>topmost</em> span is the
 * fluid's own surface height (8/9 of a block for a still source cell, less for a flowing one), and
 * the bottom of the <em>lowest</em> span is the collision top of whatever the water is standing on
 * (1 for sand, 0.5 for a bottom slab, 0 for nothing).
 *
 * <p>Immutable. {@link #truncated()} records that the scan hit its search bound while still in
 * water — i.e. the real water continues past what is known — which is what lets a domain mark a
 * face as artificial rather than as a bank (docs/fish-in-world.md).
 */
public final class WaterColumn {

    /** No water in this column. */
    public static final WaterColumn NONE = new WaterColumn(new float[0], false);

    private final float[] spans;
    private final boolean truncated;

    private WaterColumn(float[] spans, boolean truncated) {
        this.spans = spans;
        this.truncated = truncated;
    }

    /**
     * @param spans alternating {@code lo, hi} pairs in world Y, ascending and non-overlapping
     *              (not necessarily contiguous — two spans may have air or stone between them)
     */
    public static WaterColumn of(float[] spans, boolean truncated) {
        if (spans == null || spans.length == 0) return truncated ? new WaterColumn(new float[0], true) : NONE;
        float[] copy = Arrays.copyOf(spans, spans.length);
        return new WaterColumn(copy, truncated);
    }

    public boolean isEmpty() {
        return spans.length == 0;
    }

    public int spanCount() {
        return spans.length / 2;
    }

    public float lo(int span) {
        return spans[span * 2];
    }

    public float hi(int span) {
        return spans[span * 2 + 1];
    }

    /** The height a benthic creature stands at: the bottom of the lowest water in this column, or NaN. */
    public float bedY() {
        return spans.length == 0 ? Float.NaN : spans[0];
    }

    /** The fluid surface: the top of the highest water in this column, or NaN. */
    public float surfaceY() {
        return spans.length == 0 ? Float.NaN : spans[spans.length - 1];
    }

    /** The tallest single span — how deep the swimmable water is at its deepest in this column. */
    public float maxSpanHeight() {
        float best = 0f;
        for (int i = 0; i < spans.length; i += 2) {
            best = Math.max(best, spans[i + 1] - spans[i]);
        }
        return best;
    }

    /** Whether {@code y} lies inside one of the spans (inclusive of both ends). */
    public boolean containsY(float y) {
        for (int i = 0; i < spans.length; i += 2) {
            if (y >= spans[i] && y <= spans[i + 1]) return true;
        }
        return false;
    }

    /** Whether this column shares water with {@code other} at some height — the connectivity rule. */
    public boolean overlaps(WaterColumn other) {
        if (other == null || other.isEmpty() || isEmpty()) return false;
        for (int i = 0; i < spans.length; i += 2) {
            for (int j = 0; j < other.spans.length; j += 2) {
                if (spans[i] <= other.spans[j + 1] && other.spans[j] <= spans[i + 1]) return true;
            }
        }
        return false;
    }

    /** Whether the lowest span reaches into the band's bottom cell — cut below, not bedded. */
    public boolean touchesBandBottom(int bandBottom) {
        return spans.length > 0 && spans[0] <= bandBottom + 1e-3f;
    }

    /**
     * Whether the topmost span reaches into the band's top cell, so that {@link #truncated()}
     * really means "cut by the scan above" — water that stops inside the band's own top cell is
     * open to the air, not to more water.
     */
    public boolean touchesBandTop(int bandTop) {
        return spans.length > 0 && spans[spans.length - 1] > bandTop;
    }

    /**
     * Exact comparison for revalidation: the truncation flag is part of the reading, because it
     * decides whether the bed and surface are real or are the scan's own bounds — a column that
     * just learned where its bottom is is not the column that guessed.
     */
    public boolean spansEqual(WaterColumn other) {
        return other != null && truncated == other.truncated && Arrays.equals(spans, other.spans);
    }

    /** Whether the scan was cut by a bound rather than by a bed or a surface. */
    public boolean truncated() {
        return truncated;
    }

    /** Copy of the raw span data (pairs of lo/hi), for tests and callers that need the array form. */
    public float[] spans() {
        return Arrays.copyOf(spans, spans.length);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("WaterColumn[");
        for (int i = 0; i < spans.length; i += 2) {
            if (i > 0) sb.append(',');
            sb.append(spans[i]).append("..").append(spans[i + 1]);
        }
        return sb.append(truncated ? ",truncated]" : "]").toString();
    }
}
