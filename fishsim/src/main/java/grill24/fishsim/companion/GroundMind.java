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
    /** Diagnostic A/B switch for the formation edge guard (§23). */
    static boolean formationDropGuard = true;
    private int blockedCloseTicks;
    private int closeRetry;
    private Vec closeOwner, closeAnchor, recoveryStation;
    private float closeYaw;
    /** Diagnostic A/B seam, like beatsEnabled; normal operation always recovers. */
    static boolean alongsideRecoveryEnabled = true;
    private static final int CLOSE_STALL_TICKS = 40;
    double closeSide = 1;
    /** Coming up alongside unhurried (to climb on to rest), not scurrying (to be seen). */
    boolean closeCalm;
    /** Ticks it stays where it landed after a tumble, gathering itself, before it walks on. */
    int daze;
    /** Where the owner stands, on the floor (their feet projected down when they're off it). */
    Vec ownerFloor;
    private int ownerStill;
    private int planClock;
    /** -1: walking straight to the owner; otherwise the crumb it's heading for. */
    private int via = -1;
    private Vec viaPoint;
    private int hopRetry;
    int gapHops;
    /** A follower's station, supplied by its group rather than a separate desire/mind. */
    Vec formationTarget, formationPush;
    double formationSpeed;
    float formationFace = Float.NaN;
    boolean formationMember;
    /** Party-mates of other units standing too close, pushing it away (§6.2 candidate), or null. */
    Vec partyPush;
    java.util.List<PointOfInterest> playerPoints = java.util.List.of();
    Mind.GroundIntent intent;

    // In-view beats (spec §5): a scurry to a fixed spot beside the owner's path ahead, where it
    // waits for them to walk past. The spot is chosen once, never chased: a target moving with
    // the owner ran it into terrain and its followers off the trail.
    // The spec's third beat, the turn reveal, is the walk itself: within DIRECT_RANGE it walks
    // straight at its owner, cutting the corner of a turn rather than following the trail. A
    // separate turn dash measured no gain in seen and met pillars in the cellar; it was removed.
    enum Beat { OVERTAKE, ROADSIDE }
    /** Ticks a straight walk must hold before an overtake or roadside stop is tried. */
    private static final int BEAT_STRAIGHT = 12;
    /** Ticks over which the owner's way must hold straight, compared end to end. */
    private static final int STRAIGHT_WINDOW = 12;
    /** Diagnostics switch: off, nothing here runs and the ground behaves as before the beats. */
    static boolean beatsEnabled = true;
    Beat beat;
    private Vec beatSpot, beatDir;
    /** An overtake's lead: on from its spot to here at the owner's pace, out ahead in view. */
    private Vec beatEnd;
    private double beatAhead;
    private int beatTicks, beatLimit;
    private boolean beatArrived, beatLed;
    private float beatFace = Float.NaN;
    int beatCooldown;
    int overtakes, roadsides;
    /** Ticks since its last beat ended. */
    int sinceBeat = Integer.MAX_VALUE / 2;
    /** Ticks after a beat in which its owner's glimpses of it still belong to the beat. */
    static final int BEAT_AFTERMATH = 60;
    /** Set by the percher: ticks since its owner last saw it, and after how many it tries a beat. */
    int unseen, beatAfter = Integer.MAX_VALUE;
    /** Races ahead of its owner often (the sprinter), rather than rarely. */
    boolean raceAhead;
    private final Vec[] travelDirs = new Vec[STRAIGHT_WINDOW];
    /** The beats' own draws, apart from the pottering's: a run with no beat is the run without them. */
    private long beatRng;
    private int travelAt, straightTicks;

    // Idle pottering.
    private Vec spot;
    private Vec spotOwner;
    private int spotDwell;
    private boolean nextInView = true;
    /** Scales the wait after a beat (HandClock's "more ground beats": under 1 is more often). */
    double beatCooldownScale = 1;
    private float spotFace = Float.NaN;

    GroundMind(Breadcrumbs trail, GroundBody g, long seed) {
        this.trail = trail;
        this.g = g;
        this.rng = seed * 0xD1B54A32D192ED03L + 0x632BE59BD9B4E019L;
        if (rng == 0) rng = 1;
        this.beatRng = rng ^ 0x9E3779B97F4A7C15L;
        if (beatRng == 0) beatRng = 1;
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
        if (closeRetry > 0) {
            closeRetry--;
            wantClose = false;
        }

        // The way is judged with its feet down: in the air (off a step, mid-hop) its floor is the
        // one it left, and a plan from there would read it as far behind or stranded.
        boolean down = g.grounded();
        if (down && planClock-- <= 0) {
            plan(v);
            planClock = REPLAN - 1;
        } else if (down && via < 0) {
            lag = horizontal(g.pos, ownerFloor);
        }

        Vec closeTarget = wantClose ? closeStation(o, v, o.velocity().horizontal().scale(0.3)) : null;
        if (!wantClose) clearCloseRecovery();
        if (down && recoveryStation != null) {
            // The alternate station has its own checked route, even if approaching the pillar
            // briefly obscured the direct route to the owner.
            reachable = true;
            via = -1;
            lag = horizontal(g.pos, recoveryStation) + horizontal(recoveryStation, ownerFloor);
        }
        if (down) {
            strandedTicks = reachable ? 0 : strandedTicks + 1;
            if (strandedTicks >= STRANDED_TICKS || lag > FADE_LAG) wantsFade = true;
        }

        // Choose the land mode.
        Land next = land;
        if (land == Land.CATCH_UP) {
            if (lag < CAUGHT_UP_LAG && landTicks >= MIN_CATCH_UP) next = ownerStill >= IDLE_AFTER ? Land.GROUND_IDLE : Land.GROUND_FOLLOW;
        } else if (lag > (beat != null ? CATCH_UP_LAG + 1.5 : CATCH_UP_LAG)) {
            // Out ahead or beside the path on a beat its straight-line distance can pass the
            // catch-up mark without it being behind: the beat ends itself once they pass.
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
        updateBeat(o, v, down);

        // Where to go, how fast, in what rhythm.
        Vec push = ownerPush(o);
        double follow = followDist(length);
        double want;
        GroundBody.Rhythm rhythm;
        float face = Float.NaN;
        Vec desire = null;
        if (intent != null && land != Land.CATCH_UP) {
            Vec candidate = intent.target();
            double floor = g.footprintFloorFrom(v, candidate.x(), candidate.z(), g.floorY);
            if (!Double.isNaN(floor)) {
                candidate = new Vec(candidate.x(), floor, candidate.z());
                if (walkable(v, g.pos, candidate)) desire = candidate;
            }
        }
        if (daze > 0) {
            // Knocked off its perch: it lands, squashes, and stays a moment before it turns and goes.
            if (down) daze--;
            target = g.standing();
            want = 0;
            rhythm = GroundBody.Rhythm.AMBLE;
            push = null;
        } else if (!reachable) {
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
            rhythm = closeCalm ? GroundBody.Rhythm.AMBLE : GroundBody.Rhythm.SCURRY;
            target = closeTarget;
            if (recoveryStation != null) push = null;
            want = closeCalm ? g.cruise() * 0.8 : Math.max(g.cruise(), ownerSpeed + 1.5 * horizontal(g.pos, target));
        } else if (desire != null) {
            target = desire;
            rhythm = intent.hurry() ? GroundBody.Rhythm.SCURRY : GroundBody.Rhythm.TROT;
            want = intent.hurry() ? g.rhythmBase(rhythm) * 1.4 : g.cruise();
            face = intent.face();
        } else if (beat != null) {
            rhythm = GroundBody.Rhythm.SCURRY;
            if (beatArrived && beatEnd != null && !beatLed) {
                // Leading: keep its place out ahead of them, along the line checked when it set off.
                double ahead = g.pos.sub(ownerFloor).horizontal().dot(beatDir);
                target = beatEnd;
                want = Body.clamp(ownerSpeed + 1.5 * (beatAhead - ahead), 0, g.rhythmBase(GroundBody.Rhythm.SCURRY) * 1.6);
                rhythm = GroundBody.Rhythm.TROT;
            } else if (beatArrived) {
                target = g.standing();
                want = 0;
                face = beatFace;
            } else {
                target = beatSpot;
                want = g.rhythmBase(GroundBody.Rhythm.SCURRY) * 1.6;
            }
        } else if (formationTarget != null) {
            target = formationTarget;
            // A station can be beyond the owner's ankles after a turn. Route beside them
            // before returning to the formation instead of trying to turn inside their feet.
            Vec leg = target.sub(g.pos).horizontal();
            double along = leg.lengthSqr() > 1e-9
                    ? Body.clamp(ownerFloor.sub(g.pos).horizontal().dot(leg) / leg.lengthSqr(), 0, 1) : 0;
            Vec nearest = g.pos.add(leg.scale(along));
            if (horizontal(nearest, ownerFloor) < g.radius + 0.6) {
                Vec side = o.flatRight();
                if (g.pos.sub(ownerFloor).horizontal().dot(side) < 0) side = side.scale(-1);
                target = ownerFloor.add(side.scale(g.radius + 0.95));
            }
            double floor = g.footprintFloorFrom(v, target.x(), target.z(), g.floorY);
            if (!Double.isNaN(floor)) target = new Vec(target.x(), floor, target.z());
            want = formationSpeed;
            rhythm = land == Land.CATCH_UP || horizontal(g.pos, target) > 2
                    ? GroundBody.Rhythm.SCURRY : GroundBody.Rhythm.TROT;
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
        g.preventUnplannedDrop = formationDropGuard && formationMember;
        Vec ownerTravel = o.velocity().horizontal();
        double passTime = ownerTravel.lengthSqr() > 0.01
                ? Body.clamp(g.pos.sub(ownerFloor).horizontal().dot(ownerTravel) / ownerTravel.lengthSqr(), 0, 1.2) : 0;
        double scatterRoom = g.pace < 0.8 ? 1.6 : 1.2;
        boolean feetApproaching = formationMember && ownerTravel.length() > 0.6
                && horizontal(g.pos, ownerFloor.add(ownerTravel.scale(passTime))) < g.radius + scatterRoom;
        Vec passingFeet = ownerFloor;
        // Pick the soonest nearby crossing at this body's height. The owner keeps its existing
        // solo avoidance; bystanders use the same lateral escape motor for solos and groups.
        double crossing = feetApproaching ? passTime : Double.POSITIVE_INFINITY;
        for (PointOfInterest player : playerPoints) {
            if (player.kind() != PointOfInterest.Kind.PLAYER || Math.abs(player.pos().y() - g.floorY) > 0.8) continue;
            Vec travel = player.vel().horizontal();
            if (travel.length() <= 0.6) continue;
            double time = Body.clamp(g.pos.sub(player.pos()).horizontal().dot(travel) / travel.lengthSqr(), 0, 1.2);
            if (time >= crossing || horizontal(g.pos, player.pos().add(travel.scale(time))) >= g.radius + scatterRoom) continue;
            crossing = passTime = time;
            passingFeet = player.pos();
            ownerTravel = travel;
            feetApproaching = true;
        }
        g.urgent = feetApproaching;
        if (feetApproaching && ownerTravel.length() > 0.6 && daze == 0 && reachable) {
            Vec side = new Vec(-ownerTravel.z(), 0, ownerTravel.x()).normalize();
            double offset = g.pos.sub(passingFeet).horizontal().dot(side);
            // Near the centre of the path, escape toward the side it already faces. Choosing
            // purely by a tiny position offset can demand a 180-degree pivot before scattering.
            double choice = Math.abs(offset) < 0.6 ? Vec.fromYawPitch(g.yaw, 0).dot(side) : offset;
            if (choice < 0) side = side.scale(-1);
            Vec passing = passingFeet.add(ownerTravel.scale(passTime));
            target = passing.add(side.scale(g.radius + scatterRoom + 0.2));
            want = g.rhythmBase(GroundBody.Rhythm.SCURRY) * 1.6;
            rhythm = GroundBody.Rhythm.SCURRY;
            if (g.pace < 0.8) push = null; // slow crawlers follow the lateral escape waypoint directly
        }
        if (!Float.isNaN(formationFace) && !wantClose && !feetApproaching && daze == 0 && g.grounded()) {
            target = g.standing();
            want = 0;
            face = formationFace;
            // Turning on the spot, except out of a member it stands inside of.
            push = formationPush != null && formationPush.length() > 1.0 ? formationPush : null;
        }
        if (formationMember && !feetApproaching && daze == 0 && reachable) {
            Vec leg = target.sub(g.pos).horizontal();
            double along = leg.lengthSqr() > 1e-9
                    ? Body.clamp(ownerFloor.sub(g.pos).horizontal().dot(leg) / leg.lengthSqr(), 0, 1) : 0;
            if (horizontal(g.pos.add(leg.scale(along)), ownerFloor) < g.radius + 0.6) {
                Vec side = o.flatRight();
                if (g.pos.sub(ownerFloor).horizontal().dot(side) < 0) side = side.scale(-1);
                target = ownerFloor.add(side.scale(g.radius + 0.95));
            }
        }
        if (!feetApproaching && formationPush != null && push != null) push = push.add(formationPush);
        if (partyPush != null && !feetApproaching && daze == 0) push = push == null ? partyPush : push.add(partyPush);
        g.walk(target, want, rhythm, push, face, v);
    }

    private void clearCloseRecovery() {
        blockedCloseTicks = 0;
        closeOwner = closeAnchor = recoveryStation = null;
    }

    /** After a stationary approach stalls inside a tall wall, hop to a free station on that side. */
    private Vec closeStation(OwnerState o, LocalVoxels v, Vec lead) {
        Vec preferred = ownerFloor.add(o.flatRight().scale(0.75 * closeSide))
                .add(o.flatForward().scale(0.3)).add(lead);
        if (!alongsideRecoveryEnabled || o.horizontalSpeed() > OWNER_MOVING || !v.solid((int) Math.floor(preferred.x()),
                (int) Math.floor(ownerFloor.y() + GroundBody.STEP_HOP + 0.01), (int) Math.floor(preferred.z()))) {
            clearCloseRecovery();
            return preferred;
        }
        if (closeOwner == null || horizontal(closeOwner, ownerFloor) > 0.25
                || Math.abs(Body.wrap(o.yawDeg() - closeYaw)) > 10) {
            clearCloseRecovery();
            closeOwner = ownerFloor;
            closeYaw = o.yawDeg();
        }
        if (recoveryStation != null) {
            double floor = g.footprintFloorFrom(v, recoveryStation.x(), recoveryStation.z(), recoveryStation.y());
            if (!Double.isNaN(floor) && Math.abs(floor - recoveryStation.y()) < 0.05
                    && (!g.onFloor() || walkable(v, g.pos, recoveryStation))) {
                if (g.onFloor() && horizontal(g.pos, recoveryStation) < 0.4) {
                    clearCloseRecovery();
                    closeRetry = 100;
                    wantClose = false;
                    return null;
                }
                return recoveryStation;
            }
            recoveryStation = null;
        }
        if (closeAnchor == null || horizontal(closeAnchor, g.pos) > 0.2) {
            closeAnchor = g.pos;
            blockedCloseTicks = 0;
        }
        if (!g.grounded() || daze > 0 || ++blockedCloseTicks < CLOSE_STALL_TICKS) return preferred;
        Vec other = ownerFloor.add(o.flatRight().scale(0.75 * closeSide))
                .add(o.flatForward().scale(-1.5)).add(lead);
        double floor = g.footprintFloorFrom(v, other.x(), other.z(), ownerFloor.y());
        if (Double.isNaN(floor) || Math.abs(floor - ownerFloor.y()) > 0.6) return failedCloseRecovery(preferred);
        other = new Vec(other.x(), floor, other.z());
        // A checked block arc must also stay outside the owner's body. This retreat goes
        // away from the feet; reject a station whose straight leg would cross them.
        Vec leg = other.sub(g.pos).horizontal();
        double along = leg.lengthSqr() > 1e-9
                ? Body.clamp(ownerFloor.sub(g.pos).horizontal().dot(leg) / leg.lengthSqr(), 0, 1) : 0;
        if (horizontal(g.pos.add(leg.scale(along)), ownerFloor) < g.radius + OwnerState.BODY_RADIUS + 0.05) return failedCloseRecovery(preferred);
        if (g.grounded() && g.hopTo(other.add(0, g.radius + LocalVoxels.GROUND_LIFT, 0), v)) return recoveryStation = other;
        return failedCloseRecovery(preferred);
    }

    private Vec failedCloseRecovery(Vec preferred) {
        // The owner can remain reachable while both this station and the retreat are blocked.
        if (blockedCloseTicks >= STRANDED_TICKS) wantsFade = true;
        return preferred;
    }

    // ── In-view beats ──────────────────────────────────────────────────────────────────────────

    // Off to the side of their path, but inside the view: the floor near their feet is below it
    // (a level 70-degree view meets the floor about 2.4 blocks out) and too far aside is past its edge.
    private static final double[] OVERTAKE_SIDES = {1.5, 1.9, 2.3};
    private static final double[] ROADSIDE_SIDES = {1.6, 2.0, 2.4};

    /** Starts, keeps or ends a beat. */
    private void updateBeat(OwnerState o, LocalVoxels v, boolean down) {
        if (!beatsEnabled) return;
        if (beatCooldown > 0) beatCooldown--;
        if (beat == null && sinceBeat < Integer.MAX_VALUE / 2) sinceBeat++;
        Vec travel = o.velocity().horizontal();
        double speed = travel.length();
        Vec dir = speed > 1.5 ? travel.scale(1 / speed) : null;
        Vec old = travelDirs[travelAt];
        travelDirs[travelAt] = dir;
        travelAt = (travelAt + 1) % STRAIGHT_WINDOW;
        boolean straight = dir != null && old != null && dir.dot(old) > Math.cos(Math.toRadians(10));
        straightTicks = straight ? straightTicks + 1 : 0;

        boolean blocked = land != Land.GROUND_FOLLOW || !reachable || via >= 0 || intent != null || wantClose
                || daze > 0 || g.urgent || formationTarget != null || !Float.isNaN(formationFace);
        if (beat != null) {
            beatTicks++;
            if (!beatArrived && horizontal(g.pos, beatSpot) < 0.25) beatArrived = true;
            // The lead ends at its end point, or after its time: then it drops back, waiting there.
            if (beatArrived && beatEnd != null && !beatLed
                    && (horizontal(g.pos, beatEnd) < 0.25 || beatTicks > beatLimit - 40)) beatLed = true;
            boolean passed = ownerFloor.sub(beatArrived ? g.pos : beatSpot).horizontal().dot(beatDir) > 0.6;
            boolean ahead = g.pos.sub(ownerFloor).horizontal().dot(beatDir) > 1.0;
            if (dir == null && ahead && !blocked) {
                // They stopped with it out ahead: stop where it is, in view, rather than turn back.
                beatArrived = beatLed = true;
                return;
            }
            boolean veered = dir != null && dir.dot(beatDir) < Math.cos(Math.toRadians(40));
            if (blocked || passed || veered || beatTicks > beatLimit) {
                boolean stay = land == Land.GROUND_IDLE && ahead;
                endBeat();
                // Settled out ahead of them: that is its first idle spot, dwelt at facing them.
                if (stay) {
                    spot = g.standing();
                    spotOwner = ownerFloor;
                    spotDwell = (int) Math.round((1.5 + beatUnit() * 2.0) * TPS);
                    nextInView = false;
                    Vec toOwner = ownerFloor.sub(spot).horizontal();
                    spotFace = toOwner.lengthSqr() > 1e-6 ? (float) toOwner.yawDeg() : Float.NaN;
                }
            }
            return;
        }
        if (!down || blocked || beatCooldown > 0 || dir == null || o.sprinting()) return;
        if (straightTicks >= BEAT_STRAIGHT && unseen >= beatAfter && landTicks % 10 == 0
                && o.flatForward().dot(dir) > 0.85) {
            Beat kind = beatUnit() < (raceAhead ? 0.75 : 0.3) ? Beat.OVERTAKE : Beat.ROADSIDE;
            double lead = (1.5 + beatUnit() * 1.5) * (raceAhead ? 1.5 : 1);
            boolean planned = kind == Beat.OVERTAKE ? planBeat(kind, o, v, dir, 3.3, OVERTAKE_SIDES, lead)
                    : planBeat(kind, o, v, dir, 4.8, ROADSIDE_SIDES, 0);
            if (!planned) beatCooldown = 10;
            else if (kind == Beat.OVERTAKE) overtakes++;
            else roadsides++;
        }
    }

    void endBeat() {
        if (beat == null) return;
        beatCooldown = (int) Math.round((raceAhead ? 100 : 240) * beatCooldownScale);
        sinceBeat = 0;
        beat = null;
        beatArrived = false;
    }

    /**
     * Picks the beat's spot: {@code ahead} blocks along the owner's way past where they will be
     * when it gets there, off to the side it is already on, at the first offset whose route is
     * level, walkable and clear of their feet. An overtake then leads for {@code lead} seconds at
     * their pace, its whole way checked now. False if there is none it can reach in time.
     */
    private boolean planBeat(Beat kind, OwnerState o, LocalVoxels v, Vec dir, double ahead, double[] sides, double lead) {
        Vec vel = o.velocity().horizontal();
        Vec perp = new Vec(-dir.z(), 0, dir.x());
        double lateral = g.pos.sub(ownerFloor).horizontal().dot(perp);
        double sign = Math.abs(lateral) > 0.2 ? Math.signum(lateral)
                : Vec.fromYawPitch(g.yaw, 0).dot(perp) < 0 ? -1 : 1;
        // Below its top scurry: it has to get up to speed, and the rhythm surges and eases.
        double speed = 0.85 * g.rhythmBase(GroundBody.Rhythm.SCURRY) * 1.6;
        for (double side : sides) {
            Vec offset = dir.scale(ahead).add(perp.scale(sign * side));
            double time = -1;
            for (double t = 0.5; t <= 5.0; t += 0.25) {
                if (horizontal(g.pos, ownerFloor.add(vel.scale(t)).add(offset)) <= speed * t) {
                    time = t;
                    break;
                }
            }
            if (time < 0) continue;
            Vec s = ownerFloor.add(vel.scale(time)).add(offset);
            double floor = g.footprintFloorFrom(v, s.x(), s.z(), g.floorY);
            if (Double.isNaN(floor) || Math.abs(floor - g.floorY) > GroundBody.STEP_SMOOTH) continue;
            s = new Vec(s.x(), floor, s.z());
            // Only where their own way ahead is open and level for the whole beat: the feet check
            // assumes they walk on straight, and in a pillared cellar or up a stair they soon turn
            // or climb — into the spot it has just taken.
            if (!levelWalk(v, ownerFloor, ownerFloor.add(vel.scale(time + lead + 2.0)))) return false;
            if (!levelWalk(v, g.standing(), s) || !clearOfFeet(s, time, vel, lead)) continue;
            Vec end = null;
            if (lead > 0) {
                end = s.add(vel.scale(lead));
                if (!levelWalk(v, s, end)) continue;
            }
            beat = kind;
            beatSpot = s;
            beatEnd = end;
            beatAhead = ahead;
            beatLed = false;
            beatDir = dir;
            beatTicks = 0;
            beatArrived = false;
            beatLimit = (int) Math.ceil((time * 1.5 + lead) * TPS) + 40;
            beatFace = (float) (kind == Beat.ROADSIDE ? perp.scale(-sign) : dir).yawDeg();
            return true;
        }
        return false;
    }

    // Diagnostic ablation: package-private like beatsEnabled; production skips repeated columns.
    static boolean skipRepeatedBeatFootprints = true;
    static boolean countBeatFootprints;
    static long beatFootprintChecks;

    /** A straight walk with no step up or down at all on the way (a beat is a dash, not a climb). */
    private boolean levelWalk(LocalVoxels v, Vec a, Vec b) {
        Vec d = b.sub(a).horizontal();
        int steps = Math.max(1, (int) Math.ceil(d.length() / 0.1));
        boolean checked = false;
        int lastX0 = 0, lastX1 = 0, lastZ0 = 0, lastZ1 = 0;
        for (int i = 1; i <= steps; i++) {
            double s = i / (double) steps;
            double x = a.x() + d.x() * s, z = a.z() + d.z() * s;
            int x0 = LocalVoxels.floor(x - g.radius), x1 = LocalVoxels.floor(x + g.radius);
            int z0 = LocalVoxels.floor(z - g.radius), z1 = LocalVoxels.floor(z + g.radius);
            // footprintFloorFrom depends only on these columns and the fixed floorY. Keep every
            // original 0.1-block step, but reuse a successful answer until its footprint changes.
            if (skipRepeatedBeatFootprints && checked && x0 == lastX0 && x1 == lastX1
                    && z0 == lastZ0 && z1 == lastZ1) continue;
            checked = true;
            lastX0 = x0; lastX1 = x1; lastZ0 = z0; lastZ1 = z1;
            if (countBeatFootprints) beatFootprintChecks++;
            double f = g.footprintFloorFrom(v, x, z, g.floorY);
            if (Double.isNaN(f) || Math.abs(f - g.floorY) > GroundBody.STEP_SMOOTH) return false;
        }
        return true;
    }

    /**
     * Whether the dash to {@code s} (arriving in {@code time} s), the lead from there and the wait while the
     * owner walks past keep outside the reach of {@link #ownerPush}: it goes round their feet,
     * never through them.
     */
    private boolean clearOfFeet(Vec s, double time, Vec vel, double lead) {
        Vec from = g.standing();
        Vec leg = s.sub(from).horizontal();
        for (double t = 0; t <= time + lead + 2.0; t += 0.05) {
            Vec at = t < time ? from.add(leg.scale(t / time)) : s.add(vel.scale(Math.min(t - time, lead)));
            Vec feet = ownerFloor.add(vel.scale(t));
            if (horizontal(at, feet) < 0.95 || horizontal(at, feet.add(vel.scale(0.35))) < 1.05
                    || horizontal(at, feet.add(vel.scale(0.8))) < 1.35) return false;
        }
        return true;
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
        int newest = via >= 0 ? Math.min(via, trail.size()) : trail.size();
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

    private double beatUnit() {
        long x = beatRng;
        x ^= x << 13;
        x ^= x >>> 7;
        x ^= x << 17;
        beatRng = x;
        return (x >>> 11) * 0x1.0p-53;
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
