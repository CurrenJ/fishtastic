package grill24.fishtastic.mixin;

import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.component.ItemSize;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
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
public class ItemStackMixin {
    @Inject(method = "getTooltipLines", at = @At("RETURN"))
    public void modifyTooltipLines(Item.TooltipContext tooltipContext, Player player, TooltipFlag tooltipFlag, CallbackInfoReturnable<List<Component>> cir) {
        ItemStack itemStack = (ItemStack)(Object)this;
        if(player == null || player.isCreative() || !itemStack.has(DataComponents.HIDE_TOOLTIP)) {
            List<Component> tooltipLines = cir.getReturnValue();

            // Append item size information to the tooltip.
            ItemSize tooltipProvider = itemStack.get(FishtasticDataComponents.ITEM_SIZE.value());
            if (tooltipProvider != null) {
                tooltipProvider.addToTooltip(tooltipContext, tooltipLines::add, tooltipFlag);
            }
        }
    }
}
