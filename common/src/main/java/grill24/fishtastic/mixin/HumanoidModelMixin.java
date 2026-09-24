package grill24.fishtastic.mixin;

import grill24.fishtastic.client.util.FishermanPoseDebug;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives a leaderboard podium puppet holding a caught fish a "gripping it by the tail, hanging
 * down" pose instead of vanilla's default held-item arm animation.
 * <p>
 * On 26.1.2 this is scoped to {@code EntityType.MANNEQUIN}, which the podium puppet (gelatin-ui's
 * {@code PlayerAvatarRenderer}) always is. 1.21.1 has no mannequins, and gelatin's 1.21.1 puppet
 * is a {@code RemotePlayer}, so {@link FishermanPoseDebug#shouldPose(LivingEntity)} recognises the
 * puppet itself. A puppet has no AI and no walk/attack/swim animation competing for the arm every
 * frame, so overwriting its joint rotation outright never fights a live animation.
 * <p>
 * {@link FishermanPoseDebug#enabledInWorld} additionally forces the same pose onto real
 * players — a debug-only preview of a possible future player emote. A real player's arm
 * animation genuinely competes with this override every frame; the override always wins since
 * it's injected last, so turning the toggle on intentionally makes the holding arm go rigid.
 * <p>
 * Injected at the {@code TAIL} of {@code HumanoidModel.setupAnim}. On 1.21.1 {@code PlayerModel}
 * copies the arms onto its sleeve layers after calling {@code super.setupAnim}, so the sleeves
 * follow this pose. (26.1.2 reads the same inputs off the render state that
 * {@code ItemInHandLayerMixin} fills in; 1.21.1 hands the entity itself to {@code setupAnim}.)
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends LivingEntity> {
    @Shadow
    public ModelPart rightArm;
    @Shadow
    public ModelPart leftArm;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void fishtastic$applyFishermanHangPose(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                                   float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!FishermanPoseDebug.shouldPose(entity)) {
            return;
        }
        ItemStack heldStack = entity.getMainHandItem();
        if (!ItemSizeHelper.hasSize(heldStack)) {
            return;
        }

        boolean rightHanded = entity.getMainArm() == HumanoidArm.RIGHT;
        ModelPart holdingArm = rightHanded ? this.rightArm : this.leftArm;
        // Mirror the out-from-hip rotation for the left arm.
        float mirror = rightHanded ? 1.0f : -1.0f;
        holdingArm.xRot = FishermanPoseDebug.armXRot;
        holdingArm.yRot = FishermanPoseDebug.armYRot;
        holdingArm.zRot = FishermanPoseDebug.armZRot * mirror;
    }
}
