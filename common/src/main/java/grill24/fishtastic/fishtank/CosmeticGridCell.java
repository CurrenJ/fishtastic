package grill24.fishtastic.fishtank;

/**
 * Identifies a position in the 3×3 cosmetic placement grid on a single fish tank block's floor.
 * gridX and gridZ are each 0–2 (west→east, north→south respectively).
 * Cell centers are inset from the 1px frame corners so the grid is spatially centered
 * within the tank interior.
 *
 * Pixel values below correspond to element extents in the generated fish_tank_frame / fish_tank_sand
 * model JSONs (fish_tank_frame_0.json, fish_tank_sand_0.json).
 */
public record CosmeticGridCell(int gridX, int gridZ) {

    public static final int GRID_SIZE = 3;

    // ── Tank model pixel geometry ─────────────────────────────────────────────
    private static final int BLOCK_PIXELS = 16;
    private static final int WALL_PIXELS = 1;               // frame corner support width (XZ)
    private static final int FRAME_FLOOR_PIXELS = 1;        // bottom frame slab height
    private static final int SAND_LAYER_PIXELS = 1;         // sand base element height (fish_tank_sand_0: y=1→2)

    /** Y offset of the cosmetic floor surface in local block space, above the sand layer. */
    public static final float FLOOR_Y =
        (FRAME_FLOOR_PIXELS + SAND_LAYER_PIXELS) / (float) BLOCK_PIXELS;

    /** Sand layer height in block units — used externally to compute item lift offsets. */
    public static final float SAND_LAYER_HEIGHT = SAND_LAYER_PIXELS / (float) BLOCK_PIXELS;

    /** Thickness of the frame corner supports in block units. */
    private static final double WALL_THICKNESS = (double) WALL_PIXELS / BLOCK_PIXELS;
    /** Interior floor width after subtracting both side walls, in block units. */
    private static final double INTERIOR_WIDTH = 1.0 - 2.0 * WALL_THICKNESS;

    /** Width/depth of a single grid cell (the pitch between adjacent cell centers), in block units. */
    public static final double CELL_WIDTH = INTERIOR_WIDTH / GRID_SIZE;

    public CosmeticGridCell {
        if (gridX < 0 || gridX >= GRID_SIZE || gridZ < 0 || gridZ >= GRID_SIZE) {
            throw new IllegalArgumentException("Grid coords out of range: " + gridX + ", " + gridZ);
        }
    }

    public static boolean isValid(int gridX, int gridZ) {
        return gridX >= 0 && gridX < GRID_SIZE && gridZ >= 0 && gridZ < GRID_SIZE;
    }

    /** Local X center of this cell within the tank block, inset from frame walls. */
    public double localX() {
        return WALL_THICKNESS + (gridX + 0.5) * CELL_WIDTH;
    }

    /** Local Z center of this cell within the tank block, inset from frame walls. */
    public double localZ() {
        return WALL_THICKNESS + (gridZ + 0.5) * CELL_WIDTH;
    }
}
