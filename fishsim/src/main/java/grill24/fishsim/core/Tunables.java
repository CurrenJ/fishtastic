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
        float burstCoastScale,    // patrol multiplier during the glide

        // ── Tier 2 realism terms (docs/fish-swarm-realism.md §2) ───────────────────────────────
        // Planar-only, neutral-valued in DEFAULT, same guard pattern as Tier 1.

        // Nonholonomic turn limit: the maximum rate (degrees per tick) at which a fish can rotate
        // its TRAVEL direction, not just its sprite. A fish redirects by turning its body, so a
        // steering force that would swing the velocity vector faster than the body can rotate is
        // unphysical — it shows up as the fish translating sideways while pointing somewhere else.
        // This caps the turning (perpendicular) component of the steering acceleration at what the
        // fish's turn rate allows at its current speed (a = v·ω), leaving forward thrust and
        // braking untouched. The sprite yaw uses the same rate, so heading and travel agree by
        // construction. Zero disables the cap entirely and the sprite falls back to the engine's
        // PLANAR_TURN_RATE constant — which is the pre-Tier-2 behaviour, kept for ablation.
        float turnRateDegPerTick,
        // Anticipatory separation horizon, in seconds. Distance-only repulsion is a lagging
        // controller: it cannot tell a fish closing head-on at twice cruise from one drifting past
        // at the same range, so it reacts to the two identically and, in the head-on case, far too
        // late. Over the horizon a fish also extrapolates each neighbour's CURRENT relative
        // velocity to the predicted point of closest approach and repels from that instead,
        // weighted by how soon it arrives. The predicted offset collapses to the present one as
        // that time goes to zero, so this strictly adds lead, never a different steady state.
        // Zero disables it (the binary single-tank model's separation must stay bitwise-exact).
        float separationLookahead) {

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
            1f,              // burstCoastScale
            0f,              // turnRateDegPerTick — off: the binary model has no continuous yaw
            0f);             // separationLookahead — off: distance-only separation (parity)

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
            0.25f,      // separationSpeed — lowered from 0.40 once separationLookahead landed.
                        // Anticipation makes brute strength counter-productive: swept over the
                        // domain matrix at a 1 s horizon, the WORST closest approach anywhere was
                        // 0.085 at this value against 0.042 at 0.40. A hard shove applied late
                        // scatters a crowd into fresh conflicts; a gentle one applied early does
                        // not need to be hard
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
            0.3f,       // burstCoastScale — a real glide. Counter-intuitively a DEEPER glide is
                        // both smoother and more varied than a shallow one (0.6 measured worse on
                        // every axis): the fish spends longer in the slow part of the cycle, so
                        // the peak is never clipped and the transitions never need to be sharp.
                        // Final: withinCv 0.171 at maxRate 0.169 — i.e. noticeably more speed
                        // variation than the pre-Tier-1 baseline (0.138) while being marginally
                        // *smoother* than it (0.173). Also keeps a crowded 1×1×1 tank apart
                        // (min pairwise 0.033 vs the 0.02 floor); 0.6 dropped that to 0.014
            7f,         // turnRateDegPerTick — the value the sprite yaw has always used, now
                        // binding on the trajectory too. Measured pre-cap, the travel direction
                        // turned up to 20 deg/tick against this 7 deg/tick sprite limit, so the
                        // fish crabbed sideways during wall avoids and separation shoves
            1.0f);      // separationLookahead — one second, a couple of body lengths of lead at
                        // GROUP speeds. Swept 0/0.25/0.5/1/2 s: worst-case closest approach over
                        // the matrix went 0.053 (off) → 0.028 (0.5 s) → 0.085 (1 s) → 0.101 (2 s).
                        // Note the dip: a SHORT horizon is worse than none, because it fires often
                        // enough to disturb the shoal but too late to resolve the approach. Beyond
                        // 1 s the gain continues but the 1-block tanks slide further into a
                        // milling torus (see docs/fish-swarm-realism.md), so this is the knee

    /**
     * The parameter set a {@link Locomotion#GLIDE} creature is stepped with — rays, and anything
     * else large and solitary (docs/fish-sim-locomotion.md §3.3). {@code GLIDE} runs the same
     * planar model as a free swimmer, so unlike the crawl and the drift it is a set of numbers
     * rather than a step function of its own, and numbers belong here.
     *
     * <p>It is a fixed set rather than something derived from whichever set the engine is running,
     * because the engine a lone tank runs is {@link #DEFAULT} — whose planar terms are all
     * neutralised to hold the binary model's parity lock. Deriving from that would hand a ray
     * {@code patrolSpeed} 0 and no wander correlation, i.e. a ray that jiggles in place. A ray
     * moves the same way in a lone tank as in a group; only the room it has differs.
     *
     * <p>Slower and wider than {@link #GROUP} on every axis that matters, and unschooled: rays do
     * not shoal, so alignment, speed-matching and cohesion are all off, and the separation radii
     * are up around a body length so two of them never share a corner.
     */
    public static final Tunables GLIDE = new Tunables(
            DEFAULT.dt(),
            DEFAULT.gateFactor(),   // a glider is size-gated exactly like a swimmer — it needs a run
            0.10f,      // maxSpeed — half GROUP's. Nothing about a ray reads as quick
            0.008f,     // cruiseSpeed — wander turn authority, and with wanderTurnSigma below the
                        // pair that sets how much a ray weaves. Reported in game as a wobble laid
                        // over the banking; measured, a solitary ray reverses which way it is
                        // turning about once a second whatever these are set to (it is a limit
                        // cycle in the rate-limited yaw chasing its own steered velocity, not the
                        // wander), so the lever that works is the AMPLITUDE of each swing. This
                        // pair took the mean turn from 0.84 to 0.41 deg/tick — roughly +-4 degrees
                        // of weave, which is an animal swimming rather than a sprite vibrating.
                        // Stays below patrolSpeed either way, or a ray pirouettes instead of gliding
            DEFAULT.steeringGain(),
            DEFAULT.maxForce(),
            DEFAULT.neighborCount(),
            0.70f,      // separationRadius — same species. GROUP's 0.24 is a shoaling distance;
                        // rays are not shoaling, they are sharing a tank
            0.12f,      // separationSpeed — gentle: with radii this wide the term is active most of
                        // the time, so it has to read as room-keeping rather than as a shove
            0f,         // alignmentWeight — unread while alignHeadingWeight is 0, and 0 is the honest
                        // value: a ray matches nobody's heading
            0f,         // cohesionSpeed — solitary
            DEFAULT.depthRestore(),
            DEFAULT.depthDamp(),
            DEFAULT.verticalDamp(),
            0.25f,      // wallMargin — starts its turn earlier than a shoal fish, which is what a
                        // slow turn rate needs to look deliberate rather than late
            DEFAULT.wallMarginVertical(),
            GROUP.wallAvoidSpeed(),
            DEFAULT.bankGain(),
            DEFAULT.bankMax(),
            DEFAULT.headingDeadzone(),
            DEFAULT.tankHalfExtent(),
            DEFAULT.swarmMinSep(),
            DEFAULT.layerZ(),
            1.2f,       // neighborRange — wide enough to cover the separation radii above; the grid
                        // is sized from the widest set in play (FlockEngine.interactionRadius)
            0.05f,      // patrolSpeed — half GROUP's: the whole point of the class
            0.80f,      // separationRadiusOther — strangers get more room still
            0.25f,      // wanderTurnSigma — NOT GROUP's. The OU's steady-state amplitude is
                        // sigma/sqrt(2*theta), so inheriting 0.8 alongside the lower theta below
                        // would have a ray wandering *harder* than the shoal (0.80 against its
                        // 0.60) rather than more calmly, which is the opposite of the class. At
                        // 0.25 the steady state is 0.25, well under half the shoal's — see
                        // cruiseSpeed above for why this is the axis that mattered
            0.5f,       // wanderTurnTheta — ~2 s of correlation against GROUP's ~1.1 s. A ray commits
                        // to a direction for much longer than a shoal fish does
            0f,         // alignHeadingWeight — see alignmentWeight
            0f,         // speedMatchWeight — nobody to match
            GROUP.traitJitter(),
            6.0f,       // burstPeriodSeconds — burst-and-coast is not a compromise here, it is the
                        // literal gait: a ray flaps its wings and then glides. Slower and deeper
                        // than the shoal's swell
            0.30f,      // burstDuty — the glide is most of the cycle
            1.8f,       // burstThrustScale — a real wingbeat. Safe to push where GROUP's could not,
                        // because the peak (0.05 × 1.8 = 0.09) still sits under maxSpeed
            0.35f,      // burstCoastScale
            2.2f,       // turnRateDegPerTick — a third of the shoal's. This is the class's defining
                        // number: what makes a ray read as a ray is that it cannot whip around
            GROUP.separationLookahead());

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
    // A record has no wither syntax, and spelling out all 35 components at every call site (as
    // these methods used to) made adding one tunable a 24-line edit with 24 chances to transpose
    // two floats. Every wither now routes through {@link Mut}, a plain mutable mirror of the
    // component list, so a new tunable costs three lines there instead of one per wither.
    // TunablesWitherTest asserts by reflection that each wither still changes exactly the one
    // component its name promises — the property the hand-written table kept getting wrong.
    // These remain the only sanctioned way to vary a single parameter.

    /** Mutable mirror of the component list — the shared body of every {@code with*} method. */
    private static final class Mut {
        float dt;
        float gateFactor;
        float maxSpeed;
        float cruiseSpeed;
        float steeringGain;
        float maxForce;
        int neighborCount;
        float separationRadius;
        float separationSpeed;
        float alignmentWeight;
        float cohesionSpeed;
        float depthRestore;
        float depthDamp;
        float verticalDamp;
        float wallMargin;
        float wallMarginVertical;
        float wallAvoidSpeed;
        float bankGain;
        float bankMax;
        float headingDeadzone;
        float tankHalfExtent;
        float swarmMinSep;
        float[] layerZ;
        float neighborRange;
        float patrolSpeed;
        float separationRadiusOther;
        float wanderTurnSigma;
        float wanderTurnTheta;
        float alignHeadingWeight;
        float speedMatchWeight;
        float traitJitter;
        float burstPeriodSeconds;
        float burstDuty;
        float burstThrustScale;
        float burstCoastScale;
        float turnRateDegPerTick;
        float separationLookahead;

        Mut(Tunables t) {
            dt = t.dt();
            gateFactor = t.gateFactor();
            maxSpeed = t.maxSpeed();
            cruiseSpeed = t.cruiseSpeed();
            steeringGain = t.steeringGain();
            maxForce = t.maxForce();
            neighborCount = t.neighborCount();
            separationRadius = t.separationRadius();
            separationSpeed = t.separationSpeed();
            alignmentWeight = t.alignmentWeight();
            cohesionSpeed = t.cohesionSpeed();
            depthRestore = t.depthRestore();
            depthDamp = t.depthDamp();
            verticalDamp = t.verticalDamp();
            wallMargin = t.wallMargin();
            wallMarginVertical = t.wallMarginVertical();
            wallAvoidSpeed = t.wallAvoidSpeed();
            bankGain = t.bankGain();
            bankMax = t.bankMax();
            headingDeadzone = t.headingDeadzone();
            tankHalfExtent = t.tankHalfExtent();
            swarmMinSep = t.swarmMinSep();
            layerZ = t.layerZ();
            neighborRange = t.neighborRange();
            patrolSpeed = t.patrolSpeed();
            separationRadiusOther = t.separationRadiusOther();
            wanderTurnSigma = t.wanderTurnSigma();
            wanderTurnTheta = t.wanderTurnTheta();
            alignHeadingWeight = t.alignHeadingWeight();
            speedMatchWeight = t.speedMatchWeight();
            traitJitter = t.traitJitter();
            burstPeriodSeconds = t.burstPeriodSeconds();
            burstDuty = t.burstDuty();
            burstThrustScale = t.burstThrustScale();
            burstCoastScale = t.burstCoastScale();
            turnRateDegPerTick = t.turnRateDegPerTick();
            separationLookahead = t.separationLookahead();
        }

        Tunables build() {
            return new Tunables(dt, gateFactor, maxSpeed, cruiseSpeed, steeringGain, maxForce, neighborCount, separationRadius, separationSpeed, alignmentWeight, cohesionSpeed, depthRestore, depthDamp, verticalDamp, wallMargin, wallMarginVertical, wallAvoidSpeed, bankGain, bankMax, headingDeadzone, tankHalfExtent, swarmMinSep, layerZ, neighborRange, patrolSpeed, separationRadiusOther, wanderTurnSigma, wanderTurnTheta, alignHeadingWeight, speedMatchWeight, traitJitter, burstPeriodSeconds, burstDuty, burstThrustScale, burstCoastScale, turnRateDegPerTick, separationLookahead);
        }
    }

    private Tunables mutate(java.util.function.Consumer<Mut> edit) {
        Mut m = new Mut(this);
        edit.accept(m);
        return m.build();
    }

    public Tunables withMaxSpeed(float v) { return mutate(m -> m.maxSpeed = v); }
    public Tunables withCruiseSpeed(float v) { return mutate(m -> m.cruiseSpeed = v); }
    public Tunables withSteeringGain(float v) { return mutate(m -> m.steeringGain = v); }
    public Tunables withMaxForce(float v) { return mutate(m -> m.maxForce = v); }
    public Tunables withSeparationRadius(float v) { return mutate(m -> m.separationRadius = v); }
    public Tunables withSeparationSpeed(float v) { return mutate(m -> m.separationSpeed = v); }
    public Tunables withAlignmentWeight(float v) { return mutate(m -> m.alignmentWeight = v); }
    public Tunables withCohesionSpeed(float v) { return mutate(m -> m.cohesionSpeed = v); }
    public Tunables withDepthRestore(float v) { return mutate(m -> m.depthRestore = v); }
    public Tunables withWallMargin(float v) { return mutate(m -> m.wallMargin = v); }
    public Tunables withWallAvoidSpeed(float v) { return mutate(m -> m.wallAvoidSpeed = v); }
    public Tunables withHeadingDeadzone(float v) { return mutate(m -> m.headingDeadzone = v); }
    public Tunables withNeighborRange(float v) { return mutate(m -> m.neighborRange = v); }
    public Tunables withPatrolSpeed(float v) { return mutate(m -> m.patrolSpeed = v); }
    public Tunables withSeparationRadiusOther(float v) { return mutate(m -> m.separationRadiusOther = v); }
    public Tunables withWanderTurnSigma(float v) { return mutate(m -> m.wanderTurnSigma = v); }
    public Tunables withWanderTurnTheta(float v) { return mutate(m -> m.wanderTurnTheta = v); }
    public Tunables withAlignHeadingWeight(float v) { return mutate(m -> m.alignHeadingWeight = v); }
    public Tunables withSpeedMatchWeight(float v) { return mutate(m -> m.speedMatchWeight = v); }
    public Tunables withTraitJitter(float v) { return mutate(m -> m.traitJitter = v); }
    public Tunables withBurstPeriodSeconds(float v) { return mutate(m -> m.burstPeriodSeconds = v); }
    public Tunables withBurstDuty(float v) { return mutate(m -> m.burstDuty = v); }
    public Tunables withBurstThrustScale(float v) { return mutate(m -> m.burstThrustScale = v); }
    public Tunables withBurstCoastScale(float v) { return mutate(m -> m.burstCoastScale = v); }

    public Tunables withTurnRateDegPerTick(float v) { return mutate(m -> m.turnRateDegPerTick = v); }

    public Tunables withSeparationLookahead(float v) { return mutate(m -> m.separationLookahead = v); }
}
