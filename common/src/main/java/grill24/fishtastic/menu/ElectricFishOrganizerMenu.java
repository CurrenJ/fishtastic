package grill24.fishtastic.menu;

import grill24.fishtastic.FishtasticMenuTypes;
import grill24.fishtastic.blockentity.ElectricFishOrganizerBlockEntity;
import grill24.fishtastic.item.PileOfFishItem;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A 54-slot menu (double-chest-sized) over an {@link ElectricFishOrganizerBlockEntity}. The
 * sorting/merging behavior lives entirely on the block entity ({@code setChanged} triggers it),
 * so this menu is otherwise a plain vanilla chest menu — {@link OrganizerSlot} is the only thing
 * restricting what can go in each slot. Vanilla {@link Slot#mayPlace} defaults to {@code true}
 * unconditionally (it does not consult {@link Container#canPlaceItem}, which only hoppers and
 * droppers check), so without this override every click/drag/swap path into these slots would
 * accept any item — and {@link ElectricFishOrganizerBlockEntity#normalize()} would then silently
 * discard it on the next rewrite, since it only round-trips fish and piles.
 */
public class ElectricFishOrganizerMenu extends AbstractContainerMenu {
    public static final int CONTAINER_SIZE = ElectricFishOrganizerBlockEntity.CONTAINER_SIZE;
    private static final int ROWS = 6;
    private static final int COLS = 9;

    private final Container organizerContainer;

    /** Client-side constructor, used by the registered {@code MenuType} factory. */
    public ElectricFishOrganizerMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(CONTAINER_SIZE));
    }

    /** Server-side constructor, used by {@link ElectricFishOrganizerBlockEntity#createMenu}. */
    public ElectricFishOrganizerMenu(int containerId, Inventory playerInventory, ElectricFishOrganizerBlockEntity blockEntity) {
        this(containerId, playerInventory, (Container) blockEntity);
    }

    private ElectricFishOrganizerMenu(int containerId, Inventory playerInventory, Container container) {
        super(FishtasticMenuTypes.ELECTRIC_FISH_ORGANIZER.value(), containerId);
        checkContainerSize(container, CONTAINER_SIZE);
        this.organizerContainer = container;
        container.startOpen(playerInventory.player);

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                addSlot(new OrganizerSlot(container, col + row * COLS, 8 + col * 18, 18 + row * 18));
            }
        }

        int inventoryTop = 18 + ROWS * 18 + 13;
        addStandardInventorySlots(playerInventory, 8, inventoryTop);
    }

    @Override
    public boolean stillValid(Player player) {
        return organizerContainer.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        organizerContainer.stopOpen(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            newStack = stackInSlot.copy();
            if (index < CONTAINER_SIZE) {
                if (!moveItemStackTo(stackInSlot, CONTAINER_SIZE, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stackInSlot, 0, CONTAINER_SIZE, false)) {
                return ItemStack.EMPTY;
            }

            if (stackInSlot.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stackInSlot.getCount() == newStack.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stackInSlot);
        }
        return newStack;
    }

    /** Only sized fish and Pile of Fish stacks may be placed — matches {@link ElectricFishOrganizerBlockEntity#canPlaceItem}. */
    private static class OrganizerSlot extends Slot {
        OrganizerSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return PileOfFishItem.canInsertInPile(stack) || stack.getItem() instanceof PileOfFishItem;
        }
    }
}
