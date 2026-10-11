package grill24.fishsim.harness.companion;

import grill24.fishsim.companion.OwnerState;
import grill24.fishsim.companion.Vec;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A scripted owner for headless runs (docs/fish-companions-sim-plan.md §4.1): a list of segments
 * (walk somewhere, wait, look around, teleport, fly) played in a loop by a simple {@link Pilot}.
 * The viewer drives the same pilot from the keyboard instead.
 */
public final class OwnerScript {

    public static final List<String> NAMES = List.of(
            "idle_fixed", "idle_looking", "walk", "walk_turns", "sprint_stop", "camera_sweep",
            "corridor", "doorway", "forest", "teleport", "elytra");

    public static final double WALK = 4.317, SPRINT = 5.612;

    /** One step of a script; returns true when it's finished. */
    interface Segment {
        boolean tick(Pilot p);
    }

    public final String name;
    public final String sceneName;
    private final List<Segment> segments;
    private final Pilot pilot;
    private int index;

    private OwnerScript(String name, String sceneName, Vec startFeet, float startYaw, List<Segment> segments) {
        this.name = name;
        this.sceneName = sceneName;
        this.segments = segments;
        this.pilot = new Pilot(startFeet, startYaw);
    }

    /** Advances one tick and returns the owner as the companion sees it. */
    public OwnerState next() {
        pilot.beginTick();
        if (segments.get(index).tick(pilot)) index = (index + 1) % segments.size();
        return pilot.endTick();
    }

    public Pilot pilot() {
        return pilot;
    }

    // ── The pilot ──────────────────────────────────────────────────────────────────────────────

    /** A player body: feet position, look, and a velocity that reaches its target quickly. */
    public static final class Pilot {
        private static final double ACCEL = 20; // blocks/s² — players reach speed in a few ticks
        private static final float TURN = 20f;  // degrees/tick when turning to face travel

        public Vec feet;
        public Vec vel = Vec.ZERO;
        public float yaw, pitch;
        public boolean firstPerson = true;
        public float fov = 70f;
        private Vec desiredVel = Vec.ZERO;
        private boolean lookOverride, teleported, flying;
        private Vec prevFeet;

        public Pilot(Vec feet, float yaw) {
            this.feet = feet;
            this.yaw = yaw;
            this.prevFeet = feet;
        }

        public void beginTick() {
            desiredVel = Vec.ZERO;
            lookOverride = false;
            teleported = false;
            flying = false;
        }

        /** Asks to move at {@code velocity} this tick (horizontal unless flying). */
        public void move(Vec velocity) {
            desiredVel = velocity;
        }

        void fly(Vec velocity) {
            desiredVel = velocity;
            flying = true;
        }

        public void look(float yawDeg, float pitchDeg) {
            yaw = yawDeg;
            pitch = Math.max(-90f, Math.min(90f, pitchDeg));
            lookOverride = true;
        }

        public void teleport(Vec to) {
            feet = to;
            vel = Vec.ZERO;
            teleported = true;
        }

        public OwnerState endTick() {
            double dt = 0.05;
            Vec dv = desiredVel.sub(vel).clampLength(ACCEL * dt);
            vel = vel.add(dv);
            if (!flying) vel = new Vec(vel.x(), 0, vel.z());
            Vec before = feet;
            if (!teleported) feet = feet.add(vel.scale(dt));
            if (!lookOverride && vel.horizontalLength() > 0.5) {
                float target = (float) vel.yawDeg();
                float diff = wrap(target - yaw);
                yaw = wrap(yaw + Math.max(-TURN, Math.min(TURN, diff)));
                pitch += (0f - pitch) * 0.2f;
            }
            Vec realVel = teleported ? Vec.ZERO : feet.sub(before).scale(1 / dt);
            prevFeet = before;
            return new OwnerState(feet.add(0, OwnerState.EYE_HEIGHT, 0), realVel, yaw, pitch,
                    vel.horizontalLength() > (WALK + SPRINT) / 2, teleported, firstPerson, fov);
        }
    }

    // ── Segments ───────────────────────────────────────────────────────────────────────────────

    static Segment walkTo(double x, double z, double speed) {
        return p -> {
            Vec to = new Vec(x - p.feet.x(), 0, z - p.feet.z());
            double d = to.length();
            if (d < 0.25) {
                p.move(Vec.ZERO);
                return p.vel.length() < 0.2;
            }
            // Brake in time to stop at the waypoint.
            double s = Math.min(speed, Math.sqrt(2 * Pilot.ACCEL * 0.5 * d));
            p.move(to.normalize().scale(s));
            return false;
        };
    }

    static Segment flyTo(double x, double y, double z, double speed) {
        return p -> {
            Vec to = new Vec(x, y, z).sub(p.feet);
            double d = to.length();
            if (d < 0.5) return true;
            p.fly(to.normalize().scale(Math.min(speed, d * 4)));
            return false;
        };
    }

    static Segment waitTicks(int ticks) {
        int[] left = {ticks};
        return p -> {
            p.move(Vec.ZERO);
            if (left[0]-- <= 0) {
                left[0] = ticks;
                return true;
            }
            return false;
        };
    }

    /** Turns the view by a relative amount over {@code ticks}, eased, while standing. */
    static Segment lookBy(float dYaw, float dPitch, int ticks) {
        float[] from = new float[2];
        int[] t = {0};
        return p -> {
            if (t[0] == 0) {
                from[0] = p.yaw;
                from[1] = p.pitch;
            }
            t[0]++;
            double u = 0.5 - 0.5 * Math.cos(Math.PI * Math.min(1.0, t[0] / (double) ticks));
            p.move(Vec.ZERO);
            p.look((float) (from[0] + dYaw * u), (float) (from[1] + dPitch * u));
            if (t[0] >= ticks) {
                t[0] = 0;
                return true;
            }
            return false;
        };
    }

    /** Holds the current view (a standing player who isn't touching the mouse). */
    static Segment hold(int ticks) {
        int[] left = {ticks};
        return p -> {
            p.move(Vec.ZERO);
            p.look(p.yaw, p.pitch);
            if (left[0]-- <= 0) {
                left[0] = ticks;
                return true;
            }
            return false;
        };
    }

    static Segment teleportTo(double x, double y, double z) {
        return p -> {
            p.teleport(new Vec(x, y, z));
            return true;
        };
    }

    // ── The library ────────────────────────────────────────────────────────────────────────────

    public static OwnerScript named(String name, long seed) {
        Random r = new Random(seed);
        double y = 64;
        List<Segment> s = new ArrayList<>();
        switch (name) {
            case "idle_fixed" -> {
                s.add(hold(6000));
                return new OwnerScript(name, "open", new Vec(0.5, y, 0.5), 0f, s);
            }
            case "idle_looking" -> {
                for (int i = 0; i < 40; i++) {
                    s.add(lookBy((float) ((r.nextDouble() - 0.5) * 120), (float) ((r.nextDouble() - 0.5) * 30), 20 + r.nextInt(20)));
                    s.add(hold(60 + r.nextInt(140)));
                }
                return new OwnerScript(name, "open", new Vec(0.5, y, 0.5), 0f, s);
            }
            case "walk" -> {
                s.add(walkTo(0.5, 40.5, WALK));
                s.add(waitTicks(20));
                s.add(walkTo(0.5, 0.5, WALK));
                s.add(waitTicks(20));
                return new OwnerScript(name, "open", new Vec(0.5, y, 0.5), 0f, s);
            }
            case "walk_turns" -> {
                for (int i = 0; i < 30; i++) {
                    double a = r.nextDouble() * Math.PI * 2, d = 4 + r.nextDouble() * 22;
                    s.add(walkTo(Math.cos(a) * d, Math.sin(a) * d, WALK));
                    if (r.nextDouble() < 0.6) s.add(waitTicks(r.nextInt(80)));
                }
                s.add(walkTo(0.5, 0.5, WALK));
                return new OwnerScript(name, "open", new Vec(0.5, y, 0.5), 0f, s);
            }
            case "forest" -> {
                // The owner keeps to the clear lanes between trunks (trunk blocks occupy offsets
                // 0..2 of every 5), moving along one axis at a time — the fish has to weave.
                double x = 4, z = 4;
                for (int i = 0; i < 24; i++) {
                    double lane = 5 * (r.nextInt(7) - 3) + 4;
                    if (i % 2 == 0) x = lane; else z = lane;
                    s.add(walkTo(x, z, WALK));
                    if (r.nextDouble() < 0.5) s.add(waitTicks(r.nextInt(80)));
                }
                s.add(walkTo(x, 4, WALK));
                s.add(walkTo(4, 4, WALK));
                return new OwnerScript(name, "forest", new Vec(4, y, 4), 0f, s);
            }
            case "sprint_stop" -> {
                s.add(walkTo(0.5, 35.5, SPRINT));
                s.add(waitTicks(100));
                s.add(walkTo(0.5, 0.5, SPRINT));
                s.add(waitTicks(100));
                return new OwnerScript(name, "open", new Vec(0.5, y, 0.5), 0f, s);
            }
            case "camera_sweep" -> {
                s.add(hold(40));
                s.add(lookBy(110, 0, 60));
                s.add(hold(40));
                s.add(lookBy(-220, 0, 120));
                s.add(hold(40));
                s.add(lookBy(110, 60, 40));   // look down
                s.add(hold(80));
                s.add(lookBy(0, -90, 50));    // look up
                s.add(hold(40));
                s.add(lookBy(0, 30, 30));
                s.add(lookBy(180, 0, 25));    // quick about-face
                s.add(hold(60));
                s.add(lookBy(-180, 0, 25));
                return new OwnerScript(name, "open", new Vec(0.5, y, 0.5), 0f, s);
            }
            case "corridor" -> {
                s.add(waitTicks(40));
                s.add(walkTo(0.5, 29.5, WALK));
                s.add(walkTo(21.5, 29.5, WALK));
                s.add(walkTo(26.5, 29.5, WALK));
                s.add(waitTicks(60));
                s.add(walkTo(21.5, 29.5, WALK));
                s.add(walkTo(0.5, 29.5, WALK));
                s.add(walkTo(0.5, -6.5, WALK));
                return new OwnerScript(name, "corridor", new Vec(0.5, y, -6.5), 0f, s);
            }
            case "doorway" -> {
                s.add(waitTicks(40));
                s.add(walkTo(0.5, 18.5, WALK));
                s.add(waitTicks(80));
                s.add(walkTo(0.5, 0.5, WALK));
                return new OwnerScript(name, "doorway", new Vec(0.5, y, 0.5), 0f, s);
            }
            case "teleport" -> {
                s.add(walkTo(0.5, 10.5, WALK));
                s.add(waitTicks(20));
                s.add(teleportTo(200.5, y, 0.5));
                s.add(waitTicks(100));
                s.add(walkTo(200.5, 10.5, WALK));
                s.add(teleportTo(0.5, y, 0.5));
                s.add(waitTicks(100));
                return new OwnerScript(name, "open", new Vec(0.5, y, 0.5), 0f, s);
            }
            case "elytra" -> {
                s.add(waitTicks(40));
                s.add(flyTo(0.5, y + 12, 20.5, 8));
                s.add(flyTo(0.5, y + 12, 260.5, 28));
                s.add(flyTo(0.5, y, 275.5, 8));
                s.add(waitTicks(100));
                s.add(teleportTo(0.5, y, 0.5));
                s.add(waitTicks(60));
                return new OwnerScript(name, "open", new Vec(0.5, y, 0.5), 0f, s);
            }
            default -> throw new IllegalArgumentException("unknown script " + name + " (have " + NAMES + ")");
        }
    }

    static float wrap(float deg) {
        deg %= 360f;
        if (deg >= 180f) deg -= 360f;
        if (deg < -180f) deg += 360f;
        return deg;
    }
}
