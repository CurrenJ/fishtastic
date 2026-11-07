package grill24.fishtastic.mixin;

import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.item.PileOfFishItem;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets a furnace or smoker cook a Pile of Fish one fish at a time. The
 * {@code cooked_cod_from_pile_of_fish_*} recipes match the pile item; vanilla would then consume
 * the whole pile for a single cooked cod. Instead each finished cycle takes one fish out of the
 * pile, so cook time and XP per fish match the loose-fish recipes, and a pile that drops to one
 * entry unpacks into that stack. A pile holding no {@code fishtastic:fish} (only trash or vanilla
 * catches) never starts cooking.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public class FurnacePileOfFishMixin {

    @Inject(method = "canBurn", at = @At("HEAD"), cancellable = true)
    private static void fishtastic$requireCookableFish(
            NonNullList<ItemStack> items, int maxStackSize, ItemStack burnResult, CallbackInfoReturnable<Boolean> cir) {
        ItemStack input = items.get(0);
        if (input.is(FishtasticItems.PILE_OF_FISH.value()) && !PileOfFishItem.hasCookableFish(input)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "burn", at = @At("HEAD"), cancellable = true)
    private static void fishtastic$cookOneFishFromPile(
            NonNullList<ItemStack> items, ItemStack inputItemStack, ItemStack result, CallbackInfo ci) {
        if (!inputItemStack.is(FishtasticItems.PILE_OF_FISH.value())) {
            return;
        }
        ItemStack resultItemStack = items.get(2);
        if (resultItemStack.isEmpty()) {
            items.set(2, result.copy());
        } else {
            resultItemStack.grow(result.getCount());
        }
        items.set(0, PileOfFishItem.removeOneCookableFish(inputItemStack));
        ci.cancel();
    }
}
