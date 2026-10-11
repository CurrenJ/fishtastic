package grill24.fishsim.companion;

/**
 * What the companion wants, and so where it is going (docs/fish-companions-sim-plan.md §3.3).
 *
 * <p>Drives are scalar urges that rise and fall with what the fish perceives. Each tick the mind
 * scores the goals the drives propose and keeps its current goal unless a challenger beats it by
 * a margin, after a minimum commitment — only an emergency cuts that short. Nothing here is a
 * path: each goal turns the fish's situation into a desired velocity, and the {@link Body}
 * decides how a fish would actually make it.
 *
 * <p>Spike scope: three drives (proximity, wander, attention) and four goals.
 */
final class Mind {

    enum Goal {
        /** Owner standing still: drift between nearby spots, lingering at each. */
        LOITER,
        /** Owner travelling: ride alongside, changing side or position now and then. */
        ESCORT,
        /** Too far or cut off: swim hard back to the owner, along their trail if a wall is in the way. */
        CATCH_UP,
        /** Unseen for a while: swim into the owner's view and across it. */
        SHOW_OFF
    }

    private static final int TPS = 20;
    /** A challenger must beat the current goal's utility by this much to take over. */
    private static final float SWITCH_MARGIN = 0.15f;
    /** Catch-up urgency above which commitment is ignored. */
    private static final float EMERGENCY = 0.95f;
    private static final float BASE_UTILITY = 0.35f;
    /** Owner horizontal speed (blocks/s) that counts as travelling, and the speed that ends it. */
    private static final double MOVING_ENTER = 1.0, MOVING_EXIT = 0.4;
    private static final int MOVING_EXIT_TICKS = 15;
    /** Ticks in view, in a row, that count as "the owner saw me". */
    private static final int SEEN_STREAK = 15;
    private static final int SHOW_COOLDOWN_TICKS = 100;
    private static final int SHOW_MAX_TICKS = 200;
    /** Extra clearance a route keeps from blocks, beyond the body's radius. */
    private static final double ROUTE_MARGIN = 0.15;

    private final CompanionTunables t;
    private final Body body;
    private long rng;

    Goal goal = Goal.LOITER;
    int goalTicks;
    int goalSwitches;

    // ── Perception ──
    double ownerDist;
    boolean seen;
    boolean losToOwner = true;
    boolean ownerMoving;
    private int slowTicks;
    int stillTicks;
    double bandOuterNow;

    // ── Drives and utilities (exposed for the overlay) ──
    float proximity, attention;
    float uBase, uCatch, uShow;
    int unseenTicks;
    private int seenStreak;
    private int attentionThreshold;
    private int showCooldown;

    /** Where the current goal is heading, for the overlay. */
    Vec target;
    double wanderScale = 1;

    // ── LOITER ──
    private Vec spot;
    private boolean dwelling;
    private int dwellTicks;

    // ── ESCORT ──
    private int side = 1;
    private double lateralSigned = Double.NaN;
    private float driftLat, driftAlong, driftHeight;
    private final float alongBase;
    private int changeTicks;
    private int surgeTicks;
    private double surgeTarget, surge;
    private float escortYaw = Float.NaN;

    // ── SHOW_OFF ──
    private int showPhase;
    private int showSide;
    private float laggedYaw;
    private int showTicks;
    private boolean showDone;
    int showOffs;

    Mind(CompanionTunables t, Body body, long seed) {
        this.t = t;
        this.body = body;
        this.rng = seed * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L;
        if (rng == 0) rng = 1;
        this.alongBase = (float) (-0.2 + 0.7 * unit());
        this.attentionThreshold = drawAttentionThreshold();
        this.changeTicks = drawTicks(t.escortChangeMinSeconds, t.escortChangeMaxSeconds);
    }

    /** One tick of thinking: returns the velocity the fish wants. */
    Vec decide(OwnerState o, LocalVoxels v, Breadcrumbs crumbs) {
        perceive(o, v);
        updateDrives();
        choose();
        goalTicks++;
        return switch (goal) {
            case LOITER -> loiter(o, v);
            case ESCORT -> escort(o, v, crumbs);
            case CATCH_UP -> catchUp(o, v, crumbs);
            case SHOW_OFF -> showOff(o, v);
        };
    }

    /** Forget goal state after the body was placed somewhere new. */
    void reset(OwnerState o) {
        goal = ownerMoving ? Goal.ESCORT : Goal.LOITER;
        goalTicks = 0;
        spot = null;
        dwelling = false;
        lateralSigned = Double.NaN;
        escortYaw = Float.NaN;
        side = sideOf(o);
    }

    // ── Perception and drives ──────────────────────────────────────────────────────────────────

    private void perceive(OwnerState o, LocalVoxels v) {
        Vec chest = o.chest();
        ownerDist = body.pos.distanceTo(chest);
        losToOwner = v.segmentClear(body.pos, chest, 0.1);
        seen = Visibility.visible(o, body.pos, v);
        double hs = o.horizontalSpeed();
        if (hs > MOVING_ENTER) {
            ownerMoving = true;
            slowTicks = 0;
        } else {
            slowTicks = hs < MOVING_EXIT ? slowTicks + 1 : 0;
            if (slowTicks > MOVING_EXIT_TICKS) ownerMoving = false;
        }
        stillTicks = ownerMoving ? 0 : stillTicks + 1;
    }

    private void updateDrives() {
        double idleWiden = smoothstep(0, 10 * TPS, stillTicks);
        bandOuterNow = (ownerMoving ? t.bandOuter : t.bandOuter + (t.bandOuterIdle - t.bandOuter) * idleWiden)
                * body.g.bandScale();
        proximity = (float) smoothstep(bandOuterNow, t.farGap, ownerDist);
        if (!losToOwner && ownerDist > 1.2) proximity = Math.max(proximity, 0.75f);

        if (seen) {
            seenStreak++;
            if (seenStreak >= SEEN_STREAK && unseenTicks > 0) {
                unseenTicks = 0;
                attentionThreshold = drawAttentionThreshold();
            }
        } else {
            seenStreak = 0;
            unseenTicks++;
        }
        attention = clamp01((unseenTicks - attentionThreshold) / (t.attentionRampSeconds * TPS));
        if (showCooldown > 0) showCooldown--;
    }

    private void choose() {
        uBase = BASE_UTILITY;
        uCatch = proximity * 1.3f;
        boolean showFinished = goal == Goal.SHOW_OFF && showDone;
        uShow = ownerDist < t.farGap && showCooldown == 0 && !showFinished ? attention * 0.9f : 0f;

        Goal base = ownerMoving ? Goal.ESCORT : Goal.LOITER;
        Goal best = base;
        float bestU = uBase;
        if (uCatch > bestU) {
            best = Goal.CATCH_UP;
            bestU = uCatch;
        }
        if (uShow > bestU) {
            best = Goal.SHOW_OFF;
            bestU = uShow;
        }
        if (best == goal) return;

        boolean baseSwap = isBase(goal) && isBase(best);
        boolean emergency = best == Goal.CATCH_UP && uCatch > EMERGENCY;
        boolean done = goal == Goal.SHOW_OFF && showDone;
        boolean committed = goalTicks < minTicks(goal);
        if (baseSwap || emergency || done || (!committed && bestU > utility(goal) + SWITCH_MARGIN)) {
            enter(best);
        }
    }

    private float utility(Goal g) {
        return switch (g) {
            case LOITER, ESCORT -> uBase;
            case CATCH_UP -> uCatch;
            case SHOW_OFF -> showDone ? 0f : 0.8f;
        };
    }

    private static boolean isBase(Goal g) {
        return g == Goal.LOITER || g == Goal.ESCORT;
    }

    private static int minTicks(Goal g) {
        return switch (g) {
            case LOITER, ESCORT -> 20;
            case CATCH_UP -> 30;
            case SHOW_OFF -> 40;
        };
    }

    private void enter(Goal g) {
        if (goal == Goal.SHOW_OFF) {
            showCooldown = SHOW_COOLDOWN_TICKS;
        }
        goal = g;
        goalTicks = 0;
        goalSwitches++;
        switch (g) {
            case LOITER -> {
                spot = null;
                dwelling = false;
            }
            case SHOW_OFF -> {
                showPhase = 0;
                showTicks = 0;
                showDone = false;
                showSide = 0; // chosen on the first tick, from where the fish is
                showOffs++;
            }
            default -> { }
        }
    }

    // ── Goals ──────────────────────────────────────────────────────────────────────────────────

    private Vec loiter(OwnerState o, LocalVoxels v) {
        Vec chest = o.chest();
        if (spot == null || spot.distanceTo(chest) > bandOuterNow + 0.8 || !v.sphereClear(spot, body.radius + 0.1)) {
            spot = pickSpot(o, v);
            dwelling = false;
        }
        target = spot;
        Vec to = spot.sub(body.pos);
        double d = to.length();
        if (!dwelling) {
            wanderScale = 0.8;
            if (d < 0.35) {
                dwelling = true;
                dwellTicks = drawTicks(t.dwellMinSeconds, t.dwellMaxSeconds);
            }
            double speed = Math.min(body.g.cruiseSpeed() * body.burstDrive, 0.1 + d * 1.2);
            return aroundOwner(o, v, spot).sub(body.pos).normalize().scale(speed);
        }
        // Lingering: a slow mill held loosely to the spot.
        wanderScale = 1.2;
        if (--dwellTicks <= 0) spot = null;
        Vec heading = Vec.fromYawPitch(body.yaw, 0);
        return heading.scale(0.25 * body.g.cruiseSpeed() * body.burstDrive).add(to.scale(0.6));
    }

    /** A nearby open spot inside the comfort band, a short hop from where the fish is now. */
    private Vec pickSpot(OwnerState o, LocalVoxels v) {
        Vec chest = o.chest();
        Gait g = body.g;
        double inner = t.bandInner * g.bandScale() + 0.2;
        double outer = Math.max(inner + 0.3, bandOuterNow - 0.3);
        for (int attempt = 0; attempt < 24; attempt++) {
            double yaw = o.yawDeg() + (unit() - 0.5) * 300;
            double r = inner + (outer - inner) * unit();
            double h = g.rideHeight() + (-0.6 + 1.2 * unit()) * g.heightSpread();
            // A rider that wants height comes down under a ceiling: later attempts try lower.
            if (g.rideHeight() > 0 && attempt >= 8) h = Math.min(h, g.rideHeight() * (1 - (attempt - 8) / 8.0));
            Vec p = chest.add(Vec.fromYawPitch(yaw, 0).scale(r)).add(0, h, 0);
            double hop = p.distanceTo(body.pos);
            boolean relaxed = attempt >= 16; // late attempts drop the hop-length preference
            if (!relaxed && (hop < g.hopMin() || hop > g.hopMax())) continue;
            if (!v.sphereClear(p, body.radius + 0.25)) continue;
            if (!v.segmentClear(body.pos, p, body.radius)) continue;
            if (p.distanceTo(o.eye()) < 1.2 && Visibility.angleFromCenterDeg(o, p) < 12) continue;
            if (insideOwner(o, p, body.radius + 0.25)) continue;
            return p;
        }
        return body.pos;
    }

    private Vec escort(OwnerState o, LocalVoxels v, Breadcrumbs crumbs) {
        Vec chest = o.chest();
        double hs = o.horizontalSpeed();
        if (Float.isNaN(escortYaw)) escortYaw = hs > 0.5 ? (float) o.velocity().yawDeg() : o.yawDeg();
        if (hs > 0.5) {
            float diff = Body.wrap((float) o.velocity().yawDeg() - escortYaw);
            escortYaw = Body.wrap(escortYaw + Body.clamp(diff, -6f, 6f));
        }
        Vec f = Vec.fromYawPitch(escortYaw, 0);
        Vec r = f.cross(Vec.UP);
        if (Double.isNaN(lateralSigned)) {
            side = sideOf(o);
            lateralSigned = body.pos.sub(chest).dot(r);
        }

        // Now and then, decide to ride somewhere else: the other side, ahead, or behind.
        if (--changeTicks <= 0) {
            double u = unit();
            if (u < 0.5) {
                side = -side;
            } else if (u < 0.8) {
                surgeTarget = 1.2;
                surgeTicks = drawTicks(3, 5);
            } else {
                surgeTarget = -0.8;
                surgeTicks = drawTicks(2, 4);
            }
            changeTicks = drawTicks(t.escortChangeMinSeconds, t.escortChangeMaxSeconds);
        }
        if (surgeTicks > 0 && --surgeTicks == 0) surgeTarget = 0;
        surge = approach(surge, surgeTarget, 0.6 * Body.DT);
        driftLat = ou(driftLat);
        driftAlong = ou(driftAlong);
        driftHeight = ou(driftHeight);

        double lateral = t.escortLateral * body.g.lateralScale() + driftLat * 0.3;
        lateralSigned = approach(lateralSigned, side * lateral, 0.9 * Body.DT);
        // Crossing over goes in front of the owner, never through them.
        double centre = 1 - Math.min(1, Math.pow(lateralSigned / (t.escortLateral * body.g.lateralScale()), 2));
        double along = alongBase + driftAlong * 0.4 + surge + 1.4 * centre;
        double height = 0.15 + driftHeight * 0.25;
        Vec station = chest.add(r.scale(lateralSigned)).add(f.scale(along)).add(0, height, 0);
        if (body.g.rideHeight() > 0) station = ridingHigh(v, station, body.g.rideHeight());
        if (!v.sphereClear(station, body.radius + 0.2) || !v.segmentClear(chest, station, 0.1)) {
            // No room beside the owner (a corridor, a doorway): fall in behind, on their trail —
            // unless the fish is already in front and getting behind means squeezing past them,
            // in which case it leads the way instead.
            Vec behind = crumbs.trailPoint(chest, 1.4);
            Vec ahead = chest.add(f.scale(1.5));
            boolean aheadOpen = v.sphereClear(ahead, body.radius + 0.1) && v.segmentClear(chest, ahead, 0.1);
            if (behind != null && (!cutsThroughOwner(o, body.pos, behind) || !aheadOpen)) {
                station = behind;
            } else if (aheadOpen) {
                station = ahead;
            }
        }
        target = station;
        wanderScale = 0.5;
        Vec correction = aroundOwner(o, v, station).sub(body.pos).scale(1.2).clampLength(body.g.catchUpSpeed());
        Vec surgeSwim = f.scale((body.burstDrive - 1) * 0.4 * body.g.cruiseSpeed());
        return o.velocity().horizontal().add(correction).add(surgeSwim);
    }

    private Vec catchUp(OwnerState o, LocalVoxels v, Breadcrumbs crumbs) {
        Vec chest = o.chest();
        Vec beside = body.pos.sub(chest).horizontal().normalize();
        if (beside.lengthSqr() < 1e-9) beside = o.flatRight();
        Vec dest = chest.add(beside.scale(1.0));
        // Routes keep a margin beyond the body's radius: a fish turns in an arc, not on the line.
        if (!v.segmentClear(body.pos, dest, body.radius + ROUTE_MARGIN)) {
            Vec crumb = crumbs.newestReachable(body.pos, v, body.radius + ROUTE_MARGIN);
            if (crumb == null) crumb = crumbs.newestReachable(body.pos, v, body.radius);
            if (crumb != null) dest = crumb;
        }
        target = dest;
        wanderScale = 0.25;
        double d = dest.distanceTo(body.pos);
        double ownerSpeed = o.velocity().length();
        double speed = Math.max(body.g.cruiseSpeed() * 1.5, Math.min(body.g.catchUpSpeed(), ownerSpeed + 1.0 + 0.8 * d));
        speed = Math.min(speed, ownerSpeed + d * 1.5);
        return aroundOwner(o, v, dest).sub(body.pos).normalize().scale(speed);
    }

    private Vec showOff(OwnerState o, LocalVoxels v) {
        if (showSide == 0) {
            showSide = sideOf(o);
            laggedYaw = o.yawDeg();
        }
        // The aim lags the camera: the fish swims to where the owner was looking a moment ago.
        laggedYaw = Body.wrap(laggedYaw + Body.clamp(Body.wrap(o.yawDeg() - laggedYaw), -3.5f, 3.5f));
        float pitch = Body.clamp(o.pitchDeg(), -25f, 35f);
        Vec cam = o.camera();
        Vec enterPoint = cam.add(Vec.fromYawPitch(laggedYaw + showSide * 28, pitch + 6).scale(1.8));
        Vec exitPoint = cam.add(Vec.fromYawPitch(laggedYaw - showSide * 24, pitch + 4).scale(1.6));
        Vec dest = showPhase == 0 ? enterPoint : exitPoint;
        target = dest;
        wanderScale = 0.35;
        showTicks++;
        // Done once the owner has had a proper look; a pass they missed is simply swum again,
        // from the other side, until the show runs out of time.
        if (seenStreak >= SEEN_STREAK || showTicks > SHOW_MAX_TICKS || !v.sphereClear(dest, body.radius + 0.1)) {
            showDone = true;
        }
        double d = dest.distanceTo(body.pos);
        double speed;
        if (showPhase == 0) {
            if (d < 0.45) showPhase = 1;
            speed = Math.min(body.g.cruiseSpeed() * 1.6, 0.4 + d * 1.5);
        } else {
            if (d < 0.5) {
                showPhase = 0;
                showSide = -showSide;
            }
            speed = body.g.cruiseSpeed() * 0.9;
        }
        // Swim relative to the owner, so a show still reaches the front of someone walking.
        Vec relative = aroundOwner(o, v, dest).sub(body.pos).normalize().scale(speed);
        return o.velocity().horizontal().add(relative);
    }

    /**
     * {@code station} lifted by {@code height}, or as much of it as the blocks allow: a glider
     * rides over the owner's head in the open and comes down under a ceiling.
     */
    private Vec ridingHigh(LocalVoxels v, Vec station, double height) {
        for (int k = 4; k >= 1; k--) {
            Vec lifted = station.add(0, height * k / 4, 0);
            if (v.sphereClear(lifted, body.radius + 0.2) && v.segmentClear(station, lifted, body.radius)) return lifted;
        }
        return station;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────────────────

    /**
     * The push away from the owner's body (a capsule from feet to eye), blocks/s. Like wall
     * avoidance it grows with the fish's own speed, and it also carries the speed at which the
     * owner is closing in — someone walking into their fish pushes it ahead of them.
     */
    Vec ownerAvoidance(OwnerState o) {
        Vec q = nearestOnBody(o, body.pos);
        Vec away = body.pos.sub(q);
        double d = away.length();
        double limit = OwnerState.BODY_RADIUS + body.radius + t.ownerMargin;
        if (d >= limit) return Vec.ZERO;
        Vec dir = d < 1e-6 ? o.flatRight() : away.scale(1 / d);
        double w = 1 - d / limit;
        double closing = Math.max(0, o.velocity().dot(dir));
        double gain = Math.max(t.ownerAvoidSpeed, body.speed * 1.2);
        return dir.scale(w * gain + closing * Math.min(1, w * 2));
    }

    /** Clearance a swim keeps from the owner's body axis. */
    private double ownerClearance() {
        return OwnerState.BODY_RADIUS + body.radius + 0.25;
    }

    /** Whether the straight swim from {@code a} to {@code b} would pass through the owner. */
    private boolean cutsThroughOwner(OwnerState o, Vec a, Vec b) {
        return closestApproach(o, a, b) < ownerClearance();
    }

    private static double closestApproach(OwnerState o, Vec a, Vec b) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i <= 12; i++) {
            Vec p = a.lerp(b, i / 12.0);
            best = Math.min(best, p.distanceTo(nearestOnBody(o, p)));
        }
        return best;
    }

    /**
     * Where to swim to reach {@code dest}: {@code dest} itself, or — when the straight line cuts
     * through the owner — a waypoint around their side (the side the fish is already on).
     */
    private Vec aroundOwner(OwnerState o, LocalVoxels v, Vec dest) {
        if (!cutsThroughOwner(o, body.pos, dest)) return dest;
        Vec seg = dest.sub(body.pos).horizontal().normalize();
        if (seg.lengthSqr() < 1e-9) return dest;
        Vec perp = new Vec(-seg.z(), 0, seg.x());
        Vec chest = o.chest();
        double sign = body.pos.sub(chest).dot(perp) >= 0 ? 1 : -1;
        Vec axis = nearestOnBody(o, body.pos);
        double out = ownerClearance() + 0.35;
        for (int k = 0; k < 2; k++, sign = -sign) {
            Vec wp = new Vec(axis.x(), body.pos.y(), axis.z()).add(perp.scale(sign * out));
            if (v.sphereClear(wp, body.radius + 0.05) && v.segmentClear(body.pos, wp, body.radius)) return wp;
        }
        return dest;
    }

    static boolean insideOwner(OwnerState o, Vec p, double margin) {
        return p.distanceTo(nearestOnBody(o, p)) < OwnerState.BODY_RADIUS + margin;
    }

    private static Vec nearestOnBody(OwnerState o, Vec p) {
        Vec a = o.feet().add(0, OwnerState.BODY_RADIUS, 0);
        Vec b = o.eye().add(0, 0.1, 0);
        Vec ab = b.sub(a);
        double s = Math.max(0, Math.min(1, p.sub(a).dot(ab) / ab.lengthSqr()));
        return a.add(ab.scale(s));
    }

    private int sideOf(OwnerState o) {
        return body.pos.sub(o.chest()).dot(o.flatRight()) >= 0 ? 1 : -1;
    }

    private int drawAttentionThreshold() {
        return drawTicks(t.attentionMinSeconds, t.attentionMaxSeconds);
    }

    private int drawTicks(double minSeconds, double maxSeconds) {
        return (int) Math.round((minSeconds + (maxSeconds - minSeconds) * unit()) * TPS);
    }

    /** A slow, bounded drift in [-1, 1]: what keeps a riding position from being a fixed offset. */
    private float ou(float x) {
        float next = x - x * 0.4f * (float) Body.DT + 0.5f * (float) Math.sqrt(Body.DT) * 1.7320508f * signed();
        return Body.clamp(next, -1f, 1f);
    }

    private double unit() {
        return (signed() + 1f) * 0.5f;
    }

    private float signed() {
        long x = rng;
        x ^= x << 13;
        x ^= x >>> 7;
        x ^= x << 17;
        rng = x;
        return ((x >>> 40) * (1f / 8388608f)) - 1f;
    }

    private static double approach(double value, double target, double step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }

    static double smoothstep(double edge0, double edge1, double x) {
        double u = Math.max(0, Math.min(1, (x - edge0) / (edge1 - edge0)));
        return u * u * (3 - 2 * u);
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }
}
