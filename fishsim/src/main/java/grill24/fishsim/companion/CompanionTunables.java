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
