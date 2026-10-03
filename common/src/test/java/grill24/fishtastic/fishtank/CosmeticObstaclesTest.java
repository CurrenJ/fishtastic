package grill24.fishtastic.fishtank;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The obstacle boxes derived from every shipped structure (docs/fish-shelters.md §12.3), checked
 * against the structure itself: what is drawn solid is covered, what is soft is not, no pocket is
 * left that a fish could not get out of, and the openings fish do use stay open.
 */
class CosmeticObstaclesTest {

    private static final Predicate<BlockState> SOFT = ShippedStructures.softTag();

    private static List<ObstacleGeometry.Box> boxes(CosmeticStructure structure) {
        return CosmeticObstacles.derive(structure, SOFT);
    }

    /** Build-unit min corner of a part's block, as the renderer places it (unrounded). */
    private static float[] corner(CosmeticStructure structure, CosmeticStructure.StructurePart part) {
        float xz = structure.span().isPresent() ? 1f : structure.scale() / (float) CosmeticGridCell.CELL_WIDTH;
        return new float[]{part.offsetX() / xz - 0.5f, part.offsetY(), part.offsetZ() / xz - 0.5f};
    }

    private static boolean covered(List<ObstacleGeometry.Box> boxes, float x, float y, float z) {
        for (ObstacleGeometry.Box b : boxes) {
            float[] f = b.buildBounds();
            if (x >= f[0] && x <= f[3] && y >= f[1] && y <= f[4] && z >= f[2] && z <= f[5]) return true;
        }
        return false;
    }

    private static boolean inHull(CosmeticStructure structure, float x, float y, float z) {
        return structure.shelterShapes().stream().map(CosmeticStructure.DerivedShelter::shape)
                .anyMatch(s -> x >= s.hullMin().x() - 0.5f && x <= s.hullMax().x() + 0.5f
                        && y >= s.hullMin().y() && y <= s.hullMax().y() + 1
                        && z >= s.hullMin().z() - 0.5f && z <= s.hullMax().z() + 0.5f);
    }

    /** Sampled points checked over the whole shipped set — see {@link #theCoverageCheckIsNotVacuous}. */
    private static int totalChecked = 0;

    /**
     * Every solid part's drawn shape lies inside the boxes — sampled at the corners and centre of
     * each of its shape boxes, pulled in a hair so a shared face is not a miss — except inside a
     * shelter's hull, which the shelter carries.
     */
    @TestFactory
    List<DynamicTest> everySolidShapeIsCovered() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Map.Entry<String, CosmeticStructure> e : ShippedStructures.all().entrySet()) {
            tests.add(DynamicTest.dynamicTest(e.getKey(), () -> {
                CosmeticStructure structure = e.getValue();
                List<ObstacleGeometry.Box> boxes = boxes(structure);
                int checked = 0;
                int solidParts = 0;
                for (CosmeticStructure.StructurePart part : structure.parts()) {
                    if (SOFT.test(part.state())) continue;
                    solidParts++;
                    float[] c = corner(structure, part);
                    for (AABB a : part.state().getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs()) {
                        float eps = 1e-3f;
                        float[] xs = {(float) a.minX + eps, (float) (a.minX + a.maxX) / 2, (float) a.maxX - eps};
                        float[] ys = {(float) a.minY + eps, (float) (a.minY + a.maxY) / 2, (float) a.maxY - eps};
                        float[] zs = {(float) a.minZ + eps, (float) (a.minZ + a.maxZ) / 2, (float) a.maxZ - eps};
                        for (float x : xs) for (float y : ys) for (float z : zs) {
                            float bx = c[0] + x, by = c[1] + y, bz = c[2] + z;
                            if (inHull(structure, bx, by, bz)) continue;
                            checked++;
                            assertTrue(covered(boxes, bx, by, bz), part.state() + " at " + bx + "," + by + "," + bz + " is not covered");
                        }
                    }
                }
                if (!structure.hasShelter()) {
                    // A structure with no hull skips no point, so every solid part must be checked.
                    // One with a hull legitimately checks nothing when all its parts sit inside it
                    // (the Clay Pipe's walls are its shelter); the set-wide total below is its guard.
                    assertTrue(checked > 0 || solidParts == 0,
                            e.getKey() + ": " + solidParts + " solid parts, none of them checked");
                }
                totalChecked += checked;
            }));
        }
        return tests;
    }

    /** The per-structure guard cannot speak for hulled structures, so the set carries one. */
    @AfterAll
    static void theCoverageCheckIsNotVacuous() {
        assertTrue(totalChecked >= 500, "only " + totalChecked + " sampled points were checked in all");
    }

    @Test
    void softOnlyStructuresHaveNoObstacles() {
        for (String name : new String[]{"petals", "leaf_litter"}) {
            assertTrue(boxes(ShippedStructures.all().get(name)).isEmpty(), name);
        }
    }

    @Test
    void aTreeIsItsTrunkNotItsCrown() {
        CosmeticStructure tree = ShippedStructures.all().get("oak_tree");
        List<ObstacleGeometry.Box> boxes = boxes(tree);
        for (CosmeticStructure.StructurePart part : tree.parts()) {
            float[] c = corner(tree, part);
            boolean leaves = SOFT.test(part.state());
            boolean anyTrunkHere = tree.parts().stream().anyMatch(o -> !SOFT.test(o.state())
                    && java.util.Arrays.equals(corner(tree, o), c));
            if (leaves && !anyTrunkHere) {
                assertFalse(covered(boxes, c[0] + 0.5f, c[1] + 0.5f, c[2] + 0.5f), "leaves at " + c[0] + "," + c[1] + "," + c[2]);
            }
        }
    }

    /**
     * The derivation leaves no pocket a fish could not reach: filling pockets again finds nothing
     * left to fill.
     */
    @TestFactory
    List<DynamicTest> noUnreachablePocketsAreLeft() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Map.Entry<String, CosmeticStructure> e : ShippedStructures.all().entrySet()) {
            tests.add(DynamicTest.dynamicTest(e.getKey(), () -> {
                CosmeticStructure structure = e.getValue();
                List<ObstacleGeometry.Box> boxes = boxes(structure);
                if (boxes.isEmpty()) return;
                List<ObstacleGeometry.Part> parts = new ArrayList<>();
                for (ObstacleGeometry.Box b : boxes) {
                    float[] f = b.buildBounds();
                    parts.add(new ObstacleGeometry.Part(0f, 0f, 0f, List.of(f), false));
                }
                ObstacleGeometry.Voxels before = ObstacleGeometry.rasterise(parts);
                // A shelter's hull is carved out on purpose: put it back before asking. A gate's
                // never was: it has none.
                structure.shelterShapes().stream()
                        .filter(d -> d.spec().kind() != CosmeticStructure.ShelterKind.GATE)
                        .map(CosmeticStructure.DerivedShelter::shape).forEach(s -> {
                    for (int x = s.hullMin().x(); x <= s.hullMax().x(); x++)
                        for (int y = s.hullMin().y(); y <= s.hullMax().y(); y++)
                            for (int z = s.hullMin().z(); z <= s.hullMax().z(); z++)
                                parts.add(new ObstacleGeometry.Part(x - 0.5f, y, z - 0.5f, List.of(new float[]{0, 0, 0, 1, 1, 1}), false));
                });
                ObstacleGeometry.Voxels withHull = ObstacleGeometry.rasterise(parts);
                int filledBefore = count(withHull);
                int filledAfter = count(ObstacleGeometry.fillPockets(withHull, CosmeticObstacles.clearanceVoxels(structure)));
                assertEquals(filledBefore, filledAfter, "voxels a second pass still had to fill");
                assertTrue(count(before) > 0);
            }));
        }
        return tests;
    }

    private static int count(ObstacleGeometry.Voxels v) {
        int n = 0;
        for (boolean[][] a : v.filled()) for (boolean[] b : a) for (boolean c : b) if (c) n++;
        return n;
    }

    /** A shelter's hollow is the shelter's business: no obstacle box reaches into its interior. */
    @Test
    void shelterInteriorsStayOpen() {
        int shelters = 0;
        for (Map.Entry<String, CosmeticStructure> e : ShippedStructures.all().entrySet()) {
            CosmeticStructure structure = e.getValue();
            if (!structure.hasShelter()) continue;
            shelters++;
            List<ObstacleGeometry.Box> boxes = boxes(structure);
            for (CosmeticStructure.DerivedShelter d : structure.shelterShapes()) {
                for (ShelterGeometry.Cell cell : d.shape().interior()) {
                    assertFalse(covered(boxes, cell.x(), cell.y() + 0.5f, cell.z()), e.getKey() + " interior cell " + cell);
                }
            }
        }
        assertTrue(shelters >= 3, "the Hollow Log, Clay Pipe and Whale Fall are all shelters");
    }

    /** An arch is something to swim through: the middle of its opening is open water. */
    @Test
    void theFenceArchOpeningStaysOpen() {
        for (Map.Entry<String, CosmeticStructure> e : ShippedStructures.all().entrySet()) {
            if (!e.getKey().startsWith("cosmetic_fence_arch_")) continue;
            List<ObstacleGeometry.Box> boxes = boxes(e.getValue());
            // Posts at build x = ±1, two cells tall; the opening is the cell between them.
            assertFalse(covered(boxes, 0f, 0.5f, 0f), e.getKey());
            assertTrue(covered(boxes, -1f, 0.5f, 0f) && covered(boxes, 1f, 0.5f, 0f), e.getKey() + " posts");
        }
    }

    /** No cap is imposed (see ObstacleGeometry), but a runaway count would mean the merge broke. */
    @Test
    void boxCountsStaySane() {
        for (Map.Entry<String, CosmeticStructure> e : ShippedStructures.all().entrySet()) {
            int n = boxes(e.getValue()).size();
            assertTrue(n <= 400, e.getKey() + ": " + n + " boxes");
        }
    }
}
