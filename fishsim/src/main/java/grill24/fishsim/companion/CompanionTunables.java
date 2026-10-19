package grill24.fishsim.companion;

/**
 * Every companion constant in one place. Unlike the tank's {@code Tunables} record this is a plain
 * mutable holder: the viewer's sliders write straight into the instance a running agent reads,
 * and nothing here is locked by a golden. Tests and the mod use {@link #defaults()}.
 *
 * <p>Units: blocks, seconds, degrees. Speeds are blocks per second, so they compare directly with
 * a player's: walking is 4.3, sprinting 5.6.
 */
public final class CompanionTunables {

    // ── Body (docs/fish-companions-sim-plan.md §3.4) ───────────────────────────────────────────

    /** Unhurried swimming speed: loitering, moving between spots. */
    public float cruiseSpeed = 1.1f;
    /** The fastest a catch-up swim asks for — above a sprint, so a fish can close a gap. */
    public float catchUpSpeed = 7.5f;
    /** Hard ceiling on speed. */
    public float maxSpeed = 9.0f;
    /** How hard the body pursues the desired velocity, 1/s. */
    public float steeringGain = 2.5f;
    /** Forward acceleration cap (speeding up and slowing down), blocks/s². */
    public float maxForce = 6.0f;
    /** Sideways acceleration cap in a turn (v·ω), blocks/s² — what makes a fast swim arc wide. */
    public float maxLateralAccel = 14f;
    /** Jerk cap, blocks/s³ — neither acceleration can change faster than this. */
    public float maxJerk = 40f;
    /** Turn-rate cap, degrees per tick (the tank's 7). */
    public float turnRateDegPerTick = 7f;
    public float pitchMaxDeg = 40f;
    public float pitchRateDegPerTick = 4f;
    public float bankMaxDeg = 18f;

    /** Ornstein–Uhlenbeck heading noise (the tank's GROUP values). */
    public float wanderSigma = 0.8f;
    public float wanderTheta = 0.9f;
    /** How strongly that noise bends the course, as a fraction of the commanded speed. */
    public float wanderAuthority = 0.35f;
    /** The same noise on the vertical axis, blocks/s at full signal. */
    public float wanderVertical = 0.18f;

    /** Burst-and-coast. */
    public float burstPeriodSeconds = 4.0f;
    public float burstDuty = 0.45f;
    public float burstThrust = 1.4f;
    public float burstCoast = 0.45f;

    /** Whisker steering around blocks (ablation switch; see Body.clearDirection). */
    public boolean whiskers = true;
    /** Soft zone around blocks, and the away-speed at full proximity. */
    public float wallMargin = 0.6f;
    public float wallAvoidSpeed = 2.5f;
    /** Soft zone around the owner's body. */
    public float ownerMargin = 0.45f;
    public float ownerAvoidSpeed = 2.5f;

    // ── GLIDE body (§9; the tank's Tunables.GLIDE carried into air) ────────────────────────────

    /** A ray cruises a little faster than a swimmer (the tank's patrol 0.14 vs 0.10)... */
    public float glideCruiseSpeed = 1.5f;
    /**
     * ...but cannot whip round at speed. The tank caps a ray at a flat 2.2°/tick; a companion has to
     * follow its owner up stairwells, so here the cap is on sideways acceleration instead (ω = a/v):
     * 2.4°/tick at 3 b/s, under 1°/tick at catch-up speed — wider than the tank — while a slow ray
     * may pivot up to this rate. Measured over 8 seeds against the flat 2.2: walk catch-up 50% → 23%,
     * stairwell farthest 23.7 s → 15 s, cellar 32.8 s → 13.6 s.
     */
    public float glideTurnRateDegPerTick = 4.5f;
    /** Sideways acceleration cap in a turn, blocks/s²: what makes a fast ray arc wide. */
    public float glideLateralAccel = 2.5f;
    public float glidePitchMaxDeg = 25f;
    public float glidePitchRateDegPerTick = 1.5f;
    /** Rays bank hard into their slow turns. */
    public float glideBankMaxDeg = 28f;
    /** The tank's GLIDE wander: a quarter of the shoal's amplitude, twice its correlation time. */
    public float glideWanderSigma = 0.25f;
    public float glideWanderTheta = 0.5f;
    /** Wingbeat then glide: the tank's GLIDE burst-and-coast. */
    public float glideBurstPeriodSeconds = 6.0f;
    public float glideBurstDuty = 0.30f;
    public float glideBurstThrust = 1.5f;
    public float glideBurstCoast = 0.35f;
    /**
     * How close blocks must come before a glider tucks in toward a swimmer's turning limits,
     * blocks beyond its radius; 0 never tucks.
     */
    public float glideTuckMargin = 1.2f;
    /** Ride height above the owner's chest, blocks — just over their head; lowered under a ceiling. */
    public float glideRideHeight = 1.3f;
    /** Comfort band scale: a glider ranges wider, so its passes are long arcs, not tight circles. */
    public float glideBandScale = 1.4f;

    // ── DRIFT body (§9; DriftBody) ──────────────────────────────────────────────────────────────

    /** Unhurried drifting pace, blocks/s — the plan's "weak drive". */
    public float driftCruiseSpeed = 0.7f;
    /**
     * The fastest it asks to go, blocks/s: above a sprint. Owner, 2026-10-11, choosing between
     * letting it trail, towing it, and this: "stronger pulses" — it keeps swimming itself, pulsing
     * faster and harder when it falls behind, while idle stays calm. At 5.5 it fell 10–16 blocks
     * behind a walk.
     */
    public float driftTopSpeed = 7.0f;
    /** One pulse-and-coast cycle when idle (the tank's 4.5 s, quickened for air), and flat out. */
    public float driftPulseSeconds = 2.8f;
    public float driftPulseFastSeconds = 0.8f;
    /** Share of the cycle spent squeezing: at the fastest, a 6-tick stroke, not a twitch. */
    public float driftPulseDuty = 0.35f;
    /**
     * Thrust at the top of the strongest squeeze, blocks/s². Sized so a cycle's mean thrust,
     * 32 × 0.35 × ½ = 5.6, holds about 7.6 b/s against the drag below — above the top speed asked for.
     */
    public float driftThrust = 32f;
    /** Drag, 1/s: {@code driftDrag + driftDragPerSpeed · speed} — light idling, heavier fast. */
    public float driftDrag = 0.35f;
    public float driftDragPerSpeed = 0.05f;
    /** Passive sink between pulses, blocks/s². */
    public float driftSink = 0.6f;
    /** How hard it corrects toward the velocity it wants, 1/s. */
    public float driftResponse = 1.5f;
    /** How far the bell may lean from upright, degrees (short of horizontal), and how fast it leans. */
    public float driftTiltMaxDeg = 85f;
    public float driftTiltRateDegPerTick = 3f;
    /** Ride height above the owner's chest, blocks: shoulder to head height. */
    public float driftRideHeight = 0.35f;

    // Phase 1 of the drifter rework (docs/fish-companions-drift.md §3, §4, §13): each its own switch,
    // for equal-ground A/B. All on is the adopted body; all off is the first cut, bitwise.
    /** Pulse again as soon as it needs to, instead of waiting out a slow idle cycle. */
    public boolean driftRetime = true;
    /** Size each stroke to land at the asked velocity, and weaken one fired off the line of need. */
    public boolean driftArrive = true;
    /** How much of the velocity gap one cycle closes when strokes are sized to arrive. */
    public float driftArriveGain = 0.9f;
    /** The web-flare brake: extra drag, 1/s, extra sink, blocks/s², and the excess speed that flares it fully, blocks/s. */
    public boolean driftFlare = true;
    public float driftFlareDrag = 2.5f;
    public float driftFlareSink = 0.8f;
    public float driftFlareFull = 2.0f;
    /**
     * No climbing it didn't ask for: a stroke is cut so it leaves the bell rising no faster than it
     * wants to (plus half a cycle's sink and a small margin). A bell can't push down, so lift it
     * didn't want — a stroke fired while still leaning round — takes seconds of sinking to undo.
     */
    public boolean driftNoClimb = true;
    /**
     * Braking by room: no stroke carries it faster than its flare can stop in the open space ahead,
     * and it flares when it is already going faster than that. Needs {@link #driftFlare}.
     */
    public boolean driftRoomBrake = true;
    /** Extra tilt rate at rest, as a multiple of the base rate, fading out by {@link #driftSlowPivotSpeed}; 0 off. */
    public float driftSlowPivot = 0f;
    public float driftSlowPivotSpeed = 2.0f;
    /** The slow-speed pivot only while it reverses: righting through upright and leaning out the other way. */
    public boolean driftPivotSwingOnly = false;
    /**
     * Holds its height while it swings round: near upright a stroke may always be as big as holding
     * height against the sink takes, even when where it wants to go is off to the side (and so out of
     * the arrive gate). Without it a reversing bell sinks to the ground on its way through upright.
     */
    public boolean driftHoldHeight = true;

    // ── Mind (§3.3) ────────────────────────────────────────────────────────────────────────────

    /** Comfort band around the owner's chest, blocks. */
    public float bandInner = 0.9f;
    /** Outer edge while the owner moves... */
    public float bandOuter = 2.0f;
    /** ...and how far it widens after the owner has stood still for a while. */
    public float bandOuterIdle = 3.2f;
    /** Beyond this, staying near the owner is urgent. */
    public float farGap = 5.0f;
    /** Beyond this, no swim can recover: fade out and back in near the owner. */
    public float leashGap = 24f;

    /** Unseen time before wanting to be seen, seconds: a fresh threshold is drawn in this range each time. */
    public float attentionMinSeconds = 7f;
    public float attentionMaxSeconds = 20f;
    /** How long the urge takes to build from nothing to full once past the threshold. */
    public float attentionRampSeconds = 4f;

    /** How long the fish lingers at a loiter spot, seconds. */
    public float dwellMinSeconds = 1.5f;
    public float dwellMaxSeconds = 5.0f;

    /** Time between deciding to change where it rides while escorting, seconds. */
    public float escortChangeMinSeconds = 7f;
    public float escortChangeMaxSeconds = 16f;
    /** Lateral riding distance while escorting. */
    public float escortLateral = 1.25f;

    public static CompanionTunables defaults() {
        return new CompanionTunables();
    }

    public CompanionTunables copy() {
        CompanionTunables c = new CompanionTunables();
        for (var f : CompanionTunables.class.getFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            try {
                f.set(c, f.get(this));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        return c;
    }
}
