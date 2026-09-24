package grill24.fishtastic.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import grill24.fishtastic.FishtasticItemTags;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// On vanilla/Fabric, the fishing line picks the rod's hand with is(Items.FISHING_ROD), which
// excludes modded rods, so the line was drawn from the wrong hand.
// NeoForge patches this to canPerformAction(), so it already works there (hence require = 0).
// 26.1.2 hooks the static getHoldingArm helper (1.21.9+); 1.21.1 makes the same choice inline in
// getPlayerHandPos.
@Mixin(FishingHookRenderer.class)
public class FishingHookRendererMixin {
    @WrapOperation(method = "getPlayerHandPos", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
    private boolean fishtastic$treatFishtasticRodsAsRods(ItemStack mainHand, Item fishingRod, Operation<Boolean> original) {
        return original.call(mainHand, fishingRod) || mainHand.is(FishtasticItemTags.FISHING_RODS);
    }
}
