package grill24.fishtastic.item;

import grill24.fishtastic.client.FishtasticKeyBinds;
import grill24.fishtastic.compat.GelatinOpenMenuCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * Opens the Leaderboards screen on use. Unlike the Quest Book / Fishopedia there's no alert
 * texture — leaderboards have no claimable state, so there's nothing for a pip to mean.
 * The screen requests its own rows per tab (see
 * {@link grill24.fishtastic.client.LeaderboardScreen}), so opening the menu is all this does.
 */
public class LeaderboardsBookItem extends Item {

    public LeaderboardsBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            GelatinOpenMenuCompat.openFishtasticMenu(serverPlayer);
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                 Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);

        if (FishtasticKeyBinds.openLeaderboards != null && !FishtasticKeyBinds.openLeaderboards.isUnbound()) {
            builder.accept(Component.translatable("tooltip.fishtastic.keybind_hint",
                            FishtasticKeyBinds.openLeaderboards.getTranslatedKeyMessage())
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
