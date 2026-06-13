package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import grill24.fishtastic.data.FishAnimationConfig;

import java.util.Random;

public final class FishAnimator {
    private FishAnimator() {}

    /**
     * Applies orientation and animation transforms to the PoseStack for the given animation config.
     * The stack is assumed to already be translated to the fish's base XZ+Y position in the tank.
     * Scale should be applied by the caller after this method returns.
     *
     * @param random seeded consistently per tank so animations are stable across frames
     */
    public static void apply(PoseStack poseStack, FishAnimationConfig config, Random random, float t, float baseRotation) {
        switch (config) {
            case FishAnimationConfig.HorizontalSwim cfg -> applyHorizontalSwim(poseStack, cfg, random, t, baseRotation);
            case FishAnimationConfig.UprightFloat   cfg -> applyUprightFloat(poseStack, cfg, random, t, baseRotation);
            case FishAnimationConfig.FloorSit       cfg -> applyFloorSit(poseStack, cfg, random, t);
            case FishAnimationConfig.Planted        cfg -> applyPlanted(poseStack, cfg, random, t, baseRotation);
        }
    }

    // ── Mode implementations ──────────────────────────────────────────────────

    private static void applyHorizontalSwim(PoseStack poseStack, FishAnimationConfig.HorizontalSwim cfg,
                                             Random random, float t, float baseRotation) {
        float hertz = cfg.bobHertz() + (random.nextFloat() * 0.04f);
        float yBob = getBobbingHeight(random, t, cfg.bobAmplitude(), hertz);
        poseStack.translate(0f, yBob, 0f);
        poseStack.mulPose(Axis.YP.rotationDegrees(baseRotation));
        float surfAngle = getSurfingAngle(random, t, cfg.bobAmplitude(), hertz) * cfg.surfFactor();
        float yWiggle = getOrganicWiggle(random, t) * cfg.wiggleScale();
        poseStack.mulPose(Axis.YP.rotationDegrees(yWiggle));
        poseStack.mulPose(Axis.ZP.rotationDegrees(surfAngle + 45f));
    }

    private static void applyUprightFloat(PoseStack poseStack, FishAnimationConfig.UprightFloat cfg,
                                           Random random, float t, float baseRotation) {
        float randomPhaseRad = random.nextFloat() * (float) (2 * Math.PI);
        float hertz = cfg.bobHertz() + (random.nextFloat() * 0.01f);
        float yBob = (float) (Math.sin((t / (20f / hertz) + randomPhaseRad) * 2 * Math.PI) * cfg.bobAmplitude());
        poseStack.translate(0f, yBob, 0f);
        poseStack.mulPose(Axis.YP.rotationDegrees(baseRotation));
        // 45° CCW from default item diagonal → fish is upright (head pointing up)
        poseStack.mulPose(Axis.ZP.rotationDegrees(-45f));
    }

    private static void applyFloorSit(PoseStack poseStack, FishAnimationConfig.FloorSit cfg,
                                       Random random, float t) {
        float randomPhaseRad = random.nextFloat() * (float) (2 * Math.PI);
        float yRot = (float) (Math.sin(t * cfg.rotationHertz() * 2 * Math.PI + randomPhaseRad)
                * cfg.rotationAmplitude());
        poseStack.mulPose(Axis.YP.rotationDegrees((float) Math.toDegrees(randomPhaseRad) + yRot));
        // Rotate X -90° so item faces upward, lying flat on the floor
        poseStack.mulPose(Axis.XP.rotationDegrees(-90f));
    }

    private static void applyPlanted(PoseStack poseStack, FishAnimationConfig.Planted cfg,
                                      Random random, float t, float baseRotation) {
        poseStack.mulPose(Axis.YP.rotationDegrees(baseRotation));
        float randomPhaseRad = random.nextFloat() * (float) (2 * Math.PI);
        float wiggle = (float) (Math.sin(t * cfg.wiggleHertz() * 2 * Math.PI + randomPhaseRad)
                * cfg.wiggleAmplitude());
        // Pivot the sway around the item's base rather than its centre.
        // Items in FIXED display context span ~0.5 units; half-height ≈ 0.25 below centre.
        poseStack.translate(0f, PLANTED_PIVOT_Y, 0f);
        poseStack.mulPose(Axis.ZP.rotationDegrees(wiggle));
        poseStack.translate(0f, -PLANTED_PIVOT_Y, 0f);
    }

    /** Distance from item centre to its bottom in FIXED display-context world units (pre-custom-scale). */
    public static final float PLANTED_PIVOT_Y = 0.25f;

    // ── Shared animation helpers ──────────────────────────────────────────────

    private static float getBobbingHeight(Random random, float t, float amplitude, float hertz) {
        float time = t / (20f / hertz);
        time += random.nextFloat() * (float) (2 * Math.PI);
        return (float) (Math.sin(time * 2 * Math.PI) * amplitude);
    }

    private static float getSurfingAngle(Random random, float t, float amplitude, float hertz) {
        float time = t / (20f / hertz);
        time += random.nextFloat() * (float) (2 * Math.PI);
        float derivative = (float) (Math.cos(time * 2 * Math.PI) * amplitude * 2 * Math.PI);
        return (float) Math.toDegrees(Math.atan(derivative));
    }

    private static float getOrganicWiggle(Random random, float t) {
        float randomOffset = random.nextFloat() * (float) (2 * Math.PI);
        float slowWave   = (float) Math.sin((t / 60f  + randomOffset) * 2 * Math.PI) * 15f;
        float mediumWave = (float) Math.sin((t / 35f  + randomOffset * 1.3f) * 2 * Math.PI) * 8f;
        float fastWave   = (float) Math.sin((t / 18f  + randomOffset * 0.7f) * 2 * Math.PI) * 4f;
        float drift      = (float) Math.sin((t / 120f + randomOffset * 0.5f) * 2 * Math.PI) * 10f;
        return slowWave + mediumWave + fastWave + drift;
    }
}
