package grill24.fishtastic.mixin;

import grill24.fishtastic.fishtank.CosmeticPlacement;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets a sneaking player force-place a tank cosmetic — see
 * {@link CosmeticPlacement#tryForcePlace}. Vanilla skips {@code Block#useItemOn} entirely while
 * sneaking with anything in hand and calls {@code ItemStack#useOn} with the player's real
 * {@link net.minecraft.world.phys.BlockHitResult} instead, so this is the only hook that sees the
 * click for <i>every</i> cosmetic item, including the plain vanilla
 * {@code BlockItem}s tagged {@code #fishtastic:tank_cosmetics}, which can't override anything
 * themselves. Idle for every other item, hand and target: {@code tryForcePlace} answers
 * {@code null} and the vanilla call runs untouched.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackUseOnMixin {

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void fishtastic$forcePlaceCosmetic(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        InteractionResult result = CosmeticPlacement.tryForcePlace(context);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}
