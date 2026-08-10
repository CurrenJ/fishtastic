package grill24.fishtastic.client.tooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

public record RodGearTooltip(ItemStack bait, ItemStack hook, ItemStack charm) implements TooltipComponent {
}
