package grill24.fishtastic.shapegen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Set;

import static grill24.fishtastic.shapegen.TankShapeGeometry.addSingleGroup;
import static grill24.fishtastic.shapegen.TankShapeGeometry.baseModel;
import static grill24.fishtastic.shapegen.TankShapeGeometry.face;
import static grill24.fishtastic.shapegen.TankShapeGeometry.smartLabel;
import static grill24.fishtastic.shapegen.TankShapeGeometry.vec3;

/**
 * Frame generator for corner-tapered shapes (image-derived — see the
 * {@code tank-shape-image-to-datagen} skill): ceiling/floor are the fixed 1px caps shared by
 * every shape, but each corner post is built from a {@link CornerTaperProfile} instead of a
 * single constant-thickness box.
 */
public final class TaperedFrameGeometryGenerator {
    public static final String DEFAULT_TEXTURE = "block/oak_planks";

    private TaperedFrameGeometryGenerator() {}

    public static JsonObject generate(int permutationIndex, CornerTaperProfile profile) {
        return generate(permutationIndex, DEFAULT_TEXTURE, profile);
    }

    public static JsonObject generate(int permutationIndex, String textureId, CornerTaperProfile profile) {
        Set<TankFace> openFaces = TankFace.fromPermutationIndex(permutationIndex);

        JsonObject model = baseModel(textureId);
        JsonArray elements = new JsonArray();

        if (!openFaces.contains(TankFace.UP)) {
            elements.add(createCeiling(openFaces));
        }
        if (!openFaces.contains(TankFace.DOWN)) {
            elements.add(createFloor(openFaces));
        }

        boolean ceilingClosed = !openFaces.contains(TankFace.UP);
        boolean floorClosed = !openFaces.contains(TankFace.DOWN);
        List<CornerTaperProfile.Run> runs = profile.runs(ceilingClosed, floorClosed);

        if (!openFaces.contains(TankFace.NORTH) && !openFaces.contains(TankFace.WEST)) {
            addTaperedSupport(elements, 0, 0, runs);      // NW corner
        }
        if (!openFaces.contains(TankFace.NORTH) && !openFaces.contains(TankFace.EAST)) {
            addTaperedSupport(elements, 1, 0, runs);      // NE corner
        }
        if (!openFaces.contains(TankFace.SOUTH) && !openFaces.contains(TankFace.WEST)) {
            addTaperedSupport(elements, 0, 1, runs);      // SW corner
        }
        if (!openFaces.contains(TankFace.SOUTH) && !openFaces.contains(TankFace.EAST)) {
            addTaperedSupport(elements, 1, 1, runs);      // SE corner
        }

        model.add("elements", elements);
        addSingleGroup(model, "frame_" + permutationIndex);
        return model;
    }

    private static JsonObject createCeiling(Set<TankFace> openFaces) {
        JsonObject element = new JsonObject();
        element.addProperty("name", "ceiling");
        element.add("from", vec3(0, 15, 0));
        element.add("to", vec3(16, 16, 16));

        JsonObject faces = new JsonObject();
        if (!openFaces.contains(TankFace.NORTH)) faces.add("north", face(0, 0, 16, 1, "#all"));
        if (!openFaces.contains(TankFace.EAST)) faces.add("east", face(0, 0, 16, 1, "#all"));
        if (!openFaces.contains(TankFace.SOUTH)) faces.add("south", face(0, 0, 16, 1, "#all"));
        if (!openFaces.contains(TankFace.WEST)) faces.add("west", face(0, 0, 16, 1, "#all"));
        faces.add("up", face(0, 0, 16, 16, "#all"));
        faces.add("down", face(0, 0, 16, 16, "#all"));
        element.add("faces", faces);
        return element;
    }

    private static JsonObject createFloor(Set<TankFace> openFaces) {
        JsonObject element = new JsonObject();
        element.addProperty("name", "floor");
        element.add("from", vec3(0, 0, 0));
        element.add("to", vec3(16, 1, 16));

        JsonObject faces = new JsonObject();
        if (!openFaces.contains(TankFace.NORTH)) faces.add("north", face(0, 0, 16, 1, "#all"));
        if (!openFaces.contains(TankFace.EAST)) faces.add("east", face(0, 0, 16, 1, "#all"));
        if (!openFaces.contains(TankFace.SOUTH)) faces.add("south", face(0, 0, 16, 1, "#all"));
        if (!openFaces.contains(TankFace.WEST)) faces.add("west", face(0, 0, 16, 1, "#all"));
        faces.add("up", face(0, 0, 16, 16, "#all"));
        faces.add("down", face(0, 0, 16, 16, "#all"));
        element.add("faces", faces);
        return element;
    }

    /**
     * Adds one box per taper run for a given corner. Every run stays flush at the same true
     * corner ({@code cornerX}/{@code cornerZ}: 0 = west/north edge, 1 = east/south edge) and only
     * its reach-in changes, so consecutive runs of different widths always touch with no gap —
     * no bridging shelf element is needed between them.
     */
    private static void addTaperedSupport(JsonArray elements, int cornerX, int cornerZ, List<CornerTaperProfile.Run> runs) {
        for (CornerTaperProfile.Run run : runs) {
            elements.add(createSupportBox(cornerX, cornerZ, run.yFrom(), run.yTo(), run.width()));
        }
    }

    private static JsonObject createSupportBox(int cornerX, int cornerZ, double minY, double maxY, int width) {
        double x1 = cornerX == 0 ? 0 : 16 - width;
        double x2 = x1 + width;
        double z1 = cornerZ == 0 ? 0 : 16 - width;
        double z2 = z1 + width;

        JsonObject element = new JsonObject();
        element.addProperty("name", "support_" + smartLabel(x1) + "_" + smartLabel(z1) + "_" + smartLabel(minY));
        element.add("from", vec3(x1, minY, z1));
        element.add("to", vec3(x2, maxY, z2));

        JsonObject faces = new JsonObject();
        double heightUV = maxY - minY;

        // Render all faces — these supports only exist when both adjacent faces are closed, so
        // they won't conflict with glass, and a narrower band's "up"/"down" face doubles as the
        // shelf where a wider neighboring band doesn't cover it.
        faces.add("north", face(0, 0, width, heightUV, "#all"));
        faces.add("south", face(0, 0, width, heightUV, "#all"));
        faces.add("west", face(0, 0, width, heightUV, "#all"));
        faces.add("east", face(0, 0, width, heightUV, "#all"));
        faces.add("up", face(0, 0, width, width, "#all"));
        faces.add("down", face(0, 0, width, width, "#all"));

        element.add("faces", faces);
        return element;
    }
}
