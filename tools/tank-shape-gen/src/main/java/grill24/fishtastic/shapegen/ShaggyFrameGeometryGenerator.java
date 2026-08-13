package grill24.fishtastic.shapegen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Set;

import static grill24.fishtastic.shapegen.TankShapeGeometry.addSingleGroup;
import static grill24.fishtastic.shapegen.TankShapeGeometry.baseModel;
import static grill24.fishtastic.shapegen.TankShapeGeometry.face;
import static grill24.fishtastic.shapegen.TankShapeGeometry.vec3;

/**
 * Frame generator for the shaggy tank (reference {@code docs/tank-shapes/shaggy_tank.png}):
 * the same construction as {@link OrnateFrameGeometryGenerator} — a standard 1px corner-post +
 * ceiling/floor frame plus 1px-thick decorative inlays inset into the glass layer — but with a
 * shaggier, deliberately asymmetric fringe, and a full-width band at Y {@code [1,2]} that hides the
 * sand from the side entirely.
 *
 * <p>Spans live in {@link ShaggyTankSpans}, shared with {@link ShaggyGlassGeometryGenerator} so the
 * inlays and the glass holes behind them can't drift apart.
 *
 * <p>Gating, matching the ornate tank: an inlay on the north face exists only when NORTH is closed;
 * the top fringe only when UP is closed and the bottom fringe only when DOWN is closed. Corner posts
 * additionally extend to the block boundary when their cap is open, so stacked tanks meet flush.
 */
public final class ShaggyFrameGeometryGenerator {
    public static final String DEFAULT_TEXTURE = "block/oak_planks";

    private ShaggyFrameGeometryGenerator() {}

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
        // block boundary when the adjacent cap is open, so stacked tanks meet flush at the seam.
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

        if (!openFaces.contains(TankFace.UP)) {
            for (ShaggyTankSpans.Band band : ShaggyTankSpans.TOP) {
                addFaceInlays(elements, openFaces, band);
            }
        }
        if (!openFaces.contains(TankFace.DOWN)) {
            for (ShaggyTankSpans.Band band : ShaggyTankSpans.BOTTOM) {
                addFaceInlays(elements, openFaces, band);
            }
        }

        model.add("elements", elements);
        addSingleGroup(model, "frame_" + permutationIndex);
        return model;
    }

    /** Adds one band's inlay spans as 1px plates on each closed face. */
    private static void addFaceInlays(JsonArray elements, Set<TankFace> openFaces, ShaggyTankSpans.Band band) {
        boolean northClosed = !openFaces.contains(TankFace.NORTH);
        boolean southClosed = !openFaces.contains(TankFace.SOUTH);
        boolean westClosed = !openFaces.contains(TankFace.WEST);
        boolean eastClosed = !openFaces.contains(TankFace.EAST);

        int y1 = band.yFrom();
        int y2 = band.yTo();

        // North/south inlays run along X, so their edge cells are gated by the west/east posts.
        if (northClosed || southClosed) {
            List<int[]> spans = ShaggyTankSpans.spansFor(band, !westClosed, !eastClosed);
            for (int[] span : spans) {
                if (northClosed) elements.add(createBox("inlay_n_" + y1 + "_" + span[0], span[0], y1, 0, span[1], y2, 1));
                if (southClosed) elements.add(createBox("inlay_s_" + y1 + "_" + span[0], span[0], y1, 15, span[1], y2, 16));
            }
        }
        // West/east inlays run along Z, gated by the north/south posts.
        if (westClosed || eastClosed) {
            List<int[]> spans = ShaggyTankSpans.spansFor(band, !northClosed, !southClosed);
            for (int[] span : spans) {
                if (westClosed) elements.add(createBox("inlay_w_" + y1 + "_" + span[0], 0, y1, span[0], 1, y2, span[1]));
                if (eastClosed) elements.add(createBox("inlay_e_" + y1 + "_" + span[0], 15, y1, span[0], 16, y2, span[1]));
            }
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
