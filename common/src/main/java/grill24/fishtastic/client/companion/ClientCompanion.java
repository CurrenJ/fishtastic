package grill24.fishtastic.client.companion;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishsim.core.Locomotion;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.client.FishtasticClientConfig;
import grill24.fishtastic.client.renderer.CompanionFlock;
import grill24.fishtastic.client.renderer.CompanionFishPose;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Predicate;

/**
 * One companion fish on the client — a player's equipped companion, drawn for its owner and for
 * everyone who can see them (docs/fish-companions.md §3, §3.1, §5.1). {@link ClientCompanions} owns
 * the instances; this class is the per-fish state.
 *
 * <p>Geometry: the fish lives in a small invisible tank ({@link CompanionFlock}) whose centre moves
 * along the display-mode path — an orbit for free swimmers, high wide arcs for gliders, a spring
 * behind one shoulder for drifters. The two attach classes (benthic, anchored) sit on the owner's
 * body instead: they are drawn by a player render layer ({@code CompanionPerchLayer}) and only
 * enter the world path to hop off. In water every class drops its air anchor and the box swims
 * beside the owner.
 *
 * <p><b>Peeks</b> (§3.1, R2.1) are the reason the owner actually sees their companion in first
 * person: when nothing else is holding the fish near the view, every class periodically routes
 * itself through the front of the owner's view — swimmers swim there, perchers hop off and back.
 * The schedule is seeded from the fish so a self-test run is reproducible, and it is rate-limited
 * by a cooldown so it never reads as nagging. The same "is the owner seeing it" signal drives the
 * schedule, so the self-test's visibility count measures the mechanism directly.
 *
 * <p>Only the local player's companion docks: it blends over {@link #DOCK_TICKS} toward a point
 * {@link #DOCK_DEPTH} blocks in front of the camera that projects onto the minigame's dock anchor,
 * and toward a miniature scale. Once fully docked the box is camera-locked and the HUD pass draws
 * it instead. When the owner looks down, the companion hops to a point above the hotbar in front
 * of the camera (§3.1) — drawn by the world pass rather than the first-person hand pass, which
 * renders companion item geometry as a shadow-only silhouette under Iris shaderpacks.
 */
public final class ClientCompanion {
    private static final int DOCK_TICKS = 20;
    static final float DOCK_DEPTH = 2.0f;
    /** Smallest length a companion is drawn at while following, blocks — true-to-scale small fish are specks. */
    private static final float MIN_FOLLOW_LENGTH = 0.25f;
    /** Docked fish length in engine units, whatever its true size (the dock tank is 0.7 wide). */
    private static final float DOCK_FISH_LENGTH = 0.3f;

    // ── Display-mode paths (§3 table). Blocks and seconds. ─────────────────────
    private static final float FREE_RADIUS = 1.2f, FREE_DROP = 0.15f, FREE_LAP_SECONDS = 26f;
    private static final float GLIDE_RADIUS = 2.2f, GLIDE_RISE = 1.55f, GLIDE_LAP_SECONDS = 44f;
    private static final float DRIFT_BACK = 0.5f, DRIFT_SIDE = 0.5f, DRIFT_DROP = 0.3f;
    private static final float WATER_AHEAD = 0.85f, WATER_SIDE = 0.7f, WATER_DROP = 0.15f;
    private static final float PEEK_FORWARD = 1.35f, PEEK_DROP = 0.15f;
    private static final float SWOOP_RISE = -0.75f;
    private static final double PHANTOM_RADIUS = 1.2;

    // ── Timings (ticks). ───────────────────────────────────────────────────────
    private static final int PEEK_OUT = 8, PEEK_HOLD = 70, PEEK_BACK = 10;
    private static final int PEEK_COOLDOWN_MIN = 300, PEEK_COOLDOWN_VAR = 600;
    private static final int PEEK_UNSEEN_TICKS = 200;
    private static final int PEEK_MIN_GAP = 200;
    private static final int TRICK_COOLDOWN_MIN = 500, TRICK_COOLDOWN_VAR = 700, TRICK_LENGTH = 50;
    private static final float HAND_HOP_SPEED = 0.15f;
    private static final float HAND_PITCH = 52f;
    /** How close the look-down hop sits: near enough to share the hand's screen space. */
    private static final float HAND_HOP_DEPTH = 0.9f;
    /** A hostile mob this close makes an anchored companion duck in (§3). */
    private static final double DUCK_RANGE = 3.0;

    private static final int PEEK_IDLE = 0, PEEK_OUT_PHASE = 1, PEEK_HOLD_PHASE = 2, PEEK_BACK_PHASE = 3;

    /**
     * One entry of the held-item reaction table (§3.1): the item test, where the companion goes in
     * view space (right / up / forward of the owner's eyes; null keeps the normal display-mode
     * anchor, which on a perch means "stays put"), and a pose tilt. The list is checked in order,
     * so put the specific items first — the food fallback matches anything edible.
     */
    private record Reaction(String name, Predicate<ItemStack> match, @Nullable Vec3 offset, float tiltDeg) {}

    /** The owner, or null for a dev phantom parked at {@link #phantomEye}. */
    @Nullable private final Player owner;
    @Nullable private final Vec3 phantomEye;
    private final CompanionFlock flock;
    private final CompanionFishPose pose;
    private final ItemStack stack;
    private final int seed;
    private final boolean percher;
    private final List<Reaction> reactions;

    @Nullable private Vec3 center, prevCenter;
    /** DRIFT's loose spring carries a velocity; the other classes ease positionally. */
    private Vec3 springVel = Vec3.ZERO;

    private float dock, prevDock;

    // ── Behaviour state ────────────────────────────────────────────────────────
    private double orbitAngle;
    private int peekPhase = PEEK_IDLE;
    private int peekTimer;
    private float peekBlend;
    private int peekCount;
    private long peekStartGuard = Long.MIN_VALUE / 2;
    /** A percher's (or reaction's) way off the body: 0 on the body, 1 out at the target point. */
    private float outBlend, prevOutBlend;
    /** Perchers: where the hop started (world), so the hop path stays continuous. */
    private Vec3 hopStart = Vec3.ZERO;
    private float reactionBlend;
    private int trickCooldown;
    private int trickTimer;
    private int trickIndex;
    private float spinDeg;
    private float duck, prevDuck;
    private String reactionName = "";
    private float reactionTilt;
    private float handHop, prevHandHop;
    /** Client tick at which the owner last had this companion on screen (for peek scheduling). */
    private long lastSeenTick = Long.MIN_VALUE / 2;
    private boolean visibleNow;
    private boolean firstPersonHidden;

    // Per-frame geometry, written by the world pass (it runs first) and read by the HUD pass.
    private Vec3 frameCenter = Vec3.ZERO;
    private float frameScale = 1f;
    private float frameFishSize = 1f;
    private float frameYaw;
    private float frameTilt;
    private boolean frameDocked;

    ClientCompanion(@Nullable Player owner, @Nullable Vec3 phantomEye, ItemStack stack, Level level) {
        this.owner = owner;
        this.phantomEye = phantomEye;
        this.stack = stack.copy();
        this.seed = this.stack.hashCode();
        this.flock = new CompanionFlock(stack, level);
        this.pose = new CompanionFishPose(stack, level);
        // A dev phantom has no body to sink into (the perch draw needs an owner); it swims its box
        // whatever the species.
        this.percher = owner != null
                && (flock.locomotion() == Locomotion.BENTHIC || flock.locomotion() == Locomotion.ANCHORED);
        this.orbitAngle = Math.abs(seed % 1000) / 1000.0 * Math.PI * 2;
        this.reactions = List.of(
                new Reaction("rod", s -> s.is(Items.FISHING_ROD) || s.is(FishtasticItemTags.FISHING_RODS),
                        new Vec3(0.38, -0.18, 1.05), 0f),
                new Reaction("bait", s -> s.is(FishtasticItemTags.FISHING_BAIT), new Vec3(0.12, -0.28, 0.7), -15f),
                new Reaction("same_species", s -> s.is(this.stack.getItem()), new Vec3(0.3, -0.3, 0.8), 0f),
                new Reaction("predator", s -> s.is(FishtasticItemTags.PREDATOR_FISH), new Vec3(0.1, 0.2, -0.6), 0f),
                new Reaction("name_tag", s -> s.is(Items.NAME_TAG), null, 25f),
                new Reaction("food", s -> s.get(DataComponents.FOOD) != null, new Vec3(0.15, -0.2, 0.9), -5f));
        Random rng = new Random(seed);
        // The first peek waits like every other one (a random 7.5–37.5 s), so a freshly bonded
        // companion neither peeks into the owner's face at once nor follows the same clock as its
        // siblings — the seeds differ per fish, so the schedules do too.
        this.peekTimer = PEEK_COOLDOWN_MIN / 2 + rng.nextInt(PEEK_COOLDOWN_VAR);
        this.trickCooldown = TRICK_COOLDOWN_MIN + rng.nextInt(TRICK_COOLDOWN_VAR);
        // A freshly bonded companion counts as "just seen": it should not peek into the owner's
        // face the instant it is equipped. From here the unseen-for-10 s rule schedules the first.
        this.lastSeenTick = level.getGameTime();
    }

    ItemStack stack() { return stack; }

    @Nullable Player owner() { return owner; }

    boolean isDocked() { return frameDocked; }

    float dockProgress() { return dock; }

    public boolean isLocal() {
        Minecraft mc = Minecraft.getInstance();
        return owner != null && owner == mc.player;
    }

    // ── Tick ───────────────────────────────────────────────────────────────────

    /**
     * Client tick (20 Hz). {@code anchor} is the minigame's dock anchor when this is the local
     * player's companion and a minigame is up, otherwise null.
     */
    void tick(@Nullable float[] anchor) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        boolean local = isLocal();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        boolean inWater = owner != null && owner.isEyeInFluid(FluidTags.WATER);

        prevDock = dock;
        float dir = anchor != null ? 1f : -1f;
        dock = Mth.clamp(dock + dir / DOCK_TICKS, 0f, 1f);
        boolean docking = dock > 0f;
        boolean fullyDocked = dock >= 1f && anchor != null;

        prevOutBlend = outBlend;
        prevHandHop = handHop;
        if (!docking) {
            updateBehaviour(mc, now, inWater, local);
        }
        // The hand hop: local only, first person, looking down (§3.1). Docking wins over it.
        boolean wantHand = local && !docking && firstPerson
                && !FishtasticClientConfig.hideOwnCompanionFirstPerson()
                && mc.player != null && mc.player.getXRot() > HAND_PITCH;
        handHop = approach(handHop, wantHand ? 1f : 0f, HAND_HOP_SPEED);
        outBlend = Math.max(peekBlend, reactionBlend);
        float out = outAmount();
        float prevOut = Math.max(prevOutBlend, prevHandHop);
        if (out > 0f && prevOut <= 0f && center != null) hopStart = center;

        // The invisible tank swaps between air, water and the docked model.
        if (anchor != null) {
            flock.setMode(CompanionFlock.Mode.DOCKED);
        } else if (!docking) {
            flock.setMode(inWater ? CompanionFlock.Mode.WATER : CompanionFlock.Mode.FOLLOW);
        }

        moveCenter(mc, inWater, docking);
        flock.setBiasFade(1f - smooth(dock));
        flock.step();

        updateVisibility(mc, local, firstPerson, now, inWater);
    }

    /** Peeks, tricks, reactions and the duck envelope; everything that is not motion. */
    private void updateBehaviour(Minecraft mc, long now, boolean inWater, boolean local) {
        prevDuck = duck;

        updateReaction(mc);
        updateDuck(mc);

        // Tricks (§3 table's "tricks" column): a quick lap, a swoop, a rise, a spin. Anchored has
        // no trick of its own — its moment is the duck. Not in water, where every class swims.
        if (trickTimer > 0) {
            trickTimer--;
            if (trickTimer == 0) {
                spinDeg = 0f;
                trickIndex++;
                trickCooldown = TRICK_COOLDOWN_MIN + (int) (hash(trickIndex * 31 + 7) % TRICK_COOLDOWN_VAR);
            }
        } else if (--trickCooldown <= 0 && !inWater) {
            trickCooldown = TRICK_COOLDOWN_MIN + (int) (hash(trickIndex * 17 + 3) % TRICK_COOLDOWN_VAR);
            trickTimer = TRICK_LENGTH;
        }
        if (trickTimer > 0 && flock.locomotion() == Locomotion.BENTHIC) {
            spinDeg = (1f - trickTimer / (float) TRICK_LENGTH) * 360f; // one full turn in place
        }

        // Peek phase machine (§3.1). Peeks stay out of the way while a reaction already holds the
        // fish near the view, never run in water — the whole point is the companion the owner
        // cannot normally see — and pause while the owner is in the minigame, where the bar owns
        // the middle of the screen (the wait-at-the-bobber setting leaves the fish following, not
        // photobombing the fight). The timer simply holds at its current value.
        boolean ownerFishing = isLocal() && ClientCompanions.activeMinigame() != null;
        boolean mayPeek = !inWater && reactionName.isEmpty() && !ownerFishing;
        if (!mayPeek && peekPhase == PEEK_IDLE) peekBlend = 0f;
        switch (peekPhase) {
            case PEEK_IDLE -> {
                peekBlend = 0f;
                if (mayPeek) {
                    boolean unseen = local && now - lastSeenTick > PEEK_UNSEEN_TICKS;
                    if ((--peekTimer <= 0 || unseen) && now - peekStartGuard > PEEK_MIN_GAP) {
                        peekPhase = PEEK_OUT_PHASE;
                        peekTimer = PEEK_OUT;
                        peekCount++;
                    }
                }
            }
            case PEEK_OUT_PHASE -> {
                peekBlend = 1f - peekTimer / (float) PEEK_OUT;
                if (--peekTimer <= 0) {
                    peekPhase = PEEK_HOLD_PHASE;
                    peekTimer = PEEK_HOLD;
                }
            }
            case PEEK_HOLD_PHASE -> {
                peekBlend = 1f;
                if (--peekTimer <= 0) {
                    peekPhase = PEEK_BACK_PHASE;
                    peekTimer = PEEK_BACK;
                }
            }
            case PEEK_BACK_PHASE -> {
                peekBlend = peekTimer / (float) PEEK_BACK;
                if (--peekTimer <= 0) {
                    peekPhase = PEEK_IDLE;
                    peekBlend = 0f;
                    peekStartGuard = now;
                    peekTimer = PEEK_COOLDOWN_MIN + (int) (hash(peekCount) % PEEK_COOLDOWN_VAR);
                }
            }
            default -> throw new IllegalStateException("bad peek phase " + peekPhase);
        }
    }

    private long hash(long value) {
        long h = value * 6364136223846793005L + seed;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        return Math.abs(h ^ (h >>> 33));
    }

    /** The held-item reaction for this tick, if any (§3.1 table). First match wins. */
    private void updateReaction(Minecraft mc) {
        reactionName = "";
        reactionTilt = 0f;
        if (owner != null) {
            ItemStack held = owner.getMainHandItem();
            if (!held.isEmpty()) {
                for (Reaction reaction : reactions) {
                    if (reaction.match.test(held)) {
                        reactionName = reaction.name;
                        reactionTilt = reaction.tiltDeg;
                        break;
                    }
                }
            }
        }
        // A reaction with somewhere to go pulls the fish over (perchers hop out); a tilt-only
        // reaction (name tag, food) leaves the display-mode anchor alone.
        float want = reactionOffset() != null ? 1f : 0f;
        reactionBlend = approach(reactionBlend, want, 0.12f);
    }

    @Nullable
    private Vec3 reactionOffset() {
        if (reactionName.isEmpty()) return null;
        for (Reaction reaction : reactions) {
            if (reaction.name.equals(reactionName)) return reaction.offset;
        }
        return null;
    }

    /** The anchored class's duck-in when a hostile mob is close (§3). */
    private void updateDuck(Minecraft mc) {
        float target = 0f;
        if (owner != null && mc.level != null && flock.locomotion() == Locomotion.ANCHORED) {
            var mobs = mc.level.getEntities(owner, owner.getBoundingBox().inflate(DUCK_RANGE),
                    e -> e instanceof net.minecraft.world.entity.monster.Enemy && e.isAlive());
            target = mobs.isEmpty() ? 0f : 1f;
        }
        duck = approach(duck, target, 0.1f);
    }

    // ── Motion ─────────────────────────────────────────────────────────────────

    /** How far the fish is pulled off its normal anchor pattern: a peek, a reaction, or the hand hop. */
    private float outAmount() {
        return Math.max(Math.max(outBlend, handHop), 0f);
    }

    private void moveCenter(Minecraft mc, boolean inWater, boolean docking) {
        if (center == null) {
            center = prevCenter = anchorTarget(mc, inWater, docking);
            return;
        }
        prevCenter = center;
        if (percher && !inWater && !docking) {
            // Attached to the body: rigid on the perch, a hop path while out.
            Vec3 attach = attachPoint(mc);
            float out = outAmount();
            if (out > 0f) {
                float p = smooth(out);
                Vec3 target = outPoint(mc);
                center = hopStart.lerp(target, p).add(0, Math.sin(Math.PI * out) * 0.35, 0);
            } else {
                center = attach;
            }
            return;
        }
        Vec3 target = anchorTarget(mc, inWater, docking);
        if (flock.locomotion() == Locomotion.DRIFT && !docking) {
            // "Behind one shoulder on a loose spring" — velocity-based, so it lags and settles.
            springVel = springVel.add(target.subtract(center).scale(0.6)).scale(0.82);
            center = center.add(springVel.scale(0.05));
        } else {
            center = center.lerp(target, 0.35);
        }
    }

    /** Where the companion should be this tick, before the spring/ease is applied. */
    private Vec3 anchorTarget(Minecraft mc, boolean inWater, boolean docking) {
        if (owner == null) return phantomAnchor();
        if (docking) {
            // Stay on the air path while docking; the blend toward the dock point is applied at
            // render time so the move is a real swim.
            return baseAirAnchor(mc);
        }
        Vec3 base = inWater ? waterAnchor(mc) : baseAirAnchor(mc);
        Vec3 reaction = reactionOffset();
        if (reaction != null) base = fishCentred(viewPoint(mc, reaction));
        if (peekBlend > 0f) {
            // Aim the BOX at the peek point minus most of the fish's own offset inside it, so the
            // fish itself — not the empty box centre — lands in view. The residual 30% of the
            // offset is what keeps it a swim rather than a hover.
            Vec3 anchor = fishCentred(peekPoint(mc));
            base = base.lerp(anchor, smooth(peekBlend));
        }
        if (handHop > 0f && isLocal()) {
            // "Hops onto your hand / just above the hotbar" when the owner looks down (§3.1). Drawn
            // by the world pass rather than the first-person hand pass: the hand pass renders
            // companion item geometry as a shadow-only silhouette under Iris shaderpacks (verified
            // against ComplementaryReimagined), and at this range the world pass cannot be occluded
            // by terrain the owner is standing on top of.
            base = base.lerp(fishCentred(handHopPoint(mc)), smooth(handHop));
        }
        return base;
    }

    /** The lead fish's offset from the box centre, rotated into the world by the box's yaw. */
    private Vec3 worldOffsetOfFish() {
        return rotateY(fishOffset().scale(frameScale), -boxYaw());
    }

    /**
     * The box's yaw this moment — the camera's for the local player, its owner's view yaw for
     * anyone else (the same choice {@link #computeFrame} makes, but read live so the fish-centred
     * anchors are right even on the first tick out of a long perch).
     */
    private float boxYaw() {
        if (owner == null) return 0f;
        return owner == Minecraft.getInstance().player ? Minecraft.getInstance().gameRenderer.getMainCamera().yRot()
                : owner.getViewYRot(1f);
    }

    /**
     * The correction that turns "put the box here" into "put the FISH here": the fish's own offset
     * from the box centre, held back a fraction so some of its wander (a swim, not a hover)
     * survives. The vertical is compensated in full — a crawler rides its box floor, so a partial
     * correction would park every benthic peek near the bottom edge of the view.
     */
    private Vec3 fishCentred(Vec3 boxPoint) {
        Vec3 off = worldOffsetOfFish();
        return boxPoint.subtract(new Vec3(off.x * 0.7, off.y, off.z * 0.7));
    }

    /** {@code Matrix4f.rotateY} applied to a vector — the box pose's own rotation convention. */
    private static Vec3 rotateY(Vec3 v, float deg) {
        double a = Math.toRadians(deg);
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        return new Vec3(v.x * c + v.z * s, v.y, -v.x * s + v.z * c);
    }

    /** The display-mode path in air (§3 table). */
    private Vec3 baseAirAnchor(Minecraft mc) {
        Vec3 eye = owner.getEyePosition();
        Vec3 forward = flatLook();
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        return switch (flock.locomotion()) {
            case GLIDE -> {
                advanceOrbit(GLIDE_LAP_SECONDS);
                float rise = GLIDE_RISE;
                if (trickTimer > 0) {
                    // The low swoop: dip toward the floor and back over the trick's duration.
                    float t = 1f - trickTimer / (float) TRICK_LENGTH;
                    rise = Mth.lerp((float) Math.sin(Math.PI * t), GLIDE_RISE, SWOOP_RISE);
                    spawnSwoopDust(mc);
                }
                yield eye.add(orbitDirection(forward, right).scale(GLIDE_RADIUS)).add(0, rise, 0);
            }
            case DRIFT -> {
                Vec3 base = eye.add(forward.scale(-DRIFT_BACK)).add(right.scale(DRIFT_SIDE)).add(0, -DRIFT_DROP, 0);
                if (trickTimer > 0) {
                    // Rises and settles.
                    float t = 1f - trickTimer / (float) TRICK_LENGTH;
                    base = base.add(0, Math.sin(Math.PI * t) * 0.45, 0);
                }
                yield base;
            }
            // Only reachable while out (a hop, or a reaction pulling them off); hover ahead.
            case BENTHIC, ANCHORED -> outPoint(mc);
            default -> {
                advanceOrbit(trickTimer > 0 ? FREE_LAP_SECONDS / 3f : FREE_LAP_SECONDS);
                yield eye.add(orbitDirection(forward, right).scale(FREE_RADIUS)).add(0, -FREE_DROP, 0);
            }
        };
    }

    /** In water every class swims beside the owner (§3, E1). */
    private Vec3 waterAnchor(Minecraft mc) {
        Vec3 forward = flatLook();
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        // Same fish-centred correction as a peek: the box lands so the fish — not the box centre —
        // stays at the owner's side, rather than wandering a body-length off to the left.
        return fishCentred(owner.getEyePosition().add(forward.scale(WATER_AHEAD)).add(right.scale(WATER_SIDE))
                .add(0, -WATER_DROP, 0));
    }

    /**
     * Where the look-down hop lands: a fixed spot on the screen just above the hotbar, unprojected
     * at close range. Screen-anchored rather than a view-space offset because looking down rotates
     * "forward" almost straight down, which would bury a fixed offset under the bottom edge.
     */
    private Vec3 handHopPoint(Minecraft mc) {
        CameraRenderState cs = mc.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState;
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        // Slightly toward the main hand, and around the hotbar's top edge.
        return unproject(mc.gameRenderer.getMainCamera(), cs.projectionMatrix, w * 0.56f, h * 0.78f, w, h, HAND_HOP_DEPTH);
    }

    /** A point in the owner's view where the whole point is to be seen. */
    private Vec3 peekPoint(Minecraft mc) {
        return viewPoint(mc, new Vec3(Math.sin(mc.level.getGameTime() * 0.05) * 0.12, -PEEK_DROP, PEEK_FORWARD));
    }

    /** Where an out-of-body percher (or a reaction) wants to be: its reaction point, or in view. */
    private Vec3 outPoint(Minecraft mc) {
        Vec3 reaction = reactionOffset();
        return reaction != null ? viewPoint(mc, reaction) : peekPoint(mc);
    }

    /**
     * View-space (right / up / forward of the eyes) point. {@code look × up} is the right vector —
     * facing south it comes out west, which is the right hand side (east is on your left).
     */
    private Vec3 viewPoint(Minecraft mc, Vec3 offset) {
        Vec3 look = owner.getLookAngle();
        Vec3 up = owner.getUpVector(1f);
        Vec3 right = look.cross(up);
        return owner.getEyePosition().add(right.scale(offset.x)).add(up.scale(offset.y)).add(look.scale(offset.z));
    }

    private Vec3 flatLook() {
        Vec3 look = owner.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        return flat.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : flat.normalize();
    }

    private void advanceOrbit(float lapSeconds) {
        orbitAngle += (float) (2 * Math.PI) / (lapSeconds * 20f);
    }

    private Vec3 orbitDirection(Vec3 forward, Vec3 right) {
        return forward.scale(Math.sin(orbitAngle)).add(right.scale(Math.cos(orbitAngle)));
    }

    /** The point on the owner's body a benthic percher sits at — head or a shoulder, per species. */
    private Vec3 perchPoint(Minecraft mc) {
        Vec3 eye = owner.getEyePosition();
        Vec3 forward = flatLook();
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        int slot = Math.floorMod(seed, 3);
        return switch (slot) {
            case 0 -> eye.add(right.scale(0.3)).add(forward.scale(0.08)).add(0, -0.32, 0);   // right shoulder
            case 1 -> eye.add(right.scale(-0.3)).add(forward.scale(0.08)).add(0, -0.32, 0);  // left shoulder
            default -> eye.add(forward.scale(0.06)).add(0, 0.16, 0);                          // head
        };
    }

    /** The point on the owner's back an anchored companion pokes out of (§3). */
    private Vec3 backPoint() {
        Vec3 eye = owner.getEyePosition();
        Vec3 forward = flatLook();
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        return eye.add(forward.scale(-0.28)).add(right.scale(0.05)).add(0, 0.02, 0);
    }

    private Vec3 attachPoint(Minecraft mc) {
        return flock.locomotion() == Locomotion.ANCHORED ? backPoint() : perchPoint(mc);
    }

    /** Dev phantoms have no owner to orbit; they circle their parked eye point on a fixed facing. */
    private Vec3 phantomAnchor() {
        return phantomEye.add(new Vec3(Math.sin(orbitAngle), 0, Math.cos(orbitAngle)).scale(PHANTOM_RADIUS));
    }

    private void spawnSwoopDust(Minecraft mc) {
        if (owner == null || mc.level == null) return;
        if (mc.options.particles().get() == ParticleStatus.MINIMAL) return;
        Vec3 at = center;
        if (at == null) return;
        if (at.y > owner.getEyePosition().y - 0.2) return; // only while it is actually low
        // Find the ground under the fish, within a few blocks, to kick dust off a real floor.
        for (int dy = 1; dy <= 5; dy++) {
            BlockPos pos = BlockPos.containing(at.x, at.y - dy, at.z);
            BlockState state = mc.level.getBlockState(pos);
            if (!state.getCollisionShape(mc.level, pos).isEmpty()) {
                double y = pos.getY() + state.getCollisionShape(mc.level, pos).max(net.minecraft.core.Direction.Axis.Y);
                for (int i = 0; i < 3; i++) {
                    mc.level.addParticle(ParticleTypes.CLOUD,
                            at.x + (mc.level.getRandom().nextDouble() - 0.5) * 0.8, y,
                            at.z + (mc.level.getRandom().nextDouble() - 0.5) * 0.8,
                            0, 0.02, 0);
                }
                return;
            }
        }
    }

    // ── Visibility (§3.1 acceptance: visible to the owner at regular intervals) ─

    /**
     * Whether the owner can see the companion this tick, and how long it has been since they could.
     * Drives both the peek schedule and the self-test's frame counting — the same signal, so the
     * acceptance metric measures the mechanism that exists to improve it.
     */
    private void updateVisibility(Minecraft mc, boolean local, boolean firstPerson, long now, boolean inWater) {
        visibleNow = false;
        firstPersonHidden = local && firstPerson && !frameDocked
                && FishtasticClientConfig.hideOwnCompanionFirstPerson();
        if (owner == null || firstPersonHidden) return;
        if (frameDocked) {
            visibleNow = true; // the HUD pass always draws it
        } else if (drawsOnPerch()) {
            // On the owner's own body. Everyone else sees it; its owner does not see their own
            // player model in first person, which is exactly why peeks exist.
            visibleNow = !(local && firstPerson);
        } else {
            Vec3 at = center;
            if (at == null) return;
            // The fish's world point: the box centre plus its offset, rotated by the box's yaw the
            // same way the draw does (the raw offset is box-local).
            Vec3 point = at.add(worldOffsetOfFish());
            CameraRenderState cs = mc.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState;
            Camera camera = mc.gameRenderer.getMainCamera();
            Vector4f p = new Vector4f((float) (point.x - camera.position().x), (float) (point.y - camera.position().y),
                    (float) (point.z - camera.position().z), 1f);
            new Matrix4f(cs.viewRotationMatrix).transform(p);
            new Matrix4f(cs.projectionMatrix).transform(p);
            visibleNow = p.w > 0 && Math.abs(p.x / p.w) < 0.95f && Math.abs(p.y / p.w) < 0.95f;
        }
        if (visibleNow) lastSeenTick = now;
    }

    // ── World pass ─────────────────────────────────────────────────────────────

    /**
     * Called from the level submit hook; the stack is camera-relative.
     *
     * @return whether the fish was actually submitted (the caller only counts real draws)
     */
    boolean submitWorld(PoseStack poseStack, SubmitNodeCollector nodes, Camera camera, float partialTick,
                        @Nullable float[] anchor, int screenW, int screenH, boolean pipCompare) {
        if (center == null || flock.isEmpty()) return false;
        computeFrame(camera, partialTick, anchor, screenW, screenH);
        flock.interpolate(partialTick);
        if (frameDocked && !pipCompare) return false; // the HUD pass draws it now
        if (drawsOnPerch() && !pipCompare) return false; // the player render layer draws it

        Vec3 cam = camera.position();
        poseStack.pushPose();
        poseStack.mulPose(boxPose(cam));
        int light = lightAt(fishOffset());
        if (glowsNow()) light = LightCoordsUtil.FULL_BRIGHT;
        flock.submit(poseStack, nodes, light, gameTime(partialTick), frameFishSize, frameTilt);
        poseStack.popPose();
        return true;
    }

    /** A drifter glows at night (§3). */
    private boolean glowsNow() {
        Minecraft mc = Minecraft.getInstance();
        return flock.locomotion() == Locomotion.DRIFT && owner != null && mc.level != null
                && mc.level.getSkyDarken() >= 4;
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
        frameTilt = reactionTilt;
        float length = Math.max(flock.length(), 1e-3f);
        float followSize = Math.max(1f, MIN_FOLLOW_LENGTH / length);
        if (d <= 0f || anchor == null) {
            frameCenter = follow;
            frameScale = 1f;
            frameFishSize = followSize;
            frameDocked = false;
            return;
        }
        frameFishSize = Mth.lerp(d, followSize, DOCK_FISH_LENGTH / length);
        CameraRenderState cs = Minecraft.getInstance().gameRenderer.getGameRenderState().levelRenderState.cameraRenderState;
        Vec3 dockPoint = unproject(camera, cs.projectionMatrix, anchor[0], anchor[1], screenW, screenH, DOCK_DEPTH);
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

    /** The world point {@code depth} blocks ahead of the camera that projects onto GUI pixel (x, y). */
    private static Vec3 unproject(Camera camera, Matrix4f projection, float guiX, float guiY, int screenW, int screenH, float depth) {
        float ndcX = 2f * guiX / screenW - 1f;
        float ndcY = 1f - 2f * guiY / screenH;
        float right = ndcX * depth / projection.m00();
        float up = ndcY * depth / projection.m11();
        Vector3fc fwd = camera.forwardVector(), upV = camera.upVector(), left = camera.leftVector();
        Vec3 cam = camera.position();
        return new Vec3(
                cam.x + fwd.x() * depth + upV.x() * up - left.x() * right,
                cam.y + fwd.y() * depth + upV.y() * up - left.y() * right,
                cam.z + fwd.z() * depth + upV.z() * up - left.z() * right);
    }

    /** Called from {@link CompanionPipRenderer} inside the docked fish's view-space pose. */
    void submitDockedFish(PoseStack poseStack, SubmitNodeCollector nodes, int light, float gameTime) {
        // The stack is already at the box centre; the flock places each fish at its own offset.
        flock.submit(poseStack, nodes, light, gameTime, frameFishSize, 0f);
    }

    // ── Perch layer / hand pass ────────────────────────────────────────────────

    /** True while this companion sits on its owner's body and is drawn by the player render layer. */
    boolean drawsOnPerch() {
        if (!percher || outAmount() > 0f || frameDocked) return false;
        if (owner == null || owner.isInvisible()) return false;
        return !owner.isEyeInFluid(FluidTags.WATER);
    }

    /**
     * Draws the perched fish in whatever pose space the caller has set up. The caller (the player
     * render layer, or the hand pass) owns the position and orientation; this adds only the fish's
     * own idle animation, its trick spin, the anchored duck and the reaction tilt.
     */
    void submitPoseOnly(PoseStack poseStack, SubmitNodeCollector nodes, int light, float gameTimeTicks,
                        float extraYawDeg, float extraTiltDeg) {
        float spin = flock.locomotion() == Locomotion.BENTHIC ? spinDeg : 0f;
        float duckAmount = flock.locomotion() == Locomotion.ANCHORED ? duck : 0f;
        pose.submit(poseStack, nodes, light, gameTimeTicks, extraYawDeg + spin, extraTiltDeg + reactionTilt, duckAmount);
    }

    public boolean handHopActive() { return handHop > 0.05f; }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** The first fish's offset in the box this frame, with the dock clamp's fading bias. */
    Vec3 fishOffset() {
        return flock.fishOffset(0);
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

    private static float approach(float value, float target, float speed) {
        if (value < target) return Math.min(target, value + speed);
        return Math.max(target, value - speed);
    }

    // ── Dev accessors (self-test) ──────────────────────────────────────────────

    /** Dev-only: start a peek now, whatever the schedule says (self-test scenes). */
    void devStartPeek() {
        if (peekPhase == PEEK_IDLE) {
            peekPhase = PEEK_OUT_PHASE;
            peekTimer = PEEK_OUT;
            peekCount++;
        }
    }

    /** The display class as a stable name for logs and checks. */
    public String displayClassName() {
        if (flock.isTrio()) return "trio";
        return flock.locomotion().name().toLowerCase(Locale.ROOT);
    }

    public boolean isPerchedNow() {
        return drawsOnPerch();
    }

    /**
     * Which body slot a percher sits in: 0 right shoulder, 1 left shoulder, 2 head. A flat pose
     * (a starfish) is re-slotted off the crown — stood up on a shoulder it reads from the front,
     * but lying flat on the head it is a sliver from every angle but straight above.
     */
    public int perchSlot() {
        if (locomotion() == Locomotion.ANCHORED) return 3;
        int slot = Math.floorMod(seed, 3);
        return slot == 2 && perchStandsUp() ? 0 : slot;
    }

    /** The display class this companion moves as. */
    public Locomotion locomotion() {
        return flock.locomotion();
    }

    /** Whether its idle pose lies flat and should be stood up for a shoulder perch. */
    public boolean perchStandsUp() {
        return pose.flatPose();
    }

    public boolean isPeeking() {
        return peekPhase != PEEK_IDLE;
    }

    /** True while the companion's tank is in water mode, i.e. it dropped its air anchor. */
    public boolean inWaterMode() {
        return flock.mode() == CompanionFlock.Mode.WATER;
    }

    public boolean visibleToOwner() { return visibleNow; }

    public boolean hiddenInFirstPerson() { return firstPersonHidden; }

    public int fishCount() { return flock.fishCount(); }

    public String reactionName() { return reactionName; }

    public float duckAmount() { return duck; }

    public float spinDegrees() { return spinDeg; }

    public int peekStarts() { return peekCount; }

    /** Height (blocks) of the companion above the owner's eyes; null when there is no owner. */
    @Nullable
    public Double heightAboveOwnerEye() {
        if (owner == null || center == null) return null;
        return center.y - owner.getEyePosition().y;
    }

    public float horizontalDistanceToOwner() {
        if (owner == null || center == null) return -1f;
        Vec3 d = center.subtract(owner.getEyePosition());
        return (float) Math.sqrt(d.x * d.x + d.z * d.z);
    }

    /** How far behind the owner's facing the companion is (positive = behind). */
    public float behindness() {
        if (owner == null || center == null) return 0f;
        Vec3 f = flatLook();
        Vec3 d = center.subtract(owner.getEyePosition());
        return (float) -(d.x * f.x + d.z * f.z);
    }

    /** Dev diagnostics: where the box and fish are relative to the camera, and the dock state. */
    String debugState(@Nullable float[] anchor, int screenW, int screenH) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 rel = frameCenter.subtract(camera.position());
        CameraRenderState cs = mc.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState;
        Vec3 off = fishOffset();
        Vector4f p = new Vector4f((float) off.x, (float) off.y, (float) off.z, 1f);
        boxPose(camera.position()).transform(p);
        new Matrix4f(cs.viewRotationMatrix).transform(p);
        float viewZ = p.z;
        new Matrix4f(cs.projectionMatrix).transform(p);
        float gx = (p.x / p.w + 1f) / 2f * screenW, gy = (1f - p.y / p.w) / 2f * screenH;
        return String.format(Locale.ROOT,
                "owner=%s stack=%s class=%s perched=%s peek=%d(%.2f) seen=%s reaction=%s hand=%.2f duck=%.2f docked=%s dock=%.2f anchor=%s centreRel=(%.2f,%.2f,%.2f) scale=%.3f fishSize=%.2f fishGui=(%.1f,%.1f) viewZ=%.2f | %s",
                owner == null ? "phantom" : owner.getName().getString(), stack.getHoverName().getString(),
                displayClassName(), isPerchedNow(), peekPhase, peekBlend, visibleNow,
                reactionName.isEmpty() ? "-" : reactionName, handHop, duck, frameDocked, dock,
                anchor == null ? "null" : java.util.Arrays.toString(anchor),
                rel.x, rel.y, rel.z, frameScale, frameFishSize, gx, gy, viewZ, flock.debug());
    }
}
