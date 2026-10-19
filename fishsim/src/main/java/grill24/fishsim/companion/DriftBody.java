package grill24.fishsim.companion;

/**
 * A drifter's body — jellyfish and their kin (docs/fish-companions-sim-plan.md §3.4, §9): a bell
 * that moves only by pulsing along its own axis, coasts between pulses, and sinks when it stops.
 *
 * <p><b>A pulsed controller.</b> Each tick the body works out the acceleration it needs: enough
 * to hold the desired velocity against drag, to hold its height against the sink, and to close
 * the gap to the desired velocity. The bell tilts toward that need at a capped rate (never past
 * horizontal: it doesn't swim upside down), and only the part of the need along the axis is
 * thrust. Thrust comes as pulses: a smooth squeeze ({@code sin²} over the duty) whose strength is
 * set so that the cycle's mean thrust matches the need. A hovering jelly pulses slowly, upright,
 * against its sink; a hurrying one pulses faster and harder and leans into the travel. Every
 * change of velocity it makes is one of its own pulses (or the drag and sink it is subject to) —
 * it is never carried — and the same hard per-axis resolve as the swimmer keeps it out of blocks.
 *
 * <p>Pose: {@link #yaw}/{@link #pitch} give the bell's axis as a look vector (upright is pitch −90),
 * {@link #spin} its slow tumble about that axis, {@link #pulse} the squeeze for the animator.
 */
final class DriftBody extends Motor {

    /** Mean of the pulse envelope over one cycle, per unit of duty: ∫sin² = ½. */
    private static final double ENVELOPE_MEAN = 0.5;
    /** OU tumble about the axis (the tank's DRIFT_SPIN values), and its rate, degrees/second. */
    private static final float SPIN_THETA = 0.15f, SPIN_SIGMA = 0.30f, SPIN_RATE_DEG = 6.0f;
    private static final float SQRT3 = 1.7320508f;
    /** Retiming: never sooner than this after a stroke started, seconds, nor for less than this much more, blocks/s. */
    private static final double RETIME_MIN_SECONDS = 0.3, RETIME_DV = 1.0;
    private static final double COS_25 = Math.cos(Math.toRadians(25)), COS_60 = Math.cos(Math.toRadians(60));
    /** Flare: excess speed it lets ride, blocks/s, and how fast the web opens and folds, per second. */
    private static final double FLARE_DEADBAND = 0.3;
    private static final float FLARE_OPEN_RATE = 4f, FLARE_CLOSE_RATE = 5f;
    /** Height hold: how upright the bell must be (vertical part of its axis) for a holding stroke. */
    private static final double HOLD_HEIGHT_MIN_UP = 0.5, HOLD_HEIGHT_SECONDS = 0.5;
    /** No-climb: how much faster than asked it may still be rising after a stroke, blocks/s. */
    private static final double CLIMB_MARGIN = 0.3;
    /** Braking by room: how far ahead it looks and in what steps, blocks, and the margin past its radius. */
    private static final double ROOM_LOOK = 3.0, ROOM_STEP = 0.25, ROOM_MARGIN = 0.05;

    private final CompanionTunables t;
    /** How it propels itself (docs/fish-companions-drift.md §2). Not read yet: every style is the first-cut bell. */
    final DriftStyle style;
    private long noise;
    /** Position in the current pulse cycle, [0, 1); the squeeze is the first {@code duty} of it. */
    private double phase;
    /** Thrust at the top of this pulse, blocks/s², fixed when the pulse starts. */
    private double peak;
    /** Pulse cycle length, seconds, fixed when the cycle starts. */
    private double period;
    /** The bell axis as tilt from vertical and the horizontal direction of that tilt, degrees. */
    float tilt, tiltYaw;
    /** Tilt rate, degrees per tick, and the effort that sets the pulse rate (0 idle, 1 flat out). */
    float tiltRate;
    float effort;
    float spin, spinState;
    /** The squeeze, 0..1 — what the animator shows as the bell contracting. */
    float pulse;
    /** Thrust actually applied this tick, blocks/s². */
    double lastThrust;
    /** The velocity change the last stroke was sized to give, blocks/s. */
    private double lastStrokeDv;
    /** What the mind asked for this tick, before wall avoidance, blocks/s (for the harness's metrics). */
    Vec asked = Vec.ZERO;
    /** Whether a new pulse cycle started this tick (for the harness's metrics). */
    boolean strokeStart;
    /** The web flare, 0..1: the brake (§4), which the animator shows as the bell spreading wide. */
    float flare;
    /** How far the bell's axis turned this tick, degrees (tilt and lean direction together). */
    float tiltStep;
    /** Swinging back through upright to lean the other way (for {@code driftPivotSwingOnly}). */
    private boolean reversing;
    /** Braking by room this tick: the speed it could still stop from along its travel and along its bell. */
    private double roomVel = Double.POSITIVE_INFINITY, roomAxis = Double.POSITIVE_INFINITY;

    DriftBody(CompanionTunables t, DriftStyle style, Vec pos, float yaw, double radius, long seed) {
        super(grill24.fishsim.core.Locomotion.DRIFT, Gait.of(grill24.fishsim.core.Locomotion.DRIFT, t), pos, radius);
        this.t = t;
        this.style = style;
        this.noise = seed == 0 ? 0x9E3779B97F4A7C15L : seed;
        this.tiltYaw = yaw;
        this.yaw = yaw;
        this.pitch = -90f;
        this.phase = (nextSigned() + 1) * 0.5;
        this.period = t.driftPulseSeconds;
        this.spin = 180f * nextSigned();
    }

    @Override
    void step(Vec desired, double wanderScale, Vec extraAvoid, LocalVoxels voxels, Capsule body) {
        g = Gait.of(locomotion, t);
        asked = desired;
        double sp = vel.length();

        // A slow current-like wander on where it is trying to go, like the tank's drift.
        Vec ahead = pos.add(vel.scale(0.35));
        Vec avoid = voxels.avoidance(pos, t.wallMargin + radius)
                .add(voxels.avoidance(ahead, t.wallMargin + radius).scale(0.6))
                .clampLength(1);
        desired = desired.add(avoid.scale(Math.max(t.wallAvoidSpeed, sp * 1.2))).add(extraAvoid);

        // What it needs: hold the desired velocity against drag, hold height against the sink, and
        // close the gap.
        double dsp = desired.length();
        Vec hold = desired.scale(drag(dsp)).add(0, t.driftSink, 0);
        Vec need = hold.add(desired.sub(vel).scale(t.driftResponse));
        Vec axisBefore = axis();
        steerAxis(need, sp);
        Vec axis = axis();
        tiltStep = (float) Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, axisBefore.dot(axis)))));
        double along = Math.max(0, need.dot(axis));
        double meanMax = t.driftThrust * t.driftPulseDuty * ENVELOPE_MEAN;
        // Effort is how much it needs, not how much its bell is pointed at yet: a jelly far behind
        // is trying hard while it is still swinging round.
        float effortTarget = (float) Math.min(1, need.length() / meanMax);
        effort += clamp(effortTarget - effort, -1.5f * (float) DT, 1.5f * (float) DT);

        if (t.driftRoomBrake) {
            roomVel = sp > 1e-6 ? roomSpeed(vel.scale(1 / sp), voxels) : Double.POSITIVE_INFINITY;
            roomAxis = roomSpeed(axis, voxels);
        }
        if (t.driftFlare) updateFlare(desired, sp);
        advancePulse(along, hold, desired, axis);
        double thrust = peak * pulse;
        lastThrust = thrust;
        Vec before = vel;
        double dragNow = drag(sp), sinkNow = t.driftSink;
        if (t.driftFlare) {
            dragNow += flare * t.driftFlareDrag;
            sinkNow += flare * t.driftFlareSink;
        }
        Vec a = axis.scale(thrust).sub(vel.scale(dragNow)).add(0, -sinkNow, 0);
        vel = vel.add(a.scale(DT));
        speed = vel.length();
        Vec fwd = speed > 1e-6 ? vel.scale(1 / speed) : axis;
        move(voxels, fwd);
        acc = vel.sub(before).scale(1 / DT);

        advanceSpin();
        yaw = tiltYaw;
        pitch = tilt - 90f;
        burstDrive = 1f + pulse;
        tailPhase = (float) (phase * Math.PI * 2);
    }

    /**
     * The brake (docs/fish-companions-drift.md §4): a bell can't push backwards, but it can spread its
     * web like a parachute. It flares by how much faster it is going than it wants to go that way —
     * coasting past its spot, still carrying a stroke when the owner stops, or heading off at an
     * angle to where it now wants to be — and the flare is drag and a little extra sink. It can't
     * flare and squeeze at once, so a stroke folds the web back in.
     */
    private void updateFlare(Vec desired, double sp) {
        double target = 0;
        if (sp > 1e-6) {
            double kept = Math.max(0, desired.dot(vel) / sp);
            double excess = sp - kept;
            if (t.driftRoomBrake) excess = Math.max(excess, sp - roomVel);
            target = Math.max(0, Math.min(1, (excess - FLARE_DEADBAND) / t.driftFlareFull));
        }
        target *= 1 - pulse;
        flare += clamp((float) target - flare, -FLARE_CLOSE_RATE * (float) DT, FLARE_OPEN_RATE * (float) DT);
    }

    /** Drag at speed {@code v}, 1/s: light when idling so it drifts, heavier fast so it can stop. */
    private double drag(double v) {
        return t.driftDrag + t.driftDragPerSpeed * v;
    }

    /**
     * Tilts the bell toward {@code need}, never past horizontal, at a capped and eased rate. With
     * {@code driftSlowPivot} the cap rises as it slows: a hanging bell rights and leans quickly, a
     * fast one is carried on its line (and a flare, by slowing it, frees it to turn).
     */
    private void steerAxis(Vec need, double sp) {
        double h = need.horizontalLength();
        float wantTilt = (float) Math.toDegrees(Math.atan2(h, Math.max(1e-6, need.y())));
        // Never past the leaning limit: a bell doesn't swim upside down. A hovering jelly stays
        // upright by itself, because then what it needs is mostly to hold off the sink.
        wantTilt = clamp(wantTilt, 0f, t.driftTiltMaxDeg);
        float wantYaw = h > 1e-4 ? (float) need.yawDeg() : tiltYaw;
        float yawErr = Body.wrap(wantYaw - tiltYaw);
        boolean swinging = Math.abs(yawErr) > 90f && tilt > 1f;
        // A reversal is the swing back to upright and the lean out the other way after it.
        if (swinging) reversing = true;
        else if (reversing && (tilt >= wantTilt - 5f || h <= 1e-4)) reversing = false;
        float boost = t.driftSlowPivot > 0 && (!t.driftPivotSwingOnly || reversing)
                ? 1f + t.driftSlowPivot * (float) (1 - Math.min(1, sp / t.driftSlowPivotSpeed)) : 1f;
        float rate = t.driftTiltRateDegPerTick * boost;
        // Tilt toward a new direction by swinging through upright when that is shorter than turning.
        if (swinging) {
            wantTilt = 0f;
        } else {
            tiltYaw = Body.wrap(tiltYaw + clamp(yawErr * 0.2f, -rate * 2, rate * 2));
        }
        if (tilt < 1f && h > 1e-4) tiltYaw = wantYaw;
        float cmd = clamp((wantTilt - tilt) * 0.2f, -rate, rate);
        tiltRate += clamp(cmd - tiltRate, -0.4f * boost, 0.4f * boost);
        tilt = clamp(tilt + tiltRate, 0f, 90f);
        yawRate = 0f;
    }

    private Vec axis() {
        return Vec.fromYawPitch(tiltYaw, tilt - 90f);
    }

    /**
     * Advances the pulse cycle. A cycle's length and its pulse's strength are fixed when it starts,
     * from the effort and need of that moment, so a pulse never changes shape mid-squeeze.
     *
     * <p>With {@code driftRetime} a cycle may end early once its squeeze is over: when the cycle it
     * would choose now is already over (it has started hurrying), or when the stroke it wants now is
     * much bigger than the last one (that one went off while it was still swinging round, or before
     * the owner set off). A jelly can pulse whenever it likes; waiting out a 2.8 s idle cycle with
     * its owner walking off was most of the first cut's lag.
     *
     * <p>With {@code driftArrive} a stroke is sized as a velocity change, not as a held
     * acceleration: what drag and sink will take over the cycle, plus most of the gap to the asked
     * velocity, along the bell. (The first cut multiplied the continuous correction by the whole
     * cycle — at an idle cycle four times the gap.) A stroke well off the line of what's needed is
     * weakened, so a bell still swinging round doesn't push itself up and sideways.
     */
    private void advancePulse(double along, Vec hold, Vec desired, Vec axis) {
        double next = t.driftPulseSeconds + (t.driftPulseFastSeconds - t.driftPulseSeconds) * effort;
        phase += DT / period;
        boolean wrap = phase >= 1;
        if (!wrap && t.driftRetime && phase >= t.driftPulseDuty) {
            double since = phase * period;
            wrap = since >= next || since >= RETIME_MIN_SECONDS
                    && strokeDv(hold, desired, axis, next) > Math.max(1.5 * lastStrokeDv, lastStrokeDv + RETIME_DV);
        }
        strokeStart = wrap;
        if (wrap) {
            phase = phase >= 1 ? phase - 1 : 0;
            period = next;
            double mean = t.driftArrive ? strokeDv(hold, desired, axis, period) / period : along;
            peak = Math.min(t.driftThrust, mean / (t.driftPulseDuty * ENVELOPE_MEAN));
            lastStrokeDv = peak * t.driftPulseDuty * ENVELOPE_MEAN * period;
        }
        double u = phase / t.driftPulseDuty;
        double s = u < 1 ? Math.sin(Math.PI * u) : 0;
        pulse = (float) (s * s);
    }

    /**
     * The velocity change one stroke over a cycle of {@code cycle} seconds should give along the
     * bell: the drag and sink lost over the cycle plus {@code driftArriveGain} of the gap, the part
     * of that along the axis, faded out as the axis turns from 25° to 60° off it.
     */
    private double strokeDv(Vec hold, Vec desired, Vec axis, double cycle) {
        Vec want = hold.scale(cycle).add(desired.sub(vel).scale(t.driftArriveGain));
        double alongDv = want.dot(axis);
        double len = want.length();
        if ((alongDv <= 0 || len < 1e-9) && !t.driftHoldHeight) return 0;
        if (len < 1e-9) return 0;
        double cos = alongDv / len;
        double gate = Math.max(0, Math.min(1, (cos - COS_60) / (COS_25 - COS_60)));
        double dv = alongDv * gate * gate * (3 - 2 * gate);
        // Near upright it can always hold its height, wherever it wants to go next.
        if (t.driftHoldHeight && axis.y() > HOLD_HEIGHT_MIN_UP) {
            // Only a short window of sink: with retiming it can pulse again soon, and paying a slow idle
            // cycle's sink at once would bob it up and down by blocks.
            double rise = t.driftSink * HOLD_HEIGHT_SECONDS + desired.y() - vel.y();
            dv = Math.max(dv, Math.max(0, rise) / axis.y());
        }
        // No faster along the bell than its flare could stop in the room ahead.
        if (t.driftRoomBrake) dv = Math.min(dv, Math.max(0, roomAxis - vel.dot(axis)));
        // No more lift than it wants: what's left of the stroke after that goes nowhere useful.
        if (t.driftNoClimb && axis.y() > 1e-3) {
            double maxRise = Math.max(0, desired.y()) + 0.5 * t.driftSink * cycle + CLIMB_MARGIN;
            dv = Math.min(dv, Math.max(0, (maxRise - vel.y()) / axis.y()));
        }
        return dv;
    }

    /**
     * The fastest it could go along {@code dir} and still stop before a block with its web flared:
     * under linear drag {@code k} a body at {@code v} stops within {@code v / k}, so this is
     * {@code k} times the free distance ahead (beyond its radius and a small margin). Open beyond
     * {@link #ROOM_LOOK} blocks counts as unlimited.
     */
    private double roomSpeed(Vec dir, LocalVoxels voxels) {
        double k = t.driftDrag + t.driftFlareDrag;
        double r = radius + ROOM_MARGIN;
        for (double d = ROOM_STEP; d <= ROOM_LOOK + 1e-9; d += ROOM_STEP) {
            if (!voxels.sphereClear(pos.add(dir.scale(d)), r)) return k * Math.max(0, d - ROOM_STEP);
        }
        return Double.POSITIVE_INFINITY;
    }

    private void advanceSpin() {
        float k = SPIN_SIGMA * (float) Math.sqrt(DT) * SQRT3;
        spinState = clamp(spinState - spinState * SPIN_THETA * (float) DT + k * nextSigned(), -2.5f, 2.5f);
        spin = Body.wrap(spin + spinState * SPIN_RATE_DEG * (float) DT);
    }

    @Override
    float speedFactor() {
        return 0.6f + 0.9f * effort;
    }

    @Override
    void placeAt(Vec p, float facingYaw) {
        super.placeAt(p, facingYaw);
        tilt = 0f;
        tiltRate = 0f;
        tiltYaw = facingYaw;
        pitch = -90f;
        effort = 0f;
        flare = 0f;
    }

    private float nextSigned() {
        long x = noise;
        x ^= x << 13;
        x ^= x >>> 7;
        x ^= x << 17;
        noise = x;
        return ((x >>> 40) * (1f / 8388608f)) - 1f;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
