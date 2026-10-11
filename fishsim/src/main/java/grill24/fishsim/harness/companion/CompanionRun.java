package grill24.fishsim.harness.companion;

import grill24.fishsim.companion.CompanionAgent;
import grill24.fishsim.companion.CompanionGroup;
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
            case BENTHIC -> 0.3f;
            case ANCHORED -> 0.45f;
            default -> LENGTH;
        };
    }

    public record Sample(OwnerState owner, Vec pos, Vec vel, Vec acc, float yaw, float pitch, float bank,
                         float turn, String goal, boolean seen, double ownerDist, float appear,
                         CompanionAgent.Fade fade, boolean backstop, double impact, int goalTicks, float pulse, double thrust,
                         boolean riding) {}

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
        this(scriptName, seed, locomotion, length, tunables, record, false);
    }

    /** A trio of small free swimmers (spec §2): the agent is the leader; followers are recorded too. */
    public static CompanionRun trio(String scriptName, long seed, CompanionTunables tunables, boolean record) {
        return new CompanionRun(scriptName, seed, Locomotion.FREE_SWIM, TRIO_LENGTH, tunables, record, true);
    }

    /** A typical rendered length for a trio member, blocks (a fish under 20 cm). */
    public static final float TRIO_LENGTH = 0.18f;

    private CompanionRun(String scriptName, long seed, Locomotion locomotion, float length, CompanionTunables tunables,
                         boolean record, boolean trio) {
        this.script = OwnerScript.named(scriptName, seed);
        this.scene = script.scene;
        this.tunables = tunables;
        this.record = record;
        this.owner = script.next();
        if (trio) {
            this.group = new CompanionGroup(tunables, seed, length, owner, scene);
            this.agent = group.leader();
        } else {
            this.group = null;
            this.agent = new CompanionAgent(tunables, seed, locomotion, length, owner, scene);
        }
    }

    /** One follower's tick, for the trio's own invariants. */
    public record FollowerSample(Vec pos, Vec vel, Vec acc, float yaw, float pitch, float turn, boolean backstop,
                                 double impact, float appear) {}

    /** The trio, or null for a solo companion. */
    public final CompanionGroup group;
    public final List<FollowerSample[]> followerSamples = new ArrayList<>();

    public void tick() {
        owner = script.next();
        if (group != null) {
            group.step(owner, scene);
            if (record) {
                FollowerSample[] fs = new FollowerSample[2];
                for (int i = 0; i < 2; i++) {
                    CompanionGroup.Follower f = group.follower(i);
                    fs[i] = new FollowerSample(f.position(), f.velocity(), f.acceleration(), f.yaw(), f.pitch(),
                            f.lastTurnDeg(), f.backstopLastStep(), f.lastImpact(), f.appear());
                }
                followerSamples.add(fs);
            }
        } else {
            agent.step(owner, scene);
        }
        if (record) {
            samples.add(new Sample(owner, agent.position(), agent.velocity(), agent.acceleration(),
                    agent.yaw(), agent.pitch(), agent.bank(), agent.lastTurnDeg(), agent.goal(),
                    agent.seen(), agent.ownerDistance(), agent.appear(), agent.fade(), agent.backstopLastStep(), agent.lastImpact(), agent.goalTicks(), agent.pulse(), agent.thrust(), agent.onOwner()));
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

    /** What the trio's followers did: the same floor as the leader, plus keeping together. */
    public record FollowerMetrics(int penetrations, long backstops, double maxImpact, double maxAccel, double maxJerk,
                                  double maxTurnDeg, double tailFirstFraction, double minSeparation,
                                  double overlapFraction, double bodyOverlapFraction, double longestStraggleSeconds,
                                  long leashEvents) {
        public String summary() {
            return String.format("followers: pen=%d backstop=%d impact=%.2f acc=%.2f jerk=%.1f turn=%.1f° tailFirst=%.1f%% "
                            + "minSep=%.3f overlap=%.2f%% bodyOverlap=%.2f%% straggle=%.1fs leash=%d",
                    penetrations, backstops, maxImpact, maxAccel, maxJerk, maxTurnDeg, tailFirstFraction * 100,
                    minSeparation, overlapFraction * 100, bodyOverlapFraction * 100, longestStraggleSeconds, leashEvents);
        }
    }

    /** A fish as the renderer draws it: nose to tail along its heading, about this wide per unit length. */
    static final double BODY_HALF_LENGTH = 0.4, BODY_HALF_WIDTH = 0.12;

    static Vec[] fishSegment(Vec center, float yaw, float pitch, float length) {
        Vec half = Vec.fromYawPitch(yaw, pitch).scale(BODY_HALF_LENGTH * length);
        return new Vec[]{center.sub(half), center.add(half)};
    }

    /** The shortest distance between segments {@code p} and {@code q}. */
    static double segmentDistance(Vec[] p, Vec[] q) {
        Vec d1 = p[1].sub(p[0]), d2 = q[1].sub(q[0]), r = p[0].sub(q[0]);
        double a = d1.dot(d1), e = d2.dot(d2), f = d2.dot(r);
        double s, t;
        if (a < 1e-12 && e < 1e-12) return p[0].distanceTo(q[0]);
        if (a < 1e-12) {
            s = 0;
            t = clamp01(f / e);
        } else {
            double c = d1.dot(r);
            if (e < 1e-12) {
                t = 0;
                s = clamp01(-c / a);
            } else {
                double b = d1.dot(d2), denom = a * e - b * b;
                s = denom > 1e-12 ? clamp01((b * f - c * e) / denom) : 0;
                t = (b * s + f) / e;
                if (t < 0) {
                    t = 0;
                    s = clamp01(-c / a);
                } else if (t > 1) {
                    t = 1;
                    s = clamp01((b - c) / a);
                }
            }
        }
        return p[0].add(d1.scale(s)).distanceTo(q[0].add(d2.scale(t)));
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : Math.min(1, v);
    }

    /** Distance from the leader, blocks, beyond which a follower counts as straggling. */
    public static final double STRAGGLE = 2.5;

    /** The followers' metrics over the recorded run, or null for a solo companion. */
    public FollowerMetrics followerMetrics() {
        if (group == null) return null;
        LocalVoxels truth = new LocalVoxels();
        double dt = CompanionAgent.DT;
        int pen = 0, moving = 0, tailFirst = 0, together = 0, overlapping = 0, bodiesOverlapping = 0;
        double touch = 2 * group.follower(0).radius();
        double maxImpact = 0, maxAcc = 0, maxJerk = 0, maxTurn = 0, minSep = Double.MAX_VALUE;
        int[] straggle = new int[2];
        int longest = 0;
        Vec[] prevAcc = new Vec[2];
        boolean[] prevContact = new boolean[2];
        for (int k = 0; k < followerSamples.size(); k++) {
            FollowerSample[] fs = followerSamples.get(k);
            Sample leaderS = samples.get(k);
            truth.update(scene, leaderS.owner().eye());
            boolean allIn = leaderS.appear() >= 1f;
            for (int i = 0; i < 2; i++) {
                FollowerSample f = fs[i];
                if (!truth.sphereClear(f.pos(), group.follower(i).radius() * 0.999)) pen++;
                maxImpact = Math.max(maxImpact, f.impact());
                boolean settled = f.appear() >= 1f && leaderS.fade() == CompanionAgent.Fade.NONE;
                allIn &= f.appear() >= 1f;
                boolean contact = f.backstop() || prevContact[i];
                prevContact[i] = f.backstop();
                if (settled && !contact) {
                    maxAcc = Math.max(maxAcc, f.acc().length());
                    if (prevAcc[i] != null) maxJerk = Math.max(maxJerk, f.acc().sub(prevAcc[i]).length() / dt);
                    maxTurn = Math.max(maxTurn, Math.abs(f.turn()));
                    if (f.vel().length() > 1.0) {
                        moving++;
                        if (Vec.fromYawPitch(f.yaw(), f.pitch()).dot(f.vel().normalize()) < -0.17) tailFirst++;
                    }
                }
                prevAcc[i] = settled && !contact ? f.acc() : null;
                straggle[i] = settled && f.pos().distanceTo(leaderS.pos()) > STRAGGLE ? straggle[i] + 1 : 0;
                longest = Math.max(longest, straggle[i]);
            }
            if (allIn) {
                // Two bodies closer than their radii combined are drawn through each other.
                double sep = fs[0].pos().distanceTo(fs[1].pos());
                for (FollowerSample f : fs) sep = Math.min(sep, f.pos().distanceTo(leaderS.pos()));
                minSep = Math.min(minSep, sep);
                together++;
                if (sep < touch) overlapping++;
                // As drawn: long thin bodies, which side by side can be far closer than two spheres.
                Vec[] l = fishSegment(leaderS.pos(), leaderS.yaw(), leaderS.pitch(), group.leader().length());
                Vec[] a = fishSegment(fs[0].pos(), fs[0].yaw(), fs[0].pitch(), group.leader().length());
                Vec[] b = fishSegment(fs[1].pos(), fs[1].yaw(), fs[1].pitch(), group.leader().length());
                double bodyGap = Math.min(segmentDistance(l, a), Math.min(segmentDistance(l, b), segmentDistance(a, b)));
                if (bodyGap < 2 * BODY_HALF_WIDTH * group.leader().length()) bodiesOverlapping++;
            }
        }
        return new FollowerMetrics(pen, group.follower(0).backstops() + group.follower(1).backstops(), maxImpact,
                maxAcc, maxJerk, maxTurn, moving == 0 ? 0 : tailFirst / (double) moving,
                minSep == Double.MAX_VALUE ? -1 : minSep, together == 0 ? 0 : overlapping / (double) together,
                together == 0 ? 0 : bodiesOverlapping / (double) together, longest * dt,
                group.follower(0).leashEvents() + group.follower(1).leashEvents());
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
            // On its owner (riding, or pushing off and touching down), a percher is part of their
            // silhouette, as a parrot is: where it is, how it moves and how close it is to the
            // camera are the owner's, not its own.
            boolean riding = s.riding();
            if (!riding && !truth.sphereClear(s.pos(), r * 0.999)) pen++;
            maxImpact = Math.max(maxImpact, s.impact());
            boolean settled = s.fade() == CompanionAgent.Fade.NONE && !riding;
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
                // A percher going into or out of the water swaps bodies; that isn't the mind deciding.
                boolean perchSwap = prevGoal.equals("PERCH") || s.goal().equals("PERCH");
                if (!s.goal().equals("CATCH_UP") && !prevGoal.equals("SHOW_OFF") && !baseSwap && !reset && !perchSwap
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
