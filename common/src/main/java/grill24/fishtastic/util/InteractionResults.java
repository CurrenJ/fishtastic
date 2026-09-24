package grill24.fishtastic.util;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Before 1.21.2, {@code Block#useItemOn} returns {@link ItemInteractionResult} and {@code Item#use}
 * returns {@link InteractionResultHolder}, not {@link InteractionResult}. The bodies keep 26.1.2's
 * {@code InteractionResult} logic and convert at the override.
 *
 * <p>26.1.2's {@code SUCCESS_SERVER} has no 1.21.1 constant. {@code isClientSide() ? SUCCESS :
 * SUCCESS_SERVER} becomes vanilla's {@code InteractionResult.sidedSuccess(isClientSide())}, and a
 * {@code SUCCESS_SERVER} returned from a server-only branch (the client already returned
 * {@code SUCCESS} and swung) becomes {@code CONSUME}, the server half of {@code sidedSuccess}.
 */
public final class InteractionResults {
    private InteractionResults() {}

    /**
     * Converts a {@code useItemOn} result with 26.1.2's meaning. In 26.1.2 a plain {@code PASS}
     * does <em>not</em> fall back to {@code useWithoutItem} (only {@code TRY_WITH_EMPTY_HAND} does,
     * and the tree never returns it), so {@code PASS} becomes {@code SKIP_DEFAULT_BLOCK_INTERACTION},
     * which goes straight on to the item's own use, as 26.1.2 does.
     */
    public static ItemInteractionResult forUseItemOn(InteractionResult result) {
        return switch (result) {
            case SUCCESS, SUCCESS_NO_ITEM_USED -> ItemInteractionResult.SUCCESS;
            case CONSUME -> ItemInteractionResult.CONSUME;
            case CONSUME_PARTIAL -> ItemInteractionResult.CONSUME_PARTIAL;
            case FAIL -> ItemInteractionResult.FAIL;
            case PASS -> ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        };
    }

    /** An {@code Item#use} result that leaves the held stack as it is. */
    public static InteractionResultHolder<ItemStack> forItemUse(InteractionResult result, Player player, InteractionHand hand) {
        return new InteractionResultHolder<>(result, player.getItemInHand(hand));
    }
}
