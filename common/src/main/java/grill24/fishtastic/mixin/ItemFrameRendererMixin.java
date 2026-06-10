package grill24.fishtastic.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishtastic.client.renderer.FishtasticWorldOutlineRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks item frame contents into the in-world outline path.  Same two-phase pattern as
 * {@link ItemEntityRendererMixin}: capture at extraction (stack in scope), submit the
 * outline quad right before {@code state.item.submit(...)} — at that point the pose stack
 * carries the frame orientation, item rotation, and the 0.5 item scale, so the quad is
 * co-planar with the framed item.
 *
 * <p>The injection point only exists on the non-map item branch, so framed maps are
 * naturally excluded.
 */
@Mixin(ItemFrameRenderer.class)
public abstract class ItemFrameRendererMixin {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/decoration/ItemFrame;Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;F)V",
            at = @At("TAIL"))
    private void fishtastic$captureWorldOutline(ItemFrame entity, ItemFrameRenderState state, float partialTicks, CallbackInfo ci) {
        FishtasticWorldOutlineRenderer.capture(state.item, entity.getItem());
    }

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void fishtastic$submitWorldOutline(
            ItemFrameRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera,
            CallbackInfo ci) {
        // mirrorU: the FIXED display transform flips the item 180° about Y inside
        // state.item.submit(), after this injection point — see submitOutline javadoc.
        FishtasticWorldOutlineRenderer.submitOutline(poseStack, submitNodeCollector, state.item, true);
    }
}
