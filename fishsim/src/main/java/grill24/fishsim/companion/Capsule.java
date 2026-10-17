package grill24.fishsim.companion;

/**
 * A segment with a radius: the owner's body, as an obstacle the fish brakes for and steers around
 * but which, unlike a block, moves and isn't solid.
 */
record Capsule(Vec a, Vec b, double radius, Vec velocity) {

    static Capsule ownerBody(OwnerState o) {
        return new Capsule(o.feet().add(0, OwnerState.BODY_RADIUS, 0), o.eye().add(0, 0.1, 0),
                OwnerState.BODY_RADIUS, o.velocity());
    }

    Vec nearest(Vec p) {
        Vec ab = b.sub(a);
        double s = Math.max(0, Math.min(1, p.sub(a).dot(ab) / ab.lengthSqr()));
        return a.add(ab.scale(s));
    }

    /** Distance from {@code p} to the capsule's surface (negative inside). */
    double distance(Vec p) {
        return p.distanceTo(nearest(p)) - radius;
    }
}
