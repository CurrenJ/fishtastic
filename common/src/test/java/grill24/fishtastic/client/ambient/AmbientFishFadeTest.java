package grill24.fishtastic.client.ambient;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishtastic.data.FishAnimationConfig;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AmbientFishFadeTest {
    @Test void offCentreModelShrinksWithoutMovingItsCentre() {
        var bounds = new AABB(0.2, -0.4, -0.1, 0.8, 0.2, 0.1);
        checkFixedPoint(FishAnimationConfig.HorizontalSwim.DEFAULT, bounds, new Vector3f(0.5f, -0.1f, 0));
    }

    @Test void plantedBaseDoesNotRiseAsTheEelShrinks() {
        checkFixedPoint(FishAnimationConfig.Planted.DEFAULT,
                new AABB(-0.5, -0.5, -0.05, 0.5, 0.5, 0.05), new Vector3f(0, -0.5f, 0));
    }

    private void checkFixedPoint(FishAnimationConfig anim, AABB bounds, Vector3f pivot) {
        for (float fade : new float[]{1, 0.75f, 0.5f, 0.1f}) {
            PoseStack poses = new PoseStack();
            poses.translate(12, 63, -8);
            poses.scale(0.35f, 0.35f, 0.35f);
            Vector3f before = poses.last().pose().transformPosition(new Vector3f(pivot));
            AmbientFishFade.apply(poses, fade, anim, bounds);
            Vector3f after = poses.last().pose().transformPosition(new Vector3f(pivot));
            assertEquals(0, before.distance(after), 1e-5, "pivot moved at fade=" + fade);
            assertEquals(0.35f * fade, poses.last().pose().m00(), 1e-6, "body must actually shrink");
        }
    }
}
