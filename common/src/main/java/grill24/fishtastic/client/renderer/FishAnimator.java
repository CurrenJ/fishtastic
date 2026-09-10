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

    /**
     * Pose for a creature the engine is walking across the floor: identical to
     * {@link #apply} except that the heading comes from the simulation rather than from the
     * fish's seed, so the sprite faces where it is actually going. The idle sway stays on game
     * time — it is the creature fidgeting, not a function of how fast it is travelling.
     *
     * <p>{@code yawDeg} follows the engine's convention (the group swimmer path's
     * {@code renderYaw + 180f}): the item sprite's nose points along −lateral at rotation 0, so
     * the caller adds the half-turn that maps the engine's frame onto the sprite's.
     *
     * <p>Poses with no floor walk of their own fall through to {@link #apply} unchanged.
     */
    public static void applyBenthic(PoseStack poseStack, FishAnimationConfig config, Random random,
                                    float t, float yawDeg, float baseRotation, float scale, boolean mirrored) {
        switch (config) {
            case FishAnimationConfig.FloorSit cfg ->
                    applyFloorSit(poseStack, cfg, random, t, mirrored, yawDeg);
            case FishAnimationConfig.UprightSit cfg ->
                    applyUprightSit(poseStack, cfg, random, t, baseRotation, mirrored, yawDeg);
            default -> apply(poseStack, config, random, t, baseRotation, scale, mirrored);
        }
    }

    /**
     * Pose for a creature the engine is gliding — a ray. Two things come from the simulation
     * rather than from a clock: the heading, as for {@link #applyBenthic}, and the <b>bank</b>,
     * which used to be an open-loop sine and is now the lean the fish has actually earned by
     * turning. A ray banking into its own turns is most of what makes it read as a ray, and a
     * sine that banks while flying straight is most of what stopped it.
     *
     * <p>{@code bankFraction} is the engine's normalised lean in [−1, 1]
     * ({@code FlockEngine.bankFraction}); the pose's own {@code bank_amplitude} still says how far
     * this species leans at full lock, so the data file keeps authoring the amplitude and the
     * engine supplies only the shape.
     *
     * <p>Poses with no glide of their own fall through to {@link #apply} unchanged.
     */
    public static void applyGliding(PoseStack poseStack, FishAnimationConfig config, Random random,
                                    float t, float yawDeg, float bankFraction, float baseRotation,
                                    float scale, boolean mirrored) {
        switch (config) {
            case FishAnimationConfig.BellyDown cfg ->
                    applyBellyDown(poseStack, cfg, random, t, yawDeg, mirrored,
                            cfg.bankAmplitude() * bankFraction);
            default -> apply(poseStack, config, random, t, baseRotation, scale, mirrored);
        }
    }

    /**
     * How far above the sand a floor-dwelling pose has to sit, on top of whatever floor height the
     * engine reports: the config's own manual nudge, plus — for an upright pose — the compensation
     * for the item being pivoted about its centre rather than its base.
     *
     * <p>Every path that draws a crawler needs this, which is exactly why it is a method. It used
     * to be inlined in {@code FishTankBlockEntityRenderer.computeBaseY}, the single-tank path's helper; when crawlers started
     * rendering in group space too, that path translated by the engine's floor height alone and
     * upright creatures sank to their waists in the sand.
     *
     * <p>The compensation scales with the fish's own per-catch render scale. A fixed offset would
     * float small catches above the sand and sink large ones into it.
     */
    public static float floorPoseLift(FishAnimationConfig animConfig, float scale) {
        return switch (animConfig) {
            case FishAnimationConfig.FloorSit    fs -> fs.floorOffset();
            // The pivot is measured per species rather than assumed to be half the item: see
            // UprightSit.pivotFraction. PLANTED_PIVOT_Y remains the value for art that fills its
            // canvas, and the default for art nobody has measured.
            case FishAnimationConfig.UprightSit  us -> us.floorOffset() + us.pivotFraction() * scale;
            default -> 0f;
        };
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
        // Speed couples into the FREQUENCY only through the caller's clock: simulated swimmers
        // pass the engine's speed-integrated tail phase as t (see FlockEngine.renderPhase), never
        // by scaling hertz here — hertz multiplies t inside the sine, so per-frame hertz changes
        // teleport the instantaneous phase by t·Δhertz (the empirical "jitters and jumps in place"
        // bug).
        //
        // Amplitude is coupled to speed only very weakly. It used to be a straight
        // `bobAmplitude * speedFactor`, justified by per-frame deltas being sub-pixel — which is
        // true, and beside the point: what matters is the ENVELOPE, not the per-frame step. Once
        // burst-and-coast gave speed a real swing (docs/fish-swarm-realism.md §2), speedFactor
        // began sweeping ~1.0→1.45 over about half a second, so the whole body rose and fell in
        // time with every burst — reported in game as fish "hopping" up and forward every few
        // seconds. Frequency coupling already conveys effort; amplitude coupling on top of it
        // double-counts speed and is what turns a burst into a hop.
        float hertz = cfg.bobHertz() + (random.nextFloat() * 0.04f);
        float yBob = getBobbingHeight(random, t, cfg.bobAmplitude() * (0.9f + 0.1f * speedFactor), hertz);
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
        applyFloorSit(poseStack, cfg, random, t, mirrored, null);
    }

    /**
     * @param crawlYaw the engine's heading for a walking creature, or null for one that sits still
     *                 and faces wherever its seed put it
     */
    private static void applyFloorSit(PoseStack poseStack, FishAnimationConfig.FloorSit cfg,
                                       Random random, float t, boolean mirrored, Float crawlYaw) {
        float randomPhaseRad = random.nextFloat() * (float) (2 * Math.PI);
        float yRot = (float) (Math.sin(t * cfg.rotationHertz() * 2 * Math.PI + randomPhaseRad)
                * cfg.rotationAmplitude());
        float facing = crawlYaw != null ? crawlYaw : (float) Math.toDegrees(randomPhaseRad);
        poseStack.mulPose(Axis.YP.rotationDegrees(facing + yRot + (mirrored ? 180f : 0f)));
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
        applyBellyDown(poseStack, cfg, random, t, baseRotation, mirrored, null);
    }

    /**
     * @param engineBankDeg the lean the engine says this fish has earned by turning, or null for
     *                      one nothing is steering, which rocks on its own open-loop sine instead.
     *                      The random draw happens either way: the RNG stream is shared with every
     *                      other pose term and consuming it conditionally would shift them all.
     */
    private static void applyBellyDown(PoseStack poseStack, FishAnimationConfig.BellyDown cfg,
                                        Random random, float t, float baseRotation, boolean mirrored,
                                        Float engineBankDeg) {
        float hertz = cfg.bobHertz() + (random.nextFloat() * 0.02f);
        float yBob = getBobbingHeight(random, t, cfg.bobAmplitude(), hertz);
        poseStack.translate(0f, yBob, 0f);
        poseStack.mulPose(Axis.YP.rotationDegrees(baseRotation + (mirrored ? 180f : 0f)));
        float randomPhaseRad = random.nextFloat() * (float) (2 * Math.PI);
        float bankDeg = engineBankDeg != null ? engineBankDeg
                : (float) (Math.sin(t * cfg.bankHertz() * 2 * Math.PI + randomPhaseRad) * cfg.bankAmplitude());
        // Rotate -90° around the axis parallel to the swim direction (local X) so the belly faces
        // world-down; the banking offset rocks the wings gently around that same axis.
        poseStack.mulPose(Axis.XP.rotationDegrees(-90f + bankDeg));
    }

    private static void applyUprightSit(PoseStack poseStack, FishAnimationConfig.UprightSit cfg,
                                         Random random, float t, float baseRotation, boolean mirrored) {
        applyUprightSit(poseStack, cfg, random, t, baseRotation, mirrored, null);
    }

    /** @param crawlYaw as {@link #applyFloorSit}: the engine's heading, or null when it sits still. */
    private static void applyUprightSit(PoseStack poseStack, FishAnimationConfig.UprightSit cfg,
                                         Random random, float t, float baseRotation, boolean mirrored,
                                         Float crawlYaw) {
        if (crawlYaw != null) baseRotation = crawlYaw;
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
