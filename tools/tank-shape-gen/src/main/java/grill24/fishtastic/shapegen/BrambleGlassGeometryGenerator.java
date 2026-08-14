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
 * Glass generator for the bramble tank, the counterpart to {@link BrambleFrameGeometryGenerator}: a
 * pane on each closed face, split per Y band into the <em>complement</em> of that band's currently
 * rendered inlay spans (low run if the west/north neighbor is closed, high run if the east/south
 * neighbor is closed — same gating {@link BrambleFrameGeometryGenerator} uses), so the translucent
 * glass never sits behind an opaque inlay.
 *
 * <p>Unlike {@link OrnateGlassGeometryGenerator}/{@link ShaggyGlassGeometryGenerator} there is no
 * separate always-on corner post reserving the outer 1px, so the complement is always taken across
 * the full {@code [0,16]} span rather than an inset {@code [1,15]} one. This is what makes the pane
 * reach the block boundary for free whenever a gated run disappears — no separate corner-open
 * extension needed, unlike the tapered/ornate families.
 *
 * <p>Translucency invariant (as in {@link TaperedGlassGeometryGenerator}): only the two
 * thickness-axis faces are defined per segment, never up/down.
 */
public final class BrambleGlassGeometryGenerator {
    public static final String DEFAULT_TEXTURE = "block/blue_stained_glass";

    private BrambleGlassGeometryGenerator() {}

    public static JsonObject generate(int permutationIndex) {
        return generate(permutationIndex, DEFAULT_TEXTURE);
    }

    public static JsonObject generate(int permutationIndex, String textureId) {
        Set<TankFace> openFaces = TankFace.fromPermutationIndex(permutationIndex);
        boolean northClosed = !openFaces.contains(TankFace.NORTH);
        boolean southClosed = !openFaces.contains(TankFace.SOUTH);
        boolean westClosed = !openFaces.contains(TankFace.WEST);
        boolean eastClosed = !openFaces.contains(TankFace.EAST);
        boolean upOpen = openFaces.contains(TankFace.UP);
        boolean downOpen = openFaces.contains(TankFace.DOWN);

        JsonObject model = baseModel(textureId);
        JsonArray elements = new JsonArray();

        List<BrambleTankSpans.Band> bands = new ArrayList<>();
        bands.addAll(List.of(BrambleTankSpans.MIDDLE));
        bands.add(downOpen ? BrambleTankSpans.ROW14_OPEN : BrambleTankSpans.ROW14_CLOSED);
        if (upOpen) bands.add(BrambleTankSpans.TOP_OPEN);
        if (downOpen) bands.add(BrambleTankSpans.BOTTOM_OPEN);

        if (northClosed) {
            for (BrambleTankSpans.Band b : bands) {
                for (int[] span : complement(renderedSpans(b, westClosed, eastClosed), 0, 16)) {
                    elements.add(pane(span[0], b.yFrom(), 0, span[1], b.yTo(), 1, "north", "south"));
                }
            }
        }
        if (southClosed) {
            for (BrambleTankSpans.Band b : bands) {
                for (int[] span : complement(renderedSpans(b, westClosed, eastClosed), 0, 16)) {
                    elements.add(pane(span[0], b.yFrom(), 15, span[1], b.yTo(), 16, "north", "south"));
                }
            }
        }
        if (westClosed) {
            for (BrambleTankSpans.Band b : bands) {
                for (int[] span : complement(renderedSpans(b, northClosed, southClosed), 0, 16)) {
                    elements.add(paneZAxis(0, b.yFrom(), span[0], 1, b.yTo(), span[1], "west", "east"));
                }
            }
        }
        if (eastClosed) {
            for (BrambleTankSpans.Band b : bands) {
                for (int[] span : complement(renderedSpans(b, northClosed, southClosed), 0, 16)) {
                    elements.add(paneZAxis(15, b.yFrom(), span[0], 16, b.yTo(), span[1], "west", "east"));
                }
            }
        }

        model.add("elements", elements);
        return model;
    }

    /** The spans that actually render for this band given whether the low/high neighbor is closed. */
    private static List<int[]> renderedSpans(BrambleTankSpans.Band band, boolean lowClosed, boolean highClosed) {
        List<int[]> result = new ArrayList<>();
        if (lowClosed) {
            for (int[] span : band.lowSpans()) result.add(span);
        }
        if (highClosed) {
            for (int[] span : band.highSpans()) result.add(span);
        }
        result.sort((a, b) -> Integer.compare(a[0], b[0]));
        return result;
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
