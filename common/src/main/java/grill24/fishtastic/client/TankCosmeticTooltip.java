package grill24.fishtastic.client;

import grill24.fishtastic.FishtasticBlockTags;
import grill24.fishtastic.item.FishTankCosmeticItem;
import grill24.fishtastic.item.FishTankStructureCosmeticItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Marks every item usable as a fish tank cosmetic — custom {@link FishTankCosmeticItem}s and
 * {@link FishTankStructureCosmeticItem}s, plus plain vanilla {@link BlockItem}s whose block is
 * tagged {@code fishtastic:tank_cosmetics} — with a grey tooltip hint, replacing the older
 * approach of baking "(Tank Cosmetic)" into the item's display name.
 */
public final class TankCosmeticTooltip {
    private TankCosmeticTooltip() {}

    public static boolean isEligible(ItemStack stack) {
        if (stack.getItem() instanceof FishTankCosmeticItem) return true;
        if (stack.getItem() instanceof FishTankStructureCosmeticItem) return true;
        return stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock().defaultBlockState().is(FishtasticBlockTags.TANK_COSMETICS);
    }

    /** Inserts the hint right after the item's name (index 0), matching how the old name suffix read. */
    public static void append(ItemStack stack, List<Component> tooltip) {
        if (isEligible(stack)) {
            tooltip.add(1, Component.translatable("tooltip.fishtastic.tank_cosmetic")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
