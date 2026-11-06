package grill24.fishtastic.fishtank;

import grill24.fishtastic.blockentity.FishTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Hands back a tank's cosmetics when the tank is broken. Runs just before the tank leaves the world,
 * while its open faces still describe the column it stood in.
 *
 * <p>Whatever the tank stores comes out as items: its floor and lid cosmetics, and any structure
 * anchored in it (which clears that structure's links in other tanks). A structure anchored
 * <i>elsewhere</i> stays with its anchor, so breaking another tank it reaches into drops nothing.
 * A strand stored in another storey of the column gives up the segments it no longer has room for.
 */
public final class TankCosmeticDrops {

    private TankCosmeticDrops() {}

    public static void spill(Level level, FishTankBlockEntity tank) {
        List<ItemStack> drops = new ArrayList<>();
        trimStrandsThrough(level, tank, drops);

        for (CosmeticGridCell cell : List.copyOf(tank.getCosmetics().keySet())) {
            while (tank.getCosmetics().containsKey(cell)) drops.add(tank.removeCosmeticEntry(cell));
        }
        for (CosmeticGridCell cell : List.copyOf(tank.getCeilingCosmetics().keySet())) {
            while (tank.getCeilingCosmetics().containsKey(cell)) drops.add(tank.removeCeilingCosmeticEntry(cell));
        }
        for (CosmeticGridCell anchor : List.copyOf(tank.getStructureCosmetics().keySet())) {
            drops.add(tank.removeStructureCosmeticEntry(anchor));
        }

        BlockPos pos = tank.getBlockPos();
        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), drop);
        }
    }

    /** Shortens strands rooted above or below {@code broken} to what fits without it. */
    private static void trimStrandsThrough(Level level, FishTankBlockEntity broken, List<ItemStack> drops) {
        BlockPos pos = broken.getBlockPos();

        BlockPos floor = TankColumns.floorOf(level, pos);
        if (!floor.equals(pos) && level.getBlockEntity(floor) instanceof FishTankBlockEntity root) {
            int room = TankColumns.maxSegments(pos.getY() - floor.getY());
            for (CosmeticGridCell cell : List.copyOf(root.getCosmetics().keySet())) {
                while (root.getCosmetics().containsKey(cell) && root.getCosmetics().get(cell).height() > room) {
                    drops.add(root.removeCosmeticEntry(cell));
                }
            }
        }

        BlockPos ceiling = TankColumns.ceilingOf(level, pos);
        if (!ceiling.equals(pos) && level.getBlockEntity(ceiling) instanceof FishTankBlockEntity root) {
            int room = TankColumns.maxSegments(ceiling.getY() - pos.getY());
            for (CosmeticGridCell cell : List.copyOf(root.getCeilingCosmetics().keySet())) {
                while (root.getCeilingCosmetics().containsKey(cell) && root.getCeilingCosmetics().get(cell).height() > room) {
                    drops.add(root.removeCeilingCosmeticEntry(cell));
                }
            }
        }
    }
}
