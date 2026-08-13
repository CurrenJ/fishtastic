package grill24.fishtastic.shapegen;

import java.util.ArrayList;
import java.util.List;

/**
 * The shaggy tank's decorative inlay spans, read pixel-exactly off
 * {@code docs/tank-shapes/shaggy_tank.png}. Shared by
 * {@link ShaggyFrameGeometryGenerator} (which builds the inlays) and
 * {@link ShaggyGlassGeometryGenerator} (which punches the matching holes in the pane), so the two
 * can't drift out of sync — the ornate pair duplicates its table and that's the one thing worth
 * not copying from it.
 *
 * <p>The shape is the ornate tank's approach with a shaggier, deliberately asymmetric fringe: a
 * standard 1px corner-post + ceiling/floor frame, plus 1px-thick inlays inset into the glass layer.
 * The reference image has no sand color because image row {@code y=14} is a full-width frame band
 * that hides the sand completely from the side; the sand itself is the standard square one.
 *
 * <p>Image row {@code r} maps to Minecraft Y band {@code [15-r, 16-r]}, and each span is an
 * exclusive {@code [x1, x2)} range over the face's along-wall axis:
 *
 * <pre>
 *   y=00 FFFFFFFFFFFFFFFF   ceiling cap
 *   y=01 FFFFFFFFFFFFFF.F   Y[14,15]  [1,14)                    + both edge cells
 *   y=02 F.FFF.FFFF.FF..F   Y[13,14]  [2,5) [6,10) [11,13)      + low edge cell
 *   y=03 F..F..FF.F..F..F   Y[12,13]  [3,4) [6,8) [9,10) [12,13)
 *   y=04 F.....F........F   Y[11,12]  [6,7)
 *   y=05..y=11              Y[4,11]   (corner posts only)
 *   y=12 F.....F...F....F   Y[3,4]    [6,7) [10,11)             + low edge cell
 *   y=13 F.F..FF.FFFF.F.F   Y[2,3]    [2,3) [5,7) [8,12) [13,14) + low edge cell
 *   y=14 FFFFFFFFFFFFFFFF   Y[1,2]    [1,15)  (full-width band, hides the sand) + both edges
 *   y=15 FFFFFFFFFFFFFFFF   floor cap
 * </pre>
 *
 * <p>The rows are <em>not</em> mirror-symmetric and that is deliberate ("shaggy"): the same span
 * list is applied to all four faces, so the fringe reads as rotational rather than mirrored. Do not
 * "fix" the asymmetry — it is what the reference draws.
 */
public final class ShaggyTankSpans {

    /**
     * A Y band, the inlay spans sitting in it as exclusive {@code [x1,x2)} ranges, and whether the
     * fringe claims the band's two <em>edge cells</em> ({@code [0,1)} and {@code [15,16)}).
     *
     * <p>An edge cell is hidden behind the corner post while the adjacent face is closed, so it only
     * becomes visible — and only then gets emitted, to avoid two coplanar opaque quads z-fighting —
     * once that face opens. Whether it should appear at all is per-row and per-side, read off the
     * horizontal-connection reference rather than inferred: row {@code y=01} claims both edges (it
     * reads {@code FFFFFFFFFFFFFF.F} no matter what connects), row {@code y=02} claims only the low
     * edge, as do {@code y=12} and {@code y=13}; the rest claim neither.
     */
    public record Band(int yFrom, int yTo, int[][] spans, boolean lowEdgeInlay, boolean highEdgeInlay) {}

    /** Fringe hanging from the ceiling — present only when UP is closed. */
    public static final Band[] TOP = {
            new Band(14, 15, new int[][]{{1, 14}}, true, true),
            new Band(13, 14, new int[][]{{2, 5}, {6, 10}, {11, 13}}, true, false),
            new Band(12, 13, new int[][]{{3, 4}, {6, 8}, {9, 10}, {12, 13}}, false, false),
            new Band(11, 12, new int[][]{{6, 7}}, false, false),
    };

    /** Fringe rising from the floor — present only when DOWN is closed. */
    public static final Band[] BOTTOM = {
            new Band(3, 4, new int[][]{{6, 7}, {10, 11}}, true, false),
            new Band(2, 3, new int[][]{{2, 3}, {5, 7}, {8, 12}, {13, 14}}, true, false),
            new Band(1, 2, new int[][]{{1, 15}}, true, true),
    };

    private ShaggyTankSpans() {}

    /**
     * The band's spans as they should actually render for one face, given whether the two adjacent
     * faces are open (i.e. whether each corner post is gone).
     *
     * <p>A claimed edge cell that has become visible is <em>merged into</em> the neighbouring span
     * when that span already butts against the post, and emitted as its own 1px box otherwise. The
     * merge is what makes two connected tanks' fringes meet flush at the seam instead of leaving a
     * notch — the horizontal half of the rule {@code TaperedGlassGeometryGenerator} gets from its
     * {@code nwCorner}/{@code neCorner} gating. Rows claiming neither edge are unaffected and stay
     * exactly where the image drew them.
     *
     * <p>The result stays sorted and non-overlapping, which
     * {@code ShaggyGlassGeometryGenerator}'s complement pass depends on.
     */
    public static List<int[]> spansFor(Band band, boolean lowOpen, boolean highOpen) {
        List<int[]> result = new ArrayList<>(band.spans().length + 2);
        for (int[] span : band.spans()) {
            result.add(new int[]{span[0], span[1]});
        }
        if (lowOpen && band.lowEdgeInlay()) {
            if (!result.isEmpty() && result.get(0)[0] == 1) {
                result.get(0)[0] = 0;
            } else {
                result.add(0, new int[]{0, 1});
            }
        }
        if (highOpen && band.highEdgeInlay()) {
            int last = result.size() - 1;
            if (last >= 0 && result.get(last)[1] == 15) {
                result.get(last)[1] = 16;
            } else {
                result.add(new int[]{15, 16});
            }
        }
        return result;
    }
}
