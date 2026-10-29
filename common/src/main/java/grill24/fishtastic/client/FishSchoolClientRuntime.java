package grill24.fishtastic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import grill24.FishtasticRegistries;
import grill24.fishsim.core.FlockEngine;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.client.renderer.FishVisuals;
import grill24.fishtastic.client.renderer.GroupFishDraw;
import grill24.fishtastic.client.renderer.TankBubbleEmitter;
import grill24.fishtastic.data.FishAnimationConfig;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.network.FishSchoolInputPacket;
import grill24.fishtastic.network.FishSchoolSetupPacket;
import grill24.fishtastic.network.FishSchoolStatePacket;
import grill24.fishtastic.school.FishSchoolReplica;
import grill24.fishtastic.school.FishSchoolSim;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Shared-school presentation. Each tracked school is a {@link FishSchoolReplica}: the server's own
 * motion, computed again here in lockstep, so the fish swim exactly as the server has them and as
 * the ambient population swims — this class draws, it never moves a fish
 * (docs/interactive-fish-schools-motion-plan.md). It stays active when decorative ambient fish
 * are disabled.
 */
public final class FishSchoolClientRuntime {
    private static final Map<UUID, Copy> COPIES = new HashMap<>();
    /**
     * Mote sprite sheets (tools/school-particle-gen): greyscale, 8 frames of 16x16, so the mote's
     * vertex colour alone sets its hue. Index 0 is the glint, used where a mote must read as a flash.
     */
    private static final String[] MOTE_SPRITES = {"school_glint", "school_pearl_bubble", "school_sea_glass",
            "school_star_twinkle", "school_plankton_orb", "school_ripple_ring"};
    static final RenderType[] MOTE_RENDER_TYPES = new RenderType[MOTE_SPRITES.length];
    static {
        for (int i = 0; i < MOTE_SPRITES.length; i++) {
            MOTE_RENDER_TYPES[i] = RenderType.entityTranslucentEmissive(
                    Fishtastic.id("textures/particle/" + MOTE_SPRITES[i] + ".png"), false);
        }
    }
    static final int GLINT_SPRITE = 0;
    static final int STAR_TWINKLE_SPRITE = 3;
    static final int PLANKTON_ORB_SPRITE = 4;
    /** The ripple ring, last on the list: splash rings use it, airborne droplets never do. */
    static final int RIPPLE_SPRITE = 5;
    /** The pearl/sea-glass cyans the motes were drawn in before the sprites went greyscale. */
    private static final int[] DEFAULT_MOTE_TINTS = {0xB4FFF0, 0x8CF5F0, 0x5AE6DC, 0xC8FFE6};
    /**
     * Each quality's mote tints, by {@link FishQuality.Quality} ordinal: a fish's trail shows what
     * catching it gives. Common keeps the original sea-glass cyans; the rest are shades round their
     * tooltip colour (green, blue, light purple, gold).
     */
    private static final int[][] QUALITY_MOTE_TINTS = {
            DEFAULT_MOTE_TINTS,
            {0x9CFF9C, 0x6EF56E, 0x55E655, 0xC8FFC8},
            {0x8C9CFF, 0x6E7EFF, 0x5566FF, 0xB4BEFF},
            {0xFF9CFF, 0xF06EF0, 0xE055E6, 0xFFC8FF},
            {0xFFD966, 0xFFC233, 0xFFAA00, 0xFFE9A8},
    };
    /** Dev override of every school's mote tints (RGB), or null for the defaults. */
    private static int @Nullable [] moteTintOverride;
    private static final int FULL_BRIGHT = 15728880;
    private static final int GLINT_FRAME_COUNT = 8;
    /** The sheets' fullest frame, where every sprite is near its peak coverage: held motes draw it. */
    private static final int HELD_FRAME = 4;
    /**
     * Far above what spacing and lifetime produce (a calm fish leaves ~1 mote a second, each lasting
     * ~4.3 s) plus one bite fountain with all its splashes (~130 motes): the cap only guards against
     * the unexpected. At 56 it trimmed a 4-fish trail down to under a second, which is what made the
     * trail short and dense.
     */
    private static final int MAX_TRAIL_MOTES = 256;
    /** One mote per this many blocks swum: a sparse, evenly spaced trail at any speed. */
    private static final double MOTE_SPACING = 0.12;
    /** Mote lifetime, ticks: {@code MOTE_LIFETIME} plus up to {@code MOTE_LIFETIME_SPREAD}. */
    private static final int MOTE_LIFETIME = 70;
    private static final int MOTE_LIFETIME_SPREAD = 31;
    /** A departing visit fades over the server's whole departure window (decision M3). */
    private static final float DEPARTURE_FADE_TICKS = 60f;
    private static @Nullable ResourceLocation currentDimension;
    /**
     * Self-test only: trail motes draw red, and a blue dot marks each fish's engine centre every
     * tick, so a capture shows exactly where the trail spawns relative to the drawn fish.
     */
    public static boolean debugTrailMarkers;
    private static final Random POSE_RANDOM = new Random(7L);

    private static final class TrailMote {
        /** Ticks a mote takes to fade out once it has dropped below the water surface. */
        static final int SINK_FADE_TICKS = 8;
        double previousX, previousY, previousZ;
        double x, y, z;
        /** Velocity, blocks a tick: a constant drift for trail motes, a ballistic arc for fountain droplets. */
        double vx, vy, vz;
        float gravity;
        float drag = 1f;
        /** The water surface a falling droplet splashes into, or NaN for a mote that ignores it. */
        double surfaceY = Double.NaN;
        /** Whether crossing {@link #surfaceY} raises a splash (secondary droplets only sink). */
        boolean splashes;
        /** Drawn lying on the water instead of facing the camera (splash rings). */
        boolean flat;
        /**
         * Drawn on the sprite's fullest frame at full alpha instead of twinkling over its lifetime
         * (fountain droplets): a droplet sinks long before its lifetime is up, and the sheets' early
         * frames are nearly empty, so a lifetime-keyed droplet flew its whole arc almost invisible.
         */
        boolean held;
        /** Age at which the mote went under the surface and began to fade, or -1. */
        float sinkAge = -1f;
        final float halfSize;
        final float rotation;
        final int red, green, blue;
        final int sprite;
        final int lifetime;
        float age;
        /** Set only for the airborne motes owned by a cinematic member's gold trail. */
        @Nullable UUID clockOwner;

        TrailMote(double x, double y, double z, double driftX, double rise, double driftZ,
                  float halfSize, float rotation, int red, int green, int blue, int sprite, int lifetime) {
            this.previousX = this.x = x;
            this.previousY = this.y = y;
            this.previousZ = this.z = z;
            this.vx = driftX;
            this.vy = rise;
            this.vz = driftZ;
            this.halfSize = halfSize;
            this.rotation = rotation;
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.sprite = sprite;
            this.lifetime = lifetime;
        }

        void tick(double delta) {
            previousX = x;
            previousY = y;
            previousZ = z;
            x += vx * delta;
            y += vy * delta;
            z += vz * delta;
            vy -= gravity * delta;
            double dragScale = Math.pow(drag, delta);
            vx *= dragScale;
            vy *= dragScale;
            vz *= dragScale;
            age += (float) delta;
        }

        /** True on the tick a falling droplet first drops below its water surface. */
        boolean justSank() {
            return sinkAge < 0 && !Double.isNaN(surfaceY) && vy < 0 && y < surfaceY;
        }

        /** Under the surface the droplet loses its arc: it slows, drifts down and fades. */
        void sink() {
            sinkAge = age;
            gravity = 0f;
            drag = 0.7f;
            vy = -0.012;
        }

        boolean expired() {
            return age >= lifetime || (sinkAge >= 0f && age - sinkAge >= SINK_FADE_TICKS);
        }
    }

    /** One tracked school: its lockstep copy and what drawing it needs. */
    private static final class Copy {
        final FishSchoolReplica replica;
        final ItemStack stack;
        final FishAnimationConfig anim;
        final Map<UUID, Integer> visual = new HashMap<>();
        /** Each member's quality, for its trail's colour. */
        final Map<UUID, FishQuality.Quality> qualities = new HashMap<>();
        /** Bounded per-member visual controllers; the containing copy is scoped to one school epoch. */
        final Map<UUID, LegendaryBreachPresentation> breaches = new HashMap<>();
        /** Each member's world position after the previous tick, for its trail. */
        final Map<UUID, Vec3> lastPositions = new HashMap<>();
        /** The copy's sim step at the previous client tick, so each jump's surface crossings splash once; -1 unset. */
        long jumpStepSeen = -1;
        /** Each jumping member's drawn position after the previous tick: its trail follows the arc. */
        final Map<UUID, Vec3> lastDrawn = new HashMap<>();
        /** Scratch for {@link FishSchoolSim#surfaceJumpPose} while drawing. */
        final float[] jumpPose = new float[JUMP_POSE_SIZE];
        /** Each member's distance swum since its last mote, blocks. */
        final Map<UUID, Double> sinceMote = new HashMap<>();
        /** Last observed source time and delta for retimed airborne gold motes. */
        final Map<UUID, Double> sourceTimes = new HashMap<>();
        final Map<UUID, Double> sourceDeltas = new HashMap<>();
        /** Ticks each near-motionless member has gone since its last stationary glint. */
        final Map<UUID, Integer> stillTicks = new HashMap<>();
        final List<TrailMote> trail = new ArrayList<>();
        final Random trailRandom;
        float fadeTicks = -1f;
        int loggedMismatches;
        float[] depth = new float[0];
        int[] order = new int[0];

        Copy(FishSchoolReplica replica, ItemStack stack, FishAnimationConfig anim) {
            this.replica = replica;
            this.stack = stack;
            this.anim = anim;
            this.trailRandom = new Random(replica.school().hashCode() * 0xD1B54A32D192ED03L);
        }
    }

    /** Render-thread time all catching-up copies may spend replaying each tick (performance plan, step E). */
    private static final long REPLAY_BUDGET_NANOS = 2_000_000L;

    private FishSchoolClientRuntime() {}

    /**
     * Self-test counters (docs/interactive-fish-schools-performance-plan.md, step 0): render-thread
     * time spent in {@link #submitWorld} and {@link #tick}, and the motes submitted. Render thread only.
     */
    public static final class Perf {
        public static boolean enabled;
        static long frames, submitNanos, maxSubmitNanos, moteSubmits, maxMotes;
        static long ticks, tickNanos, maxTickNanos, steps;

        private Perf() {}

        public static void reset() {
            frames = submitNanos = maxSubmitNanos = moteSubmits = maxMotes = 0L;
            ticks = tickNanos = maxTickNanos = steps = 0L;
        }

        public static String report() {
            long f = Math.max(1, frames), n = Math.max(1, ticks);
            return String.format(java.util.Locale.ROOT,
                    "frames=%d submit mean=%.1fus max=%.1fus moteSubmits/frame=%.1f maxMotes=%d"
                            + " | ticks=%d tick mean=%.1fus max=%.1fus steps=%d",
                    frames, submitNanos / 1e3 / f, maxSubmitNanos / 1e3, moteSubmits / (double) f, maxMotes,
                    ticks, tickNanos / 1e3 / n, maxTickNanos / 1e3, steps);
        }
    }

    // ------------------------------------------------------------------ packets

    public static void onSetup(FishSchoolSetupPacket packet) {
        ClientLevel level = levelFor(packet.dimension());
        if (level == null) return;
        Item item = BuiltInRegistries.ITEM.getOptional(packet.species()).orElse(Items.AIR);
        if (item == Items.AIR) return;
        ItemStack stack = new ItemStack(item);
        float size = level.registryAccess().registryOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY)
                .getOptional(ResourceKey.create(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, packet.species()))
                .map(profile -> profile.size().mean()).orElse(30f);
        ItemSizeHelper.setSize(stack, size);
        FishAnimationConfig anim = FishVisuals.resolve(stack, level).animation();
        FishSchoolReplica replica = new FishSchoolReplica(packet.school(), packet.epoch(), packet.setup(),
                packet.inputs(), packet.serverStep());
        Copy copy = new Copy(replica, stack, anim);
        for (FishSchoolSetupPacket.MemberQuality q : packet.qualities()) copy.qualities.put(q.member(), q.quality());
        COPIES.put(packet.school(), copy);
        if (packet.ageTicks() <= ARRIVAL_WINDOW_TICKS && FishtasticClientConfig.isFishSchoolArrivalsShown()
                && FishtasticClientConfig.isFishSchoolsVisible()) {
            arrivalRipples(copy, packet.setup());
        }
    }

    /** Self-test counters: arrivals shown, stationary glints given off and bite bursts since the last reset. */
    private static int arrivalsShown, stillGlints, biteBursts;

    public static void resetSelfTestCounters() {
        arrivalsShown = 0;
        stillGlints = 0;
        biteBursts = 0;
    }

    public static int biteBurstsForSelfTest() { return biteBursts; }

    public static int arrivalsForSelfTest() { return arrivalsShown; }

    public static int stillGlintsForSelfTest() { return stillGlints; }

    /** A visit this young when its setup arrives has only just come: its arrival is shown, ticks. */
    private static final int ARRIVAL_WINDOW_TICKS = 100;

    /**
     * The arrival cue (encounter spec §6.3): rings spreading on the surface over each fish and a
     * little splash, so a player busy with something else notices that something came. Silent by
     * design, as automatic effects are; the rings take each fish's quality tint like its sparkle.
     */
    private static void arrivalRipples(Copy copy, FishSchoolSim.Setup setup) {
        arrivalsShown++;
        ClientLevel level = Minecraft.getInstance().level;
        BlockPos o = setup.origin();
        Random random = copy.trailRandom;
        for (FishSchoolSim.Placement p : setup.members()) {
            double x = o.getX() + p.l(), y = o.getY() + p.y(), z = o.getZ() + p.d();
            double surface = waterSurfaceAbove(x, y, z);
            int[] tints = tints(copy, p.member());
            for (int ring = 0; ring < 3; ring++) {
                TrailMote mote = addMote(copy, x + (random.nextDouble() - 0.5) * 0.4, surface + 0.015,
                        z + (random.nextDouble() - 0.5) * 0.4, 0, 0, 0, 0.35f + ring * 0.25f, 18 + ring * 10,
                        RIPPLE_SPRITE, tints);
                mote.flat = true;
            }
            if (level != null) {
                for (int i = 0; i < 6; i++) {
                    level.addParticle(ParticleTypes.SPLASH, x + (random.nextDouble() - 0.5) * 0.8, surface + 0.05,
                            z + (random.nextDouble() - 0.5) * 0.8, 0, 0.05, 0);
                }
            }
        }
        while (copy.trail.size() > MAX_TRAIL_MOTES) copy.trail.remove(0);
    }

    public static void onInputs(FishSchoolInputPacket packet) {
        if (levelFor(packet.dimension()) == null) return;
        Copy copy = COPIES.get(packet.school());
        if (copy != null && copy.replica.epoch() == packet.epoch()) copy.replica.addInputs(packet.inputs());
    }

    public static void onState(FishSchoolStatePacket packet) {
        if (levelFor(packet.dimension()) == null) return;
        Set<UUID> listed = new HashSet<>();
        for (FishSchoolStatePacket.School school : packet.schools()) {
            Copy copy = COPIES.get(school.school());
            if (copy == null || copy.replica.epoch() != school.epoch()) continue;
            listed.add(school.school());
            copy.replica.observe(school.serverStep(), school.checksum());
            copy.visual.clear();
            for (FishSchoolStatePacket.Member member : school.members()) copy.visual.put(member.member(), member.visual());
            copy.fadeTicks = school.fadeTicks() < 0 ? -1f : Math.min(copy.fadeTicks < 0 ? Float.MAX_VALUE : copy.fadeTicks,
                    school.fadeTicks());
        }
        COPIES.keySet().removeIf(id -> !listed.contains(id));
    }

    /** The current level if the packet is for it; switching dimension drops every copy. */
    private static @Nullable ClientLevel levelFor(ResourceLocation dimension) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().location().equals(dimension)) return null;
        if (currentDimension != null && !currentDimension.equals(dimension)) COPIES.clear();
        currentDimension = dimension;
        return level;
    }

    // ------------------------------------------------------------------ tick

    public static void tick(Minecraft mc) {
        if (mc.level == null) {
            clear();
            return;
        }
        ResourceLocation dimension = mc.level.dimension().location();
        if (currentDimension != null && !currentDimension.equals(dimension)) clear();
        currentDimension = dimension;
        long started = Perf.enabled ? System.nanoTime() : 0L;
        // One replay budget for every copy this tick: a late join into several schools replayed
        // ~12,800 steps in one tick (a 60 ms frame, performance plan step 0) before this.
        long replayDeadline = System.nanoTime() + REPLAY_BUDGET_NANOS;
        for (Copy copy : COPIES.values()) {
            int stepped = copy.replica.tick(event -> {
                // A surface goal's arrival is a touch too (the same nose errand as a bobber's),
                // but it starts a jump, not a bite: the jump makes its own splashes.
                FishSchoolSim sim = copy.replica.sim();
                if (event.touch() && (sim == null || !sim.goalActive(event.anchor()))) {
                    spawnBiteBurst(copy, event.anchor());
                }
            }, replayDeadline);
            if (copy.replica.mismatches() > copy.loggedMismatches) {
                if (copy.loggedMismatches == 0) {
                    Fishtastic.LOGGER.warn("Fish school {} copy disagreed with the server (replays so far: {})",
                            copy.replica.school(), copy.replica.replays());
                }
                copy.loggedMismatches = copy.replica.mismatches();
            }
            if (copy.fadeTicks > 0f) copy.fadeTicks = Math.max(0f, copy.fadeTicks - 1f);
            if (!FishtasticClientConfig.isFishSchoolsVisible()) copy.breaches.clear();
            tickTrail(copy, stepped > 0);
            if (Perf.enabled) {
                Perf.steps += stepped;
                Perf.maxMotes = Math.max(Perf.maxMotes, copy.trail.size());
            }
        }
        if (Perf.enabled && !COPIES.isEmpty()) {
            long spent = System.nanoTime() - started;
            Perf.ticks++;
            Perf.tickNanos += spent;
            Perf.maxTickNanos = Math.max(Perf.maxTickNanos, spent);
        }
    }

    public static void clear() {
        COPIES.clear();
        currentDimension = null;
    }

    /** Ages the motes, then lays new ones along each member's swim this tick, unless it is departing. */
    private static void tickTrail(Copy copy, boolean moved) {
        FishSchoolSim sim = copy.replica.sim();
        if (sim != null && copy.replica.caughtUp()) {
            Set<UUID> activeClocks = new HashSet<>();
            for (UUID member : sim.members()) {
                FishSchoolSim.SurfaceJumpClock clock = sim.surfaceJumpClock(member);
                if (clock == null || clock.profile()
                        != grill24.fishtastic.school.SurfaceJumpPlaybackProfile.LEGENDARY_CINEMATIC) continue;
                activeClocks.add(member);
                double previous = copy.sourceTimes.getOrDefault(member, clock.sourceTime());
                double delta = Math.max(0.0, clock.sourceTime() - previous);
                copy.sourceTimes.put(member, clock.sourceTime());
                copy.sourceDeltas.put(member, delta);
            }
            copy.sourceTimes.keySet().retainAll(activeClocks);
            copy.sourceDeltas.keySet().retainAll(activeClocks);
        } else {
            copy.sourceTimes.clear();
            copy.sourceDeltas.clear();
        }
        // Backwards, so splashes appended while ageing are not visited until next tick.
        for (int i = copy.trail.size() - 1; i >= 0; i--) {
            TrailMote mote = copy.trail.get(i);
            mote.tick(mote.clockOwner == null ? 1.0 : copy.sourceDeltas.getOrDefault(mote.clockOwner, 1.0));
            if (mote.justSank()) {
                mote.sink();
                if (mote.splashes) splash(copy, mote);
            }
            if (mote.expired()) copy.trail.remove(i);
        }
        if (sim == null || !copy.replica.caughtUp()) {
            copy.lastPositions.clear();
            copy.lastDrawn.clear();
            copy.jumpStepSeen = -1;
            copy.breaches.clear();
            return;
        }
        FlockEngine engine = sim.engine();
        BlockPos o = sim.origin();
        List<UUID> members = sim.members();
        long stepNow = sim.steps();
        float[] jump = new float[JUMP_POSE_SIZE];
        Map<UUID, Vec3> now = new HashMap<>(members.size());
        // The drawn fish is not at the engine's position: the renderer's pose bobs and lifts it
        // (FishAnimator's yBob, about half a body height for a bluegill). Measured with the
        // markers below, motes laid at the engine's position sat on the body's top or bottom edge.
        // So each mote is moved by this tick's drawn-minus-engine offset, through the same pose.
        engine.interpolate(1f);
        float t = Minecraft.getInstance().level == null ? 0f : Minecraft.getInstance().level.getGameTime();
        boolean reducedEffects = FishtasticClientConfig.isReducedCelebrationEffects();
        boolean minimalParticles = Minecraft.getInstance().options.particles().get()
                == net.minecraft.client.ParticleStatus.MINIMAL;
        Set<UUID> tickedBreaches = new HashSet<>();
        for (int i = 0; i < members.size(); i++) {
            UUID id = members.get(i);
            Vec3 at = new Vec3(o.getX() + engine.posL()[i], o.getY() + engine.posY()[i], o.getZ() + engine.posD()[i]);
            now.put(id, at);
            boolean jumping = sim.surfaceJumpPose(id, 1f, jump);
            FishSchoolSim.SurfaceJumpClock clock = sim.surfaceJumpClock(id);
            Vec3 drawn = drawnPoint(sim, copy.anim, i, t, 0f, 0f, 0f, jumping ? jump : null);
            if (copy.jumpStepSeen >= 0) {
                float length = engine.lengths[i];
                sim.surfaceJumpCrossings(id, copy.jumpStepSeen, stepNow,
                        cross -> jumpSplash(copy, sim, cross, length));
            }
            LegendaryBreachPresentation breach = copy.breaches.get(id);
            boolean departing = copy.visual.getOrDefault(id, FishSchoolStatePacket.AVAILABLE)
                    == FishSchoolStatePacket.DEPARTING;
            if (clock != null && clock.profile()
                    == grill24.fishtastic.school.SurfaceJumpPlaybackProfile.LEGENDARY_CINEMATIC
                    && FishtasticClientConfig.isFishSchoolsVisible()) {
                if (breach == null && clock.sourceTime() >= clock.exitSourceTime()) {
                    breach = new LegendaryBreachPresentation(copy.replica.school(), copy.replica.epoch(), id, clock);
                    breach.reconstruct(sim, sim.origin(), clock, reducedEffects, minimalParticles, engine.lengths[i]);
                    copy.breaches.put(id, breach);
                }
                if (breach != null) {
                    if (departing) breach.stop(true);
                    breach.tick(sim, sim.origin(), clock, reducedEffects, minimalParticles, engine.lengths[i]);
                    tickedBreaches.add(id);
                }
            } else if (breach != null) {
                breach.stop(departing);
                breach.tick(sim, sim.origin(), null, reducedEffects, minimalParticles, engine.lengths[i]);
                tickedBreaches.add(id);
            }
            if (debugTrailMarkers) {
                // Blue: the engine's centre. Green: the drawn body's centre.
                marker(copy, at, 40, 90, 255);
                marker(copy, drawn, 40, 255, 40);
            }
            Vec3 before = copy.lastPositions.get(id);
            // Through a jump the engine fish is held at its launch point while the drawn body flies,
            // and on landing the engine fish is moved to where it was drawn: so from the first tick
            // of a jump to the tick it lands, the trail follows the drawn body's own path instead.
            Vec3 drawnBefore = copy.lastDrawn.get(id);
            boolean followArc = jumping || drawnBefore != null;
            if (jumping) copy.lastDrawn.put(id, drawn);
            else if (drawnBefore != null) copy.lastDrawn.remove(id);
            // The trail stays on for as long as the fish is in the water and not leaving: through its
            // approach, its bite and its recovery (owner decision M8). Hooked fish are out of the sim.
            if (moved && before != null
                    && copy.visual.getOrDefault(id, FishSchoolStatePacket.AVAILABLE) != FishSchoolStatePacket.DEPARTING) {
                if (followArc) {
                    // No glint or sprint bubbles mid-leap: the held engine fish would read as still.
                    if (drawnBefore != null) {
                        UUID clockOwner = clock != null && clock.profile()
                                == grill24.fishtastic.school.SurfaceJumpPlaybackProfile.LEGENDARY_CINEMATIC
                                && clock.sourceTime() >= clock.exitSourceTime() ? id : null;
                        spawnMotes(copy, id, drawnBefore, drawn, Vec3.ZERO, engine.lengths[i], clockOwner);
                    }
                    continue;
                }
                spawnMotes(copy, id, before, at, drawn.subtract(at), engine.lengths[i], null);
                stillGlint(copy, id, before, at, drawn);
                int state = engine.shelterState(i);
                if (state == FlockEngine.SHELTER_APPROACH || state == FlockEngine.SHELTER_ENTER) {
                    sprintBubbles(copy, before, at, drawn.subtract(at), engine.lengths[i]);
                }
            }
        }
        for (var iterator = copy.breaches.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<UUID, LegendaryBreachPresentation> entry = iterator.next();
            if (tickedBreaches.contains(entry.getKey())) {
                if (entry.getValue().expired()) iterator.remove();
                continue;
            }
            entry.getValue().stop(true);
            entry.getValue().tick(sim, sim.origin(), null, reducedEffects, minimalParticles, sim.spec().length());
            if (entry.getValue().expired() || !members.contains(entry.getKey())) iterator.remove();
        }
        copy.jumpStepSeen = stepNow;
        copy.lastDrawn.keySet().retainAll(now.keySet()); // a member caught mid-jump leaves no stale arc
        copy.lastPositions.clear();
        copy.lastPositions.putAll(now);
        while (copy.trail.size() > MAX_TRAIL_MOTES) copy.trail.remove(0);
    }

    /**
     * Lays motes along this tick's swim from {@code from} to {@code to}, one every
     * {@link #MOTE_SPACING} blocks of the member's path, carrying the remainder over to the next tick.
     * Spacing and direction come from the engine's path (the drawn bob would add motes to a fish
     * hovering in place); {@code drawnOffset} moves them onto the drawn body. A surface jump passes
     * the drawn path itself with no offset, since its engine fish is held still.
     */
    private static void spawnMotes(Copy copy, UUID member, Vec3 from, Vec3 to, Vec3 drawnOffset, float length,
                                   @Nullable UUID clockOwner) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 1e-6) return;
        double travelled = copy.sinceMote.getOrDefault(member, copy.trailRandom.nextDouble() * MOTE_SPACING);
        Random random = copy.trailRandom;
        double inverseDistance = 1.0 / distance;
        double directionX = dx * inverseDistance;
        double directionY = dy * inverseDistance;
        double directionZ = dz * inverseDistance;
        double tailOffset = clamp(length * 0.46f, 0.045f, 0.16f);
        // The first mote due on this segment lies (spacing - travelled) along it.
        double next = MOTE_SPACING - travelled;
        int[] tints = tints(copy, member);
        for (; next <= distance; next += MOTE_SPACING) {
            double along = next * inverseDistance;
            double lateral = (random.nextDouble() - 0.5) * 0.045;
            double vertical = (random.nextDouble() - 0.5) * 0.055;
            double baseX = from.x + dx * along - directionX * tailOffset + drawnOffset.x;
            double baseY = from.y + dy * along - directionY * tailOffset + drawnOffset.y;
            double baseZ = from.z + dz * along - directionZ * tailOffset + drawnOffset.z;
            float size = 0.028f + random.nextFloat() * 0.026f;
            if (random.nextFloat() < 0.13f) size *= 1.35f;
            // Slower drift than the old trail, so a mote living twice as long rises about as far.
            TrailMote mote = addMote(copy, baseX - directionZ * lateral, baseY + vertical,
                    baseZ + directionX * lateral,
                    (random.nextDouble() - 0.5) * 0.0012,
                    0.0008 + random.nextDouble() * 0.0012,
                    (random.nextDouble() - 0.5) * 0.0012,
                    size, MOTE_LIFETIME + random.nextInt(MOTE_LIFETIME_SPREAD), tints);
            mote.clockOwner = clockOwner;
        }
        copy.sinceMote.put(member, distance - (next - MOTE_SPACING));
    }

    /** A fish moving less than this a tick lays almost no trail (an eel, a resting crab), blocks. */
    private static final double STILL_SPEED = 0.02;
    /** Such a fish gives off one rising glint this often instead, ticks, so it stays marked. */
    private static final int STILL_GLINT_TICKS = 12;

    /**
     * The stationary sparkle (encounter spec §6.2): a creature that barely moves leaves no swim
     * trail, so it glints in place now and then, in its quality's tint like the trail.
     */
    private static void stillGlint(Copy copy, UUID member, Vec3 before, Vec3 at, Vec3 drawn) {
        if (before.distanceToSqr(at) >= STILL_SPEED * STILL_SPEED) {
            copy.stillTicks.remove(member);
            return;
        }
        int ticks = copy.stillTicks.merge(member, 1, Integer::sum);
        if (ticks < STILL_GLINT_TICKS) return;
        copy.stillTicks.put(member, 0);
        stillGlints++;
        Random random = copy.trailRandom;
        addMote(copy, drawn.x + (random.nextDouble() - 0.5) * 0.12, drawn.y + 0.05 + random.nextDouble() * 0.08,
                drawn.z + (random.nextDouble() - 0.5) * 0.12, 0, 0.0025 + random.nextDouble() * 0.0015, 0,
                0.034f + random.nextFloat() * 0.02f, MOTE_LIFETIME + random.nextInt(MOTE_LIFETIME_SPREAD),
                tints(copy, member));
    }

    /** One bubble per this many blocks a fish sprints for a bobber, on average. */
    private static final double SPRINT_BUBBLE_SPACING = 0.09;
    /** Share of the fish's motion a shed bubble carries on with: the tanks' (TankBubbleEmitter). */
    private static final double SPRINT_BUBBLE_DRIFT = 0.3;

    /**
     * Bubbles shed along a fish's whole body as it sprints for a bobber (a school's only errand is a
     * bobber, so its approach and nose-in are the sprint): the tanks' bubbles in their exertion size
     * mix ({@link TankBubbleEmitter#exertionBubble}), popping at the surface. More bubbles the faster
     * the fish swims, at points anywhere from nose to tail, drifting on with a little of its way.
     */
    private static void sprintBubbles(Copy copy, Vec3 from, Vec3 to, Vec3 drawnOffset, float length) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Vec3 step = to.subtract(from);
        double distance = step.length();
        if (distance < 1e-4) return;
        Vec3 dir = step.scale(1.0 / distance);
        Random random = copy.trailRandom;
        double due = distance / SPRINT_BUBBLE_SPACING;
        int count = (int) due + (random.nextDouble() < due - (int) due ? 1 : 0);
        for (int k = 0; k < count; k++) {
            // Anywhere along the body: the centre is the drawn fish's, the body runs along its path.
            double along = (random.nextDouble() - 0.5) * length;
            double side = (random.nextDouble() - 0.5) * length * 0.25;
            double x = to.x + drawnOffset.x + dir.x * along - dir.z * side;
            double y = to.y + drawnOffset.y + dir.y * along + (random.nextDouble() - 0.5) * length * 0.2;
            double z = to.z + drawnOffset.z + dir.z * along + dir.x * side;
            if (!level.getFluidState(BlockPos.containing(x, y, z)).is(FluidTags.WATER)) continue;
            // The tanks' particles take (x drift, pop Y, z drift), and inherit 30% of the fish's way.
            level.addParticle(TankBubbleEmitter.exertionBubble(level.getRandom()), x, y, z,
                    step.x * SPRINT_BUBBLE_DRIFT, waterSurfaceAbove(x, y, z), step.z * SPRINT_BUBBLE_DRIFT);
        }
    }

    private static void marker(Copy copy, Vec3 at, int red, int green, int blue) {
        copy.trail.add(new TrailMote(at.x, at.y, at.z, 0, 0, 0, 0.025f, 0f, red, green, blue, GLINT_SPRITE, 2));
    }

    /**
     * Where model point {@code (mx, my, mz)} of fish #{@code i} is drawn, in the world: the same
     * {@link GroupFishPose} the renderer applies (from the engine's last {@code interpolate}), so it
     * includes the visual bob, yaw and tilt the engine's own position does not.
     */
    private static Vec3 drawnPoint(FishSchoolSim sim, FishAnimationConfig anim, int i, float t,
                                   float mx, float my, float mz, float @Nullable [] jump) {
        PoseStack pose = new PoseStack();
        grill24.fishtastic.client.renderer.GroupFishPose.apply(
                pose, sim.engine(), anim, i, t, 0f, 0f, 0f, jump, POSE_RANDOM);
        org.joml.Vector3f p = pose.last().pose().transformPosition(mx, my, mz, new org.joml.Vector3f());
        BlockPos o = sim.origin();
        return new Vec3(o.getX() + p.x(), o.getY() + p.y(), o.getZ() + p.z());
    }

    /** {@link FishSchoolSim#surfaceJumpPose}'s array length. */
    private static final int JUMP_POSE_SIZE = 7;
    /** Jump splashes are plain water, never a quality tint: the tint is the catch cue (spec §7). */
    private static final int[] JUMP_SPLASH_TINTS = {0xF4FBFF, 0xDDEFFF, 0xC6E3F7};

    /**
     * A splash where a breaching fish's body crosses the surface ({@link FishSchoolSim#surfaceJumpCrossings}):
     * {@code at} is {@code [l, d, forward speed, hl, hd, entry]}. Leaving, a narrow sheet of droplets
     * drawn up and forward after the fish; going back in, a wider crown thrown out round the entry,
     * rings, and bubbles carried down after it. Both scale with the fish. Silent, as automatic
     * effects are.
     */
    private static void jumpSplash(Copy copy, FishSchoolSim sim,
                                   FishSchoolSim.SurfaceJumpCrossing crossing, float length) {
        if (crossing.profile() == grill24.fishtastic.school.SurfaceJumpPlaybackProfile.LEGENDARY_CINEMATIC) {
            FishSchoolSim.SurfaceJumpClock clock = sim.surfaceJumpClock(crossing.member());
            if (clock == null || !FishtasticClientConfig.isFishSchoolsVisible()) return;
            LegendaryBreachPresentation presentation = copy.breaches.computeIfAbsent(crossing.member(), member ->
                    new LegendaryBreachPresentation(copy.replica.school(), copy.replica.epoch(), member, clock));
            // A rebuild or a delayed copy initializes the current look without replaying old bursts.
            if (sim.steps() - crossing.presentationStep() <= 4) {
                presentation.onCrossing(sim, sim.origin(), crossing, length);
            } else if (!crossing.entry()) {
                presentation.reconstruct(sim, sim.origin(), clock,
                        FishtasticClientConfig.isReducedCelebrationEffects(),
                        Minecraft.getInstance().options.particles().get()
                                == net.minecraft.client.ParticleStatus.MINIMAL, length);
            }
            return;
        }
        float[] at = {crossing.l(), crossing.d(), crossing.forwardSpeed(), crossing.headingL(),
                crossing.headingD(), crossing.entry() ? 1f : 0f};
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        BlockPos origin = sim.origin();
        double x = origin.getX() + at[0], z = origin.getZ() + at[1];
        float surfaceLocal = sim.domain().surfaceAt(at[0], at[1]);
        if (!Float.isFinite(surfaceLocal)) return;
        double y = waterSurfaceAbove(x, origin.getY() + surfaceLocal - 0.2, z);
        if (!level.getFluidState(BlockPos.containing(x, y - 0.1, z)).is(FluidTags.WATER)) return;
        boolean entry = at[5] > 0.5f;
        double speed = at[2], hx = at[3], hz = at[4];
        float size = Math.max(0.6f, Math.min(1.6f, length / 0.4f));
        Random random = copy.trailRandom;
        int rings = entry ? 3 : 1;
        for (int ring = 0; ring < rings; ring++) {
            TrailMote mote = addMote(copy, x, y + 0.015, z, 0, 0, 0, (0.22f + ring * 0.2f) * size,
                    14 + ring * 8, RIPPLE_SPRITE, JUMP_SPLASH_TINTS);
            mote.flat = true;
        }
        int droplets = Math.round((entry ? 18 : 10) * size);
        for (int k = 0; k < droplets; k++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double vx, vy, vz;
            if (entry) {
                // A crown: thrown out all round, a little more back than ahead.
                double out = (0.03 + random.nextDouble() * 0.035) * size;
                vx = Math.cos(angle) * out - hx * speed * 0.15;
                vz = Math.sin(angle) * out - hz * speed * 0.15;
                vy = (0.12 + random.nextDouble() * 0.1) * Math.sqrt(size);
            } else {
                // A sheet drawn up after the fish: mostly along its heading, close in.
                double out = (0.008 + random.nextDouble() * 0.018) * size;
                vx = Math.cos(angle) * out + hx * speed * (0.3 + random.nextDouble() * 0.4);
                vz = Math.sin(angle) * out + hz * speed * (0.3 + random.nextDouble() * 0.4);
                vy = (0.14 + random.nextDouble() * 0.12) * Math.sqrt(size);
            }
            TrailMote drop = addMote(copy, x + (random.nextDouble() - 0.5) * 0.15 * size, y + 0.04,
                    z + (random.nextDouble() - 0.5) * 0.15 * size, vx, vy, vz,
                    (0.03f + random.nextFloat() * 0.025f) * size, FOUNTAIN_LIFETIME,
                    random.nextInt(RIPPLE_SPRITE), JUMP_SPLASH_TINTS);
            drop.gravity = FOUNTAIN_GRAVITY;
            drop.drag = 0.98f;
            drop.surfaceY = y;
            drop.splashes = entry;
            drop.held = true;
        }
        int splashes = Math.round((entry ? 12 : 6) * size);
        for (int k = 0; k < splashes; k++) {
            double angle = random.nextDouble() * Math.PI * 2.0, r = random.nextDouble() * 0.3 * size;
            level.addParticle(ParticleTypes.SPLASH, x + Math.cos(angle) * r, y + 0.02, z + Math.sin(angle) * r,
                    Math.cos(angle) * 0.05, 0.1, Math.sin(angle) * 0.05);
        }
        if (entry) {
            for (int k = 0; k < Math.round(6 * size); k++) {
                double down = 0.1 + random.nextDouble() * 0.4;
                level.addParticle(ParticleTypes.BUBBLE, x + hx * down * 0.6 + (random.nextDouble() - 0.5) * 0.2,
                        y - down, z + hz * down * 0.6 + (random.nextDouble() - 0.5) * 0.2, 0, 0.02, 0);
            }
        }
        while (copy.trail.size() > MAX_TRAIL_MOTES) copy.trail.remove(0);
    }

    /** Droplets thrown in one bite fountain, and how many of them are tall central jets. */
    private static final int FOUNTAIN_DROPLETS = 26, FOUNTAIN_JETS = 6;
    /** Fountain gravity, blocks/tick^2: about 3x a vanilla particle's, so droplets come down fast. */
    private static final float FOUNTAIN_GRAVITY = 0.03f;
    private static final int FOUNTAIN_LIFETIME = 45;

    /**
     * A bite burst where a school fish has just put its nose to a bobber, so a bite reads as a bite
     * and not as a fish that happened to drift close: a flash at the nose, then a fountain from the
     * water surface above it. Droplets fly up and out in every direction, gravity brings them back
     * quickly, and each one splashes as it drops below the surface ({@link #splash}). Driven by the
     * copy's own touch event, the same engine event the server's bite comes from.
     */
    private static void spawnBiteBurst(Copy copy, UUID anchor) {
        FishSchoolSim sim = copy.replica.sim();
        float[] at = sim == null ? null : sim.anchorAt(anchor);
        if (at == null) return;
        biteBursts++;
        BlockPos o = sim.origin();
        double x = o.getX() + at[0], y = o.getY() + at[1], z = o.getZ() + at[2];
        Random random = copy.trailRandom;
        int[] tints = tints(copy, sim.errandMember(anchor));
        // A brief flash at the nose first: the moment of the bite, before the fountain.
        addMote(copy, x, y, z, 0, 0.004, 0, 0.16f, 9, GLINT_SPRITE, tints);
        double surface = waterSurfaceAbove(x, y, z);
        addMote(copy, x, surface + 0.03, z, 0, 0.006, 0, 0.13f, 8, GLINT_SPRITE, tints);
        TrailMote ring = addMote(copy, x, surface + 0.015, z, 0, 0, 0, 0.3f, 16, RIPPLE_SPRITE, tints);
        ring.flat = true;
        for (int i = 0; i < FOUNTAIN_DROPLETS; i++) {
            boolean jet = i < FOUNTAIN_JETS;
            int slots = jet ? FOUNTAIN_JETS : FOUNTAIN_DROPLETS - FOUNTAIN_JETS;
            double angle = (i + random.nextDouble() * 0.7) / slots * Math.PI * 2.0;
            double out = jet ? 0.006 + random.nextDouble() * 0.012 : 0.022 + random.nextDouble() * 0.04;
            double up = jet ? 0.25 + random.nextDouble() * 0.07 : 0.15 + random.nextDouble() * 0.09;
            float size = (0.035f + random.nextFloat() * 0.03f) * (jet ? 1.2f : 1f);
            // Never the ripple ring in the air: it reads as a splash, not a droplet.
            TrailMote drop = addMote(copy, x, surface + 0.05, z, Math.cos(angle) * out, up, Math.sin(angle) * out,
                    size, FOUNTAIN_LIFETIME, random.nextInt(RIPPLE_SPRITE), tints);
            drop.gravity = FOUNTAIN_GRAVITY;
            drop.drag = 0.98f;
            drop.surfaceY = surface;
            drop.splashes = true;
            drop.held = true;
        }
        while (copy.trail.size() > MAX_TRAIL_MOTES) copy.trail.remove(0);
    }

    /**
     * A fountain droplet landing back in the water: a ripple ring lying on the surface, two tiny
     * droplets kicked back up (they sink without splashing again) and vanilla's splash particles.
     * A droplet that comes down over anything but water just fades.
     */
    private static void splash(Copy copy, TrailMote drop) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.getFluidState(BlockPos.containing(drop.x, drop.surfaceY - 0.1, drop.z))
                .is(FluidTags.WATER)) {
            return;
        }
        Random random = copy.trailRandom;
        int tint = (drop.red << 16) | (drop.green << 8) | drop.blue;
        TrailMote ring = addMote(copy, drop.x, drop.surfaceY + 0.015, drop.z, 0, 0, 0,
                drop.halfSize * 2.6f, 12 + random.nextInt(5), RIPPLE_SPRITE, tint);
        ring.flat = true;
        for (int i = 0; i < 2; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double out = 0.008 + random.nextDouble() * 0.012;
            TrailMote kick = addMote(copy, drop.x, drop.surfaceY + 0.02, drop.z,
                    Math.cos(angle) * out, 0.06 + random.nextDouble() * 0.04, Math.sin(angle) * out,
                    drop.halfSize * 0.55f, 14, GLINT_SPRITE, tint);
            kick.gravity = FOUNTAIN_GRAVITY;
            kick.surfaceY = drop.surfaceY;
            kick.held = true;
        }
        for (int i = 0; i < 2; i++) {
            level.addParticle(ParticleTypes.SPLASH, drop.x + (random.nextDouble() - 0.5) * 0.1, drop.surfaceY,
                    drop.z + (random.nextDouble() - 0.5) * 0.1, drop.vx, 0.05, drop.vz);
        }
    }

    /**
     * The top of the water column at {@code (x, z)}, from {@code y} upward: the fluid's own height in
     * its top block, so a bite fountain starts at the surface the player sees. {@code y} itself when
     * there is no water there.
     */
    private static double waterSurfaceAbove(double x, double y, double z) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return y;
        BlockPos.MutableBlockPos pos = BlockPos.containing(x, y, z).mutable();
        FluidState fluid = level.getFluidState(pos);
        if (!fluid.is(FluidTags.WATER)) return y;
        for (int i = 0; i < 16; i++) {
            pos.move(0, 1, 0);
            FluidState above = level.getFluidState(pos);
            if (!above.is(FluidTags.WATER)) {
                pos.move(0, -1, 0);
                return pos.getY() + fluid.getHeight(level, pos);
            }
            fluid = above;
        }
        return pos.getY();
    }

    /** The tints {@code member}'s motes are drawn in: its quality's, or the dev override's. */
    private static int[] tints(Copy copy, @Nullable UUID member) {
        if (moteTintOverride != null) return moteTintOverride;
        FishQuality.Quality quality = member == null ? null : copy.qualities.get(member);
        return quality == null ? DEFAULT_MOTE_TINTS : QUALITY_MOTE_TINTS[quality.ordinal()];
    }

    /** One mote on a random sprite sheet, in one of {@code tints}. */
    private static TrailMote addMote(Copy copy, double x, double y, double z, double driftX, double rise,
                                     double driftZ, float size, int lifetime, int[] tints) {
        return addMote(copy, x, y, z, driftX, rise, driftZ, size, lifetime,
                copy.trailRandom.nextInt(MOTE_SPRITES.length), tints);
    }

    private static TrailMote addMote(Copy copy, double x, double y, double z, double driftX, double rise,
                                     double driftZ, float size, int lifetime, int sprite, int[] tints) {
        return addMote(copy, x, y, z, driftX, rise, driftZ, size, lifetime, sprite,
                tints[copy.trailRandom.nextInt(tints.length)]);
    }

    private static TrailMote addMote(Copy copy, double x, double y, double z, double driftX, double rise,
                                     double driftZ, float size, int lifetime, int sprite, int tint) {
        if (debugTrailMarkers) tint = 0xFF1E1E;
        TrailMote mote = new TrailMote(x, y, z, driftX, rise, driftZ, size,
                copy.trailRandom.nextFloat() * (float) Math.PI,
                (tint >> 16) & 0xFF, (tint >> 8) & 0xFF, tint & 0xFF, sprite, lifetime);
        copy.trail.add(mote);
        return mote;
    }

    /**
     * Dev command hook: draws every school's new motes in these RGB tints, or the defaults when
     * {@code tints} is null or empty. Motes already alive keep their colour.
     */
    public static void setMoteTintOverride(int @Nullable [] tints) {
        moteTintOverride = tints == null || tints.length == 0 ? null : tints.clone();
    }

    // ------------------------------------------------------------------ draw

    public static void submitWorld(PoseStack poses, MultiBufferSource buffers, Frustum frustum, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !FishtasticClientConfig.isFishSchoolsVisible() || COPIES.isEmpty()) return;
        long started = Perf.enabled ? System.nanoTime() : 0L;
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = camera.getPosition();
        var forward = camera.getLookVector();
        float t = level.getGameTime() + partialTick;
        for (Copy copy : COPIES.values()) {
            FishSchoolSim sim = copy.replica.sim();
            if (sim == null || !copy.replica.caughtUp()) continue;
            FlockEngine engine = sim.engine();
            List<UUID> members = sim.members();
            int n = members.size();
            if (n > 0) {
                engine.interpolate(partialTick);
                BlockPos o = sim.origin();
                if (copy.depth.length < n) {
                    copy.depth = new float[n];
                    copy.order = new int[n];
                }
                float[] jump = copy.jumpPose;
                for (int i = 0; i < n; i++) {
                    copy.order[i] = i;
                    boolean jumping = sim.surfaceJumpPose(members.get(i), partialTick, jump);
                    copy.depth[i] = (float) ((o.getX() + engine.renderX[i] + (jumping ? jump[0] : 0f) - cam.x) * forward.x
                            + (o.getY() + engine.renderY[i] + (jumping ? jump[1] : 0f) - cam.y) * forward.y
                            + (o.getZ() + engine.renderZ[i] + (jumping ? jump[2] : 0f) - cam.z) * forward.z());
                }
                GroupFishDraw.backToFront(copy.depth, copy.order, n);
                float fade = copy.fadeTicks < 0f ? 1f
                        : clamp((copy.fadeTicks - partialTick) / DEPARTURE_FADE_TICKS, 0f, 1f);
                for (int k = 0; k < n; k++) {
                    int i = copy.order[k];
                    UUID member = members.get(i);
                    boolean jumping = sim.surfaceJumpPose(member, partialTick, jump);
                    GroupFishDraw.submit(poses, buffers, frustum, level, camera,
                            o.getX(), o.getY(), o.getZ(), engine, i, copy.anim, copy.stack, fade,
                            null, t, jumping ? jump : null, POSE_RANDOM);
                }
            }
            submitTrail(poses, buffers, camera, cam, copy, partialTick);
            for (LegendaryBreachPresentation breach : copy.breaches.values()) {
                breach.submitWorld(poses, buffers, camera, cam, sim, sim.origin(), partialTick);
            }
        }
        if (Perf.enabled) {
            long spent = System.nanoTime() - started;
            Perf.frames++;
            Perf.submitNanos += spent;
            Perf.maxSubmitNanos = Math.max(Perf.maxSubmitNanos, spent);
        }
    }


    /** One foreground overlay at a time: the nearest visible cinematic breach owns the HUD. */
    public static void renderHud(GuiGraphics graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !FishtasticClientConfig.isFishSchoolsVisible()) return;
        Camera camera = mc.gameRenderer.getMainCamera();
        LegendaryBreachPresentation selected = null;
        float nearest = Float.POSITIVE_INFINITY;
        float[] selectedScreen = null;
        for (Copy copy : COPIES.values()) {
            for (LegendaryBreachPresentation breach : copy.breaches.values()) {
                float priority = breach.hudPriority(camera.getPosition());
                if (!Float.isFinite(priority) || priority >= nearest) continue;
                float[] screen = breach.screenPosition(mc, camera);
                if (screen == null) continue;
                int width = mc.getWindow().getGuiScaledWidth(), height = mc.getWindow().getGuiScaledHeight();
                if (screen[0] < -width * 0.1f || screen[0] > width * 1.1f
                        || screen[1] < -height * 0.1f || screen[1] > height * 1.1f) continue;
                if (mc.player == null) continue;
                var hit = level.clip(new ClipContext(camera.getPosition(), breach.focusPosition(),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
                if (hit.getType() != HitResult.Type.MISS
                        && hit.getLocation().distanceToSqr(camera.getPosition()) + 0.35
                        < breach.focusPosition().distanceToSqr(camera.getPosition())) continue;
                nearest = priority;
                selected = breach;
                selectedScreen = screen;
            }
        }
        for (Copy copy : COPIES.values()) {
            for (LegendaryBreachPresentation breach : copy.breaches.values()) {
                if (breach == selected) breach.playPendingAudio(level);
                else breach.discardPendingAudio();
            }
        }
        if (selected != null && selectedScreen != null && !mc.options.hideGui) {
            selected.renderHud(graphics, mc, partialTick, selectedScreen[0], selectedScreen[1]);
        }
    }

    /**
     * Pearl/sea-glass motes stay at the fish's recent world positions, forming a readable motion
     * trail instead of a marker attached to the fish. This remains custom world geometry so the
     * functional cue is present even when the player's particle setting is minimal.
     */
    private static void submitTrail(PoseStack poses, MultiBufferSource buffers,
                                    Camera camera, Vec3 cam, Copy copy, float partialTick) {
        for (TrailMote mote : copy.trail) {
            double ageDelta = mote.clockOwner == null ? 1.0
                    : copy.sourceDeltas.getOrDefault(mote.clockOwner, 1.0);
            float age = mote.age + (float) (partialTick * ageDelta);
            float progress = clamp(age / mote.lifetime, 0f, 1f);
            float appear = smoothstep(clamp(age / 2f, 0f, 1f));
            float sunk = mote.sinkAge < 0 ? 0f : clamp((age - mote.sinkAge) / TrailMote.SINK_FADE_TICKS, 0f, 1f);
            float alpha = appear * (mote.held ? 1f : 1f - smoothstep(progress)) * (1f - sunk);
            if (alpha <= 0.015f) continue;

            double x = mote.previousX + (mote.x - mote.previousX) * partialTick;
            double y = mote.previousY + (mote.y - mote.previousY) * partialTick;
            double z = mote.previousZ + (mote.z - mote.previousZ) * partialTick;
            double distance = Math.sqrt(cam.distanceToSqr(x, y, z));
            float distanceBoost = clamp(0.95f + (float) distance * 0.018f, 1f, 1.28f);
            float pulse = 0.82f + (float) Math.sin(progress * Math.PI) * 0.22f;
            float halfSize = mote.halfSize * distanceBoost * pulse;
            int textureFrame = mote.held ? HELD_FRAME
                    : Math.min(GLINT_FRAME_COUNT - 1, (int) (progress * GLINT_FRAME_COUNT));

            poses.pushPose();
            poses.translate(x - cam.x, y - cam.y, z - cam.z);
            // A splash ring lies face-up on the water; everything else faces the camera.
            poses.mulPose(mote.flat ? Axis.XP.rotation((float) (-Math.PI / 2)) : camera.rotation());
            poses.mulPose(Axis.ZP.rotation(mote.rotation + progress * 0.16f));
            if (Perf.enabled) Perf.moteSubmits++;
            VertexConsumer buffer = buffers.getBuffer(MOTE_RENDER_TYPES[mote.sprite]);
            sparkleQuad(buffer, poses.last(), halfSize, alpha, mote.red, mote.green, mote.blue, textureFrame);
            poses.popPose();
        }
    }

    static void sparkleQuad(VertexConsumer buffer, PoseStack.Pose pose,
                                    float halfSize, float alpha, int red, int green, int blue,
                                    int textureFrame) {
        int a = (int) (clamp(alpha, 0f, 1f) * 255f);
        float u0 = 0.5f / 16f;
        float u1 = 15.5f / 16f;
        float v0 = (textureFrame * 16f + 0.5f) / (16f * GLINT_FRAME_COUNT);
        float v1 = (textureFrame * 16f + 15.5f) / (16f * GLINT_FRAME_COUNT);
        sparkleVertex(buffer, pose, -halfSize, halfSize, 0f, u0, v0, red, green, blue, a);
        sparkleVertex(buffer, pose, -halfSize, -halfSize, 0f, u0, v1, red, green, blue, a);
        sparkleVertex(buffer, pose, halfSize, -halfSize, 0f, u1, v1, red, green, blue, a);
        sparkleVertex(buffer, pose, halfSize, halfSize, 0f, u1, v0, red, green, blue, a);
    }

    private static void sparkleVertex(VertexConsumer buffer, PoseStack.Pose pose,
                                      float x, float y, float z, float u, float v,
                                      int red, int green, int blue, int alpha) {
        buffer.vertex(pose.pose(), x, y, z)
                .color(red, green, blue, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(FULL_BRIGHT)
                .normal(pose.normal(), 0f, 0f, 1f)
                .endVertex();
    }

    private static float smoothstep(float value) {
        float t = clamp(value, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Self-test: where member {@code i} of the first caught-up copy is now, in the world, or null. */
    public static @Nullable Vec3 memberPositionForSelfTest(int i) {
        for (Copy copy : COPIES.values()) {
            FishSchoolSim sim = copy.replica.sim();
            if (sim == null || !copy.replica.caughtUp() || i >= sim.members().size()) continue;
            BlockPos o = sim.origin();
            return new Vec3(o.getX() + sim.engine().posL()[i], o.getY() + sim.engine().posY()[i],
                    o.getZ() + sim.engine().posD()[i]);
        }
        return null;
    }

    /** Self-test probe: whether a replayed school copy is currently showing a surface jump. */
    public static boolean surfaceJumpActiveForSelfTest() {
        for (Copy copy : COPIES.values()) {
            FishSchoolSim sim = copy.replica.sim();
            if (sim == null) continue;
            for (UUID member : sim.members()) if (sim.hasSurfaceJump(member)) return true;
        }
        return false;
    }

    /** Self-test probe: a live client-side Legendary breach presentation is attached to its school. */
    public static boolean legendaryBreachActiveForSelfTest() {
        for (Copy copy : COPIES.values()) {
            for (LegendaryBreachPresentation breach : copy.breaches.values()) {
                if (breach.hasLiveClock()) return true;
            }
        }
        return false;
    }

    /** Self-test probe: current custom burst-particle count across tracked Legendary presentations. */
    public static int legendaryBreachParticleCountForSelfTest() {
        int particles = 0;
        for (Copy copy : COPIES.values()) {
            for (LegendaryBreachPresentation breach : copy.breaches.values()) {
                particles += breach.activeParticleCount();
            }
        }
        return particles;
    }

    /**
     * Self-test probe: the first jumping member's engine position (world) and leap heading,
     * {@code [x, y, z, hx, hz]}, so a scene can watch the arc side-on; null with no jump.
     */
    public static double @Nullable [] surfaceJumpViewForSelfTest() {
        float[] pose = new float[JUMP_POSE_SIZE];
        for (Copy copy : COPIES.values()) {
            FishSchoolSim sim = copy.replica.sim();
            if (sim == null) continue;
            List<UUID> members = sim.members();
            for (int i = 0; i < members.size(); i++) {
                if (!sim.surfaceJumpPose(members.get(i), 1f, pose)) continue;
                double yaw = Math.toRadians(pose[4] + sim.engine().frameYawDeg());
                BlockPos o = sim.origin();
                return new double[]{o.getX() + sim.engine().posL()[i], o.getY() + sim.engine().posY()[i],
                        o.getZ() + sim.engine().posD()[i], Math.cos(yaw), -Math.sin(yaw)};
            }
        }
        return null;
    }

    /** Self-test probe: largest rendered jump offset among the tracked school copies. */
    public static float maxSurfaceJumpOffsetForSelfTest(float partialTick) {
        float max = 0f;
        for (Copy copy : COPIES.values()) {
            FishSchoolSim sim = copy.replica.sim();
            if (sim == null) continue;
            for (UUID member : sim.members()) max = Math.max(max, sim.surfaceJumpOffset(member, partialTick));
        }
        return max;
    }

    /**
     * Self-test (performance plan step 0): builds a fresh copy of the first tracked school's setup
     * with {@code fish} members (its own placements repeated, nudged apart) and times
     * {@code steps} engine steps of it on this thread, as a late joiner's catch-up would run them.
     */
    public static String replayBenchForSelfTest(int fish, int steps) {
        for (Copy copy : COPIES.values()) {
            FishSchoolSim.Setup setup = copy.replica.setup();
            if (setup.members().isEmpty()) continue;
            List<FishSchoolSim.Placement> placements = new ArrayList<>(fish);
            for (int i = 0; i < fish; i++) {
                FishSchoolSim.Placement base = setup.members().get(i % setup.members().size());
                float nudge = 0.15f * (i / setup.members().size());
                placements.add(new FishSchoolSim.Placement(new UUID(0L, i), base.l() + nudge, base.y(), base.d() + nudge));
            }
            long created = System.nanoTime();
            FishSchoolSim sim = FishSchoolSim.create(new FishSchoolSim.Setup(setup.origin(), setup.columns(),
                    setup.spec(), setup.seed(), placements, setup.shape()));
            if (sim == null) return "setup did not build";
            long start = System.nanoTime();
            long chunkStart = start, worstChunk = 0L;
            for (int i = 1; i <= steps; i++) {
                sim.step(null);
                if (i % FishSchoolReplica.REPLAY_STEPS_PER_TICK == 0 || i == steps) {
                    long now = System.nanoTime();
                    worstChunk = Math.max(worstChunk, now - chunkStart);
                    chunkStart = now;
                }
            }
            long total = System.nanoTime() - start;
            return String.format(java.util.Locale.ROOT,
                    "fish=%d steps=%d create=%.2fms total=%.2fms perStep=%.2fus worst%dStepTick=%.2fms columns=%d",
                    fish, steps, (start - created) / 1e6, total / 1e6, total / 1e3 / steps,
                    FishSchoolReplica.REPLAY_STEPS_PER_TICK, worstChunk / 1e6, setup.columns().size());
        }
        return "no tracked school";
    }

    /** Debug: one line per tracked copy (step, target, caught up, replays, fingerprint mismatches). */
    public static String debugState() {
        if (COPIES.isEmpty()) return "No tracked fish schools.";
        StringBuilder out = new StringBuilder();
        for (Copy copy : COPIES.values()) {
            FishSchoolSim sim = copy.replica.sim();
            out.append(copy.replica.school()).append(" epoch=").append(copy.replica.epoch())
                    .append(" step=").append(sim == null ? -1 : sim.steps())
                    .append(" target=").append(copy.replica.targetStep())
                    .append(" caughtUp=").append(copy.replica.caughtUp())
                    .append(" replays=").append(copy.replica.replays())
                    .append(" mismatches=").append(copy.replica.mismatches())
                    .append(" members=").append(sim == null ? 0 : sim.members().size()).append('\n');
        }
        return out.toString().trim();
    }
}
