package grill24.fishtastic.client.tooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

public record RodBaitTooltip(ItemStack bait) implements TooltipComponent {
}
