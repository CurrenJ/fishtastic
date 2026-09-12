package grill24.fishtastic.shapegen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Set;

import static grill24.fishtastic.shapegen.TankShapeGeometry.addSingleGroup;
import static grill24.fishtastic.shapegen.TankShapeGeometry.baseModel;
import static grill24.fishtastic.shapegen.TankShapeGeometry.face;
import static grill24.fishtastic.shapegen.TankShapeGeometry.vec3;

/**
 * Frame generator for the creeper tank: a standard 1px corner-post + ceiling/floor frame (byte
 * identical to STANDARD's own), plus a classic 8x8 creeper-face inlay embedded 1px deep into the
 * glass, centered on each of the four side faces.
 *
 * <p>Built on the same construction as {@link OrnateFrameGeometryGenerator} (standard supports plus
 * face-local inlay boxes), just with a different pixel pattern occupying the face's center instead
 * of its top/bottom edges. The pattern was read pixel-exactly off Mojang's own {@code creeper.png}
 * (front face of the head, an 8x8 region), thresholded to separate the dark eyes/nose/mouth from the
 * mottled green skin — not eyeballed or invented:
 *
 * <pre>
 * . . . . . . . .
 * . . . . . . . .
 * . X X . . X X .
 * . X X . . X X .
 * . . . X X . . .
 * . . X X X X . .
 * . . X X X X . .
 * . . X . . X . .
 * </pre>
 *
 * <p>Horizontally centered in the 14-wide interior (x 1..15, 3px margin each side). Vertically it's
 * the centered position (3px above/below, y 4..12) shifted up 1px per request (y 5..13 — 2px margin
 * above the eyes, 4px below the chin dimples).
 *
 * <p>Consecutive rows sharing identical spans are merged into single boxes (matching the run-merge
 * behavior every other generator gets from {@code CornerTaperProfile.runs()}), collapsing the 8
 * pixel rows (2 of which are blank) into 4 boxes per face. The inlay is gated to the face it
 * decorates (no creeper face on an open — connected — face) exactly like corner posts, and only
 * exists at all since it sits on the standard body's flat mid-section, independent of DOWN/UP.
 */
public final class CreeperFrameGeometryGenerator {
    public static final String DEFAULT_TEXTURE = "block/oak_planks";

    // Creeper-face X spans (exclusive [x1,x2)) per Y band, top to bottom, already offset into the
    // face-local coordinate space (base offset 4 = (14 - 8) / 2 + 1, then +1 to shift the whole face
    // up 1px). The two blank rows above the eyes (image rows 0-1) contribute no boxes at all.
    private static final int[] FACE_Y_FROM = {9, 8, 6, 5};
    private static final int[] FACE_Y_TO = {11, 9, 8, 6};
    private static final int[][][] FACE_SPANS = {
            // Y 9-11: eyes, two rows merged -> ".XX..XX." offset
            {{5, 7}, {9, 11}},
            // Y 8-9: nose bridge
            {{7, 9}},
            // Y 6-8: mouth, two rows merged
            {{6, 10}},
            // Y 5-6: chin dimples
            {{6, 7}, {9, 10}}
    };

    private CreeperFrameGeometryGenerator() {}

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

        for (int i = 0; i < FACE_SPANS.length; i++) {
            int yFrom = FACE_Y_FROM[i];
            int yTo = FACE_Y_TO[i];
            for (int[] span : FACE_SPANS[i]) {
                addFaceInlay(elements, openFaces, span[0], yFrom, span[1], yTo);
            }
        }

        model.add("elements", elements);
        addSingleGroup(model, "frame_" + permutationIndex);
        return model;
    }

    /** Adds the creeper-face inlay span {@code [x1,x2]} at Y {@code [y1,y2]} on each closed face. */
    private static void addFaceInlay(JsonArray elements, Set<TankFace> openFaces, int x1, int y1, int x2, int y2) {
        if (!openFaces.contains(TankFace.NORTH)) {
            elements.add(createBox("creeper_n_" + x1 + "_" + y1, x1, y1, 0, x2, y2, 1));
        }
        if (!openFaces.contains(TankFace.SOUTH)) {
            elements.add(createBox("creeper_s_" + x1 + "_" + y1, x1, y1, 15, x2, y2, 16));
        }
        if (!openFaces.contains(TankFace.WEST)) {
            elements.add(createBox("creeper_w_" + x1 + "_" + y1, 0, y1, x1, 1, y2, x2));
        }
        if (!openFaces.contains(TankFace.EAST)) {
            elements.add(createBox("creeper_e_" + x1 + "_" + y1, 15, y1, x1, 16, y2, x2));
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
