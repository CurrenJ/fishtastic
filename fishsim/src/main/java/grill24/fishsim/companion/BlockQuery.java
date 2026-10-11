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
}
