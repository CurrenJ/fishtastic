package grill24.fishtastic.client.companion;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishtastic.client.renderer.CompanionFlock;
import grill24.fishtastic.util.FishingMinigameAnimation;
import grill24.fishtastic.util.IGameRendererExtension;
import grill24.fishtastic.util.IGuiGraphicsExtension;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3fc;

/**
 * Phase 0 spike for fish companions (docs/fish-companions.md §5.1, §12). Client-only, dev-command
 * driven, one hard-coded companion for the local player. It exists to answer three questions:
 *
 * <ol>
 *   <li>Can a free-flying fish be drawn from a world-render hook — visible to its own owner in first
 *       person, which a player render layer never is?</li>
 *   <li>Can the docked fish be drawn as a real perspective 3D model <i>on top of</i> the minigame HUD
 *       (a perspective picture-in-picture renderer), and can the switch from the world pass into it
 *       be made invisible?</li>
 *   <li>Can the tank simulation drive it in the open world, unchanged?</li>
 * </ol>
 *
 * <p>Geometry: the fish lives in a small invisible tank ({@link CompanionFlock}) whose centre and
 * scale move. Following the player it is a full-size box around the head. Docking blends both, over
 * {@link #DOCK_TICKS}, toward a point {@link #DOCK_DEPTH} blocks in front of the camera that projects
 * onto the minigame's dock anchor, and toward a miniature scale sized so the box spans the dock lane
 * on screen. Once fully docked the box is camera-locked and the HUD pass draws it instead.
 */
public final class CompanionSpike {
    private static final int DOCK_TICKS = 20;
    private static final float DOCK_DEPTH = 2.0f;
    /** Follow box centre relative to the eye: this far ahead (horizontally) and this far down. */
    private static final double FOLLOW_AHEAD = 1.1, FOLLOW_DROP = 0.15;
    /** Smallest length a companion is drawn at while following, blocks — true-to-scale small fish are specks. */
    private static final float MIN_FOLLOW_LENGTH = 0.25f;
    /** Docked fish length in engine units, whatever its true size (the dock tank is 0.7 wide). */
    private static final float DOCK_FISH_LENGTH = 0.3f;

    private static boolean enabled;
    private static final CompanionFlock flock = new CompanionFlock();

    @Nullable private static Vec3 center, prevCenter;
    private static float dock, prevDock;

    /** The minigame's dock anchor from the latest HUD frame, GUI px {x, y, halfSize}; null when there is none. */
    @Nullable private static float[] anchor;
    private static int anchorScreenW, anchorScreenH;

    // Per-frame geometry, written by the world pass (it runs first) and read by the HUD pass.
    private static Vec3 frameCenter = Vec3.ZERO;
    private static float frameScale = 1f;
    private static float frameFishSize = 1f;
    private static float frameYaw;
    /**
     * How far (engine units) the fish was moved to fit the dock box when it swapped in. Added back
     * at render time and faded out over the swim to the dock, so the clamp never shows as a jump.
     */
    private static Vec3 clampBias = Vec3.ZERO;
    private static float frameBias = 0f;
    private static boolean frameDocked;

    private CompanionSpike() {}

    public static boolean isEnabled() { return enabled; }

    public static void enable(ItemStack fish) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        flock.rebuild(fish, mc.level);
        center = prevCenter = null;
        dock = prevDock = 0f;
        enabled = true;
    }

    public static void disable() {
        enabled = false;
        anchor = null;
    }

    public static ItemStack stack() { return flock.stack(); }

    public static boolean isDocked() { return enabled && frameDocked; }

    /** Where the fish lands on screen (GUI px) through the full-screen camera projection. */
    private static String projectedFish() {
        Minecraft mc = Minecraft.getInstance();
        CameraRenderState cs = mc.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState;
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        Vec3 off = fishOffset();
        org.joml.Vector4f p = new org.joml.Vector4f((float) off.x, (float) off.y, (float) off.z, 1f);
        boxPose(cam).transform(p);
        new Matrix4f(cs.viewRotationMatrix).transform(p);
        float viewZ = p.z;
        new Matrix4f(cs.projectionMatrix).transform(p);
        float gx = (p.x / p.w + 1f) / 2f * anchorScreenW, gy = (1f - p.y / p.w) / 2f * anchorScreenH;
        return String.format(java.util.Locale.ROOT, "fishGui=(%.1f,%.1f) viewZ=%.2f camYRot=%.1f pipSubmits=%d pipRenders=%d",
                gx, gy, viewZ, mc.gameRenderer.getMainCamera().yRot(), pipSubmits, pipRenders);
    }

    /** Spike diagnostics: where the box and fish are relative to the camera, and the dock state. */
    public static String debugState() {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 rel = frameCenter.subtract(camera.position());
        Vector3fc f = camera.forwardVector();
        return String.format(java.util.Locale.ROOT,
                "docked=%s dock=%.2f anchor=%s yaw=%.1f fwd=(%.2f,%.2f,%.2f) centreRel=(%.2f,%.2f,%.2f) along=%.2f scale=%.3f fishSize=%.2f off=%s",
                frameDocked, dock, anchor == null ? "null" : java.util.Arrays.toString(anchor), camera.yRot(),
                f.x(), f.y(), f.z(), rel.x, rel.y, rel.z, rel.x * f.x() + rel.y * f.y() + rel.z * f.z(),
                frameScale, frameFishSize, flock.localOffset()) + " | " + flock.debug() + " | " + projectedFish();
    }

    /** Client tick (20 Hz). */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled || mc.player == null) return;
        Vec3 look = mc.player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1e-6 ? Vec3.ZERO : flat.normalize();
        Vec3 follow = mc.player.getEyePosition().add(flat.scale(FOLLOW_AHEAD)).add(0, -FOLLOW_DROP, 0);
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

    // ---------------------------------------------------------------- world pass

    /** Called from the platform's level submit hook; the stack is camera-relative. */
    public static void submitWorld(PoseStack poseStack, SubmitNodeCollector nodes, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled || center == null || flock.isEmpty() || mc.level == null) return;
        Camera camera = mc.gameRenderer.getMainCamera();
        computeFrame(camera, partialTick);
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
     * Camera-relative, world-oriented transform of the box: its centre, turned to the camera's yaw
     * (so the box's depth axis always runs along the view — the dock box stays side-on, and the
     * follow box keeps its place relative to where the player looks), then its scale.
     */
    private static Matrix4f boxPose(Vec3 cam) {
        return new Matrix4f()
                .translate((float) (frameCenter.x - cam.x), (float) (frameCenter.y - cam.y), (float) (frameCenter.z - cam.z))
                .rotateY((float) Math.toRadians(-frameYaw))
                .scale(frameScale);
    }

    private static void computeFrame(Camera camera, float partialTick) {
        Vec3 follow = prevCenter.lerp(center, partialTick);
        float d = smooth(Mth.lerp(partialTick, prevDock, dock));
        float[] a = anchor;
        frameYaw = camera.yRot();
        float length = Math.max(flock.length(), 1e-3f);
        float followSize = Math.max(1f, MIN_FOLLOW_LENGTH / length);
        if (d <= 0f || a == null) {
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
        Vec3 dockPoint = unproject(camera, cs.projectionMatrix, a[0], a[1], DOCK_DEPTH);
        frameCenter = follow.lerp(dockPoint, d);
        frameScale = Mth.lerp(d, 1f, dockScale(cs.projectionMatrix, a[2]));
        frameDocked = d >= 1f;
    }

    /** Miniature scale at which the box's half-width covers {@code halfSizePx} of the screen at the dock depth. */
    private static float dockScale(Matrix4f projection, float halfSizePx) {
        float screenFraction = halfSizePx / (anchorScreenH / 2f);   // of the half-height
        float worldHalfHeight = DOCK_DEPTH / projection.m11();      // depth * tan(fovY/2)
        return screenFraction * worldHalfHeight / CompanionFlock.DOCK_HALF_EXTENT;
    }

    /** The world point {@code depth} blocks ahead of the camera that projects onto GUI pixel (x, y). */
    private static Vec3 unproject(Camera camera, Matrix4f projection, float guiX, float guiY, float depth) {
        float ndcX = 2f * guiX / anchorScreenW - 1f;
        float ndcY = 1f - 2f * guiY / anchorScreenH;
        float right = ndcX * depth / projection.m00();
        float up = ndcY * depth / projection.m11();
        Vector3fc fwd = camera.forwardVector(), upV = camera.upVector(), left = camera.leftVector();
        Vec3 cam = camera.position();
        return new Vec3(
                cam.x + fwd.x() * depth + upV.x() * up - left.x() * right,
                cam.y + fwd.y() * depth + upV.y() * up - left.y() * right,
                cam.z + fwd.z() * depth + upV.z() * up - left.z() * right);
    }

    // ---------------------------------------------------------------- HUD pass

    /** Called every HUD frame after the minigame draws, with the live minigame (or null). */
    public static void renderHud(GuiGraphicsExtractor graphics, @Nullable FishingMinigameAnimation minigame, float partialTick) {
        if (!enabled) { anchor = null; return; }
        anchorScreenW = graphics.guiWidth();
        anchorScreenH = graphics.guiHeight();
        anchor = minigame != null ? minigame.companionDockAnchor(anchorScreenW, anchorScreenH) : null;
        if (!frameDocked || anchor == null) return;

        Minecraft mc = Minecraft.getInstance();
        CameraRenderState cs = mc.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState;
        float pad = anchor[2] * 1.5f;
        int x0 = Mth.floor(anchor[0] - pad), x1 = Mth.ceil(anchor[0] + pad);
        int y0 = Mth.floor(anchor[1] - pad), y1 = Mth.ceil(anchor[1] + pad);

        // The camera's perspective cropped to [x0,x1]×[y0,y1] — an off-axis frustum, so the fish
        // lands in the texture exactly where the full-screen projection would put it.
        float nx0 = 2f * x0 / anchorScreenW - 1f, nx1 = 2f * x1 / anchorScreenW - 1f;
        float ny0 = 1f - 2f * y1 / anchorScreenH, ny1 = 1f - 2f * y0 / anchorScreenH;
        float cx = (nx0 + nx1) / 2f, hx = (nx1 - nx0) / 2f;
        float cy = (ny0 + ny1) / 2f, hy = (ny1 - ny0) / 2f;
        Matrix4f crop = new Matrix4f().scale(1f / hx, 1f / hy, 1f).translate(-cx, -cy, 0f);
        Matrix4f projection = crop.mul(cs.projectionMatrix).mul(cs.viewRotationMatrix);

        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        pipSubmits++;
        ((IGuiGraphicsExtension) graphics).fishtastic$addPictureInPicture(new CompanionPipRenderState(
                projection, boxPose(cam),
                pipLightOverride >= 0 ? pipLightOverride : lightAt(fishOffset()), gameTime(partialTick), x0, y0, x1, y1,
                pipCompare ? new org.joml.Matrix3x2f().translation(-2f * pad, 0f) : new org.joml.Matrix3x2f()));
    }

    /** Called from {@link CompanionPipRenderer} inside the docked fish's view-space pose. */
    static void submitDockedFish(PoseStack poseStack, SubmitNodeCollector nodes, int light, float gameTime) {
        Vec3 off = fishOffset();
        poseStack.translate(off.x, off.y, off.z);
        flock.submit(poseStack, nodes, light, gameTime, frameFishSize);
    }

    // ---------------------------------------------------------------- helpers

    /** The fish's offset in the box this frame, with the dock clamp's fading bias. */
    private static Vec3 fishOffset() {
        return flock.localOffset().add(clampBias.scale(frameBias));
    }

    /**
     * Spike experiment hook: keep drawing the docked fish in the world as well, and shift the HUD
     * copy one lane-width left, so the two passes can be compared side by side in one frame.
     */
    public static boolean pipCompare;

    /** Spike diagnostics: docked draws queued into the GUI, and how many the renderer actually ran. */
    public static int pipSubmits, pipRenders;

    /** Spike experiment hook: packed light the docked fish uses, or -1 for the world's light. */
    public static int pipLightOverride = -1;

    private static int lightAt(Vec3 localOffset) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return LightCoordsUtil.FULL_BRIGHT;
        return LevelRenderer.getLightCoords(mc.level, BlockPos.containing(frameCenter.add(localOffset.scale(frameScale))));
    }

    private static float gameTime(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0f : mc.level.getGameTime() + partialTick;
    }

    private static float smooth(float t) {
        return t * t * (3f - 2f * t);
    }

    /** True when the local player's active item animation is a running minigame. */
    @Nullable
    public static FishingMinigameAnimation activeMinigame() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null) return null;
        return ((IGameRendererExtension) mc.gameRenderer).fishtastic$getActiveAnimation() instanceof FishingMinigameAnimation m
                && m.isActive() ? m : null;
    }
}
