package grill24.fishtastic.fishtank;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The structure-to-boxes geometry on its own, without Minecraft (docs/fish-shelters.md §12.3):
 * rasterising block shapes, the exact greedy merge, carving a hull out, and filling the pockets no
 * fish could get into.
 */
class ObstacleGeometryTest {

    private static final float[] CUBE = {0f, 0f, 0f, 1f, 1f, 1f};
    /** A fence post's outline shape: 4/16 wide, centred. */
    private static final float[] POST = {0.375f, 0f, 0.375f, 0.625f, 1f, 0.625f};

    /** A part whose block's centre is build cell {@code (x, y, z)}. */
    private static ObstacleGeometry.Part at(float x, float y, float z, float[] shape) {
        return new ObstacleGeometry.Part(x - 0.5f, y, z - 0.5f, List.of(shape), false);
    }

    private static boolean filled(ObstacleGeometry.Voxels v, int x, int y, int z) {
        int ix = x - v.originX(), iy = y - v.originY(), iz = z - v.originZ();
        boolean[][][] g = v.filled();
        return ix >= 0 && iy >= 0 && iz >= 0 && ix < g.length && iy < g[0].length && iz < g[0][0].length && g[ix][iy][iz];
    }

    private static boolean inAny(List<ObstacleGeometry.Box> boxes, float x, float y, float z) {
        for (ObstacleGeometry.Box b : boxes) {
            float[] f = b.buildBounds();
            if (x > f[0] && x < f[3] && y > f[1] && y < f[4] && z > f[2] && z < f[5]) return true;
        }
        return false;
    }

    @Test
    void aFencePostIsAPostNotACube() {
        List<ObstacleGeometry.Box> boxes = ObstacleGeometry.merge(ObstacleGeometry.rasterise(List.of(at(0, 0, 0, POST))));
        assertEquals(1, boxes.size());
        // 4/16 wide, centred: it overlaps the middle two of four voxels.
        assertArrayEquals(new float[]{-0.25f, 0f, -0.25f, 0.25f, 1f, 0.25f}, boxes.get(0).buildBounds(), 1e-6f);
    }

    @Test
    void softPartsAndEmptyShapesAddNothing() {
        ObstacleGeometry.Part leaves = new ObstacleGeometry.Part(-0.5f, 0f, -0.5f, List.of(CUBE), true);
        ObstacleGeometry.Part air = new ObstacleGeometry.Part(0.5f, 0f, -0.5f, List.of(), false);
        assertTrue(ObstacleGeometry.merge(ObstacleGeometry.rasterise(List.of(leaves, air))).isEmpty());
    }

    /** The Dynamic Duo's blocks stand half a cell off the grid; rounding them moved them half a block. */
    @Test
    void partsStandWhereTheyAreDrawnEvenOffTheGrid() {
        List<ObstacleGeometry.Box> boxes = ObstacleGeometry.merge(ObstacleGeometry.rasterise(List.of(at(0.5f, 0, 0, CUBE))));
        assertEquals(1, boxes.size());
        assertArrayEquals(new float[]{0f, 0f, -0.5f, 1f, 1f, 0.5f}, boxes.get(0).buildBounds(), 1e-6f);
    }

    /** The merge is exact: the boxes cover the filled voxels, nothing else, and never overlap. */
    @Test
    void mergeCoversExactlyTheFilledVoxels() {
        Random r = new Random(7);
        for (int trial = 0; trial < 40; trial++) {
            List<ObstacleGeometry.Part> parts = new ArrayList<>();
            for (int k = 0; k < 12; k++) {
                float x0 = r.nextInt(4) / 4f, y0 = r.nextInt(4) / 4f, z0 = r.nextInt(4) / 4f;
                float[] shape = {x0, y0, z0, x0 + (1 + r.nextInt(3)) / 4f, y0 + (1 + r.nextInt(3)) / 4f, z0 + (1 + r.nextInt(3)) / 4f};
                parts.add(at(r.nextInt(4), r.nextInt(3), r.nextInt(4), shape));
            }
            ObstacleGeometry.Voxels v = ObstacleGeometry.rasterise(parts);
            List<ObstacleGeometry.Box> boxes = ObstacleGeometry.merge(v);
            int[][][] cover = new int[v.filled().length][v.filled()[0].length][v.filled()[0][0].length];
            for (ObstacleGeometry.Box b : boxes) {
                for (int x = b.minX(); x < b.maxX(); x++)
                    for (int y = b.minY(); y < b.maxY(); y++)
                        for (int z = b.minZ(); z < b.maxZ(); z++) cover[x - v.originX()][y - v.originY()][z - v.originZ()]++;
            }
            for (int x = 0; x < cover.length; x++)
                for (int y = 0; y < cover[0].length; y++)
                    for (int z = 0; z < cover[0][0].length; z++) {
                        assertEquals(v.filled()[x][y][z] ? 1 : 0, cover[x][y][z], "trial " + trial + " voxel " + x + "," + y + "," + z);
                    }
        }
    }

    @Test
    void carvingEmptiesTheHullCellsOnly() {
        List<ObstacleGeometry.Part> parts = new ArrayList<>();
        for (int x = 0; x < 4; x++) parts.add(at(x, 0, 0, CUBE));
        ObstacleGeometry.Voxels v = ObstacleGeometry.rasterise(parts);
        ObstacleGeometry.carve(v, new ShelterGeometry.Cell(1, 0, 0), new ShelterGeometry.Cell(2, 0, 0));
        List<ObstacleGeometry.Box> boxes = ObstacleGeometry.merge(v);
        assertTrue(inAny(boxes, 0f, 0.5f, 0f));
        assertFalse(inAny(boxes, 1f, 0.5f, 0f));
        assertFalse(inAny(boxes, 2f, 0.5f, 0f));
        assertTrue(inAny(boxes, 3f, 0.5f, 0f));
    }

    /** A hollow 5×5×5 shell of cubes standing on the sand, with an optional square window in its +x wall. */
    private static List<ObstacleGeometry.Part> shell(int windowWidth) {
        List<ObstacleGeometry.Part> parts = new ArrayList<>();
        for (int x = 0; x < 5; x++)
            for (int y = 0; y < 5; y++)
                for (int z = 0; z < 5; z++) {
                    boolean wall = x == 0 || x == 4 || y == 0 || y == 4 || z == 0 || z == 4;
                    if (!wall) continue;
                    boolean window = x == 4 && Math.abs(y - 2) * 2 < windowWidth && Math.abs(z - 2) * 2 < windowWidth;
                    if (!window) parts.add(at(x, y, z, CUBE));
                }
        return parts;
    }

    @Test
    void aSealedHollowIsFilled() {
        ObstacleGeometry.Voxels v = ObstacleGeometry.fillPockets(ObstacleGeometry.rasterise(shell(0)), 1);
        assertTrue(filled(v, 2 * ObstacleGeometry.RES, 2 * ObstacleGeometry.RES + 2, 2 * ObstacleGeometry.RES));
    }

    @Test
    void aHollowWithAWideWindowIsKeptAndANarrowOneIsFilled() {
        int mid = 2 * ObstacleGeometry.RES;
        // A three-cell window: 12 voxels, far wider than 2 × clearance + 1.
        ObstacleGeometry.Voxels open = ObstacleGeometry.fillPockets(ObstacleGeometry.rasterise(shell(3)), 2);
        assertFalse(filled(open, mid, mid + 2, mid), "the hollow behind a wide window is open water");
        // A one-cell window (4 voxels) is narrower than a clearance of 3 needs (7).
        ObstacleGeometry.Voxels narrow = ObstacleGeometry.fillPockets(ObstacleGeometry.rasterise(shell(1)), 3);
        assertTrue(filled(narrow, mid, mid + 2, mid), "the hollow behind a narrow window is filled");
    }

    @Test
    void waterBesideAStructureIsNeverFilled() {
        // Two pillars two cells apart: the gap between them is open water reached from outside.
        List<ObstacleGeometry.Part> parts = new ArrayList<>();
        for (int y = 0; y < 3; y++) {
            parts.add(at(0, y, 0, CUBE));
            parts.add(at(3, y, 0, CUBE));
        }
        ObstacleGeometry.Voxels v = ObstacleGeometry.fillPockets(ObstacleGeometry.rasterise(parts), 2);
        for (int x = 2; x < 10; x++) {
            assertFalse(filled(v, x, 4, 0), "gap voxel " + x);
        }
    }

    @Test
    void aPocketThatOpensOnlyDownwardIsFilled() {
        // An upturned box: no floor, so open underneath — but underneath is sand.
        List<ObstacleGeometry.Part> parts = new ArrayList<>(shell(0));
        parts.removeIf(p -> p.y() == 0f && p.x() > 0f && p.x() < 3f && p.z() > 0f && p.z() < 3f);
        ObstacleGeometry.Voxels v = ObstacleGeometry.fillPockets(ObstacleGeometry.rasterise(parts), 1);
        int mid = 2 * ObstacleGeometry.RES;
        assertTrue(filled(v, mid, mid, mid));
    }
}
