package grill24.fishtastic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.school.FishSchoolSim;
import grill24.fishtastic.school.SurfaceJumpPlaybackProfile;
import grill24.fishtastic.client.renderer.FishtasticRenderTypes;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** A bounded, client-owned presentation driven by a school's canonical jump source clock. */
public final class LegendaryBreachPresentation {
    private static final int[] RAINBOW = {
            0xFF596B, 0xFFB34F, 0xF8E66B, 0x8CF28D, 0x68E7F4, 0x7995FF, 0xD58BFF
    };
    private static final int FULL_PARTICLE_CAP = 160;
    private static final int REDUCED_PARTICLE_CAP = 32;
    /** Soft textures from tools/legendary-breach-gen: white, shape in alpha, so the tint sets the hue. */
    private static final ResourceLocation SUNBURST_TEXTURE = Fishtastic.id("textures/particle/legendary_sunburst.png");
    private static final ResourceLocation SPARKLE_TEXTURE = Fishtastic.id("textures/particle/legendary_sparkle.png");
    private static final ResourceLocation SPARKLE8_TEXTURE = Fishtastic.id("textures/particle/legendary_sparkle8.png");
    private static final RenderType SPARKLE_TYPE = sparkleType("legendary_sparkle", SPARKLE_TEXTURE);
    private static final RenderType SPARKLE8_TYPE = sparkleType("legendary_sparkle8", SPARKLE8_TEXTURE);
    private static final int SUNBURST_SIZE = 256, SPARKLE_SIZE = 64;
    /** Particle sprite ids below the shared mote sheets' indices: this event's own sparkles. */
    private static final int SPARKLE = -1, SPARKLE8 = -2;
    /** The sparkle's arms reach the quad's edge but fade early; this matches its read to the old glint's. */
    private static final float SPARKLE_SCALE = 1.7f;
    /** Twinkling stars round the fish near the apex, the first {@link #HERO_STARS} big eight-pointers. */
    private static final int WORLD_STARS = 13, SCREEN_STARS = 8, HERO_STARS = 3;
    private static final int FULL_BRIGHT = 15728880;
    /** Source ticks the ribbon takes to dissolve after re-entry, swept from the takeoff end to the landing. */
    private static final float RIBBON_FADE_TICKS = 24f;
    /** How much of the fade each bead spends going out; the rest is the sweep's head start along the ribbon. */
    private static final float RIBBON_BEAD_FADE = 0.45f;

    /** Glow-blended, like vanilla's emissive translucent entity type but adding light (FishtasticRenderPipelines). */
    private static RenderType sparkleType(String name, ResourceLocation texture) {
        return FishtasticRenderTypes.legendarySparkle(texture);
    }

    private static final class Particle {
        double px, py, pz, x, y, z;
        final double vx, vy, vz;
        final float size;
        final int color, sprite;
        final float lifetime, spin, phase;
        float age;
        Particle(double x, double y, double z, double vx, double vy, double vz,
                 float size, float lifetime, int color, int sprite, float spin, float phase) {
            this.px = this.x = x;
            this.py = this.y = y;
            this.pz = this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.size = size;
            this.lifetime = lifetime;
            this.color = color;
            this.sprite = sprite;
            this.spin = spin;
            this.phase = phase;
        }

        void tick(double delta) {
            px = x;
            py = y;
            pz = z;
            x += vx * delta;
            y += vy * delta;
            z += vz * delta;
            age += (float) delta;
        }

        boolean expired() { return age >= lifetime; }
    }

    private final UUID school;
    private final int epoch;
    private final UUID member;
    private final long startStep;
    private final Random random;
    /** Seeds the twinkling star field; derived from the event seed so every observer's stars match. */
    private final long starSeed;
    private final List<Particle> particles = new ArrayList<>();
    private double previousSourceTime;
    private double sourceTime;
    private double exitSourceTime;
    private double apexSourceTime;
    private double entrySourceTime;
    private Vec3 focusPosition = Vec3.ZERO;
    private Vec3 entryPosition = Vec3.ZERO;
    private boolean hasExit;
    private boolean hasEntry;
    private boolean exitBurstEmitted;
    private boolean entryBurstEmitted;
    private boolean takeoffCuePlayed;
    private boolean apexCuePlayed;
    private boolean entryCuePlayed;
    private boolean takeoffCuePending;
    private boolean apexCuePending;
    private boolean entryCuePending;
    private boolean cancelled;
    private float cleanupTicks = -1f;
    private float entryAge;
    private boolean reduced;
    private boolean minimalParticles;
    /**
     * The ribbon as last drawn (bead positions, sizes, colours, spins), so it can dissolve after the
     * fish lands or the jump is cancelled, when the arc can no longer be sampled.
     */
    private Vec3[] ribbonPoints = new Vec3[0];
    private float[] ribbonSizes = new float[0], ribbonSpins = new float[0];
    private int[] ribbonColors = new int[0];
    private int ribbonCount;
    /** Whether the cached ribbon already runs the whole way from exit to entry. */
    private boolean ribbonComplete;
    /**
     * How far the last tick advanced particle ages and {@link #entryAge}. Positions are drawn lerped
     * from last tick to this one, so ages are drawn the same way ({@link #renderAge}); drawn at the
     * tick's value, sizes, spins and ring radii stepped at 20 Hz while the motes slid smoothly.
     */
    private float lastParticleDelta, lastEntryDelta;

    public LegendaryBreachPresentation(UUID school, int epoch, UUID member,
                                       FishSchoolSim.SurfaceJumpClock clock) {
        this.school = school;
        this.epoch = epoch;
        this.member = member;
        this.startStep = clock.startStep();
        this.previousSourceTime = clock.previousSourceTime();
        this.sourceTime = clock.sourceTime();
        this.exitSourceTime = clock.exitSourceTime();
        this.apexSourceTime = clock.apexSourceTime();
        this.entrySourceTime = clock.entrySourceTime();
        long seed = school.getMostSignificantBits() ^ Long.rotateLeft(school.getLeastSignificantBits(), 19)
                ^ ((long) epoch << 32) ^ member.getMostSignificantBits()
                ^ Long.rotateLeft(member.getLeastSignificantBits(), 7) ^ startStep;
        this.random = new Random(seed);
        this.starSeed = seed * 0x9E3779B97F4A7C15L;
    }

    public UUID member() { return member; }
    public Vec3 focusPosition() { return focusPosition; }
    public int activeParticleCount() { return particles.size(); }
    public boolean expired() { return cleanupTicks == 0f && particles.isEmpty(); }
    public boolean hasLiveClock() { return cleanupTicks < 0f && !cancelled; }

    /** Reconstructs an event for a late tracker without replaying its one-shot takeoff effects. */
    public void reconstruct(FishSchoolSim sim, BlockPos origin, FishSchoolSim.SurfaceJumpClock clock,
                            boolean reduced, boolean minimalParticles, float length) {
        syncClock(clock);
        this.reduced = reduced;
        this.minimalParticles = minimalParticles;
        trimParticleCap();
        double[] pose = new double[7];
        boolean exitAlreadyObserved = clock.previousSourceTime() >= exitSourceTime;
        boolean apexAlreadyObserved = clock.previousSourceTime() >= apexSourceTime;
        boolean entryAlreadyObserved = clock.previousSourceTime() >= entrySourceTime;
        if (sourceTime >= exitSourceTime && exitAlreadyObserved
                && sim.surfaceJumpArcSample(member, exitSourceTime, pose)) {
            hasExit = true;
            exitBurstEmitted = true;
            takeoffCuePlayed = true;
        }
        apexCuePlayed = apexAlreadyObserved;
        if (sourceTime >= entrySourceTime && entryAlreadyObserved
                && sim.surfaceJumpArcSample(member, entrySourceTime, pose)) {
            hasEntry = true;
            entryBurstEmitted = true;
            entryCuePlayed = true;
            entryPosition = world(origin, pose);
            entryAge = (float) (sourceTime - entrySourceTime);
        }
        if (clock.cancelled()) beginCleanup(4f);
        updateFocus(sim, origin, length);
    }

    /** Advances event particles by source-clock delta, preserving their own fractional time. */
    public void tick(FishSchoolSim sim, BlockPos origin, FishSchoolSim.SurfaceJumpClock clock,
                     boolean reduced, boolean minimalParticles, float length) {
        this.reduced = reduced;
        this.minimalParticles = minimalParticles;
        trimParticleCap();
        if (clock != null) {
            double oldSourceTime = sourceTime;
            double newSourceTime = clock.sourceTime();
            double sourceDelta = Math.max(0.0, newSourceTime - oldSourceTime);
            syncClock(clock);
            updateFocus(sim, origin, length);
            if (clock.cancelled() && !hasEntry) beginCleanup(4f);
            if (!clock.cancelled() && cleanupTicks < 0f && !apexCuePlayed
                    && crossedSourceThreshold(oldSourceTime, newSourceTime, apexSourceTime)) {
                apexCuePlayed = true;
                apexCuePending = true;
            }
            tickParticles(cleanupTicks >= 0f ? 1.0
                    : Math.min(FishSchoolSim.MAX_SURFACE_JUMP_PRESENTATION_TICKS, sourceDelta));
            lastEntryDelta = hasEntry ? (float) Math.max(0.0, sourceDelta) : 0f;
            entryAge += lastEntryDelta;
            if (clock.sourceTime() >= exitSourceTime) hasExit = true;
            if (cleanupTicks > 0f) cleanupTicks = Math.max(0f, cleanupTicks - 1f);
        } else {
            if (cleanupTicks < 0f) beginCleanup(hasEntry ? 20f : 4f);
            tickParticles(1.0);
            lastEntryDelta = hasEntry ? 1f : 0f;
            entryAge += lastEntryDelta;
            if (cleanupTicks > 0f) cleanupTicks = Math.max(0f, cleanupTicks - 1f);
        }
    }

    static boolean crossedSourceThreshold(double previous, double current, double threshold) {
        return previous < threshold && current >= threshold;
    }

    private void syncClock(FishSchoolSim.SurfaceJumpClock clock) {
        previousSourceTime = clock.previousSourceTime();
        sourceTime = clock.sourceTime();
        exitSourceTime = clock.exitSourceTime();
        apexSourceTime = clock.apexSourceTime();
        entrySourceTime = clock.entrySourceTime();
    }

    private void updateFocus(FishSchoolSim sim, BlockPos origin, float length) {
        double[] pose = new double[7];
        if (sim.surfaceJumpArcSample(member, sourceTime, pose)) {
            focusPosition = world(origin, pose).add(0, Math.max(0.03, length * 0.08), 0);
        }
    }

    /** Starts the takeoff or entry burst only when the live crossing is observed. */
    public void onCrossing(FishSchoolSim sim, BlockPos origin, FishSchoolSim.SurfaceJumpCrossing crossing,
                           float length) {
        if (crossing.profile() != SurfaceJumpPlaybackProfile.LEGENDARY_CINEMATIC) return;
        double[] pose = new double[7];
        if (!sim.surfaceJumpArcSample(member, crossing.sourceTime(), pose)) return;
        Vec3 at = world(origin, pose);
        if (crossing.entry()) {
            if (entryBurstEmitted) return;
            entryBurstEmitted = true;
            if (!entryCuePlayed) {
                entryCuePlayed = true;
                entryCuePending = true;
            }
            hasEntry = true;
            entryPosition = at;
            entryAge = 0f;
            int count = particleCount(48, 12);
            for (int i = 0; i < count; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0;
                double out = 0.018 + random.nextDouble() * 0.045;
                double depth = random.nextDouble() * 0.25;
                addParticle(at.x + Math.cos(angle) * depth, at.y + 0.04,
                        at.z + Math.sin(angle) * depth,
                        Math.cos(angle) * out, 0.05 + random.nextDouble() * 0.11,
                        Math.sin(angle) * out, 0.045f + random.nextFloat() * 0.075f,
                        18f + random.nextFloat() * 12f, RAINBOW[random.nextInt(RAINBOW.length)],
                        FishSchoolClientRuntime.RIPPLE_SPRITE);
            }
        } else if (!exitBurstEmitted) {
            exitBurstEmitted = true;
            if (!takeoffCuePlayed) {
                takeoffCuePlayed = true;
                takeoffCuePending = true;
            }
            hasExit = true;
            int count = particleCount(40, 12);
            for (int i = 0; i < count; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0;
                double out = 0.008 + random.nextDouble() * 0.032;
                addParticle(at.x + (random.nextDouble() - 0.5) * 0.18,
                        at.y + 0.025 + random.nextDouble() * 0.08,
                        at.z + (random.nextDouble() - 0.5) * 0.18,
                        Math.cos(angle) * out + crossing.headingL() * crossing.forwardSpeed() * 0.25,
                        0.06 + random.nextDouble() * 0.18,
                        Math.sin(angle) * out + crossing.headingD() * crossing.forwardSpeed() * 0.25,
                        0.04f + random.nextFloat() * 0.07f, 15f + random.nextFloat() * 14f,
                        RAINBOW[random.nextInt(RAINBOW.length)], SPARKLE);
            }
        }
        updateFocus(sim, origin, length);
    }

    /** Stops an orphaned event after a catch, leave, hide or water cancellation. */
    public void stop(boolean fast) {
        beginCleanup(fast ? 4f : hasEntry ? 20f : 4f);
    }

    private void beginCleanup(float ticks) {
        cancelled |= !hasEntry;
        cleanupTicks = cleanupTicks < 0f ? ticks : Math.min(cleanupTicks, ticks);
        discardPendingAudio();
    }

    /** Audio belongs to the foreground visibility decision in FishSchoolClientRuntime. */
    public void playPendingAudio(net.minecraft.client.multiplayer.ClientLevel level) {
        float volume = reduced ? 0.55f : 1f;
        if (takeoffCuePending) {
            level.playLocalSound(focusPosition.x, focusPosition.y, focusPosition.z, SoundEvents.NOTE_BLOCK_CHIME.value(),
                    SoundSource.AMBIENT, 0.32f * volume, 1.52f, false);
        }
        if (apexCuePending) {
            level.playLocalSound(focusPosition.x, focusPosition.y, focusPosition.z, SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.AMBIENT, 0.38f * volume, 0.86f, false);
            level.playLocalSound(focusPosition.x, focusPosition.y, focusPosition.z, SoundEvents.BELL_BLOCK,
                    SoundSource.AMBIENT, 0.23f * volume, 1.18f, false);
        }
        if (entryCuePending) {
            level.playLocalSound(focusPosition.x, focusPosition.y, focusPosition.z, SoundEvents.NOTE_BLOCK_CHIME.value(),
                    SoundSource.AMBIENT, 0.28f * volume, 1.18f, false);
        }
        discardPendingAudio();
    }

    public void discardPendingAudio() {
        takeoffCuePending = false;
        apexCuePending = false;
        entryCuePending = false;
    }

    private int particleCount(int full, int reducedCount) {
        if (minimalParticles) return Math.min(reduced ? 4 : 12, reduced ? reducedCount : full);
        return reduced ? reducedCount : full;
    }

    private void addParticle(double x, double y, double z, double vx, double vy, double vz,
                             float size, float lifetime, int color, int sprite) {
        trimParticleCap();
        int cap = reduced ? REDUCED_PARTICLE_CAP : FULL_PARTICLE_CAP;
        while (particles.size() >= cap) particles.remove(0);
        particles.add(new Particle(x, y, z, vx, vy, vz, size, lifetime, color, sprite,
                (random.nextFloat() - 0.5f) * 0.12f, random.nextFloat() * (float) (Math.PI * 2)));
    }

    private void trimParticleCap() {
        int cap = reduced ? REDUCED_PARTICLE_CAP : FULL_PARTICLE_CAP;
        while (particles.size() > cap) particles.remove(0);
    }

    private void tickParticles(double delta) {
        lastParticleDelta = (float) delta;
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle particle = particles.get(i);
            particle.tick(delta);
            if (particle.expired()) particles.remove(i);
        }
    }

    /** Draws the revealed ribbon, flare stars, entry rings and custom particles in world space. */
    public void submitWorld(PoseStack poses, MultiBufferSource buffers, Camera camera, Vec3 cameraPosition,
                            FishSchoolSim sim, BlockPos origin, float partialTick) {
        double renderSource = previousSourceTime + (sourceTime - previousSourceTime) * partialTick;
        boolean airborne = hasExit && !hasEntry && renderSource > exitSourceTime && cleanupTicks < 0f;
        if (sim != null && airborne) {
            sampleRibbon(sim, origin, Math.min(renderSource, entrySourceTime));
        } else if (sim != null && hasEntry && !ribbonComplete) {
            // Landed: finish the ribbon to the entry point once, while the arc can still be sampled.
            ribbonComplete = sampleRibbon(sim, origin, entrySourceTime);
        }
        submitRibbon(poses, buffers, camera, cameraPosition, partialTick);
        if (sim != null && airborne) {

            double apexWeight = 1.0 - Math.min(1.0, Math.abs(renderSource - apexSourceTime)
                    / Math.max(1.0, (entrySourceTime - exitSourceTime) * 0.32));
            if (!reduced && apexWeight > 0.0) {
                double[] pose = new double[7];
                if (sim.surfaceJumpArcSample(member, renderSource, pose)) {
                    Vec3 center = world(origin, pose);
                    org.joml.Vector3fc forward = camera.getLookVector();
                    Vec3 backdrop = center.add(forward.x() * 0.18, forward.y() * 0.18, forward.z() * 0.18);
                    // A soft pink/gold radial core plus a sharp white-gold cross reads as a
                    // celebratory lens flare behind the fish without introducing new art assets.
                    submitBillboard(poses, buffers, camera, cameraPosition, backdrop,
                            (float) (0.78 + apexWeight * 0.48), (float) (apexWeight * 0.62),
                            0xF58BDE, FishSchoolClientRuntime.PLANKTON_ORB_SPRITE, 0f);
                    submitBillboard(poses, buffers, camera, cameraPosition, backdrop,
                            (float) (0.52 + apexWeight * 0.38), (float) (apexWeight * 0.48),
                            0xFFD982, FishSchoolClientRuntime.PLANKTON_ORB_SPRITE, 0f);
                    // The hero star: an eight-pointer that breathes and turns slowly on the fish.
                    float pulse = 1f + 0.08f * (float) Math.sin(renderSource * 0.9);
                    submitSparkle(poses, buffers, camera, cameraPosition, center,
                            (float) (0.46 + apexWeight * 0.34) * pulse, (float) (apexWeight * 0.95),
                            0xFFE7A8, true, (float) (renderSource * 0.018));
                    float fieldAlpha = (float) Math.min(1.0, apexWeight * 1.6);
                    for (int i = 0; i < WORLD_STARS; i++) {
                        Star star = star(i, renderSource, 0.3, 1.15);
                        if (star.envelope <= 0f) continue;
                        boolean hero = i < HERO_STARS;
                        float size = (hero ? 0.2f + 0.1f * star.pick : 0.05f + 0.08f * star.pick) * star.envelope;
                        Vec3 at = center.add(star.dx * star.radius, star.dy * star.radius * 0.8,
                                star.dz * star.radius);
                        submitSparkle(poses, buffers, camera, cameraPosition, at, size,
                                fieldAlpha * Math.min(1f, star.envelope * 1.2f), star.color, hero, star.rotation);
                    }
                }
            }
        }

        for (Particle particle : particles) {
            float age = renderAge(particle.age, lastParticleDelta, partialTick);
            float progress = Math.min(1f, age / particle.lifetime);
            float alpha = (1f - progress) * Math.min(1f, age * 2f);
            if (cleanupTicks >= 0f) alpha *= Math.min(1f, cleanupTicks / 4f);
            if (alpha <= 0.015f) continue;
            double x = particle.px + (particle.x - particle.px) * partialTick;
            double y = particle.py + (particle.y - particle.py) * partialTick;
            double z = particle.pz + (particle.z - particle.pz) * partialTick;
            if (particle.sprite < 0) {
                // A sparkle twinkles through its own life instead of fading flatly.
                float envelope = twinkle(progress);
                if (cleanupTicks >= 0f) envelope *= Math.min(1f, cleanupTicks / 4f);
                if (envelope <= 0.015f) continue;
                submitSparkle(poses, buffers, camera, cameraPosition, new Vec3(x, y, z),
                        particle.size * SPARKLE_SCALE * envelope, Math.min(1f, envelope * 1.2f), particle.color,
                        particle.sprite == SPARKLE8, particle.phase + particle.spin * age);
                continue;
            }
            submitBillboard(poses, buffers, camera, cameraPosition, new Vec3(x, y, z), particle.size,
                    alpha, particle.color, particle.sprite, progress * 0.3f);
        }

        float ringAge = renderAge(entryAge, lastEntryDelta, partialTick);
        if (hasEntry && ringAge < 20f && cleanupTicks != 0f) {
            float fade = Math.min(1f, (20f - ringAge) / 4f);
            for (int ring = 0; ring < 3; ring++) {
                float radius = 0.32f + ring * 0.24f + ringAge * (0.045f + ring * 0.009f);
                submitFlatRing(poses, buffers, cameraPosition, entryPosition.add(0, 0.025 + ring * 0.002, 0), radius,
                        fade * (0.62f - ring * 0.11f), RAINBOW[(ring * 2 + 4) % RAINBOW.length]);
            }
        }
    }

    private static void submitBillboard(PoseStack poses, MultiBufferSource buffers, Camera camera, Vec3 cameraPosition,
                                        Vec3 point, float size, float alpha, int color, int sprite, float rotation) {
        poses.pushPose();
        poses.translate(point.x - cameraPosition.x, point.y - cameraPosition.y, point.z - cameraPosition.z);
        poses.mulPose(camera.rotation());
        poses.mulPose(Axis.ZP.rotation(rotation));
        FishSchoolClientRuntime.sparkleQuad(buffers.getBuffer(FishSchoolClientRuntime.MOTE_RENDER_TYPES[sprite]),
                poses.last(), size, alpha, (color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, 4);
        poses.popPose();
    }

    /**
     * Samples the revealed ribbon from exit to {@code end} source time into the cache. False if the
     * arc could not be sampled the whole way (the cache then keeps what was drawn last).
     */
    private boolean sampleRibbon(FishSchoolSim sim, BlockPos origin, double end) {
        int samples = reduced ? 24 : 48;
        if (ribbonPoints.length != samples) {
            ribbonPoints = new Vec3[samples];
            ribbonSizes = new float[samples];
            ribbonSpins = new float[samples];
            ribbonColors = new int[samples];
            ribbonCount = 0;
        }
        double[] pose = new double[7];
        for (int i = 0; i < samples; i++) {
            double u = i / (double) Math.max(1, samples - 1);
            double source = exitSourceTime + (end - exitSourceTime) * u;
            if (!sim.surfaceJumpArcSample(member, source, pose)) return false;
            ribbonPoints[i] = world(origin, pose).add(0, 0.025 + Math.sin(u * Math.PI) * 0.07, 0);
            ribbonSizes[i] = (reduced ? 0.06f : 0.085f) + (float) Math.sin(u * Math.PI) * 0.035f;
            ribbonSpins[i] = (float) (source * 0.045);
            ribbonColors[i] = RAINBOW[Math.min(RAINBOW.length - 1, (int) (u * RAINBOW.length))];
        }
        ribbonCount = samples;
        return true;
    }

    /**
     * Draws the cached ribbon. In flight it is whole. After re-entry it dissolves over
     * {@link #RIBBON_FADE_TICKS}, the fade sweeping from the takeoff end to the landing, each bead
     * shrinking, fading and drifting up as it goes; a cancelled jump fades it with the cleanup instead.
     */
    private void submitRibbon(PoseStack poses, MultiBufferSource buffers, Camera camera, Vec3 cameraPosition,
                              float partialTick) {
        if (ribbonCount == 0) return;
        float baseAlpha = reduced ? 0.42f : 0.78f;
        float sweep = hasEntry
                ? Math.min(1f, renderAge(entryAge, lastEntryDelta, partialTick) / RIBBON_FADE_TICKS) : 0f;
        // The sweep ends well inside the 20-tick post-landing cleanup; only a fast stop or a
        // cancelled flight cuts it short, and then the whole ribbon fades with the cleanup.
        float cleanup = cleanupTicks < 0f ? 1f : Math.max(0f, Math.min(1f, (cleanupTicks - partialTick) / 4f));
        for (int i = 0; i < ribbonCount; i++) {
            float u = i / (float) Math.max(1, ribbonCount - 1);
            // 0 while the bead is whole, 1 once it is gone; each bead starts later the nearer the landing.
            float gone = hasEntry ? smoothstep((sweep - u * (1f - RIBBON_BEAD_FADE)) / RIBBON_BEAD_FADE) : 0f;
            float alpha = baseAlpha * (1f - gone) * cleanup;
            if (alpha <= 0.01f) continue;
            Vec3 point = gone > 0f ? ribbonPoints[i].add(0, gone * 0.18, 0) : ribbonPoints[i];
            submitBillboard(poses, buffers, camera, cameraPosition, point, ribbonSizes[i] * (1f - 0.6f * gone),
                    alpha, ribbonColors[i], FishSchoolClientRuntime.GLINT_SPRITE, ribbonSpins[i] + gone * 1.2f);
        }
    }

    /** An age as it stood {@code partialTick} of the way through the last tick, which advanced it by {@code delta}. */
    static float renderAge(float age, float delta, float partialTick) {
        return Math.max(0f, age - delta * (1f - partialTick));
    }

    private static float smoothstep(float value) {
        float t = Math.max(0f, Math.min(1f, value));
        return t * t * (3f - 2f * t);
    }

    /**
     * A soft sparkle: a tinted glow of the whole star with a near-white copy at its heart, so it
     * reads as a hot core in a coloured halo rather than a flat-tinted cross.
     */
    private static void submitSparkle(PoseStack poses, MultiBufferSource buffers, Camera camera, Vec3 cameraPosition,
                                      Vec3 point, float size, float alpha, int color, boolean eight, float rotation) {
        if (size <= 0.001f || alpha <= 0.01f) return;
        RenderType type = eight ? SPARKLE8_TYPE : SPARKLE_TYPE;
        int core = mix(color, 0xFFFFFF, 0.75f);
        poses.pushPose();
        poses.translate(point.x - cameraPosition.x, point.y - cameraPosition.y, point.z - cameraPosition.z);
        poses.mulPose(camera.rotation());
        poses.mulPose(Axis.ZP.rotation(rotation));
        VertexConsumer buffer = buffers.getBuffer(type);
        fullQuad(buffer, poses.last(), size, alpha * 0.85f, color);
        fullQuad(buffer, poses.last(), size * 0.5f, alpha, core);
        poses.popPose();
    }

    private static void fullQuad(VertexConsumer buffer, PoseStack.Pose pose, float half, float alpha, int color) {
        int a = (int) (Math.max(0f, Math.min(1f, alpha)) * 255f);
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        fullVertex(buffer, pose, -half, half, 0f, 0f, r, g, b, a);
        fullVertex(buffer, pose, -half, -half, 0f, 1f, r, g, b, a);
        fullVertex(buffer, pose, half, -half, 1f, 1f, r, g, b, a);
        fullVertex(buffer, pose, half, half, 1f, 0f, r, g, b, a);
    }

    private static void fullVertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float u, float v,
                                   int r, int g, int b, int a) {
        buffer.addVertex(pose, x, y, 0f)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, 0f, 0f, 1f);
    }

    private static int mix(int from, int to, float t) {
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }

    /**
     * Twinkle over a star's life, 0..1 in, size scale out: it pops in fast, swells, flares briefly
     * just before its peak, then shrinks away to nothing. Zero outside the life.
     */
    static float twinkle(float life) {
        if (life <= 0f || life >= 1f) return 0f;
        float pop = Math.min(1f, life / 0.12f);
        pop = 1f - (1f - pop) * (1f - pop);
        float body = (float) Math.sin(Math.PI * Math.pow(life, 0.8));
        float flare = (float) Math.exp(-Math.pow((life - 0.4f) / 0.06f, 2)) * 0.45f;
        return pop * (body + flare);
    }

    /** One twinkling star of a field at this moment: where (unit direction, radius), how big, what colour. */
    private record Star(double dx, double dy, double dz, double radius, float envelope, float pick,
                        int color, float rotation) {}

    /**
     * Star {@code index} of the field at {@code source} time. Each star lives a seeded number of
     * source ticks then is reborn somewhere new, so the field never settles into a pattern; it runs
     * on the jump's source clock, so it twinkles in slow motion with everything else near the apex.
     */
    private Star star(int index, double source, double minRadius, double maxRadius) {
        long base = mixBits(starSeed + index * 0x632BE59BD9B4E019L);
        double period = 8.0 + unit(base) * 9.0;
        double offset = unit(mixBits(base + 1)) * period;
        long cycle = (long) Math.floor((source + offset) / period);
        float life = (float) ((source + offset) / period - cycle);
        long h = mixBits(base ^ (cycle * 0xD1B54A32D192ED03L));
        double z = unit(h) * 2.0 - 1.0, a = unit(mixBits(h + 1)) * Math.PI * 2.0;
        double flat = Math.sqrt(1.0 - z * z);
        double radius = minRadius + (maxRadius - minRadius) * Math.sqrt(unit(mixBits(h + 2)));
        float pick = (float) unit(mixBits(h + 3));
        int color = RAINBOW[(int) (unit(mixBits(h + 4)) * RAINBOW.length) % RAINBOW.length];
        float rotation = (float) (unit(mixBits(h + 5)) * Math.PI + source * (pick - 0.5) * 0.04);
        return new Star(flat * Math.cos(a), z, flat * Math.sin(a), radius, twinkle(life), pick, color, rotation);
    }

    private static long mixBits(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static double unit(long bits) {
        return (bits >>> 11) * 0x1.0p-53;
    }

    private static void submitFlatRing(PoseStack poses, MultiBufferSource buffers, Vec3 cameraPosition, Vec3 point, float radius,
                                       float alpha, int color) {
        poses.pushPose();
        poses.translate(point.x - cameraPosition.x, point.y - cameraPosition.y, point.z - cameraPosition.z);
        poses.mulPose(Axis.XP.rotation((float) (-Math.PI / 2)));
        FishSchoolClientRuntime.sparkleQuad(buffers.getBuffer(
                FishSchoolClientRuntime.MOTE_RENDER_TYPES[FishSchoolClientRuntime.RIPPLE_SPRITE]), poses.last(), radius,
                alpha, (color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, 4);
        poses.popPose();
    }

    private static Vec3 world(BlockPos origin, double[] pose) {
        return new Vec3(origin.getX() + pose[0], origin.getY() + pose[1], origin.getZ() + pose[2]);
    }

    /** Low-alpha cyan/pink treatment, a glowing sunburst and twinkling sparkles; reduced mode omits screen effects. */
    public void renderHud(GuiGraphics graphics, Minecraft mc, float partialTick,
                          float screenX, float screenY) {
        if (reduced || cancelled || cleanupTicks == 0f || sourceTime < exitSourceTime) return;
        float source = (float) (previousSourceTime + (sourceTime - previousSourceTime) * partialTick);
        float apexWidth = (float) Math.max(1.0, (entrySourceTime - exitSourceTime) * 0.45);
        float apex = 1f - Math.min(1f, (float) (Math.abs(source - apexSourceTime) / apexWidth));
        float takeoff = (float) Math.min(1.0, (source - exitSourceTime) / 5.0);
        float fade = hasEntry ? Math.max(0f, 1f - entryAge / 20f) : 1f;
        float strength = Math.max(0f, Math.min(1f, (0.25f + apex * 0.75f) * takeoff * fade));
        int width = mc.getWindow().getGuiScaledWidth(), height = mc.getWindow().getGuiScaledHeight();
        int cyanAlpha = Math.round(17 * strength);
        int pinkAlpha = Math.round(11 * strength);
        if (cyanAlpha > 0) graphics.fill(0, 0, width, height, (cyanAlpha << 24) | 0x42DDEA);
        if (pinkAlpha > 0) graphics.fill(0, 0, width, height, (pinkAlpha << 24) | 0xED6DCA);

        graphics.pose().pushPose();
        graphics.pose().translate(screenX, screenY, 0);
        // Two counter-turning sunbursts, cyan over pink, glow-blended: soft tapered rays that start
        // clear of the fish and fade out well before the screen edge.
        int span = Math.max(width, height);
        float rays = (0.16f + apex * 0.42f) * strength;
        drawGlow(graphics, SUNBURST_TEXTURE, SUNBURST_SIZE, span * 1.2f, source * 0.011f, 0xB9F6F2, rays);
        drawGlow(graphics, SUNBURST_TEXTURE, SUNBURST_SIZE, span * 0.9f, 0.45f - source * 0.016f, 0xF2B2E2,
                rays * 0.8f);
        if (apex > 0.03f) {
            float reach = span * 0.14f;
            for (int i = 0; i < SCREEN_STARS; i++) {
                Star star = star(WORLD_STARS + i, source, 0.18, 1.0);
                if (star.envelope <= 0f) continue;
                boolean hero = i < 2;
                float size = (hero ? 22f + 10f * star.pick : 7f + 6f * star.pick) * star.envelope;
                float alpha = Math.min(1f, star.envelope * 1.2f) * apex * strength;
                graphics.pose().pushPose();
                graphics.pose().translate((float) (star.dx * star.radius * reach),
                        (float) (star.dy * star.radius * reach * 0.7), 0);
                drawGlow(graphics, hero ? SPARKLE8_TEXTURE : SPARKLE_TEXTURE, SPARKLE_SIZE, size, star.rotation,
                        star.color, alpha * 0.8f);
                drawGlow(graphics, hero ? SPARKLE8_TEXTURE : SPARKLE_TEXTURE, SPARKLE_SIZE, size * 0.5f,
                        star.rotation, 0xFFFFFF, alpha);
                graphics.pose().popPose();
            }
        }
        graphics.pose().popPose();
    }

    /** A whole glow texture centred on the current origin, {@code size} GUI pixels across, turned and tinted. */
    private static void drawGlow(GuiGraphics graphics, ResourceLocation texture, int textureSize, float size,
                                 float rotation, int color, float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        if (a == 0 || size < 0.5f) return;
        int half = textureSize / 2;
        graphics.pose().pushPose();
        graphics.pose().mulPose(Axis.ZP.rotation(rotation));
        graphics.pose().scale(size / textureSize, size / textureSize, 1f);
        // This legacy blit overload submits a POSITION_TEX quad directly and does not enable
        // blending. Without it the PNG's soft alpha is ignored and the rays become solid wedges.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f,
                (color & 0xFF) / 255f, a / 255f);
        graphics.blit(texture, -half, -half, 0f, 0f, textureSize, textureSize, textureSize, textureSize);
        graphics.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        graphics.pose().popPose();
    }

    public float hudPriority(Vec3 cameraPosition) {
        if (cancelled || cleanupTicks == 0f || sourceTime < exitSourceTime || focusPosition == Vec3.ZERO) return Float.POSITIVE_INFINITY;
        double distance = focusPosition.distanceTo(cameraPosition);
        return distance <= 32.0 ? (float) distance : Float.POSITIVE_INFINITY;
    }

    public float[] screenPosition(Minecraft mc, Camera camera) {
        Vec3 relative = focusPosition.subtract(camera.getPosition());
        org.joml.Vector3fc left = camera.getLeftVector(), up = camera.getUpVector();
        org.joml.Vector3fc forward = camera.getLookVector();
        double depth = relative.x * forward.x() + relative.y * forward.y() + relative.z * forward.z();
        if (depth <= 0.05) return null;
        double right = relative.dot(new Vec3(-left.x(), -left.y(), -left.z()));
        double vertical = relative.dot(new Vec3(up.x(), up.y(), up.z()));
        int width = mc.getWindow().getGuiScaledWidth(), height = mc.getWindow().getGuiScaledHeight();
        double fov = Math.toRadians(mc.options.fov().get());
        // Minecraft's configured FOV is vertical; keep the horizontal scale tied to the
        // GUI-scaled height so wide screens do not push the projected fish off-screen.
        double focal = height * 0.5 / Math.tan(fov * 0.5);
        float x = (float) (width * 0.5 + right * focal / depth);
        float y = (float) (height * 0.5 - vertical * focal / depth);
        return new float[]{x, y};
    }
}
