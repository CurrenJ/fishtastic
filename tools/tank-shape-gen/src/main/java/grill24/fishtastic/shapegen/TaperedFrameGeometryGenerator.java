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

    /**
     * Vitrine frame: both ceiling and floor are replaced by a {@link #createSkylightCeiling}/
     * {@link #createSkylightFloor} frame ring, leaving square openings for horizontal glass panes
     * on both caps (see {@code TaperedGlassGeometryGenerator#generateVitrine}) instead of the
     * usual solid ceiling + sand. Corner posts are unchanged from {@link #generate}.
     */
    public static JsonObject generateVitrine(int permutationIndex, CornerTaperProfile profile) {
        return generateVitrine(permutationIndex, DEFAULT_TEXTURE, profile);
    }

    public static JsonObject generateVitrine(int permutationIndex, String textureId, CornerTaperProfile profile) {
        Set<TankFace> openFaces = TankFace.fromPermutationIndex(permutationIndex);

        JsonObject model = baseModel(textureId);
        JsonArray elements = new JsonArray();

        if (!openFaces.contains(TankFace.UP)) {
            createSkylightCeiling(elements, openFaces, profile);
        }
        if (!openFaces.contains(TankFace.DOWN)) {
            createSkylightFloor(elements, openFaces, profile);
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
     * A standalone corner-post fragment for {@code corner}, independent of open-face state — used
     * to composite a post back onto the base bake at render time when both of {@code corner}'s
     * orthogonal faces are open but its diagonal neighbor cell is empty (see
     * {@code FishTankCompositeModelData#getDiagonalOverrideMask}). Geometry-identical to what
     * {@link #generate} would draw for this corner if both its faces were closed.
     */
    public static JsonObject generateCornerFragment(TankCorner corner, boolean ceilingClosed, boolean floorClosed, CornerTaperProfile profile) {
        return generateCornerFragment(corner, ceilingClosed, floorClosed, DEFAULT_TEXTURE, profile);
    }

    public static JsonObject generateCornerFragment(TankCorner corner, boolean ceilingClosed, boolean floorClosed, String textureId, CornerTaperProfile profile) {
        JsonObject model = baseModel(textureId);
        JsonArray elements = new JsonArray();
        List<CornerTaperProfile.Run> runs = profile.runs(ceilingClosed, floorClosed);
        addTaperedSupport(elements, corner.xEdge(), corner.zEdge(), runs);
        model.add("elements", elements);
        addSingleGroup(model, "frame_corner_" + corner.name().toLowerCase());
        return model;
    }

    /**
     * Corner-post fragment for {@link #generateSkylight}-shaped caps: identical to
     * {@link #generateCornerFragment} plus one extra plug at the ceiling cap band (Y 15-16).
     * {@link #createSkylightCeiling}'s ring strips are gated per side (north/south/west/east), so
     * at a corner whose both orthogonal faces are open — exactly the diagonal-empty-corner scenario
     * this fragment is composited back in for — neither strip reaches that corner cell, leaving the
     * ring's own corner square uncovered there (the skylight glass pane, unlike the ring, isn't
     * gated that way and flushes all the way into it instead — see
     * {@code TaperedGlassGeometryGenerator#createSkylightPane}). {@link #generateCornerFragment}'s
     * taper post never reaches this band either: {@link CornerTaperProfile#runs} only covers image
     * rows 1-14 (Minecraft Y 1-15), leaving Y 15-16 to the solid ceiling slab for ordinary shapes —
     * a slab {@link #generateSkylight} doesn't have. The plug matches the ring strip's own width,
     * {@code profile.rowWidths()[ROW_COUNT - 1]} (see {@link #createSkylightCeiling}), and is only
     * added when the ceiling cap is actually closed (matching the ring's own guard) — an open
     * ceiling means no ring was drawn at all, a real vertical-stacking seam with nothing to plug.
     */
    public static JsonObject generateSkylightCornerFragment(TankCorner corner, boolean ceilingClosed, boolean floorClosed, CornerTaperProfile profile) {
        return generateSkylightCornerFragment(corner, ceilingClosed, floorClosed, DEFAULT_TEXTURE, profile);
    }

    public static JsonObject generateSkylightCornerFragment(TankCorner corner, boolean ceilingClosed, boolean floorClosed, String textureId, CornerTaperProfile profile) {
        JsonObject model = generateCornerFragment(corner, ceilingClosed, floorClosed, textureId, profile);
        if (ceilingClosed) {
            int t = profile.rowWidths()[CornerTaperProfile.ROW_COUNT - 1];
            model.getAsJsonArray("elements").add(createSupportBox(corner.xEdge(), corner.zEdge(), 15, 16, t));
        }
        return model;
    }

    /**
     * Corner-post fragment for {@link #generateVitrine}-shaped caps: {@link
     * #generateSkylightCornerFragment}'s ceiling-band plug, plus the mirror-image plug at the floor
     * cap band (Y 0-1) for {@link #createSkylightFloor}'s ring — both caps are rings here, so both
     * can leave this corner's cell uncovered at once.
     */
    public static JsonObject generateVitrineCornerFragment(TankCorner corner, boolean ceilingClosed, boolean floorClosed, CornerTaperProfile profile) {
        return generateVitrineCornerFragment(corner, ceilingClosed, floorClosed, DEFAULT_TEXTURE, profile);
    }

    public static JsonObject generateVitrineCornerFragment(TankCorner corner, boolean ceilingClosed, boolean floorClosed, String textureId, CornerTaperProfile profile) {
        JsonObject model = generateCornerFragment(corner, ceilingClosed, floorClosed, textureId, profile);
        JsonArray elements = model.getAsJsonArray("elements");
        int t = profile.rowWidths()[CornerTaperProfile.ROW_COUNT - 1];
        if (ceilingClosed) elements.add(createSupportBox(corner.xEdge(), corner.zEdge(), 15, 16, t));
        if (floorClosed) elements.add(createSupportBox(corner.xEdge(), corner.zEdge(), 0, 1, t));
        return model;
    }

    /**
     * A standalone edge-diagonal frame-beam fragment for {@code edge}, independent of open-face
     * state — used to composite a beam back onto the base bake at render time when both of
     * {@code edge}'s horizontal and vertical faces are open but its edge-diagonal neighbor cell is
     * empty (see {@code FishTankCompositeModelData#getEdgeDiagonalOverrideMask}).
     *
     * <p>No {@code capState} parameter, unlike {@link #generateCornerFragment}: eligibility
     * <em>requires</em> the vertical face (UP or DOWN) to be open, and
     * {@link CornerTaperProfile#effectiveRowWidths} already forces the cap-adjacent run to
     * {@link CornerTaperProfile#baseWidth()} whenever that cap is open — so this fragment is
     * always exactly one box at {@link CornerTaperProfile#baseWidth()}, reaching inward from the
     * beam's edge by that width and spanning the full 0-16 perpendicular width (the beam is a real
     * structural seal along the entire wall/floor-or-ceiling seam, not just its middle). It is not
     * trimmed against the adjacent corner fragments' own reach — the two are allowed to overlap,
     * since both render the same opaque frame texture from coplanar or fully-interior boxes (the
     * same flush-overlap-by-construction convention {@link #addTaperedSupport} already relies on).
     *
     * <p>The beam's ends previously also collided with the perpendicular wall's own glass pane,
     * which flush-extends into the same corner cell when that wall's own corner post is absent
     * (see {@code TaperedGlassGeometryGenerator}) — real opaque-over-translucent overlap, not the
     * coplanar frame-on-frame kind, and it z-fought. That is fixed on the glass side instead (see
     * {@code TaperedGlassGeometryGenerator#addNorthGlassPane} et al.'s cap-adjacent-run note): the
     * glass now insets there too whenever this beam is eligible, rather than shrinking the beam —
     * insetting the beam instead once caused a visible regression (a "missing" frame corner with
     * glass showing through in its place), since the beam's ends are exactly where a corner post is
     * expected to read as solid.
     */
    public static JsonObject generateEdgeFragment(TankEdge edge, CornerTaperProfile profile) {
        return generateEdgeFragment(edge, DEFAULT_TEXTURE, profile);
    }

    public static JsonObject generateEdgeFragment(TankEdge edge, String textureId, CornerTaperProfile profile) {
        JsonObject model = baseModel(textureId);
        JsonArray elements = new JsonArray();
        elements.add(createEdgeBeamBox(edge, profile.baseWidth()));
        model.add("elements", elements);
        addSingleGroup(model, "frame_edge_" + edge.name().toLowerCase());
        return model;
    }

    /**
     * The edge beam box: occupies the vertical cap's band (Y 0-1 for a DOWN edge, Y 15-16 for UP),
     * reaches inward from the horizontal side's true edge by {@code width}, and spans the full
     * 0-16 perpendicular width (no trimming against corner fragments — see
     * {@link #generateEdgeFragment}'s note).
     */
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
        // Mirrors the sand's floor-adjacent inset — correct for a plain-taper profile like STANDARD,
        // where that row equals the profile's steady-state width. Not reused as-is by the stepped
        // shapes (see the (int) overload below).
        createSkylightCeiling(elements, openFaces, profile.rowWidths()[CornerTaperProfile.ROW_COUNT - 1]);
    }

    /**
     * Window-inset overload of {@link #createSkylightCeiling(JsonArray, Set, CornerTaperProfile)},
     * for shapes whose literal floor-adjacent row isn't a usable window size — a stepped shape like
     * STURDY has a full-width ({@code 16}) chamfered-ring row there, so
     * {@code ShellFrameGeometryGenerator#generateCupola}/{@code #generateHutch} pass
     * {@link CornerTaperProfile#baseWidth()} instead, matching the hollow square the chamfered ring
     * band immediately below the cap already leaves open.
     */
    static void createSkylightCeiling(JsonArray elements, Set<TankFace> openFaces, int t) {
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
     * The vitrine floor ring: {@link #createSkylightCeiling}'s mirror image at the bottom cap,
     * leaving a square opening for the floorlight glass pane (see
     * {@code TaperedGlassGeometryGenerator#generateVitrine}) instead of a solid floor slab or sand.
     * {@link #createRingBox} is direction-agnostic about which cap it sits on — it gates the "up"
     * face on the block's UP boundary and "down" on DOWN regardless of the box's own Y extent — so
     * reusing it here at Y 0..1 is exactly as safe as the ceiling's Y 15..16 use.
     */
    private static void createSkylightFloor(JsonArray elements, Set<TankFace> openFaces, CornerTaperProfile profile) {
        createSkylightFloor(elements, openFaces, profile.rowWidths()[CornerTaperProfile.ROW_COUNT - 1]);
    }

    /** Window-inset overload — see {@link #createSkylightCeiling(JsonArray, Set, int)}'s note. */
    static void createSkylightFloor(JsonArray elements, Set<TankFace> openFaces, int t) {
        boolean northOpen = openFaces.contains(TankFace.NORTH);
        boolean southOpen = openFaces.contains(TankFace.SOUTH);
        boolean westOpen = openFaces.contains(TankFace.WEST);
        boolean eastOpen = openFaces.contains(TankFace.EAST);

        int xLo = westOpen ? 0 : t;
        int xHi = eastOpen ? 16 : 16 - t;
        int zLo = northOpen ? 0 : t;
        int zHi = southOpen ? 16 : 16 - t;

        if (zLo > 0) {
            elements.add(createRingBox("floorlight_north", 0, 0, 0, 16, 1, zLo, openFaces));
        }
        if (zHi < 16) {
            elements.add(createRingBox("floorlight_south", 0, 0, zHi, 16, 1, 16, openFaces));
        }
        if (xLo > 0) {
            elements.add(createRingBox("floorlight_west", 0, 0, zLo, xLo, 1, zHi, openFaces));
        }
        if (xHi < 16) {
            elements.add(createRingBox("floorlight_east", xHi, 0, zLo, 16, 1, zHi, openFaces));
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
