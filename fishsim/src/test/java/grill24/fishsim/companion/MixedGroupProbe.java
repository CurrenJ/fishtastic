package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import grill24.fishsim.harness.companion.GroupShapeMetrics;
import grill24.fishsim.harness.companion.OwnerScript;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;

/**
 * Diagnostic (docs/fish-companions-party.md §3, §8): party-built groups of mixed sizes against the
 * identical-member groups they replace, over 8 equal seeds. Per config, the mean and the worst run of
 * each measure. Run with -PsimStdout; -PsweepSeeds=N changes the seed count.
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class MixedGroupProbe {

    static final int TICKS = 2400;

    /** The run's tunables: defaults, with -PtweakA applied (e.g. -PtweakA=partySwimSeparation=false). */
    private static CompanionTunables tunables() {
        CompanionTunables t = CompanionTunables.defaults();
        SeedSweepProbe.tweak(System.getProperty("tweakA")).accept(t);
        return t;
    }

    private static int seeds() {
        return Integer.parseInt(System.getProperty("sweepSeeds", "8"));
    }

    private record School(String name, float[] lengths) {}

    private record Ground(String name, float[] lengths, BenthicGroup.Formation formation) {}

    static final List<String> GROUND_SCRIPTS = List.of("walk_flat", "walk_meander", "stairs_up_down",
            "long_idle_then_walk", "sprint_away", "group_passers", "walk_backward");

    @Test
    void schools() {
        List<School> configs = List.of(
                new School("trio 18x3 (ref)", new float[]{0.18f, 0.18f, 0.18f}),
                new School("mixed 9/16/24", CompanionRun.SCHOOL_MIXED),
                new School("mixed 24/16/9", new float[]{0.24f, 0.16f, 0.09f}),
                new School("pair 16/24", CompanionRun.SCHOOL_PAIR),
                new School("big 40x3 (ref)", new float[]{0.40f, 0.40f, 0.40f}),
                new School("big 25/40/60", CompanionRun.SCHOOL_BIG),
                new School("big 60/40/25", new float[]{0.60f, 0.40f, 0.25f}));
        for (School c : configs) {
            List<CompanionRun.FollowerMetrics> fm = new ArrayList<>();
            List<GroupShapeMetrics.Result> shapes = new ArrayList<>();
            List<String> keys = new ArrayList<>();
            int trioFails = 0;
            List<String> failed = new ArrayList<>();
            List<Integer> failFlags = new ArrayList<>();
            for (String script : OwnerScript.NAMES) {
                for (long seed = 1; seed <= seeds(); seed++) {
                    CompanionRun run = CompanionRun.school(script, seed, c.lengths(), Temperament.BASELINE,
                            tunables(), true);
                    run.run(TICKS);
                    CompanionRun.FollowerMetrics f = run.followerMetrics();
                    fm.add(f);
                    shapes.add(run.schoolShape());
                    keys.add(script + "#" + seed);
                    String why = trioBounds(script, f, run.group.followerCount());
                    failFlags.add(why == null ? 0 : 1);
                    if (why != null) {
                        trioFails++;
                        if (failed.size() < 6) failed.add(script + "#" + seed + " " + why);
                    }
                }
            }
            System.out.printf(Locale.ROOT, "SCHOOL %-16s runs=%d trioBoundFails=%d %s%n", c.name(), fm.size(), trioFails, failed);
            line("SCHOOL", c.name(), "pen", fm, f -> f.penetrations(), keys);
            line("SCHOOL", c.name(), "bodyOverlap%", fm, f -> 100 * f.bodyOverlapFraction(), keys);
            line("SCHOOL", c.name(), "sphereOverlap%", fm, f -> 100 * f.overlapFraction(), keys);
            line("SCHOOL", c.name(), "straggle s", fm, CompanionRun.FollowerMetrics::longestStraggleSeconds, keys);
            line("SCHOOL", c.name(), "leash", fm, f -> f.leashEvents(), keys);
            line("SCHOOL", c.name(), "maxImpact", fm, CompanionRun.FollowerMetrics::maxImpact, keys);
            line("SCHOOL", c.name(), "maxJerk", fm, CompanionRun.FollowerMetrics::maxJerk, keys);
            line("SCHOOL", c.name(), "tailFirst%", fm, f -> 100 * f.tailFirstFraction(), keys);
            shape("SCHOOL", c.name(), shapes, keys);
            perSeed("SCHOOL", c.name(), "trioFails", failFlags, keys, x -> x, null, true);
            perSeed("SCHOOL", c.name(), "maxStraggle", fm, keys, CompanionRun.FollowerMetrics::longestStraggleSeconds, null, false);
            perSeed("SCHOOL", c.name(), "maxImpact", fm, keys, CompanionRun.FollowerMetrics::maxImpact, null, false);
        }
    }

    @Test
    void ground() {
        List<Ground> configs = List.of(
                new Ground("line 40x3 (ref)", new float[]{0.4f, 0.4f, 0.4f}, BenthicGroup.Formation.LINE),
                new Ground("line 30/40/50", new float[]{0.30f, 0.40f, 0.50f}, BenthicGroup.Formation.LINE),
                new Ground("line 50/40/30", new float[]{0.50f, 0.40f, 0.30f}, BenthicGroup.Formation.LINE),
                new Ground("line pair 40x2 (ref)", new float[]{0.4f, 0.4f}, BenthicGroup.Formation.LINE),
                new Ground("starfish pair 30/45", CompanionRun.STARFISH_PAIR, BenthicGroup.Formation.LINE),
                new Ground("crowd 12x3 (ref)", new float[]{0.12f, 0.12f, 0.12f}, BenthicGroup.Formation.CROWD),
                new Ground("crowd 8/12/18", CompanionRun.SLUG_CROWD_MIXED, BenthicGroup.Formation.CROWD),
                new Ground("crowd 18/12/8", new float[]{0.18f, 0.12f, 0.08f}, BenthicGroup.Formation.CROWD),
                new Ground("crowd 30/40/50", new float[]{0.30f, 0.40f, 0.50f}, BenthicGroup.Formation.CROWD),
                new Ground("crowd 50/40/30", new float[]{0.50f, 0.40f, 0.30f}, BenthicGroup.Formation.CROWD));
        for (Ground c : configs) {
            List<GroupRun> runs = new ArrayList<>();
            List<GroupShapeMetrics.Result> shapes = new ArrayList<>();
            List<String> keys = new ArrayList<>();
            int pen = 0, slotErrors = 0;
            for (String script : GROUND_SCRIPTS) {
                for (long seed = 1; seed <= seeds(); seed++) {
                    LocalVoxels truth = new LocalVoxels();
                    int[] p = {0};
                    GroupRun r = new GroupRun(script, seed, c.lengths(), Temperament.BASELINE, c.formation(), tunables())
                            .run((tick, world, group) -> {
                                truth.update(world.blocks(), world.owner().eye());
                                for (int i = 0; i < group.count(); i++) {
                                    CompanionAgent a = group.member(i);
                                    if (!truth.sphereClear(a.position(), a.radius() * 0.95)) p[0]++;
                                }
                            });
                    pen += p[0];
                    slotErrors += r.slotErrors.size();
                    runs.add(r);
                    shapes.add(r.shape);
                    keys.add(script + "#" + seed);
                }
            }
            System.out.printf(Locale.ROOT, "GROUND %-20s runs=%d terrainPen=%d slotErrors=%d%n", c.name(), runs.size(), pen, slotErrors);
            line("GROUND", c.name(), "deepTicks", runs, r -> r.deepTicks, keys);
            line("GROUND", c.name(), "overlapTicks", runs, r -> r.overlapTicks, keys);
            line("GROUND", c.name(), "underfoot", runs, r -> r.underfootTicks, keys);
            line("GROUND", c.name(), "fades", runs, r -> r.fades, keys);
            line("GROUND", c.name(), "lineOrder%", runs, r -> 100 * r.orderShare(), keys);
            line("GROUND", c.name(), "gapCv", runs, GroupRun::gapCv, keys);
            shape("GROUND", c.name(), shapes, keys);
            perSeed("GROUND", c.name(), "fades", runs, keys, r -> r.fades, null, true);
            perSeed("GROUND", c.name(), "sprintFades", runs, keys, r -> r.script.equals("sprint_away") ? r.fades : 0, null, true);
            perSeed("GROUND", c.name(), "maxUnderfoot", runs, keys, r -> r.underfootTicks, null, false);
            perSeed("GROUND", c.name(), "maxDeep", runs, keys, r -> r.deepTicks, null, false);
            perSeed("GROUND", c.name(), "maxLineOrder%", runs, keys, r -> 100 * r.orderShare(), null, false);
        }
    }

    /**
     * One value per seed across every script: the sum ({@code sum}) or the worst. {@code byKey}, when
     * given, replaces {@code f} with a function of the run and its key.
     */
    private static <T> void perSeed(String kind, String name, String metric, List<T> xs, List<String> keys,
                                    ToDoubleFunction<T> f, java.util.function.ToDoubleBiFunction<T, String> byKey,
                                    boolean sum) {
        double[] v = new double[seeds()];
        for (int i = 0; i < xs.size(); i++) {
            String key = keys.get(i);
            int seed = Integer.parseInt(key.substring(key.indexOf('#') + 1)) - 1;
            double x = byKey != null ? byKey.applyAsDouble(xs.get(i), key) : f.applyAsDouble(xs.get(i));
            if (Double.isNaN(x)) continue;
            v[seed] = sum ? v[seed] + x : Math.max(v[seed], x);
        }
        StringBuilder b = new StringBuilder();
        for (double x : v) b.append(String.format(Locale.ROOT, " %7.2f", x));
        System.out.printf(Locale.ROOT, "  SEED %s %-20s %-15s%s%n", kind, name, metric, b);
    }

    private static void shape(String kind, String name, List<GroupShapeMetrics.Result> s, List<String> keys) {
        line(kind, name, "spacing L", s, GroupShapeMetrics.Result::meanSpacingLengths, keys);
        lineMin(kind, name, "minSpacing L", s, GroupShapeMetrics.Result::minSpacingLengths, keys);
        line(kind, name, "spacingRate", s, GroupShapeMetrics.Result::spacingRate, keys);
        line(kind, name, "ahead%", s, r -> 100 * r.aheadShare(), keys);
        line(kind, name, "overtakes/min", s, GroupShapeMetrics.Result::overtakesPerTravelMinute, keys);
        line(kind, name, "lag", s, GroupShapeMetrics.Result::meanLag, keys);
        line(kind, name, "maxLag", s, GroupShapeMetrics.Result::maxLag, keys);
        line(kind, name, "lagRate", s, GroupShapeMetrics.Result::lagRate, keys);
        line(kind, name, "contact%", s, r -> 100 * r.contactShare(), keys);
    }

    private static <T> void line(String kind, String name, String metric, List<T> xs, ToDoubleFunction<T> f, List<String> keys) {
        double sum = 0, worst = Double.NEGATIVE_INFINITY;
        int at = -1;
        for (int i = 0; i < xs.size(); i++) {
            double v = f.applyAsDouble(xs.get(i));
            if (Double.isNaN(v)) continue;
            sum += v;
            if (v > worst) { worst = v; at = i; }
        }
        System.out.printf(Locale.ROOT, "  %s %-20s %-15s mean %8.3f  worst %8.3f (%s)%n", kind, name, metric,
                sum / xs.size(), worst, at < 0 ? "-" : keys.get(at));
    }

    private static <T> void lineMin(String kind, String name, String metric, List<T> xs, ToDoubleFunction<T> f, List<String> keys) {
        double sum = 0, worst = Double.POSITIVE_INFINITY;
        int at = -1, n = 0;
        for (int i = 0; i < xs.size(); i++) {
            double v = f.applyAsDouble(xs.get(i));
            if (v < 0 || Double.isNaN(v)) continue;
            sum += v;
            n++;
            if (v < worst) { worst = v; at = i; }
        }
        System.out.printf(Locale.ROOT, "  %s %-20s %-15s mean %8.3f  worst %8.3f (%s)%n", kind, name, metric,
                n == 0 ? 0 : sum / n, worst, at < 0 ? "-" : keys.get(at));
    }

    /** {@link TrioInvariantTest}'s bounds for one run, or null when they hold. */
    static String trioBounds(String script, CompanionRun.FollowerMetrics f, int followers) {
        boolean obstacles = MixedSchoolInvariantTest.OBSTACLE_SCRIPTS.contains(script);
        CompanionTunables t = CompanionTunables.defaults();
        double maxAccel = Math.hypot(t.maxLateralAccel, t.maxForce) + 1.5;
        if (f.penetrations() != 0) return "pen";
        if (f.maxTurnDeg() > t.turnRateDegPerTick + 1e-3) return "turn";
        if (f.maxAccel() > maxAccel) return "accel";
        if (f.maxJerk() > 160) return "jerk " + f.maxJerk();
        if (f.tailFirstFraction() > 0.005) return "tailFirst";
        if (!MixedSchoolInvariantTest.TUNNEL_SCRIPTS.contains(script) && f.bodyOverlapFraction() > 0.015)
            return "bodyOverlap " + f.bodyOverlapFraction();
        if (f.longestStraggleSeconds() > (obstacles ? 15 : 5)) return "straggle " + f.longestStraggleSeconds();
        if (!obstacles && !java.util.Set.of("teleport", "elytra", "creative_flight").contains(script) && f.leashEvents() != 0)
            return "leash";
        if (obstacles && !MixedSchoolInvariantTest.TUNNEL_SCRIPTS.contains(script)) {
            double perMinute = f.backstops() / (TICKS / 20.0 / 60.0) / followers;
            if (perMinute > 8) return "contacts/min " + perMinute;
            double bound = MixedSchoolInvariantTest.IMPACT_GAPS.contains(script) ? 5.0 : 3.0;
            if (f.maxImpact() > bound) return "impact " + f.maxImpact();
        }
        return null;
    }

    @SuppressWarnings("unused")
    private static String fmt(float[] l) {
        return Arrays.toString(l);
    }
}
