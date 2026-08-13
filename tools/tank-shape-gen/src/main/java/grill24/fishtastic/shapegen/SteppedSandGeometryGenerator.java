package grill24.fishtastic.shapegen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static grill24.fishtastic.shapegen.TankShapeGeometry.addSingleGroup;
import static grill24.fishtastic.shapegen.TankShapeGeometry.baseModel;
import static grill24.fishtastic.shapegen.TankShapeGeometry.face;
import static grill24.fishtastic.shapegen.TankShapeGeometry.vec3;

/**
 * Generates a <em>stepped-octagon</em> sand layer for shapes whose floor-adjacent corner width
 * differs from their mid-body width (the image-derived {@code FACETED}/{@code BASTION} shapes).
 *
 * <p>Where {@link SandGeometryGenerator} produces a single square base sand inset by the profile's
 * <em>last</em> row, this generator instead applies the full {@link CornerTaperProfile} along the
 * horizontal Z axis: the sand's west/east inset at each Z row is that row's taper value
 * ({@code rowWidths[Z-1]}), so the sand's footprint is a stepped octagon whose steps match the
 * corner posts' vertical taper (see the {@code tank-shape-image-to-datagen} skill, "stepped
 * octagon / 2D taper"). Consecutive equal-inset Z rows are merged into single boxes, so a 14-row
 * profile produces only its distinct-step count of boxes, not 14.
 *
 * <p>The sand sits on the floor slab at y=1..2, exactly like {@link SandGeometryGenerator}; a
 * shape with a thicker base (a {@code 16} = full-width floor-adjacent row) must instead be given a
 * chamfered base frame so the sand is not occluded — handled by the frame generator, not here.
 */
public final class SteppedSandGeometryGenerator {
    public static final String DEFAULT_TEXTURE = "block/sand";

    private SteppedSandGeometryGenerator() {}

    public static JsonObject generate(int permutationIndex, CornerTaperProfile profile) {
        return generate(permutationIndex, DEFAULT_TEXTURE, profile);
    }

    public static JsonObject generate(int permutationIndex, String textureId, CornerTaperProfile profile) {
        Set<TankFace> openFaces = TankFace.fromPermutationIndex(permutationIndex);
        int[] taper = profile.rowWidths(); // 14 entries, image rows 1-14 (top-to-bottom)
        int floor = 1;

        JsonObject model = baseModel(textureId);
        JsonArray elements = new JsonArray();

        // Sand only exists when the DOWN face is closed (otherwise it falls into the tank below).
        if (!openFaces.contains(TankFace.DOWN)) {
            boolean northOpen = openFaces.contains(TankFace.NORTH);
            boolean southOpen = openFaces.contains(TankFace.SOUTH);
            boolean westOpen = openFaces.contains(TankFace.WEST);
            boolean eastOpen = openFaces.contains(TankFace.EAST);

            // The sand's footprint: for Z in 1..14 the west/east inset is taper[Z-1]. Merge
            // consecutive equal-inset rows into a single box (Z from run start to run end).
            for (SandRun run : sandRuns(taper)) {
                int inset = run.inset;
                if (inset >= 16) {
                    continue; // full-width row — no sand in that band
                }
                int z1 = run.zFrom;
                int z2 = run.zTo;
                int x1 = inset;
                int x2 = 16 - inset;
                elements.add(createSandBox("step_" + inset + "_" + z1, x1, floor, z1, x2, floor + 1, z2));
            }

            // Extend toward each open cardinal face: bridge the sand's footprint to the glass inner
            // face (1 / 15) on the open side, because the frame/glass on that side is gone.
            int northEdge = 15;
            int southEdge = 1;
            for (SandRun run : sandRuns(taper)) {
                if (run.inset < 16) {
                    northEdge = Math.min(northEdge, run.zFrom);
                    southEdge = Math.max(southEdge, run.zTo);
                }
            }
            if (northOpen) {
                addNorthBridge(elements, floor, northEdge);
            }
            if (southOpen) {
                addSouthBridge(elements, floor, southEdge);
            }
            if (westOpen) {
                addWestBridge(elements, taper, floor);
            }
            if (eastOpen) {
                addEastBridge(elements, taper, floor);
            }
        }

        model.add("elements", elements);
        addSingleGroup(model, "sand_" + permutationIndex);
        return model;
    }

    /** A merged run of equal-inset Z rows: sand spans Z {@code [zFrom, zTo)}, inset {@code inset}. */
    private record SandRun(int zFrom, int zTo, int inset) {}

    /** Merge the 14-row taper (image rows 1-14 → sand Z rows 1-15) into equal-inset runs. */
    private static List<SandRun> sandRuns(int[] taper) {
        List<SandRun> runs = new ArrayList<>();
        int runStartZ = 1;
        int runInset = taper[0];
        for (int z = 2; z <= 14; z++) {
            int inset = taper[z - 1];
            if (inset != runInset) {
                runs.add(new SandRun(runStartZ, z, runInset));
                runStartZ = z;
                runInset = inset;
            }
        }
        runs.add(new SandRun(runStartZ, 15, runInset));
        return runs;
    }

    /**
     * When NORTH is open, the frame's north wall is gone and the west/east glass (if closed) is the
     * only remaining boundary, so the sand reaches X=1..15 (the glass inner face) from z=0 to the
     * sand's north edge.
     */
    private static void addNorthBridge(JsonArray elements, int floor, int northEdge) {
        elements.add(createSandBox("north_bridge", 1, floor, 0, 15, floor + 1, northEdge));
    }

    private static void addSouthBridge(JsonArray elements, int floor, int southEdge) {
        elements.add(createSandBox("south_bridge", 1, floor, southEdge, 15, floor + 1, 16));
    }

    private static void addWestBridge(JsonArray elements, int[] taper, int floor) {
        // The west edge of the stepped footprint: for each Z row the sand starts at x = taper[Z-1].
        // Bridging to x=0 means adding the triangular-ish notch; simplest correct bridge is a thin
        // column that fills the widest remaining gap on the west side. To keep the footprint
        // exact, emit one box per Z run between x=1 (glass inner face) and the run's inset.
        int prevInset = taper[0];
        int runStartZ = 1;
        for (int z = 2; z <= 14; z++) {
            int inset = taper[z - 1];
            if (inset != prevInset) {
                addWestRunBridge(elements, runStartZ, z, prevInset, floor);
                runStartZ = z;
                prevInset = inset;
            }
        }
        addWestRunBridge(elements, runStartZ, 15, prevInset, floor);
    }

    private static void addWestRunBridge(JsonArray elements, int z1, int z2, int inset, int floor) {
        // inset >= 16: no sand in that band (a full-width taper row).
        if (inset >= 16) return;
        elements.add(createSandBox("west_bridge_" + z1, 0, floor, z1, inset, floor + 1, z2));
    }

    private static void addEastBridge(JsonArray elements, int[] taper, int floor) {
        int prevInset = taper[0];
        int runStartZ = 1;
        for (int z = 2; z <= 14; z++) {
            int inset = taper[z - 1];
            if (inset != prevInset) {
                addEastRunBridge(elements, runStartZ, z, prevInset, floor);
                runStartZ = z;
                prevInset = inset;
            }
        }
        addEastRunBridge(elements, runStartZ, 15, prevInset, floor);
    }

    private static void addEastRunBridge(JsonArray elements, int z1, int z2, int inset, int floor) {
        if (inset >= 16) return;
        elements.add(createSandBox("east_bridge_" + z1, 16 - inset, floor, z1, 16, floor + 1, z2));
    }

    /** Full 6-faced sand box, UV convention matching {@link SandGeometryGenerator#createSandBox}. */
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
