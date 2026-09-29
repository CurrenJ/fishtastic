package grill24.fishtastic.client;

import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.item.FishtasticFishingRodItem;
import net.minecraft.world.item.ItemStack;

/**
 * Decides which container-slot items get the gold "look here" outline.
 *
 * <p>{@link #matches} is a stateless "would these two go together" check: it looks only at item
 * types, not at what a rod already holds, so a glow means "compatible kind", not "guaranteed to
 * insert" (a rod whose charm slot is full still glows for a charm).
 */
public final class ItemHighlightRules {

    private ItemHighlightRules() {}

    /** True for the three kinds of item a rod can be loaded with. */
    public static boolean isRodEquipment(ItemStack stack) {
        return stack.is(FishtasticItemTags.FISHING_BAIT)
                || stack.is(FishtasticItemTags.FISHING_HOOKS)
                || stack.is(FishtasticItemTags.FISHING_CHARMS);
    }

    public static boolean isFishtasticRod(ItemStack stack) {
        return stack.getItem() instanceof FishtasticFishingRodItem;
    }

    /**
     * Cursor holds bait/hook/charm → rods glow. Cursor holds a rod → bait/hooks/charms glow.
     * Anything else (including an empty cursor) → nothing.
     */
    public static boolean matches(ItemStack cursor, ItemStack candidate) {
        if (cursor.isEmpty() || candidate.isEmpty()) return false;
        if (isRodEquipment(cursor)) return isFishtasticRod(candidate);
        if (isFishtasticRod(cursor)) return isRodEquipment(candidate);
        return false;
    }

    /** {@link #matches} plus the tutorial's "pick up the bait" prompt, which fires with a non-bait cursor. */
    public static boolean shouldHighlight(ItemStack cursor, ItemStack candidate) {
        if (matches(cursor, candidate)) return true;
        return TutorialClientHandler.isPromptingBaitPickup(cursor) && candidate.is(FishtasticItemTags.FISHING_BAIT);
    }
}
