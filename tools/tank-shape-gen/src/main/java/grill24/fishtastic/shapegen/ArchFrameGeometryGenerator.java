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
 * Frame generator for the arch tank — a window-arch motif on each face, built from the bands in
 * {@link ArchTankSpans} (which carries the pixel transcription and the reasoning behind the two
 * different gating rules).
 *
 * <p>In short: each wall's <b>jamb</b> is an ordinary corner post, gated by the perpendicular face
 * at that end of the wall, while the <b>arc</b> is drawn regardless of the perpendicular faces so
 * connected tanks form a continuous arcade. This replaces the earlier purely face-local gating,
 * which kept a full jamb standing on both ends of every closed wall and so read as a doubled 2px
 * pillar at each horizontal seam instead of an open archway.
 */
public final class ArchFrameGeometryGenerator {
    public static final String DEFAULT_TEXTURE = "block/oak_planks";

    private ArchFrameGeometryGenerator() {}

    public static JsonObject generate(int permutationIndex) {
        return generate(permutationIndex, DEFAULT_TEXTURE);
    }

    public static JsonObject generate(int permutationIndex, String textureId) {
        Set<TankFace> openFaces = TankFace.fromPermutationIndex(permutationIndex);
        boolean upOpen = openFaces.contains(TankFace.UP);
        boolean downOpen = openFaces.contains(TankFace.DOWN);
        boolean northOpen = openFaces.contains(TankFace.NORTH);
        boolean southOpen = openFaces.contains(TankFace.SOUTH);
        boolean westOpen = openFaces.contains(TankFace.WEST);
        boolean eastOpen = openFaces.contains(TankFace.EAST);

        JsonObject model = baseModel(textureId);
        JsonArray elements = new JsonArray();

        if (!upOpen) elements.add(createCap("ceiling", 15, 16, openFaces, true));
        if (!downOpen) elements.add(createCap("floor", 0, 1, openFaces, false));

        // North/south walls run along X and take their ends from WEST/EAST; west/east walls run
        // along Z and take theirs from NORTH/SOUTH.
        List<ArchTankSpans.Band> alongX = ArchTankSpans.bands(westOpen, eastOpen, upOpen, downOpen);
        List<ArchTankSpans.Band> alongZ = ArchTankSpans.bands(northOpen, southOpen, upOpen, downOpen);

        if (!northOpen) {
            for (ArchTankSpans.Band band : alongX) {
                for (int[] span : band.spans()) {
                    elements.add(createBox("arch_n_" + band.yFrom() + "_" + span[0],
                            span[0], band.yFrom(), 0, span[1], band.yTo(), 1));
                }
            }
        }
        if (!southOpen) {
            for (ArchTankSpans.Band band : alongX) {
                for (int[] span : band.spans()) {
                    elements.add(createBox("arch_s_" + band.yFrom() + "_" + span[0],
                            span[0], band.yFrom(), 15, span[1], band.yTo(), 16));
                }
            }
        }
        if (!westOpen) {
            for (ArchTankSpans.Band band : alongZ) {
                for (int[] span : band.spans()) {
                    elements.add(createBox("arch_w_" + band.yFrom() + "_" + span[0],
                            0, band.yFrom(), span[0], 1, band.yTo(), span[1]));
                }
            }
        }
        if (!eastOpen) {
            for (ArchTankSpans.Band band : alongZ) {
                for (int[] span : band.spans()) {
                    elements.add(createBox("arch_e_" + band.yFrom() + "_" + span[0],
                            15, band.yFrom(), span[0], 16, band.yTo(), span[1]));
                }
            }
        }

        // Two perpendicular 2px jambs meeting at a corner (e.g. north's x[0,2) z[0,1) and west's
        // x[0,1) z[0,2)) only cover an L-shape, leaving the diagonal cell — x[1,2) z[1,2) for NW —
        // bare. Both jambs exist exactly when both bordering faces are closed, so fill it then.
        // (Caught by TankShapeConnectivitySafetyTest's floor-coverage sweep.)
        int wideFrom = downOpen ? 0 : 1;
        int wideTo = ArchTankSpans.WIDE_JAMB_Y_TO;
        if (!northOpen && !westOpen) elements.add(createBox("arch_corner_nw", 1, wideFrom, 1, 2, wideTo, 2));
        if (!northOpen && !eastOpen) elements.add(createBox("arch_corner_ne", 14, wideFrom, 1, 15, wideTo, 2));
        if (!southOpen && !westOpen) elements.add(createBox("arch_corner_sw", 1, wideFrom, 14, 2, wideTo, 15));
        if (!southOpen && !eastOpen) elements.add(createBox("arch_corner_se", 14, wideFrom, 14, 15, wideTo, 15));

        model.add("elements", elements);
        addSingleGroup(model, "frame_" + permutationIndex);
        return model;
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
