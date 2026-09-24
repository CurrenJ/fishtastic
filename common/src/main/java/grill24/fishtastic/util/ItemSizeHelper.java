package grill24.fishtastic.util;

import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.component.ItemSize;
import net.minecraft.world.item.ItemStack;

/**
 * Utility class for working with item sizes.
 */
public class ItemSizeHelper {
    /**
     * Sets the size data on an ItemStack.
     * @param stack The ItemStack to modify
     * @param size The size value to set (should be positive)
     * @return The modified ItemStack
     */
    public static ItemStack setSize(ItemStack stack, float size) {
        if (!stack.isEmpty() && size > 0) {
            FishtasticItemData.set(stack, FishtasticDataComponents.ITEM_SIZE, new ItemSize(size));
        }
        return stack;
    }

    /**
     * Gets the size data from an ItemStack.
     * @param stack The ItemStack to read from
     * @return The size value, or 0.0f if not present
     */
    public static float getSize(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0f;
        }
        ItemSize itemSize = FishtasticItemData.get(stack, FishtasticDataComponents.ITEM_SIZE);
        return itemSize != null ? itemSize.size() : 0.0f;
    }

    /**
     * Checks if an ItemStack has size data.
     * @param stack The ItemStack to check
     * @return true if the stack has size data, false otherwise
     */
    public static boolean hasSize(ItemStack stack) {
        return !stack.isEmpty() && FishtasticItemData.has(stack, FishtasticDataComponents.ITEM_SIZE);
    }

    /**
     * Removes the size data from an ItemStack.
     * @param stack The ItemStack to modify
     * @return The modified ItemStack
     */
    public static ItemStack removeSize(ItemStack stack) {
        if (!stack.isEmpty()) {
            FishtasticItemData.remove(stack, FishtasticDataComponents.ITEM_SIZE);
        }
        return stack;
    }

    /**
     * Creates a copy of an ItemStack with a specific size.
     * @param stack The ItemStack to copy
     * @param size The size value to set
     * @return A new ItemStack with the size data
     */
    public static ItemStack withSize(ItemStack stack, float size) {
        ItemStack copy = stack.copy();
        return setSize(copy, size);
    }
}
