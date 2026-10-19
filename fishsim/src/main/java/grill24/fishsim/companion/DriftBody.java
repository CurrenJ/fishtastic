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
 * The JET style instead uses omnidirectional fins and mantle-first pulses, with unrestricted,
 * bounded reorientation; its counter fins can stay open during a mantle squeeze.
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
    /** A calm bell falling this much faster than it wants, blocks/s, strokes again without waiting out its cycle. */
    private static final double CALM_CATCH_DV = 0.5;
    /** Braking by room: how far ahead it looks and in what steps, blocks, and the margin past its radius. */
    private static final double ROOM_LOOK = 3.0, ROOM_STEP = 0.25, ROOM_MARGIN = 0.05;
    /** Touching a block already, the sweep's radius is found by this many halvings. */
    private static final int ROOM_BISECTIONS = 9;
    /** Drawn facing: the tilt at which it fully follows the lean, degrees, and its turn ease and spin-up per tick. */
    private static final float FACE_FULL_TILT = 15f, FACE_GAIN = 0.08f, FACE_ACCEL = 0.4f;
    /** Drawn squeeze: the stroke, blocks/s, that shows at full strength, and the least a stroke shows. */
    private static final double SQUEEZE_FULL_DV = 0.5;
    /** A need rising less steeply than this (15°) counts as level: braking, or correcting a fall. */
    private static final double TAN_LEVEL = Math.tan(Math.toRadians(15));
    private static final float SQUEEZE_FLOOR = 0.2f;

    private final CompanionTunables t;
    /** How it propels itself; BOB retains the bell until Phase 3. */
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
    /**
     * The way the art faces, degrees, and how fast that is turning, degrees per tick. Drawn only: the
     * physics and the mind read {@link #yaw} (the lean), which may swap sides in a tick.
     */
    float face, faceRate;
    /** Tilt rate, degrees per tick, and the effort that sets the pulse rate (0 idle, 1 flat out). */
    float tiltRate;
    float effort;
    float spin, spinState;
    /** The pulse envelope, 0..1: the shape of each stroke's thrust. */
    float pulse;
    /**
     * The squeeze the animator draws, 0..1 (§13.10): the envelope scaled by how hard this stroke
     * pushes, so a stroke that throws it across the room shows and one that only holds its height
     * barely breathes. Drawn only.
     */
    float squeeze;
    /** This stroke's drawn strength, 0..1, fixed when it starts. */
    private float strokeShow;
    /** Thrust actually applied this tick, blocks/s². */
    double lastThrust;
    /** The velocity change the last stroke was sized to give, blocks/s. */
    private double lastStrokeDv;
    /** What the mind asked for this tick, before wall avoidance, blocks/s (for the harness's metrics). */
    Vec asked = Vec.ZERO;
    /** What the bell steered toward this tick, blocks/s² (for the harness's metrics). */
    Vec need = Vec.ZERO;
    /** Whether a new pulse cycle started this tick (for the harness's metrics). */
    boolean strokeStart;
    /** The web flare, 0..1: the brake (§4), which the animator shows as the bell spreading wide. */
    float flare;
    /** How far the bell's axis turned this tick, degrees (tilt and lean direction together). */
    float tiltStep;
    /** Swinging back through upright to lean the other way (for {@code driftPivotSwingOnly}). */
    private boolean reversing;
    /** How settled at rest it is, 0..1 (§13.7): eases its sink and slows its pulse while it idles. */
    float calm;
    /** Its sink this tick, blocks/s², eased by {@link #calm}. */
    private double sink;
    /** Braking by room this tick: the speed it could still stop from along its travel and along its bell. */
    private double roomVel = Double.POSITIVE_INFINITY, roomAxis = Double.POSITIVE_INFINITY;
    /** Actual propulsion, including the squid's off-axis fins; excludes drag and gravity. */
    Vec propulsion = Vec.ZERO;
    double mantleThrust;
    private Vec jetAxis = Vec.UP;
    private Vec jetStrokeAxis = Vec.UP;
    private double jetClock;

    DriftBody(CompanionTunables t, DriftStyle style, Vec pos, float yaw, double radius, long seed) {
        super(grill24.fishsim.core.Locomotion.DRIFT, Gait.of(grill24.fishsim.core.Locomotion.DRIFT, t), pos, radius);
        this.t = t;
        this.style = style;
        this.noise = seed == 0 ? 0x9E3779B97F4A7C15L : seed;
        this.tiltYaw = yaw;
        this.yaw = yaw;
        this.face = yaw;
        this.pitch = -90f;
        this.phase = (nextSigned() + 1) * 0.5;
        this.period = t.driftPulseSeconds;
        this.spin = 180f * nextSigned();
    }

    @Override
    void step(Vec desired, double wanderScale, Vec extraAvoid, LocalVoxels voxels, Capsule body) {
        if (style == DriftStyle.JET && t.driftJet) {
            stepJet(desired, extraAvoid, voxels);
            return;
        }
        g = Gait.of(locomotion, t);
        asked = desired;
        double sp = vel.length();
        float calmRate = (float) DT / Math.max(0.05f, t.driftIdleCalmSeconds);
        calm = clamp(calm + (idling ? calmRate : -4 * calmRate), 0f, 1f);
        sink = t.driftSink * (1 - calm * (1 - t.driftIdleSink));

        // A slow current-like wander on where it is trying to go, like the tank's drift.
        Vec ahead = pos.add(vel.scale(0.35));
        Vec avoid = voxels.avoidance(pos, t.wallMargin + radius)
                .add(voxels.avoidance(ahead, t.wallMargin + radius).scale(0.6))
                .clampLength(1);
        desired = desired.add(avoid.scale(Math.max(t.wallAvoidSpeed, sp * 1.2))).add(extraAvoid);

        // What it needs: hold the desired velocity against drag, hold height against the sink, and
        // close the gap.
        double dsp = desired.length();
        Vec hold = desired.scale(drag(dsp)).add(0, sink, 0);
        Vec need = hold.add(desired.sub(vel).scale(t.driftResponse));
        this.need = need;
        Vec axisBefore = axis();
        steerAxis(need, hold, sp);
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
        propulsion = axis.scale(thrust);
        Vec before = vel;
        double dragNow = drag(sp), sinkNow = sink;
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
        turnFace();
        burstDrive = 1f + pulse;
        tailPhase = (float) (phase * Math.PI * 2);
    }

    /** Fins close small velocity gaps in any direction; larger gaps need mantle-first pulses. */
    private void stepJet(Vec desired, Vec extraAvoid, LocalVoxels voxels) {
        g = Gait.of(locomotion, t);
        asked = desired;
        double sp = vel.length();
        sink = t.driftSink;
        Vec avoid = voxels.avoidance(pos, t.wallMargin + radius)
                .add(voxels.avoidance(pos.add(vel.scale(0.35)), t.wallMargin + radius).scale(0.6))
                .clampLength(1);
        desired = desired.add(avoid.scale(Math.max(t.wallAvoidSpeed, sp * 1.2))).add(extraAvoid);
        roomVel = sp > 1e-6 && t.driftRoomBrake ? roomSpeed(vel.normalize(), voxels) : Double.POSITIVE_INFINITY;
        if (t.driftRoomBrake && desired.length() > 1e-6)
            desired = desired.clampLength(roomSpeed(desired.normalize(), voxels));
        if (t.driftFlare) updateFlare(desired, sp);
        double k = drag(sp) + (t.driftFlare ? flare * t.driftFlareDrag : 0);
        Vec hold = desired.scale(t.jetCounterFinBrake ? drag(sp) : k).add(0, sink, 0);
        need = hold.add(desired.sub(vel).scale(t.driftResponse));
        Vec fins = need.clampLength(Math.max(0, t.jetFinThrust));
        Vec residual = need.sub(fins);
        // At rest the mantle points up and the tentacles hang down. Travel and fast braking can
        // turn through any elevation; a squid is not constrained to a bell's upright hemisphere.
        Vec target = residual.length() > 0.15 ? residual.normalize() : Vec.UP;
        double cap = Math.toRadians(t.jetTurnDegPerTick + (t.jetSlowTurnDegPerTick - t.jetTurnDegPerTick)
                * (1 - Math.min(1, sp / 2)));
        double angle = Math.acos(Math.max(-1, Math.min(1, jetAxis.dot(target))));
        tiltStep = (float) Math.toDegrees(Math.min(angle, cap));
        if (jetClock < Math.max(0.1, t.jetPulseSeconds) * Math.max(0.05, Math.min(0.9, t.jetPulseDuty)) && peak > 0) {
            tiltStep = 0;
        } else if (angle <= cap) jetAxis = target;
        else {
            Vec tangent = target.sub(jetAxis.scale(jetAxis.dot(target))).normalize();
            if (tangent.lengthSqr() < 1e-8) {
                Vec side = Math.abs(jetAxis.y()) < 0.9 ? Vec.UP : Vec.fromYawPitch(tiltYaw, 0);
                tangent = side.sub(jetAxis.scale(side.dot(jetAxis))).normalize();
            }
            jetAxis = jetAxis.scale(Math.cos(cap)).add(tangent.scale(Math.sin(cap))).normalize();
        }
        double cycle = Math.max(0.1, t.jetPulseSeconds);
        double duty = Math.max(0.05, Math.min(0.9, t.jetPulseDuty));
        jetClock += DT;
        strokeStart = false;
        if (jetClock >= cycle && residual.length() > 0.15
                && jetAxis.dot(target) > Math.cos(Math.toRadians(t.jetAlignDeg))) {
            jetClock = 0;
            jetStrokeAxis = jetAxis;
            // Fins already pay their share. Fix the remaining impulse and direction at onset,
            // so turning during the coast never sweeps a live jet across the room.
            double dv = Math.max(0, hold.scale(cycle).add(desired.sub(vel).scale(t.driftArriveGain))
                    .sub(fins.scale(cycle)).dot(jetStrokeAxis));
            if (t.driftRoomBrake) dv = Math.min(dv,
                    Math.max(0, roomSpeed(jetStrokeAxis, voxels) - vel.dot(jetStrokeAxis)));
            peak = Math.min(t.jetThrust, dv / (cycle * duty * ENVELOPE_MEAN));
            strokeShow = (float) Math.min(1, dv / SQUEEZE_FULL_DV);
            strokeStart = peak > 0;
        }
        double u = jetClock / (cycle * duty);
        double s = u < 1 ? Math.sin(Math.PI * u) : 0;
        pulse = (float) (s * s);
        squeeze = pulse * strokeShow;
        double jet = peak * pulse;
        if (t.jetArrive) {
            double available = desired.sub(vel).scale(1 / DT).add(vel.scale(k)).add(0, sink, 0)
                    .sub(fins).dot(jetStrokeAxis);
            if (t.driftRoomBrake) available = Math.min(available,
                    (roomSpeed(jetStrokeAxis, voxels) - vel.dot(jetStrokeAxis)) / DT);
            jet = Math.min(jet, Math.max(0, available));
        }
        propulsion = fins.add(jetStrokeAxis.scale(jet));
        mantleThrust = jet;
        squeeze = peak > 0 ? (float) (jet / peak) * strokeShow : 0;
        lastThrust = propulsion.length();
        Vec before = vel;
        vel = vel.add(propulsion.sub(vel.scale(k)).add(0, -sink, 0).scale(DT));
        speed = vel.length();
        move(voxels, speed > 1e-6 ? vel.normalize() : jetAxis);
        acc = vel.sub(before).scale(1 / DT);
        if (jetAxis.horizontalLength() > 1e-6) tiltYaw = (float) jetAxis.yawDeg();
        tilt = (float) Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, jetAxis.y()))));
        yaw = tiltYaw;
        pitch = tilt - 90;
        turnFace();
        effort = (float) Math.min(1, need.length() / Math.max(1e-6, t.jetFinThrust));
        advanceSpin();
        // The pulse is already the motor's surge. Feeding it back into the mind's asked speed
        // would count it twice and make the target reverse on every jet coast.
        burstDrive = 1;
        tailPhase += (float) (DT * Math.PI * 4);
    }

    /**
     * The brake (docs/fish-companions-drift.md §4): a bell can't push backwards, but it can spread its
     * web like a parachute. It flares by how much faster it is going than it wants to go that way —
     * coasting past its spot, still carrying a stroke when the owner stops, or heading off at an
     * angle to where it now wants to be — and the flare is drag and a little extra sink. A bell
     * can't flare and squeeze at once. A squid's counter fins are independent of its mantle.
     */
    private void updateFlare(Vec desired, double sp) {
        double target = 0;
        if (sp > 1e-6) {
            double kept = Math.max(0, desired.dot(vel) / sp);
            double excess = sp - kept;
            if (t.driftRoomBrake) excess = Math.max(excess, sp - roomVel);
            target = Math.max(0, Math.min(1, (excess - FLARE_DEADBAND) / t.driftFlareFull));
        }
        if (style != DriftStyle.JET || !t.driftJet || !t.jetCounterFinBrake) target *= 1 - pulse;
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
    private void steerAxis(Vec need, Vec hold, double sp) {
        double h = need.horizontalLength();
        float wantTilt = (float) Math.toDegrees(Math.atan2(h, Math.max(1e-6, need.y())));
        // Never past the leaning limit: a bell doesn't swim upside down. A hovering jelly stays
        // upright by itself, because then what it needs is mostly to hold off the sink.
        wantTilt = clamp(wantTilt, 0f, t.driftTiltMaxDeg);
        // Idling and asked to go only a little way, it hangs nearly upright and lets its strokes carry
        // it: a lean toward the whole gap at an idle ask rocked the bell through 60° on every move
        // (§13.7). Travelling, it leans as it always did.
        if (t.driftCalmTiltDeg < t.driftTiltMaxDeg && calm > 0) {
            double k = Math.min(1, asked.length() / t.driftCalmTiltSpeed);
            double slow = t.driftCalmTiltDeg + (t.driftTiltMaxDeg - t.driftCalmTiltDeg) * k;
            wantTilt = Math.min(wantTilt, (float) (t.driftTiltMaxDeg + (slow - t.driftTiltMaxDeg) * calm));
        }
        // Asked to go slowly it leans only somewhat, calm or not (§13.11): showing off by a still owner
        // asks for about 1 b/s, which a lean of ~35° holds. Only hurrying lays it right over.
        if (t.driftAskTiltDeg < t.driftTiltMaxDeg) {
            double k = Math.min(1, asked.length() / t.driftAskTiltSpeed);
            wantTilt = Math.min(wantTilt, (float) (t.driftAskTiltDeg + (t.driftTiltMaxDeg - t.driftAskTiltDeg) * k));
        }
        // Braking or out of a fall, what it needs points level or down, where a bell can't push. It
        // leans only as far as holding its asked velocity takes and lets the flare and sink do the rest.
        if (!t.driftLeanIntoBrake && need.y() <= need.horizontalLength() * TAN_LEVEL) {
            float holdTilt = (float) Math.toDegrees(Math.atan2(hold.horizontalLength(), Math.max(1e-6, hold.y())));
            wantTilt = Math.min(wantTilt, holdTilt + t.driftBrakeTiltMarginDeg);
        }
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

    /**
     * Turns the drawn facing toward the lean, eased and capped, and only as far as it leans: hanging
     * upright it holds its facing, so a swing back through upright to lean the other way is a slow
     * turn of the body rather than a 180° flip.
     */
    private void turnFace() {
        float lean = Math.min(1f, tilt / FACE_FULL_TILT);
        float cap = t.driftFaceRateDegPerTick * lean;
        float cmd = clamp(Body.wrap(tiltYaw - face) * FACE_GAIN * lean, -cap, cap);
        faceRate += clamp(cmd - faceRate, -FACE_ACCEL, FACE_ACCEL);
        face = Body.wrap(face + faceRate);
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
        float slowest = t.driftIdlePulseSeconds > 0
                ? t.driftPulseSeconds + (t.driftIdlePulseSeconds - t.driftPulseSeconds) * calm : t.driftPulseSeconds;
        double next = slowest + (t.driftPulseFastSeconds - slowest) * effort;
        phase += DT / period;
        boolean wrap = phase >= 1;
        if (!wrap && t.driftRetime && phase >= t.driftPulseDuty) {
            double since = phase * period;
            wrap = since >= next || since >= RETIME_MIN_SECONDS
                    && (strokeDv(hold, desired, axis, next) > Math.max(1.5 * lastStrokeDv, lastStrokeDv + RETIME_DV)
                    // A calm bell's long cycle doesn't let it fall: dropping well faster than it wants
                    // (out of a swoop, or under a flare), it strokes again at once — also when it has
                    // just stopped idling but is still in a long idle cycle (§13.7).
                    || (calm > 0 || period > t.driftPulseSeconds) && t.driftCalmCatch
                    && vel.y() < desired.y() - CALM_CATCH_DV);
        }
        strokeStart = wrap;
        if (wrap) {
            phase = phase >= 1 ? phase - 1 : 0;
            period = next;
            double mean = t.driftArrive ? strokeDv(hold, desired, axis, period) / period : along;
            peak = Math.min(t.driftThrust, mean / (t.driftPulseDuty * ENVELOPE_MEAN));
            lastStrokeDv = peak * t.driftPulseDuty * ENVELOPE_MEAN * period;
            strokeShow = (float) Math.max(SQUEEZE_FLOOR, Math.min(1, lastStrokeDv / SQUEEZE_FULL_DV));
        }
        double u = phase / t.driftPulseDuty;
        double s = u < 1 ? Math.sin(Math.PI * u) : 0;
        pulse = (float) (s * s);
        squeeze = pulse * strokeShow;
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
            double rise = sink * HOLD_HEIGHT_SECONDS + desired.y() - vel.y();
            dv = Math.max(dv, Math.max(0, rise) / axis.y());
        }
        // No faster along the bell than its flare could stop in the room ahead.
        if (t.driftRoomBrake) dv = Math.min(dv, Math.max(0, roomAxis - vel.dot(axis)));
        // No more lift than it wants: what's left of the stroke after that goes nowhere useful.
        if (t.driftNoClimb && axis.y() > 1e-3) {
            double maxRise = Math.max(0, desired.y()) + 0.5 * sink * cycle + CLIMB_MARGIN;
            dv = Math.min(dv, Math.max(0, (maxRise - vel.y()) / axis.y()));
        }
        return dv;
    }

    /**
     * The fastest it could go along {@code dir} and still stop before a block with its web flared:
     * under linear drag {@code k} a body at {@code v} stops within {@code v / k}, so this is
     * {@code k} times the free distance ahead (beyond its radius and a small margin). Open beyond
     * {@link #ROOM_LOOK} blocks counts as unlimited.
     * For a squid, reserve the distance travelled while its counter fins open as well; assuming
     * full stopping drag immediately made the old candidate see a door too late.
     *
     * <p>With {@code driftRoomPastContact}, a block it already touches is not in the way unless it
     * comes closer along {@code dir}: it sweeps the largest sphere that is clear where it is (§13.12).
     * Pressed to a tunnel wall or sitting on the floor, the full sphere read no room in any direction,
     * so every stroke was cut to nothing and it could neither stop a fall nor push off the floor.
     */
    private double roomSpeed(Vec dir, LocalVoxels voxels) {
        double k = t.driftDrag + t.driftFlareDrag;
        boolean jet = style == DriftStyle.JET && t.driftJet;
        if (jet) k /= 1 + k * t.jetBrakeDelay;
        double r = radius + (jet ? t.jetRoomMargin : ROOM_MARGIN);
        if (t.driftRoomPastContact && !voxels.sphereClear(pos, r)) {
            // The clearance where it is, to within a millimetre: only what it is already touching is let off.
            double lo = 0, hi = r;
            for (int i = 0; i < ROOM_BISECTIONS; i++) {
                double mid = 0.5 * (lo + hi);
                if (voxels.sphereClear(pos, mid)) lo = mid; else hi = mid;
            }
            r = lo;
        }
        double step = jet ? 0.1 : ROOM_STEP;
        for (double d = step; d <= ROOM_LOOK + 1e-9; d += step) {
            if (!voxels.sphereClear(pos.add(dir.scale(d)), r)) return k * Math.max(0, d - step);
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
        face = facingYaw;
        faceRate = 0f;
        pitch = -90f;
        effort = 0f;
        flare = 0f;
        calm = 0f;
        jetAxis = jetStrokeAxis = Vec.UP;
        jetClock = 0;
        if (style == DriftStyle.JET && t.driftJet) {
            peak = 0;
            pulse = squeeze = 0;
        }
        propulsion = Vec.ZERO;
        mantleThrust = 0;
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
