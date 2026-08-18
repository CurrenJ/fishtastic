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
 * Glass generator for the mullion tank — the counterpart to {@link MullionFrameGeometryGenerator}.
 * One pane per closed face, split into the complement of that face's frame content: the persistent
 * anchor at local {@code x=0}, the three bars at {@code x=4,8,12}, and (only when the high-side
 * neighbor is closed) the ordinary wall at {@code x=15}. When the high-side neighbor is open the
 * pane simply extends to the block boundary in its place — no extra gating needed since the
 * complement is recomputed from whatever frame spans are actually present.
 *
 * <p>Translucency invariant (as in {@link TaperedGlassGeometryGenerator}): only the two
 * thickness-axis faces are defined per segment, never up/down.
 */
public final class MullionGlassGeometryGenerator {
    public static final String DEFAULT_TEXTURE = "block/blue_stained_glass";

    private MullionGlassGeometryGenerator() {}

    public static JsonObject generate(int permutationIndex) {
        return generate(permutationIndex, DEFAULT_TEXTURE);
    }

    public static JsonObject generate(int permutationIndex, String textureId) {
        Set<TankFace> openFaces = TankFace.fromPermutationIndex(permutationIndex);
        boolean upOpen = openFaces.contains(TankFace.UP);
        boolean downOpen = openFaces.contains(TankFace.DOWN);
        int yFrom = downOpen ? 0 : 1;
        int yTo = upOpen ? 16 : 15;

        boolean northClosed = !openFaces.contains(TankFace.NORTH);
        boolean southClosed = !openFaces.contains(TankFace.SOUTH);
        boolean westClosed = !openFaces.contains(TankFace.WEST);
        boolean eastClosed = !openFaces.contains(TankFace.EAST);

        JsonObject model = baseModel(textureId);
        JsonArray elements = new JsonArray();

        if (northClosed) {
            for (int[] span : complement(eastClosed)) {
                elements.add(pane(span[0], yFrom, 0, span[1], yTo, 1, "north", "south"));
            }
        }
        if (southClosed) {
            for (int[] span : complement(eastClosed)) {
                elements.add(pane(span[0], yFrom, 15, span[1], yTo, 16, "north", "south"));
            }
        }
        if (westClosed) {
            for (int[] span : complement(southClosed)) {
                elements.add(paneZAxis(0, yFrom, span[0], 1, yTo, span[1], "west", "east"));
            }
        }
        if (eastClosed) {
            for (int[] span : complement(southClosed)) {
                elements.add(paneZAxis(15, yFrom, span[0], 16, yTo, span[1], "west", "east"));
            }
        }

        model.add("elements", elements);
        return model;
    }

    /** Complement of {anchor[0,1), bars[4,5)[8,9)[12,13), wall[15,16) if closed} within [0,16]. */
    private static List<int[]> complement(boolean wallClosed) {
        List<int[]> occupied = new ArrayList<>();
        occupied.add(new int[]{0, 1});
        for (int bx : MullionFrameGeometryGenerator.BAR_X) occupied.add(new int[]{bx, bx + 1});
        if (wallClosed) occupied.add(new int[]{15, 16});

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
