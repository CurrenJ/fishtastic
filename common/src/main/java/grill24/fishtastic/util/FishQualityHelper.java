package grill24.fishtastic.util;

import grill24.fishtastic.FishtasticDataComponents;
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
            stack.set(FishtasticDataComponents.FISH_QUALITY.value(), new FishQuality(quality));
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

        FishQuality fishQuality = stack.get(FishtasticDataComponents.FISH_QUALITY.value());
        return fishQuality != null ? fishQuality.quality() : null;
    }

    /**
     * Check if an item stack has a quality component.
     * @param stack The item stack to check
     * @return True if the stack has a quality component
     */
    public static boolean hasQuality(ItemStack stack) {
        return !stack.isEmpty() && stack.has(FishtasticDataComponents.FISH_QUALITY.value());
    }

    /**
     * Remove the quality component from an item stack.
     * @param stack The item stack to modify
     */
    public static void removeQuality(ItemStack stack) {
        if (!stack.isEmpty()) {
            stack.remove(FishtasticDataComponents.FISH_QUALITY.value());
        }
    }

    /**
     * Check if a fish should render with visual effects based on its quality.
     * @param stack The item stack to check
     * @return True if the fish should have visual effects
     */
    public static boolean shouldRenderEffect(ItemStack stack) {
        FishQuality fishQuality = stack.get(FishtasticDataComponents.FISH_QUALITY.value());
        return fishQuality != null && fishQuality.shouldRenderEffect();
    }

    /**
     * Get the effect intensity for a fish item.
     * @param stack The item stack to check
     * @return Effect intensity (0.0 to 1.0), or 0.0 if no quality set
     */
    public static float getEffectIntensity(ItemStack stack) {
        FishQuality fishQuality = stack.get(FishtasticDataComponents.FISH_QUALITY.value());
        return fishQuality != null ? fishQuality.getEffectIntensity() : 0.0f;
    }
}
