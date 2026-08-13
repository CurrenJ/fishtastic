package grill24.fishtastic.shapegen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Set;

import static grill24.fishtastic.shapegen.TankShapeGeometry.addSingleGroup;
import static grill24.fishtastic.shapegen.TankShapeGeometry.baseModel;
import static grill24.fishtastic.shapegen.TankShapeGeometry.face;
import static grill24.fishtastic.shapegen.TankShapeGeometry.smartLabel;
import static grill24.fishtastic.shapegen.TankShapeGeometry.vec3;

/**
 * Generates the fish tank sand's 64-permutation geometry: a base layer plus edge/corner
 * extensions toward each open face, or nothing at all when the tank's DOWN face is open (sand
 * falls into the tank below). The sand's horizontal footprint insets from the block edge by the
 * {@link CornerTaperProfile}'s floor-adjacent row width (image row 14 — the corner supports it
 * clears) and rests on the floor slab, which is always a fixed 1px (the profile's caps don't
 * taper), so a tapered shape's wider corner posts never lift the sand off its floor. The sand
 * layer's own thickness (1px) is a separate, unscaled quantity, same as glass pane thickness.
 *
 * <p>With {@link CornerTaperProfile#STANDARD} this reproduces the original hand-written
 * {@code FishTankSandModelProvider} datagen byte-for-byte — see
 * docs/fish-tanks.md, Phase 1 and Phase 2.
 *
 * <p>Sand behavior:
 * <ul>
 *   <li>If DOWN is open, no sand is rendered (empty model)</li>
 *   <li>Sand extends to edges in cardinal directions where those faces are open</li>
 *   <li>Base sand dimensions: [t, 1, t] to [16-t, 2, 16-t] where t = the profile's
 *       floor-adjacent row width</li>
 *   <li>Extensions bridge from the block edge (0/16) to the base sand's edge (t/16-t)</li>
 * </ul>
 */
public final class SandGeometryGenerator {
    public static final String DEFAULT_TEXTURE = "block/sand";

    private SandGeometryGenerator() {}

    public static JsonObject generate(int permutationIndex) {
        return generate(permutationIndex, DEFAULT_TEXTURE, CornerTaperProfile.STANDARD);
    }

    public static JsonObject generate(int permutationIndex, CornerTaperProfile profile) {
        return generate(permutationIndex, DEFAULT_TEXTURE, profile);
    }

    public static JsonObject generate(int permutationIndex, String textureId, CornerTaperProfile profile) {
        Set<TankFace> openFaces = TankFace.fromPermutationIndex(permutationIndex);
        // Sand only ever cares about the floor-adjacent row (image row 14 = the profile's last
        // entry). The floor slab is always a fixed 1px (CornerTaperProfile caps don't taper), so
        // the sand sits at y=1..2 regardless of how wide the corner posts are.
        int t = profile.rowWidths()[CornerTaperProfile.ROW_COUNT - 1];
        int floor = 1;

        JsonObject model = baseModel(textureId);

        JsonArray elements = new JsonArray();

        // If bottom is open, no sand at all (empty model)
        if (!openFaces.contains(TankFace.DOWN)) {
            boolean northOpen = openFaces.contains(TankFace.NORTH);
            boolean southOpen = openFaces.contains(TankFace.SOUTH);
            boolean westOpen = openFaces.contains(TankFace.WEST);
            boolean eastOpen = openFaces.contains(TankFace.EAST);

            // Add base sand layer
            elements.add(createBaseSand(t, floor));

            // Add extensions for open sides (bridge the base sand to the block edge)
            if (northOpen) {
                elements.add(createNorthExtension(t, floor));
            }
            if (southOpen) {
                elements.add(createSouthExtension(t, floor));
            }
            if (westOpen) {
                elements.add(createWestExtension(t, floor));
            }
            if (eastOpen) {
                elements.add(createEastExtension(t, floor));
            }

            // Closed sides: the base sand insets to t to clear the wider corner posts, but the
            // glass wall is only 1px thick — bridge the base sand to the glass inner face (1/15)
            // on each closed side so the sand reaches the wall instead of leaving a t-1 gap. A
            // uniform-width shape (t == 1) already sits flush against the glass, so there's
            // nothing to add.
            if (t > 1) {
                if (!northOpen) {
                    elements.add(createSandBox("north_fill", t, floor, 1, 16 - t, floor + 1, t));
                }
                if (!southOpen) {
                    elements.add(createSandBox("south_fill", t, floor, 16 - t, 16 - t, floor + 1, 15));
                }
                if (!westOpen) {
                    elements.add(createSandBox("west_fill", 1, floor, t, t, floor + 1, 16 - t));
                }
                if (!eastOpen) {
                    elements.add(createSandBox("east_fill", 16 - t, floor, t, 15, floor + 1, 16 - t));
                }
            }

            // Add corner fills. The corner post occupies the full t×t corner only when both
            // adjacent faces are closed; otherwise the sand fills in toward it — reaching the
            // block edge (0/16) on an open face or the 1px glass inner face (1/15) on a closed one.
            addCorner(elements, 0, 0, t, floor, westOpen, northOpen);   // NW
            addCorner(elements, 1, 0, t, floor, eastOpen, northOpen);   // NE
            addCorner(elements, 0, 1, t, floor, westOpen, southOpen);   // SW
            addCorner(elements, 1, 1, t, floor, eastOpen, southOpen);   // SE
        }

        model.add("elements", elements);

        addSingleGroup(model, "sand_" + permutationIndex);

        return model;
    }

    /** Create the base sand layer (center area between frame edges, sitting on the floor slab). */
    private static JsonObject createBaseSand(int t, int floor) {
        JsonObject element = new JsonObject();
        element.addProperty("name", "base_sand");
        element.add("from", vec3(t, floor, t));
        element.add("to", vec3(16 - t, floor + 1, 16 - t));

        JsonObject faces = new JsonObject();
        faces.add("north", face(t, 14, 16 - t, 15, "#all"));
        faces.add("east", face(t, 14, 16 - t, 15, "#all"));
        faces.add("south", face(t, 14, 16 - t, 15, "#all"));
        faces.add("west", face(t, 14, 16 - t, 15, "#all"));
        faces.add("up", face(t, t, 16 - t, 16 - t, "#all"));
        faces.add("down", face(t, t, 16 - t, 16 - t, "#all"));
        element.add("faces", faces);

        return element;
    }

    /** North extension - bridges from the base sand's north edge to the block's north edge (Z=0). */
    private static JsonObject createNorthExtension(int t, int floor) {
        JsonObject element = new JsonObject();
        element.addProperty("name", "north_extension");
        element.add("from", vec3(t, floor, 0));
        element.add("to", vec3(16 - t, floor + 1, t));

        JsonObject faces = new JsonObject();
        faces.add("north", face(t, 14, 16 - t, 15, "#all"));
        faces.add("east", face(0, 14, t, 15, "#all"));
        faces.add("south", face(t, 14, 16 - t, 15, "#all"));
        faces.add("west", face(0, 14, t, 15, "#all"));
        faces.add("up", face(t, 0, 16 - t, t, "#all"));
        faces.add("down", face(t, 0, 16 - t, t, "#all"));
        element.add("faces", faces);

        return element;
    }

    /** South extension - bridges from the base sand's south edge to the block's south edge (Z=16). */
    private static JsonObject createSouthExtension(int t, int floor) {
        JsonObject element = new JsonObject();
        element.addProperty("name", "south_extension");
        element.add("from", vec3(t, floor, 16 - t));
        element.add("to", vec3(16 - t, floor + 1, 16));

        JsonObject faces = new JsonObject();
        faces.add("north", face(t, 14, 16 - t, 15, "#all"));
        faces.add("east", face(16 - t, 14, 16, 15, "#all"));
        faces.add("south", face(t, 14, 16 - t, 15, "#all"));
        faces.add("west", face(16 - t, 14, 16, 15, "#all"));
        faces.add("up", face(t, 16 - t, 16 - t, 16, "#all"));
        faces.add("down", face(t, 16 - t, 16 - t, 16, "#all"));
        element.add("faces", faces);

        return element;
    }

    /** West extension - bridges from the base sand's west edge to the block's west edge (X=0). */
    private static JsonObject createWestExtension(int t, int floor) {
        JsonObject element = new JsonObject();
        element.addProperty("name", "west_extension");
        element.add("from", vec3(0, floor, t));
        element.add("to", vec3(t, floor + 1, 16 - t));

        JsonObject faces = new JsonObject();
        faces.add("north", face(0, 14, t, 15, "#all"));
        faces.add("east", face(t, 14, 16 - t, 15, "#all"));
        faces.add("south", face(0, 14, t, 15, "#all"));
        faces.add("west", face(t, 14, 16 - t, 15, "#all"));
        faces.add("up", face(0, t, t, 16 - t, "#all"));
        faces.add("down", face(0, t, t, 16 - t, "#all"));
        element.add("faces", faces);

        return element;
    }

    /** East extension - bridges from the base sand's east edge to the block's east edge (X=16). */
    private static JsonObject createEastExtension(int t, int floor) {
        JsonObject element = new JsonObject();
        element.addProperty("name", "east_extension");
        element.add("from", vec3(16 - t, floor, t));
        element.add("to", vec3(16, floor + 1, 16 - t));

        JsonObject faces = new JsonObject();
        faces.add("north", face(16 - t, 14, 16, 15, "#all"));
        faces.add("east", face(t, 14, 16 - t, 15, "#all"));
        faces.add("south", face(16 - t, 14, 16, 15, "#all"));
        faces.add("west", face(t, 14, 16 - t, 15, "#all"));
        faces.add("up", face(16 - t, t, 16, 16 - t, "#all"));
        faces.add("down", face(16 - t, t, 16, 16 - t, "#all"));
        element.add("faces", faces);

        return element;
    }

    /**
     * Fills a corner with sand. The corner post occupies the full {@code t}×{@code t} corner only
     * when both adjacent faces are closed, so it's skipped then; otherwise the sand fills in
     * toward the post — reaching the block edge ({@code 0}/{@code 16}) on an open face or the
     * 1px glass inner face ({@code 1}/{@code 15}) on a closed one. When either dimension is zero
     * (a {@code STANDARD} mixed corner, where the inset equals the glass thickness) the box is
     * degenerate and skipped.
     *
     * @param cornerX        0 = west edge, 1 = east edge
     * @param cornerZ        0 = north edge, 1 = south edge
     * @param horizontalOpen whether the west/east face (the X boundary) is open
     * @param verticalOpen   whether the north/south face (the Z boundary) is open
     */
    private static void addCorner(JsonArray elements, int cornerX, int cornerZ, int t, int floor,
                                  boolean horizontalOpen, boolean verticalOpen) {
        if (!horizontalOpen && !verticalOpen) {
            return; // corner post occupies the whole corner
        }
        int xFrom = cornerX == 0 ? (horizontalOpen ? 0 : 1) : 16 - t;
        int xTo = cornerX == 0 ? t : (horizontalOpen ? 16 : 15);
        int zFrom = cornerZ == 0 ? (verticalOpen ? 0 : 1) : 16 - t;
        int zTo = cornerZ == 0 ? t : (verticalOpen ? 16 : 15);
        if (xFrom >= xTo || zFrom >= zTo) {
            return; // degenerate (zero-thickness) box — e.g. STANDARD's t=1 mixed corners
        }
        elements.add(createSandBox(
                "corner_extension_" + smartLabel(xFrom) + "_" + smartLabel(zFrom),
                xFrom, floor, zFrom, xTo, floor + 1, zTo));
    }

    /**
     * Builds a full 6-faced sand element using the established UV convention shared by the other
     * sand elements: side faces map their along-wall extent to U and a fixed 1px texture band
     * (14..15) to V, while up/down map X to U and Z to V.
     */
    private static JsonObject createSandBox(String name, int x1, int y1, int z1, int x2, int y2, int z2) {
        JsonObject element = new JsonObject();
        element.addProperty("name", name);
        element.add("from", vec3(x1, y1, z1));
        element.add("to", vec3(x2, y2, z2));

        JsonObject faces = new JsonObject();
        faces.add("north", face(x1, 14, x2, 15, "#all"));
        faces.add("south", face(x1, 14, x2, 15, "#all"));
        faces.add("west", face(z1, 14, z2, 15, "#all"));
        faces.add("east", face(z1, 14, z2, 15, "#all"));
        faces.add("up", face(x1, z1, x2, z2, "#all"));
        faces.add("down", face(x1, z1, x2, z2, "#all"));
        element.add("faces", faces);

        return element;
    }
}
