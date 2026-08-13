package grill24.fishtastic.mixin;

import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.component.ItemSize;
import grill24.fishtastic.fishtank.FishTankShape;
import grill24.fishtastic.itemeffect.ItemEffectManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Inject(method = "getTooltipLines", at = @At("RETURN"))
    public void modifyTooltipLines(Item.TooltipContext tooltipContext, Player player, TooltipFlag tooltipFlag, CallbackInfoReturnable<List<Component>> cir) {
        ItemStack itemStack = (ItemStack)(Object)this;
        TooltipDisplay tooltipDisplay = itemStack.get(DataComponents.TOOLTIP_DISPLAY);
        boolean tooltipHidden = tooltipDisplay != null && tooltipDisplay.hideTooltip();
        if (player == null || player.isCreative() || !tooltipHidden) {
            List<Component> tooltipLines = cir.getReturnValue();

            // Append item size information to the tooltip.
            ItemSize sizeProvider = itemStack.get(FishtasticDataComponents.ITEM_SIZE.value());
            if (sizeProvider != null) {
                sizeProvider.addToTooltip(tooltipContext, tooltipLines::add, tooltipFlag, itemStack);
            }

            // Append fish quality information to the tooltip.
            FishQuality qualityProvider = itemStack.get(FishtasticDataComponents.FISH_QUALITY.value());
            if (qualityProvider != null) {
                qualityProvider.addToTooltip(tooltipContext, tooltipLines::add, tooltipFlag, itemStack);
            }

            // Append the fish tank's shape name to the tooltip.
            FishTankShape shape = itemStack.get(FishtasticDataComponents.FISH_TANK_SHAPE.value());
            if (shape != null) {
                shape.addToTooltip(tooltipContext, tooltipLines::add, tooltipFlag, itemStack);
            }
        }
    }

    /**
     * Make items with matching item effects shimmer with the foil/glint effect.
     */
    @Inject(method = "hasFoil", at = @At("HEAD"), cancellable = true)
    public void addQualityFoilEffect(CallbackInfoReturnable<Boolean> cir) {
        ItemStack itemStack = (ItemStack)(Object)this;
        if (ItemEffectManager.shouldShowEffect(itemStack)) {
            cir.setReturnValue(true);
        }
    }
}
