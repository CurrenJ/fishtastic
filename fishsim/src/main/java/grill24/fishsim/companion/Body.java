package grill24.fishsim.companion;

/**
 * A free-swimming companion's body: it takes the velocity the mind wants and turns it into motion
 * a fish could make (docs/fish-companions-sim-plan.md §3.4).
 *
 * <p><b>Heading first.</b> The body has a heading (yaw, pitch) and a forward speed, and it travels
 * where it faces. The tank's planar model instead steers a velocity vector and caps only the
 * turning part of the acceleration; braking is uncapped there, so a velocity can pass through
 * zero and come out reversed — tolerable in a tank seen side-on, but a companion seen up close
 * would be swimming backwards (the first spike run measured 100–170° between facing and travel).
 * Here that can't happen by construction:
 * <ul>
 *   <li>yaw and pitch turn toward the desired direction with a capped turn rate, and the turn
 *       rate itself changes at a capped rate — so a turn eases in and out instead of snapping on;</li>
 *   <li>both caps tighten with speed, from the lateral-acceleration and jerk limits ({@code a = v·ω}),
 *       so a fast swim turns in a wide arc and a slow one can pivot — the tank's nonholonomic rule
 *       expressed on the heading;</li>
 *   <li>forward speed chases the desired speed, cut back when the desired direction is off to the
 *       side or behind, so a fish that wants to reverse slows and turns tightly rather than braking
 *       into reverse; speed changes are acceleration- and jerk-limited;</li>
 *   <li>a small vertical slip lets it rise and sink a little without pitching, like buoyancy.</li>
 * </ul>
 * Kept from the tank: the 7°/tick turn rate, OU wander, burst-and-coast, bank as a function of turn
 * rate with the tank's smoothing. Blocks: soft avoidance shapes the desired velocity, scaled up with
 * speed, and a hard per-axis resolve makes "never inside a block" structural.
 */
final class Body {

    static final double DT = 0.05;
    /** The speed the turn caps are computed at when the fish is slower — pivoting is cheap. */
    private static final double PIVOT_SPEED_FRACTION = 0.25;
    /** Desired speed below which the heading holds rather than chasing a noisy direction. */
    private static final double HOLD_SPEED = 0.05;
    /** Proportional heading gain, per tick: commanded turn rate per degree of heading error. */
    private static final float HEADING_GAIN = 0.15f;
    /** Upper bound on how fast the turn rate may change, degrees per tick². */
    private static final float MAX_TURN_ACCEL = 1.5f;
    /** Vertical slip: how far it can rise or sink without pitching, blocks/s, and how quickly. */
    private static final double SLIP_MAX = 0.35, SLIP_ACCEL = 1.5;
    /** The tank's bank smoothing, per second, and a roll-rate limit, degrees per second. */
    private static final float BANK_SMOOTH_RATE = 4.0f;
    private static final float BANK_ROLL_RATE = 60f;
    private static final float WANDER_CLAMP = 2.5f;
    private static final float SQRT3 = 1.7320508f;
    private static final float BURST_ATTACK_RATE = 3.0f;
    private static final float BURST_DECAY_RATE = 0.8f;
    /** How far ahead (seconds of travel) the avoidance looks. */
    private static final double AVOID_LOOKAHEAD = 0.35;
    /** Whisker sweep: step and extent (degrees of yaw), pitch offsets tried at each, and furthest look. */
    private static final int WHISKER_STEP = 10, WHISKER_SWEEP = 140;
    private static final int[] WHISKER_PITCH = {0, -25, 25};
    private static final double WHISKER_MAX = 3.5;
    /** How close, blocks, the fish lets itself come to the owner's body when braking for it. */
    private static final double OWNER_GAP = 0.15;
    /** Braking: time for the deceleration to build (the jerk limit), and the share of maxForce planned on. */
    private static final double BRAKE_DELAY = 0.15, BRAKE_SHARE = 0.6;
    /** Avoidance strength grows with speed: at least this many times the current speed. */
    private static final double AVOID_SPEED_SCALE = 1.2;

    private final CompanionTunables t;
    final double radius;
    /** This step's non-solid obstacle (the owner's body), or null. */
    private Capsule obstacle;
    /** The side whisker steering last swung to (+1 right, −1 left), or 0 when the way was clear. */
    private int whiskerSide;

    Vec pos;
    Vec vel = Vec.ZERO;
    /** Measured acceleration over the last tick, blocks/s². */
    Vec acc = Vec.ZERO;
    double speed;
    private double forwardAccel;
    private double slipY;
    float yaw, pitch;
    /** Current turn rates, degrees per tick; positive yaw rate turns right. */
    float yawRate, pitchRate;
    /** Commanded and drawn bank, degrees; positive leans right (into a right turn). */
    float bankCmd, bank;
    float burstPhase, burstDrive = 1f;
    private final float burstStep;
    float wanderH, wanderV;
    private long noise;
    float tailPhase;
    /** Steps on which the hard resolve had to stop the body short of a block. */
    long backstops;
    boolean backstopLastStep;
    /** Speed lost to the last backstop, blocks/s — a touch is ~0, a crash is large. */
    double lastImpact;

    Body(CompanionTunables t, Vec pos, float yaw, double radius, long seed) {
        this.t = t;
        this.pos = pos;
        this.yaw = yaw;
        this.radius = radius;
        this.noise = seed == 0 ? 0x9E3779B97F4A7C15L : seed;
        float jitter = 1f + 0.25f * nextSigned();
        this.burstStep = (float) (DT / (t.burstPeriodSeconds * jitter));
        this.burstPhase = (nextSigned() + 1f) * 0.5f;
    }

    /**
     * One tick toward {@code desired}.
     *
     * @param wanderScale how much heading noise this goal tolerates (0 = none)
     * @param extraAvoid  soft pushes from things that aren't blocks (the owner's body), blocks/s
     * @param body        a non-solid obstacle to brake for (the owner), or null
     */
    void step(Vec desired, double wanderScale, Vec extraAvoid, LocalVoxels voxels, Capsule body) {
        this.obstacle = body;
        advanceWander();
        advanceBurst();

        // Heading noise bends the course sideways and a little vertically, in proportion to how
        // fast the goal wants to go — a fish hanging still doesn't spin on the spot.
        double dsp = desired.horizontalLength();
        if (wanderScale > 0 && dsp > 1e-4) {
            Vec side = new Vec(-desired.z() / dsp, 0, desired.x() / dsp);
            desired = desired.add(side.scale(wanderH * t.wanderAuthority * dsp * wanderScale))
                    .add(0, wanderV * t.wanderVertical * wanderScale, 0);
        }

        Vec ahead = pos.add(vel.scale(AVOID_LOOKAHEAD));
        Vec avoid = voxels.avoidance(pos, t.wallMargin + radius)
                .add(voxels.avoidance(ahead, t.wallMargin + radius).scale(0.6))
                .clampLength(1);
        double avoidGain = Math.max(t.wallAvoidSpeed, speed * AVOID_SPEED_SCALE);
        desired = desired.add(avoid.scale(avoidGain)).add(extraAvoid);

        desired = clearDirection(desired, voxels);
        steerHeading(desired);
        steerSpeed(desired, voxels);

        Vec fwd = Vec.fromYawPitch(yaw, pitch);
        double slipTarget = clamp(desired.y() - fwd.y() * speed, -SLIP_MAX, SLIP_MAX);
        slipY += clamp(slipTarget - slipY, -SLIP_ACCEL * DT, SLIP_ACCEL * DT);

        Vec before = vel;
        vel = fwd.scale(speed).add(0, slipY, 0);
        move(voxels, fwd);
        acc = vel.sub(before).scale(1 / DT);
        updateBank();

        tailPhase += (float) (speedFactor() * 2.0 * Math.PI * 1.6 * DT);
        if (tailPhase > Math.PI * 2) tailPhase -= (float) (Math.PI * 2);
    }

    private void steerHeading(Vec desired) {
        double dLen = desired.length();
        float targetYaw = yaw, targetPitch = 0f;
        if (dLen > HOLD_SPEED) {
            if (desired.horizontalLength() > 1e-6) targetYaw = (float) desired.yawDeg();
            targetPitch = clamp((float) -Math.toDegrees(Math.atan2(desired.y(), desired.horizontalLength())),
                    -t.pitchMaxDeg, t.pitchMaxDeg);
        }
        double sp = Math.max(speed, PIVOT_SPEED_FRACTION * t.cruiseSpeed);
        float rateMax = (float) Math.min(t.turnRateDegPerTick, Math.toDegrees(t.maxLateralAccel * DT / sp));
        float rateAccel = (float) Math.min(MAX_TURN_ACCEL, Math.toDegrees(t.maxJerk * DT * DT / sp));

        float yawCmd = clamp(wrap(targetYaw - yaw) * HEADING_GAIN, -rateMax, rateMax);
        yawRate += clamp(yawCmd - yawRate, -rateAccel, rateAccel);
        yaw = wrap(yaw + yawRate);

        float pitchMax = Math.min(rateMax, t.pitchRateDegPerTick);
        float pitchCmd = clamp((targetPitch - pitch) * HEADING_GAIN, -pitchMax, pitchMax);
        pitchRate += clamp(pitchCmd - pitchRate, -rateAccel, rateAccel);
        pitch = clamp(pitch + pitchRate, -t.pitchMaxDeg, t.pitchMaxDeg);
    }

    private void steerSpeed(Vec desired, LocalVoxels voxels) {
        double dLen = desired.length();
        Vec fwd = Vec.fromYawPitch(yaw, pitch);
        double target = 0;
        if (dLen > HOLD_SPEED) {
            double align = fwd.dot(desired.scale(1 / dLen));
            // Full speed when facing the way it wants to go; slow and turn tight when it doesn't.
            target = align > 0 ? dLen * (0.3 + 0.7 * align) : Math.min(dLen, 0.3 * t.cruiseSpeed);
        }
        // Never faster than it can stop before whatever is ahead: the body can't sidestep, so
        // braking in time is what keeps it off walls (the hard resolve is only a backstop).
        double reach = 0.4 + speed * speed / (2 * t.maxForce) + speed * (BRAKE_DELAY + 0.1);
        double free = freeDistance(voxels, fwd, reach);
        if (vel.length() > 1e-3) free = Math.min(free, freeDistance(voxels, vel.normalize(), reach));
        free = Math.min(free, arcFreeDistance(voxels, reach));
        if (free < reach) {
            double room = Math.max(0, free - 0.15 - speed * BRAKE_DELAY);
            target = Math.min(target, Math.sqrt(2 * t.maxForce * BRAKE_SHARE * room));
        }
        target = Math.min(target, ownerSpeedLimit(fwd));
        target = Math.min(target, t.maxSpeed);
        double cmd = clamp((target - speed) * t.steeringGain, -t.maxForce, t.maxForce);
        forwardAccel += clamp(cmd - forwardAccel, -t.maxJerk * DT, t.maxJerk * DT);
        speed += forwardAccel * DT;
        if (speed < 0) {
            speed = 0;
            forwardAccel = Math.max(0, forwardAccel);
        }
    }

    /**
     * Whisker steering: when the straight line toward {@code desired} isn't clear for as far as the
     * fish needs to stop, the nearest direction that is — swinging yaw first, then pitch too. A
     * heading-first body can't sidestep, so pushes alone would let it turn into a trunk it wants
     * to get past. The side it swung to last time is tried first and kept while the way is still
     * blocked, so it commits to going round one side instead of dithering between the two.
     */
    private Vec clearDirection(Vec desired, LocalVoxels voxels) {
        double dLen = desired.length();
        if (!t.whiskers || dLen < HOLD_SPEED) return desired;
        Vec dir = desired.scale(1 / dLen);
        double sp = Math.max(speed, dLen);
        double need = Math.min(WHISKER_MAX, 0.5 + sp * sp / (2 * t.maxForce) + sp * (BRAKE_DELAY + 0.1));
        double directFree = freeDistance(voxels, dir, need);
        if (directFree >= need) {
            whiskerSide = 0;
            return desired;
        }
        // Nothing needs to be fully clear to be useful: in a tunnel no direction is, and the one
        // with the most room (along it) is the right answer. So take the smallest swing that is
        // fully clear, and failing that the direction with the most room.
        int first = whiskerSide != 0 ? whiskerSide : (yawRate >= 0 ? 1 : -1);
        Vec roomiest = dir;
        double mostRoom = directFree;
        int roomiestSide = 0;
        for (int angle = WHISKER_STEP; angle <= WHISKER_SWEEP; angle += WHISKER_STEP) {
            for (int k = 0; k < 2; k++) {
                int side = k == 0 ? first : -first;
                for (int pitchOffset : WHISKER_PITCH) {
                    Vec cand = rotate(dir, side * angle, pitchOffset);
                    double free = freeDistance(voxels, cand, need);
                    if (free >= need) {
                        whiskerSide = side;
                        return cand.scale(dLen);
                    }
                    // A swing has to buy real room to beat a smaller one.
                    if (free > mostRoom + 0.1 * angle / WHISKER_STEP) {
                        mostRoom = free;
                        roomiest = cand;
                        roomiestSide = side;
                    }
                }
            }
        }
        whiskerSide = roomiestSide;
        return roomiest.scale(dLen);
    }

    /** {@code dir} turned by {@code yawDeg} about the vertical and tilted by {@code pitchDeg}. */
    private static Vec rotate(Vec dir, double yawDeg, double pitchDeg) {
        double h = dir.horizontalLength();
        double yaw = (h > 1e-6 ? dir.yawDeg() : 0) + yawDeg;
        double pitch = -Math.toDegrees(Math.atan2(dir.y(), h)) + pitchDeg;
        return Vec.fromYawPitch(yaw, Math.max(-80, Math.min(80, pitch)));
    }

    /**
     * Like {@link #freeDistance} but along the arc the body is actually on: the current turn rate
     * held. A straight probe misses the side of a trunk that a turning fish is swinging into.
     */
    private double arcFreeDistance(LocalVoxels voxels, double reach) {
        if (Math.abs(yawRate) < 0.5f && Math.abs(pitchRate) < 0.5f) return reach;
        double step = 0.1;
        double perStep = Math.max(speed, 0.5) * DT; // distance covered per tick on the arc
        double y = yaw, p = pitch;
        Vec at = pos;
        for (double d = step; d <= reach; d += step) {
            double ticks = step / perStep;
            y += yawRate * ticks;
            p = Math.max(-t.pitchMaxDeg, Math.min(t.pitchMaxDeg, p + pitchRate * ticks));
            at = at.add(Vec.fromYawPitch(y, p).scale(step));
            if (!voxels.sphereClear(at, radius + 0.02)) return d - step;
        }
        return reach;
    }

    /** How far the body can travel along {@code dir} before touching a block, up to {@code reach}. */
    private double freeDistance(LocalVoxels voxels, Vec dir, double reach) {
        double step = 0.1;
        for (double d = step; d <= reach; d += step) {
            if (!voxels.sphereClear(pos.add(dir.scale(d)), radius + 0.02)) return d - step;
        }
        return reach;
    }

    /**
     * The fastest the fish may swim along {@code fwd} and still stop short of the owner's body —
     * judged on the motion relative to the owner, who moves too: a fish keeping pace beside or
     * behind someone walking is closing on nothing, so nothing slows it.
     */
    private double ownerSpeedLimit(Vec fwd) {
        if (obstacle == null) return Double.MAX_VALUE;
        Vec rel = fwd.scale(speed).sub(obstacle.velocity());
        double relSp = rel.length();
        if (relSp < 1e-3) return Double.MAX_VALUE;
        Vec dir = rel.scale(1 / relSp);
        double reach = 0.4 + relSp * relSp / (2 * t.maxForce) + relSp * (BRAKE_DELAY + 0.1);
        double startGap = obstacle.distance(pos) - radius;
        double step = 0.1;
        double free = reach;
        for (double d = step; d <= reach; d += step) {
            double gap = obstacle.distance(pos.add(dir.scale(d))) - radius;
            // Already close: only moving further in counts, or the fish could never leave.
            if (gap < OWNER_GAP && gap < startGap) {
                free = d - step;
                break;
            }
        }
        if (free >= reach) return Double.MAX_VALUE;
        double room = Math.max(0, free - relSp * BRAKE_DELAY);
        double allowedClosing = Math.sqrt(2 * t.maxForce * BRAKE_SHARE * room);
        return Math.max(0, obstacle.velocity().dot(fwd) + allowedClosing);
    }

    /** Moves by one tick of velocity, stopping short per axis wherever a block is in the way. */
    private void move(LocalVoxels voxels, Vec fwd) {
        backstopLastStep = false;
        lastImpact = 0;
        Vec next = pos.add(vel.scale(DT));
        if (voxels.sphereClear(next, radius) || !voxels.sphereClear(pos, radius)) {
            // Clear ahead — or already overlapping (a block was placed on the fish), where the
            // only useful thing is to keep moving and let avoidance carry it out.
            pos = next;
            return;
        }
        backstops++;
        backstopLastStep = true;
        Vec hit = vel;
        double x = pos.x(), y = pos.y(), z = pos.z();
        double vx = vel.x(), vy = vel.y(), vz = vel.z();
        if (voxels.sphereClear(new Vec(next.x(), y, z), radius)) x = next.x(); else vx = 0;
        if (voxels.sphereClear(new Vec(x, next.y(), z), radius)) y = next.y(); else vy = 0;
        if (voxels.sphereClear(new Vec(x, y, next.z()), radius)) z = next.z(); else vz = 0;
        pos = new Vec(x, y, z);
        vel = new Vec(vx, vy, vz);
        lastImpact = hit.sub(vel).length();
        speed = Math.max(0, vel.dot(fwd));
        forwardAccel = Math.min(0, forwardAccel);
        slipY = clamp(vy - fwd.y() * speed, -SLIP_MAX, SLIP_MAX);
    }

    private void updateBank() {
        if (speed > HOLD_SPEED) {
            bankCmd = clamp(yawRate / t.turnRateDegPerTick, -1f, 1f) * t.bankMaxDeg;
        } else {
            bankCmd *= 0.9f;
        }
        float roll = (bankCmd - bank) * BANK_SMOOTH_RATE * (float) DT;
        float maxRoll = BANK_ROLL_RATE * (float) DT;
        bank += clamp(roll, -maxRoll, maxRoll);
    }

    private void advanceWander() {
        float k = t.wanderSigma * (float) Math.sqrt(DT) * SQRT3;
        float decay = t.wanderTheta * (float) DT;
        wanderH = clamp(wanderH - wanderH * decay + k * nextSigned(), -WANDER_CLAMP, WANDER_CLAMP);
        wanderV = clamp(wanderV - wanderV * decay + k * nextSigned(), -WANDER_CLAMP, WANDER_CLAMP);
    }

    private void advanceBurst() {
        burstPhase += burstStep;
        if (burstPhase >= 1f) burstPhase -= 1f;
        boolean thrusting = burstPhase < t.burstDuty;
        float target = thrusting ? t.burstThrust : t.burstCoast;
        float rate = thrusting ? BURST_ATTACK_RATE : BURST_DECAY_RATE;
        burstDrive += (target - burstDrive) * rate * (float) DT;
    }

    /** The animator's tail-beat multiplier: the tank's 0.6 + 0.9·normalized speed. */
    float speedFactor() {
        double normalized = Math.min(speed / (2 * t.cruiseSpeed), 1.6);
        return (float) (0.6 + 0.9 * normalized);
    }

    /** Places the body somewhere new, at rest (the fade-in after a leash). */
    void placeAt(Vec p, float facingYaw) {
        pos = p;
        vel = Vec.ZERO;
        acc = Vec.ZERO;
        speed = 0;
        forwardAccel = 0;
        slipY = 0;
        yaw = facingYaw;
        pitch = 0f;
        yawRate = pitchRate = 0f;
        bank = bankCmd = 0f;
    }

    /** One xorshift64 draw from this body's private noise stream, in [−1, 1). */
    float nextSigned() {
        long x = noise;
        x ^= x << 13;
        x ^= x >>> 7;
        x ^= x << 17;
        noise = x;
        return ((x >>> 40) * (1f / 8388608f)) - 1f;
    }

    static float wrap(float deg) {
        deg %= 360f;
        if (deg >= 180f) deg -= 360f;
        if (deg < -180f) deg += 360f;
        return deg;
    }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
