package grill24.fishtastic.client.renderer;

import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.data.FishAnimationConfig;
import grill24.fishtastic.data.SwarmConfig;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Random;

/**
 * Client-only, non-authoritative flocking simulation for the fish inside a single fish tank.
 *
 * <p><b>Where the state lives.</b> A per-tank {@code TankFlockSimulation} is held in
 * {@code ClientTankFlocks}, a client-side registry keyed by {@code BlockPos}. We chose a registry
 * over a field on {@code FishTankBlockEntity} for two reasons: the block entity is common code
 * shared with the server (and this work must not touch the server — invariant 6), and a registry
 * makes "only tick tanks that are actually being rendered" explicit rather than implicit. The cost
 * is that we own eviction, which {@code ClientTankFlocks} handles with a last-extract timestamp.
 *
 * <p><b>Frame-rate independence.</b> {@link #step()} advances the simulation at a fixed 20 Hz from
 * the client tick; {@link #interpolate(float)} lerps each fish's previous → current position with
 * the render {@code partialTick}. The sim never runs inside {@code extractRenderState}, which can
 * be invoked more than once per game tick.
 *
 * <p><b>2.5D constraint.</b> Fish move freely in lateral (their swim axis) and vertical, while
 * depth is damped back toward the fish's home {@code LAYER_Z} plane. Heading is binary — the sprite
 * either faces +lateral or −lateral — so a fish is always broadside to the glass and can never
 * present as an edge-on line. Direction reversal is a mirror flip (a 180° yaw, never a negative
 * scale), sold by the {@code mirrored} flag.
 *
 * <p><b>No allocation in the hot path.</b> All per-fish state is parallel primitive arrays sized
 * once per content change; the neighbour search reuses scratch arrays and runs brute force
 * (25 fish ≈ 600 distance tests, well under 10 µs — no spatial hash).
 */
public final class TankFlockSimulation {

    // Fixed 20 Hz integration timestep.
    private static final float DT = 1f / 20f;

    // Size gate: a fish free-swims only when the tank's shortest run is at least this many body
    // lengths. Kept a named constant (playtest, not yet configurable).
    private static final float GATE_FACTOR = 2.5f;

    // Speeds and steering limits (block units / second, and seconds²). Kept low: a 1-block tank is
    // only ~0.7 blocks wide, so anything faster reads as darting and slams into the walls.
    private static final float MAX_SPEED = 0.12f;
    private static final float CRUISE_SPEED = 0.08f;   // wander baseline
    private static final float STEERING_GAIN = 2.0f;   // response to desired velocity
    private static final float MAX_FORCE = 0.5f;       // soft-steering acceleration cap

    // Flocking (topological neighbours, brute force).
    private static final int NEIGHBOR_COUNT = 6;
    private static final float SEPARATION_RADIUS = 0.18f;
    private static final float SEPARATION_RADIUS2 = SEPARATION_RADIUS * SEPARATION_RADIUS;
    private static final float SEPARATION_SPEED = 0.22f; // push strength
    private static final float ALIGNMENT_WEIGHT = 0.3f;  // velocity matching
    private static final float COHESION_SPEED = 0.10f;   // pull toward neighbours

    // 2.5D: depth and vertical are both damped so the fish stay in a calm slab; depth is also
    // biased back toward the fish's home layer plane.
    private static final float DEPTH_RESTORE = 1.2f; // blocks/s² per block of depth error
    private static final float DEPTH_DAMP = 2.5f;    // per-second depth-velocity damping
    private static final float VERTICAL_DAMP = 1.2f; // per-second vertical-velocity damping

    // Wall avoidance (soft, review §4.1): the desired velocity turns away from a wall as the fish
    // approaches, so it decelerates and turns instead of bouncing. Lateral/depth use a larger
    // margin than vertical because the vertical box is tiny.
    private static final float WALL_MARGIN = 0.14f;          // lateral/depth soft zone
    private static final float WALL_MARGIN_VERTICAL = 0.05f; // vertical soft zone
    private static final float WALL_AVOID_SPEED = 0.24f;     // desired away-speed at full proximity

    // Animation coupling (Task 8). Bank is kept subtle — a strong wall force would rock the fish.
    private static final float BANK_GAIN = 14f; // degrees per block/s² of lateral acceleration
    private static final float BANK_MAX = 10f;

    // Lateral speed (blocks/s) below which a mirror flip does not re-trigger — hysteresis so a
    // nearly-vertical fish doesn't flicker.
    private static final float HEADING_DEADZONE = 0.02f;

    // Single-tank swim volume (formerly in FishTankBlockEntityRenderer).
    private static final float TANK_HALF_EXTENT = 0.35f;
    private static final float SWARM_MIN_SEP = 0.14f;
    private static final float[] LAYER_Z = {-0.25f, 0f, 0.25f};

    // ── Descriptor arrays — fixed between syncs ─────────────────────────────
    ItemStack[] stacks = new ItemStack[0];
    FishAnimationConfig[] anims = new FishAnimationConfig[0];
    float[] calibrations = new float[0];
    float[] baseRotations = new float[0];
    long[] seeds = new long[0];
    float[] scales = new float[0];
    boolean[] swimmers = new boolean[0];      // passed the size gate
    boolean[] hoverMirrored = new boolean[0]; // random slot mirror flag (hover path only)
    float[] wanderPhaseA = new float[0];
    float[] wanderPhaseB = new float[0];
    float[] homeDepth = new float[0];         // the LAYER_Z plane this fish drifts toward

    // ── Sim state (local lateral / vertical / depth) ───────────────────────
    float[] posL = new float[0], posY = new float[0], posD = new float[0];
    float[] prevL = new float[0], prevY = new float[0], prevD = new float[0];
    float[] velL = new float[0], velY = new float[0], velD = new float[0];
    float[] heading = new float[0]; // +1 / -1 (nose along +lateral / −lateral)
    float[] speed = new float[0];   // blocks/sec, for animation coupling
    float[] bank = new float[0];    // bank angle (deg), for animation coupling

    // ── Render scratch — filled by interpolate(), read by the renderer ─────
    float[] renderX = new float[0], renderY = new float[0], renderZ = new float[0];
    int[] order = new int[0]; // depth-sorted index order (ascending world Z)
    // One render state per fish (persistent, reused across frames). A single shared state would be
    // wrong: ItemStackRenderState.submit defers to the end of the frame, so every fish would render
    // as the last one submitted.
    ItemStackRenderState[] itemRenderStates = new ItemStackRenderState[0];

    // Placement scratch for rejection sampling (rebuild only, no allocation).
    private float[] placedL = new float[0], placedY = new float[0], placedD = new float[0];

    // Neighbour-search scratch (reused every step, no allocation).
    private final int[] neighborIdx = new int[NEIGHBOR_COUNT];
    private final float[] neighborDist = new float[NEIGHBOR_COUNT];

    private FlockDomain domain = new FlockDomain.Box(TANK_HALF_EXTENT, 0.1f, LAYER_Z);
    private final Random rng = new Random(); // seeded at rebuild for the deterministic scatter

    private int count;
    private float cosR = 1f, sinR = 0f; // local → world rotation from the tank's facing
    private long lastExtractTick = Long.MIN_VALUE;
    private long simTick = 0;

    public int count() {
        return count;
    }

    public long lastExtractTick() {
        return lastExtractTick;
    }

    /** Marks this flock as having been extracted this client tick (drives eviction). */
    public void touch(long tick) {
        this.lastExtractTick = tick;
    }

    /**
     * Rebuilds the fish descriptors if the tank's contents changed since the last sync, then leaves
     * the sim state untouched otherwise. Called every extract (per frame) — the content check is an
     * allocation-free scan of the tank's slots; the rebuild itself only runs on a real change.
     */
    public void sync(FishTankBlockEntity be, int blockPosHash, Level level) {
        int idx = 0;
        boolean changed = false;
        for (int slot = 0; slot < FishTankBlockEntity.CONTAINER_SIZE && !changed; slot++) {
            ItemStack s = be.getItem(slot);
            if (!s.isEmpty()) {
                changed = idx >= count || !ItemStack.isSameItemSameComponents(s, stacks[idx]);
                idx++;
            }
        }
        if (!changed && idx == count) return;
        rebuild(be, blockPosHash, level);
    }

    private void rebuild(FishTankBlockEntity be, int blockPosHash, Level level) {
        SwarmConfig swarm = SwarmConfig.resolve(be.getFirstItem(), level);
        float firstItemRotation = be.getFirstItemRotation();
        int maxCount = swarm.count();

        int n = 0;
        for (int slot = 0; slot < FishTankBlockEntity.CONTAINER_SIZE && n < maxCount; slot++) {
            if (!be.getItem(slot).isEmpty()) n++;
        }

        count = n;
        allocate(n);
        for (int i = 0; i < n; i++) order[i] = i;

        ItemStack[] items = new ItemStack[n];
        int[] slots = new int[n];
        int idx = 0;
        for (int slot = 0; slot < FishTankBlockEntity.CONTAINER_SIZE && idx < n; slot++) {
            ItemStack s = be.getItem(slot);
            if (!s.isEmpty()) {
                items[idx] = s.copy();
                slots[idx] = slot;
                idx++;
            }
        }

        float rotRad = (float) Math.toRadians(firstItemRotation);
        cosR = (float) Math.cos(rotRad);
        sinR = (float) Math.sin(rotRad);

        int depthLayers = Math.max(1, Math.min(swarm.depthLayers(), LAYER_Z.length));
        float xzSpread = Math.min(swarm.xzSpread(), TANK_HALF_EXTENT);
        float yRange = swarm.yRange();
        float rotationJitter = swarm.rotationJitter();

        domain = new FlockDomain.Box(TANK_HALF_EXTENT, verticalHalf(yRange), LAYER_Z);

        rng.setSeed(blockPosHash);
        int placed = 0;

        for (int i = 0; i < n; i++) {
            ItemStack stack = items[i];
            FishTankBlockEntityRenderer.ResolvedFishRender render =
                    FishTankBlockEntityRenderer.resolveFishRender(stack, level);
            FishAnimationConfig anim = render.animation();
            float calibration = render.renderCalibration();

            float lateral, y, depth;
            float baseRotation;
            long seed;
            if (n == 1) {
                // Solo fish: centre, no scatter, no jitter — mirrors the pre-sim solo path.
                lateral = 0f;
                y = 0f;
                depth = 0f;
                baseRotation = firstItemRotation;
                seed = blockPosHash;
            } else {
                depth = LAYER_Z[i % depthLayers];
                float[] ly = sampleXY(xzSpread, yRange, depth, placed);
                lateral = ly[0];
                y = ly[1];
                baseRotation = firstItemRotation + (rng.nextFloat() - 0.5f) * 2f * rotationJitter;
                seed = (long) blockPosHash ^ ((long) (i + 1) * 2654435761L);
            }

            boolean mirrored = be.isItemMirrored(slots[i]);
            float scale = renderedLength(stack, calibration);

            stacks[i] = stack;
            anims[i] = anim;
            calibrations[i] = calibration;
            baseRotations[i] = baseRotation;
            seeds[i] = seed;
            scales[i] = scale;
            hoverMirrored[i] = mirrored;
            swimmers[i] = !FishTankBlockEntityRenderer.isFloorAnchored(anim)
                    && anim instanceof FishAnimationConfig.HorizontalSwim
                    && domain.shortestRun() >= GATE_FACTOR * scale;

            posL[i] = prevL[i] = lateral;
            posY[i] = prevY[i] = y;
            posD[i] = prevD[i] = depth;
            homeDepth[i] = depth;
            heading[i] = mirrored ? -1f : 1f;
            velL[i] = velY[i] = velD[i] = 0f;
            speed[i] = 0f;
            bank[i] = 0f;
            wanderPhaseA[i] = (float) ((seed & 0xFFFF) / 65536.0) * 2f * (float) Math.PI;
            wanderPhaseB[i] = (float) (((seed >>> 16) & 0xFFFF) / 65536.0) * 2f * (float) Math.PI;

            if (n > 1) {
                placedL[placed] = lateral;
                placedY[placed] = y;
                placedD[placed] = depth;
                placed++;
            }
        }
    }

    private void allocate(int n) {
        if (stacks.length == n) return;
        stacks = new ItemStack[n];
        anims = new FishAnimationConfig[n];
        calibrations = new float[n];
        baseRotations = new float[n];
        seeds = new long[n];
        scales = new float[n];
        swimmers = new boolean[n];
        hoverMirrored = new boolean[n];
        wanderPhaseA = new float[n];
        wanderPhaseB = new float[n];
        homeDepth = new float[n];
        posL = new float[n]; posY = new float[n]; posD = new float[n];
        prevL = new float[n]; prevY = new float[n]; prevD = new float[n];
        velL = new float[n]; velY = new float[n]; velD = new float[n];
        heading = new float[n];
        speed = new float[n];
        bank = new float[n];
        renderX = new float[n]; renderY = new float[n]; renderZ = new float[n];
        order = new int[n];
        itemRenderStates = new ItemStackRenderState[n];
        for (int i = 0; i < n; i++) itemRenderStates[i] = new ItemStackRenderState();
        placedL = new float[n]; placedY = new float[n]; placedD = new float[n];
    }

    /** Advances the simulation by one fixed 20 Hz step. Runs on the client tick, never at render. */
    public void step() {
        if (count == 0) return;
        simTick++;

        System.arraycopy(posL, 0, prevL, 0, count);
        System.arraycopy(posY, 0, prevY, 0, count);
        System.arraycopy(posD, 0, prevD, 0, count);

        for (int i = 0; i < count; i++) {
            if (!swimmers[i]) continue;
            stepFish(i);
        }
    }

    private void stepFish(int i) {
        // Soft steering: separation over all close fish (swimmers + hovering obstacles), plus
        // alignment and cohesion over the k nearest swimmers.
        float dL = wanderL(i) * CRUISE_SPEED;
        float dY = wanderY(i) * CRUISE_SPEED;

        float sepL = 0f, sepY = 0f, sepD = 0f;
        for (int j = 0; j < count; j++) {
            if (j == i) continue;
            float dx = posL[i] - posL[j], dy = posY[i] - posY[j], dz = posD[i] - posD[j];
            float d2 = dx * dx + dy * dy + dz * dz;
            if (d2 < SEPARATION_RADIUS2 && d2 > 1e-6f) {
                float d = (float) Math.sqrt(d2);
                float w = (SEPARATION_RADIUS - d) / SEPARATION_RADIUS;
                sepL += (dx / d) * w;
                sepY += (dy / d) * w;
                sepD += (dz / d) * w;
            }
        }

        int neigh = findNearestSwimmers(i);
        float aliL = 0f, aliY = 0f, aliD = 0f, cohL = 0f, cohY = 0f, cohD = 0f;
        for (int b = 0; b < NEIGHBOR_COUNT; b++) {
            int j = neighborIdx[b];
            if (j < 0) break;
            aliL += velL[j]; aliY += velY[j]; aliD += velD[j];
            cohL += posL[j] - posL[i]; cohY += posY[j] - posY[i]; cohD += posD[j] - posD[i];
        }
        if (neigh > 0) {
            aliL /= neigh; aliY /= neigh; aliD /= neigh;
            cohL /= neigh; cohY /= neigh; cohD /= neigh;
        }

        dL += sepL * SEPARATION_SPEED + aliL * ALIGNMENT_WEIGHT + cohL * COHESION_SPEED;
        dY += sepY * SEPARATION_SPEED + aliY * ALIGNMENT_WEIGHT + cohY * COHESION_SPEED;

        // Depth is biased toward the fish's home layer plane (2.5D), with the same soft wall
        // avoidance as lateral/vertical so the fish never hugs the glass.
        float dD = (homeDepth[i] - posD[i]) * DEPTH_RESTORE
                + wallAvoidance(posD[i], domain.minDepth(), domain.maxDepth(), WALL_MARGIN) * WALL_AVOID_SPEED;
        // Soft wall avoidance turns the fish away from a wall well before it reaches it — this is
        // what makes a reversal a graceful turn rather than a bounce.
        dL += wallAvoidance(posL[i], domain.minLateral(), domain.maxLateral(), WALL_MARGIN) * WALL_AVOID_SPEED;
        dY += wallAvoidance(posY[i], domain.minVertical(), domain.maxVertical(), WALL_MARGIN_VERTICAL) * WALL_AVOID_SPEED;

        // Steering acceleration toward the desired velocity, soft force clamped.
        float aL = (dL - velL[i]) * STEERING_GAIN;
        float aY = (dY - velY[i]) * STEERING_GAIN;
        float aD = (dD - velD[i]) * STEERING_GAIN;
        float f = (float) Math.sqrt(aL * aL + aY * aY + aD * aD);
        if (f > MAX_FORCE) {
            float k = MAX_FORCE / f;
            aL *= k; aY *= k; aD *= k;
        }

        // Integrate velocity; damp depth and vertical so those axes stay calm (only lateral is
        // free-running — the fish's swim axis).
        velL[i] += aL * DT;
        velY[i] += aY * DT;
        velY[i] *= (1f - VERTICAL_DAMP * DT);
        velD[i] += aD * DT;
        velD[i] *= (1f - DEPTH_DAMP * DT);

        // Speed ceiling (no floor — the wander keeps fish from stopping, and a floor would block
        // the velocity zero-crossing that makes a reversal read as a turn rather than a pop).
        float sp = (float) Math.sqrt(velL[i] * velL[i] + velY[i] * velY[i] + velD[i] * velD[i]);
        if (sp > MAX_SPEED) {
            float k = MAX_SPEED / sp;
            velL[i] *= k; velY[i] *= k; velD[i] *= k;
            sp = MAX_SPEED;
        }
        speed[i] = sp;

        // Integrate position and hard-clamp to the box.
        posL[i] += velL[i] * DT;
        posY[i] += velY[i] * DT;
        posD[i] += velD[i] * DT;
        posL[i] = Mth.clamp(posL[i], domain.minLateral(), domain.maxLateral());
        posY[i] = Mth.clamp(posY[i], domain.minVertical(), domain.maxVertical());
        posD[i] = Mth.clamp(posD[i], domain.minDepth(), domain.maxDepth());

        // Heading from lateral velocity, with hysteresis.
        if (velL[i] > HEADING_DEADZONE) heading[i] = 1f;
        else if (velL[i] < -HEADING_DEADZONE) heading[i] = -1f;

        bank[i] = Mth.clamp(-aL * BANK_GAIN, -BANK_MAX, BANK_MAX);
    }

    private int findNearestSwimmers(int i) {
        for (int b = 0; b < NEIGHBOR_COUNT; b++) {
            neighborIdx[b] = -1;
            neighborDist[b] = Float.MAX_VALUE;
        }
        for (int j = 0; j < count; j++) {
            if (j == i || !swimmers[j]) continue;
            float dx = posL[j] - posL[i], dy = posY[j] - posY[i], dz = posD[j] - posD[i];
            float d2 = dx * dx + dy * dy + dz * dz;
            if (d2 >= neighborDist[NEIGHBOR_COUNT - 1]) continue;
            int b = NEIGHBOR_COUNT - 1;
            while (b > 0 && d2 < neighborDist[b - 1]) {
                neighborIdx[b] = neighborIdx[b - 1];
                neighborDist[b] = neighborDist[b - 1];
                b--;
            }
            neighborIdx[b] = j;
            neighborDist[b] = d2;
        }
        int found = 0;
        for (int b = 0; b < NEIGHBOR_COUNT; b++) {
            if (neighborIdx[b] >= 0) found++;
        }
        return found;
    }

    private float wanderL(int i) {
        float t = simTick;
        return (float) (Math.sin(wanderPhaseA[i] + t * 0.031) * 0.7
                + Math.sin(wanderPhaseB[i] + t * 0.017) * 0.5);
    }

    private float wanderY(int i) {
        float t = simTick;
        return (float) (Math.sin(wanderPhaseA[i] * 1.7 + t * 0.023) * 0.6);
    }

    /** Signed proximity to the nearer wall in [−1, 1]: positive near the low wall, negative near the high wall, 0 in the open. */
    private static float wallAvoidance(float pos, float min, float max, float margin) {
        float lo = min + margin;
        float hi = max - margin;
        if (pos < lo) return (lo - pos) / margin;
        if (pos > hi) return -(pos - hi) / margin;
        return 0f;
    }

    /** Tail-beat frequency factor from forward speed; the hover path always uses 1.0. */
    float speedFactor(int i) {
        float normalized = Mth.clamp(speed[i] / MAX_SPEED, 0f, 1f);
        return 0.6f + 0.9f * normalized;
    }

    /**
     * Writes interpolated world-space offsets for this frame's {@code partialTick} into the render
     * scratch arrays and re-sorts the draw order by depth. Called from {@code extractRenderState}
     * every frame — read-only with respect to simulation time.
     */
    void interpolate(float partialTick) {
        if (count == 0) return;
        for (int i = 0; i < count; i++) {
            float l = Mth.lerp(partialTick, prevL[i], posL[i]);
            float y = Mth.lerp(partialTick, prevY[i], posY[i]);
            float d = Mth.lerp(partialTick, prevD[i], posD[i]);
            renderX[i] = l * cosR + d * sinR;
            renderZ[i] = -l * sinR + d * cosR;
            renderY[i] = y;
        }
        // Insertion sort by world Z (ascending = back-to-front). Positions change little between
        // frames, so the order is near-sorted and this is ~O(n) — no comparator allocation.
        for (int a = 1; a < count; a++) {
            int key = order[a];
            float keyZ = renderZ[key];
            int b = a - 1;
            while (b >= 0 && renderZ[order[b]] > keyZ) {
                order[b + 1] = order[b];
                b--;
            }
            order[b + 1] = key;
        }
    }

    private float[] sampleXY(float xzSpread, float yRange, float depth, int placed) {
        int maxAttempts = 25;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            float x = (rng.nextFloat() - 0.5f) * 2f * xzSpread;
            float y = (yRange > 0f) ? (rng.nextFloat() - 0.5f) * yRange : 0f;
            if (isFarEnough(x, y, depth, placed)) return new float[]{x, y};
        }
        float x = (rng.nextFloat() - 0.5f) * 2f * xzSpread;
        float y = (yRange > 0f) ? (rng.nextFloat() - 0.5f) * yRange : 0f;
        return new float[]{x, y};
    }

    private boolean isFarEnough(float x, float y, float z, int placed) {
        float minSep2 = SWARM_MIN_SEP * SWARM_MIN_SEP;
        for (int j = 0; j < placed; j++) {
            float dx = x - placedL[j], dy = y - placedY[j], dz = z - placedD[j];
            if (dx * dx + dy * dy + dz * dz < minSep2) return false;
        }
        return true;
    }

    private static float renderedLength(ItemStack stack, float calibration) {
        // Matches the render scale exactly: unrolled fish render at a flat 0.5 (never length 0,
        // which would let an unmeasured species free-swim when it shouldn't).
        if (ItemSizeHelper.hasSize(stack)) {
            return (ItemSizeHelper.getSize(stack) / 100f) * calibration;
        }
        return 0.5f;
    }

    private static float verticalHalf(float yRange) {
        return Math.max(0.1f, yRange * 0.5f);
    }
}
