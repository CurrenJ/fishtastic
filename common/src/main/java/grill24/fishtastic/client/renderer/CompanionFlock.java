package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.VoxelDomain;
import grill24.fishtastic.data.FishAnimationConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Phase 0 spike (docs/fish-companions.md §12): one companion fish run on the tank simulation, in an
 * invisible tank that follows a moving centre instead of sitting on a block.
 *
 * <p>The engine is the group path's voxel-domain engine over a small all-open box, so the fish gets
 * the planar continuous-yaw model, banking and speed-coupled tail beat for free — nothing about how
 * it swims is new. What is new is only the frame: positions come out box-relative, and the box
 * centre is whatever the caller says it is this tick, scaled by the caller so the same
 * box can be shrunk to a miniature when the fish docks next to the minigame bar.
 */
public final class CompanionFlock {
    /** Following the player: an open 3 × 2 × 3 box. */
    private static final VoxelDomain FOLLOW_DOMAIN = openBox(3, 2, 3);
    /** Docked: the single-tank model's lateral half-extent (Tunables.DEFAULT.tankHalfExtent). */
    public static final float DOCK_HALF_EXTENT = 0.35f;
    private static final float DOCK_Y_RANGE = 0.5f;
    private static final float DOCK_SIM_LENGTH = 0.2f;

    private final FlockEngine engine = new FlockEngine(Tunables.GROUP);
    private final ItemStackRenderState renderState = new ItemStackRenderState();
    private final Random random = new Random();
    private ItemStack stack = ItemStack.EMPTY;
    private FishAnimationConfig anim = FishAnimationConfig.HorizontalSwim.DEFAULT;
    private FishSpec spec;
    private boolean docked;

    public void rebuild(ItemStack fish, Level level) {
        stack = fish.copyWithCount(1);
        FishTankBlockEntityRenderer.ResolvedFishRender render = FishTankBlockEntityRenderer.resolveFishRender(stack, level);
        anim = render.animation();
        float length = TankFlockAdapter.renderedLength(stack, render.renderCalibration());
        Locomotion locomotion = TankFlockAdapter.locomotionOf(anim);
        spec = new FishSpec(length, locomotion, false, TankFlockAdapter.speciesId(stack));
        docked = false;
        engine.rebuild(new FishSpec[]{spec}, stack.hashCode(), 0f, 0f, FOLLOW_DOMAIN);
    }

    /**
     * Swaps between the two engine models, keeping the fish where it was. Following, it is the
     * group path's planar model in an open 3 x 2 x 3 box - continuous heading, free to turn any way.
     * Docked, it is the single-tank binary model in a one-block tank: the fish only ever swims left
     * or right, which is exactly what keeps it side-on to the camera next to the minigame bar.
     *
     * <p>The tank's swim gate passes only short fish (2.5 x length must fit its 0.7 run), so the
     * docked engine is given a nominal {@link #DOCK_SIM_LENGTH}; the drawn size is the caller's.
     *
     * @return how far the fish was moved to fit the new box (engine units), for the caller to fade out
     */
    public Vec3 setDocked(boolean dock) {
        if (dock == docked || isEmpty()) return Vec3.ZERO;
        docked = dock;
        float l = engine.posL()[0], y = engine.posY()[0], d = engine.posD()[0];
        if (dock) {
            engine.setTunables(Tunables.DEFAULT);
            engine.rebuild(new FishSpec[]{new FishSpec(Math.min(spec.length(), DOCK_SIM_LENGTH), Locomotion.FREE_SWIM,
                    false, spec.species())}, stack.hashCode(), 0f, 1, DOCK_HALF_EXTENT, DOCK_Y_RANGE, 0f);
        } else {
            engine.setTunables(Tunables.GROUP);
            engine.rebuild(new FishSpec[]{spec}, stack.hashCode(), 0f, 0f, FOLLOW_DOMAIN);
        }
        // Carried over outside a smaller box, the engine's wall constraint (which assumes the fish
        // starts inside) would pin it there for good - clamp it in and report the difference.
        var box = engine.domain();
        float m = 0.03f;
        float cl = clamp(l, box.minLateral() + m, box.maxLateral() - m);
        float cy = clamp(y, box.minVertical() + m, box.maxVertical() - m);
        float cd = dock ? engine.posD()[0] : clamp(d, box.minDepth() + m, box.maxDepth() - m);
        engine.posL()[0] = cl;
        engine.posY()[0] = cy;
        engine.posD()[0] = cd;
        return new Vec3(l - cl, y - cy, d - cd);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Spike diagnostics. */
    public String debug() {
        if (isEmpty()) return "empty";
        return String.format(java.util.Locale.ROOT, "count=%d swimmer=%s loco=%s pos=(%.3f,%.3f,%.3f) vel=(%.3f,%.3f,%.3f) docked=%s",
                engine.count(), engine.swimmers[0], engine.locomotion[0],
                engine.posL()[0], engine.posY()[0], engine.posD()[0],
                engine.velL()[0], engine.velY()[0], engine.velD()[0], docked) + " planar=" + engine.planar();
    }

    /** The fish's rendered length in engine units — the true-to-scale size the tank would draw. */
    public float length() {
        return isEmpty() ? 0f : engine.lengths[0];
    }

    private static VoxelDomain openBox(int l, int y, int d) {
        boolean[][][] occupancy = new boolean[l][y][d];
        for (boolean[][] plane : occupancy) for (boolean[] row : plane) java.util.Arrays.fill(row, true);
        return new VoxelDomain(occupancy);
    }

    public boolean isEmpty() { return stack.isEmpty() || engine.count() == 0; }

    public ItemStack stack() { return stack; }

    public void step() {
        if (!isEmpty()) engine.step();
    }

    public void interpolate(float partialTick) {
        if (!isEmpty()) engine.interpolate(partialTick);
    }

    /** The fish's offset from the box centre this frame, in engine units (call after {@link #interpolate}). */
    public Vec3 localOffset() {
        return new Vec3(engine.renderX[0], engine.renderY[0], engine.renderZ[0]);
    }

    /**
     * Poses and submits the fish. The stack must already be translated to the fish's own position
     * and carry whatever outer scale maps engine units onto the destination (1 in the world, the
     * dock's miniature factor on the HUD). {@code sizeMultiplier} enlarges the fish itself without
     * moving it — the engine's swim box is sized for the true-to-scale fish.
     */
    public void submit(PoseStack poseStack, SubmitNodeCollector nodes, int light, float gameTimeTicks, float sizeMultiplier) {
        if (isEmpty()) return;
        FlockEngine eng = engine;
        float scale = eng.lengths[0] * sizeMultiplier;
        random.setSeed(eng.seeds[0]);
        poseStack.pushPose();
        if (eng.swimmers[0] && anim instanceof FishAnimationConfig.HorizontalSwim swim && eng.planar()) {
            FishAnimator.applySwimming(poseStack, swim, random, eng.renderPhase[0], eng.renderYaw[0] + 180f,
                    false, eng.speedFactor(0), eng.bank[0], eng.renderBobRoom[0], scale);
        } else if (eng.swimmers[0] && anim instanceof FishAnimationConfig.HorizontalSwim swim) {
            // The tank's binary model, exactly as FishTankBlockEntityRenderer draws it.
            FishAnimator.applySwimming(poseStack, swim, random, eng.renderPhase[0], eng.baseRotations[0],
                    eng.heading[0] > 0f, eng.speedFactor(0), eng.bank[0], eng.renderBobRoom[0], scale);
        } else {
            // Spike scope is the free swimmer; everything else holds its tank idle pose for now.
            FishAnimator.apply(poseStack, anim, random, gameTimeTicks, eng.baseRotations[0], scale, false);
        }
        poseStack.scale(scale, scale, scale);
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(renderState, stack, ItemDisplayContext.FIXED, null, null, 0);
        renderState.submit(poseStack, nodes, light, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}
