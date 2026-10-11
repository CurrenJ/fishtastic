package grill24.fishsim.companion;

/**
 * The world's blocks as the companion sees them: is this cell something a fish can't swim through?
 * In game the adapter answers from collision shapes; headless, a voxel scene does.
 */
@FunctionalInterface
public interface BlockQuery {
    boolean solid(int x, int y, int z);
}
