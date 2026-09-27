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
// Forge/NeoForge patch this to canPerformAction(), which Fishtastic rods inherit from
// FishingRodItem, so it already works there (hence require = 0).
// 26.1.2 hooks the static getHoldingArm helper (1.21.9+); 1.21.1 makes the same choice inline in
// getPlayerHandPos.
// PORT-ONLY (1.20.1): there is no getPlayerHandPos here — 1.20.1 makes the choice inline in
// FishingHookRenderer.render (getMainHandItem(), then the is(Items.FISHING_ROD) this wraps), and
// naming getPlayerHandPos made the wrap a silent no-op (require = 0) that drew the line off the
// empty hand: the wrong side of the screen in first person.
// `method` is the bare name on purpose — no descriptor. With one, the legacy Mixin AP fails to remap
// this particular method (an override of the generic EntityRenderer<T>.render) and writes the refmap
// value with the mojmap name left in place — Fabric "class_906;render(FishingHook...)V", Forge the
// same name — even though the mappings carry it (FishingHookRenderer.render = method_3974 / m_7392_).
// Mixin resolves the target by that literal name against an intermediary/SRG runtime, finds nothing,
// and require = 0 turns it into the same silent no-op this fix exists to remove. The bare-name form
// goes through the mappings' name lookup and takes the descriptor from them (FishingHookMixin's own
// "tick" resolves that way, and it is an override too).
// Forge 1.20.1 patches this site to canPerformAction(ToolActions.FISHING_ROD_CAST), so require = 0
// keeps the wrap a no-op there, as intended.
@Mixin(FishingHookRenderer.class)
public class FishingHookRendererMixin {
    @WrapOperation(method = "render", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
    private boolean fishtastic$treatFishtasticRodsAsRods(ItemStack mainHand, Item fishingRod, Operation<Boolean> original) {
        return original.call(mainHand, fishingRod) || mainHand.is(FishtasticItemTags.FISHING_RODS);
    }
}
