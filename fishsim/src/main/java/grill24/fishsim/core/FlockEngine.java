package grill24.fishsim.core;

import grill24.fishsim.domain.FlockDomain;
import grill24.fishsim.domain.FloorField;

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
    /**
     * Effective locomotion class — the spec's declared class after its size gate, so a fish that
     * failed its gate reads as {@link Locomotion#STATIC} here. This is what {@link #step}
     * dispatches on.
     */
    public Locomotion[] locomotion = new Locomotion[0];
    /** Derived from {@link #locomotion}: gate-passing free swimmers. Kept as the hot-path flag consumers read. */
    public boolean[] swimmers = new boolean[0];
    public boolean[] hoverMirrored = new boolean[0]; // spec mirror flag (hover path only)
    float[] wanderPhaseA = new float[0];
    float[] wanderPhaseB = new float[0];
    float[] homeDepth = new float[0];             // the layer plane this fish drifts toward
    public int[] species = new int[0];            // opaque species id (planar model only)

    // ── Tier 1 per-fish state (planar model only — docs/fish-swarm-realism.md §2) ──────────
    // Ornstein–Uhlenbeck wander state and its private noise stream, replacing the two global
    // sine frequencies every fish used to share. One float of state per axis per fish.
    float[] wanderState = new float[0];
    float[] wanderStateY = new float[0];
    /**
     * OU wander driving a drifter's own slow tumble (§3.2) — unused by every other locomotion
     * class, unlike {@link #wanderState}, which the crawl's heading and the drift's horizontal
     * advection both already share. Angular velocity in the wander's usual dimensionless units;
     * {@link #DRIFT_SPIN_RATE_DEG} converts it to degrees/second.
     */
    float[] spinState = new float[0];
    long[] noiseState = new long[0];
    // Per-fish trait multipliers, derived deterministically from the fish's own seed — no two
    // fish in a tank have quite the same top speed, cruise, or turn rate.
    float[] speedScale = new float[0];
    float[] patrolScale = new float[0];
    float[] turnScale = new float[0];
    // Burst-and-coast cycle: phase in [0,1), advanced by a per-fish rate (period jitter) so a
    // shoal never beats in unison even though each individual's own cycle is regular.
    float[] burstPhase = new float[0];
    float[] burstStep = new float[0];
    /** Integrated burst envelope — the actual multiplier, see {@link #advanceBurst}. */
    float[] burstDrive = new float[0];

    // ── Sim state (local lateral / vertical / depth) ───────────────────────
    float[] posL = new float[0], posY = new float[0], posD = new float[0];
    float[] prevL = new float[0], prevY = new float[0], prevD = new float[0];
    float[] velL = new float[0], velY = new float[0], velD = new float[0];
    public float[] heading = new float[0]; // +1 / -1 (nose along +lateral / −lateral)
    public float[] speed = new float[0];   // blocks/sec, for animation coupling
    public float[] bank = new float[0];    // bank angle (deg), for animation coupling
    /**
     * Low-passed {@link #bank}, and its previous tick — the lean a pose should actually be drawn
     * with. {@code bank} is computed from the yaw delta of a single tick, so it carries all of the
     * wander's and the separation term's tick-to-tick noise: measured on a gliding ray it moved a
     * tenth of full lean every tick and changed sign about twice a second, which reads in game as
     * a small, fast vibration laid over the real bank. Rolling an animal's whole body is the
     * slowest thing it does, not the fastest, so the filter is not a cosmetic smoothing pass — it
     * is the missing physics.
     *
     * <p>{@code bank} itself is left exactly as it was: {@code ParityTest} asserts it bitwise.
     */
    private float[] bankSmooth = new float[0], prevBankSmooth = new float[0];
    /** {@link #bankSmooth} interpolated to the frame, the way {@link #renderYaw} mirrors yaw. */
    public float[] renderBank = new float[0];

    /**
     * A second envelope off the same trigger as {@link #burstDrive}, shaped for the
     * <i>silhouette</i> rather than for the velocity — the squash-and-stretch drive of
     * docs/fish-sim-locomotion.md §3.6. Only the classes whose poses are otherwise nearly still
     * run it: a drifter's bell contraction and a crawler's push-off.
     *
     * <p>It is a separate array rather than a reuse of {@code burstDrive} because the two want
     * opposite decays. Drift's drive rises in ~0.8&nbsp;s and relaxes over ~3.7&nbsp;s, because
     * that is what makes the <i>motion</i> right — a quick push and a long coast. Mapped straight
     * onto scale, the bell would snap shut and then take four seconds to refill, which is not what
     * a jellyfish does: a real one recovers quickly and then simply hangs. So the shape shares the
     * attack and relaxes several times faster.
     *
     * <p>It lives in the engine despite being purely cosmetic, for the same reason
     * {@link #tailPhase} does: this is where it can be integrated at a fixed 20&nbsp;Hz,
     * interpolated to the frame, carried across a rebuild and tested headlessly. A render-side
     * follower would be re-derived per frame at a variable rate and none of that would hold.
     */
    float[] shapeDrive = new float[0];
    float[] prevShapeDrive = new float[0];

    /**
     * An anchored creature's startle clock, in seconds, with the sign carrying the phase:
     * <b>positive</b> is hiding and counting down, <b>negative</b> is habituated and counting up
     * (a threat in range is ignored), and zero is out and watching.
     *
     * <p>The two phases are what make the reaction an <i>event</i> rather than a state. Holding
     * the retract target up for as long as something big is nearby is correct in a quiet tank and
     * completely wrong in a busy one: past a certain stocking density there is always some fish
     * inside the radius, the target never falls, and the colony sits permanently in the sand
     * (reported in game, in a large tank). A fixed hide plus a longer refractory bounds the duty
     * cycle at about a fifth no matter how crowded it gets, so the eels duck and reappear instead.
     */
    float[] anchorTimer = new float[0];

    /**
     * Whether this creature is ready to be startled at all — the other half of making the reaction
     * an event. It is cleared when the watcher arrives and set again only on <b>positive evidence
     * that the watcher went away</b>: present, and beyond {@link #ANCHOR_REARM_RADIUS}.
     *
     * <p>That phrasing is the whole of it, and the obvious phrasing is wrong. "Arm whenever the
     * watcher is not near" reads the same and shipped a colony that ducked at a player standing
     * perfectly still, because <i>not near</i> is also true when the engine has not been told where
     * the watcher is yet — a group engine is rebuilt from scratch whenever membership changes — and
     * when a rebuild re-initialises a fish that carry-over did not cover, and at float resolution
     * on the radius itself. Each of those armed an eel that nobody had walked away from, and the
     * refractory then handed it a fresh reaction a dozen seconds later.
     *
     * <p>So it starts <b>false</b> too. A creature that has never seen the watcher leave has no
     * business reacting to it arriving: if you are already at the glass when the tank loads, you
     * were not an approach. Tanks render from far further away than this radius, so in practice an
     * eel arms long before anyone can walk up to it.
     */
    boolean[] anchorArmed = new boolean[0];

    // The watcher: one external point the host may declare worth hiding from, in this engine's own
    // local coordinates. The engine is deliberately ignorant of what it is — on the Minecraft side
    // TankFlockAdapter feeds it the local player's eye position, mapped through toLocal().
    private boolean watcherPresent;
    private float watcherL, watcherY, watcherD;

    /**
     * Declares where the watcher is, in sim-local coordinates, or that there is none. Cheap enough
     * to call every tick, which is how the adapter uses it — a player moves.
     */
    public void setWatcher(boolean present, float l, float y, float d) {
        watcherPresent = present;
        watcherL = l;
        watcherY = y;
        watcherD = d;
    }

    /**
     * Maps a model-space offset — the frame the render scratch is written in, see
     * {@link #interpolate} — back into sim-local coordinates. The inverse of the rotation
     * {@code interpolate} applies, and the only way a caller can hand this engine a position in
     * the frame it actually thinks in.
     */
    public void toLocal(float x, float y, float z, float[] out) {
        out[0] = x * cosR - z * sinR;
        out[1] = y;
        out[2] = x * sinR + z * cosR;
    }
    /** {@link #shapeDrive} interpolated to the frame — what a pose may actually scale by. */
    public float[] renderShape = new float[0];

    // ── Planar mode (voxel domains) ────────────────────────────────────────
    // Continuous horizontal heading instead of the single-tank binary ±lateral: lateral and depth
    // are fully symmetric swim axes (an L-tank's two arms behave identically), wander curves the
    // path, and patrol + soft wall avoidance produce emergent wall-following loops. The
    // single-tank Box path never uses this — its binary 2.5D model stays bitwise-locked.
    private boolean planar;
    /**
     * Whether any fish in this rebuild walks the floor. Crawlers carry a continuous heading like
     * the planar model's, so the sprite yaw has to be tracked and interpolated even in a
     * single-tank Box engine, which otherwise has no use for it.
     */
    private boolean hasBenthic;
    /** Whether any fish here is a glider — the one class stepped with a parameter set not the engine's. */
    private boolean hasGlide;
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

    /**
     * Rate (1/s) at which the drawn lean chases the commanded one — a ~0.25 s time constant. Fast
     * enough that a turn and its lean still look simultaneous, slow enough that a tick of steering
     * noise moves the body by a fraction of what it asks for.
     */
    private static final float BANK_SMOOTH_RATE = 4.0f;

    /**
     * Hard limit on how fast the drawn lean may change, in degrees per second. The low-pass alone
     * is not enough: {@code bank} saturates at ±{@code bankMax} on any turn at all — for a glider,
     * whose turn budget is small, that is most turns — so it slams the full width of its range and
     * a fraction of a two-lean gap is still a visible snap (measured: 0.32 of full lean in a single
     * tick, through the filter). A rolling body has a top rate, and this is it: from level to fully
     * banked over takes about 1.7 s, which is what an animal with a wingspan looks like.
     */
    private static final float BANK_ROLL_RATE_DEG_PER_SECOND = 6f;

    /** √3 — scales a uniform(−1,1) draw to unit variance for the OU wander's drive term. */
    private static final float SQRT3 = 1.7320508f;
    /**
     * Hard bound on the OU wander signal. The process is unbounded in principle, and one
     * multi-sigma excursion would command a turn far outside anything the rest of the model can
     * absorb; ~4σ at the shipped tunables, so it clips essentially never.
     */
    private static final float WANDER_CLAMP = 2.5f;
    /**
     * Speed, as a fraction of {@code maxSpeed}, below which the nonholonomic turn cap stops
     * shrinking with speed. A fish this slow is pivoting rather than turning, and the travel
     * direction it would be "turning" is numerically ill-defined anyway.
     */
    private static final float TURN_CAP_MIN_SPEED_FRACTION = 0.25f;
    /** ± spread on each fish's burst-and-coast period, as a fraction of the tunable. */
    private static final float BURST_PERIOD_JITTER = 0.25f;
    /**
     * Envelope rates for the burst-and-coast drive, per second. Deliberately asymmetric: the
     * attack is brisk (a fish reaches speed in a few beats) while the glide bleeds off slowly.
     * A symmetric envelope reads as a hop rather than as swimming.
     */
    private static final float BURST_ATTACK_RATE = 3.0f;
    private static final float BURST_DECAY_RATE = 0.8f;

    // ── Benthic crawl (docs/fish-sim-locomotion.md §3.1) ───────────────────────────────────────
    // Deliberately engine constants rather than Tunables, following PLANAR_TURN_RATE and the burst
    // envelope rates above: they are internal to one motion model, DEFAULT and GROUP stay untouched
    // (so the parity lock cannot be reached from here at all), and nothing sweeps them yet. They
    // graduate to Tunables the day the viewer needs sliders for them.

    /** Crawl speed at full drive, blocks/s — an order of magnitude under a swimmer's cruise. */
    private static final float CRAWL_SPEED = 0.035f;
    /** Max crawl turn, degrees per tick. A crawler pivots on the spot; it does not bank. */
    private static final float CRAWL_TURN_RATE = 4.5f;
    /** Period of one scuttle-and-pause cycle, seconds (± BURST_PERIOD_JITTER per fish). */
    private static final float CRAWL_DWELL_SECONDS = 6.0f;
    /** Fraction of that cycle spent actually moving. */
    private static final float CRAWL_DUTY = 0.35f;
    /** Envelope rates for the crawl drive — gentler than the swimmers', a crab has no glide. */
    private static final float CRAWL_ATTACK_RATE = 2.5f;
    private static final float CRAWL_STOP_RATE = 2.0f;
    /**
     * Push-off deformation envelope (§3.6): shares the scuttle's attack, then relaxes far faster
     * than the scuttle does. A crab compresses as it shoves off and is back to its own shape well
     * before it stops moving — the deformation is the start of the move, not the whole of it.
     */
    private static final float CRAWL_SHAPE_ATTACK_RATE = CRAWL_ATTACK_RATE;
    private static final float CRAWL_SHAPE_DECAY_RATE = 6.0f;
    /** OU wander on the crawl heading. */
    private static final float CRAWL_WANDER_SIGMA = 0.25f;
    private static final float CRAWL_WANDER_THETA = 1.2f;
    /** How far ahead a crawler tests the floor before committing to a step, blocks. */
    private static final float CRAWL_PROBE = 0.14f;
    /** Sideways probe angle used to pick which way to turn around an obstacle, degrees. */
    private static final float CRAWL_PROBE_SPREAD = 60f;
    /** Footprint radius as a fraction of body length — the 2D separation distance. */
    private static final float CRAWL_FOOTPRINT = 0.6f;
    /** Push applied when two crawlers overlap footprints, blocks/s. */
    private static final float CRAWL_SEPARATION_SPEED = 0.04f;
    /** Walkable floor a crawler needs, in body-length² — its size gate. */
    private static final float CRAWL_GATE_AREA_FACTOR = 4f;
    /** Keeps a crawler off the very edge of the swim volume, blocks. */
    private static final float CRAWL_EDGE_MARGIN = 0.02f;

    // ── Drift (docs/fish-sim-locomotion.md §3.2) ───────────────────────────────────────────────
    // Engine constants for the same reason the crawl's are: internal to one motion model, and
    // unreachable from DEFAULT/GROUP so the parity lock cannot be perturbed from here.

    /** Horizontal advection speed at one sigma of the wander, blocks/s. */
    private static final float DRIFT_SPEED = 0.012f;
    /**
     * OU wander driving the horizontal drift. Theta is an order of magnitude below the swimmers'
     * deliberately: at ~4 s of correlation the signal reads as being carried by a current rather
     * than as the creature deciding to go somewhere, which is the whole point of the class.
     */
    private static final float DRIFT_WANDER_SIGMA = 0.35f;
    private static final float DRIFT_WANDER_THETA = 0.25f;
    /** Period of one pulse-and-sink cycle, seconds (± BURST_PERIOD_JITTER per fish). */
    private static final float DRIFT_PULSE_SECONDS = 4.5f;
    /** Fraction of that cycle spent contracting the bell. */
    private static final float DRIFT_PULSE_DUTY = 0.18f;
    /**
     * Envelope rates for the pulse drive, per second. Far more asymmetric than the swimmers'
     * burst: a jellyfish contracts hard and briefly, then sinks for several seconds.
     */
    private static final float DRIFT_PULSE_ATTACK_RATE = 4.0f;
    private static final float DRIFT_PULSE_DECAY_RATE = 0.9f;
    /**
     * Bell-contraction deformation envelope (§3.6). Shares the pulse's attack — the squeeze and
     * the thrust are the same event — and relaxes more than three times faster, because the long
     * decay in {@link #DRIFT_PULSE_DECAY_RATE} is the coast, and a bell does not stay squeezed
     * through it.
     */
    private static final float DRIFT_SHAPE_ATTACK_RATE = DRIFT_PULSE_ATTACK_RATE;
    private static final float DRIFT_SHAPE_DECAY_RATE = 3.0f;
    /**
     * OU wander on the drifter's own facing (§3.2) — a jellyfish is not pointing where it is
     * going, but it is not welded to one heading either: it lazily tumbles as the current turns
     * it. Theta is below even the horizontal advection's: a tumble reading as "current" rather
     * than "spinning" needs to be slower than the drift it rides on top of, not the same speed.
     * Sigma is comparable to the horizontal wander's own, so the two read as the same water.
     */
    private static final float DRIFT_SPIN_THETA = 0.15f;
    private static final float DRIFT_SPIN_SIGMA = 0.30f;
    /**
     * Degrees/second at one sigma of {@link #spinState}. Chosen so the steady-state angular
     * speed (sigma/√(2·theta) ≈ 0.55σ in wander units, here ×6 ≈ 3.3°/s) turns the bell through
     * roughly an eighth-turn over one correlation period — visible as a slow tumble across many
     * seconds, never fast enough to read as spinning in place.
     */
    private static final float DRIFT_SPIN_RATE_DEG = 6.0f;

    // ── Anchored (docs/fish-sim-locomotion.md §3.4) ────────────────────────────────────────────
    /**
     * How close the <b>watcher</b> — whatever the host has declared it is worth hiding from, which
     * on the Minecraft side is the local player — has to come before an eel withdraws.
     *
     * <p>An absolute distance in blocks, unlike every other radius in this engine, and
     * deliberately so: the watcher is a fixed-size thing standing in the world rather than another
     * inhabitant of the tank, so scaling this by the eel's own body length would say that a colony
     * of small eels lets you get closer, which is backwards.
     *
     * <p>Three blocks is about "someone has walked up to the tank". Much larger and the eels are
     * already hidden by the time you arrive, so the reaction is never seen happening — which is
     * the same failure as it not firing.
     */
    private static final float ANCHOR_WATCHER_RADIUS = 3.0f;
    /**
     * And how far away the watcher has to get before the same eel will react again — a hysteresis
     * band, not a second threshold to tune. One radius for both edges means a watcher sitting on
     * the boundary re-arms and re-fires on sub-block movement, which is a strobe rather than a
     * reaction.
     */
    private static final float ANCHOR_REARM_RADIUS = ANCHOR_WATCHER_RADIUS * 1.5f;
    /**
     * Down fast, up slow — the whole character of the animation is in the asymmetry, and it is the
     * same shape as every other envelope here, only far more lopsided. 10/s puts the eel most of
     * the way into the sand inside 0.3 s; 0.8/s takes it the better part of three seconds to come
     * back out, which is what §3.4's "re-emerges a couple of seconds later" buys without a hold
     * timer and the per-fish state that would come with it. A threat that lingers simply keeps the
     * target held, which is also what a real eel does.
     */
    private static final float ANCHOR_RETRACT_RATE = 10.0f;
    private static final float ANCHOR_EMERGE_RATE = 0.8f;
    /**
     * How long it stays down once startled, and how long it then ignores everything.
     *
     * <p>These are the numbers that decide what a <i>crowded</i> tank looks like, and they are the
     * reason the reaction is edge-triggered. The hide is a fixed duration rather than "until the
     * threat leaves", and the refractory that follows is far longer than the emerge takes (~3 s at
     * {@link #ANCHOR_EMERGE_RATE}), so the eel is always fully out and visible for a good while
     * before it can be startled again. Together they cap the fraction of time an eel spends hidden
     * at roughly a ninth, however many fish are swimming past it.
     *
     * <p>The refractory was doubled from 6 s after the first in-game look: at 6 the colony read as
     * twitchy in a busy tank — technically out most of the time and ducking too often to settle.
     * What a viewer reads is the <i>rate</i> of the reaction, not the fraction, and the two are
     * only the same thing when the tank is quiet.
     */
    private static final float ANCHOR_HIDE_SECONDS = 1.6f;
    private static final float ANCHOR_REFRACTORY_SECONDS = 12.0f;
    /** Per-fish spread on both, so a colony does not duck and reappear in unison. */
    private static final float ANCHOR_TIMING_JITTER = 0.35f;
    /** Floor a burrow needs, in body-length² — §2.4's "a free floor footprint exists", measured. */
    private static final float ANCHOR_GATE_AREA_FACTOR = 1.0f;
    /**
     * Upward speed at full pulse drive, blocks/s, against the passive sink between pulses. The
     * sink is set to the pulse's own duty-cycle mean (~0.35 of the peak) so the two cancel over a
     * cycle and a drifter neither climbs to the lid nor settles on the sand; what residual bias
     * survives is absorbed by the wall avoidance below, which is why this need not be exact.
     */
    private static final float DRIFT_PULSE_SPEED = 0.10f;
    private static final float DRIFT_SINK_SPEED = 0.035f;
    /**
     * Wall avoidance for a drifter — its only containment, and its vertical centring.
     *
     * <p>The margins are the drifter's own rather than {@code Tunables}', which is not a detail.
     * A swimmer's margin is sized to give a fish travelling at cruise room to turn; a drifter
     * moves an order of magnitude slower and needs no room at all, so borrowing the swimmers'
     * 0.20 left the avoidance term active across the entire width of a one-block-deep tank —
     * measured: horizontal speed peaking at 0.052 blocks/s against a drift speed of 0.012, i.e.
     * the containment doing four fifths of the moving. Pulled in to a band near the glass, the
     * open water is pure drift again and the term does only the job it is there for.
     *
     * <p>The authority must still exceed {@link #DRIFT_SINK_SPEED}, or a sinking drifter would
     * settle on the sand and stay there; vertical gets the wider band because that is the axis
     * the pulse cycle works on.
     */
    private static final float DRIFT_AVOID_SPEED = 0.06f;
    private static final float DRIFT_WALL_MARGIN = 0.08f;
    private static final float DRIFT_WALL_MARGIN_VERTICAL = 0.10f;
    /** Weak same-species cohesion: loose smacks rather than a ball. Radius in blocks. */
    private static final float DRIFT_COHESION_RADIUS = 0.5f;
    private static final float DRIFT_COHESION_SPEED = 0.01f;
    /** Bell radius as a fraction of body length, and the push applied when two overlap. */
    private static final float DRIFT_FOOTPRINT = 0.7f;
    private static final float DRIFT_SEPARATION_SPEED = 0.03f;
    /**
     * Headroom a drifter needs to pulse, in body lengths — its size gate.
     *
     * <p>Below one length on purpose, unlike the swimmers' 2.5. The three drifting species are
     * 28–40 cm (≈0.30–0.36 blocks rendered) while the legacy single-tank model's vertical band is
     * only {@code yRange} = 0.25 blocks tall, so any factor at or above 1 would demote every
     * jellyfish in every single tank to STATIC — a gate that only ever fires is not a gate. A
     * drifter's excursion is a fraction of its body length, not a multiple of it.
     */
    private static final float DRIFT_GATE_HEIGHT_FACTOR = 0.6f;

    // ── GLIDE (docs/fish-sim-locomotion.md §3.3) ───────────────────────────────────────────────
    // The class's *parameters* are Tunables.GLIDE, not constants here, because GLIDE is the planar
    // swimmer model with different numbers. What lives here is the one thing that is not in that
    // model at all: a ray's relationship with the floor it flies over.

    /** Ride height above the sand, in blocks — a ceiling on the fraction below, for deep domains. */
    private static final float GLIDE_RIDE_HEIGHT = 0.45f;
    /** Ride height as a fraction of the headroom above the sand — what applies in shallow ones. */
    private static final float GLIDE_RIDE_FRACTION = 0.30f;
    /** Period of the slow rise and fall over that ride height. Long: this is a swell, not a bob. */
    private static final float GLIDE_SWELL_SECONDS = 17f;
    /** Amplitude of that swell, as a fraction of the ride height, so it scales with the domain. */
    private static final float GLIDE_SWELL_FRACTION = 0.55f;
    /** Vertical desire per block of height error (1/s), and the cap on it (blocks/s). */
    private static final float GLIDE_HEIGHT_GAIN = 0.35f;
    private static final float GLIDE_CLIMB_SPEED = 0.05f;

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

    // Uniform-grid spatial index over the planar model's positions (docs/fish-tank-group-scaling.md
    // §5.2). Rebuilt once per step, queried once per fish; the binary single-tank model never uses
    // it and keeps its brute-force scan for bitwise parity.
    private final NeighborGrid grid = new NeighborGrid();
    /** Candidate count from this fish's grid query, or −1 when the grid is inactive (brute force). */
    private int candidateCount = -1;
    /** Verification hook only — see {@link #setSpatialIndexEnabled}. */
    private boolean spatialIndex = true;

    // Domain-callback scratch (reused every step, no allocation).
    private final float[] floorScratch = new float[2];
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

    /**
     * Verification hook: turns the planar model's spatial index off, forcing the brute-force
     * O(n²) scan. The index is meant to be a pure optimisation — identical results, not merely
     * similar ones (docs/fish-tank-group-scaling.md §5.2) — and the only way to assert that is to
     * run both and compare raw float bits, which {@code SpatialIndexEquivalenceTest} does.
     * Production code must never call this; the brute-force path is quadratic by construction.
     */
    public void setSpatialIndexEnabled(boolean enabled) {
        this.spatialIndex = enabled;
        configureGrid();
    }

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
        // Trait multipliers and burst cadence are pure functions of (seed, tunables) — re-derive
        // them so the new parameter set takes effect without a reseed. Everything continuous
        // (positions, velocities, wander state, burst phase) is deliberately left alone.
        for (int i = 0; i < count; i++) deriveTraits(i, seeds[i]);
        // The interaction radius is derived from the tunables (see interactionRadius()), so the
        // viewer's sliders must re-size the index or it would silently under-reach.
        configureGrid();
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
        rebuild(specs, baseSeed, baseRotationDeg, depthLayers, xzSpread, yRange, rotationJitter, null);
    }

    /**
     * As above, with the tank's floor supplied — the sand height and the cells its cosmetics are
     * standing in. Only crawlers read it; passing null gives an open floor at the bottom of the
     * swim volume, which is what every caller that has no crawlers wants.
     */
    public void rebuild(FishSpec[] specs, long baseSeed, float baseRotationDeg,
                        int depthLayers, float xzSpread, float yRange, float rotationJitter,
                        FloorField floor) {
        rebuildBox(specs, null, baseSeed, baseRotationDeg, depthLayers, xzSpread, yRange,
                rotationJitter, floor);
    }

    /**
     * As {@link #rebuild(FishSpec[], long, float, int, float, float, float)}, but fish the caller
     * recognises as the same individual keep their live sim state instead of being re-scattered.
     *
     * <p>Tank contents change one fish at a time (a player adding or taking one), and every
     * previous rebuild re-seeded the <i>whole</i> flock from index — so removing one fish
     * teleported every other fish in the tank. {@code carryFrom[i]} is the index this fish held in
     * the previous rebuild, or {@code -1} for a fish that wasn't there before; carried fish keep
     * position, velocity, heading, animation phase, seed and rotation jitter, and only genuinely
     * new fish draw a scatter position. Nothing else about the rebuild changes, so a fish that
     * crossed the tank's size gate (or fell out of it) still re-evaluates it here.
     *
     * @param carryFrom one entry per new fish, in the same order as {@code specs}
     */
    public void rebuildPreserving(FishSpec[] specs, int[] carryFrom, long baseSeed, float baseRotationDeg,
                                  int depthLayers, float xzSpread, float yRange, float rotationJitter) {
        rebuildPreserving(specs, carryFrom, baseSeed, baseRotationDeg, depthLayers, xzSpread, yRange,
                rotationJitter, null);
    }

    /** As above, with the tank's floor supplied — see {@link #rebuild(FishSpec[], long, float, int, float, float, float, FloorField)}. */
    public void rebuildPreserving(FishSpec[] specs, int[] carryFrom, long baseSeed, float baseRotationDeg,
                                  int depthLayers, float xzSpread, float yRange, float rotationJitter,
                                  FloorField floor) {
        rebuildBox(specs, carryFrom, baseSeed, baseRotationDeg, depthLayers, xzSpread, yRange,
                rotationJitter, floor);
    }

    private void rebuildBox(FishSpec[] specs, int[] carryFrom, long baseSeed, float baseRotationDeg,
                            int depthLayers, float xzSpread, float yRange, float rotationJitter,
                            FloorField floor) {
        int n = specs.length;
        // Must run before count/allocate/initFish overwrite the arrays it reads.
        captureCarry(carryFrom, n);
        count = n;
        allocate(n);
        for (int i = 0; i < n; i++) order[i] = i;
        hasBenthic = false;
        hasGlide = false;
        for (FishSpec spec : specs) {
            if (spec.locomotion() == Locomotion.BENTHIC) hasBenthic = true;
            if (spec.locomotion() == Locomotion.GLIDE) hasGlide = true;
        }

        float rotRad = (float) Math.toRadians(baseRotationDeg);
        cosR = (float) Math.cos(rotRad);
        sinR = (float) Math.sin(rotRad);

        float[] layerZ = t.layerZ();
        depthLayers = Math.max(1, Math.min(depthLayers, layerZ.length));
        xzSpread = Math.min(xzSpread, t.tankHalfExtent());

        domain = new FlockDomain.Box(t.tankHalfExtent(), verticalHalf(yRange), layerZ, floor);
        // The binary 2.5D model — no continuous yaw, no spatial index, brute-force neighbour scan
        // (bitwise parity). Explicit rather than implicit so re-using an engine that previously
        // ran a voxel group cannot leave it in planar mode.
        this.planar = false;
        configureGrid();

        rng.setSeed(baseSeed);
        int placed = 0;

        // Carried fish are seeded first so the scatter below rejection-samples against where the
        // residents actually are, not against the throwaway positions they'd have been given.
        placed = seedCarried(specs, n, placed);

        for (int i = 0; i < n; i++) {
            if (isCarried(i)) continue;
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

            if (floorPlaced(spec.locomotion())) {
                placeOnFloor(spec, seed, i, floorScratch);
                lateral = floorScratch[0];
                depth = floorScratch[1];
                y = floorHeightAt(lateral, depth);
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
        rebuildPlanar(specs, null, baseSeed, baseRotationDeg, rotationJitter, newDomain);
    }

    /**
     * Carry-over counterpart of {@link #rebuild(FishSpec[], long, float, float, FlockDomain)} —
     * see {@link #rebuildPreserving(FishSpec[], int[], long, float, int, float, float, float)} for
     * what {@code carryFrom} means and why it exists.
     */
    public void rebuildPreserving(FishSpec[] specs, int[] carryFrom, long baseSeed, float baseRotationDeg,
                                  float rotationJitter, FlockDomain newDomain) {
        rebuildPlanar(specs, carryFrom, baseSeed, baseRotationDeg, rotationJitter, newDomain);
    }

    private void rebuildPlanar(FishSpec[] specs, int[] carryFrom, long baseSeed, float baseRotationDeg,
                               float rotationJitter, FlockDomain newDomain) {
        int n = specs.length;
        captureCarry(carryFrom, n);
        count = n;
        allocate(n);
        for (int i = 0; i < n; i++) order[i] = i;
        hasBenthic = false;
        hasGlide = false;
        for (FishSpec spec : specs) {
            if (spec.locomotion() == Locomotion.BENTHIC) hasBenthic = true;
            if (spec.locomotion() == Locomotion.GLIDE) hasGlide = true;
        }

        float rotRad = (float) Math.toRadians(baseRotationDeg);
        cosR = (float) Math.cos(rotRad);
        sinR = (float) Math.sin(rotRad);

        this.domain = newDomain;
        this.planar = true;
        configureGrid();

        rng.setSeed(baseSeed);
        int placed = 0;

        placed = seedCarried(specs, n, placed);

        for (int i = 0; i < n; i++) {
            if (isCarried(i)) continue;
            sampleInDomain(placed, posScratch);
            float lateral = posScratch[0];
            float y = posScratch[1];
            float depth = posScratch[2];
            float baseRotation = baseRotationDeg + (rng.nextFloat() - 0.5f) * 2f * rotationJitter;
            long seed = baseSeed ^ ((long) (i + 1) * 2654435761L);

            if (floorPlaced(specs[i].locomotion())) {
                placeOnFloor(specs[i], seed, i, floorScratch);
                lateral = floorScratch[0];
                depth = floorScratch[1];
                y = floorHeightAt(lateral, depth);
            }

            initFish(i, specs[i], lateral, y, depth, baseRotation, seed);
            yawDeg[i] = prevYawDeg[i] = specs[i].mirrored() ? 180f : 0f;

            placedL[placed] = lateral;
            placedY[placed] = y;
            placedD[placed] = depth;
            placed++;
        }
    }

    // ── Carry-over across rebuilds ──────────────────────────────────────────
    // Snapshot of the previous rebuild's per-fish state for the fish that survived it, indexed by
    // NEW index. Captured before allocate()/initFish() clobber the live arrays, replayed once the
    // fish has been re-initialised from its (unchanged) spec.

    /** New index -> whether this fish carried state over; null/-1 entries scatter as usual. */
    private int[] carrySlot = new int[0];
    private boolean carrying;
    private float[] cPosL = new float[0], cPosY = new float[0], cPosD = new float[0];
    private float[] cPrevL = new float[0], cPrevY = new float[0], cPrevD = new float[0];
    private float[] cVelL = new float[0], cVelY = new float[0], cVelD = new float[0];
    private float[] cHeading = new float[0], cSpeed = new float[0], cBank = new float[0];
    private float[] cBankSmooth = new float[0], cPrevBankSmooth = new float[0];
    private float[] cShapeDrive = new float[0], cPrevShapeDrive = new float[0];
    private float[] cAnchorTimer = new float[0];
    private boolean[] cAnchorArmed = new boolean[0];
    private float[] cTailPhase = new float[0], cPrevTailPhase = new float[0];
    private float[] cHomeDepth = new float[0], cBaseRotation = new float[0];
    private float[] cYawDeg = new float[0], cPrevYawDeg = new float[0];
    private long[] cSeeds = new long[0];
    // Tier 1 state that is *continuous in time* rather than derived from the seed: the OU wander
    // and its noise stream, and the burst-and-coast phase. The seed-derived traits do not need
    // carrying (initFish re-derives them from the carried seed), but these do — resetting the
    // burst phase would visibly jolt a surviving fish's speed mid-glide, which is exactly the
    // class of rebuild teleport rebuildPreserving exists to prevent.
    private float[] cWanderState = new float[0], cWanderStateY = new float[0], cSpinState = new float[0];
    private float[] cBurstPhase = new float[0], cBurstDrive = new float[0];
    private long[] cNoiseState = new long[0];

    private void captureCarry(int[] carryFrom, int n) {
        carrying = carryFrom != null;
        if (!carrying) return;
        allocateCarry(n);
        for (int i = 0; i < n; i++) {
            int from = i < carryFrom.length ? carryFrom[i] : -1;
            // Guard a stale or out-of-range map rather than trusting the caller — a bad index here
            // would silently read another fish's state.
            if (from < 0 || from >= count) {
                carrySlot[i] = -1;
                continue;
            }
            carrySlot[i] = from;
            cPosL[i] = posL[from]; cPosY[i] = posY[from]; cPosD[i] = posD[from];
            cPrevL[i] = prevL[from]; cPrevY[i] = prevY[from]; cPrevD[i] = prevD[from];
            cVelL[i] = velL[from]; cVelY[i] = velY[from]; cVelD[i] = velD[from];
            cHeading[i] = heading[from]; cSpeed[i] = speed[from]; cBank[i] = bank[from];
            cBankSmooth[i] = bankSmooth[from]; cPrevBankSmooth[i] = prevBankSmooth[from];
            cShapeDrive[i] = shapeDrive[from]; cPrevShapeDrive[i] = prevShapeDrive[from];
            cAnchorTimer[i] = anchorTimer[from]; cAnchorArmed[i] = anchorArmed[from];
            cTailPhase[i] = tailPhase[from]; cPrevTailPhase[i] = prevTailPhase[from];
            cHomeDepth[i] = homeDepth[from]; cBaseRotation[i] = baseRotations[from];
            cYawDeg[i] = yawDeg[from]; cPrevYawDeg[i] = prevYawDeg[from];
            cSeeds[i] = seeds[from];
            cWanderState[i] = wanderState[from]; cWanderStateY[i] = wanderStateY[from];
            cSpinState[i] = spinState[from];
            cNoiseState[i] = noiseState[from]; cBurstPhase[i] = burstPhase[from];
            cBurstDrive[i] = burstDrive[from];
        }
    }

    private boolean isCarried(int i) {
        return carrying && carrySlot[i] >= 0;
    }

    /**
     * Re-initialises every carried fish from its snapshot and registers its position with the
     * placement scratch, so the scatter pass that follows treats residents as occupied space.
     *
     * @return the new {@code placed} count
     */
    private int seedCarried(FishSpec[] specs, int n, int placed) {
        if (!carrying) return placed;
        for (int i = 0; i < n; i++) {
            if (carrySlot[i] < 0) continue;
            // Seed and base rotation come from the snapshot too: seeds[] drives the animation RNG
            // (bob phase, wiggle), so re-deriving it from the new index would make a surviving
            // fish visibly jump mid-stroke even with its position held.
            initFish(i, specs[i], cPosL[i], cPosY[i], cPosD[i], cBaseRotation[i], cSeeds[i]);
            homeDepth[i] = cHomeDepth[i];
            prevL[i] = cPrevL[i]; prevY[i] = cPrevY[i]; prevD[i] = cPrevD[i];
            velL[i] = cVelL[i]; velY[i] = cVelY[i]; velD[i] = cVelD[i];
            heading[i] = cHeading[i]; speed[i] = cSpeed[i]; bank[i] = cBank[i];
            bankSmooth[i] = cBankSmooth[i]; prevBankSmooth[i] = cPrevBankSmooth[i];
            shapeDrive[i] = cShapeDrive[i]; prevShapeDrive[i] = cPrevShapeDrive[i];
            anchorTimer[i] = cAnchorTimer[i]; anchorArmed[i] = cAnchorArmed[i];
            tailPhase[i] = cTailPhase[i]; prevTailPhase[i] = cPrevTailPhase[i];
            yawDeg[i] = cYawDeg[i]; prevYawDeg[i] = cPrevYawDeg[i];
            wanderState[i] = cWanderState[i]; wanderStateY[i] = cWanderStateY[i];
            spinState[i] = cSpinState[i];
            noiseState[i] = cNoiseState[i]; burstPhase[i] = cBurstPhase[i];
            burstDrive[i] = cBurstDrive[i];

            // A crawler's world can change under it: a cosmetic dropped into the cell it was
            // standing in, or a group re-shaped around it. Carrying it there would leave it
            // inside a shipwreck, so it re-places — the one case where carry-over cannot win.
            if (floorPlaced(specs[i].locomotion()) && !standable(posL[i], posD[i])) {
                placeOnFloor(specs[i], seeds[i], i, floorScratch);
                posL[i] = prevL[i] = floorScratch[0];
                posD[i] = prevD[i] = floorScratch[1];
                posY[i] = prevY[i] = floorHeightAt(posL[i], posD[i]);
                velL[i] = velD[i] = velY[i] = 0f;
            }

            placedL[placed] = posL[i];
            placedY[placed] = posY[i];
            placedD[placed] = posD[i];
            placed++;
        }
        return placed;
    }

    private void allocateCarry(int n) {
        if (carrySlot.length == n) return;
        carrySlot = new int[n];
        cPosL = new float[n]; cPosY = new float[n]; cPosD = new float[n];
        cPrevL = new float[n]; cPrevY = new float[n]; cPrevD = new float[n];
        cVelL = new float[n]; cVelY = new float[n]; cVelD = new float[n];
        cHeading = new float[n]; cSpeed = new float[n]; cBank = new float[n];
        cBankSmooth = new float[n]; cPrevBankSmooth = new float[n];
        cShapeDrive = new float[n]; cPrevShapeDrive = new float[n];
        cAnchorTimer = new float[n];
        cAnchorArmed = new boolean[n];
        cTailPhase = new float[n]; cPrevTailPhase = new float[n];
        cHomeDepth = new float[n]; cBaseRotation = new float[n];
        cYawDeg = new float[n]; cPrevYawDeg = new float[n];
        cSeeds = new long[n];
        cWanderState = new float[n]; cWanderStateY = new float[n]; cSpinState = new float[n];
        cNoiseState = new long[n]; cBurstPhase = new float[n]; cBurstDrive = new float[n];
    }

    private void initFish(int i, FishSpec spec, float lateral, float y, float depth,
                          float baseRotation, long seed) {
        lengths[i] = spec.length();
        baseRotations[i] = baseRotation;
        seeds[i] = seed;
        species[i] = spec.species();
        hoverMirrored[i] = spec.mirrored();
        locomotion[i] = gate(spec.locomotion(), spec.length());
        swimmers[i] = locomotion[i] == Locomotion.FREE_SWIM;

        posL[i] = prevL[i] = lateral;
        posY[i] = prevY[i] = y;
        posD[i] = prevD[i] = depth;
        homeDepth[i] = depth;
        heading[i] = spec.mirrored() ? -1f : 1f;
        velL[i] = velY[i] = velD[i] = 0f;
        speed[i] = 0f;
        bank[i] = 0f;
        bankSmooth[i] = prevBankSmooth[i] = renderBank[i] = 0f;
        shapeDrive[i] = prevShapeDrive[i] = renderShape[i] = 0f;
        anchorTimer[i] = 0f;
        anchorArmed[i] = false;
        wanderPhaseA[i] = (float) ((seed & 0xFFFF) / 65536.0) * 2f * (float) Math.PI;
        wanderPhaseB[i] = (float) (((seed >>> 16) & 0xFFFF) / 65536.0) * 2f * (float) Math.PI;

        // Tier 1 state. All of it hangs off `seed`, which carry-over preserves (see seedCarried),
        // so a fish that survives a rebuild keeps its individuality as well as its position — and
        // a fish re-seeded from the same tank always draws the same traits.
        deriveTraits(i, seed);
        burstPhase[i] = (unitFromHash(seed, 4) + 1f) * 0.5f;
        burstDrive[i] = 1f;
        if (locomotion[i] == Locomotion.DRIFT) {
            // Relaxed bell: burstDrive is the pulse envelope here, and starting it at 1 would fire
            // every jellyfish in the tank at full contraction on the rebuild tick.
            burstDrive[i] = 0f;
        }
        if (locomotion[i] == Locomotion.BENTHIC || locomotion[i] == Locomotion.GLIDE) {
            // Both keep a continuous heading like the planar model's rather than the swimmers'
            // binary ±lateral, and start facing wherever their mirror flag points. (rebuildPlanar
            // sets this for every fish; a Box domain's rebuild does not, and a glider swims there
            // too.)
            yawDeg[i] = prevYawDeg[i] = spec.mirrored() ? 180f : 0f;
        }
        if (locomotion[i] == Locomotion.BENTHIC) {
            // A crawler starts at rest: burstDrive is its scuttle envelope here.
            burstDrive[i] = 0f;
        }
        wanderState[i] = 0f;
        wanderStateY[i] = 0f;
        spinState[i] = 0f;
        // xorshift64 is dead at zero, so force an odd non-zero stream state.
        noiseState[i] = (seed * 0x2545F4914F6CDD1DL) | 1L;
    }

    /**
     * Applies a locomotion class's own size gate, demoting to {@link Locomotion#STATIC} when the
     * domain is too small for that kind of creature to do its thing. One gate per class, because
     * they measure different quantities: a swimmer needs a straight run, a crawler needs floor
     * area, a drifter needs headroom (docs/fish-sim-locomotion.md §2.4).
     *
     * <p>The swim gate is the pre-existing rule, unchanged and still bitwise-locked. Every class
     * has its own gate now; a creature that fails one is demoted to {@link Locomotion#STATIC} and
     * simply not stepped, but it keeps the position its own class was placed at — a demoted
     * crawler or eel still stands on the sand.
     */
    private Locomotion gate(Locomotion declared, float length) {
        return switch (declared) {
            case FREE_SWIM, GLIDE ->
                    domain.sizeGateRun() >= t.gateFactor() * length ? declared : Locomotion.STATIC;
            case BENTHIC ->
                    domain.floor().area() >= CRAWL_GATE_AREA_FACTOR * length * length
                            ? declared : Locomotion.STATIC;
            case ANCHORED ->
                    domain.floor().area() >= ANCHOR_GATE_AREA_FACTOR * length * length
                            ? declared : Locomotion.STATIC;
            case DRIFT ->
                    domain.maxVertical() - domain.minVertical() >= DRIFT_GATE_HEIGHT_FACTOR * length
                            ? declared : Locomotion.STATIC;
            case STATIC -> Locomotion.STATIC;
        };
    }

    /**
     * (Re)derives the seed-and-tunables-only part of a fish's Tier 1 state: its trait multipliers
     * and its burst cadence. Split out of {@link #initFish} so {@link #setTunables} can call it
     * without disturbing anything continuous in time — otherwise the viewer's traitJitter and
     * burst sliders would read as dead until the next reseed.
     */
    private void deriveTraits(int i, long seed) {
        // This fish's own set: a glider's burst period is its wingbeat and is nothing like the
        // shoal's swell. Safe to call from initFish because locomotion[i] is assigned above it.
        Tunables p = params(i);
        float jitter = p.traitJitter();
        speedScale[i] = 1f + jitter * unitFromHash(seed, 1);
        patrolScale[i] = 1f + jitter * unitFromHash(seed, 2);
        turnScale[i] = 1f + jitter * unitFromHash(seed, 3);
        float period = p.burstPeriodSeconds() * (1f + BURST_PERIOD_JITTER * unitFromHash(seed, 5));
        burstStep[i] = period > 0f ? p.dt() / period : 0f;
    }

    /**
     * Deterministic uniform draw in [−1, 1) from a fish seed and a salt — a SplitMix64 finalizer,
     * which decorrelates the low-entropy seeds the adapter hands us (a blockPos hash XOR a small
     * index multiple) far better than masking off bits the way the legacy wander phases do.
     */
    private static float unitFromHash(long seed, long salt) {
        long z = seed + salt * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return ((z >>> 40) * (1f / 8388608f)) - 1f;
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
        locomotion = new Locomotion[n];
        swimmers = new boolean[n];
        hoverMirrored = new boolean[n];
        wanderPhaseA = new float[n];
        wanderPhaseB = new float[n];
        homeDepth = new float[n];
        species = new int[n];
        wanderState = new float[n]; wanderStateY = new float[n]; spinState = new float[n];
        noiseState = new long[n];
        speedScale = new float[n]; patrolScale = new float[n]; turnScale = new float[n];
        burstPhase = new float[n]; burstStep = new float[n]; burstDrive = new float[n];
        posL = new float[n]; posY = new float[n]; posD = new float[n];
        prevL = new float[n]; prevY = new float[n]; prevD = new float[n];
        velL = new float[n]; velY = new float[n]; velD = new float[n];
        heading = new float[n];
        speed = new float[n];
        bank = new float[n];
        bankSmooth = new float[n]; prevBankSmooth = new float[n]; renderBank = new float[n];
        shapeDrive = new float[n]; prevShapeDrive = new float[n]; renderShape = new float[n];
        anchorTimer = new float[n];
        anchorArmed = new boolean[n];
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
        System.arraycopy(bankSmooth, 0, prevBankSmooth, 0, count);
        System.arraycopy(shapeDrive, 0, prevShapeDrive, 0, count);
        if (planar || continuousYaw()) System.arraycopy(yawDeg, 0, prevYawDeg, 0, count);
        if (planar) grid.build(prevL, prevY, prevD, count);

        for (int i = 0; i < count; i++) {
            // The one place that decides what a locomotion class actually does. FREE_SWIM reaches
            // the identical instructions it always has (the parity lock depends on that); the
            // classes below it have no motion model yet and hold their scatter position exactly
            // like STATIC — each gains one in its own phase, docs/fish-sim-locomotion.md §5.
            boolean beats = switch (locomotion[i]) {
                case FREE_SWIM -> {
                    if (planar) {
                        stepFishPlanar(i);
                    } else {
                        stepFish(i);
                    }
                    yield true;
                }
                case BENTHIC -> {
                    stepBenthic(i);
                    yield true;
                }
                case DRIFT -> {
                    // Moves, but has no tail beat to clock: a jellyfish's pose is a bell pulse and
                    // a spin on game time, neither of which is a function of how fast it travels.
                    stepDrift(i);
                    yield false;
                }
                case GLIDE -> {
                    // The same planar model as a free swimmer, stepped with Tunables.GLIDE — see
                    // stepFishPlanar's first line. It runs in a lone tank's Box domain too, where
                    // the spatial index is inactive and the query below falls back to the full
                    // scan, exactly as the binary model does.
                    stepFishPlanar(i);
                    // No tail beat: a ray's pose is a wingbeat and a bank, and the bank is already
                    // driven by the engine's own turn (FlockEngine.bankFraction).
                    yield false;
                }
                case ANCHORED -> {
                    // Withdraws and re-emerges without ever leaving its burrow. No tail beat: an
                    // eel's pose is a sway on game time and a retract on the engine's envelope.
                    stepAnchored(i);
                    yield false;
                }
                case STATIC -> false;
            };
            // The speed-integrated animation clock runs only for a fish whose pose is driven by
            // its own swimming; everything else is animated open-loop against game time by the
            // renderer, and a drifting tailPhase would double-drive it.
            if (beats) tailPhase[i] += speedFactor(i);
            // Every fish, whether or not it was stepped: a class that never banks holds zero, and
            // one that just stopped being stepped relaxes out of its lean instead of freezing in it.
            float roll = (bank[i] - bankSmooth[i]) * BANK_SMOOTH_RATE * t.dt();
            float maxRoll = BANK_ROLL_RATE_DEG_PER_SECOND * t.dt();
            bankSmooth[i] += SimMath.clamp(roll, -maxRoll, maxRoll);
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
        // Which parameter set this fish is stepped with. A free swimmer gets the engine's own,
        // which is the SAME OBJECT and therefore the identical arithmetic — the planar goldens
        // depend on that. A glider gets the class's set (Tunables.GLIDE): same model, different
        // numbers, which is the whole of what makes a ray a ray (docs/fish-sim-locomotion.md §3.3).
        final Tunables p = params(i);
        final boolean glide = locomotion[i] == Locomotion.GLIDE;
        // One grid query feeds both radius-limited passes below (separation and the neighbour
        // search). Positions are still this fish's own start-of-step values — stepFishPlanar
        // integrates i at the very end — so the query point matches what the index was built on.
        candidateCount = grid.gather(posL[i], posY[i], posD[i]);
        // Patrol/wander steer relative to the fish's own committed heading (yawDeg), not the raw
        // instantaneous velocity direction. yawDeg only turns at PLANAR_TURN_RATE per tick, so
        // this is the persistent "intention" a school needs — using velocity directly here made
        // the direction basis chase whatever wander/avoidance/flocking did to velocity THIS tick,
        // which then fed straight back into next tick's desired velocity: a memoryless loop that
        // read as fish reversing course seconds after any turn (empirical: heading autocorrelation
        // decorrelated by ~4s and went negative by 8s — see WallTurnAnalysis). yawDeg still chases
        // the actual velocity direction every tick (below), just rate-limited — so this is a lag,
        // not a disconnect.
        advanceWander(i, p);
        advanceBurst(i, p);

        float yr = (float) Math.toRadians(yawDeg[i]);
        float dirL = (float) Math.cos(yr);
        float dirD = -(float) Math.sin(yr);

        // OU wander when it's configured, the legacy sine pair otherwise (which is what the
        // single-tank parity set leaves in place).
        boolean ouWander = p.wanderTurnSigma() > 0f;
        float wander = ouWander ? wanderState[i] : wanderL(i);
        float wanderVert = ouWander ? wanderStateY[i] : wanderY(i);

        // Wall avoidance is sampled up front (it depends only on position, which does not change
        // until integration below) because the burst needs to know how close the glass is.
        domain.avoidance(posL[i], posY[i], posD[i], p.wallMargin(), p.wallMarginVertical(), avoidScratch);
        float avoidMag = (float) Math.sqrt(avoidScratch[0] * avoidScratch[0]
                + avoidScratch[1] * avoidScratch[1] + avoidScratch[2] * avoidScratch[2]);
        if (avoidMag > 1f) avoidMag = 1f;

        // Patrol is this individual's cruise, modulated by where it sits in its own
        // beat-and-glide cycle — the term that turns a constant-speed drift into swimming.
        //
        // The burst is faded out toward plain cruise as the fish nears a wall. That is how real
        // fish behave (nothing sprints at glass), and it is also load-bearing: soft containment
        // only works while wallAvoidSpeed out-shoves everything pushing the other way, and an
        // ungated burst peak plus a separation shove beat it in a 1-block domain — the hard
        // backstop started engaging, which the invariant tests treat as a containment failure
        // rather than a tuning nit.
        float burst = burstFactor(i);
        burst += (1f - burst) * avoidMag;
        float patrol = p.patrolSpeed() * patrolScale[i] * burst;

        float dL = dirL * patrol + (-dirD) * wander * p.cruiseSpeed();
        float dD = dirD * patrol + dirL * wander * p.cruiseSpeed();
        float dY = wanderVert * p.cruiseSpeed();

        // A glider hugs the terrain instead of using the water column freely: a ray cruises a
        // little way off the sand and rises and falls over a long period, which is most of what
        // separates it from a fish at this distance. Both quantities are fractions of the actual
        // headroom above the sand *here*, not absolute heights — the same creature has to work in
        // a lone tank's quarter-block slab and in a stacked group's several blocks, and an
        // absolute ride height would pin it to the lid of the former.
        if (glide) {
            float ride = glideRideHeight(i);
            float base = glideFloor(i);
            float swellPhase = simTick * p.dt() / GLIDE_SWELL_SECONDS
                    + (unitFromHash(seeds[i], 7) + 1f) * 0.5f; // per-fish, so a pair never rises together
            float swell = (float) Math.sin(swellPhase * 2.0 * Math.PI) * GLIDE_SWELL_FRACTION * ride;
            float target = SimMath.clamp(base + ride + swell, domain.minVertical(), domain.maxVertical());
            dY += SimMath.clamp((target - posY[i]) * GLIDE_HEIGHT_GAIN,
                    -GLIDE_CLIMB_SPEED, GLIDE_CLIMB_SPEED);
        }

        // Species-aware separation: shoal-mates use the tight radius (they may swarm), strangers
        // the wide one — this is what keeps a mixed tank from congealing into one ball while
        // letting each species keep its own cluster.
        float sepL = 0f, sepY = 0f, sepD = 0f;
        float horizon = p.separationLookahead();
        int scanned = candidateCount >= 0 ? candidateCount : count;
        int[] cand = candidateCount >= 0 ? grid.candidates() : null;
        for (int c = 0; c < scanned; c++) {
            int j = cand != null ? cand[c] : c;
            if (j == i) continue;
            float radius = species[i] == species[j] ? p.separationRadius() : p.separationRadiusOther();
            float radius2 = species[i] == species[j] ? p.separationRadius2() : p.separationRadiusOther2();
            float dx = posL[i] - posL[j], dy = posY[i] - posY[j], dz = posD[i] - posD[j];
            float d2 = dx * dx + dy * dy + dz * dz;
            if (d2 < radius2 && d2 > 1e-6f) {
                float d = (float) Math.sqrt(d2);
                float w = (radius - d) / radius;
                sepL += (dx / d) * w;
                sepY += (dy / d) * w;
                sepD += (dz / d) * w;
            }

            // Anticipatory half of the term (Tier 2): repel from where this pair is predicted to
            // be at closest approach, not only from where it is now. Folded into the same loop
            // because the neighbour scan is already the planar step's dominant cost.
            if (horizon <= 0f) continue;
            float rvL = velL[i] - velL[j], rvY = velY[i] - velY[j], rvD = velD[i] - velD[j];
            float closing = dx * rvL + dy * rvY + dz * rvD;
            if (closing >= 0f) continue; // opening, or holding station — nothing to anticipate
            float rv2 = rvL * rvL + rvY * rvY + rvD * rvD;
            if (rv2 < 1e-8f) continue;
            float tStar = -closing / rv2;
            if (tStar > horizon) continue; // too far off to steer for yet
            float pL = dx + rvL * tStar, pY = dy + rvY * tStar, pD = dz + rvD * tStar;
            float p2 = pL * pL + pY * pY + pD * pD;
            if (p2 >= radius2) continue; // predicted to pass clear
            // Sooner means stronger, linearly, so this fades into the distance term rather than
            // switching on at the horizon.
            float urgency = 1f - tStar / horizon;
            float pd = (float) Math.sqrt(p2);
            if (pd > 1e-4f) {
                float w = (radius - pd) / radius * urgency;
                sepL += (pL / pd) * w;
                sepY += (pY / pd) * w;
                sepD += (pD / pd) * w;
            }
            // No else: a predicted offset this small is an exactly-head-on approach, which carries
            // no escape direction to normalize. Instrumented over the whole domain matrix, the
            // branch fired zero times in 42k fish-ticks — an exact head-on is a measure-zero event
            // in float arithmetic, and near-head-on pairs are already handled above, where the
            // residual offset is small but well-conditioned. So this is only a divide guard, and
            // skipping one tick of lead costs nothing: the distance term still applies, and the
            // pair is back above the threshold by the next step.
        }

        // Saturate the total separation thrust below the wall-avoidance authority: in a domain
        // too small to honor the cross-species radius (many strangers, tiny box), the summed
        // shoves must never out-shove containment — crowded fish tolerate closeness instead of
        // pushing each other through the glass. (Empirical: uncapped 0.6-radius shoves in a
        // 1×1×1 with 12 fish engaged the hard backstop.)
        float sepCap = p.wallAvoidSpeed() * 0.45f;
        float sepMagnitude = (float) Math.sqrt(sepL * sepL + sepY * sepY + sepD * sepD) * p.separationSpeed();
        float sepScale = sepMagnitude > sepCap ? sepCap / sepMagnitude : 1f;

        // Alignment: on unit heading when alignHeadingWeight is set, on raw velocity otherwise.
        // Splitting direction-matching from speed-matching is what lets the direction gain be
        // raised to a level that actually polarizes the school — averaging velocities made the
        // two inseparable, so the gain had to stay low enough not to also flatten every fish's
        // speed onto the neighbourhood mean.
        boolean headingAlign = p.alignHeadingWeight() > 0f;
        int neigh = findNearestSwimmers(i);
        float aliL = 0f, aliY = 0f, aliD = 0f, cohL = 0f, cohY = 0f, cohD = 0f;
        float neighSpeed = 0f;
        for (int b = 0; b < neighborIdx.length; b++) {
            int j = neighborIdx[b];
            if (j < 0) break;
            if (headingAlign) {
                float s = (float) Math.sqrt(velL[j] * velL[j] + velY[j] * velY[j] + velD[j] * velD[j]);
                if (s > 1e-5f) {
                    aliL += velL[j] / s; aliY += velY[j] / s; aliD += velD[j] / s;
                }
                neighSpeed += s;
            } else {
                aliL += velL[j]; aliY += velY[j]; aliD += velD[j];
            }
            cohL += posL[j] - posL[i]; cohY += posY[j] - posY[i]; cohD += posD[j] - posD[i];
        }
        if (neigh > 0) {
            aliL /= neigh; aliY /= neigh; aliD /= neigh;
            cohL /= neigh; cohY /= neigh; cohD /= neigh;
            neighSpeed /= neigh;
        }

        float alignWeight;
        if (headingAlign) {
            alignWeight = p.alignHeadingWeight();
            // Formation-keeping yields to containment near the glass.
            //
            // The unit-heading formulation silently dropped a negative feedback that the
            // velocity-averaged one had for free: fish decelerating into a wall contributed a
            // shrinking average, so alignment faded exactly when containment needed the
            // authority. A unit heading has magnitude 1 however nearly stopped its owner is, so a
            // school pressed against glass kept commanding a full-strength push into it. Ablation
            // over the 1x1x1 matrix (n=6..12) identified this term as the *sole* cause of
            // hard-backstop engagements — zeroing it alone returned every case to zero.
            //
            // Gating on neighbour speed was the obvious repair and measurably did not work (it
            // left, and in places worsened, the engagements). Gating on wall proximity does:
            // zero engagements across the whole matrix. avoidMag is only non-zero inside
            // wallMargin, so a school still holds formation everywhere but the last few
            // centimetres before the glass — where real fish break formation too.
            alignWeight *= (1f - avoidMag);
        } else {
            alignWeight = p.alignmentWeight();
        }
        dL += sepL * p.separationSpeed() * sepScale + aliL * alignWeight + cohL * p.cohesionSpeed();
        dD += sepD * p.separationSpeed() * sepScale + aliD * alignWeight + cohD * p.cohesionSpeed();
        dY += sepY * p.separationSpeed() * sepScale + aliY * alignWeight + cohY * p.cohesionSpeed();

        // Speed matching, the other half of the split: nudge own forward speed toward the
        // neighbourhood mean along the fish's OWN heading, never sideways — a fish keeping pace
        // with the shoal speeds up, it does not get dragged across the tank.
        if (headingAlign && neigh > 0 && p.speedMatchWeight() > 0f) {
            float own = (float) Math.sqrt(velL[i] * velL[i] + velY[i] * velY[i] + velD[i] * velD[i]);
            float match = (neighSpeed - own) * p.speedMatchWeight();
            dL += dirL * match;
            dD += dirD * match;
        }

        // (avoidScratch was filled at the top of this method.)
        dL += avoidScratch[0] * p.wallAvoidSpeed();
        dY += avoidScratch[1] * p.wallAvoidSpeed();
        dD += avoidScratch[2] * p.wallAvoidSpeed();

        float aL = (dL - velL[i]) * p.steeringGain();
        float aY = (dY - velY[i]) * p.steeringGain();
        float aD = (dD - velD[i]) * p.steeringGain();
        float f = (float) Math.sqrt(aL * aL + aY * aY + aD * aD);
        if (f > p.maxForce()) {
            float k = p.maxForce() / f;
            aL *= k; aY *= k; aD *= k;
        }

        // Nonholonomic turn limit (Tier 2). A fish redirects by rotating its body, so its travel
        // direction cannot swing faster than it can turn — but until this term, PLANAR_TURN_RATE
        // capped only the *sprite* yaw while the velocity vector swung as fast as maxForce allowed.
        // Measured over the domain matrix before the cap: travel direction turning up to 20°/tick
        // against a 7°/tick sprite limit, leaving >30° of sideslip on ~3% of ticks in a crowded
        // tank — the fish visibly crabbing during wall avoids and separation shoves.
        //
        // Only the TURNING component is capped: the part of the steering acceleration
        // perpendicular to the current travel direction, bounded by a = v·ω. Forward thrust and
        // braking pass through untouched, which is what keeps soft containment's authority intact
        // — a fish that can no longer sidestep glass still decelerates into it, and the wall term
        // still commands the turn away. Applied AFTER the maxForce clamp, so it only ever reduces
        // |a| and the acceleration invariant is unaffected.
        if (p.turnRateDegPerTick() > 0f) {
            float hspNow = (float) Math.sqrt(velL[i] * velL[i] + velD[i] * velD[i]);
            if (hspNow > 1e-5f) {
                // Below a real cruising speed the travel direction is numerically ill-defined and
                // a fish is effectively pivoting in place, where turning costs nothing; the
                // reference speed floors the budget rather than letting it collapse to zero.
                float refSpeed = Math.max(hspNow, TURN_CAP_MIN_SPEED_FRACTION * p.maxSpeed());
                float omega = (float) Math.toRadians(p.turnRateDegPerTick() * turnScale[i]) / p.dt();
                float maxLateral = refSpeed * omega;
                float fwdL = velL[i] / hspNow, fwdD = velD[i] / hspNow;
                float along = aL * fwdL + aD * fwdD;
                float latL = aL - along * fwdL, latD = aD - along * fwdD;
                float latMag = (float) Math.sqrt(latL * latL + latD * latD);
                if (latMag > maxLateral) {
                    float k = maxLateral / latMag;
                    aL = along * fwdL + latL * k;
                    aD = along * fwdD + latD * k;
                }
            }
        }

        velL[i] += aL * p.dt();
        velY[i] += aY * p.dt();
        velY[i] *= (1f - p.verticalDamp() * p.dt());
        velD[i] += aD * p.dt();

        // The ceiling is this fish's own, not the shoal's: a school where every member tops out
        // at exactly the same speed can never string out into the ragged line real ones form.
        float cap = p.maxSpeed() * speedScale[i];
        float sp = (float) Math.sqrt(velL[i] * velL[i] + velY[i] * velY[i] + velD[i] * velD[i]);
        if (sp > cap) {
            float k = cap / sp;
            velL[i] *= k; velY[i] *= k; velD[i] *= k;
            sp = cap;
        }
        speed[i] = sp;

        posL[i] += velL[i] * p.dt();
        posY[i] += velY[i] * p.dt();
        posD[i] += velD[i] * p.dt();
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
            // The same rate that bounds the trajectory above, so sprite and travel agree by
            // construction rather than by the velocity happening to turn slowly enough.
            float turnRate = (p.turnRateDegPerTick() > 0f ? p.turnRateDegPerTick() : PLANAR_TURN_RATE)
                    * turnScale[i];
            float turn = SimMath.clamp(diff, -turnRate, turnRate);
            yawDeg[i] = wrapDeg(yawDeg[i] + turn);
            // Bank is a genuine function of turn rate: full rate leans fully, and everything below
            // it leans proportionally. The old fixed 1.5°-of-bank-per-degree-of-turn saturated at
            // |turn| ≥ 6.7° of a 7° budget, so with an uncapped trajectory — where diff was pinned
            // at the limit through every turn — bank read as binary rather than as a lean.
            bank[i] = SimMath.clamp(turn * (p.bankMax() / turnRate), -p.bankMax(), p.bankMax());
        } else {
            bank[i] *= 0.9f;
        }
        // Keep the binary heading roughly meaningful for consumers that read it (metrics ignore
        // flips in planar mode).
        heading[i] = dirL >= 0f ? 1f : -1f;
    }

    /**
     * This fish's bank as a fraction of its own full lean, in [−1, 1].
     *
     * <p>Reads the smoothed, interpolated {@link #renderBank}, so it is only meaningful after
     * {@link #interpolate} — like every other {@code render*} value the animator consumes.
     * {@link #bank} in degrees against whichever parameter set stepped the fish is the raw signal
     * behind it, and the renderer has no business knowing which set that was. A pose that authors its own lean angle
     * (a ray's {@code bank_amplitude}) multiplies it by this instead, so the data file keeps
     * saying how far the creature leans and the engine says only when, and how much of it.
     */
    public float bankFraction(int i) {
        float max = params(i).bankMax();
        return max <= 0f ? 0f : SimMath.clamp(renderBank[i] / max, -1f, 1f);
    }

    /**
     * The parameter set fish {@code i} is stepped with. Every class but {@link Locomotion#GLIDE}
     * gets the engine's own set — identically, by reference, because the goldens are bitwise.
     */
    private Tunables params(int i) {
        return locomotion[i] == Locomotion.GLIDE ? Tunables.GLIDE : t;
    }

    /** The surface a glider measures its ride height from: the sand under it, or the domain floor. */
    private float glideFloor(int i) {
        float floor = floorHeightAt(posL[i], posD[i]);
        return Float.isNaN(floor) ? domain.minVertical() : Math.max(floor, domain.minVertical());
    }

    /** How far above {@link #glideFloor} this fish cruises — a fraction of the headroom, capped. */
    private float glideRideHeight(int i) {
        float headroom = domain.maxVertical() - glideFloor(i);
        if (headroom <= 0f) return 0f;
        return Math.min(GLIDE_RIDE_HEIGHT, GLIDE_RIDE_FRACTION * headroom);
    }

    /** Wraps an angle to (−180, 180]. */
    private static float wrapDeg(float deg) {
        float d = deg % 360f;
        if (d > 180f) d -= 360f;
        if (d <= -180f) d += 360f;
        return d;
    }

    private void stepFish(int i) {
        candidateCount = -1; // binary model: brute-force scan, bitwise parity
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

    /**
     * The radius beyond which one fish provably cannot affect another, for the planar model —
     * the sizing input for {@link NeighborGrid}. Getting this wrong silently changes behaviour,
     * so it is derived rather than tuned:
     *
     * <ul>
     *   <li>The plain separation term is guarded by {@code d2 < radius2}, so it dies past
     *       {@code max(separationRadius, separationRadiusOther)}.</li>
     *   <li>{@link #findNearestSwimmers} is guarded by {@code d2 >= neighborRange2}.</li>
     *   <li>The anticipatory separation term (Tier 2) is <em>not</em> distance-guarded — it fires
     *       on the predicted offset at closest approach. Since {@code |p| >= |d| - |rv|*tStar},
     *       {@code tStar <= separationLookahead}, and each fish's speed is capped at
     *       {@code maxSpeed * (1 + traitJitter)}, it cannot fire past
     *       {@code sepRadius + 2*vMax*lookahead}. That term, not the neighbour range, is what
     *       sets the radius at the shipped GROUP tunables (≈1.05 against 0.9).</li>
     * </ul>
     */
    /**
     * Whether any fish here steers by a continuous yaw rather than the binary model's ±lateral
     * heading. It decides whether the yaw arrays are worth copying and interpolating in a lone
     * tank's Box domain, which otherwise has no use for them.
     */
    private boolean continuousYaw() {
        return hasBenthic || hasGlide;
    }

    private float interactionRadius() {
        float r = interactionRadiusOf(t);
        // A glider is stepped with its own, wider set (Tunables.GLIDE): its separation radii are
        // around a body length where the shoal's are a quarter of one. The index must reach the
        // widest set actually in play, or a ray would silently stop seeing the neighbours it is
        // supposed to keep away from — the grid's contract is that a skipped fish contributes
        // exactly zero, and that only holds if nothing in range is ever skipped.
        if (hasGlide) r = Math.max(r, interactionRadiusOf(Tunables.GLIDE));
        return r;
    }

    private float interactionRadiusOf(Tunables p) {
        float nr = p.neighborRange();
        if (nr == Float.MAX_VALUE || Float.isInfinite(nr)) return Float.MAX_VALUE;
        float sep = Math.max(p.separationRadius(), p.separationRadiusOther());
        float anticipatory = sep + 2f * peakSpeed() * Math.max(0f, p.separationLookahead());
        return Math.max(nr, anticipatory);
    }

    /** The fastest any fish may be moving — the per-fish speed cap at the top of the trait spread. */
    private float peakSpeed() {
        return t.maxSpeed() * (1f + Math.abs(t.traitJitter()));
    }

    /**
     * Re-sizes the spatial index for the current domain, tunables, and fish count. Called from
     * every planar rebuild and from {@link #setTunables}; never from the step loop.
     */
    private void configureGrid() {
        if (!planar || !spatialIndex) {
            grid.configure(domain, Float.MAX_VALUE, 0f, 0);
            return;
        }
        grid.configure(domain, interactionRadius(), peakSpeed() * t.dt(), count);
    }

    /**
     * The {@code k} nearest shoal-mates of fish {@code i}, into {@link #neighborIdx}.
     *
     * <p>{@code candidateCount} is the planar model's grid query for this fish (−1 = brute force,
     * which the binary single-tank model always takes). The candidate list is ascending, so the
     * scan order — and therefore which index wins a distance tie in the insertion below — is the
     * same as the full scan's; every fish the grid omitted is farther than {@code neighborRange}
     * and would have failed the {@code d2 >= range2} test anyway.
     */
    private int findNearestSwimmers(int i) {
        int neighborCount = neighborIdx.length;
        for (int b = 0; b < neighborCount; b++) {
            neighborIdx[b] = -1;
            neighborDist[b] = Float.MAX_VALUE;
        }
        float range2 = t.neighborRange2(); // MAX_VALUE (single-tank set) admits everyone — parity-safe
        int scanned = candidateCount >= 0 ? candidateCount : count;
        int[] cand = candidateCount >= 0 ? grid.candidates() : null;
        for (int c = 0; c < scanned; c++) {
            int j = cand != null ? cand[c] : c;
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

    /**
     * Advances this fish's Ornstein–Uhlenbeck wander one tick — the replacement for the sine pair
     * above, whose two frequencies were global constants, so every fish in a tank shared one
     * rhythm no matter how their phases were scattered.
     *
     * <p>OU is the right process here rather than plain white noise: it is mean-reverting (the
     * fish always drifts back toward straight, so a wander excursion is a curve and not a
     * permanent course change) and correlated over ~1/theta seconds (so the signal reads as an
     * intention rather than as jitter). Steady-state standard deviation is sigma/√(2·theta).
     *
     * <p>The drive is a uniform draw, not a Gaussian: the integrator is itself a low-pass, so the
     * driving distribution's shape does not survive into the output, and √3 rescales the uniform
     * to unit variance so sigma keeps its meaning.
     */
    /**
     * @param p this fish's parameter set — not necessarily the engine's, see {@link #params}. A
     *          glider's wander is deliberately calmer and far more correlated than a shoal fish's,
     *          and reading {@code t} here would silently hand it the shoal's.
     */
    private void advanceWander(int i, Tunables p) {
        if (p.wanderTurnSigma() <= 0f) return;
        float dt = p.dt();
        float k = p.wanderTurnSigma() * (float) Math.sqrt(dt) * SQRT3;
        float decay = p.wanderTurnTheta() * dt;
        wanderState[i] += -wanderState[i] * decay + k * nextSignedUnit(i);
        wanderStateY[i] += -wanderStateY[i] * decay + k * nextSignedUnit(i);
        wanderState[i] = SimMath.clamp(wanderState[i], -WANDER_CLAMP, WANDER_CLAMP);
        wanderStateY[i] = SimMath.clamp(wanderStateY[i], -WANDER_CLAMP, WANDER_CLAMP);
    }

    /**
     * Advances this fish's burst-and-coast cycle one tick: the phase wraps, and the drive
     * envelope chases the phase's current target.
     *
     * <p>The envelope is <i>integrated</i> rather than being a direct function of phase, and its
     * two rates are deliberately asymmetric — a fast attack and a much slower decay. That is the
     * shape real burst-and-coast has: a fish beats hard, then <i>glides</i>, losing speed only to
     * drag.
     *
     * <p>The first implementation used a symmetric sin² pulse over the duty fraction and then
     * dropped straight to the coast level. In game that read as a hop: a lurch forward every
     * couple of seconds followed by a conspicuously fast slowdown, because holding a near-zero
     * target while the steering gain drives velocity toward it is an active brake, not a glide.
     * Integrating the envelope makes the commanded speed continuous and the decay gradual.
     */
    /** @param p as {@link #advanceWander}: for a ray this cycle is a wingbeat, not a tail burst. */
    private void advanceBurst(int i, Tunables p) {
        if (burstStep[i] <= 0f) {
            burstDrive[i] = 1f;
            return;
        }
        burstPhase[i] += burstStep[i];
        if (burstPhase[i] >= 1f) burstPhase[i] -= 1f;
        boolean thrusting = burstPhase[i] < p.burstDuty();
        float target = thrusting ? p.burstThrustScale() : p.burstCoastScale();
        float rate = thrusting ? BURST_ATTACK_RATE : BURST_DECAY_RATE;
        burstDrive[i] += (target - burstDrive[i]) * rate * p.dt();
    }

    /** This fish's current burst-and-coast multiplier on patrol speed. */
    private float burstFactor(int i) {
        return burstStep[i] <= 0f ? 1f : burstDrive[i];
    }

    /** One xorshift64 draw from this fish's private noise stream, mapped to [−1, 1). */
    private float nextSignedUnit(int i) {
        long x = noiseState[i];
        x ^= x << 13;
        x ^= x >>> 7;
        x ^= x << 17;
        noiseState[i] = x;
        return ((x >>> 40) * (1f / 8388608f)) - 1f;
    }

    /**
     * Advances the silhouette envelope off the motion envelope's own trigger (§3.6). Same
     * integrator as the drives it rides alongside, with its own pair of rates — see
     * {@link #shapeDrive} for why it is not simply the drive itself.
     */
    private void advanceShape(int i, boolean driving, float attack, float decay, float dt) {
        float target = driving ? 1f : 0f;
        float rate = driving ? attack : decay;
        shapeDrive[i] += (target - shapeDrive[i]) * rate * dt;
    }

    // ── Benthic crawl (docs/fish-sim-locomotion.md §3.1) ───────────────────────────────────────

    /**
     * One step of the floor walk. Deliberately <b>not</b> the swimmers' force model: a crawler is
     * overdamped — it has no glide and no momentum worth integrating — so this commands velocity
     * directly, and the position it produces is the position it keeps. That also makes the "never
     * inside an obstacle" invariant structural rather than emergent: the move is tested against
     * the floor before it is taken, and refused outright if it would land off it.
     *
     * <p>Vertical is not simulated at all. A crawler's Y <i>is</i> the floor height under it, so
     * it steps up between two tanks of different heights for free, and the domain's vertical
     * clamp — which describes the swim volume, well above the sand — never applies to it.
     */
    private void stepBenthic(int i) {
        float dt = t.dt();

        // Scuttle-and-pause. Same integrated-envelope shape as the swimmers' burst-and-coast, and
        // for the same reason: a stepped target reads as a twitch, an integrated one as a start.
        boolean moving = dwellPhase(i) < CRAWL_DUTY;
        float target = moving ? 1f : 0f;
        float rate = moving ? CRAWL_ATTACK_RATE : CRAWL_STOP_RATE;
        burstDrive[i] += (target - burstDrive[i]) * rate * dt;
        advanceShape(i, moving, CRAWL_SHAPE_ATTACK_RATE, CRAWL_SHAPE_DECAY_RATE, dt);

        // Heading: band-limited wander, overridden by obstacle avoidance when the way is blocked.
        float k = CRAWL_WANDER_SIGMA * (float) Math.sqrt(dt) * SQRT3;
        wanderState[i] += -wanderState[i] * CRAWL_WANDER_THETA * dt + k * nextSignedUnit(i);
        wanderState[i] = SimMath.clamp(wanderState[i], -WANDER_CLAMP, WANDER_CLAMP);

        float yaw = yawDeg[i];
        float turn = wanderState[i] * CRAWL_TURN_RATE * turnScale[i];
        if (!clearAhead(i, yaw)) {
            boolean left = clearAhead(i, yaw + CRAWL_PROBE_SPREAD);
            boolean right = clearAhead(i, yaw - CRAWL_PROBE_SPREAD);
            if (left != right) {
                turn = left ? CRAWL_TURN_RATE : -CRAWL_TURN_RATE;
            } else {
                // Boxed in (a corner, or a gap narrower than the probe spread): turn steadily in
                // this fish's own preferred direction until something opens up. Seeded rather than
                // fixed, so two crabs in one corner don't mirror each other forever.
                turn = (unitFromHash(seeds[i], 8) < 0f ? -2f : 2f) * CRAWL_TURN_RATE;
            }
        }
        yaw = wrapDeg(yaw + turn);
        yawDeg[i] = yaw;

        float yr = (float) Math.toRadians(yaw);
        float dirL = (float) Math.cos(yr);
        float dirD = -(float) Math.sin(yr);

        float drive = CRAWL_SPEED * burstDrive[i] * speedScale[i];
        float vl = dirL * drive;
        float vd = dirD * drive;

        // Footprint separation, in 2D and at the scale of the shells rather than the bodies — this
        // is what stops crawlers stacking on the same patch of sand.
        float radius = CRAWL_FOOTPRINT * lengths[i];
        for (int j = 0; j < count; j++) {
            if (j == i || locomotion[j] != Locomotion.BENTHIC) continue;
            float dl = posL[i] - posL[j];
            float dd = posD[i] - posD[j];
            float distSq = dl * dl + dd * dd;
            float want = radius + CRAWL_FOOTPRINT * lengths[j];
            if (distSq >= want * want || distSq <= 1e-8f) continue;
            float dist = (float) Math.sqrt(distSq);
            float push = (want - dist) / want * CRAWL_SEPARATION_SPEED;
            vl += dl / dist * push;
            vd += dd / dist * push;
        }

        float nextL = posL[i] + vl * dt;
        float nextD = posD[i] + vd * dt;
        if (standable(nextL, nextD)) {
            posL[i] = nextL;
            posD[i] = nextD;
        } else {
            // The probe missed something — a diagonal clip past a corner, or a neighbour's push
            // toward an obstacle. Refusing the move is what makes the invariant unconditional.
            vl = 0f;
            vd = 0f;
        }
        posY[i] = floorHeightAt(posL[i], posD[i]);

        velL[i] = vl;
        velD[i] = vd;
        velY[i] = 0f;
        speed[i] = (float) Math.sqrt(vl * vl + vd * vd);
        bank[i] = 0f;
    }

    /** Where this fish sits in its own scuttle-and-pause cycle, in [0, 1). */
    private float dwellPhase(int i) {
        float period = CRAWL_DWELL_SECONDS * (1f + BURST_PERIOD_JITTER * unitFromHash(seeds[i], 6));
        float offset = (unitFromHash(seeds[i], 7) + 1f) * 0.5f;
        float phase = (simTick * t.dt()) / period + offset;
        return phase - (float) Math.floor(phase);
    }

    /** Whether the floor a probe-length ahead on this bearing can be stood on. */
    private boolean clearAhead(int i, float yaw) {
        float yr = (float) Math.toRadians(yaw);
        return standable(posL[i] + (float) Math.cos(yr) * CRAWL_PROBE,
                posD[i] - (float) Math.sin(yr) * CRAWL_PROBE);
    }

    /**
     * Whether a crawler may occupy this horizontal position: the floor is walkable there (sand,
     * not an obstacle and not thin air) and it is inside the domain's horizontal extent. The
     * vertical extent deliberately does not apply — the floor lies below the swim volume.
     */
    private boolean standable(float l, float d) {
        return l >= domain.minLateral() + CRAWL_EDGE_MARGIN && l <= domain.maxLateral() - CRAWL_EDGE_MARGIN
                && d >= domain.minDepth() + CRAWL_EDGE_MARGIN && d <= domain.maxDepth() - CRAWL_EDGE_MARGIN
                && !Float.isNaN(floorHeightAt(l, d));
    }

    /**
     * Floor height under a point given in the sim's local frame.
     *
     * <p>The transform matters and is easy to miss: a single tank's local lateral/depth axes are
     * rotated by the placement yaw the tank recorded from the player who placed it (the same
     * {@code cosR}/{@code sinR} {@link #interpolate} applies), but the sand and the cosmetic grid
     * standing on it are <i>block</i>-aligned and do not rotate with it. So the floor is indexed
     * in the block's own frame, and local coordinates are rotated into it here. The group engine
     * runs unrotated, where this is the identity.
     */
    private float floorHeightAt(float l, float d) {
        return domain.floor().heightAt(l * cosR + d * sinR, -l * sinR + d * cosR);
    }

    /**
     * Picks a walkable spot for a crawler at rebuild, writing {@code out[0..1]} = lateral, depth.
     *
     * <p>Draws come from the fish's own seed rather than the shared scatter {@code rng}, which
     * matters more than it looks: adding a crab to a tank therefore does not shift the random
     * stream, and every other fish in that tank still scatters to bit-identical positions.
     * Candidates are rejected against the floor and against the crawlers already placed, so a
     * colony spaces itself out instead of piling into one corner.
     */
    private void placeOnFloor(FishSpec spec, long seed, int upTo, float[] out) {
        float lo = domain.minLateral() + CRAWL_EDGE_MARGIN;
        float span = (domain.maxLateral() - CRAWL_EDGE_MARGIN) - lo;
        float loD = domain.minDepth() + CRAWL_EDGE_MARGIN;
        float spanD = (domain.maxDepth() - CRAWL_EDGE_MARGIN) - loD;
        float want = CRAWL_FOOTPRINT * spec.length();

        for (int attempt = 0; attempt < 48; attempt++) {
            float l = lo + (unitFromHash(seed, 100 + attempt * 2L) + 1f) * 0.5f * span;
            float d = loD + (unitFromHash(seed, 101 + attempt * 2L) + 1f) * 0.5f * spanD;
            if (!standable(l, d)) continue;
            if (attempt < 32 && !farEnoughOnFloor(l, d, want, upTo)) continue;
            out[0] = l;
            out[1] = d;
            return;
        }

        // Exhaustive fallback: the first walkable cell. A crawler that reaches this is in a
        // near-fully-obstructed tank, and standing somewhere legal beats standing in a wall.
        for (float l = lo; l <= lo + span; l += FloorField.CELL_SIZE) {
            for (float d = loD; d <= loD + spanD; d += FloorField.CELL_SIZE) {
                if (standable(l, d)) {
                    out[0] = l;
                    out[1] = d;
                    return;
                }
            }
        }
        out[0] = lo + span * 0.5f;
        out[1] = loD + spanD * 0.5f;
    }

    /**
     * Whether the rebuild puts this class on the sand rather than in the water. It keys on the
     * <b>declared</b> class, never the gated one: a creature the gate demoted still belongs on the
     * floor — the renderer stopped pinning these poses' Y, so a demoted crab or eel left in
     * mid-water would simply hover there.
     */
    private static boolean floorPlaced(Locomotion locomotion) {
        return locomotion == Locomotion.BENTHIC || locomotion == Locomotion.ANCHORED;
    }

    private boolean farEnoughOnFloor(float l, float d, float want, int upTo) {
        for (int j = 0; j < upTo && j < count; j++) {
            if (!floorPlaced(locomotion[j])) continue;
            float dl = l - posL[j];
            float dd = d - posD[j];
            float other = want + CRAWL_FOOTPRINT * lengths[j];
            if (dl * dl + dd * dd < other * other) return false;
        }
        return true;
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
            renderBank[i] = SimMath.lerp(partialTick, prevBankSmooth[i], bankSmooth[i]);
            renderShape[i] = SimMath.lerp(partialTick, prevShapeDrive[i], shapeDrive[i]);
            if (planar || continuousYaw()) {
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

    // ── Drift (docs/fish-sim-locomotion.md §3.2) ───────────────────────────────────────────────

    /**
     * One step of the passive drift. Like the crawl and unlike the swimmers, this commands
     * velocity directly rather than integrating a force: a jellyfish has essentially no inertia of
     * its own worth modelling — what looks like momentum is the water, and the water is already in
     * the wander's correlation time.
     *
     * <p>The two axes are deliberately unlike each other. Horizontally there is <b>no forward
     * drive at all</b>: position moves only by a slow, band-limited wander, which reads as being
     * carried by a current. Vertically there is a pulse-and-sink cycle — the burst-and-coast
     * envelope again, on a new axis and far more asymmetric — and that is the axis a viewer
     * actually reads as the animal being alive.
     *
     * <p>No alignment: jellyfish do not school, and a drifter is not in {@code swimmers[]} so the
     * shoal never aligns to one either. Same-species cohesion is weak enough to gather a loose
     * smack without pulling it into a ball.
     */
    private void stepDrift(int i) {
        float dt = t.dt();

        // Bell pulse. Integrated rather than stepped, for the reason advanceBurst documents at
        // length: holding a target while a gain chases it is what makes the onset read as a
        // contraction instead of a twitch.
        boolean pulsing = pulsePhase(i) < DRIFT_PULSE_DUTY;
        float target = pulsing ? 1f : 0f;
        float rate = pulsing ? DRIFT_PULSE_ATTACK_RATE : DRIFT_PULSE_DECAY_RATE;
        burstDrive[i] += (target - burstDrive[i]) * rate * dt;
        advanceShape(i, pulsing, DRIFT_SHAPE_ATTACK_RATE, DRIFT_SHAPE_DECAY_RATE, dt);

        // Horizontal advection: two independent OU processes, one per axis. Independent rather
        // than a wandering heading (the crawl's formulation) because a drifter has no heading to
        // wander — it is not pointing where it is going, it is being carried.
        float k = DRIFT_WANDER_SIGMA * (float) Math.sqrt(dt) * SQRT3;
        wanderState[i] += -wanderState[i] * DRIFT_WANDER_THETA * dt + k * nextSignedUnit(i);
        wanderStateY[i] += -wanderStateY[i] * DRIFT_WANDER_THETA * dt + k * nextSignedUnit(i);
        wanderState[i] = SimMath.clamp(wanderState[i], -WANDER_CLAMP, WANDER_CLAMP);
        wanderStateY[i] = SimMath.clamp(wanderStateY[i], -WANDER_CLAMP, WANDER_CLAMP);

        // Facing: a third, independent OU process rather than a reuse of either advection axis —
        // see DRIFT_SPIN_THETA. It drives baseRotations directly rather than yawDeg, which stays
        // untouched (see the note on bank below): baseRotations isn't wrap-aware interpolated
        // between ticks, and a tumble this slow doesn't need to be.
        float kSpin = DRIFT_SPIN_SIGMA * (float) Math.sqrt(dt) * SQRT3;
        spinState[i] += -spinState[i] * DRIFT_SPIN_THETA * dt + kSpin * nextSignedUnit(i);
        spinState[i] = SimMath.clamp(spinState[i], -WANDER_CLAMP, WANDER_CLAMP);
        baseRotations[i] += spinState[i] * DRIFT_SPIN_RATE_DEG * dt;

        float drive = DRIFT_SPEED * speedScale[i];
        float vl = wanderState[i] * drive;
        float vd = wanderStateY[i] * drive;
        float vy = (DRIFT_PULSE_SPEED * burstDrive[i] - DRIFT_SINK_SPEED) * speedScale[i];

        // Bell separation and weak same-species cohesion, in one pass. Drifters interact only with
        // each other here; every other class already sees them through the swimmers' separation
        // term, which scans all fish regardless of class.
        float radius = DRIFT_FOOTPRINT * lengths[i];
        float cohL = 0f, cohY = 0f, cohD = 0f;
        int cohCount = 0;
        for (int j = 0; j < count; j++) {
            if (j == i || locomotion[j] != Locomotion.DRIFT) continue;
            float dl = posL[i] - posL[j];
            float dy = posY[i] - posY[j];
            float dd = posD[i] - posD[j];
            float distSq = dl * dl + dy * dy + dd * dd;
            if (distSq <= 1e-8f) continue;

            float want = radius + DRIFT_FOOTPRINT * lengths[j];
            if (distSq < want * want) {
                float dist = (float) Math.sqrt(distSq);
                float push = (want - dist) / want * DRIFT_SEPARATION_SPEED;
                vl += dl / dist * push;
                vy += dy / dist * push;
                vd += dd / dist * push;
            }
            if (species[j] == species[i] && distSq < DRIFT_COHESION_RADIUS * DRIFT_COHESION_RADIUS) {
                cohL -= dl; cohY -= dy; cohD -= dd;
                cohCount++;
            }
        }
        if (cohCount > 0) {
            vl += cohL / cohCount * DRIFT_COHESION_SPEED;
            vy += cohY / cohCount * DRIFT_COHESION_SPEED;
            vd += cohD / cohCount * DRIFT_COHESION_SPEED;
        }

        // Soft containment, and the only thing keeping the vertical cycle centred: whatever bias
        // survives between the pulse and the sink is cancelled here rather than by tuning the two
        // against each other, which would be a balance that a domain of a different height breaks.
        domain.avoidance(posL[i], posY[i], posD[i], DRIFT_WALL_MARGIN, DRIFT_WALL_MARGIN_VERTICAL,
                avoidScratch);
        vl += avoidScratch[0] * DRIFT_AVOID_SPEED;
        vy += avoidScratch[1] * DRIFT_AVOID_SPEED;
        vd += avoidScratch[2] * DRIFT_AVOID_SPEED;

        posL[i] += vl * dt;
        posY[i] += vy * dt;
        posD[i] += vd * dt;
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

        velL[i] = vl;
        velY[i] = vy;
        velD[i] = vd;
        speed[i] = (float) Math.sqrt(vl * vl + vy * vy + vd * vd);
        // yawDeg is still the pose's business, not the engine's, for a drifter: it has no travel
        // heading to speak of, only the tumble above. bank is likewise left alone — a jellyfish
        // doesn't lean into a turn it isn't making.
        bank[i] = 0f;
    }

    // ── Anchored (docs/fish-sim-locomotion.md §3.4) ────────────────────────────────────────────

    /**
     * One step of the burrow. The cheapest model here by a wide margin, and deliberately so: an
     * eel's footprint was chosen at rebuild by the same floor scatter a crawler gets, and it never
     * moves again — {@code AnchoredTest} asserts it bitwise. All that is stepped is the
     * retract/emerge envelope, which the renderer draws as the animal pulling down into the sand.
     *
     * <p>The trigger is a plain scan rather than a spatial-index query, following the crawl's and
     * the drift's separation passes: the classes that scan are the ones with few members, and an
     * eel colony is the smallest of them. It also keeps the anchored radius out of
     * {@link #interactionRadius}, whose contract — a fish the grid skips contributes exactly zero
     * — would otherwise have to grow to cover it.
     *
     * <p>Only something that <i>moves</i> startles an eel: another anchored creature parked half a
     * block away is scenery, and would otherwise hold every eel in a colony permanently retracted.
     */
    private void stepAnchored(int i) {
        float dt = t.dt();

        // The startle is edge-triggered, then habituates. Two independent conditions have to be
        // true for an eel to duck: it must be armed (the watcher has been away since it last
        // reacted) and out of its refractory. The first is what makes a player who walks up and
        // stays a single event; the second is what stops one who paces in and out being a strobe.
        boolean near = watcherWithin(i, ANCHOR_WATCHER_RADIUS);
        // Positive evidence only — see anchorArmed. "Not near" is not the same statement as "the
        // watcher left", and the difference is a colony that ducks at someone standing still.
        if (watcherPresent && !watcherWithin(i, ANCHOR_REARM_RADIUS)) anchorArmed[i] = true;

        boolean hiding;
        if (anchorTimer[i] > 0f) {
            anchorTimer[i] -= dt;
            if (anchorTimer[i] <= 0f) {
                anchorTimer[i] = -ANCHOR_REFRACTORY_SECONDS
                        * (1f + ANCHOR_TIMING_JITTER * unitFromHash(seeds[i], 12));
            }
            hiding = true;
        } else if (anchorTimer[i] < 0f) {
            anchorTimer[i] = Math.min(0f, anchorTimer[i] + dt);
            hiding = false;
        } else {
            hiding = near && anchorArmed[i];
            if (hiding) {
                anchorArmed[i] = false;
                anchorTimer[i] = ANCHOR_HIDE_SECONDS
                        * (1f + ANCHOR_TIMING_JITTER * unitFromHash(seeds[i], 11));
            }
        }
        advanceShape(i, hiding, ANCHOR_RETRACT_RATE, ANCHOR_EMERGE_RATE, dt);

        // Nothing else moves, and saying so explicitly matters: velocity feeds the animation
        // coupling, and an eel that kept a stale speed from its scatter would beat a tail it does
        // not have.
        velL[i] = velY[i] = velD[i] = 0f;
        speed[i] = 0f;
        bank[i] = 0f;
    }

    /**
     * Whether the watcher is next to this burrow right now.
     *
     * <p><b>Only</b> the watcher, and never merely "the watcher is not known to be here": an
     * absent watcher is absent, which is why this answers a question about a radius rather than
     * about nearness, and the caller asks it twice with two of them.
     *
     * <p><b>Only</b> the watcher, in the other sense too. Nothing inside the tank startles an eel, which is a design
     * statement rather than an omission: the fish an eel shares a tank with are its neighbours,
     * it sees them all day, and a reaction to them is either constant (in a stocked tank) or
     * arbitrary (in an empty one). What a garden eel visibly reacts to is the large animal that
     * has just leaned over its burrow — so that is the only thing this asks about, and each eel
     * asks it about its <i>own</i> burrow, so a colony spread down a long aquarium reacts where
     * the watcher actually is.
     */
    private boolean watcherWithin(int i, float radius) {
        if (!watcherPresent) return false;
        float dl = posL[i] - watcherL;
        float dy = posY[i] - watcherY;
        float dd = posD[i] - watcherD;
        return dl * dl + dy * dy + dd * dd < radius * radius;
    }

    /** Where this fish sits in its own pulse-and-sink cycle, in [0, 1). */
    private float pulsePhase(int i) {
        float period = DRIFT_PULSE_SECONDS * (1f + BURST_PERIOD_JITTER * unitFromHash(seeds[i], 9));
        float offset = (unitFromHash(seeds[i], 10) + 1f) * 0.5f;
        float phase = (simTick * t.dt()) / period + offset;
        return phase - (float) Math.floor(phase);
    }

    private static float verticalHalf(float yRange) {
        return Math.max(0.1f, yRange * 0.5f);
    }
}
