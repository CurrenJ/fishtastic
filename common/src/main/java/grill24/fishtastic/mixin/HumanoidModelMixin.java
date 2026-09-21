package grill24.fishtastic.mixin;

import grill24.fishtastic.client.util.FishermanPoseDebug;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives a decorative {@code Mannequin} holding a caught fish a "gripping it by the tail, hanging
 * down" pose instead of vanilla's default held-item arm animation.
 * <p>
 * Scoped to {@code EntityType.MANNEQUIN} by default: a Mannequin has no AI and no walk/attack/swim
 * animation competing for the arm every frame (unlike a real player or mob), so overwriting its
 * joint rotation outright never fights a live animation. This also means the leaderboard's
 * {@code ClientMannequin} podium puppet (gelatin-ui's {@code PlayerAvatarRenderer}) picks up this
 * pose automatically — its entity type is always {@code MANNEQUIN} — with no cross-module marker
 * needed, and any real player-placed decorative mannequin holding a sized fish gets the same
 * trophy pose for free.
 * <p>
 * {@link FishermanPoseDebug#enabledInWorld} additionally forces the same pose onto real
 * {@code EntityType.PLAYER}s — a debug-only preview of a possible future player emote. Unlike the
 * Mannequin case, a real player's arm animation genuinely competes with this override every
 * frame (walking, attacking, swimming); the override always wins since it's injected last, so
 * turning the toggle on intentionally makes the holding arm go rigid regardless of what the
 * player is doing.
 * <p>
 * Injected at the {@code TAIL} of {@code HumanoidModel.setupAnim}: {@code PlayerModel.setupAnim}
 * calls {@code super.setupAnim(state)} as its last statement and never touches the arms
 * afterward, so this is the last code to run before the pose is submitted — it simply overwrites
 * whatever vanilla's {@code ArmPose} switch computed.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends HumanoidRenderState> {
    @Shadow
    public ModelPart rightArm;
    @Shadow
    public ModelPart leftArm;

    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void fishtastic$applyFishermanHangPose(T state, CallbackInfo ci) {
        if (!(state instanceof ArmedEntityRenderState armed) || !FishermanPoseDebug.shouldPose(armed.entityType)) {
            return;
        }
        ItemStack heldStack = armed.getMainHandItemStack();
        if (!ItemSizeHelper.hasSize(heldStack)) {
            return;
        }

        boolean rightHanded = armed.mainArm == HumanoidArm.RIGHT;
        ModelPart holdingArm = rightHanded ? this.rightArm : this.leftArm;
        // Mirror the out-from-hip rotation for the left arm.
        float mirror = rightHanded ? 1.0f : -1.0f;
        holdingArm.xRot = FishermanPoseDebug.armXRot;
        holdingArm.yRot = FishermanPoseDebug.armYRot;
        holdingArm.zRot = FishermanPoseDebug.armZRot * mirror;
    }
}
