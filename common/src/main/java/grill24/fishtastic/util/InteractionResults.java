package grill24.fishtastic.util;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Pre-1.20.5, {@code Block#use} returns a single {@link InteractionResult} (no
 * {@code useItemOn}/{@code useWithoutItem} split, and no {@code ItemInteractionResult} wrapper
 * type at all — see {@code FishTankBlock#use}), and {@code Item#use} returns
 * {@link InteractionResultHolder}, not {@link InteractionResult} directly. Only the latter still
 * needs a conversion helper on this port.
 */
public final class InteractionResults {
    private InteractionResults() {}

    /** An {@code Item#use} result that leaves the held stack as it is. */
    public static InteractionResultHolder<ItemStack> forItemUse(InteractionResult result, Player player, InteractionHand hand) {
        return new InteractionResultHolder<>(result, player.getItemInHand(hand));
    }
}
