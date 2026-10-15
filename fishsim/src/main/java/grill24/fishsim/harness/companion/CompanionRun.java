package grill24.fishsim.harness.companion;

import grill24.fishsim.companion.CompanionAgent;
import grill24.fishsim.companion.CompanionGroup;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.companion.CompanionTunables;
import grill24.fishsim.companion.CompanionWorld;
import grill24.fishsim.companion.DriftStyle;
import grill24.fishsim.companion.GroundGait;
import grill24.fishsim.companion.LocalVoxels;
import grill24.fishsim.companion.OwnerState;
import grill24.fishsim.companion.Temperament;
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
                         boolean riding, String pose, float curiosity,
                         boolean impulse, double impulseSize, boolean onGround, boolean feetDown, double lag,
                         boolean reachable, boolean doorClosed, boolean tumbling, String land,
                         Vec asked, boolean strokeStart, float flare, float tilt, float tiltRate, Vec propulsion,
                         double mantleThrust, boolean bodyPerch) {}

    public final VoxelScene scene;
    public final OwnerScript script;
    public final CompanionAgent agent;
    public final CompanionTunables tunables;
    public OwnerState owner;
    /** What the companion perceives this tick: the owner, the scene, and the script's points of interest. */
    public CompanionWorld world;
    public final List<Sample> samples = new ArrayList<>();
    private final boolean record;
    private String worldScene;
    private Vec featureAnchor;
    private long worldTick;
    private long screenTicks, reactionTicks;
    public void worldScene(String name) { worldScene(name, owner.feet()); }
    /** A scene whose features sit relative to {@code anchor} rather than the owner's starting feet. */
    public void worldScene(String name, Vec anchor) {
        if (!WorldScenes.NAMES.contains(name)) throw new IllegalArgumentException(name);
        worldScene = name; featureAnchor = anchor; agent.habitat(WorldScenes.habitat(name));
        if (name.equals("water")) scene.addWorldWater(featureAnchor.add(0.7,0,2.5));
    }
    /** Share of visible ticks carrying a distinct pose, emote or active Notice performance. */
    public double reactionShare() { return screenTicks == 0 ? 0 : (double) reactionTicks / screenTicks; }


    public CompanionRun(String scriptName, long seed, CompanionTunables tunables, boolean record) {
        this(scriptName, seed, Locomotion.FREE_SWIM, tunables, record);
    }

    public CompanionRun(String scriptName, long seed, Locomotion locomotion, CompanionTunables tunables, boolean record) {
        this(scriptName, seed, locomotion, lengthOf(locomotion), tunables, record);
    }

    public CompanionRun(String scriptName, long seed, Locomotion locomotion, float length, CompanionTunables tunables,
                        boolean record) {
        this(scriptName, seed, locomotion, length, Temperament.BASELINE, tunables, record, false);
    }

    /** A benthic of a given size, temperament and ground gait (docs/fish-companions-benthic-follow.md). */
    public static CompanionRun benthic(String scriptName, long seed, float length, Temperament temperament, GroundGait gait,
                                       CompanionTunables tunables, boolean record) {
        return new CompanionRun(scriptName, seed, Locomotion.BENTHIC, length, temperament, gait, tunables, record);
    }

    private CompanionRun(String scriptName, long seed, Locomotion locomotion, float length, Temperament temperament,
                         GroundGait gait, CompanionTunables tunables, boolean record) {
        this.script = OwnerScript.named(scriptName, seed);
        this.scene = script.scene;
        this.tunables = tunables;
        this.record = record;
        this.world = script.nextWorld();
        this.owner = world.owner();
        this.group = null;
        this.agent = new CompanionAgent(tunables, seed, locomotion, length, temperament, gait, world);
    }

    /** A solo companion of the given temperament (spec §4). */
    public CompanionRun(String scriptName, long seed, Locomotion locomotion, Temperament temperament,
                        CompanionTunables tunables, boolean record) {
        this(scriptName, seed, locomotion, lengthOf(locomotion), temperament, tunables, record, false);
    }

    /** A trio whose leader has the given temperament. */
    public static CompanionRun trio(String scriptName, long seed, Temperament temperament, CompanionTunables tunables,
                                    boolean record) {
        return new CompanionRun(scriptName, seed, Locomotion.FREE_SWIM, TRIO_LENGTH, temperament, tunables, record, true);
    }

    /** A trio of small free swimmers (spec §2): the agent is the leader; followers are recorded too. */
    public static CompanionRun trio(String scriptName, long seed, CompanionTunables tunables, boolean record) {
        return new CompanionRun(scriptName, seed, Locomotion.FREE_SWIM, TRIO_LENGTH, Temperament.BASELINE, tunables, record, true);
    }

    /** A typical rendered length for a trio member, blocks (a fish under 20 cm). */
    public static final float TRIO_LENGTH = 0.18f;

    /** Party-built group member lengths, leader first, blocks (docs/fish-companions-party.md §3; true to scale). */
    public static final float[] SCHOOL_MIXED = {0.09f, 0.16f, 0.24f}, SCHOOL_PAIR = {0.16f, 0.24f},
            SCHOOL_BIG = {0.25f, 0.40f, 0.60f}, STARFISH_PAIR = {0.30f, 0.45f}, SLUG_CROWD_MIXED = {0.08f, 0.12f, 0.18f};

    /**
     * A school built from explicit party members (docs/fish-companions-party.md §3): {@code
     * lengths[0]} is the leader's body length, the rest the followers'. Three equal lengths of
     * {@link #TRIO_LENGTH} are exactly {@link #trio}.
     */
    public static CompanionRun school(String scriptName, long seed, float[] lengths, Temperament temperament,
                                      CompanionTunables tunables, boolean record) {
        return new CompanionRun(scriptName, seed, lengths[0], lengths, temperament, tunables, record);
    }

    private CompanionRun(String scriptName, long seed, float length, float[] lengths, Temperament temperament,
                         CompanionTunables tunables, boolean record) {
        this.script = OwnerScript.named(scriptName, seed);
        this.scene = script.scene;
        this.tunables = tunables;
        this.record = record;
        this.world = script.nextWorld();
        this.owner = world.owner();
        this.group = new CompanionGroup(tunables, seed, lengths, temperament, world);
        this.agent = group.leader();
    }

    /** A drifter of the given style (docs/fish-companions-drift.md §2). */
    public static CompanionRun drifter(String scriptName, long seed, DriftStyle style, Temperament temperament,
                                       CompanionTunables tunables, boolean record) {
        return new CompanionRun(scriptName, seed, Locomotion.DRIFT, lengthOf(Locomotion.DRIFT), temperament, tunables,
                record, false, style);
    }

    private CompanionRun(String scriptName, long seed, Locomotion locomotion, float length, Temperament temperament,
                         CompanionTunables tunables,
                         boolean record, boolean trio) {
        this(scriptName, seed, locomotion, length, temperament, tunables, record, trio, DriftStyle.BELL);
    }

    private CompanionRun(String scriptName, long seed, Locomotion locomotion, float length, Temperament temperament,
                         CompanionTunables tunables,
                         boolean record, boolean trio, DriftStyle driftStyle) {
        this.script = OwnerScript.named(scriptName, seed);
        this.scene = script.scene;
        this.tunables = tunables;
        this.record = record;
        this.world = script.nextWorld();
        this.owner = world.owner();
        if (trio) {
            this.group = new CompanionGroup(tunables, seed, length, temperament, world);
            this.agent = group.leader();
        } else {
            this.group = null;
            // A benthic walks the ground (docs/fish-companions-benthic-follow.md), with the crawl.
            this.agent = new CompanionAgent(tunables, seed, locomotion, length, temperament,
                    locomotion == Locomotion.BENTHIC ? GroundGait.CRAWL : null, driftStyle, world);
        }
    }

    /** One follower's tick, for the trio's own invariants. */
    public record FollowerSample(Vec pos, Vec vel, Vec acc, float yaw, float pitch, float turn, boolean backstop,
                                 double impact, float appear) {}

    /** The trio, or null for a solo companion. */
    public final CompanionGroup group;
    public final List<FollowerSample[]> followerSamples = new ArrayList<>();

    private int sceneVersion;

    public void tick() {
        world = WorldScenes.apply(script.nextWorld(), worldScene, featureAnchor, worldTick++);
        owner = world.owner();
        // A door shuts only once nothing is in the doorway; any block change refreshes what's cached.
        scene.applyDoor(d -> doorClear(d));
        if (scene.version() != sceneVersion) {
            sceneVersion = scene.version();
            agent.voxels().invalidate();
        }
        if (group != null) {
            group.step(world);
            if (record) {
                FollowerSample[] fs = new FollowerSample[group.followerCount()];
                for (int i = 0; i < fs.length; i++) {
                    CompanionGroup.Follower f = group.follower(i);
                    fs[i] = new FollowerSample(f.position(), f.velocity(), f.acceleration(), f.yaw(), f.pitch(),
                            f.lastTurnDeg(), f.backstopLastStep(), f.lastImpact(), f.appear());
                }
                followerSamples.add(fs);
            }
        } else {
            agent.step(world);
        }
        if (seenNow()) { screenTicks++; if (agent.reacting()) reactionTicks++; }
        if (record) {
            samples.add(new Sample(owner, agent.position(), agent.velocity(), agent.acceleration(),
                    agent.yaw(), agent.pitch(), agent.bank(), agent.lastTurnDeg(), agent.goal(),
                    seenNow(), agent.ownerDistance(), agent.appear(), agent.fade(), agent.backstopLastStep(), agent.lastImpact(), agent.goalTicks(), agent.pulse(), agent.thrust(), agent.onOwner(),
                    agent.poseHint().name(), agent.curiosity(), agent.impulse(), agent.lastImpulse(),
                    agent.onGround(), agent.feetDown(), agent.groundLag(), agent.groundReachable(), scene.doorClosed(),
                    agent.tumbling(), agent.landMode(), agent.driftAsked(), agent.driftStrokeStart(),
                    agent.flare(), agent.driftTilt(), agent.driftTiltRate(), agent.driftPropulsion(), agent.mantleThrust(),
                    agent.riding() && !agent.perchSlot().hand()));
        }
    }

    /** Whether the companion (and the owner) are clear of the door's cells. */
    private boolean doorClear(int[] d) {
        double r = agent.radius();
        Vec p = agent.position();
        boolean fish = p.x() + r < d[0] || p.x() - r > d[3] || p.y() + r < d[1] || p.y() - r > d[4]
                || p.z() + r < d[2] || p.z() - r > d[5];
        Vec f = owner.feet();
        boolean own = f.x() + 0.3 < d[0] || f.x() - 0.3 > d[3] || f.z() + 0.3 < d[2] || f.z() - 0.3 > d[5];
        return fish && own;
    }

    /**
     * Whether the owner can see the fish where it is now, against this tick's camera: the harness's
     * own judgement, independent of what the mind believes, so the metric means the same thing
     * whatever the mind does.
     */
    private boolean seenNow() {
        return Visibility.visible(owner, agent.position(), agent.voxels());
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

    /**
     * The school's formation shape over the recorded run (docs/fish-companions-party.md §8): spacing,
     * overtakes, lag and contact, each with its rate. Null for a solo companion. A member counts on a
     * tick only when fully appeared.
     */
    public GroupShapeMetrics.Result schoolShape() {
        if (group == null) return null;
        int n = group.followerCount() + 1;
        float[] lengths = new float[n];
        double[] radii = new double[n];
        lengths[0] = group.leaderLength();
        radii[0] = Math.max(0.08, Math.min(0.3, lengths[0] * 0.3));
        for (int i = 1; i < n; i++) {
            lengths[i] = group.follower(i - 1).length();
            radii[i] = group.follower(i - 1).radius();
        }
        GroupShapeMetrics m = new GroupShapeMetrics(lengths, radii);
        Vec[] pos = new Vec[n];
        boolean[] active = new boolean[n];
        for (int k = 0; k < followerSamples.size(); k++) {
            Sample s = samples.get(k);
            FollowerSample[] fs = followerSamples.get(k);
            pos[0] = s.pos();
            active[0] = s.appear() >= 1f;
            for (int i = 1; i < n; i++) {
                pos[i] = fs[i - 1].pos();
                active[i] = fs[i - 1].appear() >= 1f;
            }
            m.tick(pos, s.vel(), active);
        }
        return m.result();
    }

    /** The followers' metrics over the recorded run, or null for a solo companion. */
    public FollowerMetrics followerMetrics() {
        if (group == null) return null;
        LocalVoxels truth = new LocalVoxels();
        double dt = CompanionAgent.DT;
        int pen = 0, moving = 0, tailFirst = 0, together = 0, overlapping = 0, bodiesOverlapping = 0;
        int n = group.followerCount();
        // Members' sphere radii and body lengths, leader first; the leader's radius by the followers'
        // own rule, so a school of identical fish measures exactly as the trio always did.
        double[] radius = new double[n + 1];
        float[] length = new float[n + 1];
        length[0] = group.leaderLength();
        radius[0] = Math.max(0.08, Math.min(0.3, length[0] * 0.3));
        for (int i = 0; i < n; i++) {
            radius[i + 1] = group.follower(i).radius();
            length[i + 1] = group.follower(i).length();
        }
        double maxImpact = 0, maxAcc = 0, maxJerk = 0, maxTurn = 0, minSep = Double.MAX_VALUE;
        int[] straggle = new int[n];
        int longest = 0;
        Vec[] prevAcc = new Vec[n];
        boolean[] prevContact = new boolean[n];
        for (int k = 0; k < followerSamples.size(); k++) {
            FollowerSample[] fs = followerSamples.get(k);
            Sample leaderS = samples.get(k);
            truth.update(scene, leaderS.owner().eye());
            boolean allIn = leaderS.appear() >= 1f;
            for (int i = 0; i < n; i++) {
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
                // Two bodies closer than their radii combined are drawn through each other; and as
                // drawn, long thin bodies side by side can be far closer than two spheres. Each pair
                // by its own sizes.
                Vec[] pos = new Vec[n + 1];
                Vec[][] seg = new Vec[n + 1][];
                pos[0] = leaderS.pos();
                seg[0] = fishSegment(leaderS.pos(), leaderS.yaw(), leaderS.pitch(), length[0]);
                for (int i = 0; i < n; i++) {
                    pos[i + 1] = fs[i].pos();
                    seg[i + 1] = fishSegment(fs[i].pos(), fs[i].yaw(), fs[i].pitch(), length[i + 1]);
                }
                boolean sphere = false, drawn = false;
                for (int a = 0; a <= n; a++) {
                    for (int b = a + 1; b <= n; b++) {
                        double sep = pos[a].distanceTo(pos[b]);
                        minSep = Math.min(minSep, sep);
                        if (sep < radius[a] + radius[b]) sphere = true;
                        if (segmentDistance(seg[a], seg[b]) < BODY_HALF_WIDTH * ((double) length[a] + length[b])) drawn = true;
                    }
                }
                together++;
                if (sphere) overlapping++;
                if (drawn) bodiesOverlapping++;
            }
        }
        long backstops = 0, leashes = 0;
        for (int i = 0; i < n; i++) {
            backstops += group.follower(i).backstops();
            leashes += group.follower(i).leashEvents();
        }
        return new FollowerMetrics(pen, backstops, maxImpact,
                maxAcc, maxJerk, maxTurn, moving == 0 ? 0 : tailFirst / (double) moving,
                minSep == Double.MAX_VALUE ? -1 : minSep, together == 0 ? 0 : overlapping / (double) together,
                together == 0 ? 0 : bodiesOverlapping / (double) together, longest * dt, leashes);
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
        boolean prevImpulse = false;
        double maxImpulse = 0;
        boolean door = scene.doorClosed();
        for (Sample s : samples) {
            asAt(s, truth);
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
            // A launch or touchdown jumps the velocity by design, and the tick after it re-adds the
            // jump: an impulse, measured on its own, not the body's acceleration.
            boolean jumped = s.impulse() || prevImpulse;
            prevImpulse = s.impulse();
            if (s.impulse()) maxImpulse = Math.max(maxImpulse, s.impulseSize());
            if (settled && !contact && !jumped) {
                maxAcc = Math.max(maxAcc, s.acc().length());
                // A backstop stop is a collision, counted on its own; it isn't the body's own jerk.
                if (prevAcc != null && !s.backstop()) maxJerk = Math.max(maxJerk, s.acc().sub(prevAcc).length() / dt);
                maxTurn = Math.max(maxTurn, Math.abs(s.turn()));
                // Travelling with its front more than 100° from the way it's going: a swimmer going
                // backwards, or a bell pulsing tentacles-first. A bell sinking between pulses is
                // just sinking, so for a drifter only ticks with real thrust count.
                boolean propelled = agent.locomotion() != Locomotion.DRIFT || s.thrust() > 1.0;
                if (agent.locomotion() == Locomotion.DRIFT && agent.driftStyle() == DriftStyle.JET && tunables.driftJet)
                    propelled = s.mantleThrust() > 1.0;
                if (s.vel().length() > 1.0 && propelled && !s.tumbling()) {
                    movingTicks++;
                    if (tailFirst(s, agent.locomotion(), agent.driftStyle())) tailFirstTicks++;
                }
                if (s.vel().horizontalLength() > 0.5 && !s.tumbling()) {
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
            prevAcc = settled && !contact && !jumped ? s.acc() : null;

            if (s.seen()) {
                seenTicks++;
                unseenRun = 0;
            } else {
                longestUnseen = Math.max(longestUnseen, ++unseenRun);
            }
            // Riding a shoulder, the head or the back it is drawn on the player model, which first
            // person never draws: an anchored fish on the head (D5) is above the lens, not in view.
            boolean centred = !s.bodyPerch() && s.pos().distanceTo(s.owner().camera()) < 2.5
                    && Visibility.angleFromCenterDeg(s.owner(), s.pos()) < 8;
            centreRun = centred ? centreRun + 1 : 0;
            longestCentre = Math.max(longestCentre, centreRun);

            farRun = settled && s.ownerDist() > tunables.farGap ? farRun + 1 : 0;
            longestFar = Math.max(longestFar, farRun);

            goalTicks.merge(s.goal(), 1, Integer::sum);
            if (prevGoal != null && !prevGoal.equals(s.goal())) {
                switches++;
                // Commitment applies to everything but a catch-up taking over in an emergency, the
                // end of a show-off or an inspection (finishing — or the thing went away — not
                // dithering), and loiter/escort swaps (those
                // follow the owner starting and stopping, through its own hysteresis). The length
                // is the mind's own goal clock. A fade's reset (goal clock 0 on the switching tick;
                // a decision has already counted 1) re-places the fish rather than deciding.
                boolean baseSwap = isBase(prevGoal) && isBase(s.goal());
                // A benthic catching up on foot (spec section 6) is the walk's emergency, like a swimmer's catch-up.
                boolean reset = s.goalTicks() == 0;
                // A percher going into or out of the water swaps bodies; that isn't the mind deciding.
                boolean perchSwap = prevGoal.equals("PERCH") || s.goal().equals("PERCH");
                boolean finished = prevGoal.equals("SHOW_OFF") || prevGoal.equals("INSPECT") || prevGoal.equals("NUZZLE");
                // Like a catch-up, taking cover (or squaring up) may cut in: an alarm is an emergency.
                boolean urgent = s.goal().equals("CATCH_UP") || s.goal().equals("HIDE") || s.goal().equals("GUARD");
                if (!urgent && !finished && !baseSwap && !reset && !perchSwap
                        && prevGoalTicks >= 0) {
                    shortest = Math.min(shortest, prevGoalTicks);
                }
            }
            prevGoalTicks = s.goalTicks();
            prevGoal = s.goal();
        }
        restoreDoor(door);
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

    /**
     * Whether {@code s} is travelling more than 100° from its front: the heading for a swimmer, the
     * bell's axis for a drifter. Per drift style (docs/fish-companions-drift.md §5), a {@code jet}
     * squid travelling mantle-first is going forwards: its reported axis points toward the mantle,
     * rather than the tentacles. Off-axis fin corrections can briefly travel across this axis.
     */
    static boolean tailFirst(Sample s, Locomotion c, DriftStyle style) {
        Vec front = Vec.fromYawPitch(s.yaw(), s.pitch());
        if (c == Locomotion.DRIFT) {
            front = switch (style) {
                case BELL -> front;
                case JET -> front; // Mantle-first is forward; tentacle-first is reverse.
                // The float stays upright: its front is the way its sail points.
                case BOB -> Vec.fromYawPitch(s.yaw(), 0);
            };
        }
        return front.dot(s.vel().normalize()) < -0.17;
    }

    // ── Drifters (docs/fish-companions-drift.md §9) ────────────────────────────────────────────

    /**
     * What a drifter's strokes did, each a level paired with a rate (a level alone can be met by a
     * body doing the wrong thing more often).
     *
     * @param strokes              pulse cycles that were settled, contact-free and asked for at least {@link #STROKE_MIN_ASK}
     * @param meanOvershoot        mean of (peak speed over a cycle) / (mean asked speed over that cycle)
     * @param maxOvershoot         the worst such ratio
     * @param overshootsPerMinute  cycles whose ratio is over {@link #OVERSHOOT}, per minute
     * @param reorients            swings: the asked direction turned more than 90° within {@link #SWING_TICKS}
     * @param meanReorientSeconds  from a swing until its thrust points along the new ask
     * @param maxReorientSeconds   the slowest (an unfinished swing counts to the end of the run)
     * @param reorientsPerMinute   swings per minute
     * @param minClearance         lowest gap from its underside to the floor under it, blocks (NaN: never over a floor)
     * @param lowClearanceFraction share of settled ticks under {@link #LOW_CLEARANCE}
     * @param lowDipsPerMinute     times it dropped under {@link #LOW_CLEARANCE}, per minute
     * @param reversalsPerMinute   its velocity turning more than 135° within {@link #SWING_TICKS}, both ends over 1 b/s
     * @param meanReversalDeltaV   mean change of velocity across such a reversal, blocks/s
     */
    public record DriftMetrics(int strokes, double meanOvershoot, double maxOvershoot, double overshootsPerMinute,
                               int reorients, double meanReorientSeconds, double maxReorientSeconds,
                               double reorientsPerMinute, double minClearance, double lowClearanceFraction,
                               double lowDipsPerMinute, double reversalsPerMinute, double meanReversalDeltaV) {
        public String summary() {
            return String.format("strokes=%d over=%.2f/%.2f (%.1f/min) reorient=%d %.2f/%.2fs (%.1f/min) "
                            + "clear=%.2f low=%.1f%% (%.1f/min) reversals=%.1f/min dv=%.2f",
                    strokes, meanOvershoot, maxOvershoot, overshootsPerMinute, reorients, meanReorientSeconds,
                    maxReorientSeconds, reorientsPerMinute, minClearance, lowClearanceFraction * 100,
                    lowDipsPerMinute, reversalsPerMinute, meanReversalDeltaV);
        }
    }

    /**
     * A drifter's body limits (docs/fish-companions-drift.md §13), the drift-aware stand-ins for the
     * swimmer's turn, facing and jerk checks, which are blind for a bell (its yaw holds the lean
     * direction, it has no turn rate, and every stroke's onset is a jerk by design). Over settled,
     * contact-free ticks: the most the bell's axis turned in a tick, degrees; the largest jerk while
     * coasting (no thrust this tick or the last) and while stroking, blocks/s³; the largest
     * acceleration, blocks/s².
     */
    public record DriftBodyMetrics(double maxTiltStepDeg, double maxCoastJerk, double maxStrokeJerk, double maxAccel) {
        public String summary() {
            return String.format("tiltStep=%.2f° coastJerk=%.0f strokeJerk=%.0f acc=%.1f",
                    maxTiltStepDeg, maxCoastJerk, maxStrokeJerk, maxAccel);
        }
    }

    /** The drifter body limits over the recorded run (zeros for other classes). */
    public DriftBodyMetrics driftBodyMetrics() {
        double dt = CompanionAgent.DT;
        double tilt = 0, coast = 0, stroke = 0, accel = 0;
        Sample prev = null;
        boolean prevContact = false;
        for (Sample s : samples) {
            boolean settled = s.fade() == CompanionAgent.Fade.NONE && !s.riding();
            boolean contact = s.backstop() || prevContact;
            prevContact = s.backstop();
            if (settled && !contact) {
                tilt = Math.max(tilt, s.tiltRate());
                accel = Math.max(accel, s.acc().length());
                if (prev != null) {
                    double jerk = s.acc().sub(prev.acc()).length() / dt;
                    if (s.thrust() == 0 && prev.thrust() == 0) coast = Math.max(coast, jerk);
                    else stroke = Math.max(stroke, jerk);
                }
                prev = s;
            } else {
                prev = null;
            }
        }
        return new DriftBodyMetrics(tilt, coast, stroke, accel);
    }

    /**
     * Coasting into the owner (docs/fish-companions-drift.md §13.8), over settled ticks: how often the
     * fish's centre enters the owner's body capsule, per minute; how many of those entries come while
     * the owner is stopping (slower than 1 b/s within 3 s of having gone faster than 2.5 b/s — the
     * owner's "walk or sprint ahead and stop"); the deepest the fish's sphere reaches into the capsule,
     * blocks; and the fastest it was closing on the body's axis as it entered, blocks/s (a coast
     * through reads fast, a slow drift-by slow). And the share of its idle ticks (the owner still for a
     * second or more, as {@code DriftIdleProbe} counts them) spent inside them, and how many idle ticks.
     */
    public record OwnerOverlapMetrics(double entriesPerMinute, int stopEntries, double maxDepth, double maxEntrySpeed,
                                      double idleInsideFraction, int idleTicks) {
        public String summary() {
            return String.format("ownerIn=%.1f/min stopIn=%d depth=%.2f inSpeed=%.2f idleIn=%.2f%% idleTicks=%d",
                    entriesPerMinute, stopEntries, maxDepth, maxEntrySpeed, idleInsideFraction * 100, idleTicks);
        }
    }

    /** Owner-overlap metrics over the recorded run. */
    public OwnerOverlapMetrics ownerOverlap() {
        double r = agent.radius();
        int entries = 0, stopEntries = 0, sinceFast = Integer.MAX_VALUE / 2, stillFor = 0, idle = 0, idleIn = 0;
        double depth = 0, entrySpeed = 0;
        boolean wasIn = false;
        for (Sample s : samples) {
            OwnerState o = s.owner();
            double hs = o.velocity().horizontalLength();
            sinceFast = hs > 2.5 ? 0 : sinceFast + 1;
            stillFor = hs < 0.2 ? stillFor + 1 : 0;
            boolean settled = s.fade() == CompanionAgent.Fade.NONE && !s.riding();
            if (!settled) {
                wasIn = false;
                continue;
            }
            Vec a = o.feet().add(0, OwnerState.BODY_RADIUS, 0);
            Vec b = o.eye().add(0, 0.1, 0);
            Vec ab = b.sub(a);
            double u = Math.max(0, Math.min(1, s.pos().sub(a).dot(ab) / ab.lengthSqr()));
            Vec q = a.add(ab.scale(u));
            Vec away = s.pos().sub(q);
            double d = away.length();
            depth = Math.max(depth, OwnerState.BODY_RADIUS + r - d);
            boolean in = d < OwnerState.BODY_RADIUS;
            if (stillFor >= 20) {
                idle++;
                if (in) idleIn++;
            }
            if (in && !wasIn) {
                entries++;
                if (hs < 1.0 && sinceFast <= 60) stopEntries++;
                if (d > 1e-6) entrySpeed = Math.max(entrySpeed, -s.vel().sub(o.velocity()).dot(away.scale(1 / d)));
            }
            wasIn = in;
        }
        double minutes = samples.size() * CompanionAgent.DT / 60.0;
        return new OwnerOverlapMetrics(minutes == 0 ? 0 : entries / minutes, stopEntries, Math.max(0, depth), entrySpeed,
                idle == 0 ? 0 : idleIn / (double) idle, idle);
    }

    /** Asked speed under which a stroke isn't judged for overshoot, blocks/s (a hover's strokes hold height). */
    public static final double STROKE_MIN_ASK = 1.0;
    /** Peak over asked above which a stroke overshot. */
    public static final double OVERSHOOT = 1.15;
    /** Window for a swing of the ask and for a reversal of velocity, ticks (1 s). */
    public static final int SWING_TICKS = 20;
    /** Gap to the floor under which a drifter is scraping it, blocks. */
    public static final double LOW_CLEARANCE = 0.15;

    /** The drifter metrics over the recorded run (zeros for other classes). */
    public DriftMetrics driftMetrics() {
        LocalVoxels truth = new LocalVoxels();
        double r = agent.radius(), dt = CompanionAgent.DT;
        int n = samples.size();
        double minutes = Math.max(1e-9, n * dt / 60.0);
        // Strokes: one cycle from a stroke start to the next, settled and contact-free throughout.
        int strokes = 0, overshoots = 0, cycleN = 0;
        double sumRatio = 0, maxRatio = 0, cyclePeak = 0, cycleAsk = 0;
        boolean inCycle = false, cycleClean = false;
        // Swings of the ask.
        int reorients = 0, swingStart = -1;
        int lastSwing = -SWING_TICKS;
        double sumReorient = 0, maxReorient = 0;
        // Floor clearance.
        double minClear = Double.NaN;
        int settledN = 0, lowTicks = 0, dips = 0;
        boolean wasLow = false;
        // Velocity reversals.
        int reversals = 0, lastReversal = -SWING_TICKS;
        double sumDv = 0;
        boolean door = scene.doorClosed();
        for (int k = 0; k < n; k++) {
            Sample s = samples.get(k);
            asAt(s, truth);
            truth.update(scene, s.owner().eye());
            boolean settled = s.fade() == CompanionAgent.Fade.NONE && !s.riding();

            if (s.strokeStart()) {
                if (inCycle && cycleClean && cycleN > 0) {
                    double ask = cycleAsk / cycleN;
                    if (ask >= STROKE_MIN_ASK) {
                        double ratio = cyclePeak / ask;
                        strokes++;
                        sumRatio += ratio;
                        maxRatio = Math.max(maxRatio, ratio);
                        if (ratio > OVERSHOOT) overshoots++;
                    }
                }
                inCycle = true;
                cycleClean = true;
                cyclePeak = 0;
                cycleAsk = 0;
                cycleN = 0;
            }
            if (inCycle) {
                cycleClean &= settled && !s.backstop();
                cyclePeak = Math.max(cyclePeak, s.vel().length());
                cycleAsk += s.asked().length();
                cycleN++;
            }

            if (swingStart < 0 && k >= SWING_TICKS && k - lastSwing >= SWING_TICKS && settled) {
                Vec now = s.asked(), then = samples.get(k - SWING_TICKS).asked();
                if (now.length() > 0.5 && then.length() > 0.5 && now.normalize().dot(then.normalize()) < 0) {
                    swingStart = k;
                    lastSwing = k;
                }
            }
            if (swingStart >= 0 && (alongAsked(s) || k == n - 1)) {
                double secs = (k - swingStart) * dt;
                reorients++;
                sumReorient += secs;
                maxReorient = Math.max(maxReorient, secs);
                swingStart = -1;
            }

            if (settled) {
                settledN++;
                Vec p = s.pos();
                double floor = truth.columnFloor((int) Math.floor(p.x()), (int) Math.floor(p.z()), p.y(), 4.0);
                if (!Double.isNaN(floor)) {
                    double clear = p.y() - r - floor;
                    minClear = Double.isNaN(minClear) ? clear : Math.min(minClear, clear);
                    boolean low = clear < LOW_CLEARANCE;
                    if (low) lowTicks++;
                    if (low && !wasLow) dips++;
                    wasLow = low;
                } else {
                    wasLow = false;
                }
            }

            if (k >= SWING_TICKS && settled && k - lastReversal >= SWING_TICKS) {
                Vec v = s.vel(), u = samples.get(k - SWING_TICKS).vel();
                if (v.length() > 1.0 && u.length() > 1.0 && v.normalize().dot(u.normalize()) < -0.7071) {
                    reversals++;
                    sumDv += v.sub(u).length();
                    lastReversal = k;
                }
            }
        }
        restoreDoor(door);
        return new DriftMetrics(strokes, strokes == 0 ? 0 : sumRatio / strokes, maxRatio, overshoots / minutes,
                reorients, reorients == 0 ? 0 : sumReorient / reorients, maxReorient, reorients / minutes,
                minClear, settledN == 0 ? 0 : lowTicks / (double) settledN, dips / minutes, reversals / minutes,
                reversals == 0 ? 0 : sumDv / reversals);
    }

    /**
     * Whether a drifter's thrust now points along what it's asked for: within 45°, judged on the
     * horizontal when the ask is mostly horizontal (its bell never tilts past {@code driftTiltMaxDeg},
     * and part of every stroke holds off the sink).
     */
    private static boolean alongAsked(Sample s) {
        if (s.thrust() <= 1.0) return false;
        Vec axis = s.propulsion().normalize(), ask = s.asked();
        if (ask.horizontalLength() > 0.5 * ask.length()) {
            Vec ah = new Vec(axis.x(), 0, axis.z()), kh = new Vec(ask.x(), 0, ask.z());
            if (ah.length() < Math.sin(Math.toRadians(10))) return false;
            return ah.normalize().dot(kh.normalize()) > 0.7071;
        }
        return axis.dot(ask.normalize()) > 0.7071;
    }

    private static boolean isBase(String goal) {
        return goal.equals("LOITER") || goal.equals("SETTLE") || goal.equals("ESCORT")
                // A benthic on the ground idles or follows as its owner stops and starts.
                || goal.equals("GROUND_IDLE") || goal.equals("GROUND_FOLLOW");
    }

    // ── On the ground (docs/fish-companions-benthic-follow.md section 7) ───────────────────────────

    /**
     * What a benthic did on the ground.
     *
     * @param groundShare      share of ticks walking the ground
     * @param maxFloorError    worst gap between its underside and the floor under it while its feet are down, blocks
     * @param floorErrorTicks  ticks with its feet down more than {@link #FLOOR_EPS} off the floor
     * @param longestStuckSeconds longest run on the ground, far from its owner and not moving
     * @param fades            fades onto the perch (it couldn't follow on foot)
     * @param maxImpulse       the biggest velocity jump at a launch or touchdown, blocks/s
     * @param meanLag          mean lag behind the owner along their way while following or catching up, blocks
     * @param maxLag           worst lag on the ground, blocks
     * @param catchUpShare     share of ground ticks catching up
     * @param hops             hops taken (onto the owner, off them, across gaps, up steps excluded)
     * @param gapHops          hops across gaps it couldn't walk
     */
    public record GroundMetrics(double groundShare, double maxFloorError, int floorErrorTicks, double longestStuckSeconds,
                                long fades, double maxImpulse, double meanLag, double maxLag, double catchUpShare,
                                int hops, int gapHops, int climbs, int dismounts, int tumbles, int firstClimbTick) {
        public String summary() {
            return String.format("ground=%.0f%% floorErr=%.3f (%d) stuck=%.1fs fades=%d impulse=%.1f lag=%.1f/%.1f "
                            + "catchUp=%.0f%% hops=%d gapHops=%d climbs=%d dismounts=%d tumbles=%d firstClimb=%d",
                    groundShare * 100, maxFloorError, floorErrorTicks, longestStuckSeconds, fades, maxImpulse,
                    meanLag, maxLag, catchUpShare * 100, hops, gapHops, climbs, dismounts, tumbles, firstClimbTick);
        }
    }

    /** How far off the floor counts as not on it, blocks. */
    public static final double FLOOR_EPS = 0.01;

    /** The ground metrics over the recorded run (a benthic's; zeros for anything else). */
    public GroundMetrics groundMetrics() {
        LocalVoxels truth = new LocalVoxels();
        double r = agent.radius(), dt = CompanionAgent.DT;
        int ground = 0, floorErrTicks = 0, stuck = 0, longestStuck = 0, catching = 0, lagN = 0;
        double maxErr = 0, maxImpulse = 0, sumLag = 0, maxLag = 0;
        boolean door = scene.doorClosed();
        for (Sample s : samples) {
            asAt(s, truth);
            truth.update(scene, s.owner().eye());
            if (s.impulse()) maxImpulse = Math.max(maxImpulse, s.impulseSize());
            if (!s.onGround()) {
                stuck = 0;
                continue;
            }
            ground++;
            if (s.feetDown()) {
                double bottom = s.pos().y() - r - LocalVoxels.GROUND_LIFT;
                double floor = Double.NEGATIVE_INFINITY;
                int x0 = (int) Math.floor(s.pos().x() - r), x1 = (int) Math.floor(s.pos().x() + r);
                int z0 = (int) Math.floor(s.pos().z() - r), z1 = (int) Math.floor(s.pos().z() + r);
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        double f = truth.columnFloor(x, z, bottom + 0.05, 6);
                        if (!Double.isNaN(f)) floor = Math.max(floor, f);
                    }
                }
                double err = Double.isInfinite(floor) ? 6 : Math.abs(bottom - floor);
                maxErr = Math.max(maxErr, err);
                if (err > FLOOR_EPS) floorErrTicks++;
            }
            boolean far = s.owner().feet().sub(s.pos()).horizontalLength() > 4.5;
            stuck = far && s.vel().horizontalLength() < 0.2 ? stuck + 1 : 0;
            longestStuck = Math.max(longestStuck, stuck);
            if ("CATCH_UP".equals(s.land())) catching++;
            if ("GROUND_FOLLOW".equals(s.land()) || "CATCH_UP".equals(s.land())) {
                sumLag += s.lag();
                lagN++;
            }
            maxLag = Math.max(maxLag, s.lag());
        }
        restoreDoor(door);
        int n = samples.size();
        return new GroundMetrics(n == 0 ? 0 : ground / (double) n, maxErr, floorErrTicks, longestStuck * dt,
                agent.groundFades(), maxImpulse, lagN == 0 ? 0 : sumLag / lagN, maxLag,
                ground == 0 ? 0 : catching / (double) ground, agent.hops(), agent.gapHops(),
                agent.climbs(), agent.dismounts(), agent.tumbles(), firstClimbTick());
    }

    private int firstClimbTick() {
        for (int i = 0; i < samples.size(); i++) {
            if ("CLIMB".equals(samples.get(i).land())) return i;
        }
        return -1;
    }

    private void restoreDoor(boolean closed) {
        if (!scene.hasDoor()) return;
        scene.setDoorClosed(closed);
        sceneVersion = scene.version();
    }

    /** Puts the scene's door as it was at sample {@code s}, so the truth checks see the blocks of that tick. */
    private void asAt(Sample s, LocalVoxels truth) {
        if (scene.hasDoor() && s.doorClosed() != scene.doorClosed()) {
            scene.setDoorClosed(s.doorClosed());
            sceneVersion = scene.version();
            truth.invalidate();
        }
    }

    /** Whether {@code p} is inside the owner's body capsule. */
    public static boolean insideBody(OwnerState o, Vec p) {
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
