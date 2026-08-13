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
 * Frame generator for the stepped-octagon shapes ({@code FACETED}/{@code BASTION}): the corner
 * posts are slimmed from solid {@code w×w} blocks to 1px-thick flat plates that still reach inward
 * by the taper's {@code w}, so the frame no longer occludes the stepped-octagon sand or overlaps
 * the glass. A full-width ({@code 16}) run instead keeps the chamfered base ring (its reach tapers
 * along Z), matching {@code BASTION}'s caps.
 *
 * <p>Each face's frame is two 1px plates — one hugging the north/west end, one the south/east end —
 * leaving the middle of the face for the glass pane, exactly like the standard tank's corner posts
 * but with the plate length driven by the taper rather than fixed at 1px.
 */
public final class ShellFrameGeometryGenerator {
    public static final String DEFAULT_TEXTURE = "block/oak_planks";

    private ShellFrameGeometryGenerator() {}

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

        List<CornerTaperProfile.Run> runs = profile.runs(!openFaces.contains(TankFace.UP), !openFaces.contains(TankFace.DOWN));
        for (CornerTaperProfile.Run run : runs) {
            // A full-width run gets a full chamfered ring (no sand/glass there). The floor-adjacent
            // run (Y 1..2) keeps its flat plates and additionally gets west/east chamfers filling the
            // disjoint space between the 1px glass and the sand circle. All other runs are just
            // 1px-thick flat plates.
            if (run.width() >= 16) {
                addChamferedRing(elements, run, profile.rowWidths(), openFaces);
            } else {
                addFlatPlates(elements, run, openFaces);
                // The floor chamfer only makes sense when there is sand to carve around (floor
                // closed); with DOWN open the sand is gone and the taper falls back to the base.
                if (run.yFrom() <= 1 && !openFaces.contains(TankFace.DOWN)) {
                    addFloorChamfers(elements, run, profile.rowWidths(), openFaces);
                }
            }
        }

        model.add("elements", elements);
        addSingleGroup(model, "frame_" + permutationIndex);
        return model;
    }

    /** 1px-thick flat plates: two per face (north/south ends on west/east faces, west/east ends on
     * north/south faces), each reaching {@code w} inward. Leaves the face middle for the glass.
     * Each plate belongs to a corner and is gated by <em>both</em> of that corner's faces, so a
     * plate disappears when either adjacent face opens (matching the corner-post behavior). The
     * north/south plates own the corner cells; the west/east plates start/end 1px in from the
     * corners so the plates never overlap (for {@code w == 1} the west/east plates are degenerate
     * and skipped, leaving just the four 1×1 corner posts). */
    private static void addFlatPlates(JsonArray elements, CornerTaperProfile.Run run, Set<TankFace> openFaces) {
        int w = run.width();
        double y1 = run.yFrom();
        double y2 = run.yTo();

        boolean nC = !openFaces.contains(TankFace.NORTH);
        boolean sC = !openFaces.contains(TankFace.SOUTH);
        boolean wC = !openFaces.contains(TankFace.WEST);
        boolean eC = !openFaces.contains(TankFace.EAST);

        if (nC && wC) elements.add(createBox("n_" + smartLabel(y1) + "_w", 0, y1, 0, w, y2, 1));
        if (nC && eC) elements.add(createBox("n_" + smartLabel(y1) + "_e", 16 - w, y1, 0, 16, y2, 1));
        if (sC && wC) elements.add(createBox("s_" + smartLabel(y1) + "_w", 0, y1, 15, w, y2, 16));
        if (sC && eC) elements.add(createBox("s_" + smartLabel(y1) + "_e", 16 - w, y1, 15, 16, y2, 16));
        if (w > 1) {
            if (wC && nC) elements.add(createBox("w_" + smartLabel(y1) + "_n", 0, y1, 1, 1, y2, w));
            if (wC && sC) elements.add(createBox("w_" + smartLabel(y1) + "_s", 0, y1, 16 - w, 1, y2, 15));
            if (eC && nC) elements.add(createBox("e_" + smartLabel(y1) + "_n", 15, y1, 1, 16, y2, w));
            if (eC && sC) elements.add(createBox("e_" + smartLabel(y1) + "_s", 15, y1, 16 - w, 16, y2, 15));
        }
    }

    /** Chamfered octagonal ring filling the region outside the sand at this Y band: full
     * north/south walls over the no-sand end bands, plus tapering west/east walls over the sand's
     * Z extent, each wall gated by its face so a connected face's wall disappears at the seam. */
    private static void addChamferedRing(JsonArray elements, CornerTaperProfile.Run run, int[] rowWidths, Set<TankFace> openFaces) {
        double y1 = run.yFrom();
        double y2 = run.yTo();

        // Sand's Z extent (rows where the taper is < 16 = a full-width band with no sand).
        int northEdge = 1;
        while (northEdge <= 14 && rowWidths[northEdge - 1] >= 16) northEdge++;
        int southEdge = 15;
        while (southEdge >= 2 && rowWidths[southEdge - 2] >= 16) southEdge--;

        if (!openFaces.contains(TankFace.NORTH)) {
            elements.add(createBox("ring_n", 0, y1, 0, 16, y2, northEdge));
        }
        if (!openFaces.contains(TankFace.SOUTH)) {
            elements.add(createBox("ring_s", 0, y1, southEdge, 16, y2, 16));
        }

        int runStartZ = northEdge;
        int runInset = rowWidths[northEdge - 1];
        for (int z = northEdge + 1; z <= southEdge; z++) {
            int inset = z < southEdge ? rowWidths[z - 1] : Integer.MIN_VALUE;
            if (inset != runInset) {
                if (runInset < 16 && runInset > 0) {
                    if (!openFaces.contains(TankFace.WEST)) elements.add(createBox("ring_w_" + smartLabel(runStartZ), 0, y1, runStartZ, runInset, y2, z));
                    if (!openFaces.contains(TankFace.EAST)) elements.add(createBox("ring_e_" + smartLabel(runStartZ), 16 - runInset, y1, runStartZ, 16, y2, z));
                }
                runStartZ = z;
                runInset = inset;
            }
        }
    }

    /** West/east chamfers for the floor-adjacent run: fills the disjoint space between the 1px
     * glass (X=0..1 / 15..16) and the sand's west/east edge (X=taper[Z-1] / 16-taper[Z-1]) at the
     * sand's Z extent. Gated by the face, so an open west/east face's chamfer yields to the sand's
     * own bridge. Skipped where the sand is already flush (inset <= 1). */
    private static void addFloorChamfers(JsonArray elements, CornerTaperProfile.Run run, int[] rowWidths, Set<TankFace> openFaces) {
        double y1 = run.yFrom();
        double y2 = run.yTo();

        int northEdge = 1;
        while (northEdge <= 14 && rowWidths[northEdge - 1] >= 16) northEdge++;
        int southEdge = 15;
        while (southEdge >= 2 && rowWidths[southEdge - 2] >= 16) southEdge--;

        int runStartZ = northEdge;
        int runInset = rowWidths[northEdge - 1];
        for (int z = northEdge + 1; z <= southEdge; z++) {
            int inset = z < southEdge ? rowWidths[z - 1] : Integer.MIN_VALUE;
            if (inset != runInset) {
                if (runInset > 1 && runInset < 16) {
                    if (!openFaces.contains(TankFace.WEST)) elements.add(createBox("chamfer_w_" + smartLabel(runStartZ), 1, y1, runStartZ, runInset, y2, z));
                    if (!openFaces.contains(TankFace.EAST)) elements.add(createBox("chamfer_e_" + smartLabel(runStartZ), 16 - runInset, y1, runStartZ, 15, y2, z));
                }
                runStartZ = z;
                runInset = inset;
            }
        }
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
        if (!openFaces.contains(TankFace.NORTH)) faces.add("north", face(0, 15, 16, 16, "#all"));
        if (!openFaces.contains(TankFace.EAST)) faces.add("east", face(0, 15, 16, 16, "#all"));
        if (!openFaces.contains(TankFace.SOUTH)) faces.add("south", face(0, 15, 16, 16, "#all"));
        if (!openFaces.contains(TankFace.WEST)) faces.add("west", face(0, 15, 16, 16, "#all"));
        faces.add("up", face(0, 0, 16, 16, "#all"));
        faces.add("down", face(0, 0, 16, 16, "#all"));
        element.add("faces", faces);
        return element;
    }

    /** Full 6-faced opaque box; UVs use FaceBakery.defaultFaceUV conventions. */
    private static JsonObject createBox(String name, int x1, double y1, int z1, int x2, double y2, int z2) {
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
