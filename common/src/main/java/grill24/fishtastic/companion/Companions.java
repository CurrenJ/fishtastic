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
 * Server-side companion operations (docs/fish-companions.md §8). The fish item <i>is</i> the
 * companion: bonding moves one fish out of the player's inventory into {@link CompanionStorage}, and
 * unequipping gives that exact stack back. Every change happens in one call on the server thread, so
 * a fish is always in exactly one place.
 */
public final class Companions {
    private Companions() {}

    public enum BondResult { BONDED, NOT_A_FISH, LOCKED, NOT_CARRIED }

    /**
     * Bonds with a fish the player is carrying. {@code wanted} only <i>describes</i> the fish — the
     * client sends what it showed in the picker — and the server finds a fish identical in every
     * component (count aside) in the inventory or inside a Pile of Fish, never trusting a slot index.
     * If nothing matches (the fish was dropped while the screen was open), nothing changes. A
     * previously equipped companion comes back to the inventory, or drops at the player's feet.
     */
    public static BondResult bond(ServerPlayer player, ItemStack wanted) {
        if (wanted.isEmpty() || !wanted.is(FishtasticItemTags.FISH)) return BondResult.NOT_A_FISH;
        if (!CompanionBondGate.isUnlocked(player, wanted)) return BondResult.LOCKED;
        ItemStack taken = takeOneMatching(player, wanted);
        if (taken.isEmpty()) return BondResult.NOT_CARRIED;
        equip(player, taken);
        return BondResult.BONDED;
    }

    /** Gives the companion back, exactly as it was bonded. Returns false when there was none. */
    public static boolean unequip(ServerPlayer player) {
        ItemStack current = CompanionStorage.get(player);
        if (current.isEmpty()) return false;
        ItemStack returned = current.copy();
        CompanionStorage.set(player, ItemStack.EMPTY);
        RemoveTankEntryPacket.giveOrDrop(player, returned);
        return true;
    }

    /**
     * Renames the equipped companion (docs/fish-companions.md §7): the name is the stored fish's
     * custom name, so this rewrites that one component and syncs; blank clears it back to the
     * species name. Returns false when there is no companion to rename.
     */
    public static boolean rename(ServerPlayer player, String name) {
        ItemStack current = CompanionStorage.get(player);
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
        CompanionStorage.set(player, renamed);
        return true;
    }

    /**
     * Puts {@code fish} in the companion slot, returning whatever was there. The caller owns
     * {@code fish} — {@link #bond} has already taken it from the inventory; the debug
     * {@code /fishtastic companion give} conjures it.
     */
    public static void equip(ServerPlayer player, ItemStack fish) {
        ItemStack previous = CompanionStorage.get(player).copy();
        CompanionStorage.set(player, fish.copyWithCount(1));
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
