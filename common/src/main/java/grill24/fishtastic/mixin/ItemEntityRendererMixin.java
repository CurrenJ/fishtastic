package grill24.fishtastic.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishtastic.client.renderer.FishtasticWorldOutlineRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks dropped item entities into the in-world outline path.
 *
 * <p>Two injections, mirroring the resolve/draw split documented in
 * {@code docs/item-effect-rendering.md}:
 * <ul>
 *   <li><b>extractRenderState TAIL</b> — the only point where the {@link ItemEntity}'s
 *       {@code ItemStack} is in scope; captures the effect + atlas slot against the
 *       render state's identity.</li>
 *   <li><b>submit, before {@code submitMultipleFromCount}</b> — the pose stack has the
 *       bob + spin transforms applied at exactly this call, so the outline quad submitted
 *       here stays co-planar with the spinning item model.</li>
 * </ul>
 */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",
            at = @At("TAIL"))
    private void fishtastic$captureWorldOutline(ItemEntity entity, ItemEntityRenderState state, float partialTicks, CallbackInfo ci) {
        FishtasticWorldOutlineRenderer.capture(state.item, entity.getItem());
    }

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/ItemEntityRenderer;submitMultipleFromCount(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ItemClusterRenderState;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/phys/AABB;)V"))
    private void fishtastic$submitWorldOutline(
            ItemEntityRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera,
            CallbackInfo ci) {
        FishtasticWorldOutlineRenderer.submitOutline(poseStack, submitNodeCollector, state.item, false);
    }
}
