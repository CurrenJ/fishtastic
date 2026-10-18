package grill24.fishtastic.client.ambient;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishtastic.client.renderer.FishAnimator;
import grill24.fishtastic.data.FishAnimationConfig;
import net.minecraft.world.phys.AABB;

/** Shrink around the resolved model's centre, or an eel's planted base. */
public final class AmbientFishFade {
    private AmbientFishFade() {}

    public static void apply(PoseStack poses, float fade, FishAnimationConfig animation, AABB bounds) {
        double x = (bounds.minX + bounds.maxX) * 0.5;
        double y = (bounds.minY + bounds.maxY) * 0.5;
        double z = (bounds.minZ + bounds.maxZ) * 0.5;
        if (animation instanceof FishAnimationConfig.Planted) {
            x = z = 0;
            y = -FishAnimator.PLANTED_PIVOT_Y;
        }
        poses.translate(x, y, z);
        poses.scale(fade, fade, fade);
        poses.translate(-x, -y, -z);
    }
}
