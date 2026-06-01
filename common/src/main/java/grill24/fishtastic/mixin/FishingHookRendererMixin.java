package grill24.fishtastic.mixin;

import grill24.fishtastic.FishtasticItems;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// On vanilla/Fabric, getHoldingArm checks is(Items.FISHING_ROD) which excludes modded rods,
// causing the cast model condition to check the wrong arm and always return false.
// NeoForge patches this to canPerformAction(), so it already works there.
@Environment(EnvType.CLIENT)
@Mixin(FishingHookRenderer.class)
public class FishingHookRendererMixin {
    @Inject(method = "getHoldingArm", at = @At("HEAD"), cancellable = true)
    private static void fishtastic$getHoldingArmForCopperRod(Player owner, CallbackInfoReturnable<HumanoidArm> cir) {
        ItemStack mainHand = owner.getMainHandItem();
        if (mainHand.is(FishtasticItems.COPPER_FISHING_ROD)) {
            cir.setReturnValue(owner.getMainArm());
        } else if (owner.getOffhandItem().is(FishtasticItems.COPPER_FISHING_ROD)) {
            cir.setReturnValue(owner.getMainArm().getOpposite());
        }
    }
}
