package grill24.fishsim.core;

/**
 * Every simulation constant, as one immutable parameter set passed to {@link FlockEngine} —
 * a record instead of static finals so tests and the sweep harness can vary parameter sets;
 * the mod passes exactly one canonical instance, {@link #DEFAULT}, whose values are the
 * constants from the original {@code TankFlockSimulation} unchanged.
 *
 * <p>Units: block units and seconds (speeds in blocks/s, forces in blocks/s²), angles in degrees.
 */
public record Tunables(
        // Fixed integration timestep (20 Hz).
        float dt,
        // Size gate: a fish free-swims only when the domain's longest run is at least this many
        // body lengths.
        float gateFactor,
        // Speeds and steering limits. Kept low: a 1-block tank is only ~0.7 blocks wide, so
        // anything faster reads as darting and slams into the walls.
        float maxSpeed,
        float cruiseSpeed,      // wander baseline
        float steeringGain,     // response to desired velocity
        float maxForce,         // soft-steering acceleration cap
        // Flocking (topological neighbours, brute force).
        int neighborCount,
        float separationRadius,
        float separationSpeed,  // push strength
        float alignmentWeight,  // velocity matching
        float cohesionSpeed,    // pull toward neighbours
        // 2.5D: depth and vertical are both damped so the fish stay in a calm slab; depth is also
        // biased back toward the fish's home layer plane.
        float depthRestore,     // blocks/s² per block of depth error
        float depthDamp,        // per-second depth-velocity damping
        float verticalDamp,     // per-second vertical-velocity damping
        // Wall avoidance (soft): the desired velocity turns away from a wall as the fish
        // approaches, so it decelerates and turns instead of bouncing. Lateral/depth use a larger
        // margin than vertical because the vertical box is tiny.
        float wallMargin,          // lateral/depth soft zone
        float wallMarginVertical,  // vertical soft zone
        float wallAvoidSpeed,      // desired away-speed at full proximity
        // Animation coupling. Bank is kept subtle — a strong wall force would rock the fish.
        float bankGain,         // degrees per block/s² of lateral acceleration
        float bankMax,
        // Lateral speed (blocks/s) below which a mirror flip does not re-trigger — hysteresis so
        // a nearly-vertical fish doesn't flicker.
        float headingDeadzone,
        // Single-tank swim volume.
        float tankHalfExtent,
        float swarmMinSep,
        float[] layerZ,
        // Metric perception radius for the topological neighbour search (blocks): fish farther
        // than this are never flock-mates, so cohesion stays local instead of pulling the whole
        // domain into one clump. MAX_VALUE (the single-tank default) disables it — any finite
        // value larger than the tank diagonal is behaviour-identical there, but MAX_VALUE keeps
        // the parity guarantee unconditional.
        float neighborRange,
        // Desired cruise speed along the fish's current heading (blocks/s). Zero (the single-tank
        // default — engine skips the term entirely, keeping bitwise parity) leaves only the
        // zero-mean wander, which oscillates fish around a fixed point; in multi-block domains a
        // patrol impulse is what makes fish actually traverse the length, with wall avoidance +
        // heading hysteresis producing the turn at each end.
        float patrolSpeed,
        // Cross-species separation radius (blocks), planar model only: fish of DIFFERENT species
        // repel from this far out (empirically ~0.6 keeps a mixed tank from congealing into one
        // ball), while same-species fish use the tighter separationRadius and so still swarm.
        // The binary single-tank model never reads it.
        float separationRadiusOther,

        // ── Tier 1 realism terms (docs/fish-swarm-realism.md §2) ───────────────────────────────
        // All PLANAR-ONLY, and all neutral-valued in DEFAULT where the engine skips the term
        // entirely — exactly the guard pattern patrolSpeed already uses, so the binary single-tank
        // model stays bitwise-locked.

        // Ornstein–Uhlenbeck wander: band-limited per-fish noise on the TURN RATE, replacing the
        // two global sine frequencies whose shared clock made every fish in a tank ride the same
        // rhythm. sigma is the drive strength (dimensionless wander units per √second); theta is
        // the mean-reversion rate (1/s) and so sets the correlation time (~1/theta). Zero sigma
        // keeps the legacy sine wander.
        float wanderTurnSigma,
        float wanderTurnTheta,
        // Alignment on UNIT heading rather than raw velocity (blocks/s). Averaging velocities
        // conflates "swim the way my neighbours swim" with "swim as fast as my neighbours do";
        // those are different behaviours wanting different gains, and fusing them forced
        // alignmentWeight down to a level where the school could not polarize at all. Zero keeps
        // the legacy velocity-averaged alignment.
        float alignHeadingWeight,
        // The speed half of that split, as a fraction of the neighbour mean-speed error matched.
        // Deliberately weak — schools match direction far more tightly than they match speed.
        float speedMatchWeight,
        // Per-fish trait spread: ± this fraction on top speed, patrol speed and turn rate, drawn
        // deterministically from the fish's own seed. Zero makes every fish identical (the legacy
        // behaviour) and is the last thing holding a shoal in lockstep.
        float traitJitter,
        // Burst-and-coast: real fish beat hard for a moment then glide rather than holding a
        // constant cruise. The cycle scales patrolSpeed, so it also drives speedFactor and hence
        // the tail-beat animation for free. Zero period disables it.
        float burstPeriodSeconds,
        float burstDuty,          // fraction of the cycle spent thrusting
        float burstThrustScale,   // patrol multiplier at the peak of a burst
        float burstCoastScale) {  // patrol multiplier during the glide

    /** The canonical parameter set the mod ships — every default identical to the pre-extraction constants. */
    public static final Tunables DEFAULT = new Tunables(
            1f / 20f,   // dt
            2.5f,       // gateFactor
            0.12f,      // maxSpeed
            0.08f,      // cruiseSpeed
            2.0f,       // steeringGain
            0.5f,       // maxForce
            6,          // neighborCount
            0.18f,      // separationRadius
            0.22f,      // separationSpeed
            0.3f,       // alignmentWeight
            0.10f,      // cohesionSpeed
            1.2f,       // depthRestore
            2.5f,       // depthDamp
            1.2f,       // verticalDamp
            0.14f,      // wallMargin
            0.05f,      // wallMarginVertical
            0.24f,      // wallAvoidSpeed
            14f,        // bankGain
            10f,        // bankMax
            0.02f,      // headingDeadzone
            0.35f,      // tankHalfExtent
            0.14f,      // swarmMinSep
            new float[]{-0.25f, 0f, 0.25f},
            Float.MAX_VALUE, // neighborRange — unlimited: single-tank parity set
            0f,              // patrolSpeed — off: single-tank parity set
            0.18f,           // separationRadiusOther — unread by the binary model
            // Tier 1 terms all neutral — the binary model must stay bitwise-identical.
            0f,              // wanderTurnSigma — legacy sine wander
            0f,              // wanderTurnTheta
            0f,              // alignHeadingWeight — legacy velocity-averaged alignment
            0f,              // speedMatchWeight
            0f,              // traitJitter — every fish identical
            0f,              // burstPeriodSeconds — constant cruise
            0f,              // burstDuty
            1f,              // burstThrustScale
            1f);             // burstCoastScale

    /**
     * The canonical parameter set for multi-tank (voxel-domain) groups. Tuned via the headless
     * harness (2026-08-22, 4x1x2 trajectory plots + metrics): DEFAULT's global cohesion collapsed
     * large domains into one vertical slice — a finite perception radius keeps flocking local,
     * weaker cohesion/alignment let the per-fish wander actually explore, and a slightly higher
     * cruise/max speed reads better across multi-block runs. Single tanks never use this set.
     */
    public static final Tunables GROUP = new Tunables(
            DEFAULT.dt(),
            DEFAULT.gateFactor(),
            0.20f,      // maxSpeed — raised from 0.16 to leave headroom above the burst peak; at
                        // 0.16 the ceiling clipped every burst flat and erased exactly the speed
                        // variance burst-and-coast exists to create
            0.025f,     // cruiseSpeed — wander turn authority; must stay BELOW patrolSpeed or fish
                        // pirouette in place instead of gliding (perpendicular wander vs forward)
            DEFAULT.steeringGain(),
            DEFAULT.maxForce(),
            DEFAULT.neighborCount(),
            0.24f,      // separationRadius — patrolling fish close head-on; react earlier
            0.40f,      // separationSpeed — strong enough to deflect a 2×0.16 closing speed
            0.15f,      // alignmentWeight — halved: full velocity-matching synced every wander
            0.05f,      // cohesionSpeed — halved: shoal loosely, don't collapse
            DEFAULT.depthRestore(),
            DEFAULT.depthDamp(),
            DEFAULT.verticalDamp(),
            0.20f,      // wallMargin — react a little earlier than the single-tank set
            DEFAULT.wallMarginVertical(),
            0.50f,      // wallAvoidSpeed — must out-shove patrol (0.07) + separation (0.40) pileups
            DEFAULT.bankGain(),
            DEFAULT.bankMax(),
            DEFAULT.headingDeadzone(),
            DEFAULT.tankHalfExtent(),
            DEFAULT.swarmMinSep(),
            DEFAULT.layerZ(),
            0.9f,       // neighborRange — flocking is local, clusters can drift apart
            0.10f,      // patrolSpeed — forward cruise dominates the wander turn (gentle arcs)
            0.60f,      // separationRadiusOther — strangers keep their distance (user-tuned)
            0.8f,       // wanderTurnSigma — with theta below, steady-state σ = 0.8/√1.8 ≈ 0.60,
                        // matching the legacy sine pair's RMS (≈0.61) so the wander's authority
                        // against patrol/flocking is unchanged; only its *character* is
            0.9f,       // wanderTurnTheta — ~1.1 s correlation time: long enough to read as an
                        // intention, short enough that a fish never commits to a straight line
            0.05f,      // alignHeadingWeight — swept (3×1×3, n=12, 3 seeds): this term trades
                        // polarization against speed variance, because a school that matches
                        // headings hard also flattens onto one speed. 0.09 gave Φ≈0.97, which is
                        // a rigid block, not a school; 0.05 lands Φ≈0.94 — inside the range real
                        // schools occupy — while leaving the burst its variance
            0.15f,      // speedMatchWeight — weak on purpose (see the field comment)
            0.12f,      // traitJitter — ±12%: visible desynchronisation, no odd-fish-out
            4.0f,       // burstPeriodSeconds — a long, slow swell rather than a beat. The first
                        // shipped value (2.2s) was reported in game as fish "hopping" every few
                        // seconds; at this scale a tank is under a block wide, so a cycle short
                        // enough to read as a tail-beat reads as a twitch instead
            0.45f,      // burstDuty — the glide is the longer half, as in real burst-and-coast
            1.4f,       // burstThrustScale — gentle. The original 2.2 commanded a peak above the
                        // speed ceiling and a trough near zero, so the fish spent the cycle
                        // clipped at one end or the other: measured, that config had *less*
                        // within-fish speed variation than no burst at all (0.125 vs 0.138
                        // pre-Tier-1) while nearly doubling the peak rate of speed change
                        // (0.31 vs 0.17 blocks/s²). All the lurch, none of the variation
            0.3f);      // burstCoastScale — a real glide. Counter-intuitively a DEEPER glide is
                        // both smoother and more varied than a shallow one (0.6 measured worse on
                        // every axis): the fish spends longer in the slow part of the cycle, so
                        // the peak is never clipped and the transitions never need to be sharp.
                        // Final: withinCv 0.171 at maxRate 0.169 — i.e. noticeably more speed
                        // variation than the pre-Tier-1 baseline (0.138) while being marginally
                        // *smoother* than it (0.173). Also keeps a crowded 1×1×1 tank apart
                        // (min pairwise 0.033 vs the 0.02 floor); 0.6 dropped that to 0.014

    /** Squared separation radius, matching the derived {@code SEPARATION_RADIUS2} constant. */
    public float separationRadius2() {
        return separationRadius * separationRadius;
    }

    /** Squared neighbour perception radius ({@code MAX_VALUE} when unlimited). */
    public float neighborRange2() {
        return neighborRange == Float.MAX_VALUE ? Float.MAX_VALUE : neighborRange * neighborRange;
    }

    /** Squared cross-species separation radius. */
    public float separationRadiusOther2() {
        return separationRadiusOther * separationRadiusOther;
    }

    // ── Single-field copies, for the viewer's live sliders ─────────────────────────────────────
    // A record has no wither syntax, and spelling out all 35 components at every call site (as the
    // viewer's knob table used to) makes adding one field a 24-line edit with 24 chances to
    // transpose two floats. These are the only sanctioned way to vary a single parameter.

    public Tunables withMaxSpeed(float v) {
        return new Tunables(dt, gateFactor, v, cruiseSpeed, steeringGain, maxForce, neighborCount, separationRadius, separationSpeed, alignmentWeight, cohesionSpeed, depthRestore, depthDamp, verticalDamp, wallMargin, wallMarginVertical, wallAvoidSpeed, bankGain, bankMax, headingDeadzone, tankHalfExtent, swarmMinSep, layerZ, neighborRange, patrolSpeed, separationRadiusOther, wanderTurnSigma, wanderTurnTheta, alignHeadingWeight, speedMatchWeight, traitJitter, burstPeriodSeconds, burstDuty, burstThrustScale, burstCoastScale);
    }

    public Tunables withCruiseSpeed(float v) {
        return new Tunables(dt, gateFactor, maxSpeed, v, steeringGain, maxForce, neighborCount, separationRadius, separationSpeed, alignmentWeight, cohesionSpeed, depthRestore, depthDamp, verticalDamp, wallMargin, wallMarginVertical, wallAvoidSpeed, bankGain, bankMax, headingDeadzone, tankHalfExtent, swarmMinSep, layerZ, neighborRange, patrolSpeed, separationRadiusOther, wanderTurnSigma, wanderTurnTheta, alignHeadingWeight, speedMatchWeight, traitJitter, burstPeriodSeconds, burstDuty, burstThrustScale, burstCoastScale);
    }

    public Tunables withSteeringGain(float v) {
        return new Tunables(dt, gateFactor, maxSpeed, cruiseSpeed, v, maxForce, neighborCount, separationRadius, separationSpeed, alignmentWeight, cohesionSpeed, depthRestore, depthDamp, verticalDamp, wallMargin, wallMarginVertical, wallAvoidSpeed, bankGain, bankMax, headingDeadzone, tankHalfExtent, swarmMinSep, layerZ, neighborRange, patrolSpeed, separationRadiusOther, wanderTurnSigma, wanderTurnTheta, alignHeadingWeight, speedMatchWeight, traitJitter, burstPeriodSeconds, burstDuty, burstThrustScale, burstCoastScale);
    }

    public Tunables withMaxForce(float v) {
        return new Tunables(dt, gateFactor, maxSpeed, cruiseSpeed, steeringGain, v, neighborCount, separationRadius, separationSpeed, alignmentWeight, cohesionSpeed, depthRestore, depthDamp, verticalDamp, wallMargin, wallMarginVertical, wallAvoidSpeed, bankGain, bankMax, headingDeadzone, tankHalfExtent, swarmMinSep, layerZ, neighborRange, patrolSpeed, separationRadiusOther, wanderTurnSigma, wanderTurnTheta, alignHeadingWeight, speedMatchWeight, traitJitter, burstPeriodSeconds, burstDuty, burstThrustScale, burstCoastScale);
    }

    public Tunables withSeparationRadius(float v) {
        return new Tunables(dt, gateFactor, maxSpeed, cruiseSpeed, steeringGain, maxForce, neighborCount, v, separationSpeed, alignmentWeight, cohesionSpeed, depthRestore, depthDamp, verticalDamp, wallMargin, wallMarginVertical, wallAvoidSpeed, bankGain, bankMax, headingDeadzone, tankHalfExtent, swarmMinSep, layerZ, neighborRange, patrolSpeed, separationRadiusOther, wanderTurnSigma, wanderTurnTheta, alignHeadingWeight, speedMatchWeight, traitJitter, burstPeriodSeconds, burstDuty, burstThrustScale, burstCoastScale);
    }

    public Tunables withSeparationSpeed(float v) {
        return new Tunables(dt, gateFactor, maxSpeed, cruiseSpeed, steeringGain, maxForce, neighborCount, separationRadius, v, alignmentWeight, cohesionSpeed, depthRestore, depthDamp, verticalDamp, wallMargin, wallMarginVertical, wallAvoidSpeed, bankGain, bankMax, headingDeadzone, tankHalfExtent, swarmMinSep, layerZ, neighborRange, patrolSpeed, separationRadiusOther, wanderTurnSigma, wanderTurnTheta, alignHeadingWeight, speedMatchWeight, traitJitter, burstPeriodSeconds, burstDuty, burstThrustScale, burstCoastScale);
    }

    public Tunables withAlignmentWeight(float v) {
        return new Tunables(dt, gateFactor, maxSpeed, cruiseSpeed, steeringGain, maxForce, neighborCount, separationRadius, separationSpeed, v, cohesionSpeed, depthRestore, depthDamp, verticalDamp, wallMargin, wallMarginVertical, wallAvoidSpeed, bankGain, bankMax, headingDeadzone, tankHalfExtent, swarmMinSep, layerZ, neighborRange, patrolSpeed, separationRadiusOther, wanderTurnSigma, wanderTurnTheta, alignHeadingWeight, speedMatchWeight, traitJitter, burstPeriodSeconds, burstDuty, burstThrustScale, burstCoastScale);
    }

    public Tunables withCohesionSpeed(float v) {
        return new Tunables(dt, gateFactor, maxSpeed, cruiseSpeed, steeringGain, maxForce, neighborCount, separationRadius, separationSpeed, alignmentWeight, v, depthRestore, depthDamp, verticalDamp, wallMargin, wallMarginVertical, wallAvoidSpeed, bankGain, bankMax, headingDeadzone, tankHalfExtent, swarmMinSep, layerZ, neighborRange, patrolSpeed, separationRadiusOther, wanderTurnSigma, wanderTurnTheta, alignHeadingWeight, speedMatchWeight, traitJitter, burstPeriodSeconds, burstDuty, burstThrustScale, burstCoastScale);
    }

    public Tunables withDepthRestore(float v) {
        return new Tunables(dt, gateFactor, maxSpeed, cruiseSpeed, steeringGain, maxForce, neighborCount, separationRadius, separationSpeed, alignmentWeight, cohesionSpeed, v, depthDamp, verticalDamp, wallMargin, wallMarginVertical, wallAvoidSpeed, bankGain, bankMax, headingDeadzone, tankHalfExtent, swarmMinSep, layerZ, neighborRange, patrolSpeed, separationRadiusOther, wanderTurnSigma, wanderTurnTheta, alignHeadingWeight, speedMatchWeight, traitJitter, burstPeriodSeconds, burstDuty, burstThrustScale, burstCoastScale);
    }

    public Tunables withWallMargin(float v) {
        return new Tunables(dt, gateFactor, maxSpeed, cruiseSpeed, steeringGain, maxForce, neighborCount, separationRadius, separationSpeed, alignmentWeight, cohesionSpeed, depthRestore, depthDamp, verticalDamp, v, wallMarginVertical, wallAvoidSpeed, bankGain, bankMax, headingDeadzone, tankHalfExtent, swarmMinSep, layerZ, neighborRange, patrolSpeed, separationRadiusOther, wanderTurnSigma, wanderTurnTheta, alignHeadingWeight, speedMatchWeight, traitJitter, burstPeriodSeconds, burstDuty, burstThrustScale, burstCoastScale);
    }

    public Tunables withWallAvoidSpeed(float v) {
        return new Tunables(dt, gateFactor, maxSpeed, cruiseSpeed, steeringGain, maxForce, neighborCount, separationRadius, separationSpeed, alignmentWeight, cohesionSpeed, depthRestore, depthDamp, verticalDamp, wallMargin, wallMarginVertical, v, bankGain, bankMax, headingDeadzone, tankHalfExtent, swarmMinSep, layerZ, neighborRange, patrolSpeed, separationRadiusOther, wanderTurnSigma, wanderTurnTheta, alignHeadingWeight, speedMatchWeight, traitJitter, burstPeriodSeconds, burstDuty, burstThrustScale, burstCoastScale);
    }

    public Tunables withHeadingDeadzone(float v) {
        return new Tunables(dt, gateFactor, maxSpeed, cruiseSpeed, steeringGain, maxForce, neighborCount, separationRadius, separationSpeed, alignmentWeight, cohesionSpeed, depthRestore, depthDamp, verticalDamp, wallMargin, wallMarginVertical, wallAvoidSpeed, bankGain, bankMax, v, tankHalfExtent, swarmMinSep, layerZ, neighborRange, patrolSpeed, separationRadiusOther, wanderTurnSigma, wanderTurnTheta, alignHeadingWeight, speedMatchWeight, traitJitter, burstPeriodSeconds, burstDuty, burstThrustScale, burstCoastScale);
    }

    public Tunables withNeighborRange(float v) {
        return new Tunables(dt, gateFactor, maxSpeed, cruiseSpeed, steeringGain, maxForce, neighborCount, separationRadius, separationSpeed, alignmentWeight, cohesionSpeed, depthRestore, depthDamp, verticalDamp, wallMargin, wallMarginVertical, wallAvoidSpeed, bankGain, bankMax, headingDeadzone, tankHalfExtent, swarmMinSep, layerZ, v, patrolSpeed, separationRadiusOther, wanderTurnSigma, wanderTurnTheta, alignHeadingWeight, speedMatchWeight, traitJitter, burstPeriodSeconds, burstDuty, burstThrustScale, burstCoastScale);
    }

    public Tunables withPatrolSpeed(float v) {
        return new Tunables(dt, gateFactor, maxSpeed, cruiseSpeed, steeringGain, maxForce, neighborCount, separationRadius, separationSpeed, alignmentWeight, cohesionSpeed, depthRestore, depthDamp, verticalDamp, wallMargin, wallMarginVertical, wallAvoidSpeed, bankGain, bankMax, headingDeadzone, tankHalfExtent, swarmMinSep, layerZ, neighborRange, v, separationRadiusOther, wanderTurnSigma, wanderTurnTheta, alignHeadingWeight, speedMatchWeight, traitJitter, burstPeriodSeconds, burstDuty, burstThrustScale, burstCoastScale);
    }

    public Tunables withSeparationRadiusOther(float v) {
        return new Tunables(dt, gateFactor, maxSpeed, cruiseSpeed, steeringGain, maxForce, neighborCount, separationRadius, separationSpeed, alignmentWeight, cohesionSpeed, depthRestore, depthDamp, verticalDamp, wallMargin, wallMarginVertical, wallAvoidSpeed, bankGain, bankMax, headingDeadzone, tankHalfExtent, swarmMinSep, layerZ, neighborRange, patrolSpeed, v, wanderTurnSigma, wanderTurnTheta, alignHeadingWeight, speedMatchWeight, traitJitter, burstPeriodSeconds, burstDuty, burstThrustScale, burstCoastScale);
    }

    public Tunables withWanderTurnSigma(float v) {
        return new Tunables(dt, gateFactor, maxSpeed, cruiseSpeed, steeringGain, maxForce, neighborCount, separationRadius, separationSpeed, alignmentWeight, cohesionSpeed, depthRestore, depthDamp, verticalDamp, wallMargin, wallMarginVertical, wallAvoidSpeed, bankGain, bankMax, headingDeadzone, tankHalfExtent, swarmMinSep, layerZ, neighborRange, patrolSpeed, separationRadiusOther, v, wanderTurnTheta, alignHeadingWeight, speedMatchWeight, traitJitter, burstPeriodSeconds, burstDuty, burstThrustScale, burstCoastScale);
    }

    public Tunables withWanderTurnTheta(float v) {
        return new Tunables(dt, gateFactor, maxSpeed, cruiseSpeed, steeringGain, maxForce, neighborCount, separationRadius, separationSpeed, alignmentWeight, cohesionSpeed, depthRestore, depthDamp, verticalDamp, wallMargin, wallMarginVertical, wallAvoidSpeed, bankGain, bankMax, headingDeadzone, tankHalfExtent, swarmMinSep, layerZ, neighborRange, patrolSpeed, separationRadiusOther, wanderTurnSigma, v, alignHeadingWeight, speedMatchWeight, traitJitter, burstPeriodSeconds, burstDuty, burstThrustScale, burstCoastScale);
    }

    public Tunables withAlignHeadingWeight(float v) {
        return new Tunables(dt, gateFactor, maxSpeed, cruiseSpeed, steeringGain, maxForce, neighborCount, separationRadius, separationSpeed, alignmentWeight, cohesionSpeed, depthRestore, depthDamp, verticalDamp, wallMargin, wallMarginVertical, wallAvoidSpeed, bankGain, bankMax, headingDeadzone, tankHalfExtent, swarmMinSep, layerZ, neighborRange, patrolSpeed, separationRadiusOther, wanderTurnSigma, wanderTurnTheta, v, speedMatchWeight, traitJitter, burstPeriodSeconds, burstDuty, burstThrustScale, burstCoastScale);
    }

    public Tunables withSpeedMatchWeight(float v) {
        return new Tunables(dt, gateFactor, maxSpeed, cruiseSpeed, steeringGain, maxForce, neighborCount, separationRadius, separationSpeed, alignmentWeight, cohesionSpeed, depthRestore, depthDamp, verticalDamp, wallMargin, wallMarginVertical, wallAvoidSpeed, bankGain, bankMax, headingDeadzone, tankHalfExtent, swarmMinSep, layerZ, neighborRange, patrolSpeed, separationRadiusOther, wanderTurnSigma, wanderTurnTheta, alignHeadingWeight, v, traitJitter, burstPeriodSeconds, burstDuty, burstThrustScale, burstCoastScale);
    }

    public Tunables withTraitJitter(float v) {
        return new Tunables(dt, gateFactor, maxSpeed, cruiseSpeed, steeringGain, maxForce, neighborCount, separationRadius, separationSpeed, alignmentWeight, cohesionSpeed, depthRestore, depthDamp, verticalDamp, wallMargin, wallMarginVertical, wallAvoidSpeed, bankGain, bankMax, headingDeadzone, tankHalfExtent, swarmMinSep, layerZ, neighborRange, patrolSpeed, separationRadiusOther, wanderTurnSigma, wanderTurnTheta, alignHeadingWeight, speedMatchWeight, v, burstPeriodSeconds, burstDuty, burstThrustScale, burstCoastScale);
    }

    public Tunables withBurstPeriodSeconds(float v) {
        return new Tunables(dt, gateFactor, maxSpeed, cruiseSpeed, steeringGain, maxForce, neighborCount, separationRadius, separationSpeed, alignmentWeight, cohesionSpeed, depthRestore, depthDamp, verticalDamp, wallMargin, wallMarginVertical, wallAvoidSpeed, bankGain, bankMax, headingDeadzone, tankHalfExtent, swarmMinSep, layerZ, neighborRange, patrolSpeed, separationRadiusOther, wanderTurnSigma, wanderTurnTheta, alignHeadingWeight, speedMatchWeight, traitJitter, v, burstDuty, burstThrustScale, burstCoastScale);
    }

    public Tunables withBurstDuty(float v) {
        return new Tunables(dt, gateFactor, maxSpeed, cruiseSpeed, steeringGain, maxForce, neighborCount, separationRadius, separationSpeed, alignmentWeight, cohesionSpeed, depthRestore, depthDamp, verticalDamp, wallMargin, wallMarginVertical, wallAvoidSpeed, bankGain, bankMax, headingDeadzone, tankHalfExtent, swarmMinSep, layerZ, neighborRange, patrolSpeed, separationRadiusOther, wanderTurnSigma, wanderTurnTheta, alignHeadingWeight, speedMatchWeight, traitJitter, burstPeriodSeconds, v, burstThrustScale, burstCoastScale);
    }

    public Tunables withBurstThrustScale(float v) {
        return new Tunables(dt, gateFactor, maxSpeed, cruiseSpeed, steeringGain, maxForce, neighborCount, separationRadius, separationSpeed, alignmentWeight, cohesionSpeed, depthRestore, depthDamp, verticalDamp, wallMargin, wallMarginVertical, wallAvoidSpeed, bankGain, bankMax, headingDeadzone, tankHalfExtent, swarmMinSep, layerZ, neighborRange, patrolSpeed, separationRadiusOther, wanderTurnSigma, wanderTurnTheta, alignHeadingWeight, speedMatchWeight, traitJitter, burstPeriodSeconds, burstDuty, v, burstCoastScale);
    }

    public Tunables withBurstCoastScale(float v) {
        return new Tunables(dt, gateFactor, maxSpeed, cruiseSpeed, steeringGain, maxForce, neighborCount, separationRadius, separationSpeed, alignmentWeight, cohesionSpeed, depthRestore, depthDamp, verticalDamp, wallMargin, wallMarginVertical, wallAvoidSpeed, bankGain, bankMax, headingDeadzone, tankHalfExtent, swarmMinSep, layerZ, neighborRange, patrolSpeed, separationRadiusOther, wanderTurnSigma, wanderTurnTheta, alignHeadingWeight, speedMatchWeight, traitJitter, burstPeriodSeconds, burstDuty, burstThrustScale, v);
    }
}
