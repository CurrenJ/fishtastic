package grill24.fishtastic.menu;

import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.FishtasticMenuTypes;
import grill24.fishtastic.companion.CompanionBondGate;
import grill24.fishtastic.companion.CompanionParty;
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

/**
 * The companion party's three persistent slots (docs/fish-companions-party.md §5.3) above the
 * player's inventory, with ordinary, server-authoritative vanilla inventory handling. Menu slots
 * 0–2 are party slots 0–2; slot 0 is the lead. Moving a fish between them reorders the party.
 */
public final class CompanionMenu extends AbstractContainerMenu {
    /** Installed only during client initialization; the dedicated server never loads client classes. */
    public static java.util.function.Predicate<ItemStack> clientEligibility = stack -> true;
    public static final int PARTY_SLOTS = CompanionParty.SIZE;
    public static final int INVENTORY_START = PARTY_SLOTS;
    public static final int INVENTORY_END = INVENTORY_START + 36;
    /** Menu x of each party slot's item origin; the frames are 26 px wide, centred on the panel. */
    public static final int[] PARTY_SLOT_X = {54, 80, 106};
    public static final int PARTY_SLOT_Y = 34;
    private final Player owner;
    private final SimpleContainer party = new SimpleContainer(PARTY_SLOTS) {
        @Override public void setChanged() {
            super.setChanged();
            if (readingStorage || !(owner instanceof ServerPlayer player)) return;
            for (int i = 0; i < PARTY_SLOTS; i++) {
                if (!FishtasticItemData.matches(getItem(i), CompanionStorage.get(player, i))) Companions.setFromMenu(player, i, getItem(i));
            }
        }
    };
    private final DataSlot species = DataSlot.standalone();
    private boolean readingStorage;
    /** Tracks a fish taken from a party slot until it is actually put back in the inventory. */
    private ItemStack returningFish = ItemStack.EMPTY;

    public CompanionMenu(int id, Inventory inventory) {
        this(id, inventory, null);
    }

    public CompanionMenu(int id, Inventory inventory, Item highlightedSpecies) {
        super(FishtasticMenuTypes.COMPANION.value(), id);
        owner = inventory.player;
        species.set(highlightedSpecies == null ? -1 : BuiltInRegistries.ITEM.getId(highlightedSpecies));
        addDataSlot(species);
        readingStorage = true;
        try {
            for (int i = 0; i < PARTY_SLOTS; i++) party.setItem(i, CompanionStorage.get(owner, i).copy());
        } finally {
            readingStorage = false;
        }
        for (int i = 0; i < PARTY_SLOTS; i++) {
            addSlot(new Slot(party, i, PARTY_SLOT_X[i], PARTY_SLOT_Y) {
                @Override public int getMaxStackSize() { return 1; }
                @Override public boolean mayPlace(ItemStack stack) {
                    return stack.is(FishtasticItemTags.FISH)
                            && (owner instanceof ServerPlayer player ? CompanionBondGate.isUnlocked(player, stack) : clientEligibility.test(stack));
                }
            });
        }
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

    public static boolean isPartySlot(int index) {
        return index >= 0 && index < PARTY_SLOTS;
    }

    /**
     * Attachment changes (pile selection, rename, return) also update this menu's mirror. Packets
     * handled earlier in the same tick can change the attachment before the next broadcast, so a
     * click must never act on a stale mirror: that would hand back a fish {@code equip} already returned.
     */
    private void pullStorage() {
        if (!(owner instanceof ServerPlayer)) return;
        readingStorage = true;
        try {
            for (int i = 0; i < PARTY_SLOTS; i++) {
                ItemStack stored = CompanionStorage.get(owner, i);
                if (!FishtasticItemData.matches(stored, party.getItem(i))) party.setItem(i, stored.copy());
            }
        } finally {
            readingStorage = false;
        }
    }

    @Override public void broadcastChanges() {
        pullStorage();
        super.broadcastChanges();
    }

    @Override public boolean stillValid(Player player) { return player == owner && player.isAlive(); }

    @Override public void clicked(int slot, int button, net.minecraft.world.inventory.ClickType input, Player player) {
        pullStorage();
        ItemStack partyBefore = isPartySlot(slot) ? getSlot(slot).getItem().copy() : ItemStack.EMPTY;
        ItemStack cursorBefore = getCarried().copy();
        try {
            if (input == net.minecraft.world.inventory.ClickType.QUICK_MOVE && (button == 0 || button == 1)) {
                // Vanilla repeats quickMove while the source still contains the same item type.
                // A one-fish companion move must run exactly once, even for same-species fish.
                quickMoveStack(player, slot);
                return;
            }
            if (isPartySlot(slot) && input == net.minecraft.world.inventory.ClickType.PICKUP && (button == 0 || button == 1)) {
                clickCompanion(player, slot);
                return;
            }
            super.clicked(slot, button, input, player);
        } finally {
            if (player instanceof ServerPlayer serverPlayer) {
                if (!partyBefore.isEmpty() && !FishtasticItemData.matches(partyBefore, getSlot(slot).getItem())
                        && FishtasticItemData.isSameItemSameData(partyBefore, getCarried())) {
                    returningFish = partyBefore.copyWithCount(1);
                }
                boolean putAway = slot >= INVENTORY_START && slot < INVENTORY_END && !returningFish.isEmpty()
                        && FishtasticItemData.isSameItemSameData(returningFish, cursorBefore)
                        && (!FishtasticItemData.isSameItemSameData(cursorBefore, getCarried())
                        || getCarried().getCount() < cursorBefore.getCount());
                boolean hotbarReturn = isPartySlot(slot) && !partyBefore.isEmpty()
                        && input == net.minecraft.world.inventory.ClickType.SWAP
                        && !FishtasticItemData.matches(partyBefore, getSlot(slot).getItem());
                if (putAway || hotbarReturn) {
                    // TODO(F15): CompanionTutorialManager.onReturned(serverPlayer)
                    returningFish = ItemStack.EMPTY;
                } else if (!returningFish.isEmpty() && !FishtasticItemData.isSameItemSameData(returningFish, getCarried())) {
                    returningFish = ItemStack.EMPTY;
                }
            }
        }
    }

    @Override public void removed(Player player) {
        boolean returning = !returningFish.isEmpty() && FishtasticItemData.isSameItemSameData(returningFish, getCarried())
                && Companions.hasRoomToReturn(player, returningFish);
        super.removed(player);
        if (returning && player instanceof ServerPlayer serverPlayer) {
            // TODO(F15): CompanionTutorialManager.onReturned(serverPlayer)
        }
    }

    /**
     * Plain take/place/swap for a party slot. Vanilla would first offer the click to the items
     * themselves, and a fish clicked onto a fish auto-piles (SizedItemClickMixin) straight into the
     * slot without asking {@link Slot#mayPlace} — two fish stored as one companion.
     */
    private void clickCompanion(Player player, int index) {
        Slot slot = slots.get(index);
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

    public enum PileTake { TAKEN, CURSOR_BUSY, NOT_A_FISH, NOT_CARRIED }

    /**
     * The pile strip (§5.5): takes one fish described by {@code wanted} out of a Pile of Fish in the
     * inventory and puts it on this menu's cursor. Like bonding, the client only describes the fish;
     * the server finds an identical one itself. Only with an empty cursor, rechecked here.
     */
    public PileTake takeFromPile(ServerPlayer player, ItemStack wanted) {
        if (!getCarried().isEmpty()) return PileTake.CURSOR_BUSY;
        if (wanted.isEmpty() || !wanted.is(FishtasticItemTags.FISH)) return PileTake.NOT_A_FISH;
        ItemStack taken = Companions.takeOneFromPiles(player, wanted);
        if (taken.isEmpty()) return PileTake.NOT_CARRIED;
        setCarried(taken);
        return PileTake.TAKEN;
    }

    /** Double-click collecting a stack must not quietly pull a companion out with it. */
    @Override public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
        return !isPartySlot(target.index);
    }

    /**
     * Shift-click: out of a party slot returns that fish to the inventory; from the inventory, one
     * fish goes into the first empty party slot. A full party takes nothing — shift-click never
     * replaces a companion (§5.3).
     */
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot source = slots.get(index);
        if (!source.hasItem() || !source.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = source.getItem(), original = stack.copy();
        if (isPartySlot(index)) {
            if (!moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) return ItemStack.EMPTY;
            source.setByPlayer(stack.isEmpty() ? ItemStack.EMPTY : stack);
            // TODO(F15): CompanionTutorialManager.onReturned(player)
        } else {
            Slot target = null;
            for (int i = 0; i < PARTY_SLOTS; i++) {
                if (!slots.get(i).hasItem()) {
                    target = slots.get(i);
                    break;
                }
            }
            if (target == null || !target.mayPlace(stack)) return ItemStack.EMPTY;
            ItemStack next = stack.copyWithCount(1);
            stack.shrink(1);
            source.setByPlayer(stack.isEmpty() ? ItemStack.EMPTY : stack);
            target.setByPlayer(next);
        }
        source.onTake(player, original);
        return original;
    }
}
