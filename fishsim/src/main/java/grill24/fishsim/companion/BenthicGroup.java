package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;

/**
 * A ground companion formation: one mind and shared drives, with up to two follower motors.
 * Lines carry each predecessor's actual trail; crowds separate and gather without alignment.
 * Each member retains the solo ground/perch/water motor and its own fade fallback.
 *
 * <p>Members are same-species fish from the owner's party (docs/fish-companions-party.md §3), each
 * with its own body length; spacing between two members uses the mean of their lengths. The old
 * constructor taking a {@code count} builds that many identical members, bitwise as before.
 */
public final class BenthicGroup {
    public enum Formation { LINE, CROWD }

    private final CompanionAgent[] members;
    private final Breadcrumbs[] trails;
    private final Formation formation;
    /** Each member's body length, blocks, leader first. */
    private final float[] lengths;
    private final LocalVoxels voxels = new LocalVoxels();
    private int climbClock;
    private boolean requestedClimb;
    private Vec lastTravel = Vec.ZERO;
    private int cornerTicks;
    private float cornerYaw;
    private final boolean[] cornerPending;

    /** {@code count} identical members of one body length (the profile-count path, kept bitwise). */
    public BenthicGroup(CompanionTunables t, long seed, float length, Temperament temperament,
                        GroundGait gait, int count, Formation formation, CompanionWorld world) {
        this(t, seed, filled(length, count), temperament, gait, formation, world);
    }

    /**
     * A formation of one to three same-species members (docs/fish-companions-party.md §3): {@code
     * lengths[0]} is the leader's body length (the member in the lowest party slot), the rest the
     * followers' in slot order. {@code seed} is the leader's; each follower's agent is seeded from it
     * and its index, as the profile-count group always was.
     */
    public BenthicGroup(CompanionTunables t, long seed, float[] lengths, Temperament temperament,
                        GroundGait gait, Formation formation, CompanionWorld world) {
        int count = lengths.length;
        if (count < 1 || count > 3) throw new IllegalArgumentException("benthic group count must be 1..3");
        this.lengths = lengths.clone();
        this.formation = java.util.Objects.requireNonNull(formation);
        members = new CompanionAgent[count];
        cornerPending = new boolean[count];
        trails = new Breadcrumbs[count];
        members[0] = new CompanionAgent(t, seed, Locomotion.BENTHIC, lengths[0], temperament, gait, world);
        double back = 0;
        for (int i = 0; i < count; i++) {
            trails[i] = new Breadcrumbs(Math.min(0.12, lengths[i] * 0.3));
            if (i > 0) members[i] = new CompanionAgent(t, seed + i * 977, Locomotion.BENTHIC,
                    lengths[i], temperament, gait, world, members[0]);
            // Solos retain their seeded slot; groups allocate left, right, head leader-first.
            if (count > 1) members[i].groupSlot(new PerchBody.Slot[]{PerchBody.Slot.LEFT_SHOULDER,
                    PerchBody.Slot.RIGHT_SHOULDER, PerchBody.Slot.HEAD}[i], i > 0, i);
            if (i > 0) {
                back += spacing(i - 1, i);
                members[i].groupStart(members[0].position().sub(world.owner().flatForward().scale(back)));
            }
        }
        // The leader sets the formation's pace: no faster than its slowest member walks (D10), so a
        // big leader never strands small followers. Identical members leave it uncapped.
        if (t.groupPaceCap) {
            double slowest = members[0].groundPace();
            for (CompanionAgent a : members) slowest = Math.min(slowest, a.groundPace());
            if (slowest < members[0].groundPace()) members[0].groundPaceCap(slowest);
        }
    }

    private static float[] filled(float length, int count) {
        if (count < 1 || count > 3) throw new IllegalArgumentException("benthic group count must be 1..3");
        float[] l = new float[count];
        java.util.Arrays.fill(l, length);
        return l;
    }

    /** The spacing length for members {@code a} and {@code b}: the mean of their lengths (exact when equal). */
    private double pairLength(int a, int b) {
        return ((double) lengths[a] + lengths[b]) * 0.5;
    }

    /** How far member {@code b} keeps from member {@code a}: 1.2 pair lengths, or contact plus a little. */
    private double spacing(int a, int b) {
        return Math.max(1.2 * pairLength(a, b), 2 * members[b].radius() + 0.04);
    }

    public void step(CompanionWorld world) {
        OwnerState o = world.owner();
        if (o.horizontalSpeed() > 0.6) {
            Vec travel = o.velocity().horizontal().normalize();
            if (lastTravel.lengthSqr() == 0 || lastTravel.dot(travel) < 0.5) {
                cornerTicks = 40;
                cornerYaw = (float) travel.yawDeg();
                java.util.Arrays.fill(cornerPending, true);
            }
            lastTravel = travel;
        }
        if (cornerTicks > 0) cornerTicks--;
        voxels.update(world.blocks(), o.eye());
        if (o.teleported()) for (Breadcrumbs trail : trails) trail.clear();
        members[0].formation(null, formation == Formation.CROWD ? separation(0) : null, 0, true);
        members[0].step(world);
        record(0);
        climbClock = members[0].landMode().equals("CLIMB")
                || members[0].riding() && !members[0].perchSlot().hand() ? climbClock + 1 : 0;
        for (int i = 1; i < members.length; i++) {
            CompanionAgent a = members[i], ahead = members[i - 1];
            Vec target;
            // Lines space each member from the one ahead; water stations and crowds from the leader.
            double spacing = formation == Formation.LINE ? spacing(i - 1, i) : spacing(0, i);
            double speed;
            if (o.inWater()) {
                Vec forward = Vec.fromYawPitch(members[0].yaw(), 0);
                double toLeader = spacing(0, i);
                target = members[0].position().sub(forward.scale(toLeader * i))
                        .add(forward.cross(Vec.UP).scale((i == 1 ? 1 : -1) * toLeader));
                speed = 0; // the follower's swim motor uses the station velocity directly
            } else if (formation == Formation.LINE && ahead.onGround()) {
                // Steer toward the next member's newest trail point, regulating the gap with
                // speed. Steering at a station already under the follower's nose makes arrival
                // braking overshoot it, followed by an unnecessary full circle to get back.
                target = trails[i - 1].trailPoint(ahead.position(), 0);
                if (target == null) target = ahead.position();
                // Arrival braking retains the station rather than passing the predecessor.
                speed = Math.max(0, ahead.velocity().horizontalLength()
                        + 3 * (a.position().sub(ahead.position()).horizontalLength() - spacing));
                if (a.groundLag() > GroundMind.CATCH_UP_LAG
                        && a.position().sub(ahead.position()).horizontalLength() > spacing + 0.3) {
                    speed = Math.max(speed, 1.6 * GroundBody.BASE_CRUISE * Math.sqrt(lengths[i] / 0.3));
                }
            } else {
                Vec centre = members[0].onGround() ? members[0].position()
                        : GroundMind.ownerFloor(o, voxels).sub(o.flatForward().scale(2));
                double side = i == 1 ? 1 : -1;
                double spread = o.horizontalSpeed() < 0.6 ? 1.2 : 0.7;
                target = centre.add(o.flatRight().scale(side * spacing * spread))
                        .sub(o.flatForward().scale(spacing * 0.6));
                speed = Math.max(0, o.horizontalSpeed() + 3 * a.position().sub(target).horizontalLength());
            }
            if (requestedClimb && climbClock == i * 16) a.devPerch();
            boolean predecessorClimbing = ahead.climbs() > 0;
            // Followers in a line keep out of each other too: steering at the newest trail point,
            // which lies under a slowing or pivoting predecessor, walked them into it. A line's
            // push acts only at contact (its gap is kept by speed); a crowd's keeps them spread.
            a.formation(target, separation(i), speed, climbClock >= i * 16 && predecessorClimbing);
            // A sharp travel turn reaches the whole line together, avoiding a serial 180-degree
            // pivot delay at each member. Once turned, each resumes its predecessor's trail.
            Vec heading = Vec.fromYawPitch(cornerYaw, 0);
            if (cornerPending[i] && Math.abs(Body.wrap(a.yaw() - cornerYaw)) <= 25
                    && ahead.velocity().horizontal().dot(heading) > 0.6
                    && target.sub(a.position()).horizontal().dot(heading) >= 0) cornerPending[i] = false;
            if (o.horizontalSpeed() < 0.6 && cornerTicks == 0) cornerPending[i] = false;
            a.formationTurn(formation == Formation.LINE && cornerPending[i] ? cornerYaw : Float.NaN);
            a.step(world);
            record(i);
        }
        if (requestedClimb && climbClock > members.length * 16) requestedClimb = false;
    }

    private void record(int i) {
        CompanionAgent a = members[i];
        if (a.onGround() && a.feetDown()) trails[i].record(a.position());
        else if (a.riding()) trails[i].clear();
    }

    private Vec separation(int index) {
        CompanionAgent a = members[index];
        Vec push = Vec.ZERO;
        for (int i = 0; i < members.length; i++) {
            if (i == index || !members[i].onGround() || !a.onGround()) continue;
            double range = formation == Formation.LINE ? 2 * a.radius() + 0.04
                    : Math.max(pairLength(index, i) * 1.2, 2 * a.radius() + 0.04);
            Vec away = a.position().sub(members[i].position()).horizontal();
            double d = away.length();
            if (d < range) {
                Vec dir = d < 1e-6 ? new Vec(index <= i ? -1 : 1, 0, 0) : away.scale(1 / d);
                push = push.add(dir.scale(4 * (1 - d / range)));
            }
        }
        return push;
    }

    public CompanionAgent leader() { return members[0]; }
    public CompanionAgent member(int index) { return members[index]; }
    public int count() { return members.length; }
    public Formation formation() { return formation; }
    /** Member {@code index}'s body length, blocks. */
    public float length(int index) { return lengths[index]; }
    public void interpolate(float alpha) { for (CompanionAgent a : members) a.interpolate(alpha); }
    public void invalidateBlocks() {
        voxels.invalidate();
        for (CompanionAgent a : members) a.voxels().invalidate();
    }
    public void devPerch() { requestedClimb = true; members[0].devPerch(); }
    public void devGround() { requestedClimb = false; for (CompanionAgent a : members) a.devGround(); }
    public void devTumble() { for (CompanionAgent a : members) a.devTumble(); }
}
