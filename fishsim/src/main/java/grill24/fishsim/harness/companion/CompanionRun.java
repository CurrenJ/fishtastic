package grill24.fishsim.harness.companion;

import grill24.fishsim.companion.CompanionAgent;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.companion.CompanionTunables;
import grill24.fishsim.companion.LocalVoxels;
import grill24.fishsim.companion.OwnerState;
import grill24.fishsim.companion.Vec;
import grill24.fishsim.companion.Visibility;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One headless companion run: a scripted owner in a scene, an agent following them, and a
 * per-tick record the invariants and diagnostics are computed from (docs/fish-companions-sim-plan.md §4).
 */
public final class CompanionRun {

    /** Default fish length for runs, blocks. */
    public static final float LENGTH = 0.4f;

    /** A typical rendered length for a companion of each class, blocks. */
    public static float lengthOf(Locomotion c) {
        return switch (c) {
            case GLIDE -> 0.7f;
            case DRIFT -> 0.35f;
            default -> LENGTH;
        };
    }

    public record Sample(OwnerState owner, Vec pos, Vec vel, Vec acc, float yaw, float pitch, float bank,
                         float turn, String goal, boolean seen, double ownerDist, float appear,
                         CompanionAgent.Fade fade, boolean backstop, double impact, int goalTicks, float pulse, double thrust) {}

    public final VoxelScene scene;
    public final OwnerScript script;
    public final CompanionAgent agent;
    public final CompanionTunables tunables;
    public OwnerState owner;
    public final List<Sample> samples = new ArrayList<>();
    private final boolean record;

    public CompanionRun(String scriptName, long seed, CompanionTunables tunables, boolean record) {
        this(scriptName, seed, Locomotion.FREE_SWIM, tunables, record);
    }

    public CompanionRun(String scriptName, long seed, Locomotion locomotion, CompanionTunables tunables, boolean record) {
        this(scriptName, seed, locomotion, lengthOf(locomotion), tunables, record);
    }

    public CompanionRun(String scriptName, long seed, Locomotion locomotion, float length, CompanionTunables tunables,
                        boolean record) {
        this.script = OwnerScript.named(scriptName, seed);
        this.scene = script.scene;
        this.tunables = tunables;
        this.record = record;
        this.owner = script.next();
        this.agent = new CompanionAgent(tunables, seed, locomotion, length, owner, scene);
    }

    public void tick() {
        owner = script.next();
        agent.step(owner, scene);
        if (record) {
            samples.add(new Sample(owner, agent.position(), agent.velocity(), agent.acceleration(),
                    agent.yaw(), agent.pitch(), agent.bank(), agent.lastTurnDeg(), agent.goal(),
                    agent.seen(), agent.ownerDistance(), agent.appear(), agent.fade(), agent.backstopLastStep(), agent.lastImpact(), agent.goalTicks(), agent.pulse(), agent.thrust()));
        }
    }

    public void run(int ticks) {
        for (int i = 0; i < ticks; i++) tick();
    }

    // ── Metrics ────────────────────────────────────────────────────────────────────────────────

    /** Everything the invariant tests check and the headless export prints, from one run. */
    public record Metrics(
            int ticks,
            int solidPenetrations,
            long backstops,
            double maxImpact,
            double maxAccel,
            double maxJerk,
            double maxTurnDeg,
            double maxFacingErrorDeg,
            double tailFirstFraction,
            double seenFraction,
            double longestUnseenSeconds,
            int insideOwnerTicks,
            int nearClipTicks,
            int longestCentreDwellTicks,
            long leashEvents,
            double longestFarSeconds,
            double meanSpeed,
            double speedCv,
            double goalSwitchesPerMinute,
            int shortestNonEmergencyGoalTicks,
            Map<String, Double> goalShare) {

        public String summary() {
            return String.format(
                    "pen=%d backstop=%d impact=%.2f acc=%.2f jerk=%.1f turn=%.1f° faceErr=%.1f° tailFirst=%.1f%% seen=%.0f%% maxUnseen=%.1fs "
                            + "inOwner=%d nearClip=%d centreDwell=%d leash=%d far=%.1fs speed=%.2f cv=%.2f switches/min=%.1f "
                            + "minGoal=%d goals=%s",
                    solidPenetrations, backstops, maxImpact, maxAccel, maxJerk, maxTurnDeg, maxFacingErrorDeg,
                    tailFirstFraction * 100,
                    seenFraction * 100, longestUnseenSeconds, insideOwnerTicks, nearClipTicks,
                    longestCentreDwellTicks, leashEvents, longestFarSeconds, meanSpeed, speedCv, goalSwitchesPerMinute,
                    shortestNonEmergencyGoalTicks, goalShareString());
        }

        private String goalShareString() {
            StringBuilder b = new StringBuilder();
            goalShare.forEach((k, v) -> b.append(k, 0, Math.min(5, k.length())).append(':')
                    .append(Math.round(v * 100)).append("% "));
            return b.toString().trim();
        }
    }

    /** Computes the metrics over the recorded samples (requires {@code record = true}). */
    public Metrics metrics() {
        LocalVoxels truth = new LocalVoxels();
        double r = agent.radius();
        double dt = CompanionAgent.DT;
        int pen = 0, insideOwner = 0, nearClip = 0, seenTicks = 0, movingTicks = 0, tailFirstTicks = 0;
        int unseenRun = 0, longestUnseen = 0, centreRun = 0, longestCentre = 0, farRun = 0, longestFar = 0;
        double maxImpact = 0;
        double maxAcc = 0, maxJerk = 0, maxTurn = 0, maxFace = 0;
        double sumSpeed = 0, sumSpeed2 = 0;
        int speedN = 0;
        Map<String, Integer> goalTicks = new LinkedHashMap<>();
        int switches = 0, shortest = Integer.MAX_VALUE, prevGoalTicks = -1;
        String prevGoal = null;
        Vec prevAcc = null;
        boolean prevContact = false;
        for (Sample s : samples) {
            truth.update(scene, s.owner().eye());
            if (!truth.sphereClear(s.pos(), r * 0.999)) pen++;
            maxImpact = Math.max(maxImpact, s.impact());
            boolean settled = s.fade() == CompanionAgent.Fade.NONE;
            // A backstop tick is a collision (its velocity change is the impact, counted above), and
            // the tick after it re-adds the blocked component; neither is the body's own motion.
            boolean contact = s.backstop() || prevContact;
            prevContact = s.backstop();
            if (settled && !contact) {
                maxAcc = Math.max(maxAcc, s.acc().length());
                // A backstop stop is a collision, counted on its own; it isn't the body's own jerk.
                if (prevAcc != null && !s.backstop()) maxJerk = Math.max(maxJerk, s.acc().sub(prevAcc).length() / dt);
                maxTurn = Math.max(maxTurn, Math.abs(s.turn()));
                // Travelling with its front more than 100° from the way it's going: a swimmer going
                // backwards, or a bell pulsing tentacles-first. A bell sinking between pulses is
                // just sinking, so for a drifter only ticks with real thrust count.
                boolean propelled = agent.locomotion() != Locomotion.DRIFT || s.thrust() > 1.0;
                if (s.vel().length() > 1.0 && propelled) {
                    movingTicks++;
                    if (Vec.fromYawPitch(s.yaw(), s.pitch()).dot(s.vel().normalize()) < -0.17) tailFirstTicks++;
                }
                if (s.vel().horizontalLength() > 0.5) {
                    float travel = (float) s.vel().yawDeg();
                    maxFace = Math.max(maxFace, Math.abs(wrap(travel - s.yaw())));
                }
                double sp = s.vel().length();
                sumSpeed += sp;
                sumSpeed2 += sp * sp;
                speedN++;
                if (insideBody(s.owner(), s.pos())) insideOwner++;
                if (s.pos().distanceTo(s.owner().camera()) < Visibility.NEAR + r) nearClip++;
            }
            prevAcc = settled && !contact ? s.acc() : null;

            if (s.seen()) {
                seenTicks++;
                unseenRun = 0;
            } else {
                longestUnseen = Math.max(longestUnseen, ++unseenRun);
            }
            boolean centred = s.pos().distanceTo(s.owner().camera()) < 2.5
                    && Visibility.angleFromCenterDeg(s.owner(), s.pos()) < 8;
            centreRun = centred ? centreRun + 1 : 0;
            longestCentre = Math.max(longestCentre, centreRun);

            farRun = settled && s.ownerDist() > tunables.farGap ? farRun + 1 : 0;
            longestFar = Math.max(longestFar, farRun);

            goalTicks.merge(s.goal(), 1, Integer::sum);
            if (prevGoal != null && !prevGoal.equals(s.goal())) {
                switches++;
                // Commitment applies to everything but a catch-up taking over in an emergency, the
                // end of a show-off (finishing, not dithering), and loiter/escort swaps (those
                // follow the owner starting and stopping, through its own hysteresis). The length
                // is the mind's own goal clock. A fade's reset (goal clock 0 on the switching tick;
                // a decision has already counted 1) re-places the fish rather than deciding.
                boolean baseSwap = isBase(prevGoal) && isBase(s.goal());
                boolean reset = s.goalTicks() == 0;
                if (!s.goal().equals("CATCH_UP") && !prevGoal.equals("SHOW_OFF") && !baseSwap && !reset
                        && prevGoalTicks >= 0) {
                    shortest = Math.min(shortest, prevGoalTicks);
                }
            }
            prevGoalTicks = s.goalTicks();
            prevGoal = s.goal();
        }
        int n = samples.size();
        double mean = speedN == 0 ? 0 : sumSpeed / speedN;
        double var = speedN == 0 ? 0 : Math.max(0, sumSpeed2 / speedN - mean * mean);
        Map<String, Double> share = new LinkedHashMap<>();
        goalTicks.forEach((k, v) -> share.put(k, v / (double) n));
        return new Metrics(n, pen, agent.backstops(), maxImpact, maxAcc, maxJerk, maxTurn, maxFace,
                movingTicks == 0 ? 0 : tailFirstTicks / (double) movingTicks,
                n == 0 ? 0 : seenTicks / (double) n, longestUnseen * dt, insideOwner, nearClip, longestCentre,
                agent.leashEvents(), longestFar * dt, mean, mean > 0 ? Math.sqrt(var) / mean : 0,
                n == 0 ? 0 : switches / (n * dt / 60.0),
                shortest == Integer.MAX_VALUE ? -1 : shortest, share);
    }

    private static boolean isBase(String goal) {
        return goal.equals("LOITER") || goal.equals("ESCORT");
    }

    /** Whether {@code p} is inside the owner's body capsule. */
    static boolean insideBody(OwnerState o, Vec p) {
        Vec a = o.feet().add(0, OwnerState.BODY_RADIUS, 0);
        Vec b = o.eye().add(0, 0.1, 0);
        Vec ab = b.sub(a);
        double s = Math.max(0, Math.min(1, p.sub(a).dot(ab) / ab.lengthSqr()));
        return p.distanceTo(a.add(ab.scale(s))) < OwnerState.BODY_RADIUS;
    }

    private static float wrap(float deg) {
        deg %= 360f;
        if (deg >= 180f) deg -= 360f;
        if (deg < -180f) deg += 360f;
        return deg;
    }
}
