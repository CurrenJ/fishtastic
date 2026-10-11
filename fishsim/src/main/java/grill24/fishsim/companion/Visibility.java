package grill24.fishsim.companion;

/**
 * Whether the owner can see a point: inside the camera's view and not behind a block. The
 * attention drive and the visibility invariants both use this one definition.
 */
public final class Visibility {

    /** Assumed screen aspect; the adapter can pass the real one later. */
    public static final double ASPECT = 16.0 / 9.0;
    /** Fraction of the half-FOV that counts as on screen, so a sliver at the edge doesn't count. */
    private static final double EDGE = 0.92;
    /** Closer to the camera than this, the fish is clipped by the near plane. */
    public static final double NEAR = 0.3;

    private Visibility() {}

    /** On screen (ignoring blocks). */
    public static boolean inFrustum(OwnerState o, Vec p) {
        Vec cam = o.camera();
        Vec look = o.look();
        Vec right = look.cross(Vec.UP).normalize();
        if (right.lengthSqr() < 1e-9) right = o.flatRight();
        Vec up = right.cross(look);
        Vec d = p.sub(cam);
        double z = d.dot(look);
        if (z < NEAR) return false;
        double tanV = Math.tan(Math.toRadians(o.fovDeg()) / 2) * EDGE;
        return Math.abs(d.dot(up) / z) < tanV && Math.abs(d.dot(right) / z) < tanV * ASPECT;
    }

    /** On screen and not hidden behind a block. */
    public static boolean visible(OwnerState o, Vec p, LocalVoxels voxels) {
        return inFrustum(o, p) && voxels.segmentClear(o.camera(), p, 0.0);
    }

    /** Angle between the view centre and {@code p}, degrees. */
    public static double angleFromCenterDeg(OwnerState o, Vec p) {
        Vec d = p.sub(o.camera()).normalize();
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, d.dot(o.look())))));
    }
}
