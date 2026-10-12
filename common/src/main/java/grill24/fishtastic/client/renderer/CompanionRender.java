package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import grill24.fishsim.companion.CompanionAgent;
import grill24.fishsim.companion.PoseHint;
import grill24.fishsim.companion.Vec;
import grill24.fishsim.core.Locomotion;
import grill24.fishtastic.data.FishAnimationConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Draws a companion's fish from the sim's world-space pose (docs/fish-companions-sim-plan.md §3.5):
 * position, heading, pitch and bank from the body, the tail clock from its tail phase, the appear
 * fade as scale, and each class through its own {@link FishAnimator} entry point — the same per-class
 * pose dispatch the tank group renderer uses. Nothing here is box-relative: the fish's world pose
 * is the pose.
 *
 * <p>The sim's {@link PoseHint}s are mapped where the old companion already had an equivalent
 * pose: an anchored fish's duck (the retract envelope), the name-tag head tilt, and the nose-up of
 * begging for bait and mild interest in food. The rest are named in the debug overlay only, until
 * the owner decides how they should look.
 */
public final class CompanionRender {
    /**
     * Presentation clock rates for companions (owner feedback 2026-10-10: the tank's rates read as
     * frantic on a fish right next to the camera). The swim clock scales the sim's tail clock; the
     * idle clock scales game time for the classes that pose on it.
     */
    public static final float SWIM_CLOCK_RATE = 0.55f, IDLE_CLOCK_RATE = 0.6f;
    /**
     * Gain on an upright benthic's push-off swell for a companion (slugs, the spider crab, the
     * octopus); the flat pose is left as is. Set live with {@code /fishtastic companion swell}.
     */
    public static final float DEFAULT_UPRIGHT_SWELL = 2.5f;
    public static float uprightSwell = DEFAULT_UPRIGHT_SWELL;
    /** The sim's tail phase advances 2π·1.6 rad per second at speed factor 1; the animator wants ticks. */
    private static final float PHASE_PER_TICK = (float) (2 * Math.PI * 1.6 / 20);
    /** The glider's bank at full lock, degrees (CompanionTunables.glideBankMaxDeg), for the animator's fraction. */
    private static final float GLIDE_BANK_MAX = 28f;
    /** The sprite's nose in the animator's yaw convention, after the caller has turned to the heading. */
    private static final float NOSE_FORWARD = 90f;

    /** Hint envelopes: how far and how fast, per tick. */
    private static final float TILT_DEG = 25f, TILT_RATE = 2.5f;
    private static final float BEG_PITCH = -15f, MILD_PITCH = -5f, PITCH_RATE = 1.5f;
    private static final float RETRACT_RATE = 0.15f;

    private final ItemStack stack;
    private final FishAnimationConfig anim;
    private final Locomotion locomotion;
    private final float length;
    private final long seed;
    private final Random random = new Random();
    private final ItemStackRenderState[] states = new ItemStackRenderState[3];
    /** Each fish's unwrapped tail clock in ticks, and the tail phase it was last advanced from. */
    private final float[] tailClock = new float[3];
    private final float[] lastPhase = {Float.NaN, Float.NaN, Float.NaN};

    private float tilt, prevTilt, hintPitch, prevHintPitch, retract, prevRetract;

    public CompanionRender(ItemStack fish, Level level) {
        FishTankBlockEntityRenderer.ResolvedFishRender render = FishTankBlockEntityRenderer.resolveFishRender(fish, level);
        this.stack = fish.copyWithCount(1);
        this.anim = render.animation();
        this.length = TankFlockAdapter.renderedLength(fish, render.renderCalibration());
        this.locomotion = TankFlockAdapter.locomotionOf(anim);
        this.seed = TankFlockAdapter.speciesId(fish);
        for (int i = 0; i < states.length; i++) states[i] = new ItemStackRenderState();
    }

    /** The class this species moves as (from its animation config). */
    public Locomotion locomotion() { return locomotion; }

    /** True-to-scale rendered length, blocks. */
    public float length() { return length; }

    /** Client tick: eases the pose-hint envelopes toward what the lead fish's hint says now. */
    public void tick(CompanionAgent agent) {
        PoseHint hint = agent.poseHint();
        prevTilt = tilt;
        prevHintPitch = hintPitch;
        prevRetract = retract;
        tilt = approach(tilt, hint == PoseHint.TILT ? TILT_DEG : 0f, TILT_RATE);
        float pitch = hint == PoseHint.BEG ? BEG_PITCH : hint == PoseHint.MILD ? MILD_PITCH : 0f;
        hintPitch = approach(hintPitch, pitch, PITCH_RATE);
        retract = approach(retract, hint == PoseHint.DUCK ? 1f : 0f, RETRACT_RATE);
    }

    public float hintTilt(float partialTick) { return Mth.lerp(partialTick, prevTilt, tilt); }

    public float hintPitch(float partialTick) { return Mth.lerp(partialTick, prevHintPitch, hintPitch); }

    public float retract(float partialTick) { return Mth.lerp(partialTick, prevRetract, retract); }

    /**
     * Poses and submits fish {@code index} (0 the lead, 1–2 trio followers) at a world pose. The
     * stack is camera-relative. Returns false when it is faded out entirely.
     *
     * @param yawDeg   Minecraft yaw of the heading
     * @param pitchDeg Minecraft pitch of the heading (positive is nose down)
     * @param riding   a percher on its owner (the hand): poses as perched, not swimming
     * @param onFloor  {@code pos} is the floor under a benthic walking on the ground: the pose is
     *                 lifted by its own floor offset (docs/fish-companions-benthic-follow.md §21)
     * @param ripple   a flatfish gliding on the ground: its ripple runs on the gait's phase
     *                 ({@code tailPhase}), quickening as it glides faster and slowing when it stops
     */
    public boolean submit(PoseStack poseStack, SubmitNodeCollector nodes, Vec3 cam, int index, Vec pos,
                          float yawDeg, float pitchDeg, float bankDeg, float appear, float tailPhase,
                          float speedFactor, float pulse, float squash, boolean riding,
                          float gameTime, int light, boolean onFloor, boolean ripple) {
        float clock = advanceTail(index, tailPhase);
        if (appear <= 0f) return false;
        float scale = Math.max(length, 1e-3f) * appear;
        float t = gameTime * IDLE_CLOCK_RATE;
        float partial = gameTime - (float) Math.floor(gameTime); // the frame's partial tick
        double lift = onFloor ? FishAnimator.floorPoseLift(anim, scale) : 0;

        poseStack.pushPose();
        poseStack.translate(pos.x() - cam.x, pos.y() + lift - cam.y, pos.z() - cam.z);
        // Local +z is the heading: turn to the yaw, then pitch the nose.
        poseStack.mulPose(Axis.YP.rotationDegrees(-yawDeg));
        // A drifter's pitch is its bell axis (upright is −90); the pose is drawn upright, so tilt by the difference.
        float tiltDeg = locomotion == Locomotion.DRIFT ? pitchDeg + 90f : pitchDeg;
        poseStack.mulPose(Axis.XP.rotationDegrees(tiltDeg + hintPitch(partial)));
        float roll = hintTilt(partial);
        if (roll != 0f) poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
        random.setSeed(seed * 31 + index);
        if (!riding && anim instanceof FishAnimationConfig.HorizontalSwim swim) {
            FishAnimator.applySwimming(poseStack, swim, random, clock * SWIM_CLOCK_RATE, NOSE_FORWARD, false,
                    speedFactor, bankDeg, Float.POSITIVE_INFINITY, scale);
        } else if (locomotion == Locomotion.GLIDE) {
            FishAnimator.applyGliding(poseStack, anim, random, t, NOSE_FORWARD,
                    Mth.clamp(bankDeg / GLIDE_BANK_MAX, -1f, 1f), NOSE_FORWARD, scale, false);
        } else if (locomotion == Locomotion.DRIFT) {
            FishAnimator.applyDrifting(poseStack, anim, random, t, NOSE_FORWARD, scale, false, pulse);
        } else if (locomotion == Locomotion.BENTHIC) {
            // Standing up, the push-off is seen from the side at 5% and reads as nothing; the flat
            // pose's in-plane swell (a starfish's) already reads, so only the upright one is raised.
            float swell = anim instanceof FishAnimationConfig.UprightSit ? squash * uprightSwell : squash;
            FishAnimator.applyBenthic(poseStack, anim, random, t, NOSE_FORWARD, NOSE_FORWARD, scale, false, swell,
                    ripple ? tailPhase : Float.NaN);
        } else if (locomotion == Locomotion.ANCHORED) {
            FishAnimator.applyAnchored(poseStack, anim, random, t, NOSE_FORWARD, scale, false, retract(partial));
        } else {
            FishAnimator.apply(poseStack, anim, random, t, NOSE_FORWARD, scale, false);
        }
        poseStack.scale(scale, scale, scale);
        ItemStackRenderState state = states[index];
        Minecraft.getInstance().getItemModelResolver()
                .updateForTopItem(state, stack, ItemDisplayContext.FIXED, null, null, 0);
        state.submit(poseStack, nodes, light, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
        return true;
    }

    /**
     * The tail clock in ticks: the sim's wrapped tail phase, unwrapped frame by frame. It only ever
     * runs forward, so a frame-rate wobble can't make the tail beat jump.
     */
    private float advanceTail(int index, float phase) {
        float last = lastPhase[index];
        lastPhase[index] = phase;
        if (Float.isNaN(last)) return tailClock[index];
        float d = phase - last;
        if (d < -Math.PI) d += (float) (2 * Math.PI);
        if (d > 0f && d < Math.PI) tailClock[index] += d / PHASE_PER_TICK;
        return tailClock[index];
    }

    private static float approach(float value, float target, float speed) {
        if (value < target) return Math.min(target, value + speed);
        return Math.max(target, value - speed);
    }
}
