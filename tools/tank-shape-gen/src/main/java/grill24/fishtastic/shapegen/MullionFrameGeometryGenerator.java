package grill24.fishtastic.shapegen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Set;

import static grill24.fishtastic.shapegen.TankShapeGeometry.addSingleGroup;
import static grill24.fishtastic.shapegen.TankShapeGeometry.baseModel;
import static grill24.fishtastic.shapegen.TankShapeGeometry.face;
import static grill24.fishtastic.shapegen.TankShapeGeometry.vec3;

/**
 * Frame generator for the mullion tank (image {@code proposed_shapes_6_wide_modified.png} tank slot
 * 3, 0-indexed): a standard 1px frame plus three interior "mullion" bars per face at local
 * {@code x = 4, 8, 12}, running the full wall height (rows 1-14, including through the sand row —
 * row 14 is {@code FSSSFSSSFSSSFSSF}).
 *
 * <p>Bars sit on a period-4 lattice anchored at local {@code x = 0}: {0, 4, 8, 12}. The bar at
 * {@code x = 0} is a <b>persistent</b> part of the pattern — it renders whenever this face itself is
 * closed, regardless of the adjacent (west-for-north/south, north-for-west/east) face's open state.
 * The far edge at local {@code x = 15} is an <em>ordinary</em> wall/corner post: it renders only when
 * this face AND its adjacent high-side face (east-for-north/south, south-for-west/east) are both
 * closed — i.e. it behaves like a normal corner post, not a bar.
 *
 * <p>This asymmetry (anchor low edge, ordinary high edge) is what keeps the 3px bar spacing exact
 * across a horizontal connection: when the high-side face opens, its wall disappears and the
 * neighbor's own {@code x=0} anchor bar (unaffected by the connection) supplies the next bar 3px
 * away. See {@code MullionGlassGeometryGenerator} for the matching pane holes, and the shipping
 * report for the round-trip verification against both the standalone and 3-tank-connected targets.
 * The standalone pattern is deliberately not mirror-symmetric (panes {@code [3,3,3,2]}, not
 * {@code [3,3,3,3]}) — a 1px-wide bar on a period-4 lattice has no placement that's simultaneously
 * mirror-symmetric and seam-consistent, and evenness across connections was the chosen tradeoff.
 */
public final class MullionFrameGeometryGenerator {
    public static final String DEFAULT_TEXTURE = "block/oak_planks";

    static final int[] BAR_X = {4, 8, 12};

    private MullionFrameGeometryGenerator() {}

    public static JsonObject generate(int permutationIndex) {
        return generate(permutationIndex, DEFAULT_TEXTURE);
    }

    public static JsonObject generate(int permutationIndex, String textureId) {
        Set<TankFace> openFaces = TankFace.fromPermutationIndex(permutationIndex);
        boolean upOpen = openFaces.contains(TankFace.UP);
        boolean downOpen = openFaces.contains(TankFace.DOWN);
        int yFrom = downOpen ? 0 : 1;
        int yTo = upOpen ? 16 : 15;

        JsonObject model = baseModel(textureId);
        JsonArray elements = new JsonArray();

        if (!upOpen) elements.add(createCap("ceiling", 15, 16, openFaces, true));
        if (!downOpen) elements.add(createCap("floor", 0, 1, openFaces, false));

        boolean northClosed = !openFaces.contains(TankFace.NORTH);
        boolean southClosed = !openFaces.contains(TankFace.SOUTH);
        boolean westClosed = !openFaces.contains(TankFace.WEST);
        boolean eastClosed = !openFaces.contains(TankFace.EAST);

        // North/south faces: local x runs west(0) -> east(16). Anchor = west edge (x=0, persistent),
        // ordinary = east edge (x=15, gated additionally by EAST closed).
        if (northClosed) {
            elements.add(createBox("mullion_n_anchor", 0, yFrom, 0, 1, yTo, 1));
            if (eastClosed) elements.add(createBox("mullion_n_wall", 15, yFrom, 0, 16, yTo, 1));
            for (int bx : BAR_X) elements.add(createBox("mullion_n_bar_" + bx, bx, yFrom, 0, bx + 1, yTo, 1));
        }
        if (southClosed) {
            elements.add(createBox("mullion_s_anchor", 0, yFrom, 15, 1, yTo, 16));
            if (eastClosed) elements.add(createBox("mullion_s_wall", 15, yFrom, 15, 16, yTo, 16));
            for (int bx : BAR_X) elements.add(createBox("mullion_s_bar_" + bx, bx, yFrom, 15, bx + 1, yTo, 16));
        }
        // West/east faces: local z runs north(0) -> south(16). Anchor = north edge (z=0, persistent),
        // ordinary = south edge (z=15, gated additionally by SOUTH closed).
        if (westClosed) {
            elements.add(createBox("mullion_w_anchor", 0, yFrom, 0, 1, yTo, 1));
            if (southClosed) elements.add(createBox("mullion_w_wall", 0, yFrom, 15, 1, yTo, 16));
            for (int bz : BAR_X) elements.add(createBox("mullion_w_bar_" + bz, 0, yFrom, bz, 1, yTo, bz + 1));
        }
        if (eastClosed) {
            elements.add(createBox("mullion_e_anchor", 15, yFrom, 0, 16, yTo, 1));
            if (southClosed) elements.add(createBox("mullion_e_wall", 15, yFrom, 15, 16, yTo, 16));
            for (int bz : BAR_X) elements.add(createBox("mullion_e_bar_" + bz, 15, yFrom, bz, 16, yTo, bz + 1));
        }

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
