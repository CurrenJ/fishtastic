package grill24.fishtastic.shapegen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static grill24.fishtastic.shapegen.TankShapeGeometry.baseModel;
import static grill24.fishtastic.shapegen.TankShapeGeometry.face;
import static grill24.fishtastic.shapegen.TankShapeGeometry.vec3;

/**
 * Glass generator for the ornate tank: a standard 1px pane on each face, spanning the full
 * interior height, but with <em>holes</em> wherever the decorative frame brackets sit — the
 * brackets (see {@link OrnateFrameGeometryGenerator}) are 1px inlays on the glass surface, so the
 * glass must not render behind them (which would z-fight). The pane is therefore split into X
 * segments per Y band, the complement of the bracket spans.
 *
 * <p>The pane's extent is the standard glass's, not a fixed box: it reaches the block boundary
 * both vertically (to y=0/16 when the floor/ceiling is open, so stacked tanks meet flush) and
 * horizontally (to x/z=0/16 when the adjacent corner post is absent — i.e. the adjacent face is
 * open), matching {@link TaperedGlassGeometryGenerator}'s corner gating.
 *
 * <p>Bracket holes are gated exactly like the brackets themselves: the top bands only lose glass to
 * brackets when UP is closed, the bottom bands only when DOWN is closed.
 *
 * <p>Translucency invariant (as in {@link TaperedGlassGeometryGenerator}): only the two thickness-
 * axis faces are defined per segment, never up/down.
 */
public final class OrnateGlassGeometryGenerator {
    public static final String DEFAULT_TEXTURE = "block/blue_stained_glass";

    // Interior bracket X spans (exclusive [x1,x2)) per Y band, shared with the frame generator.
    private static final List<int[]> BOTTOM_Y12 = List.of(new int[]{2, 3}, new int[]{4, 6}, new int[]{7, 9}, new int[]{10, 12}, new int[]{13, 14});
    private static final List<int[]> BOTTOM_Y23 = List.of(new int[]{4, 5}, new int[]{11, 12});
    private static final List<int[]> TOP_Y1213 = List.of(new int[]{3, 4}, new int[]{12, 13});
    private static final List<int[]> TOP_Y1314 = List.of(new int[]{2, 4}, new int[]{7, 9}, new int[]{12, 14});
    private static final List<int[]> TOP_Y1415 = List.of(new int[]{1, 5}, new int[]{6, 7}, new int[]{9, 10}, new int[]{11, 15});

    private OrnateGlassGeometryGenerator() {}

    public static JsonObject generate(int permutationIndex) {
        return generate(permutationIndex, DEFAULT_TEXTURE);
    }

    public static JsonObject generate(int permutationIndex, String textureId) {
        Set<TankFace> openFaces = TankFace.fromPermutationIndex(permutationIndex);
        boolean northClosed = !openFaces.contains(TankFace.NORTH);
        boolean southClosed = !openFaces.contains(TankFace.SOUTH);
        boolean westClosed = !openFaces.contains(TankFace.WEST);
        boolean eastClosed = !openFaces.contains(TankFace.EAST);

        JsonObject model = baseModel(textureId);
        JsonArray elements = new JsonArray();

        boolean upOpen = openFaces.contains(TankFace.UP);
        boolean downOpen = openFaces.contains(TankFace.DOWN);
        List<Band> bands = glassBands(!upOpen, !downOpen);

        // Within the 1px cap band (Y[0,1) when the floor is open, Y[15,16) when the ceiling is
        // open), a corner that would otherwise flush to the boundary (its perpendicular face open)
        // instead yields to that face's own edge-diagonal frame beam — see
        // TaperedFrameGeometryGenerator#generateEdgeFragment and
        // TaperedGlassGeometryGenerator#splitRunForCapBands's matching note. Ornate's corner post is
        // the same plain 1px post as CornerTaperProfile.STANDARD, so the same fix applies here,
        // hand-duplicated since ornate has its own glass generator.
        if (northClosed) {
            for (Band b : bands) {
                boolean capBand = (downOpen && b.yFrom == 0) || (upOpen && b.yTo == 16);
                int minX = (westClosed || (capBand && !westClosed)) ? 1 : 0;
                int maxX = (eastClosed || (capBand && !eastClosed)) ? 15 : 16;
                for (int[] span : complement(b.bracketSpans, minX, maxX)) {
                    elements.add(pane(span[0], b.yFrom, 0, span[1], b.yTo, 1, "north", "south"));
                }
            }
        }
        if (southClosed) {
            for (Band b : bands) {
                boolean capBand = (downOpen && b.yFrom == 0) || (upOpen && b.yTo == 16);
                int minX = (westClosed || (capBand && !westClosed)) ? 1 : 0;
                int maxX = (eastClosed || (capBand && !eastClosed)) ? 15 : 16;
                for (int[] span : complement(b.bracketSpans, minX, maxX)) {
                    elements.add(pane(span[0], b.yFrom, 15, span[1], b.yTo, 16, "north", "south"));
                }
            }
        }
        if (westClosed) {
            for (Band b : bands) {
                boolean capBand = (downOpen && b.yFrom == 0) || (upOpen && b.yTo == 16);
                int minZ = (northClosed || (capBand && !northClosed)) ? 1 : 0;
                int maxZ = (southClosed || (capBand && !southClosed)) ? 15 : 16;
                for (int[] span : complement(b.bracketSpans, minZ, maxZ)) {
                    elements.add(paneZAxis(0, b.yFrom, span[0], 1, b.yTo, span[1], "west", "east"));
                }
            }
        }
        if (eastClosed) {
            for (Band b : bands) {
                boolean capBand = (downOpen && b.yFrom == 0) || (upOpen && b.yTo == 16);
                int minZ = (northClosed || (capBand && !northClosed)) ? 1 : 0;
                int maxZ = (southClosed || (capBand && !southClosed)) ? 15 : 16;
                for (int[] span : complement(b.bracketSpans, minZ, maxZ)) {
                    elements.add(paneZAxis(15, b.yFrom, span[0], 16, b.yTo, span[1], "west", "east"));
                }
            }
        }

        model.add("elements", elements);
        return model;
    }

    /** A Y band and the bracket spans to carve out of it (empty = full pane). */
    private record Band(int yFrom, int yTo, List<int[]> bracketSpans) {}

    /**
     * The glass's Y bands, top to bottom. Each band's {@code bracketSpans} are the holes to leave;
     * they are empty when the corresponding cap is open (the brackets are gone). The band touching
     * an open cap extends to the block boundary (y=0 / y=16) for the seam, and is kept as its own
     * separate 1px band there (rather than merged with its neighbor) so the cap-band-only corner
     * fix above can target exactly that 1px slice.
     */
    private static List<Band> glassBands(boolean upClosed, boolean downClosed) {
        List<Band> bands = new ArrayList<>();
        if (downClosed) {
            bands.add(new Band(1, 2, BOTTOM_Y12));
        } else {
            bands.add(new Band(0, 1, List.of()));
            bands.add(new Band(1, 2, List.of()));
        }
        bands.add(new Band(2, 3, downClosed ? BOTTOM_Y23 : List.of()));
        bands.add(new Band(3, 12, List.of()));
        bands.add(new Band(12, 13, upClosed ? TOP_Y1213 : List.of()));
        bands.add(new Band(13, 14, upClosed ? TOP_Y1314 : List.of()));
        if (upClosed) {
            bands.add(new Band(14, 15, TOP_Y1415));
        } else {
            bands.add(new Band(14, 15, List.of()));
            bands.add(new Band(15, 16, List.of()));
        }
        return bands;
    }

    /** The complement of the given spans within [min,max]. Spans must be sorted and non-overlapping. */
    private static List<int[]> complement(List<int[]> spans, int min, int max) {
        List<int[]> result = new ArrayList<>();
        int cursor = min;
        for (int[] span : spans) {
            if (span[0] > cursor) {
                result.add(new int[]{cursor, span[0]});
            }
            cursor = Math.max(cursor, span[1]);
        }
        if (cursor < max) {
            result.add(new int[]{cursor, max});
        }
        return result;
    }

    /** North/south-facing pane segment (thickness along Z). */
    private static JsonObject pane(int x1, int y1, int z1, int x2, int y2, int z2, String faceA, String faceB) {
        JsonObject element = new JsonObject();
        element.add("from", vec3(x1, y1, z1));
        element.add("to", vec3(x2, y2, z2));

        JsonObject faces = new JsonObject();
        faces.add(faceA, face(x1, y1, x2, y2, "#all"));
        faces.add(faceB, face(x1, y1, x2, y2, "#all"));
        element.add("faces", faces);
        return element;
    }

    /** West/east-facing pane segment (thickness along X). */
    private static JsonObject paneZAxis(int x1, int y1, int z1, int x2, int y2, int z2, String faceA, String faceB) {
        JsonObject element = new JsonObject();
        element.add("from", vec3(x1, y1, z1));
        element.add("to", vec3(x2, y2, z2));

        JsonObject faces = new JsonObject();
        faces.add(faceA, face(z1, y1, z2, y2, "#all"));
        faces.add(faceB, face(z1, y1, z2, y2, "#all"));
        element.add("faces", faces);
        return element;
    }
}
