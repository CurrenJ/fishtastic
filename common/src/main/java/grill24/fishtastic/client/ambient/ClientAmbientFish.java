package grill24.fishtastic.client.ambient;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.VoxelDomain;
import grill24.fishtastic.client.renderer.FishVisuals;
import grill24.fishtastic.client.renderer.GroupFishPose;
import grill24.fishtastic.env.DevEnvironmentCheck;
import grill24.fishtastic.util.Ids;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.Arrays;
import java.util.Random;

/** Phase 1 development fixture only. No discovery, catches, networking or release population. */
public final class ClientAmbientFish {
    private ClientAmbientFish() {}
    public static final String[] SPECIES = {"bluegill", "acute_iaspis", "portuguese_man_o_war", "plaice"};
    public static final float[] SIZE_CM = {30f, 45f, 40f, 35f};
    private static final int SX = 4, SY = 2, SZ = 3;
    private static Fixture fixture;
    public static int worldSubmitsThisFrame;

    /** Explicit opt-in from the dev harness. Origin is the minimum water cell, never the player. */
    public static boolean startFixture(BlockPos min) {
        Minecraft mc = Minecraft.getInstance();
        if (!DevEnvironmentCheck.isDevelopmentEnvironment() || mc.level == null) return false;
        if (!waterPresent(mc.level, min)) return false;
        fixture = new Fixture(mc.level, min.immutable());
        // A species that failed its locomotion gate must not silently become a hover specimen.
        for (int i = 0; i < fixture.engine.count(); i++) {
            if (fixture.engine.locomotion[i] != FishVisuals.locomotionOf(fixture.anims[i])) {
                clear();
                return false;
            }
        }
        return true;
    }

    public static void clear() { fixture = null; worldSubmitsThisFrame = 0; }
    /** Resource/model reload discards deferred states; the harness must explicitly re-arm. */
    public static void invalidate() { clear(); }
    public static int count() { return fixture == null ? 0 : fixture.ids.length; }
    public static long steps() { return fixture == null ? 0 : fixture.steps; }
    public static BlockPos origin() { return fixture == null ? null : fixture.min; }
    public static long identity(int i) { return fixture.ids[i]; }

    /** Read-only development camera target; interpolation never advances the engine. */
    public record FixturePose(Vec3 position, float yaw) {}
    public static FixturePose fixturePose(int i, float partialTick) {
        Fixture f = fixture;
        if (f == null || i < 0 || i >= f.ids.length) return null;
        f.engine.interpolate(partialTick);
        return new FixturePose(f.center.add(f.engine.renderX[i],
                f.engine.renderY[i] + grill24.fishtastic.client.renderer.FishAnimator.floorPoseLift(f.anims[i], f.engine.lengths[i]),
                f.engine.renderZ[i]), f.engine.renderYaw[i]);
    }

    /** Called even with no world, so level changes/menus cannot retain old world state. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        Fixture f = fixture;
        if (f == null) return;
        if (mc.level != f.level || !waterPresent(f.level, f.min)) { clear(); return; }
        if (mc.isPaused() || !f.level.tickRateManager().runsNormally()) return;
        long time = f.level.getGameTime();
        if (f.lastTick == time) return;
        f.lastTick = time;
        if (mc.player != null) {
            Vec3 eye = mc.player.getEyePosition();
            f.engine.setWatcher(true, (float)(eye.x - f.center.x), (float)(eye.y - f.center.y),
                    (float)(eye.z - f.center.z));
        } else f.engine.setWatcher(false, 0f, 0f, 0f);
        f.engine.step();
        f.steps++;
    }

    /** Uses ordinary world depth/fog pipelines. Rendering only changes interpolation scratch. */
    public static void submitWorld(PoseStack poses, SubmitNodeCollector nodes, float partialTick) {
        worldSubmitsThisFrame = 0;
        Minecraft mc = Minecraft.getInstance();
        Fixture f = fixture;
        if (f == null || mc.level != f.level) return;
        var camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = camera.position();
        var forward = camera.forwardVector();
        f.engine.interpolate(partialTick);
        // Camera-space depth, rather than the tank engine's world-Z ordering. Never alter engine.order.
        for (int i = 0; i < f.ids.length; i++) {
            f.order[i] = i;
            f.depth[i] = (f.center.x + f.engine.renderX[i] - cam.x) * forward.x()
                    + (f.center.y + f.engine.renderY[i] - cam.y) * forward.y()
                    + (f.center.z + f.engine.renderZ[i] - cam.z) * forward.z();
        }
        for (int k = 1; k < f.order.length; k++) {
            int i = f.order[k], at = k;
            while (at > 0 && f.depth[f.order[at - 1]] < f.depth[i]) {
                f.order[at] = f.order[at - 1]; at--;
            }
            f.order[at] = i;
        }
        float t = f.level.getGameTime() + partialTick;
        for (int i : f.order) {
            double x = f.center.x + f.engine.renderX[i], y = f.center.y + f.engine.renderY[i],
                    z = f.center.z + f.engine.renderZ[i];
            // Conservative sphere encloses the complete sprite, pose pivots and bob, including floor lift.
            double radius = Math.max(1.0, f.engine.lengths[i] * 2.0);
            if (!camera.getCullFrustum().isVisible(new AABB(x-radius,y-radius,z-radius,x+radius,y+radius,z+radius))) continue;
            poses.pushPose();
            poses.translate(f.center.x-cam.x, f.center.y-cam.y, f.center.z-cam.z);
            GroupFishPose.apply(poses, f.engine, f.anims[i], i, t, 0f, 0f, 0f, f.random);
            // Each fish owns a state because collector submission is deferred until later in the frame.
            mc.getItemModelResolver().updateForTopItem(f.states[i], f.stacks[i], ItemDisplayContext.FIXED, null, null, 0);
            int light = LevelRenderer.getLightCoords(f.level, BlockPos.containing(x,y,z));
            f.states[i].submit(poses, nodes, light, OverlayTexture.NO_OVERLAY, 0);
            poses.popPose();
            worldSubmitsThisFrame++;
        }
    }

    private static boolean waterPresent(ClientLevel level, BlockPos min) {
        for (int x = 0; x < SX; x++) for (int y = 0; y < SY; y++) for (int z = 0; z < SZ; z++) {
            BlockPos pos = min.offset(x,y,z);
            if (!level.hasChunkAt(pos) || !level.getFluidState(pos).is(FluidTags.WATER)
                    || !level.getFluidState(pos).isSource() || !level.getBlockState(pos).getCollisionShape(level,pos).isEmpty()) return false;
        }
        return true;
    }

    private static final class Fixture {
        final ClientLevel level;
        final BlockPos min;
        final Vec3 center;
        final FlockEngine engine = new FlockEngine(Tunables.GROUP);
        final long[] ids = {1L, 2L, 3L, 4L};
        final ItemStack[] stacks = new ItemStack[4];
        final grill24.fishtastic.data.FishAnimationConfig[] anims = new grill24.fishtastic.data.FishAnimationConfig[4];
        final ItemStackRenderState[] states = new ItemStackRenderState[4];
        final int[] order = new int[4];
        final double[] depth = new double[4];
        final Random random = new Random();
        long lastTick = Long.MIN_VALUE, steps;
        Fixture(ClientLevel level, BlockPos min) {
            this.level = level; this.min = min;
            center = new Vec3(min.getX()+SX/2.0, min.getY()+SY/2.0, min.getZ()+SZ/2.0);
            boolean[][][] cells = new boolean[SX][SY][SZ];
            for (boolean[][] plane : cells) for (boolean[] row : plane) Arrays.fill(row, true);
            FishSpec[] specs = new FishSpec[4];
            for (int i = 0; i < stacks.length; i++) {
                stacks[i] = new ItemStack(BuiltInRegistries.ITEM.getValue(Ids.of("fishtastic", SPECIES[i])));
                ItemSizeHelper.setSize(stacks[i], SIZE_CM[i]);
                anims[i] = FishVisuals.resolve(stacks[i], level).animation();
                specs[i] = FishVisuals.descriptor(stacks[i], level, false);
                states[i] = new ItemStackRenderState();
            }
            // No size clamping: authored representative sizes, seed 42, group yaw/rotation jitter.
            engine.rebuildPreserving(specs, new int[]{-1,-1,-1,-1}, 42, 0f, 20f, new VoxelDomain(cells));
        }
    }
}
