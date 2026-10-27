package grill24.fishtastic.school;

/**
 * The path a breaching school fish's drawn body follows (wild-fish-surface-jumps-design-spec
 * §12.6-12.7), in sim-local coordinates, time in ticks from the moment the fish commits.
 *
 * <p>Three phases. A <b>run-in</b>: a Hermite curve that starts exactly where the swimming fish is,
 * at the velocity it already has, and ends at the exit point on the surface, at the exit velocity,
 * so the fish swims on into the leap without stopping. A ballistic <b>flight</b> under real
 * gravity along the leap heading. A re-entry <b>glide</b>: drag bleeds its speed away, the dive's
 * fastest, so it levels out a little under the surface.
 *
 * <p>Position and velocity are continuous throughout. Pitch and yaw follow the velocity, except at
 * the start of the run-in, where they are eased in from the engine's own pose (which has no pitch).
 * The fish therefore curves up and leans back into the leap, is level at the apex, and noses down
 * into the water.
 *
 * <p>Pure and deterministic ({@link StrictMath}): every copy of a school builds the same arc from
 * the same inputs.
 */
public final class SurfaceJumpArc {
    /** Gravity, blocks/tick²: Earth's, at a block a metre and 20 ticks a second. */
    static final double GRAVITY = 9.8 / 400.0;
    /** The exit angle above the horizontal at full reach, degrees. */
    static final double LAUNCH_PITCH_DEG = 60.0;
    /** Bounds on the run-in's length, ticks: never a lurch, never a crawl. */
    static final double MIN_RUN_IN_TICKS = 6.0, MAX_RUN_IN_TICKS = 24.0;
    static final double GLIDE_TICKS = 16.0;
    /** The glide hands the fish back to the engine at this fraction of its entry speed. */
    static final double GLIDE_END_FRACTION = 0.25;
    /**
     * How fast the water stops the dive after re-entry, per tick: a 0.5-block breach's fish goes
     * about half a block under the surface before it levels out.
     */
    static final double DIVE_DECAY = 0.35;
    /** The lowest apex, blocks above the surface, and how much of the body length is added to it. */
    static final double MIN_APEX = 0.25, APEX_PER_LENGTH = 0.5;
    /** Extra tail beat, tail-phase ticks per tick: ramping up through the run-in, easing in the air, hard on landing. */
    static final double RUN_IN_BEAT = 2.5, FLIGHT_BEAT = 0.6, GLIDE_BEAT = 2.0;
    /**
     * A free swimmer heading for its launch point commits to the leap once it is this close
     * horizontally and this shallow, blocks: far enough out to swim into it, near enough that the
     * run-in is a short curve.
     */
    public static final float COMMIT_DISTANCE = 1.2f, COMMIT_DEPTH = 1.0f;
    /** Horizontal speed below which the run-in's yaw holds the leap heading instead of a noisy direction. */
    private static final double YAW_MIN_SPEED = 0.01;

    private final double pl, py, pd, vl, vy0, vd;
    private final double el, ey, ed, hl, hd;
    /** Exit speed components along the heading and up, blocks a tick. */
    private final double vs, vy;
    private final double runIn, tGlide, tEnd, glideDecay;
    private final double headingYaw;

    private SurfaceJumpArc(double pl, double py, double pd, double vl, double vy0, double vd,
                           double el, double ey, double ed, double hl, double hd,
                           double apex, double reach, double runInScale) {
        this.pl = pl; this.py = py; this.pd = pd;
        this.vl = vl; this.vy0 = vy0; this.vd = vd;
        this.el = el; this.ey = ey; this.ed = ed;
        this.hl = hl; this.hd = hd;
        this.vy = StrictMath.sqrt(2.0 * GRAVITY * apex);
        this.vs = reach * vy / StrictMath.tan(StrictMath.toRadians(LAUNCH_PITCH_DEG));
        double chord = StrictMath.sqrt((el - pl) * (el - pl) + (ey - py) * (ey - py) + (ed - pd) * (ed - pd));
        double startSpeed = StrictMath.sqrt(vl * vl + vy0 * vy0 + vd * vd);
        double ticks = 2.0 * chord / (startSpeed + StrictMath.hypot(vs, vy)) * runInScale;
        this.runIn = StrictMath.max(MIN_RUN_IN_TICKS, StrictMath.min(MAX_RUN_IN_TICKS, ticks));
        this.tGlide = runIn + 2.0 * vy / GRAVITY;
        this.tEnd = tGlide + GLIDE_TICKS;
        this.glideDecay = -StrictMath.log(GLIDE_END_FRACTION) / GLIDE_TICKS;
        this.headingYaw = StrictMath.toDegrees(StrictMath.atan2(-hd, hl));
    }

    /**
     * An arc for a fish at {@code (pl, py, pd)} moving {@code (vl, vy, vd)} blocks a tick that leaves
     * the water at {@code (el, surface, ed)} along unit heading {@code (hl, hd)}, its body centre
     * peaking {@code apex} above the surface. {@code reach} scales the flight's horizontal speed (1 is
     * the full 60° launch; less is steeper and shorter); {@code runInScale} shortens the run-in.
     */
    public static SurfaceJumpArc of(double pl, double py, double pd, double vl, double vy, double vd,
                                    double el, double surface, double ed, double hl, double hd,
                                    double apex, double reach, double runInScale) {
        return new SurfaceJumpArc(pl, py, pd, vl, vy, vd, el, surface, ed, hl, hd, apex, reach, runInScale);
    }

    /**
     * The apex a fish of body {@code length} reaches when sent with {@code height} above a launch
     * point {@code depth} under the surface: never so low that its silhouette barely clears.
     */
    public static double apexFor(double height, double depth, double length, double maxHeight) {
        double apex = StrictMath.max(height - StrictMath.max(0.0, depth), MIN_APEX + APEX_PER_LENGTH * length);
        return StrictMath.min(apex, maxHeight);
    }

    /** How far past the exit point the fish is above the water at full reach, blocks. */
    public static double airReach(double apex) {
        double up = StrictMath.sqrt(2.0 * GRAVITY * apex);
        return up / StrictMath.tan(StrictMath.toRadians(LAUNCH_PITCH_DEG)) * 2.0 * up / GRAVITY;
    }

    public double durationTicks() { return tEnd; }
    /** When the body's centre leaves the water and when it goes back in, ticks. */
    public double exitTick() { return runIn; }
    public double entryTick() { return tGlide; }
    /** Forward speed at the surface, blocks a tick. */
    public double surfaceSpeed() { return vs; }
    public double headingL() { return hl; }
    public double headingD() { return hd; }
    /** The leap heading as a yaw, the engine's {@code atan2(-d, l)} convention, degrees. */
    public double headingYaw() { return headingYaw; }
    /** Forward speed the fish is handed back with, blocks a tick. */
    public double endSpeed() { return vs * GLIDE_END_FRACTION; }

    /** Where the body's centre crosses the surface going out ({@code entry} false) or back in: {@code [l, d]}. */
    public double[] crossing(boolean entry) {
        double s = entry ? vs * (tGlide - runIn) : 0.0;
        return new double[]{el + hl * s, ed + hd * s};
    }

    /**
     * Fills {@code out} at {@code tau} ticks: {@code [l, y, d, pitch° (nose up), yaw°, yaw weight,
     * extra tail phase]}. Before 0 it runs back along the starting velocity; after the end it holds
     * the end point.
     */
    public double[] sample(double tau, double[] out) {
        tau = StrictMath.min(tau, tEnd);
        if (tau < 0.0) {
            // Frames drawn in the tick before the commit: still the engine's own swim, carried back
            // along the velocity it committed with, so the hand-over has no pause in it.
            out[0] = pl + vl * tau;
            out[1] = py + vy0 * tau;
            out[2] = pd + vd * tau;
            out[3] = 0.0;
            out[4] = headingYaw;
            out[5] = 0.0;
            out[6] = 0.0;
        } else if (tau < runIn) {
            double u = tau / runIn;
            double a = h00(u), b = h10(u) * runIn, c = h01(u), e = h11(u) * runIn;
            double xl = vs * hl, xd = vs * hd;
            out[0] = a * pl + b * vl + c * el + e * xl;
            out[1] = a * py + b * vy0 + c * ey + e * vy;
            out[2] = a * pd + b * vd + c * ed + e * xd;
            double da = dh00(u) / runIn, db = dh10(u), dc = dh01(u) / runIn, de = dh11(u);
            double ul = da * pl + db * vl + dc * el + de * xl;
            double uy = da * py + db * vy0 + dc * ey + de * vy;
            double ud = da * pd + db * vd + dc * ed + de * xd;
            double flat = StrictMath.hypot(ul, ud);
            // The engine draws a swimmer level and facing its own way: pitch and yaw take over from
            // it over the run-in, fully the velocity's own by the surface.
            double take = smoother(u);
            out[3] = take * StrictMath.toDegrees(StrictMath.atan2(uy, flat));
            out[4] = flat < YAW_MIN_SPEED ? headingYaw : StrictMath.toDegrees(StrictMath.atan2(-ud, ul));
            out[5] = take;
            out[6] = RUN_IN_BEAT * runIn * u * u * 0.5;
        } else if (tau < tGlide) {
            double t = tau - runIn;
            out[0] = el + hl * vs * t;
            out[1] = ey + vy * t - 0.5 * GRAVITY * t * t;
            out[2] = ed + hd * vs * t;
            out[3] = StrictMath.toDegrees(StrictMath.atan2(vy - GRAVITY * t, vs));
            out[4] = headingYaw;
            out[5] = 1.0;
            out[6] = RUN_IN_BEAT * runIn * 0.5 + FLIGHT_BEAT * t;
        } else {
            // Glide: water drag bleeds both speeds away, the dive faster than the run forward, so
            // the fish levels out as it slows and never swoops back up.
            double t = tau - tGlide;
            double decay = StrictMath.exp(-glideDecay * t), sink = StrictMath.exp(-DIVE_DECAY * t);
            double s = vs * (tGlide - runIn) + vs / glideDecay * (1.0 - decay);
            out[0] = el + hl * s;
            out[1] = ey - vy / DIVE_DECAY * (1.0 - sink);
            out[2] = ed + hd * s;
            out[3] = StrictMath.toDegrees(StrictMath.atan2(-vy * sink, vs * decay));
            out[4] = headingYaw;
            out[5] = 1.0;
            out[6] = RUN_IN_BEAT * runIn * 0.5 + FLIGHT_BEAT * (tGlide - runIn)
                    + GLIDE_BEAT * (1.0 - decay) / glideDecay;
        }
        return out;
    }

    /** A fingerprint of everything that shapes the arc, for the school checksum. */
    int fingerprint() {
        int h = Double.hashCode(pl);
        h = 31 * h + Double.hashCode(py);
        h = 31 * h + Double.hashCode(pd);
        h = 31 * h + Double.hashCode(vl);
        h = 31 * h + Double.hashCode(vy0);
        h = 31 * h + Double.hashCode(vd);
        h = 31 * h + Double.hashCode(el);
        h = 31 * h + Double.hashCode(ey);
        h = 31 * h + Double.hashCode(ed);
        h = 31 * h + Double.hashCode(vs);
        h = 31 * h + Double.hashCode(vy);
        h = 31 * h + Double.hashCode(runIn);
        return h;
    }

    private static double h00(double u) { return 2 * u * u * u - 3 * u * u + 1; }
    private static double h10(double u) { return u * u * u - 2 * u * u + u; }
    private static double h01(double u) { return -2 * u * u * u + 3 * u * u; }
    private static double h11(double u) { return u * u * u - u * u; }
    private static double dh00(double u) { return 6 * u * u - 6 * u; }
    private static double dh10(double u) { return 3 * u * u - 4 * u + 1; }
    private static double dh01(double u) { return -6 * u * u + 6 * u; }
    private static double dh11(double u) { return 3 * u * u - 2 * u; }
    private static double smoother(double u) { return u * u * u * (u * (6 * u - 15) + 10); }
}
