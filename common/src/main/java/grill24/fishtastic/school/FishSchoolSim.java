package grill24.fishtastic.school;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.ShelterUse;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.Shelter;
import grill24.fishsim.domain.WaterDomain;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * One interactive school's motion: a {@link FlockEngine} in a {@link WaterDomain} built from the
 * visit's water — the same engine the ambient population and the tanks run, and nothing else.
 *
 * <p>The server runs one of these per school and every watching client runs a copy. Both are
 * built from the same {@link Setup} and fed the same {@link Input}s at the same step counts, and
 * the engine is a pure function of those (fishsim's {@code SchoolLockstepTest}), so the copies
 * agree bit for bit without positions being streamed. Everything here is therefore deterministic
 * by construction: inputs carry floats (never doubles a side might round differently), in the
 * school's own frame (blocks from {@link Setup#origin}, so precision does not fall off far from
 * the world origin), and anything an input decides — which way a fish comes at a bobber — is
 * decided from engine state both sides share.
 *
 * <p>No watcher is ever declared: each client has a different player nearby, and a startle on one
 * copy would be a divergence (decided in docs/interactive-fish-schools-design-spec.md).
 */
public final class FishSchoolSim {

    /** The ambient population's per-fish rotation jitter, degrees. */
    public static final float ROTATION_JITTER = 20f;

    /** A member's starting point, in the school's frame. */
    public record Placement(UUID member, float l, float y, float d) {}

    /**
     * Everything a copy is built from.
     *
     * @param origin  the school's frame: positions and anchors are blocks from this block's corner
     * @param columns the visit's water, in world coordinates
     * @param spec    the species' engine descriptor, as the server resolved it — shipped rather than
     *                re-derived, so a client never builds the school from different numbers
     * @param seed    the engine's base seed
     * @param members every member present at step 0, in order
     * @param shape   the visit's shape, which picks the engine's flocking parameters
     */
    public record Setup(BlockPos origin, List<WaterDomain.Column> columns, FishSpec spec, long seed,
                        List<Placement> members, VisitShape shape) {
        public Setup {
            columns = List.copyOf(columns);
            members = List.copyOf(members);
        }
    }

    /** A change the server makes to the school, applied on every copy at the same step count. */
    public sealed interface Input permits Leave, Rejoin, Approach, Withdraw, Water, Watcher, SurfaceJump, SurfaceGoal {}

    /** A cosmetic breach. It never changes the member's underwater engine position. */
    public record SurfaceJump(UUID member, int durationTicks, float height,
                              SurfaceJumpPlaybackProfile playbackProfile) implements Input {
        public SurfaceJump(UUID member, int durationTicks, float height) {
            this(member, durationTicks, height, SurfaceJumpPlaybackProfile.NORMAL);
        }
    }

    /**
     * A member's goal of breaching: it swims to the launch point {@code (l, y, d)} just under the
     * surface (the engine's own nose errand, as for a bobber) and, when it gets there, makes the
     * {@code durationTicks}, {@code height} jump and holds at the point for its length. Whether it
     * could go is the engine's answer, the same on every copy; {@link #apply} returns it. The goal
     * ends at the jump's end, or sooner by {@link Withdraw} of {@code goal}, a bobber approach for
     * the member, or the member leaving; it also ends if the fish cannot line up at the point.
     */
    public record SurfaceGoal(UUID goal, UUID member, float l, float y, float d, int durationTicks,
                              float height, SurfaceJumpPlaybackProfile playbackProfile) implements Input {
        public SurfaceGoal(UUID goal, UUID member, float l, float y, float d, int durationTicks, float height) {
            this(goal, member, l, y, d, durationTicks, height, SurfaceJumpPlaybackProfile.NORMAL);
        }
    }

    /** Read-only presentation clock and phase thresholds for a live jump. */
    public record SurfaceJumpClock(SurfaceJumpPlaybackProfile profile, double previousSourceTime,
                                   double sourceTime, double playbackRate, double exitSourceTime,
                                   double apexSourceTime, double entrySourceTime, double endSourceTime,
                                   long startStep, int presentationDurationTicks, boolean cancelled) {}

    /** A one-shot crossing retained briefly so a catching-up client can observe it exactly once. */
    public record SurfaceJumpCrossing(UUID member, long presentationStep, double sourceTime,
                                      float l, float d, float forwardSpeed, float headingL, float headingD,
                                      boolean entry, SurfaceJumpPlaybackProfile profile) {}

    /**
     * Someone rushing the visit at {@code (l, y, d)} ({@code near}), or nobody ({@code !near}).
     * Only garden eels read it: they duck from a player who comes at them fast (encounter spec
     * §4.3). Shared like every input, so each copy ducks at the same step.
     */
    public record Watcher(boolean near, float l, float y, float d) implements Input {}

    /** The member leaves the water: hooked into a fight, caught, or gone with its visit. */
    public record Leave(UUID member) implements Input {}

    /** The member is back in the water at {@code (l, y, d)}, as after a fight that got away. */
    public record Rejoin(UUID member, float l, float y, float d) implements Input {}

    /**
     * A bobber at {@code (l, y, d)} — the point under it a fish can nose, chosen by
     * {@link #anchorPoint} — and the member sent to it, approaching {@code speedScale} times as
     * fast as a visit and holding there {@code holdSeconds}. Whether it could go is the engine's
     * answer, the same on every copy; {@link #apply} returns it.
     */
    public record Approach(UUID anchor, UUID member, float l, float y, float d, float speedScale,
                           float holdSeconds) implements Input {}

    /** The bobber is gone, or the server gave up on it: its fish, if still sent, is called off. */
    public record Withdraw(UUID anchor) implements Input {}

    /** The visit's water changed shape (a block placed or broken in it). */
    public record Water(List<WaterDomain.Column> columns) implements Input {
        public Water {
            columns = List.copyOf(columns);
        }
    }

    /** A sent fish touched its bobber ({@code touch}), or let go of it. */
    public record Event(UUID anchor, boolean touch) {}

    /** An input and the step it is applied before (when {@link #steps()} equals {@code step}). */
    public record Stamped(long step, Input input) {}

    private final BlockPos origin;
    private final FishSpec spec;
    private final long seed;
    private final WaterDomain.BodyBox body;
    private final FlockEngine engine;
    private final List<UUID> order = new ArrayList<>();
    private final Map<UUID, Shelter> anchors = new LinkedHashMap<>();
    /** Transient, deterministic presentation phases keyed by stable member identity. */
    private final Map<UUID, JumpPhase> surfaceJumps = new LinkedHashMap<>();
    private final List<SurfaceJumpCrossing> recentSurfaceCrossings = new ArrayList<>();
    /** Non-swimmers' errands, by bobber: they reach a bait by seeking it, not by the swimmers' nose errand. */
    private final Map<UUID, Seek> seeks = new LinkedHashMap<>();
    /** Members swimming up to breach, by goal id; the goal's nose anchor is in {@link #anchors} under the same key. */
    private final Map<UUID, Goal> goals = new LinkedHashMap<>();
    private WaterDomain domain;
    private long steps;

    public static final int MAX_SURFACE_JUMP_TICKS = 80;
    /** Hard wall-clock bound for any one synchronized jump. */
    public static final int MAX_SURFACE_JUMP_PRESENTATION_TICKS = 160;
    /** Crossing events are read on the next replica tick; four steps absorb brief client stalls. */
    public static final int SURFACE_JUMP_CROSSING_RETENTION_STEPS = 4;
    public static final float MAX_SURFACE_JUMP_HEIGHT = 2f;
    private static final int JUMP_CANCEL_TICKS = 4;

    /**
     * One breach in progress: its {@link SurfaceJumpArc}, in sim-local coordinates from where the
     * fish committed. The drawn body follows the arc wherever the engine fish is meanwhile (it
     * carries on to its launch point and holds there). A cancelled jump folds the drawn body back
     * onto the engine fish over {@link #JUMP_CANCEL_TICKS}; one that runs its course hands the fish
     * to the engine where it was drawn ({@link #finishSurfaceJumps}).
     */
    private static final class JumpPhase {
        final long startStep;
        final SurfaceJumpArc arc;
        final SurfaceJumpPlaybackProfile profile;
        final int presentationDurationTicks;
        final long exitPresentationStep;
        final long entryPresentationStep;
        double previousSourceTime = -1.0;
        double sourceTime;
        double playbackRate = 1.0;
        long lastAdvancedStep;
        long cancelStep = -1;

        JumpPhase(long startStep, SurfaceJumpArc arc, SurfaceJumpPlaybackProfile profile) {
            this.startStep = startStep;
            this.arc = arc;
            this.profile = profile;
            this.presentationDurationTicks = profile.presentationTicks(arc, MAX_SURFACE_JUMP_PRESENTATION_TICKS);
            this.exitPresentationStep = startStep + stepsToSource(arc, profile, arc.exitTick());
            this.entryPresentationStep = startStep + stepsToSource(arc, profile, arc.entryTick());
            this.lastAdvancedStep = startStep;
        }

        long endStep() {
            return cancelStep >= 0 ? cancelStep + JUMP_CANCEL_TICKS : startStep + presentationDurationTicks;
        }

        private static int stepsToSource(SurfaceJumpArc arc, SurfaceJumpPlaybackProfile profile, double target) {
            double source = 0.0;
            int ticks = 0;
            while (source < target && ticks < MAX_SURFACE_JUMP_PRESENTATION_TICKS) {
                source = StrictMath.min(arc.durationTicks(), source + profile.rate(arc, source));
                ticks++;
            }
            return ticks;
        }

        void advance(long step, UUID member, List<SurfaceJumpCrossing> events) {
            if (step <= lastAdvancedStep) return;
            lastAdvancedStep = step;
            if (cancelStep >= 0) {
                previousSourceTime = sourceTime;
                playbackRate = 0.0;
                return;
            }
            previousSourceTime = sourceTime;
            playbackRate = profile.rate(arc, sourceTime);
            sourceTime = StrictMath.min(arc.durationTicks(), sourceTime + playbackRate);
            addCrossing(events, member, step, arc.exitTick(), false);
            addCrossing(events, member, step, arc.entryTick(), true);
        }

        private void addCrossing(List<SurfaceJumpCrossing> events, UUID member, long step,
                                 double threshold, boolean entry) {
            if (previousSourceTime >= threshold || sourceTime < threshold) return;
            double[] where = arc.crossing(entry);
            events.add(new SurfaceJumpCrossing(member, step, threshold, (float) where[0], (float) where[1],
                    (float) arc.surfaceSpeed(), (float) arc.headingL(), (float) arc.headingD(), entry, profile));
        }

        /** How much of the arc's pose shows at {@code step}: 1, or less while a cancel folds it away. */
        double fold(double step) {
            if (cancelStep < 0 || step <= cancelStep) return 1d;
            return 1d - Math.min(1d, (step - cancelStep) / JUMP_CANCEL_TICKS);
        }

        /** The arc's sample at {@code step} ({@link SurfaceJumpArc#sample}); a cancelled one holds its cancel pose. */
        double[] sampleAt(float partialTick, double[] out) {
            double source = previousSourceTime + (sourceTime - previousSourceTime) * partialTick;
            return arc.sample(source, out);
        }

        int fingerprint() {
            int h = Long.hashCode(startStep);
            h = 31 * h + arc.fingerprint();
            h = 31 * h + profile.ordinal();
            h = 31 * h + Double.hashCode(previousSourceTime);
            h = 31 * h + Double.hashCode(sourceTime);
            h = 31 * h + Double.hashCode(playbackRate);
            h = 31 * h + presentationDurationTicks;
            h = 31 * h + Long.hashCode(cancelStep);
            return h;
        }
    }

    /**
     * A swimmer's errand to the surface: who, its launch point (where its body will break the
     * surface), the jump it makes, the heading it will leap along, whether its jump has begun, and
     * whether it reached the point somewhere no arc fits, and gives up.
     */
    private static final class Goal {
        final UUID member;
        final float l, y, d;
        final int durationTicks;
        final float height;
        final SurfaceJumpPlaybackProfile playbackProfile;
        final float hl, hd;
        boolean arrived, failed;

        Goal(UUID member, float l, float y, float d, int durationTicks, float height,
             SurfaceJumpPlaybackProfile playbackProfile, float hl, float hd) {
            this.member = member;
            this.l = l;
            this.y = y;
            this.d = d;
            this.durationTicks = durationTicks;
            this.height = height;
            this.playbackProfile = playbackProfile;
            this.hl = hl;
            this.hd = hd;
        }
    }

    /**
     * Horizontal reach scales tried, longest first, when fitting an arc into the water around a
     * launch; each is multiplied by the jump's {@link SurfaceJumpPlaybackProfile#reachScale}.
     */
    private static final double[] ARC_REACHES = {1.0, 0.6, 0.3};
    /** Run-in scales tried for each reach: a briefer run-in dips less on its way up. */
    private static final double[] ARC_RUN_INS = {1.0, 0.7};
    /** Apex scales tried last of all, for water too shallow for the full dive. */
    private static final double[] ARC_APEX_SCALES = {1.0, 0.6};
    /** How far the re-entry surface may sit from the launch surface, blocks. */
    private static final float ARC_SURFACE_TOLERANCE = 0.35f;
    /**
     * A swimmer commits only while heading at its launch point: its horizontal velocity's share
     * along the line there. Anything less (swimming past, or backing off) waits for the touch.
     */
    private static final float COMMIT_ALIGNMENT = 0.5f;
    /** The goal that last ran its jump to the end, for the server's cooldown (one goal per visit at a time). */
    private @Nullable UUID lastCompletedGoal;
    private final double[] arcScratch = new double[7];

    /** The nose is held at the launch point this many ticks past the jump, so the fish is still there as it lands. */
    private static final int GOAL_HOLD_SLACK_TICKS = 4;
    /**
     * How eagerly a member swims to its launch point, as for a bobber ({@code Approach.speedScale}).
     * Measured in {@code FishSchoolSimTest}: at 1x a two-block climb took 530-650 ticks, at 3x about 200.
     */
    public static final float GOAL_SPEED_SCALE = 3f;

    /** A crawler's, drifter's or eel's errand: who, where, how long it holds once there, and when it got there. */
    private static final class Seek {
        final UUID member;
        final float l, y, d;
        final int holdSteps;
        long touchedAt = -1;

        Seek(UUID member, float l, float y, float d, int holdSteps) {
            this.member = member;
            this.l = l;
            this.y = y;
            this.d = d;
            this.holdSteps = holdSteps;
        }
    }

    /** How fast a crawler walks to a bait, blocks a second: about ten times its idle scuttle. */
    static final float CRAWL_SEEK_SPEED = 0.35f;
    /** How fast a drifter is carried to a bait, blocks a second. */
    static final float DRIFT_SEEK_SPEED = 0.2f;
    /** A crawler is at the bait when its centre is this close beneath it, blocks (horizontal). */
    static final float CRAWL_ARRIVED = 0.3f;
    /** A drifter is at the bait when this close to it, blocks. */
    static final float DRIFT_ARRIVED = 0.45f;
    /** An eel takes a bobber that lands within this of its burrow, blocks (horizontal). */
    public static final float EEL_REACH = 2f;
    /** An eel this far withdrawn or less counts as out of its burrow. */
    static final float EEL_EMERGED = 0.3f;
    /** How far above the floor a bobber may be for a crawler to take it from the bottom, blocks (spec §4.4). */
    public static final float BOTTOM_BITE_DEPTH = 12f;
    /** Where the eels' watcher sits when nobody is rushing them: present (so they re-arm) but far away. */
    private static final float WATCHER_AWAY = 1.0e5f;

    private FishSchoolSim(BlockPos origin, FishSpec spec, long seed, WaterDomain domain, Tunables tunables) {
        this.engine = new FlockEngine(tunables);
        this.origin = origin;
        this.spec = spec;
        this.seed = seed;
        this.body = WaterDomain.BodyBox.of(spec.locomotion(), spec.length());
        this.domain = domain;
    }

    /** A copy at step 0, or null when the water cannot be built into a domain at all. */
    public static @Nullable FishSchoolSim create(Setup setup) {
        WaterDomain.BodyBox body = WaterDomain.BodyBox.of(setup.spec().locomotion(), setup.spec().length());
        WaterDomain domain = buildDomain(setup.origin(), setup.columns(), body);
        if (domain == null) return null;
        FishSchoolSim sim = new FishSchoolSim(setup.origin(), setup.spec(), setup.seed(), domain,
                setup.shape().tunables());
        for (Placement p : setup.members()) sim.order.add(p.member());
        sim.engine.rebuild(sim.specs(), setup.seed(), 0f, ROTATION_JITTER, domain);
        for (int i = 0; i < setup.members().size(); i++) {
            Placement p = setup.members().get(i);
            sim.place(i, p.l(), p.y(), p.d());
        }
        // Eels re-arm their startle only while a watcher is declared and away from them.
        if (setup.spec().locomotion() == Locomotion.ANCHORED) {
            sim.engine.setWatcher(true, WATCHER_AWAY, 0f, WATCHER_AWAY);
        }
        return sim;
    }

    /**
     * Puts member #{@code i} at {@code (l, y, d)}; a floor creature goes onto the floor under that
     * point instead, or keeps the engine's own floor spot when there is none there.
     */
    private void place(int i, float l, float y, float d) {
        Locomotion locomotion = spec.locomotion();
        if (locomotion == Locomotion.BENTHIC || locomotion == Locomotion.ANCHORED) {
            float floor = domain.floor().heightAt(l, d);
            if (Float.isFinite(floor)) engine.placeFish(i, l, floor, d);
            return;
        }
        float[] fit = nearestFit(l, y, d);
        if (fit != null) engine.placeFish(i, fit[0], fit[1], fit[2]);
        else engine.placeFish(i, l, y, d);
    }

    /** How far a placement is moved to find room for the body: vertically, then sideways, blocks. */
    private static final float FIT_REACH_VERTICAL = 3f, FIT_REACH_SIDEWAYS = 2f;
    private static final float FIT_STEP_VERTICAL = 0.1f, FIT_STEP_SIDEWAYS = 0.5f;

    /**
     * The point nearest {@code (l, y, d)} where the whole body fits, or null when there is none
     * close by. A swimmer placed where its body pokes out of the water (a big fish on a block
     * centre in the top layer) never moves: the engine refuses every step from a spot that does not
     * fit, so it would swim in place forever and never reach a bobber. Deterministic, so every copy
     * moves it to the same point.
     */
    private float @Nullable [] nearestFit(float l, float y, float d) {
        if (bodyFits(l, y, d)) return new float[]{l, y, d};
        // Straight down (or up) first: the usual fix for a fish too near the surface.
        for (float dy = FIT_STEP_VERTICAL; dy <= FIT_REACH_VERTICAL; dy += FIT_STEP_VERTICAL) {
            if (bodyFits(l, y - dy, d)) return new float[]{l, y - dy, d};
            if (bodyFits(l, y + dy, d)) return new float[]{l, y + dy, d};
        }
        float[] best = null;
        float bestD2 = Float.MAX_VALUE;
        for (float dl = -FIT_REACH_SIDEWAYS; dl <= FIT_REACH_SIDEWAYS; dl += FIT_STEP_SIDEWAYS) {
            for (float dd = -FIT_REACH_SIDEWAYS; dd <= FIT_REACH_SIDEWAYS; dd += FIT_STEP_SIDEWAYS) {
                for (float dy = -FIT_REACH_VERTICAL; dy <= FIT_REACH_VERTICAL; dy += FIT_STEP_VERTICAL) {
                    float d2 = dl * dl + dd * dd + dy * dy;
                    if (d2 < bestD2 && bodyFits(l + dl, y + dy, d + dd)) {
                        bestD2 = d2;
                        best = new float[]{l + dl, y + dy, d + dd};
                    }
                }
            }
        }
        return best;
    }

    /**
     * Whether a visit's water can host its species: the engine's own size gate for the species'
     * class, the same one a tank applies, and somewhere deep enough for the whole body. A fish that
     * failed either would be frozen in place in the world, so such a visit must not form
     * (encounter spec §4.1).
     */
    public static boolean hosts(BlockPos origin, List<WaterDomain.Column> columns, FishSpec spec, VisitShape shape) {
        FishSchoolSim sim = create(new Setup(origin, columns, spec, 0L, List.of(), shape));
        return sim != null && sim.hostsSpecies() && sim.roomForBody(columns);
    }

    /** Whether any column of {@code columns} has a point where the whole body fits. */
    private boolean roomForBody(List<WaterDomain.Column> columns) {
        Locomotion locomotion = spec.locomotion();
        if (locomotion == Locomotion.BENTHIC || locomotion == Locomotion.ANCHORED) return true; // on the floor
        for (WaterDomain.Column column : columns) {
            float l = column.worldX() - origin.getX() + 0.5f, d = column.worldZ() - origin.getZ() + 0.5f;
            float[] spans = column.spans();
            for (int k = 0; k + 1 < spans.length; k += 2) {
                for (float y = spans[k]; y <= spans[k + 1]; y += FIT_STEP_VERTICAL) {
                    if (bodyFits(l, y - origin.getY(), d)) return true;
                }
            }
        }
        return false;
    }

    /** Whether this copy's current water still passes its species' size gate. */
    public boolean hostsSpecies() {
        return engine.passesGate(spec.locomotion(), spec.length());
    }

    private static @Nullable WaterDomain buildDomain(BlockPos origin, List<WaterDomain.Column> columns,
                                                     WaterDomain.BodyBox body) {
        // Every face closed: a school stays inside its own visit's water (design spec §5.4).
        return WaterDomain.build(columns, origin.getX(), origin.getY(), origin.getZ(), body.above(), null);
    }

    /** The species descriptor a school of {@code length}-block, {@code locomotion} fish runs with. */
    public static FishSpec spec(float length, Locomotion locomotion, ShelterUse shelterUse) {
        // One species per school: the id only has to agree with itself.
        return new FishSpec(length, locomotion, false, 0, shelterUse);
    }

    private FishSpec[] specs() {
        FishSpec[] specs = new FishSpec[order.size()];
        for (int i = 0; i < specs.length; i++) {
            // Alternate the starting heading, as a tank's per-slot mirror flag does.
            specs[i] = new FishSpec(spec.length(), spec.locomotion(), (i & 1) == 0, spec.species(), spec.shelterUse());
        }
        return specs;
    }

    public BlockPos origin() { return origin; }
    public FishSpec spec() { return spec; }
    public WaterDomain.BodyBox body() { return body; }
    public FlockEngine engine() { return engine; }
    public WaterDomain domain() { return domain; }
    /** Steps taken since step 0. */
    public long steps() { return steps; }
    /** The members in the water, in engine order. */
    public List<UUID> members() { return List.copyOf(order); }
    public int indexOf(UUID member) { return order.indexOf(member); }

    /** One engine step; each touch and let-go since the last is handed to {@code events}. */
    public void step(@Nullable Consumer<Event> events) {
        engine.step();
        steps++;
        pruneRecentSurfaceCrossings();
        engine.drainTriggerEvents(e -> {
            if (!(e.key() instanceof UUID anchor)) return;
            Goal goal = goals.get(anchor);
            if (goal != null && e.start()) arriveAtGoal(goal);
            if (events != null) events.accept(new Event(anchor, e.start()));
        });
        if (!surfaceJumps.isEmpty()) finishSurfaceJumps();
        if (!goals.isEmpty()) advanceGoals();
        if (!seeks.isEmpty()) advanceSeeks(events);
    }

    /**
     * The member's nose is at its launch point without having committed on the way in (it came up
     * from straight below, or was not heading at the point): it leaps from where it is now, if an
     * arc fits the water there.
     */
    private void arriveAtGoal(Goal goal) {
        int fish = order.indexOf(goal.member);
        if (goal.arrived || fish < 0 || surfaceJumps.containsKey(goal.member)) return;
        goal.arrived = true;
        JumpPhase jump = fitJump(fish, goal);
        if (jump == null) goal.failed = true; // ended by advanceGoals, outside the engine's event drain
        else surfaceJumps.put(goal.member, jump);
    }

    /**
     * Whether goal {@code goal}'s fish, still swimming in, is close enough and heading at its launch
     * point, so it can swim on into the leap ({@link SurfaceJumpArc#COMMIT_DISTANCE}).
     */
    private boolean readyToCommit(int i, Goal goal) {
        float dl = goal.l - engine.posL()[i], dd = goal.d - engine.posD()[i];
        float run = (float) Math.sqrt(dl * dl + dd * dd);
        float surface = domain.surfaceAt(engine.posL()[i], engine.posD()[i]);
        if (run > SurfaceJumpArc.COMMIT_DISTANCE || !Float.isFinite(surface)
                || surface - engine.posY()[i] > SurfaceJumpArc.COMMIT_DEPTH) return false;
        // Close enough that the line to the point no longer says much: the approach is done.
        if (run < 0.15f) return true;
        float vl = engine.velL()[i], vd = engine.velD()[i];
        float flat = (float) Math.sqrt(vl * vl + vd * vd);
        return flat > 1e-4f && (vl * dl + vd * dd) / (flat * run) >= COMMIT_ALIGNMENT;
    }

    /**
     * The longest arc from fish {@code i}'s position and velocity out through the goal's launch
     * point along its heading whose underwater parts all lie in the water and whose re-entry surface
     * matches its launch surface, within the goal's duration. Null if none fits. Judged from engine
     * state and the domain every copy shares.
     */
    private @Nullable JumpPhase fitJump(int i, Goal goal) {
        return fitJump(i, goal.height, goal.durationTicks, goal.l, goal.y, goal.d, goal.hl, goal.hd,
                goal.playbackProfile);
    }

    private @Nullable JumpPhase fitJump(int i, float height, int maxTicks, float exitL, float exitY, float exitD,
                                        float hl, float hd) {
        return fitJump(i, height, maxTicks, exitL, exitY, exitD, hl, hd, SurfaceJumpPlaybackProfile.NORMAL);
    }

    private @Nullable JumpPhase fitJump(int i, float height, int maxTicks, float exitL, float exitY, float exitD,
                                        float hl, float hd, SurfaceJumpPlaybackProfile profile) {
        float surface = domain.surfaceAt(exitL, exitD);
        if (!Float.isFinite(surface)) return null;
        float dt = engine.tunables().dt(); // engine velocities are blocks a second; the arc's are a tick
        float l = engine.posL()[i], y = engine.posY()[i], d = engine.posD()[i];
        float vl = engine.velL()[i] * dt, vy = engine.velY()[i] * dt, vd = engine.velD()[i] * dt;
        double apex = SurfaceJumpArc.apexFor(height, surface - exitY, engine.lengths[i], MAX_SURFACE_JUMP_HEIGHT);
        // A lower breach also dives less on the way back in, which is what fits a shallow pool.
        for (double lower : ARC_APEX_SCALES) {
            for (double reach : ARC_REACHES) {
                for (double runIn : ARC_RUN_INS) {
                    SurfaceJumpArc arc = SurfaceJumpArc.of(l, y, d, vl, vy, vd, exitL, surface, exitD, hl, hd,
                            apex * lower, reach * profile.reachScale(), runIn);
                    if (arc.durationTicks() > maxTicks) continue;
                    if (!arcFits(arc, surface)) continue;
                    JumpPhase phase = new JumpPhase(steps, arc, profile);
                    if (phase.presentationDurationTicks <= MAX_SURFACE_JUMP_PRESENTATION_TICKS) return phase;
                }
            }
        }
        return null;
    }

    private boolean arcFits(SurfaceJumpArc arc, float surface) {
        double[] entry = arc.crossing(true);
        float entrySurface = domain.surfaceAt((float) entry[0], (float) entry[1]);
        if (!Float.isFinite(entrySurface) || Math.abs(entrySurface - surface) > ARC_SURFACE_TOLERANCE) return false;
        int ticks = (int) Math.ceil(arc.durationTicks());
        double flightFrom = arc.exitTick(), flightTo = arc.entryTick();
        for (int t = 0; t <= ticks; t++) {
            if (t > flightFrom && t < flightTo) continue; // above the water: the server checked the sky
            arc.sample(t, arcScratch);
            // A sample at the surface itself counts as the water just under it.
            float sy = (float) Math.min(arcScratch[1], surface - domain.topMargin() - 0.01f);
            if (!domain.contains((float) arcScratch[0], sy, (float) arcScratch[2])) return false;
        }
        return true;
    }

    /**
     * Retires each jump whose time is up. One that ran its course puts the engine fish where it
     * was drawn, still swimming along its heading at the glide's end speed, so the fish carries on
     * from the landing instead of being pulled back to its launch point; its goal, if any, ends there.
     */
    private void finishSurfaceJumps() {
        var it = surfaceJumps.entrySet().iterator();
        List<UUID> landed = null;
        while (it.hasNext()) {
            Map.Entry<UUID, JumpPhase> entry = it.next();
            JumpPhase jump = entry.getValue();
            jump.advance(steps, entry.getKey(), recentSurfaceCrossings);
            if (steps < jump.endStep()) continue;
            it.remove();
            if (jump.cancelStep >= 0) continue;
            int i = order.indexOf(entry.getKey());
            if (i < 0) continue;
            double[] end = jump.arc.sample(jump.arc.durationTicks(), arcScratch);
            float l = (float) end[0], y = (float) end[1], d = (float) end[2];
            if (domain.contains(l, y, d)) {
                float speed = (float) jump.arc.endSpeed() / engine.tunables().dt(); // blocks a second
                engine.relocate(i, l, y, d, speed * (float) jump.arc.headingL(), 0f,
                        speed * (float) jump.arc.headingD(), (float) jump.arc.headingYaw());
            }
            if (landed == null) landed = new ArrayList<>();
            landed.add(entry.getKey());
        }
        if (landed == null) return;
        boolean changed = false;
        for (UUID member : landed) {
            UUID goal = goalOf(member);
            if (goal == null) continue;
            goals.remove(goal);
            anchors.remove(goal);
            lastCompletedGoal = goal;
            changed = true;
        }
        if (changed) domain.rebuildShelters(List.copyOf(anchors.values()));
    }

    private void pruneRecentSurfaceCrossings() {
        recentSurfaceCrossings.removeIf(crossing ->
                steps - crossing.presentationStep() > SURFACE_JUMP_CROSSING_RETENTION_STEPS);
    }

    /**
     * Commits each goal's fish that is swimming in close enough to leap, and ends each goal whose
     * errand is over: the fish reached the point where no arc fits, its hold ran out, or it gave up
     * at the point without ever touching it. Judged from engine state every copy shares.
     */
    private void advanceGoals() {
        boolean changed = false;
        var it = goals.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Goal> entry = it.next();
            Goal goal = entry.getValue();
            if (!goal.arrived && !goal.failed) {
                int fish = order.indexOf(goal.member);
                if (fish >= 0 && !surfaceJumps.containsKey(goal.member) && readyToCommit(fish, goal)) {
                    // Not fitting yet is no failure: it is tried again as the fish swims closer.
                    JumpPhase jump = fitJump(fish, goal);
                    if (jump != null) {
                        surfaceJumps.put(goal.member, jump);
                        goal.arrived = true;
                    }
                }
            }
            if (!goal.failed && errandActive(entry.getKey())) continue;
            it.remove();
            anchors.remove(entry.getKey());
            cancelSurfaceJump(goal.member);
            changed = true;
        }
        if (changed) domain.rebuildShelters(List.copyOf(anchors.values()));
    }

    /**
     * Each seek's touch and let-go, judged from engine state every copy shares, so every copy
     * fires them at the same step: a touch when the creature reaches its bait, a let-go once it has
     * held there its time, as a swimmer's errand does.
     */
    private void advanceSeeks(@Nullable Consumer<Event> events) {
        boolean changed = false;
        var it = seeks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Seek> entry = it.next();
            Seek seek = entry.getValue();
            int i = order.indexOf(seek.member);
            if (i < 0) {
                it.remove();
                changed = true;
                continue;
            }
            if (seek.touchedAt < 0) {
                if (arrived(i, seek)) {
                    seek.touchedAt = steps;
                    if (events != null) events.accept(new Event(entry.getKey(), true));
                }
            } else if (steps - seek.touchedAt >= seek.holdSteps) {
                it.remove();
                changed = true;
                if (events != null) events.accept(new Event(entry.getKey(), false));
            }
        }
        if (changed) syncSeeks();
    }

    private boolean arrived(int i, Seek seek) {
        float dl = engine.posL()[i] - seek.l, dy = engine.posY()[i] - seek.y, dd = engine.posD()[i] - seek.d;
        return switch (spec.locomotion()) {
            case BENTHIC -> dl * dl + dd * dd <= CRAWL_ARRIVED * CRAWL_ARRIVED;
            case DRIFT -> dl * dl + dy * dy + dd * dd <= DRIFT_ARRIVED * DRIFT_ARRIVED;
            case ANCHORED -> engine.shapeDrive(i) <= EEL_EMERGED;
            default -> false;
        };
    }

    /** Re-declares every seek to the engine, whose slots a rebuild renumbers. */
    private void syncSeeks() {
        engine.clearSeeks();
        float speed = switch (spec.locomotion()) {
            case BENTHIC -> CRAWL_SEEK_SPEED;
            case DRIFT -> DRIFT_SEEK_SPEED;
            default -> 0f;
        };
        if (speed <= 0f) return; // an eel never moves to its bait
        for (Seek seek : seeks.values()) {
            int i = order.indexOf(seek.member);
            if (i >= 0) engine.seek(i, seek.l, seek.y, seek.d, speed);
        }
    }

    /** Applies {@code input} before the next step. False when it changed nothing. */
    public boolean apply(Input input) {
        return switch (input) {
            case Leave leave -> leave(leave.member());
            case Rejoin rejoin -> rejoin(rejoin);
            case Approach approach -> approach(approach);
            case Withdraw withdraw -> withdraw(withdraw.anchor());
            case Water water -> water(water.columns());
            case SurfaceJump jump -> surfaceJump(jump);
            case SurfaceGoal goal -> surfaceGoal(goal);
            case Watcher watcher -> {
                if (watcher.near()) engine.setWatcher(true, watcher.l(), watcher.y(), watcher.d());
                else engine.setWatcher(true, WATCHER_AWAY, 0f, WATCHER_AWAY);
                yield true;
            }
        };
    }

    private boolean surfaceJump(SurfaceJump jump) {
        if (!order.contains(jump.member()) || surfaceJumps.containsKey(jump.member())
                || jump.durationTicks() < 1 || jump.durationTicks() > MAX_SURFACE_JUMP_TICKS
                || !Float.isFinite(jump.height()) || jump.height() <= 0f
                || jump.height() > MAX_SURFACE_JUMP_HEIGHT || jump.playbackProfile() == null) return false;
        // No launch point given: the fish breaks the surface a little ahead of where it faces.
        int fish = order.indexOf(jump.member());
        float[] heading = yawHeading(fish);
        float exitL = engine.posL()[fish] + heading[0] * DIRECT_JUMP_LEAD;
        float exitD = engine.posD()[fish] + heading[1] * DIRECT_JUMP_LEAD;
        JumpPhase phase = fitJump(fish, jump.height(), jump.durationTicks(), exitL, engine.posY()[fish], exitD,
                heading[0], heading[1], jump.playbackProfile());
        if (phase == null) return false;
        surfaceJumps.put(jump.member(), phase);
        return true;
    }

    /**
     * The horizontal unit heading {@code member} will leap along if sent to breach at
     * {@code (l, d)}: the line it swims in on, which is the way its nose faces at the point, or
     * the way it already faces when the point is straight above it. Null for a non-member. The
     * server uses this to check the sky along the arc before it sends the goal.
     */
    public float @Nullable [] goalHeading(UUID member, float l, float d) {
        int fish = order.indexOf(member);
        if (fish < 0) return null;
        float dl = l - engine.posL()[fish], dd = d - engine.posD()[fish];
        float len = (float) Math.sqrt(dl * dl + dd * dd);
        if (len < GOAL_HEADING_MIN_RUN) return yawHeading(fish);
        return new float[]{dl / len, dd / len};
    }

    /** The unit heading fish {@code i}'s yaw faces (the engine's {@code atan2(-velD, velL)} convention). */
    private float[] yawHeading(int i) {
        double yaw = StrictMath.toRadians(engine.yawDeg[i]);
        return new float[]{(float) StrictMath.cos(yaw), (float) -StrictMath.sin(yaw)};
    }

    /** How far ahead of a fish told to jump on the spot ({@link SurfaceJump}) it breaks the surface, blocks. */
    private static final float DIRECT_JUMP_LEAD = 0.5f;

    /** A launch point closer than this, blocks, is straight above the fish: it leaps the way it faces. */
    private static final float GOAL_HEADING_MIN_RUN = 0.3f;

    private boolean surfaceGoal(SurfaceGoal goal) {
        int fish = order.indexOf(goal.member());
        if (fish < 0 || spec.locomotion() != Locomotion.FREE_SWIM || surfaceJumps.containsKey(goal.member())
                || anchors.containsKey(goal.goal()) || seeks.containsKey(goal.goal()) || goalOf(goal.member()) != null
                || goal.durationTicks() < 1 || goal.durationTicks() > MAX_SURFACE_JUMP_TICKS
                || !Float.isFinite(goal.height()) || goal.height() <= 0f || goal.height() > MAX_SURFACE_JUMP_HEIGHT
                || goal.playbackProfile() == null
                || !Float.isFinite(goal.l()) || !Float.isFinite(goal.y()) || !Float.isFinite(goal.d())) return false;
        // Nose first along the line it leaps along, which is the one it comes from, as for a bobber.
        float[] heading = goalHeading(goal.member(), goal.l(), goal.d());
        int presentationHoldTicks = goal.playbackProfile() == SurfaceJumpPlaybackProfile.NORMAL
                ? goal.durationTicks() : MAX_SURFACE_JUMP_PRESENTATION_TICKS;
        float hold = (presentationHoldTicks + GOAL_HOLD_SLACK_TICKS) / 20f;
        anchors.put(goal.goal(), Shelter.noseAnchor(goal.l(), goal.y(), goal.d(), heading[0], heading[1], hold,
                goal.goal()));
        domain.rebuildShelters(List.copyOf(anchors.values()));
        if (engine.sendToTrigger(anchorIndex(goal.goal()), fish, GOAL_SPEED_SCALE)) {
            goals.put(goal.goal(), new Goal(goal.member(), goal.l(), goal.y(), goal.d(), goal.durationTicks(),
                    goal.height(), goal.playbackProfile(), heading[0], heading[1]));
            return true;
        }
        anchors.remove(goal.goal());
        domain.rebuildShelters(List.copyOf(anchors.values()));
        return false;
    }

    /** The goal id {@code member} is swimming to, or null. */
    private @Nullable UUID goalOf(UUID member) {
        for (Map.Entry<UUID, Goal> entry : goals.entrySet()) {
            if (entry.getValue().member.equals(member)) return entry.getKey();
        }
        return null;
    }

    /** Whether goal {@code goal} is live: its fish is on its way to the launch point or breaching there. */
    public boolean goalActive(UUID goal) {
        return goals.containsKey(goal);
    }

    /** Whether {@code member} has a live surface goal. */
    public boolean hasGoal(UUID member) {
        return goalOf(member) != null;
    }

    /** Ends {@code member}'s goal, if any, and any jump it began. True if there was one. */
    private boolean endGoalOf(UUID member) {
        UUID goal = goalOf(member);
        if (goal == null) return false;
        goals.remove(goal);
        anchors.remove(goal);
        domain.rebuildShelters(List.copyOf(anchors.values()));
        cancelSurfaceJump(member);
        return true;
    }

    /** Interpolated vertical render offset for a member; the fishsim position remains unchanged. */
    public float surfaceJumpOffset(UUID member, float partialTick) {
        float[] pose = new float[7];
        return surfaceJumpPose(member, partialTick, pose) ? pose[1] : 0f;
    }

    /**
     * {@code member}'s breach pose at {@code partialTick}, for the renderer: {@code out} gets
     * {@code [dx, dy, dz, pitch° (nose up), yaw° (engine frame), yaw weight, extra tail phase]},
     * the offset from the engine fish's interpolated position to the drawn body, in the engine's
     * render frame. False, and {@code out} untouched, with no jump.
     */
    public boolean surfaceJumpPose(UUID member, float partialTick, float[] out) {
        JumpPhase jump = surfaceJumps.get(member);
        int i = order.indexOf(member);
        if (jump == null || i < 0) return false;
        double step = steps - 1d + partialTick;
        double[] arc = jump.sampleAt(partialTick, new double[7]);
        float fold = (float) jump.fold(step);
        float[] live = new float[3];
        engine.localAt(i, partialTick, live);
        float[] render = new float[3];
        engine.toRender(((float) arc[0] - live[0]) * fold, ((float) arc[1] - live[1]) * fold,
                ((float) arc[2] - live[2]) * fold, render);
        out[0] = render[0];
        out[1] = render[1];
        out[2] = render[2];
        out[3] = (float) arc[3] * fold;
        out[4] = (float) arc[4];
        out[5] = (float) arc[5] * fold;
        out[6] = (float) arc[6];
        return true;
    }

    /**
     * The surface crossings of {@code member}'s jump that fall in steps {@code (after, through]}:
     * for each, {@code splash} gets {@code [l, d, forward speed (blocks a tick), hl, hd, entry ? 1 : 0]},
     * sim-local. A cancelled jump makes no crossing after its cancel.
     */
    public void surfaceJumpCrossings(UUID member, long after, long through,
                                     Consumer<SurfaceJumpCrossing> crossingConsumer) {
        if (order.indexOf(member) < 0 || through <= after) return;
        for (SurfaceJumpCrossing crossing : recentSurfaceCrossings) {
            if (crossing.member().equals(member) && crossing.presentationStep() > after
                    && crossing.presentationStep() <= through) crossingConsumer.accept(crossing);
        }
    }

    /** Current canonical playback clock, or null when the member has no active breach. */
    public @Nullable SurfaceJumpClock surfaceJumpClock(UUID member) {
        JumpPhase jump = surfaceJumps.get(member);
        if (jump == null) return null;
        double apex = jump.arc.exitTick() + (jump.arc.entryTick() - jump.arc.exitTick()) * 0.5;
        return new SurfaceJumpClock(jump.profile, jump.previousSourceTime, jump.sourceTime, jump.playbackRate,
                jump.arc.exitTick(), apex, jump.arc.entryTick(), jump.arc.durationTicks(), jump.startStep,
                jump.presentationDurationTicks, jump.cancelStep >= 0);
    }

    /** Samples the immutable geometric arc at source time; used to reconstruct client ribbon state. */
    public boolean surfaceJumpArcSample(UUID member, double sourceTime, double[] out) {
        JumpPhase jump = surfaceJumps.get(member);
        if (jump == null) return false;
        jump.arc.sample(sourceTime, out);
        return true;
    }

    /** Whether {@code goal}'s fish has begun its leap (committed on the way in, or reached the point). */
    public boolean goalStarted(UUID goal) {
        Goal g = goals.get(goal);
        return g != null && g.arrived;
    }

    /** Whether {@code goal} was the last to run its jump to the end: its fish breached and landed. */
    public boolean goalCompleted(UUID goal) {
        return goal.equals(lastCompletedGoal);
    }

    public boolean hasSurfaceJump(UUID member) {
        return surfaceJumps.containsKey(member);
    }

    private boolean leave(UUID member) {
        int gone = order.indexOf(member);
        if (gone < 0) return false;
        endGoalOf(member);
        surfaceJumps.remove(member);
        int[] carry = new int[order.size() - 1];
        for (int i = 0, j = 0; i < order.size(); i++) {
            if (i != gone) carry[j++] = i;
        }
        order.remove(gone);
        engine.rebuildPreserving(specs(), carry, seed, 0f, ROTATION_JITTER, domain);
        seeks.values().removeIf(seek -> seek.member.equals(member));
        syncSeeks();
        return true;
    }

    private boolean rejoin(Rejoin rejoin) {
        if (order.contains(rejoin.member())) return false;
        int[] carry = new int[order.size() + 1];
        for (int i = 0; i < order.size(); i++) carry[i] = i;
        carry[order.size()] = -1;
        order.add(rejoin.member());
        engine.rebuildPreserving(specs(), carry, seed, 0f, ROTATION_JITTER, domain);
        place(order.size() - 1, rejoin.l(), rejoin.y(), rejoin.d());
        syncSeeks();
        return true;
    }

    private boolean approach(Approach approach) {
        int fish = order.indexOf(approach.member());
        if (fish < 0 || anchors.containsKey(approach.anchor()) || seeks.containsKey(approach.anchor())) return false;
        if (spec.locomotion() != Locomotion.FREE_SWIM) {
            // A non-swimmer seeks its bait instead (encounter spec §4); one errand per creature.
            for (Seek seek : seeks.values()) {
                if (seek.member.equals(approach.member())) return false;
            }
            int hold = Math.max(1, Math.round(approach.holdSeconds() * 20f));
            seeks.put(approach.anchor(), new Seek(approach.member(), approach.l(), approach.y(), approach.d(), hold));
            syncSeeks();
            cancelSurfaceJump(approach.member());
            return true;
        }
        // A bobber outranks a breach: the member's surface goal, if it has one, is called off.
        endGoalOf(approach.member());
        // Nose first along the line it comes from: the fish's own side of the bobber.
        float dl = approach.l() - engine.posL()[fish], dd = approach.d() - engine.posD()[fish];
        anchors.put(approach.anchor(), Shelter.noseAnchor(approach.l(), approach.y(), approach.d(), dl, dd,
                approach.holdSeconds(), approach.anchor()));
        domain.rebuildShelters(List.copyOf(anchors.values()));
        if (engine.sendToTrigger(anchorIndex(approach.anchor()), fish, approach.speedScale())) {
            cancelSurfaceJump(approach.member());
            return true;
        }
        anchors.remove(approach.anchor());
        domain.rebuildShelters(List.copyOf(anchors.values()));
        return false;
    }

    private boolean withdraw(UUID anchor) {
        Goal goal = goals.get(anchor);
        if (goal != null) return endGoalOf(goal.member);
        if (seeks.remove(anchor) != null) {
            syncSeeks();
            return true;
        }
        if (anchors.remove(anchor) == null) return false;
        domain.rebuildShelters(List.copyOf(anchors.values()));
        return true;
    }

    private boolean water(List<WaterDomain.Column> columns) {
        WaterDomain next = buildDomain(origin, columns, body);
        if (next == null) return false;
        next.rebuildShelters(List.copyOf(anchors.values()));
        domain = next;
        cancelAllSurfaceJumps();
        int[] carry = new int[order.size()];
        for (int i = 0; i < carry.length; i++) carry[i] = i;
        engine.rebuildPreserving(specs(), carry, seed, 0f, ROTATION_JITTER, domain);
        syncSeeks();
        return true;
    }

    private void cancelSurfaceJump(UUID member) {
        JumpPhase jump = surfaceJumps.get(member);
        if (jump == null || jump.cancelStep >= 0) return;
        jump.cancelStep = steps;
    }

    private void cancelAllSurfaceJumps() {
        for (UUID member : List.copyOf(surfaceJumps.keySet())) cancelSurfaceJump(member);
    }

    private int anchorIndex(UUID anchor) {
        int i = 0;
        for (UUID key : anchors.keySet()) {
            if (key.equals(anchor)) return i;
            i++;
        }
        return -1;
    }

    /** Whether the fish sent to {@code anchor} is still on its way there or holding at it. */
    public boolean errandActive(UUID anchor) {
        if (seeks.containsKey(anchor)) return true;
        int s = anchorIndex(anchor);
        return s >= 0 && engine.triggerFish(s) >= 0;
    }

    /** The member on its way to, or holding at, {@code anchor}, or null. */
    public @Nullable UUID errandMember(UUID anchor) {
        Seek seek = seeks.get(anchor);
        if (seek != null) return seek.member;
        int s = anchorIndex(anchor);
        int fish = s < 0 ? -1 : engine.triggerFish(s);
        return fish >= 0 && fish < order.size() ? order.get(fish) : null;
    }

    public boolean hasAnchor(UUID anchor) {
        return anchors.containsKey(anchor) || seeks.containsKey(anchor);
    }

    /** Where {@code anchor}'s fish puts its nose, in the school's frame, or null if there is no such anchor. */
    public float @Nullable [] anchorAt(UUID anchor) {
        Seek seek = seeks.get(anchor);
        if (seek != null) return new float[]{seek.l, seek.y, seek.d};
        Shelter shelter = anchors.get(anchor);
        if (shelter == null) return null;
        Shelter.Mouth face = shelter.mouths().get(0);
        return new float[]{face.centerL(), face.centerY(), face.centerD()};
    }

    /**
     * Where under a bobber at {@code (l, y, d)} a fish can put its nose: the first point at or
     * below it (below the surface, for a bobber still sinking from its cast), down to {@link #ANCHOR_SEARCH_DEPTH}, where the whole anchor (its box reaches
     * {@link Shelter#NOSE_ANCHOR_HALF} above and below) is in the water by the engine's own rule,
     * which is what the engine checks before it will send a fish there. A floating bobber sits
     * above the head-room a fish keeps under the surface, so this is usually a little below it.
     * Null when there is no such point. Not part of the lockstep: the server calls it, and ships
     * the answer inside its {@link Approach}.
     */
    public float @Nullable [] anchorPoint(float l, float y, float d) {
        // From the surface, not the bobber: a bobber still plunging from its cast (vanilla damps
        // it on entry but lets it sink up to about a block before it bobs back up) is already in
        // the water, and anchoring at that depth left the fish biting well under the floating bobber.
        float surface = domain.surfaceAt(l, d);
        if (surface > y && surface - y <= ANCHOR_SEARCH_DEPTH) y = surface;
        float h = Shelter.NOSE_ANCHOR_HALF;
        for (float drop = 0f; drop <= ANCHOR_SEARCH_DEPTH; drop += ANCHOR_SEARCH_STEP) {
            float at = y - drop;
            if (domain.contains(l, at + h, d) && domain.contains(l, at, d) && domain.contains(l, at - h, d)) {
                return new float[]{l, at, d};
            }
        }
        return null;
    }

    /**
     * Where {@code member} goes to take a bobber at {@code (l, y, d)}, by its class (encounter spec
     * §4), or null when it cannot: a swimmer or drifter to {@link #anchorPoint} under it; a crawler
     * to the floor straight beneath it, at most {@link #BOTTOM_BITE_DEPTH} down (the bottom bite);
     * an eel only when the bobber is within {@link #EEL_REACH} of its burrow and it is out, to its
     * own spot. Server-side, like {@link #anchorPoint}; the answer ships in the {@link Approach}.
     */
    public float @Nullable [] goalFor(UUID member, float l, float y, float d) {
        int i = order.indexOf(member);
        if (i < 0) return null;
        return switch (spec.locomotion()) {
            case BENTHIC -> {
                float floor = domain.floor().heightAt(l, d);
                float top = Math.min(y, domain.surfaceAt(l, d));
                yield Float.isFinite(floor) && top - floor <= BOTTOM_BITE_DEPTH && domain.contains(l, floor + 0.2f, d)
                        ? new float[]{l, floor, d} : null;
            }
            case ANCHORED -> {
                float dl = engine.posL()[i] - l, dd = engine.posD()[i] - d;
                yield dl * dl + dd * dd <= EEL_REACH * EEL_REACH && engine.shapeDrive(i) <= EEL_EMERGED
                        ? new float[]{engine.posL()[i], engine.posY()[i], engine.posD()[i]} : null;
            }
            default -> anchorPoint(l, y, d);
        };
    }

    /** How far below a bobber its anchor may be looked for, blocks: the hook hangs under it. */
    static final float ANCHOR_SEARCH_DEPTH = 1.5f;
    static final float ANCHOR_SEARCH_STEP = 0.1f;

    /** Fish #{@code i}'s heading for display, degrees, from its velocity ({@code atan2(l, d)}). */
    public float yawDeg(int i) {
        return (float) Math.toDegrees(StrictMath.atan2(engine.velL()[i], engine.velD()[i]));
    }

    /**
     * A fingerprint of where every fish is right now: two copies at the same step agree on it
     * exactly when they agree on every position bit. Order-sensitive, as the engine is.
     */
    public int checksum() {
        int h = order.size();
        for (int i = 0; i < order.size(); i++) {
            UUID member = order.get(i);
            h = 31 * h + member.hashCode();
            h = 31 * h + Float.floatToIntBits(engine.posL()[i]);
            h = 31 * h + Float.floatToIntBits(engine.posY()[i]);
            h = 31 * h + Float.floatToIntBits(engine.posD()[i]);
            JumpPhase jump = surfaceJumps.get(member);
            h = 31 * h + (jump == null ? 0 : jump.fingerprint());
        }
        for (Map.Entry<UUID, Goal> entry : goals.entrySet()) {
            Goal goal = entry.getValue();
            h = 31 * h + entry.getKey().hashCode();
            h = 31 * h + goal.member.hashCode();
            h = 31 * h + (goal.arrived ? 1 : 0);
            h = 31 * h + goal.playbackProfile.ordinal();
        }
        return h;
    }

    /** Whether a member placed at {@code (l, y, d)} would have room for its whole body. */
    public boolean bodyFits(float l, float y, float d) {
        return domain.bodyFits(l, y, d, body);
    }
}
