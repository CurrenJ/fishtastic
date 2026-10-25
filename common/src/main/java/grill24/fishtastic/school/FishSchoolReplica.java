package grill24.fishtastic.school;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;

/**
 * A client's copy of one school's motion, kept in lockstep with the server's
 * (docs/interactive-fish-schools-motion-plan.md). It is a {@link FishSchoolSim} built from the
 * epoch's setup and fed the server's inputs at their stamped steps, so it computes the very
 * positions the server did — nothing here moves a fish.
 *
 * <p>It runs {@link #DELAY_TICKS} behind the server's step (last reported, plus the ticks since),
 * so inputs sent the moment they happen arrive before they are due. An input stamped for a step it
 * has already passed, or a position fingerprint that disagrees with the server's, means the copy is
 * wrong: it rebuilds from the setup and replays every input, up to {@link #REPLAY_STEPS_PER_TICK}
 * steps a tick within the caller's time budget, and is not drawn until it has caught up. Client-independent, so the scheduling is testable.
 */
public final class FishSchoolReplica {

    /** How far behind the server's step the copy runs, ticks: the bite-lag budget (decision M4). */
    public static final int DELAY_TICKS = 3;
    /**
     * Most replay steps a tick while catching up: a whole default visit (12,000 steps) in three
     * ticks, if the caller's time budget allows.
     */
    public static final int REPLAY_STEPS_PER_TICK = 4000;
    /** Fewest replay steps a tick, budget or not, so every copy catching up makes progress. */
    public static final int MIN_REPLAY_STEPS_PER_TICK = 32;
    /** Caught up but this far behind (a client stall): replay forward instead of trickling. */
    public static final int RESYNC_LAG = 40;

    private final UUID school;
    private final int epoch;
    private final FishSchoolSim.Setup setup;
    private final List<FishSchoolSim.Stamped> inputs = new ArrayList<>();
    /** Server step → fingerprint there, waiting for the copy to reach that step. */
    private final TreeMap<Long, Integer> checks = new TreeMap<>();
    private @Nullable FishSchoolSim sim;
    private int next;
    private long anchorStep;
    private int ticksSinceAnchor;
    private boolean caughtUp;
    private boolean replayNeeded;
    private boolean replayedForMismatch;
    private int mismatches;
    private int replays;

    public FishSchoolReplica(UUID school, int epoch, FishSchoolSim.Setup setup,
                             List<FishSchoolSim.Stamped> inputs, long serverStep) {
        this.school = school;
        this.epoch = epoch;
        this.setup = setup;
        this.inputs.addAll(inputs);
        this.anchorStep = serverStep;
        this.sim = FishSchoolSim.create(setup);
    }

    public UUID school() { return school; }
    public int epoch() { return epoch; }
    /** The epoch's setup, as the server sent it. */
    public FishSchoolSim.Setup setup() { return setup; }
    /** The copy's sim, or null when its setup could not be built. Read-only to callers. */
    public @Nullable FishSchoolSim sim() { return sim; }
    /** Whether the copy has reached the server's step and may be drawn. */
    public boolean caughtUp() { return caughtUp; }
    /** Fingerprints that disagreed with the server's so far. */
    public int mismatches() { return mismatches; }
    /** Rebuilds from the setup so far. */
    public int replays() { return replays; }

    /** The step the copy is aiming for this tick. */
    public long targetStep() {
        return Math.max(0, anchorStep + ticksSinceAnchor - DELAY_TICKS);
    }

    /** New inputs from the server, in the order it applied them. */
    public void addInputs(List<FishSchoolSim.Stamped> more) {
        for (FishSchoolSim.Stamped stamped : more) {
            // Due at a step the copy has already taken: it went wrong there, so start over.
            if (sim != null && stamped.step() < sim.steps()) replayNeeded = true;
            inputs.add(stamped);
        }
    }

    /** The server's step now and its fingerprint at that step. */
    public void observe(long serverStep, int checksum) {
        anchorStep = serverStep;
        ticksSinceAnchor = 0;
        checks.put(serverStep, checksum);
    }

    /**
     * One client tick: up to two steps once caught up (one normally, two to close a small gap), the
     * replay budget while catching up.
     *
     * @return steps taken
     */
    public int tick() {
        return tick(null);
    }

    /**
     * As {@link #tick()}, handing {@code events} the touches and let-gos of the steps taken — but
     * only once caught up: a copy replaying a visit must not show its past bites again.
     */
    public int tick(@Nullable java.util.function.Consumer<FishSchoolSim.Event> events) {
        return tick(events, Long.MAX_VALUE);
    }

    /**
     * As {@link #tick(java.util.function.Consumer)}, but a copy catching up also stops once
     * {@link System#nanoTime()} passes {@code replayDeadline}, after at least
     * {@link #MIN_REPLAY_STEPS_PER_TICK} steps: one time budget shared by every copy keeps a late
     * join off the frame time (performance plan, step E). A caught-up copy ignores it.
     */
    public int tick(@Nullable java.util.function.Consumer<FishSchoolSim.Event> events, long replayDeadline) {
        ticksSinceAnchor++;
        if (replayNeeded) restart();
        if (sim == null) return 0;
        long target = targetStep();
        long behind = target - sim.steps();
        if (caughtUp && behind > RESYNC_LAG) caughtUp = false;
        int budget = !caughtUp ? REPLAY_STEPS_PER_TICK : behind > 1 ? 2 : (int) Math.max(0, behind);
        java.util.function.Consumer<FishSchoolSim.Event> live = caughtUp ? events : null;
        int taken = 0;
        while (taken < budget && sim.steps() < target && !replayNeeded) {
            if (!caughtUp && taken >= MIN_REPLAY_STEPS_PER_TICK && taken % MIN_REPLAY_STEPS_PER_TICK == 0
                    && System.nanoTime() - replayDeadline >= 0) break;
            applyDue();
            sim.step(live);
            taken++;
            verify();
        }
        if (!caughtUp && !replayNeeded && sim.steps() >= target - 1) caughtUp = true;
        return taken;
    }

    private void applyDue() {
        while (next < inputs.size() && inputs.get(next).step() <= sim.steps()) {
            FishSchoolSim.Stamped stamped = inputs.get(next++);
            if (stamped.step() < sim.steps()) {
                replayNeeded = true;
                return;
            }
            sim.apply(stamped.input());
        }
    }

    private void verify() {
        // A fingerprint for a step already behind the copy can no longer be checked.
        while (!checks.isEmpty() && checks.firstKey() < sim.steps()) checks.pollFirstEntry();
        Integer expected = checks.remove(sim.steps());
        if (expected == null || expected == sim.checksum()) return;
        mismatches++;
        // One rebuild per copy: it fixes a missed or misordered input. If the copy still disagrees
        // after that, the engine itself diverged, and replaying would only repeat it.
        if (!replayedForMismatch) {
            replayedForMismatch = true;
            replayNeeded = true;
        }
    }

    private void restart() {
        sim = FishSchoolSim.create(setup);
        next = 0;
        caughtUp = false;
        replayNeeded = false;
        replays++;
    }
}
