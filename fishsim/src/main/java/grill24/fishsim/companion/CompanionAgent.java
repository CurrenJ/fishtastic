package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;

/**
 * One companion fish: a {@link Mind} that decides, a {@link Body} that swims, and the blocks
 * around its owner (docs/fish-companions-sim-plan.md §3).
 *
 * <p>Fixed 20 Hz {@link #step}; {@link #interpolate} is the only per-frame call and never changes
 * simulation state. Fully deterministic for a given seed and input sequence.
 *
 * <p>The one placement that isn't a swim: when the owner teleports or outruns any fish (elytra,
 * minecarts), the companion fades out, is placed beside the owner, and fades back in.
 */
public final class CompanionAgent {

    public static final float DT = (float) Body.DT;
    /** Ticks to fade fully out or in. */
    private static final int FADE_TICKS = 8;

    /** NONE: visible. OUT/IN: fading. HIDDEN: faded out while the owner moves faster than any swim. */
    public enum Fade { NONE, OUT, HIDDEN, IN }

    /** Owner speed (fraction of catch-up speed) above which the companion stays hidden after fading out... */
    private static final double HIDE_SPEED = 0.8;
    /** ...and below which, held for {@link #REAPPEAR_TICKS}, it comes back. */
    private static final double REAPPEAR_SPEED = 0.5;
    private static final int REAPPEAR_TICKS = 10;

    private final CompanionTunables t;
    private final Motor body;
    private final Mind mind;
    /** Curiosity (and, as Phase 2 lands, wariness and rest): what the world around the owner stirs up. */
    private final Drives drives;
    private final Temperament temperament;
    private PoseHint pose = PoseHint.NONE;
    private Vec lookTarget;
    /** A percher's body (BENTHIC, ANCHORED), else null; its swim body is the one the mind steers. */
    private final PerchBody perch;
    private boolean perchSeen;
    private final LocalVoxels voxels = new LocalVoxels();
    private final Breadcrumbs crumbs = new Breadcrumbs();
    /** The owner's path at their feet, on the floor: the way a ground companion follows (BENTHIC only). */
    private final Breadcrumbs footCrumbs = new Breadcrumbs();
    private Vec lastChest;
    private final float length;
    private final Locomotion locomotion;

    private float appear = 1f;
    private Fade fade = Fade.NONE;
    private long leashEvents;
    private int slowTicks;
    private long ticks;

    // Previous-tick pose, for interpolation.
    private Vec prevPos;
    private float prevYaw, prevPitch, prevBank, prevAppear = 1f, prevTail;

    // Interpolated pose for this frame, written by interpolate().
    private Vec renderPos;
    private float renderYaw, renderPitch, renderBank, renderAppear = 1f, renderTail;

    /**
     * @param length rendered body length in blocks; sets the collision radius
     */
    public CompanionAgent(CompanionTunables t, long seed, float length, CompanionWorld world) {
        this(t, seed, Locomotion.FREE_SWIM, length, world);
    }

    /**
     * @param locomotion the species' class
     * @param length     rendered body length in blocks; sets the collision radius
     */
    public CompanionAgent(CompanionTunables t, long seed, Locomotion locomotion, float length, CompanionWorld world) {
        this(t, seed, locomotion, length, Temperament.BASELINE, world);
    }

    /**
     * @param locomotion  the species' class
     * @param length      rendered body length in blocks; sets the collision radius
     * @param temperament the species' temperament (spec §4)
     */
    public CompanionAgent(CompanionTunables t, long seed, Locomotion locomotion, float length, Temperament temperament,
                          CompanionWorld world) {
        // No gait: a benthic rides its perch on land as in Phase 3 (the game's path until the
        // adapter supplies a gait in Phase 5, docs/fish-companions-benthic-follow.md §8).
        this(t, seed, locomotion, length, temperament, null, world);
    }

    /**
     * @param locomotion  the species' class
     * @param length      rendered body length in blocks; sets the collision radius
     * @param temperament the species' temperament (spec §4)
     * @param gait        how a benthic gets about on the ground, or null to keep it on its perch on
     *                    land (the Phase 3 percher); ignored for other classes
     */
    public CompanionAgent(CompanionTunables t, long seed, Locomotion locomotion, float length, Temperament temperament,
                          GroundGait gait, CompanionWorld world) {
        OwnerState owner = world.owner();
        BlockQuery blocks = world.blocks();
        this.t = t;
        this.temperament = temperament;
        this.drives = new Drives(temperament);
        this.length = length;
        this.locomotion = locomotion;
        double radius = Math.max(0.08, Math.min(0.3, length * 0.3));
        voxels.update(blocks, owner.eye());
        Vec start = besideOwner(owner, radius);
        if (locomotion == Locomotion.BENTHIC || locomotion == Locomotion.ANCHORED) {
            Body swim = new Body(t, Locomotion.FREE_SWIM, start, owner.yawDeg(), radius, seed ^ 0x5DEECE66DL);
            if (locomotion == Locomotion.BENTHIC && gait != null) {
                // On land a benthic lives on the ground (docs/fish-companions-benthic-follow.md).
                GroundBody ground = new GroundBody(locomotion, gait, start, owner.yawDeg(), radius, length,
                        seed ^ 0x2545F4914F6CDD1DL);
                GroundMind gmind = new GroundMind(footCrumbs, ground, seed ^ 0x6A09E667F3BCC909L);
                this.perch = new PerchBody(t, locomotion, swim, owner, radius, length, seed, drives, ground, gmind, voxels);
            } else {
                this.perch = new PerchBody(t, locomotion, swim, owner, radius, length, seed, drives);
            }
            this.body = perch;
            this.mind = new Mind(t, swim, seed, drives, temperament);
            start = perch.pos;
            swim.pace = temperament.pace();
        } else {
            this.perch = null;
            this.body = locomotion == Locomotion.DRIFT
                    ? new DriftBody(t, start, owner.yawDeg(), radius, seed ^ 0x5DEECE66DL)
                    : new Body(t, locomotion, start, owner.yawDeg(), radius, seed ^ 0x5DEECE66DL);
            this.mind = new Mind(t, body, seed, drives, temperament);
            if (body instanceof Body b) b.pace = temperament.pace();
        }
        this.prevPos = start;
        this.renderPos = start;
        this.prevYaw = renderYaw = owner.yawDeg();
    }

    public void step(CompanionWorld world) {
        OwnerState owner = world.owner();
        BlockQuery blocks = world.blocks();
        prevPos = body.pos;
        prevYaw = body.yaw;
        prevPitch = body.pitch;
        prevBank = body.bank;
        prevAppear = appear;
        prevTail = body.tailPhase;
        ticks++;

        voxels.update(blocks, owner.eye());
        drives.update(world);
        if (owner.teleported()) crumbs.clear();
        crumbs.record(owner.chest());
        lastChest = owner.chest();
        if (perch != null && perch.ground != null) {
            if (owner.teleported()) footCrumbs.clear();
            // Only where they set foot: in the air over a gap (a jump, a fall, flight) their feet
            // aren't a way across it.
            Vec floor = GroundMind.ownerFloor(owner, voxels);
            if (owner.feet().y() - floor.y() < 0.6) footCrumbs.record(floor);
        }

        boolean riding = perch != null && perch.attached();
        // A ground companion's leash is its own: lag along the way the owner went (GroundMind).
        boolean grounded = perch != null && perch.onGround();
        if (fade == Fade.NONE && !riding
                && (owner.teleported() || !grounded && body.pos.distanceTo(owner.chest()) > t.leashGap)) {
            fade = Fade.OUT;
        }

        if (perch != null) {
            // The drives react to whether it was seen last tick; seen is then judged where the
            // body is now, against this tick's camera (judging last tick's spot against this tick's
            // camera put a crab riding a walking owner's hand out of view).
            perch.step(owner, voxels, perchSeen, () -> mind.think(owner, voxels, crumbs),
                    () -> mind.ownerAvoidance(owner), () -> mind.reset(owner));
            perchSeen = Visibility.visible(owner, perch.pos, voxels);
            if (fade == Fade.NONE && perch.wantsFade && !perch.attached()) fade = Fade.OUT;
            // Riding the owner through a teleport: it went with them, so don't draw it streaking.
            if (perch.attached() && owner.teleported()) prevPos = perch.pos;
            perchPose();
        } else {
            Vec desired = mind.think(owner, voxels, crumbs);
            body.step(desired, mind.wanderScale, mind.ownerAvoidance(owner), voxels, Capsule.ownerBody(owner));
            pose = mind.pose;
            lookTarget = mind.goal == Mind.Goal.INSPECT && drives.target != null ? drives.target.pos : null;
        }

        switch (fade) {
            case OUT -> {
                appear = Math.max(0f, appear - 1f / FADE_TICKS);
                if (appear == 0f) {
                    leashEvents++;
                    slowTicks = 0;
                    if (perch != null && perch.ground != null) {
                        // A benthic fades back in on its perch at once: riding along is how it keeps up.
                        reappear(owner);
                        groundFades++;
                    } else if (owner.velocity().length() > HIDE_SPEED * t.catchUpSpeed) {
                        fade = Fade.HIDDEN;
                    } else {
                        reappear(owner);
                    }
                }
            }
            case HIDDEN -> {
                // Faded out while the owner outpaces any swim (elytra, minecarts): stay hidden,
                // riding along unseen, and come back once they've slowed down.
                place(owner);
                slowTicks = owner.velocity().length() < REAPPEAR_SPEED * t.catchUpSpeed ? slowTicks + 1 : 0;
                if (slowTicks >= REAPPEAR_TICKS) reappear(owner);
            }
            case IN -> {
                appear = Math.min(1f, appear + 1f / FADE_TICKS);
                if (appear == 1f) fade = Fade.NONE;
            }
            case NONE -> { }
        }
    }

    /**
     * A percher out of the water shows its curiosity in its pose: nose to a held item it has hopped
     * onto the hand for, or a watchful look at something it can't go to. Watching a thing in the
     * world counts as a look, so that wears off; a held item only wears off once it's on the hand
     * (watching it from the shoulder is just waiting to hop).
     */
    private void perchPose() {
        if (perch.onGround()) lookTarget = null;
        if (perch.mode == PerchBody.Mode.SWIM) {
            pose = mind.pose;
            lookTarget = mind.goal == Mind.Goal.INSPECT && drives.target != null ? drives.target.pos : null;
            return;
        }
        Drives.Interest it = drives.target;
        pose = PoseHint.NONE;
        lookTarget = null;
        boolean guard = temperament.guard() && drives.threat == Drives.Threat.HOSTILE;
        if (temperament.guard() && drives.sinceHurt < 14) {
            pose = PoseHint.BITE;
            lookTarget = drives.threatPos;
            return;
        }
        if (locomotion == Locomotion.ANCHORED && (drives.hostileClose || drives.wariness >= PerchBody.COVER_WARINESS)) {
            // An aggressive eel leans out at it, puffed; any other ducks in.
            pose = guard ? PoseHint.PUFF : PoseHint.DUCK;
            lookTarget = drives.threatPos;
            return;
        }
        if (drives.wariness >= PerchBody.COVER_WARINESS && drives.threatPos != null) {
            pose = guard ? PoseHint.PUFF : PoseHint.HIDE;
            lookTarget = drives.threatPos;
            return;
        }
        if (it == null || drives.curiosity < PerchBody.WATCH_CURIOSITY) {
            if (drives.resting && perch.mode == PerchBody.Mode.PERCHED) pose = PoseHint.SLEEPY;
            else if (temperament.tremble() && perch.mode == PerchBody.Mode.PERCHED) pose = PoseHint.TREMBLE;
            return;
        }
        lookTarget = it.pos;
        boolean onHand = perch.slot.hand() && perch.mode == PerchBody.Mode.PERCHED;
        if (!it.isHeld() || onHand) drives.engage(it, 0, 0);
        pose = onHand && it.isHeld() ? Mind.poseFor(it) : PoseHint.WATCH;
    }

    private void reappear(OwnerState owner) {
        place(owner);
        fade = Fade.IN;
    }

    /** Puts the body beside the owner, at rest, with no interpolation across the jump. */
    private void place(OwnerState owner) {
        Vec p = besideOwner(owner, body.radius);
        body.placeAt(p, owner.yawDeg());
        mind.reset(owner);
        if (perch != null) {
            // A percher comes back where it lives: on its owner.
            perch.reperch(owner);
            p = perch.pos;
        }
        prevPos = p;
        prevYaw = body.yaw;
        prevPitch = body.pitch;
        prevBank = body.bank;
    }

    /** Writes this frame's pose. Read-only with respect to simulation state. */
    public void interpolate(float alpha) {
        renderPos = prevPos.lerp(body.pos, alpha);
        renderYaw = prevYaw + Body.wrap(body.yaw - prevYaw) * alpha;
        renderPitch = prevPitch + (body.pitch - prevPitch) * alpha;
        renderBank = prevBank + (body.bank - prevBank) * alpha;
        renderAppear = prevAppear + (appear - prevAppear) * alpha;
        float dTail = body.tailPhase - prevTail;
        if (dTail < 0) dTail += (float) (Math.PI * 2);
        renderTail = prevTail + dTail * alpha;
    }

    /** An open spot beside the owner: their right, left, behind, or failing those, the eye. */
    private Vec besideOwner(OwnerState o, double radius) {
        Vec chest = o.chest();
        Vec r = o.flatRight(), f = o.flatForward();
        Vec[] candidates = {
                chest.add(r.scale(1.1)).add(f.scale(0.3)),
                chest.add(r.scale(-1.1)).add(f.scale(0.3)),
                chest.add(f.scale(-1.2)),
                chest.add(0, 0.6, 0).add(r.scale(0.8)),
                chest.add(f.scale(1.0)),
        };
        for (Vec c : candidates) {
            if (voxels.sphereClear(c, radius + 0.1)) return c;
        }
        return o.eye();
    }

    // ── State (simulation tick) ────────────────────────────────────────────────────────────────

    public Vec position() { return body.pos; }
    public Vec velocity() { return body.vel; }
    public Vec acceleration() { return body.acc; }
    public float yaw() { return body.yaw; }
    public float pitch() { return body.pitch; }
    public float bank() { return body.bank; }
    public float lastTurnDeg() { return body.yawRate; }
    public boolean backstopLastStep() { return body.backstopLastStep; }
    public double lastImpact() { return body.lastImpact; }
    public float speedFactor() { return body.speedFactor(); }
    public float burstDrive() { return body.burstDrive; }
    /** A drifter's bell squeeze, 0..1, and its tumble about the axis, degrees (0 for swimmers). */
    public float pulse() { return body instanceof DriftBody d ? d.pulse : 0f; }
    /** A drifter's thrust this tick, blocks/s² (0 for swimmers). */
    public double thrust() { return body instanceof DriftBody d ? d.lastThrust : 0; }
    public float spin() { return body instanceof DriftBody d ? d.spin : 0f; }
    public double radius() { return body.radius; }
    public float length() { return length; }
    public Locomotion locomotion() { return locomotion; }
    /** This class's turn-rate cap, degrees per tick (for invariants and overlays). */
    public float turnRateCap() { return body.g.turnRateDegPerTick(); }
    public float appear() { return appear; }
    public Fade fade() { return fade; }
    public long leashEvents() { return leashEvents; }
    public long backstops() { return body.backstopCount(); }
    public long ticks() { return ticks; }

    // ── Mind, for overlays, tests and the adapter's debug display ──────────────────────────────

    /**
     * The mind's goal — or PERCH while a percher is riding or hopping (its hops aren't mind goals),
     * or a benthic's land mode while it walks (GROUND_IDLE, GROUND_FOLLOW, CATCH_UP).
     */
    public String goal() {
        if (perch != null && perch.mode == PerchBody.Mode.GROUND) return perch.gmind.land.name();
        return perch != null && perch.mode != PerchBody.Mode.SWIM ? "PERCH" : mind.goal.name();
    }
    /** A percher's mode and slot, for overlays (null for other classes). */
    public String perchState() { return perch == null ? null : perch.mode + " " + perch.slot; }
    /** A percher's slot (where it rides or is hopping to), else null. */
    public PerchBody.Slot perchSlot() { return perch == null ? null : perch.slot; }
    /** A percher's mode, else null. */
    public PerchBody.Mode perchMode() { return perch == null ? null : perch.mode; }
    /** A percher's crouch and landing squash, 0..1, for the animator (0 for other classes). */
    public float perchSquash() { return perch == null ? 0f : perch.crouch; }
    // ── On the ground (BENTHIC), for overlays, tests and the animator ──────────────────────────

    private long groundFades;

    /** Whether it walks the ground this tick (or crouches there for a hop). */
    public boolean onGround() { return perch != null && perch.onGround(); }
    /** Its ground body's phase (WALK, CROUCH, AIR, LAND), or null when not on the ground. */
    public String groundPhase() { return onGround() ? perch.ground.phase.name() : null; }
    /** Whether its feet are on the floor (not in the air off a step or a hop): only while on the ground. */
    public boolean feetDown() { return onGround() && perch.ground.grounded(); }
    /** How far behind its owner it is along the way they went, blocks (0 off the ground). */
    public double groundLag() { return perch != null && perch.gmind != null ? perch.gmind.lag : 0; }
    /** Whether it has a way to walk to its owner. */
    public boolean groundReachable() { return perch == null || perch.gmind == null || perch.gmind.reachable; }
    /** Fades onto its perch because it couldn't follow on foot. */
    public long groundFades() { return groundFades; }
    /** Hops it has taken across gaps it couldn't walk. */
    public int gapHops() { return perch != null && perch.gmind != null ? perch.gmind.gapHops : 0; }
    /** The push-off deformation, 0..1 (the tank crawl's shape envelope) — the walk's breath for the animator. */
    public float groundShape() { return onGround() ? perch.ground.shape : 0f; }
    /** A launch or touchdown this tick: velocity jumps there by design. */
    public boolean impulse() { return perch != null && perch.impulse; }
    public double lastImpulse() { return perch == null ? 0 : perch.lastImpulse; }
    /** The owner's trail at their feet (a benthic's way to follow), newest first, for overlays. */
    public java.util.List<Vec> footTrail() {
        java.util.List<Vec> out = new java.util.ArrayList<>();
        for (int i = 0; i < footCrumbs.size(); i++) out.add(footCrumbs.get(i));
        return out;
    }
    /** A percher's own unseen clock (ticks), which sends it to show itself on the hand; −1 for others. */
    public int perchUnseenTicks() { return perch == null ? -1 : perch.unseenTicks; }
    /** Whether a benthic on the ground is coming up alongside its owner to hop to their hand. */
    public boolean comingClose() { return perch != null && perch.gmind != null && perch.gmind.wantClose; }
    /** Dev trigger: a benthic riding its perch goes back down to the ground (Phase 2's dismount, by hand). */
    public void devGround() { if (perch != null) perch.devGround(); }

    /** Dev trigger: as if the owner had idled long enough to settle (see {@link Drives#devDrowsy}). */
    public void devDrowsy() { drives.devDrowsy(); }
    /** Whether a percher is riding its owner this tick (perched, crouching or settling). */
    public boolean riding() { return perch != null && perch.attached(); }
    /** Whether a percher is on its owner: riding, or pushing off or touching down in a hop. */
    public boolean onOwner() { return perch != null && perch.onOwner(); }
    public int hops() { return perch == null ? 0 : perch.hops; }
    public int goalTicks() { return perch != null && perch.mode == PerchBody.Mode.GROUND ? perch.gmind.landTicks : mind.goalTicks; }
    public int goalSwitches() { return mind.goalSwitches; }
    public int showOffs() { return mind.showOffs; }
    public float proximity() { return mind.proximity; }
    public float attention() { return mind.attention; }
    public float utilityBase() { return mind.uBase; }
    public float utilityCatchUp() { return mind.uCatch; }
    public float utilityShowOff() { return mind.uShow; }
    public boolean seen() { return perch != null && perch.mode != PerchBody.Mode.SWIM ? perchSeen : mind.seen; }
    public int unseenTicks() { return mind.unseenTicks; }
    public double ownerDistance() {
        // Off the swim, the mind isn't tracking it: measure it here.
        if (perch != null && perch.mode != PerchBody.Mode.SWIM && lastChest != null) return body.pos.distanceTo(lastChest);
        return mind.ownerDist;
    }
    public boolean ownerMoving() { return mind.ownerMoving; }
    public Vec target() { return perch != null && perch.onGround() ? perch.gmind.target : mind.target; }
    public float curiosity() { return drives.curiosity; }
    public float wariness() { return drives.wariness; }
    public float rest() { return drives.rest; }
    public boolean resting() { return drives.resting; }
    /** Where the thing it's wary of is, or null. */
    public Vec threat() { return drives.threatPos; }
    public int hides() { return mind.hides; }
    public int nuzzles() { return mind.nuzzles; }
    public int inspections() { return mind.inspections; }
    /** What the pose should say this tick, for the animator. */
    public PoseHint poseHint() { return pose; }
    /** What the fish is looking at (an inspected or watched thing), or null. */
    public Vec lookTarget() { return lookTarget; }
    public Temperament temperament() { return temperament; }

    // ── Interpolated pose (after interpolate) ──────────────────────────────────────────────────

    public Vec renderPos() { return renderPos; }
    public float renderYaw() { return renderYaw; }
    public float renderPitch() { return renderPitch; }
    public float renderBank() { return renderBank; }
    public float renderAppear() { return renderAppear; }
    public float renderTailPhase() { return renderTail; }

    /** The voxel window, for renderers that want to draw what the fish perceives. */
    public LocalVoxels voxels() { return voxels; }
}
