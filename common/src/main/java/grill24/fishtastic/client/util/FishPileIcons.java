package grill24.fishtastic.client.util;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.blockentity.FishPileBlockEntity;
import grill24.fishtastic.client.renderer.FishPileBlockItemModel;
import grill24.fishtastic.network.RecentCatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;

import java.util.ArrayList;
import java.util.List;

/** Builds the item stacks that render as a Fish Pile block icon (see {@link FishPileBlockItemModel}). */
public final class FishPileIcons {
    /** Client item definition selecting {@link FishPileBlockItemModel}, applied per stack. */
    public static final ResourceLocation PILE_BLOCK_ITEM_MODEL = Fishtastic.id("fish_pile_block");

    private FishPileIcons() {
    }

    /**
     * Splits {@code catches} into one or more Pile of Fish stacks that each draw as a placed Fish
     * Pile block, bottom block first — a column of them once the requested fish count outgrows the
     * {@link FishPileBlockEntity#MAX_FISH} fish that fit in a single block. The last block in the
     * column is partially filled when {@code maxFish} isn't a whole multiple of that, so a column
     * built this way scales smoothly rather than in whole-block steps.
     *
     * @param catches newest first (as the leaderboard sends them) — reversed here so the oldest
     *                catches settle at the bottom of the bottom block and the newest ends up on
     *                top of the column, the way a real pile grows.
     * @param maxFish how many fish the column may show in total; extra catches are dropped
     */
    public static List<ItemStack> pileBlocks(List<RecentCatch> catches, int maxFish) {
        int capacity = Math.min(catches.size(), Math.max(maxFish, 0));
        List<ItemStack> oldestFirst = new ArrayList<>(capacity);
        for (int i = capacity - 1; i >= 0; i--) {
            ItemStack stack = catches.get(i).toStack();
            if (!stack.isEmpty()) oldestFirst.add(stack);
        }

        List<ItemStack> piles = new ArrayList<>();
        for (int from = 0; from < oldestFirst.size(); from += FishPileBlockEntity.MAX_FISH) {
            int to = Math.min(from + FishPileBlockEntity.MAX_FISH, oldestFirst.size());
            ItemStack pile = new ItemStack(FishtasticItems.PILE_OF_FISH.value());
            List<ItemStackTemplate> blockContents = oldestFirst.subList(from, to).stream()
                    .map(ItemStackTemplate::fromNonEmptyStack)
                    .toList();
            FishtasticItemData.setBundleContents(pile, new BundleContents(blockContents));
            pile.set(DataComponents.ITEM_MODEL, PILE_BLOCK_ITEM_MODEL);
            piles.add(pile);
        }
        return piles;
    }
}
