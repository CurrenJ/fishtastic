package grill24.fishtastic.shapegen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Set;

import static grill24.fishtastic.shapegen.TankShapeGeometry.baseModel;
import static grill24.fishtastic.shapegen.TankShapeGeometry.face;
import static grill24.fishtastic.shapegen.TankShapeGeometry.vec3;

/**
 * Glass generator for corner-tapered shapes (image-derived — see the
 * {@code tank-shape-image-to-datagen} skill): each pane is split into one segment per
 * {@link CornerTaperProfile} run, trimmed by that run's width instead of a constant thickness.
 * The pane's depth position never changes — only its along-wall trim varies per row, so segments
 * stack directly with no gap.
 *
 * <p><b>Translucency invariant:</b> {@link #pane}/{@link #paneZAxis} must only ever define the
 * two opposing faces perpendicular to the pane's thickness axis (e.g. {@code north}+{@code south}
 * for a north/south wall) — never {@code up}/{@code down}. Segments stack along Y, so an
 * {@code up}/{@code down} face is exactly the plane where two segments touch; defining it on both
 * sides of that boundary would render two coincident translucent quads there, visibly darkening
 * that seam (alpha compounds) versus the rest of the pane. Leaving those faces undefined means
 * nothing renders at the seam at all, which is what's wanted — segments should read as one
 * continuous pane, not stacked slabs.
 */
public final class TaperedGlassGeometryGenerator {
    public static final String DEFAULT_TEXTURE = "block/blue_stained_glass";

    private TaperedGlassGeometryGenerator() {}

    public static JsonObject generate(int permutationIndex, CornerTaperProfile profile) {
        return generate(permutationIndex, DEFAULT_TEXTURE, profile);
    }

    public static JsonObject generate(int permutationIndex, String textureId, CornerTaperProfile profile) {
        Set<TankFace> openFaces = TankFace.fromPermutationIndex(permutationIndex);

        JsonObject model = baseModel(textureId);
        JsonArray elements = new JsonArray();

        List<CornerTaperProfile.Run> runs = profile.runs(!openFaces.contains(TankFace.UP), !openFaces.contains(TankFace.DOWN));

        if (!openFaces.contains(TankFace.NORTH)) {
            addNorthGlassPane(elements, openFaces, runs);
        }
        if (!openFaces.contains(TankFace.SOUTH)) {
            addSouthGlassPane(elements, openFaces, runs);
        }
        if (!openFaces.contains(TankFace.WEST)) {
            addWestGlassPane(elements, openFaces, runs);
        }
        if (!openFaces.contains(TankFace.EAST)) {
            addEastGlassPane(elements, openFaces, runs);
        }

        model.add("elements", elements);
        return model;
    }

    /**
     * Skylight glass: identical to {@link #generate(int, String, CornerTaperProfile)} plus a single
     * horizontal pane on the UP face when it's closed, filling the opening left by the skylight
     * frame ring (see {@code TaperedFrameGeometryGenerator#generateSkylight}). When UP is open the
     * side panes already extend to the block boundary and no top pane is added, exactly like the
     * solid ceiling.
     */
    public static JsonObject generateSkylight(int permutationIndex, CornerTaperProfile profile) {
        return generateSkylight(permutationIndex, DEFAULT_TEXTURE, profile);
    }

    public static JsonObject generateSkylight(int permutationIndex, String textureId, CornerTaperProfile profile) {
        Set<TankFace> openFaces = TankFace.fromPermutationIndex(permutationIndex);

        JsonObject model = baseModel(textureId);
        JsonArray elements = new JsonArray();

        List<CornerTaperProfile.Run> runs = profile.runs(!openFaces.contains(TankFace.UP), !openFaces.contains(TankFace.DOWN));

        if (!openFaces.contains(TankFace.NORTH)) {
            addNorthGlassPane(elements, openFaces, runs);
        }
        if (!openFaces.contains(TankFace.SOUTH)) {
            addSouthGlassPane(elements, openFaces, runs);
        }
        if (!openFaces.contains(TankFace.WEST)) {
            addWestGlassPane(elements, openFaces, runs);
        }
        if (!openFaces.contains(TankFace.EAST)) {
            addEastGlassPane(elements, openFaces, runs);
        }

        if (!openFaces.contains(TankFace.UP)) {
            elements.add(createSkylightPane(profile, openFaces));
        }

        model.add("elements", elements);
        return model;
    }

    private static void addNorthGlassPane(JsonArray elements, Set<TankFace> openFaces, List<CornerTaperProfile.Run> runs) {
        boolean nwCorner = !openFaces.contains(TankFace.NORTH) && !openFaces.contains(TankFace.WEST);
        boolean neCorner = !openFaces.contains(TankFace.NORTH) && !openFaces.contains(TankFace.EAST);
        for (CornerTaperProfile.Run run : runs) {
            if (run.width() >= 16) continue; // full-width run is a solid cap ring — never a window
            double minX = nwCorner ? run.width() : 0;
            double maxX = neCorner ? 16 - run.width() : 16;
            if (minX >= maxX) continue; // degenerate — a full-width run leaves no pane
            elements.add(pane(minX, run.yFrom(), 0, maxX, run.yTo(), 1, "north", "south"));
        }
    }

    private static void addSouthGlassPane(JsonArray elements, Set<TankFace> openFaces, List<CornerTaperProfile.Run> runs) {
        boolean swCorner = !openFaces.contains(TankFace.SOUTH) && !openFaces.contains(TankFace.WEST);
        boolean seCorner = !openFaces.contains(TankFace.SOUTH) && !openFaces.contains(TankFace.EAST);
        for (CornerTaperProfile.Run run : runs) {
            if (run.width() >= 16) continue; // full-width run is a solid cap ring — never a window
            double minX = swCorner ? run.width() : 0;
            double maxX = seCorner ? 16 - run.width() : 16;
            if (minX >= maxX) continue; // degenerate — a full-width run leaves no pane
            elements.add(pane(minX, run.yFrom(), 15, maxX, run.yTo(), 16, "north", "south"));
        }
    }

    private static void addWestGlassPane(JsonArray elements, Set<TankFace> openFaces, List<CornerTaperProfile.Run> runs) {
        boolean nwCorner = !openFaces.contains(TankFace.NORTH) && !openFaces.contains(TankFace.WEST);
        boolean swCorner = !openFaces.contains(TankFace.SOUTH) && !openFaces.contains(TankFace.WEST);
        for (CornerTaperProfile.Run run : runs) {
            if (run.width() >= 16) continue; // full-width run is a solid cap ring — never a window
            double minZ = nwCorner ? run.width() : 0;
            double maxZ = swCorner ? 16 - run.width() : 16;
            if (minZ >= maxZ) continue; // degenerate — a full-width run leaves no pane
            elements.add(paneZAxis(0, run.yFrom(), minZ, 1, run.yTo(), maxZ, "west", "east"));
        }
    }

    private static void addEastGlassPane(JsonArray elements, Set<TankFace> openFaces, List<CornerTaperProfile.Run> runs) {
        boolean neCorner = !openFaces.contains(TankFace.NORTH) && !openFaces.contains(TankFace.EAST);
        boolean seCorner = !openFaces.contains(TankFace.SOUTH) && !openFaces.contains(TankFace.EAST);
        for (CornerTaperProfile.Run run : runs) {
            if (run.width() >= 16) continue; // full-width run is a solid cap ring — never a window
            double minZ = neCorner ? run.width() : 0;
            double maxZ = seCorner ? 16 - run.width() : 16;
            if (minZ >= maxZ) continue; // degenerate — a full-width run leaves no pane
            elements.add(paneZAxis(15, run.yFrom(), minZ, 16, run.yTo(), maxZ, "west", "east"));
        }
    }

    /** North/south-facing pane segment: UV mirrors the X/Y footprint. */
    private static JsonObject pane(double x1, double y1, double z1, double x2, double y2, double z2, String faceA, String faceB) {
        JsonObject element = new JsonObject();
        element.add("from", vec3(x1, y1, z1));
        element.add("to", vec3(x2, y2, z2));

        JsonObject faces = new JsonObject();
        faces.add(faceA, face(x1, y1, x2, y2, "#all"));
        faces.add(faceB, face(x1, y1, x2, y2, "#all"));
        element.add("faces", faces);
        return element;
    }

    /** West/east-facing pane segment: UV mirrors the Z/Y footprint. */
    private static JsonObject paneZAxis(double x1, double y1, double z1, double x2, double y2, double z2, String faceA, String faceB) {
        JsonObject element = new JsonObject();
        element.add("from", vec3(x1, y1, z1));
        element.add("to", vec3(x2, y2, z2));

        JsonObject faces = new JsonObject();
        faces.add(faceA, face(z1, y1, z2, y2, "#all"));
        faces.add(faceB, face(z1, y1, z2, y2, "#all"));
        element.add("faces", faces);
        return element;
    }

    /**
     * The skylight pane: a horizontal pane at the ceiling band (Y 15..16) mirroring the sand's
     * footprint — inset by the floor-adjacent row width on each closed side, extending flush to the
     * block edge on each open side so a connected neighbor's roof meets it with no frame border
     * between them (the frame ring omits its strip on an open side). A horizontal pane's two faces
     * perpendicular to its thickness are {@code up}/{@code down}, so only those are defined (same UV
     * on both, matching {@link SandGeometryGenerator} and the side panes); the 1px side edges are
     * omitted, so nothing coincident renders against the ring's inner faces or the side panes.
     */
    private static JsonObject createSkylightPane(CornerTaperProfile profile, Set<TankFace> openFaces) {
        int t = profile.rowWidths()[CornerTaperProfile.ROW_COUNT - 1];
        int xLo = openFaces.contains(TankFace.WEST) ? 0 : t;
        int xHi = openFaces.contains(TankFace.EAST) ? 16 : 16 - t;
        int zLo = openFaces.contains(TankFace.NORTH) ? 0 : t;
        int zHi = openFaces.contains(TankFace.SOUTH) ? 16 : 16 - t;

        JsonObject element = new JsonObject();
        element.addProperty("name", "skylight");
        element.add("from", vec3(xLo, 15, zLo));
        element.add("to", vec3(xHi, 16, zHi));

        JsonObject faces = new JsonObject();
        faces.add("up", face(xLo, zLo, xHi, zHi, "#all"));
        faces.add("down", face(xLo, zLo, xHi, zHi, "#all"));
        element.add("faces", faces);
        return element;
    }
}
