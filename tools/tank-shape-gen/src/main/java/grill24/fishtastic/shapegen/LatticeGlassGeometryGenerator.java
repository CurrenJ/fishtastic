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
 * Glass generator for the lattice tank — the counterpart to {@link LatticeFrameGeometryGenerator}.
 * One pane segment per Y-band per closed face, split into the complement of that band's edge +
 * diagonal spans, mirroring {@link OrnateGlassGeometryGenerator}'s approach.
 *
 * <p>Translucency invariant (as in {@link TaperedGlassGeometryGenerator}): only the two
 * thickness-axis faces are defined per segment, never up/down.
 */
public final class LatticeGlassGeometryGenerator {
    public static final String DEFAULT_TEXTURE = "block/blue_stained_glass";

    private record Band(int yFrom, int yTo, int width, int[][] diag) {}

    private LatticeGlassGeometryGenerator() {}

    public static JsonObject generate(int permutationIndex) {
        return generate(permutationIndex, DEFAULT_TEXTURE);
    }

    public static JsonObject generate(int permutationIndex, String textureId) {
        Set<TankFace> openFaces = TankFace.fromPermutationIndex(permutationIndex);
        boolean upOpen = openFaces.contains(TankFace.UP);
        boolean downOpen = openFaces.contains(TankFace.DOWN);
        boolean northClosed = !openFaces.contains(TankFace.NORTH);
        boolean southClosed = !openFaces.contains(TankFace.SOUTH);
        boolean westClosed = !openFaces.contains(TankFace.WEST);
        boolean eastClosed = !openFaces.contains(TankFace.EAST);

        JsonObject model = baseModel(textureId);
        JsonArray elements = new JsonArray();

        List<Band> bands = List.of(
                new Band(14, upOpen ? 16 : 15, 2, new int[0][]),
                new Band(13, 14, 1, new int[][]{{2, 3}, {13, 14}}),
                new Band(12, 13, 1, new int[][]{{3, 4}, {12, 13}}),
                new Band(11, 12, 1, new int[][]{{4, 5}, {11, 12}}),
                new Band(10, 11, 1, new int[][]{{5, 6}, {10, 11}}),
                new Band(9, 10, 1, new int[][]{{6, 7}, {9, 10}}),
                new Band(7, 9, 1, new int[][]{{7, 9}}),
                new Band(6, 7, 1, new int[][]{{6, 7}, {9, 10}}),
                new Band(5, 6, 1, new int[][]{{5, 6}, {10, 11}}),
                new Band(4, 5, 1, new int[][]{{4, 5}, {11, 12}}),
                new Band(3, 4, 1, new int[][]{{3, 4}, {12, 13}}),
                new Band(2, 3, 1, new int[][]{{2, 3}, {13, 14}}),
                new Band(downOpen ? 0 : 1, 2, 2, new int[0][])
        );

        if (northClosed) {
            for (Band b : bands) for (int[] span : complement(b)) elements.add(pane(span[0], b.yFrom(), 0, span[1], b.yTo(), 1, "north", "south"));
        }
        if (southClosed) {
            for (Band b : bands) for (int[] span : complement(b)) elements.add(pane(span[0], b.yFrom(), 15, span[1], b.yTo(), 16, "north", "south"));
        }
        if (westClosed) {
            for (Band b : bands) for (int[] span : complement(b)) elements.add(paneZAxis(0, b.yFrom(), span[0], 1, b.yTo(), span[1], "west", "east"));
        }
        if (eastClosed) {
            for (Band b : bands) for (int[] span : complement(b)) elements.add(paneZAxis(15, b.yFrom(), span[0], 16, b.yTo(), span[1], "west", "east"));
        }

        model.add("elements", elements);
        return model;
    }

    private static List<int[]> complement(Band b) {
        List<int[]> occupied = new ArrayList<>();
        occupied.add(new int[]{0, b.width()});
        occupied.add(new int[]{16 - b.width(), 16});
        for (int[] d : b.diag()) occupied.add(d);
        occupied.sort((a, c) -> Integer.compare(a[0], c[0]));

        List<int[]> result = new ArrayList<>();
        int cursor = 0;
        for (int[] span : occupied) {
            if (span[0] > cursor) result.add(new int[]{cursor, span[0]});
            cursor = Math.max(cursor, span[1]);
        }
        if (cursor < 16) result.add(new int[]{cursor, 16});
        return result;
    }

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
