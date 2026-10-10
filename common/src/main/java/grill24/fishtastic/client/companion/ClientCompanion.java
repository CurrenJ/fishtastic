package grill24.fishtastic.client.companion;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishtastic.client.renderer.CompanionFlock;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3fc;

/**
 * One companion fish on the client — a player's equipped companion, drawn for its owner and for
 * everyone who can see them (docs/fish-companions.md §3, §5.1). {@link ClientCompanions} owns the
 * instances; this class is the per-fish state the Phase 0 spike proved out.
 *
 * <p>Geometry: the fish lives in a small invisible tank ({@link CompanionFlock}) whose centre and
 * scale move. Following its owner it is a full-size box ahead of the eyes. Only the local player's
 * companion docks: it blends both, over {@link #DOCK_TICKS}, toward a point {@link #DOCK_DEPTH}
 * blocks in front of the camera that projects onto the minigame's dock anchor, and toward a
 * miniature scale sized so the box spans the dock lane on screen. Once fully docked the box is
 * camera-locked and the HUD pass draws it instead.
 */
public final class ClientCompanion {
    private static final int DOCK_TICKS = 20;
    static final float DOCK_DEPTH = 2.0f;
    /** Follow box centre relative to the eye: this far ahead (horizontally) and this far down. */
    private static final double FOLLOW_AHEAD = 1.1, FOLLOW_DROP = 0.15;
    /** Smallest length a companion is drawn at while following, blocks — true-to-scale small fish are specks. */
    private static final float MIN_FOLLOW_LENGTH = 0.25f;
    /** Docked fish length in engine units, whatever its true size (the dock tank is 0.7 wide). */
    private static final float DOCK_FISH_LENGTH = 0.3f;

    /** The owner, or null for a dev phantom parked at {@link #phantomEye}. */
    @Nullable private final Player owner;
    @Nullable private final Vec3 phantomEye;
    private final CompanionFlock flock = new CompanionFlock();
    private final ItemStack stack;

    @Nullable private Vec3 center, prevCenter;
    private float dock, prevDock;

    // Per-frame geometry, written by the world pass (it runs first) and read by the HUD pass.
    private Vec3 frameCenter = Vec3.ZERO;
    private float frameScale = 1f;
    private float frameFishSize = 1f;
    private float frameYaw;
    /**
     * How far (engine units) the fish was moved to fit the dock box when it swapped in. Added back
     * at render time and faded out over the swim to the dock, so the clamp never shows as a jump.
     */
    private Vec3 clampBias = Vec3.ZERO;
    private float frameBias = 0f;
    private boolean frameDocked;

    ClientCompanion(@Nullable Player owner, @Nullable Vec3 phantomEye, ItemStack stack, Level level) {
        this.owner = owner;
        this.phantomEye = phantomEye;
        this.stack = stack.copy();
        flock.rebuild(stack, level);
    }

    ItemStack stack() { return stack; }

    @Nullable Player owner() { return owner; }

    boolean isDocked() { return frameDocked; }

    float dockProgress() { return dock; }

    /**
     * Client tick (20 Hz). {@code anchor} is the minigame's dock anchor when this is the local
     * player's companion and a minigame is up, otherwise null.
     */
    void tick(@Nullable float[] anchor) {
        Vec3 follow = followPoint();
        if (center == null) center = prevCenter = follow;
        prevCenter = center;
        center = center.lerp(follow, 0.35);

        prevDock = dock;
        float dir = anchor != null ? 1f : -1f;
        dock = Mth.clamp(dock + dir / DOCK_TICKS, 0f, 1f);
        // Thin dock box as soon as the swim to the dock starts, back to the follow box once home.
        if (anchor != null) {
            Vec3 moved = flock.setDocked(true);
            if (moved.lengthSqr() > 0) clampBias = moved;
        } else if (dock <= 0f) {
            flock.setDocked(false);
        }

        flock.step();
    }

    private Vec3 followPoint() {
        if (owner == null) return phantomEye;
        Vec3 look = owner.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1e-6 ? Vec3.ZERO : flat.normalize();
        return owner.getEyePosition().add(flat.scale(FOLLOW_AHEAD)).add(0, -FOLLOW_DROP, 0);
    }

    // ---------------------------------------------------------------- world pass

    /** Called from the level submit hook; the stack is camera-relative. */
    void submitWorld(PoseStack poseStack, SubmitNodeCollector nodes, Camera camera, float partialTick,
                     @Nullable float[] anchor, int screenW, int screenH, boolean pipCompare) {
        if (center == null || flock.isEmpty()) return;
        computeFrame(camera, partialTick, anchor, screenW, screenH);
        flock.interpolate(partialTick);
        if (frameDocked && !pipCompare) return; // the HUD pass draws it now

        Vec3 cam = camera.position();
        poseStack.pushPose();
        poseStack.mulPose(boxPose(cam));
        Vec3 off = fishOffset();
        poseStack.translate(off.x, off.y, off.z);
        flock.submit(poseStack, nodes, lightAt(off), gameTime(partialTick), frameFishSize);
        poseStack.popPose();
    }

    /**
     * Camera-relative, world-oriented transform of the box: its centre, turned to a yaw, then its
     * scale. The local player's box turns with the camera (so the dock box stays side-on and the
     * follow box keeps its place relative to the view); anyone else's turns with its owner.
     */
    Matrix4f boxPose(Vec3 cam) {
        return new Matrix4f()
                .translate((float) (frameCenter.x - cam.x), (float) (frameCenter.y - cam.y), (float) (frameCenter.z - cam.z))
                .rotateY((float) Math.toRadians(-frameYaw))
                .scale(frameScale);
    }

    private void computeFrame(Camera camera, float partialTick, @Nullable float[] anchor, int screenW, int screenH) {
        Vec3 follow = prevCenter.lerp(center, partialTick);
        float d = smooth(Mth.lerp(partialTick, prevDock, dock));
        frameYaw = owner == null ? 0f
                : owner == Minecraft.getInstance().player ? camera.yRot() : owner.getViewYRot(partialTick);
        float length = Math.max(flock.length(), 1e-3f);
        float followSize = Math.max(1f, MIN_FOLLOW_LENGTH / length);
        if (d <= 0f || anchor == null) {
            frameBias = 0f;
            frameCenter = follow;
            frameScale = 1f;
            frameFishSize = followSize;
            frameDocked = false;
            return;
        }
        frameFishSize = Mth.lerp(d, followSize, DOCK_FISH_LENGTH / length);
        frameBias = 1f - d;
        CameraRenderState cs = Minecraft.getInstance().gameRenderer.getGameRenderState().levelRenderState.cameraRenderState;
        Vec3 dockPoint = unproject(camera, cs.projectionMatrix, anchor[0], anchor[1], screenW, screenH);
        frameCenter = follow.lerp(dockPoint, d);
        frameScale = Mth.lerp(d, 1f, dockScale(cs.projectionMatrix, anchor[2], screenH));
        frameDocked = d >= 1f;
    }

    /** Miniature scale at which the box's half-width covers {@code halfSizePx} of the screen at the dock depth. */
    private static float dockScale(Matrix4f projection, float halfSizePx, int screenH) {
        float screenFraction = halfSizePx / (screenH / 2f);         // of the half-height
        float worldHalfHeight = DOCK_DEPTH / projection.m11();      // depth * tan(fovY/2)
        return screenFraction * worldHalfHeight / CompanionFlock.DOCK_HALF_EXTENT;
    }

    /** The world point {@link #DOCK_DEPTH} blocks ahead of the camera that projects onto GUI pixel (x, y). */
    private static Vec3 unproject(Camera camera, Matrix4f projection, float guiX, float guiY, int screenW, int screenH) {
        float ndcX = 2f * guiX / screenW - 1f;
        float ndcY = 1f - 2f * guiY / screenH;
        float right = ndcX * DOCK_DEPTH / projection.m00();
        float up = ndcY * DOCK_DEPTH / projection.m11();
        Vector3fc fwd = camera.forwardVector(), upV = camera.upVector(), left = camera.leftVector();
        Vec3 cam = camera.position();
        return new Vec3(
                cam.x + fwd.x() * DOCK_DEPTH + upV.x() * up - left.x() * right,
                cam.y + fwd.y() * DOCK_DEPTH + upV.y() * up - left.y() * right,
                cam.z + fwd.z() * DOCK_DEPTH + upV.z() * up - left.z() * right);
    }

    /** Called from {@link CompanionPipRenderer} inside the docked fish's view-space pose. */
    void submitDockedFish(PoseStack poseStack, SubmitNodeCollector nodes, int light, float gameTime) {
        Vec3 off = fishOffset();
        poseStack.translate(off.x, off.y, off.z);
        flock.submit(poseStack, nodes, light, gameTime, frameFishSize);
    }

    // ---------------------------------------------------------------- helpers

    /** The fish's offset in the box this frame, with the dock clamp's fading bias. */
    Vec3 fishOffset() {
        return flock.localOffset().add(clampBias.scale(frameBias));
    }

    int lightAt(Vec3 localOffset) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return LightCoordsUtil.FULL_BRIGHT;
        return LevelRenderer.getLightCoords(mc.level, BlockPos.containing(frameCenter.add(localOffset.scale(frameScale))));
    }

    static float gameTime(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0f : mc.level.getGameTime() + partialTick;
    }

    private static float smooth(float t) {
        return t * t * (3f - 2f * t);
    }

    /** Dev diagnostics: where the box and fish are relative to the camera, and the dock state. */
    String debugState(@Nullable float[] anchor, int screenW, int screenH) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 rel = frameCenter.subtract(camera.position());
        CameraRenderState cs = mc.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState;
        Vec3 off = fishOffset();
        org.joml.Vector4f p = new org.joml.Vector4f((float) off.x, (float) off.y, (float) off.z, 1f);
        boxPose(camera.position()).transform(p);
        new Matrix4f(cs.viewRotationMatrix).transform(p);
        float viewZ = p.z;
        new Matrix4f(cs.projectionMatrix).transform(p);
        float gx = (p.x / p.w + 1f) / 2f * screenW, gy = (1f - p.y / p.w) / 2f * screenH;
        return String.format(java.util.Locale.ROOT,
                "owner=%s stack=%s docked=%s dock=%.2f anchor=%s centreRel=(%.2f,%.2f,%.2f) scale=%.3f fishSize=%.2f fishGui=(%.1f,%.1f) viewZ=%.2f | %s",
                owner == null ? "phantom" : owner.getName().getString(), stack.getHoverName().getString(),
                frameDocked, dock, anchor == null ? "null" : java.util.Arrays.toString(anchor),
                rel.x, rel.y, rel.z, frameScale, frameFishSize, gx, gy, viewZ, flock.debug());
    }
}
