package grill24.fishtastic.ecology;

import grill24.fishtastic.ecology.VisitWindows.Params;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Step 0 of docs/world-fish-exploration-implementation-plan.md: visit density model and report.
 *
 * <p>The quick tests run with the normal suite (a closed-form vs Monte-Carlo sanity check at a high
 * chance, so it is cheap). The full report is gated on the {@code visitDensityReportDir} system
 * property and writes Markdown and CSV there:
 * <pre>./gradlew :common:test --tests '*VisitDensityReportTest*' -PvisitDensityReport=build/reports/visit-density</pre>
 */
class VisitDensityReportTest {

    /**
     * Default school tracking range in blocks: {@code FishtasticServerConfig.fishSchoolTrackingRange()}
     * reads {@code schools.trackingRange}, default 48, clamped to 16..96; {@code FishSchoolManager.syncNearbyPlayers}
     * sends a school to a player when its anchor is within that distance. Hardcoded here so the test
     * does not touch config state; keep in sync.
     */
    static final double TRACKING_RANGE = 48.0;
    static final long L_TWO_MINUTES = 2400L;
    static final double WATER_SHARE = 0.5;
    static final double TARGET_STATIONARY = 4.0;

    // ---- quick tests (normal run) ------------------------------------------------------------

    @Test
    void closedFormSurvivalMatchesTheHashedField() {
        Params p = new Params(3600L, 2400L, 0.08, 2);
        double model = VisitDensityModel.survival(p);
        double measured = 0;
        int seeds = 3;
        for (int i = 0; i < seeds; i++) {
            measured += 1.0 - new VisitFieldSim(VisitFieldSim.seedOf(i), p, 1.0).suppressedShare(40, 3);
        }
        measured /= seeds;
        assertEquals(model, measured, 0.03, "survival model=" + model + " measured=" + measured);
        assertTrue(model < 0.95 && model > 0.3, "survival model=" + model);
    }

    @Test
    void closedFormStationaryAndTravelRatesMatchTheHashedFieldAtHighDensity() {
        Params p = new Params(3600L, 2400L, 0.05, 2);
        double[] stationary = new double[4];
        double[] travelling = new double[4];
        for (int i = 0; i < 4; i++) {
            VisitFieldSim sim = new VisitFieldSim(VisitFieldSim.seedOf(100 + i), p, WATER_SHARE);
            stationary[i] = sim.stationary(8, 8, TRACKING_RANGE, 15 * 72_000L).starts().length / 15.0;
            travelling[i] = sim.visitsPassed(VisitFieldSim.straightRoute(5.0, 3600), TRACKING_RANGE);
        }
        double modelStationary = VisitDensityModel.stationaryStartsPerHour(p, WATER_SHARE, TRACKING_RANGE);
        double modelTravel = VisitDensityModel.travelPassesPerHour(p, WATER_SHARE, TRACKING_RANGE, 5.0);
        assertEquals(modelStationary, mean(stationary), modelStationary * 0.15, "stationary");
        assertEquals(modelTravel, mean(travelling), modelTravel * 0.15, "travel");
    }

    @Test
    void closedFormAnchorsTheProposalTarget() {
        double chance = VisitDensityModel.solveChance(3600L, L_TWO_MINUTES, 2, WATER_SHARE, TRACKING_RANGE, TARGET_STATIONARY);
        Params p = new Params(3600L, L_TWO_MINUTES, chance, 2);
        assertEquals(TARGET_STATIONARY, VisitDensityModel.stationaryStartsPerHour(p, WATER_SHARE, TRACKING_RANGE), 1e-6);
    }

    // ---- gated report -------------------------------------------------------------------------

    @Test
    void writeVisitDensityReport() throws IOException {
        String dir = System.getProperty("visitDensityReportDir");
        Assumptions.assumeTrue(dir != null, "set -PvisitDensityReport=<dir> to run the visit density report");
        Path out = Path.of(dir);
        Files.createDirectories(out);

        StringBuilder md = new StringBuilder();
        md.append("# Visit density report (world fish exploration, Step 0)\n\n");
        md.append(String.format(Locale.ROOT,
                "Tracking range %.0f blocks (default of `schools.trackingRange`, `FishtasticServerConfig.fishSchoolTrackingRange`; "
                        + "schools are sent to a player when the anchor is within it). Water share %.2f of chunks. L = %d ticks (2 min). "
                        + "Target %.1f visits/hour for a stationary player (P3/E15). Suppression treats every neighbour chunk as a site (P1). "
                        + "Windows carry a per-site hashed phase (`SALT_PHASE`).%n%n",
                TRACKING_RANGE, WATER_SHARE, L_TWO_MINUTES, TARGET_STATIONARY));

        // Candidates: solve the chance for the target, per (T, R).
        long[][] candidates = {{3600, 2}, {2400, 2}, {7200, 2}, {3600, 1}, {3600, 3}, {3600, 0}};
        List<Candidate> cands = new ArrayList<>();
        for (long[] c : candidates) {
            double chance = VisitDensityModel.solveChance(c[0], L_TWO_MINUTES, (int) c[1], WATER_SHARE, TRACKING_RANGE, TARGET_STATIONARY);
            cands.add(new Candidate(new Params(c[0], L_TWO_MINUTES, round(chance, 5), (int) c[1])));
        }

        md.append("## Candidates (chance solved for the stationary target)\n\n");
        md.append("| T (ticks) | T (min) | R (chunks) | chance | survival | suppressed | hosted/window | open share per site | stationary starts/h | available (>=1 open in view) | travel 2.5 b/s | travel 5 b/s | pond wait to next visit (h) | pond wait to next start (h) |\n");
        md.append("|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (Candidate c : cands) {
            Params p = c.params;
            md.append(String.format(Locale.ROOT, "| %d | %.1f | %d | %.4f | %.3f | %.1f%% | %.4f | %.2f%% | %.2f | %.0f%% | %.1f | %.1f | %.2f | %.2f |%n",
                    p.periodTicks(), p.periodTicks() / 1200.0, p.suppressionRadiusChunks(), p.visitChance(),
                    VisitDensityModel.survival(p), 100 * VisitDensityModel.suppressionRate(p),
                    VisitDensityModel.hostedPerWindow(p), 100 * VisitDensityModel.openFraction(p),
                    VisitDensityModel.stationaryStartsPerHour(p, WATER_SHARE, TRACKING_RANGE),
                    100 * VisitDensityModel.stationaryAvailableFraction(p, WATER_SHARE, TRACKING_RANGE),
                    VisitDensityModel.travelPassesPerHour(p, WATER_SHARE, TRACKING_RANGE, 2.5),
                    VisitDensityModel.travelPassesPerHour(p, WATER_SHARE, TRACKING_RANGE, 5.0),
                    VisitDensityModel.pondWaitTicks(p) / VisitDensityModel.TICKS_PER_HOUR,
                    VisitDensityModel.pondWaitForNextStartTicks(p) / VisitDensityModel.TICKS_PER_HOUR));
        }
        md.append("\nStationary, travelling and pond-wait figures depend only on the hosted starts per site per hour, which the target fixes; "
                + "T and R change the window granularity and how much suppression thins the field (the chance rises to compensate).\n\n");

        // Monte-Carlo vs closed form per candidate.
        md.append("## Closed form vs Monte-Carlo\n\n");
        md.append("Stationary: 40 hashed worlds x 100 h each, player at a fixed point. Travel: 24 worlds x 1 h; straight walk and a wandering walk (heading noise 0.1 rad/s, so the walker revisits less ground than a straight line). ");
        md.append("Suppressed share: 121x121 chunks x 6 windows x 4 worlds. Pond wait: 1500 sites x 12 random start times. Values are mean +- standard error across worlds.\n\n");
        md.append("| T | R | chance | stationary starts/h (model / MC) | available % (model / MC) | travel 2.5 b/s (model / MC) | travel 5 b/s (model / MC) | wandering 5 b/s MC | suppressed % (model / MC) | pond wait to visit h (model / MC) | pond wait to start h (model / MC) |\n");
        md.append("|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        StationaryStats recommended = null;
        for (Candidate c : cands) {
            Params p = c.params;
            StationaryStats st = measureStationary(p, 40, 100);
            double[] t25 = measureTravel(p, 2.5, false, 24);
            double[] t5 = measureTravel(p, 5.0, false, 24);
            double[] tw = measureTravel(p, 5.0, true, 24);
            double[] supp = new double[4];
            for (int i = 0; i < supp.length; i++) {
                supp[i] = 100 * new VisitFieldSim(VisitFieldSim.seedOf(300 + i), p, 1.0).suppressedShare(60, 6);
            }
            double[] waits = measureWaits(p, 1500, 12, false);
            double[] nextStarts = measureWaits(p, 1500, 12, true);
            md.append(String.format(Locale.ROOT, "| %d | %d | %.4f | %.2f / %s | %.1f / %s | %.1f / %s | %.1f / %s | %s | %.1f / %s | %.2f / %s | %.2f / %s |%n",
                    p.periodTicks(), p.suppressionRadiusChunks(), p.visitChance(),
                    VisitDensityModel.stationaryStartsPerHour(p, WATER_SHARE, TRACKING_RANGE), pm(st.rates, 2),
                    100 * VisitDensityModel.stationaryAvailableFraction(p, WATER_SHARE, TRACKING_RANGE), pm(scale(st.available, 100), 1),
                    VisitDensityModel.travelPassesPerHour(p, WATER_SHARE, TRACKING_RANGE, 2.5), pm(t25, 1),
                    VisitDensityModel.travelPassesPerHour(p, WATER_SHARE, TRACKING_RANGE, 5.0), pm(t5, 1),
                    pm(tw, 1),
                    100 * VisitDensityModel.suppressionRate(p), pm(supp, 1),
                    VisitDensityModel.pondWaitTicks(p) / VisitDensityModel.TICKS_PER_HOUR, pm(scale(waits, 1.0 / VisitDensityModel.TICKS_PER_HOUR), 2),
                    VisitDensityModel.pondWaitForNextStartTicks(p) / VisitDensityModel.TICKS_PER_HOUR, pm(scale(nextStarts, 1.0 / VisitDensityModel.TICKS_PER_HOUR), 2)));
            if (c == cands.get(0)) recommended = st;
        }
        md.append("\n");

        // Temporal clustering for the first (recommended) candidate and a Poisson reference.
        Params rec = cands.get(0).params;
        md.append("## Temporal clustering (stationary player, recommended row)\n\n");
        md.append(clusteringSection(rec, recommended));

        // Spatial correlation.
        md.append("## Spatial correlation (recommended row)\n\n");
        md.append(correlationSection(rec));
        md.append(correlationSection(new Params(rec.periodTicks(), rec.visitTicks(), rec.visitChance(), 0)));

        // Sensitivity and sweep.
        md.append("## Water share sensitivity (recommended row, closed form)\n\n");
        md.append("| water share | stationary starts/h | travel 2.5 b/s | travel 5 b/s | chance needed for 4/h |\n|---:|---:|---:|---:|---:|\n");
        for (double w : new double[]{0.1, 0.25, 0.5, 0.75, 1.0}) {
            double needed = VisitDensityModel.solveChance(rec.periodTicks(), rec.visitTicks(), rec.suppressionRadiusChunks(), w, TRACKING_RANGE, TARGET_STATIONARY);
            md.append(String.format(Locale.ROOT, "| %.2f | %.2f | %.1f | %.1f | %s |%n", w,
                    VisitDensityModel.stationaryStartsPerHour(rec, w, TRACKING_RANGE),
                    VisitDensityModel.travelPassesPerHour(rec, w, TRACKING_RANGE, 2.5),
                    VisitDensityModel.travelPassesPerHour(rec, w, TRACKING_RANGE, 5.0),
                    Double.isNaN(needed) ? "not reachable" : String.format(Locale.ROOT, "%.4f", needed)));
        }
        md.append("\nA stationary player at a single-chunk pond sees one site, about `starts per site per hour` = ")
                .append(String.format(Locale.ROOT, "%.3f (one visit every %.1f h).%n%n",
                        VisitDensityModel.hostedStartsPerSiteHour(rec), 1.0 / VisitDensityModel.hostedStartsPerSiteHour(rec)));

        Files.writeString(out.resolve("visit-density.md"), md.toString(), StandardCharsets.UTF_8);
        writeSweep(out.resolve("visit-density-sweep.csv"));
    }

    private record Candidate(Params params) {
    }

    private record StationaryStats(double[] rates, double[] available, List<long[]> startsPerWorld) {
    }

    private static StationaryStats measureStationary(Params p, int worlds, int hours) {
        double[] rates = new double[worlds];
        double[] available = new double[worlds];
        List<long[]> starts = new ArrayList<>();
        for (int i = 0; i < worlds; i++) {
            VisitFieldSim sim = new VisitFieldSim(VisitFieldSim.seedOf(1000 + i), p, WATER_SHARE);
            VisitFieldSim.Stationary s = sim.stationary(8, 8, TRACKING_RANGE, hours * 72_000L);
            rates[i] = s.starts().length / (double) hours;
            available[i] = s.availableFraction();
            starts.add(s.starts());
        }
        return new StationaryStats(rates, available, starts);
    }

    private static double[] measureTravel(Params p, double speed, boolean meander, int worlds) {
        double[] out = new double[worlds];
        for (int i = 0; i < worlds; i++) {
            VisitFieldSim sim = new VisitFieldSim(VisitFieldSim.seedOf(2000 + i), p, WATER_SHARE);
            double[][] route = meander ? VisitFieldSim.meanderingRoute(speed, 3600, 0.1, 7000 + i)
                    : VisitFieldSim.straightRoute(speed, 3600);
            out[i] = sim.visitsPassed(route, TRACKING_RANGE);
        }
        return out;
    }

    private static double[] measureWaits(Params p, int sites, int times, boolean nextStartOnly) {
        VisitFieldSim sim = new VisitFieldSim(VisitFieldSim.seedOf(3000), p, 1.0);
        Random random = new Random(42);
        double[] perSite = new double[sites];
        for (int i = 0; i < sites; i++) {
            double sum = 0;
            for (int j = 0; j < times; j++) {
                sum += sim.waitTicks(i % 50 - 25, i / 50 - 15, (long) (random.nextDouble() * 50 * p.periodTicks()), nextStartOnly);
            }
            perSite[i] = sum / times;
        }
        return perSite;
    }

    private static String clusteringSection(Params p, StationaryStats st) {
        long bin = 12_000L; // 10 minutes
        long span = 100 * 72_000L;
        int binsPerWorld = (int) (span / bin);
        double sumCounts = 0, sumSq = 0;
        long n = 0;
        List<Double> gaps = new ArrayList<>();
        double noStart30 = 0, totalTime = 0;
        long events = 0, shortGaps = 0;
        for (long[] starts : st.startsPerWorld) {
            int[] counts = new int[binsPerWorld];
            for (long s : starts) counts[(int) (s / bin)]++;
            for (int c : counts) {
                sumCounts += c;
                sumSq += (double) c * c;
                n++;
            }
            for (int i = 1; i < starts.length; i++) {
                double gap = starts[i] - starts[i - 1];
                gaps.add(gap);
                if (gap < 2400) shortGaps++;
            }
            events += starts.length;
            // Fraction of instants with no start in the next 30 min.
            long prev = 0;
            for (long s : starts) {
                noStart30 += Math.max(0, s - prev - 36_000L);
                prev = s;
            }
            noStart30 += Math.max(0, span - prev - 36_000L);
            totalTime += span;
        }
        double mean = sumCounts / n;
        double fano = (sumSq / n - mean * mean) / mean;
        double gm = gaps.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double gv = gaps.stream().mapToDouble(g -> (g - gm) * (g - gm)).average().orElse(0);
        double lambdaPerTick = events / totalTime;
        StringBuilder sb = new StringBuilder();
        sb.append("| measure | measured | Poisson reference |\n|---|---:|---:|\n");
        sb.append(String.format(Locale.ROOT, "| Fano factor of 10-minute start counts | %.3f | 1.000 |%n", fano));
        sb.append(String.format(Locale.ROOT, "| Coefficient of variation of gaps between starts | %.3f | 1.000 |%n", Math.sqrt(gv) / gm));
        sb.append(String.format(Locale.ROOT, "| Share of gaps under 2 minutes | %.2f%% | %.2f%% |%n", 100.0 * shortGaps / gaps.size(), 100 * (1 - Math.exp(-lambdaPerTick * 2400))));
        sb.append(String.format(Locale.ROOT, "| Share of time with no start in the next 30 min | %.1f%% | %.1f%% |%n", 100 * noStart30 / totalTime, 100 * Math.exp(-lambdaPerTick * 36_000)));
        sb.append(String.format(Locale.ROOT, "| Mean wait between starts (min) | %.1f | %.1f |%n%n", gm / 1200.0, 1 / lambdaPerTick / 1200.0));
        sb.append("Values near 1.0 mean visits arrive like a Poisson process; a Fano factor above 1 would mean bunching in time.\n\n");
        return sb.toString();
    }

    private static String correlationSection(Params p) {
        int half = 300;
        int worlds = 6;
        int times = 48;
        int[][] offsets = {{1, 0}, {1, 1}, {2, 0}, {2, 1}, {2, 2}, {3, 0}, {4, 0}, {6, 0}, {10, 0}};
        long[] bothHosted = new long[offsets.length];
        long[] bothActive = new long[offsets.length];
        long hostedTotal = 0, activeTotal = 0, sites = 0;
        double minActive = 1, maxActive = 0;
        int size = 2 * half + 1;
        Random random = new Random(9);
        for (int w = 0; w < worlds; w++) {
            VisitFieldSim sim = new VisitFieldSim(VisitFieldSim.seedOf(4100 + w), p, 1.0);
            for (int t = 0; t < times; t++) {
                long tick = (long) (random.nextDouble() * 20 * p.periodTicks());
                boolean[][] active = new boolean[size][size];
                boolean[][] hosted = new boolean[size][size];
                long activeNow = 0;
                for (int x = 0; x < size; x++) {
                    for (int z = 0; z < size; z++) {
                        active[x][z] = sim.activeOpen(x - half, z - half, tick);
                        if (active[x][z]) {
                            activeNow++;
                            hosted[x][z] = sim.hostedOpen(x - half, z - half, tick);
                        }
                    }
                }
                double fraction = activeNow / (double) ((long) size * size);
                minActive = Math.min(minActive, fraction);
                maxActive = Math.max(maxActive, fraction);
                for (int x = 0; x < size; x++) {
                    for (int z = 0; z < size; z++) {
                        if (active[x][z]) activeTotal++;
                        if (hosted[x][z]) hostedTotal++;
                        sites++;
                        for (int o = 0; o < offsets.length; o++) {
                            int nx = x + offsets[o][0], nz = z + offsets[o][1];
                            if (nx >= size || nz >= size) continue;
                            if (active[x][z] && active[nx][nz]) bothActive[o]++;
                            if (hosted[x][z] && hosted[nx][nz]) bothHosted[o]++;
                        }
                    }
                }
            }
        }
        double pa = activeTotal / (double) sites, ph = hostedTotal / (double) sites;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "R = %d. Sample: %d worlds x %d random instants x %dx%d chunks. P(active and open) = %.4f (expected %.4f; per-instant range %.4f..%.4f, no global pulse), P(hosted and open) = %.4f.%n%n",
                p.suppressionRadiusChunks(), worlds, times, size, size, pa, p.visitChance() * p.visitTicks() / p.periodTicks(), minActive, maxActive, ph));
        sb.append("Ratio = P(both chunks have the state) / P(state)^2; 1.00 means independent, 0 means never together.\n\n");
        sb.append("| offset (chunks) | distance | both active ratio | both hosted ratio | hosted pairs |\n|---|---:|---:|---:|---:|\n");
        long pairsPerOffset = sites;
        for (int o = 0; o < offsets.length; o++) {
            sb.append(String.format(Locale.ROOT, "| (%d,%d) | %.2f | %.2f | %.2f | %d |%n", offsets[o][0], offsets[o][1],
                    Math.hypot(offsets[o][0], offsets[o][1]),
                    bothActive[o] / (pairsPerOffset * pa * pa), bothHosted[o] / (pairsPerOffset * Math.max(ph * ph, 1e-12)), bothHosted[o]));
        }
        sb.append("\n");
        return sb.toString();
    }

    private static void writeSweep(Path file) throws IOException {
        StringBuilder csv = new StringBuilder("T_ticks,L_ticks,R_chunks,water_share,range_blocks,chance_for_4_per_hour,survival,suppressed_share,stationary_starts_per_hour,available_fraction,travel_2_5_per_hour,travel_5_per_hour,pond_wait_to_visit_hours,pond_wait_to_start_hours\n");
        for (long t : new long[]{2400, 3600, 4800, 7200, 12000}) {
            for (int r : new int[]{0, 1, 2, 3, 4}) {
                for (double w : new double[]{0.25, 0.5, 0.75}) {
                    double chance = VisitDensityModel.solveChance(t, L_TWO_MINUTES, r, w, TRACKING_RANGE, TARGET_STATIONARY);
                    if (Double.isNaN(chance)) continue; // target unreachable even at chance 1
                    Params p = new Params(t, L_TWO_MINUTES, chance, r);
                    csv.append(String.format(Locale.ROOT, "%d,%d,%d,%.2f,%.0f,%.6f,%.4f,%.4f,%.3f,%.4f,%.2f,%.2f,%.3f,%.3f%n",
                            t, L_TWO_MINUTES, r, w, TRACKING_RANGE, chance, VisitDensityModel.survival(p), VisitDensityModel.suppressionRate(p),
                            VisitDensityModel.stationaryStartsPerHour(p, w, TRACKING_RANGE),
                            VisitDensityModel.stationaryAvailableFraction(p, w, TRACKING_RANGE),
                            VisitDensityModel.travelPassesPerHour(p, w, TRACKING_RANGE, 2.5),
                            VisitDensityModel.travelPassesPerHour(p, w, TRACKING_RANGE, 5.0),
                            VisitDensityModel.pondWaitTicks(p) / VisitDensityModel.TICKS_PER_HOUR,
                            VisitDensityModel.pondWaitForNextStartTicks(p) / VisitDensityModel.TICKS_PER_HOUR));
                }
            }
        }
        Files.writeString(file, csv.toString(), StandardCharsets.UTF_8);
    }

    // ---- small helpers ------------------------------------------------------------------------

    private static double mean(double[] values) {
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    private static double stdErr(double[] values) {
        double m = mean(values);
        double ss = 0;
        for (double v : values) ss += (v - m) * (v - m);
        return Math.sqrt(ss / Math.max(1, values.length - 1) / values.length);
    }

    private static String pm(double[] values, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f +- %." + decimals + "f", mean(values), stdErr(values));
    }

    private static double[] scale(double[] values, double factor) {
        double[] out = new double[values.length];
        for (int i = 0; i < out.length; i++) out[i] = values[i] * factor;
        return out;
    }

    private static double round(double value, int decimals) {
        double f = Math.pow(10, decimals);
        return Math.round(value * f) / f;
    }
}
