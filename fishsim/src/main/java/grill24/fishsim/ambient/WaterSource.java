package grill24.fishsim.ambient;

/**
 * Everything {@link WaterDiscovery} is allowed to know about the world: per-cell block/fluid
 * facts, and nothing else. The Minecraft-aware side implements this over a client level with a
 * loaded-chunk guard; the discovery, the scan assembly and every rule built on them stay
 * Minecraft-free and unit-testable against a synthetic world.
 *
 * <p>A cell that is not loaded must answer {@link #LOADED} clear. Nothing here may force a chunk
 * to load: an unknown cell is simply not water.
 */
public interface WaterSource {
    /** Equal keys may share a population patch. Called only for already-loaded water columns. */
    default Object contextKey(int x, int surfaceY, int z) { return 0; }

    /** The cell's chunk is present, so its other answers mean something. */
    int LOADED = 1;
    /** The cell holds water. */
    int WATER = 2;
    /** The cell's block has a non-empty collision shape. */
    int COLLISION = 4;
    /** Every flag at once, for sources that are always loaded and always answer fully (tests). */
    int ALL = LOADED | WATER | COLLISION;

    /** Bitwise {@link #LOADED}/{@link #WATER}/{@link #COLLISION} for one cell. */
    int flags(int x, int y, int z);

    /**
     * Height of this cell's water above the cell's own bottom, in (0, 1]: the fluid's own surface
     * height when nothing of the same fluid sits above it (8/9 of a block for a still source cell,
     * less for a flowing one, 1 for falling water), or 1 when water does sit above it. Only
     * meaningful where {@link #WATER} is set.
     */
    float fluidTop(int x, int y, int z);

    /**
     * How high this cell's block collision reaches above the cell's own bottom, in [0, 1]. 1 for a
     * full block, 0.5 for a bottom slab, 0 for a cell with no collision shape (including plants
     * and other passable blocks). A source may also count a shape from the cell below that rises
     * into this one — a fence post or wall reaches half a block up — provided it sets
     * {@link #COLLISION} for that cell as well. Only meaningful where {@link #COLLISION} is set.
     */
    float collisionTop(int x, int y, int z);
}
