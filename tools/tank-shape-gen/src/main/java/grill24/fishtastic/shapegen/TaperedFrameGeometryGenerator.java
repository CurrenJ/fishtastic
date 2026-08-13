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

    /**
     * Skylight frame: identical to {@link #generate(int, String, CornerTaperProfile)} except the
     * solid ceiling slab is replaced by a {@link #createSkylightCeiling frame ring} that leaves a
     * square opening for the skylight glass pane (see
     * {@code TaperedGlassGeometryGenerator#generateSkylight}). The floor and corner posts are
     * unchanged, so this is the STANDARD body with a see-through top rather than a solid cap.
     */
    public static JsonObject generateSkylight(int permutationIndex, CornerTaperProfile profile) {
        return generateSkylight(permutationIndex, DEFAULT_TEXTURE, profile);
    }

    public static JsonObject generateSkylight(int permutationIndex, String textureId, CornerTaperProfile profile) {
        Set<TankFace> openFaces = TankFace.fromPermutationIndex(permutationIndex);

        JsonObject model = baseModel(textureId);
        JsonArray elements = new JsonArray();

        if (!openFaces.contains(TankFace.UP)) {
            createSkylightCeiling(elements, openFaces, profile);
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
        // Floor sits at the very bottom of the block (y=0..1), so its side faces show the
        // texture's bottom 1px band (v=15..16), not the top band — the frame must read as a
        // full placed block with the glass punched out (FaceBakery.defaultFaceUV).
        if (!openFaces.contains(TankFace.NORTH)) faces.add("north", face(0, 15, 16, 16, "#all"));
        if (!openFaces.contains(TankFace.EAST)) faces.add("east", face(0, 15, 16, 16, "#all"));
        if (!openFaces.contains(TankFace.SOUTH)) faces.add("south", face(0, 15, 16, 16, "#all"));
        if (!openFaces.contains(TankFace.WEST)) faces.add("west", face(0, 15, 16, 16, "#all"));
        faces.add("up", face(0, 0, 16, 16, "#all"));
        faces.add("down", face(0, 0, 16, 16, "#all"));
        element.add("faces", faces);
        return element;
    }

    /**
     * The skylight ceiling: a frame ring whose strips each sit on a closed side, leaving the glass
     * generator's horizontal pane to fill the rest. A strip is present only when its side is closed
     * — when that side is open the strip is omitted and the glass pane extends flush to the block
     * edge instead (see {@code TaperedGlassGeometryGenerator#createSkylightPane}), so two connected
     * tanks' roofs meet with no frame border between them. The strips are sized off the pane's
     * footprint, so a perpendicular strip extends into a neighbor strip's side when that side opens
     * and keeps the corner cells covered.
     *
     * <p>Outer faces are boundary-conditioned exactly like the solid cap's ({@link #createCeiling}):
     * a face lying on an open block boundary is omitted so a closed side still shows the frame's top
     * band while an open side exposes the seam to its neighbor. Inner faces (facing the glass) and
     * up/down are always drawn — the glass pane defines no side faces, so nothing here can z-fight it.
     */
    private static void createSkylightCeiling(JsonArray elements, Set<TankFace> openFaces, CornerTaperProfile profile) {
        int t = profile.rowWidths()[CornerTaperProfile.ROW_COUNT - 1]; // mirror the sand's floor-adjacent inset
        boolean northOpen = openFaces.contains(TankFace.NORTH);
        boolean southOpen = openFaces.contains(TankFace.SOUTH);
        boolean westOpen = openFaces.contains(TankFace.WEST);
        boolean eastOpen = openFaces.contains(TankFace.EAST);

        int xLo = westOpen ? 0 : t;
        int xHi = eastOpen ? 16 : 16 - t;
        int zLo = northOpen ? 0 : t;
        int zHi = southOpen ? 16 : 16 - t;

        if (zLo > 0) {
            elements.add(createRingBox("skylight_north", 0, 15, 0, 16, 16, zLo, openFaces));
        }
        if (zHi < 16) {
            elements.add(createRingBox("skylight_south", 0, 15, zHi, 16, 16, 16, openFaces));
        }
        if (xLo > 0) {
            elements.add(createRingBox("skylight_west", 0, 15, zLo, xLo, 16, zHi, openFaces));
        }
        if (xHi < 16) {
            elements.add(createRingBox("skylight_east", xHi, 15, zLo, 16, 16, zHi, openFaces));
        }
    }

    /**
     * One strip of the skylight's frame ring: a full 6-faced box using {@code FaceBakery.defaultFaceUV}
     * (the same UV convention as {@link #createSupportBox}, so the ring reads as a placed block with
     * the window punched out of it), with any face lying on an open block boundary omitted. Faces on
     * the ring's inner edge and its up/down are interior and always kept.
     */
    private static JsonObject createRingBox(String name, double x1, double y1, double z1, double x2, double y2, double z2,
                                            Set<TankFace> openFaces) {
        JsonObject element = new JsonObject();
        element.addProperty("name", name);
        element.add("from", vec3(x1, y1, z1));
        element.add("to", vec3(x2, y2, z2));

        JsonObject faces = new JsonObject();
        if (!(z1 == 0 && openFaces.contains(TankFace.NORTH))) {
            faces.add("north", face(16 - x2, 16 - y2, 16 - x1, 16 - y1, "#all"));
        }
        if (!(z2 == 16 && openFaces.contains(TankFace.SOUTH))) {
            faces.add("south", face(x1, 16 - y2, x2, 16 - y1, "#all"));
        }
        if (!(x1 == 0 && openFaces.contains(TankFace.WEST))) {
            faces.add("west", face(z1, 16 - y2, z2, 16 - y1, "#all"));
        }
        if (!(x2 == 16 && openFaces.contains(TankFace.EAST))) {
            faces.add("east", face(16 - z2, 16 - y2, 16 - z1, 16 - y1, "#all"));
        }
        if (!(y2 == 16 && openFaces.contains(TankFace.UP))) {
            faces.add("up", face(x1, z1, x2, z2, "#all"));
        }
        if (!(y1 == 0 && openFaces.contains(TankFace.DOWN))) {
            faces.add("down", face(x1, 16 - z2, x2, 16 - z1, "#all"));
        }
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

        // UVs are FaceBakery.defaultFaceUV for this sub-box — the exact texture region a full
        // block of the same material shows at these world coordinates. This is what makes the
        // corner post read as a placed block with the glass punched out: a post at world height
        // [minY, maxY] must sample the texture's corresponding vertical band, not its top, and a
        // post on the west edge must sample the west pixels of each face, not the top-left corner
        // of the texture for every face. (These supports only exist when both adjacent faces are
        // closed, so they never conflict with glass; a narrower band's "up"/"down" face still
        // doubles as the shelf where a wider neighboring band doesn't cover it.)
        faces.add("north", face(16 - x2, 16 - maxY, 16 - x1, 16 - minY, "#all"));
        faces.add("south", face(x1, 16 - maxY, x2, 16 - minY, "#all"));
        faces.add("west", face(z1, 16 - maxY, z2, 16 - minY, "#all"));
        faces.add("east", face(16 - z2, 16 - maxY, 16 - z1, 16 - minY, "#all"));
        faces.add("up", face(x1, z1, x2, z2, "#all"));
        faces.add("down", face(x1, 16 - z2, x2, 16 - z1, "#all"));

        element.add("faces", faces);
        return element;
    }
}
