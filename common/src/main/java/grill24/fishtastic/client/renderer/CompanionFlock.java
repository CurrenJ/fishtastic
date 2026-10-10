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

import java.util.Arrays;
import java.util.Random;

/**
 * One companion fish run on the tank simulation, in an invisible tank that follows a moving centre
 * instead of sitting on a block (docs/fish-companions.md §3, §12).
 *
 * <p>The engine is the group path's voxel-domain engine over a small all-open box, so the fish gets
 * the planar continuous-yaw model, banking and speed-coupled tail beat for free — nothing about how
 * it swims is new. What is new is only the frame: positions come out box-relative, and the box
 * centre is whatever the caller says it is this tick, scaled by the caller so the same
 * box can be shrunk to a miniature when the fish docks next to the minigame bar.
 *
 * <p>Phase 3 added the class behaviours on top of that frame:
 * <ul>
 *   <li>the follow box is sized for the fish's own length, so the engine's size gate passes every
 *       species whose swimming is meant to show (a giant too big even for that glides rigidly with
 *       the box, which for an animal that size is indistinguishable);</li>
 *   <li><b>trio</b> — a sub-20&nbsp;cm free swimmer is rebuilt as three same-species specs and the
 *       engine's own swarm offsets spread them (docs/fish-companions.md §2);</li>
 *   <li><b>water</b> — every class drops its air anchor: the box moves beside the player and the
 *       engine runs the planar model, with the two attach classes (benthic, anchored) swapped to
 *       free-swim locomotion, because a starfish has no swim of its own (§3, E1);</li>
 *   <li>per-fish pose dispatch copied from the tank group renderer, so each class is drawn with its
 *       own animator entry point and the engine's own drive values (heading, bank, bell, retract).</li>
 * </ul>
 */
public final class CompanionFlock {
    /** Following the player: an open box whose side is sized from the fish's length. */
    private static final int MAX_FOLLOW_SIDE = 8;
    /** Docked: the single-tank model's lateral half-extent (Tunables.DEFAULT.tankHalfExtent). */
    public static final float DOCK_HALF_EXTENT = 0.35f;
    private static final float DOCK_Y_RANGE = 0.5f;
    private static final float DOCK_SIM_LENGTH = 0.2f;
    /** Bonded fish under this size come as a trio when they are small free swimmers (§2). */
    public static final float TRIO_MAX_CM = 20f;

    /** Which invisible tank the fish lives in — chosen by the caller from the owner's state. */
    public enum Mode { FOLLOW, WATER, DOCKED }

    private final FlockEngine engine = new FlockEngine(Tunables.GROUP);
    private final Random random = new Random();
    private final FishAnimationConfig anim;
    private final int seed;
    private final float length;
    private final Locomotion locomotion;
    private final boolean trio;

    private ItemStack[] stacks = new ItemStack[0];
    private ItemStackRenderState[] renderStates = new ItemStackRenderState[0];
    /** How far (engine units) each fish was moved to fit the current box; faded by {@link #biasFade}. */
    private Vec3[] clampBias = new Vec3[0];
    /** 1 while a clamp move is fully visible, 0 once the caller has faded it out. */
    private float biasFade = 1f;
    private Mode mode = Mode.FOLLOW;
    private boolean empty = true;

    public CompanionFlock(ItemStack fish, Level level) {
        FishTankBlockEntityRenderer.ResolvedFishRender render = FishTankBlockEntityRenderer.resolveFishRender(fish, level);
        this.anim = render.animation();
        this.length = TankFlockAdapter.renderedLength(fish, render.renderCalibration());
        this.locomotion = TankFlockAdapter.locomotionOf(anim);
        this.seed = fish.hashCode();
        this.trio = locomotion == Locomotion.FREE_SWIM
                && grill24.fishtastic.util.ItemSizeHelper.hasSize(fish)
                && grill24.fishtastic.util.ItemSizeHelper.getSize(fish) < TRIO_MAX_CM;
        ItemStack single = fish.copyWithCount(1);
        int n = trio ? 3 : 1;
        this.stacks = new ItemStack[n];
        this.renderStates = new ItemStackRenderState[n];
        for (int i = 0; i < n; i++) {
            stacks[i] = single;
            renderStates[i] = new ItemStackRenderState();
        }
        this.clampBias = new Vec3[n];
        Arrays.fill(clampBias, Vec3.ZERO);
        empty = false;
        rebuildEngine();
    }

    /** The display class this companion moves as (docs/fish-companions.md §3). */
    public Locomotion locomotion() { return locomotion; }

    public boolean isTrio() { return trio; }

    public boolean isEmpty() { return empty; }

    /** The fish's rendered length in engine units — the true-to-scale size the tank would draw. */
    public float length() { return length; }

    public int count() { return engine.count(); }

    public int fishCount() { return stacks.length; }

    public Mode mode() { return mode; }

    // ── Engine configuration ────────────────────────────────────────────────

    /**
     * The all-open box the fish swims in, sized so the engine's size gate (2.5 body lengths at the
     * follow tunables) passes it: {@code 2.5 · length + 1} cells, at least 3, at most
     * {@link #MAX_FOLLOW_SIDE}. Gliders and water boxes get extra room — a ray is at its best
     * crossing the box corner to corner.
     */
    private static VoxelDomain followBox(float length, boolean glide, boolean water) {
        int side = Math.max(3, (int) Math.ceil(2.5f * length) + 1);
        if (glide) side += 2;
        if (water) side += 1;
        side = Math.min(side, MAX_FOLLOW_SIDE);
        return openBox(side, 2, side);
    }

    /** The locomotion handed to the engine — water swaps the two attach classes onto free swim. */
    private Locomotion simLocomotion(Mode mode) {
        if (mode == Mode.WATER && (locomotion == Locomotion.BENTHIC || locomotion == Locomotion.ANCHORED)) {
            return Locomotion.FREE_SWIM;
        }
        return locomotion;
    }

    private FishSpec[] specs(Locomotion locomotion) {
        FishSpec[] specs = new FishSpec[stacks.length];
        int species = TankFlockAdapter.speciesId(stacks[0]);
        for (int i = 0; i < specs.length; i++) {
            specs[i] = new FishSpec(length, locomotion, false, species);
        }
        return specs;
    }

    /** (Re)builds the engine for the current mode, keeping the fish where they were. */
    private void rebuildEngine() {
        Locomotion locomotion = simLocomotion(mode);
        if (mode == Mode.DOCKED) {
            engine.setTunables(Tunables.DEFAULT);
            engine.rebuild(new FishSpec[]{new FishSpec(Math.min(length, DOCK_SIM_LENGTH), Locomotion.FREE_SWIM,
                    false, TankFlockAdapter.speciesId(stacks[0]))}, seed, 0f, 1,
                    DOCK_HALF_EXTENT, DOCK_Y_RANGE, 0f);
        } else {
            engine.setTunables(Tunables.GROUP);
            FishSpec[] specs = specs(locomotion);
            int[] carryFrom = new int[specs.length];
            for (int i = 0; i < specs.length; i++) carryFrom[i] = i;
            engine.rebuildPreserving(specs, carryFrom, seed, 0f, 0f,
                    followBox(length, locomotion == Locomotion.GLIDE, mode == Mode.WATER));
        }
        clampIntoDomain();
    }

    /**
     * Switches the fish's invisible tank between following, water and the docked minigame model.
     * Docked it is the single-tank binary model, exactly as in the Phase 0 dock: the fish only ever
     * swims left or right, which is what keeps it side-on to the camera next to the minigame bar.
     *
     * <p>The tank's swim gate passes only short fish (2.5 x length must fit its 0.7 run), so the
     * docked engine is given a nominal {@link #DOCK_SIM_LENGTH}; the drawn size is the caller's.
     */
    public void setMode(Mode next) {
        if (next == mode || empty) return;
        mode = next;
        rebuildEngine();
    }

    /**
     * Carried over outside a smaller box, the engine's wall constraint (which assumes the fish
     * starts inside) would pin it there for good — clamp each fish in and remember the difference,
     * which the caller fades out at render time via {@link #setBiasFade}.
     */
    private void clampIntoDomain() {
        var box = engine.domain();
        float m = 0.03f;
        float[] l = engine.posL(), y = engine.posY(), d = engine.posD();
        for (int i = 0; i < engine.count(); i++) {
            float cl = clamp(l[i], box.minLateral() + m, box.maxLateral() - m);
            float cy = clamp(y[i], box.minVertical() + m, box.maxVertical() - m);
            float cd = clamp(d[i], box.minDepth() + m, box.maxDepth() - m);
            clampBias[i] = new Vec3(l[i] - cl, y[i] - cy, d[i] - cd);
            l[i] = cl;
            y[i] = cy;
            d[i] = cd;
        }
        biasFade = 1f;
    }

    /** The caller's dock-blend: how much of each clamp move should still be drawn (1 → 0). */
    public void setBiasFade(float fade) {
        biasFade = clamp(fade, 0f, 1f);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static VoxelDomain openBox(int l, int y, int d) {
        boolean[][][] occupancy = new boolean[l][y][d];
        for (boolean[][] plane : occupancy) for (boolean[] row : plane) java.util.Arrays.fill(row, true);
        return new VoxelDomain(occupancy);
    }

    /** Spike diagnostics. */
    public String debug() {
        if (empty) return "empty";
        String className = locomotion == Locomotion.FREE_SWIM && !trio ? "free_swim"
                : trio ? "trio" : locomotion.name().toLowerCase(java.util.Locale.ROOT);
        return String.format(java.util.Locale.ROOT, "count=%d class=%s sim=%s pos=(%.3f,%.3f,%.3f) vel=(%.3f,%.3f,%.3f) mode=%s",
                engine.count(), className, engine.locomotion[0],
                engine.posL()[0], engine.posY()[0], engine.posD()[0],
                engine.velL()[0], engine.velY()[0], engine.velD()[0], mode) + " planar=" + engine.planar();
    }

    public void step() {
        if (!empty) engine.step();
    }

    public void interpolate(float partialTick) {
        if (!empty) engine.interpolate(partialTick);
    }

    /**
     * The {@code i}th fish's offset from the box centre this frame, in engine units, with its
     * clamp-move bias faded by the caller's dock blend (call after {@link #interpolate}).
     */
    public Vec3 fishOffset(int i) {
        return new Vec3(engine.renderX[i], engine.renderY[i], engine.renderZ[i])
                .add(clampBias[i].scale(biasFade));
    }

    /**
     * Poses and submits every fish. The stack must already be translated to the box centre and
     * carry whatever outer scale maps engine units onto the destination (1 in the world, the dock's
     * miniature factor on the HUD). Each fish is placed at its own offset inside the box.
     * {@code sizeMultiplier} enlarges the fish itself without moving it — the engine's swim box is
     * sized for the true-to-scale fish. {@code tiltDeg} is an extra lean about X applied to the
     * whole submission (a held-item reaction's head tilt; 0 normally).
     */
    public void submit(PoseStack poseStack, SubmitNodeCollector nodes, int light, float gameTimeTicks,
                       float sizeMultiplier, float tiltDeg) {
        if (empty) return;
        FlockEngine eng = engine;
        boolean planar = eng.planar();
        for (int i = 0; i < eng.count(); i++) {
            Vec3 off = fishOffset(i);
            float scale = eng.lengths[i] * sizeMultiplier;
            poseStack.pushPose();
            poseStack.translate(off.x, off.y + FishAnimator.floorPoseLift(anim, scale), off.z);
            if (tiltDeg != 0f) poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(tiltDeg));
            random.setSeed(eng.seeds[i]);
            boolean mirrored = eng.swimmers[i] ? eng.heading[i] > 0f : eng.hoverMirrored[i];
            if (anim instanceof FishAnimationConfig.HorizontalSwim swim && eng.swimmers[i] && planar) {
                // Planar swimmers animate on the engine's speed-integrated clock, with the
                // continuous yaw the +180° maps onto the item sprite's nose.
                FishAnimator.applySwimming(poseStack, swim, random, eng.renderPhase[i],
                        eng.renderYaw[i] + 180f, false, eng.speedFactor(i), eng.bank[i],
                        eng.renderBobRoom[i], scale);
            } else if (anim instanceof FishAnimationConfig.HorizontalSwim swim && eng.swimmers[i]) {
                // The tank's binary model, exactly as FishTankBlockEntityRenderer draws it.
                FishAnimator.applySwimming(poseStack, swim, random, eng.renderPhase[i], eng.baseRotations[i],
                        eng.heading[i] > 0f, eng.speedFactor(i), eng.bank[i], eng.renderBobRoom[i], scale);
            } else if (eng.locomotion[i] == Locomotion.BENTHIC) {
                FishAnimator.applyBenthic(poseStack, anim, random, gameTimeTicks, eng.renderYaw[i] + 180f,
                        eng.baseRotations[i], scale, false, eng.renderShape[i]);
            } else if (eng.locomotion[i] == Locomotion.GLIDE) {
                FishAnimator.applyGliding(poseStack, anim, random, gameTimeTicks, eng.renderYaw[i] + 180f,
                        eng.bankFraction(i), eng.baseRotations[i], scale, false);
            } else if (eng.locomotion[i] == Locomotion.DRIFT) {
                FishAnimator.applyDrifting(poseStack, anim, random, gameTimeTicks, eng.baseRotations[i],
                        scale, mirrored, eng.renderShape[i]);
            } else if (eng.locomotion[i] == Locomotion.ANCHORED) {
                FishAnimator.applyAnchored(poseStack, anim, random, gameTimeTicks, eng.baseRotations[i],
                        scale, mirrored, eng.renderShape[i]);
            } else {
                // Anything demoted to STATIC (a fish too big for its box) or a water-swapped
                // class with no swim of its own poses on game time, exactly as the tank does.
                FishAnimator.apply(poseStack, anim, random, gameTimeTicks, eng.baseRotations[i], scale, mirrored);
            }
            poseStack.scale(scale, scale, scale);

            ItemStackRenderState renderState = renderStates[i];
            Minecraft.getInstance().getItemModelResolver()
                    .updateForTopItem(renderState, stacks[i], ItemDisplayContext.FIXED, null, null, 0);
            renderState.submit(poseStack, nodes, light, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

}
