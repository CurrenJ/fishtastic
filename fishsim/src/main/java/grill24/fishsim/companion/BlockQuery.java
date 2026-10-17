package grill24.fishsim.companion;

/**
 * The world's blocks as the companion sees them: is this cell something a fish can't swim through,
 * and is it water? In game the adapter answers from collision shapes and fluid states; headless, a
 * voxel scene does.
 */
@FunctionalInterface
public interface BlockQuery {
    boolean solid(int x, int y, int z);

    /** Whether the cell holds water. Never true for a solid cell. */
    default boolean water(int x, int y, int z) {
        return false;
    }

    /**
     * For a solid cell, how high its collision reaches inside the cell, in (0, 1]: a full block 1,
     * a slab 0.5, a carpet 1/16. Only asked of solid cells. Something walking on the ground stands
     * on it; for everything else a solid cell fills the cell from its floor up to this height.
     * Default: a full block.
     */
    default float top(int x, int y, int z) {
        return 1f;
    }
}
