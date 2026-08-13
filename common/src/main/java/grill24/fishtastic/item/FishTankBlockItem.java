package grill24.fishtastic.item;

import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.client.tooltip.FishTankMaterialsTooltip;
import grill24.fishtastic.component.FishTankMaterials;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.Optional;

/**
 * The fish tank's item form. Carries the same {@link FishTankMaterials} data component the block
 * entity does, and renders that component as a row of item-stack icons in the tooltip (rather than
 * wordy "Frame: / Sand: / Glass:" text lines) — see {@link FishTankMaterialsTooltip}.
 */
public class FishTankBlockItem extends BlockItem {

    public FishTankBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        FishTankMaterials materials = stack.get(FishtasticDataComponents.FISH_TANK_MATERIALS.value());
        return materials == null ? Optional.empty() : Optional.of(FishTankMaterialsTooltip.of(materials));
    }
}
