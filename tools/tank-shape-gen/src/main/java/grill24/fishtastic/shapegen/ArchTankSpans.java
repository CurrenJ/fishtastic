package grill24.fishtastic.shapegen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The arch tank's per-face wall layout, read pixel-exactly off
 * {@code docs/tank-shapes/arch_revised_conn.png} (three 16x16 tanks side by side: left = WEST
 * closed/EAST open, middle = both open, right = WEST open/EAST closed):
 *
 * <pre>
 *        0123456789012345           0123456789012345           0123456789012345
 * y=00   FFFFFFFFFFFFFFFF   y=00   FFFFFFFFFFFFFFFF   y=00   FFFFFFFFFFFFFFFF
 * y=01   F...............   y=01   ................   y=01   ...............F
 * y=02   F.....FFFF......   y=02   ......FFFF......   y=02   ......FFFF.....F
 * y=03   F...FF....FF....   y=03   ....FF....FF....   y=03   ....FF....FF...F
 * y=04   F..F........F...   y=04   ...F........F...   y=04   ...F........F..F
 * y=05   F.F..........F..   y=05   ..F..........F..   y=05   ..F..........F.F
 * y=06   F.F..........F..   y=06   ..F..........F..   y=06   ..F..........F.F
 * y=07   FF............F.   y=07   .F............F.   y=07   .F............FF
 * y=08   FF.............F   y=08   F..............F   y=08   F.............FF
 * y=09   FF..............   y=09   ................   y=09   ..............FF
 * y=10-13 (same as y=09)     y=10-13 (same as y=09)     y=10-13 (same as y=09)
 * y=14   FFSSSSSSSSSSSSSS   y=14   SSSSSSSSSSSSSSSS   y=14   SSSSSSSSSSSSSSFF
 * y=15   FFFFFFFFFFFFFFFF   y=15   FFFFFFFFFFFFFFFF   y=15   FFFFFFFFFFFFFFFF
 * </pre>
 *
 * <p>Two independent components make up each row, and the whole point of this revision is that they
 * are gated <b>differently</b>:
 *
 * <ul>
 *   <li><b>The jamb</b> — a 1px post on rows 1-6 widening to 2px on rows 7-14 (the width the sand's
 *   2px inset already clears). It is an ordinary corner post: present only while the perpendicular
 *   face at that end of the wall is closed. Diffing the three tanks is what proves this — the left
 *   tank has a jamb at {@code x=0} and none at {@code x=15}, the right tank the mirror, the middle
 *   tank neither.</li>
 *   <li><b>The arc</b> — the curve springing from the crown blob at row 2 down to the corners. It is
 *   <b>never</b> gated by the perpendicular faces: it is byte-identical in all three tanks. That is
 *   what makes a row of connected arch tanks read as a real arcade — each tank draws a whole arch,
 *   and neighbouring arches meet in a V at the seam ({@code x=15} of one tank against {@code x=0} of
 *   the next on row 8).</li>
 * </ul>
 *
 * <p>The arc's two lowest steps ({@code [1,2)}/{@code [14,15)} on row 7 and {@code [0,1)}/{@code
 * [15,16)} on row 8) sit inside the 2px jamb's footprint, so on a closed end they are simply
 * swallowed by it — {@link #bands} unions the two components per row rather than emitting both, so
 * no box is ever drawn twice. Unioning also means the glass complement (see
 * {@link ArchGlassGeometryGenerator}) automatically picks up the horizontal seam extension required
 * when a jamb disappears: with no jamb at that end, the complement runs all the way to the block
 * boundary.
 *
 * <p>Applied identically to all four faces (rotated, not mirrored), matching the bramble/tooth/film
 * convention: the north/south walls run along X and take their low/high ends from WEST/EAST, the
 * west/east walls run along Z and take theirs from NORTH/SOUTH.
 */
public final class ArchTankSpans {

    /** One horizontal wall band: {@code [yFrom, yTo)} in model units, with its solid X (or Z) spans. */
    public record Band(int yFrom, int yTo, List<int[]> spans) {}

    /**
     * One transcribed image row (or run of identical rows): its Y-band, the jamb width at that
     * height, and the arc spans crossing it.
     */
    private record Row(int yFrom, int yTo, int jambWidth, int[][] arc) {}

    /** Image rows 1-14, top-down, with runs of identical rows already merged. */
    private static final List<Row> ROWS = List.of(
            new Row(14, 15, 1, new int[0][]),                          // row 1
            new Row(13, 14, 1, new int[][]{{6, 10}}),                  // row 2  (arc crown)
            new Row(12, 13, 1, new int[][]{{4, 6}, {10, 12}}),         // row 3
            new Row(11, 12, 1, new int[][]{{3, 4}, {12, 13}}),         // row 4
            new Row(9, 11, 1, new int[][]{{2, 3}, {13, 14}}),          // rows 5-6
            new Row(8, 9, 2, new int[][]{{1, 2}, {14, 15}}),           // row 7
            new Row(7, 8, 2, new int[][]{{0, 1}, {15, 16}}),           // row 8
            new Row(1, 7, 2, new int[0][]));                           // rows 9-14

    /** The Y the 2px jamb reaches up to — the band the corner diagonal fill has to span. */
    public static final int WIDE_JAMB_Y_TO = 9;

    /** The jamb width of the row nearest the floor ({@code ROWS}' last entry) — the edge-diagonal
     * frame beam's width for a {@code DOWN} edge (see {@code ArchFrameGeometryGenerator#generateEdgeFragment}). */
    public static final int NEAR_FLOOR_JAMB_WIDTH = ROWS.getLast().jambWidth();

    /** The jamb width of the row nearest the ceiling ({@code ROWS}' first entry) — the edge-diagonal
     * frame beam's width for an {@code UP} edge. */
    public static final int NEAR_CEILING_JAMB_WIDTH = ROWS.getFirst().jambWidth();

    private ArchTankSpans() {}

    /**
     * Builds one wall's bands for a given connection state.
     *
     * @param lowOpen  whether the perpendicular face at the wall's {@code 0} end is open
     * @param highOpen whether the perpendicular face at the wall's {@code 16} end is open
     * @param upOpen   whether UP is open (row 1 then seam-extends to {@code y=16})
     * @param downOpen whether DOWN is open (rows 9-14 then seam-extend to {@code y=0})
     */
    public static List<Band> bands(boolean lowOpen, boolean highOpen, boolean upOpen, boolean downOpen) {
        List<Band> bands = new ArrayList<>();
        for (Row row : ROWS) {
            int yFrom = row.yFrom() == 1 && downOpen ? 0 : row.yFrom();
            int yTo = row.yTo() == 15 && upOpen ? 16 : row.yTo();
            bands.add(new Band(yFrom, yTo, solidSpans(row, lowOpen, highOpen)));
        }
        return mergeAdjacent(bands);
    }

    /**
     * The glass-side counterpart to {@link #bands}: identical except that, within the narrow band
     * nearest an open cap that an edge-diagonal frame beam might occupy — {@code [0,
     * NEAR_FLOOR_JAMB_WIDTH)} when {@code downOpen}, {@code [16-NEAR_CEILING_JAMB_WIDTH, 16)} when
     * {@code upOpen} — both jamb ends are treated as present (closed) regardless of the real {@code
     * lowOpen}/{@code highOpen}, so the glass complement never extends flush into the beam's
     * territory (which would double-cover it with opaque frame over translucent glass — see {@code
     * TankShapeConnectivitySafetyTest#edgeFragmentNeverOverlapsBaseGlass}). Everywhere outside that
     * band, the real open state applies, unchanged from {@link #bands}. See {@code
     * ArchGlassGeometryGenerator#generateEdgeGlassFillFragment} for the matching restore fragment.
     *
     * <p>Only the near-floor row (height 6, wider than its 2px cap band) actually needs splitting;
     * the near-ceiling row's own height already equals its 1px cap band, so forcing applies to it
     * whole. The split is written generically off the two cap-band cut points rather than hardcoding
     * "the last row", so it keeps working if the pixel transcription ever changes.
     */
    public static List<Band> glassBands(boolean lowOpen, boolean highOpen, boolean upOpen, boolean downOpen) {
        int downCapTo = downOpen ? NEAR_FLOOR_JAMB_WIDTH : -1;
        int upCapFrom = upOpen ? 16 - NEAR_CEILING_JAMB_WIDTH : 17;

        List<Band> bands = new ArrayList<>();
        for (Row row : ROWS) {
            int yFrom = row.yFrom() == 1 && downOpen ? 0 : row.yFrom();
            int yTo = row.yTo() == 15 && upOpen ? 16 : row.yTo();

            List<Integer> cuts = new ArrayList<>();
            cuts.add(yFrom);
            if (downCapTo > yFrom && downCapTo < yTo) cuts.add(downCapTo);
            if (upCapFrom > yFrom && upCapFrom < yTo) cuts.add(upCapFrom);
            cuts.add(yTo);

            // Descending so a row's own sub-bands come out high-to-low, matching bands()'s overall
            // top-down order (mergeAdjacent expects previous.yFrom() == current.yTo()).
            for (int i = cuts.size() - 2; i >= 0; i--) {
                int subFrom = cuts.get(i), subTo = cuts.get(i + 1);
                if (subFrom >= subTo) continue;
                int mid = (subFrom + subTo) / 2;
                boolean forcedClosed = mid < downCapTo || mid >= upCapFrom;
                List<int[]> spans = solidSpans(row, forcedClosed ? false : lowOpen, forcedClosed ? false : highOpen);
                bands.add(new Band(subFrom, subTo, spans));
            }
        }
        return mergeAdjacent(bands);
    }

    /** The complement of {@code spans} within {@code [0,16)} — where the glass pane survives. */
    public static List<int[]> complement(List<int[]> spans) {
        List<int[]> result = new ArrayList<>();
        int cursor = 0;
        for (int[] span : spans) {
            if (span[0] > cursor) result.add(new int[]{cursor, span[0]});
            cursor = Math.max(cursor, span[1]);
        }
        if (cursor < 16) result.add(new int[]{cursor, 16});
        return result;
    }

    /** Jamb (gated by the end's perpendicular face) unioned with the arc (never gated). */
    private static List<int[]> solidSpans(Row row, boolean lowOpen, boolean highOpen) {
        List<int[]> spans = new ArrayList<>();
        if (!lowOpen) spans.add(new int[]{0, row.jambWidth()});
        if (!highOpen) spans.add(new int[]{16 - row.jambWidth(), 16});
        for (int[] arc : row.arc()) spans.add(new int[]{arc[0], arc[1]});
        spans.sort((a, b) -> Integer.compare(a[0], b[0]));

        List<int[]> merged = new ArrayList<>();
        for (int[] span : spans) {
            if (!merged.isEmpty() && span[0] <= merged.getLast()[1]) {
                merged.getLast()[1] = Math.max(merged.getLast()[1], span[1]);
            } else {
                merged.add(new int[]{span[0], span[1]});
            }
        }
        return merged;
    }

    /** Collapses vertically-adjacent bands whose spans came out identical into single boxes. */
    private static List<Band> mergeAdjacent(List<Band> bands) {
        List<Band> merged = new ArrayList<>();
        for (Band band : bands) {
            Band previous = merged.isEmpty() ? null : merged.getLast();
            if (previous != null && previous.yFrom() == band.yTo() && sameSpans(previous.spans(), band.spans())) {
                merged.set(merged.size() - 1, new Band(band.yFrom(), previous.yTo(), previous.spans()));
            } else {
                merged.add(band);
            }
        }
        return merged;
    }

    private static boolean sameSpans(List<int[]> a, List<int[]> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!Arrays.equals(a.get(i), b.get(i))) return false;
        }
        return true;
    }
}
