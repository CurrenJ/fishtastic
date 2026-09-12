package grill24.fishtastic.client.util;

import grill24.fishsim.domain.FloorField;
import grill24.fishsim.domain.VoxelDomain;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one piece of arithmetic joining the tank's cosmetic grid to the simulation's floor grid.
 *
 * <p>It is worth its own test because every cheaper check passes when it is wrong: a transposed
 * axis or an off-by-one still produces a mask of the right size holding the right number of
 * blocked cells, and the only symptom is crawlers stepping around bare sand while walking through
 * a shipwreck — visible in game, invisible everywhere else.
 */
class TankFloorsTest {

    /** A tank's cosmetic cell is a floor cell: same 3×3, same order, no resampling. */
    @Test
    void aCosmeticCellIsAFloorCell() {
        assertEquals(FloorField.SUBCELLS, CosmeticGridCell.GRID_SIZE,
                "the two grids must stay the same resolution for packed() to be the mask index");

        for (int gridX = 0; gridX < CosmeticGridCell.GRID_SIZE; gridX++) {
            for (int gridZ = 0; gridZ < CosmeticGridCell.GRID_SIZE; gridZ++) {
                CosmeticGridCell cell = new CosmeticGridCell(gridX, gridZ);
                boolean[] blocked = new boolean[9];
                blocked[cell.packed()] = true;
                FloorField floor = FloorField.flat(1, 1, -0.5f, -0.5f, 0f, blocked);

                // The cell's own centre is blocked; the cell diagonally opposite it is not.
                float l = -0.5f + (gridX + 0.5f) * FloorField.CELL_SIZE;
                float d = -0.5f + (gridZ + 0.5f) * FloorField.CELL_SIZE;
                assertFalse(floor.walkable(l, d), "cosmetic cell " + gridX + "," + gridZ + " was walkable");

                float otherL = -0.5f + (CosmeticGridCell.GRID_SIZE - 1 - gridX + 0.5f) * FloorField.CELL_SIZE;
                float otherD = -0.5f + (CosmeticGridCell.GRID_SIZE - 1 - gridZ + 0.5f) * FloorField.CELL_SIZE;
                if (gridX * 2 != CosmeticGridCell.GRID_SIZE - 1 || gridZ * 2 != CosmeticGridCell.GRID_SIZE - 1) {
                    assertTrue(floor.walkable(otherL, otherD),
                            "blocking " + gridX + "," + gridZ + " also blocked the opposite cell");
                }
            }
        }
    }

    /**
     * The group mask puts a member's cosmetic where that member actually is. Checked through a
     * real {@link VoxelDomain} rather than against the index formula restated, so a transposed
     * axis cannot pass by matching a copy of itself.
     */
    @Test
    void aMembersCosmeticLandsUnderThatMember() {
        // Three tanks in a row along lateral, one deep. Block the middle tank's north-east cell.
        boolean[][][] occupancy = new boolean[3][1][1];
        for (boolean[][] column : occupancy) column[0][0] = true;
        int cellsD = 1 * FloorField.SUBCELLS;

        int gridX = 2, gridZ = 0;
        boolean[] blocked = new boolean[3 * FloorField.SUBCELLS * cellsD];
        blocked[TankFloors.groupIndex(1, 0, gridX, gridZ, cellsD)] = true;

        VoxelDomain domain = new VoxelDomain(occupancy, VoxelDomain.DEFAULT_INSET,
                TankFloors.GROUP_SURFACE_OFFSET, blocked);

        // Grid spans lateral [-1.5, 1.5]: the middle tank is [-0.5, 0.5], and its cell (2,0) is
        // the high-lateral, low-depth third of it.
        float l = -0.5f + (gridX + 0.5f) * FloorField.CELL_SIZE;
        float d = -0.5f + (gridZ + 0.5f) * FloorField.CELL_SIZE;
        assertFalse(domain.floor().walkable(l, d), "the blocked cell was walkable");

        // The same cell of each neighbouring tank is open, which is what catches a member offset
        // applied to the wrong axis or dropped entirely.
        assertTrue(domain.floor().walkable(l - 1f, d), "the low-lateral neighbour was blocked too");
        assertTrue(domain.floor().walkable(l + 1f, d), "the high-lateral neighbour was blocked too");
        // And so is the rest of the middle tank.
        assertTrue(domain.floor().walkable(l, d + 2 * FloorField.CELL_SIZE),
                "the whole depth column was blocked");

        assertEquals(3f - FloorField.CELL_SIZE * FloorField.CELL_SIZE, domain.floor().area(), 1e-5f,
                "exactly one cell should have been taken out of a 3-block floor");
    }

    /** A stacked tank's floor is the tank below's — only the bottom of each column carries sand. */
    @Test
    void onlyTheBottomOfAColumnHasAFloor() {
        boolean[][][] occupancy = new boolean[1][2][1];
        occupancy[0][0][0] = true;
        occupancy[0][1][0] = true;
        VoxelDomain domain = new VoxelDomain(occupancy, VoxelDomain.DEFAULT_INSET,
                TankFloors.GROUP_SURFACE_OFFSET, null);

        assertEquals(1f, domain.floor().area(), 1e-5f, "a 1×2×1 stack has one block of floor, not two");
        // Local Y puts the grid centre at 0, so the lower block spans [-1, 0]: its sand sits just
        // above -1, not above 0.
        assertEquals(-1f + TankFloors.GROUP_SURFACE_OFFSET, domain.floor().heightAt(0f, 0f), 1e-5f);
    }
}
