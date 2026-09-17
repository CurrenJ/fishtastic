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
 * Frame generator for the lattice tank (image {@code proposed_shapes_6_wide_modified.png} tank slot
 * 4, 0-indexed): a 1px edge on every row (2px at the very top/bottom rows, matching the sand's 2px
 * inset) plus a pair of diagonal 1px points per row that walk inward from the edge toward the
 * center, crossing at rows 7-8.
 *
 * <p>Row transcription (image rows 1-14, {@code F} runs as {@code [x1,x2)} spans, edges implicit on
 * every row except where noted):
 * <pre>
 * row01  edges w2: [0,2) [14,16)                                (no diagonal — cap row)
 * row02  edges w1: [0,1) [15,16)   diag [2,3)  [13,14)
 * row03  edges w1                  diag [3,4)  [12,13)
 * row04  edges w1                  diag [4,5)  [11,12)
 * row05  edges w1                  diag [5,6)  [10,11)
 * row06  edges w1                  diag [6,7)  [9,10)
 * row07  edges w1                  diag [7,9)                    (crossing, merged with row08)
 * row08  edges w1                  diag [7,9)                    (== row07)
 * row09  edges w1                  diag [6,7)  [9,10)             (== row06)
 * row10  edges w1                  diag [5,6)  [10,11)            (== row05)
 * row11  edges w1                  diag [4,5)  [11,12)            (== row04)
 * row12  edges w1                  diag [3,4)  [12,13)            (== row03)
 * row13  edges w1                  diag [2,3)  [13,14)            (== row02)
 * row14  edges w2: [0,2) [14,16)                                (no diagonal — sand row, inset 2)
 * </pre>
 *
 * <p>Every element is face-local (gated only by that face's own open/closed state, like
 * {@link OrnateFrameGeometryGenerator}'s brackets) — no special connection behavior was requested
 * for lattice, so this follows the shipped ornate-family convention directly. The two cap-adjacent
 * rows (1 and 14) extend to the block boundary when the corresponding cap is open, the same
 * seam-extension every other generator applies.
 */
public final class LatticeFrameGeometryGenerator {
    public static final String DEFAULT_TEXTURE = "block/oak_planks";

    private record Row(int yFrom, int yTo, int[][] diag) {}

    // Rows 2-13 (interior, edge width 1, never touch a cap boundary).
    private static final List<Row> ROWS = List.of(
            new Row(13, 14, new int[][]{{2, 3}, {13, 14}}),   // row02
            new Row(12, 13, new int[][]{{3, 4}, {12, 13}}),   // row03
            new Row(11, 12, new int[][]{{4, 5}, {11, 12}}),   // row04
            new Row(10, 11, new int[][]{{5, 6}, {10, 11}}),   // row05
            new Row(9, 10, new int[][]{{6, 7}, {9, 10}}),     // row06
            new Row(7, 9, new int[][]{{7, 9}}),                // rows07-08 merged
            new Row(6, 7, new int[][]{{6, 7}, {9, 10}}),      // row09
            new Row(5, 6, new int[][]{{5, 6}, {10, 11}}),     // row10
            new Row(4, 5, new int[][]{{4, 5}, {11, 12}}),     // row11
            new Row(3, 4, new int[][]{{3, 4}, {12, 13}}),     // row12
            new Row(2, 3, new int[][]{{2, 3}, {13, 14}})      // row13
    );

    /** The cap rows' edge width (rows 1/14) — constant regardless of whether the adjacent cap is
     * open, unlike every tapered/shell shape's cap-adjacent row (see {@link #generateEdgeFragment}). */
    private static final int CAP_EDGE_WIDTH = 2;

    private LatticeFrameGeometryGenerator() {}

    public static JsonObject generate(int permutationIndex) {
        return generate(permutationIndex, DEFAULT_TEXTURE);
    }

    public static JsonObject generate(int permutationIndex, String textureId) {
        Set<TankFace> openFaces = TankFace.fromPermutationIndex(permutationIndex);
        boolean upOpen = openFaces.contains(TankFace.UP);
        boolean downOpen = openFaces.contains(TankFace.DOWN);

        JsonObject model = baseModel(textureId);
        JsonArray elements = new JsonArray();

        if (!upOpen) elements.add(createCap("ceiling", 15, 16, openFaces, true));
        if (!downOpen) elements.add(createCap("floor", 0, 1, openFaces, false));

        // row01: 2px edge, seam-extended to y=16 when UP is open.
        addEdge(elements, openFaces, 14, upOpen ? 16 : 15, "top");
        // rows 2-13: 1px edge + diagonal.
        for (Row row : ROWS) {
            addEdge(elements, openFaces, row.yFrom(), row.yTo(), "mid_" + row.yFrom());
            addDiag(elements, openFaces, row.yFrom(), row.yTo(), row.diag());
        }
        // row14: 2px edge, seam-extended to y=0 when DOWN is open.
        addEdge(elements, openFaces, downOpen ? 0 : 1, 2, "bottom");

        model.add("elements", elements);
        addSingleGroup(model, "frame_" + permutationIndex);
        return model;
    }

    /**
     * A standalone edge-diagonal frame-beam fragment for {@code edge} — see the edge-diagonal frame
     * beam fix design doc and {@code docs/tank-shapes/edge-diagonal-fix-remaining-shapes.md}'s
     * lattice writeup. Unlike {@link TaperedFrameGeometryGenerator#generateEdgeFragment}, which
     * derives its box width from {@link CornerTaperProfile#baseWidth()} (the width a tapered
     * corner post narrows <em>to</em> once its cap opens), lattice's cap rows never narrow at all —
     * {@link #addEdge} always uses {@link #CAP_EDGE_WIDTH} regardless of {@code upOpen}/{@code
     * downOpen} — so this uses that same constant width directly instead of borrowing a profile.
     *
     * <p>The matching glass-fill fragment ({@link LatticeGlassGeometryGenerator#generateEdgeGlassFillFragment})
     * is an empty no-op (unlike the tapered/comb families): {@link LatticeGlassGeometryGenerator}'s
     * cap-row bands already exclude the {@code [0,width)}/{@code [16-width,16)} corner columns from
     * glass <em>unconditionally</em> — regardless of whether the perpendicular face is open — since
     * {@link #addEdge} likewise renders both of a closed face's corner columns unconditionally (see
     * this class's javadoc). So the beam's full-width reach never overlaps real glass at any
     * permutation, and there is no cap-band sliver to restore — but the fragment model must still be
     * emitted, because the runtime loads one for every shape that has edge fragments.
     */
    public static JsonObject generateEdgeFragment(TankEdge edge) {
        return generateEdgeFragment(edge, DEFAULT_TEXTURE);
    }

    public static JsonObject generateEdgeFragment(TankEdge edge, String textureId) {
        JsonObject model = baseModel(textureId);
        JsonArray elements = new JsonArray();
        elements.add(createEdgeBeamBox(edge, CAP_EDGE_WIDTH));
        model.add("elements", elements);
        addSingleGroup(model, "frame_edge_" + edge.name().toLowerCase());
        return model;
    }

    /** Mirrors {@code TaperedFrameGeometryGenerator#createEdgeBeamBox} exactly — see its note. */
    private static JsonObject createEdgeBeamBox(TankEdge edge, int width) {
        double y1 = edge.vertical() == TankFace.DOWN ? 0 : 16 - width;
        double y2 = edge.vertical() == TankFace.DOWN ? width : 16;

        double x1, x2, z1, z2;
        switch (edge.horizontal()) {
            case NORTH -> { x1 = 0; x2 = 16; z1 = 0; z2 = width; }
            case SOUTH -> { x1 = 0; x2 = 16; z1 = 16 - width; z2 = 16; }
            case WEST -> { x1 = 0; x2 = width; z1 = 0; z2 = 16; }
            case EAST -> { x1 = 16 - width; x2 = 16; z1 = 0; z2 = 16; }
            default -> throw new IllegalArgumentException("Not a horizontal face: " + edge.horizontal());
        }

        JsonObject element = new JsonObject();
        element.addProperty("name", "edge_" + edge.name().toLowerCase());
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

    /** Adds the row's edge columns — width 2 for the cap rows (y span 1 unit tall touching a cap),
     * width 1 otherwise, inferred from the y-span identity used by the two callers above. */
    private static void addEdge(JsonArray elements, Set<TankFace> openFaces, int y1, int y2, String tag) {
        boolean wide = tag.equals("top") || tag.equals("bottom");
        int w = wide ? CAP_EDGE_WIDTH : 1;
        boolean northClosed = !openFaces.contains(TankFace.NORTH);
        boolean southClosed = !openFaces.contains(TankFace.SOUTH);
        boolean westClosed = !openFaces.contains(TankFace.WEST);
        boolean eastClosed = !openFaces.contains(TankFace.EAST);

        if (northClosed) {
            elements.add(createBox("lat_n_e0_" + tag, 0, y1, 0, w, y2, 1));
            elements.add(createBox("lat_n_e15_" + tag, 16 - w, y1, 0, 16, y2, 1));
        }
        if (southClosed) {
            elements.add(createBox("lat_s_e0_" + tag, 0, y1, 15, w, y2, 16));
            elements.add(createBox("lat_s_e15_" + tag, 16 - w, y1, 15, 16, y2, 16));
        }
        if (westClosed) {
            elements.add(createBox("lat_w_e0_" + tag, 0, y1, 0, 1, y2, w));
            elements.add(createBox("lat_w_e15_" + tag, 0, y1, 16 - w, 1, y2, 16));
        }
        if (eastClosed) {
            elements.add(createBox("lat_e_e0_" + tag, 15, y1, 0, 16, y2, w));
            elements.add(createBox("lat_e_e15_" + tag, 15, y1, 16 - w, 16, y2, 16));
        }

        // At w=2 (the cap rows) the two 1px-thick perpendicular edge plates meeting at a corner only
        // cover an L-shape, leaving the diagonal cell bare — fill it explicitly, same fix as
        // ArchFrameGeometryGenerator's addFaceWide. Not needed at w=1: an L of two 1x1 plates already
        // is the full square.
        if (wide) {
            if (northClosed && westClosed) elements.add(createBox("lat_corner_nw_" + tag, 1, y1, 1, 2, y2, 2));
            if (northClosed && eastClosed) elements.add(createBox("lat_corner_ne_" + tag, 14, y1, 1, 15, y2, 2));
            if (southClosed && westClosed) elements.add(createBox("lat_corner_sw_" + tag, 1, y1, 14, 2, y2, 15));
            if (southClosed && eastClosed) elements.add(createBox("lat_corner_se_" + tag, 14, y1, 14, 15, y2, 15));
        }
    }

    private static void addDiag(JsonArray elements, Set<TankFace> openFaces, int y1, int y2, int[][] diag) {
        boolean northClosed = !openFaces.contains(TankFace.NORTH);
        boolean southClosed = !openFaces.contains(TankFace.SOUTH);
        boolean westClosed = !openFaces.contains(TankFace.WEST);
        boolean eastClosed = !openFaces.contains(TankFace.EAST);

        for (int[] span : diag) {
            if (northClosed) elements.add(createBox("lat_n_d_" + y1 + "_" + span[0], span[0], y1, 0, span[1], y2, 1));
            if (southClosed) elements.add(createBox("lat_s_d_" + y1 + "_" + span[0], span[0], y1, 15, span[1], y2, 16));
            if (westClosed) elements.add(createBox("lat_w_d_" + y1 + "_" + span[0], 0, y1, span[0], 1, y2, span[1]));
            if (eastClosed) elements.add(createBox("lat_e_d_" + y1 + "_" + span[0], 15, y1, span[0], 16, y2, span[1]));
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
