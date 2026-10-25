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
        Vec3 cam = camera.getPosition();
        double x = originX + (frozen == null ? engine.renderX[i] : frozen.m30());
        double y = originY + (frozen == null ? engine.renderY[i] : frozen.m31());
        double z = originZ + (frozen == null ? engine.renderZ[i] : frozen.m32());
        double radius = Math.max(1.0, engine.lengths[i] * 2.0);
        if (!level.hasChunkAt(BlockPos.containing(x, y, z))) return false;
        if (!frustum.isVisible(new AABB(x - radius, y - radius, z - radius,
                x + radius, y + radius, z + radius))) return false;
        poses.pushPose();
        poses.translate(originX - cam.x, originY - cam.y, originZ - cam.z);
        if (frozen == null) GroupFishPose.apply(poses, engine, anim, i, time, 0f, 0f, 0f, poseRandom);
        else poses.mulPose(frozen);
        if (fade < 1f) AmbientFishFade.apply(poses, fade, anim, AmbientModelBounds.of(stack, level));
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(x, y, z));
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED,
                light, OverlayTexture.NO_OVERLAY, poses, buffers, level, 0);
        poses.popPose();
        return true;
    }
}