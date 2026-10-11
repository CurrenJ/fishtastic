package grill24.fishtastic.client.companion;

import grill24.fishsim.companion.OwnerState;
import grill24.fishsim.companion.PointOfInterest;
import grill24.fishsim.companion.Vec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Dev-only fake points of interest for the local companion (the owner's solo-playtest rule): a
 * zombie that walks up, a cat that strolls over and sits, an item dropped ahead, another companion
 * swimming past. Each is a short script laid out from where the owner stands and looks when it is
 * made, like the harness's {@code ScriptedPoints}, and is marked with particles so you can see what
 * the fish is reacting to. Only the local companion perceives them.
 */
final class CompanionDevPoints {
    private CompanionDevPoints() {}

    enum Kind { HOSTILE, CAT, ITEM, COMPANION }

    /** One scripted thing: walks from {@code from} toward {@code to} at {@code speed}, then stays until it expires. */
    private static final class Track {
        final PointOfInterest.Kind kind;
        final int id;
        final Vec to;
        final double speed, radius;
        final int lifeTicks;
        final ParticleOptions marker;
        Vec pos, vel = Vec.ZERO;
        int age;

        Track(PointOfInterest.Kind kind, int id, Vec from, Vec to, double speed, double radius, int lifeTicks,
              ParticleOptions marker) {
            this.kind = kind;
            this.id = id;
            this.pos = from;
            this.to = to;
            this.speed = speed;
            this.radius = radius;
            this.lifeTicks = lifeTicks;
            this.marker = marker;
        }
    }

    private static final List<Track> TRACKS = new ArrayList<>();
    private static int nextId = -1_000_000;

    static String add(Kind kind, OwnerState o) {
        Vec feet = o.feet();
        Vec f = o.flatForward(), r = o.flatRight();
        Track t = switch (kind) {
            // A zombie from 7 blocks ahead, walking up to arm's reach and staying.
            case HOSTILE -> new Track(PointOfInterest.Kind.HOSTILE, nextId--, feet.add(f.scale(7)).add(0, 1, 0),
                    feet.add(f.scale(1.5)).add(0, 1, 0), 1.2, 0.3, 25 * 20, ParticleTypes.ANGRY_VILLAGER);
            // A cat strolling in from the right and sitting beside you.
            case CAT -> new Track(PointOfInterest.Kind.CAT, nextId--, feet.add(r.scale(5)).add(0, 0.35, 0),
                    feet.add(r.scale(1.2)).add(f.scale(0.4)).add(0, 0.35, 0), 1.0, 0.3, 40 * 20, ParticleTypes.HEART);
            // An item on the ground ahead.
            case ITEM -> {
                Vec at = feet.add(f.scale(2.5)).add(0, 0.15, 0);
                yield new Track(PointOfInterest.Kind.DROPPED_ITEM, nextId--, at, at, 0, 0.125, 20 * 20,
                        ParticleTypes.HAPPY_VILLAGER);
            }
            // Another companion swimming across in front, left to right.
            case COMPANION -> new Track(PointOfInterest.Kind.COMPANION, nextId--,
                    o.eye().add(f.scale(2.5)).add(r.scale(-5)), o.eye().add(f.scale(2.5)).add(r.scale(5)),
                    1.0, 0.15, 12 * 20, ParticleTypes.END_ROD);
        };
        TRACKS.add(t);
        return "Fake " + kind.name().toLowerCase(Locale.ROOT) + " added (" + TRACKS.size() + " now)";
    }

    static void clear() {
        TRACKS.clear();
    }

    /** Client tick: moves every track along, expires the old ones, and marks each with particles. */
    static void tick(ClientLevel level) {
        for (Iterator<Track> it = TRACKS.iterator(); it.hasNext(); ) {
            Track t = it.next();
            if (++t.age > t.lifeTicks) {
                it.remove();
                continue;
            }
            Vec gap = t.to.sub(t.pos);
            double step = t.speed / 20;
            if (gap.length() > step) {
                t.vel = gap.normalize().scale(t.speed);
                t.pos = t.pos.add(gap.normalize().scale(step));
            } else {
                t.vel = Vec.ZERO;
                t.pos = t.to;
                // A passer that has arrived is gone.
                if (t.kind == PointOfInterest.Kind.COMPANION) it.remove();
            }
            if (t.age % 4 == 0) level.addParticle(t.marker, t.pos.x(), t.pos.y() + t.radius + 0.2, t.pos.z(), 0, 0, 0);
        }
    }

    static List<PointOfInterest> points() {
        if (TRACKS.isEmpty()) return List.of();
        List<PointOfInterest> out = new ArrayList<>(TRACKS.size());
        for (Track t : TRACKS) out.add(new PointOfInterest(t.kind, t.id, t.pos, t.vel, t.radius));
        return out;
    }
}
