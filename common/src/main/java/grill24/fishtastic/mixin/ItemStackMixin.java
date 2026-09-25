package grill24.fishtastic.mixin;

import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.component.ItemSize;
import grill24.fishtastic.fishtank.FishTankShape;
import grill24.fishtastic.itemeffect.ItemEffectManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Inject(method = "getTooltipLines", at = @At("RETURN"))
    public void modifyTooltipLines(@Nullable Player player, TooltipFlag tooltipFlag, CallbackInfoReturnable<List<Component>> cir) {
        ItemStack itemStack = (ItemStack)(Object)this;
        boolean tooltipHidden = FishtasticItemData.isTooltipHidden(itemStack);
        if (player == null || player.isCreative() || !tooltipHidden) {
            List<Component> tooltipLines = cir.getReturnValue();
            Level level = player == null ? null : player.level();

            // Append item size information to the tooltip.
            ItemSize sizeProvider = FishtasticItemData.get(itemStack, FishtasticDataComponents.ITEM_SIZE);
            if (sizeProvider != null) {
                sizeProvider.addToTooltip(level, tooltipLines::add, tooltipFlag);
            }

            // Append fish quality information to the tooltip.
            FishQuality qualityProvider = FishtasticItemData.get(itemStack, FishtasticDataComponents.FISH_QUALITY);
            if (qualityProvider != null) {
                qualityProvider.addToTooltip(level, tooltipLines::add, tooltipFlag);
            }

            // Append the fish tank's shape name to the tooltip.
            FishTankShape shape = FishtasticItemData.get(itemStack, FishtasticDataComponents.FISH_TANK_SHAPE);
            if (shape != null) {
                shape.addToTooltip(level, tooltipLines::add, tooltipFlag);
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
