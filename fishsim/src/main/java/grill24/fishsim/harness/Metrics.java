package grill24.fishsim.harness;

import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.FlockDomain;

/**
 * Motion-quality measurements over a simulation run — the shared vocabulary between the invariant
 * tests (docs/fish-sim-engine-plan.md §3.1) and the tunables-sweep harness. Call {@link #sample()}
 * once after every {@code engine.step()}; read the accumulated numbers at the end.
 *
 * <p>Everything here observes the engine from outside (positions, velocities, heading flags) — it
 * never reaches into private state, so it measures exactly what a renderer would see.
 */
public final class Metrics {

    private final FlockEngine engine;
    private final Tunables tunables;
    /** Ticks to skip before shape metrics (neighbor distances) start accumulating. */
    private final int warmupTicks;

    private int ticks;

    // Previous-tick state for finite differences.
    private float[] prevVelL = new float[0], prevVelY = new float[0], prevVelD = new float[0];
    private float[] prevAccL = new float[0], prevAccY = new float[0], prevAccD = new float[0];
    private float[] prevHeading = new float[0];
    private boolean havePrev;

    // ── Invariant 1: containment ────────────────────────────────────────────
    private long wallPenetrations;

    // ── Invariant 2: dynamics bounds ────────────────────────────────────────
    private float maxObservedSpeed;
    private float maxObservedAccel; // |dv|/dt
    private float maxObservedJerk;  // |da|/dt

    // ── Invariant 3: heading flips ──────────────────────────────────────────
    private long[] flipCounts = new long[0];
    private long deadzoneViolations;

    // ── Invariant 4: near-wall vertical calm ────────────────────────────────
    private long nearWallSamples;
    private double nearWallVelYSum, nearWallVelYSumSq;

    // ── Invariant 6: shoal shape (after warmup) ─────────────────────────────
    private long nnSamples;
    private double nnDistSum;
    private float minPairwiseDist = Float.MAX_VALUE;

    // ── Realism measures (docs/fish-swarm-realism.md §3, after warmup) ──────
    // These are what turn "does the swarm look more realistic" into a number. Unlike the
    // invariants above they have no pass/fail bound — they are read as a table while tuning, and
    // compared against the ranges real schools occupy.
    /** Per-tick polarization Φ = |mean unit velocity|, accumulated over ticks. */
    private long polSamples;
    private double polSum;
    /** Per-tick milling index M = |mean of (r̂ × v̂)_y| about the shoal centroid. */
    private double millSum;
    /** Per-fish speed moments, for the coefficient of variation. */
    private long speedSamples;
    private double speedSum, speedSumSq;
    /**
     * Nearest-neighbour bearing in the observing fish's own frame, in 8 sectors of 45° starting
     * dead ahead (sector 0 = ±22.5° of the heading, sectors increasing counter-clockwise). A real
     * school shows a pronounced front/side preference; an isotropic model gives a flat ring, which
     * is what this histogram is here to expose.
     */
    private final long[] nnBearingSectors = new long[8];
    private long nnBearingSamples;

    public Metrics(FlockEngine engine, Tunables tunables, int warmupTicks) {
        this.engine = engine;
        this.tunables = tunables;
        this.warmupTicks = warmupTicks;
    }

    /** Accumulates one tick's worth of measurements. Call once after every {@code engine.step()}. */
    public void sample() {
        ticks++;
        int n = engine.count();
        FlockDomain domain = engine.domain();
        float dt = tunables.dt();

        if (prevVelL.length != n) {
            prevVelL = new float[n]; prevVelY = new float[n]; prevVelD = new float[n];
            prevAccL = new float[n]; prevAccY = new float[n]; prevAccD = new float[n];
            prevHeading = new float[n];
            flipCounts = new long[n];
            havePrev = false;
        }

        float[] posL = engine.posL(), posY = engine.posY(), posD = engine.posD();
        float[] velL = engine.velL(), velY = engine.velY(), velD = engine.velD();

        for (int i = 0; i < n; i++) {
            if (!engine.swimmers[i]) continue;

            // Invariant 1 — containment: never outside the swimmable interior (for a voxel domain
            // this includes the unoccupied cells of the bounding box, e.g. an L-bend's corner).
            if (!domain.contains(posL[i], posY[i], posD[i])) {
                wallPenetrations++;
            }

            // Invariant 2 — dynamics bounds via finite differences.
            float sp = (float) Math.sqrt(velL[i] * velL[i] + velY[i] * velY[i] + velD[i] * velD[i]);
            if (sp > maxObservedSpeed) maxObservedSpeed = sp;
            if (havePrev) {
                float aL = (velL[i] - prevVelL[i]) / dt;
                float aY = (velY[i] - prevVelY[i]) / dt;
                float aD = (velD[i] - prevVelD[i]) / dt;
                float acc = (float) Math.sqrt(aL * aL + aY * aY + aD * aD);
                if (acc > maxObservedAccel) maxObservedAccel = acc;
                if (ticks > 2) {
                    float jL = (aL - prevAccL[i]) / dt;
                    float jY = (aY - prevAccY[i]) / dt;
                    float jD = (aD - prevAccD[i]) / dt;
                    float jerk = (float) Math.sqrt(jL * jL + jY * jY + jD * jD);
                    if (jerk > maxObservedJerk) maxObservedJerk = jerk;
                }
                prevAccL[i] = aL; prevAccY[i] = aY; prevAccD[i] = aD;

                // Invariant 3 — flips and hysteresis (binary-heading model only: the planar model
                // has continuous turn-rate-capped yaw, so lateral sign changes are ordinary turns).
                if (!engine.planar() && engine.heading[i] != prevHeading[i]) {
                    flipCounts[i]++;
                    if (Math.abs(velL[i]) <= tunables.headingDeadzone()) deadzoneViolations++;
                }
            }

            // Invariant 4 — vertical velocity calm near side walls (the wall-jitter regression
            // showed up as vertical shimmer while cruising along the glass).
            float wallDist = domain.wallDistance(posL[i], posY[i], posD[i]);
            if (wallDist < tunables.wallMargin()) {
                nearWallSamples++;
                nearWallVelYSum += velY[i];
                nearWallVelYSumSq += (double) velY[i] * velY[i];
            }

            // Invariant 6 — shoal shape, after warmup.
            if (ticks > warmupTicks) {
                float nn2 = Float.MAX_VALUE;
                int nnJ = -1;
                for (int j = 0; j < n; j++) {
                    if (j == i || !engine.swimmers[j]) continue;
                    float dx = posL[i] - posL[j], dy = posY[i] - posY[j], dz = posD[i] - posD[j];
                    float d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 < nn2) { nn2 = d2; nnJ = j; }
                }
                if (nn2 != Float.MAX_VALUE) {
                    float d = (float) Math.sqrt(nn2);
                    nnSamples++;
                    nnDistSum += d;
                    if (d < minPairwiseDist) minPairwiseDist = d;
                }

                speedSamples++;
                speedSum += sp;
                speedSumSq += (double) sp * sp;

                // Nearest-neighbour bearing, measured in the observer's own horizontal frame —
                // which is why it needs the observer to actually be moving.
                float hsp = (float) Math.sqrt(velL[i] * velL[i] + velD[i] * velD[i]);
                if (nnJ >= 0 && hsp > 1e-4f) {
                    float fwdL = velL[i] / hsp, fwdD = velD[i] / hsp;
                    float relL = posL[nnJ] - posL[i], relD = posD[nnJ] - posD[i];
                    // Rotate the neighbour offset into the observer's frame, then bin by angle.
                    float ahead = relL * fwdL + relD * fwdD;
                    float abeam = relL * (-fwdD) + relD * fwdL;
                    double angle = Math.atan2(abeam, ahead); // 0 = dead ahead
                    int sector = (int) Math.floor((angle + Math.PI / 8 + 2 * Math.PI) / (Math.PI / 4)) % 8;
                    nnBearingSectors[sector]++;
                    nnBearingSamples++;
                }
            }

            prevVelL[i] = velL[i]; prevVelY[i] = velY[i]; prevVelD[i] = velD[i];
            prevHeading[i] = engine.heading[i];
        }
        if (ticks > warmupTicks) sampleGroupShape(n, posL, posD, velL, velY, velD);
        havePrev = true;
    }

    /**
     * The two whole-school order parameters, which are per-tick group quantities rather than
     * per-fish ones and so cannot ride along in the loop above.
     *
     * <p>Polarization Φ is the classic flocking order parameter: 1 when every fish points the same
     * way, ~1/√n for independent headings. Milling M is its rotational counterpart — the mean
     * signed tangential component about the shoal centroid — which is high exactly in the torus
     * formation a polarization-only reading would score as disordered.
     */
    private void sampleGroupShape(int n, float[] posL, float[] posD,
                                  float[] velL, float[] velY, float[] velD) {
        float sumL = 0f, sumY = 0f, sumD = 0f;   // mean unit velocity
        float cenL = 0f, cenD = 0f;              // shoal centroid (horizontal)
        int swimmers = 0;
        for (int i = 0; i < n; i++) {
            if (!engine.swimmers[i]) continue;
            swimmers++;
            cenL += posL[i]; cenD += posD[i];
            float s = (float) Math.sqrt(velL[i] * velL[i] + velY[i] * velY[i] + velD[i] * velD[i]);
            if (s > 1e-5f) { sumL += velL[i] / s; sumY += velY[i] / s; sumD += velD[i] / s; }
        }
        if (swimmers < 2) return;
        cenL /= swimmers; cenD /= swimmers;
        polSum += Math.sqrt((double) sumL * sumL + (double) sumY * sumY + (double) sumD * sumD) / swimmers;

        double mill = 0;
        int milled = 0;
        for (int i = 0; i < n; i++) {
            if (!engine.swimmers[i]) continue;
            float rL = posL[i] - cenL, rD = posD[i] - cenD;
            float r = (float) Math.sqrt(rL * rL + rD * rD);
            float hsp = (float) Math.sqrt(velL[i] * velL[i] + velD[i] * velD[i]);
            if (r < 1e-4f || hsp < 1e-5f) continue;
            mill += ((rL / r) * (velD[i] / hsp) - (rD / r) * (velL[i] / hsp));
            milled++;
        }
        if (milled > 0) millSum += Math.abs(mill / milled);
        polSamples++;
    }

    public int ticks() { return ticks; }

    public long wallPenetrations() { return wallPenetrations; }

    /** The engine's own count of hard-backstop engagements (soft containment must keep this 0). */
    public long hardClampContacts() { return engine.backstopEngagements(); }

    public float maxObservedSpeed() { return maxObservedSpeed; }

    public float maxObservedAccel() { return maxObservedAccel; }

    public float maxObservedJerk() { return maxObservedJerk; }

    /** Highest per-fish flip rate over the run, in flips per 10 seconds of sim time. */
    public double maxFlipRatePer10s(){
        double seconds = ticks * (double) tunables.dt();
        if (seconds <= 0) return 0;
        long max = 0;
        for (long c : flipCounts) max = Math.max(max, c);
        return max * 10.0 / seconds;
    }

    public long deadzoneViolations() { return deadzoneViolations; }

    /** Variance of vertical velocity while within the wall-avoidance margin of a side wall. */
    public double nearWallVelYVariance() {
        if (nearWallSamples < 2) return 0;
        double mean = nearWallVelYSum / nearWallSamples;
        return nearWallVelYSumSq / nearWallSamples - mean * mean;
    }

    public long nearWallSamples() { return nearWallSamples; }

    /** Mean nearest-neighbour distance among swimmers, post-warmup. */
    public double meanNearestNeighborDist() {
        return nnSamples == 0 ? 0 : nnDistSum / nnSamples;
    }

    /** Smallest pairwise distance ever observed post-warmup ({@code MAX_VALUE} if <2 swimmers). */
    public float minPairwiseDist() { return minPairwiseDist; }

    /** Mean polarization Φ over the post-warmup run: 1 = every fish points the same way. */
    public double polarization() {
        return polSamples == 0 ? 0 : polSum / polSamples;
    }

    /** Mean milling index over the post-warmup run: high in a torus, near 0 when polarized. */
    public double millingIndex() {
        return polSamples == 0 ? 0 : millSum / polSamples;
    }

    /** Coefficient of variation of swimmer speed — burst-and-coast shows up here and nowhere else. */
    public double speedCv() {
        if (speedSamples < 2) return 0;
        double mean = speedSum / speedSamples;
        if (mean <= 0) return 0;
        double var = speedSumSq / speedSamples - mean * mean;
        return var <= 0 ? 0 : Math.sqrt(var) / mean;
    }

    /** Nearest-neighbour bearing histogram as fractions, 8 sectors of 45° from dead ahead. */
    public double[] nnBearingDistribution() {
        double[] out = new double[nnBearingSectors.length];
        if (nnBearingSamples == 0) return out;
        for (int i = 0; i < out.length; i++) out[i] = nnBearingSectors[i] / (double) nnBearingSamples;
        return out;
    }

    /**
     * Fraction of nearest neighbours sitting in the forward three sectors (±67.5° of the
     * heading) — the single headline number from the bearing histogram, and the one that
     * separates a school with real structure from an isotropic cloud.
     */
    public double nnBearingFrontFraction() {
        if (nnBearingSamples == 0) return 0;
        return (nnBearingSectors[0] + nnBearingSectors[1] + nnBearingSectors[7])
                / (double) nnBearingSamples;
    }

    /** One CSV row of every headline number (see {@link #csvHeader()}), for the sweep harness. */
    public String csvRow() {
        return ticks + "," + wallPenetrations + "," + hardClampContacts() + ","
                + maxObservedSpeed + "," + maxObservedAccel + "," + maxObservedJerk + ","
                + maxFlipRatePer10s() + "," + deadzoneViolations + ","
                + nearWallVelYVariance() + "," + nearWallSamples + ","
                + meanNearestNeighborDist() + "," + (nnSamples == 0 ? "" : minPairwiseDist) + ","
                + polarization() + "," + millingIndex() + "," + speedCv() + ","
                + nnBearingFrontFraction();
    }

    public static String csvHeader() {
        return "ticks,wallPenetrations,hardClampContacts,maxSpeed,maxAccel,maxJerk,"
                + "maxFlipRatePer10s,deadzoneViolations,nearWallVelYVariance,nearWallSamples,"
                + "meanNNDist,minPairwiseDist,"
                + "polarization,millingIndex,speedCv,nnFrontFraction";
    }
}
