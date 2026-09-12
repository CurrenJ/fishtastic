package grill24.fishtastic.menu;

import grill24.fishtastic.FishtasticMenuTypes;
import grill24.fishtastic.blockentity.ElectricFishOrganizerBlockEntity;
import grill24.fishtastic.blockentity.OrganizerSortMode;
import grill24.fishtastic.item.PileOfFishItem;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
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

    /** Server→client sync of the current sort mode/direction (client and server both read these). */
    private final DataSlot sortModeSlot;
    private final DataSlot sortAscendingSlot;

    /** Client-side constructor, used by the registered {@code MenuType} factory. */
    public ElectricFishOrganizerMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(CONTAINER_SIZE), OrganizerSortMode.SPECIES, true);
    }

    /** Server-side constructor, used by {@link ElectricFishOrganizerBlockEntity#createMenu}. */
    public ElectricFishOrganizerMenu(int containerId, Inventory playerInventory, ElectricFishOrganizerBlockEntity blockEntity) {
        this(containerId, playerInventory, (Container) blockEntity, blockEntity.getSortMode(), blockEntity.isSortAscending());
    }

    private ElectricFishOrganizerMenu(int containerId, Inventory playerInventory, Container container,
                                       OrganizerSortMode initialSortMode, boolean initialSortAscending) {
        super(FishtasticMenuTypes.ELECTRIC_FISH_ORGANIZER.value(), containerId);
        checkContainerSize(container, CONTAINER_SIZE);
        this.organizerContainer = container;
        container.startOpen(playerInventory.player);

        this.sortModeSlot = addDataSlot(DataSlot.standalone());
        this.sortModeSlot.set(initialSortMode.ordinal());
        this.sortAscendingSlot = addDataSlot(DataSlot.standalone());
        this.sortAscendingSlot.set(initialSortAscending ? 1 : 0);

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                addSlot(new OrganizerSlot(container, col + row * COLS, 8 + col * 18, 18 + row * 18));
            }
        }

        int inventoryTop = 18 + ROWS * 18 + 13;
        addStandardInventorySlots(playerInventory, 8, inventoryTop);
    }

    /** The sort mode both sides currently agree on (client mirrors the server via {@link #sortModeSlot}). */
    public OrganizerSortMode getSortMode() {
        OrganizerSortMode[] modes = OrganizerSortMode.values();
        int ordinal = sortModeSlot.get();
        return ordinal >= 0 && ordinal < modes.length ? modes[ordinal] : OrganizerSortMode.SPECIES;
    }

    public boolean isSortAscending() {
        return sortAscendingSlot.get() != 0;
    }

    /**
     * Server-side sort change (from {@link grill24.fishtastic.network.SetOrganizerSortPacket}):
     * persist it on the block entity (which re-sorts its contents) and push the new state to the
     * client's mirrored data slots.
     */
    public void setSortState(OrganizerSortMode mode, boolean ascending) {
        if (organizerContainer instanceof ElectricFishOrganizerBlockEntity blockEntity) {
            blockEntity.setSortMode(mode, ascending);
        }
        sortModeSlot.set(mode.ordinal());
        sortAscendingSlot.set(ascending ? 1 : 0);
        broadcastChanges();
    }

    /**
     * Client-side optimistic update so the buttons react instantly on click, before the server
     * confirms via {@link #setData}. The server remains authoritative.
     */
    public void setSortStateLocal(OrganizerSortMode mode, boolean ascending) {
        sortModeSlot.set(mode.ordinal());
        sortAscendingSlot.set(ascending ? 1 : 0);
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
