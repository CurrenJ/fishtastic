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
    /** What the mind asked for this tick, before wall avoidance, blocks/s (for the harness's metrics). */
    Vec asked = Vec.ZERO;
    /** Whether a new pulse cycle started this tick (for the harness's metrics). */
    boolean strokeStart;

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
        Vec need = desired.scale(drag(dsp))
                .add(0, t.driftSink, 0)
                .add(desired.sub(vel).scale(t.driftResponse));
        steerAxis(need);
        Vec axis = axis();
        double along = Math.max(0, need.dot(axis));
        double meanMax = t.driftThrust * t.driftPulseDuty * ENVELOPE_MEAN;
        // Effort is how much it needs, not how much its bell is pointed at yet: a jelly far behind
        // is trying hard while it is still swinging round.
        float effortTarget = (float) Math.min(1, need.length() / meanMax);
        effort += clamp(effortTarget - effort, -1.5f * (float) DT, 1.5f * (float) DT);

        advancePulse(along);
        double thrust = peak * pulse;
        lastThrust = thrust;
        Vec before = vel;
        Vec a = axis.scale(thrust).sub(vel.scale(drag(sp))).add(0, -t.driftSink, 0);
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

    /** Drag at speed {@code v}, 1/s: light when idling so it drifts, heavier fast so it can stop. */
    private double drag(double v) {
        return t.driftDrag + t.driftDragPerSpeed * v;
    }

    /** Tilts the bell toward {@code need}, never past horizontal, at a capped and eased rate. */
    private void steerAxis(Vec need) {
        double h = need.horizontalLength();
        float wantTilt = (float) Math.toDegrees(Math.atan2(h, Math.max(1e-6, need.y())));
        // Never past the leaning limit: a bell doesn't swim upside down. A hovering jelly stays
        // upright by itself, because then what it needs is mostly to hold off the sink.
        wantTilt = clamp(wantTilt, 0f, t.driftTiltMaxDeg);
        float wantYaw = h > 1e-4 ? (float) need.yawDeg() : tiltYaw;
        // Tilt toward a new direction by swinging through upright when that is shorter than turning.
        float yawErr = Body.wrap(wantYaw - tiltYaw);
        if (Math.abs(yawErr) > 90f && tilt > 1f) {
            wantTilt = 0f;
        } else {
            tiltYaw = Body.wrap(tiltYaw + clamp(yawErr * 0.2f, -t.driftTiltRateDegPerTick * 2, t.driftTiltRateDegPerTick * 2));
        }
        if (tilt < 1f && h > 1e-4) tiltYaw = wantYaw;
        float cmd = clamp((wantTilt - tilt) * 0.2f, -t.driftTiltRateDegPerTick, t.driftTiltRateDegPerTick);
        tiltRate += clamp(cmd - tiltRate, -0.4f, 0.4f);
        tilt = clamp(tilt + tiltRate, 0f, 90f);
        yawRate = 0f;
    }

    private Vec axis() {
        return Vec.fromYawPitch(tiltYaw, tilt - 90f);
    }

    /**
     * Advances the pulse cycle. A cycle's length and its pulse's strength are fixed when it starts,
     * from the effort and need of that moment, so a pulse never changes shape mid-squeeze.
     */
    private void advancePulse(double along) {
        phase += DT / period;
        strokeStart = phase >= 1;
        if (phase >= 1) {
            phase -= 1;
            period = t.driftPulseSeconds + (t.driftPulseFastSeconds - t.driftPulseSeconds) * effort;
            double mean = along;
            peak = Math.min(t.driftThrust, mean / (t.driftPulseDuty * ENVELOPE_MEAN));
        }
        double u = phase / t.driftPulseDuty;
        double s = u < 1 ? Math.sin(Math.PI * u) : 0;
        pulse = (float) (s * s);
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
