package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.OwnerScript;

import java.util.ArrayList;
import java.util.List;

/**
 * One benthic group on one script, with the formation measures (docs/fish-companions-benthic-follow.md
 * §4, §20): line order, members overlapping each other, the line's wave (how far a follower strays
 * from the way its predecessor went) and spacing, the crowd's spread, and members underfoot.
 */
final class GroupRun {
    static final int TICKS = 2400;

    final String script;
    final long seed;
    final BenthicGroup group;
    final OwnerScript owner;

    /** Ticks a follower stood on a newer point of the leader's way than the follower before it. */
    int orderTicks;
    /** Ticks measured for line order (following, all on the ground and on the leader's way). */
    int orderMeasured;
    /** Ticks two grounded members interpenetrated by more than {@link #OVERLAP_DEPTH} of their radii. */
    int overlapTicks;
    /** Ticks two grounded members were stacked: centres closer than half their contact distance. */
    int deepTicks;
    double deepestOverlap;
    /** Overlap ticks by situation: start (first 3 s), follow, idle, transition (a hop, climb, dismount, tumble or swim). */
    final java.util.Map<String, Integer> overlapBy = new java.util.TreeMap<>();
    /** Ticks a grounded member's centre was within 0.4 of any player's feet (owner or passer). */
    int underfootTicks;
    /** Line followers while following: distance from the predecessor's path of the last 2 s. */
    double waveSum, waveMax;
    int waveN;
    /** Line followers while following: gap to the predecessor. */
    double gapSum, gapSq;
    int gapN;
    /** Crowd: mean distance of grounded members from their centroid, owner still and moving. */
    double spreadStillSum, spreadMovingSum;
    int spreadStillN, spreadMovingN;
    long fades;
    int[] climbs, dismounts;
    final List<String> slotErrors = new ArrayList<>();
    /** Ticks a cat or hostile was near (within 6 blocks of the owner), and of those, members up on the owner. */
    int threatTicks, threatRiding;

    static final double OVERLAP_DEPTH = 0.15;

    GroupRun(String script, long seed, float length, Temperament tm, BenthicGroup.Formation formation) {
        this.script = script;
        this.seed = seed;
        owner = OwnerScript.named(script, seed);
        CompanionWorld world = owner.nextWorld();
        group = new BenthicGroup(CompanionTunables.defaults(), seed, length, tm, GroundGait.CRAWL, 3, formation, world);
        first = world;
    }

    /** A party-built formation: one member per length, leader first (docs/fish-companions-party.md §3). */
    GroupRun(String script, long seed, float[] lengths, Temperament tm, BenthicGroup.Formation formation) {
        this.script = script;
        this.seed = seed;
        owner = OwnerScript.named(script, seed);
        CompanionWorld world = owner.nextWorld();
        group = new BenthicGroup(CompanionTunables.defaults(), seed, lengths, tm, GroundGait.CRAWL, formation, world);
        first = world;
    }

    /** Spacing, overtakes, lag and contact (party spec §8), over members walking the ground. */
    grill24.fishsim.harness.companion.GroupShapeMetrics.Result shape;

    private CompanionWorld first;
    /** Called every tick after the step, for extra per-test checks. */
    interface Watch { void tick(int tick, CompanionWorld world, BenthicGroup group); }

    /** Ticks to run (two minutes unless a test needs the script's cycle to repeat). */
    int ticks = TICKS;

    GroupRun ticks(int t) {
        ticks = t;
        return this;
    }

    GroupRun run(Watch watch) {
        int n = group.count();
        float[] lengths = new float[n];
        double[] radii = new double[n];
        for (int i = 0; i < n; i++) {
            lengths[i] = group.length(i);
            radii[i] = group.member(i).radius();
        }
        var shapes = new grill24.fishsim.harness.companion.GroupShapeMetrics(lengths, radii);
        Vec[] shapePos = new Vec[n];
        boolean[] shapeActive = new boolean[n];
        Vec[][] history = new Vec[n][40];
        Vec[] travelHistory = new Vec[20];
        // The leader's own way over the last 10 s, and when it was at each point.
        Vec[] leaderTrail = new Vec[200];
        int[] leaderTick = new int[200];
        int leaderAt = 0;
        for (int tick = 0; tick < ticks; tick++) {
            CompanionWorld world = tick == 0 ? first : owner.nextWorld();
            group.step(world);
            OwnerState o = world.owner();
            for (int i = 0; i < n; i++) history[i][tick % 40] = group.member(i).position();
            if (group.leader().onGround() && group.leader().feetDown()) {
                leaderTrail[leaderAt % 200] = group.leader().position();
                leaderTick[leaderAt % 200] = tick;
                leaderAt++;
            }
            Vec travel = o.horizontalSpeed() > 1 ? o.velocity().horizontal().normalize() : null;
            Vec old = travelHistory[tick % 20];
            travelHistory[tick % 20] = travel;
            boolean straight = travel != null && old != null && travel.dot(old) > 0.95;

            // Members underfoot, of the owner or of any player passing.
            List<Vec> feet = new ArrayList<>();
            feet.add(o.feet());
            for (PointOfInterest p : world.points()) if (p.kind() == PointOfInterest.Kind.PLAYER) feet.add(p.pos());
            for (int i = 0; i < n; i++) {
                CompanionAgent a = group.member(i);
                if (!a.onGround() || !a.feetDown()) continue;
                for (Vec f : feet) {
                    if (Math.abs(f.y() - (a.position().y() - a.radius())) < 0.8
                            && a.position().sub(f).horizontalLength() < 0.4) {
                        underfootTicks++;
                        break;
                    }
                }
            }
            // Members inside each other.
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    CompanionAgent a = group.member(i), b = group.member(j);
                    if (!a.onGround() || !b.onGround()) continue;
                    double sum = a.radius() + b.radius();
                    Vec d = a.position().sub(b.position());
                    if (Math.abs(d.y()) > sum) continue;
                    double depth = (sum - d.horizontalLength()) / sum;
                    if (depth > 0.5) deepTicks++;
                    if (depth > OVERLAP_DEPTH) {
                        overlapTicks++;
                        overlapBy.merge(situation(tick, o, a, b), 1, Integer::sum);
                    }
                    deepestOverlap = Math.max(deepestOverlap, depth);
                }
            }
            boolean allDown = true;
            for (int i = 0; i < n; i++) allDown &= group.member(i).onGround() && group.member(i).feetDown();
            if (group.formation() == BenthicGroup.Formation.LINE && allDown && travel != null && tick >= 40) {
                // Order along the line, not along the owner's way (round a hairpin a follower still
                // walking to the bend is ahead of the owner's new heading, and rightly so): each
                // follower stands where the leader was earlier than where the one before it stands.
                int[] when = new int[n];
                boolean measured = true;
                for (int i = 1; i < n; i++) {
                    when[i] = -1;
                    double best = 0.6;
                    for (int k = 0; k < Math.min(leaderAt, 200); k++) {
                        double d = group.member(i).position().sub(leaderTrail[k]).horizontalLength();
                        if (d < best) { best = d; when[i] = leaderTick[k]; }
                    }
                    String m = group.member(i).landMode();
                    if (when[i] < 0 || !"GROUND_FOLLOW".equals(m) && !"CATCH_UP".equals(m)) measured = false;
                }
                if (measured) {
                    orderMeasured++;
                    boolean outOfOrder = false;
                    for (int i = 2; i < n; i++) if (when[i] > when[i - 1] + 5) outOfOrder = true;
                    if (outOfOrder) orderTicks++;
                }
            }
            if (group.formation() == BenthicGroup.Formation.LINE && allDown && straight && tick >= 40) {
                for (int i = 1; i < n; i++) {
                    CompanionAgent a = group.member(i), ahead = group.member(i - 1);
                    if (!"GROUND_FOLLOW".equals(a.landMode()) && !"CATCH_UP".equals(a.landMode())) continue;
                    double wave = Double.MAX_VALUE;
                    for (Vec p : history[i - 1]) if (p != null) wave = Math.min(wave, a.position().sub(p).horizontalLength());
                    waveSum += wave;
                    waveMax = Math.max(waveMax, wave);
                    waveN++;
                    double gap = a.position().sub(ahead.position()).horizontalLength();
                    gapSum += gap;
                    gapSq += gap * gap;
                    gapN++;
                }
            }
            if (group.formation() == BenthicGroup.Formation.CROWD && allDown) {
                Vec c = Vec.ZERO;
                for (int i = 0; i < n; i++) c = c.add(group.member(i).position());
                c = c.scale(1.0 / n);
                double spread = 0;
                for (int i = 0; i < n; i++) spread += group.member(i).position().sub(c).horizontalLength();
                spread /= n;
                if (o.horizontalSpeed() < 0.3) { spreadStillSum += spread; spreadStillN++; }
                else if (o.horizontalSpeed() > 1) { spreadMovingSum += spread; spreadMovingN++; }
            }
            // Perched members never share a slot.
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    CompanionAgent a = group.member(i), b = group.member(j);
                    if (a.riding() && b.riding() && a.perchSlot() == b.perchSlot())
                        slotErrors.add("t=" + tick + " members " + i + "," + j + " share " + a.perchSlot());
                }
            }
            for (PointOfInterest p : world.points()) {
                if ((p.kind() == PointOfInterest.Kind.CAT || p.kind() == PointOfInterest.Kind.HOSTILE)
                        && p.pos().sub(o.feet()).horizontalLength() < 6) {
                    threatTicks++;
                    for (int i = 0; i < n; i++) if (group.member(i).riding()) threatRiding++;
                    break;
                }
            }
            for (int i = 0; i < n; i++) {
                CompanionAgent a = group.member(i);
                shapePos[i] = a.position();
                shapeActive[i] = a.onGround() && a.feetDown() && !a.riding() && a.appear() >= 1f;
            }
            shapes.tick(shapePos, group.leader().velocity(), shapeActive);
            if (watch != null) watch.tick(tick, world, group);
        }
        shape = shapes.result();
        climbs = new int[n];
        dismounts = new int[n];
        for (int i = 0; i < n; i++) {
            fades += group.member(i).groundFades();
            climbs[i] = group.member(i).climbs();
            dismounts[i] = group.member(i).dismounts();
        }
        return this;
    }

    private static final java.util.Set<String> STEADY = java.util.Set.of("GROUND_IDLE", "GROUND_FOLLOW", "CATCH_UP");

    static String situation(int tick, OwnerState o, CompanionAgent a, CompanionAgent b) {
        if (tick < 60) return "start";
        if (!STEADY.contains(a.landMode()) || !STEADY.contains(b.landMode())) return "transition";
        return o.horizontalSpeed() > 0.6 ? "follow" : "idle";
    }

    double orderShare() { return orderMeasured == 0 ? 0 : orderTicks / (double) orderMeasured; }
    double meanWave() { return waveN == 0 ? 0 : waveSum / waveN; }
    double meanGap() { return gapN == 0 ? 0 : gapSum / gapN; }
    double gapCv() {
        if (gapN < 2) return 0;
        double m = meanGap();
        return Math.sqrt(Math.max(0, gapSq / gapN - m * m)) / m;
    }
    double spreadStill() { return spreadStillN == 0 ? Double.NaN : spreadStillSum / spreadStillN; }
    double spreadMoving() { return spreadMovingN == 0 ? Double.NaN : spreadMovingSum / spreadMovingN; }

    String summary() {
        return String.format("order=%.2f%% (%d/%d) overlap=%d deep=%d deepest=%.2f underfoot=%d wave=%.3f/%.3f gap=%.2f cv=%.2f "
                        + "spread still=%.2f moving=%.2f fades=%d climbs=%s dismounts=%s slotErrors=%d threat=%d riding=%d by=%s",
                100 * orderShare(), orderTicks, orderMeasured, overlapTicks, deepTicks, deepestOverlap, underfootTicks,
                meanWave(), waveMax, meanGap(), gapCv(), spreadStill(), spreadMoving(), fades,
                java.util.Arrays.toString(climbs), java.util.Arrays.toString(dismounts), slotErrors.size(),
                threatTicks, threatRiding, overlapBy);
    }
}
