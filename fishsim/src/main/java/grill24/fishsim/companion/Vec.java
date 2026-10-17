package grill24.fishsim.companion;

/**
 * An immutable 3-vector in world block units — the companion sim's only geometry type.
 *
 * <p>Doubles, not the tank engine's floats: a companion lives at world coordinates, which run to
 * millions of blocks, where a float can no longer resolve a tail beat. The axis and angle
 * conventions are Minecraft's, so the adapter copies values across without converting: +Y up, yaw
 * 0 faces +Z and increases clockwise seen from above (90 faces −X), pitch is positive looking down.
 */
public record Vec(double x, double y, double z) {

    public static final Vec ZERO = new Vec(0, 0, 0);
    public static final Vec UP = new Vec(0, 1, 0);

    public Vec add(Vec o) { return new Vec(x + o.x, y + o.y, z + o.z); }

    public Vec add(double dx, double dy, double dz) { return new Vec(x + dx, y + dy, z + dz); }

    public Vec sub(Vec o) { return new Vec(x - o.x, y - o.y, z - o.z); }

    public Vec scale(double k) { return new Vec(x * k, y * k, z * k); }

    public double dot(Vec o) { return x * o.x + y * o.y + z * o.z; }

    public Vec cross(Vec o) { return new Vec(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x); }

    public double lengthSqr() { return x * x + y * y + z * z; }

    public double length() { return Math.sqrt(lengthSqr()); }

    public double horizontalLength() { return Math.sqrt(x * x + z * z); }

    public Vec horizontal() { return new Vec(x, 0, z); }

    public double distanceTo(Vec o) { return sub(o).length(); }

    /** This vector at unit length, or {@link #ZERO} when it has no direction to keep. */
    public Vec normalize() {
        double l = length();
        return l < 1e-9 ? ZERO : scale(1 / l);
    }

    public Vec clampLength(double max) {
        double l = length();
        return l > max ? scale(max / l) : this;
    }

    public Vec lerp(Vec o, double t) { return new Vec(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t); }

    /** The unit look vector for a Minecraft yaw/pitch pair (degrees). */
    public static Vec fromYawPitch(double yawDeg, double pitchDeg) {
        double yr = Math.toRadians(yawDeg), pr = Math.toRadians(pitchDeg);
        double c = Math.cos(pr);
        return new Vec(-Math.sin(yr) * c, -Math.sin(pr), Math.cos(yr) * c);
    }

    /** The Minecraft yaw (degrees) of this vector's horizontal part. */
    public double yawDeg() { return Math.toDegrees(Math.atan2(-x, z)); }
}
