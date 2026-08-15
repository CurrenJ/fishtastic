package grill24.fishtastic.shapegen;

import java.util.List;

/**
 * Shared data shape for the "comb" family of tanks (tooth, film): a repeating tooth/perforation
 * pattern hanging from the ceiling and/or rising from the floor, with a plain — but not necessarily
 * 1px — corner post filling the waist between them. Read pixel-exactly off each shape's
 * {@code docs/tank-shapes/<name>_shape_3_wide.png} (three horizontally-connected tanks: left tank
 * WEST closed/EAST open, middle tank both open, right tank WEST open/EAST closed — enough to read
 * off both the closed-corner reference and the open-seam behavior for every row at once). Used by
 * {@link CombFrameGeometryGenerator} / {@link CombGlassGeometryGenerator}, which take a {@link Spec}
 * instance rather than being subclassed per shape, the same reuse pattern
 * {@code ShellFrameGeometryGenerator} already uses across four tapered shapes via
 * {@link CornerTaperProfile}.
 *
 * <p>Unlike {@link BrambleTankSpans} (whose bands are entirely low/high, gated by the adjacent
 * corner's state, nothing rendered unconditionally) these two reference images show rows where part
 * of the pattern stays put regardless of what the neighboring face is doing — a decorative tooth
 * that reads as attached to the cap rather than to the corner. Confirmed directly against the
 * 3-wide reference: e.g. tooth's row {@code y=02} is byte-identical whether the west neighbor is
 * open or closed, so that row's content can't be a west-gated "low" run — it has to be unconditional.
 * That's the one thing {@link BrambleTankSpans}'s pure low/high split can't express, hence the third
 * {@code base} component here.
 */
public final class CombTankSpans {

    /**
     * One Y band's frame content on a single face, split into three independently-gated parts over
     * the face's along-wall axis, each an exclusive {@code [x1,x2)} span list:
     * <ul>
     *   <li>{@code base} — rendered whenever the face itself (north/south, or west/east) is closed,
     *       regardless of what the adjacent corner's neighbor is doing.</li>
     *   <li>{@code low} — rendered only when the low-side neighbor is <em>also</em> closed (west for
     *       a north/south face's spans, north for a west/east face's).</li>
     *   <li>{@code high} — rendered only when the high-side neighbor is also closed (east / south).</li>
     * </ul>
     * As with bramble, a gated run simply vanishes when its neighbor opens with nothing added back —
     * confirmed against the reference, which shows a clean disappearance at every observed seam.
     */
    public record Band(int yFrom, int yTo, int[][] base, int[][] low, int[][] high) {
        public Band(int yFrom, int yTo, int[][] base) {
            this(yFrom, yTo, base, new int[0][], new int[0][]);
        }
    }

    /**
     * One shape's full comb layout.
     *
     * @param top              Bands near the ceiling, ceiling-most first. Rendered only when UP is
     *                          closed; the whole zone vanishes (no replacement band) when UP opens —
     *                          no vertical-connection reference exists for these shapes, so this
     *                          mirrors {@code ShaggyFrameGeometryGenerator}'s TOP/BOTTOM behavior
     *                          rather than inventing an open-state pattern the way bramble's
     *                          TOP_OPEN/BOTTOM_OPEN does from its own vertical reference.
     * @param middleYFromClosed the waist corner post's Y range when both caps are closed
     * @param middleYToClosed   ditto
     * @param middleLow         the waist's low-side (west/north) span, constant across the whole
     *                          middle zone — always a single run, mergeable into one box per the
     *                          usual run-merging principle since every middle row in both reference
     *                          images shares one width
     * @param middleHigh        the waist's high-side (east/south) span
     * @param bottom            Bands near the floor, excluding the sand-row band — see {@code
     *                          sandRow}. Rendered only when DOWN is closed; vanishes when DOWN opens,
     *                          same reasoning as {@code top}.
     * @param sandRow           The image row that doubles as the sand row (row 14). Rendered only
     *                          when DOWN is closed (the sand itself already drops out when DOWN
     *                          opens), by both the frame generator (the teeth) and the glass
     *                          generator (a pane filling the complement, needed because the standard
     *                          sand only insets to the glass's inner face — see
     *                          {@link CombGlassGeometryGenerator}).
     */
    public record Spec(
            List<Band> top,
            int middleYFromClosed, int middleYToClosed,
            int[][] middleLow, int[][] middleHigh,
            List<Band> bottom,
            Band sandRow
    ) {}

    private CombTankSpans() {}
}
