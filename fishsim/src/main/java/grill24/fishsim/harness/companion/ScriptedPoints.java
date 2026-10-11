package grill24.fishsim.harness.companion;

import grill24.fishsim.companion.CompanionWorld;
import grill24.fishsim.companion.PointOfInterest;
import grill24.fishsim.companion.Vec;

import java.util.ArrayList;
import java.util.List;

/**
 * The scripted things around a headless owner (docs/fish-companions-sim-plan.md §10): mobs that
 * walk up and stay, cats that come to lounge, items that drop, other companions swimming past,
 * what the owner holds, and when they get hurt. Each track is played on the script's own clock,
 * looping with the given period, and sees where the owner is.
 */
final class ScriptedPoints {

    /** One scripted thing: where it is this tick, or null while it's absent. */
    interface Track {
        PointOfInterest at(int tick, OwnerScript.Pilot owner);
    }

    /** What the owner holds on each tick. */
    interface HeldSchedule {
        CompanionWorld.Held at(int tick, OwnerScript.Pilot owner);
    }

    private ScriptedPoints() {}

    static final double ZOMBIE_SPEED = 2.0, CAT_SPEED = 1.4;
    /** A hostile this close hurts its owner once a second. */
    static final double HURT_RANGE = 1.8;

    /**
     * A mob that appears at {@code dist} from the owner, at {@code angleDeg} from their starting
     * facing, walks up to {@code stopAt} from them at {@code speed}, stays, then leaves.
     */
    static Track approacher(PointOfInterest.Kind kind, int id, int period, int from, int to, double angleDeg,
                            double dist, double speed, double stopAt, double height, double radius) {
        Vec[] pos = {null};
        return (tick, p) -> {
            int t = Math.floorMod(tick, period);
            if (t < from || t >= to) {
                pos[0] = null;
                return null;
            }
            Vec feet = p.feet;
            if (pos[0] == null) {
                double a = Math.toRadians(angleDeg);
                pos[0] = new Vec(feet.x() - Math.sin(a) * dist, feet.y() + height, feet.z() + Math.cos(a) * dist);
            }
            Vec to2 = new Vec(feet.x() - pos[0].x(), 0, feet.z() - pos[0].z());
            double d = to2.length();
            Vec vel = Vec.ZERO;
            if (d > stopAt) {
                vel = to2.normalize().scale(Math.min(speed, (d - stopAt) * 20));
                pos[0] = pos[0].add(vel.scale(0.05));
            }
            pos[0] = new Vec(pos[0].x(), feet.y() + height, pos[0].z());
            return new PointOfInterest(kind, id, pos[0], vel, radius);
        };
    }

    /** Something that stays put at a world position between two ticks of the loop. */
    static Track sitter(PointOfInterest.Kind kind, int id, int period, int from, int to, Vec at, double radius) {
        return (tick, p) -> {
            int t = Math.floorMod(tick, period);
            return t >= from && t < to ? new PointOfInterest(kind, id, at, Vec.ZERO, radius) : null;
        };
    }

    /** Something travelling a straight line from {@code a} to {@code b} at {@code speed}, starting at {@code from}. */
    static Track passer(PointOfInterest.Kind kind, int id, int period, int from, Vec a, Vec b, double speed,
                        double radius) {
        double len = a.distanceTo(b);
        int ticks = (int) Math.ceil(len / speed * 20);
        Vec vel = b.sub(a).normalize().scale(speed);
        return (tick, p) -> {
            int t = Math.floorMod(tick, period) - from;
            if (t < 0 || t >= ticks) return null;
            return new PointOfInterest(kind, id, a.add(vel.scale(t * 0.05)), vel, radius);
        };
    }

    /**
     * Something keeping pace beside the owner at a fixed offset in their frame (another
     * player's companion walking with them), between two ticks of the loop.
     */
    static Track alongside(PointOfInterest.Kind kind, int id, int period, int from, int to, double right,
                           double ahead, double up, double radius) {
        return (tick, p) -> {
            int t = Math.floorMod(tick, period);
            if (t < from || t >= to) return null;
            Vec f = Vec.fromYawPitch(p.yaw, 0), r = f.cross(Vec.UP);
            Vec at = p.feet.add(r.scale(right)).add(f.scale(ahead)).add(0, up, 0);
            return new PointOfInterest(kind, id, at, p.vel, radius);
        };
    }

    /**
     * Items dropping near the owner: one at each listed tick, at a seeded spot 1.5–3.5 blocks off,
     * lying there for {@code life} ticks unless the owner walks over it and picks it up.
     */
    static List<Track> drops(int period, int[] at, int life, java.util.Random r, int firstId) {
        List<Track> out = new ArrayList<>();
        for (int k = 0; k < at.length; k++) {
            int from = at[k];
            double a = r.nextDouble() * Math.PI * 2, d = 1.5 + 2 * r.nextDouble();
            int id = firstId + k;
            Vec[] spot = {null};
            boolean[] taken = {false};
            out.add((tick, p) -> {
                int t = Math.floorMod(tick, period);
                if (t < from || t >= from + life) {
                    spot[0] = null;
                    taken[0] = false;
                    return null;
                }
                if (taken[0]) return null;
                if (spot[0] == null) {
                    spot[0] = new Vec(p.feet.x() + Math.cos(a) * d, p.feet.y() + 0.15, p.feet.z() + Math.sin(a) * d);
                }
                if (new Vec(spot[0].x() - p.feet.x(), 0, spot[0].z() - p.feet.z()).length() < 1.0) {
                    taken[0] = true;
                    return null;
                }
                return new PointOfInterest(PointOfInterest.Kind.DROPPED_ITEM, id, spot[0], Vec.ZERO, 0.15);
            });
        }
        return out;
    }

    /** Holds each item from its start tick until the next one's; {@code ticks} and {@code held} pair up. */
    static HeldSchedule schedule(int period, int[] ticks, CompanionWorld.Held[] held) {
        return (tick, p) -> {
            int t = Math.floorMod(tick, period);
            CompanionWorld.Held h = CompanionWorld.Held.NONE;
            for (int i = 0; i < ticks.length; i++) if (t >= ticks[i]) h = held[i];
            return h;
        };
    }
}
