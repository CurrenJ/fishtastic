package grill24.fishtastic.companion;

import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.network.RemoveTankEntryPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side companion operations (docs/fish-companions-party.md §5.1). The fish item <i>is</i> the
 * companion: bonding moves one fish out of the player's inventory into a slot of the player's
 * {@link CompanionParty}, and unequipping gives that exact stack back. Every change happens in one
 * call on the server thread, so a fish is always in exactly one place.
 *
 * <p>The party has three slots and slot 0 is the lead. Any three bonded fish may share it,
 * duplicates included: three of one species is how a player builds a school (§3).
 */
public final class Companions {
    private Companions() {}

    /** A bond request with no slot named: fill the first empty slot, never replace one. */
    public static final int FIRST_EMPTY = -1;

    /** Vanilla menu clicks already move the real item; persist that slot without returning it again. */
    public static void setFromMenu(ServerPlayer player, int slot, ItemStack fish) {
        CompanionStorage.set(player, slot, fish.isEmpty() ? ItemStack.EMPTY : fish.copyWithCount(1));
    }

    public enum BondResult { BONDED, NOT_A_FISH, LOCKED, NOT_CARRIED, INVENTORY_FULL, PARTY_FULL }

    /** Bonds into the first empty slot; a full party answers {@link BondResult#PARTY_FULL}. */
    public static BondResult bond(ServerPlayer player, ItemStack wanted) {
        return bond(player, wanted, FIRST_EMPTY);
    }

    /**
     * Bonds with a fish the player is carrying. {@code wanted} only <i>describes</i> the fish — the
     * client sends what it showed — and the server finds a fish identical in every component (count
     * aside) in the inventory or inside a Pile of Fish, never trusting an inventory index. If nothing
     * matches (the fish was dropped while the screen was open), nothing changes.
     *
     * <p>{@code slot} is {@link #FIRST_EMPTY} (a full party answers {@link BondResult#PARTY_FULL}
     * and changes nothing) or a party slot the player chose explicitly, whose previous fish comes
     * back to the inventory, or drops at the player's feet.
     */
    public static BondResult bond(ServerPlayer player, ItemStack wanted, int slot) {
        if (wanted.isEmpty() || !wanted.is(FishtasticItemTags.FISH)) return BondResult.NOT_A_FISH;
        if (slot != FIRST_EMPTY && !CompanionParty.validSlot(slot)) return BondResult.NOT_A_FISH;
        if (!CompanionBondGate.isUnlocked(player, wanted)) return BondResult.LOCKED;
        int target = slot == FIRST_EMPTY ? CompanionStorage.party(player).firstEmpty() : slot;
        if (target == FIRST_EMPTY) return BondResult.PARTY_FULL;
        ItemStack previous = CompanionStorage.get(player, target);
        if (!previous.isEmpty() && !hasRoomToReturn(player, previous)) {
            boolean freesSlot = false;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack carried = player.getInventory().getItem(i);
                if (sameFish(carried, wanted)) {
                    // Mirror takeOneMatching's first-match order, rather than assuming a later
                    // single fish will be taken. Removing a matching stack also creates merge room.
                    freesSlot = (i < 36 && carried.getCount() == 1) || sameFish(previous, wanted);
                    break;
                }
            }
            if (!freesSlot) return BondResult.INVENTORY_FULL;
        }
        ItemStack taken = takeOneMatching(player, wanted);
        if (taken.isEmpty()) return BondResult.NOT_CARRIED;
        equip(player, target, taken);
        return BondResult.BONDED;
    }

    /** Gives the fish in {@code slot} back, exactly as it was bonded. Returns false when there was none. */
    public static boolean unequip(ServerPlayer player, int slot) {
        ItemStack current = CompanionStorage.get(player, slot);
        if (current.isEmpty()) return false;
        ItemStack returned = current.copy();
        CompanionStorage.set(player, slot, ItemStack.EMPTY);
        RemoveTankEntryPacket.giveOrDrop(player, returned);
        return true;
    }

    public static boolean hasRoomToReturn(net.minecraft.world.entity.player.Player player, ItemStack fish) {
        if (fish.isEmpty()) return true;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty() || (sameFish(stack, fish) && stack.getCount() < Math.min(stack.getMaxStackSize(), 64))) return true;
        }
        return false;
    }

    /**
     * Renames the companion in {@code slot} (docs/fish-companions.md §7): the name is the stored
     * fish's custom name, so this rewrites that one component and syncs; blank clears it back to the
     * species name. Returns false when the slot is empty.
     */
    public static boolean rename(ServerPlayer player, int slot, String name) {
        ItemStack current = CompanionStorage.get(player, slot);
        if (current.isEmpty()) return false;
        ItemStack renamed = current.copy();
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            renamed.remove(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        } else {
            if (trimmed.length() > grill24.fishtastic.network.RenameCompanionPacket.MAX_NAME_LENGTH) {
                trimmed = trimmed.substring(0, grill24.fishtastic.network.RenameCompanionPacket.MAX_NAME_LENGTH);
            }
            renamed.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    net.minecraft.network.chat.Component.literal(trimmed));
        }
        CompanionStorage.set(player, slot, renamed);
        return true;
    }

    /**
     * Puts {@code fish} in {@code slot}, returning whatever was there to the inventory. The caller
     * owns {@code fish} — {@link #bond} has already taken it from the inventory; the debug
     * {@code /fishtastic companion give} conjures it.
     */
    public static void equip(ServerPlayer player, int slot, ItemStack fish) {
        ItemStack previous = CompanionStorage.get(player, slot).copy();
        CompanionStorage.set(player, slot, fish.copyWithCount(1));
        RemoveTankEntryPacket.giveOrDrop(player, previous);
    }

    /**
     * Removes one fish matching {@code wanted} from the player's inventory — loose stacks first,
     * then the contents of any Pile of Fish — and returns it, or {@link ItemStack#EMPTY}. A pile
     * emptied by this is removed, and a pile left holding one fish becomes that fish, the same way
     * {@code PileOfFishItem} treats a pile it pulls the second-to-last fish out of.
     */
    static ItemStack takeOneMatching(ServerPlayer player, ItemStack wanted) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (sameFish(stack, wanted)) {
                ItemStack one = stack.copyWithCount(1);
                stack.shrink(1);
                inventory.setChanged();
                return one;
            }
        }
        return takeOneFromPiles(player, wanted);
    }

    /** {@link #takeOneMatching}'s pile half: only fish inside a Pile of Fish, never a loose one. */
    public static ItemStack takeOneFromPiles(ServerPlayer player, ItemStack wanted) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack pile = inventory.getItem(i);
            if (!pile.is(FishtasticItems.PILE_OF_FISH.value())) continue;
            BundleContents contents = FishtasticItemData.bundleContentsOrEmpty(pile);
            List<ItemStackTemplate> items = contents.items();
            for (int j = 0; j < items.size(); j++) {
                ItemStack fish = items.get(j).create();
                if (!sameFish(fish, wanted)) continue;
                List<ItemStackTemplate> rest = new ArrayList<>(items);
                if (fish.getCount() > 1) {
                    rest.set(j, items.get(j).withCount(fish.getCount() - 1));
                } else {
                    rest.remove(j);
                }
                if (rest.isEmpty()) {
                    inventory.setItem(i, ItemStack.EMPTY);
                } else if (rest.size() == 1) {
                    inventory.setItem(i, rest.get(0).create());
                } else {
                    FishtasticItemData.setBundleContents(pile, new BundleContents(rest));
                }
                inventory.setChanged();
                return fish.copyWithCount(1);
            }
        }
        return ItemStack.EMPTY;
    }

    /** Same fish in every component — species, size, quality, name — whatever the counts. */
    static boolean sameFish(ItemStack candidate, ItemStack wanted) {
        return !candidate.isEmpty() && ItemStack.isSameItemSameComponents(candidate, wanted);
    }
}
