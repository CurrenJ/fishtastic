package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Locomotion;
import grill24.fishtastic.data.FishAnimationConfig;
import com.mojang.math.Axis;
import java.util.Random;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/** The established planar tank pose, shared by world fish without advancing simulation. */
public final class GroupFishPose {
    private GroupFishPose() {}

    /** Stack is in the caller's frame; offsets locate the engine origin in that frame. */
    public static void apply(PoseStack poseStack, FlockEngine eng, FishAnimationConfig anim,
                             int i, float t, float offsetX, float offsetY, float offsetZ, Random random) {
        apply(poseStack, eng, anim, i, t, offsetX, offsetY, offsetZ, null, random);
    }

    /**
     * As {@link #apply}, with a school fish's surface-jump pose on top
     * ({@code FishSchoolSim.surfaceJumpPose}): {@code [dx, dy, dz, pitch°, yaw°, yaw weight,
     * extra tail phase]}, or null for none. Presentation only; the engine position is unchanged.
     */
    public static void apply(PoseStack poseStack, FlockEngine eng, FishAnimationConfig anim,
                             int i, float t, float offsetX, float offsetY, float offsetZ,
                             float @Nullable [] jump, Random random) {
        float scale = eng.lengths[i];
        float yaw = eng.renderYaw[i], phase = eng.renderPhase[i];
        if (jump != null) {
            offsetX += jump[0];
            offsetY += jump[1];
            offsetZ += jump[2];
            yaw += jump[5] * Mth.wrapDegrees(jump[4] - yaw);
            phase += jump[6];
        }
        // The engine's Y is the sand surface for a crawler and the swim position for a
        // swimmer; only the former needs a pose lift off the floor, which floorPoseLift
        // returns 0 for everything else.
        poseStack.translate(
                offsetX + eng.renderX[i],
                offsetY + eng.renderY[i] + FishAnimator.floorPoseLift(anim, scale),
                offsetZ + eng.renderZ[i]);

        random.setSeed(eng.seeds[i]);
        // Planar model: continuous yaw, no mirror flag. The +180° maps the engine's
        // "faces +lateral at 0°" convention onto the item sprite, whose nose points along
        // −lateral at rotation 0 (same offset the single-tank mirror mapping encodes).
        if (eng.swimmers[i]) {
            if (jump != null && jump[3] != 0f) {
                // Pitch about the body's own sideways axis, at its centre: turned onto the heading,
                // tipped, and turned back, so the swim pose below lays its yaw over the tipped frame.
                // The sprite's nose is local -X after the yaw, so a negative Z turn lifts it.
                poseStack.mulPose(Axis.YP.rotationDegrees(yaw + 180f));
                poseStack.mulPose(Axis.ZP.rotationDegrees(-jump[3]));
                poseStack.mulPose(Axis.YP.rotationDegrees(-(yaw + 180f)));
            }
            FishAnimator.applySwimming(poseStack, (FishAnimationConfig.HorizontalSwim) anim, random,
                    phase, yaw + 180f, false, eng.speedFactor(i), eng.bank[i],
                    eng.renderBobRoom[i], scale);
        } else if (eng.locomotion[i] == Locomotion.BENTHIC) {
            FishAnimator.applyBenthic(poseStack, anim, random, t, eng.renderYaw[i] + 180f,
                    eng.baseRotations[i], scale, false, eng.renderShape[i]);
        } else if (eng.locomotion[i] == Locomotion.GLIDE) {
            FishAnimator.applyGliding(poseStack, anim, random, t, eng.renderYaw[i] + 180f,
                    eng.bankFraction(i), eng.baseRotations[i], scale, false);
        } else if (eng.locomotion[i] == Locomotion.DRIFT) {
            FishAnimator.applyDrifting(poseStack, anim, random, t, eng.baseRotations[i],
                    scale, eng.hoverMirrored[i], eng.renderShape[i]);
        } else if (eng.locomotion[i] == Locomotion.ANCHORED) {
            FishAnimator.applyAnchored(poseStack, anim, random, t, eng.baseRotations[i],
                    scale, eng.hoverMirrored[i], eng.renderShape[i]);
        } else {
            // Anything the group's engine demoted to STATIC: pose on game time exactly as the
            // single-tank path does. Branching on swimmers[] rather than falling through to
            // applySwimming is what keeps a jellyfish — an UprightFloat, not a
            // HorizontalSwim — from reaching that cast.
            FishAnimator.apply(poseStack, anim, random, t, eng.baseRotations[i], scale,
                    eng.hoverMirrored[i]);
        }

        poseStack.scale(scale, scale, scale);

    }
}
