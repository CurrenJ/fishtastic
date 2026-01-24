package grill24.fishtastic.examples;

import grill24.fishtastic.util.FishingTarget;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Random;

/**
 * Examples of creating fishing targets with multiple reward items.
 * This demonstrates the new multi-item reward system.
 */
public class MultiItemTargetExamples {

    /**
     * Example 1: Single-item target (backward compatible)
     */
    public static FishingTarget createSingleItemTarget(Random random) {
        List<ItemStack> rewards = List.of(
                new ItemStack(Items.DIAMOND)
        );
        return new FishingTarget(rewards, FishingTarget.TargetCategory.TREASURE, random);
    }

    /**
     * Example 2: Treasure chest with multiple items
     */
    public static FishingTarget createTreasureChest(Random random) {
        List<ItemStack> rewards = List.of(
                new ItemStack(Items.DIAMOND, 3),
                new ItemStack(Items.EMERALD, 2),
                new ItemStack(Items.GOLD_INGOT, 5)
        );
        return new FishingTarget(rewards, FishingTarget.TargetCategory.TREASURE, random);
    }

    /**
     * Example 3: Fish catch with bonus items
     */
    public static FishingTarget createFishWithBonus(Random random) {
        List<ItemStack> rewards = List.of(
                new ItemStack(Items.SALMON),
                new ItemStack(Items.BONE_MEAL, 2)  // Bonus item
        );
        return new FishingTarget(rewards, FishingTarget.TargetCategory.FISH, random);
    }

    /**
     * Example 4: Rare treasure with multiple high-value items
     */
    public static FishingTarget createRareTreasure(Random random) {
        List<ItemStack> rewards = List.of(
                new ItemStack(Items.DIAMOND, 5),
                new ItemStack(Items.NETHERITE_SCRAP, 2),
                new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1),
                new ItemStack(Items.NAUTILUS_SHELL, 3)
        );
        return new FishingTarget(rewards, FishingTarget.TargetCategory.TREASURE, random);
    }

    /**
     * Example 5: School of fish (multiple fish items)
     */
    public static FishingTarget createSchoolOfFish(Random random) {
        List<ItemStack> rewards = List.of(
                new ItemStack(Items.COD, 3),
                new ItemStack(Items.TROPICAL_FISH, 2),
                new ItemStack(Items.SALMON, 1)
        );
        return new FishingTarget(rewards, FishingTarget.TargetCategory.FISH, random);
    }

    /**
     * Example 6: Mixed category (fish + treasure)
     * Note: Category determines the display icon, not the actual items
     */
    public static FishingTarget createMixedRewards(Random random) {
        List<ItemStack> rewards = List.of(
                new ItemStack(Items.PUFFERFISH),
                new ItemStack(Items.EMERALD),  // Treasure item
                new ItemStack(Items.EXPERIENCE_BOTTLE)
        );
        // Display as FISH since the main item is a fish
        return new FishingTarget(rewards, FishingTarget.TargetCategory.FISH, random);
    }

    /**
     * Example 7: Creating target with specific initial position
     * Useful for testing or custom spawn patterns
     */
    public static FishingTarget createTargetAtPosition(Random random, float position) {
        List<ItemStack> rewards = List.of(
                new ItemStack(Items.DIAMOND, 10),
                new ItemStack(Items.GOLD_BLOCK, 5)
        );
        return new FishingTarget(
                rewards,
                FishingTarget.TargetCategory.TREASURE,
                random,
                position  // 0.0 to 1.0
        );
    }

    /**
     * Example server-side: Generating targets with loot table results
     * This shows how you might integrate with Minecraft loot tables
     */
    public static FishingTarget createFromLootTable(List<ItemStack> lootTableResults, Random random) {
        // Determine category based on first item in loot
        FishingTarget.TargetCategory category = FishingTarget.TargetCategory.TREASURE;
        if (!lootTableResults.isEmpty()) {
            ItemStack firstItem = lootTableResults.get(0);
            if (firstItem.is(net.minecraft.tags.ItemTags.FISHES)) {
                category = FishingTarget.TargetCategory.FISH;
            }
        }

        return new FishingTarget(lootTableResults, category, random);
    }
}
