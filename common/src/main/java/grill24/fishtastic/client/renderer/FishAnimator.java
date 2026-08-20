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
     * Scale should be applied by the caller after this method returns — {@code scale} is passed in
     * only so a mode can pivot around a point derived from the item's own size (see
     * {@link #applyPlanted}); it isn't applied to the stack here.
     *
     * @param random seeded consistently per tank so animations are stable across frames
     * @param scale the per-fish render scale the caller will apply after this call returns
     * @param mirrored whether to render left/right-flipped, for natural variety. Implemented as an
     *                 extra 180° turn about Y rather than a negative-scale reflection: the generated
     *                 item model's back face already carries a horizontally mirrored UV (so a flat
     *                 sprite reads correctly from either side), so turning the fish around presents
     *                 that pre-mirrored face to the camera. A true negative-scale flip would corrupt
     *                 the model's face winding/normals instead.
     */
    public static void apply(PoseStack poseStack, FishAnimationConfig config, Random random, float t, float baseRotation, float scale, boolean mirrored) {
        switch (config) {
            case FishAnimationConfig.HorizontalSwim cfg -> applyHorizontalSwim(poseStack, cfg, random, t, baseRotation, mirrored, 1f, 0f);
            case FishAnimationConfig.UprightFloat   cfg -> applyUprightFloat(poseStack, cfg, random, t, baseRotation, mirrored);
            case FishAnimationConfig.FloorSit       cfg -> applyFloorSit(poseStack, cfg, random, t, mirrored);
            case FishAnimationConfig.Planted        cfg -> applyPlanted(poseStack, cfg, random, t, baseRotation, scale, mirrored);
            case FishAnimationConfig.BellyDown      cfg -> applyBellyDown(poseStack, cfg, random, t, baseRotation, mirrored);
            case FishAnimationConfig.UprightSit     cfg -> applyUprightSit(poseStack, cfg, random, t, baseRotation, mirrored);
        }
    }

    // ── Mode implementations ──────────────────────────────────────────────────

    /**
     * Horizontal-swim pose with animation coupling for simulated swimmers: {@code speedFactor}
     * scales the tail-beat frequency (and amplitude), and {@code bankDeg} banks the fish about its
     * swim axis in response to turning. Both are 0/1 on the hover path, which keeps the random
     * consumption order identical to the pre-simulation animation.
     */
    public static void applySwimming(PoseStack poseStack, FishAnimationConfig.HorizontalSwim cfg,
                                     Random random, float t, float baseRotation, boolean mirrored,
                                     float speedFactor, float bankDeg) {
        applyHorizontalSwim(poseStack, cfg, random, t, baseRotation, mirrored, speedFactor, bankDeg);
    }

    private static void applyHorizontalSwim(PoseStack poseStack, FishAnimationConfig.HorizontalSwim cfg,
                                             Random random, float t, float baseRotation, boolean mirrored,
                                             float speedFactor, float bankDeg) {
        float hertz = (cfg.bobHertz() + (random.nextFloat() * 0.04f)) * speedFactor;
        float yBob = getBobbingHeight(random, t, cfg.bobAmplitude() * speedFactor, hertz);
        poseStack.translate(0f, yBob, 0f);
        poseStack.mulPose(Axis.YP.rotationDegrees(baseRotation + (mirrored ? 180f : 0f)));
        if (bankDeg != 0f) poseStack.mulPose(Axis.XP.rotationDegrees(bankDeg));
        float surfAngle = getSurfingAngle(random, t, cfg.bobAmplitude(), hertz) * cfg.surfFactor();
        float yWiggle = getOrganicWiggle(random, t) * cfg.wiggleScale();
        poseStack.mulPose(Axis.YP.rotationDegrees(yWiggle));
        poseStack.mulPose(Axis.ZP.rotationDegrees(surfAngle + (cfg.diagonalTexture() ? 45f : 0f)));
    }

    private static void applyUprightFloat(PoseStack poseStack, FishAnimationConfig.UprightFloat cfg,
                                           Random random, float t, float baseRotation, boolean mirrored) {
        float randomPhaseRad = random.nextFloat() * (float) (2 * Math.PI);
        float hertz = cfg.bobHertz() + (random.nextFloat() * 0.01f);
        float yBob = (float) (Math.sin((t / (20f / hertz) + randomPhaseRad) * 2 * Math.PI) * cfg.bobAmplitude());
        poseStack.translate(0f, yBob, 0f);
        poseStack.mulPose(Axis.YP.rotationDegrees(baseRotation + (mirrored ? 180f : 0f)));
        if (cfg.diagonalTexture()) {
            // 45° CCW from default item diagonal → fish is upright (head pointing up)
            poseStack.mulPose(Axis.ZP.rotationDegrees(-45f));
        }
    }

    private static void applyFloorSit(PoseStack poseStack, FishAnimationConfig.FloorSit cfg,
                                       Random random, float t, boolean mirrored) {
        float randomPhaseRad = random.nextFloat() * (float) (2 * Math.PI);
        float yRot = (float) (Math.sin(t * cfg.rotationHertz() * 2 * Math.PI + randomPhaseRad)
                * cfg.rotationAmplitude());
        poseStack.mulPose(Axis.YP.rotationDegrees((float) Math.toDegrees(randomPhaseRad) + yRot + (mirrored ? 180f : 0f)));
        // Rotate X -90° so item faces upward, lying flat on the floor
        poseStack.mulPose(Axis.XP.rotationDegrees(-90f));
    }

    private static void applyPlanted(PoseStack poseStack, FishAnimationConfig.Planted cfg,
                                      Random random, float t, float baseRotation, float scale, boolean mirrored) {
        poseStack.mulPose(Axis.YP.rotationDegrees(baseRotation + (mirrored ? 180f : 0f)));
        float randomPhaseRad = random.nextFloat() * (float) (2 * Math.PI);
        float wiggle = (float) (Math.sin(t * cfg.wiggleHertz() * 2 * Math.PI + randomPhaseRad)
                * cfg.wiggleAmplitude());
        // Pivot the sway around the item's base rather than its centre. This translate runs before
        // the caller's poseStack.scale(scale), i.e. in unscaled world-block units, so PLANTED_PIVOT_Y
        // (the item's own half-height at scale 1) must be scaled here explicitly to land on the same
        // point regardless of this fish's own render scale.
        float pivot = PLANTED_PIVOT_Y * scale;
        poseStack.translate(0f, pivot, 0f);
        poseStack.mulPose(Axis.ZP.rotationDegrees(wiggle));
        poseStack.translate(0f, -pivot, 0f);
    }

    private static void applyBellyDown(PoseStack poseStack, FishAnimationConfig.BellyDown cfg,
                                        Random random, float t, float baseRotation, boolean mirrored) {
        float hertz = cfg.bobHertz() + (random.nextFloat() * 0.02f);
        float yBob = getBobbingHeight(random, t, cfg.bobAmplitude(), hertz);
        poseStack.translate(0f, yBob, 0f);
        poseStack.mulPose(Axis.YP.rotationDegrees(baseRotation + (mirrored ? 180f : 0f)));
        float randomPhaseRad = random.nextFloat() * (float) (2 * Math.PI);
        float bankDeg = (float) (Math.sin(t * cfg.bankHertz() * 2 * Math.PI + randomPhaseRad) * cfg.bankAmplitude());
        // Rotate -90° around the axis parallel to the swim direction (local X) so the belly faces
        // world-down; the banking offset rocks the wings gently around that same axis.
        poseStack.mulPose(Axis.XP.rotationDegrees(-90f + bankDeg));
    }

    private static void applyUprightSit(PoseStack poseStack, FishAnimationConfig.UprightSit cfg,
                                         Random random, float t, float baseRotation, boolean mirrored) {
        float randomPhaseRad = random.nextFloat() * (float) (2 * Math.PI);
        float yRot = (float) (Math.sin(t * cfg.rotationHertz() * 2 * Math.PI + randomPhaseRad)
                * cfg.rotationAmplitude());
        poseStack.mulPose(Axis.YP.rotationDegrees(baseRotation + yRot + (mirrored ? 180f : 0f)));
        if (cfg.diagonalTexture()) {
            // Same correction as UprightFloat: 45° CCW from default item diagonal → upright.
            poseStack.mulPose(Axis.ZP.rotationDegrees(-45f));
        }
    }

    /**
     * Distance from an item's centre to its bottom in FIXED display-context world units, at this
     * fish's own render scale of 1.0 — i.e. always needs multiplying by the fish's actual render
     * scale by the caller. Verified against vanilla: {@code ItemTransform.apply} centres every
     * item's baked quad with a final {@code translate(-0.5, -0.5, -0.5)}, and the FIXED transform
     * for {@code item/generated} models applies no additional scale, so the full quad spans
     * {@code -0.5..+0.5} — half-height 0.5, not 0.25.
     */
    public static final float PLANTED_PIVOT_Y = 0.5f;

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
