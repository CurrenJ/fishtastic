package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;

/**
 * A benthic companion's body on the ground (docs/fish-companions-benthic-follow.md §4): a walk on
 * the floor with the same heading-first rules as the swimmers' {@link Body}.
 *
 * <ul>
 *   <li><b>Heading first.</b> It travels where it faces: yaw turns toward where it wants to go at a
 *       capped rate (tighter at speed, from a sideways-acceleration cap), forward speed chases the
 *       wanted speed under acceleration and jerk caps, and it slows to turn when the way it wants
 *       to go is off to the side. It never walks backwards or sideways.</li>
 *   <li><b>The floor.</b> Its height is the floor under it: the highest surface under its
 *       footprint (centre and four points round it), so it steps off an edge only once all of it
 *       is past, and meets a step before its body would. Rises of a hair (a carpet) are walked;
 *       anything up to a block is hopped; more is a wall. A drop of up to {@link #MAX_DROP} is
 *       walked off and fallen; more is an edge it keeps back from.</li>
 *   <li><b>Hops and falls</b> are ballistic under {@link PerchBody#GRAVITY}. A half-block hop is
 *       taken on the move; a full block wants a stop and a crouch first. A landing squashes.</li>
 *   <li><b>Rhythm.</b> Speed comes in a rhythm, from the tank's crawl: an idle scuttle-and-pause,
 *       a steady trot behind a walking owner with a little surge in it, and bursts to catch up.
 *       The plaice ({@link GroundGait#RIPPLE}) glides on a smoother, slower rhythm.</li>
 * </ul>
 *
 * Every block the walk can't take is refused before it moves, so it is never inside one; the
 * owner isn't a block, and the controller steers round them.
 */
final class GroundBody extends Motor {

    enum Phase { WALK, CROUCH, AIR, LAND }

    /** How it is getting about: idling round its owner, following them, or catching up. */
    enum Rhythm { AMBLE, TROT, SCURRY }

    static final double GRAVITY = PerchBody.GRAVITY;
    /** Cruising speed for a body 0.3 blocks long, blocks/s; bigger bodies go faster (by √length). */
    static final double BASE_CRUISE = 3.6;
    /** The tallest rise walked without a hop (a carpet, a path block), and the tallest hopped. */
    static final double STEP_SMOOTH = 0.13, STEP_HOP = 1.0;
    /** Rises up to this are hopped on the move; taller ones want a stop and a crouch. */
    static final double STEP_MOVING = 0.55;
    /** How far short of a riser it stops to crouch, blocks: room for the arc to clear the edge. */
    static final double STANDOFF = 0.12;
    /** How far above a low step a moving hop peaks, blocks. */
    static final double MOVING_CLEAR = 0.15;
    /** The deepest drop walked off; deeper is an edge. */
    static final double MAX_DROP = 3.0;
    /** Forward acceleration and braking caps, blocks/s², and the jerk cap, blocks/s³. */
    private static final double ACCEL = 9, DECEL = 11, JERK = 90;
    /** Turn-rate cap, degrees/tick (under the swimmers' 7), its rate of change, and the sideways-acceleration cap. */
    private static final float TURN_RATE = 6.5f, TURN_ACCEL = 1.2f;
    private static final double LATERAL_ACCEL = 9;
    /** Speed below which turning is costed as if at this speed: a crab pivots cheaply. */
    private static final double PIVOT_SPEED = 0.6;
    private static final float HEADING_GAIN = 0.2f;
    /** Ticks of squash after a landing; ticks of crouch before a standing hop at pace 1. */
    private static final int LAND_TICKS = 4, CROUCH_TICKS = 4;
    private static final float WANDER_SIGMA = 0.25f, WANDER_THETA = 1.2f, WANDER_CLAMP = 2.5f;
    private static final float SQRT3 = 1.7320508f;

    final GroundGait gait;
    /** Size pacing: 1 at 0.3 blocks long. */
    final double pace;
    /** The temperament's pace (spec §4). */
    double tempo = 1;

    Phase phase = Phase.WALK;
    int phaseTicks;
    /** The floor height under the body (its footprint's highest point) while on it. */
    double floorY;
    private double forwardAccel;
    /** Where a hop is going, its flight time and time flown; null when not hopping. */
    private Vec hopLanding;
    private double hopRise;
    private int stepCooldown;
    /** 0..1 squash for the animator: the crouch before a hop and the landing after. */
    float squash;
    /** 0..1 push-off envelope, the tank's crawl deformation: rises as it sets off, relaxes fast. */
    float shape;
    private float driveSlow;
    private float dwellPhase;
    private final float dwellJitter;
    private float wander;
    /** Launch and touchdown ticks: the body's velocity jumps there by design (an impulse, not a snap). */
    boolean impulse;
    /** The last velocity change at a launch or touchdown, blocks/s. */
    double lastImpulse;
    private long noise;

    GroundBody(Locomotion locomotion, GroundGait gait, Vec pos, float yaw, double radius, float length, long seed) {
        super(locomotion, null, pos, radius);
        this.gait = gait;
        this.yaw = yaw;
        this.pace = Math.sqrt(Math.max(0.1, Math.min(0.8, length)) / 0.3);
        this.noise = seed == 0 ? 0x9E3779B97F4A7C15L : seed;
        this.dwellJitter = 1f + 0.25f * nextSigned();
        this.dwellPhase = (nextSigned() + 1f) * 0.5f;
    }

    @Override
    void step(Vec desired, double wanderScale, Vec extraAvoid, LocalVoxels voxels, Capsule body) {
        throw new UnsupportedOperationException("a ground body walks to a target: use walk(...)");
    }

    /** Cruising speed: by size and temperament. */
    double cruise() {
        return BASE_CRUISE * pace * tempo * (gait == GroundGait.RIPPLE ? 0.85 : 1.0);
    }

    /**
     * What its rhythm's speed is a multiple of: the cruise, except that a slow temperament still
     * scurries at full pace when it has been left behind (its pace is how it ambles and trots).
     */
    double rhythmBase(Rhythm r) {
        return r == Rhythm.SCURRY ? cruise() / tempo * Math.max(1, tempo) : cruise();
    }

    /** Where it stands: its position on the floor (walk targets are floor points). */
    Vec standing() {
        return new Vec(pos.x(), floorY, pos.z());
    }

    /** Whether it is standing or walking on the floor (not crouching for a hop, in the air or landing). */
    boolean onFloor() {
        return phase == Phase.WALK || phase == Phase.LAND;
    }

    /** Whether it touches the floor this tick (on it, crouching or landing): not in the air. */
    boolean grounded() {
        return phase != Phase.AIR;
    }

    /**
     * One tick: walk toward {@code target} (a point on the floor: its height says how far down
     * it may step off an edge on the way) at up to {@code wantSpeed},
     * in {@code rhythm}; {@code push} bends the way it heads (blocks/s, horizontal), and
     * {@code faceYaw} (NaN for none) is where to face once it has stopped.
     */
    void walk(Vec target, double wantSpeed, Rhythm rhythm, Vec push, float faceYaw, LocalVoxels v) {
        phaseTicks++;
        impulse = false;
        lastImpulse = 0;
        backstopLastStep = false;
        lastImpact = 0;
        Vec before = vel;
        advanceRhythm(rhythm);
        advanceWander();
        switch (phase) {
            case AIR -> fly(v);
            case CROUCH -> crouch(v);
            case WALK, LAND -> stride(target, wantSpeed, rhythm, push, faceYaw, v);
        }
        acc = vel.sub(before).scale(1 / DT);
        double legRate = gait == GroundGait.RIPPLE ? 0.9 + 1.4 * speed / Math.max(0.2, pace) : speed / (0.25 * pace);
        tailPhase += (float) (Math.min(legRate, 6.0) * 2 * Math.PI * DT);
        if (tailPhase > Math.PI * 2) tailPhase -= (float) (Math.PI * 2);
    }

    // ── On the floor ───────────────────────────────────────────────────────────────────────────

    /**
     * The lowest floor it will walk off onto this tick: anywhere it could hop back up from, or, if
     * where it's going is down there, as far as {@link #MAX_DROP}. (Off the side of a boardwalk it
     * couldn't climb back onto, it would be stranded.)
     */
    private double dropFloor;

    private boolean floorOk(double f) {
        return !Double.isNaN(f) && f >= dropFloor;
    }

    /** Whether walking off an edge at {@code p} (onto floor {@code f}) would come down on {@link #keepClear}. */
    private boolean landsUnderfoot(Vec p, Vec fwd, double f) {
        if (keepClear == null || f >= floorY - STEP_SMOOTH) return false;
        double t = Math.sqrt(2 * (floorY - f) / GRAVITY);
        return p.add(fwd.scale(speed * t)).sub(keepClear).horizontalLength() < radius + 0.45;
    }

    /** Somewhere not to come down on (the owner's feet), or null. */
    Vec keepClear;
    /** Formation members stop at an unrequested drop so their successors can keep this path. */
    boolean preventUnplannedDrop;
    /** Escape from approaching feet skips the idle rhythm's delay; acceleration stays bounded. */
    boolean urgent;

    private void stride(Vec target, double wantSpeed, Rhythm rhythm, Vec push, float faceYaw, LocalVoxels v) {
        // Down off an edge only when where it's going is down there: wandering along a raised
        // walk it keeps to it, rather than stepping off somewhere it may not get back up from.
        dropFloor = target.y() < floorY - 0.25 ? floorY - MAX_DROP - 0.04 : floorY - STEP_SMOOTH - 0.02;
        if (phase == Phase.LAND) {
            squash = Math.max(0f, squash - 1f / LAND_TICKS);
            if (phaseTicks >= LAND_TICKS) enter(Phase.WALK);
        } else {
            squash = Math.max(0f, squash - 0.2f);
        }
        Vec to = new Vec(target.x() - pos.x(), 0, target.z() - pos.z());
        double dist = to.length();
        Vec want = dist > 1e-6 ? to.scale(1 / dist) : Vec.ZERO;
        // A gentle sideways bend of the course (the crawl's wander) while it travels.
        if (dist > 0.3 && wantSpeed > 0.3) {
            want = want.add(new Vec(-want.z(), 0, want.x()).scale(wander * 0.12 * (rhythm == Rhythm.AMBLE ? 1.5 : 1.0)));
        }
        if (push != null && push.lengthSqr() > 1e-9) want = want.scale(Math.max(wantSpeed, 0.5)).add(push);
        double wLen = want.horizontalLength();
        float targetYaw = yaw;
        boolean going = dist > 0.08 && wantSpeed > 0.05 || (push != null && push.lengthSqr() > 0.04);
        if (going && wLen > 1e-6) targetYaw = (float) want.yawDeg();
        else if (!Float.isNaN(faceYaw)) targetYaw = faceYaw;

        // Round blocks: if the way it wants to go is blocked close ahead, the nearest clear way.
        if (going) targetYaw = clearYaw(targetYaw, v);
        turnToward(targetYaw);

        Vec fwd = Vec.fromYawPitch(yaw, 0);
        double target2 = 0;
        if (going) {
            double env = envelope(rhythm);
            double arrive = Math.sqrt(2 * DECEL * 0.6 * Math.max(0, dist - 0.05));
            target2 = Math.min(Math.min(wantSpeed, rhythmBase(rhythm) * env), arrive);
            if (push != null && push.lengthSqr() > 0.04) target2 = Math.max(target2, Math.min(cruise(), push.length()));
            double err = Math.abs(Body.wrap(targetYaw - yaw));
            // Off to the side: slow and turn tight; behind: nearly stop and pivot.
            if (err > 30) target2 *= Math.max(0.1, Math.cos(Math.toRadians(Math.min(err, 85))));
        }
        target2 = Math.min(target2, aheadLimit(fwd, v));
        if (Math.abs(yawRate) > 0.5f) {
            // Turning, it sweeps across what's beside its nose: look where the turn is taking it too.
            target2 = Math.min(target2, aheadLimit(Vec.fromYawPitch(yaw + yawRate * 4, 0), v));
        }

        // A step up ahead is planned for, not met: from far enough back a low step is hopped on
        // the move; otherwise it brakes to a stop short of the riser, crouches and hops. (Walked up
        // to touching it, no arc clears the riser's edge.)
        if (stepCooldown > 0) stepCooldown--;
        double riser = Double.NaN, riserFloor = Double.NaN;
        if (going && (phase == Phase.WALK || phase == Phase.LAND)) {
            double look = radius + standoff(STEP_HOP) + speed * speed / (2 * 0.6 * DECEL) + speed * 0.25 + 0.1;
            for (double d = 0.04; d <= look + 1e-9; d += 0.04) {
                Vec p = pos.add(fwd.scale(d));
                double f = footprintFloor(v, p.x(), p.z());
                if (!floorOk(f)) break;
                if (f - floorY > STEP_SMOOTH) {
                    riser = d;
                    riserFloor = f;
                    break;
                }
            }
        }
        if (!Double.isNaN(riser)) {
            double rise = riserFloor - floorY;
            double gap = riser - standoff(rise);
            // On the move, it bounds up the step without stopping if an arc from where it would
            // leave is clear and lands on the step; otherwise it stops short and crouches.
            // Cooling down after a step it couldn't take, it still brakes for one; it just doesn't hop.
            // Finish steering toward the route before committing to a ballistic heading.
            // A hop launched while still turning can carry it off a terrace's side.
            boolean linedUp = Math.abs(Body.wrap(targetYaw - yaw)) < 20;
            boolean bound = speed > 0.8 && stepCooldown == 0 && linedUp;
            if (bound) {
                double launchGap = movingLaunchGap(rise);
                double toLaunch = riser - launchGap;
                if (toLaunch <= speed * DT) {
                    if (tryMovingHop(pos, fwd, riser, riserFloor, v, true)) return;
                    bound = false;
                } else if (!tryMovingHop(pos.add(fwd.scale(toLaunch)), fwd, launchGap, riserFloor, v, false)) {
                    bound = false;
                }
            }
            if (!bound) {
                // Stop short of it, then crouch and hop.
                target2 = Math.min(target2, stopSpeed(gap));
                if (gap < 0.04 && speed < 0.3 && stepCooldown == 0 && linedUp) {
                    Vec landing = pos.add(fwd.scale(riser + 2 * radius + 0.06));
                    double lf = footprintFloor(v, landing.x(), landing.z());
                    // Like walking, a step landing rests on the highest footprint surface.
                    // Requiring every column to support it strands diagonal approaches at corners;
                    // launch() still checks the entire flight and landing for block clearance.
                    if (!Double.isNaN(lf) && Math.abs(lf - riserFloor) < 0.02) {
                        hopLanding = new Vec(landing.x(), lf + radius + LocalVoxels.GROUND_LIFT, landing.z());
                        hopRise = rise;
                        enter(Phase.CROUCH);
                        squash = Math.max(squash, 0.1f);
                        return;
                    }
                    stepCooldown = 20; // too narrow a step to land on (or along its side): try another way
                    avoid(yaw);
                }
            }
        }

        double cmd = Body.clamp((target2 - speed) * 6, -DECEL, ACCEL);
        forwardAccel += Body.clamp(cmd - forwardAccel, -JERK * DT, JERK * DT);
        speed += forwardAccel * DT;
        if (speed < 0) {
            speed = 0;
            forwardAccel = Math.max(0, forwardAccel);
        }

        Vec next = pos.add(fwd.scale(speed * DT));
        double fNext = footprintFloor(v, next.x(), next.z());
        Vec guard = next.add(fwd.scale(STANDOFF * 0.5));
        double fGuard = footprintFloor(v, guard.x(), guard.z());
        if (!Double.isNaN(fGuard) && fGuard - floorY > STEP_SMOOTH && !Double.isNaN(fNext) && fNext - floorY <= STEP_SMOOTH) {
            // Closing on a riser it hasn't planned a hop for: hold off, never up against it, and
            // look for another way. Stopped short at speed, that's a contact.
            avoid(yaw);
            if (speed > 0.3) {
                backstops++;
                backstopLastStep = true;
                lastImpact = speed;
            }
            speed = 0;
            forwardAccel = 0;
            vel = Vec.ZERO;
            return;
        }
        if (!preventUnplannedDrop && !Double.isNaN(fNext) && fNext < dropFloor && fNext >= floorY - MAX_DROP - 0.04
                && speed > stopSpeed(0.1)) {
            // An edge it chose not to step off only just now (it changed its mind about where to
            // go), too close to stop at: it goes over rather than slamming to a halt.
            dropFloor = fNext;
        }
        if (!floorOk(fNext) || fNext - floorY > STEP_SMOOTH) {
            // A wall, an edge, or a step it didn't see coming: refuse the move outright.
            if (speed > 0.3) {
                backstops++;
                backstopLastStep = true;
                lastImpact = speed;
            }
            speed = 0;
            forwardAccel = 0;
            vel = Vec.ZERO;
            return;
        }
        if (fNext < floorY - STEP_SMOOTH && keepClear != null) {
            // Not off an edge onto someone's feet: where the fall would come down.
            double t = Math.sqrt(2 * (floorY - fNext) / GRAVITY);
            Vec land = next.add(fwd.scale(speed * t));
            if (land.sub(keepClear).horizontalLength() < radius + 0.45 && speed <= stopSpeed(0.1)) {
                speed = 0;
                forwardAccel = 0;
                vel = Vec.ZERO;
                return;
            }
        }
        if (fNext < floorY - STEP_SMOOTH) {
            // Off an edge: fall from here, keeping its pace.
            pos = next;
            vel = fwd.scale(speed);
            hopLanding = null;
            enter(Phase.AIR);
            return;
        }
        Vec placed = new Vec(next.x(), fNext + radius + LocalVoxels.GROUND_LIFT, next.z());
        if (!v.sphereClear(placed, radius)) {
            // Something overhead it can't get under (a low lintel).
            if (speed > 0.3) {
                backstops++;
                backstopLastStep = true;
                lastImpact = speed;
            }
            speed = 0;
            forwardAccel = 0;
            vel = Vec.ZERO;
            return;
        }
        Vec was = vel;
        vel = placed.sub(pos).scale(1 / DT);
        if (Math.abs(fNext - floorY) > 1e-3) {
            // Onto or off something a hair high (a carpet, a path block): the feet step, the body
            // snaps that much in a tick. A step, like a touchdown, not acceleration.
            impulse = true;
            lastImpulse = Math.max(lastImpulse, vel.sub(was).length());
        }
        pos = placed;
        floorY = fNext;
        updateShape();
    }

    /**
     * How fast it may go along {@code fwd} and still stop short of the first thing it can't walk
     * onto (a wall, an edge, something too tall to hop).
     */
    private double aheadLimit(Vec fwd, LocalVoxels v) {
        double reach = radius + 0.2 + speed * speed / (2 * DECEL) + speed * 0.1;
        for (double d = 0.15; d <= reach + 1e-9; d += 0.15) {
            Vec p = pos.add(fwd.scale(d));
            double f = footprintFloor(v, p.x(), p.z());
            if (!floorOk(f) || landsUnderfoot(p, fwd, f)) {
                return stopSpeed(Math.max(0, d - 0.15 - radius * 0.5));
            }
        }
        return Double.MAX_VALUE;
    }

    /**
     * {@code wantYaw}, or if the floor that way is blocked within a block, the nearest heading that
     * isn't — the side it turned to last tried first, so it commits to going round one way.
     */
    private int whiskerSide;

    /** A heading that just failed it (a step it couldn't take from here), avoided for a second. */
    private float avoidYaw = Float.NaN;
    private int avoidTicks;

    private void avoid(float headingYaw) {
        avoidYaw = headingYaw;
        avoidTicks = 20;
    }

    private boolean avoided(float cand) {
        return avoidTicks > 0 && Math.abs(Body.wrap(cand - avoidYaw)) < 25f;
    }

    private float clearYaw(float wantYaw, LocalVoxels v) {
        if (avoidTicks > 0) avoidTicks--;
        if (!avoided(wantYaw) && walkableFor(wantYaw, 0.9, v)) {
            whiskerSide = 0;
            return wantYaw;
        }
        int first = whiskerSide != 0 ? whiskerSide : (Body.wrap(wantYaw - yaw) >= 0 ? 1 : -1);
        for (int a = 20; a <= 140; a += 20) {
            for (int k = 0; k < 2; k++) {
                int side = k == 0 ? first : -first;
                float cand = Body.wrap(wantYaw + side * a);
                if (!avoided(cand) && walkableFor(cand, 0.9, v)) {
                    whiskerSide = side;
                    return cand;
                }
            }
        }
        return wantYaw;
    }

    private boolean walkableFor(float yawDeg, double dist, LocalVoxels v) {
        return walkableFor(yawDeg, dist, v, false);
    }

    /** Whether it can walk {@code dist} that way; with {@code level}, without stepping up or down. */
    private boolean walkableFor(float yawDeg, double dist, LocalVoxels v, boolean level) {
        Vec d = Vec.fromYawPitch(yawDeg, 0);
        double prev = floorY;
        // From right in front of it (as the brake looks), not a step out: a trunk's corner can be
        // in the way at 0.15 and clear again by 0.25.
        for (double s = 0.05; s <= dist + 1e-9; s += s < 0.4 ? 0.05 : 0.15) {
            Vec p = pos.add(d.scale(s));
            double f = footprintFloorFrom(v, p.x(), p.z(), prev);
            if (!floorOk(f)) return false;
            if (level && Math.abs(f - prev) > STEP_SMOOTH) return false;
            prev = f;
        }
        return true;
    }

    private void turnToward(float targetYaw) {
        double sp = Math.max(speed, PIVOT_SPEED);
        float rateMax = (float) Math.min(TURN_RATE, Math.toDegrees(LATERAL_ACCEL * DT / sp));
        float cmd = Body.clamp(Body.wrap(targetYaw - yaw) * HEADING_GAIN, -rateMax, rateMax);
        yawRate += Body.clamp(cmd - yawRate, -TURN_ACCEL, TURN_ACCEL);
        yawRate = Body.clamp(yawRate, -rateMax, rateMax);
        yaw = Body.wrap(yaw + yawRate);
        pitch = 0f;
        bank = 0f;
    }

    // ── Hops and falls ─────────────────────────────────────────────────────────────────────────

    /**
     * The fastest it can go and still stop within {@code room}: braking at 60% of its cap, after
     * the jerk cap's lag in building it up.
     */
    private static double stopSpeed(double room) {
        double a = 0.6 * DECEL, lag = 0.25;
        return a * (-lag + Math.sqrt(lag * lag + 2 * Math.max(0, room) / a));
    }

    /** How far short of a riser of this height it stops to crouch: a taller one needs more room to clear. */
    private static double standoff(double rise) {
        return STANDOFF + 0.2 * rise;
    }

    /** Gap to a riser, blocks, at which a moving hop of this rise must leave to clear its edge. */
    private double movingLaunchGap(double rise) {
        double vy = Math.sqrt(2 * GRAVITY * (rise + MOVING_CLEAR));
        double disc = vy * vy - 2 * GRAVITY * (rise + 0.02);
        double tUp = (vy - Math.sqrt(Math.max(0, disc))) / GRAVITY;
        return speed * tUp + 0.05;
    }

    /**
     * A step taken on the move from {@code from}, {@code riser} short of its edge: it leaves at its
     * own pace and comes down on the step about as far on as that pace carries it, or failing a
     * clear arc there, just past the edge. With {@code go} false it only says whether it could.
     */
    private boolean tryMovingHop(Vec from, Vec fwd, double riser, double riserFloor, LocalVoxels v, boolean go) {
        double rise = riserFloor - floorY;
        double vy = Math.sqrt(2 * GRAVITY * (rise + MOVING_CLEAR));
        double T = (vy + Math.sqrt(Math.max(0, vy * vy - 2 * GRAVITY * rise))) / GRAVITY;
        double[] reaches = {Math.max(speed * T, riser + 2 * radius + 0.06), riser + 2 * radius + 0.06};
        for (int k = 0; k < reaches.length; k++) {
            double reach = reaches[k];
            Vec land = from.add(fwd.scale(reach));
            double lf = footprintFloorFrom(v, land.x(), land.z(), floorY);
            if (Double.isNaN(lf) || Math.abs(lf - riserFloor) > 0.02) continue;
            if (k == 0) {
                // Landing on the run wants room to run on: not right under the next step.
                Vec on = from.add(fwd.scale(reach + standoff(STEP_HOP) + 0.1));
                double of = footprintFloorFrom(v, on.x(), on.z(), lf);
                if (Double.isNaN(of) || Math.abs(of - lf) > STEP_SMOOTH) continue;
            }
            Vec landing = new Vec(land.x(), lf + radius + LocalVoxels.GROUND_LIFT, land.z());
            Vec start = new Vec(from.x(), pos.y(), from.z());
            double t = arcTime(start, landing, rise + MOVING_CLEAR, v);
            if (t < 0) continue;
            if (go) {
                hopLanding = landing;
                hopRise = rise;
                launchArc(t, rise + MOVING_CLEAR);
            }
            return true;
        }
        return false;
    }

    /**
     * Hops to {@code landing} (a body centre resting on a floor) from a crouch: a gap, or onto
     * something the controller has chosen. False if no clear arc reaches it or it's out of range.
     */
    boolean hopTo(Vec landing, LocalVoxels v) {
        if (!onFloor() || landing.distanceTo(pos) > PerchBody.HOP_RANGE) return false;
        double rise = landing.y() - pos.y();
        if (rise > STEP_HOP + 0.05) return false;
        if (arcTime(pos, landing, Math.max(0, rise) + 0.35, v) < 0) return false;
        hopLanding = landing;
        hopRise = rise;
        enter(Phase.CROUCH);
        return true;
    }

    private void crouch(LocalVoxels v) {
        // Brake, face the landing, squash; then go.
        double cmd = Body.clamp(-speed * 6, -DECEL, ACCEL);
        forwardAccel += Body.clamp(cmd - forwardAccel, -JERK * DT, JERK * DT);
        speed = Math.max(0, speed + forwardAccel * DT);
        Vec to = hopLanding.sub(pos);
        if (to.horizontalLength() > 1e-3) turnToward((float) to.yawDeg());
        Vec fwd = Vec.fromYawPitch(yaw, 0);
        Vec next = pos.add(fwd.scale(speed * DT));
        double fNext = footprintFloor(v, next.x(), next.z());
        if (!Double.isNaN(fNext) && Math.abs(fNext - floorY) <= STEP_SMOOTH) {
            pos = new Vec(next.x(), fNext + radius + LocalVoxels.GROUND_LIFT, next.z());
            floorY = fNext;
            vel = fwd.scale(speed);
        } else {
            speed = 0;
            vel = Vec.ZERO;
        }
        int ticks = gait == GroundGait.RIPPLE ? 2 : (int) Math.round(CROUCH_TICKS * pace);
        squash = Math.min(1f, squash + 1f / Math.max(1, ticks));
        float err = Math.abs(Body.wrap((float) to.yawDeg() - yaw));
        if (phaseTicks >= ticks && speed < 0.4 && (to.horizontalLength() < 1e-3 || err < 4f)) launch(v);
        else if (phaseTicks > 40) {
            hopLanding = null; // couldn't line it up: give up and walk on
            enter(Phase.WALK);
        }
    }

    /** Leaves the floor on an arc that clears the blocks to {@link #hopLanding}. */
    private void launch(LocalVoxels v) {
        double apex = Math.max(0, hopRise) + 0.3;
        double T = -1;
        for (int k = 0; k < 4 && T < 0; k++, apex += 0.25) T = arcTime(pos, hopLanding, apex, v);
        if (T < 0) {
            // Too close under the edge for an arc: straight up, then over (see climbStep).
            for (double extra : new double[]{0.35, 0.6}) {
                double vy = Math.sqrt(2 * GRAVITY * (Math.max(0, hopRise) + extra));
                if (climbReaches(vy, v)) {
                    Vec before = vel;
                    climb = true;
                    climbClearY = pos.y() + Math.max(0, hopRise) + 0.03;
                    vel = new Vec(0, vy, 0);
                    speed = 0;
                    yawRate = 0f;
                    lastImpulse = vel.sub(before).length();
                    impulse = true;
                    squash = 0.2f;
                    enter(Phase.AIR);
                    return;
                }
            }
        }
        if (T < 0) {
            hopLanding = null;
            enter(Phase.WALK);
            stepCooldown = 20;
            speed = 0;
            vel = Vec.ZERO;
            return;
        }
        launchArc(T, apex - 0.25);
    }

    private void launchArc(double T, double apex) {
        Vec d = hopLanding.sub(pos);
        Vec h = d.horizontal().scale(1 / T);
        double vy = (d.y() + 0.5 * GRAVITY * T * T) / T;
        Vec before = vel;
        vel = new Vec(h.x(), vy, h.z());
        if (h.length() > 0.05) yaw = (float) h.yawDeg();
        yawRate = 0f;
        speed = h.length();
        lastImpulse = vel.sub(before).length();
        impulse = true;
        squash = 0.2f;
        enter(Phase.AIR);
    }

    /**
     * The flight time of the arc from {@code a} to {@code b} peaking {@code apex} above {@code a}
     * (at least above {@code b}), or −1 if it would touch a block.
     */
    private double arcTime(Vec a, Vec b, double apex, LocalVoxels v) {
        double top = Math.max(apex, b.y() - a.y() + 0.1);
        double vy = Math.sqrt(2 * GRAVITY * top);
        double disc = vy * vy - 2 * GRAVITY * (b.y() - a.y());
        if (disc < 0) return -1;
        double T = (vy + Math.sqrt(disc)) / GRAVITY;
        Vec h = b.sub(a).horizontal().scale(1 / T);
        double path = b.sub(a).horizontalLength() + 2 * top + Math.abs(b.y() - a.y());
        int n = Math.max(12, (int) Math.ceil(path / 0.03));
        for (int i = 1; i <= n; i++) {
            double s = T * i / n;
            Vec p = a.add(h.scale(s)).add(0, vy * s - 0.5 * GRAVITY * s * s, 0);
            if (!v.sphereClear(p, radius)) return -1;
        }
        return T;
    }

    private void fly(LocalVoxels v) {
        if (climb && hopLanding != null) {
            Vec[] next = climbStep(pos, vel, hopLanding);
            vel = next[1];
            moveAir(v, vel);
        } else {
            // Exact under constant gravity (the midpoint velocity), so the flight is the arc that
            // was checked clear; the plain Euler step runs low and clips the edge it was meant to clear.
            Vec mid = vel.add(0, -0.5 * GRAVITY * DT, 0);
            vel = vel.add(0, -GRAVITY * DT, 0);
            moveAir(v, mid);
        }
    }

    /** Steering available in a climb hop once it is above the edge, blocks/s² (as a percher's hop). */
    private static final double AIR_CONTROL = PerchBody.AIR_CONTROL;
    /** A climb hop: straight up until clear of the edge, then forward under air control. */
    private boolean climb;
    private double climbClearY;

    /**
     * One tick of a climb hop's flight from ({@code p}, {@code v}) toward {@code landing}: gravity,
     * and once the body is above {@link #climbClearY} a forward speed (along its facing) that
     * changes by at most the air control toward what lands it there. Returns {position, velocity}.
     */
    private Vec[] climbStep(Vec p, Vec v, Vec landing) {
        Vec fwd = Vec.fromYawPitch(yaw, 0);
        double h = Math.max(0, v.horizontal().dot(fwd));
        double vy = v.y() - GRAVITY * DT;
        if (p.y() >= climbClearY) {
            double dh = Math.max(0, landing.sub(p).horizontal().dot(fwd));
            double drop = p.y() - landing.y();
            double tLeft = (vy + Math.sqrt(Math.max(0, vy * vy + 2 * GRAVITY * drop))) / GRAVITY;
            double want = dh / Math.max(tLeft, DT);
            h += Body.clamp(want - h, -AIR_CONTROL * DT, AIR_CONTROL * DT);
            h = Math.max(0, h);
        }
        Vec nv = fwd.scale(h).add(0, vy, 0);
        return new Vec[]{p.add(nv.scale(DT)), nv};
    }

    /** Whether a climb hop launched now at {@code vy} comes down on {@link #hopLanding} without touching a block. */
    private boolean climbReaches(double vy, LocalVoxels v) {
        double saveClear = climbClearY;
        climbClearY = pos.y() + Math.max(0, hopRise) + 0.03;
        Vec p = pos, vel0 = new Vec(0, vy, 0);
        boolean ok = false;
        for (int i = 0; i < 40; i++) {
            Vec[] n = climbStep(p, vel0, hopLanding);
            if (n[1].y() < 0 && n[0].y() <= hopLanding.y() + 0.05) {
                // Down at the landing's height: there if it's over the landing (the floor takes it).
                ok = n[0].horizontal().sub(hopLanding.horizontal()).length() < 0.15;
                break;
            }
            if (!v.sphereClear(n[0], radius)) break;
            p = n[0];
            vel0 = n[1];
        }
        climbClearY = saveClear;
        return ok;
    }

    /** One tick of flight: blocked sideways it drops straight; meeting the floor it lands. */
    private void moveAir(LocalVoxels v, Vec step) {
        Vec next = pos.add(step.scale(DT));
        if (vel.y() <= 0) {
            // Touchdown first: coming down onto the floor this tick is a landing, not a collision.
            double f = landingFloor(v, next.x(), next.z(), pos.y() - radius);
            if (!Double.isNaN(f) && next.y() - radius - LocalVoxels.GROUND_LIFT <= f + 1e-9) {
                Vec at = new Vec(next.x(), f + radius + LocalVoxels.GROUND_LIFT, next.z());
                if (v.sphereClear(at, radius)) {
                    land(at, v);
                    return;
                }
            }
        }
        if (!v.sphereClear(next, radius)) {
            Vec hit = vel;
            double x = pos.x(), y = pos.y(), z = pos.z();
            double vx = vel.x(), vy = vel.y(), vz = vel.z();
            if (v.sphereClear(new Vec(next.x(), y, z), radius)) x = next.x(); else vx = 0;
            if (v.sphereClear(new Vec(x, next.y(), z), radius)) y = next.y(); else vy = Math.min(0, vy);
            if (v.sphereClear(new Vec(x, y, next.z()), radius)) z = next.z(); else vz = 0;
            if (vx == 0 || vz == 0) {
                // Clipped sideways mid-air: it drops straight down from there rather than sliding
                // along the face (which would carry it somewhere its nose isn't pointing).
                if (vx == 0 && vel.x() != 0 || vz == 0 && vel.z() != 0) {
                    vx = 0;
                    vz = 0;
                    x = pos.x();
                    z = pos.z();
                }
            }
            if (vy < 0 && y == pos.y()) {
                // Resting on something mid-fall (an edge): a landing, on the floor under it.
                double f = landingFloor(v, x, z, y - radius);
                if (!Double.isNaN(f) && y - radius - LocalVoxels.GROUND_LIFT - f < 0.2
                        && v.sphereClear(new Vec(x, f + radius + LocalVoxels.GROUND_LIFT, z), radius)) {
                    land(new Vec(x, f + radius + LocalVoxels.GROUND_LIFT, z), v);
                    return;
                }
                // Caught on a corner with no floor to land on: a collision, stopped there.
                vy = 0;
                backstops++;
                backstopLastStep = true;
                lastImpact = Math.max(lastImpact, -vel.y());
            }
            next = new Vec(x, y, z);
            vel = new Vec(vx, vy, vz);
            if (hit.horizontal().sub(vel.horizontal()).length() > 0.3) {
                backstops++;
                backstopLastStep = true;
                lastImpact = hit.sub(vel).length();
            }
        }
        if (vel.y() <= 0) {
            double f = landingFloor(v, next.x(), next.z(), pos.y() - radius);
            if (!Double.isNaN(f) && next.y() - radius - LocalVoxels.GROUND_LIFT <= f + 1e-9) {
                land(new Vec(next.x(), f + radius + LocalVoxels.GROUND_LIFT, next.z()), v);
                return;
            }
        }
        pos = next;
        speed = vel.horizontalLength();
    }

    private void land(Vec at, LocalVoxels v) {
        Vec before = vel;
        pos = at;
        floorY = at.y() - radius - LocalVoxels.GROUND_LIFT;
        Vec fwd = Vec.fromYawPitch(yaw, 0);
        // The landing takes some of the way it was going (more the harder it comes down); it walks
        // on from there.
        double keep = Body.clamp(0.85 - 0.03 * Math.max(0, -before.y()), 0.45, 0.85);
        speed = Math.max(0, before.horizontal().dot(fwd)) * keep;
        vel = fwd.scale(speed);
        forwardAccel = 0;
        lastImpulse = vel.sub(before).length();
        impulse = true;
        squash = (float) Math.min(1, 0.4 + Math.max(0, -before.y()) * 0.08);
        hopLanding = null;
        climb = false;
        enter(Phase.LAND);
    }

    // ── The floor ──────────────────────────────────────────────────────────────────────────────

    /** A perch hop coming down crosses support here, even if it aimed at a lower tread. */
    boolean crossesFloor(Vec from, Vec next, LocalVoxels v) {
        double f = landingFloor(v, next.x(), next.z(), from.y() - radius);
        return !Double.isNaN(f) && next.y() - radius - LocalVoxels.GROUND_LIFT <= f
                && v.sphereClear(new Vec(next.x(), f + radius + LocalVoxels.GROUND_LIFT, next.z()), radius);
    }

    /**
     * Support during a fall, independent of the limits on a deliberate walking drop. A body
     * can land on a terrace edge even when another column of its footprint is over a deep drop.
     * Only surfaces below its previous underside count: a riser ahead is not a touchdown.
     */
    private double landingFloor(LocalVoxels v, double x, double z, double bottom) {
        double best = Double.NEGATIVE_INFINITY;
        for (int bx = LocalVoxels.floor(x - radius); bx <= LocalVoxels.floor(x + radius); bx++) {
            for (int bz = LocalVoxels.floor(z - radius); bz <= LocalVoxels.floor(z + radius); bz++) {
                double f = v.columnFloor(bx, bz, bottom + 0.02, 24);
                if (!Double.isNaN(f)) best = Math.max(best, f);
            }
        }
        return best == Double.NEGATIVE_INFINITY ? Double.NaN : best;
    }

    /** The floor under a body here, standing at about its current floor: see {@link #footprintFloorFrom}. */
    double footprintFloor(LocalVoxels v, double x, double z) {
        return footprintFloorFrom(v, x, z, floorY);
    }

    /**
     * The highest surface under the body's footprint (the square of its radius) centred at
     * ({@code x}, {@code z}), reachable from a floor at {@code fromFloor}: no more than a hop up,
     * no more than {@link #MAX_DROP} down. NaN if any column under it is a wall or an edge.
     */
    double footprintFloorFrom(LocalVoxels v, double x, double z, double fromFloor) {
        double from = fromFloor + STEP_HOP + 0.02, depth = STEP_HOP + MAX_DROP + 0.04;
        int x0 = LocalVoxels.floor(x - radius), x1 = LocalVoxels.floor(x + radius);
        int z0 = LocalVoxels.floor(z - radius), z1 = LocalVoxels.floor(z + radius);
        double best = Double.NEGATIVE_INFINITY;
        for (int bx = x0; bx <= x1; bx++) {
            for (int bz = z0; bz <= z1; bz++) {
                double f = v.columnFloor(bx, bz, from, depth);
                if (Double.isNaN(f)) return f;
                best = Math.max(best, f);
            }
        }
        return best;
    }

    /**
     * Whether the whole footprint here is floor at {@code level}: somewhere to land, not
     * straddling an edge (half on a step, half over the drop beside it).
     */
    boolean flatAt(LocalVoxels v, double x, double z, double level) {
        double from = level + 0.02, depth = 0.04;
        int x0 = LocalVoxels.floor(x - radius), x1 = LocalVoxels.floor(x + radius);
        int z0 = LocalVoxels.floor(z - radius), z1 = LocalVoxels.floor(z + radius);
        for (int bx = x0; bx <= x1; bx++) {
            for (int bz = z0; bz <= z1; bz++) {
                if (Double.isNaN(v.columnFloor(bx, bz, from, depth))) return false;
            }
        }
        return true;
    }

    /**
     * Whether no part of the footprint here hangs over a drop below {@code level}: a step's next
     * riser in it is fine (that's higher), the side edge of a terrace isn't.
     */
    boolean noOverhang(LocalVoxels v, double x, double z, double level) {
        int x0 = LocalVoxels.floor(x - radius), x1 = LocalVoxels.floor(x + radius);
        int z0 = LocalVoxels.floor(z - radius), z1 = LocalVoxels.floor(z + radius);
        for (int bx = x0; bx <= x1; bx++) {
            for (int bz = z0; bz <= z1; bz++) {
                if (v.solid(bx, LocalVoxels.floor(level - 0.01), bz)
                        && LocalVoxels.floor(level - 0.01) + v.top(bx, LocalVoxels.floor(level - 0.01), bz) >= level - STEP_SMOOTH) {
                    continue;
                }
                double f = v.columnFloor(bx, bz, level + 0.02, STEP_SMOOTH + 0.04);
                if (Double.isNaN(f)) return false;
            }
        }
        return true;
    }

    /** Puts the body on the floor here, at rest, if there is a floor; false if there isn't one. */
    boolean placeOnFloor(Vec near, float facingYaw, LocalVoxels v) {
        double f = footprintFloorFrom(v, near.x(), near.z(), near.y() - 0.5);
        if (Double.isNaN(f)) return false;
        placeAt(new Vec(near.x(), f + radius + LocalVoxels.GROUND_LIFT, near.z()), facingYaw);
        floorY = f;
        enter(Phase.WALK);
        return true;
    }

    /**
     * Takes over a body arriving through the air (off its owner): it keeps its velocity and falls
     * or lands from there, nose the way it is going.
     */
    void placeFalling(Vec p, Vec v, float facingYaw) {
        placeAt(p, facingYaw);
        vel = v;
        if (v.horizontalLength() > 0.05) yaw = (float) v.yawDeg();
        speed = v.horizontalLength();
        hopLanding = null;
        enter(Phase.AIR);
    }

    /**
     * Takes over a body knocked off its owner (a tumble): it falls from there with velocity
     * {@code v}, still facing the way it faced on the perch (it doesn't flip or turn to the fall),
     * lands with a squash, and turns to walk on from there.
     */
    void placeTumbling(Vec p, Vec v, float facingYaw) {
        placeAt(p, facingYaw);
        vel = v;
        speed = v.horizontalLength();
        enter(Phase.AIR);
    }

    @Override
    void placeAt(Vec p, float facingYaw) {
        super.placeAt(p, facingYaw);
        forwardAccel = 0;
        squash = 0f;
        hopLanding = null;
        climb = false;
    }

    // ── Rhythm ─────────────────────────────────────────────────────────────────────────────────

    /** The speed multiplier the rhythm asks for this tick (× cruise). */
    private float drive = 0f;

    private double envelope(Rhythm r) {
        return urgent && r == Rhythm.SCURRY ? Math.max(drive, 1.4) : drive;
    }

    private void advanceRhythm(Rhythm r) {
        boolean ripple = gait == GroundGait.RIPPLE;
        float period, duty, hi, lo, attack, decay;
        switch (r) {
            case AMBLE -> {
                period = ripple ? 6f : 3.5f;
                duty = ripple ? 0.3f : 0.4f;
                hi = 0.45f;
                lo = 0f;
                attack = ripple ? 1.5f : 2.5f;
                decay = 2.0f;
            }
            case TROT -> {
                period = ripple ? 1.6f : 0.9f;
                duty = 0.6f;
                hi = ripple ? 1.05f : 1.15f;
                lo = ripple ? 0.85f : 0.7f;
                attack = ripple ? 1.5f : 3.0f;
                decay = ripple ? 1.5f : 2.0f;
            }
            default -> {
                period = ripple ? 1.2f : 0.7f;
                duty = 0.7f;
                hi = ripple ? 1.45f : 1.6f;
                lo = ripple ? 1.2f : 1.1f;
                attack = 3.0f;
                decay = 2.0f;
            }
        }
        dwellPhase += (float) (DT / (period * dwellJitter * Math.sqrt(pace)));
        if (dwellPhase >= 1f) dwellPhase -= 1f;
        boolean up = dwellPhase < duty;
        float target = up ? hi : lo;
        float rate = target > drive ? attack : decay;
        drive += (target - drive) * Math.min(1f, rate * (float) DT);
        burstDrive = drive;
    }

    private void updateShape() {
        driveSlow += (drive - driveSlow) * (float) (1.5 * DT);
        float target = Math.max(0f, Math.min(1f, (drive - driveSlow) * 3f));
        shape += (target - shape) * (target > shape ? 0.5f : 0.3f);
    }

    private void advanceWander() {
        float k = WANDER_SIGMA * (float) Math.sqrt(DT) * SQRT3;
        wander = Body.clamp(wander - wander * WANDER_THETA * (float) DT + k * nextSigned(), -WANDER_CLAMP, WANDER_CLAMP);
    }

    private void enter(Phase p) {
        phase = p;
        phaseTicks = 0;
    }

    @Override
    float speedFactor() {
        double normalized = Math.min(speed / (2 * cruise()), 1.6);
        return (float) (0.6 + 0.9 * normalized);
    }

    float nextSigned() {
        long x = noise;
        x ^= x << 13;
        x ^= x >>> 7;
        x ^= x << 17;
        noise = x;
        return ((x >>> 40) * (1f / 8388608f)) - 1f;
    }
}
