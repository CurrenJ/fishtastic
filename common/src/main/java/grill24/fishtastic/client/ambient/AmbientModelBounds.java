package grill24.fishtastic.client.ambient;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * The drawn extent of a fish item in the pose space {@code ItemRenderer.renderStatic} leaves the
 * pose stack in (display transform plus the (-.5,-.5,-.5) centring). 26.1 reads this from
 * {@code ItemStackRenderState.getModelBoundingBox}; 1.21.1 has no such state, so it is rebuilt from
 * the baked quads the same way: every vertex, through the FIXED display transform.
 */
public final class AmbientModelBounds {
    private AmbientModelBounds() {}

    public static AABB of(ItemStack stack, Level level) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getItemRenderer().getModel(stack, level, null, 0);
        PoseStack poses = new PoseStack();
        model.getTransforms().getTransform(ItemDisplayContext.FIXED).apply(false, poses);
        poses.translate(-0.5f, -0.5f, -0.5f);
        var pose = poses.last().pose();
        Vector3f v = new Vector3f();
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        RandomSource random = RandomSource.create(42L);
        boolean any = false;
        for (int side = -1; side < Direction.values().length; side++) {
            Direction dir = side < 0 ? null : Direction.values()[side];
            for (BakedQuad quad : model.getQuads(null, dir, random)) {
                int[] data = quad.getVertices();
                for (int i = 0; i < 4; i++) {
                    v.set(Float.intBitsToFloat(data[i * 8]), Float.intBitsToFloat(data[i * 8 + 1]),
                            Float.intBitsToFloat(data[i * 8 + 2])).mulPosition(pose);
                    minX = Math.min(minX, v.x); maxX = Math.max(maxX, v.x);
                    minY = Math.min(minY, v.y); maxY = Math.max(maxY, v.y);
                    minZ = Math.min(minZ, v.z); maxZ = Math.max(maxZ, v.z);
                    any = true;
                }
            }
        }
        return any ? new AABB(minX, minY, minZ, maxX, maxY, maxZ) : new AABB(0, 0, 0, 0, 0, 0);
    }
}
