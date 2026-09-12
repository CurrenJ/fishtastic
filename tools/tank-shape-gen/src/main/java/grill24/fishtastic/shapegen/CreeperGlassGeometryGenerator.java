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
 * Glass generator for the creeper tank: a standard 1px pane on each face, with holes carved out
 * where {@link CreeperFrameGeometryGenerator}'s creeper-face inlay sits, so the opaque frame pixels
 * and the translucent pane never coincide (the same z-fighting concern {@link
 * OrnateGlassGeometryGenerator} solves for its brackets).
 *
 * <p>Unlike the ornate tank's top/bottom brackets, the creeper face sits entirely in the flat
 * mid-section (Y 5-13) independent of DOWN/UP — so unlike {@link OrnateGlassGeometryGenerator}'s
 * bands, the hole set here never changes with which caps are open; only the plain top/bottom bands
 * above/below the face extend to the block boundary for the vertical seam.
 *
 * <p>The pane also reaches the block boundary horizontally (x/z = 0/16) when the adjacent corner
 * post is absent — i.e. the adjacent face is open — matching {@link TaperedGlassGeometryGenerator}'s
 * corner gating.
 *
 * <p>Translucency invariant (as in {@link TaperedGlassGeometryGenerator}): only the two thickness-
 * axis faces are defined per segment, never up/down.
 */
public final class CreeperGlassGeometryGenerator {
    public static final String DEFAULT_TEXTURE = "block/blue_stained_glass";

    // Creeper-face hole spans, shared with the frame generator, top to bottom.
    private static final List<int[]> Y911 = List.of(new int[]{5, 7}, new int[]{9, 11});
    private static final List<int[]> Y89 = List.of(new int[]{7, 9});
    private static final List<int[]> Y68 = List.of(new int[]{6, 10});
    private static final List<int[]> Y56 = List.of(new int[]{6, 7}, new int[]{9, 10});

    private CreeperGlassGeometryGenerator() {}

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

        List<Band> bands = glassBands(!openFaces.contains(TankFace.UP), !openFaces.contains(TankFace.DOWN));

        if (northClosed) {
            int minX = westClosed ? 1 : 0;
            int maxX = eastClosed ? 15 : 16;
            for (Band b : bands) {
                for (int[] span : complement(b.holeSpans, minX, maxX)) {
                    elements.add(pane(span[0], b.yFrom, 0, span[1], b.yTo, 1, "north", "south"));
                }
            }
        }
        if (southClosed) {
            int minX = westClosed ? 1 : 0;
            int maxX = eastClosed ? 15 : 16;
            for (Band b : bands) {
                for (int[] span : complement(b.holeSpans, minX, maxX)) {
                    elements.add(pane(span[0], b.yFrom, 15, span[1], b.yTo, 16, "north", "south"));
                }
            }
        }
        if (westClosed) {
            int minZ = northClosed ? 1 : 0;
            int maxZ = southClosed ? 15 : 16;
            for (Band b : bands) {
                for (int[] span : complement(b.holeSpans, minZ, maxZ)) {
                    elements.add(paneZAxis(0, b.yFrom, span[0], 1, b.yTo, span[1], "west", "east"));
                }
            }
        }
        if (eastClosed) {
            int minZ = northClosed ? 1 : 0;
            int maxZ = southClosed ? 15 : 16;
            for (Band b : bands) {
                for (int[] span : complement(b.holeSpans, minZ, maxZ)) {
                    elements.add(paneZAxis(15, b.yFrom, span[0], 16, b.yTo, span[1], "west", "east"));
                }
            }
        }

        model.add("elements", elements);
        return model;
    }

    /** A Y band and the creeper-face hole spans to carve out of it (empty = full pane). */
    private record Band(int yFrom, int yTo, List<int[]> holeSpans) {}

    /**
     * The glass's Y bands, top to bottom. The plain bands above/below the creeper face extend to
     * the block boundary (y=0 / y=16) when the adjacent cap is open, for the vertical seam; the
     * creeper-face bands themselves never touch a cap so are unaffected by UP/DOWN.
     */
    private static List<Band> glassBands(boolean upClosed, boolean downClosed) {
        List<Band> bands = new ArrayList<>();
        bands.add(new Band(downClosed ? 1 : 0, 5, List.of()));
        bands.add(new Band(5, 6, Y56));
        bands.add(new Band(6, 8, Y68));
        bands.add(new Band(8, 9, Y89));
        bands.add(new Band(9, 11, Y911));
        bands.add(new Band(11, upClosed ? 15 : 16, List.of()));
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
