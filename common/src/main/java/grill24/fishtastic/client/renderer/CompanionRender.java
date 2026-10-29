package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import grill24.fishsim.companion.CompanionAgent;
import grill24.fishsim.companion.PoseHint;
import grill24.fishsim.companion.Temperament;
import grill24.fishsim.companion.Vec;
import grill24.fishsim.core.Locomotion;
import grill24.fishtastic.data.FishAnimationConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Draws a companion's fish from the sim's world-space pose (docs/fish-companions.md):
 * position, heading, pitch and bank from the body, the tail clock from its tail phase, the appear
 * fade as scale, and each class through its own {@link FishAnimator} entry point — the same per-class
 * pose dispatch the tank group renderer uses. Nothing here is box-relative: the fish's world pose
 * is the pose.
 *
 * <p>The sim's {@link PoseHint}s are mapped where the old companion already had an equivalent
 * pose: an anchored fish's duck (the retract envelope), the name-tag head tilt, and the nose-up of
 * begging for bait and mild interest in food; and the Phase 5 moods' shiver (a quick tremble of
 * roll and yaw), moon gaze (nose up), drowsy droop, water dips and musical sways.
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
    /**
     * Gain on a drifter's squeeze for a companion (docs/fish-companions-drift.md): the tank's
     * {@code pulse_stretch} alone (10%) over an idle stroke's second-long squeeze reads as nothing,
     * though the push it gives plainly shows. The squeeze is already scaled by the stroke's strength.
     * Set live with {@code /fishtastic companion squeeze}.
     */
    public static final float DEFAULT_DRIFT_SQUEEZE = 3f;
    public static float driftSqueeze = DEFAULT_DRIFT_SQUEEZE;
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
    /** The moon gaze's nose-up, degrees (negative is up, as begging's). */
    private static final float MOON_PITCH = -28f;
    /** The cold shiver: roll and yaw amplitudes, degrees; its rate, radians per tick (~7 Hz); and its ease, per tick. */
    private static final float SHIVER_ROLL_DEG = 5f, SHIVER_YAW_DEG = 2.5f, SHIVER_RATE = 2.2f, SHIVER_EASE = 0.1f;

    private final ItemStack stack;
    private final FishAnimationConfig anim;
    private final Locomotion locomotion;
    private final float length;
    private final long seed;
    private final Random random = new Random();
    /** Each fish's unwrapped tail clock in ticks, and the tail phase it was last advanced from. */
    private final float[] tailClock = new float[3];
    private final float[] lastPhase = {Float.NaN, Float.NaN, Float.NaN};
    private final boolean[] wet = new boolean[3];
    private final boolean[] mediumSampled = new boolean[3];
    private final float[] mediumFlipSince = {Float.NaN, Float.NaN, Float.NaN};
    private static final float MEDIUM_HOLD_TICKS = 4f;

    private float tilt, prevTilt, hintPitch, prevHintPitch, retract, prevRetract, shiver, prevShiver;
    private float reactionClock;
    private Temperament temperament = Temperament.BASELINE;
    private Vec watchDirection;
    private boolean watching;

    public CompanionRender(ItemStack fish, Level level) {
        FishVisuals.ResolvedFishRender render = FishVisuals.resolve(fish, level);
        this.stack = fish.copyWithCount(1);
        this.anim = render.animation();
        this.length = FishVisuals.renderedLength(fish, render.renderCalibration());
        this.locomotion = FishVisuals.locomotionOf(anim);
        this.seed = FishVisuals.speciesId(fish);
    }

    /** The class this species moves as (from its animation config). */
    public Locomotion locomotion() { return locomotion; }

    /** True-to-scale rendered length, blocks. */
    public float length() { return length; }

    /** Client tick: eases the pose-hint envelopes toward what the lead fish's hint says now. */
    public void tick(CompanionAgent agent) {
        tick(agent.poseHint(), agent.temperament(), agent.lookTarget() == null ? null : agent.lookTarget().sub(agent.position()));
    }

    public void tick(PoseHint hint, Temperament temperament, Vec watchDirection) {
        this.temperament = temperament;
        this.watchDirection = watchDirection;
        tick(hint);
    }

    /** Client tick with this fish's own hint (a group member's mood differs from its leader's). */
    public void tick(PoseHint hint) {
        prevTilt = tilt;
        prevHintPitch = hintPitch;
        prevRetract = retract;
        prevShiver = shiver;
        reactionClock++;
        watching = hint == PoseHint.WATCH;
        float rhythm = reactionClock * (temperament.stillness() > .8f ? .11f : temperament.nuzzle() ? .17f : .25f);
        if (temperament.regular() < 1f && temperament.jitter() > 1.3f)
            rhythm += .6f * Mth.sin(reactionClock * .47f);
        float danceRoll = (temperament.guard() ? 7f : temperament.stillness() > .8f ? 29f : temperament.nuzzle() ? 12f : 20f)
                * Mth.sin(rhythm);
        float dancePitch = (temperament.guard() ? 30f : temperament.nuzzle() ? 5f : 12f) * Mth.sin(rhythm * 2);
        float roll = hint == PoseHint.TILT ? TILT_DEG : hint == PoseHint.WATCH ? 12f
                : hint == PoseHint.DANCE ? danceRoll
                : hint == PoseHint.GLEE ? 28f * Mth.sin(reactionClock * .18f)
                : hint == PoseHint.SLEEPY ? 9f + 4f * Mth.sin(reactionClock * .045f) : 0f;
        tilt = approach(tilt, roll, TILT_RATE);
        float pitch = hint == PoseHint.BEG ? BEG_PITCH : hint == PoseHint.MILD ? MILD_PITCH
                : hint == PoseHint.MOON_GAZE ? MOON_PITCH
                : hint == PoseHint.DIP ? 32f : hint == PoseHint.DROOP ? 22f
                : hint == PoseHint.SLEEPY ? 18f + 5f * Mth.sin(reactionClock * .06f)
                : hint == PoseHint.WATCH ? -12f
                : hint == PoseHint.DANCE ? dancePitch : 0f;
        shiver = approach(shiver, hint == PoseHint.SHIVER ? 1f : 0f, SHIVER_EASE);
        hintPitch = approach(hintPitch, pitch, PITCH_RATE);
        retract = approach(retract, hint == PoseHint.DUCK ? 1f : 0f, RETRACT_RATE);
    }

    public float hintTilt(float partialTick) { return Mth.lerp(partialTick, prevTilt, tilt); }

    public float hintPitch(float partialTick) { return Mth.lerp(partialTick, prevHintPitch, hintPitch); }

    public float retract(float partialTick) { return Mth.lerp(partialTick, prevRetract, retract); }

    public float shiver(float partialTick) { return Mth.lerp(partialTick, prevShiver, shiver); }

    /** The cold shiver's roll for fish {@code index} this frame, degrees (0 unless it's shivering). */
    public float shiverRoll(float partialTick, float gameTime, int index) {
        float cold = shiver(partialTick);
        return cold <= 0f ? 0f : cold * SHIVER_ROLL_DEG * Mth.sin(shiverPhase(gameTime, index));
    }

    /** The cold shiver's yaw tremor for fish {@code index} this frame, degrees. */
    public float shiverYaw(float partialTick, float gameTime, int index) {
        float cold = shiver(partialTick);
        return cold <= 0f ? 0f : cold * SHIVER_YAW_DEG * Mth.sin(shiverPhase(gameTime, index) * 1.37f + 0.8f);
    }

    /** Each fish of a trio trembles out of step with the others. */
    private static float shiverPhase(float gameTime, int index) {
        return gameTime * SHIVER_RATE + index * 1.9f;
    }

    /**
     * Poses and submits fish {@code index} (0 the lead, 1–2 trio followers) at a world pose. The
     * stack is camera-relative. Returns false when it is faded out entirely.
     *
     * @param yawDeg   Minecraft yaw of the heading
     * @param faceDeg  the way the art faces, degrees: a drifter's heading is its lean, which can swap
     *                 sides in a tick, so its art turns separately and slower; others pass {@code yawDeg}
     * @param pitchDeg Minecraft pitch of the heading (positive is nose down)
     * @param riding   a percher on its owner (the hand): poses as perched, not swimming
     * @param onFloor  {@code pos} is the floor under a benthic walking on the ground: the pose is
     *                 lifted by its own floor offset (docs/fish-companions.md)
     * @param pulse    a drifter's squeeze, 0..1 (its stroke envelope scaled by the stroke's strength)
     * @param ripple   a flatfish gliding on the ground: its ripple runs on the gait's phase
     *                 ({@code tailPhase}), quickening as it glides faster and slowing when it stops
     */
    public boolean submit(PoseStack poseStack, MultiBufferSource buffers, Vec3 cam, int index, Vec pos,
                          float yawDeg, float faceDeg, float pitchDeg, float bankDeg, float appear, float tailPhase,
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
        boolean sourceLean = riding && watching && watchDirection != null;
        float moodPitch = sourceLean ? 0 : hintPitch(partial);
        if (onFloor) {
            poseStack.mulPose(Axis.XP.rotationDegrees(tiltDeg));
            applyGroundHinge(poseStack, moodPitch, scale * 0.5f, (float) lift);
        } else {
            poseStack.mulPose(Axis.XP.rotationDegrees(tiltDeg + moodPitch));
        }
        if (sourceLean) {
            var lean = CompanionFeatureLean.toward(poseStack.last().pose(), watchDirection);
            poseStack.mulPose(Axis.XP.rotationDegrees(lean.pitch()));
            poseStack.mulPose(Axis.ZP.rotationDegrees(lean.roll()));
        }
        float tremor = shiverYaw(partial, gameTime, index);
        if (tremor != 0f) poseStack.mulPose(Axis.YP.rotationDegrees(tremor));
        boolean lyingFlat = onFloor && anim instanceof FishAnimationConfig.FloorSit;
        float roll = (sourceLean || lyingFlat ? 0 : hintTilt(partial)) + shiverRoll(partial, gameTime, index);
        if (roll != 0f) poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
        // Lean toward the heading, but face the art its own way: upright, this is just the facing.
        if (locomotion == Locomotion.DRIFT) poseStack.mulPose(Axis.YP.rotationDegrees(Mth.wrapDegrees(yawDeg - faceDeg)));
        random.setSeed(seed * 31 + index);
        if (!riding && anim instanceof FishAnimationConfig.HorizontalSwim swim) {
            FishAnimator.applySwimming(poseStack, swim, random, clock * SWIM_CLOCK_RATE, NOSE_FORWARD, false,
                    speedFactor, bankDeg, Float.POSITIVE_INFINITY, scale);
        } else if (locomotion == Locomotion.GLIDE) {
            FishAnimator.applyGliding(poseStack, anim, random, t, NOSE_FORWARD,
                    Mth.clamp(bankDeg / GLIDE_BANK_MAX, -1f, 1f), NOSE_FORWARD, scale, false);
        } else if (locomotion == Locomotion.DRIFT) {
            FishAnimator.applyDrifting(poseStack, anim, random, t, NOSE_FORWARD, scale, false, pulse * driftSqueeze);
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
        // 1.21.1: no ItemStackRenderState / submit queue; draw through the item renderer directly.
        Minecraft mc = Minecraft.getInstance();
        boolean previousMedium = FishRenderMedium.set(wetAt(index, pos.x(), pos.y() + lift, pos.z(), gameTime));
        try {
            mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                    poseStack, buffers, mc.level, 0);
        } finally {
            FishRenderMedium.set(previousMedium);
        }
        poseStack.popPose();
        return true;
    }

    /**
     * Hinge a ground walker's mood pitch at its tail contact; discard nose-down pitch.
     */
    static void applyGroundHinge(PoseStack poseStack, float pitchDeg, float halfLength, float lift) {
        float raise = Math.min(pitchDeg, 0f);
        if (raise == 0f) return;
        poseStack.translate(0f, -lift, -halfLength);
        poseStack.mulPose(Axis.XP.rotationDegrees(raise));
        poseStack.translate(0f, lift, halfLength);
    }

    /** Hold waterline changes for four ticks so companions do not flicker at the surface. */
    private boolean wetAt(int index, double x, double y, double z, float gameTime) {
        var level = Minecraft.getInstance().level;
        boolean water = level != null && level.getFluidState(BlockPos.containing(x, y, z)).is(FluidTags.WATER);
        if (!mediumSampled[index] || water == wet[index]) {
            mediumSampled[index] = true;
            wet[index] = water;
            mediumFlipSince[index] = Float.NaN;
        } else if (Float.isNaN(mediumFlipSince[index]) || gameTime < mediumFlipSince[index]) {
            mediumFlipSince[index] = gameTime;
        } else if (gameTime - mediumFlipSince[index] >= MEDIUM_HOLD_TICKS) {
            wet[index] = water;
            mediumFlipSince[index] = Float.NaN;
        }
        return wet[index];
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
