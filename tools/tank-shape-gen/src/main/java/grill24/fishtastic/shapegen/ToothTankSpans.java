package grill24.fishtastic.shapegen;

import java.util.List;

/**
 * The tooth tank's comb layout, read pixel-exactly off
 * {@code docs/tank-shapes/tooth_shape_3_wide.png} (three 16x16 tanks side by side: left = WEST
 * closed/EAST open, middle = both open, right = WEST open/EAST closed):
 *
 * <pre>
 *        0123456789012345           0123456789012345           0123456789012345
 * y=00   FFFFFFFFFFFFFFFF   y=00   FFFFFFFFFFFFFFFF   y=00   FFFFFFFFFFFFFFFF
 * y=01   FFFFFFFFFFFFFFFF   y=01   FFFFFFFFFFFFFFFF   y=01   FFFFFFFFFFFFFFFF
 * y=02   FFF.FFF.FFF.FFF.   y=02   FFF.FFF.FFF.FFF.   y=02   FFF.FFF.FFF.FFFF
 * y=03   FF...F...F...F..   y=03   .F...F...F...F..   y=03   .F...F...F...FFF
 * y=04   FF..............   y=04   ................   y=04   ..............FF
 * y=05-11 (same as y=04)     y=05-11 (same as y=04)     y=05-11 (same as y=04)
 * y=12   FF.F...F...F...F   y=12   ...F...F...F...F   y=12   ...F...F...F..FF
 * y=13   FFFFF.FFF.FFF.FF   y=13   F.FFF.FFF.FFF.FF   y=13   F.FFF.FFF.FFF.FF
 * y=14   FFFFFFFFFFFFFFFF   y=14   FFFFFFFFFFFFFFFF   y=14   FFFFFFFFFFFFFFFF
 * y=15   FFFFFFFFFFFFFFFF   y=15   FFFFFFFFFFFFFFFF   y=15   FFFFFFFFFFFFFFFF
 * </pre>
 *
 * <p>Rows 0/15 are the fixed caps. Row 1 (Y[14,15]) and row 14 (Y[1,2]) are always full-width
 * regardless of west/east, so they carry no low/high component at all — row 14 is what hides the
 * sand from the side (same trick as the shaggy tank). Rows 2/3 (Y[13,14]/Y[12,13]) hang from the
 * ceiling; rows 12/13 (Y[3,4]/Y[2,3]) rise from the floor; both pairs mix an unconditional {@code
 * base} tooth pattern with west/east-gated low/high pixels — confirmed by diffing left vs. middle
 * (west toggles) and middle vs. right (east toggles) tank-by-tank, e.g. row 2's leading "FFF." teeth
 * are byte-identical whether west is open or closed, so they can't be part of a west-gated run.
 * Rows 4-11 are a uniform 2px corner post with no base component at all — a plain low/high pair,
 * mergeable into one band spanning Y[4,12] per the usual run-merging principle.
 *
 * <p>Shared by {@link CombFrameGeometryGenerator} (builds the teeth) and
 * {@link CombGlassGeometryGenerator} (punches the matching holes), applied identically to all four
 * faces (rotated, not mirrored), matching the bramble/shaggy convention.
 */
public final class ToothTankSpans {

    public static final CombTankSpans.Spec SPEC = new CombTankSpans.Spec(
            List.of(
                    new CombTankSpans.Band(14, 15, new int[][]{{0, 16}}),
                    new CombTankSpans.Band(13, 14,
                            new int[][]{{0, 3}, {4, 7}, {8, 11}, {12, 15}},
                            new int[0][],
                            new int[][]{{15, 16}}),
                    new CombTankSpans.Band(12, 13,
                            new int[][]{{1, 2}, {5, 6}, {9, 10}, {13, 14}},
                            new int[][]{{0, 1}},
                            new int[][]{{14, 16}})
            ),
            4, 12,
            new int[][]{{0, 2}},
            new int[][]{{14, 16}},
            List.of(
                    new CombTankSpans.Band(3, 4,
                            new int[][]{{3, 4}, {7, 8}, {11, 12}, {15, 16}},
                            new int[][]{{0, 2}},
                            new int[][]{{14, 15}}),
                    new CombTankSpans.Band(2, 3,
                            new int[][]{{0, 1}, {2, 5}, {6, 9}, {10, 13}, {14, 16}},
                            new int[][]{{1, 2}},
                            new int[0][])
            ),
            new CombTankSpans.Band(1, 2, new int[][]{{0, 16}})
    );

    private ToothTankSpans() {}
}
