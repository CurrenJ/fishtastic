package grill24.fishtastic.shapegen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Sweeps every shipped tank shape ({@link TankShapeGeometryStrategies#ALL}) across all 64
 * connection permutations and checks structural safety invariants that four real shipped bugs
 * violated before their fixes (see the tank-shape-variants project notes, 2026-08-13/14):
 *
 * <ol>
 *   <li><b>No frame/glass/sand volume overlap.</b> Two of these three parts sharing volume means
 *   either a translucent glass pane hidden behind opaque frame, or a real glass pane poking through
 *   solid frame — exactly how the BASTION north+south / east+west "frame-glass clipping" bug showed
 *   up (an accidental corner-trim degeneracy that stopped protecting once both ends of the
 *   perpendicular axis opened at once).</li>
 *   <li><b>No bare gaps in the floor.</b> Wherever DOWN is closed, the frame+sand+glass union must
 *   fully tile the floor Y-band (Y 1-2) with no empty cell — sand is designed to fill the *entire*
 *   footprint there (unlike every other Y-band, where the tank's water-filled interior is
 *   legitimately uncovered), so this is a full-16x16 check. A gap here is exactly what the
 *   stranded-floor-frame, missing-ring, and all-four-open corner-hole bugs looked like: a piece that
 *   correctly vanished on an open face without another piece extending to take over its
 *   territory.</li>
 *   <li><b>No bare gaps in the outer wall skin at any Y-band.</b> Away from the floor, only the
 *   outer 1px perimeter needs checking (the interior is meant to be open water) — and only where the
 *   perimeter cell's owning face is actually closed, since an open face legitimately leaves its own
 *   edge bare. A corner cell is only required when at least one of its two owning faces stays closed
 *   (the still-standing wall's frame/glass extends to cover the corner — the same "seam extension"
 *   invariant used throughout these generators); it's legitimately empty only when both open at
 *   once. Exercises BASTION's chamfered ring at the ceiling band too, which shares the exact same
 *   code path as the floor ring but had no dedicated bug report to prove it against.</li>
 * </ol>
 *
 * <p>Sweeps the same {@link TankShapeGeometryStrategies#ALL} list Fabric datagen consumes, so a new
 * shape gets this coverage automatically the moment it's added there — there's no separate shape
 * list here to fall out of sync. One {@link DynamicTest} per shape/permutation pair per check so a
 * failure names exactly which one broke, rather than reporting through a single pass/fail bit.
 */
class TankShapeConnectivitySafetyTest {

    private record Box(double x1, double y1, double z1, double x2, double y2, double z2) {
        boolean overlaps(Box o) {
            double ox1 = Math.max(x1, o.x1), ox2 = Math.min(x2, o.x2);
            double oy1 = Math.max(y1, o.y1), oy2 = Math.min(y2, o.y2);
            double oz1 = Math.max(z1, o.z1), oz2 = Math.min(z2, o.z2);
            // Strict overlap only — boxes merely touching along a shared face (e.g. the floor slab
            // meeting the sand layer directly above it) have zero volume in common and are fine.
            return ox1 < ox2 - 1e-9 && oy1 < oy2 - 1e-9 && oz1 < oz2 - 1e-9;
        }
    }

    @TestFactory
    Stream<DynamicTest> noPairwiseVolumeOverlap() {
        List<DynamicTest> tests = new ArrayList<>();
        for (TankShapeGeometryStrategies.Strategy shape : TankShapeGeometryStrategies.ALL) {
            for (int perm = 0; perm < 64; perm++) {
                int p = perm;
                tests.add(dynamicTest(shape.name() + " perm " + p + " " + TankFace.fromPermutationIndex(p), () -> {
                    List<Box> frame = boxes(shape.frame().apply(p));
                    List<Box> glass = boxes(shape.glass().apply(p));
                    List<Box> sand = boxes(shape.sand().apply(p));
                    assertNoOverlap("frame", frame, "glass", glass);
                    assertNoOverlap("frame", frame, "sand", sand);
                    assertNoOverlap("glass", glass, "sand", sand);
                }));
            }
        }
        return tests.stream();
    }

    @TestFactory
    Stream<DynamicTest> floorHasNoGapsWhenDownClosed() {
        List<DynamicTest> tests = new ArrayList<>();
        for (TankShapeGeometryStrategies.Strategy shape : TankShapeGeometryStrategies.ALL) {
            for (int perm = 0; perm < 64; perm++) {
                if (TankFace.fromPermutationIndex(perm).contains(TankFace.DOWN)) continue;
                int p = perm;
                tests.add(dynamicTest(shape.name() + " perm " + p + " " + TankFace.fromPermutationIndex(p), () -> {
                    boolean[][] covered = coverage(shape, p, 1.0, 2.0);
                    List<String> gaps = new ArrayList<>();
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            if (!covered[x][z]) gaps.add("(" + x + "," + z + ")");
                        }
                    }
                    assertTrue(gaps.isEmpty(), shape.name() + " perm " + p + " (" + TankFace.fromPermutationIndex(p)
                            + ") has bare floor gaps at " + gaps);
                }));
            }
        }
        return tests.stream();
    }

    /**
     * Every distinct Y-value that appears as a box boundary in any shape's frame model — the set of
     * Y-bands worth checking the wall skin at, so this doesn't have to hardcode "the ceiling band"
     * as one arbitrary example and miss the rest.
     */
    @TestFactory
    Stream<DynamicTest> outerWallSkinHasNoGapsAtAnyBand() {
        List<DynamicTest> tests = new ArrayList<>();
        for (TankShapeGeometryStrategies.Strategy shape : TankShapeGeometryStrategies.ALL) {
            List<Double> yBounds = distinctYBoundaries(shape);
            for (int perm = 0; perm < 64; perm++) {
                int p = perm;
                var openFaces = TankFace.fromPermutationIndex(p);
                boolean westOpen = openFaces.contains(TankFace.WEST);
                boolean eastOpen = openFaces.contains(TankFace.EAST);
                boolean northOpen = openFaces.contains(TankFace.NORTH);
                boolean southOpen = openFaces.contains(TankFace.SOUTH);

                for (int i = 0; i < yBounds.size() - 1; i++) {
                    double yLo = yBounds.get(i);
                    double yHi = yBounds.get(i + 1);
                    tests.add(dynamicTest(shape.name() + " perm " + p + " " + openFaces + " y[" + yLo + "," + yHi + ")", () -> {
                        boolean[][] covered = coverage(shape, p, yLo, yHi);
                        List<String> gaps = new ArrayList<>();
                        for (int x = 0; x < 16; x++) {
                            for (int z = 0; z < 16; z++) {
                                if (!requiresCoverage(x, z, westOpen, eastOpen, northOpen, southOpen)) continue;
                                if (!covered[x][z]) gaps.add("(" + x + "," + z + ")");
                            }
                        }
                        assertTrue(gaps.isEmpty(), shape.name() + " perm " + p + " (" + openFaces + ") y[" + yLo + "," + yHi
                                + ") has bare wall-skin gaps at " + gaps);
                    }));
                }
            }
        }
        return tests.stream();
    }

    /**
     * Whether the outer-perimeter cell {@code (x,z)} needs coverage: interior cells never do (the
     * tank's water-filled body is legitimately open away from the floor); a straight-edge perimeter
     * cell needs it iff its one owning face is closed; a corner cell needs it unless *both* its
     * owning faces are open (a still-closed wall's frame/glass extends to cover the corner — the
     * same seam-extension invariant the generators already rely on elsewhere).
     */
    private static boolean requiresCoverage(int x, int z, boolean westOpen, boolean eastOpen, boolean northOpen, boolean southOpen) {
        boolean west = x == 0, east = x == 15, north = z == 0, south = z == 15;
        if (west && north) return !(westOpen && northOpen);
        if (west && south) return !(westOpen && southOpen);
        if (east && north) return !(eastOpen && northOpen);
        if (east && south) return !(eastOpen && southOpen);
        if (west) return !westOpen;
        if (east) return !eastOpen;
        if (north) return !northOpen;
        if (south) return !southOpen;
        return false; // interior cell
    }

    private static List<Double> distinctYBoundaries(TankShapeGeometryStrategies.Strategy shape) {
        // Permutation 0 (everything closed) has the richest set of elements/bands across every
        // shape's frame generator.
        TreeSet<Double> ys = new TreeSet<>();
        for (Box box : boxes(shape.frame().apply(0))) {
            ys.add(box.y1());
            ys.add(box.y2());
        }
        return new ArrayList<>(ys);
    }

    private static boolean[][] coverage(TankShapeGeometryStrategies.Strategy shape, int perm, double yLo, double yHi) {
        boolean[][] covered = new boolean[16][16]; // [x][z]
        cover(covered, shape.frame().apply(perm), yLo, yHi);
        cover(covered, shape.sand().apply(perm), yLo, yHi);
        cover(covered, shape.glass().apply(perm), yLo, yHi);
        return covered;
    }

    private static void assertNoOverlap(String nameA, List<Box> a, String nameB, List<Box> b) {
        for (Box ba : a) {
            for (Box bb : b) {
                assertTrue(!ba.overlaps(bb), nameA + " box " + ba + " overlaps " + nameB + " box " + bb);
            }
        }
    }

    private static void cover(boolean[][] covered, JsonObject model, double yLo, double yHi) {
        for (Box box : boxes(model)) {
            if (box.y2() <= yLo || box.y1() >= yHi) continue; // doesn't intersect this Y-band
            int x1 = Math.max(0, (int) Math.floor(box.x1()));
            int x2 = Math.min(16, (int) Math.ceil(box.x2()));
            int z1 = Math.max(0, (int) Math.floor(box.z1()));
            int z2 = Math.min(16, (int) Math.ceil(box.z2()));
            for (int x = x1; x < x2; x++) {
                for (int z = z1; z < z2; z++) {
                    covered[x][z] = true;
                }
            }
        }
    }

    private static List<Box> boxes(JsonObject model) {
        List<Box> boxes = new ArrayList<>();
        JsonArray elements = model.getAsJsonArray("elements");
        for (JsonElement el : elements) {
            JsonObject e = el.getAsJsonObject();
            JsonArray from = e.getAsJsonArray("from");
            JsonArray to = e.getAsJsonArray("to");
            boxes.add(new Box(
                    from.get(0).getAsDouble(), from.get(1).getAsDouble(), from.get(2).getAsDouble(),
                    to.get(0).getAsDouble(), to.get(1).getAsDouble(), to.get(2).getAsDouble()));
        }
        return boxes;
    }
}
