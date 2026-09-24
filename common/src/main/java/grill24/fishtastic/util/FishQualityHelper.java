package grill24.fishtastic.util;

import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.component.FishQuality;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Helper class for working with fish quality data components.
 */
public class FishQualityHelper {

    /**
     * Set the quality of a fish item stack.
     * @param stack The item stack to modify
     * @param quality The quality tier to set
     */
    public static void setQuality(ItemStack stack, FishQuality.Quality quality) {
        if (!stack.isEmpty()) {
            FishtasticItemData.set(stack, FishtasticDataComponents.FISH_QUALITY, new FishQuality(quality));
        }
    }

    /**
     * Get the quality of a fish item stack.
     * @param stack The item stack to check
     * @return The quality tier, or null if not set
     */
    @Nullable
    public static FishQuality.Quality getQuality(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        FishQuality fishQuality = FishtasticItemData.get(stack, FishtasticDataComponents.FISH_QUALITY);
        return fishQuality != null ? fishQuality.quality() : null;
    }

    /**
     * Get the highest quality present across a group of stacks — e.g. every reward on one fishing
     * target, where the group's rarity is whatever its best item is.
     *
     * @param stacks The stacks to scan
     * @return The highest quality found, or null if none of them carry a quality component
     */
    @Nullable
    public static FishQuality.Quality bestQuality(Iterable<ItemStack> stacks) {
        FishQuality.Quality best = null;
        for (ItemStack stack : stacks) {
            FishQuality.Quality quality = getQuality(stack);
            if (quality != null && (best == null || quality.ordinal() > best.ordinal())) {
                best = quality;
            }
        }
        return best;
    }

    /**
     * Check if an item stack has a quality component.
     * @param stack The item stack to check
     * @return True if the stack has a quality component
     */
    public static boolean hasQuality(ItemStack stack) {
        return !stack.isEmpty() && FishtasticItemData.has(stack, FishtasticDataComponents.FISH_QUALITY);
    }

    /**
     * Remove the quality component from an item stack.
     * @param stack The item stack to modify
     */
    public static void removeQuality(ItemStack stack) {
        if (!stack.isEmpty()) {
            FishtasticItemData.remove(stack, FishtasticDataComponents.FISH_QUALITY);
        }
    }

    /**
     * Check if a fish should render with visual effects based on its quality.
     * @param stack The item stack to check
     * @return True if the fish should have visual effects
     */
    public static boolean shouldRenderEffect(ItemStack stack) {
        FishQuality fishQuality = FishtasticItemData.get(stack, FishtasticDataComponents.FISH_QUALITY);
        return fishQuality != null && fishQuality.shouldRenderEffect();
    }

    /**
     * Get the effect intensity for a fish item.
     * @param stack The item stack to check
     * @return Effect intensity (0.0 to 1.0), or 0.0 if no quality set
     */
    public static float getEffectIntensity(ItemStack stack) {
        FishQuality fishQuality = FishtasticItemData.get(stack, FishtasticDataComponents.FISH_QUALITY);
        return fishQuality != null ? fishQuality.getEffectIntensity() : 0.0f;
    }
}
