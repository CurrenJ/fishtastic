package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishsim.core.FlockEngine;
import grill24.fishtastic.client.ambient.AmbientFishFade;
import grill24.fishtastic.client.ambient.AmbientModelBounds;
import grill24.fishtastic.data.FishAnimationConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/** Shared immediate-mode drawing for decorative group fish and interactive school fish. */
public final class GroupFishDraw {
    private GroupFishDraw() {}

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

    public static boolean submit(PoseStack poses, MultiBufferSource buffers, Frustum frustum,
                                 ClientLevel level, Camera camera, double originX, double originY, double originZ,
                                 FlockEngine engine, int i, FishAnimationConfig anim, ItemStack stack,
                                 float fade, @Nullable Matrix4f frozen, float time, java.util.Random poseRandom) {
        return submit(poses, buffers, frustum, level, camera, originX, originY, originZ, engine, i,
                anim, stack, fade, frozen, time, null, poseRandom);
    }

    /** Renders a fish with an optional surface-jump pose, keeping the target immediate-mode item path. */
    public static boolean submit(PoseStack poses, MultiBufferSource buffers, Frustum frustum,
                                 ClientLevel level, Camera camera, double originX, double originY, double originZ,
                                 FlockEngine engine, int i, FishAnimationConfig anim, ItemStack stack,
                                 float fade, @Nullable Matrix4f frozen, float time, float @Nullable [] jump,
                                 java.util.Random poseRandom) {
        Vec3 cam = camera.getPosition();
        boolean jumping = frozen == null && jump != null;
        double x = originX + (frozen == null ? engine.renderX[i] + (jumping ? jump[0] : 0f) : frozen.m30());
        double y = originY + (frozen == null ? engine.renderY[i] + (jumping ? jump[1] : 0f) : frozen.m31());
        double z = originZ + (frozen == null ? engine.renderZ[i] + (jumping ? jump[2] : 0f) : frozen.m32());
        double radius = Math.max(1.0, engine.lengths[i] * 2.0);
        if (!level.hasChunkAt(BlockPos.containing(x, y, z))) return false;
        if (!frustum.isVisible(new AABB(x - radius, y - radius, z - radius,
                x + radius, y + radius, z + radius))) return false;
        poses.pushPose();
        poses.translate(originX - cam.x, originY - cam.y, originZ - cam.z);
        if (frozen == null) GroupFishPose.apply(poses, engine, anim, i, time, 0f, 0f, 0f, jump, poseRandom);
        else {
            poses.last().pose().mul(frozen);
            poses.last().normal().mul(frozen.normal(new org.joml.Matrix3f()));
        }
        if (fade < 1f) AmbientFishFade.apply(poses, fade, anim, AmbientModelBounds.of(stack, level));
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(x, y, z));
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED,
                light, OverlayTexture.NO_OVERLAY, poses, buffers, level, 0);
        poses.popPose();
        return true;
    }
}
