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

        // North/south's "wall" (local x=15, gated on EAST) is the one an EAST_UP/EAST_DOWN
        // edge-diagonal beam can collide with — see generateEdgeGlassFillFragment's note. Within the
        // beam's own capBand (its fixed 1px reach at the true cap boundary), the wall is treated as
        // closed regardless of EAST's real state, so the pane insets there instead of flushing into
        // the corner the beam now occupies.
        if (northClosed) {
            for (double[] seg : splitForCapBands(yFrom, yTo, upOpen, downOpen)) {
                int segYFrom = (int) seg[0], segYTo = (int) seg[1];
                if (segYFrom >= segYTo) continue;
                boolean capBand = seg[2] != 0;
                boolean wallClosed = eastClosed || (capBand && !eastClosed);
                for (int[] span : complement(wallClosed)) {
                    elements.add(pane(span[0], segYFrom, 0, span[1], segYTo, 1, "north", "south"));
                }
            }
        }
        if (southClosed) {
            for (double[] seg : splitForCapBands(yFrom, yTo, upOpen, downOpen)) {
                int segYFrom = (int) seg[0], segYTo = (int) seg[1];
                if (segYFrom >= segYTo) continue;
                boolean capBand = seg[2] != 0;
                boolean wallClosed = eastClosed || (capBand && !eastClosed);
                for (int[] span : complement(wallClosed)) {
                    elements.add(pane(span[0], segYFrom, 15, span[1], segYTo, 16, "north", "south"));
                }
            }
        }
        // West/east's "wall" (local z=15, gated on SOUTH) is the one a SOUTH_UP/SOUTH_DOWN
        // edge-diagonal beam can collide with — same capBand treatment, mirrored onto SOUTH instead.
        if (westClosed) {
            for (double[] seg : splitForCapBands(yFrom, yTo, upOpen, downOpen)) {
                int segYFrom = (int) seg[0], segYTo = (int) seg[1];
                if (segYFrom >= segYTo) continue;
                boolean capBand = seg[2] != 0;
                boolean wallClosed = southClosed || (capBand && !southClosed);
                for (int[] span : complement(wallClosed)) {
                    elements.add(paneZAxis(0, segYFrom, span[0], 1, segYTo, span[1], "west", "east"));
                }
            }
        }
        if (eastClosed) {
            for (double[] seg : splitForCapBands(yFrom, yTo, upOpen, downOpen)) {
                int segYFrom = (int) seg[0], segYTo = (int) seg[1];
                if (segYFrom >= segYTo) continue;
                boolean capBand = seg[2] != 0;
                boolean wallClosed = southClosed || (capBand && !southClosed);
                for (int[] span : complement(wallClosed)) {
                    elements.add(paneZAxis(15, segYFrom, span[0], 16, segYTo, span[1], "west", "east"));
                }
            }
        }

        model.add("elements", elements);
        return model;
    }

    /**
     * Splits {@code [yFrom,yTo]} at the edge-diagonal beam's fixed 1px capBand boundary (mirrors
     * {@code TaperedGlassGeometryGenerator#splitRunForCapBands}, fixed at width 1 since {@link
     * MullionFrameGeometryGenerator#generateEdgeFragment} always uses that width regardless of the
     * mullion pattern). Returned as {@code {yFrom, yTo, capBand(0/1)}} triples.
     */
    private static List<double[]> splitForCapBands(int yFrom, int yTo, boolean upOpen, boolean downOpen) {
        double downBandTo = downOpen ? 1 : -1;
        double upBandFrom = upOpen ? 15 : 17;

        java.util.TreeSet<Double> cuts = new java.util.TreeSet<>();
        cuts.add((double) yFrom);
        cuts.add((double) yTo);
        if (downBandTo > yFrom && downBandTo < yTo) cuts.add(downBandTo);
        if (upBandFrom > yFrom && upBandFrom < yTo) cuts.add(upBandFrom);

        List<Double> sorted = new ArrayList<>(cuts);
        List<double[]> result = new ArrayList<>();
        for (int i = 0; i < sorted.size() - 1; i++) {
            double lo = sorted.get(i), hi = sorted.get(i + 1);
            double mid = (lo + hi) / 2;
            boolean capBand = mid < downBandTo || mid > upBandFrom;
            result.add(new double[]{lo, hi, capBand ? 1 : 0});
        }
        return result;
    }

    /**
     * A standalone glass-restore fragment for one end of an eligible edge-diagonal beam — the
     * mullion counterpart to {@code TaperedGlassGeometryGenerator#generateEdgeGlassFillFragment}.
     * Empty for every edge except {@code edge.horizontal() == EAST}/{@code SOUTH}: those are the only
     * ones whose beam can collide with a real pane (the wall-gated corners — see {@link #generate}'s
     * note), so they're the only ones whose base bake ever needs a sliver restored when the
     * edge-diagonal neighbor cell IS filled (beam doesn't render). The anchor side (NORTH/WEST) never
     * insets in the first place, so there is nothing to restore there.
     */
    public static JsonObject generateEdgeGlassFillFragment(TankEdge edge, TankCorner corner) {
        return generateEdgeGlassFillFragment(edge, corner, DEFAULT_TEXTURE);
    }

    public static JsonObject generateEdgeGlassFillFragment(TankEdge edge, TankCorner corner, String textureId) {
        JsonObject model = baseModel(textureId);
        JsonArray elements = new JsonArray();

        if (edge.horizontal() == TankFace.EAST || edge.horizontal() == TankFace.SOUTH) {
            int width = 1;
            double y1 = edge.vertical() == TankFace.DOWN ? 0 : 16 - width;
            double y2 = edge.vertical() == TankFace.DOWN ? width : 16;

            TankFace wall = corner.faceA() == edge.horizontal() ? corner.faceB() : corner.faceA();

            switch (wall) {
                case NORTH -> elements.add(pane(15, (int) y1, 0, 16, (int) y2, 1, "north", "south"));
                case SOUTH -> elements.add(pane(15, (int) y1, 15, 16, (int) y2, 16, "north", "south"));
                case WEST -> elements.add(paneZAxis(0, (int) y1, 15, 1, (int) y2, 16, "west", "east"));
                case EAST -> elements.add(paneZAxis(15, (int) y1, 15, 16, (int) y2, 16, "west", "east"));
                default -> throw new IllegalArgumentException("Not a wall face: " + wall);
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
