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
 * {@code 16} to take over its territory.
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

        List<ArchTankSpans.Band> alongX = ArchTankSpans.bands(westOpen, eastOpen, upOpen, downOpen);
        List<ArchTankSpans.Band> alongZ = ArchTankSpans.bands(northOpen, southOpen, upOpen, downOpen);

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
