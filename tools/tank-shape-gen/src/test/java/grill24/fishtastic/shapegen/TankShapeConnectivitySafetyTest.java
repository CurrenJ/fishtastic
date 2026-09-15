package grill24.fishtastic.shapegen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    /** See {@link #floorHasNoGapsWhenDownClosed}'s exemption note. */
    private static boolean hasNoSandEver(TankShapeGeometryStrategies.Strategy shape) {
        for (int perm = 0; perm < 64; perm++) {
            if (!boxes(shape.sand().apply(perm)).isEmpty()) return false;
        }
        return true;
    }

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
            // A shape that never produces sand geometry at any permutation (e.g. VITRINE) glazes
            // the floor one Y-band lower instead — at Y[0,1], the true exterior face — leaving the
            // Y[1,2] band legitimately open water, same as every other interior Y-band. That's a
            // deliberate design, not the "piece vanished without another covering its territory"
            // bug class this check exists for, so such shapes are exempt from it. Detected
            // structurally (sand is empty at every permutation) rather than by name, so this stays
            // in sync automatically as shapes are added or changed.
            if (hasNoSandEver(shape)) continue;
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
     * Confirms an edge-diagonal frame beam never occupies volume the base bake's own glass already
     * fills at that permutation — the exact bug class a full-length (untrimmed) beam produced: a
     * perpendicular closed wall's glass pane flush-extends into the corner cell whenever that
     * corner's own post is absent (see {@code TaperedFrameGeometryGenerator#generateEdgeFragment}'s
     * note), so a beam reaching that far double-covers it with opaque frame over translucent glass
     * and z-fights. The beam is inset at both perpendicular ends specifically to avoid this; this
     * test guards that inset against regressing back to a full-length beam.
     */
    @TestFactory
    Stream<DynamicTest> edgeFragmentNeverOverlapsBaseGlass() {
        List<DynamicTest> tests = new ArrayList<>();
        for (TankShapeGeometryStrategies.Strategy shape : TankShapeGeometryStrategies.ALL) {
            if (shape.edgeFragment() == null) continue;
            for (int perm = 0; perm < 64; perm++) {
                Set<TankFace> openFaces = TankFace.fromPermutationIndex(perm);
                for (TankEdge edge : TankEdge.values()) {
                    if (!edge.isEligible(openFaces)) continue;
                    int p = perm;
                    TankEdge e = edge;
                    tests.add(dynamicTest(shape.name() + " perm " + p + " " + openFaces + " edge " + e, () -> {
                        List<Box> fragment = boxes(shape.edgeFragment().apply(e));
                        List<Box> glass = boxes(shape.glass().apply(p));
                        assertNoOverlap("edge fragment", fragment, "glass", glass);
                    }));
                }
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
                                // A corner cell inside an eligible edge's cap band is covered by the
                                // frame beam (diagonal empty) or a glass-fill fragment (diagonal
                                // filled) instead of the base bake alone — see
                                // edgeDiagonalsHaveNoGapsWhenEdgeDiagonalEmpty/Filled, which already
                                // validate both of those; the base-bake-only invariant this test
                                // otherwise checks doesn't apply there.
                                if (isEdgeCapBandExemption(x, z, yLo, yHi, shape, openFaces)) continue;
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

    /**
     * Whether {@code (x,z)} falls inside an eligible edge's cap-band "reach square" at
     * {@code [yLo, yHi)} — i.e. whether coverage there depends on the edge-diagonal mask (frame
     * beam or glass-fill fragment) rather than the base bake alone. The reach isn't always a single
     * pixel — a {@code width > 1} shape (e.g. STURDY-family, width 2) has its beam's own perpendicular
     * thickness carve out a {@code width × width} square at the corner, not just the corner pixel —
     * so this derives {@code width} straight from the real edge fragment's own baked box rather than
     * assuming 1, and checks the Y-band against that same box's Y-extent, so it can't drift from the
     * actual geometry.
     */
    private static boolean isEdgeCapBandExemption(int x, int z, double yLo, double yHi,
                                                   TankShapeGeometryStrategies.Strategy shape, Set<TankFace> openFaces) {
        if (shape.edgeFragment() == null) return false;
        for (TankEdge edge : TankEdge.values()) {
            if (!edge.isEligible(openFaces)) continue;
            List<Box> boxes = boxes(shape.edgeFragment().apply(edge));
            if (boxes.isEmpty()) continue;
            Box beam = boxes.get(0);
            if (!(yLo >= beam.y1() - 1e-9 && yHi <= beam.y2() + 1e-9)) continue;
            double width = (edge.horizontal() == TankFace.NORTH || edge.horizontal() == TankFace.SOUTH)
                    ? beam.z2() - beam.z1()
                    : beam.x2() - beam.x1();
            for (TankCorner corner : edge.endCorners()) {
                boolean xInReach = corner.xEdge() == 0 ? (x < width) : (x >= 16 - width);
                boolean zInReach = corner.zEdge() == 0 ? (z < width) : (z >= 16 - width);
                if (xInReach && zInReach) return true;
            }
        }
        return false;
    }

    /**
     * Sweeps every shape whose frame generator has a combined-face corner gate (i.e. every
     * {@link TankShapeGeometryStrategies.Strategy} with a non-null {@code cornerFragment()}) across
     * every permutation/corner pair where that corner's two orthogonal faces are both open — the one
     * scenario a diagonal-empty neighbor cell needs the corner post composited back in for (see
     * docs on diagonal-aware corner posts). Confirms the fragment {@code cornerFragment()} produces,
     * merged onto the base frame/sand/glass for that permutation, actually closes the gap at that
     * corner cell at every Y-band — i.e. the fragment really is geometry-equivalent to "this corner's
     * post as if both faces were closed."
     */
    @TestFactory
    Stream<DynamicTest> diagonalCornersHaveNoGapsWhenDiagonalEmpty() {
        List<DynamicTest> tests = new ArrayList<>();
        for (TankShapeGeometryStrategies.Strategy shape : TankShapeGeometryStrategies.ALL) {
            if (shape.cornerFragment() == null) continue;
            List<Double> yBounds = distinctYBoundaries(shape);
            for (int perm = 0; perm < 64; perm++) {
                Set<TankFace> openFaces = TankFace.fromPermutationIndex(perm);
                for (TankCorner corner : TankCorner.values()) {
                    if (!corner.isOrthogonallyEligible(openFaces)) continue;
                    int p = perm;
                    TankCorner c = corner;
                    tests.add(dynamicTest(shape.name() + " perm " + p + " " + openFaces + " corner " + c, () -> {
                        boolean ceilingClosed = !openFaces.contains(TankFace.UP);
                        boolean floorClosed = !openFaces.contains(TankFace.DOWN);
                        int capState = TankCorner.capState(ceilingClosed, floorClosed);
                        JsonObject fragment = shape.cornerFragment().apply(c, capState);

                        int x = c.xEdge() == 0 ? 0 : 15;
                        int z = c.zEdge() == 0 ? 0 : 15;

                        for (int i = 0; i < yBounds.size() - 1; i++) {
                            double yLo = yBounds.get(i);
                            double yHi = yBounds.get(i + 1);
                            boolean[][] covered = new boolean[16][16];
                            cover(covered, shape.frame().apply(p), yLo, yHi);
                            cover(covered, shape.sand().apply(p), yLo, yHi);
                            cover(covered, shape.glass().apply(p), yLo, yHi);
                            cover(covered, fragment, yLo, yHi);
                            assertTrue(covered[x][z], shape.name() + " perm " + p + " (" + openFaces + ") corner " + c
                                    + " y[" + yLo + "," + yHi + ") still has a gap at (" + x + "," + z
                                    + ") even with its corner fragment composited in");
                        }
                    }));
                }
            }
        }
        return tests.stream();
    }

    /**
     * Sweeps every shape whose frame generator supports edge-diagonal frame beams (i.e. every
     * {@link TankShapeGeometryStrategies.Strategy} with a non-null {@code edgeFragment()}) across
     * every permutation/edge pair where that edge's horizontal and vertical faces are both open —
     * the scenario an empty edge-diagonal neighbor cell needs the frame beam composited back in for
     * (see the edge-diagonal frame beam fix design doc). Confirms the fragment {@code
     * edgeFragment()} produces, merged onto the base frame/sand/glass for that permutation, actually
     * closes the gap along that edge's beam run at every Y-band spot-checked (not just a single
     * pixel, since this is a beam spanning a length).
     */
    @TestFactory
    Stream<DynamicTest> edgeDiagonalsHaveNoGapsWhenEdgeDiagonalEmpty() {
        List<DynamicTest> tests = new ArrayList<>();
        for (TankShapeGeometryStrategies.Strategy shape : TankShapeGeometryStrategies.ALL) {
            if (shape.edgeFragment() == null) continue;
            for (int perm = 0; perm < 64; perm++) {
                Set<TankFace> openFaces = TankFace.fromPermutationIndex(perm);
                for (TankEdge edge : TankEdge.values()) {
                    if (!edge.isEligible(openFaces)) continue;
                    int p = perm;
                    TankEdge e = edge;
                    tests.add(dynamicTest(shape.name() + " perm " + p + " " + openFaces + " edge " + e, () -> {
                        JsonObject fragment = shape.edgeFragment().apply(e);

                        // Sample points along the beam's run: the two ends and the middle of the
                        // edge, on the vertical band the beam occupies.
                        double y = e.vertical() == TankFace.DOWN ? 0.5 : 15.5;
                        int[] xs, zs;
                        if (e.horizontal() == TankFace.NORTH || e.horizontal() == TankFace.SOUTH) {
                            xs = new int[]{0, 8, 15};
                            zs = new int[]{e.horizontal() == TankFace.NORTH ? 0 : 15};
                        } else {
                            xs = new int[]{e.horizontal() == TankFace.WEST ? 0 : 15};
                            zs = new int[]{0, 8, 15};
                        }

                        boolean[][] covered = new boolean[16][16];
                        cover(covered, shape.frame().apply(p), y, y + 1e-6);
                        cover(covered, shape.sand().apply(p), y, y + 1e-6);
                        cover(covered, shape.glass().apply(p), y, y + 1e-6);
                        cover(covered, fragment, y, y + 1e-6);

                        // The edge beam is deliberately inset at both perpendicular ends (see
                        // TaperedFrameGeometryGenerator#generateEdgeFragment's note) — those end
                        // cells are covered instead by whichever corner mechanism actually applies
                        // there in the real composited render: the corner-diagonal fragment, when
                        // that corner's own two orthogonal faces are both open too. Mirror that here
                        // by compositing it under the same worst-case assumption the corner-diagonal
                        // test itself uses (diagonal empty, so the override fires).
                        boolean ceilingClosed = !openFaces.contains(TankFace.UP);
                        boolean floorClosed = !openFaces.contains(TankFace.DOWN);
                        int capState = TankCorner.capState(ceilingClosed, floorClosed);
                        for (TankCorner corner : e.endCorners()) {
                            if (shape.cornerFragment() != null && corner.isOrthogonallyEligible(openFaces)) {
                                cover(covered, shape.cornerFragment().apply(corner, capState), y, y + 1e-6);
                            }
                        }

                        List<String> gaps = new ArrayList<>();
                        for (int x : xs) {
                            for (int z : zs) {
                                if (!covered[x][z]) gaps.add("(" + x + "," + z + ")");
                            }
                        }
                        assertTrue(gaps.isEmpty(), shape.name() + " perm " + p + " (" + openFaces + ") edge " + e
                                + " still has gaps at " + gaps + " even with its edge fragment composited in");
                    }));
                }
            }
        }
        return tests.stream();
    }

    /**
     * The mirror image of {@link #edgeDiagonalsHaveNoGapsWhenEdgeDiagonalEmpty}: sweeps the same
     * shape/permutation/edge space, but for the case the edge-diagonal cell IS filled by a real
     * neighbor tank — meaning the beam itself does <em>not</em> render (see
     * {@code FishTankCompositeModelData#getEdgeDiagonalGlassFillMask}, the inverse of the beam's own
     * override mask). Confirms the base frame/sand/glass bake, plus the glass-fill fragment(s) for
     * whichever of the edge's two end corners actually has a closed perpendicular wall, still fully
     * covers that wall's corner cell — i.e. the fragment restores exactly what the base glass bake's
     * cap-band split (see {@code TaperedGlassGeometryGenerator#splitRunForCapBands}) removed.
     */
    @TestFactory
    Stream<DynamicTest> edgeDiagonalsHaveNoGapsWhenEdgeDiagonalFilled() {
        List<DynamicTest> tests = new ArrayList<>();
        for (TankShapeGeometryStrategies.Strategy shape : TankShapeGeometryStrategies.ALL) {
            if (shape.edgeGlassFillFragment() == null) continue;
            for (int perm = 0; perm < 64; perm++) {
                Set<TankFace> openFaces = TankFace.fromPermutationIndex(perm);
                for (TankEdge edge : TankEdge.values()) {
                    if (!edge.isEligible(openFaces)) continue;
                    int p = perm;
                    TankEdge e = edge;
                    tests.add(dynamicTest(shape.name() + " perm " + p + " " + openFaces + " edge " + e, () -> {
                        double y = e.vertical() == TankFace.DOWN ? 0.5 : 15.5;

                        boolean[][] covered = new boolean[16][16];
                        cover(covered, shape.frame().apply(p), y, y + 1e-6);
                        cover(covered, shape.sand().apply(p), y, y + 1e-6);
                        cover(covered, shape.glass().apply(p), y, y + 1e-6);
                        // No edge frame fragment here — the whole point is that it does NOT render
                        // when the diagonal is filled.

                        for (TankCorner corner : e.endCorners()) {
                            TankFace wall = corner.faceA() == e.horizontal() ? corner.faceB() : corner.faceA();
                            if (openFaces.contains(wall)) continue; // wall open — nothing to restore
                            cover(covered, shape.edgeGlassFillFragment().apply(e, corner), y, y + 1e-6);

                            int x = corner.xEdge() == 0 ? 0 : 15;
                            int z = corner.zEdge() == 0 ? 0 : 15;
                            assertTrue(covered[x][z], shape.name() + " perm " + p + " (" + openFaces + ") edge " + e
                                    + " corner " + corner + " still has a gap at (" + x + "," + z
                                    + ") even with its glass-fill fragment composited in");
                        }
                    }));
                }
            }
        }
        return tests.stream();
    }

    /**
     * Guards the manual audit behind {@code TankShapeGeometryStrategies}'s null {@code
     * edgeFragment()} entries: each of these shapes was confirmed to have no edge-beam support yet
     * (permanently excluded, no taper/plate abstraction to borrow from, or deferred pending
     * hand-authored geometry) and the known edge-diagonal gap still reproduces at one representative
     * eligible permutation/edge. This is an intentional "expected failure, not a pass" tracking
     * assertion — it starts failing the moment someone adds edge-fragment support for that shape,
     * which is the signal to remove it from the exclusion list.
     */
    @Test
    void excludedShapesHaveKnownEdgeDiagonalGapsDocumented() {
        for (TankShapeGeometryStrategies.Strategy shape : TankShapeGeometryStrategies.ALL) {
            if (shape.edgeFragment() != null) continue;

            // Representative permutation: EAST and DOWN open (the repro from the design doc's
            // Context section), checked at TankEdge.EAST_DOWN.
            int perm = (1 << TankFace.EAST.ordinal()) | (1 << TankFace.DOWN.ordinal());
            TankEdge edge = TankEdge.EAST_DOWN;
            Set<TankFace> openFaces = TankFace.fromPermutationIndex(perm);
            assertTrue(edge.isEligible(openFaces), shape.name() + ": expected EAST_DOWN eligible at this permutation");

            boolean[][] covered = new boolean[16][16];
            cover(covered, shape.frame().apply(perm), 0.0, 1e-6);
            cover(covered, shape.sand().apply(perm), 0.0, 1e-6);
            cover(covered, shape.glass().apply(perm), 0.0, 1e-6);

            // The gap should still be present at the east edge's midpoint (z=8) — a spot along the
            // missing beam's run away from the corners, which the corner-post fragments (if any)
            // don't reach and a closed perpendicular wall's own extension can't coincidentally cover.
            assertFalse(covered[15][8], shape.name()
                    + " no longer has the known edge-diagonal gap at EAST_DOWN — if edge-fragment support "
                    + "was added for this shape, remove it from the null-edgeFragment() exclusion list");
        }
    }

    /**
     * Guards the manual audit behind {@code TankShapeGeometryStrategies}'s null {@code
     * cornerFragment()} entries: each of these shapes' frame generator was confirmed, by reading the
     * source, to gate its corner posts independently per face rather than on both faces at once —
     * the one pattern that can leave a real gap when a diagonal neighbor tank is missing. If a future
     * edit introduces that combined gate here without also adding corner-fragment support, this test
     * catches it instead of silently shipping a corner-post gap.
     */
    private static final Map<String, List<String>> SHAPES_WITHOUT_CORNER_FRAGMENT_SOURCE_FILES = Map.of(
            "bramble", List.of("BrambleFrameGeometryGenerator.java"),
            "tooth", List.of("CombFrameGeometryGenerator.java"),
            "film", List.of("CombFrameGeometryGenerator.java"),
            "arch", List.of("ArchFrameGeometryGenerator.java"),
            "mullion", List.of("MullionFrameGeometryGenerator.java"),
            "lattice", List.of("LatticeFrameGeometryGenerator.java")
    );

    private static final Pattern COMBINED_CORNER_GATE = Pattern.compile(
            "!openFaces\\.contains\\(TankFace\\.(NORTH|SOUTH)\\)\\s*&&\\s*!openFaces\\.contains\\(TankFace\\.(WEST|EAST)\\)");

    @Test
    void shapesWithoutCornerFragmentSupportHaveNoCombinedFaceCornerGate() throws IOException {
        Path srcDir = Path.of("src/main/java/grill24/fishtastic/shapegen");
        for (Map.Entry<String, List<String>> entry : SHAPES_WITHOUT_CORNER_FRAGMENT_SOURCE_FILES.entrySet()) {
            TankShapeGeometryStrategies.Strategy strategy = TankShapeGeometryStrategies.byName(entry.getKey());
            assertNull(strategy.cornerFragment(), entry.getKey()
                    + " now has corner-fragment support — remove it from this guardrail's exemption list");
            for (String fileName : entry.getValue()) {
                String source = Files.readString(srcDir.resolve(fileName));
                assertFalse(COMBINED_CORNER_GATE.matcher(source).find(), fileName
                        + " now has a combined-face corner gate but no corner-fragment support was added for '"
                        + entry.getKey() + "' — see docs on diagonal-aware corner posts");
            }
        }
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
