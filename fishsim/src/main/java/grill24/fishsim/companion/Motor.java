package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;

/**
 * What every companion body shares, whatever moves it: where it is, how it's moving, its pose for
 * the animator, and how it met the blocks (docs/fish-companions-sim-plan.md §3.4). The mind and the
 * agent talk to this; {@link Body} (swimmers) and {@link DriftBody} (drifters) decide the physics.
 */
abstract class Motor {

    static final double DT = 0.05;

    /** The class this body moves as, and its limits, refreshed every tick. */
    final Locomotion locomotion;
    Gait g;
    final double radius;

    Vec pos;
    Vec vel = Vec.ZERO;
    /** Measured acceleration over the last tick, blocks/s². */
    Vec acc = Vec.ZERO;
    double speed;
    float yaw, pitch;
    /** Current turn rates, degrees per tick; positive yaw rate turns right. */
    float yawRate, pitchRate;
    /** Drawn bank, degrees; positive leans right (into a right turn). */
    float bank;
    float burstDrive = 1f;
    /** Set by the mind each tick: idling by a still owner (LOITER, SETTLE). Only a drifter reads it (§13.7). */
    boolean idling;
    float tailPhase;
    /** Steps on which the hard resolve had to stop the body short of a block. */
    long backstops;
    boolean backstopLastStep;
    /** Speed lost to the last backstop, blocks/s — a touch is ~0, a crash is large. */
    double lastImpact;

    Motor(Locomotion locomotion, Gait g, Vec pos, double radius) {
        this.locomotion = locomotion;
        this.g = g;
        this.pos = pos;
        this.radius = radius;
    }

    /**
     * One tick toward {@code desired}.
     *
     * @param wanderScale how much heading noise this goal tolerates (0 = none)
     * @param extraAvoid  soft pushes from things that aren't blocks (the owner's body), blocks/s
     * @param body        a non-solid obstacle to brake for (the owner), or null
     */
    abstract void step(Vec desired, double wanderScale, Vec extraAvoid, LocalVoxels voxels, Capsule body);

    /** Every contact this body (and any body it hands off to) has had with blocks. */
    long backstopCount() {
        return backstops;
    }

    /** The animator's tail-beat (or pulse) multiplier. */
    abstract float speedFactor();

    /** Places the body somewhere new, at rest (the fade-in after a leash). */
    void placeAt(Vec p, float facingYaw) {
        pos = p;
        vel = Vec.ZERO;
        acc = Vec.ZERO;
        speed = 0;
        yaw = facingYaw;
        pitch = 0f;
        yawRate = pitchRate = 0f;
        bank = 0f;
    }

    /** Moves by one tick of velocity, stopping short per axis wherever a block is in the way. */
    void move(LocalVoxels voxels, Vec fwd) {
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
        afterBackstop(fwd, vy);
    }

    /** Lets the body bring its own state into line after the resolve cut its velocity. */
    void afterBackstop(Vec fwd, double vy) {
        speed = vel.length();
    }
}
