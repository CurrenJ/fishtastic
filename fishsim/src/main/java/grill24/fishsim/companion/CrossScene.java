package grill24.fishsim.companion;

/**
 * A cross-player scene as one party plays it (docs/fish-companions-party.md §28.2): G1's ring or
 * G2's figure-eight, between this party's unit and another player's. The server names the pair and
 * the start tick; whoever runs the party (the game's client, or {@code TwoOwnerRun} headless) builds
 * one of these per side and feeds it every tick with {@link #update}: the ticks since the shared
 * start, both owners' feet in the packet's order, and where the partner unit's leader is.
 *
 * <p>The path comes from the shared clock and the two owners only, never from the partner fish
 * (D29): two clients compute the same targets, so each view is self-consistent even where their
 * sims differ. Role 0 is the packet's first companion, role 1 its second: half a lap on.
 *
 * <p>The scene ends itself here (D30): its time is up, an owner walked off or went more than
 * {@link #LEAVE_DISTANCE} from where it began, or the caller {@link #cancel}s it (the partner is
 * gone, the party was rebuilt). The fish may also leave it early (a wall, no room); see {@code Mind}.
 */
public final class CrossScene {

    /** The scene's shape, with its length on the shared clock (CompanionSceneType's durations). */
    public enum Kind {
        /** G1, one species: a horizontal ring above the pair (D22), the two fish opposite each other. */
        RING(240),
        /** G2, two free swimmers: two tangent lobes, one round each owner (D23), half a lap apart. */
        EIGHT(320);

        public final int durationTicks;

        Kind(int durationTicks) {
            this.durationTicks = durationTicks;
        }
    }

    /**
     * The greeting that opens every scene (D27), ticks: both fish swim to the scene's meeting point
     * (G1's ring centre, G2's crossing) and meet nose to nose; the path's clock starts as it ends.
     */
    public static final int GREET_TICKS = 50;
    /** G1's closing linger, back to the centre and nose to nose again, ticks. */
    public static final int LINGER_TICKS = 20;
    /** The longest a G2 scene runs to finish one whole eight with its owners far apart, ticks. */
    public static final int EIGHT_MAX_TICKS = 30 * 20;
    /** Path speed, blocks/s (D24: about 1.3× a swimmer's cruise of 1.1). Both roles share it. */
    public static final double PATH_SPEED = 1.43;
    /** How far ahead on the path a fish aims, s. */
    public static final double LEAD_SECONDS = 0.4;
    /** G1's ring: radius, height above the owners' eyes, the smallest it may shrink to. */
    public static final double RING_RADIUS = 1.1, RING_ABOVE_EYE = 0.6, RING_MIN_RADIUS = 0.9;
    /** G1: horizontal room the ring keeps from an owner's axis (it passes above their heads). */
    public static final double RING_OWNER_CLEARANCE = 0.5;
    /** G2: the lobes' floor round an owner (body + a fish + margin), and their height below the eyes. */
    public static final double EIGHT_MIN_RADIUS = OwnerState.BODY_RADIUS + 0.15 + 0.4, EIGHT_BELOW_EYE = 0.25;
    /** G2: how far above (role 0) or below (role 1) the crossing each fish passes (D23). */
    public static final double EIGHT_CROSS_SPLIT = 0.35;
    /** An owner this far from where the scene began has left it (D30), blocks. */
    public static final double LEAVE_DISTANCE = 9.0;
    /** An owner walking this long ends it (D30), ticks; and the speed that counts as walking, blocks/s. */
    public static final int WALK_OFF_TICKS = 30;
    public static final double WALKING_SPEED = 1.0;

    public final Kind kind;
    public final int role;
    /** The server's length; G2 runs on to finish one whole eight ({@link #endTicks}). */
    public final int durationTicks;
    /** When this side's scene ends, ticks on the shared clock: set on the first update. */
    private int endTicks;

    /** Ticks since the shared start (this tick). */
    int ticks = -1;
    /** Both owners' feet, in the packet's order. */
    Vec firstFeet, secondFeet;
    /** The partner unit's leader, or null while it isn't seen. */
    Vec partner;

    private Vec startCentre, prevFirst, prevSecond;
    private int firstWalking, secondWalking;
    private String endReason;

    public CrossScene(Kind kind, int role) {
        this.kind = kind;
        this.role = role;
        this.durationTicks = kind.durationTicks;
    }

    /**
     * This tick's inputs. {@code ticksSinceStart} is the shared clock's (the game's time minus the
     * packet's start), so a late packet joins mid-path; a scene already over when it arrives ends.
     */
    public void update(int ticksSinceStart, Vec firstFeet, Vec secondFeet, Vec partner) {
        this.ticks = ticksSinceStart;
        this.firstFeet = firstFeet;
        this.secondFeet = secondFeet;
        this.partner = partner;
        if (endReason != null) return;
        if (startCentre == null) startCentre = firstFeet.lerp(secondFeet, 0.5);
        // G2 runs one whole eight, each fish round both owners, however far apart they stand (D24).
        // The server starts a scene as an owner walks into range, so the length is judged from where
        // they stand until the greeting ends, then kept.
        if (ticksSinceStart <= GREET_TICKS || endTicks == 0) {
            endTicks = durationTicks;
            if (kind == Kind.EIGHT) {
                double eight = 4 * Math.PI * lobeRadius(secondFeet.sub(firstFeet).horizontalLength());
                endTicks = Math.min(EIGHT_MAX_TICKS, Math.max(durationTicks, GREET_TICKS + (int) Math.ceil(eight / PATH_SPEED * 20)));
            }
        }
        if (ticksSinceStart >= endTicks) {
            end("done");
            return;
        }
        if (firstFeet.distanceTo(startCentre) > LEAVE_DISTANCE || secondFeet.distanceTo(startCentre) > LEAVE_DISTANCE) {
            end("owner left");
            return;
        }
        firstWalking = walking(prevFirst, firstFeet) ? firstWalking + 1 : 0;
        secondWalking = walking(prevSecond, secondFeet) ? secondWalking + 1 : 0;
        prevFirst = firstFeet;
        prevSecond = secondFeet;
        if (firstWalking >= WALK_OFF_TICKS || secondWalking >= WALK_OFF_TICKS) end("owner walked off");
    }

    private static boolean walking(Vec prev, Vec now) {
        return prev != null && now.sub(prev).horizontalLength() * 20 > WALKING_SPEED;
    }

    /** The caller ends it: the partner is gone, or the party was rebuilt. */
    public void cancel(String reason) {
        end(reason);
    }

    void end(String reason) {
        if (endReason == null) endReason = reason;
    }

    /** Fed at least once and not over. */
    public boolean active() {
        return endReason == null && ticks >= 0;
    }

    /** Why it ended, or null while it runs. */
    public String endReason() {
        return endReason;
    }

    public int ticks() {
        return ticks;
    }

    /** In the opening greeting (by the shared clock). */
    public boolean greeting() {
        return ticks < GREET_TICKS;
    }

    /** In G1's closing linger. */
    boolean lingering() {
        return kind == Kind.RING && ticks >= endTicks - LINGER_TICKS;
    }

    /** When it ends on the shared clock (0 before the first update). */
    public int endTicks() {
        return endTicks;
    }

    /**
     * Where the fish meet (the greeting, and G1's linger): G1's ring centre, G2's crossing. A fish
     * holds off it on its own side, so the two meet nose to nose without a partner position.
     */
    public Vec meetingPoint() {
        if (kind == Kind.RING) return ringCentre();
        double y = (firstFeet.y() + secondFeet.y()) / 2 + OwnerState.EYE_HEIGHT - EIGHT_BELOW_EYE;
        Vec mid = firstFeet.lerp(secondFeet, 0.5);
        return new Vec(mid.x(), y, mid.z());
    }

    // ── The path ───────────────────────────────────────────────────────────────────────────────

    /**
     * Where this role should be at shared-clock tick {@code tick} (fractional), from the owners as
     * they stand now. The path's own clock starts when the greeting ends, from the meeting point's
     * side: G2's two fish leave the crossing together, one round each owner.
     */
    public Vec pathPoint(double tick) {
        double pathTick = tick - GREET_TICKS;
        return kind == Kind.RING ? ringPoint(pathTick) : eightPoint(pathTick);
    }

    /**
     * Which way from the meeting point this role waits during the greeting (a unit vector): G1
     * toward where its ring begins; G2 back along the path from the crossing it sets out over,
     * a layer above or below the partner's. The same on every client.
     */
    public Vec startSide() {
        Vec meet = meetingPoint();
        if (kind == Kind.RING) {
            Vec out = pathPoint(GREET_TICKS).sub(meet).horizontal();
            return out.lengthSqr() < 1e-9 ? new Vec(1, 0, 0) : out.normalize();
        }
        Vec back = pathPoint(GREET_TICKS - 8).sub(meet);
        return back.lengthSqr() < 1e-9 ? new Vec(1, 0, 0) : back.normalize();
    }

    /** G1's ring radius for owners {@code d} apart: 1.1, unless that passes over an owner (then inside, else outside). */
    static double ringRadius(double d) {
        double half = d / 2;
        if (Math.abs(RING_RADIUS - half) >= RING_OWNER_CLEARANCE) return RING_RADIUS;
        double inside = half - RING_OWNER_CLEARANCE;
        return inside >= RING_MIN_RADIUS ? inside : half + RING_OWNER_CLEARANCE;
    }

    /** The ring's centre: the owners' midpoint, above their eyes. */
    public Vec ringCentre() {
        Vec mid = firstFeet.lerp(secondFeet, 0.5);
        return mid.add(0, OwnerState.EYE_HEIGHT + RING_ABOVE_EYE, 0);
    }

    private Vec ringPoint(double tick) {
        Vec axis = secondFeet.sub(firstFeet).horizontal();
        double d = axis.length();
        double r = ringRadius(d);
        double base = d < 1e-6 ? 0 : Math.atan2(axis.z(), axis.x());
        double angle = base + PATH_SPEED * (tick / 20.0) / r + role * Math.PI;
        return ringCentre().add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
    }

    /** G2's lobe radius for owners {@code d} apart: half the gap, never under the floor. */
    static double lobeRadius(double d) {
        return Math.max(d / 2, EIGHT_MIN_RADIUS);
    }

    private Vec eightPoint(double tick) {
        Vec axis = secondFeet.sub(firstFeet).horizontal();
        double d = axis.length();
        Vec u = d < 1e-6 ? new Vec(1, 0, 0) : axis.scale(1 / d);
        Vec n = new Vec(-u.z(), 0, u.x());
        double r = lobeRadius(d);
        double lobe = 2 * Math.PI * r, length = 2 * lobe;
        double s = (PATH_SPEED * (tick / 20.0) + role * lobe) % length;
        if (s < 0) s += length;
        double y = (firstFeet.y() + secondFeet.y()) / 2 + OwnerState.EYE_HEIGHT - EIGHT_BELOW_EYE;
        Vec centre, radial;
        double fromCrossing;
        if (s < lobe) {
            // Round the second owner, counter-clockwise from the crossing.
            double phi = Math.PI + s / r;
            centre = secondFeet;
            radial = u.scale(Math.cos(phi)).add(n.scale(Math.sin(phi)));
            fromCrossing = Math.min(s, lobe - s) / r;
        } else {
            // Round the first owner, clockwise from the crossing: the tangent carries straight through.
            double phi = -(s - lobe) / r;
            centre = firstFeet;
            radial = u.scale(Math.cos(phi)).add(n.scale(Math.sin(phi)));
            fromCrossing = Math.min(s - lobe, length - s) / r;
        }
        double split = fromCrossing < Math.PI / 2 ? EIGHT_CROSS_SPLIT * (1 + Math.cos(2 * fromCrossing)) / 2 : 0;
        Vec p = new Vec(centre.x(), y, centre.z()).add(radial.scale(r));
        return p.add(0, role == 0 ? split : -split, 0);
    }

    /** Open water along the whole path for a body of {@code radius}: else the fish only greets. */
    boolean roomClear(LocalVoxels v, double radius) {
        double lap = kind == Kind.RING ? 2 * Math.PI * ringRadius(secondFeet.sub(firstFeet).horizontalLength())
                : 4 * Math.PI * lobeRadius(secondFeet.sub(firstFeet).horizontalLength());
        double lapTicks = lap / PATH_SPEED * 20;
        for (int k = 0; k < 24; k++) {
            if (!v.sphereClear(pathPoint(ticks + lapTicks * k / 24.0), radius + 0.1)) return false;
        }
        return true;
    }
}
