package grill24.fishtastic.neoforge;

import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Handles adding item size information to tooltips on NeoForge.
 */
public class ItemSizeTooltipHandler {
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (ItemSizeHelper.hasSize(event.getItemStack())) {
            float size = ItemSizeHelper.getSize(event.getItemStack());

            // Format the size with 2 decimal places
            String sizeText = String.format("%.1f", size);

            // Add the size information to the tooltip
            event.getToolTip().add(Component.translatable("tooltip.fishtastic.item_size.cm", sizeText)
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
