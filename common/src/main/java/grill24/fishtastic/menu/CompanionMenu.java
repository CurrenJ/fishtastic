package grill24.fishtastic.menu;

import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.FishtasticMenuTypes;
import grill24.fishtastic.companion.CompanionBondGate;
import grill24.fishtastic.companion.CompanionStorage;
import grill24.fishtastic.companion.Companions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** A persistent companion slot with ordinary, server-authoritative vanilla inventory handling. */
public final class CompanionMenu extends AbstractContainerMenu {
    /** Installed only during client initialization; the dedicated server never loads client classes. */
    public static java.util.function.Predicate<ItemStack> clientEligibility = stack -> true;
    public static final int INVENTORY_START = 1;
    public static final int INVENTORY_END = 37;
    private final Player owner;
    private final SimpleContainer companion = new SimpleContainer(1) {
        @Override public void setChanged() {
            super.setChanged();
            if (!readingStorage && owner instanceof ServerPlayer player) Companions.setFromMenu(player, getItem(0));
        }
    };
    private final DataSlot species = DataSlot.standalone();
    private boolean readingStorage;

    public CompanionMenu(int id, Inventory inventory) {
        this(id, inventory, null);
    }

    public CompanionMenu(int id, Inventory inventory, Item highlightedSpecies) {
        super(FishtasticMenuTypes.COMPANION.value(), id);
        owner = inventory.player;
        species.set(highlightedSpecies == null ? -1 : BuiltInRegistries.ITEM.getId(highlightedSpecies));
        addDataSlot(species);
        companion.setItem(0, CompanionStorage.get(owner).copy());
        addSlot(new Slot(companion, 0, 80, 34) {
            @Override public int getMaxStackSize() { return 1; }
            @Override public boolean mayPlace(ItemStack stack) {
                return stack.is(FishtasticItemTags.FISH)
                        && (owner instanceof ServerPlayer player ? CompanionBondGate.isUnlocked(player, stack) : clientEligibility.test(stack));
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 120 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 178));
    }

    public Item highlightedSpecies() {
        return species.get() < 0 ? null : BuiltInRegistries.ITEM.byId(species.get());
    }

    /**
     * Attachment changes (pile selection, rename, return) also update this menu's mirror. Packets
     * handled earlier in the same tick can change the attachment before the next broadcast, so a
     * click must never act on a stale mirror: that would hand back a fish {@code equip} already returned.
     */
    private void pullStorage() {
        if (!(owner instanceof ServerPlayer)) return;
        ItemStack stored = CompanionStorage.get(owner);
        if (ItemStack.matches(stored, companion.getItem(0))) return;
        readingStorage = true;
        try { companion.setItem(0, stored.copy()); }
        finally { readingStorage = false; }
    }

    @Override public void broadcastChanges() {
        pullStorage();
        super.broadcastChanges();
    }

    @Override public boolean stillValid(Player player) { return player == owner && player.isAlive(); }

    @Override public void clicked(int slot, int button, net.minecraft.world.inventory.ContainerInput input, Player player) {
        pullStorage();
        if (input == net.minecraft.world.inventory.ContainerInput.QUICK_MOVE && (button == 0 || button == 1)) {
            // Vanilla repeats quickMove while the source still contains the same item type.
            // A one-fish companion exchange must run exactly once, even for same-species swaps.
            quickMoveStack(player, slot);
            return;
        }
        if (slot == 0 && input == net.minecraft.world.inventory.ContainerInput.PICKUP && (button == 0 || button == 1)) {
            clickCompanion(player);
            return;
        }
        super.clicked(slot, button, input, player);
    }

    /**
     * Plain take/place/swap for the companion slot. Vanilla would first offer the click to the items
     * themselves, and a fish clicked onto a fish auto-piles (SizedItemClickMixin) straight into the
     * slot without asking {@link Slot#mayPlace} — two fish stored as one companion.
     */
    private void clickCompanion(Player player) {
        Slot slot = slots.get(0);
        ItemStack carried = getCarried(), current = slot.getItem();
        if (carried.isEmpty()) {
            if (!current.isEmpty()) setCarried(slot.safeTake(current.getCount(), Integer.MAX_VALUE, player));
        } else if (slot.mayPlace(carried)) {
            if (current.isEmpty()) {
                setCarried(slot.safeInsert(carried, 1));
            } else if (carried.getCount() == 1) {
                ItemStack previous = current.copy();
                slot.setByPlayer(carried);
                slot.onTake(player, previous);
                setCarried(previous);
            }
        }
        slot.setChanged();
    }

    /** Double-click collecting a stack must not quietly pull the companion out with it. */
    @Override public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
        return target.index != 0;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot source = slots.get(index);
        if (!source.hasItem() || !source.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = source.getItem(), original = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) return ItemStack.EMPTY;
            source.setByPlayer(stack.isEmpty() ? ItemStack.EMPTY : stack);
        } else {
            Slot target = slots.get(0);
            if (!target.mayPlace(stack)) return ItemStack.EMPTY;
            ItemStack previous = target.getItem().copy();
            if (stack.getCount() == 1) {
                // A full inventory is fine: the old companion takes the source fish's place.
                target.setByPlayer(stack.copyWithCount(1));
                source.setByPlayer(previous);
            } else {
                // Never drop the old companion as a side effect of shift-clicking.
                if (!previous.isEmpty() && !moveItemStackTo(previous, INVENTORY_START, INVENTORY_END, false)) {
                    return ItemStack.EMPTY;
                }
                ItemStack next = stack.copyWithCount(1);
                stack.shrink(1);
                source.setByPlayer(stack);
                target.setByPlayer(next);
            }
        }
        source.onTake(player, original);
        return original;
    }
}
