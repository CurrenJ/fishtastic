package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import grill24.fishsim.core.Locomotion;
import grill24.fishtastic.data.FishAnimationConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Random;

/**
 * Draws a companion fish with its class's idle pose and no simulation behind it
 * (docs/fish-companions.md). Used where a companion is attached to something rather than
 * swimming free: the player render layer that draws a percher on its owner's body. (The look-down
 * hand is drawn by the world pass — see ClientCompanion.)
 *
 * <p>Everything that comes from the sim (see {@link CompanionRender}) — heading, bank, bell,
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
    private final Random random = new Random();

    /**
     * @param minLength the smallest length to draw at, blocks — the same floor the world pass uses,
     *                  so a fish is the same size on its owner's shoulder as when it hops off
     */
    public CompanionFishPose(ItemStack fish, Level level, float minLength) {
        FishVisuals.ResolvedFishRender render = FishVisuals.resolve(fish, level);
        this.stack = fish.copyWithCount(1);
        this.anim = render.animation();
        this.length = Math.max(FishVisuals.renderedLength(fish, render.renderCalibration()), minLength);
        this.locomotion = FishVisuals.locomotionOf(anim);
        this.seed = fish.hashCode();
    }

    /** Centre height above its supporting surface, including the species' measured foot pivot. */
    public float floorLift() { return FishAnimator.floorPoseLift(anim, length); }

    /** A planted fish's base across its art (FishAnimationConfig.Planted.baseX), else 0. */
    public float baseX() { return anim instanceof FishAnimationConfig.Planted p ? p.baseX() : 0f; }

    /** The length it is drawn at, blocks. */
    public float length() { return length; }

    /**
     * Poses and submits the fish at its true-to-scale length. The stack must already sit at the
     * fish's position in whatever space the caller is drawing in.
     *
     * @param extraYawDeg  a yaw added on top of the pose — a trick spin, or the facing the caller
     *                     wants the sprite to have in its space
     * @param tiltDeg      an extra lean about X (a reaction head-tilt)
     * @param retract      the anchored class's retract envelope in [0, 1]; 0 for everything else
     */
    public void submit(PoseStack poseStack, MultiBufferSource buffers, int light, float gameTimeTicks,
                       float extraYawDeg, float tiltDeg, float retract) {
        float scale = Math.max(length, 1e-3f);
        random.setSeed(seed);
        poseStack.pushPose();
        if (extraYawDeg != 0f) poseStack.mulPose(Axis.YP.rotationDegrees(extraYawDeg));
        if (tiltDeg != 0f) poseStack.mulPose(Axis.XP.rotationDegrees(tiltDeg));
        // The companion idle-clock rate, same as CompanionRender's: the tank's sway and pulse rates
        // are authored for a fish at arm's length, and read frantic on a companion this close to
        // the camera (owner feedback 2026-10-10).
        float t = gameTimeTicks * CompanionRender.IDLE_CLOCK_RATE;
        if (locomotion == Locomotion.ANCHORED) {
            // Planted: the sway is game time, the withdrawal is the caller's envelope, and the
            // art's base (not its centre line) sits on the perch.
            FishAnimator.applyAnchored(poseStack, anim, random, t, 0f, scale, false, retract, true);
        } else if (locomotion == Locomotion.DRIFT) {
            FishAnimator.applyDrifting(poseStack, anim, random, t, 0f, scale, false, 0f);
        } else {
            FishAnimator.apply(poseStack, anim, random, t, 0f, scale, false);
        }
        poseStack.scale(scale, scale, scale);
        // 1.21.1: no ItemStackRenderState / submit queue; draw through the item renderer directly.
        Minecraft mc = Minecraft.getInstance();
        mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                poseStack, buffers, mc.level, 0);
        poseStack.popPose();
    }
}
