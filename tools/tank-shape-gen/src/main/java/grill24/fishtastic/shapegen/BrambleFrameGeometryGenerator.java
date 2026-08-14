package grill24.fishtastic.shapegen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Set;

import static grill24.fishtastic.shapegen.TankShapeGeometry.addSingleGroup;
import static grill24.fishtastic.shapegen.TankShapeGeometry.baseModel;
import static grill24.fishtastic.shapegen.TankShapeGeometry.face;
import static grill24.fishtastic.shapegen.TankShapeGeometry.vec3;

/**
 * Frame generator for the bramble tank (references {@code docs/tank-shapes/bramble_shape_*.png}):
 * unlike the ornate/shaggy family, there is no separate always-on 1px corner post — every one of
 * the 13 interior Y bands carries its own thorny, asymmetric inlay pattern that already includes
 * whatever corner-post-width pixels the reference shows at that height. Spans live in
 * {@link BrambleTankSpans}, shared with {@link BrambleGlassGeometryGenerator} so the inlays and the
 * glass holes behind them can't drift apart.
 *
 * <p>Gating, matching the ornate/shaggy tanks: an inlay on the north face exists only when NORTH is
 * closed. Within that, each band's spans split into a "low" run (near the west/north corner) and a
 * "high" run (near the east/south corner), independently gated by whether that corner's other face
 * is closed — see {@link BrambleTankSpans} for how this was read off the connection references.
 * Unlike shaggy there is no edge-cell merge: a gated run simply vanishes when its face opens, with
 * nothing added back, because (confirmed against the horizontal-connection reference) nothing needs
 * to fill the gap — the neighboring tank's own geometry does.
 */
public final class BrambleFrameGeometryGenerator {
    public static final String DEFAULT_TEXTURE = "block/oak_planks";

    private BrambleFrameGeometryGenerator() {}

    public static JsonObject generate(int permutationIndex) {
        return generate(permutationIndex, DEFAULT_TEXTURE);
    }

    public static JsonObject generate(int permutationIndex, String textureId) {
        Set<TankFace> openFaces = TankFace.fromPermutationIndex(permutationIndex);

        JsonObject model = baseModel(textureId);
        JsonArray elements = new JsonArray();

        boolean upOpen = openFaces.contains(TankFace.UP);
        boolean downOpen = openFaces.contains(TankFace.DOWN);

        if (!upOpen) {
            elements.add(createCap("ceiling", 15, 16, openFaces, true));
        }
        if (!downOpen) {
            elements.add(createCap("floor", 0, 1, openFaces, false));
        }

        for (BrambleTankSpans.Band band : BrambleTankSpans.MIDDLE) {
            addFaceInlays(elements, openFaces, band);
        }
        addFaceInlays(elements, openFaces, downOpen ? BrambleTankSpans.ROW14_OPEN : BrambleTankSpans.ROW14_CLOSED);
        if (upOpen) {
            addFaceInlays(elements, openFaces, BrambleTankSpans.TOP_OPEN);
        }
        if (downOpen) {
            addFaceInlays(elements, openFaces, BrambleTankSpans.BOTTOM_OPEN);
        }

        model.add("elements", elements);
        addSingleGroup(model, "frame_" + permutationIndex);
        return model;
    }

    /** Adds one band's low/high inlay spans as 1px plates on each closed face. */
    private static void addFaceInlays(JsonArray elements, Set<TankFace> openFaces, BrambleTankSpans.Band band) {
        boolean northClosed = !openFaces.contains(TankFace.NORTH);
        boolean southClosed = !openFaces.contains(TankFace.SOUTH);
        boolean westClosed = !openFaces.contains(TankFace.WEST);
        boolean eastClosed = !openFaces.contains(TankFace.EAST);

        int y1 = band.yFrom();
        int y2 = band.yTo();

        // North/south inlays run along X: the low run is west-gated, the high run east-gated.
        if (northClosed || southClosed) {
            if (westClosed) {
                for (int[] span : band.lowSpans()) {
                    if (northClosed) elements.add(createBox("inlay_n_" + y1 + "_" + span[0], span[0], y1, 0, span[1], y2, 1));
                    if (southClosed) elements.add(createBox("inlay_s_" + y1 + "_" + span[0], span[0], y1, 15, span[1], y2, 16));
                }
            }
            if (eastClosed) {
                for (int[] span : band.highSpans()) {
                    if (northClosed) elements.add(createBox("inlay_n_" + y1 + "_" + span[0], span[0], y1, 0, span[1], y2, 1));
                    if (southClosed) elements.add(createBox("inlay_s_" + y1 + "_" + span[0], span[0], y1, 15, span[1], y2, 16));
                }
            }
        }
        // West/east inlays run along Z: the low run is north-gated, the high run south-gated.
        if (westClosed || eastClosed) {
            if (northClosed) {
                for (int[] span : band.lowSpans()) {
                    if (westClosed) elements.add(createBox("inlay_w_" + y1 + "_" + span[0], 0, y1, span[0], 1, y2, span[1]));
                    if (eastClosed) elements.add(createBox("inlay_e_" + y1 + "_" + span[0], 15, y1, span[0], 16, y2, span[1]));
                }
            }
            if (southClosed) {
                for (int[] span : band.highSpans()) {
                    if (westClosed) elements.add(createBox("inlay_w_" + y1 + "_" + span[0], 0, y1, span[0], 1, y2, span[1]));
                    if (eastClosed) elements.add(createBox("inlay_e_" + y1 + "_" + span[0], 15, y1, span[0], 16, y2, span[1]));
                }
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
