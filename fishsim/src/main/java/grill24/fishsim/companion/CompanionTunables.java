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

    // ── Body (docs/fish-companions.md) ───────────────────────────────────────────

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
    /**
     * How fast a drifter's drawn facing may turn, degrees per tick. The bell is round, so its lean can
     * swap sides in a tick when it swings through upright; the art isn't, so the facing follows the
     * lean at this capped rate instead of flipping 180° (docs/fish-companions-drift.md).
     */
    public float driftFaceRateDegPerTick = 4f;
    /** Ride height above the owner's chest, blocks: shoulder to head height. */
    public float driftRideHeight = 0.35f;

    /** Fin acceleration, blocks/s²: enough to hover and sidestep, too weak for fast travel. */
    public float jetFinThrust = 2f;
    /** Squid mantle turn cap, degrees/tick; fins turn faster while hovering. */
    public float jetTurnDegPerTick = 24f;
    public float jetSlowTurnDegPerTick = 30f;
    /** Travel jets: cycle seconds and squeeze fraction, independent of the bell's slow idle. */
    public float jetPulseSeconds = 0.35f;
    public float jetPulseDuty = 0.6f;
    public float jetThrust = 28f;
    /** Extra forward ride offset for squid, blocks; 0 uses the bell's shoulder station. */
    public float jetRideAhead = 2.2f;
    /** Extra room around a travelling squid, blocks, for its faster corrections. */
    public float jetRoomMargin = 0.2f;
    /** Travel prediction, seconds, only when the whole catch-up lane is clear. */
    public float jetFollowLead = 0.7f;
    /** Largest steering error at mantle onset; fins finish lining the mantle up first. */
    public float jetAlignDeg = 8f;
    /** Time to open the counter fins before full stopping drag is available, seconds. */
    public float jetBrakeDelay = 0.2f;
    /** Relative pace of a squid's attention pass, blocks/s; 0 restores the bell's pass pace. */
    public float jetViewPassSpeed = 2.5f;

    // Phase 3 of the drifter rework (docs/fish-companions-drift.md, §15): the man o' war's bob.
    /** Phase 3: false restores the bell body for the bob style, bitwise. */
    public boolean driftBob = true;
    /** One bob (rise and fall) idling, and flat out, seconds. */
    public float bobPeriodSeconds = 1.8f;
    public float bobPeriodFastSeconds = 0.7f;
    /** How far the float rises and falls about its ride height, blocks: idling, and flat out. */
    public float bobHeight = 0.05f;
    public float bobHeightFast = 0.1f;
    /**
     * The hardest shove a downstroke gives along the sail, blocks/s². Over a cycle that is
     * 14 × ½ (the downstroke) × ½ (the envelope) = 3.5 b/s² mean, which holds about 5.6 b/s against
     * the drift drag: a walk with room to catch up, a sprint only just.
     */
    public float bobThrust = 14f;
    /** The float's height hold: how fast it closes on the climb it wants, 1/s, its strongest push, b/s², and its fastest climb or dive, b/s. */
    public float bobLiftResponse = 8f;
    public float bobLift = 12f;
    public float bobClimbSpeed = 3f;
    /** How fast the sail turns, degrees per tick. */
    public float bobSailTurnDegPerTick = 8f;
    /** The most it leans forward into a shove or rocks into a turn, degrees: it stays near upright. */
    public float bobTiltMaxDeg = 20f;
    /** A downstroke shoves in full only with the sail within this of the way it needs to go, degrees; none past twice it. */
    public float bobAlignDeg = 30f;
    /**
     * The fastest it travels on average, blocks/s, whatever it is asked (0: no cap). Above a walk and
     * below a sprint (spec §6): a sprint leaves it behind, and the fade is the backstop.
     */
    public float bobTopSpeed = 5.5f;
    /** Dips into a downstroke at once when it needs a much bigger shove than its last, instead of riding out the bob. */
    public boolean bobRetime = true;
    /** Extra forward ride offset for the man o' war, blocks (the squid's jetRideAhead); 0 uses the bell's station. */
    public float bobRideAhead = 1.5f;
    /** The escort station turns at the sail's rate instead of a bell's 6°/tick. */
    public boolean bobMindTurn = true;

    // The bob's calm idle (docs/fish-companions-drift.md), from the owner after Phase 3: "not chill
    // enough - it moves kind of frenetically". By a still owner the sail swung round ~10 times a minute
    // (176° each) chasing station-keeping corrections under 0.3 b/s. They act as it settles (the
    // bell's calm, §13.7), so travel is unchanged.
    /** false restores the Phase 3 bob, bitwise. */
    public boolean bobCalm = true;
    /**
     * At full calm, a gentle drift (current and tentacle drag) makes corrections up to this, blocks/s²,
     * in any direction, without turning the sail: the squid's fins (jetFinThrust) for a float.
     */
    public float bobCalmDrift = 1.0f;
    /**
     * At full calm the sail turns only for what the drift can't cover beyond this, blocks/s² (it keeps
     * turning until that falls under half), and at most {@link #bobCalmSailTurnDegPerTick}.
     */
    public float bobCalmSailNeed = 0.6f;
    public float bobCalmSailTurnDegPerTick = 2f;
    /** Its slowest bob at full calm, seconds (the bell's driftIdlePulseSeconds). */
    public float bobIdlePeriodSeconds = 4.5f;
    /** At full calm, its rock into a turn as a share of the travelling rock. */
    public float bobCalmBank = 0.4f;
    /** Drawn only: at full calm the art turns no faster than this, degrees per tick; travelling it is the sail. */
    public float bobCalmFaceDegPerTick = 1.5f;

    // Phase 1 of the drifter rework (docs/fish-companions-drift.md, §4, §13).
    /** How much of the velocity gap one cycle closes: each stroke is sized to arrive. */
    public float driftArriveGain = 0.9f;
    /** The web-flare brake: extra drag, 1/s, extra sink, blocks/s², and the excess speed that flares it fully, blocks/s. */
    public float driftFlareDrag = 2.5f;
    public float driftFlareSink = 0.8f;
    public float driftFlareFull = 2.0f;

    // The calm idle (docs/fish-companions-drift.md), from the owner's look at Phase 1: "too
    // hyperactive when the player is idling". They act only on a drifter, and only while it idles by
    // a still owner (LOITER, SETTLE) — or, for the lean, while it is asked to go slowly. A calm drifter's
    // idle ask doesn't rise and fall with its own pulse.
    /** Pace of a drifter's idle moves between spots, as a multiple of its cruise speed. */
    public float driftIdlePace = 0.5f;
    /** How long a drifter lingers at each idle spot, as a multiple of the swimmers' dwell. */
    public float driftIdleDwell = 2f;
    /**
     * The lean of an idling bell asked to go slowly: at most this, degrees, at no asked speed, rising to the
     * full {@link #driftTiltMaxDeg} by {@link #driftCalmTiltSpeed} blocks/s. A jelly drifting a little
     * way hangs nearly upright and lets its strokes carry it; only hurrying lays it over. At or above
     * the tilt limit it is off.
     */
    public float driftCalmTiltDeg = 15f;
    public float driftCalmTiltSpeed = 1.5f;
    /**
     * At rest by a still owner a bell is near neutrally buoyant: its sink is this share of
     * {@link #driftSink}, so it needs only small strokes, seldom, to hang where it is (1 off). Eased
     * in over {@link #driftIdleCalmSeconds} once it idles, and out four times faster when it doesn't.
     */
    public float driftIdleSink = 0.2f;
    /** Its slowest pulse cycle at rest, seconds. */
    public float driftIdlePulseSeconds = 4.5f;
    public float driftIdleCalmSeconds = 2f;

    // Owner feedback (docs/fish-companions-drift.md): "the flapjack can end up rotating a full
    // 90° ... tilt somewhat over in the horizontal direction to propel but not that far".
    /**
     * The lean of any bell asked to go slowly, calm or not: at most this, degrees, at no asked speed,
     * rising to the full {@link #driftTiltMaxDeg} by {@link #driftAskTiltSpeed} blocks/s. The calm
     * lean (§13.7) covered only idling, so showing off by a still owner laid it flat. At or above the
     * tilt limit it is off.
     */
    public float driftAskTiltDeg = 30f;
    public float driftAskTiltSpeed = 3.0f;
    /**
     * A bell whose need points level or down (it is braking, or correcting a fall) leans no further
     * than holding its asked velocity takes, plus this, degrees: it can't push down or back, so lying
     * over toward that need moves it nowhere useful.
     */
    public float driftBrakeTiltMarginDeg = 10f;

    // Owner feedback after Phase 1 (docs/fish-companions-drift.md): "it ends up inside me often
    // when I walk or sprint ahead and stop, the drifter's momentum carries it through into my body".
    /**
     * A drifter catching up heads abreast of its owner, this far to its own side, blocks, instead of a
     * block behind them on their trail: a bell can't sidestep, so whatever it carries past them when
     * they stop then goes past their side, not into their back.
     */
    public float driftOwnerAside = 1.2f;
    /** The clearance beyond its radius the spot abreast and the ways to it need, blocks. */
    public float driftOwnerAsideRoom = 0.4f;
    /** How far ahead, seconds of the owner's travel, the lane abreast must stay open; 0 off. */
    public float driftOwnerAsideAhead = 1.0f;
    /**
     * How long the room abreast must have held before it heads there, seconds; 0 at once. Between trunks
     * or at a door the room flickers, and swinging out and back each time grazed them; 1 s keeps the open
     * ground fix whole and leaves tight scenes as they were.
     */
    public float driftOwnerAsideSettle = 1.0f;

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

    /**
     * Free-swimming units of one party push apart in the water (D21: a predator apart from its prey's
     * shoal), as ground walkers do on the ground. Only a party with two swimming units has anything to do.
     */
    public boolean partySwimSeparation = true;
    /**
     * The predator scene (D21, G4): now and then, with the owner standing still, a party's predator
     * does a smug lap round them while its prey hides behind their head. Rare: the first after
     * {@link #predatorSceneFirstMinSeconds}..max, then {@link #predatorSceneGapMinSeconds}..max apart.
     */
    public float predatorSceneFirstMinSeconds = 60f;
    public float predatorSceneFirstMaxSeconds = 150f;
    public float predatorSceneGapMinSeconds = 240f;
    public float predatorSceneGapMaxSeconds = 480f;
    /**
     * Swimmers keep out of other players' bodies (their PLAYER points) as they do out of their own
     * owner's (party spec §28.6: two solo fish 1.5 blocks apart were inside the other player 6% of
     * the time, mixed parties 21%). Ground walkers already step round players' feet.
     */
    public boolean otherPlayerClearance = true;

    // Hand visits (owner feedback 2026-10-14: about one a minute, solo or party). HandClock: a party's
    // perchers share one clock, and turns go round, longest since its own last visit first.
    /** After any landing on a hand, the unseen timer waits this long before calling a fish there again. */
    public float handGapMinSeconds = 45f;
    public float handGapMaxSeconds = 75f;
    /** Ground beats (overtake, roadside) come this many times as soon, and as often, with hand visits rare. */
    public double groundBeatSooner = 2.0;

    // Look-down hand visits (owner feedback 2026-10-28: building, every glance at the feet called a
    // benthic up to the hand, ~7 a minute, 42% of the time on the hand; PositionSplitProbe).
    /** A benthic on the ground its owner can see when they look down stays there and turns to face them; false hops up as before. */
    public boolean lookDownFaceOnGround = true;
    /** After any landing on a hand, a look down can't call a fish there again for this long; 0 = no wait (as before). */
    public float lookDownGapSeconds = 30f;
    /** How long the owner must look down before a fish comes to the hand (0.75 before). */
    public float lookDownSeconds = 1.5f;

    /**
     * A perched anchored fish ducks only this long at a predator fish in its owner's hand, then
     * comes back up and watches it (owner feedback 2026-10-14: a lionfish in hand kept a garden eel
     * squashed ~14 s). Hostiles and cats still keep it ducked. 0 = duck for as long as it's wary.
     */
    public float anchoredPredatorDuckSeconds = 2f;

    /**
     * The party and hand-visit switches the game's dev command flips live (party spec Phase 3c), in
     * the order it lists them. Each is a public field of this class.
     */
    public static final java.util.List<String> DEV_SWITCHES = java.util.List.of(
            "partySwimSeparation", "otherPlayerClearance", "anchoredPredatorDuckSeconds", "bobCalm",
            "lookDownFaceOnGround", "lookDownGapSeconds", "lookDownSeconds");

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
