package grill24.fishtastic.client.util;

import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.blockentity.FishPileBlockEntity;
import grill24.fishtastic.client.renderer.FishPileBlockItemModel;
import grill24.fishtastic.network.RecentCatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import grill24.fishtastic.component.BundleContents;
import net.minecraft.world.item.component.CustomModelData;

import java.util.ArrayList;
import java.util.List;

/** Builds the item stacks that render as a Fish Pile block icon (see {@link FishPileBlockItemModel}). */
public final class FishPileIcons {
    /**
     * PORT-ONLY: marks a Pile of Fish stack to be drawn as a pile <em>block</em>. 26.1 sets
     * {@code minecraft:item_model} to its {@code fish_pile_block} item model instead; 1.21.1 has
     * neither, and the Pile of Fish's item renderer ({@code FishtasticItemRenderers}) picks
     * {@link FishPileBlockItemModel} when it sees this value. The stacks are client-only (built
     * for the leaderboard), so a vanilla component is enough.
     */
    public static final CustomModelData PILE_BLOCK_MARKER = new CustomModelData(1913);

    /** Whether {@code stack} was built by {@link #pileBlocks} to draw as a pile block. */
    public static boolean isPileBlock(ItemStack stack) {
        return PILE_BLOCK_MARKER.equals(stack.get(DataComponents.CUSTOM_MODEL_DATA));
    }

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
            List<ItemStack> blockContents = oldestFirst.subList(from, to).stream()
                    .map(ItemStack::copy)
                    .toList();
            FishtasticItemData.setBundleContents(pile, new BundleContents(blockContents));
            pile.set(DataComponents.CUSTOM_MODEL_DATA, PILE_BLOCK_MARKER);
            piles.add(pile);
        }
        return piles;
    }
}
