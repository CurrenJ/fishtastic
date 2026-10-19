package grill24.fishtastic.client.companion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import grill24.fishsim.companion.BenthicGroup;
import grill24.fishsim.companion.CompanionAgent;
import grill24.fishsim.companion.CompanionGroup;
import grill24.fishsim.companion.GroundGait;
import grill24.fishsim.companion.LocalVoxels;
import grill24.fishsim.companion.CompanionTunables;
import grill24.fishsim.companion.CompanionWorld;
import grill24.fishsim.companion.DriftStyle;
import grill24.fishsim.companion.OwnerState;
import grill24.fishsim.companion.PartyGrouping;
import grill24.fishsim.companion.PerchBody;
import grill24.fishsim.companion.PointOfInterest;
import grill24.fishsim.companion.Temperament;
import grill24.fishsim.companion.Vec;
import grill24.fishsim.core.Locomotion;
import grill24.fishtastic.client.renderer.CompanionFishPose;
import grill24.fishtastic.client.renderer.CompanionRender;
import grill24.fishtastic.client.renderer.FishVisuals;
import grill24.fishtastic.companion.CompanionGroups;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * One unit of a player's companion party on the client (docs/fish-companions-party.md §3): a solo
 * fish, a school of 2–3 same-species free swimmers, or a ground formation of 2–3 same-species
 * benthics, as {@link PartyGrouping} groups the party. Drawn for its owner and for everyone who can
 * see them (docs/fish-companions-sim-plan.md §3.5). {@link ClientCompanions} owns the instances, one
 * per group.
 *
 * <p>All motion and behaviour is the {@code :fishsim} companion sim — a {@link CompanionAgent}, a
 * {@link CompanionGroup} for a school or a {@link BenthicGroup} for a ground formation — stepped once
 * per client tick from the snapshot {@link CompanionWorldAdapter} builds, and interpolated per frame.
 * This class only owns what the sim can't know: each member's bonded stack, its render data, and
 * drawing. Every member is its own fish: its own size, quality, name and render calibration.
 *
 * <p>Each client simulates every companion it can see from that owner's synced party, seeded from
 * the owner, each bonded fish and its slot, so a remote companion behaves in character without having
 * to match frame for frame (plan §3.6).
 */
public final class ClientCompanion {
    /**
     * Every companion is simulated, perched and drawn at its true size (party spec D11: "let small
     * fish look small", solos too). A 0.25-block floor once sized the sim's body and the perched
     * pose of small fish; it is gone. Only a degenerate zero length is guarded.
     */
    private static final float MIN_SIM_LENGTH = 1e-3f;
    private static final CompanionTunables TUNABLES = CompanionTunables.defaults();

    /** The owner, or null for a dev phantom: an imaginary still owner standing at {@link #phantomEye}. */
    @Nullable private final Player owner;
    @Nullable private final Vec3 phantomEye;
    private final float phantomYaw;
    private final PartyGrouping.Group group;
    /** Each member's bonded fish, leader first (the group's own member order). */
    private final ItemStack[] stacks;
    private final CompanionRender[] renders;
    private final CompanionFishPose[] perchPoses;
    /** Each member's simulated length, blocks. */
    private final float[] lengths;
    private final Locomotion locomotion;
    private final Temperament temperament;
    /** How a benthic walks on land, or null for every other class. */
    @Nullable private final GroundGait gait;
    /** How a drifter propels itself (docs/fish-companions-drift.md §2), from the profile's companion_drift. */
    private final DriftStyle driftStyle;
    private final CompanionWorldAdapter.OwnerTracker tracker = new CompanionWorldAdapter.OwnerTracker();

    @Nullable private CompanionAgent agent;
    @Nullable private CompanionGroup school;
    @Nullable private BenthicGroup benthic;
    @Nullable private OwnerState lastOwner;
    private CompanionWorld.Held lastHeld = CompanionWorld.Held.NONE;
    private int lastPoints;
    /** Dev trigger: report the owner hurt on the next tick. */
    private boolean devHurt;
    /** Nanoseconds the last sim step took, for the debug overlay. */
    long stepNanos;

    /**
     * @param group  how this unit's members group, from {@link PartyGrouping#group}
     * @param stacks each member's bonded fish, in {@code group.members()} order
     */
    ClientCompanion(@Nullable Player owner, @Nullable Vec3 phantomEye, float phantomYaw, PartyGrouping.Group group,
                    List<ItemStack> stacks, ClientLevel level, @Nullable Temperament override) {
        if (stacks.size() != group.members().size()) throw new IllegalArgumentException("one stack per member");
        this.owner = owner;
        this.phantomEye = phantomEye;
        this.phantomYaw = phantomYaw;
        this.group = group;
        int n = stacks.size();
        this.stacks = new ItemStack[n];
        this.renders = new CompanionRender[n];
        this.perchPoses = new CompanionFishPose[n];
        this.lengths = new float[n];
        for (int i = 0; i < n; i++) {
            ItemStack stack = stacks.get(i);
            this.stacks[i] = stack.copy();
            this.renders[i] = new CompanionRender(stack, level);
            this.perchPoses[i] = new CompanionFishPose(stack, level, MIN_SIM_LENGTH);
            this.lengths[i] = Math.max(renders[i].length(), MIN_SIM_LENGTH);
        }
        this.locomotion = renders[0].locomotion();
        Optional<FishProfile> profile = CompanionGroups.profileOf(level.registryAccess(), this.stacks[0]);
        this.temperament = override != null ? override : profile.flatMap(FishProfile::temperament)
                .map(key -> Temperament.named(key.identifier().getPath()))
                .orElse(Temperament.BASELINE);
        this.gait = CompanionGroups.gaitOf(locomotion, profile);
        this.driftStyle = locomotion == Locomotion.DRIFT
                ? DriftStyle.valueOf(profile.map(FishProfile::companionDrift).orElse(FishProfile.CompanionDrift.BELL).name())
                : DriftStyle.BELL;
    }

    /** A dev phantom: one fish round an imaginary still owner at {@code eye}. */
    static ClientCompanion phantom(Vec3 eye, float yaw, ItemStack fish, ClientLevel level) {
        UUID id = UUID.nameUUIDFromBytes(eye.toString().getBytes());
        var member = CompanionGroups.members(new grill24.fishtastic.companion.CompanionParty(List.of(fish)),
                level.registryAccess(), s -> trueLength(s, level), s -> stackSeed(id, s));
        return new ClientCompanion(null, eye, yaw, PartyGrouping.group(member).get(0), List.of(fish), level, null);
    }

    /** A fish's true-to-scale rendered length, blocks: what grouping ranks members by. */
    static float trueLength(ItemStack fish, Level level) {
        return FishVisuals.renderedLength(fish, FishVisuals.resolve(fish, level).renderCalibration());
    }

    /**
     * The same on every client: the owner and the bonded fish's species and size (never an
     * identity hash, which differs per JVM). {@link PartyGrouping.Member#seed} mixes in the slot.
     */
    static long stackSeed(UUID owner, ItemStack stack) {
        long h = owner.getMostSignificantBits() * 31 + owner.getLeastSignificantBits();
        h = h * 31 + BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().hashCode();
        if (ItemSizeHelper.hasSize(stack)) h = h * 31 + Float.floatToIntBits(ItemSizeHelper.getSize(stack));
        return h ^ (h >>> 29);
    }

    /** The lead fish's bonded stack (the group's leader). */
    ItemStack stack() { return stacks[0]; }

    @Nullable Player owner() { return owner; }

    /** How this unit's members group. */
    public PartyGrouping.Group group() { return group; }

    /** The party slot of this unit's formation leader (its smallest fish, D9; not necessarily the lowest slot). */
    public int leaderSlot() { return group.leaderSlot(); }

    /**
     * This unit's lowest party slot: where it sits in the party, and with its owner its identity
     * (point-of-interest ids, the overlay). The party lead (slot 0) may be a follower in its school,
     * so anything the lead owns finds its unit with {@link #holdsSlot}, never by leadership.
     */
    public int lowestSlot() { return group.lowestSlot(); }

    /** Whether this unit holds party slot {@code slot}. */
    public boolean holdsSlot(int slot) {
        for (int s : group.slots()) if (s == slot) return true;
        return false;
    }

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
            held = CompanionWorldAdapter.held(owner.getMainHandItem(), stacks[0]);
            points = CompanionWorldAdapter.points(level, owner, companions);
        } else {
            boolean wet = level.getFluidState(BlockPos.containing(phantomEye.subtract(0, OwnerState.CHEST_DROP, 0)))
                    .is(FluidTags.WATER);
            ownerState = new OwnerState(CompanionWorldAdapter.vec(phantomEye), Vec.ZERO, phantomYaw, 0f,
                    false, false, true, 70f, wet);
            hurt = false;
            held = CompanionWorld.Held.NONE;
            points = new ArrayList<>(companions);
        }
        if (isLocal() && !devPoints.isEmpty()) {
            points = new ArrayList<>(points);
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
            switch (group.kind()) {
                case SCHOOL -> {
                    school = group.school(TUNABLES, temperament, world);
                    agent = school.leader();
                }
                case GROUND -> {
                    benthic = group.ground(TUNABLES, temperament, world);
                    agent = benthic.leader();
                }
                // A null gait is the old constructor's path: every non-benthic class is unchanged.
                default -> agent = new CompanionAgent(TUNABLES, group.seed(), locomotion, lengths[0], temperament,
                        gait, driftStyle, world);
            }
        } else if (school != null) {
            school.step(world);
        } else if (benthic != null) {
            benthic.step(world);
        } else {
            agent.step(world);
        }
        stepNanos = System.nanoTime() - start;
        // Every member's pose hints follow the leader's mind (a group thinks as one).
        for (CompanionRender render : renders) render.tick(agent);
    }

    /** Forgets every sampled block (a block changed nearby, or a chunk arrived). */
    void invalidateBlocks() {
        if (school != null) school.invalidateBlocks();
        else if (benthic != null) benthic.invalidateBlocks();
        else if (agent != null) agent.voxels().invalidate();
    }

    /**
     * This companion as another companion's point of interest: the lead fish, with a stable id
     * that can't collide with an entity's ({@link ClientCompanions} derives it from the owner and slot).
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
        if (school != null) school.interpolate(partialTick);
        else if (benthic != null) benthic.interpolate(partialTick);
        else agent.interpolate(partialTick);
        float gameTime = gameTime(partialTick);
        boolean glow = glowsNow();
        boolean drew = false;

        for (int i = 0; i < agentCount(); i++) {
            CompanionAgent a = member(i);
            if (drawsOnPerch(i)) continue;
            Vec pos = a.renderPos();
            float yaw = a.renderYaw();
            float face = a.renderFace();
            float pitch = a.renderPitch();
            boolean onFloor = false;
            if (handAttached(a)) {
                // Riding the hand in first person: attached to the view at frame rate (the parrot
                // pattern), so a mouse flick doesn't leave it a tick behind the camera.
                OwnerState o = frameOwner(partialTick);
                pos = PerchBody.slotPoint(o, a.perchSlot());
                yaw = face = o.yawDeg();
                pitch = 0f;
            } else if (walksOnGround(a)) {
                // Its feet, not its centre: the pose stands on the floor by its own lift.
                pos = new Vec(pos.x(), pos.y() - a.radius() - LocalVoxels.GROUND_LIFT, pos.z());
                onFloor = true;
            }
            // The walk's push-off and the crouch/landing squash drive the same silhouette envelope.
            float shape = gait != null ? Math.max(a.perchSquash(), a.groundShape()) : a.perchSquash();
            drew |= renders[i].submit(poseStack, nodes, cam, i, pos, yaw, face, pitch, a.renderBank(),
                    a.renderAppear(), a.renderTailPhase(), a.speedFactor(), a.renderSqueeze(),
                    shape, a.riding(), gameTime, light(pos, glow), onFloor,
                    onFloor && gait == GroundGait.RIPPLE);
        }
        if (school != null) {
            for (int i = 0; i < school.followerCount(); i++) {
                CompanionGroup.Follower f = school.follower(i);
                Vec pos = f.renderPos();
                drew |= renders[i + 1].submit(poseStack, nodes, cam, i + 1, pos, f.renderYaw(), f.renderYaw(),
                        f.renderPitch(), f.renderBank(), f.renderAppear(), f.renderTailPhase(), f.speedFactor(), 0f, 0f,
                        false, gameTime, light(pos, glow), false, false);
            }
        }
        return drew;
    }

    /**
     * Each fish's own name above itself while its owner sneaks (party spec D6): the bonded stack's
     * custom name, else the species name, in vanilla's discreet sneaking name-tag style. Perched fish
     * get theirs too, from the sim's place for them. The stack is camera-relative.
     */
    void submitNames(PoseStack poseStack, SubmitNodeCollector nodes, Vec3 cam, float partialTick) {
        if (agent == null || owner == null || !owner.isCrouching() || owner.isInvisible()) return;
        CameraRenderState camera = Minecraft.getInstance().gameRenderer.getGameRenderState().levelRenderState.cameraRenderState;
        for (int i = 0; i < bodyCount(); i++) {
            Vec pos;
            double radius;
            float appear;
            if (i < agentCount()) {
                CompanionAgent a = member(i);
                pos = handAttached(a) ? PerchBody.slotPoint(frameOwner(partialTick), a.perchSlot()) : a.renderPos();
                radius = a.radius();
                appear = a.renderAppear();
            } else {
                CompanionGroup.Follower f = school.follower(i - 1);
                pos = f.renderPos();
                radius = f.radius();
                appear = f.renderAppear();
            }
            if (appear < 0.5f) continue;
            double dx = pos.x() - cam.x, dy = pos.y() - cam.y, dz = pos.z() - cam.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > NAME_RANGE * NAME_RANGE) continue;
            poseStack.pushPose();
            poseStack.translate(dx, dy, dz);
            // The renderer lifts every tag by half a block above its attachment point.
            nodes.submitNameTag(poseStack, new Vec3(0, radius + NAME_GAP - 0.5, 0), 0, stacks[i].getHoverName(),
                    false, light(pos, false), distSq, camera);
            poseStack.popPose();
        }
    }

    /** Names show within this range of the camera, blocks (vanilla's sneaking tags fade far closer). */
    private static final double NAME_RANGE = 32.0;
    /** Space between a fish's top and its name, blocks. */
    private static final double NAME_GAP = 0.15;

    /**
     * A benthic with a gait out of the water and off its owner: walking, crouching, or in the air
     * of a hop or a fall from the ground. In water it swims, drawn about its centre as before.
     */
    private boolean walksOnGround(CompanionAgent a) {
        return gait != null && !a.riding() && a.perchMode() != PerchBody.Mode.SWIM;
    }

    /** How many fish this companion draws: every member, a school's followers included. */
    public int bodyCount() {
        return stacks.length;
    }

    /**
     * How many members are full agents (perchers that ride the perch layer): a ground formation's
     * 1–3, else 1 — a school's followers are lighter bodies, drawn apart.
     */
    int memberCount() {
        return agentCount();
    }

    private int agentCount() {
        return benthic != null ? benthic.count() : 1;
    }

    /** Member {@code i}: 0 is the lead. */
    CompanionAgent member(int i) {
        return benthic != null ? benthic.member(i) : agent;
    }

    /** A percher riding its owner's hand, seen by that owner in first person. */
    private boolean handAttached(CompanionAgent a) {
        return a.riding() && a.perchSlot().hand() && isLocal()
                && Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    /** The owner's state at this frame's camera, for attachments drawn at frame rate. */
    private OwnerState frameOwner(float partialTick) {
        Vec3 eye = owner.getEyePosition(partialTick);
        return new OwnerState(CompanionWorldAdapter.vec(eye), lastOwner.velocity(), owner.getViewYRot(partialTick),
                owner.getViewXRot(partialTick), false, false, true, lastOwner.fovDeg(), false);
    }

    /** True while the player render layer draws member {@code i} on its owner's body. */
    boolean drawsOnPerch(int i) {
        if (agent == null || owner == null || owner.isInvisible()) return false;
        CompanionAgent a = member(i);
        return a.riding() && !a.perchSlot().hand();
    }

    /** True while the player render layer draws any member on its owner's body. */
    boolean drawsOnPerch() {
        for (int i = 0; i < agentCount(); i++) if (drawsOnPerch(i)) return true;
        return false;
    }

    /** Member {@code i}'s perch slot for {@link CompanionPerchLayer}: a group's members ride left, right, head. */
    PerchBody.Slot perchSlot(int i) {
        return member(i).perchSlot();
    }

    Locomotion locomotion() { return locomotion; }

    /** Height of member {@code i}'s perched sprite centre above the surface its feet rest on. */
    float perchFloorLift(int i) { return perchPoses[i].floorLift(); }

    /**
     * Draws perched member {@code i} in the pose space the player layer has set up: its idle
     * animation, the anchored duck, a head tilt from the pose hint, and the appear fade.
     */
    void submitPerched(int i, PoseStack poseStack, SubmitNodeCollector nodes, int light, float partialTick,
                       float extraYawDeg) {
        float appear = member(i).renderAppear();
        if (appear <= 0f) return;
        CompanionRender render = renders[i];
        poseStack.pushPose();
        if (appear < 1f) poseStack.scale(appear, appear, appear);
        float tilt = render.hintTilt(partialTick);
        if (tilt != 0f) poseStack.mulPose(Axis.ZP.rotationDegrees(tilt));
        // Members breathe out of step: each idles on its own offset clock.
        perchPoses[i].submit(poseStack, nodes, light, gameTime(partialTick) + i * MEMBER_IDLE_OFFSET, extraYawDeg,
                render.hintPitch(partialTick),
                render.retract(partialTick));
        poseStack.popPose();
    }

    /** Ticks between group members' perched idle clocks, so three riders don't breathe in unison. */
    private static final float MEMBER_IDLE_OFFSET = 23f;

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

    /** Dev-only: the lead fish itself — the dev commands clone it to park a phantom partner. */
    public ItemStack devStack() { return stacks[0]; }

    @Nullable public CompanionAgent agent() { return agent; }

    public Temperament temperament() { return temperament; }

    void devHurt() { devHurt = true; }

    void devDrowsy() {
        if (agent != null) agent.devDrowsy();
    }

    /** Dev (the viewer's G): every member back down to the ground. False if this isn't a ground walker. */
    boolean devGround() {
        if (gait == null || agent == null) return false;
        if (benthic != null) benthic.devGround();
        else agent.devGround();
        return true;
    }

    /** Dev (the viewer's P): climb onto the perch, members one after another. */
    boolean devClimb() {
        if (gait == null || agent == null) return false;
        if (benthic != null) benthic.devPerch();
        else agent.devPerch();
        return true;
    }

    /** Dev (the viewer's B): knocked off the perch, once attached and out of cooldown. */
    boolean devTumble() {
        if (gait == null || agent == null) return false;
        if (benthic != null) benthic.devTumble();
        else agent.devTumble();
        return true;
    }

    /** Whether the owner can see the lead fish this tick (the sim's own judgement). */
    boolean seenByOwner() {
        return agent != null && agent.seen();
    }

    /** The display class as a stable name for logs, overlays and checks: "school of 3", "ground line of 2", "glide". */
    public String displayClassName() {
        return switch (group.kind()) {
            case SCHOOL -> "school of " + bodyCount();
            case GROUND -> "ground " + group.formation().name().toLowerCase(Locale.ROOT) + " of " + bodyCount();
            case SOLO -> locomotion.name().toLowerCase(Locale.ROOT);
        };
    }

    /** One line naming this unit for the overlay's party header: "slot 1+2: bluegill school of 2". */
    public String partyLine() {
        StringBuilder slots = new StringBuilder();
        for (int s : group.slots()) slots.append(slots.isEmpty() ? "" : "+").append(s + 1);
        return "slot " + slots + ": " + BuiltInRegistries.ITEM.getKey(stacks[0].getItem()).getPath() + " "
                + displayClassName();
    }

    /** The debug overlay's lines (goal, drives, pose hint, temperament). */
    List<String> debugLines() {
        if (agent == null) return List.of("companion: not stepped yet");
        CompanionAgent a = agent;
        String perch = a.perchState() == null ? "" : "  perch " + a.perchState().toLowerCase(Locale.ROOT);
        String ground = a.footTrail().isEmpty() ? "" : String.format(Locale.ROOT,
                "  land %s lag %.1f trail %d %s", a.landMode(), a.groundLag(), a.footTrail().size(),
                a.groundReachable() ? "reachable" : "stranded");
        StringBuilder sizes = new StringBuilder();
        for (float l : lengths) sizes.append(sizes.isEmpty() ? "" : "/").append(String.format(Locale.ROOT, "%.2f", l));
        List<String> lines = new ArrayList<>(List.of(
                String.format(Locale.ROOT, "%s  %s  temperament %s  %s blocks",
                        BuiltInRegistries.ITEM.getKey(stacks[0].getItem()).getPath(), displayClassName(),
                        temperament.name(), sizes),
                String.format(Locale.ROOT, "goal %s (%.1f s)%s  pose %s  fade %s", a.goal(),
                        a.goalTicks() / 20f, perch + ground, a.poseHint(), a.fade()),
                "curiosity " + bar(a.curiosity()) + "  wariness " + bar(a.wariness())
                        + "  rest " + bar(a.rest()) + (a.resting() ? " settled" : ""),
                "proximity " + bar(a.proximity()) + "  attention " + bar(a.attention())
                        + "  seen " + (a.seen() ? "yes" : "no (" + a.unseenTicks() / 20 + " s)"),
                String.format(Locale.ROOT, "held %s  points %d  owner %.1f blocks  step %d us",
                        lastHeld, lastPoints, a.ownerDistance(), stepNanos / 1000)));
        if (gait != null) {
            lines.add(String.format(Locale.ROOT, "ground gait %s  group %d %s  beat %s (overtakes %d roadsides %d)"
                            + "  climbs %d dismounts %d tumbles %d",
                    gait.name().toLowerCase(Locale.ROOT), agentCount(), group.formation().name().toLowerCase(Locale.ROOT),
                    a.beat() == null ? "-" : a.beat().toLowerCase(Locale.ROOT), a.overtakes(), a.roadsides(),
                    a.climbs(), a.dismounts(), a.tumbles()));
            StringBuilder members = new StringBuilder("members");
            for (int i = 0; i < agentCount(); i++) {
                CompanionAgent m = member(i);
                members.append(String.format(Locale.ROOT, "  #%d %s lag %.1f%s", i, m.landMode(), m.groundLag(),
                        m.riding() ? " on " + m.perchSlot().name().toLowerCase(Locale.ROOT) : ""));
            }
            lines.add(members.toString());
        }
        return lines;
    }

    private static String bar(float v) {
        int n = Mth.clamp(Math.round(v * 10), 0, 10);
        return "[" + "|".repeat(n) + ".".repeat(10 - n) + "]";
    }
}
