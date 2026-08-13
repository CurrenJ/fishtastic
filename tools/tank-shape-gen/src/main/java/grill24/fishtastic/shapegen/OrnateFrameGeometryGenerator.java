package grill24.fishtastic.shapegen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Set;

import static grill24.fishtastic.shapegen.TankShapeGeometry.addSingleGroup;
import static grill24.fishtastic.shapegen.TankShapeGeometry.baseModel;
import static grill24.fishtastic.shapegen.TankShapeGeometry.face;
import static grill24.fishtastic.shapegen.TankShapeGeometry.vec3;

/**
 * Frame generator for the ornate tank (image {@code docs/tank-shapes/ornate_tank.png}): a
 * standard 1px corner-post + ceiling/floor frame, plus decorative 1px-thick brackets that project
 * inward on each face near the top and bottom (the interior {@code F} pixels in the reference's
 * cross-section).
 *
 * <p>The brackets are symmetric — the same pattern appears on all four faces, mirrored/rotated —
 * and are gated by the adjacent faces exactly like corner posts: a bracket on the north face only
 * exists when NORTH is closed. The top brackets (Y 12..15) only exist when the ceiling (UP) is
 * closed, and the bottom brackets (Y 2..3) only when the floor (DOWN) is closed, matching the
 * vertical-connection reference where an open cap removes its corbels.
 */
public final class OrnateFrameGeometryGenerator {
    public static final String DEFAULT_TEXTURE = "block/oak_planks";

    // Interior bracket X spans per Y band (top-to-bottom), excluding the 1px corner posts. Each
    // span is an exclusive [x1, x2) box range (x2 = last frame pixel + 1).
    private static final int[][][] TOP_BRACKETS = {
            // Y 14-15: "FFFFF.F..F.FFFFF" -> x=1..4, 6, 9, 11..14
            {{1, 5}, {6, 7}, {9, 10}, {11, 15}},
            // Y 13-14: "F.FF...FF...FF.F" -> x=2..3, 7..8, 12..13
            {{2, 4}, {7, 9}, {12, 14}},
            // Y 12-13: "F..F........F..F" -> x=3, 12
            {{3, 4}, {12, 13}}
    };
    private static final int[] TOP_BRACKET_Y_FROM = {14, 13, 12};
    private static final int[] TOP_BRACKET_Y_TO = {15, 14, 13};

    private static final int[][] BOTTOM_BRACKETS_Y23 = {
            // Y 2-3: "F...F......F...F" -> x=4, 11
            {4, 5}, {11, 12}
    };

    // Bottom inlays at Y 1-2 (the "F" pixels interleaved with the standard sand in the reference).
    private static final int[][] BOTTOM_BRACKETS_Y12 = {
            // Y 1-2: "FSFSFFSFFSFFSFSF" -> x=2, 4..5, 7..8, 10..11, 13
            {2, 3}, {4, 6}, {7, 9}, {10, 12}, {13, 14}
    };

    private OrnateFrameGeometryGenerator() {}

    public static JsonObject generate(int permutationIndex) {
        return generate(permutationIndex, DEFAULT_TEXTURE);
    }

    public static JsonObject generate(int permutationIndex, String textureId) {
        Set<TankFace> openFaces = TankFace.fromPermutationIndex(permutationIndex);

        JsonObject model = baseModel(textureId);
        JsonArray elements = new JsonArray();

        if (!openFaces.contains(TankFace.UP)) {
            elements.add(createCap("ceiling", 15, 16, openFaces, true));
        }
        if (!openFaces.contains(TankFace.DOWN)) {
            elements.add(createCap("floor", 0, 1, openFaces, false));
        }

        // Corner posts (1px), gated like the standard tank's supports. Their height extends to the
        // block boundary when the adjacent cap is open, so stacked ornate tanks meet flush at the
        // seam (the same seam-extension the tapered generators get from CornerTaperProfile.runs()).
        int supportYFrom = openFaces.contains(TankFace.DOWN) ? 0 : 1;
        int supportYTo = openFaces.contains(TankFace.UP) ? 16 : 15;
        if (!openFaces.contains(TankFace.NORTH) && !openFaces.contains(TankFace.WEST)) {
            elements.add(createSupport(0, 0, supportYFrom, supportYTo));
        }
        if (!openFaces.contains(TankFace.NORTH) && !openFaces.contains(TankFace.EAST)) {
            elements.add(createSupport(1, 0, supportYFrom, supportYTo));
        }
        if (!openFaces.contains(TankFace.SOUTH) && !openFaces.contains(TankFace.WEST)) {
            elements.add(createSupport(0, 1, supportYFrom, supportYTo));
        }
        if (!openFaces.contains(TankFace.SOUTH) && !openFaces.contains(TankFace.EAST)) {
            elements.add(createSupport(1, 1, supportYFrom, supportYTo));
        }

        // Top brackets (only when the ceiling is closed).
        if (!openFaces.contains(TankFace.UP)) {
            for (int i = 0; i < TOP_BRACKETS.length; i++) {
                int yFrom = TOP_BRACKET_Y_FROM[i];
                int yTo = TOP_BRACKET_Y_TO[i];
                for (int[] span : TOP_BRACKETS[i]) {
                    addFaceBrackets(elements, openFaces, span[0], yFrom, span[1], yTo);
                }
            }
        }

        // Bottom brackets (only when the floor is closed).
        if (!openFaces.contains(TankFace.DOWN)) {
            for (int[] span : BOTTOM_BRACKETS_Y23) {
                addFaceBrackets(elements, openFaces, span[0], 2, span[1], 3);
            }
            for (int[] span : BOTTOM_BRACKETS_Y12) {
                addFaceBrackets(elements, openFaces, span[0], 1, span[1], 2);
            }
        }

        model.add("elements", elements);
        addSingleGroup(model, "frame_" + permutationIndex);
        return model;
    }

    /** Adds the bracket span {@code [x1,x2]} at Y {@code [y1,y2]} on each closed face. */
    private static void addFaceBrackets(JsonArray elements, Set<TankFace> openFaces, int x1, int y1, int x2, int y2) {
        if (!openFaces.contains(TankFace.NORTH)) {
            elements.add(createBox("bracket_n_" + x1, x1, y1, 0, x2, y2, 1));
        }
        if (!openFaces.contains(TankFace.SOUTH)) {
            elements.add(createBox("bracket_s_" + x1, x1, y1, 15, x2, y2, 16));
        }
        if (!openFaces.contains(TankFace.WEST)) {
            elements.add(createBox("bracket_w_" + x1, 0, y1, x1, 1, y2, x2));
        }
        if (!openFaces.contains(TankFace.EAST)) {
            elements.add(createBox("bracket_e_" + x1, 15, y1, x1, 16, y2, x2));
        }
    }

    private static JsonObject createCap(String name, int y1, int y2, Set<TankFace> openFaces, boolean ceiling) {
        JsonObject element = new JsonObject();
        element.addProperty("name", name);
        element.add("from", vec3(0, y1, 0));
        element.add("to", vec3(16, y2, 16));

        JsonObject faces = new JsonObject();
        if (!openFaces.contains(TankFace.NORTH)) faces.add("north", face(0, ceiling ? 0 : 15, 16, ceiling ? 1 : 16, "#all"));
        if (!openFaces.contains(TankFace.EAST)) faces.add("east", face(0, ceiling ? 0 : 15, 16, ceiling ? 1 : 16, "#all"));
        if (!openFaces.contains(TankFace.SOUTH)) faces.add("south", face(0, ceiling ? 0 : 15, 16, ceiling ? 1 : 16, "#all"));
        if (!openFaces.contains(TankFace.WEST)) faces.add("west", face(0, ceiling ? 0 : 15, 16, ceiling ? 1 : 16, "#all"));
        faces.add("up", face(0, 0, 16, 16, "#all"));
        faces.add("down", face(0, 0, 16, 16, "#all"));
        element.add("faces", faces);
        return element;
    }

    private static JsonObject createSupport(int cornerX, int cornerZ, int yFrom, int yTo) {
        double x1 = cornerX == 0 ? 0 : 15;
        double z1 = cornerZ == 0 ? 0 : 15;
        JsonObject element = new JsonObject();
        element.addProperty("name", "support_" + (int) x1 + "_" + (int) z1);
        element.add("from", vec3(x1, yFrom, z1));
        element.add("to", vec3(x1 + 1, yTo, z1 + 1));

        JsonObject faces = new JsonObject();
        faces.add("north", face(16 - (x1 + 1), 16 - yTo, 16 - x1, 16 - yFrom, "#all"));
        faces.add("south", face(x1, 16 - yTo, x1 + 1, 16 - yFrom, "#all"));
        faces.add("west", face(z1, 16 - yTo, z1 + 1, 16 - yFrom, "#all"));
        faces.add("east", face(16 - (z1 + 1), 16 - yTo, 16 - z1, 16 - yFrom, "#all"));
        faces.add("up", face(x1, z1, x1 + 1, z1 + 1, "#all"));
        faces.add("down", face(x1, 16 - (z1 + 1), x1 + 1, 16 - z1, "#all"));
        element.add("faces", faces);
        return element;
    }

    private static JsonObject createBox(String name, int x1, int y1, int z1, int x2, int y2, int z2) {
        JsonObject element = new JsonObject();
        element.addProperty("name", name);
        element.add("from", vec3(x1, y1, z1));
        element.add("to", vec3(x2, y2, z2));

        JsonObject faces = new JsonObject();
        faces.add("north", face(16 - x2, 16 - y2, 16 - x1, 16 - y1, "#all"));
        faces.add("south", face(x1, 16 - y2, x2, 16 - y1, "#all"));
        faces.add("west", face(z1, 16 - y2, z2, 16 - y1, "#all"));
        faces.add("east", face(16 - z2, 16 - y2, 16 - z1, 16 - y1, "#all"));
        faces.add("up", face(x1, z1, x2, z2, "#all"));
        faces.add("down", face(x1, 16 - z2, x2, 16 - z1, "#all"));
        element.add("faces", faces);

        return element;
    }
}
