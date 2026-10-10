package grill24.fishtastic.client;

import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.FishtasticItems;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;

import java.util.ArrayList;
import java.util.List;

/**
 * The fish of one species a player is carrying right now (docs/fish-companions.md §8.2): loose
 * stacks plus the contents of any Pile of Fish. This is the picker's list, so it must mirror the
 * server's own search ({@code Companions.takeOneMatching}) — same inventory span, same
 * loose-then-piles order, same "identical in <em>every</em> component (count aside)" rule — or the
 * picker could show a row the server then refuses to find.
 *
 * <p>Client-side and read-only: the chosen row's {@link Group#representative()} <i>describes</i>
 * the fish in the bond packet; the server re-finds and takes the real one.
 */
public final class CarriedFish {
    private CarriedFish() {}

    /** One picker row: the first matching fish seen, and how many match it exactly. */
    public record Group(ItemStack representative, int count) {}

    /** Every group of {@code species} in {@code player}'s inventory, loose stacks first. */
    public static List<Group> groupsOf(Player player, Item species) {
        List<Group> groups = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.is(species)) {
                add(groups, stack);
            } else if (stack.is(FishtasticItems.PILE_OF_FISH.value())) {
                for (ItemStackTemplate template : FishtasticItemData.bundleContentsOrEmpty(stack).items()) {
                    ItemStack fish = template.create();
                    if (fish.is(species)) add(groups, fish);
                }
            }
        }
        return groups;
    }

    /** Adds a stack to the group identical to it in every component, or starts a new group. */
    private static void add(List<Group> groups, ItemStack stack) {
        for (int i = 0; i < groups.size(); i++) {
            Group group = groups.get(i);
            if (ItemStack.isSameItemSameComponents(group.representative(), stack)) {
                groups.set(i, new Group(group.representative(), group.count() + stack.getCount()));
                return;
            }
        }
        groups.add(new Group(stack.copy(), stack.getCount()));
    }

    /** Same-bundle helper for tests: groups a list of loose stacks by full-component identity. */
    public static List<Group> group(List<ItemStack> stacks) {
        List<Group> groups = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            add(groups, stack);
        }
        return groups;
    }
}
