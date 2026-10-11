package grill24.fishsim.companion;

/**
 * What a benthic companion on the ground decides (docs/fish-companions-benthic-follow.md §3, §6):
 * idle round its owner's feet, follow them, or catch up — and when it can't, ask for the fade
 * onto its perch.
 *
 * <ul>
 *   <li><b>The way you went.</b> The owner's path at their feet, projected onto the floor, is a
 *       trail of crumbs. The body walks straight to its owner when it can; otherwise to the
 *       newest crumb it can walk to, which is the way they went.</li>
 *   <li><b>Lag</b> is how far behind it is along that way: the straight walk, or the trail from the
 *       crumb it is heading for. Past {@link #CATCH_UP_LAG} it scurries to catch up; past
 *       {@link #FADE_LAG}, or with no way to its owner for {@link #STRANDED_TICKS}, it fades out
 *       and back in on its perch (a ground fade-in behind a fast owner would only lag again).</li>
 *   <li><b>A gap</b> it can't walk (a trench the owner jumped) it hops, if a crumb across is in
 *       hop range and the arc is clear.</li>
 *   <li><b>Idle</b>, it potters round the owner's feet: spots at the edge of their view (to be
 *       seen) alternating with spots beside or behind them, a scuttle-and-pause between, a dwell at
 *       each. It keeps out from under the owner's feet and scurries aside when they walk at it.</li>
 * </ul>
 */
final class GroundMind {

    enum Land { GROUND_IDLE, GROUND_FOLLOW, CATCH_UP }

    private static final int TPS = 20;
    /** Lag at which it starts catching up, and below which it stops, blocks. */
    static final double CATCH_UP_LAG = 4.0, CAUGHT_UP_LAG = 2.5;
    /** Lag at which it gives up and fades onto its perch, blocks. */
    static final double FADE_LAG = 16.0;
    /** Ticks with no way to its owner before it fades onto its perch. */
    static final int STRANDED_TICKS = 60;
    /** Owner horizontal speed above which they count as moving, blocks/s. */
    private static final double OWNER_MOVING = 0.6;
    /** Ticks the owner must stand still before it starts pottering about. */
    private static final int IDLE_AFTER = 10;
    /** Shortest catch-up, ticks: a decision, not a flicker. */
    private static final int MIN_CATCH_UP = 20;
    /** Re-plan the way to the owner every this many ticks. */
    private static final int REPLAN = 3;
    /** Straight-line distance beyond which it doesn't try walking straight to the owner, blocks. */
    private static final double DIRECT_RANGE = 8;
    /** Crumbs further than this in a straight line aren't tried as a walk, blocks. */
    private static final double CRUMB_RANGE = 10;
    private static final int MAX_CRUMB_TESTS = 14;

    private final Breadcrumbs trail;
    private final GroundBody g;
    private long rng;

    Land land = Land.GROUND_IDLE;
    int landTicks;
    /** How far behind its owner it is along the way they went, blocks. */
    double lag;
    /** Whether it has a way to its owner it can walk. */
    boolean reachable = true;
    int strandedTicks;
    /** Set when it wants the fade onto its perch. */
    boolean wantsFade;
    /** Where it is walking to this tick (for overlays). */
    Vec target;
    /**
     * Set by the percher when it wants to be seen and its owner's hand is out of hop range: it
     * comes up alongside the owner, on that side (+1 right, −1 left), close enough to hop up.
     */
    boolean wantClose;
    double closeSide = 1;
    /** Where the owner stands, on the floor (their feet projected down when they're off it). */
    Vec ownerFloor;
    private int ownerStill;
    private int planClock;
    /** -1: walking straight to the owner; otherwise the crumb it's heading for. */
    private int via = -1;
    private Vec viaPoint;
    private int hopRetry;
    int gapHops;

    // Idle pottering.
    private Vec spot;
    private Vec spotOwner;
    private int spotDwell;
    private boolean nextInView = true;
    private float spotFace = Float.NaN;

    GroundMind(Breadcrumbs trail, GroundBody g, long seed) {
        this.trail = trail;
        this.g = g;
        this.rng = seed * 0xD1B54A32D192ED03L + 0x632BE59BD9B4E019L;
        if (rng == 0) rng = 1;
    }

    /** The follow distance behind the owner: a little more for a bigger body. */
    private double followDist(float length) {
        return 1.3 + 1.2 * length;
    }

    /** One tick of deciding and walking. */
    void step(OwnerState o, LocalVoxels v, Drives drives, float length) {
        ownerFloor = ownerFloor(o, v);
        double ownerSpeed = o.horizontalSpeed();
        ownerStill = ownerSpeed > OWNER_MOVING ? 0 : ownerStill + 1;

        // The way is judged with its feet down: in the air (off a step, mid-hop) its floor is the
        // one it left, and a plan from there would read it as far behind or stranded.
        boolean down = g.grounded();
        if (down && planClock-- <= 0) {
            plan(v);
            planClock = REPLAN - 1;
        } else if (down && via < 0) {
            lag = horizontal(g.pos, ownerFloor);
        }

        if (down) {
            strandedTicks = reachable ? 0 : strandedTicks + 1;
            if (strandedTicks >= STRANDED_TICKS || lag > FADE_LAG) wantsFade = true;
        }

        // Choose the land mode.
        Land next = land;
        if (land == Land.CATCH_UP) {
            if (lag < CAUGHT_UP_LAG && landTicks >= MIN_CATCH_UP) next = ownerStill >= IDLE_AFTER ? Land.GROUND_IDLE : Land.GROUND_FOLLOW;
        } else if (lag > CATCH_UP_LAG) {
            next = Land.CATCH_UP;
        } else if (ownerStill >= IDLE_AFTER) {
            next = Land.GROUND_IDLE;
        } else if (ownerSpeed > OWNER_MOVING) {
            next = Land.GROUND_FOLLOW;
        }
        if (next != land) {
            land = next;
            landTicks = 0;
            spot = null;
        }
        landTicks++;

        // Where to go, how fast, in what rhythm.
        Vec push = ownerPush(o);
        double follow = followDist(length);
        double want;
        GroundBody.Rhythm rhythm;
        float face = Float.NaN;
        if (!reachable) {
            // Somewhere it can't walk: try a hop across, else wait at the edge.
            target = g.standing();
            want = 0;
            rhythm = GroundBody.Rhythm.TROT;
            if (--hopRetry <= 0 && strandedTicks >= 6) {
                hopRetry = 10;
                tryGapHop(v);
            }
        } else if (wantClose && via < 0) {
            // Up alongside the owner on its side, a little ahead of where they'll be: in reach of
            // the hand (and, ahead of their feet, nearer the bottom of their view).
            rhythm = GroundBody.Rhythm.SCURRY;
            Vec lead = o.velocity().horizontal().scale(0.3);
            target = ownerFloor.add(o.flatRight().scale(0.75 * closeSide)).add(o.flatForward().scale(0.3)).add(lead);
            want = Math.max(g.cruise(), ownerSpeed + 1.5 * horizontal(g.pos, target));
        } else if (land == Land.GROUND_IDLE) {
            rhythm = GroundBody.Rhythm.AMBLE;
            potter(o, v, drives);
            target = spot != null ? spot : g.standing();
            want = spot != null ? g.cruise() : 0;
            face = spotFace;
        } else {
            rhythm = land == Land.CATCH_UP ? GroundBody.Rhythm.SCURRY : GroundBody.Rhythm.TROT;
            if (via < 0) {
                // Straight to the owner, stopping short behind them.
                double d = horizontal(g.pos, ownerFloor);
                Vec dir = d > 1e-6 ? ownerFloor.sub(g.pos).horizontal().scale(1 / d) : Vec.ZERO;
                // Catching up it aims well past its usual spot, so the arrival braking doesn't
                // just hold it at the owner's pace a few blocks back.
                double stop = land == Land.CATCH_UP ? follow * 0.4 : follow;
                target = d > stop ? g.pos.add(dir.scale(d - stop)) : g.standing();
                // At the owner's level: how far down it may step on the way depends on it.
                target = new Vec(target.x(), ownerFloor.y(), target.z());
                want = land == Land.CATCH_UP ? g.rhythmBase(GroundBody.Rhythm.SCURRY) * 1.6
                        : Math.max(0, ownerSpeed + 1.5 * (d - follow));
                if (d <= follow) face = (float) dir.yawDeg();
            } else {
                target = viaPoint;
                want = land == Land.CATCH_UP ? g.rhythmBase(GroundBody.Rhythm.SCURRY) * 1.6 : Math.max(g.cruise(), ownerSpeed);
            }
        }
        g.keepClear = ownerFloor;
        g.walk(target, want, rhythm, push, face, v);
    }

    // ── The way to the owner ───────────────────────────────────────────────────────────────────

    /** Finds the way to the owner: straight, or the newest crumb it can walk to; and the lag along it. */
    private void plan(LocalVoxels v) {
        double straight = horizontal(g.pos, ownerFloor);
        if (straight < DIRECT_RANGE && walkable(v, g.pos, ownerFloor)) {
            via = -1;
            reachable = true;
            lag = straight;
            return;
        }
        double along = 0;
        Vec prev = ownerFloor;
        double best = Double.MAX_VALUE;
        int tests = 0;
        for (int i = 0; i < trail.size(); i++) {
            Vec c = trail.get(i);
            along += horizontal(prev, c) + Math.min(1, Math.abs(prev.y() - c.y()));
            prev = c;
            double d = horizontal(g.pos, c);
            best = Math.min(best, along + d);
            if (d > CRUMB_RANGE || tests >= MAX_CRUMB_TESTS) continue;
            tests++;
            if (walkable(v, g.pos, c)) {
                via = i;
                viaPoint = c;
                lag = along + d;
                // Standing on the newest crumb it can walk to, with the owner further on: the way
                // ends here (a gap, a ladder, a shut door). That's stranded, not following.
                reachable = d > 0.5 || lag < CATCH_UP_LAG;
                return;
            }
        }
        via = -1;
        reachable = false;
        lag = Math.min(best, straight * 1.5);
    }

    /**
     * Whether the body could walk in a straight line from {@code a} to the floor point {@code b}:
     * every footprint on the way is floor it can step, hop or drop to, and it arrives at
     * {@code b}'s level (not on a ledge above or below it).
     */
    boolean walkable(LocalVoxels v, Vec a, Vec b) {
        Vec d = b.sub(a).horizontal();
        double len = d.length();
        double floor = g.floorY;
        // Fine enough not to step over a pillar's corner the body can't get past.
        int steps = Math.max(1, (int) Math.ceil(len / 0.1));
        for (int i = 1; i <= steps; i++) {
            double s = i / (double) steps;
            double f = g.footprintFloorFrom(v, a.x() + d.x() * s, a.z() + d.z() * s, floor);
            if (Double.isNaN(f)) return false;
            floor = f;
        }
        return Math.abs(floor - b.y()) < 0.6;
    }

    /** A hop across something it can't walk, to the newest crumb in range that it can land on. */
    private void tryGapHop(LocalVoxels v) {
        // Only further along the way than it can walk to, and only somewhere it can't walk: a hop
        // is for getting across, not about.
        int newest = via >= 0 ? via : trail.size();
        for (int i = 0; i < newest; i++) {
            Vec c = trail.get(i);
            double d = c.distanceTo(g.pos);
            if (d > PerchBody.HOP_RANGE || d < 0.6) continue;
            if (walkable(v, g.pos, c)) continue;
            double f = g.footprintFloorFrom(v, c.x(), c.z(), c.y());
            if (Double.isNaN(f) || Math.abs(f - c.y()) > 0.05 || !g.flatAt(v, c.x(), c.z(), f)) continue;
            if (g.hopTo(new Vec(c.x(), f + g.radius + LocalVoxels.GROUND_LIFT, c.z()), v)) {
                gapHops++;
                return;
            }
        }
    }

    /** The owner's feet on the floor under them (their own height if no floor is found). */
    static Vec ownerFloor(OwnerState o, LocalVoxels v) {
        Vec f = o.feet();
        double y = v.floorBelow(f.x(), f.z(), f.y() + 0.05, 24, 0.05);
        return new Vec(f.x(), Double.isNaN(y) ? f.y() : y, f.z());
    }

    // ── Pottering ──────────────────────────────────────────────────────────────────────────────

    private void potter(OwnerState o, LocalVoxels v, Drives drives) {
        if (spot != null && spotOwner != null && horizontal(ownerFloor, spotOwner) > 1.5) spot = null;
        if (spot != null && horizontal(g.pos, spot) < 0.15) {
            if (spotDwell-- > 0) return;
            spot = null;
        }
        if (spot != null) return;
        if (spotDwell > 0) {
            spotDwell--;
            return;
        }
        double restK = drives.resting ? 2.0 : 1.0;
        Vec f = o.flatForward(), r = o.flatRight();
        for (int k = 0; k < 8; k++) {
            Vec cand;
            if (nextInView) {
                // Far enough out to be in a level view (the floor at the owner's feet only comes
                // into a 70-degree view about 2.7 blocks out), and off the very middle of it.
                double ang = Math.toRadians((unit() < 0.5 ? -1 : 1) * (6 + unit() * 18));
                double dist = 2.9 + unit() * 1.0;
                Vec dir = f.scale(Math.cos(ang)).add(r.scale(Math.sin(ang)));
                cand = ownerFloor.add(dir.scale(dist));
            } else {
                double ang = Math.toRadians(60 + unit() * 240);
                double dist = 1.1 + unit() * 1.1;
                Vec dir = f.scale(Math.cos(ang)).add(r.scale(Math.sin(ang)));
                cand = ownerFloor.add(dir.scale(dist));
            }
            double fl = g.footprintFloorFrom(v, cand.x(), cand.z(), g.floorY);
            // Somewhere it can get back to its owner from: no further down than a hop.
            if (Double.isNaN(fl) || Math.abs(fl - ownerFloor.y()) > GroundBody.STEP_HOP) continue;
            cand = new Vec(cand.x(), fl, cand.z());
            if (!walkable(v, g.pos, cand)) continue;
            spot = cand;
            spotOwner = ownerFloor;
            nextInView = !nextInView;
            spotDwell = (int) Math.round((1.5 + unit() * 3.5) * restK * TPS);
            // Settle facing the owner, more or less.
            Vec toOwner = ownerFloor.sub(cand).horizontal();
            spotFace = toOwner.lengthSqr() > 1e-6 ? (float) (toOwner.yawDeg() + (unit() - 0.5) * 80) : Float.NaN;
            return;
        }
        spotDwell = 10; // nowhere to go just now: try again shortly
    }

    /**
     * The push away from the owner's feet: out of the way when they walk at it, and never
     * underfoot. Blocks/s, horizontal.
     */
    private Vec ownerPush(OwnerState o) {
        Vec feet = ownerFloor;
        Vec ov0 = o.velocity().horizontal();
        Vec push = Vec.ZERO;
        // Where their feet are now, and where they'll be in a moment: walked at, it starts out of
        // their way while there is time to turn (it pivots 90 degrees in about 0.7 s).
        Vec[] at = {feet, feet.add(ov0.scale(0.35)), feet.add(ov0.scale(0.8))};
        double[] reaches = {0.9, 1.0, 1.3};
        for (int k = 0; k < at.length; k++) {
            Vec p = at[k];
            Vec away = g.pos.sub(p).horizontal();
            double d = away.length();
            double reach = reaches[k];
            if (d >= reach) continue;
            if (d < 1e-4) away = o.flatRight();
            else away = away.scale(1 / d);
            // Walked at: step aside, off their line, rather than run ahead of them.
            Vec ov = o.velocity().horizontal();
            if (ov.length() > OWNER_MOVING) {
                Vec side = new Vec(-ov.z(), 0, ov.x()).normalize();
                if (side.dot(away) < 0) side = side.scale(-1);
                away = away.add(side).normalize();
            }
            push = push.add(away.scale(4.0 * (reach - d) / reach));
        }
        return push;
    }

    private static double horizontal(Vec a, Vec b) {
        double dx = a.x() - b.x(), dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private double unit() {
        long x = rng;
        x ^= x << 13;
        x ^= x >>> 7;
        x ^= x << 17;
        rng = x;
        return (x >>> 11) * 0x1.0p-53;
    }
}
