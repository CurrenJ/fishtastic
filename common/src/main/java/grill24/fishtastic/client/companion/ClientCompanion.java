package grill24.fishtastic.client.companion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import grill24.fishsim.companion.CompanionAgent;
import grill24.fishsim.companion.CompanionGroup;
import grill24.fishsim.companion.CompanionTunables;
import grill24.fishsim.companion.CompanionWorld;
import grill24.fishsim.companion.OwnerState;
import grill24.fishsim.companion.PerchBody;
import grill24.fishsim.companion.PointOfInterest;
import grill24.fishsim.companion.Temperament;
import grill24.fishsim.companion.Vec;
import grill24.fishsim.core.Locomotion;
import grill24.FishtasticRegistries;
import grill24.fishtastic.client.renderer.CompanionFishPose;
import grill24.fishtastic.client.renderer.CompanionRender;
import grill24.fishtastic.data.FishAnimationConfig;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * One companion fish on the client: a player's equipped companion, drawn for its owner and for
 * everyone who can see them (docs/fish-companions-sim-plan.md §3.5). {@link ClientCompanions} owns
 * the instances.
 *
 * <p>All motion and behaviour is the {@code :fishsim} companion sim — a {@link CompanionAgent}, or
 * a {@link CompanionGroup} for a trio — stepped once per client tick from the snapshot
 * {@link CompanionWorldAdapter} builds, and interpolated per frame. This class only owns what the
 * sim can't know: the bonded stack, the species' render data, and drawing.
 *
 * <p>Each client simulates every companion it can see from that owner's synced state, seeded from
 * the owner and the bonded fish, so a remote companion behaves in character without having to match
 * frame for frame (plan §3.6).
 */
public final class ClientCompanion {
    /** Smallest length a companion is drawn (and simulated) at, blocks — true-to-scale small fish are specks. */
    private static final float MIN_LENGTH = 0.25f;
    private static final CompanionTunables TUNABLES = CompanionTunables.defaults();

    /** The owner, or null for a dev phantom: an imaginary still owner standing at {@link #phantomEye}. */
    @Nullable private final Player owner;
    @Nullable private final Vec3 phantomEye;
    private final float phantomYaw;
    private final ItemStack stack;
    private final long seed;
    private final Locomotion locomotion;
    private final float length;
    private final Temperament temperament;
    private final boolean trio;
    private final CompanionRender render;
    private final CompanionFishPose perchPose;
    private final CompanionWorldAdapter.OwnerTracker tracker = new CompanionWorldAdapter.OwnerTracker();

    @Nullable private CompanionAgent agent;
    @Nullable private CompanionGroup group;
    @Nullable private OwnerState lastOwner;
    private CompanionWorld.Held lastHeld = CompanionWorld.Held.NONE;
    private int lastPoints;
    /** Dev trigger: report the owner hurt on the next tick. */
    private boolean devHurt;
    /** Nanoseconds the last sim step took, for the debug overlay. */
    long stepNanos;

    ClientCompanion(@Nullable Player owner, @Nullable Vec3 phantomEye, float phantomYaw, ItemStack stack,
                    ClientLevel level, @Nullable Temperament override) {
        this.owner = owner;
        this.phantomEye = phantomEye;
        this.phantomYaw = phantomYaw;
        this.stack = stack.copy();
        this.render = new CompanionRender(stack, level);
        this.perchPose = new CompanionFishPose(stack, level, MIN_LENGTH);
        this.locomotion = render.locomotion();
        this.length = Math.max(render.length(), MIN_LENGTH);
        float sizeCm = ItemSizeHelper.hasSize(stack) ? ItemSizeHelper.getSize(stack) : Float.MAX_VALUE;
        this.trio = CompanionGroup.isTrio(locomotion, sizeCm);
        this.temperament = override != null ? override : temperamentOf(stack, level);
        this.seed = seedOf(owner != null ? owner.getUUID() : UUID.nameUUIDFromBytes(phantomEye.toString().getBytes()),
                stack);
    }

    /** The species' temperament from its fish profile (spec §4), or the baseline. */
    private static Temperament temperamentOf(ItemStack stack, ClientLevel level) {
        var itemKey = BuiltInRegistries.ITEM.getResourceKey(stack.getItem());
        if (itemKey.isEmpty()) return Temperament.BASELINE;
        return level.registryAccess().lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY)
                .getOptional(ResourceKey.create(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, itemKey.get().identifier()))
                .flatMap(FishProfile::temperament)
                .map(key -> Temperament.named(key.identifier().getPath()))
                .orElse(Temperament.BASELINE);
    }

    /**
     * The same on every client: the owner and the bonded fish's species and size (never an
     * identity hash, which differs per JVM).
     */
    private static long seedOf(UUID owner, ItemStack stack) {
        long h = owner.getMostSignificantBits() * 31 + owner.getLeastSignificantBits();
        h = h * 31 + BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().hashCode();
        if (ItemSizeHelper.hasSize(stack)) h = h * 31 + Float.floatToIntBits(ItemSizeHelper.getSize(stack));
        return h ^ (h >>> 29);
    }

    ItemStack stack() { return stack; }

    @Nullable Player owner() { return owner; }

    public boolean isLocal() {
        return owner != null && owner == Minecraft.getInstance().player;
    }

    // ── Tick ───────────────────────────────────────────────────────────────────

    /**
     * Client tick (20 Hz): builds this companion's view of the world and steps the sim.
     *
     * @param companions every other companion near enough to matter, as points of interest
     */
    void tick(ClientLevel level, CompanionWorldAdapter.LevelBlocks blocks, List<PointOfInterest> companions,
              List<PointOfInterest> devPoints) {
        OwnerState ownerState;
        boolean hurt;
        CompanionWorld.Held held;
        List<PointOfInterest> points;
        if (owner != null) {
            ownerState = tracker.owner(owner, isLocal());
            hurt = tracker.hurt(owner);
            held = CompanionWorldAdapter.held(owner.getMainHandItem(), stack);
            points = CompanionWorldAdapter.points(level, owner, companions);
        } else {
            boolean wet = level.getFluidState(BlockPos.containing(phantomEye.subtract(0, OwnerState.CHEST_DROP, 0)))
                    .is(FluidTags.WATER);
            ownerState = new OwnerState(CompanionWorldAdapter.vec(phantomEye), Vec.ZERO, phantomYaw, 0f,
                    false, false, true, 70f, wet);
            hurt = false;
            held = CompanionWorld.Held.NONE;
            points = new java.util.ArrayList<>(companions);
        }
        if (isLocal() && !devPoints.isEmpty()) {
            points = new java.util.ArrayList<>(points);
            points.addAll(devPoints);
        }
        hurt |= devHurt;
        devHurt = false;
        CompanionWorld world = new CompanionWorld(ownerState, blocks, points, held, hurt);
        lastOwner = ownerState;
        lastHeld = held;
        lastPoints = points.size();

        long start = System.nanoTime();
        if (agent == null) {
            if (trio) {
                group = new CompanionGroup(TUNABLES, seed, length, temperament, world);
                agent = group.leader();
            } else {
                agent = new CompanionAgent(TUNABLES, seed, locomotion, length, temperament, world);
            }
        } else if (group != null) {
            group.step(world);
        } else {
            agent.step(world);
        }
        stepNanos = System.nanoTime() - start;
        render.tick(agent);
    }

    /** Forgets every sampled block (a block changed nearby, or a chunk arrived). */
    void invalidateBlocks() {
        if (group != null) group.invalidateBlocks();
        else if (agent != null) agent.voxels().invalidate();
    }

    /**
     * This companion as another companion's point of interest: the lead fish, with a stable id
     * that can't collide with an entity's (the owner's entity id, negated).
     */
    @Nullable
    PointOfInterest asPoint(int id) {
        if (agent == null || agent.appear() < 1f) return null;
        return new PointOfInterest(PointOfInterest.Kind.COMPANION, id, agent.position(), agent.velocity(),
                agent.radius());
    }

    // ── Rendering ──────────────────────────────────────────────────────────────

    /**
     * Draws every fish the world pass owns: everything except a percher riding a shoulder, the head
     * or the back, which {@link CompanionPerchLayer} draws on the player model. Returns whether
     * anything was drawn. The stack is camera-relative.
     */
    boolean submitWorld(PoseStack poseStack, SubmitNodeCollector nodes, Vec3 cam, float partialTick) {
        if (agent == null) return false;
        if (group != null) group.interpolate(partialTick);
        else agent.interpolate(partialTick);
        float gameTime = gameTime(partialTick);
        boolean glow = glowsNow();
        boolean drew = false;

        if (!drawsOnPerch()) {
            Vec pos = agent.renderPos();
            float yaw = agent.renderYaw();
            float pitch = agent.renderPitch();
            if (handAttached()) {
                // Riding the hand in first person: attached to the view at frame rate (the parrot
                // pattern), so a mouse flick doesn't leave it a tick behind the camera.
                OwnerState o = frameOwner(partialTick);
                pos = PerchBody.slotPoint(o, agent.perchSlot());
                yaw = o.yawDeg();
                pitch = 0f;
            }
            drew |= render.submit(poseStack, nodes, cam, 0, pos, yaw, pitch, agent.renderBank(),
                    agent.renderAppear(), agent.renderTailPhase(), agent.speedFactor(), agent.pulse(),
                    agent.perchSquash(), riding(), gameTime, light(pos, glow));
        }
        if (group != null) {
            for (int i = 0; i < 2; i++) {
                CompanionGroup.Follower f = group.follower(i);
                Vec pos = f.renderPos();
                drew |= render.submit(poseStack, nodes, cam, i + 1, pos, f.renderYaw(), f.renderPitch(),
                        f.renderBank(), f.renderAppear(), f.renderTailPhase(), f.speedFactor(), 0f, 0f, false, gameTime, light(pos, glow));
            }
        }
        return drew;
    }

    /** Whether the sim has this percher on its owner's body (perched, crouching or settling). */
    private boolean riding() {
        return agent != null && agent.riding();
    }

    /** A percher riding its owner's hand, seen by that owner in first person. */
    private boolean handAttached() {
        return riding() && agent.perchSlot().hand() && isLocal()
                && Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    /** The owner's state at this frame's camera, for attachments drawn at frame rate. */
    private OwnerState frameOwner(float partialTick) {
        Vec3 eye = owner.getEyePosition(partialTick);
        return new OwnerState(CompanionWorldAdapter.vec(eye), lastOwner.velocity(), owner.getViewYRot(partialTick),
                owner.getViewXRot(partialTick), false, false, true, lastOwner.fovDeg(), false);
    }

    /** True while the player render layer draws this companion on its owner's body. */
    boolean drawsOnPerch() {
        if (!riding() || owner == null || owner.isInvisible()) return false;
        return !agent.perchSlot().hand();
    }

    /** The perch slot for {@link CompanionPerchLayer}. */
    PerchBody.Slot perchSlot() {
        return agent.perchSlot();
    }

    Locomotion locomotion() { return locomotion; }

    /** Height of the perched sprite's centre above the surface its feet rest on. */
    float perchFloorLift() { return perchPose.floorLift(); }

    /**
     * Draws the perched fish in the pose space the player layer has set up: its idle animation, the
     * anchored duck, a head tilt from the pose hint, and the appear fade.
     */
    void submitPerched(PoseStack poseStack, SubmitNodeCollector nodes, int light, float partialTick,
                       float extraYawDeg) {
        float appear = agent.renderAppear();
        if (appear <= 0f) return;
        poseStack.pushPose();
        if (appear < 1f) poseStack.scale(appear, appear, appear);
        float tilt = render.hintTilt(partialTick);
        if (tilt != 0f) poseStack.mulPose(Axis.ZP.rotationDegrees(tilt));
        perchPose.submit(poseStack, nodes, light, gameTime(partialTick), extraYawDeg, render.hintPitch(partialTick),
                render.retract(partialTick));
        poseStack.popPose();
    }

    /** A drifter glows at night (spec §3). */
    private boolean glowsNow() {
        Minecraft mc = Minecraft.getInstance();
        return locomotion == Locomotion.DRIFT && mc.level != null && mc.level.getSkyDarken() >= 4;
    }

    private static int light(Vec pos, boolean glow) {
        Minecraft mc = Minecraft.getInstance();
        if (glow || mc.level == null) return LightCoordsUtil.FULL_BRIGHT;
        return LevelRenderer.getLightCoords(mc.level, BlockPos.containing(pos.x(), pos.y(), pos.z()));
    }

    static float gameTime(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0f : mc.level.getGameTime() + partialTick;
    }

    // ── Dev hooks and diagnostics ──────────────────────────────────────────────

    /** Dev-only: the bonded fish itself — the dev commands clone it to park a phantom partner. */
    public ItemStack devStack() { return stack; }

    @Nullable public CompanionAgent agent() { return agent; }

    public Temperament temperament() { return temperament; }

    void devHurt() { devHurt = true; }

    void devDrowsy() {
        if (agent != null) agent.devDrowsy();
    }

    /** Whether the owner can see the lead fish this tick (the sim's own judgement). */
    boolean seenByOwner() {
        return agent != null && agent.seen();
    }

    /** The display class as a stable name for logs, overlays and checks. */
    public String displayClassName() {
        if (trio) return "trio";
        return locomotion.name().toLowerCase(Locale.ROOT);
    }

    /** The debug overlay's lines (goal, drives, pose hint, temperament). */
    List<String> debugLines() {
        if (agent == null) return List.of("companion: not stepped yet");
        CompanionAgent a = agent;
        String perch = a.perchState() == null ? "" : "  perch " + a.perchState().toLowerCase(Locale.ROOT);
        String ground = a.footTrail().isEmpty() ? "" : String.format(Locale.ROOT,
                "  land %s lag %.1f trail %d %s", a.landMode(), a.groundLag(), a.footTrail().size(),
                a.groundReachable() ? "reachable" : "stranded");
        return List.of(
                String.format(Locale.ROOT, "%s  %s  temperament %s  %.2f blocks",
                        BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(), displayClassName(),
                        temperament.name(), length),
                String.format(Locale.ROOT, "goal %s (%.1f s)%s  pose %s  fade %s", a.goal(),
                        a.goalTicks() / 20f, perch + ground, a.poseHint(), a.fade()),
                "curiosity " + bar(a.curiosity()) + "  wariness " + bar(a.wariness())
                        + "  rest " + bar(a.rest()) + (a.resting() ? " settled" : ""),
                "proximity " + bar(a.proximity()) + "  attention " + bar(a.attention())
                        + "  seen " + (a.seen() ? "yes" : "no (" + a.unseenTicks() / 20 + " s)"),
                String.format(Locale.ROOT, "held %s  points %d  owner %.1f blocks  step %d us",
                        lastHeld, lastPoints, a.ownerDistance(), stepNanos / 1000));
    }

    private static String bar(float v) {
        int n = Mth.clamp(Math.round(v * 10), 0, 10);
        return "[" + "|".repeat(n) + ".".repeat(10 - n) + "]";
    }
}
