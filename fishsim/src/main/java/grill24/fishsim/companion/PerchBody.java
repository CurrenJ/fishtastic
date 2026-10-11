package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;

import java.util.function.Supplier;

/**
 * A percher's body — crabs and the like (BENTHIC), garden eels (ANCHORED): it rides on its owner
 * and gets about by hopping (docs/fish-companions-sim-plan.md §3.4, spec §3).
 *
 * <ul>
 *   <li><b>Perched</b> is a real attachment, the parrot pattern: the body sits at a slot on the
 *       owner (a shoulder or the head, or for an anchored fish the back) and goes wherever they
 *       go, teleports included. This is the one placement the plan allows that isn't a swim.</li>
 *   <li><b>Hops</b> are ballistic: a crouch, a launch solved to land where the target will be
 *       when it comes down, bounded air control to correct for the owner changing course, and a
 *       short settle on landing. Crouch and flight are paced by the body's size.</li>
 *   <li>A benthic percher shows itself by hopping onto the owner's hand (in view, low right), and
 *       goes there when they look down; now and then it changes shoulders. An anchored one stays
 *       planted.</li>
 *   <li><b>In water</b> both hop off and swim with the free-swim body under the {@link Mind}
 *       (spec E1), then hop back on once the owner is out.</li>
 * </ul>
 */
public final class PerchBody extends Motor {

    public enum Mode { PERCHED, CROUCH, HOP, SETTLE, SWIM }

    public enum Slot {
        RIGHT_SHOULDER, LEFT_SHOULDER, HEAD, BACK, RIGHT_HAND, LEFT_HAND;

        public boolean hand() {
            return this == RIGHT_HAND || this == LEFT_HAND;
        }
    }

    private static final int TPS = 20;
    /** Hop gravity, blocks/s² — a lively cartoon hop, not a fall. */
    static final double GRAVITY = 22;
    /** Steering available in flight, blocks/s². */
    static final double AIR_CONTROL = 10;
    /** Longest hop, blocks. */
    static final double HOP_RANGE = 3.5;
    private static final int SETTLE_TICKS = 4;
    /** How close a hop's arc may pass the camera once off the body, blocks beyond its radius (the lens is 0.05). */
    private static final double CAMERA_CLEARANCE = 0.1;
    private static final int ARC_SAMPLES = 16;
    /** Ticks the owner's height must hold before a hop, and ticks after a mid-air bump before remounting. */
    private static final int STEADY_TICKS = 10, REMOUNT_COOLDOWN = 20;
    /** Change in the owner's horizontal velocity per tick, blocks/s, above which they aren't steady. */
    private static final double STEADY_DV = 0.1;
    /** The first and last part of a hop, seconds, during which it is still leaving or landing on the owner. */
    private static final double ON_OWNER_SECONDS = 0.12;
    /** Ticks of water at the owner's chest before hopping off, and of dry land before hopping back. */
    private static final int WATER_TICKS = 10, DRY_TICKS = 20;
    /** Owner pitch, degrees, that counts as looking down, and ticks held. */
    private static final float LOOK_DOWN_PITCH = 50f;
    private static final int LOOK_DOWN_TICKS = 15;

    /** Curiosity at which a percher takes notice: a held item draws it to the hand, anything else a look. */
    static final float WATCH_CURIOSITY = 0.3f, HAND_CURIOSITY = 0.45f;

    private final CompanionTunables t;
    private final Drives drives;
    final Body swim;
    private long rng;
    /** Size pacing: 1 at 0.3 blocks long, slower for bigger bodies. */
    private final double pace;

    Mode mode = Mode.PERCHED;
    int modeTicks;
    Slot home;
    Slot slot;
    private Slot hopTarget;
    private Vec waterTarget;
    private double hopTime, hopElapsed;
    private boolean launchedFromOwner;
    /** Where the body sat relative to the slot when it landed, decaying over the settle. */
    private Vec settleOffset = Vec.ZERO;
    /** 0..1 squash of the crouch and the landing, for the animator. */
    float crouch;

    // Drives.
    int unseenTicks;
    private int attentionThreshold;
    private int handTicks, wanderTicks, lookDownTicks, wetTicks, dryTicks, steadyTicks;
    private Vec lastOwnerVel = Vec.ZERO;
    int hops;

    PerchBody(CompanionTunables t, Locomotion locomotion, Body swim, OwnerState owner, double radius, float length,
              long seed, Drives drives) {
        super(locomotion, Gait.of(Locomotion.FREE_SWIM, t), Vec.ZERO, radius);
        this.t = t;
        this.drives = drives;
        this.swim = swim;
        this.rng = seed * 0x9E3779B97F4A7C15L + 0x2545F4914F6CDD1DL;
        if (rng == 0) rng = 1;
        this.pace = Math.sqrt(Math.max(0.15, length) / 0.3);
        this.home = locomotion == Locomotion.ANCHORED ? Slot.BACK
                : switch ((int) Math.floorMod(seed, 3L)) {
                    case 0 -> Slot.RIGHT_SHOULDER;
                    case 1 -> Slot.LEFT_SHOULDER;
                    default -> Slot.HEAD;
                };
        this.slot = home;
        this.attentionThreshold = drawTicks(t.attentionMinSeconds, t.attentionMaxSeconds);
        this.wanderTicks = drawTicks(20, 45);
        attach(owner);
    }

    /** Whether it rides the owner this tick (perched, crouching or settling). */
    boolean attached() {
        return mode == Mode.PERCHED || mode == Mode.CROUCH || mode == Mode.SETTLE;
    }

    /**
     * Whether it is on its owner's body this tick: riding, or at the very start or end of a hop
     * between slots, where it is still pushing off or touching down.
     */
    boolean onOwner() {
        if (attached()) return true;
        if (mode != Mode.HOP) return false;
        boolean fromOwner = launchedFromOwner && hopElapsed < ON_OWNER_SECONDS;
        boolean toOwner = hopTarget != null && hopTime - hopElapsed < ON_OWNER_SECONDS;
        return fromOwner || toOwner;
    }

    @Override
    void step(Vec desired, double wanderScale, Vec extraAvoid, LocalVoxels voxels, Capsule body) {
        throw new UnsupportedOperationException("a percher steps with the owner: use step(owner, ...)");
    }

    /**
     * One tick. {@code swimIntent} is the mind's desired velocity, asked for only while swimming;
     * {@code seen} whether the owner can see the body this tick.
     */
    void step(OwnerState o, LocalVoxels v, boolean seen, Supplier<Vec> swimIntent, Supplier<Vec> ownerAvoid,
              Runnable onSwimStart) {
        modeTicks++;
        Vec before = vel;
        Mode was = mode;
        updateDrives(o, seen);
        switch (mode) {
            case PERCHED -> {
                attach(o);
                crouch = Math.max(0f, crouch - 0.15f);
                Slot next = wantedSlot(o, v);
                if (next != null) startCrouch(next);
            }
            case CROUCH -> {
                attach(o);
                crouch = Math.min(1f, crouch + 1f / crouchTicks());
                if (modeTicks >= crouchTicks()) launch(o, v);
            }
            case HOP -> fly(o, v);
            case SETTLE -> {
                settleOffset = settleOffset.scale(1 - 1.0 / SETTLE_TICKS);
                attach(o);
                pos = pos.add(settleOffset);
                crouch = Math.max(0f, crouch - 0.25f);
                if (modeTicks >= SETTLE_TICKS) enter(Mode.PERCHED);
            }
            case SWIM -> {
                swim.step(swimIntent.get(), 1, ownerAvoid.get(), v, Capsule.ownerBody(o));
                copyFrom(swim);
                if (dryTicks >= DRY_TICKS && !o.inWater() && ownerSteady() && modeTicks >= REMOUNT_COOLDOWN) {
                    Slot target = home;
                    Vec to = slotPoint(o, target);
                    double T = pos.distanceTo(to) < HOP_RANGE ? clearHopTime(v, pos, to, o) : -1;
                    if (T > 0) {
                        hopTarget = target;
                        launchFrom(o, pos, to, T);
                    }
                }
                return;
            }
        }
        if (mode != Mode.SWIM) acc = vel.sub(before).scale(1 / DT);
        if (mode == Mode.SWIM && was != Mode.SWIM) onSwimStart.run();
    }

    // ── Deciding where to be ───────────────────────────────────────────────────────────────────

    private void updateDrives(OwnerState o, boolean seen) {
        unseenTicks = seen ? 0 : unseenTicks + 1;
        lookDownTicks = o.pitchDeg() > LOOK_DOWN_PITCH ? lookDownTicks + 1 : 0;
        // Steady: not climbing or falling, and not speeding up, slowing or turning. A hop is aimed
        // at where the owner will be, assuming they keep going as they are.
        Vec dv = o.velocity().horizontal().sub(lastOwnerVel.horizontal());
        boolean changing = Math.abs(o.velocity().y()) > 0.5 || dv.length() > STEADY_DV;
        steadyTicks = changing ? 0 : steadyTicks + 1;
        lastOwnerVel = o.velocity();
        wetTicks = o.inWater() ? wetTicks + 1 : 0;
        dryTicks = o.inWater() ? 0 : dryTicks + 1;
        if (slot.hand() && mode == Mode.PERCHED) handTicks++;
        if (wanderTicks > 0) wanderTicks--;
    }

    /** The slot it wants to hop to from its perch, or null to stay. */
    private Slot wantedSlot(OwnerState o, LocalVoxels v) {
        if (wetTicks >= WATER_TICKS) {
            startWaterHop(o, v);
            return null;
        }
        if (locomotion == Locomotion.ANCHORED || modeTicks < 10) return null;
        // Something alarming: over to cover, and stay there until it's passed.
        Slot cover = coverSlot(o);
        if (cover != null) return slot == cover ? null : cover;
        boolean lookingDown = lookDownTicks >= LOOK_DOWN_TICKS;
        // Something interesting in the owner's hand: over to it, and stay while it holds the interest.
        Drives.Interest it = drives.target;
        boolean heldInterest = it != null && it.isHeld() && drives.curiosity >= HAND_CURIOSITY;
        if (slot.hand()) {
            // Back home once it has been seen a while and the owner isn't looking down at it.
            if (!lookingDown && !heldInterest && handTicks >= drawTicksOnce()) return home;
            return null;
        }
        if (lookingDown || heldInterest) return hand();
        if (unseenTicks >= attentionThreshold) return hand();
        // Resting, it stays on its shoulder rather than changing sides (it still shows itself now and then).
        if (wanderTicks == 0 && !drives.resting) {
            Temperament tm = drives.temperament();
            double k = tm.decisions() * (1 + 2 * tm.stillness());
            wanderTicks = k == 1 ? drawTicks(20, 45) : drawTicks(20 * k, 45 * k);
            return home == Slot.RIGHT_SHOULDER ? Slot.LEFT_SHOULDER : home == Slot.LEFT_SHOULDER
                    ? Slot.RIGHT_SHOULDER : (unit() < 0.5 ? Slot.RIGHT_SHOULDER : Slot.LEFT_SHOULDER);
        }
        return null;
    }

    /** Wariness at which a percher takes cover. */
    static final float COVER_WARINESS = 0.4f;

    /**
     * Where to take cover from what it's wary of, or null if nothing is: the head from a cat
     * (spec §6, "flee onto your head"), otherwise the shoulder on the far side from the threat.
     */
    private Slot coverSlot(OwnerState o) {
        if (drives.wariness < COVER_WARINESS || drives.threatPos == null) return null;
        if (drives.threat == Drives.Threat.CAT) return Slot.HEAD;
        boolean threatRight = drives.threatPos.sub(o.chest()).dot(o.flatRight()) > 0;
        // An aggressive percher squares up on the near shoulder instead.
        if (drives.temperament().guard() && drives.threat == Drives.Threat.HOSTILE) threatRight = !threatRight;
        return threatRight ? Slot.LEFT_SHOULDER : Slot.RIGHT_SHOULDER;
    }

    private int handStay = -1;

    /** The hand on the side it perches: a short step down, not a leap across the owner's face. */
    private Slot hand() {
        return home == Slot.LEFT_SHOULDER ? Slot.LEFT_HAND : Slot.RIGHT_HAND;
    }

    /** How long to sit on the hand once there, drawn once per visit. */
    private int drawTicksOnce() {
        if (handStay < 0) handStay = drawTicks(2, 4);
        return handStay;
    }

    private void startCrouch(Slot target) {
        hopTarget = target;
        enter(Mode.CROUCH);
    }

    private int crouchTicks() {
        return (int) Math.round(3 * pace);
    }

    /**
     * Whether the owner's height has held still long enough to hop. Measured: on stairs a step up
     * or down every ~0.2 s moves the target a block in an eighth of a second mid-hop, more than air
     * control can follow, and the hop meets the step.
     */
    private boolean ownerSteady() {
        return steadyTicks >= STEADY_TICKS;
    }

    // ── Hopping ────────────────────────────────────────────────────────────────────────────────

    private void launch(OwnerState o, LocalVoxels v) {
        if (!ownerSteady()) {
            // The owner is jumping or on stairs: wait, crouched, for them to settle.
            return;
        }
        Vec to = slotPoint(o, hopTarget);
        double T = clearHopTime(v, pos, to, o);
        if (T < 0) {
            // No clear way there (a ceiling, a wall): stay put and try again later.
            enter(Mode.PERCHED);
            wanderTicks = drawTicks(5, 10);
            unseenTicks = Math.min(unseenTicks, attentionThreshold / 2);
            return;
        }
        launchFrom(o, pos, to, T);
    }

    /**
     * The quickest hop from {@code a} to {@code b} whose arc is clear of blocks and passes round
     * the owner's head and camera — a higher, slower hop if the quick one would go through them —
     * or −1 if none is.
     */
    private double clearHopTime(LocalVoxels v, Vec a, Vec b, OwnerState o) {
        double T = hopTimeFor(a.distanceTo(b));
        for (int k = 0; k < 5; k++, T *= 1.2) {
            if (arcClear(v, a, b, T, o)) return T;
        }
        return -1;
    }

    private void launchFrom(OwnerState o, Vec from, Vec to) {
        launchFrom(o, from, to, hopTimeFor(from.distanceTo(to)));
    }

    private void launchFrom(OwnerState o, Vec from, Vec to, double T) {
        // Aim where the target will be when it lands; air control mops up the rest.
        Vec aim = hopTarget != null ? to.add(o.velocity().scale(T)) : to;
        hopTime = T;
        hopElapsed = 0;
        launchedFromOwner = mode != Mode.SWIM;
        vel = aim.sub(from).scale(1 / T).add(0, 0.5 * GRAVITY * T, 0);
        pos = from;
        yaw = (float) aim.sub(from).yawDeg();
        pitch = 0f;
        hops++;
        enter(Mode.HOP);
    }

    private void fly(OwnerState o, LocalVoxels v) {
        hopElapsed += DT;
        double left = Math.max(DT, hopTime - hopElapsed);
        Vec to = hopTarget != null ? slotPoint(o, hopTarget) : waterTarget;
        // Where the current arc comes down, against where the target will be by then.
        Vec landing = pos.add(vel.scale(left)).add(0, -0.5 * GRAVITY * left * left, 0);
        Vec targetThen = hopTarget != null ? to.add(o.velocity().scale(left)) : to;
        Vec correction = targetThen.sub(landing).scale(2 / (left * left)).clampLength(AIR_CONTROL);
        vel = vel.add(correction.add(0, -GRAVITY, 0).scale(DT));
        Vec h = vel.horizontal();
        if (h.length() > 0.3) yaw = (float) h.yawDeg();
        crouch = Math.max(0f, crouch - 0.2f);
        move(v, vel.length() > 1e-6 ? vel.normalize() : Vec.UP);
        if (backstopLastStep) {
            // Something got in the way mid-air: swim it out from here instead.
            startSwim();
            return;
        }
        if (hopElapsed >= hopTime - 1e-9 || pos.distanceTo(to) < 0.08) {
            if (hopTarget == null) {
                startSwim();
            } else {
                slot = hopTarget;
                handTicks = 0;
                handStay = -1;
                if (slot.hand()) unseenTicks = 0;
                if (!slot.hand() && unseenTicks >= attentionThreshold) {
                    attentionThreshold = drawTicks(t.attentionMinSeconds, t.attentionMaxSeconds);
                }
                settleOffset = pos.sub(slotPoint(o, slot));
                crouch = 0.8f;
                enter(Mode.SETTLE);
            }
        }
    }

    /** Off the perch into the water beside the owner. */
    private void startWaterHop(OwnerState o, LocalVoxels v) {
        Vec f = o.flatForward(), r = o.flatRight();
        Vec[] options = {o.chest().add(f.scale(1.0)).add(r.scale(0.6)).add(0, -0.4, 0),
                o.chest().add(f.scale(1.0)).add(r.scale(-0.6)).add(0, -0.4, 0),
                o.chest().add(f.scale(-1.0)).add(0, -0.4, 0)};
        for (Vec w : options) {
            double T = v.sphereClear(w, radius + 0.1) ? clearHopTime(v, pos, w, o) : -1;
            if (T > 0) {
                hopTarget = null;
                waterTarget = w;
                launchFrom(o, pos, w, T);
                return;
            }
        }
        startSwim(); // nowhere clear to hop: just let go
    }

    private void startSwim() {
        swim.placeMoving(pos, vel, yaw);
        hopTarget = null;
        enter(Mode.SWIM);
    }

    private double hopTimeFor(double dist) {
        return Math.max(0.28, Math.min(0.65, 0.22 + 0.1 * dist)) * pace;
    }

    /**
     * Whether the ballistic arc from {@code a} to {@code b} over {@code T} seconds is clear of
     * blocks and keeps off its owner: it may start and end on them (it hops off and lands on their
     * body), but once it has left their body it mustn't pass back through it, nor through the
     * camera's lens.
     */
    private boolean arcClear(LocalVoxels v, Vec a, Vec b, double T, OwnerState o) {
        Vec v0 = b.sub(a).scale(1 / T).add(0, 0.5 * GRAVITY * T, 0);
        int n = ARC_SAMPLES;
        Vec[] pts = new Vec[n + 1];
        boolean[] onBody = new boolean[n + 1];
        int firstOff = -1, lastOff = -1;
        for (int i = 0; i <= n; i++) {
            double s = T * i / n;
            pts[i] = a.add(v0.scale(s)).add(0, -0.5 * GRAVITY * s * s, 0);
            if (i > 0 && !v.sphereClear(pts[i], radius)) return false;
            onBody[i] = Mind.insideOwner(o, pts[i], radius);
            if (!onBody[i]) {
                if (firstOff < 0) firstOff = i;
                lastOff = i;
            }
        }
        if (firstOff < 0) return true; // never leaves the body: a shuffle along it
        for (int i = firstOff; i <= lastOff; i++) {
            if (onBody[i]) return false;
            if (pts[i].distanceTo(o.camera()) < CAMERA_CLEARANCE + radius) return false;
        }
        return true;
    }

    // ── The owner's body ───────────────────────────────────────────────────────────────────────

    /** Where {@code s} is on the owner right now. */
    public static Vec slotPoint(OwnerState o, Slot s) {
        Vec eye = o.eye();
        Vec f = o.flatForward(), r = o.flatRight();
        return switch (s) {
            case RIGHT_SHOULDER -> eye.add(r.scale(0.3)).add(f.scale(0.08)).add(0, -0.32, 0);
            case LEFT_SHOULDER -> eye.add(r.scale(-0.3)).add(f.scale(0.08)).add(0, -0.32, 0);
            case HEAD -> eye.add(f.scale(0.06)).add(0, 0.16, 0);
            case BACK -> eye.add(f.scale(-0.28)).add(r.scale(0.05)).add(0, 0.02, 0);
            case RIGHT_HAND, LEFT_HAND -> {
                // In the view's own frame, low and to the side, where a held item sits in first person.
                Vec look = o.look();
                Vec right = look.cross(Vec.UP).normalize();
                if (right.lengthSqr() < 1e-9) right = r;
                Vec up = right.cross(look);
                double side = s == Slot.RIGHT_HAND ? 0.3 : -0.3;
                yield eye.add(look.scale(0.6)).add(right.scale(side)).add(up.scale(-0.26));
            }
        };
    }

    /** Rides the slot: position, velocity and facing are the owner's. */
    private void attach(OwnerState o) {
        Vec p = slotPoint(o, slot);
        vel = o.velocity();
        pos = p;
        speed = vel.length();
        yaw = o.yawDeg();
        pitch = 0f;
        bank = 0f;
    }

    private void copyFrom(Body b) {
        pos = b.pos;
        vel = b.vel;
        acc = b.acc;
        speed = b.speed;
        yaw = b.yaw;
        pitch = b.pitch;
        bank = b.bank;
        yawRate = b.yawRate;
        tailPhase = b.tailPhase;
        burstDrive = b.burstDrive;
        backstopLastStep = b.backstopLastStep;
        lastImpact = b.lastImpact;
    }

    private void enter(Mode m) {
        mode = m;
        modeTicks = 0;
    }

    @Override
    long backstopCount() {
        return backstops + swim.backstops;
    }

    @Override
    float speedFactor() {
        return mode == Mode.SWIM ? swim.speedFactor() : 0.6f;
    }

    @Override
    void placeAt(Vec p, float facingYaw) {
        super.placeAt(p, facingYaw);
        swim.placeAt(p, facingYaw);
    }

    /** Back on its perch after a fade (the owner teleported while it was swimming). */
    void reperch(OwnerState o) {
        slot = home;
        enter(Mode.PERCHED);
        attach(o);
    }

    // ── Randomness ─────────────────────────────────────────────────────────────────────────────

    private int drawTicks(double minSeconds, double maxSeconds) {
        return (int) Math.round((minSeconds + (maxSeconds - minSeconds) * unit()) * TPS);
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
