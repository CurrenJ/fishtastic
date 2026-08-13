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
        int floor = 1;

        JsonObject model = baseModel(textureId);
        JsonArray elements = new JsonArray();

        // Sand only exists when the DOWN face is closed (otherwise it falls into the tank below).
        if (!openFaces.contains(TankFace.DOWN)) {
            boolean northOpen = openFaces.contains(TankFace.NORTH);
            boolean southOpen = openFaces.contains(TankFace.SOUTH);
            boolean westOpen = openFaces.contains(TankFace.WEST);
            boolean eastOpen = openFaces.contains(TankFace.EAST);

            // An open north/south cap has nothing for that end's taper to flare into — same
            // "open-cap fallback" CornerTaperProfile already applies to the vertical ceiling/floor
            // taper, reused here for the horizontal one so the stepped octagon doesn't leave a
            // stray taper step (and the matching frame chamfer) stranded past the open boundary.
            int[] taper = profile.effectiveRowWidths(!northOpen, !southOpen);

            // The sand's footprint: for Z in 1..14 the west/east inset is taper[Z-1]. Merge
            // consecutive equal-inset rows into a single box (Z from run start to run end).
            List<SandRun> runs = sandRuns(taper);
            // Extend the boundary run flush to the block edge on an open north/south cap — mirrors
            // CornerTaperProfile.runs()'s seam extension for the vertical case — so a connected
            // neighbor's sand meets this tank's with zero gap.
            if (northOpen && !runs.isEmpty() && runs.get(0).inset < 16) {
                SandRun first = runs.get(0);
                runs.set(0, new SandRun(0, first.zTo, first.inset));
            }
            if (southOpen && !runs.isEmpty() && runs.get(runs.size() - 1).inset < 16) {
                SandRun last = runs.get(runs.size() - 1);
                runs.set(runs.size() - 1, new SandRun(last.zFrom, 16, last.inset));
            }
            for (SandRun run : runs) {
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

            // West/east still need a separate bridge: the taper there varies per Z row rather than
            // collapsing to one flattened run, so there's no single run to extend to the boundary.
            if (westOpen) {
                addWestBridge(elements, taper, floor, northOpen, southOpen);
            }
            if (eastOpen) {
                addEastBridge(elements, taper, floor, northOpen, southOpen);
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

    private static void addWestBridge(JsonArray elements, int[] taper, int floor, boolean northOpen, boolean southOpen) {
        // The west edge of the stepped footprint: for each Z row the sand starts at x = taper[Z-1].
        // Bridging to x=0 means adding the triangular-ish notch; simplest correct bridge is a thin
        // column that fills the widest remaining gap on the west side. To keep the footprint
        // exact, emit one box per Z run between x=1 (glass inner face) and the run's inset.
        for (int[] zRun : bridgeRuns(taper, northOpen, southOpen)) {
            int inset = zRun[2];
            if (inset >= 16) continue; // no sand in that band (a full-width taper row)
            elements.add(createSandBox("west_bridge_" + zRun[0], 0, floor, zRun[0], inset, floor + 1, zRun[1]));
        }
    }

    private static void addEastBridge(JsonArray elements, int[] taper, int floor, boolean northOpen, boolean southOpen) {
        for (int[] zRun : bridgeRuns(taper, northOpen, southOpen)) {
            int inset = zRun[2];
            if (inset >= 16) continue;
            elements.add(createSandBox("east_bridge_" + zRun[0], 16 - inset, floor, zRun[0], 16, floor + 1, zRun[1]));
        }
    }

    /**
     * Per-Z-row taper runs as {@code {zFrom, zTo, inset}}, with the leading/trailing run extended
     * to the block boundary when the corresponding north/south cap is also open — otherwise, on a
     * tank with every horizontal face open at once, the extreme corner tiles (Z 0-1 / 15-16 at the
     * west/east margin) are covered by neither this bridge nor the main sand run, leaving a bare
     * gap in the floor.
     */
    private static List<int[]> bridgeRuns(int[] taper, boolean northOpen, boolean southOpen) {
        List<int[]> runs = new ArrayList<>();
        int prevInset = taper[0];
        int runStartZ = 1;
        for (int z = 2; z <= 14; z++) {
            int inset = taper[z - 1];
            if (inset != prevInset) {
                runs.add(new int[]{runStartZ, z, prevInset});
                runStartZ = z;
                prevInset = inset;
            }
        }
        runs.add(new int[]{runStartZ, 15, prevInset});
        if (northOpen) runs.get(0)[0] = 0;
        if (southOpen) runs.get(runs.size() - 1)[1] = 16;
        return runs;
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
