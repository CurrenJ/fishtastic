package grill24.fishtastic.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import grill24.fishtastic.client.renderer.FishTankBlockEntityRenderer;
import grill24.fishtastic.client.util.FishermanPoseDebug;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Any held item carrying a recorded {@code ItemSize} (i.e. any caught fish) renders at its true
 * size — the same {@code size/100 * per-species render_calibration} formula the fish tank uses to
 * draw fish true-to-scale (see {@link FishTankBlockEntityRenderer#getHeldItemRenderScale}) —
 * instead of vanilla's fixed item-model scale. Applies to every entity that draws held items
 * through {@link ItemInHandLayer} (players and mobs alike), since {@code renderArmWithItem} is the
 * one place both converge before the item is drawn.
 * <p>
 * Brackets the existing {@code ItemInHandRenderer.renderItem(...)} call with a push/scale/pop
 * rather than replacing it, so vanilla's own arm-relative positioning (already applied to
 * {@code poseStack} earlier in the method) is preserved — the fish just grows or shrinks around
 * that same anchor point.
 * <p>
 * For the leaderboard's podium puppet (and real players when {@code HumanoidModelMixin}'s
 * {@link FishermanPoseDebug#enabledInWorld} debug toggle is on — see its doc), the same bracket
 * also rolls the item so it hangs head-down —
 * {@link FishTankBlockEntityRenderer#getHeldItemHangingRollDegrees} — and then translates it by
 * {@link FishermanPoseDebug#offsetX}/{@link FishermanPoseDebug#offsetY}/
 * {@link FishermanPoseDebug#offsetZ} so the grip point (tail) sits at the hand instead of the
 * sprite's centre. The translate happens last, inside the same
 * scale+rotate bracket, so it's expressed in the sprite's own pre-scale local units and
 * automatically grows/shrinks with the fish's size and rotates with the roll — no per-size or
 * per-orientation retuning needed. Real players/mobs keep vanilla's normal item orientation and
 * position; only the scale applies universally.
 * <p>
 * 26.1.2 wraps {@code ItemStackRenderState.submit} inside {@code submitArmWithItem}; 1.21.1 draws
 * the item immediately with {@code ItemInHandRenderer.renderItem} inside {@code renderArmWithItem}.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    private static final String RENDER_ITEM_TARGET =
            "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    // Fish items use plain vanilla `minecraft:item/generated` with no custom display overrides,
    // whose "fixed" transform (used by the tank renderer) scales 1.0 but whose
    // "thirdperson_righthand"/"thirdperson_lefthand" transform scales 0.55 — a held item is
    // already shrunk to 55% before our multiplier runs. Compensate so a held fish ends up the
    // same absolute size as the same fish drawn in a tank, not 55% of it.
    private static final float THIRD_PERSON_HAND_TRANSFORM_SCALE = 0.55f;
    private static final float THIRD_PERSON_HAND_COMPENSATION = 1.0f / THIRD_PERSON_HAND_TRANSFORM_SCALE;

    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target = RENDER_ITEM_TARGET))
    private void fishtastic$pushSizeScale(
            LivingEntity entity, ItemStack itemStack, ItemDisplayContext displayContext, HumanoidArm arm,
            PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        float scale = fishtastic$scaleFor(itemStack);
        if (scale != 1.0f) {
            poseStack.pushPose();
            poseStack.scale(scale, scale, scale);

            if (FishermanPoseDebug.shouldPose(entity)) {
                Level level = Minecraft.getInstance().level;
                if (level != null) {
                    float roll = FishTankBlockEntityRenderer.getHeldItemHangingRollDegrees(itemStack, level);
                    poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
                    // Last, so it's applied to raw sprite-local coordinates before scale/rotate —
                    // see the class doc for why that's what makes it size/orientation-proportional.
                    poseStack.translate(FishermanPoseDebug.offsetX, FishermanPoseDebug.offsetY, FishermanPoseDebug.offsetZ);
                }
            }
        }
    }

    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target = RENDER_ITEM_TARGET, shift = At.Shift.AFTER))
    private void fishtastic$popSizeScale(
            LivingEntity entity, ItemStack itemStack, ItemDisplayContext displayContext, HumanoidArm arm,
            PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (fishtastic$scaleFor(itemStack) != 1.0f) {
            poseStack.popPose();
        }
    }

    private static float fishtastic$scaleFor(ItemStack itemStack) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return 1.0f;
        }
        float scale = FishTankBlockEntityRenderer.getHeldItemRenderScale(itemStack, level);
        return scale == 1.0f ? 1.0f : scale * THIRD_PERSON_HAND_COMPENSATION;
    }
}
