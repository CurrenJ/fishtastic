package grill24.fishsim.harness;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.VoxelDomain;

/**
 * Temporary diagnostic (not shipped, not a build target used by other tools): quantifies the
 * "approach wall, turn away, turn right back" behaviour reported from in-game observation of the
 * 4x1x2 tank. Two measurements, both planar-model-only:
 *
 * <ol>
 *   <li><b>Heading autocorrelation</b> — mean cos(angle between direction(t) and direction(t+lag)))
 *   averaged over all swimmers/time, for a range of lags. A school with real directional
 *   persistence stays correlated (near +1) for seconds; a twitchy one decorrelates in well under a
 *   second.</li>
 *   <li><b>Large-turn rate, near-wall vs open-water</b> — rate of "the heading now points >90° away
 *   from where it pointed 2s ago" events, split by whether the fish was within wallMargin of a wall
 *   at the start of that 2s window. If wall-adjacent turn rate is much higher than open-water, the
 *   wall-avoidance response is specifically implicated.</li>
 * </ol>
 *
 * Run: {@code ./gradlew :fishsim:runWallTurnAnalysis}
 */
public final class WallTurnAnalysis {

    private WallTurnAnalysis() {}

    public static void main(String[] args) {
        String domainName = "4x1x2";
        int fish = 12;
        long seed = 42L;
        int ticks = 12_000; // 10 minutes of sim time
        int warmup = 400;

        Tunables tunables = Tunables.GROUP;
        FlockEngine engine = new FlockEngine(tunables);
        FishSpec[] specs = Scenarios.specs(fish, seed);
        engine.rebuild(specs, seed, 0f, 20f, new VoxelDomain(Scenarios.occupancy(domainName)));

        int n = engine.count();
        int[] lags = {5, 10, 20, 40, 80, 160, 320}; // ticks (0.25s .. 16s @ 20Hz)
        double[] corrSum = new double[lags.length];
        long[] corrCount = new long[lags.length];

        // Ring buffers of direction (dirL,dirD) and wallDist per fish, big enough for the max lag.
        int maxLag = lags[lags.length - 1];
        int bufLen = maxLag + 1;
        float[][] histDirL = new float[n][bufLen];
        float[][] histDirD = new float[n][bufLen];
        float[][] histWallDist = new float[n][bufLen];

        // Large-turn (>90 deg over a fixed 2s = 40-tick window) event counts. Fixed classification
        // threshold (not tunables.wallMargin()) so near-wall/open-water is comparable across
        // tuning sweeps that change wallMargin itself — the 4x1x2 domain's vertical extent is only
        // ~0.7 blocks, so a large wallMargin would otherwise make "near wall" cover almost
        // everything vertically and swamp the split.
        float nearWallThreshold = 0.16f;
        int turnWindow = 40;
        long nearWallTurnEvents = 0, nearWallTurnSamples = 0;
        long openWaterTurnEvents = 0, openWaterTurnSamples = 0;

        for (int tick = 0; tick < ticks; tick++) {
            engine.step();
            int slot = tick % bufLen;

            for (int i = 0; i < n; i++) {
                if (!engine.swimmers[i]) continue;
                float vl = engine.velL()[i], vd = engine.velD()[i];
                float sp = (float) Math.sqrt(vl * vl + vd * vd);
                float dirL, dirD;
                if (sp > 1e-4f) {
                    dirL = vl / sp;
                    dirD = vd / sp;
                } else {
                    dirL = histDirL[i][(tick - 1 + bufLen) % bufLen];
                    dirD = histDirD[i][(tick - 1 + bufLen) % bufLen];
                }
                float wallDist = engine.domain().wallDistance(engine.posL()[i], engine.posY()[i], engine.posD()[i]);
                histDirL[i][slot] = dirL;
                histDirD[i][slot] = dirD;
                histWallDist[i][slot] = wallDist;

                if (tick < warmup) continue;

                for (int li = 0; li < lags.length; li++) {
                    int lag = lags[li];
                    if (tick < lag) continue;
                    int pastSlot = (tick - lag) % bufLen;
                    float pdL = histDirL[i][pastSlot], pdD = histDirD[i][pastSlot];
                    double cos = dirL * pdL + dirD * pdD;
                    corrSum[li] += cos;
                    corrCount[li]++;
                }

                if (tick >= turnWindow) {
                    int pastSlot = (tick - turnWindow) % bufLen;
                    float pdL = histDirL[i][pastSlot], pdD = histDirD[i][pastSlot];
                    float pastWallDist = histWallDist[i][pastSlot];
                    double cos = dirL * pdL + dirD * pdD;
                    boolean bigTurn = cos < 0.0; // heading now points >90 deg from 2s ago
                    boolean wasNearWall = pastWallDist < nearWallThreshold;
                    if (wasNearWall) {
                        nearWallTurnSamples++;
                        if (bigTurn) nearWallTurnEvents++;
                    } else {
                        openWaterTurnSamples++;
                        if (bigTurn) openWaterTurnEvents++;
                    }
                }
            }
        }

        System.out.println("=== Heading autocorrelation (mean cos angle vs lag) ===");
        System.out.println("lag_ticks,lag_seconds,meanCos");
        for (int li = 0; li < lags.length; li++) {
            double mean = corrCount[li] == 0 ? Double.NaN : corrSum[li] / corrCount[li];
            System.out.printf("%d,%.2f,%.4f%n", lags[li], lags[li] / 20.0, mean);
        }

        System.out.println();
        System.out.println("=== Large-turn rate (>90 deg heading change over 2s window) ===");
        double nearWallRate = nearWallTurnSamples == 0 ? Double.NaN : (double) nearWallTurnEvents / nearWallTurnSamples;
        double openWaterRate = openWaterTurnSamples == 0 ? Double.NaN : (double) openWaterTurnEvents / openWaterTurnSamples;
        System.out.printf("near-wall (dist<%.2f): %d/%d = %.4f%n",
                nearWallThreshold, nearWallTurnEvents, nearWallTurnSamples, nearWallRate);
        System.out.printf("open-water: %d/%d = %.4f%n",
                openWaterTurnEvents, openWaterTurnSamples, openWaterRate);
        System.out.printf("ratio (near-wall / open-water): %.2fx%n", nearWallRate / openWaterRate);
    }
}
