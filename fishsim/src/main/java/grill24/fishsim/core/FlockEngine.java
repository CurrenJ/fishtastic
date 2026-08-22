package grill24.fishsim.core;

import grill24.fishsim.domain.FlockDomain;

import java.util.Random;

/**
 * The flocking simulation for the fish inside one tank domain — a straight port of the mod's
 * {@code TankFlockSimulation} step logic with every Minecraft type stripped (see
 * docs/fish-sim-engine-plan.md). The Minecraft side talks to this exclusively through
 * {@code TankFlockAdapter}; this class never learns what an ItemStack or animation config is.
 *
 * <p><b>Frame-rate independence.</b> {@link #step()} advances the simulation at a fixed
 * {@code Tunables.dt} (20 Hz) from the client tick; {@link #interpolate(float)} lerps each fish's
 * previous → current position with the render partial tick. The step count is decoupled from the
 * interpolate call count by construction.
 *
 * <p><b>2.5D constraint.</b> Fish move freely in lateral (their swim axis) and vertical, while
 * depth is damped back toward the fish's home layer plane. Heading is binary — the sprite either
 * faces +lateral or −lateral — so a fish is always broadside to the glass and can never present as
 * an edge-on line. Direction reversal is a mirror flip (a 180° yaw, never a negative scale).
 *
 * <p><b>Soft containment.</b> Wall avoidance shapes the desired velocity so a fish decelerates and
 * turns before reaching glass; the hard position clamp is an unreachable backstop, never
 * load-bearing (a hard wall force empirically caused vertical jitter + mirror-flip storms).
 *
 * <p><b>No allocation in the hot path.</b> All per-fish state is parallel primitive arrays sized
 * once per rebuild; the neighbour search reuses scratch arrays and runs brute force.
 *
 * <p><b>Float parity.</b> The math here is bitwise-identical to the pre-extraction code —
 * operation order matters (see the parity tests). Do not reassociate expressions.
 */
public final class FlockEngine {

    private Tunables t;

    // ── Descriptor arrays — fixed between rebuilds (read-only for consumers) ─
    public float[] lengths = new float[0];        // rendered body length (the render scale)
    public float[] baseRotations = new float[0];  // degrees, incl. per-fish jitter
    public long[] seeds = new long[0];            // deterministic per-fish seed (animation RNG)
    public boolean[] swimmers = new boolean[0];   // passed the size gate
    public boolean[] hoverMirrored = new boolean[0]; // spec mirror flag (hover path only)
    float[] wanderPhaseA = new float[0];
    float[] wanderPhaseB = new float[0];
    float[] homeDepth = new float[0];             // the layer plane this fish drifts toward
    public int[] species = new int[0];            // opaque species id (planar model only)

    // ── Sim state (local lateral / vertical / depth) ───────────────────────
    float[] posL = new float[0], posY = new float[0], posD = new float[0];
    float[] prevL = new float[0], prevY = new float[0], prevD = new float[0];
    float[] velL = new float[0], velY = new float[0], velD = new float[0];
    public float[] heading = new float[0]; // +1 / -1 (nose along +lateral / −lateral)
    public float[] speed = new float[0];   // blocks/sec, for animation coupling
    public float[] bank = new float[0];    // bank angle (deg), for animation coupling

    // ── Planar mode (voxel domains) ────────────────────────────────────────
    // Continuous horizontal heading instead of the single-tank binary ±lateral: lateral and depth
    // are fully symmetric swim axes (an L-tank's two arms behave identically), wander curves the
    // path, and patrol + soft wall avoidance produce emergent wall-following loops. The
    // single-tank Box path never uses this — its binary 2.5D model stays bitwise-locked.
    private boolean planar;
    /**
     * Sprite yaw in degrees, same convention as the render rotation (rotating a +lateral-facing
     * object by this makes it face the swim direction). Chases the velocity direction with a
     * turn-rate cap so a momentary velocity flip never snaps the sprite.
     */
    public float[] yawDeg = new float[0];
    float[] prevYawDeg = new float[0];
    /** Interpolated (wrap-aware) yaw for this frame — filled by {@link #interpolate}. */
    public float[] renderYaw = new float[0];

    /** Max sprite turn rate, degrees per tick (planar mode). */
    private static final float PLANAR_TURN_RATE = 7f;
    /** Horizontal speed below which the yaw holds instead of chasing a noisy direction. */
    private static final float PLANAR_YAW_MIN_SPEED = 0.005f;

    // Speed-integrated animation clock, in speed-scaled ticks: advances by speedFactor(i) per
    // step, so tail-beat frequency tracks swim speed CONTINUOUSLY. The animator must consume this
    // (via renderPhase) instead of multiplying its sine frequency by the instantaneous speed —
    // frequency×time with a per-frame-changing frequency teleports the phase (empirical: violent
    // in-place jitter).
    float[] tailPhase = new float[0];
    float[] prevTailPhase = new float[0];

    // ── Render scratch — filled by interpolate(), read by the adapter ──────
    public float[] renderX = new float[0], renderY = new float[0], renderZ = new float[0];
    /** Interpolated animation clock for this frame — pass as the swim animator's time input. */
    public float[] renderPhase = new float[0];
    public int[] order = new int[0]; // depth-sorted index order (ascending world Z)

    // Placement scratch for rejection sampling (rebuild only, no allocation).
    private float[] placedL = new float[0], placedY = new float[0], placedD = new float[0];

    // Neighbour-search scratch (reused every step, no allocation).
    private int[] neighborIdx;
    private float[] neighborDist;

    // Domain-callback scratch (reused every step, no allocation).
    private final float[] avoidScratch = new float[3];
    private final float[] posScratch = new float[3];

    // How often the hard backstop actually moved a fish. Soft containment must keep this at zero —
    // the invariant tests assert it (handoff invariant 9: "soft containment stays soft").
    private long backstopEngagements;

    private FlockDomain domain;
    private final Random rng = new Random(); // seeded at rebuild for the deterministic scatter

    private int count;
    private float cosR = 1f, sinR = 0f; // local → world rotation from the tank's facing
    private long simTick = 0;

    public FlockEngine(Tunables tunables) {
        this.t = tunables;
        this.neighborIdx = new int[tunables.neighborCount()];
        this.neighborDist = new float[tunables.neighborCount()];
        this.domain = new FlockDomain.Box(tunables.tankHalfExtent(), 0.1f, tunables.layerZ());
    }

    public int count() {
        return count;
    }

    public FlockDomain domain() {
        return domain;
    }

    // Read-only views of the live sim state, for tests, metrics, and the harness. The arrays are
    // the engine's own (no copies — zero allocation); callers must not write to them.
    public float[] posL() { return posL; }
    public float[] posY() { return posY; }
    public float[] posD() { return posD; }
    public float[] velL() { return velL; }
    public float[] velY() { return velY; }
    public float[] velD() { return velD; }

    /** Times the hard backstop clamp actually moved a fish (must stay 0 — soft containment does the work). */
    public long backstopEngagements() { return backstopEngagements; }

    /** Whether this engine runs the continuous-yaw planar model (voxel domains) instead of the binary 2.5D one. */
    public boolean planar() { return planar; }

    public Tunables tunables() { return t; }

    /**
     * Swaps the parameter set live, keeping all sim state — the viewer's tuning loop. Does not
     * re-run the size gate or re-scatter; reseed for that.
     */
    public void setTunables(Tunables tunables) {
        this.t = tunables;
        if (neighborIdx.length != tunables.neighborCount()) {
            neighborIdx = new int[tunables.neighborCount()];
            neighborDist = new float[tunables.neighborCount()];
        }
    }

    /**
     * Re-seeds the whole flock from specs: deterministic scatter placement, per-fish seeds and
     * wander phases, the size gate, and zeroed velocities. Simulation time deliberately keeps
     * counting across rebuilds (matches the pre-extraction behavior — the wander field's phase
     * doesn't restart when tank contents change).
     *
     * @param specs           one per fish, in slot order
     * @param baseSeed        the tank's deterministic seed (the sign-extended blockPos hash)
     * @param baseRotationDeg the tank's first-item rotation, degrees
     * @param depthLayers     how many home depth planes to spread fish across
     * @param xzSpread        lateral scatter half-extent (clamped to the tank half-extent)
     * @param yRange          vertical scatter range (also sets the domain's vertical half-extent)
     * @param rotationJitter  per-fish base-rotation jitter half-range, degrees
     */
    public void rebuild(FishSpec[] specs, long baseSeed, float baseRotationDeg,
                        int depthLayers, float xzSpread, float yRange, float rotationJitter) {
        int n = specs.length;
        count = n;
        allocate(n);
        for (int i = 0; i < n; i++) order[i] = i;

        float rotRad = (float) Math.toRadians(baseRotationDeg);
        cosR = (float) Math.cos(rotRad);
        sinR = (float) Math.sin(rotRad);

        float[] layerZ = t.layerZ();
        depthLayers = Math.max(1, Math.min(depthLayers, layerZ.length));
        xzSpread = Math.min(xzSpread, t.tankHalfExtent());

        domain = new FlockDomain.Box(t.tankHalfExtent(), verticalHalf(yRange), layerZ);

        rng.setSeed(baseSeed);
        int placed = 0;

        for (int i = 0; i < n; i++) {
            FishSpec spec = specs[i];

            float lateral, y, depth;
            float baseRotation;
            long seed;
            if (n == 1) {
                // Solo fish: centre, no scatter, no jitter — mirrors the pre-sim solo path.
                lateral = 0f;
                y = 0f;
                depth = 0f;
                baseRotation = baseRotationDeg;
                seed = baseSeed;
            } else {
                depth = layerZ[i % depthLayers];
                float[] ly = sampleXY(xzSpread, yRange, depth, placed);
                lateral = ly[0];
                y = ly[1];
                baseRotation = baseRotationDeg + (rng.nextFloat() - 0.5f) * 2f * rotationJitter;
                seed = baseSeed ^ ((long) (i + 1) * 2654435761L);
            }

            initFish(i, spec, lateral, y, depth, baseRotation, seed);

            if (n > 1) {
                placedL[placed] = lateral;
                placedY[placed] = y;
                placedD[placed] = depth;
                placed++;
            }
        }
    }

    /**
     * The multi-tank rebuild path: fish scatter uniformly across a caller-supplied domain (a
     * {@link grill24.fishsim.domain.VoxelDomain} in practice) and run the continuous-yaw planar
     * model — lateral and depth are fully symmetric. The single-tank {@code rebuild} above keeps
     * its exact legacy scatter and binary model (bitwise parity) — this path has its own golden
     * fixtures instead.
     */
    public void rebuild(FishSpec[] specs, long baseSeed, float baseRotationDeg,
                        float rotationJitter, FlockDomain newDomain) {
        int n = specs.length;
        count = n;
        allocate(n);
        for (int i = 0; i < n; i++) order[i] = i;

        float rotRad = (float) Math.toRadians(baseRotationDeg);
        cosR = (float) Math.cos(rotRad);
        sinR = (float) Math.sin(rotRad);

        this.domain = newDomain;
        this.planar = true;

        rng.setSeed(baseSeed);
        int placed = 0;

        for (int i = 0; i < n; i++) {
            sampleInDomain(placed, posScratch);
            float lateral = posScratch[0];
            float y = posScratch[1];
            float depth = posScratch[2];
            float baseRotation = baseRotationDeg + (rng.nextFloat() - 0.5f) * 2f * rotationJitter;
            long seed = baseSeed ^ ((long) (i + 1) * 2654435761L);

            initFish(i, specs[i], lateral, y, depth, baseRotation, seed);
            yawDeg[i] = prevYawDeg[i] = specs[i].mirrored() ? 180f : 0f;

            placedL[placed] = lateral;
            placedY[placed] = y;
            placedD[placed] = depth;
            placed++;
        }
    }

    private void initFish(int i, FishSpec spec, float lateral, float y, float depth,
                          float baseRotation, long seed) {
        lengths[i] = spec.length();
        baseRotations[i] = baseRotation;
        seeds[i] = seed;
        species[i] = spec.species();
        hoverMirrored[i] = spec.mirrored();
        swimmers[i] = spec.canSwim() && domain.sizeGateRun() >= t.gateFactor() * spec.length();

        posL[i] = prevL[i] = lateral;
        posY[i] = prevY[i] = y;
        posD[i] = prevD[i] = depth;
        homeDepth[i] = depth;
        heading[i] = spec.mirrored() ? -1f : 1f;
        velL[i] = velY[i] = velD[i] = 0f;
        speed[i] = 0f;
        bank[i] = 0f;
        wanderPhaseA[i] = (float) ((seed & 0xFFFF) / 65536.0) * 2f * (float) Math.PI;
        wanderPhaseB[i] = (float) (((seed >>> 16) & 0xFFFF) / 65536.0) * 2f * (float) Math.PI;
    }

    /**
     * Occupancy-aware scatter for domain-supplied rebuilds: uniform over the bounding box,
     * rejection-sampled against {@code contains} + min separation, falling back to contains-only
     * and finally the bounding-box centre (deterministic — every draw comes from the seeded rng).
     */
    private void sampleInDomain(int placed, float[] out) {
        float lo = domain.minLateral(), spanL = domain.maxLateral() - lo;
        float loY = domain.minVertical(), spanY = domain.maxVertical() - loY;
        float loD = domain.minDepth(), spanD = domain.maxDepth() - loD;
        for (int attempt = 0; attempt < 40; attempt++) {
            float x = lo + rng.nextFloat() * spanL;
            float y = loY + rng.nextFloat() * spanY;
            float d = loD + rng.nextFloat() * spanD;
            if (domain.contains(x, y, d) && isFarEnough(x, y, d, placed)) {
                out[0] = x;
                out[1] = y;
                out[2] = d;
                return;
            }
        }
        for (int attempt = 0; attempt < 40; attempt++) {
            float x = lo + rng.nextFloat() * spanL;
            float y = loY + rng.nextFloat() * spanY;
            float d = loD + rng.nextFloat() * spanD;
            if (domain.contains(x, y, d)) {
                out[0] = x;
                out[1] = y;
                out[2] = d;
                return;
            }
        }
        out[0] = lo + spanL * 0.5f;
        out[1] = loY + spanY * 0.5f;
        out[2] = loD + spanD * 0.5f;
    }

    private void allocate(int n) {
        if (lengths.length == n) return;
        lengths = new float[n];
        baseRotations = new float[n];
        seeds = new long[n];
        swimmers = new boolean[n];
        hoverMirrored = new boolean[n];
        wanderPhaseA = new float[n];
        wanderPhaseB = new float[n];
        homeDepth = new float[n];
        species = new int[n];
        posL = new float[n]; posY = new float[n]; posD = new float[n];
        prevL = new float[n]; prevY = new float[n]; prevD = new float[n];
        velL = new float[n]; velY = new float[n]; velD = new float[n];
        heading = new float[n];
        speed = new float[n];
        bank = new float[n];
        renderX = new float[n]; renderY = new float[n]; renderZ = new float[n];
        renderPhase = new float[n];
        tailPhase = new float[n]; prevTailPhase = new float[n];
        yawDeg = new float[n]; prevYawDeg = new float[n]; renderYaw = new float[n];
        order = new int[n];
        placedL = new float[n]; placedY = new float[n]; placedD = new float[n];
    }

    /** Advances the simulation by one fixed 20 Hz step. Runs on the client tick, never at render. */
    public void step() {
        if (count == 0) return;
        simTick++;

        System.arraycopy(posL, 0, prevL, 0, count);
        System.arraycopy(posY, 0, prevY, 0, count);
        System.arraycopy(posD, 0, prevD, 0, count);
        System.arraycopy(tailPhase, 0, prevTailPhase, 0, count);
        if (planar) System.arraycopy(yawDeg, 0, prevYawDeg, 0, count);

        for (int i = 0; i < count; i++) {
            if (!swimmers[i]) continue;
            if (planar) {
                stepFishPlanar(i);
            } else {
                stepFish(i);
            }
            tailPhase[i] += speedFactor(i);
        }
    }

    /**
     * One step of the continuous-yaw planar model (voxel domains): the horizontal plane is fully
     * isotropic — no swim axis, no home depth planes. Desired velocity = patrol along the current
     * travel direction + wander steering perpendicular to it (which curves the path instead of
     * oscillating it), plus the same flocking terms as the binary model applied symmetrically to
     * lateral/depth, plus the distance-field wall avoidance (already isotropic). Near a wall the
     * forward patrol and the outward avoidance sum to a tangential drift — that is what produces
     * the emergent follow-the-wall loops around a domain's perimeter.
     */
    private void stepFishPlanar(int i) {
        // Current travel direction (unit, horizontal); falls back to the sprite yaw when still.
        float dirL, dirD;
        float hsp = (float) Math.sqrt(velL[i] * velL[i] + velD[i] * velD[i]);
        if (hsp > 1e-4f) {
            dirL = velL[i] / hsp;
            dirD = velD[i] / hsp;
        } else {
            float yr = (float) Math.toRadians(yawDeg[i]);
            dirL = (float) Math.cos(yr);
            dirD = -(float) Math.sin(yr);
        }

        float wander = wanderL(i);
        float dL = dirL * t.patrolSpeed() + (-dirD) * wander * t.cruiseSpeed();
        float dD = dirD * t.patrolSpeed() + dirL * wander * t.cruiseSpeed();
        float dY = wanderY(i) * t.cruiseSpeed();

        // Species-aware separation: shoal-mates use the tight radius (they may swarm), strangers
        // the wide one — this is what keeps a mixed tank from congealing into one ball while
        // letting each species keep its own cluster.
        float sepL = 0f, sepY = 0f, sepD = 0f;
        for (int j = 0; j < count; j++) {
            if (j == i) continue;
            float radius = species[i] == species[j] ? t.separationRadius() : t.separationRadiusOther();
            float radius2 = species[i] == species[j] ? t.separationRadius2() : t.separationRadiusOther2();
            float dx = posL[i] - posL[j], dy = posY[i] - posY[j], dz = posD[i] - posD[j];
            float d2 = dx * dx + dy * dy + dz * dz;
            if (d2 < radius2 && d2 > 1e-6f) {
                float d = (float) Math.sqrt(d2);
                float w = (radius - d) / radius;
                sepL += (dx / d) * w;
                sepY += (dy / d) * w;
                sepD += (dz / d) * w;
            }
        }

        // Saturate the total separation thrust below the wall-avoidance authority: in a domain
        // too small to honor the cross-species radius (many strangers, tiny box), the summed
        // shoves must never out-shove containment — crowded fish tolerate closeness instead of
        // pushing each other through the glass. (Empirical: uncapped 0.6-radius shoves in a
        // 1×1×1 with 12 fish engaged the hard backstop.)
        float sepCap = t.wallAvoidSpeed() * 0.45f;
        float sepMagnitude = (float) Math.sqrt(sepL * sepL + sepY * sepY + sepD * sepD) * t.separationSpeed();
        float sepScale = sepMagnitude > sepCap ? sepCap / sepMagnitude : 1f;

        int neigh = findNearestSwimmers(i);
        float aliL = 0f, aliY = 0f, aliD = 0f, cohL = 0f, cohY = 0f, cohD = 0f;
        for (int b = 0; b < neighborIdx.length; b++) {
            int j = neighborIdx[b];
            if (j < 0) break;
            aliL += velL[j]; aliY += velY[j]; aliD += velD[j];
            cohL += posL[j] - posL[i]; cohY += posY[j] - posY[i]; cohD += posD[j] - posD[i];
        }
        if (neigh > 0) {
            aliL /= neigh; aliY /= neigh; aliD /= neigh;
            cohL /= neigh; cohY /= neigh; cohD /= neigh;
        }

        dL += sepL * t.separationSpeed() * sepScale + aliL * t.alignmentWeight() + cohL * t.cohesionSpeed();
        dD += sepD * t.separationSpeed() * sepScale + aliD * t.alignmentWeight() + cohD * t.cohesionSpeed();
        dY += sepY * t.separationSpeed() * sepScale + aliY * t.alignmentWeight() + cohY * t.cohesionSpeed();

        domain.avoidance(posL[i], posY[i], posD[i], t.wallMargin(), t.wallMarginVertical(), avoidScratch);
        dL += avoidScratch[0] * t.wallAvoidSpeed();
        dY += avoidScratch[1] * t.wallAvoidSpeed();
        dD += avoidScratch[2] * t.wallAvoidSpeed();

        float aL = (dL - velL[i]) * t.steeringGain();
        float aY = (dY - velY[i]) * t.steeringGain();
        float aD = (dD - velD[i]) * t.steeringGain();
        float f = (float) Math.sqrt(aL * aL + aY * aY + aD * aD);
        if (f > t.maxForce()) {
            float k = t.maxForce() / f;
            aL *= k; aY *= k; aD *= k;
        }

        velL[i] += aL * t.dt();
        velY[i] += aY * t.dt();
        velY[i] *= (1f - t.verticalDamp() * t.dt());
        velD[i] += aD * t.dt();

        float sp = (float) Math.sqrt(velL[i] * velL[i] + velY[i] * velY[i] + velD[i] * velD[i]);
        if (sp > t.maxSpeed()) {
            float k = t.maxSpeed() / sp;
            velL[i] *= k; velY[i] *= k; velD[i] *= k;
            sp = t.maxSpeed();
        }
        speed[i] = sp;

        posL[i] += velL[i] * t.dt();
        posY[i] += velY[i] * t.dt();
        posD[i] += velD[i] * t.dt();
        posScratch[0] = posL[i];
        posScratch[1] = posY[i];
        posScratch[2] = posD[i];
        domain.constrain(prevL[i], prevY[i], prevD[i], posScratch);
        if (Float.floatToRawIntBits(posScratch[0]) != Float.floatToRawIntBits(posL[i])
                || Float.floatToRawIntBits(posScratch[1]) != Float.floatToRawIntBits(posY[i])
                || Float.floatToRawIntBits(posScratch[2]) != Float.floatToRawIntBits(posD[i])) {
            backstopEngagements++;
        }
        posL[i] = posScratch[0];
        posY[i] = posScratch[1];
        posD[i] = posScratch[2];

        // Sprite yaw chases the travel direction with a turn-rate cap; bank leans into the turn.
        float hsp2 = (float) Math.sqrt(velL[i] * velL[i] + velD[i] * velD[i]);
        if (hsp2 > PLANAR_YAW_MIN_SPEED) {
            float target = (float) Math.toDegrees(Math.atan2(-velD[i], velL[i]));
            float diff = wrapDeg(target - yawDeg[i]);
            float turn = SimMath.clamp(diff, -PLANAR_TURN_RATE, PLANAR_TURN_RATE);
            yawDeg[i] = wrapDeg(yawDeg[i] + turn);
            bank[i] = SimMath.clamp(turn * 1.5f, -t.bankMax(), t.bankMax());
        } else {
            bank[i] *= 0.9f;
        }
        // Keep the binary heading roughly meaningful for consumers that read it (metrics ignore
        // flips in planar mode).
        heading[i] = dirL >= 0f ? 1f : -1f;
    }

    /** Wraps an angle to (−180, 180]. */
    private static float wrapDeg(float deg) {
        float d = deg % 360f;
        if (d > 180f) d -= 360f;
        if (d <= -180f) d += 360f;
        return d;
    }

    private void stepFish(int i) {
        // Soft steering: separation over all close fish (swimmers + hovering obstacles), plus
        // alignment and cohesion over the k nearest swimmers.
        float dL = wanderL(i) * t.cruiseSpeed();
        float dY = wanderY(i) * t.cruiseSpeed();
        // Patrol: cruise along the current heading so fish traverse multi-block domains (wall
        // avoidance + heading hysteresis produce the end-of-run turn). Guarded, not added-as-zero:
        // the single-tank parity set relies on this branch never executing.
        if (t.patrolSpeed() != 0f) {
            dL += heading[i] * t.patrolSpeed();
        }

        float sepL = 0f, sepY = 0f, sepD = 0f;
        for (int j = 0; j < count; j++) {
            if (j == i) continue;
            float dx = posL[i] - posL[j], dy = posY[i] - posY[j], dz = posD[i] - posD[j];
            float d2 = dx * dx + dy * dy + dz * dz;
            if (d2 < t.separationRadius2() && d2 > 1e-6f) {
                float d = (float) Math.sqrt(d2);
                float w = (t.separationRadius() - d) / t.separationRadius();
                sepL += (dx / d) * w;
                sepY += (dy / d) * w;
                sepD += (dz / d) * w;
            }
        }

        int neigh = findNearestSwimmers(i);
        float aliL = 0f, aliY = 0f, aliD = 0f, cohL = 0f, cohY = 0f, cohD = 0f;
        for (int b = 0; b < neighborIdx.length; b++) {
            int j = neighborIdx[b];
            if (j < 0) break;
            aliL += velL[j]; aliY += velY[j]; aliD += velD[j];
            cohL += posL[j] - posL[i]; cohY += posY[j] - posY[i]; cohD += posD[j] - posD[i];
        }
        if (neigh > 0) {
            aliL /= neigh; aliY /= neigh; aliD /= neigh;
            cohL /= neigh; cohY /= neigh; cohD /= neigh;
        }

        dL += sepL * t.separationSpeed() + aliL * t.alignmentWeight() + cohL * t.cohesionSpeed();
        dY += sepY * t.separationSpeed() + aliY * t.alignmentWeight() + cohY * t.cohesionSpeed();

        // Soft wall avoidance turns the fish away from a wall well before it reaches it — this is
        // what makes a reversal a graceful turn rather than a bounce. The domain computes the
        // proximity direction (per-axis margins for a Box, a distance-field lookup for a
        // VoxelDomain); depth is additionally biased toward the fish's home layer plane (2.5D).
        domain.avoidance(posL[i], posY[i], posD[i], t.wallMargin(), t.wallMarginVertical(), avoidScratch);
        float dD = (homeDepth[i] - posD[i]) * t.depthRestore()
                + avoidScratch[2] * t.wallAvoidSpeed();
        dL += avoidScratch[0] * t.wallAvoidSpeed();
        dY += avoidScratch[1] * t.wallAvoidSpeed();

        // Steering acceleration toward the desired velocity, soft force clamped.
        float aL = (dL - velL[i]) * t.steeringGain();
        float aY = (dY - velY[i]) * t.steeringGain();
        float aD = (dD - velD[i]) * t.steeringGain();
        float f = (float) Math.sqrt(aL * aL + aY * aY + aD * aD);
        if (f > t.maxForce()) {
            float k = t.maxForce() / f;
            aL *= k; aY *= k; aD *= k;
        }

        // Integrate velocity; damp depth and vertical so those axes stay calm (only lateral is
        // free-running — the fish's swim axis).
        velL[i] += aL * t.dt();
        velY[i] += aY * t.dt();
        velY[i] *= (1f - t.verticalDamp() * t.dt());
        velD[i] += aD * t.dt();
        velD[i] *= (1f - t.depthDamp() * t.dt());

        // Speed ceiling (no floor — the wander keeps fish from stopping, and a floor would block
        // the velocity zero-crossing that makes a reversal read as a turn rather than a pop).
        float sp = (float) Math.sqrt(velL[i] * velL[i] + velY[i] * velY[i] + velD[i] * velD[i]);
        if (sp > t.maxSpeed()) {
            float k = t.maxSpeed() / sp;
            velL[i] *= k; velY[i] *= k; velD[i] *= k;
            sp = t.maxSpeed();
        }
        speed[i] = sp;

        // Integrate position; the domain's hard backstop clamps only if the soft avoidance failed.
        posL[i] += velL[i] * t.dt();
        posY[i] += velY[i] * t.dt();
        posD[i] += velD[i] * t.dt();
        posScratch[0] = posL[i];
        posScratch[1] = posY[i];
        posScratch[2] = posD[i];
        domain.constrain(prevL[i], prevY[i], prevD[i], posScratch);
        if (Float.floatToRawIntBits(posScratch[0]) != Float.floatToRawIntBits(posL[i])
                || Float.floatToRawIntBits(posScratch[1]) != Float.floatToRawIntBits(posY[i])
                || Float.floatToRawIntBits(posScratch[2]) != Float.floatToRawIntBits(posD[i])) {
            backstopEngagements++;
        }
        posL[i] = posScratch[0];
        posY[i] = posScratch[1];
        posD[i] = posScratch[2];

        // Heading from lateral velocity, with hysteresis.
        if (velL[i] > t.headingDeadzone()) heading[i] = 1f;
        else if (velL[i] < -t.headingDeadzone()) heading[i] = -1f;

        bank[i] = SimMath.clamp(-aL * t.bankGain(), -t.bankMax(), t.bankMax());
    }

    private int findNearestSwimmers(int i) {
        int neighborCount = neighborIdx.length;
        for (int b = 0; b < neighborCount; b++) {
            neighborIdx[b] = -1;
            neighborDist[b] = Float.MAX_VALUE;
        }
        float range2 = t.neighborRange2(); // MAX_VALUE (single-tank set) admits everyone — parity-safe
        for (int j = 0; j < count; j++) {
            if (j == i || !swimmers[j]) continue;
            // Planar model: only shoal-mates align/cohere — each species schools with its own.
            // Never filters in the binary single-tank model (bitwise parity).
            if (planar && species[j] != species[i]) continue;
            float dx = posL[j] - posL[i], dy = posY[j] - posY[i], dz = posD[j] - posD[i];
            float d2 = dx * dx + dy * dy + dz * dz;
            if (d2 >= range2 || d2 >= neighborDist[neighborCount - 1]) continue;
            int b = neighborCount - 1;
            while (b > 0 && d2 < neighborDist[b - 1]) {
                neighborIdx[b] = neighborIdx[b - 1];
                neighborDist[b] = neighborDist[b - 1];
                b--;
            }
            neighborIdx[b] = j;
            neighborDist[b] = d2;
        }
        int found = 0;
        for (int b = 0; b < neighborCount; b++) {
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

    /** Tail-beat frequency factor from forward speed; the hover path always uses 1.0. */
    public float speedFactor(int i) {
        float normalized = SimMath.clamp(speed[i] / t.maxSpeed(), 0f, 1f);
        return 0.6f + 0.9f * normalized;
    }

    /**
     * Writes interpolated world-space offsets for this frame's partial tick into the render
     * scratch arrays and re-sorts the draw order by depth. Called from the render extract every
     * frame — read-only with respect to simulation time.
     */
    public void interpolate(float partialTick) {
        if (count == 0) return;
        for (int i = 0; i < count; i++) {
            float l = SimMath.lerp(partialTick, prevL[i], posL[i]);
            float y = SimMath.lerp(partialTick, prevY[i], posY[i]);
            float d = SimMath.lerp(partialTick, prevD[i], posD[i]);
            renderX[i] = l * cosR + d * sinR;
            renderZ[i] = -l * sinR + d * cosR;
            renderY[i] = y;
            renderPhase[i] = SimMath.lerp(partialTick, prevTailPhase[i], tailPhase[i]);
            if (planar) {
                // Wrap-aware angular lerp so a fish crossing the ±180° seam doesn't spin the long way.
                renderYaw[i] = prevYawDeg[i] + partialTick * wrapDeg(yawDeg[i] - prevYawDeg[i]);
            }
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
        float minSep2 = t.swarmMinSep() * t.swarmMinSep();
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
