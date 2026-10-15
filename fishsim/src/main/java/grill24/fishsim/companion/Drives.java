package grill24.fishsim.companion;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The urges that come from what is around the owner (docs/fish-companions-sim-plan.md §3.3, §10):
 * updated every tick for every class, whatever its body is doing. The swimmer's {@link Mind} turns
 * them into goals; a percher's body into slots and poses.
 *
 * <p>Wariness comes from hostiles (at once), cats (after a dwell) and a predator fish in the
 * owner's hand. It too follows R7.2: a cat lounging nearby or a predator held for a while wears off
 * and then can't set it off again for a minute; a hostile that keeps its distance wears off, one
 * closing in never does.
 *
 * <p>Rest builds while the owner idles and drains the moment they move or something happens; a
 * rested-enough fish settles (a slower, closer, longer-lingering idle) until it's woken.
 *
 * <p>Curiosity follows spec R7.2 for things that could stay around during normal play (a stream
 * of dropped items, a rod held all session): a new thing takes a moment to be <b>noticed</b>, a
 * close look <b>habituates</b> it, and once it has had enough it goes on <b>cooldown</b>. Each later
 * visit is less interesting than the last, until the fish has forgotten it; and a run of things of
 * one kind (an item stream) tires it of that kind.
 */
final class Drives {

    private static final int TPS = 20;
    private static final double DT = 1.0 / TPS;

    /** Things further than this from the owner's chest, blocks, aren't noticed at all. */
    static final double SENSE_RANGE = 10;
    /**
     * Curiosity reaches only things within this of the owner: further off, keeping near them
     * matters more, and an inspection out there would fight the proximity drive.
     */
    static final double CURIOSITY_REACH = 4.0;
    /** A thing gone for this long is forgotten, so coming back makes it new again. */
    private static final int FORGET_TICKS = 60 * TPS;
    /** Ticks a satisfied thing can't draw interest again. */
    private static final int COOLDOWN_TICKS = 30 * TPS;
    /** Each visit is this much as interesting as the one before. */
    private static final double REVISIT = 0.6;
    /** Seconds for a new thing to be fully noticed: a creature takes a beat before it reacts. */
    private static final double NOTICE_SECONDS = 0.5;
    /** Each finished inspection tires the fish of that kind of thing by this much (at most {@link #FATIGUE_MAX})... */
    private static final double FATIGUE_STEP = 0.25, FATIGUE_MAX = 0.8;
    /** ...and the fatigue wears off over this many seconds. */
    private static final double FATIGUE_SECONDS = 90;

    /** One thing the fish might be curious about, as remembered between ticks. */
    static final class Interest {
        final long key;
        /** What it is, or null for the held item. */
        final PointOfInterest.Kind kind;
        /** The held item's category, or null for things in the world. */
        final CompanionWorld.Held held;
        Vec pos = Vec.ZERO, vel = Vec.ZERO;
        double radius;
        /** 0..1: how interesting it is right now, before temperament and reach. */
        float interest;
        /** 0..1: how far it has been noticed since it appeared. */
        float noticed;
        /** Ticks spent close to it this visit. */
        int engaged;
        int cooldown;
        int visits;
        int lastSeen = Integer.MIN_VALUE / 2;
        boolean present;

        Interest(long key, PointOfInterest.Kind kind, CompanionWorld.Held held) {
            this.key = key;
            this.kind = kind;
            this.held = held;
        }

        boolean isHeld() {
            return held != null;
        }
    }

    private final Map<Long, Interest> interests = new HashMap<>();
    /** How tired of each kind of world thing it is (dropped items, other companions), 0..{@link #FATIGUE_MAX}. */
    private final double[] fatigue = new double[PointOfInterest.Kind.values().length];
    private final Temperament temperament;
    private int tick;

    // ── Wariness ──

    enum Threat { NONE, HOSTILE, CAT, PREDATOR }

    /** One thing the fish is wary of, as remembered between ticks. */
    private static final class Fear {
        final Threat kind;
        Vec pos = Vec.ZERO;
        int dwell;
        double habituation;
        int cooldown;
        int lastSeen = Integer.MIN_VALUE / 2;
        boolean present;

        Fear(Threat kind) {
            this.kind = kind;
        }
    }

    /** Hostiles further than this from the owner, blocks, aren't a worry; within {@link #HOSTILE_NEAR} they're the most. */
    static final double HOSTILE_FAR = 8, HOSTILE_NEAR = 3;
    static final double CAT_FAR = 5, CAT_NEAR = 1.5;
    /** Ticks a thing must be there before it alarms: a hostile at once (a reaction time), a cat after a look. */
    private static final int HOSTILE_DWELL = 4, CAT_DWELL = 2 * TPS, PREDATOR_DWELL = 4;
    /** Seconds to get used to it while it stays; a hostile only while it keeps more than {@link #HOSTILE_HABIT_RANGE} away. */
    private static final double CAT_HABIT = 15, PREDATOR_HABIT = 20, HOSTILE_HABIT = 40, HOSTILE_HABIT_RANGE = 4;
    /** Once used to a cat or a held predator, a minute before that one can alarm it again — and then only half as much. */
    private static final int FEAR_COOLDOWN_TICKS = 60 * TPS;

    private final Map<Long, Fear> fears = new HashMap<>();
    /** 0..1: how wary it is of the most alarming thing about. */
    float wariness;
    Threat threat = Threat.NONE;
    /** Where that thing is (null when nothing is). */
    Vec threatPos;
    /** A party-mate predator on its lap (D21, PartyLayer's scene), feared as a held predator is; null for none. */
    Vec sceneThreat;
    private static final long SCENE_FEAR_ID = Long.MIN_VALUE + 1;
    /** A hostile within {@link #HOSTILE_NEAR} of the owner, used to it or not: an anchored fish ducks in. */
    boolean hostileClose;

    // ── Rest ──

    /** Owner idle this long before rest starts to build, ticks. */
    private static final int REST_ONSET_TICKS = 10 * TPS;
    /** Seconds of further idling for rest to build from 0 to 1 (at temperament weight 1). */
    private static final double REST_BUILD_SECONDS = 45;
    /** Seconds for rest to drain once the owner moves; faster when something stirs it. */
    private static final double WAKE_SECONDS = 1.5, STARTLE_SECONDS = 1.0;
    /** Rest at which it settles, and below which it wakes. */
    static final float SETTLE_AT = 0.5f, WAKE_AT = 0.3f;

    /** Ticks since the owner was last hurt (large when never). */
    int sinceHurt = Integer.MAX_VALUE / 2;

    /** 0..1: how restful it feels. */
    float rest;
    /** Settled: rest crossed {@link #SETTLE_AT} with the owner still, and hasn't fallen below {@link #WAKE_AT}. */
    boolean resting;
    private boolean ownerMoving;
    private int slowTicks, stillTicks;

    // ── Mood (Phase 5) ──

    /**
     * The mood that sets the levers: the fish's own, or for a group its leader's (each member keeps
     * its own for its pose, CompanionAgent / CompanionGroup.Follower). The agent updates it.
     */
    final Mood mood = new Mood();
    /**
     * The mood's pace, only while the owner stands about: following and catching up stay the
     * temperament's own, so a mood never leaves a fish behind.
     */
    float moodPace() {
        return ownerMoving ? 1f : mood.pace;
    }

    /** Keys warm things apart from entity ids and held items. */
    private static final long WARMTH_KEY = 0x4000_0000_0000_0000L;
    /** A warm thing's rough size, blocks: its hold keeps the fish out of the flames. */
    static final double WARMTH_RADIUS = 1.0;

    /** 0..1: the pull of the most interesting thing in reach. */
    float curiosity;
    /** That thing, or null. */
    Interest target;

    Drives(Temperament temperament) {
        this.temperament = temperament;
    }

    Temperament temperament() {
        return temperament;
    }

    /**
     * Dev trigger (the in-game debug command): as if the owner had already idled long enough to
     * settle. Nothing in the sim calls it; it only moves the drive the next update would build.
     */
    void devDrowsy() {
        stillTicks = Math.max(stillTicks, REST_ONSET_TICKS + 1);
        rest = Math.max(rest, SETTLE_AT);
    }

    /**
     * A percher (BENTHIC, ANCHORED): the mood's rest gain doesn't apply. A resting percher climbs
     * onto its owner's shoulder, which first person never shows (sleepy cost a benthic 1.1 points
     * of seen over 10 seeds, plan §5 Phase 5).
     */
    boolean percher;

    void update(CompanionWorld w) {
        tick++;
        sinceHurt = w.hurt() ? 0 : Math.min(Integer.MAX_VALUE / 2, sinceHurt + 1);
        sense(w);
        updateWariness(w.owner());
        updateCuriosity(w.owner());
        updateRest(w);
    }

    /**
     * Reports a close look at {@code i}: only real attention habituates, so a fish that never got
     * near (the owner walked on) still finds it new next time.
     */
    void engage(Interest i, double distance, double holdDistance) {
        if (distance < holdDistance + 0.6) i.engaged++;
    }

    // ── Perception ─────────────────────────────────────────────────────────────────────────────

    private void sense(CompanionWorld w) {
        Vec chest = w.owner().chest();
        for (Interest i : interests.values()) i.present = false;
        for (Fear f : fears.values()) f.present = false;
        for (PointOfInterest p : w.points()) {
            if (p.kind() == PointOfInterest.Kind.HOSTILE || p.kind() == PointOfInterest.Kind.CAT) {
                if (p.pos().distanceTo(chest) <= SENSE_RANGE) {
                    fear(p.id(), p.kind() == PointOfInterest.Kind.CAT ? Threat.CAT : Threat.HOSTILE, p.pos());
                }
                continue;
            }
            if (p.kind() != PointOfInterest.Kind.DROPPED_ITEM && p.kind() != PointOfInterest.Kind.COMPANION) continue;
            if (p.pos().distanceTo(chest) > SENSE_RANGE) continue;
            Interest i = interests.computeIfAbsent((long) p.id(), k -> new Interest(k, p.kind(), null));
            see(i, p.pos(), p.vel(), p.radius());
        }
        if (mood.wantsWarmth) {
            for (Vec p : w.moodAmbient().warmth()) {
                if (p.distanceTo(chest) > SENSE_RANGE) continue;
                long key = WARMTH_KEY | (((long) Math.floor(p.x()) & 0x1FFFFF) << 42)
                        | (((long) Math.floor(p.y()) & 0x1FFFFF) << 21) | ((long) Math.floor(p.z()) & 0x1FFFFF);
                Interest i = interests.computeIfAbsent(key, k -> new Interest(k, PointOfInterest.Kind.WARMTH, null));
                see(i, p, Vec.ZERO, WARMTH_RADIUS);
            }
        }
        CompanionWorld.Held h = w.held();
        if (h == CompanionWorld.Held.PREDATOR) fear(-1L - h.ordinal(), Threat.PREDATOR, w.heldPoint());
        if (sceneThreat != null) fear(SCENE_FEAR_ID, Threat.PREDATOR, sceneThreat);
        if (h != CompanionWorld.Held.NONE && novelty(h) > 0) {
            Interest i = interests.computeIfAbsent(-1L - h.ordinal(), k -> new Interest(k, null, h));
            see(i, w.heldPoint(), w.owner().velocity(), 0.1);
        }
        for (Iterator<Interest> it = interests.values().iterator(); it.hasNext(); ) {
            Interest i = it.next();
            if (!i.present && tick - i.lastSeen > FORGET_TICKS) it.remove();
        }
        for (Iterator<Fear> it = fears.values().iterator(); it.hasNext(); ) {
            Fear f = it.next();
            if (!f.present && tick - f.lastSeen > FORGET_TICKS) it.remove();
        }
    }

    private void fear(long id, Threat kind, Vec pos) {
        Fear f = fears.computeIfAbsent(id, k -> new Fear(kind));
        f.present = true;
        f.lastSeen = tick;
        f.pos = pos;
    }

    private void see(Interest i, Vec pos, Vec vel, double radius) {
        if (tick - i.lastSeen > 1 && i.cooldown == 0) {
            // Newly arrived, or back after being away: interesting, less so each visit.
            double fresh = (i.isHeld() ? novelty(i.held) : novelty(i.kind) * (1 - fatigue[i.kind.ordinal()]))
                    * Math.pow(REVISIT, i.visits);
            i.interest = (float) Math.max(i.interest, fresh);
            i.engaged = 0;
            i.noticed = 0;
        }
        i.present = true;
        i.lastSeen = tick;
        i.pos = pos;
        i.vel = vel;
        i.radius = radius;
    }

    /** How interesting a held item is when it first appears (spec §3.1). A predator fish is fear, not interest. */
    static double novelty(CompanionWorld.Held h) {
        return switch (h) {
            case BAIT -> 1.0;
            case ROD, SAME_SPECIES -> 0.9;
            case NAME_TAG -> 0.7;
            case FOOD -> 0.62;
            default -> 0;
        };
    }

    static double novelty(PointOfInterest.Kind k) {
        if (k == PointOfInterest.Kind.WARMTH) return 0.85;
        return k == PointOfInterest.Kind.COMPANION ? 0.8 : 0.7;
    }

    /** Seconds of close looking that satisfy it. */
    private static double inspectSeconds(Interest i) {
        if (i.held == CompanionWorld.Held.BAIT || i.held == CompanionWorld.Held.ROD) return 6;
        if (i.held == CompanionWorld.Held.FOOD) return 2.5;
        return 4;
    }

    // ── Wariness ───────────────────────────────────────────────────────────────────────────────

    private void updateWariness(OwnerState o) {
        Vec chest = o.chest();
        wariness = 0;
        threat = Threat.NONE;
        threatPos = null;
        hostileClose = false;
        for (Fear f : fears.values()) {
            if (!f.present) {
                f.dwell = 0;
                f.habituation = Math.max(0, f.habituation - DT / 30);
                if (f.cooldown > 0) f.cooldown--;
                continue;
            }
            double d = f.pos.distanceTo(chest);
            if (f.kind == Threat.HOSTILE && d < HOSTILE_NEAR) hostileClose = true;
            if (f.cooldown > 0) {
                if (--f.cooldown == 0) f.habituation = 0.5; // back, but half used to it
                continue;
            }
            f.dwell++;
            int needed = switch (f.kind) {
                case CAT -> CAT_DWELL;
                case PREDATOR -> PREDATOR_DWELL;
                default -> HOSTILE_DWELL;
            };
            if (f.dwell < needed) continue;
            double raw = switch (f.kind) {
                case HOSTILE -> Mind.smoothstep(HOSTILE_FAR, HOSTILE_NEAR, d);
                case CAT -> Mind.smoothstep(CAT_FAR, CAT_NEAR, d);
                default -> 0.8;
            };
            if (raw <= 0) continue;
            double habit = switch (f.kind) {
                case CAT -> CAT_HABIT;
                case PREDATOR -> PREDATOR_HABIT;
                default -> d > HOSTILE_HABIT_RANGE ? HOSTILE_HABIT : Double.POSITIVE_INFINITY;
            };
            if (Double.isFinite(habit)) f.habituation = Math.min(1, f.habituation + DT / habit);
            if (f.habituation >= 1 && f.kind != Threat.HOSTILE) {
                f.cooldown = FEAR_COOLDOWN_TICKS;
                continue;
            }
            float v = (float) Math.min(1, raw * (1 - 0.7 * f.habituation) * temperament.wariness());
            if (v > wariness) {
                wariness = v;
                threat = f.kind;
                threatPos = f.pos;
            }
        }
    }

    // ── Rest ───────────────────────────────────────────────────────────────────────────────────

    private void updateRest(CompanionWorld w) {
        double hs = w.owner().horizontalSpeed();
        if (hs > 1.0) {
            ownerMoving = true;
            slowTicks = 0;
        } else {
            slowTicks = hs < 0.4 ? slowTicks + 1 : 0;
            if (slowTicks > 15) ownerMoving = false;
        }
        stillTicks = ownerMoving ? 0 : stillTicks + 1;
        if (ownerMoving) {
            rest = Math.max(0, rest - (float) (DT / WAKE_SECONDS));
        } else if (stillTicks > REST_ONSET_TICKS) {
            rest = Math.min(1, rest + (float) (DT / REST_BUILD_SECONDS * (temperament.rest() * (percher ? 1f : mood.rest))));
        }
        // Something happening wakes it; the owner getting hurt, at once.
        if (curiosity > 0.3f || wariness > 0.3f) rest = Math.max(0, rest - (float) (DT / STARTLE_SECONDS));
        if (w.hurt()) rest = 0;
        if (resting) {
            resting = rest >= WAKE_AT && !ownerMoving;
        } else {
            resting = rest >= SETTLE_AT && !ownerMoving;
        }
    }

    // ── Curiosity ──────────────────────────────────────────────────────────────────────────────

    private void updateCuriosity(OwnerState o) {
        Vec chest = o.chest();
        curiosity = 0;
        target = null;
        for (int k = 0; k < fatigue.length; k++) fatigue[k] = Math.max(0, fatigue[k] - DT * FATIGUE_MAX / FATIGUE_SECONDS);
        for (Interest i : interests.values()) {
            if (i.cooldown > 0) {
                i.cooldown--;
                i.interest = 0;
                continue;
            }
            if (!i.present) continue;
            // A close look satisfies it in a few seconds; unattended, it grows dull slowly. A warm
            // thing doesn't: the bask ends when the mood has had its fill (Mood#wantsWarmth).
            if (i.kind != PointOfInterest.Kind.WARMTH) i.interest -= (float) (DT / (i.engaged > 0 ? inspectSeconds(i) : 40));
            if (i.interest <= 0.08f) {
                i.interest = 0;
                if (i.engaged > 0) {
                    i.visits++;
                    i.cooldown = COOLDOWN_TICKS;
                    i.engaged = 0;
                    if (!i.isHeld()) {
                        fatigue[i.kind.ordinal()] = Math.min(FATIGUE_MAX, fatigue[i.kind.ordinal()] + FATIGUE_STEP);
                    }
                }
                continue;
            }
            i.noticed = (float) Math.min(1, i.noticed + DT / NOTICE_SECONDS);
            double reach = i.isHeld() ? 1 : Mind.smoothstep(CURIOSITY_REACH + 1, CURIOSITY_REACH, i.pos.distanceTo(chest));
            // Warmth draws a cold fish whatever its mood; the mood's damper is for everything else.
            float gain = i.kind == PointOfInterest.Kind.WARMTH ? temperament.curiosity() : temperament.curiosity() * mood.curiosity;
            float v = (float) Math.min(1, i.interest * i.noticed * reach * gain);
            if (v > curiosity) {
                curiosity = v;
                target = i;
            }
        }
    }
}
