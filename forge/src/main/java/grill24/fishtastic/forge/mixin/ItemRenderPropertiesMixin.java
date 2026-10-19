package grill24.fishtastic.forge.mixin;

import grill24.fishtastic.client.renderer.FishtasticItemRenderers;
import grill24.fishtastic.forge.FishtasticForgeItemRenderer;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forge initializes item render extensions from the item's own constructor. Fishtastic's items
 * are loader-neutral, so supply the Forge custom-renderer adapter at the read site instead.
 * This mixin is listed in the {@code client} section of {@code fishtastic-forge.mixins.json};
 * it never loads on a dedicated server.
 */
@Mixin(Item.class)
public abstract class ItemRenderPropertiesMixin {
    @Inject(method = "getRenderPropertiesInternal", at = @At("RETURN"), cancellable = true, remap = false)
    private void fishtastic$customRenderer(CallbackInfoReturnable<Object> cir) {
        if (cir.getReturnValue() != null) {
            return;
        }
        Item self = (Item) (Object) this;
        if (FishtasticItemRenderers.isCustomRendererItem(self)) {
            cir.setReturnValue(FishtasticForgeItemRenderer.EXTENSIONS);
        }
    }
}
