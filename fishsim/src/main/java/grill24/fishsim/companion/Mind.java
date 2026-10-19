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
 * <p>Its own drives are proximity, wander and attention (the spike's); curiosity, wariness and rest
 * come from the shared {@link Drives}, which every class updates (plan §10).
 */
final class Mind {

    enum Goal {
        /** Owner standing still: drift between nearby spots, lingering at each. */
        LOITER,
        /** Owner idle a long while and the fish rested: a slow, close idle with long lingers, often at the edge of view. */
        SETTLE,
        /** Owner travelling: ride alongside, changing side or position now and then. */
        ESCORT,
        /** Too far or cut off: swim hard back to the owner, along their trail if a wall is in the way. */
        CATCH_UP,
        /** Unseen for a while: swim into the owner's view and across it. */
        SHOW_OFF,
        /** Something interesting: swim over, hold off at a species-sized distance, nose at it, lose interest. */
        INSPECT,
        /** Something alarming: dart into cover on the owner's far side and keep an eye on it from there. */
        HIDE,
        /** A hostile, for an aggressive fish: take up a spot between it and the owner, puffed up, facing it. */
        GUARD,
        /** The owner standing still, for a docile fish: over to the side of their head for a nuzzle. */
        NUZZLE
    }

    private static final int TPS = 20;
    /** A challenger must beat the current goal's utility by this much to take over. */
    private static final float SWITCH_MARGIN = 0.15f;
    /** Catch-up urgency above which commitment is ignored. */
    private static final float EMERGENCY = 0.95f;
    /** How far ahead of its sprinting owner a sprinter rides, blocks. */
    private static final double RACE_AHEAD = 2.6;
    /** Wariness above which hiding cuts any commitment short. */
    private static final float ALARM = 0.6f;
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
    private final Motor body;
    private final Drives drives;
    private final Temperament temperament;
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
    float uBase, uCatch, uShow, uInspect, uHide, uNuzzle;
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

    // ── HIDE, GUARD ──
    int hides;
    private int coverTicks, peekTicks, peekSide = 1, peekWait;
    /** Ticks left of a bite at the air (an aggressive fish whose owner was just hurt). */
    private int biteTicks;

    // ── NUZZLE ──
    private int nuzzleCooldown, nuzzleHold, nuzzleSide, nuzzlesThisStill;
    private boolean nuzzleDone;
    int nuzzles;

    // ── Race ahead ──
    private boolean racing;

    // ── Ambush ──
    private int ambushPhase, ambushHold;

    // ── INSPECT ──
    /** What is being inspected; while it's still there and still the most interesting thing, the goal holds. */
    private Drives.Interest inspecting;
    /** The pose the current goal asks for. */
    PoseHint pose = PoseHint.NONE;
    int inspections;

    // ── SHOW_OFF ──
    private int showPhase;
    private int showSide;
    private float laggedYaw;
    private int showTicks;
    private boolean showDone;
    int showOffs;

    Mind(CompanionTunables t, Motor body, long seed, Drives drives, Temperament temperament) {
        this.t = t;
        this.body = body;
        this.drives = drives;
        this.temperament = temperament;
        this.rng = seed * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L;
        if (rng == 0) rng = 1;
        this.alongBase = (float) (-0.2 + 0.7 * unit());
        this.attentionThreshold = drawAttentionThreshold();
        this.changeTicks = drawTicks(t.escortChangeMinSeconds * temperament.decisions(),
                t.escortChangeMaxSeconds * temperament.decisions());
    }

    /** One tick of thinking: returns the velocity the fish wants. */
    Vec decide(OwnerState o, LocalVoxels v, Breadcrumbs crumbs) {
        o0 = o;
        perceive(o, v);
        updateDrives();
        choose();
        goalTicks++;
        // A drifter calms while it idles — but not while it, or where it is going, is in the middle of
        // the owner's view: from there it leaves at its travelling self (§13.7).
        body.idling = (goal == Goal.LOITER || goal == Goal.SETTLE) && centreTicks == 0;
        return switch (goal) {
            case LOITER -> loiter(o, v);
            case SETTLE -> settle(o, v);
            case ESCORT -> escort(o, v, crumbs);
            case CATCH_UP -> catchUp(o, v, crumbs);
            case SHOW_OFF -> showOff(o, v);
            case INSPECT -> inspect(o, v);
            case HIDE, GUARD -> hide(o, v);
            case NUZZLE -> nuzzle(o, v);
        };
    }

    /** One tick of thinking, with the temperament's heading noise applied to whatever the goal asked. */
    Vec think(OwnerState o, LocalVoxels v, Breadcrumbs crumbs) {
        Vec desired = decide(o, v, crumbs);
        // A jittery temperament steadies itself between walls (a wandering heading in a tunnel is
        // a fish bumping the sides); a calm one is calm everywhere.
        if (temperament.jitter() != 1) {
            boolean tight = !v.sphereClear(body.pos, body.radius + 0.6);
            wanderScale *= tight ? Math.min(1, temperament.jitter()) : temperament.jitter();
        }
        if (temperament.tremble() && pose == PoseHint.NONE && isBase(goal) && dwelling) pose = PoseHint.TREMBLE;
        if (biteTicks > 0) {
            biteTicks--;
            pose = PoseHint.BITE;
        }
        return desired;
    }

    record GroundIntent(Vec target, float face, boolean hurry) {}

    /** The same goal selection and habituation, expressed as a floor destination for a walker. */
    GroundIntent thinkGround(OwnerState o, LocalVoxels v, GroundBody ground) {
        o0 = o;
        perceive(o, v, ground.pos);
        updateDrives();
        // Ground idle spots are deliberately farther out than the swimmer's comfort band.
        // Their leash is horizontal trail lag, so height below the chest must not win over
        // curiosity or an ankle nuzzle as a spurious swimming catch-up urgency.
        proximity = (float) smoothstep(GroundMind.CATCH_UP_LAG, GroundMind.FADE_LAG,
                ground.pos.sub(o.feet()).horizontalLength());
        if (goal == Goal.SHOW_OFF && (seenStreak >= SEEN_STREAK || goalTicks >= SHOW_MAX_TICKS)) showDone = true;
        choose();
        goalTicks++;
        pose = PoseHint.NONE;
        Vec feet = GroundMind.ownerFloor(o, v);
        Vec destination = null;
        float face = Float.NaN;
        boolean hurry = false;
        switch (goal) {
            case INSPECT -> {
                Drives.Interest it = inspecting;
                // Held things retain their hand-hop presentation; world things draw a detour.
                if (it == null || it.isHeld()) break;
                double hold = (0.35 + 1.2 * bodyLength() + it.radius) * temperament.inspectSpacing();
                Vec away = ground.pos.sub(it.pos).horizontal().normalize();
                if (away.lengthSqr() < 1e-6) away = o.flatRight();
                destination = it.pos.add(away.scale(hold));
                drives.engage(it, ground.pos.sub(it.pos).horizontalLength(), hold);
                face = (float) it.pos.sub(ground.pos).horizontal().yawDeg();
                pose = poseFor(it);
            }
            case HIDE, GUARD -> {
                if (drives.threatPos == null || drives.threat != Drives.Threat.HOSTILE) break;
                Vec away = feet.sub(drives.threatPos).horizontal().normalize();
                if (away.lengthSqr() < 1e-6) away = o.flatForward().scale(-1);
                boolean guard = goal == Goal.GUARD;
                destination = feet.add(away.scale((guard ? -1 : 1) * (0.8 + ground.radius)));
                face = (float) drives.threatPos.sub(ground.pos).horizontal().yawDeg();
                pose = guard ? (drives.sinceHurt < BITE_TICKS ? PoseHint.BITE : PoseHint.PUFF) : PoseHint.HIDE;
                hurry = true;
            }
            case NUZZLE -> {
                if (goalTicks == 1) nuzzleSide = ground.pos.sub(feet).dot(o.flatRight()) < 0 ? -1 : 1;
                // Brush along one ankle, then past it; retain the existing dwell and cooldown.
                double along = goalTicks < 50 ? -0.55 : 0.55;
                destination = feet.add(o.flatRight().scale(nuzzleSide * (0.5 + ground.radius)))
                        .add(o.flatForward().scale(along));
                face = (float) feet.sub(ground.pos).horizontal().yawDeg();
                pose = PoseHint.NUZZLE;
                if (goalTicks > 120) nuzzleDone = true;
            }
            default -> { }
        }
        if (destination == null) return null;
        target = destination;
        return new GroundIntent(destination, face, hurry);
    }

    /** Forget goal state after the body was placed somewhere new. */
    void reset(OwnerState o) {
        goal = ownerMoving ? Goal.ESCORT : drives.resting ? Goal.SETTLE : Goal.LOITER;
        goalTicks = 0;
        spot = null;
        dwelling = false;
        lateralSigned = Double.NaN;
        escortYaw = Float.NaN;
        side = sideOf(o);
    }

    // ── Perception and drives ──────────────────────────────────────────────────────────────────

    private void perceive(OwnerState o, LocalVoxels v) {
        perceive(o, v, body.pos);
    }

    private void perceive(OwnerState o, LocalVoxels v, Vec position) {
        Vec chest = o.chest();
        ownerDist = position.distanceTo(chest);
        losToOwner = v.segmentClear(position, chest, 0.1);
        seen = Visibility.visible(o, position, v);
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
                * body.g.bandScale() * temperament.band();
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
        if (nuzzleCooldown > 0) nuzzleCooldown--;
        if (ownerMoving) nuzzlesThisStill = 0;
        if (temperament.guard() && drives.sinceHurt == 0) biteTicks = BITE_TICKS;
    }

    private static final int BITE_TICKS = 14;

    private void choose() {
        uBase = BASE_UTILITY;
        uCatch = proximity * 1.3f;
        boolean showFinished = goal == Goal.SHOW_OFF && showDone;
        uShow = ownerDist < t.farGap && showCooldown == 0 && !showFinished ? attention * 0.9f : 0f;
        uInspect = drives.target != null && ownerDist < t.farGap ? drives.curiosity : 0f;
        // Something alarming beats being seen and being curious; being left behind still wins.
        uHide = drives.threatPos != null ? drives.wariness * 1.2f : 0f;
        if (uHide > 0) uInspect = 0;
        // Docile: while the owner stands still, now and then over for a nuzzle (dwell, then a
        // cooldown that lengthens with each nuzzle in the same still spell — spec R7.2).
        boolean nuzzleFinished = goal == Goal.NUZZLE && nuzzleDone;
        uNuzzle = temperament.nuzzle() && !ownerMoving && stillTicks > NUZZLE_DWELL_TICKS && nuzzleCooldown == 0
                && drives.wariness == 0 && ownerDist < t.farGap && !nuzzleFinished ? 0.6f : 0f;

        Goal base = ownerMoving ? Goal.ESCORT : drives.resting ? Goal.SETTLE : Goal.LOITER;
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
        if (uInspect > bestU) {
            best = Goal.INSPECT;
            bestU = uInspect;
        }
        if (uNuzzle > bestU) {
            best = Goal.NUZZLE;
            bestU = uNuzzle;
        }
        if (uHide > bestU) {
            // An aggressive fish stands its ground against a hostile instead.
            best = temperament.guard() && drives.threat == Drives.Threat.HOSTILE ? Goal.GUARD : Goal.HIDE;
            bestU = uHide;
        }
        if (best == goal) return;

        boolean baseSwap = isBase(goal) && isBase(best);
        boolean emergency = (best == Goal.CATCH_UP && uCatch > EMERGENCY)
                || ((best == Goal.HIDE || best == Goal.GUARD) && drives.wariness > ALARM);
        // An inspection is over when the thing is gone or has stopped being the most interesting.
        boolean done = (goal == Goal.SHOW_OFF && showDone)
                || (goal == Goal.INSPECT && (inspecting == null || drives.target != inspecting))
                || nuzzleFinished;
        boolean committed = goalTicks < minTicks(goal);
        if (baseSwap || emergency || done || (!committed && bestU > utility(goal) + SWITCH_MARGIN)) {
            enter(best);
        }
    }

    private float utility(Goal g) {
        return switch (g) {
            case LOITER, SETTLE, ESCORT -> uBase;
            case CATCH_UP -> uCatch;
            case SHOW_OFF -> showDone ? 0f : 0.8f;
            case INSPECT -> uInspect;
            case HIDE, GUARD -> uHide;
            case NUZZLE -> nuzzleDone ? 0f : 0.6f;
        };
    }

    private static boolean isBase(Goal g) {
        return g == Goal.LOITER || g == Goal.SETTLE || g == Goal.ESCORT;
    }

    /** The minimum time a goal is kept, ticks: the temperament's commitment, never under a second. */
    private int minTicks(Goal g) {
        int base = switch (g) {
            case LOITER, SETTLE, ESCORT -> 20;
            case CATCH_UP -> 30;
            case SHOW_OFF, INSPECT, HIDE, GUARD, NUZZLE -> 40;
        };
        return temperament.commitment() == 1 ? base : Math.max(20, Math.round(base * temperament.commitment()));
    }

    /** The owner as of this tick, for goals that need it the moment they start. */
    private OwnerState o0;

    private void enter(Goal g) {
        if (goal == Goal.SHOW_OFF) {
            showCooldown = SHOW_COOLDOWN_TICKS;
        }
        if (goal == Goal.NUZZLE) {
            nuzzlesThisStill++;
            nuzzleCooldown = drawTicks(25, 45) * (1 << Math.min(3, nuzzlesThisStill - 1));
        }
        goal = g;
        goalTicks = 0;
        goalSwitches++;
        pose = PoseHint.NONE;
        switch (g) {
            case LOITER, SETTLE -> {
                spot = null;
                dwelling = false;
                centreTicks = 0;
            }
            case SHOW_OFF -> {
                showPhase = 0;
                showTicks = 0;
                showDone = false;
                showSide = 0; // chosen on the first tick, from where the fish is
                showOffs++;
            }
            case INSPECT -> {
                inspecting = drives.target;
                inspections++;
                ambushPhase = 0;
            }
            case HIDE, GUARD -> {
                hides++;
                coverTicks = 0;
                peekTicks = 0;
                // Only a temperament that peeks draws for it (the random stream stays the baseline's otherwise).
                peekWait = temperament.peekOut() ? drawTicks(2, 4) : 0;
            }
            case NUZZLE -> {
                nuzzleSide = sideOf(o0);
                nuzzleHold = drawTicks(1.5, 2.5);
                nuzzleDone = false;
                nuzzles++;
            }
            default -> { }
        }
    }

    // ── Goals ──────────────────────────────────────────────────────────────────────────────────

    private Vec loiter(OwnerState o, LocalVoxels v) {
        Vec chest = o.chest();
        // The middle of the owner's view is kept clear, as a settled fish does: it never lingers
        // there, and if its spot (or the fish by it) ends up there — the owner turned to it — it is
        // off somewhere else after a second. (Phase 1 picked a spot there and slowed through it
        // for 2–3 s; plan §10.5.)
        boolean centred = body.pos.distanceTo(o.eye()) < CENTRE_CLEAR && Visibility.angleFromCenterDeg(o, body.pos) < 9;
        boolean spotCentred = spot != null && spot.distanceTo(o.eye()) < CENTRE_CLEAR
                && Visibility.angleFromCenterDeg(o, spot) < 10;
        centreTicks = spotCentred || centred ? centreTicks + 1 : 0;
        if (spot == null || spot.distanceTo(chest) > bandOuterNow + 0.8 || !v.sphereClear(spot, body.radius + 0.1)
                || centreTicks > 20) {
            spot = pickSpot(o, v);
            dwelling = false;
            centreTicks = 0;
        }
        target = spot;
        Vec to = spot.sub(body.pos);
        double d = to.length();
        if (!dwelling) {
            wanderScale = 0.8;
            if (d < 0.35 && !centred) {
                dwelling = true;
                dwellTicks = drawTicks(t.dwellMinSeconds * temperament.dwell() * idleDwell(),
                        t.dwellMaxSeconds * temperament.dwell() * idleDwell());
            }
            // Out of the middle of the view it goes at its own pace, never the calm one (§13.7).
            double pace = centreTicks > 0 ? 1 : idlePace();
            double speed = Math.min(body.g.cruiseSpeed() * idleSurge() * pace, 0.1 + d * 1.2);
            return aroundOwner(o, v, spot).sub(body.pos).normalize().scale(speed);
        }
        // Lingering: a slow mill held loosely to the spot (a still temperament hangs almost motionless).
        wanderScale = 1.2 * (1 - 0.7 * temperament.stillness());
        if (--dwellTicks <= 0) spot = null;
        Vec heading = Vec.fromYawPitch(body.yaw, 0);
        return heading.scale(0.25 * body.g.cruiseSpeed() * idleSurge() * idlePace() * (1 - temperament.stillness())).add(to.scale(0.6));
    }

    // A drifter's calm idle (docs/fish-companions-drift.md §13.7). For every other body these are the
    // identity, so a swimmer's idle is untouched to the bit.

    private boolean drifter() {
        return body.locomotion == grill24.fishsim.core.Locomotion.DRIFT;
    }

    /**
     * How far a drifter's idle has calmed, 0..1: eased in over a second of its owner standing still.
     * (Waiting a moment longer before calming was measured worse on equal seeds — §13.7.)
     */
    private double calmK() {
        return drifter() ? Math.min(1, stillTicks / (double) TPS) : 0;
    }

    /** Pace of an idle move, as a multiple of the cruise. */
    private double idlePace() {
        return drifter() ? 1 + (t.driftIdlePace - 1) * calmK() : 1;
    }

    /** Scale on how long it lingers at an idle spot. */
    private double idleDwell() {
        return drifter() ? 1 + (t.driftIdleDwell - 1) * calmK() : 1;
    }

    /** The burst surge on an idle ask: a swimmer's tail beat, a steady drifter's none. */
    private float idleSurge() {
        return t.driftIdleSteady && calmK() > 0 ? 1f : body.burstDrive;
    }

    // ── SETTLE ──
    private int centreTicks;
    /** Settle spots alternate: the edge of the owner's view, then beside or behind them. */
    private boolean settleInView = true;

    /**
     * The rested idle: the same drift between spots as {@link #loiter}, but slower, closer, with
     * long lingers and a lazier mill. About half its spots are at the edge of the owner's view, so
     * a dozing fish is there to be seen; if the owner turns to put it in the middle of the screen,
     * it drifts aside after a moment.
     */
    private Vec settle(OwnerState o, LocalVoxels v) {
        Vec chest = o.chest();
        // The spot, or the fish lingering by it, in the middle of the view (the owner turned to it):
        // after a second, off somewhere else.
        boolean spotCentred = spot != null && spot.distanceTo(o.eye()) < CENTRE_CLEAR && Visibility.angleFromCenterDeg(o, spot) < 10;
        boolean fishCentred = body.pos.distanceTo(o.eye()) < CENTRE_CLEAR && Visibility.angleFromCenterDeg(o, body.pos) < 9;
        centreTicks = spotCentred || fishCentred ? centreTicks + 1 : 0;
        if (spot == null || spot.distanceTo(chest) > bandOuterNow + 0.8 || !v.sphereClear(spot, body.radius + 0.1)
                || centreTicks > 20) {
            spot = pickSettleSpot(o, v);
            dwelling = false;
            centreTicks = 0;
        }
        target = spot;
        Vec to = spot.sub(body.pos);
        double d = to.length();
        // Drifting across the middle of the owner's view, it doesn't dawdle (nor linger there).
        boolean centred = body.pos.distanceTo(o.eye()) < CENTRE_CLEAR && Visibility.angleFromCenterDeg(o, body.pos) < 9;
        if (!dwelling) {
            wanderScale = 0.6;
            if (d < 0.3 && !centred) {
                dwelling = true;
                dwellTicks = drawTicks(t.dwellMinSeconds * 3 * temperament.dwell() * idleDwell(),
                        t.dwellMaxSeconds * 3 * temperament.dwell() * idleDwell());
            }
            double speed = Math.min(0.6 * body.g.cruiseSpeed() * idleSurge() * idlePace(), 0.1 + d * 1.0);
            if (centred) speed = Math.max(speed, body.g.cruiseSpeed());
            return aroundOwner(o, v, spot).sub(body.pos).normalize().scale(speed);
        }
        wanderScale = 0.6 * (1 - 0.7 * temperament.stillness());
        pose = PoseHint.SLEEPY;
        if (--dwellTicks <= 0) spot = null;
        Vec heading = Vec.fromYawPitch(body.yaw, 0);
        return heading.scale(0.12 * body.g.cruiseSpeed() * (1 - temperament.stillness())).add(to.scale(0.5));
    }

    /** Within this of the eye, blocks, a spot must keep out of the central 12° of the view. */
    private static final double CENTRE_CLEAR = 2.5;

    /**
     * A resting spot, alternately at the edge of the owner's view (15–28° off centre, 1.6–2.4 blocks
     * out, a little below the line of sight) and close beside or behind them, a short drift away —
     * so a dozing fish is in sight every other linger. Late attempts take either kind.
     */
    private Vec pickSettleSpot(OwnerState o, LocalVoxels v) {
        Vec chest = o.chest();
        double inner = t.bandInner * body.g.bandScale() * temperament.band() + 0.2;
        boolean wantView = settleInView;
        settleInView = !settleInView;
        for (int attempt = 0; attempt < 24; attempt++) {
            int side = unit() < 0.5 ? -1 : 1;
            boolean inView = attempt < 12 ? wantView : attempt % 2 == 0;
            Vec p;
            if (inView) {
                double off = 15 + 13 * unit();
                p = o.eye().add(Vec.fromYawPitch(o.yawDeg() + side * (float) off,
                        Body.clamp(o.pitchDeg(), -20f, 30f) + 8f).scale(1.6 + 0.8 * unit()));
            } else {
                double yaw = o.yawDeg() + side * (70 + 90 * unit());
                p = chest.add(Vec.fromYawPitch((float) yaw, 0).scale(inner + 0.9 * unit())).add(0, -0.2 + 0.4 * unit(), 0);
            }
            double hop = p.distanceTo(body.pos);
            if (attempt < 16 && (hop < 0.5 || hop > 3.2)) continue;
            if (!v.sphereClear(p, body.radius + 0.25)) continue;
            if (!v.segmentClear(body.pos, p, body.radius)) continue;
            if (p.distanceTo(o.eye()) < CENTRE_CLEAR && Visibility.angleFromCenterDeg(o, p) < 12) continue;
            if (insideOwner(o, p, body.radius + 0.25)) continue;
            return p;
        }
        return body.pos;
    }

    /** A nearby open spot inside the comfort band, a short hop from where the fish is now. */
    private Vec pickSpot(OwnerState o, LocalVoxels v) {
        Vec chest = o.chest();
        Gait g = body.g;
        double inner = t.bandInner * g.bandScale() * temperament.band() + 0.2;
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
            // A still temperament, which hangs motionless at its spot, doesn't even pick one there.
            double clear = temperament.stillness() > 0 ? CENTRE_CLEAR : 1.2;
            if (p.distanceTo(o.eye()) < clear && Visibility.angleFromCenterDeg(o, p) < 12) continue;
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
            changeTicks = drawTicks(t.escortChangeMinSeconds * temperament.decisions(),
                    t.escortChangeMaxSeconds * temperament.decisions());
        }
        if (surgeTicks > 0 && --surgeTicks == 0) surgeTarget = 0;
        // A sprinter dashes out ahead while its owner sprints, and drops back when they stop.
        if (temperament.raceAhead()) {
            if (o.sprinting()) {
                racing = true;
                surgeTarget = RACE_AHEAD;
                pose = PoseHint.EXCITED;
            } else if (racing) {
                racing = false;
                surgeTarget = 0;
            }
        }
        surge = approach(surge, surgeTarget, (racing ? 1.5 : 0.6) * Body.DT);
        driftLat = ou(driftLat);
        driftAlong = ou(driftAlong);
        driftHeight = ou(driftHeight);

        double lateral = t.escortLateral * body.g.lateralScale() * temperament.band() + driftLat * 0.3;
        lateralSigned = approach(lateralSigned, side * lateral, 0.9 * Body.DT);
        // Crossing over goes in front of the owner, never through them.
        double centre = 1 - Math.min(1, Math.pow(lateralSigned / (t.escortLateral * body.g.lateralScale() * temperament.band()), 2));
        double along = alongBase + driftAlong * 0.4 + surge + 1.4 * centre + body.g.alongOffset();
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
     * Over to something interesting, to a spot a species-sized distance off it, then nose at it.
     * A held item is looked at from beyond the hand, along the view, so the owner sees the fish
     * do it; a thing in the world from the fish's own side of it, a little above.
     */
    private Vec inspect(OwnerState o, LocalVoxels v) {
        Drives.Interest it = inspecting;
        if (it == null) return loiter(o, v);
        Vec item = it.pos;
        double hold = (0.35 + 1.2 * bodyLength() + it.radius) * temperament.inspectSpacing();
        Vec standoff;
        if (it.isHeld()) {
            // In the view's own frame, beside the item and a little beyond it — but never so deep
            // that the spot slides into the middle of the screen (it stays 14° or more off centre).
            Vec look = o.look();
            Vec right = look.cross(Vec.UP).normalize();
            if (right.lengthSqr() < 1e-9) right = o.flatRight();
            Vec up = right.cross(look);
            standoff = it.held == CompanionWorld.Held.ROD
                    ? item.add(right.scale(0.25)).add(look.scale(-0.15)).sub(up.scale(0.15))
                    : o.eye().add(look.scale(Math.min(1.5, 0.6 + hold * 0.8))).add(right.scale(0.36)).add(up.scale(-0.16));
        } else {
            Vec away = body.pos.sub(item).horizontal();
            if (away.lengthSqr() < 1e-6) away = o.chest().sub(item).horizontal();
            standoff = item.add(away.normalize().scale(hold)).add(0, 0.3, 0);
        }
        if (!v.sphereClear(standoff, body.radius + 0.1)) standoff = standoff.add(0, 0.4, 0);
        Vec carry = it.vel.horizontal();
        if (temperament.ambush() && it.kind == PointOfInterest.Kind.DROPPED_ITEM && ambushPhase < 2) {
            return ambush(o, v, it, item, standoff, hold);
        }
        target = standoff;
        drives.engage(it, body.pos.distanceTo(item), hold);
        pose = poseFor(it);
        wanderScale = 0.3;
        Vec to = standoff.sub(body.pos);
        double d = to.length();
        if (d > 0.3) {
            double speed = Math.min(body.g.cruiseSpeed() * 1.8, 0.2 + d * 1.4);
            // The ambusher's dart: everything at once, then an arrive.
            if (ambushPhase == 2) speed = Math.min(body.g.catchUpSpeed() * 0.7, 0.4 + d * 4);
            return carry.add(aroundOwner(o, v, standoff).sub(body.pos).normalize().scale(speed));
        }
        if (ambushPhase == 2) ambushPhase = 3;
        // There: hold the spot loosely and turn the nose to it at a creep.
        Vec nose = item.sub(body.pos).normalize().scale(0.25 * body.g.cruiseSpeed());
        return carry.add(nose).add(to.scale(0.8));
    }

    /**
     * Into cover on the owner's far side from the threat — behind their head from a cat or a
     * predator fish in their hand, behind their body from a hostile — quickly, then held loosely
     * there, nose toward the danger.
     */
    private Vec hide(OwnerState o, LocalVoxels v) {
        Vec threat = drives.threatPos;
        if (threat == null) return loiter(o, v);
        Vec chest = o.chest();
        Vec away = chest.sub(threat).horizontal();
        if (away.lengthSqr() < 1e-6) away = o.flatForward().scale(-1);
        away = away.normalize();
        boolean guarding = goal == Goal.GUARD;
        boolean head = drives.threat != Drives.Threat.HOSTILE;
        Vec cover = guarding
                // In front of the owner, between them and the hostile, puffed up.
                ? chest.add(0, 0.15, 0).sub(away.scale(OwnerState.BODY_RADIUS + body.radius + 0.6))
                : (head ? o.eye().add(0, 0.05, 0) : chest.add(0, 0.15, 0))
                        .add(away.scale(OwnerState.BODY_RADIUS + body.radius + 0.4));
        if (!v.sphereClear(cover, body.radius + 0.1)) cover = chest.add(away.scale(OwnerState.BODY_RADIUS + body.radius + 0.4));
        Vec to0 = cover.sub(body.pos);
        coverTicks = to0.length() < 0.35 ? coverTicks + 1 : 0;
        pose = guarding ? PoseHint.PUFF : PoseHint.HIDE;
        // Skittish: settled in cover, it now and then edges out sideways for a look.
        if (temperament.peekOut() && !guarding && coverTicks > 0) {
            if (peekTicks > 0) {
                peekTicks--;
                cover = cover.add(away.cross(Vec.UP).scale(0.45 * peekSide));
                pose = PoseHint.PEEK;
            } else if (--peekWait <= 0) {
                peekTicks = 20;
                peekSide = -peekSide;
                peekWait = drawTicks(2, 4);
            }
        }
        target = cover;
        wanderScale = 0.2;
        Vec to = cover.sub(body.pos);
        double d = to.length();
        Vec carry = o.velocity().horizontal();
        if (d > 0.3) {
            // A quick dart, but never past 0.7 × catch-up speed all told, the owner's pace
            // included: hard turns and climbs into cover at more than that overrun the body's caps.
            double speed = Math.min(body.g.catchUpSpeed() * 0.7, 0.5 + d * 3);
            return carry.add(aroundOwner(o, v, cover).sub(body.pos).normalize().scale(speed))
                    .clampLength(body.g.catchUpSpeed() * 0.7);
        }
        Vec watch = threat.sub(body.pos).normalize().scale(0.15 * body.g.cruiseSpeed());
        // A bite at the air: a short lunge toward the threat, then back.
        if (guarding && biteTicks > BITE_TICKS / 2) watch = threat.sub(body.pos).normalize().scale(2.5);
        return carry.add(watch).add(to.scale(0.8));
    }

    /** Ticks of standing still before a docile fish comes for a nuzzle. */
    private static final int NUZZLE_DWELL_TICKS = 5 * TPS;

    /**
     * A docile fish's nuzzle: over to the side of its owner's head (the side it is on), a moment
     * there nosing at their cheek, then back to whatever it was doing.
     */
    private Vec nuzzle(OwnerState o, LocalVoxels v) {
        Vec cheek = o.eye().add(o.flatRight().scale(nuzzleSide * 0.55)).add(o.flatForward().scale(-0.05)).add(0, -0.08, 0);
        if (!v.sphereClear(cheek, body.radius + 0.05)) {
            nuzzleDone = true;
            return loiter(o, v);
        }
        target = cheek;
        pose = PoseHint.NUZZLE;
        wanderScale = 0.15;
        Vec to = cheek.sub(body.pos);
        double d = to.length();
        if (goalTicks > 6 * TPS) nuzzleDone = true;
        if (d > 0.25) {
            double speed = Math.min(body.g.cruiseSpeed(), 0.15 + d * 1.2);
            return aroundOwner(o, v, cheek).sub(body.pos).normalize().scale(speed);
        }
        if (--nuzzleHold <= 0) nuzzleDone = true;
        Vec nose = o.eye().sub(body.pos).horizontal().normalize().scale(0.12 * body.g.cruiseSpeed());
        return nose.add(to.scale(0.8));
    }

    /**
     * An ambusher's approach to a dropped item: creep to twice the inspecting distance, hang there
     * dead still, nose on, for a second or two — then dart in, and inspect as usual.
     */
    private Vec ambush(OwnerState o, LocalVoxels v, Drives.Interest it, Vec item, Vec standoff, double hold) {
        Vec stalk = item.add(standoff.sub(item).scale(2.2));
        if (!v.sphereClear(stalk, body.radius + 0.1)) stalk = standoff;
        target = stalk;
        pose = PoseHint.WATCH;
        wanderScale = 0.1;
        Vec to = stalk.sub(body.pos);
        double d = to.length();
        if (ambushPhase == 0) {
            if (d < 0.3) {
                ambushPhase = 1;
                ambushHold = drawTicks(1.0, 2.0);
            }
            return aroundOwner(o, v, stalk).sub(body.pos).normalize().scale(Math.min(0.5 * body.g.cruiseSpeed(), 0.1 + d));
        }
        // Holding still, nose on the item; then the dart (the rest of the inspection does it,
        // asked for at speed).
        if (--ambushHold <= 0) ambushPhase = 2;
        Vec nose = item.sub(body.pos).normalize().scale(0.1 * body.g.cruiseSpeed());
        return nose.add(to.scale(0.8));
    }

    static PoseHint poseFor(Drives.Interest it) {
        if (!it.isHeld()) return PoseHint.NOSE;
        return switch (it.held) {
            case ROD -> PoseHint.EXCITED;
            case BAIT -> PoseHint.BEG;
            case NAME_TAG -> PoseHint.TILT;
            case FOOD -> PoseHint.MILD;
            default -> PoseHint.NOSE;
        };
    }

    /** The fish's drawn length, from the collision radius it was given (radius = 0.3 × length, clamped). */
    private double bodyLength() {
        return body.radius / 0.3;
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
        // A nuzzle is meant to come close.
        double limit = OwnerState.BODY_RADIUS + body.radius + (goal == Goal.NUZZLE ? 0.05 : t.ownerMargin);
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
        return aroundOwner(o, v, body.pos, dest, body.radius);
    }

    /**
     * Where a body of {@code radius} at {@code pos} should swim to reach {@code dest}: {@code dest}
     * itself, or — when the straight line cuts through the owner — a waypoint round their side
     * (the side it is already on). Shared by the mind and a trio's followers.
     */
    static Vec aroundOwner(OwnerState o, LocalVoxels v, Vec pos, Vec dest, double radius) {
        double clearance = OwnerState.BODY_RADIUS + radius + 0.25;
        if (closestApproach(o, pos, dest) >= clearance) return dest;
        Vec seg = dest.sub(pos).horizontal().normalize();
        if (seg.lengthSqr() < 1e-9) return dest;
        Vec perp = new Vec(-seg.z(), 0, seg.x());
        Vec chest = o.chest();
        double sign = pos.sub(chest).dot(perp) >= 0 ? 1 : -1;
        Vec axis = nearestOnBody(o, pos);
        double out = clearance + 0.35;
        for (int k = 0; k < 2; k++, sign = -sign) {
            Vec wp = new Vec(axis.x(), pos.y(), axis.z()).add(perp.scale(sign * out));
            if (v.sphereClear(wp, radius + 0.05) && v.segmentClear(pos, wp, radius)) return wp;
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
        double u = unit();
        // A rhythmic temperament draws from nearer the middle of the range, all the way to always the middle.
        if (temperament.regular() > 0) u = 0.5 + (u - 0.5) * (1 - temperament.regular());
        return (int) Math.round((minSeconds + (maxSeconds - minSeconds) * u) * TPS);
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
