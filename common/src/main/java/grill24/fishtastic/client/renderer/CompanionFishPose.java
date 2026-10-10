package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import grill24.fishsim.core.Locomotion;
import grill24.fishtastic.data.FishAnimationConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Random;

/**
 * Draws a companion fish with its class's idle pose and no simulation behind it
 * (docs/fish-companions.md §3). Used where a companion is attached to something rather than
 * swimming free: the player render layer that draws a percher on its owner's body. (The look-down
 * hand hop is drawn by the world pass along with the rest of the follow box — see ClientCompanion.)
 *
 * <p>Everything that comes from a running engine in {@link CompanionFlock} — heading, bank, bell,
 * retract — is passed in by the caller instead. A perched fish is not going anywhere: its pose is
 * its animation config's idle plus whatever the caller says (a trick spin, a reaction tilt, the
 * anchored class's duck).
 */
public final class CompanionFishPose {
    private final ItemStack stack;
    private final FishAnimationConfig anim;
    private final Locomotion locomotion;
    private final float length;
    private final long seed;
    private final ItemStackRenderState renderState = new ItemStackRenderState();
    private final Random random = new Random();

    public CompanionFishPose(ItemStack fish, Level level) {
        FishTankBlockEntityRenderer.ResolvedFishRender render = FishTankBlockEntityRenderer.resolveFishRender(fish, level);
        this.stack = fish.copyWithCount(1);
        this.anim = render.animation();
        this.length = TankFlockAdapter.renderedLength(fish, render.renderCalibration());
        this.locomotion = TankFlockAdapter.locomotionOf(anim);
        this.seed = fish.hashCode();
    }

    /**
     * Whether this species' idle pose lies flat (a floor sit) — the perch layer stands those up on
     * a shoulder so the sprite reads from the front, like a fish pinned to the outside of the arm.
     */
    public boolean flatPose() { return anim instanceof FishAnimationConfig.FloorSit; }

    /**
     * Poses and submits the fish at its true-to-scale length. The stack must already sit at the
     * fish's position in whatever space the caller is drawing in.
     *
     * @param extraYawDeg  a yaw added on top of the pose — a trick spin, or the facing the caller
     *                     wants the sprite to have in its space
     * @param tiltDeg      an extra lean about X (a reaction head-tilt)
     * @param retract      the anchored class's retract envelope in [0, 1]; 0 for everything else
     */
    public void submit(PoseStack poseStack, SubmitNodeCollector nodes, int light, float gameTimeTicks,
                       float extraYawDeg, float tiltDeg, float retract) {
        float scale = Math.max(length, 1e-3f);
        random.setSeed(seed);
        poseStack.pushPose();
        if (extraYawDeg != 0f) poseStack.mulPose(Axis.YP.rotationDegrees(extraYawDeg));
        if (tiltDeg != 0f) poseStack.mulPose(Axis.XP.rotationDegrees(tiltDeg));
        // The companion idle-clock rate, same as CompanionFlock's: the tank's sway and pulse rates
        // are authored for a fish at arm's length, and read frantic on a companion this close to
        // the camera (owner feedback 2026-10-10).
        float t = gameTimeTicks * CompanionFlock.IDLE_CLOCK_RATE;
        if (locomotion == Locomotion.ANCHORED) {
            // Planted: the sway is game time, the withdrawal is the caller's envelope.
            FishAnimator.applyAnchored(poseStack, anim, random, t, 0f, scale, false, retract);
        } else if (locomotion == Locomotion.DRIFT) {
            FishAnimator.applyDrifting(poseStack, anim, random, t, 0f, scale, false, 0f);
        } else {
            FishAnimator.apply(poseStack, anim, random, t, 0f, scale, false);
        }
        poseStack.scale(scale, scale, scale);
        Minecraft.getInstance().getItemModelResolver()
                .updateForTopItem(renderState, stack, ItemDisplayContext.FIXED, null, null, 0);
        renderState.submit(poseStack, nodes, light, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}
