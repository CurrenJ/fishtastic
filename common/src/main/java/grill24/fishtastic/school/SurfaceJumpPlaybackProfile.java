package grill24.fishtastic.school;

/** Deterministic playback rate for a synchronized surface jump. */
public enum SurfaceJumpPlaybackProfile {
    NORMAL,
    LEGENDARY_CINEMATIC;

    public static final double LEGENDARY_MIN_RATE = 0.22;
    public static final double LEGENDARY_SLOW_START = 0.38;
    public static final double LEGENDARY_SLOW_END = 0.62;
    /** The breach's apex above the surface the server asks for, blocks; the sim raises it for a long fish. */
    public static final double NORMAL_BREACH_HEIGHT = 0.5, LEGENDARY_BREACH_HEIGHT = 1.25;
    /** Scales the flight's horizontal speed: 1.5 flattens the 60° launch to about 49°, so it flies farther. */
    public static final double LEGENDARY_REACH_SCALE = 1.5;

    /** The apex the server sends this profile's goal with, blocks above the surface. */
    public double breachHeight() {
        return this == NORMAL ? NORMAL_BREACH_HEIGHT : LEGENDARY_BREACH_HEIGHT;
    }

    /** Multiplies every reach the arc fitter tries: a legendary leap is longer as well as higher. */
    public double reachScale() {
        return this == NORMAL ? 1.0 : LEGENDARY_REACH_SCALE;
    }

    /** Playback rate at an arc source time. Normal jumps retain the original one-source-tick rate. */
    public double rate(SurfaceJumpArc arc, double sourceTime) {
        if (this == NORMAL) return 1.0;
        double exit = arc.exitTick();
        double flight = arc.entryTick() - exit;
        if (sourceTime <= exit || sourceTime >= arc.entryTick() || flight <= 0.0) return 1.0;
        double q = (sourceTime - exit) / flight;
        if (q < LEGENDARY_SLOW_START) {
            return 1.0 + (LEGENDARY_MIN_RATE - 1.0) * smoothstep(q / LEGENDARY_SLOW_START);
        }
        if (q <= LEGENDARY_SLOW_END) return LEGENDARY_MIN_RATE;
        return LEGENDARY_MIN_RATE + (1.0 - LEGENDARY_MIN_RATE)
                * smoothstep((q - LEGENDARY_SLOW_END) / (1.0 - LEGENDARY_SLOW_END));
    }

    /** Exact number of presentation steps needed when the arc's source clock is advanced by {@link #rate}. */
    public int presentationTicks(SurfaceJumpArc arc, int ceiling) {
        double source = 0.0;
        int ticks = 0;
        while (source < arc.durationTicks() && ticks < ceiling) {
            source = StrictMath.min(arc.durationTicks(), source + rate(arc, source));
            ticks++;
        }
        return source >= arc.durationTicks() ? ticks : ceiling + 1;
    }

    private static double smoothstep(double t) {
        t = StrictMath.max(0.0, StrictMath.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }
}
