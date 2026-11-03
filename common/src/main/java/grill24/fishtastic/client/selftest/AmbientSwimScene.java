package grill24.fishtastic.client.selftest;

import grill24.fishtastic.client.FishtasticClientConfig;
import grill24.fishtastic.client.ambient.AmbientBudget;
import grill24.fishtastic.client.ambient.AmbientChunkField;
import grill24.fishtastic.client.ambient.AmbientDiagnostics;
import grill24.fishtastic.client.ambient.AmbientFishRuntime;
import grill24.fishtastic.client.ambient.ClientAmbientFish;
import grill24.fishsim.ambient.WaterDiscovery;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static grill24.fishtastic.client.selftest.RenderSelfTest.note;
import static grill24.fishtastic.client.selftest.RenderSelfTest.param;
import static grill24.fishtastic.client.selftest.RenderSelfTest.queue;
import static grill24.fishtastic.client.selftest.RenderSelfTest.run;
import static grill24.fishtastic.client.selftest.RenderSelfTest.screenshot;
import static grill24.fishtastic.client.selftest.RenderSelfTest.server;
import static grill24.fishtastic.client.selftest.RenderSelfTest.tickProbe;
import static grill24.fishtastic.client.selftest.RenderSelfTest.waitFor;
import static grill24.fishtastic.client.selftest.RenderSelfTest.worldCommand;

/**
 * Diagnosis scene for "ambient fish don't load in fast enough while moving": an open-ocean fixture
 * the player swims through at fixed speeds, with a per-second log of where the pipeline stands
 * (discovery lead, roster statuses ahead, spawn-skip reasons, cap use) and per-spawn evidence
 * (distance, in view, faded or popped). Passes no judgement; it prints {@code NOTE ambientswim ...}.
 *
 * <p>Marker params: {@code density=50}, {@code speeds=0.11,0.28,0.5} (blocks per tick),
 * {@code leg=240} (blocks per leg), and optional budget overrides {@code radius=}, {@code
 * individuals=}, {@code domains=}, {@code cells=}, {@code columns=} for range/performance sweeps.
 *
 * <p>Each stationary settle also logs a {@code MOTION} line (docs/ambient-fish-schools-design-spec.md
 * §7): per-fish speed spread ({@code acrossCv}), within-fish variation ({@code withinCv}), the peak
 * rate of speed change ({@code rate p95/p99}, b/s²), per-species mean speeds, and clumping as the
 * share of fish whose nearest neighbour is the same species ({@code nnSame}) against the share a
 * random mix would give ({@code nnChance}). {@code settle=200} sets the stationary ticks.
 * Ambient speed is docs/ambient-fish-schools-design-spec.md §5.
 */
final class AmbientSwimScene {
    private AmbientSwimScene() {}

    // Ocean fixture: x 0..400, z -48..48, floor y 55, water y 56..63 (8 deep), surface at 63.
    private static final int X0 = 0, X1 = 400, Z0 = -48, Z1 = 48, FLOOR = 55, TOP = 63;
    private static final double EYE_Y = 61.6;
    private static final int RECOVERY_TICKS = 400, FOG_DIST = 24;
    /** Speeds above this (b/s) between two ticks are a re-placement, not a swim. */
    private static final double JUMP_SPEED = 4.0;
    private static int baselineTicks = 200;

    private static final class State {
        int phase; // 0 settle, 1 swim, 2 recover, 3 done
        int leg;
        int phaseTick;
        double x;
        int dir = 1;
        double baselineSum;
        int baselineN;
        double baseline;
        final Set<Long> seen = new HashSet<>();
        double lastTickMs, lastSubmitMs;
        long lastTicks, lastFrames, lastDisplaced;
        long settleDisplaced, swimDisplaced;
        // window accumulators (20 ticks)
        double wInView, wAlive, wTickMs, wTickPeak, wSubmitMs;
        int wN, wSpawn, wSpawnInView, wPop;
        long wDisplaced;
        double wScans, wReval, wCells;
        long lastDiscNanos, wDiscNanos, lDiscNanos;
        double lastBuildMs, wBuildMs, lBuildMs;
        long lastAlloc, wAlloc, lAlloc, lastDeferrals, lDeferrals;
        int lSeenNear, wSeenNear;
        final List<Integer> lSeenDist = new ArrayList<>();
        int wLeadMin = Integer.MAX_VALUE;
        final Map<String, Integer> wSkip = new LinkedHashMap<>();
        // leg accumulators
        double lInView;
        int lN, lSpawns, lSpawnInView, lPops, lSpawnNear;
        final List<Integer> lSpawnDist = new ArrayList<>();
        double lTickMs, lTickPeak, lSubmitMs;
        int recoverAt = -1;
        // motion accumulators (stationary settle only)
        final Map<Long, double[]> mPrev = new HashMap<>();     // x, y, z, last speed (-1 = none)
        final Map<Long, double[]> mFish = new HashMap<>();     // samples, sum, sum of squares
        final Map<Long, String> mSpeciesOf = new HashMap<>();
        final List<Double> mRates = new ArrayList<>();
        double mNnSame, mNnChance, mSameNear;
        int mNnN, mNnSamples;
    }

    static void queueAmbientSwimScene() {
        int density = Integer.parseInt(param("density", "50"));
        List<Double> speeds = new ArrayList<>();
        for (String s : param("speeds", "0.11,0.28,0.5").split(",")) speeds.add(Double.parseDouble(s.trim()));
        double leg = Double.parseDouble(param("leg", "240"));
        baselineTicks = Integer.parseInt(param("settle", "200"));
        Integer radius = intParam("radius"), individuals = intParam("individuals"), domains = intParam("domains"),
                cells = intParam("cells"), columns = intParam("columns");

        queue(1, mc -> worldCommand(mc, "forceload add " + X0 + " " + Z0 + " " + X1 + " " + Z1));
        for (int x = X0; x <= X1; x += 32) {
            final int a = x, b = Math.min(X1, x + 31);
            queue(1, mc -> server(mc, s -> {
                run(s, String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:sandstone", a, FLOOR, Z0, b, FLOOR, Z1));
                run(s, String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:water", a, FLOOR + 1, Z0, b, TOP, Z1));
                run(s, String.format(Locale.ROOT, "fillbiome %d %d %d %d %d %d minecraft:ocean", a, FLOOR - 4, Z0, b, TOP + 8, Z1));
            }));
        }
        queue(2, mc -> server(mc, s -> {
            run(s, String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", X0 - 1, FLOOR, Z0 - 1, X1 + 1, TOP + 1, Z0 - 1));
            run(s, String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", X0 - 1, FLOOR, Z1 + 1, X1 + 1, TOP + 1, Z1 + 1));
            run(s, String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", X0 - 1, FLOOR, Z0, X0 - 1, TOP + 1, Z1));
            run(s, String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", X1 + 1, FLOOR, Z0, X1 + 1, TOP + 1, Z1));
            run(s, "gamemode spectator @a");
        }));
        State st = new State();
        st.x = 40;
        queue(5, mc -> {
            FishtasticClientConfig.setTestOverride(FishtasticClientConfig.AMBIENT_FISH_ENABLED_KEY, true);
            FishtasticClientConfig.setAmbientDensityTestOverride(density);
            if (radius != null || individuals != null || domains != null || cells != null || columns != null) {
                ClientAmbientFish.budgetOverride(b -> new AmbientBudget(b.density(),
                        radius != null ? radius : b.radius(), columns != null ? columns : b.columnsPerTick(),
                        cells != null ? cells : b.cellsPerTick(), domains != null ? domains : b.domains(),
                        individuals != null ? individuals : b.individuals()));
            }
            ClientAmbientFish.productionTest(true);
            tp(mc, st.x, -90f);
        });
        waitFor(10, 1200, mc -> ClientAmbientFish.natural().stats().active >= 4, mc -> {
            AmbientBudget b = ClientAmbientFish.natural().budget();
            note("ambientswim config density=" + b.density() + " radius=" + b.radius() + " individuals=" + b.individuals()
                    + " domains=" + b.domains() + " cells=" + b.cellsPerTick() + " columns=" + b.columnsPerTick()
                    + " speeds=" + speeds + " leg=" + leg + " renderDistance=" + mc.options.renderDistance().get()
                    + " fov=" + mc.options.fov().get() + " window=" + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight());
            AmbientFishRuntime.Stats s0 = ClientAmbientFish.natural().stats();
            st.lastTickMs = s0.totalTickMillis;
            st.lastTicks = s0.ticks;
            st.lastSubmitMs = s0.totalSubmitMillis;
            st.lastFrames = s0.renderFrames;
            st.lastDisplaced = s0.retireDisplaced;
            st.lastDiscNanos = s0.discoveryNanos;
            st.lastBuildMs = s0.totalBuildMillis;
            st.lastAlloc = s0.allocBytes;
            st.lastDeferrals = s0.deferrals;
            for (var f : ClientAmbientFish.natural().diagnostics().individuals()) st.seen.add(f.id());
            tickProbe = client -> probe(client, st, speeds, leg);
        });
        waitFor(20, 40 * 60 * 20, mc -> st.phase == 3, mc -> {
            tickProbe = null;
            ClientAmbientFish.budgetOverride(null);
            ClientAmbientFish.productionTest(false);
            FishtasticClientConfig.clearTestOverrides();
            worldCommand(mc, "forceload remove all");
        });
    }

    private static Integer intParam(String key) {
        String v = param(key, null);
        return v == null ? null : Integer.parseInt(v.trim());
    }

    private static void tp(Minecraft mc, double x, float yaw) {
        double feet = EYE_Y - mc.player.getEyeHeight();
        server(mc, s -> run(s, String.format(Locale.ROOT, "tp @a %.3f %.3f 0.5 %.1f 0.0", x, feet, yaw)));
    }

    private static void probe(Minecraft mc, State st, List<Double> speeds, double leg) {
        if (st.phase == 3 || mc.player == null) return;
        AmbientFishRuntime rt = ClientAmbientFish.natural();
        AmbientFishRuntime.Stats stats = rt.stats();
        st.phaseTick++;

        // ── motion
        if (st.phase == 1) {
            double v = speeds.get(st.leg);
            st.x += v * st.dir;
            tp(mc, st.x, st.dir > 0 ? -90f : 90f);
        }

        // ── observation
        var camera = mc.gameRenderer.getMainCamera();
        var eye = camera.getPosition();
        var fwd = camera.getLookVector();
        double fx = fwd.x(), fy = fwd.y(), fz = fwd.z();
        double rx = -fz, rz = fx; // right = forward x up(0,1,0)
        double rl = Math.sqrt(rx * rx + rz * rz);
        rx /= rl; rz /= rl;
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy; // up = right x forward
        double vfov = Math.toRadians(mc.options.fov().get());
        double aspect = mc.getWindow().getWidth() / (double) Math.max(1, mc.getWindow().getHeight());
        double tanV = Math.tan(vfov / 2), tanH = tanV * aspect;

        int px = mc.player.blockPosition().getX(), pz = mc.player.blockPosition().getZ();
        int inView = 0, alive = 0;
        Set<Long> now = new HashSet<>();
        for (AmbientDiagnostics.Individual f : rt.diagnostics().individuals()) {
            now.add(f.id());
            double dx = f.x() - eye.x, dy = f.y() - eye.y, dz = f.z() - eye.z;
            double depth = dx * fx + dy * fy + dz * fz;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            boolean visible = depth > 0.1 && Math.abs(dx * rx + dz * rz) <= depth * tanH
                    && Math.abs(dx * ux + dy * uy + dz * uz) <= depth * tanV && dist <= FOG_DIST;
            if (!f.retiring()) {
                alive++;
                if (visible && f.fade() >= 0.5f) inView++;
            }
            if (!st.seen.contains(f.id()) && !f.retiring()) {
                st.wSpawn++;
                if (st.phase == 1) {
                    st.lSpawns++;
                    st.lSpawnDist.add((int) Math.round(dist));
                    if (dist < 12) st.lSpawnNear++;
                }
                if (visible) {
                    st.wSpawnInView++;
                    if (dist < 12) st.wSeenNear++;
                    if (st.phase == 1) {
                        st.lSpawnInView++;
                        st.lSeenDist.add((int) Math.round(dist));
                        if (dist < 12) st.lSeenNear++;
                    }
                    if (f.fade() >= 0.99f) {
                        st.wPop++;
                        if (st.phase == 1) st.lPops++;
                    }
                }
            }
        }
        st.seen.clear();
        st.seen.addAll(now);

        if (st.phase == 0) sampleMotion(rt, st);

        // discovery lead: farthest known water column straight ahead
        int lead = Integer.MIN_VALUE;
        for (WaterDiscovery.Column c : rt.discovery().columns()) {
            if (Math.abs(c.z() - pz) > 2 || c.water().isEmpty()) continue;
            lead = Math.max(lead, (c.x() - px) * st.dir);
        }
        st.wLeadMin = Math.min(st.wLeadMin, lead);
        String skip = rt.lastSpawnSkip();
        int sp = skip.indexOf(' ');
        st.wSkip.merge(sp > 0 ? skip.substring(0, sp) : skip, 1, Integer::sum);

        double tickMs = stats.ticks > st.lastTicks ? (stats.totalTickMillis - st.lastTickMs) / (stats.ticks - st.lastTicks) : 0;
        double submitMs = stats.renderFrames > st.lastFrames
                ? (stats.totalSubmitMillis - st.lastSubmitMs) / (stats.renderFrames - st.lastFrames) : 0;
        st.lastTickMs = stats.totalTickMillis; st.lastTicks = stats.ticks;
        st.lastSubmitMs = stats.totalSubmitMillis; st.lastFrames = stats.renderFrames;

        long displacedNow = stats.retireDisplaced - st.lastDisplaced;
        st.lastDisplaced = stats.retireDisplaced;
        st.wDisplaced += displacedNow;
        if (st.phase == 1) st.swimDisplaced += displacedNow; else st.settleDisplaced += displacedNow;
        var dc = rt.discovery().counters();
        double buildNow = stats.totalBuildMillis - st.lastBuildMs;
        st.lastBuildMs = stats.totalBuildMillis;
        long allocNow = stats.allocBytes - st.lastAlloc;
        st.lastAlloc = stats.allocBytes;
        st.wBuildMs += buildNow;
        st.wAlloc += allocNow;
        if (st.phase == 1) {
            st.lBuildMs += buildNow;
            st.lAlloc += allocNow;
            st.lDeferrals += stats.deferrals - st.lastDeferrals;
        }
        st.lastDeferrals = stats.deferrals;
        long discNow = stats.discoveryNanos - st.lastDiscNanos;
        st.lastDiscNanos = stats.discoveryNanos;
        st.wDiscNanos += discNow;
        if (st.phase == 1) st.lDiscNanos += discNow;
        st.wScans += dc.scansThisTick;
        st.wReval += dc.revalidatedThisTick;
        st.wCells += dc.cellsThisTick;
        st.wInView += inView; st.wAlive += alive; st.wTickMs += tickMs; st.wSubmitMs += submitMs;
        st.wTickPeak = Math.max(st.wTickPeak, tickMs);
        st.wN++;
        if (st.phase == 1) {
            st.lInView += inView; st.lN++;
            st.lTickMs += tickMs; st.lSubmitMs += submitMs; st.lTickPeak = Math.max(st.lTickPeak, tickMs);
        }

        if (st.wN == 20) {
            Map<AmbientFishRuntime.Status, int[]> ahead = new LinkedHashMap<>();
            for (AmbientFishRuntime.Status s : AmbientFishRuntime.Status.values()) ahead.put(s, new int[2]);
            for (AmbientFishRuntime.RosterFish r : rt.rosterSnapshot()) {
                int dx = (r.homeX() - px) * st.dir;
                ahead.get(r.status())[dx > 0 ? 0 : 1]++;
            }
            StringBuilder ros = new StringBuilder();
            ahead.forEach((k, v) -> { if (v[0] + v[1] > 0) ros.append(k.name().toLowerCase(Locale.ROOT)).append('=').append(v[0]).append('/').append(v[1]).append(' '); });
            int chunkLead = Integer.MIN_VALUE;
            for (AmbientChunkField.Scan s : rt.chunkField().all()) {
                if (Math.abs((s.chunkZ() << 4) + 8 - pz) > 16) continue;
                chunkLead = Math.max(chunkLead, (((s.chunkX() << 4) + (st.dir > 0 ? 15 : 0)) - px) * st.dir);
            }
            note(String.format(Locale.ROOT,
                    "ambientswim leg=%d phase=%s t=%d x=%.1f inView=%.1f alive=%.1f cap=%d/%d displaced=%d spawns=%d inViewSpawns=%d seenNear=%d pops=%d buildMs=%.3f allocKB=%.1f "
                            + "discLeadMin=%d disc[newCols=%.1f reval=%.1f cells=%.0f ms=%.3f lead=%d,%d]/tick chunkLead=%d known=%d domains=%d roster[ahead/behind] %sskips=%s tickMs=%.3f peak=%.3f submitMs=%.3f fps=%d",
                    st.leg, phaseName(st.phase), st.phaseTick, st.x, st.wInView / st.wN, st.wAlive / st.wN,
                    stats.active, rt.budget().individuals(), st.wDisplaced, st.wSpawn, st.wSpawnInView, st.wSeenNear, st.wPop, st.wBuildMs / st.wN, st.wAlloc / 1024.0 / st.wN,
                    st.wLeadMin == Integer.MAX_VALUE ? -1 : st.wLeadMin, st.wScans / st.wN, st.wReval / st.wN, st.wCells / st.wN, st.wDiscNanos / 1e6 / st.wN, rt.discovery().leadX(), rt.discovery().leadZ(), chunkLead, rt.discovery().columns().size(),
                    rt.domainCount(), ros, st.wSkip, st.wTickMs / st.wN, st.wTickPeak, st.wSubmitMs / st.wN, mc.getFps()));
            st.wInView = st.wAlive = st.wTickMs = st.wTickPeak = st.wSubmitMs = 0;
            st.wN = st.wSpawn = st.wSpawnInView = st.wPop = 0;
            st.wDisplaced = 0;
            st.wBuildMs = 0;
            st.wAlloc = 0;
            st.wSeenNear = 0;
            st.wScans = st.wReval = st.wCells = 0;
            st.wDiscNanos = 0;
            st.wLeadMin = Integer.MAX_VALUE;
            st.wSkip.clear();
        }

        // ── phase transitions
        switch (st.phase) {
            case 0 -> {
                st.baselineSum += inView;
                st.baselineN++;
                if (st.phaseTick >= baselineTicks) {
                    st.baseline = st.baselineSum / st.baselineN;
                    noteMotion(st);
                    note(String.format(Locale.ROOT, "ambientswim leg=%d baseline inView=%.2f (stationary, %d ticks)",
                            st.leg, st.baseline, st.baselineN));
                    if (st.leg == 0) screenshot(mc, "ambientswim", "baseline");
                    st.phase = 1;
                    st.phaseTick = 0;
                }
            }
            case 1 -> {
                double travelled = st.phaseTick * speeds.get(st.leg);
                if (st.phaseTick == (int) (leg / speeds.get(st.leg) / 2)) screenshot(mc, "ambientswim", "swim_leg" + st.leg);
                if (travelled >= leg) {
                    List<Integer> d = st.lSpawnDist;
                    d.sort(Integer::compare);
                    note(String.format(Locale.ROOT,
                            "ambientswim leg=%d SUMMARY displacedSwim=%d displacedStationarySoFar=%d speed=%.2fb/t (%.1f b/s) baselineInView=%.2f swimInView=%.2f ratio=%.2f "
                                    + "spawns=%d inViewSpawns=%d seenNear=%d seenDist[p10=%d p50=%d] deferrals=%d pops=%d spawnsUnder12=%d spawnDist[p10=%d p50=%d p90=%d] buildMs=%.3f allocKB=%.1f discMs=%.3f tickMs=%.3f peak=%.3f submitMs=%.3f",
                            st.leg, st.swimDisplaced, st.settleDisplaced, speeds.get(st.leg), speeds.get(st.leg) * 20, st.baseline, st.lInView / Math.max(1, st.lN),
                            st.baseline > 0 ? st.lInView / Math.max(1, st.lN) / st.baseline : 0,
                            st.lSpawns, st.lSpawnInView, st.lSeenNear, pct(sortedCopy(st.lSeenDist), 10),
                            pct(sortedCopy(st.lSeenDist), 50), st.lDeferrals, st.lPops, st.lSpawnNear,
                            pct(d, 10), pct(d, 50), pct(d, 90), st.lBuildMs / Math.max(1, st.lN),
                            st.lAlloc / 1024.0 / Math.max(1, st.lN), st.lDiscNanos / 1e6 / Math.max(1, st.lN), st.lTickMs / Math.max(1, st.lN), st.lTickPeak,
                            st.lSubmitMs / Math.max(1, st.lN)));
                    st.phase = 2;
                    st.phaseTick = 0;
                    st.recoverAt = -1;
                }
            }
            case 2 -> {
                if (st.recoverAt < 0 && st.baseline > 0 && inView >= 0.8 * st.baseline) st.recoverAt = st.phaseTick;
                if (st.phaseTick >= RECOVERY_TICKS) {
                    note(String.format(Locale.ROOT, "ambientswim leg=%d RECOVERY ticksTo80pctBaseline=%d",
                            st.leg, st.recoverAt));
                    long[] by = stats.allocBySection;
                    double t = Math.max(1, stats.ticks);
                    note(String.format(Locale.ROOT,
                            "ambientswim ALLOC KB/tick since start: discovery=%.1f domains=%.1f roster=%.1f reconcile=%.1f spawn=%.1f engines=%.1f rest=%.1f",
                            by[0] / 1024.0 / t, by[1] / 1024.0 / t, by[2] / 1024.0 / t, by[3] / 1024.0 / t,
                            by[4] / 1024.0 / t, by[5] / 1024.0 / t, by[6] / 1024.0 / t));
                    st.leg++;
                    if (st.leg >= speeds.size()) {
                        st.phase = 3;
                        return;
                    }
                    st.dir = -st.dir;
                    st.phase = 0;
                    st.phaseTick = 0;
                    st.baselineSum = 0;
                    st.baselineN = 0;
                    st.lInView = 0; st.lN = 0; st.lSpawns = st.lSpawnInView = st.lPops = st.lSpawnNear = 0;
                    st.lSpawnDist.clear();
                    st.lTickMs = st.lTickPeak = st.lSubmitMs = 0;
                    st.swimDisplaced = 0;
                    st.lDiscNanos = 0;
                    st.lBuildMs = 0;
                    st.lAlloc = 0;
                    st.lDeferrals = 0;
                    st.lSeenNear = 0;
                    st.lSeenDist.clear();
                    tp(mc, st.x, st.dir > 0 ? -90f : 90f);
                }
            }
            default -> { }
        }
    }

    /** One stationary tick of the §7 motion metrics: speeds from position deltas, clumping every 10 ticks. */
    private static void sampleMotion(AmbientFishRuntime rt, State st) {
        List<AmbientDiagnostics.Individual> fish = new ArrayList<>();
        for (AmbientDiagnostics.Individual f : rt.diagnostics().individuals()) {
            if (!f.retiring() && f.fade() >= 0.99f) fish.add(f);
        }
        Set<Long> live = new HashSet<>();
        for (AmbientDiagnostics.Individual f : fish) {
            live.add(f.id());
            double[] prev = st.mPrev.get(f.id());
            double v = -1;
            if (prev != null) {
                double dx = f.x() - prev[0], dy = f.y() - prev[1], dz = f.z() - prev[2];
                v = Math.sqrt(dx * dx + dy * dy + dz * dz) * 20.0;
                if (v > JUMP_SPEED) v = -1;
            }
            if (v >= 0) {
                double[] acc = st.mFish.computeIfAbsent(f.id(), k -> new double[3]);
                acc[0]++;
                acc[1] += v;
                acc[2] += v * v;
                st.mSpeciesOf.put(f.id(), f.species());
                if (prev[3] >= 0) st.mRates.add(Math.abs(v - prev[3]) * 20.0);
            }
            st.mPrev.put(f.id(), new double[] {f.x(), f.y(), f.z(), v});
        }
        st.mPrev.keySet().retainAll(live);
        if (st.phaseTick % 10 != 0 || fish.size() < 2) return;
        Map<String, Integer> counts = new HashMap<>();
        for (AmbientDiagnostics.Individual f : fish) counts.merge(f.species(), 1, Integer::sum);
        int n = fish.size();
        double chance = 0;
        for (int c : counts.values()) chance += c * (double) (c - 1) / (n * (double) (n - 1));
        int same = 0;
        double near = 0;
        for (AmbientDiagnostics.Individual a : fish) {
            double best = Double.MAX_VALUE;
            AmbientDiagnostics.Individual nn = null;
            for (AmbientDiagnostics.Individual b : fish) {
                if (a == b) continue;
                double dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
                double d2 = dx * dx + dy * dy + dz * dz;
                if (d2 < best) {
                    best = d2;
                    nn = b;
                }
                if (d2 <= 9.0 && a.species().equals(b.species())) near++;
            }
            if (nn != null && nn.species().equals(a.species())) same++;
        }
        st.mNnSame += same / (double) n;
        st.mNnChance += chance;
        st.mSameNear += near / n;
        st.mNnN += n;
        st.mNnSamples++;
    }

    /** Logs and resets the settle's motion metrics. */
    private static void noteMotion(State st) {
        List<Double> means = new ArrayList<>();
        double cvSum = 0;
        int cvN = 0;
        Map<String, double[]> bySpecies = new HashMap<>();
        for (var e : st.mFish.entrySet()) {
            double[] a = e.getValue();
            if (a[0] < 40) continue;
            double mean = a[1] / a[0];
            double var = Math.max(0, a[2] / a[0] - mean * mean);
            means.add(mean);
            if (mean > 1e-3) {
                cvSum += Math.sqrt(var) / mean;
                cvN++;
            }
            double[] sp = bySpecies.computeIfAbsent(st.mSpeciesOf.get(e.getKey()), k -> new double[2]);
            sp[0]++;
            sp[1] += mean;
        }
        means.sort(Double::compare);
        double pooled = 0;
        for (double m : means) pooled += m;
        pooled /= Math.max(1, means.size());
        double across = 0;
        for (double m : means) across += (m - pooled) * (m - pooled);
        across = means.size() > 1 && pooled > 0 ? Math.sqrt(across / means.size()) / pooled : 0;
        List<Double> rates = new ArrayList<>(st.mRates);
        rates.sort(Double::compare);
        StringBuilder species = new StringBuilder();
        bySpecies.entrySet().stream()
                .sorted((x, y) -> Double.compare(y.getValue()[1] / y.getValue()[0], x.getValue()[1] / x.getValue()[0]))
                .forEach(e -> {
                    String id = e.getKey() == null ? "?" : e.getKey().substring(e.getKey().indexOf(':') + 1);
                    species.append(String.format(Locale.ROOT, "%s:%.3f(%d) ", id, e.getValue()[1] / e.getValue()[0], (int) e.getValue()[0]));
                });
        double k = Math.max(1, st.mNnSamples);
        note(String.format(Locale.ROOT,
                "ambientswim leg=%d MOTION fish=%d meanSpeed=%.3f speed[p10=%.3f p50=%.3f p90=%.3f max=%.3f] acrossCv=%.3f withinCv=%.3f "
                        + "rate[p95=%.3f p99=%.3f max=%.3f] nnSame=%.3f nnChance=%.3f sameWithin3=%.2f meanAlive=%.1f "
                        + "groupDropped=%d heldAtCap=%d species=%s",
                st.leg, means.size(), pooled, pctD(means, 10), pctD(means, 50), pctD(means, 90),
                means.isEmpty() ? -1 : means.get(means.size() - 1), across, cvN > 0 ? cvSum / cvN : 0,
                pctD(rates, 95), pctD(rates, 99), rates.isEmpty() ? -1 : rates.get(rates.size() - 1),
                st.mNnSame / k, st.mNnChance / k, st.mSameNear / k, st.mNnN / k,
                ClientAmbientFish.natural().stats().groupMembersDropped, ClientAmbientFish.natural().stats().groupsHeldAtCap,
                species.toString().trim()));
        st.mPrev.clear();
        st.mFish.clear();
        st.mSpeciesOf.clear();
        st.mRates.clear();
        st.mNnSame = st.mNnChance = st.mSameNear = 0;
        st.mNnN = st.mNnSamples = 0;
    }

    private static double pctD(List<Double> sorted, int p) {
        if (sorted.isEmpty()) return -1;
        return sorted.get(Math.min(sorted.size() - 1, sorted.size() * p / 100));
    }

    private static String phaseName(int phase) {
        return switch (phase) { case 0 -> "settle"; case 1 -> "swim"; case 2 -> "recover"; default -> "done"; };
    }

    private static List<Integer> sortedCopy(List<Integer> values) {
        List<Integer> copy = new ArrayList<>(values);
        copy.sort(Integer::compare);
        return copy;
    }

    private static int pct(List<Integer> sorted, int p) {
        if (sorted.isEmpty()) return -1;
        return sorted.get(Math.min(sorted.size() - 1, sorted.size() * p / 100));
    }
}
