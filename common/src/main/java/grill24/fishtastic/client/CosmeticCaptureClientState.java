package grill24.fishtastic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import grill24.fishtastic.client.renderer.FishtasticRenderTypes;
import grill24.fishtastic.command.CosmeticCaptureSession;
import grill24.fishtastic.network.CosmeticCaptureSyncPacket;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FastColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side mirror of the local player's {@link CosmeticCaptureSession}, updated by
 * {@link CosmeticCaptureSyncPacket}, and the world-space preview of the current selection.
 *
 * <p><b>PORT-ONLY.</b> 26.1 draws the boxes as per-tick {@code Gizmos} cuboids, collected during
 * the client tick; 1.21.1 has no gizmo system, so they're drawn from the loaders' world-render
 * hooks instead — Fabric {@code WorldRenderEvents.AFTER_ENTITIES}, NeoForge
 * {@code RenderLevelStageEvent} at {@code Stage.AFTER_ENTITIES} — with the vanilla debug
 * primitives 26.1's gizmos stand in for: {@link LevelRenderer#renderLineBox} for the strokes (a
 * 2 px line, {@link FishtasticRenderTypes#GIZMO_LINES}) and
 * {@link DebugRenderer#renderFilledBox} for the anchor's translucent volume. Both hooks hand over
 * the world-space pose stack vanilla renders entities with, so the draw is translated by
 * {@code -camera} and the boxes are given in world coordinates.
 */
public final class CosmeticCaptureClientState {

    private static final int CORNER_COLOR = FastColor.ARGB32.color(255, 255, 215, 0);
    private static final int ANCHOR_COLOR = FastColor.ARGB32.color(255, 255, 0, 255);
    private static final int ANCHOR_FILL_COLOR = FastColor.ARGB32.color(80, 255, 0, 255);

    private static boolean active = false;
    private static CosmeticCaptureSession.Mode mode = CosmeticCaptureSession.Mode.CORNER_1;
    @Nullable private static BlockPos corner1;
    @Nullable private static BlockPos corner2;
    @Nullable private static BlockPos anchor;

    private CosmeticCaptureClientState() {
    }

    public static void apply(CosmeticCaptureSyncPacket packet) {
        active = packet.active();
        mode = packet.mode();
        corner1 = packet.corner1().orElse(null);
        corner2 = packet.corner2().orElse(null);
        anchor = packet.anchor().orElse(null);
    }

    public static void reset() {
        active = false;
        corner1 = null;
        corner2 = null;
        anchor = null;
    }

    /**
     * The selection preview: the corner box once a first corner is placed, and the anchor's box
     * (stroked over a translucent fill) while an anchor is held. Called from the loaders'
     * world-render hooks with the world-space pose stack and the level's buffer source, and the
     * partial-tick camera used for the pass.
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, Vec3 camera) {
        if (!active) {
            return;
        }

        if (corner1 != null) {
            stroke(pose, buffers, camera, corner2 != null ? boxOf(corner1, corner2) : boxOf(corner1, corner1),
                    CORNER_COLOR);
        }

        if (anchor != null) {
            AABB box = boxOf(anchor, anchor);
            fill(pose, buffers, camera, box, ANCHOR_FILL_COLOR);
            stroke(pose, buffers, camera, box, ANCHOR_COLOR);
        }
    }

    private static void stroke(PoseStack pose, MultiBufferSource buffers, Vec3 camera, AABB box, int color) {
        VertexConsumer consumer = buffers.getBuffer(FishtasticRenderTypes.GIZMO_LINES);
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        LevelRenderer.renderLineBox(pose, consumer, box, red(color), green(color), blue(color), alpha(color));
        pose.popPose();
    }

    private static void fill(PoseStack pose, MultiBufferSource buffers, Vec3 camera, AABB box, int color) {
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        DebugRenderer.renderFilledBox(pose, buffers, box, red(color), green(color), blue(color), alpha(color));
        pose.popPose();
    }

    private static float alpha(int color) {
        return FastColor.ARGB32.alpha(color) / 255.0F;
    }

    private static float red(int color) {
        return FastColor.ARGB32.red(color) / 255.0F;
    }

    private static float green(int color) {
        return FastColor.ARGB32.green(color) / 255.0F;
    }

    private static float blue(int color) {
        return FastColor.ARGB32.blue(color) / 255.0F;
    }

    /** The box covering both corners' blocks, inclusive (26.1's {@code Gizmos.cuboid(BlockPos, BlockPos)}). */
    private static AABB boxOf(BlockPos a, BlockPos b) {
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
    }
}
