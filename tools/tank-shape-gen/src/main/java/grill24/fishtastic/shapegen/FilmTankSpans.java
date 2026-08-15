package grill24.fishtastic.shapegen;

import java.util.List;

/**
 * The film tank's comb layout, read pixel-exactly off
 * {@code docs/tank-shapes/film_shape_3_wide.png} (three 16x16 tanks side by side: left = WEST
 * closed/EAST open, middle = both open, right = WEST open/EAST closed):
 *
 * <pre>
 *        0123456789012345           0123456789012345           0123456789012345
 * y=00   FFFFFFFFFFFFFFFF   y=00   FFFFFFFFFFFFFFFF   y=00   FFFFFFFFFFFFFFFF
 * y=01   F.F.F.F.F.F.F.F.   y=01   F.F.F.F.F.F.F.F.   y=01   F.F.F.F.F.F.F.FF
 * y=02   F...............   y=02   ................   y=02   ...............F
 * y=03-13 (same as y=02)     y=03-13 (same as y=02)     y=03-13 (same as y=02)
 * y=14   FFSFSFSFSFSFSFSF   y=14   SFSFSFSFSFSFSFSF   y=14   SFSFSFSFSFSFSFSF
 * y=15   FFFFFFFFFFFFFFFF   y=15   FFFFFFFFFFFFFFFF   y=15   FFFFFFFFFFFFFFFF
 * </pre>
 *
 * <p>Rows 0/15 are the fixed caps. Row 1 (Y[14,15]) is a period-2 "sprocket hole" comb — the
 * filmstrip motif the shape is named for — unconditional on west/east (byte-identical between the
 * left and middle tank) except its very last tooth, which only appears once east is closed. Rows
 * 2-13 (Y[2,14]) are a plain uniform 1px corner post with no base component, mergeable into one
 * band. Row 14 (Y[1,2], the sand row) repeats row 1's comb rhythm but against sand instead of glass
 * — {@code S} is the ordinary standard sand showing through wherever this row has no frame tooth;
 * per the tank-shape-image-to-datagen skill's ornate-path note, that sand stays the plain standard
 * square and these are just inlay teeth sitting in front of it, not a modified sand footprint. Row
 * 14's west corner (idx 0) is the one gated pixel — {@code F} when west is closed, otherwise the
 * comb's own background, which reads as sand in the reference because the standard sand sits right
 * behind the glass filling that gap — see {@link CombGlassGeometryGenerator}.
 *
 * <p>Shared by {@link CombFrameGeometryGenerator} (builds the comb) and
 * {@link CombGlassGeometryGenerator} (punches the matching holes, including row 14's) applied identically
 * to all four faces (rotated, not mirrored), matching the bramble/shaggy/tooth convention.
 */
public final class FilmTankSpans {

    public static final CombTankSpans.Spec SPEC = new CombTankSpans.Spec(
            List.of(
                    new CombTankSpans.Band(14, 15,
                            new int[][]{{0, 1}, {2, 3}, {4, 5}, {6, 7}, {8, 9}, {10, 11}, {12, 13}, {14, 15}},
                            new int[0][],
                            new int[][]{{15, 16}})
            ),
            2, 14,
            new int[][]{{0, 1}},
            new int[][]{{15, 16}},
            List.of(),
            new CombTankSpans.Band(1, 2,
                    new int[][]{{1, 2}, {3, 4}, {5, 6}, {7, 8}, {9, 10}, {11, 12}, {13, 14}, {15, 16}},
                    new int[][]{{0, 1}},
                    new int[0][])
    );

    private FilmTankSpans() {}
}
