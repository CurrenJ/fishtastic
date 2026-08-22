package grill24.fishsim;

import grill24.fishsim.domain.FlockDomain;

import java.util.Random;

/**
 * A frozen copy of the pre-extraction {@code TankFlockSimulation} float math (rebuild scatter,
 * step, wander, wall avoidance, interpolate), with the Minecraft-only concerns (ItemStacks,
 * animation configs, render states) replaced by plain parameters. The {@code Mth} calls are
 * inlined with vanilla's exact semantics.
 *
 * <p>This class exists solely as the bitwise-parity oracle for {@code FlockEngine}
 * (docs/fish-sim-engine-handoff.md Task 3). <b>Never edit the math here</b> — it is the record of
 * what the game shipped. Delete the class only after the adapter rewire (Task 4) has shipped; the
 * golden fixtures stay forever.
 */
final class LegacyStepReference {

    // ── Constants copied verbatim from TankFlockSimulation ─────────────────
    private static final float DT = 1f / 20f;
    private static final float GATE_FACTOR = 2.5f;
    private static final float MAX_SPEED = 0.12f;
    private static final float CRUISE_SPEED = 0.08f;
    private static final float STEERING_GAIN = 2.0f;
    private static final float MAX_FORCE = 0.5f;
    private static final int NEIGHBOR_COUNT = 6;
    private static final float SEPARATION_RADIUS = 0.18f;
    private static final float SEPARATION_RADIUS2 = SEPARATION_RADIUS * SEPARATION_RADIUS;
    private static final float SEPARATION_SPEED = 0.22f;
    private static final float ALIGNMENT_WEIGHT = 0.3f;
    private static final float COHESION_SPEED = 0.10f;
    private static final float DEPTH_RESTORE = 1.2f;
    private static final float DEPTH_DAMP = 2.5f;
    private static final float VERTICAL_DAMP = 1.2f;
    private static final float WALL_MARGIN = 0.14f;
    private static final float WALL_MARGIN_VERTICAL = 0.05f;
    private static final float WALL_AVOID_SPEED = 0.24f;
    private static final float BANK_GAIN = 14f;
    private static final float BANK_MAX = 10f;
    private static final float HEADING_DEADZONE = 0.02f;
    private static final float TANK_HALF_EXTENT = 0.35f;
    private static final float SWARM_MIN_SEP = 0.14f;
    private static final float[] LAYER_Z = {-0.25f, 0f, 0.25f};

    // ── State (verbatim structure) ─────────────────────────────────────────
    float[] scales = new float[0];
    float[] baseRotations = new float[0];
    long[] seeds = new long[0];
    boolean[] swimmers = new boolean[0];
    boolean[] hoverMirrored = new boolean[0];
    float[] wanderPhaseA = new float[0];
    float[] wanderPhaseB = new float[0];
    float[] homeDepth = new float[0];

    float[] posL = new float[0], posY = new float[0], posD = new float[0];
    float[] prevL = new float[0], prevY = new float[0], prevD = new float[0];
    float[] velL = new float[0], velY = new float[0], velD = new float[0];
    float[] heading = new float[0];
    float[] speed = new float[0];
    float[] bank = new float[0];

    float[] renderX = new float[0], renderY = new float[0], renderZ = new float[0];
    int[] order = new int[0];

    private float[] placedL = new float[0], placedY = new float[0], placedD = new float[0];

    private final int[] neighborIdx = new int[NEIGHBOR_COUNT];
    private final float[] neighborDist = new float[NEIGHBOR_COUNT];

    private FlockDomain domain = new FlockDomain.Box(TANK_HALF_EXTENT, 0.1f, LAYER_Z);
    private final Random rng = new Random();

    int count;
    private float cosR = 1f, sinR = 0f;
    private long simTick = 0;

    // ── Inlined Mth ports (vanilla semantics, verified against 26.1.2 sources) ─
    private static float mthClamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    private static float mthLerp(float alpha, float p0, float p1) {
        return p0 + alpha * (p1 - p0);
    }

    /**
     * The legacy rebuild, with the MC-side inputs pre-resolved: {@code lengths[i]} is the
     * rendered length ({@code renderedLength} already applied), {@code canSwim[i]} is
     * "not floor-anchored and HorizontalSwim", {@code mirrored[i]} is the slot mirror flag.
     */
    void rebuild(float[] lengths, boolean[] canSwim, boolean[] mirrored,
                 long blockPosHash, float firstItemRotation,
                 int depthLayersIn, float xzSpreadIn, float yRange, float rotationJitter) {
        int n = lengths.length;
        count = n;
        allocate(n);
        for (int i = 0; i < n; i++) order[i] = i;

        float rotRad = (float) Math.toRadians(firstItemRotation);
        cosR = (float) Math.cos(rotRad);
        sinR = (float) Math.sin(rotRad);

        int depthLayers = Math.max(1, Math.min(depthLayersIn, LAYER_Z.length));
        float xzSpread = Math.min(xzSpreadIn, TANK_HALF_EXTENT);

        domain = new FlockDomain.Box(TANK_HALF_EXTENT, verticalHalf(yRange), LAYER_Z);

        rng.setSeed(blockPosHash);
        int placed = 0;

        for (int i = 0; i < n; i++) {
            float lateral, y, depth;
            float baseRotation;
            long seed;
            if (n == 1) {
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
                seed = blockPosHash ^ ((long) (i + 1) * 2654435761L);
            }

            float scale = lengths[i];

            scales[i] = scale;
            baseRotations[i] = baseRotation;
            seeds[i] = seed;
            hoverMirrored[i] = mirrored[i];
            swimmers[i] = canSwim[i] && domain.sizeGateRun() >= GATE_FACTOR * scale;

            posL[i] = prevL[i] = lateral;
            posY[i] = prevY[i] = y;
            posD[i] = prevD[i] = depth;
            homeDepth[i] = depth;
            heading[i] = mirrored[i] ? -1f : 1f;
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
        if (scales.length == n) return;
        scales = new float[n];
        baseRotations = new float[n];
        seeds = new long[n];
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
        placedL = new float[n]; placedY = new float[n]; placedD = new float[n];
    }

    void step() {
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

        float dD = (homeDepth[i] - posD[i]) * DEPTH_RESTORE
                + wallAvoidance(posD[i], domain.minDepth(), domain.maxDepth(), WALL_MARGIN) * WALL_AVOID_SPEED;
        dL += wallAvoidance(posL[i], domain.minLateral(), domain.maxLateral(), WALL_MARGIN) * WALL_AVOID_SPEED;
        dY += wallAvoidance(posY[i], domain.minVertical(), domain.maxVertical(), WALL_MARGIN_VERTICAL) * WALL_AVOID_SPEED;

        float aL = (dL - velL[i]) * STEERING_GAIN;
        float aY = (dY - velY[i]) * STEERING_GAIN;
        float aD = (dD - velD[i]) * STEERING_GAIN;
        float f = (float) Math.sqrt(aL * aL + aY * aY + aD * aD);
        if (f > MAX_FORCE) {
            float k = MAX_FORCE / f;
            aL *= k; aY *= k; aD *= k;
        }

        velL[i] += aL * DT;
        velY[i] += aY * DT;
        velY[i] *= (1f - VERTICAL_DAMP * DT);
        velD[i] += aD * DT;
        velD[i] *= (1f - DEPTH_DAMP * DT);

        float sp = (float) Math.sqrt(velL[i] * velL[i] + velY[i] * velY[i] + velD[i] * velD[i]);
        if (sp > MAX_SPEED) {
            float k = MAX_SPEED / sp;
            velL[i] *= k; velY[i] *= k; velD[i] *= k;
            sp = MAX_SPEED;
        }
        speed[i] = sp;

        posL[i] += velL[i] * DT;
        posY[i] += velY[i] * DT;
        posD[i] += velD[i] * DT;
        posL[i] = mthClamp(posL[i], domain.minLateral(), domain.maxLateral());
        posY[i] = mthClamp(posY[i], domain.minVertical(), domain.maxVertical());
        posD[i] = mthClamp(posD[i], domain.minDepth(), domain.maxDepth());

        if (velL[i] > HEADING_DEADZONE) heading[i] = 1f;
        else if (velL[i] < -HEADING_DEADZONE) heading[i] = -1f;

        bank[i] = mthClamp(-aL * BANK_GAIN, -BANK_MAX, BANK_MAX);
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

    private static float wallAvoidance(float pos, float min, float max, float margin) {
        float lo = min + margin;
        float hi = max - margin;
        if (pos < lo) return (lo - pos) / margin;
        if (pos > hi) return -(pos - hi) / margin;
        return 0f;
    }

    float speedFactor(int i) {
        float normalized = mthClamp(speed[i] / MAX_SPEED, 0f, 1f);
        return 0.6f + 0.9f * normalized;
    }

    void interpolate(float partialTick) {
        if (count == 0) return;
        for (int i = 0; i < count; i++) {
            float l = mthLerp(partialTick, prevL[i], posL[i]);
            float y = mthLerp(partialTick, prevY[i], posY[i]);
            float d = mthLerp(partialTick, prevD[i], posD[i]);
            renderX[i] = l * cosR + d * sinR;
            renderZ[i] = -l * sinR + d * cosR;
            renderY[i] = y;
        }
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

    private static float verticalHalf(float yRange) {
        return Math.max(0.1f, yRange * 0.5f);
    }
}
