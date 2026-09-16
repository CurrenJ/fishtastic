package grill24.fishtastic.shapegen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Set;

import static grill24.fishtastic.shapegen.TankShapeGeometry.baseModel;
import static grill24.fishtastic.shapegen.TankShapeGeometry.face;
import static grill24.fishtastic.shapegen.TankShapeGeometry.vec3;

/**
 * Glass generator for the arch tank — the counterpart to {@link ArchFrameGeometryGenerator}. One
 * pane segment per solid-span gap per band per closed face, taken as the complement of the exact
 * same {@link ArchTankSpans} bands the frame builds from, so the glass can never drift out of step
 * with the jamb/arc gating.
 *
 * <p>Because the complement is taken against the frame's <i>actual</i> spans for this permutation,
 * the horizontal seam extension falls out for free: when a jamb is gone because its perpendicular
 * face opened, that band's spans no longer reach the boundary and the pane extends to {@code 0} /
 * {@code 16} to take over its territory — except within an eligible edge-diagonal beam's own cap
 * band, where {@link ArchTankSpans#glassBands} deliberately withholds that extension (both jamb ends
 * treated as present there regardless of the real state) so the beam and the base glass bake never
 * double-cover the same cell; see {@link #generateEdgeGlassFillFragment} for the matching restore
 * used when the beam itself doesn't render.
 *
 * <p>Translucency invariant (as in {@link TaperedGlassGeometryGenerator}): only the two
 * thickness-axis faces are defined per segment, never up/down.
 */
public final class ArchGlassGeometryGenerator {
    public static final String DEFAULT_TEXTURE = "block/blue_stained_glass";

    private ArchGlassGeometryGenerator() {}

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

        List<ArchTankSpans.Band> alongX = ArchTankSpans.glassBands(westOpen, eastOpen, upOpen, downOpen);
        List<ArchTankSpans.Band> alongZ = ArchTankSpans.glassBands(northOpen, southOpen, upOpen, downOpen);

        if (!northOpen) {
            for (ArchTankSpans.Band b : alongX) {
                for (int[] gap : ArchTankSpans.complement(b.spans())) {
                    elements.add(pane(gap[0], b.yFrom(), 0, gap[1], b.yTo(), 1, "north", "south"));
                }
            }
        }
        if (!southOpen) {
            for (ArchTankSpans.Band b : alongX) {
                for (int[] gap : ArchTankSpans.complement(b.spans())) {
                    elements.add(pane(gap[0], b.yFrom(), 15, gap[1], b.yTo(), 16, "north", "south"));
                }
            }
        }
        if (!westOpen) {
            for (ArchTankSpans.Band b : alongZ) {
                for (int[] gap : ArchTankSpans.complement(b.spans())) {
                    elements.add(paneZAxis(0, b.yFrom(), gap[0], 1, b.yTo(), gap[1], "west", "east"));
                }
            }
        }
        if (!eastOpen) {
            for (ArchTankSpans.Band b : alongZ) {
                for (int[] gap : ArchTankSpans.complement(b.spans())) {
                    elements.add(paneZAxis(15, b.yFrom(), gap[0], 16, b.yTo(), gap[1], "west", "east"));
                }
            }
        }

        model.add("elements", elements);
        return model;
    }

    /**
     * A standalone glass-restore fragment for one end of an eligible edge-diagonal beam — the arch
     * counterpart to {@code TaperedGlassGeometryGenerator#generateEdgeGlassFillFragment}, restoring
     * exactly the sliver {@link ArchTankSpans#glassBands}' cap-band forcing withheld from the base
     * bake, at the wall named by {@code corner}'s non-{@code edge.horizontal()} face. A no-op (empty
     * model) makes no sense here — unlike mullion, every one of arch's 8 edges can collide with real
     * glass (the jamb's low <em>and</em> high ends are both ordinary gated corner posts, with no
     * always-present anchor side), so both of an edge's end corners can call this and both may need a
     * restore, provided that corner's wall face is actually closed (an open wall never had glass
     * there to begin with — see {@code TankShapeConnectivitySafetyTest#edgeDiagonalsHaveNoGapsWhenEdgeDiagonalFilled}'s
     * skip for that case).
     */
    public static JsonObject generateEdgeGlassFillFragment(TankEdge edge, TankCorner corner) {
        return generateEdgeGlassFillFragment(edge, corner, DEFAULT_TEXTURE);
    }

    public static JsonObject generateEdgeGlassFillFragment(TankEdge edge, TankCorner corner, String textureId) {
        JsonObject model = baseModel(textureId);
        JsonArray elements = new JsonArray();

        int width = edge.vertical() == TankFace.DOWN ? ArchTankSpans.NEAR_FLOOR_JAMB_WIDTH : ArchTankSpans.NEAR_CEILING_JAMB_WIDTH;
        int y1 = edge.vertical() == TankFace.DOWN ? 0 : 16 - width;
        int y2 = edge.vertical() == TankFace.DOWN ? width : 16;

        TankFace wall = corner.faceA() == edge.horizontal() ? corner.faceB() : corner.faceA();

        if (edge.horizontal() == TankFace.NORTH || edge.horizontal() == TankFace.SOUTH) {
            int z1 = edge.horizontal() == TankFace.NORTH ? 0 : 16 - width;
            int z2 = edge.horizontal() == TankFace.NORTH ? width : 16;
            switch (wall) {
                case WEST -> elements.add(paneZAxis(0, y1, z1, 1, y2, z2, "west", "east"));
                case EAST -> elements.add(paneZAxis(15, y1, z1, 16, y2, z2, "west", "east"));
                default -> throw new IllegalArgumentException("Not a wall face: " + wall);
            }
        } else {
            int x1 = edge.horizontal() == TankFace.WEST ? 0 : 16 - width;
            int x2 = edge.horizontal() == TankFace.WEST ? width : 16;
            switch (wall) {
                case NORTH -> elements.add(pane(x1, y1, 0, x2, y2, 1, "north", "south"));
                case SOUTH -> elements.add(pane(x1, y1, 15, x2, y2, 16, "north", "south"));
                default -> throw new IllegalArgumentException("Not a wall face: " + wall);
            }
        }

        model.add("elements", elements);
        return model;
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
