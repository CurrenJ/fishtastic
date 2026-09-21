package grill24.fishtastic.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishtastic.client.renderer.FishTankBlockEntityRenderer;
import grill24.fishtastic.client.renderer.FishtasticGlintState;
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
 * Hooks dropped item entities into the in-world outline path, and makes a dropped caught fish
 * render at its recorded size — the same {@code size/100 * per-species render_calibration}
 * formula the fish tank uses (see {@link FishTankBlockEntityRenderer#getHeldItemRenderScale}) —
 * instead of vanilla's fixed item-model scale.
 *
 * <p>Three injections, mirroring the resolve/draw split documented in
 * {@code docs/item-effect-rendering.md}:
 * <ul>
 *   <li><b>extractRenderState TAIL</b> — the only point where the {@link ItemEntity}'s
 *       {@code ItemStack} is in scope; captures the outline effect + atlas slot, and the
 *       size-based render scale, against the render state's identity.</li>
 *   <li><b>submit, before {@code submitMultipleFromCount}</b> — the pose stack has the
 *       bob + spin transforms applied at exactly this call, so both the outline quad and the
 *       size scaling stay co-planar/centred with the spinning item model. The scale is pushed
 *       here and popped immediately after, bracketing the whole item-cluster submission (so a
 *       stack of several dropped fish all scale together).</li>
 * </ul>
 */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
    private static final String SUBMIT_MULTIPLE_TARGET =
            "Lnet/minecraft/client/renderer/entity/ItemEntityRenderer;submitMultipleFromCount(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ItemClusterRenderState;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/phys/AABB;)V";

    // Dropped items resolve against ItemDisplayContext.GROUND, whose baked-in scale for vanilla's
    // plain `minecraft:item/generated` model (verified against generated.json in the 26.1.2
    // client jar) is 0.5 vs. "fixed" (the tank's context) at 1.0 — compensate the same way
    // ItemInHandLayerMixin does for the third-person-hand context, so a dropped fish ends up the
    // same absolute size as the same fish drawn in a tank, not 50% of it.
    private static final float GROUND_TRANSFORM_SCALE = 0.5f;
    private static final float GROUND_COMPENSATION = 1.0f / GROUND_TRANSFORM_SCALE;

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",
            at = @At("TAIL"))
    private void fishtastic$captureWorldOutlineAndScale(ItemEntity entity, ItemEntityRenderState state, float partialTicks, CallbackInfo ci) {
        FishtasticWorldOutlineRenderer.capture(state.item, entity.getItem());

        float scale = FishTankBlockEntityRenderer.getHeldItemRenderScale(entity.getItem(), entity.level());
        if (scale != 1.0f) {
            FishtasticGlintState.WORLD_ITEM_SCALE_MAP.put(state.item, scale * GROUND_COMPENSATION);
        } else {
            FishtasticGlintState.WORLD_ITEM_SCALE_MAP.remove(state.item);
        }
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = SUBMIT_MULTIPLE_TARGET))
    private void fishtastic$submitWorldOutlineAndPushScale(
            ItemEntityRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera,
            CallbackInfo ci) {
        Float scale = FishtasticGlintState.WORLD_ITEM_SCALE_MAP.get(state.item);
        if (scale != null) {
            poseStack.pushPose();
            poseStack.scale(scale, scale, scale);
        }

        // Submitted after the scale push so the outline quad shares the same transform as the
        // (now correctly scaled) item model it's supposed to trace.
        FishtasticWorldOutlineRenderer.submitOutline(poseStack, submitNodeCollector, state.item, false);
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = SUBMIT_MULTIPLE_TARGET, shift = At.Shift.AFTER))
    private void fishtastic$popScale(
            ItemEntityRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera,
            CallbackInfo ci) {
        if (FishtasticGlintState.WORLD_ITEM_SCALE_MAP.containsKey(state.item)) {
            poseStack.popPose();
        }
    }
}
