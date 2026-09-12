package grill24.fishtastic.client.util;

import grill24.fishsim.domain.FloorField;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.client.renderer.FishTankBlockEntityRenderer;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.TankGroups;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Turns a tank's sand and the cosmetics standing on it into the {@link FloorField} the simulation
 * walks its benthic creatures across (docs/fish-sim-locomotion.md §2.3, §4.1).
 *
 * <p>The two grids line up by construction, which is the whole reason this is cheap: a tank places
 * cosmetics on a 3×3 floor grid ({@link CosmeticGridCell}), and {@code FloorField} resolves the
 * floor at {@link FloorField#SUBCELLS}² cells per block — the same 3×3, in the same order. A
 * cosmetic cell is therefore one floor cell, with no resampling, and
 * {@link CosmeticGridCell#packed()} <i>is</i> the single-tank mask index.
 *
 * <p>Both single-block cosmetics and multi-block structures block their cells: a structure's
 * whole footprint is occupied, not just its anchor, so a crawler walks around a shipwreck rather
 * than through the part of it that has no anchor in it.
 */
public final class TankFloors {

    /**
     * Local vertical of the sand surface in the single-tank engine's frame. The engine's Y origin
     * is the renderer's item baseline, so the sand — which sits {@link CosmeticGridCell#FLOOR_Y}
     * above the block's own bottom — is that far below it.
     */
    public static final float LOCAL_SURFACE_Y =
            CosmeticGridCell.FLOOR_Y - FishTankBlockEntityRenderer.ITEM_BASELINE_Y;

    /**
     * Height of the sand above a block's own bottom face — what a voxel domain adds to each block
     * column's floor. Unlike {@link #LOCAL_SURFACE_Y} this needs no baseline correction: a group's
     * local Y already measures whole blocks from the grid's centre.
     */
    public static final float GROUP_SURFACE_OFFSET = CosmeticGridCell.FLOOR_Y;

    private TankFloors() {}

    /** The floor of one tank, with its own cosmetics standing on it. */
    public static FloorField single(FishTankBlockEntity be) {
        return FloorField.flat(1, 1, -0.5f, -0.5f, LOCAL_SURFACE_Y, blockedCells(be));
    }

    /** A 3×3 obstacle mask for one tank, indexed exactly as {@link CosmeticGridCell#packed()}. */
    public static boolean[] blockedCells(FishTankBlockEntity be) {
        boolean[] blocked = new boolean[CosmeticGridCell.GRID_SIZE * CosmeticGridCell.GRID_SIZE];
        for (CosmeticGridCell cell : be.getCosmetics().keySet()) blocked[cell.packed()] = true;
        for (CosmeticGridCell cell : be.getStructureCellIndex().keySet()) blocked[cell.packed()] = true;
        return blocked;
    }

    /**
     * The floor of a whole connected group: every member's sand, with every member's cosmetics on
     * it. Cells belonging to blocks that have no floor of their own — the upper tank of a stack,
     * whose sand is the tank below's — are left to {@link FloorField#fromOccupancy} to resolve.
     */
    public static boolean[] groupBlockedCells(TankGroups.Group group, Level level) {
        boolean[][][] occupancy = group.occupancy();
        int cellsD = occupancy[0][0].length * FloorField.SUBCELLS;
        boolean[] blocked = new boolean[occupancy.length * FloorField.SUBCELLS * cellsD];
        BlockPos min = group.min();

        for (BlockPos memberPos : group.members()) {
            if (!(level.getBlockEntity(memberPos) instanceof FishTankBlockEntity member)) continue;
            int ix = memberPos.getX() - min.getX();
            int iz = memberPos.getZ() - min.getZ();
            for (CosmeticGridCell cell : member.getCosmetics().keySet()) {
                blocked[groupIndex(ix, iz, cell, cellsD)] = true;
            }
            for (CosmeticGridCell cell : member.getStructureCellIndex().keySet()) {
                blocked[groupIndex(ix, iz, cell, cellsD)] = true;
            }
        }
        return blocked;
    }

    /**
     * Where one member's cosmetic cell lands in the group-wide mask. Package-visible and taking
     * plain ints so it can be tested without a world: a transposed axis here is invisible in
     * every other check (the mask is still the right size and the right density) and shows up
     * only as crawlers avoiding empty sand while walking through a shipwreck.
     *
     * @param ix,iz the member's block offset from the group's min corner
     */
    static int groupIndex(int ix, int iz, int gridX, int gridZ, int cellsD) {
        return (ix * FloorField.SUBCELLS + gridX) * cellsD + (iz * FloorField.SUBCELLS + gridZ);
    }

    private static int groupIndex(int ix, int iz, CosmeticGridCell cell, int cellsD) {
        return groupIndex(ix, iz, cell.gridX(), cell.gridZ(), cellsD);
    }

    /**
     * A cheap value that changes whenever this tank's cosmetics do. Cosmetics move only on a
     * player action, but nothing else tells the renderer that they did — unlike membership, which
     * has an epoch — so the floor is rebuilt off a fingerprint comparison instead. A tank holds at
     * most nine of them, so this is a nine-element scan, not a scan of the world.
     */
    public static int fingerprint(FishTankBlockEntity be) {
        int hash = 1;
        for (CosmeticGridCell cell : be.getCosmetics().keySet()) hash += 31 * (cell.packed() + 1);
        for (CosmeticGridCell cell : be.getStructureCellIndex().keySet()) hash += 131 * (cell.packed() + 1);
        return hash;
    }

    /** {@link #fingerprint} over every member of a group. */
    public static int groupFingerprint(TankGroups.Group group, Level level) {
        int hash = 1;
        for (BlockPos memberPos : group.members()) {
            if (!(level.getBlockEntity(memberPos) instanceof FishTankBlockEntity member)) continue;
            hash = hash * 31 + fingerprint(member) * (memberPos.hashCode() | 1);
        }
        return hash;
    }
}
