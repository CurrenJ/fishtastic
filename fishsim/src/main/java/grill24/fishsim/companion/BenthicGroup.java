package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;

/**
 * A ground companion formation: one mind and shared drives, with up to two follower motors.
 * Lines carry each predecessor's actual trail; crowds separate and gather without alignment.
 * Each member retains the solo ground/perch/water motor and its own fade fallback.
 */
public final class BenthicGroup {
    public enum Formation { LINE, CROWD }

    private final CompanionAgent[] members;
    private final Breadcrumbs[] trails;
    private final Formation formation;
    private final float length;
    private final LocalVoxels voxels = new LocalVoxels();
    private int climbClock;
    private boolean requestedClimb;
    private Vec lastTravel = Vec.ZERO;
    private int cornerTicks;
    private float cornerYaw;
    private final boolean[] cornerPending;

    public BenthicGroup(CompanionTunables t, long seed, float length, Temperament temperament,
                        GroundGait gait, int count, Formation formation, CompanionWorld world) {
        if (count < 1 || count > 3) throw new IllegalArgumentException("benthic group count must be 1..3");
        this.length = length;
        this.formation = java.util.Objects.requireNonNull(formation);
        members = new CompanionAgent[count];
        cornerPending = new boolean[count];
        trails = new Breadcrumbs[count];
        members[0] = new CompanionAgent(t, seed, Locomotion.BENTHIC, length, temperament, gait, world);
        for (int i = 0; i < count; i++) {
            trails[i] = new Breadcrumbs(Math.min(0.12, length * 0.3));
            if (i > 0) members[i] = new CompanionAgent(t, seed + i * 977, Locomotion.BENTHIC,
                    length, temperament, gait, world, members[0]);
            // Solos retain their seeded slot; groups allocate left, right, head leader-first.
            if (count > 1) members[i].groupSlot(new PerchBody.Slot[]{PerchBody.Slot.LEFT_SHOULDER,
                    PerchBody.Slot.RIGHT_SHOULDER, PerchBody.Slot.HEAD}[i], i > 0, i);
            if (i > 0) members[i].groupStart(members[0].position().sub(world.owner().flatForward()
                    .scale(i * Math.max(1.2 * length, 2 * members[i].radius() + 0.04))));
        }
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
            double spacing = Math.max(1.2 * length, 2 * a.radius() + 0.04);
            double speed;
            if (o.inWater()) {
                Vec forward = Vec.fromYawPitch(members[0].yaw(), 0);
                target = members[0].position().sub(forward.scale(spacing * i))
                        .add(forward.cross(Vec.UP).scale((i == 1 ? 1 : -1) * spacing));
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
                    speed = Math.max(speed, 1.6 * GroundBody.BASE_CRUISE * Math.sqrt(length / 0.3));
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
            a.formation(target, formation == Formation.CROWD ? separation(i) : null, speed,
                    climbClock >= i * 16 && predecessorClimbing);
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
        double range = Math.max(length * 1.2, 2 * a.radius() + 0.04);
        for (int i = 0; i < members.length; i++) {
            if (i == index || !members[i].onGround() || !a.onGround()) continue;
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
    public void interpolate(float alpha) { for (CompanionAgent a : members) a.interpolate(alpha); }
    public void invalidateBlocks() {
        voxels.invalidate();
        for (CompanionAgent a : members) a.voxels().invalidate();
    }
    public void devPerch() { requestedClimb = true; members[0].devPerch(); }
    public void devGround() { requestedClimb = false; for (CompanionAgent a : members) a.devGround(); }
    public void devTumble() { for (CompanionAgent a : members) a.devTumble(); }
}
