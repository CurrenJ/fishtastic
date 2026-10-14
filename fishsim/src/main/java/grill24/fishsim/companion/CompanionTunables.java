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
    /**
     * How fast a drifter's drawn facing may turn, degrees per tick. The bell is round, so its lean can
     * swap sides in a tick when it swings through upright; the art isn't, so the facing follows the
     * lean at this capped rate instead of flipping 180° (docs/fish-companions-drift.md §13.9).
     */
    public float driftFaceRateDegPerTick = 4f;
    /** Ride height above the owner's chest, blocks: shoulder to head height. */
    public float driftRideHeight = 0.35f;

    /** Phase 2: false restores the bell body for equal-seed squid comparisons. */
    public boolean driftJet = true;
    /** Fin acceleration, blocks/s²: enough to hover and sidestep, too weak for fast travel. */
    public float jetFinThrust = 2f;
    /** Squid mantle turn cap, degrees/tick; fins turn faster while hovering. */
    public float jetTurnDegPerTick = 24f;
    public float jetSlowTurnDegPerTick = 30f;
    /** Travel jets: cycle seconds and squeeze fraction, independent of the bell's slow idle. */
    public float jetPulseSeconds = 0.35f;
    public float jetPulseDuty = 0.6f;
    public float jetThrust = 28f;
    /** Cut unused jet thrust once fins have already closed the planned velocity gap. */
    public boolean jetArrive = true;
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
    /** Counter fins can brake during a mantle stroke; holding thrust never pays that braking drag. */
    public boolean jetCounterFinBrake = true;
    /** Escort and view-pass targets reorient with the squid's fins instead of a bell's slow yaw. */
    public boolean jetMindTurn = true;
    /** Relative pace of a squid's attention pass, blocks/s; 0 restores the bell's pass pace. */
    public float jetViewPassSpeed = 2.5f;

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
    /**
     * Braking by room ignores a block it already touches unless it comes closer (§13.12). Off, a bell
     * pressed to a tunnel wall or sitting on the floor reads no room anywhere and can't stroke off it.
     * False is the body before, bitwise.
     */
    public boolean driftRoomPastContact = true;
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

    // The calm idle (docs/fish-companions-drift.md §13.7), from the owner's look at Phase 1: "too
    // hyperactive when the player is idling". Each its own switch for equal-ground A/B; the defaults
    // are the adopted calm idle, and pace/dwell/sink 1, steady false, calm tilt 85 and idle pulse 0
    // together are the Phase 1 idle, bitwise. They act only on a drifter, and only while it
    // idles by a still owner (LOITER, SETTLE) — or, for the lean, while it is asked to go slowly.
    /** Pace of a drifter's idle moves between spots, as a multiple of its cruise speed. */
    public float driftIdlePace = 0.5f;
    /** How long a drifter lingers at each idle spot, as a multiple of the swimmers' dwell. */
    public float driftIdleDwell = 2f;
    /** Its idle ask doesn't rise and fall with its own pulse (the swimmer's burst surge, fed back). */
    public boolean driftIdleSteady = true;
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
    /** Its slowest pulse cycle at rest, seconds (0: the travelling {@link #driftPulseSeconds}). */
    public float driftIdlePulseSeconds = 4.5f;
    public float driftIdleCalmSeconds = 2f;
    /**
     * A calm bell strokes again at once when it is falling well faster than it wants, instead of
     * waiting out its long idle cycle — measured: out of a low swoop it fell 1.6 blocks onto the floor.
     */
    public boolean driftCalmCatch = true;

    // Owner feedback (docs/fish-companions-drift.md §13.11): "the flapjack can end up rotating a full
    // 90° ... tilt somewhat over in the horizontal direction to propel but not that far". Each its own
    // switch; ask tilt 85 and lean-into-brake true together are the body before, bitwise.
    /**
     * The lean of any bell asked to go slowly, calm or not: at most this, degrees, at no asked speed,
     * rising to the full {@link #driftTiltMaxDeg} by {@link #driftAskTiltSpeed} blocks/s. The calm
     * lean (§13.7) covered only idling, so showing off by a still owner laid it flat. At or above the
     * tilt limit it is off.
     */
    public float driftAskTiltDeg = 30f;
    public float driftAskTiltSpeed = 3.0f;
    /**
     * With this off, a bell whose need points level or down (it is braking, or correcting a fall)
     * leans no further than holding its asked velocity takes, plus {@link #driftBrakeTiltMarginDeg}: it
     * can't push down or back, so lying over toward that need moves it nowhere useful. True is the
     * body before.
     */
    public boolean driftLeanIntoBrake = false;
    public float driftBrakeTiltMarginDeg = 10f;

    // Owner feedback after Phase 1 (docs/fish-companions-drift.md §13.8): "it ends up inside me often
    // when I walk or sprint ahead and stop, the drifter's momentum carries it through into my body".
    /**
     * A drifter catching up heads abreast of its owner, this far to its own side, blocks, instead of a
     * block behind them on their trail: a bell can't sidestep, so whatever it carries past them when
     * they stop then goes past their side, not into their back. 0 off (the Phase 1 body, bitwise).
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
     * An explicit cap: a ground formation's leader walks no faster than its slowest member
     * (docs/fish-companions-party.md D10, §14). Off by default because D9 already delivers D10 by
     * construction: the smallest member leads, and a ground body's pace grows with its size, so the
     * leader is always the slowest walker and the cap never engages (bitwise identical on or off for
     * every smallest-first group). It only acts on a big-leader order, where it trades sprint fades
     * for more underfoot time (measured, 8 seeds), so it isn't a clear win. Schools need no cap: a
     * swimmer's pace doesn't depend on its size.
     */
    public boolean groupPaceCap = false;

    // Party perches (docs/fish-companions-party.md §6.1, Phase 2d). Each its own switch for
    // equal-ground A/B against the 2c baseline; a one-unit party never consults the first two.
    /** An anchored fish's home is the head, not the back (D5): solo or in a party. */
    public boolean anchoredHead = true;
    /** The party gives every percher its own home perch, once, when it is built. */
    public boolean partyHomes = true;
    /** A transient move (wander, hand, cover, climb) takes a free perch or doesn't happen. */
    public boolean partyLeases = true;

    /**
     * Ground walkers of different units push apart on the ground, as a crowd's members do
     * (docs/fish-companions-party.md §6.2, Phase 2f): overlap 9.25 → 2.16% for crab + octopus + slug.
     */
    public boolean partyGroundSeparation = true;

    // Hand visits (owner feedback 2026-10-14: about one a minute, solo or party). HandClock.
    /** After any landing on a hand, the unseen timer waits this long before calling a fish there again. */
    public boolean handClock = true;
    public float handGapMinSeconds = 45f;
    public float handGapMaxSeconds = 75f;
    /** A party's perchers share one hand clock; turns go round, longest since its own last visit first. */
    public boolean partyHandClock = true;
    /** With the clock on: a ground fish due to show itself, its owner stopped, settles 1-2 blocks out to the side (idea 2). Off: it measured a loss (§19). */
    public boolean idleInView = false;
    /** With the clock on: a percher riding a shoulder or the head, due to show itself, peeks into the view's corner (idea 4). */
    public boolean shoulderPeek = true;
    /** With the clock on: ground beats (overtake, roadside) come {@link #groundBeatSooner} times as soon, and as often. */
    public boolean moreGroundBeats = true;
    public double groundBeatSooner = 2.0;

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
