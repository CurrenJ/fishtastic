package grill24.fishtastic.client.selftest;

import grill24.fishtastic.client.ambient.ClientAmbientFish;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.VoxelDomain;
import grill24.fishtastic.client.renderer.FishVisuals;
import grill24.fishtastic.client.renderer.GroupFishPose;
import grill24.fishtastic.util.Ids;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
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

/** Fixed four-species parity reference. Excluded with the rest of the self-test harness. */
final class AmbientParityFixture implements ClientAmbientFish.DevelopmentView {
    static final String[] SPECIES = {"bluegill", "acute_iaspis", "portuguese_man_o_war", "plaice"};
    static final float[] SIZE_CM = {30f, 45f, 40f, 35f};
    private static final int SX = 4, SY = 2, SZ = 3;
    private final State state;

    private AmbientParityFixture(State state) { this.state = state; }

    static AmbientParityFixture create(BlockPos min) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !waterPresent(mc.level, min)) return null;
        State state = new State(mc.level, min.immutable());
        for (int i = 0; i < state.engine.count(); i++) {
            if (state.engine.locomotion[i] != FishVisuals.locomotionOf(state.anims[i])) return null;
        }
        return new AmbientParityFixture(state);
    }

    int count() { return state.ids.length; }
    long steps() { return state.steps; }
    BlockPos origin() { return state.min; }
    long identity(int i) { return state.ids[i]; }

    record Pose(Vec3 position, float yaw) {}
    Pose fixturePose(int i, float partialTick) {
        State f = state;
        if (i < 0 || i >= f.ids.length) return null;
        f.engine.interpolate(partialTick);
        return new Pose(f.center.add(f.engine.renderX[i],
                f.engine.renderY[i] + grill24.fishtastic.client.renderer.FishAnimator.floorPoseLift(f.anims[i], f.engine.lengths[i]),
                f.engine.renderZ[i]), f.engine.renderYaw[i]);
    }

    @Override
    public boolean tick(Minecraft mc) {
        State f = state;
        if (mc.level != f.level || !waterPresent(f.level, f.min)) return false;
        if (mc.isPaused()) return true;
        long time = f.level.getGameTime();
        if (f.lastTick == time) return true;
        f.lastTick = time;
        if (mc.player != null) {
            Vec3 eye = mc.player.getEyePosition();
            f.engine.setWatcher(true, (float)(eye.x - f.center.x), (float)(eye.y - f.center.y),
                    (float)(eye.z - f.center.z));
        } else f.engine.setWatcher(false, 0f, 0f, 0f);
        f.engine.step();
        f.steps++;
        return true;
    }

    @Override
    public int submitWorld(PoseStack poses, MultiBufferSource buffers, Frustum frustum, float partialTick) {
        State f = state;
        int worldSubmitsThisFrame = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != f.level) return 0;
        var camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = camera.getPosition();
        var forward = camera.getLookVector();
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
            if (!frustum.isVisible(new AABB(x-radius,y-radius,z-radius,x+radius,y+radius,z+radius))) continue;
            poses.pushPose();
            poses.translate(f.center.x-cam.x, f.center.y-cam.y, f.center.z-cam.z);
            GroupFishPose.apply(poses, f.engine, f.anims[i], i, t, 0f, 0f, 0f, f.random);
            int light = LevelRenderer.getLightColor(f.level, BlockPos.containing(x,y,z));
            mc.getItemRenderer().renderStatic(f.stacks[i], ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                    poses, buffers, f.level, 0);
            poses.popPose();
            worldSubmitsThisFrame++;
        }

        return worldSubmitsThisFrame;
    }

    private static boolean waterPresent(ClientLevel level, BlockPos min) {
        for (int x = 0; x < SX; x++) for (int y = 0; y < SY; y++) for (int z = 0; z < SZ; z++) {
            BlockPos pos = min.offset(x,y,z);
            if (!level.hasChunkAt(pos) || !level.getFluidState(pos).is(FluidTags.WATER)
                    || !level.getFluidState(pos).isSource() || !level.getBlockState(pos).getCollisionShape(level,pos).isEmpty()) return false;
        }
        return true;
    }

    private static final class State {
        final ClientLevel level;
        final BlockPos min;
        final Vec3 center;
        final FlockEngine engine = new FlockEngine(Tunables.GROUP);
        final long[] ids = {1L, 2L, 3L, 4L};
        final ItemStack[] stacks = new ItemStack[4];
        final grill24.fishtastic.data.FishAnimationConfig[] anims = new grill24.fishtastic.data.FishAnimationConfig[4];
        final int[] order = new int[4];
        final double[] depth = new double[4];
        final Random random = new Random();
        long lastTick = Long.MIN_VALUE, steps;
        State(ClientLevel level, BlockPos min) {
            this.level = level; this.min = min;
            center = new Vec3(min.getX()+SX/2.0, min.getY()+SY/2.0, min.getZ()+SZ/2.0);
            boolean[][][] cells = new boolean[SX][SY][SZ];
            for (boolean[][] plane : cells) for (boolean[] row : plane) Arrays.fill(row, true);
            FishSpec[] specs = new FishSpec[4];
            for (int i = 0; i < stacks.length; i++) {
                stacks[i] = new ItemStack(BuiltInRegistries.ITEM.get(Ids.of("fishtastic", SPECIES[i])));
                ItemSizeHelper.setSize(stacks[i], SIZE_CM[i]);
                anims[i] = FishVisuals.resolve(stacks[i], level).animation();
                specs[i] = FishVisuals.descriptor(stacks[i], level, false);
            }
            // No size clamping: authored representative sizes, seed 42, group yaw/rotation jitter.
            engine.rebuildPreserving(specs, new int[]{-1,-1,-1,-1}, 42, 0f, 20f, new VoxelDomain(cells));
        }
    }
}
