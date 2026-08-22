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
        float separationRadiusOther) {

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
            0.18f);          // separationRadiusOther — unread by the binary model

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
            0.16f,      // maxSpeed — longer runs read better a touch faster
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
            0.60f);     // separationRadiusOther — strangers keep their distance (user-tuned)

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
}
