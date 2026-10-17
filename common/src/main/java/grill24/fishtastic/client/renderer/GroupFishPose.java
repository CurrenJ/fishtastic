package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Locomotion;
import grill24.fishtastic.data.FishAnimationConfig;
import java.util.Random;

/** The established planar tank pose, shared by world fish without advancing simulation. */
public final class GroupFishPose {
    private GroupFishPose() {}

    /** Stack is in the caller's frame; offsets locate the engine origin in that frame. */
    public static void apply(PoseStack poseStack, FlockEngine eng, FishAnimationConfig anim,
                             int i, float t, float offsetX, float offsetY, float offsetZ, Random random) {
        float scale = eng.lengths[i];
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
            FishAnimator.applySwimming(poseStack, (FishAnimationConfig.HorizontalSwim) anim, random,
                    eng.renderPhase[i], eng.renderYaw[i] + 180f, false, eng.speedFactor(i), eng.bank[i],
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
