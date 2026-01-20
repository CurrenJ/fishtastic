package grill24.fishtastic.item;

import grill24.fishtastic.FishtasticDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Base class for items that support size data.
 */
public class SizedItem extends Item {
    public SizedItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        Float size = stack.get(FishtasticDataComponents.ITEM_SIZE.value());
        if (size != null && size > 0) {
            tooltipComponents.add(Component.translatable("tooltip.fishtastic.size", String.format("%.2f", size))
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
