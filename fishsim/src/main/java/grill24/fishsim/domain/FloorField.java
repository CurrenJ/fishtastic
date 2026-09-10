package grill24.fishsim.domain;

/**
 * Where the walkable sand is, and where something is standing on it — the terrain a
 * {@link grill24.fishsim.core.Locomotion#BENTHIC} creature walks (docs/fish-sim-locomotion.md
 * §2.3). Swimmers never read it.
 *
 * <p><b>Resolution.</b> The field is a 2D grid at {@link #SUBCELLS}² cells per block, aligned to
 * the block grid — deliberately, because that is exactly the resolution the tank's own cosmetic
 * placement grid uses on the MC side (a 3×3 floor grid per tank block). One subcell is therefore
 * one cosmetic cell, and "this cell has a shipwreck in it" maps to "this subcell is not walkable"
 * with no resampling. The two grids are not quite geometrically identical — a cosmetic cell is
 * inset from the block's frame walls and so is slightly narrower than a third of a block — but
 * they are both 3×3 covering the same interior in the same order, and the difference is far below
 * the scale at which a crawler's footprint separation operates.
 *
 * <p><b>Height.</b> Each subcell holds the local-vertical height of its walkable surface, or
 * {@link Float#NaN} where there is nothing to walk on: outside the field, a column of the domain
 * with no floor at all (the overhanging arm of an L-shaped group), or a cell an obstacle is
 * standing in. Callers test with {@link #walkable}; the NaN encoding means an unchecked
 * {@link #heightAt} propagates rather than silently reading as 0.
 *
 * <p>Immutable and shared: a group's field is built once per membership epoch alongside the
 * distance field, never per rebuild (docs/fish-tank-group-scaling.md §5.3a).
 */
public final class FloorField {

    /** Floor cells per block along each horizontal axis — the tank's cosmetic grid pitch. */
    public static final int SUBCELLS = 3;

    /** Width of one floor cell in blocks. */
    public static final float CELL_SIZE = 1f / SUBCELLS;

    private final float originL, originD;
    private final int cellsL, cellsD;
    private final float[] height;
    private final float area;

    private FloorField(float originL, float originD, int cellsL, int cellsD, float[] height) {
        this.originL = originL;
        this.originD = originD;
        this.cellsL = cellsL;
        this.cellsD = cellsD;
        this.height = height;
        int walkable = 0;
        for (float h : height) {
            if (!Float.isNaN(h)) walkable++;
        }
        this.area = walkable * CELL_SIZE * CELL_SIZE;
    }

    /**
     * A single flat floor spanning a whole number of blocks — the single-tank case.
     *
     * @param blocksL,blocksD block extents of the floor
     * @param originL,originD local coordinate of the low corner of block (0,0)
     * @param surfaceY        local vertical height of the sand surface
     * @param blocked         one flag per floor cell, indexed {@code il * cellsD + id}, true where
     *                        an obstacle stands; null for an open floor
     */
    public static FloorField flat(int blocksL, int blocksD, float originL, float originD,
                                  float surfaceY, boolean[] blocked) {
        int cellsL = blocksL * SUBCELLS, cellsD = blocksD * SUBCELLS;
        float[] height = new float[cellsL * cellsD];
        for (int i = 0; i < height.length; i++) {
            height[i] = blocked != null && i < blocked.length && blocked[i] ? Float.NaN : surfaceY;
        }
        return new FloorField(originL, originD, cellsL, cellsD, height);
    }

    /**
     * The floor of a voxel domain: for each block column, the lowest occupied cell whose
     * downward neighbour is empty — i.e. the lowest tank in that column that has a bottom, and so
     * has sand in it. A column with no such cell has no floor and reads as NaN throughout.
     *
     * @param occupancy      the domain's occupancy, indexed {@code [lateral][vertical][depth]}
     * @param gridMinL,gridMinY,gridMinD local coordinate of cell (0,0,0)'s low corner
     * @param surfaceOffset  height of the sand surface above a block's own bottom face
     * @param blocked        obstacle flags, indexed as in {@link #flat}; null for an open floor
     */
    public static FloorField fromOccupancy(boolean[][][] occupancy, float gridMinL, float gridMinY,
                                           float gridMinD, float surfaceOffset, boolean[] blocked) {
        int sx = occupancy.length, sy = occupancy[0].length, sz = occupancy[0][0].length;
        int cellsL = sx * SUBCELLS, cellsD = sz * SUBCELLS;
        float[] height = new float[cellsL * cellsD];

        for (int ix = 0; ix < sx; ix++) {
            for (int iz = 0; iz < sz; iz++) {
                float surface = Float.NaN;
                for (int iy = 0; iy < sy; iy++) {
                    if (occupancy[ix][iy][iz] && (iy == 0 || !occupancy[ix][iy - 1][iz])) {
                        surface = gridMinY + iy + surfaceOffset;
                        break;
                    }
                }
                for (int sl = 0; sl < SUBCELLS; sl++) {
                    for (int sd = 0; sd < SUBCELLS; sd++) {
                        int index = (ix * SUBCELLS + sl) * cellsD + (iz * SUBCELLS + sd);
                        height[index] = blocked != null && index < blocked.length && blocked[index]
                                ? Float.NaN : surface;
                    }
                }
            }
        }
        return new FloorField(gridMinL, gridMinD, cellsL, cellsD, height);
    }

    /** Walkable surface height at this horizontal position, or NaN where there is nothing to stand on. */
    public float heightAt(float l, float d) {
        int index = index(l, d);
        return index < 0 ? Float.NaN : height[index];
    }

    /** Whether a creature can stand at this horizontal position. */
    public boolean walkable(float l, float d) {
        return !Float.isNaN(heightAt(l, d));
    }

    /** Total walkable floor, in blocks² — the benthic size gate measures against this. */
    public float area() {
        return area;
    }

    /** Local coordinate of the low corner of cell (0,0) — with {@link #CELL_SIZE}, the grid's placement. */
    public float originLateral() {
        return originL;
    }

    public float originDepth() {
        return originD;
    }

    /** Walkable height of a cell by index, or NaN — the render/debug path's iteration order. */
    public float heightAtCell(int il, int id) {
        return height[il * cellsD + id];
    }

    public int cellsLateral() {
        return cellsL;
    }

    public int cellsDepth() {
        return cellsD;
    }

    /** Index of the floor cell containing this position, or −1 when it lies outside the field. */
    public int index(float l, float d) {
        int il = (int) Math.floor((l - originL) / CELL_SIZE);
        int id = (int) Math.floor((d - originD) / CELL_SIZE);
        if (il < 0 || il >= cellsL || id < 0 || id >= cellsD) return -1;
        return il * cellsD + id;
    }
}
