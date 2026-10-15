package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;

import java.util.function.Supplier;

/**
 * A percher's body — crabs and the like (BENTHIC), garden eels (ANCHORED): it rides on its owner
 * and gets about by hopping (docs/fish-companions-sim-plan.md §3.4, spec §3).
 *
 * <ul>
 *   <li><b>Perched</b> is a real attachment, the parrot pattern: the body sits at a slot on the
 *       owner (a shoulder or the head; an anchored fish the head, D5) and goes wherever they
 *       go, teleports included. This is the one placement the plan allows that isn't a swim.</li>
 *   <li><b>Hops</b> are ballistic: a crouch, a launch solved to land where the target will be
 *       when it comes down, bounded air control to correct for the owner changing course, and a
 *       short settle on landing. Crouch and flight are paced by the body's size.</li>
 *   <li>A benthic percher shows itself by hopping onto the owner's hand (in view, low right), and
 *       goes there when they look down; now and then it changes shoulders. An anchored one stays
 *       planted.</li>
 *   <li><b>In water</b> both hop off and swim with the free-swim body under the {@link Mind}
 *       (spec E1), then hop back on once the owner is out.</li>
 *   <li><b>In a party</b> each percher has its own home perch, and a transient move takes only a
 *       perch no party-mate calls home, holds or is heading for ({@link PerchLeases}).</li>
 *   <li><b>On the ground</b> (BENTHIC, docs/fish-companions-benthic-follow.md): on land a benthic
 *       lives on the ground, walking with a {@link GroundBody} that a {@link GroundMind} steers,
 *       and hops up to the owner only to show itself on the hand, to take cover, or when the
 *       owner rides or glides off. Past what it can follow on foot it asks the agent for the fade
 *       onto its perch. Sustained rest calls it up; walking travel brings it down again.</li>
 * </ul>
 */
public final class PerchBody extends Motor {

    public enum Mode { PERCHED, CROUCH, HOP, SETTLE, SWIM, GROUND, GROUND_CROUCH }

    public enum Slot {
        RIGHT_SHOULDER, LEFT_SHOULDER, HEAD, BACK, RIGHT_HAND, LEFT_HAND;

        public boolean hand() {
            return this == RIGHT_HAND || this == LEFT_HAND;
        }

        /** A perch it can peek into view from: the shoulders and the head (not the back). */
        public boolean peeks() {
            return this == RIGHT_SHOULDER || this == LEFT_SHOULDER || this == HEAD;
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

    // The ground (BENTHIC only; null for an anchored percher).
    final GroundBody ground;
    final GroundMind gmind;
    private final float length;
    /** It lives on the ground (BENTHIC): its perch is somewhere it visits, not home. */
    boolean homeGround;
    /** Where a hop off the owner (or out of the water) lands on the ground; null otherwise. */
    private Vec groundTarget;
    /** Set when it wants the agent's fade onto its perch (it can't follow on foot). */
    boolean wantsFade;
    /** A hop from the ground is being crouched for: to this slot, or else to {@link #waterTarget}. */
    private Slot crouchSlot;
    private int groundHopCooldown, riderWait;
    /** This tick's velocity jumped by design: a launch off the ground or out of the water. */
    boolean impulse;
    double lastImpulse;
    boolean climbing, dismounting, tumbling;
    int climbs, dismounts, tumbles;
    private int perchedTicks, sinceSprint, sinceMounted, tumbleCooldown, tumbleTicks;
    private boolean devClimb, devTumble;
    boolean groupMember, groupFollower;
    boolean allowGroupClimb = true;
    /** The party's perch bookkeeping (docs/fish-companions-party.md §6.1), or null alone: every move is allowed. */
    PerchLeases leases;
    /** Its party slot: the lower claims a contested perch first, so the lead has first claim on a hand. */
    int partySlot;
    /** Transient moves the party refused, counted once per blocked spell. */
    int leaseRefusals;
    private boolean refusedNow, refusedWas;
    /** When the unseen timer may next call it to the hand: its own, or the party's (HandClock). */
    HandClock handClock = new HandClock();
    /** Ticks since it last landed on a hand: a party's hand turns go round by this (HandClock). */
    int sinceHand;
    /**
     * A pure percher's peek (owner, 2026-10-14, idea 4): with the hand not due, it leans from its
     * shoulder into the bottom corner of the view for a moment instead. Ticks left, its length, and
     * the wait before the next. Diagnostic count in {@link #peeks}.
     */
    private int peekTicks, peekLen, peekCooldown;
    int peeks;
    private static final int PEEK_EASE = 8;
    /** Diagnostic only (HandVisitProbe): landings on a hand by why it went, and ticks sat there. */
    public enum HandWhy { LOOK_DOWN, HELD_ITEM, UNSEEN, OTHER }
    final int[] handVisits = new int[HandWhy.values().length];
    int handPerchedTicks;
    private HandWhy handWhy = HandWhy.OTHER;
    private final Vec[] travel = new Vec[80];
    private int travelIndex, travelCount;
    private double netTravel;

    PerchBody(CompanionTunables t, Locomotion locomotion, Body swim, OwnerState owner, double radius, float length,
              long seed, Drives drives) {
        this(t, locomotion, swim, owner, radius, length, seed, drives, null, null, null);
    }

    PerchBody(CompanionTunables t, Locomotion locomotion, Body swim, OwnerState owner, double radius, float length,
              long seed, Drives drives, GroundBody ground, GroundMind gmind, LocalVoxels voxels) {
        super(locomotion, Gait.of(Locomotion.FREE_SWIM, t), Vec.ZERO, radius);
        this.t = t;
        this.drives = drives;
        this.swim = swim;
        this.ground = ground;
        this.gmind = gmind;
        this.length = length;
        this.rng = seed * 0x9E3779B97F4A7C15L + 0x2545F4914F6CDD1DL;
        if (rng == 0) rng = 1;
        this.pace = Math.sqrt(Math.max(0.15, length) / 0.3);
        this.home = locomotion == Locomotion.ANCHORED ? (t.anchoredHead ? Slot.HEAD : Slot.BACK)
                : switch ((int) Math.floorMod(seed, 3L)) {
                    case 0 -> Slot.RIGHT_SHOULDER;
                    case 1 -> Slot.LEFT_SHOULDER;
                    default -> Slot.HEAD;
                };
        this.slot = home;
        this.attentionThreshold = drawTicks(t.attentionMinSeconds, t.attentionMaxSeconds);
        this.wanderTicks = drawTicks(20, 45);
        attach(owner);
        if (ground != null) {
            homeGround = true;
            Vec spot = groundSpot(owner, voxels);
            if (spot != null) {
                ground.placeOnFloor(spot, owner.yawDeg(), voxels);
                enter(Mode.GROUND);
                copyFromGround();
            }
        }
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
        if (tumbling && tumbleTicks <= 3) return true;
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
        if (refusedNow && !refusedWas) leaseRefusals++;
        refusedWas = refusedNow;
        refusedNow = false;
        Vec before = vel;
        Mode was = mode;
        impulse = false;
        lastImpulse = 0;
        updateDrives(o, seen);
        if (ground != null) {
            if (tumbleCooldown > 0) tumbleCooldown--;
            if (tumbling) {
                tumbleTicks++;
                if (ground.onFloor()) tumbling = false;
            }
            if (attached() && tumbleCooldown == 0 && !o.riding() && !o.gliding() && !o.inWater()
                    && (drives.sinceHurt == 0 || o.landedFall() >= 3 || devTumble)) {
                attach(o);
                Vec outward = switch (slot) {
                    case LEFT_SHOULDER, LEFT_HAND -> o.flatRight().scale(-1);
                    case RIGHT_SHOULDER, RIGHT_HAND -> o.flatRight();
                    default -> o.flatForward().scale(-1);
                };
                Vec launch = o.velocity().horizontal().add(outward.scale(2)).add(0, 2, 0);
                ground.placeTumbling(pos, launch, yaw);
                gmind.daze = 10;
                groundHopCooldown = 40;
                homeGround = true;
                climbing = dismounting = devTumble = false;
                groundTarget = null;
                hopTarget = null;
                tumbling = true;
                tumbleTicks = 1;
                tumbleCooldown = 200;
                tumbles++;
                enter(Mode.GROUND);
                copyFromGround();
                impulse = true;
                lastImpulse = launch.sub(o.velocity().horizontal()).length();
                acc = Vec.ZERO;
                return;
            }
        }
        switch (mode) {
            case GROUND -> groundStep(o, v, onSwimStart);
            case GROUND_CROUCH -> groundCrouch(o, v);
            case PERCHED -> {
                attach(o);
                crouch = Math.max(0f, crouch - 0.15f);
                Slot next = wantedSlot(o, v);
                if (next != null && leased(next)) startCrouch(next);
            }
            case CROUCH -> {
                if (hopTarget != null && outranked(hopTarget)) {
                    // A party-mate already holds the perch, or claimed it first: stay.
                    enter(Mode.PERCHED);
                    wanderTicks = drawTicks(5, 10);
                    attach(o);
                    break;
                }
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
                if (homeGround && dryTicks >= DRY_TICKS && !o.inWater() && modeTicks >= REMOUNT_COOLDOWN) {
                    // Out of the water onto the ground beside the owner, not onto them.
                    Vec to = groundSpot(o, v);
                    double T = to != null && pos.distanceTo(to) < HOP_RANGE ? clearHopTime(v, pos, to, o) : -1;
                    if (T > 0) {
                        hopTarget = null;
                        waterTarget = null;
                        groundTarget = to;
                        launchFrom(o, pos, to, T);
                    }
                    return;
                }
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
        if (mode != Mode.SWIM && was == Mode.SWIM && ground != null) {
            // Out of the water: no swim to blend from.
            acc = Vec.ZERO;
        }
    }

    // ── Deciding where to be ───────────────────────────────────────────────────────────────────

    private void updateDrives(OwnerState o, boolean seen) {
        if (ground != null) {
            sinceSprint = o.sprinting() ? 0 : sinceSprint + 1;
            sinceMounted = o.riding() || o.gliding() ? 0 : sinceMounted + 1;
            if (attached() && !homeGround) perchedTicks++;
            Vec old = travel[travelIndex];
            netTravel = travelCount == travel.length ? o.feet().sub(old).horizontalLength() : 0;
            travel[travelIndex] = o.feet();
            travelIndex = (travelIndex + 1) % travel.length;
            travelCount = Math.min(travel.length, travelCount + 1);
        }
        // A glimpse during a ground beat, or while it is still out ahead after one, isn't showing
        // itself: the hand ride still comes when due. Reset by them, the beats (due sooner) took
        // its place and cost seen while walking in 9 of 12 seeds; the ride is most of that seen.
        boolean beating = gmind != null && (gmind.beat != null || gmind.sinceBeat < GroundMind.BEAT_AFTERMATH);
        unseenTicks = seen && !beating ? 0 : unseenTicks + 1;
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
        if (!handClock.shared() && handClock.wait > 0) handClock.wait--;
        sinceHand++;
        if (peekTicks > 0 && (mode != Mode.PERCHED || !slot.peeks())) peekTicks = 0;
        if (peekTicks > 0) peekTicks--;
        else if (peekCooldown > 0) peekCooldown--;
        if (slot.hand() && attached()) handPerchedTicks++;
        if (wanderTicks > 0) wanderTicks--;
    }

    /** The slot it wants to hop to from its perch, or null to stay. */
    private Slot wantedSlot(OwnerState o, LocalVoxels v) {
        if (wetTicks >= WATER_TICKS) {
            startWaterHop(o, v);
            return null;
        }
        if (locomotion == Locomotion.ANCHORED || modeTicks < 10) return null;
        if (peekTicks > 0) return null;
        // Something alarming: over to cover, and stay there until it's passed.
        Slot cover = coverSlot(o);
        if (cover != null) return slot == cover ? null : cover;
        boolean lookingDown = lookDownTicks >= LOOK_DOWN_TICKS;
        // Something interesting in the owner's hand: over to it, and stay while it holds the interest.
        Drives.Interest it = drives.target;
        boolean heldInterest = it != null && it.isHeld() && drives.curiosity >= HAND_CURIOSITY;
        if (homeGround && (o.riding() || o.gliding())) {
            // Riding or gliding off with it aboard: stay on until walking resumes.
            homeGround = false;
            perchedTicks = 0;
            return null;
        }
        if (ground != null && !homeGround && perchedTicks >= 200 && netTravel >= 6
                && sinceSprint >= 20 && sinceMounted >= 80 && !o.inWater()) {
            homeGround = true;
            dismounting = true;
        }
        if (slot.hand()) {
            // Back home once it has been seen a while and the owner isn't looking down at it.
            if (!lookingDown && !heldInterest && handTicks >= drawTicksOnce()) {
                if (homeGround) {
                    hopDown(o, v);
                    return null;
                }
                return home;
            }
            return null;
        }
        if (homeGround) {
            // Up here only for cover (now passed) or the start: back down to the ground.
            hopDown(o, v);
            return null;
        }
        // In a group only the leader shows itself on the hand, as from the ground: a follower's
        // hand is another member's (the right shoulder's and the head's are both the right hand).
        if (!groupFollower && (lookingDown || heldInterest)) {
            handWhy = lookingDown ? HandWhy.LOOK_DOWN : HandWhy.HELD_ITEM;
            return hand();
        }
        // Unseen a while with the hand not due: it peeks into view from where it rides (a ground
        // fish too: idle, it rests up here most of the time).
        if (t.handClock && t.shoulderPeek && !groupMember && slot.peeks() && peekCooldown == 0
                && unseenTicks >= attentionThreshold && !handDue() && !handTurn()) {
            peekLen = peekTicks = drawTicks(1.5, 2.5);
            peekCooldown = drawTicks(t.attentionMinSeconds, t.attentionMaxSeconds);
            peeks++;
            return null;
        }
        if (!groupFollower && (unseenTicks >= attentionThreshold && handDue() || handTurn())) {
            handWhy = HandWhy.UNSEEN;
            return hand();
        }
        // Resting, it stays on its shoulder rather than changing sides (it still shows itself now and then).
        if (wanderTicks == 0 && !drives.resting && !groupMember) {
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
        // A group shares one set of slots: each member's cover is its own (up on the owner),
        // never the one slot all three would otherwise pile onto.
        if (groupMember) return home;
        Slot wanted;
        if (drives.threat == Drives.Threat.CAT) {
            wanted = Slot.HEAD;
        } else {
            boolean threatRight = drives.threatPos.sub(o.chest()).dot(o.flatRight()) > 0;
            // An aggressive percher squares up on the near shoulder instead.
            if (drives.temperament().guard() && drives.threat == Drives.Threat.HOSTILE) threatRight = !threatRight;
            wanted = threatRight ? Slot.LEFT_SHOULDER : Slot.RIGHT_SHOULDER;
        }
        if (leases == null || wanted == home || leases.free(this, wanted)) return wanted;
        // In a party the cover may be a mate's home: the next free perch, else its own.
        for (Slot s : new Slot[]{Slot.HEAD, Slot.BACK}) {
            if (s == home || leases.free(this, s)) return s;
        }
        return home;
    }

    /**
     * Whether it may go to {@code s} now: always alone, and always to its own home; in a party only
     * to a perch no mate calls home or holds or is heading for (§6.1 leases).
     */
    private boolean leased(Slot s) {
        if (leases == null || s == home || leases.free(this, s)) return true;
        refusedNow = true;
        return false;
    }

    /** Whether a party-mate sits on {@code s}, or heads for it with the earlier claim: then it yields. */
    private boolean outranked(Slot s) {
        if (leases == null || s == home || !leases.outranked(this, s)) return false;
        refusedNow = true;
        return true;
    }

    /** The perch it is heading for, or null: what a party-mate sees it claim besides the one it sits on. */
    Slot heading() {
        return switch (mode) {
            case CROUCH, HOP -> hopTarget;
            case GROUND_CROUCH -> crouchSlot;
            default -> null;
        };
    }

    private int handStay = -1;

    /** Whether the unseen timer may call it to the hand now (always, with the clock off). */
    private boolean handDue() {
        return !t.handClock || handClock.ready(this);
    }

    /** In a party, whether its turn on the hand has come round (HandClock): it goes, seen or not. */
    private boolean handTurn() {
        return t.handClock && handClock.shared() && handClock.ready(this);
    }

    /** Whether a party's hand turn can go to it: a percher that can lead, on the owner or the ground. */
    boolean mayTakeHandTurn() {
        return locomotion != Locomotion.ANCHORED && !groupFollower && (attached() || onGround());
    }

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
        if (hopTarget == null && groundTarget != null) {
            // Down to the ground beside where the owner is now, not where they were when it crouched.
            Vec now = groundSpot(o, v);
            if (now != null) groundTarget = now;
        }
        Vec to = hopTarget != null ? slotPoint(o, hopTarget) : groundTarget;
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

    /** Extra room a leap off the ground wants round its arc: its flight bends to follow a moving owner. */
    private static final double GROUND_LEAP_MARGIN = 0.15;
    private double arcMargin;

    private void launchFrom(OwnerState o, Vec from, Vec to) {
        launchFrom(o, from, to, hopTimeFor(from.distanceTo(to)));
    }

    private void launchFrom(OwnerState o, Vec from, Vec to, double T) {
        // Aim where the target will be when it lands; air control mops up the rest.
        Vec aim = hopTarget != null ? to.add(o.velocity().scale(T)) : to;
        hopTime = T;
        hopElapsed = 0;
        launchedFromOwner = mode != Mode.SWIM && mode != Mode.GROUND_CROUCH;
        Vec was = vel;
        vel = aim.sub(from).scale(1 / T).add(0, 0.5 * GRAVITY * T, 0);
        if (!launchedFromOwner && ground != null) {
            // Off the ground or out of the water: a push-off, by design a jump in velocity.
            impulse = true;
            lastImpulse = vel.sub(was).length();
        }
        pos = from;
        yaw = (float) aim.sub(from).yawDeg();
        pitch = 0f;
        hops++;
        enter(Mode.HOP);
    }

    private void fly(OwnerState o, LocalVoxels v) {
        hopElapsed += DT;
        if (groundTarget != null && vel.y() < 0 && pos.y() + vel.y() * DT < groundTarget.y() + 0.4) {
            // Coming down onto the ground: the ground body takes the touchdown (it lands on the
            // floor rather than overshooting a point resting on it).
            enterGroundFalling();
            return;
        }
        double left = Math.max(DT, hopTime - hopElapsed);
        Vec to = hopTarget != null ? slotPoint(o, hopTarget) : groundTarget != null ? groundTarget : waterTarget;
        // Where the current arc comes down, against where the target will be by then.
        Vec landing = pos.add(vel.scale(left)).add(0, -0.5 * GRAVITY * left * left, 0);
        Vec targetThen = hopTarget != null ? to.add(o.velocity().scale(left)) : to;
        Vec correction = targetThen.sub(landing).scale(2 / (left * left)).clampLength(AIR_CONTROL);
        Vec dv = correction.add(0, -GRAVITY, 0).scale(DT);
        Vec after = vel.add(dv);
        Vec h = after.horizontal();
        if (h.length() > 0.3) yaw = (float) h.yawDeg();
        crouch = Math.max(0f, crouch - 0.2f);
        if (ground != null) {
            // A hop down can cross a higher tread before reaching its chosen landing. Let the
            // ground motor take that touchdown instead of treating the tread as an air collision.
            Vec midpoint = vel.add(dv.scale(0.5));
            if (groundTarget != null && after.y() < 0
                    && ground.crossesFloor(pos, pos.add(midpoint.scale(DT)), v)) {
                enterGroundFalling();
                return;
            }
            // A benthic's hops step at the midpoint velocity: exact under constant acceleration,
            // so it flies the arc that was checked clear (the Euler step runs low and clips the
            // edge of a terrace it was meant to clear).
            vel = vel.add(dv.scale(0.5));
            move(v, after.length() > 1e-6 ? after.normalize() : Vec.UP);
            if (!backstopLastStep) vel = after;
        } else {
            vel = after;
            move(v, vel.length() > 1e-6 ? vel.normalize() : Vec.UP);
        }
        if (backstopLastStep) {
            // Something got in the way mid-air: swim it out from here instead (or, a benthic on
            // land, fall to the ground).
            if (groundTarget != null || ground != null && waterTarget == null && !v.water(pos)) enterGroundFalling();
            else startSwim();
            return;
        }
        if (hopElapsed >= hopTime - 1e-9 || pos.distanceTo(to) < 0.08) {
            if (hopTarget == null && groundTarget != null) {
                enterGroundFalling();
            } else if (hopTarget == null) {
                startSwim();
            } else {
                slot = hopTarget;
                if (climbing) {
                    homeGround = false;
                    climbing = devClimb = false;
                    climbs++;
                    perchedTicks = 0;
                }
                handTicks = 0;
                handStay = -1;
                if (slot.hand()) {
                    unseenTicks = 0;
                    sinceHand = 0;
                    if (t.handClock) handClock.wait = drawTicks(t.handGapMinSeconds, t.handGapMaxSeconds);
                    handVisits[handWhy.ordinal()]++;
                    handWhy = HandWhy.OTHER;
                }
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
        for (Vec w : waterOptions(o)) {
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

    private static Vec[] waterOptions(OwnerState o) {
        Vec f = o.flatForward(), r = o.flatRight();
        return new Vec[]{o.chest().add(f.scale(1.0)).add(r.scale(0.6)).add(0, -0.4, 0),
                o.chest().add(f.scale(1.0)).add(r.scale(-0.6)).add(0, -0.4, 0),
                o.chest().add(f.scale(-1.0)).add(0, -0.4, 0)};
    }

    private void startSwim() {
        climbing = dismounting = tumbling = false;
        swim.placeMoving(pos, vel, yaw);
        hopTarget = null;
        groundTarget = null;
        enter(Mode.SWIM);
    }

    // -- On the ground --------------------------------------------------------------------------

    /** One tick walking: unless something calls it up onto its owner or into the water. */
    private void groundStep(OwnerState o, LocalVoxels v, Runnable onSwimStart) {
        // Never the mood's pace: a walker is the slowest companion, and even an idle slowdown left
        // it behind in the forest and stairwell (fades +42% sleepy over 120 seeds, plan §5 Phase 5).
        ground.tempo = drives.temperament().pace();
        if (groundHopCooldown > 0) groundHopCooldown--;
        boolean ready = ground.onFloor() && groundHopCooldown == 0 && gmind.daze == 0;
        if (wetTicks >= WATER_TICKS && ready) {
            // The owner is swimming: in already, swim; otherwise hop in beside them.
            if (v.water(pos)) {
                startSwim();
                return;
            }
            for (Vec w : waterOptions(o)) {
                if (v.sphereClear(w, radius + 0.1) && w.distanceTo(pos) < HOP_RANGE) {
                    beginGroundHop(null, w);
                    return;
                }
            }
        }
        if (o.riding() || o.gliding()) {
            // Nothing on the ground keeps up with a horse or elytra: climb on, or fade onto the perch.
            riderWait++;
            if (riderWait > 20 || slotPoint(o, home).distanceTo(pos) > HOP_RANGE) {
                wantsFade = true;
            } else if (ready && ownerSteady()) {
                // Climbing on as they set off: only once they're steady enough to land on.
                beginGroundHop(home, null);
                return;
            }
        } else {
            riderWait = 0;
        }
        if (ready && o.velocity().y() > -0.5) {
            Slot cover = drives.threat == Drives.Threat.HOSTILE ? null : coverSlot(o);
            boolean lookingDown = lookDownTicks >= LOOK_DOWN_TICKS;
            Drives.Interest it = drives.target;
            boolean heldInterest = it != null && it.isHeld() && drives.curiosity >= HAND_CURIOSITY;
            boolean climbWant = (drives.resting || devClimb) && allowGroupClimb && o.horizontalSpeed() < 0.3
                    && !o.riding() && !o.gliding();
            boolean showDue = !groupFollower && drives.wariness < COVER_WARINESS && unseenTicks >= groundAttention();
            Slot up = cover != null ? cover
                    : !groupFollower && drives.wariness < COVER_WARINESS
                            && (lookingDown || heldInterest || showDue && handDue() || handTurn()) ? hand()
                    : climbWant ? home : null;
            if (up != null && !leased(up)) up = null;
            if (up != null && up.hand()) {
                handWhy = lookingDown ? HandWhy.LOOK_DOWN : heldInterest ? HandWhy.HELD_ITEM : HandWhy.UNSEEN;
            }
            // Only from somewhere a hop up is clear (not from behind them, through their body).
            arcMargin = GROUND_LEAP_MARGIN;
            boolean clear = up != null && slotPoint(o, up).distanceTo(pos) <= CLOSE_RANGE
                    && clearHopTime(v, pos, slotPoint(o, up), o) > 0;
            arcMargin = 0;
            if (clear) {
                climbing = climbWant && up == home && cover == null;
                beginGroundHop(up, null);
                return;
            }
            // Out of reach of the hand it wants to show itself on: come up alongside first.
            gmind.wantClose = up != null && (up.hand() || climbWant);
            gmind.closeCalm = climbWant && up == home;
            gmind.closeSide = home == Slot.LEFT_SHOULDER ? -1 : 1;
        } else {
            gmind.wantClose = gmind.wantClose && unseenTicks >= groundAttention();
        }
        // The in-view beats go first: well before it would come up to the hand, and sooner for a racer.
        gmind.unseen = unseenTicks;
        gmind.showDue = t.handClock && t.idleInView && !groupMember && unseenTicks >= groundAttention();
        gmind.raceAhead = drives.temperament().raceAhead();
        // More ground beats with hand visits rare (HandClock): sooner after going unseen, and again sooner.
        boolean boost = t.handClock && t.moreGroundBeats && !groupMember;
        gmind.beatAfter = (int) (groundAttention() / (gmind.raceAhead ? 4 : 2) / (boost ? t.groundBeatSooner : 1));
        gmind.beatCooldownScale = boost ? 1 / t.groundBeatSooner : 1;
        gmind.step(o, v, drives, length);
        if (gmind.wantsFade) wantsFade = true;
        copyFromGround();
    }

    /** How close to a slot it starts a hop up to it from the ground: within a hop, with room to spare for a moving owner. */
    private static final double CLOSE_RANGE = 2.6;

    private void beginGroundHop(Slot toSlot, Vec toWater) {
        gmind.wantClose = false;
        crouchSlot = toSlot;
        waterTarget = toWater;
        groundTarget = null;
        enter(Mode.GROUND_CROUCH);
    }

    /** Brakes, turns to face where it's going, crouches, and goes, if the way is clear. */
    private void groundCrouch(OwnerState o, LocalVoxels v) {
        if (crouchSlot != null && outranked(crouchSlot)) {
            abortGroundHop();
            return;
        }
        Vec to = crouchSlot != null ? slotPoint(o, crouchSlot) : waterTarget;
        Vec d = to.sub(pos).horizontal();
        float face = d.lengthSqr() > 1e-6 ? (float) d.yawDeg() : ground.yaw;
        // Beside an owner on the move it leaps on the run, keeping pace until it faces the slot;
        // beside a still one it stops, faces it and crouches.
        boolean running = crouchSlot != null && o.horizontalSpeed() > 0.6;
        if (running) {
            // At the owner's level, so it follows them down a step on the way.
            ground.walk(new Vec(to.x(), GroundMind.ownerFloor(o, v).y(), to.z()), o.horizontalSpeed() * 1.1,
                    GroundBody.Rhythm.SCURRY, null, face, v);
        } else {
            ground.walk(ground.standing(), 0, GroundBody.Rhythm.AMBLE, null, face, v);
        }
        copyFromGround();
        crouch = Math.min(1f, crouch + 1f / crouchTicks());
        boolean faced = Math.abs(Body.wrap(face - ground.yaw)) < (running ? 12f : 4f);
        boolean settled = running ? modeTicks >= 2 && levelAhead(o, v) : modeTicks >= crouchTicks() && ground.speed < 0.3;
        if (settled && faced && ground.onFloor() && (crouchSlot == null || ownerSteady())) {
            arcMargin = GROUND_LEAP_MARGIN;
            double T = to.distanceTo(pos) <= HOP_RANGE ? clearHopTime(v, pos, to, o) : -1;
            arcMargin = 0;
            if (T < 0) {
                abortGroundHop();
                return;
            }
            hopTarget = crouchSlot;
            groundTarget = null;
            launchFrom(o, pos, to, T);
            return;
        }
        if (modeTicks > 40 || (o.riding() || o.gliding()) && ++riderWait > 20) {
            abortGroundHop();
            if (o.riding() || o.gliding()) wantsFade = true;
        }
    }

    /**
     * Whether the owner's ground stays level for the next second of their walk: a leap at a hand
     * that's about to step down (or up) a terrace bends its arc into the riser.
     */
    private boolean levelAhead(OwnerState o, LocalVoxels v) {
        Vec feet = GroundMind.ownerFloor(o, v);
        Vec step = o.velocity().horizontal().scale(0.25);
        for (int i = 1; i <= 4; i++) {
            Vec p = feet.add(step.scale(i));
            double f = v.floorBelow(p.x(), p.z(), feet.y() + 1.05, 2.1, 0.05);
            if (Double.isNaN(f) || Math.abs(f - feet.y()) > 0.1) return false;
        }
        return true;
    }

    private void abortGroundHop() {
        climbing = false;
        crouchSlot = null;
        waterTarget = null;
        groundHopCooldown = 20;
        // Try again shortly: down on the ground it is out of sight more than it is on a shoulder.
        unseenTicks = Math.min(unseenTicks, Math.max(0, groundAttention() - 40));
        enter(Mode.GROUND);
    }

    /**
     * Unseen this long on the ground, it goes to show itself on the hand: sooner than from a
     * shoulder, because down on the ground behind its owner it is out of sight most of the time.
     */
    private int groundAttention() {
        return (int) (attentionThreshold * 0.6);
    }

    /** Off the owner down to the ground beside them, if there's a clear hop to somewhere to stand. */
    private void hopDown(OwnerState o, LocalVoxels v) {
        if (!ownerSteady()) return;
        Vec to = groundSpot(o, v);
        double T = to != null ? clearHopTime(v, pos, to, o) : -1;
        if (T < 0) {
            enter(Mode.PERCHED); // nowhere to land just now: look again shortly
            return;
        }
        hopTarget = null;
        waterTarget = null;
        groundTarget = to;
        startCrouchToGround();
    }

    private void startCrouchToGround() {
        enter(Mode.CROUCH);
    }

    /**
     * Somewhere on the ground beside the owner a body could stand (its centre, resting on the
     * floor), on the side it rides, or null.
     */
    Vec groundSpot(OwnerState o, LocalVoxels v) {
        if (ground == null) return null;
        Vec feet = GroundMind.ownerFloor(o, v);
        Vec f = o.flatForward(), r = o.flatRight();
        double side = home == Slot.LEFT_SHOULDER || slot == Slot.LEFT_HAND ? -1 : 1;
        Vec[] options = {feet.add(r.scale(0.9 * side)).add(f.scale(0.3)),
                feet.add(r.scale(1.1 * side)).add(f.scale(-0.5)),
                feet.add(r.scale(-0.9 * side)).add(f.scale(0.3)),
                feet.add(f.scale(-1.1)),
                feet.add(f.scale(1.3))};
        if (dismounting) {
            Vec first = options[0];
            options[0] = options[1];
            options[1] = first;
        }
        for (Vec c : options) {
            double fl = ground.footprintFloorFrom(v, c.x(), c.z(), feet.y());
            if (Double.isNaN(fl) || Math.abs(fl - feet.y()) > 1.0 || !ground.flatAt(v, c.x(), c.z(), fl)) continue;
            Vec at = new Vec(c.x(), fl + radius + LocalVoxels.GROUND_LIFT, c.z());
            if (v.sphereClear(at, radius)) return at;
        }
        return null;
    }

    private void enterGroundFalling() {
        climbing = false;
        if (dismounting) {
            dismounts++;
            dismounting = false;
        }
        ground.placeFalling(pos, vel, yaw);
        groundTarget = null;
        hopTarget = null;
        enter(Mode.GROUND);
    }

    void copyFromGround() {
        pos = ground.pos;
        vel = ground.vel;
        acc = ground.acc;
        speed = ground.speed;
        yaw = ground.yaw;
        pitch = ground.pitch;
        bank = 0f;
        yawRate = ground.yawRate;
        tailPhase = ground.tailPhase;
        burstDrive = ground.burstDrive;
        backstopLastStep = ground.backstopLastStep;
        lastImpact = ground.lastImpact;
        crouch = Math.max(ground.squash, mode == Mode.GROUND_CROUCH ? crouch : 0f);
        if (ground.impulse) {
            impulse = true;
            lastImpulse = Math.max(lastImpulse, ground.lastImpulse);
        }
    }

    /** Whether it is on the ground: walking, or crouching there for a hop. */
    boolean onGround() {
        return mode == Mode.GROUND || mode == Mode.GROUND_CROUCH;
    }

    /** Dev trigger: return to the ground. */
    void devGround() {
        if (ground == null) return;
        homeGround = true;
        climbing = devClimb = devTumble = false;
        wantsFade = false;
        gmind.wantsFade = false;
        gmind.strandedTicks = 0;
    }

    void devPerch() { if (ground != null) devClimb = true; }
    void devTumble() { if (ground != null) devTumble = true; }

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
            if (i > 0 && !v.sphereClear(pts[i], radius + arcMargin)) return false;
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

    /** 0..1: how far into its peek it leans, eased in and out. */
    float peek() {
        if (peekTicks <= 0) return 0f;
        double k = Math.min(1, Math.min(peekLen - peekTicks, peekTicks) / (double) PEEK_EASE);
        return (float) (k * k * (3 - 2 * k));
    }

    /**
     * Where it rides on the owner, seen from their view: its slot, leant {@code peek} of the way to
     * the bottom corner of the view on its side (outside the hand, which sits nearer the middle).
     */
    public static Vec viewPoint(OwnerState o, Slot s, float peek) {
        Vec p = slotPoint(o, s);
        if (peek <= 0f || !s.peeks()) return p;
        Vec look = o.look();
        Vec right = look.cross(Vec.UP).normalize();
        if (right.lengthSqr() < 1e-9) right = o.flatRight();
        Vec up = right.cross(look);
        double side = s == Slot.LEFT_SHOULDER ? -0.5 : 0.5;
        Vec corner = o.eye().add(look.scale(0.6)).add(right.scale(side)).add(up.scale(-0.34));
        return p.add(corner.sub(p).scale(peek));
    }

    /** Rides the slot: position, velocity and facing are the owner's. */
    private void attach(OwnerState o) {
        Vec p = viewPoint(o, slot, peek());
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
        return backstops + swim.backstops + (ground != null ? ground.backstops : 0);
    }

    @Override
    float speedFactor() {
        if (onGround()) return ground.speedFactor();
        return mode == Mode.SWIM ? swim.speedFactor() : 0.6f;
    }

    @Override
    void placeAt(Vec p, float facingYaw) {
        super.placeAt(p, facingYaw);
        swim.placeAt(p, facingYaw);
    }

    /** The party gives it its own home perch (§6.1): from now on it rides there, starting now if it rides. */
    void rehome(Slot s, OwnerState o) {
        home = s;
        slot = s;
        if (attached()) attach(o);
    }

    /** Back on its perch after a fade (the owner teleported while it was swimming). */
    void reperch(OwnerState o) {
        slot = home;
        enter(Mode.PERCHED);
        attach(o);
        if (ground != null) {
            perchedTicks = 0;
            climbing = dismounting = tumbling = false;
            devClimb = devTumble = false;
            travelCount = 0;
            // On its perch now; sustained walking can bring it down again.
            homeGround = false;
            wantsFade = false;
            gmind.wantsFade = false;
            gmind.strandedTicks = 0;
            gmind.endBeat();
            groundTarget = null;
        }
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
