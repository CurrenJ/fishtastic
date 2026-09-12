package grill24.fishtastic.client.renderer;

import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Locomotion;
import grill24.fishtastic.FishtasticParticleTypes;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.data.FishAnimationConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Turns what the flock simulation is doing into bubbles (docs/fish-tank-bubbles.md). Runs once per
 * client tick per rendered tank, straight after that tank's engines step, so emission is
 * event-driven at the sim's own 20 Hz and independent of frame rate.
 *
 * <p>Strictly an observer: it reads the engine's public arrays and probes, keeps its own edge
 * detection state (in {@link State}, owned by the adapter), and draws randomness from the client
 * level's RNG — never the engine's noise stream. The golden/parity locks on the engine see nothing.
 *
 * <p>Two sources of bubbles per fish:
 * <ul>
 *   <li><b>Passive</b> — a per-tick probability that grows with the square of normalised swim
 *   speed and with body size: breath at the nose, and at real pace a wake off the tail.</li>
 *   <li><b>Exertion events</b> — the rising edge of the engine's burst envelope (a swimmer's
 *   thrust, a jelly's contraction, a crawler's scuttle) or an anchored creature's retract, each
 *   releasing a small cluster in one go.</li>
 * </ul>
 * Swarm scaling is emergent — N fish roll N times — bounded by a per-flock spawn budget that
 * thins passive emission smoothly rather than starving whichever fish sit late in the array.
 */
public final class TankBubbleEmitter {

    // ── Passive emission ─────────────────────────────────────────────────────
    /** Per-tick chance at rest: about one breath every six seconds. */
    private static final float P_REST = 1f / 120f;
    /** Per-tick chance flat out: about one every 1.5 s, before the size weight. */
    private static final float P_CRUISE = 1f / 30f;
    /**
     * Body length (blocks) that counts as size weight 1.0. Both how often a fish emits and how
     * many bubbles each emission releases scale linearly with {@code length / reference}, clamped
     * only to keep a mis-calibrated species from going silent or flooding the budget.
     */
    private static final float SIZE_REFERENCE_LENGTH = 0.3f;
    private static final float SIZE_WEIGHT_MIN = 0.25f;
    private static final float SIZE_WEIGHT_MAX = 3.0f;
    /** Above this normalised speed a passive roll may also shed a wake bubble at the tail. */
    private static final float WAKE_VIGOR = 0.6f;
    /** A glider is huge and slow: fewer, larger passive bubbles. */
    private static final float GLIDE_RATE_SCALE = 0.5f;

    // ── Particle type mix ────────────────────────────────────────────────────
    // Every spawn rolls its type from a weight table (tiny : small : medium : tank_bubble), so the
    // smallest bubble is always the most common and the full vanilla-size one the rarest. Exertion
    // shifts the mix toward the larger sizes; a glider's breath uses the exertion mix because the
    // animal is huge.
    /** Passive breath/wake: overwhelmingly tiny, a rare small, a full bubble about 1 in 100. */
    private static final int[] PASSIVE_WEIGHTS = {85, 10, 4, 1};
    /** Exertion events: still tiny-led, but the larger sizes are a real part of the cluster. */
    private static final int[] EXERTION_WEIGHTS = {55, 28, 12, 5};
    private static final Holder<SimpleParticleType>[] TYPES_BY_SIZE = typesBySize();

    @SuppressWarnings("unchecked")
    private static Holder<SimpleParticleType>[] typesBySize() {
        return new Holder[] {
                FishtasticParticleTypes.TINY_BUBBLE, FishtasticParticleTypes.SMALL_BUBBLE,
                FishtasticParticleTypes.MEDIUM_BUBBLE, FishtasticParticleTypes.TANK_BUBBLE };
    }

    // ── Budget & LOD ─────────────────────────────────────────────────────────
    private static final int BUDGET_MIN = 2;
    private static final int BUDGET_MAX = 20;
    private static final int BUDGET_FISH_PER_EXTRA = 16;
    /** No emission at all beyond this camera distance; rates fade linearly inside it. */
    private static final float LOD_RADIUS = 32f;

    // ── Geometry ─────────────────────────────────────────────────────────────
    /** Nose / tail offsets along the forward vector, as a fraction of body length. */
    private static final float NOSE_FRACTION = 0.4f;
    private static final float TAIL_FRACTION = 0.45f;
    /** Fraction of the fish's velocity (blocks/s → blocks/tick) a shed bubble inherits. */
    private static final float DRIFT_INHERIT = 0.3f / 20f;
    /** Positional jitter on every spawn so a cluster isn't a single stacked point. */
    private static final double SPAWN_JITTER = 0.02;

    /**
     * For classes whose envelope runs 0→1 on its own clock (drift pulse, crawler scuttle) the
     * onset is the envelope rising through this level; the swimmers' phase clock wraps instead.
     * Chosen by locomotion class, not by any engine flag — every fish carries a burst step, but
     * only the swimmers' phase is ever advanced.
     */
    private static final float ENVELOPE_ONSET = 0.5f;

    // ── Burrow poof ──────────────────────────────────────────────────────────
    // An anchored creature (garden / royal eel) snapping back into its burrow displaces water:
    // a single burst of many bubbles pushed gently outward from the hole, rather than a
    // trickle. Count is before the size weight.
    private static final int POOF_MIN = 8;
    private static final int POOF_RANGE = 6;
    /** Radial start offset (blocks) so the puff already has some width on its first frame. */
    private static final float POOF_RADIUS = 0.04f;
    /** Outward drift per tick (blocks); bleeds off in the particle's own tick like any drift. */
    private static final float POOF_SPEED_MIN = 0.006f;
    private static final float POOF_SPEED_RANGE = 0.010f;

    // A drifter's bell contraction — the pulse that gives it its little upward squeeze — is the
    // same kind of water displacement as the burrow poof, just gentler: fewer bubbles, pushed out
    // more softly from the top of the bell.
    private static final int PULSE_POOF_MIN = 4;
    private static final int PULSE_POOF_RANGE = 4;
    private static final float PULSE_POOF_SPEED_SCALE = 0.6f;

    private TankBubbleEmitter() {}

    /** Edge-detection memory for one engine, sized to its fish count on demand. */
    public static final class State {
        private float[] prevBurstPhase = new float[0];
        private float[] prevBurstDrive = new float[0];
        private float[] prevAnchorTimer = new float[0];

        private void fit(FlockEngine eng) {
            int n = eng.count();
            if (prevBurstPhase.length == n) return;
            // A rebuild reindexes fish; a spurious edge on the next tick is harmless, so just
            // resize and resample.
            prevBurstPhase = new float[n];
            prevBurstDrive = new float[n];
            prevAnchorTimer = new float[n];
            for (int i = 0; i < n; i++) {
                prevBurstPhase[i] = eng.burstPhase(i);
                prevBurstDrive[i] = eng.burstDrive(i);
                prevAnchorTimer[i] = eng.anchorTimer(i);
            }
        }
    }

    /**
     * Emits this tick's bubbles for a tank that was rendered last frame. Lone tanks emit from
     * their own engine; a group anchor additionally emits for the group engine, in the same frames
     * the two draw loops use.
     *
     * @param eye the camera position, for LOD; null skips emission entirely
     */
    public static void emit(ClientLevel level, BlockPos pos, TankFlockAdapter flock, Vec3 eye) {
        if (eye == null) return;
        double dist = Math.sqrt(eye.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        if (dist >= LOD_RADIUS) return;
        float lod = 1f - (float) (dist / LOD_RADIUS);

        if (!(level.getBlockEntity(pos) instanceof FishTankBlockEntity be)) return;
        boolean hasOpenDownFace = be.getOpenFaces().contains(Direction.DOWN);

        FlockEngine lone = flock.engine();
        if (lone.count() > 0) {
            emitFor(level, pos, lone, flock.anims, flock.loneEmitter, lod,
                    0.5f, Float.NaN, 0.5f, hasOpenDownFace);
        }
        FlockEngine group = flock.groupEngine();
        if (group != null && group.count() > 0) {
            emitFor(level, pos, group, flock.groupAnims, flock.groupEmitter, lod,
                    flock.groupOffsetX, flock.groupOffsetY, flock.groupOffsetZ, hasOpenDownFace);
        }
    }

    /**
     * @param originY the model-space Y origin, or NaN for the lone-tank path where the origin is
     *                per-fish ({@code FishTankBlockEntityRenderer.computeBaseY})
     */
    private static void emitFor(ClientLevel level, BlockPos pos, FlockEngine eng, FishAnimationConfig[] anims,
            State state, float lod, float originX, float originY, float originZ, boolean hasOpenDownFace) {
        state.fit(eng);
        RandomSource random = level.getRandom();
        int n = eng.count();
        int budget = Mth.clamp(BUDGET_MIN + n / BUDGET_FISH_PER_EXTRA, BUDGET_MIN, BUDGET_MAX);
        int spawned = 0;

        float[] posL = eng.posL(), posY = eng.posY(), posD = eng.posD();
        float[] velL = eng.velL(), velD = eng.velD();
        float[] scratch = new float[3];

        // Pass 1 — events first, so a burst you can see always bubbles even when the budget is tight.
        for (int i = 0; i < n; i++) {
            // A swimmer's burst clock wraps at thrust onset; a drifter's or crawler's phase never
            // moves, so its 0→1 envelope is watched directly instead.
            boolean burstOnset = switch (eng.locomotion[i]) {
                case FREE_SWIM, GLIDE -> eng.burstPhase(i) < state.prevBurstPhase[i];
                case DRIFT, BENTHIC -> eng.burstDrive(i) >= ENVELOPE_ONSET && state.prevBurstDrive[i] < ENVELOPE_ONSET;
                default -> false;
            };
            boolean retractOnset = eng.anchorTimer(i) > 0f && state.prevAnchorTimer[i] <= 0f;
            state.prevBurstPhase[i] = eng.burstPhase(i);
            state.prevBurstDrive[i] = eng.burstDrive(i);
            state.prevAnchorTimer[i] = eng.anchorTimer(i);
            if (!burstOnset && !retractOnset) continue;
            if (random.nextFloat() > lod) continue;

            Locomotion loco = eng.locomotion[i];
            float length = eng.lengths[i];
            float size = sizeWeight(eng, i);
            FishPose fish = FishPose.of(eng, i, posL, posY, posD, velL, velD, scratch,
                    originX, originY, originZ, anims[i], hasOpenDownFace);
            switch (loco) {
                case FREE_SWIM -> {
                    if (!burstOnset) break;
                    spawned += spawn(level, pos, EXERTION_WEIGHTS, fish,
                            -TAIL_FRACTION * length, 0f, sized(1 + random.nextInt(3), size, random), random);
                }
                case DRIFT -> {
                    if (!burstOnset) break;
                    // From the bell top, straight up from the body centre.
                    spawned += poof(level, pos, EXERTION_WEIGHTS, fish, length * 0.3f,
                            sized(PULSE_POOF_MIN + random.nextInt(PULSE_POOF_RANGE), size, random),
                            PULSE_POOF_SPEED_SCALE, random);
                }
                case BENTHIC -> {
                    if (!burstOnset) break;
                    // A subtle sand kick — the passive mix keeps it almost all tiny.
                    spawned += spawn(level, pos, PASSIVE_WEIGHTS, fish,
                            0f, 0.02f, sized(1 + random.nextInt(2), size, random), random);
                }
                case ANCHORED -> {
                    if (!retractOnset) break;
                    spawned += poof(level, pos, EXERTION_WEIGHTS, fish, 0.02f,
                            sized(POOF_MIN + random.nextInt(POOF_RANGE), size, random), 1f, random);
                }
                default -> { }
            }
        }

        // Pass 2 — passive breath/wake, thinned to whatever budget the events left.
        int remaining = budget - spawned;
        if (remaining <= 0) return;
        float expected = 0f;
        for (int i = 0; i < n; i++) expected += passiveChance(eng, i);
        float thin = expected > remaining ? remaining / expected : 1f;

        for (int i = 0; i < n && spawned < budget; i++) {
            float p = passiveChance(eng, i) * thin * lod;
            if (p <= 0f || random.nextFloat() >= p) continue;

            Locomotion loco = eng.locomotion[i];
            float length = eng.lengths[i];
            FishPose fish = FishPose.of(eng, i, posL, posY, posD, velL, velD, scratch,
                    originX, originY, originZ, anims[i], hasOpenDownFace);
            float size = sizeWeight(eng, i);
            int[] breath = loco == Locomotion.GLIDE ? EXERTION_WEIGHTS : PASSIVE_WEIGHTS;
            spawned += spawn(level, pos, breath, fish, NOSE_FRACTION * length, 0f, sized(1, size, random), random);
            if (loco == Locomotion.FREE_SWIM && vigor(eng, i) > WAKE_VIGOR && random.nextBoolean()) {
                spawned += spawn(level, pos, PASSIVE_WEIGHTS, fish,
                        -TAIL_FRACTION * length, 0f, sized(1, size, random), random);
            }
        }
    }

    /** Normalised swim speed in [0,1], recovered from the engine's own speed→animation mapping. */
    private static float vigor(FlockEngine eng, int i) {
        return Mth.clamp((eng.speedFactor(i) - 0.6f) / 0.9f, 0f, 1f);
    }

    /** Body size relative to the reference length — the linear factor on both rate and cluster size. */
    private static float sizeWeight(FlockEngine eng, int i) {
        return Mth.clamp(eng.lengths[i] / SIZE_REFERENCE_LENGTH, SIZE_WEIGHT_MIN, SIZE_WEIGHT_MAX);
    }

    /**
     * Scales a cluster count by body size, rounding stochastically so a 1.5x fish releases two
     * bubbles half the time rather than always one or always two. Never below one — an emission
     * that fires always shows something.
     */
    private static int sized(int count, float size, RandomSource random) {
        float scaled = count * size;
        int whole = (int) scaled;
        if (random.nextFloat() < scaled - whole) whole++;
        return Math.max(1, whole);
    }

    private static float passiveChance(FlockEngine eng, int i) {
        Locomotion loco = eng.locomotion[i];
        if (loco != Locomotion.FREE_SWIM && loco != Locomotion.GLIDE) return 0f;
        float v = vigor(eng, i);
        float p = P_REST + (P_CRUISE - P_REST) * v * v;
        p *= sizeWeight(eng, i);
        if (loco == Locomotion.GLIDE) p *= GLIDE_RATE_SCALE;
        return p;
    }

    /**
     * One fish's model-space position (relative to the tank block), forward vector and inherited
     * drift, resolved once per emitting fish.
     */
    private record FishPose(double x, double y, double z, double fwdX, double fwdZ, double driftX, double driftZ) {
        static FishPose of(FlockEngine eng, int i, float[] posL, float[] posY, float[] posD, float[] velL, float[] velD,
                float[] scratch, float originX, float originY, float originZ,
                FishAnimationConfig anim, boolean hasOpenDownFace) {
            eng.toRender(posL[i], posY[i], posD[i], scratch);
            double x = originX + scratch[0];
            double z = originZ + scratch[2];
            double y;
            if (Float.isNaN(originY)) {
                // Lone tank: mirrors the renderer's translate — per-pose baseline plus the engine's Y.
                y = FishTankBlockEntityRenderer.computeBaseY(anim, hasOpenDownFace, eng.lengths[i]) + scratch[1];
            } else {
                y = originY + scratch[1];
            }

            // Forward from velocity when moving, else the binary heading along lateral — so a wake
            // trails the direction actually travelled, and a hovering fish still has a nose.
            float vl = velL[i], vd = velD[i];
            float sp = (float) Math.sqrt(vl * vl + vd * vd);
            if (sp > 1e-4f) {
                eng.toRender(vl / sp, 0f, vd / sp, scratch);
            } else {
                eng.toRender(eng.heading[i], 0f, 0f, scratch);
            }
            double fwdX = scratch[0], fwdZ = scratch[2];

            eng.toRender(vl, 0f, vd, scratch);
            return new FishPose(x, y, z, fwdX, fwdZ, scratch[0] * DRIFT_INHERIT, scratch[2] * DRIFT_INHERIT);
        }
    }

    /** Rolls a bubble size from a (tiny, small, tank_bubble) weight table. */
    private static Holder<SimpleParticleType> rollType(int[] weights, RandomSource random) {
        int total = 0;
        for (int w : weights) total += w;
        int r = random.nextInt(total);
        for (int k = 0; k < weights.length; k++) {
            r -= weights[k];
            if (r < 0) return TYPES_BY_SIZE[k];
        }
        return TYPES_BY_SIZE[0];
    }

    /**
     * Spawns {@code count} bubbles at {@code along} blocks along the fish's forward vector and
     * {@code up} blocks above its centre, each jittered slightly and each rolling its own size
     * from {@code weights}.
     */
    private static int spawn(ClientLevel level, BlockPos pos, int[] weights, FishPose fish,
            float along, float up, int count, RandomSource random) {
        double x = pos.getX() + fish.x() + fish.fwdX() * along;
        double y = pos.getY() + fish.y() + up;
        double z = pos.getZ() + fish.z() + fish.fwdZ() * along;
        double popY = popCeilingY(level, pos, x, y, z);
        for (int k = 0; k < count; k++) {
            double jx = (random.nextFloat() - 0.5) * SPAWN_JITTER;
            double jy = (random.nextFloat() - 0.5) * SPAWN_JITTER;
            double jz = (random.nextFloat() - 0.5) * SPAWN_JITTER;
            level.addParticle(rollType(weights, random).value(), x + jx, y + jy, z + jz, fish.driftX(), popY, fish.driftZ());
        }
        return count;
    }

    /**
     * Spawns {@code count} bubbles in a ring around the point {@code up} blocks above the fish's
     * centre, each drifting outward along its own random bearing — the burrow puff. Unlike
     * {@link #spawn} the drift is per-bubble and radial, not the fish's velocity (an anchored
     * creature has none worth inheriting). {@code speedScale} softens the outward push for a
     * gentler displacement, like a jelly's pulse.
     */
    private static int poof(ClientLevel level, BlockPos pos, int[] weights, FishPose fish,
            float up, int count, float speedScale, RandomSource random) {
        double cx = pos.getX() + fish.x();
        double cy = pos.getY() + fish.y() + up;
        double cz = pos.getZ() + fish.z();
        double popY = popCeilingY(level, pos, cx, cy, cz);
        for (int k = 0; k < count; k++) {
            double angle = random.nextFloat() * Math.PI * 2.0;
            double dirX = Math.cos(angle), dirZ = Math.sin(angle);
            double r = random.nextFloat() * POOF_RADIUS;
            double speed = (POOF_SPEED_MIN + random.nextFloat() * POOF_SPEED_RANGE) * speedScale;
            double jy = (random.nextFloat() - 0.5) * SPAWN_JITTER;
            level.addParticle(rollType(weights, random).value(),
                    cx + dirX * r, cy + jy, cz + dirZ * r, dirX * speed, popY, dirZ * speed);
        }
        return count;
    }

    /**
     * The glass-ceiling Y this bubble pops at: the top of the connected stack above the tank cell
     * the fish is actually in (a group's columns can differ in height), falling back to this
     * tank's own column when the spawn point lands outside any tank.
     */
    private static double popCeilingY(ClientLevel level, BlockPos tankPos, double x, double y, double z) {
        BlockPos cell = BlockPos.containing(x, y, z);
        BlockPos column = level.getBlockEntity(cell) instanceof FishTankBlockEntity ? cell : tankPos;
        BlockPos top = FishTankBlockEntityRenderer.topOfConnectedTankStack(level, column);
        return top.getY() + FishTankBlockEntityRenderer.TANK_CEILING_Y;
    }
}
