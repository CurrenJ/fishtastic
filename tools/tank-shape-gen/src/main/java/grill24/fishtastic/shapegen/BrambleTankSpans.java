package grill24.fishtastic.shapegen;

/**
 * The bramble tank's decorative inlay spans, read pixel-exactly off
 * {@code docs/tank-shapes/bramble_shape_standalone.png} (closed-state baseline),
 * {@code bramble_shape_2_wide.png} (horizontal-connection reference: left tank = EAST open, right
 * tank = WEST open) and {@code bramble_shape_3_tall.png} (vertical-connection reference: top tank =
 * DOWN open, bottom tank = UP open). Shared by {@link BrambleFrameGeometryGenerator} (which builds
 * the inlays) and {@link BrambleGlassGeometryGenerator} (which punches the matching holes in the
 * pane), so the two can't drift out of sync.
 *
 * <p>Unlike the ornate/shaggy tanks — a standard 1px corner post plus a handful of fringe bands near
 * the caps, with a clear glass waist in between — bramble has no plain corner post and no clear
 * waist at all: every one of the 13 interior image rows (1-13) carries its own thorny, asymmetric
 * frame pattern, and the pattern's {@code F} pixels partition cleanly into a "low" run (touching or
 * near {@code x=0}) and a "high" run (touching or near {@code x=15}) with a bare gap between them —
 * confirmed directly against the horizontal-connection reference, where opening one side always
 * removes exactly one whole run (never a partial pixel, never both runs, never neither).
 *
 * <p>Image row {@code r} maps to Minecraft Y band {@code [15-r, 16-r]}. Each span is an exclusive
 * {@code [x1, x2)} range over the face's along-wall axis. The always-on middle bands (image rows
 * 1-13) are read directly off the standalone reference:
 *
 * <pre>
 *   y=01 F..............F   Y[14,15]  low={[0,1)}             high={[15,16)}
 *   y=02 FFF.......FFFF.F   Y[13,14]  low={[0,3)}              high={[10,14),[15,16)}
 *   y=03 F.FFF......F.FFF   Y[12,13]  low={[0,1),[2,5)}        high={[11,12),[13,16)}
 *   y=04 F...F..........F   Y[11,12]  low={[0,1),[4,5)}        high={[15,16)}
 *   y=05 FFF.........FF.F   Y[10,11]  low={[0,3)}              high={[12,14),[15,16)}
 *   y=06 F............FFF   Y[9,10]   low={[0,1)}              high={[13,16)}
 *   y=07 FFFF...........F   Y[8,9]    low={[0,4)}              high={[15,16)}
 *   y=08 F.F.........FFFF   Y[7,8]    low={[0,1),[2,3)}        high={[12,16)}
 *   y=09 F............F.F   Y[6,7]    low={[0,1)}              high={[13,14),[15,16)}
 *   y=10 F.FFF..........F   Y[5,6]    low={[0,1),[2,5)}        high={[15,16)}
 *   y=11 F.F.F......FFF.F   Y[4,5]    low={[0,1),[2,3),[4,5)}  high={[11,14),[15,16)}
 *   y=12 FFF........F.FFF   Y[3,4]    low={[0,3)}              high={[11,12),[13,16)}
 *   y=13 FF............FF   Y[2,3]    low={[0,2)}              high={[14,16)}
 * </pre>
 *
 * <p>Row 14 (the sand row) has two variants selected by whether DOWN is closed: closed, it's a
 * plain 1px corner post (low {@code [0,1)}, high {@code [15,16)}, same shape as row 1) sitting above
 * the sand; open, the sand vanishes and the row grows the same thorny pattern as everywhere else,
 * read off the top tank's row 14 in the vertical reference. Rows 0 (ceiling) and 15 (floor) are
 * ordinarily the fixed solid caps and carry no inlay at all — but when their cap opens, the vertical
 * reference shows they too grow a bracket band instead of just vanishing, read off the bottom tank's
 * row 0 and the top tank's row 15 respectively. Neither the top-open nor the two down-open bands has
 * a directly-observed low/high split (no reference shows west/east opened at the same time as
 * up/down), so their split follows the same rule every other row demonstrates: the run(s) nearer
 * {@code x=0} are low, the run(s) nearer {@code x=15} are high, split at the row's one clear gap.
 *
 * <pre>
 *   Y[15,16] (UP open)    FFF.F......FFF.F   low={[0,3),[4,5)}    high={[11,14),[15,16)}
 *   Y[1,2]   (DOWN open)  F...FF....FF...F   low={[0,1),[4,6)}    high={[10,12),[15,16)}
 *   Y[0,1]   (DOWN open)  F.FFF......F.FFF   low={[0,1),[2,5)}    high={[11,12),[13,16)}
 * </pre>
 *
 * <p>As with the shaggy tank, the same span list is applied to all four faces (rotated, not
 * mirrored), and the rows are not symmetric — that is deliberate ("bramble"). Do not "fix" it.
 */
public final class BrambleTankSpans {

    /** A Y band and its low/high-side inlay spans, each an exclusive {@code [x1, x2)} range. */
    public record Band(int yFrom, int yTo, int[][] lowSpans, int[][] highSpans) {}

    /** Image rows 1-13 — always present, regardless of UP/DOWN state. */
    public static final Band[] MIDDLE = {
            new Band(14, 15, new int[][]{{0, 1}}, new int[][]{{15, 16}}),
            new Band(13, 14, new int[][]{{0, 3}}, new int[][]{{10, 14}, {15, 16}}),
            new Band(12, 13, new int[][]{{0, 1}, {2, 5}}, new int[][]{{11, 12}, {13, 16}}),
            new Band(11, 12, new int[][]{{0, 1}, {4, 5}}, new int[][]{{15, 16}}),
            new Band(10, 11, new int[][]{{0, 3}}, new int[][]{{12, 14}, {15, 16}}),
            new Band(9, 10, new int[][]{{0, 1}}, new int[][]{{13, 16}}),
            new Band(8, 9, new int[][]{{0, 4}}, new int[][]{{15, 16}}),
            new Band(7, 8, new int[][]{{0, 1}, {2, 3}}, new int[][]{{12, 16}}),
            new Band(6, 7, new int[][]{{0, 1}}, new int[][]{{13, 14}, {15, 16}}),
            new Band(5, 6, new int[][]{{0, 1}, {2, 5}}, new int[][]{{15, 16}}),
            new Band(4, 5, new int[][]{{0, 1}, {2, 3}, {4, 5}}, new int[][]{{11, 14}, {15, 16}}),
            new Band(3, 4, new int[][]{{0, 3}}, new int[][]{{11, 12}, {13, 16}}),
            new Band(2, 3, new int[][]{{0, 2}}, new int[][]{{14, 16}}),
    };

    /** Image row 14 (the sand row) when DOWN is closed — a plain 1px corner post above the sand. */
    public static final Band ROW14_CLOSED = new Band(1, 2, new int[][]{{0, 1}}, new int[][]{{15, 16}});

    /** Image row 14 when DOWN is open — the sand is gone, replaced by the same thorny pattern. */
    public static final Band ROW14_OPEN = new Band(1, 2, new int[][]{{0, 1}, {4, 6}}, new int[][]{{10, 12}, {15, 16}});

    /** Image row 0 (the ceiling cap boundary) — only present when UP is open. */
    public static final Band TOP_OPEN = new Band(15, 16, new int[][]{{0, 3}, {4, 5}}, new int[][]{{11, 14}, {15, 16}});

    /** Image row 15 (the floor cap boundary) — only present when DOWN is open. */
    public static final Band BOTTOM_OPEN = new Band(0, 1, new int[][]{{0, 1}, {2, 5}}, new int[][]{{11, 12}, {13, 16}});

    private BrambleTankSpans() {}
}
