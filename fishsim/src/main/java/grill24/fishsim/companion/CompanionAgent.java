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
    private final Body body;
    private final Mind mind;
    private final LocalVoxels voxels = new LocalVoxels();
    private final Breadcrumbs crumbs = new Breadcrumbs();
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
    public CompanionAgent(CompanionTunables t, long seed, float length, OwnerState owner, BlockQuery blocks) {
        this(t, seed, Locomotion.FREE_SWIM, length, owner, blocks);
    }

    /**
     * @param locomotion the species' class: FREE_SWIM or GLIDE so far
     * @param length     rendered body length in blocks; sets the collision radius
     */
    public CompanionAgent(CompanionTunables t, long seed, Locomotion locomotion, float length, OwnerState owner,
                          BlockQuery blocks) {
        this.t = t;
        this.length = length;
        this.locomotion = locomotion;
        double radius = Math.max(0.08, Math.min(0.3, length * 0.3));
        voxels.update(blocks, owner.eye());
        Vec start = besideOwner(owner, radius);
        this.body = new Body(t, locomotion, start, owner.yawDeg(), radius, seed ^ 0x5DEECE66DL);
        this.mind = new Mind(t, body, seed);
        this.prevPos = start;
        this.renderPos = start;
        this.prevYaw = renderYaw = owner.yawDeg();
    }

    public void step(OwnerState owner, BlockQuery blocks) {
        prevPos = body.pos;
        prevYaw = body.yaw;
        prevPitch = body.pitch;
        prevBank = body.bank;
        prevAppear = appear;
        prevTail = body.tailPhase;
        ticks++;

        voxels.update(blocks, owner.eye());
        if (owner.teleported()) crumbs.clear();
        crumbs.record(owner.chest());

        if (fade == Fade.NONE && (owner.teleported() || body.pos.distanceTo(owner.chest()) > t.leashGap)) {
            fade = Fade.OUT;
        }

        Vec desired = mind.decide(owner, voxels, crumbs);
        body.step(desired, mind.wanderScale, mind.ownerAvoidance(owner), voxels, Capsule.ownerBody(owner));

        switch (fade) {
            case OUT -> {
                appear = Math.max(0f, appear - 1f / FADE_TICKS);
                if (appear == 0f) {
                    leashEvents++;
                    slowTicks = 0;
                    if (owner.velocity().length() > HIDE_SPEED * t.catchUpSpeed) {
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

    private void reappear(OwnerState owner) {
        place(owner);
        fade = Fade.IN;
    }

    /** Puts the body beside the owner, at rest, with no interpolation across the jump. */
    private void place(OwnerState owner) {
        Vec p = besideOwner(owner, body.radius);
        body.placeAt(p, owner.yawDeg());
        mind.reset(owner);
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
    public double radius() { return body.radius; }
    public float length() { return length; }
    public Locomotion locomotion() { return locomotion; }
    /** This class's turn-rate cap, degrees per tick (for invariants and overlays). */
    public float turnRateCap() { return body.g.turnRateDegPerTick(); }
    public float appear() { return appear; }
    public Fade fade() { return fade; }
    public long leashEvents() { return leashEvents; }
    public long backstops() { return body.backstops; }
    public long ticks() { return ticks; }

    // ── Mind, for overlays, tests and the adapter's debug display ──────────────────────────────

    public String goal() { return mind.goal.name(); }
    public int goalTicks() { return mind.goalTicks; }
    public int goalSwitches() { return mind.goalSwitches; }
    public int showOffs() { return mind.showOffs; }
    public float proximity() { return mind.proximity; }
    public float attention() { return mind.attention; }
    public float utilityBase() { return mind.uBase; }
    public float utilityCatchUp() { return mind.uCatch; }
    public float utilityShowOff() { return mind.uShow; }
    public boolean seen() { return mind.seen; }
    public int unseenTicks() { return mind.unseenTicks; }
    public double ownerDistance() { return mind.ownerDist; }
    public boolean ownerMoving() { return mind.ownerMoving; }
    public Vec target() { return mind.target; }

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
