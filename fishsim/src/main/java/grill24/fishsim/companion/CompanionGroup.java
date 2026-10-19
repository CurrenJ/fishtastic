package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;

/**
 * A school of two or three same-species free swimmers (docs/fish-companions-party.md §3; first
 * built as the automatic trio of spec §2, docs/fish-companions-sim-plan.md §3.1): a leader
 * with the full {@link CompanionAgent} mind, and two followers that keep loosely to it by local
 * rules — a station behind and beside the leader in its own frame, separation from the others,
 * and room for the owner. The followers swim the same heading-first {@link Body} as any free
 * swimmer, so they never swim backwards, turn under the same caps and stop for walls the same way;
 * where a wall stands between a follower and its leader, it follows the leader's own trail.
 *
 * <p>Fades travel together: when the leader fades (a teleport, the owner outrunning it), so do the
 * followers, and they come back beside it. A follower left far behind fades out and rejoins on its
 * own.
 */
public final class CompanionGroup {

    /**
     * The old automatic trio limit (spec §2). A school now comes from the party
     * (docs/fish-companions-party.md §3, D8: any size schools); see {@link PartyGrouping}.
     */
    @Deprecated
    public static final float TRIO_MAX_SIZE_CM = 20f;

    /** @deprecated groups come from same-species party members now ({@link PartyGrouping#group}). */
    @Deprecated
    public static boolean isTrio(Locomotion locomotion, float sizeCm) {
        return locomotion == Locomotion.FREE_SWIM && sizeCm < TRIO_MAX_SIZE_CM;
    }

    private static final int FADE_TICKS = 8;
    /** Station behind the leader and to each side, blocks, scaled by body length. */
    private static final double STATION_BACK = 2.2, STATION_SIDE = 1.8, STATION_LOW = 0.15;
    /** How hard a follower makes for its station, 1/s, and the fastest it will ask to close in. */
    private static final double STATION_GAIN = 1.6;
    /** Separation: closer than this many body lengths apart, they push off each other. */
    private static final double SEPARATION_LENGTHS = 1.2;
    private static final double SEPARATION_SPEED = 2.0;
    /** Separation grows with the follower's own speed, like the push off walls: at least this x speed. */
    private static final double SEPARATION_SPEED_SCALE = 1.2;
    /** Off station by more than this, blocks, a follower may sprint to rejoin: up to this x top speed. */
    private static final double SPRINT_GAP = 2.0, SPRINT_BOOST = 1.4;
    /**
     * A follower this close to its owner's body (blocks from the surface), in water open this far
     * every way at its own height, routes round them to its station instead of braking into them.
     */
    private static final double ROUND_OWNER_RANGE = 0.7, OPEN_REACH = 2.5;
    /** Beyond this, blocks, a follower has been left behind: it fades and rejoins. */
    private static final double LOST = 14.0;

    private final CompanionTunables t;
    private final CompanionAgent leader;
    private final Follower[] followers;
    private final Breadcrumbs leaderTrail = new Breadcrumbs();
    private final LocalVoxels voxels = new LocalVoxels();
    /** The leader's body length, blocks; each follower has its own. */
    private final float length;

    public CompanionGroup(CompanionTunables t, long seed, float length, CompanionWorld world) {
        this(t, seed, length, Temperament.BASELINE, world);
    }

    /** A trio of three identical fish whose leader has the species' temperament (the followers keep to the leader). */
    public CompanionGroup(CompanionTunables t, long seed, float length, Temperament temperament, CompanionWorld world) {
        this(t, seed, new float[]{length, length, length}, temperament, world);
    }

    /**
     * A school of two or three members of one species (docs/fish-companions-party.md §3). {@code
     * lengths[0]} is the leader's body length (the member in the lowest party slot); the rest are the
     * followers', in slot order, and each may differ (D8). {@code seed} is the leader's: the
     * followers' randomness comes from it and their index, exactly as the trio's always did, so
     * three identical members are bitwise today's trio.
     */
    public CompanionGroup(CompanionTunables t, long seed, float[] lengths, Temperament temperament, CompanionWorld world) {
        if (lengths.length < 2 || lengths.length > 3) throw new IllegalArgumentException("a school has 2 or 3 members");
        OwnerState owner = world.owner();
        BlockQuery blocks = world.blocks();
        this.t = t;
        this.length = lengths[0];
        this.leader = new CompanionAgent(t, seed, Locomotion.FREE_SWIM, length, temperament, world);
        voxels.update(blocks, owner.eye());
        followers = new Follower[lengths.length - 1];
        for (int i = 0; i < followers.length; i++) {
            int side = i == 0 ? 1 : -1;
            float own = lengths[i + 1];
            double radius = Math.max(0.08, Math.min(0.3, own * 0.3));
            Vec start = stationBesideLeader(side, radius, pairLength(length, own));
            followers[i] = new Follower(new Body(t, Locomotion.FREE_SWIM, start, leader.yaw(), radius,
                    (seed * 31 + i + 1) ^ 0x5DEECE66DL), side, seed * 977 + i, own);
        }
    }

    /** The spacing length for two members: the mean of their body lengths (exactly {@code a} when equal). */
    static double pairLength(float a, float b) {
        return ((double) a + b) * 0.5;
    }

    public void step(CompanionWorld world) {
        OwnerState owner = world.owner();
        BlockQuery blocks = world.blocks();
        leader.step(world);
        voxels.update(blocks, owner.eye());
        if (owner.teleported() || leader.fade() == CompanionAgent.Fade.HIDDEN) leaderTrail.clear();
        leaderTrail.record(leader.position());
        Capsule ownerBody = Capsule.ownerBody(owner);
        for (Follower f : followers) f.step(owner, ownerBody);
    }

    public void interpolate(float alpha) {
        leader.interpolate(alpha);
        for (Follower f : followers) f.interpolate(alpha);
    }

    public CompanionAgent leader() { return leader; }

    public Follower follower(int i) { return followers[i]; }

    /** Followers: 1 for a pair, 2 for a trio. */
    public int followerCount() { return followers.length; }

    /** The leader's body length, blocks. */
    public float leaderLength() { return length; }

    /** Forgets every sampled block, the leader's and the group's — call after a block change nearby. */
    public void invalidateBlocks() {
        leader.voxels().invalidate();
        voxels.invalidate();
    }

    /** A spot on the leader's {@code side}, behind it, clear of blocks — or the leader itself. */
    private Vec stationBesideLeader(int side, double radius, double pair) {
        Vec p = station(side, 0, pair);
        return voxels.sphereClear(p, radius + 0.05) ? p : leader.position();
    }

    /**
     * Where a follower on {@code side} rides, in the leader's frame, with {@code drift} applied,
     * spaced by {@code pair}: the mean of the leader's and the follower's body lengths.
     */
    private Vec station(int side, double drift, double pair) {
        Vec fwd = Vec.fromYawPitch(leader.yaw(), 0);
        Vec right = fwd.cross(Vec.UP);
        double back = STATION_BACK * pair + 0.15 * drift;
        double sideways = STATION_SIDE * pair * (1 + 0.25 * drift);
        return leader.position().sub(fwd.scale(back)).add(right.scale(side * sideways)).add(0, -STATION_LOW * pair, 0);
    }

    /** One follower: a free-swim body that keeps to a station on its leader. */
    public final class Follower {
        private final Body body;
        private final int side;
        /** This follower's own body length, and its spacing length to the leader. */
        private final float length;
        private final double pairToLeader;
        private long rng;
        private float drift;
        private float appear = 1f;
        private boolean fadingOut;
        long leashEvents;

        private Vec prevPos;
        private float prevYaw, prevPitch, prevBank, prevTail, prevAppear = 1f;
        private Vec renderPos;
        private float renderYaw, renderPitch, renderBank, renderTail, renderAppear = 1f;

        Follower(Body body, int side, long seed, float length) {
            this.body = body;
            this.side = side;
            this.length = length;
            this.pairToLeader = pairLength(CompanionGroup.this.length, length);
            this.rng = seed == 0 ? 1 : seed * 0x9E3779B97F4A7C15L;
            this.prevPos = renderPos = body.pos;
            this.prevYaw = renderYaw = body.yaw;
        }

        void step(OwnerState owner, Capsule ownerBody) {
            prevPos = body.pos;
            prevYaw = body.yaw;
            prevPitch = body.pitch;
            prevBank = body.bank;
            prevTail = body.tailPhase;
            prevAppear = appear;

            CompanionAgent.Fade lf = leader.fade();
            if (lf == CompanionAgent.Fade.HIDDEN || (lf != CompanionAgent.Fade.NONE && leader.appear() == 0f)) {
                // The leader is gone for now: so is the follower, waiting beside where it'll come back.
                appear = leader.appear();
                place();
                return;
            }
            if (lf != CompanionAgent.Fade.NONE) {
                appear = Math.min(appear, leader.appear());
                if (lf == CompanionAgent.Fade.IN) appear = leader.appear();
            }

            double toLeader = body.pos.distanceTo(leader.position());
            if (!fadingOut && toLeader > LOST) fadingOut = true;
            if (fadingOut) {
                appear = Math.max(0f, appear - 1f / FADE_TICKS);
                if (appear == 0f) {
                    leashEvents++;
                    place();
                    fadingOut = false;
                }
            } else if (lf == CompanionAgent.Fade.NONE && appear < 1f) {
                appear = Math.min(1f, appear + 1f / FADE_TICKS);
            }

            drift = ou(drift);
            Vec target = station(side, drift, pairToLeader);
            Vec dest = target;
            if (!voxels.segmentClear(body.pos, leader.position(), body.radius + 0.1)) {
                // A wall between it and the leader: go the way the leader went.
                Vec crumb = leaderTrail.newestReachable(body.pos, voxels, body.radius + 0.1);
                if (crumb != null) dest = crumb;
            } else if (!voxels.sphereClear(target, body.radius + 0.1)) {
                // No room for the station (a tunnel): fall in behind the leader instead.
                Vec behind = leaderTrail.trailPoint(leader.position(), STATION_BACK * pairToLeader * 1.5);
                if (behind != null) dest = behind;
            }
            // Pressed against its owner with them between it and its station: round their side, as
            // the leader would, rather than braking into their back. Only in open water — between
            // walls the trail already leads it, and a waypoint beside the owner can be in a wall.
            if (Capsule.ownerBody(owner).distance(body.pos) < ROUND_OWNER_RANGE && openAround(body.pos, body.radius)) {
                dest = Mind.aroundOwner(owner, voxels, body.pos, dest, body.radius);
            }
            // Small schooling fish dash to rejoin: well off station, it may outswim the leader.
            boolean behind = body.pos.distanceTo(target) > SPRINT_GAP;
            body.speedBoost = behind ? SPRINT_BOOST : 1;
            double pullCap = t.catchUpSpeed * (behind ? SPRINT_BOOST : 1);
            Vec pull = dest.sub(body.pos).scale(STATION_GAIN).clampLength(pullCap);
            Vec desired = leader.velocity().add(pull).add(separation());
            body.step(desired, 0.6, ownerAvoidance(owner), voxels, ownerBody);
        }

        /** Open water round {@code p}: a clear swim of {@link #OPEN_REACH} blocks every way, at its own height. */
        private boolean openAround(Vec p, double radius) {
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4;
                if (!voxels.segmentClear(p, p.add(Math.cos(a) * OPEN_REACH, 0, Math.sin(a) * OPEN_REACH), radius)) return false;
            }
            return true;
        }

        /**
         * Push away from the leader and the other follower when closer than a comfortable spacing,
         * each pair spaced by the mean of its two body lengths.
         */
        private Vec separation() {
            Vec push = Vec.ZERO;
            push = push.add(repel(leader.position(), SEPARATION_LENGTHS * pairToLeader + 2 * body.radius));
            for (Follower o : followers) {
                if (o != this) push = push.add(repel(o.body.pos,
                        SEPARATION_LENGTHS * pairLength(length, o.length) + 2 * body.radius));
            }
            return push;
        }

        private Vec repel(Vec other, double range) {
            Vec away = body.pos.sub(other);
            double d = away.length();
            if (d >= range || d < 1e-6) return Vec.ZERO;
            double w = 1 - d / range;
            double gain = Math.max(SEPARATION_SPEED, body.speed * SEPARATION_SPEED_SCALE);
            return away.scale(1 / d).scale(gain * w);
        }

        /** The same soft room the leader gives the owner's body. */
        private Vec ownerAvoidance(OwnerState o) {
            Capsule c = Capsule.ownerBody(o);
            Vec q = c.nearest(body.pos);
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

        private void place() {
            Vec p = stationBesideLeader(side, body.radius, pairToLeader);
            body.placeAt(p, leader.yaw());
            prevPos = p;
            prevYaw = body.yaw;
            prevPitch = body.pitch;
            prevBank = body.bank;
        }

        void interpolate(float alpha) {
            renderPos = prevPos.lerp(body.pos, alpha);
            renderYaw = prevYaw + Body.wrap(body.yaw - prevYaw) * alpha;
            renderPitch = prevPitch + (body.pitch - prevPitch) * alpha;
            renderBank = prevBank + (body.bank - prevBank) * alpha;
            renderAppear = prevAppear + (appear - prevAppear) * alpha;
            float dTail = body.tailPhase - prevTail;
            if (dTail < 0) dTail += (float) (Math.PI * 2);
            renderTail = prevTail + dTail * alpha;
        }

        private float ou(float x) {
            float next = x - x * 0.3f * (float) Body.DT + 0.45f * (float) Math.sqrt(Body.DT) * 1.7320508f * signed();
            return Body.clamp(next, -1f, 1f);
        }

        private float signed() {
            long x = rng;
            x ^= x << 13;
            x ^= x >>> 7;
            x ^= x << 17;
            rng = x;
            return ((x >>> 40) * (1f / 8388608f)) - 1f;
        }

        public Vec position() { return body.pos; }
        public Vec velocity() { return body.vel; }
        public Vec acceleration() { return body.acc; }
        public float yaw() { return body.yaw; }
        public float pitch() { return body.pitch; }
        public float lastTurnDeg() { return body.yawRate; }
        public boolean backstopLastStep() { return body.backstopLastStep; }
        public double lastImpact() { return body.lastImpact; }
        public long backstops() { return body.backstops; }
        public double radius() { return body.radius; }
        /** This follower's own body length, blocks. */
        public float length() { return length; }
        public float appear() { return appear; }
        public long leashEvents() { return leashEvents; }

        public Vec renderPos() { return renderPos; }
        public float renderYaw() { return renderYaw; }
        public float renderPitch() { return renderPitch; }
        public float renderBank() { return renderBank; }
        public float renderAppear() { return renderAppear; }
        public float renderTailPhase() { return renderTail; }
        public float speedFactor() { return body.speedFactor(); }
    }
}
