package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishsim.core.FlockEngine;
import grill24.fishtastic.client.ambient.AmbientFishFade;
import grill24.fishtastic.data.FishAnimationConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.Random;

/**
 * Draws fish an in-world {@link FlockEngine} is moving — the ambient population and interactive
 * schools alike — so the two can never drift apart in how a simulated fish looks: the pose is
 * {@link GroupFishPose}'s, the fade {@link AmbientFishFade}'s, and the item is lit where it swims.
 */
public final class GroupFishDraw {
    private GroupFishDraw() {}

    /**
     * Sorts {@code order[0..n)} so the fish furthest along the camera's view come first (drawn
     * first, under the nearer ones), by the view depths in {@code depth}. Insertion sort: a
     * population is a few dozen fish and already nearly in order frame to frame.
     */
    public static void backToFront(float[] depth, int[] order, int n) {
        for (int k = 1; k < n; k++) {
            int i = order[k], at = k;
            while (at > 0 && depth[order[at - 1]] < depth[i]) {
                order[at] = order[at - 1];
                at--;
            }
            order[at] = i;
        }
    }

    /**
     * Submits fish #{@code i} of {@code engine}, whose frame is {@code origin} in the world, after
     * {@link FlockEngine#interpolate} has run for this frame. {@code frozen}, if given, replaces the
     * live pose (a retiring ambient fish holds still while it fades). Fish in unloaded chunks or
     * out of the frustum are skipped.
     *
     * @return whether it was drawn
     */
    public static boolean submit(PoseStack poses, SubmitNodeCollector nodes, ClientLevel level, Camera camera,
                                 double originX, double originY, double originZ, FlockEngine engine, int i,
                                 FishAnimationConfig anim, ItemStack stack, ItemStackRenderState state,
                                 float fade, @Nullable Matrix4f frozen, float time, Random poseRandom) {
        return submit(poses, nodes, level, camera, originX, originY, originZ, engine, i, anim, stack,
                state, fade, frozen, time, null, poseRandom);
    }

    /** As {@link #submit}, with a school fish's surface-jump pose for this frame ({@link GroupFishPose#apply}). */
    public static boolean submit(PoseStack poses, SubmitNodeCollector nodes, ClientLevel level, Camera camera,
                                 double originX, double originY, double originZ, FlockEngine engine, int i,
                                 FishAnimationConfig anim, ItemStack stack, ItemStackRenderState state,
                                 float fade, @Nullable Matrix4f frozen, float time, float @Nullable [] jump,
                                 Random poseRandom) {
        Vec3 cam = camera.position();
        boolean jumping = frozen == null && jump != null;
        double x = originX + (frozen == null ? engine.renderX[i] + (jumping ? jump[0] : 0f) : frozen.m30());
        double y = originY + (frozen == null ? engine.renderY[i] + (jumping ? jump[1] : 0f) : frozen.m31());
        double z = originZ + (frozen == null ? engine.renderZ[i] + (jumping ? jump[2] : 0f) : frozen.m32());
        double radius = Math.max(1.0, engine.lengths[i] * 2.0);
        if (!level.hasChunkAt(BlockPos.containing(x, y, z))) return false;
        if (!camera.getCullFrustum().isVisible(new AABB(x - radius, y - radius, z - radius,
                x + radius, y + radius, z + radius))) return false;
        poses.pushPose();
        poses.translate(originX - cam.x, originY - cam.y, originZ - cam.z);
        if (frozen != null) {
            poses.mulPose(frozen);
        } else {
            GroupFishPose.apply(poses, engine, anim, i, time, 0f, 0f, 0f, jump, poseRandom);
        }
        // Always wet: a school only leaves the water mid-jump, and a jump keeps the wet look.
        FishRenderMedium.resolve(Minecraft.getInstance().getItemModelResolver(), state, stack, true);
        if (fade < 1f) AmbientFishFade.apply(poses, fade, anim, state.getModelBoundingBox());
        int light = LevelRenderer.getLightCoords(level, BlockPos.containing(x, y, z));
        state.submit(poses, nodes, light, OverlayTexture.NO_OVERLAY, 0);
        poses.popPose();
        return true;
    }
}
