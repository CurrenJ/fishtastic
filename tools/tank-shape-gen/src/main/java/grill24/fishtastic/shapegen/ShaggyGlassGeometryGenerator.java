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
 * Glass generator for the shaggy tank, the counterpart to {@link ShaggyFrameGeometryGenerator}: a
 * standard 1px pane on each face, split into the <em>complement</em> of that face's inlay spans so
 * the translucent glass never sits behind an opaque 1px inlay (which would z-fight). Same approach
 * as {@link OrnateGlassGeometryGenerator}, reading its spans from the shared
 * {@link ShaggyTankSpans} table rather than a private copy.
 *
 * <p>The band at Y {@code [1,2]} is fully covered by the frame's full-width band, so its complement
 * is empty and no glass is emitted there at all — that band is what hides the sand from the side.
 *
 * <p>The pane reaches the block boundary both vertically (to y=0/16 when the floor/ceiling is open)
 * and horizontally (to x/z=0/16 when the adjacent corner post is absent), and the inlay holes are
 * extended by the same rule via {@link ShaggyTankSpans#extended} so hole and inlay stay congruent.
 *
 * <p>Translucency invariant (as in {@link TaperedGlassGeometryGenerator}): only the two
 * thickness-axis faces are defined per segment, never up/down — segments stack along Y and a
 * coincident pair of translucent quads compounds into a visibly darker seam line.
 */
public final class ShaggyGlassGeometryGenerator {
    public static final String DEFAULT_TEXTURE = "block/blue_stained_glass";

    private ShaggyGlassGeometryGenerator() {}

    public static JsonObject generate(int permutationIndex) {
        return generate(permutationIndex, DEFAULT_TEXTURE);
    }

    public static JsonObject generate(int permutationIndex, String textureId) {
        Set<TankFace> openFaces = TankFace.fromPermutationIndex(permutationIndex);
        boolean northClosed = !openFaces.contains(TankFace.NORTH);
        boolean southClosed = !openFaces.contains(TankFace.SOUTH);
        boolean westClosed = !openFaces.contains(TankFace.WEST);
        boolean eastClosed = !openFaces.contains(TankFace.EAST);
        boolean upClosed = !openFaces.contains(TankFace.UP);
        boolean downClosed = !openFaces.contains(TankFace.DOWN);

        JsonObject model = baseModel(textureId);
        JsonArray elements = new JsonArray();

        List<ShaggyTankSpans.Band> bands = glassBands(upClosed, downClosed);

        if (northClosed) {
            int minX = westClosed ? 1 : 0;
            int maxX = eastClosed ? 15 : 16;
            for (ShaggyTankSpans.Band b : bands) {
                for (int[] span : complement(ShaggyTankSpans.spansFor(b, !westClosed, !eastClosed), minX, maxX)) {
                    elements.add(pane(span[0], b.yFrom(), 0, span[1], b.yTo(), 1, "north", "south"));
                }
            }
        }
        if (southClosed) {
            int minX = westClosed ? 1 : 0;
            int maxX = eastClosed ? 15 : 16;
            for (ShaggyTankSpans.Band b : bands) {
                for (int[] span : complement(ShaggyTankSpans.spansFor(b, !westClosed, !eastClosed), minX, maxX)) {
                    elements.add(pane(span[0], b.yFrom(), 15, span[1], b.yTo(), 16, "north", "south"));
                }
            }
        }
        if (westClosed) {
            int minZ = northClosed ? 1 : 0;
            int maxZ = southClosed ? 15 : 16;
            for (ShaggyTankSpans.Band b : bands) {
                for (int[] span : complement(ShaggyTankSpans.spansFor(b, !northClosed, !southClosed), minZ, maxZ)) {
                    elements.add(paneZAxis(0, b.yFrom(), span[0], 1, b.yTo(), span[1], "west", "east"));
                }
            }
        }
        if (eastClosed) {
            int minZ = northClosed ? 1 : 0;
            int maxZ = southClosed ? 15 : 16;
            for (ShaggyTankSpans.Band b : bands) {
                for (int[] span : complement(ShaggyTankSpans.spansFor(b, !northClosed, !southClosed), minZ, maxZ)) {
                    elements.add(paneZAxis(15, b.yFrom(), span[0], 16, b.yTo(), span[1], "west", "east"));
                }
            }
        }

        model.add("elements", elements);
        return model;
    }

    private static final int[][] NO_SPANS = new int[0][];

    /**
     * The glass's Y bands, top to bottom. A band's spans are the inlay holes to leave; they're
     * dropped when the corresponding cap is open (the fringe is gone with it), and the band touching
     * an open cap extends to the block boundary for the seam.
     */
    private static List<ShaggyTankSpans.Band> glassBands(boolean upClosed, boolean downClosed) {
        List<ShaggyTankSpans.Band> bands = new ArrayList<>();
        for (ShaggyTankSpans.Band band : ShaggyTankSpans.TOP) {
            int yTo = band.yTo() == 15 && !upClosed ? 16 : band.yTo();
            bands.add(upClosed
                    ? new ShaggyTankSpans.Band(band.yFrom(), yTo, band.spans(), band.lowEdgeInlay(), band.highEdgeInlay())
                    : new ShaggyTankSpans.Band(band.yFrom(), yTo, NO_SPANS, false, false));
        }
        bands.add(new ShaggyTankSpans.Band(4, 11, NO_SPANS, false, false));
        for (ShaggyTankSpans.Band band : ShaggyTankSpans.BOTTOM) {
            int yFrom = band.yFrom() == 1 && !downClosed ? 0 : band.yFrom();
            bands.add(downClosed
                    ? new ShaggyTankSpans.Band(yFrom, band.yTo(), band.spans(), band.lowEdgeInlay(), band.highEdgeInlay())
                    : new ShaggyTankSpans.Band(yFrom, band.yTo(), NO_SPANS, false, false));
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
