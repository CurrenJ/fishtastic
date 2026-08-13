package grill24.fishtastic.client.tooltip;

import grill24.fishtastic.component.FishTankMaterials;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/**
 * Tooltip image for a fish tank item: the three material blocks shown as item-stack icons, in the
 * same order as the fish tank assembly GUI's slots (frame, then glass, then sand — see
 * {@link grill24.fishtastic.menu.FishTankAssemblyMenu}'s FRAME/GLASS/SAND slot Y positions).
 */
public record FishTankMaterialsTooltip(ItemStack frame, ItemStack glass, ItemStack sand) implements TooltipComponent {

    public static FishTankMaterialsTooltip of(FishTankMaterials materials) {
        return new FishTankMaterialsTooltip(
                new ItemStack(materials.frame().asItem()),
                new ItemStack(materials.glass().asItem()),
                new ItemStack(materials.sand().asItem())
        );
    }
}
