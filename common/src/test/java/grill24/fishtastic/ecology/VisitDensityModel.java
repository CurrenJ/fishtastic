package grill24.fishtastic.ecology;

import grill24.fishtastic.ecology.VisitWindows.Params;

/**
 * Closed-form visit densities for the world fish exploration windows (Step 0 of
 * docs/world-fish-exploration-implementation-plan.md). Pure arithmetic over {@link Params}, a water
 * share and a tracking range; {@link VisitFieldSim} measures the same numbers over the real hashes,
 * and {@link VisitDensityReportTest} compares the two.
 *
 * <p>Model: every chunk is a site (P1) with windows of length L every T ticks at a uniform random
 * phase. A window is hosted with probability {@code h = chance * survival}, where survival is the
 * chance an active window is not suppressed. Water only gates which hosted windows can be visited,
 * so the water share multiplies every density but never the suppression.
 */
final class VisitDensityModel {

    static final double TICKS_PER_HOUR = 72_000.0;

    private VisitDensityModel() {
    }

    /** Number of other chunks within the Euclidean suppression radius. */
    static int neighbourCount(int radius) {
        int n = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if ((dx != 0 || dz != 0) && dx * dx + dz * dz <= radius * radius) n++;
            }
        }
        return n;
    }

    /**
     * Probability that an active window is not suppressed. Suppression judges overlap over the hosted
     * span {@code S = min(T, 2L)} ({@link VisitWindows#hostedSpanTicks}), so a neighbour with a random
     * phase has two windows overlapping ours with probability {@code 2S/T - 1} (when that is positive),
     * otherwise one with probability {@code 2S/T} (capped at 1); each overlapping window beats ours when it is active
     * and ranks higher. Given our rank u, that is {@code q = p (1 - u)}, independently per window, so
     * survival = integral over u of (product over neighbours of E[(1 - q)^count]).
     */
    static double survival(Params params) {
        int n = neighbourCount(params.suppressionRadiusChunks());
        if (n == 0) return 1.0;
        double x = 2.0 * VisitWindows.hostedSpanTicks(params) / params.periodTicks();
        double c0, c1, c2;
        if (x <= 1.0) {
            c0 = 1.0 - x;
            c1 = x;
            c2 = 0.0;
        } else {
            c0 = 0.0;
            c1 = 2.0 - x;
            c2 = x - 1.0;
        }
        int steps = 4000;
        double sum = 0.0;
        for (int i = 0; i < steps; i++) {
            double u = (i + 0.5) / steps;
            double notBeaten = 1.0 - params.visitChance() * (1.0 - u);
            double factor = c0 + c1 * notBeaten + c2 * notBeaten * notBeaten;
            sum += Math.pow(factor, n);
        }
        return sum / steps;
    }

    /** Fraction of active windows that suppression removes. */
    static double suppressionRate(Params params) {
        return 1.0 - survival(params);
    }

    /** Probability that a given window hosts a visit (active and not suppressed), wet or dry. */
    static double hostedPerWindow(Params params) {
        return params.visitChance() * survival(params);
    }

    /** Hosted windows starting per site per hour. */
    static double hostedStartsPerSiteHour(Params params) {
        return TICKS_PER_HOUR / params.periodTicks() * hostedPerWindow(params);
    }

    /** Fraction of time a given site has a hosted window open. */
    static double openFraction(Params params) {
        return hostedPerWindow(params) * params.visitTicks() / params.periodTicks();
    }

    /** Chunks whose visit anchor lies within the tracking range of a point (expected, area / 256). */
    static double chunksInView(double range) {
        return Math.PI * range * range / 256.0;
    }

    /** Visits starting per hour with their anchor within tracking range of a stationary player. */
    static double stationaryStartsPerHour(Params params, double waterShare, double range) {
        return chunksInView(range) * waterShare * hostedStartsPerSiteHour(params);
    }

    /** Probability that at least one hosted visit is open within range at a random instant. */
    static double stationaryAvailableFraction(Params params, double waterShare, double range) {
        return 1.0 - Math.exp(-chunksInView(range) * waterShare * openFraction(params));
    }

    /**
     * Distinct visits per hour that a player walking a straight line at {@code speed} blocks/s comes
     * within tracking range of while they are open. A visit anchored at lateral offset d is in range
     * for tau(d) = 2 sqrt(r^2 - d^2) / speed seconds; it is caught when a window overlaps that
     * interval, which has probability {@code h (L + tau) / T}. Integrating over d gives a sweep term
     * (path length x 2r x L) plus a term equal to the stationary rate. Valid while {@code L + tau <= T}.
     */
    static double travelPassesPerHour(Params params, double waterShare, double range, double speed) {
        double density = waterShare / 256.0;
        double h = hostedPerWindow(params);
        double sweep = 3600.0 * speed * 2.0 * range * params.visitTicks();
        double stationary = TICKS_PER_HOUR * Math.PI * range * range;
        return density * h / params.periodTicks() * (sweep + stationary);
    }

    /**
     * Mean wait, in ticks, from a random instant until a given site next has a visit available
     * (already open, or the start of the next hosted window). Windows are hosted independently with
     * probability h; the wait given the position u inside the current period is
     * {@code (T - u) + T (1 - h) / h} unless the current window is hosted and still open.
     */
    static double pondWaitTicks(Params params) {
        double h = hostedPerWindow(params);
        if (h <= 0) return Double.POSITIVE_INFINITY;
        double t = params.periodTicks();
        double l = params.visitTicks();
        double toNextStart = t / 2.0 + t * (1 - h) / h;
        double openCredit = h * (l - l * l / (2 * t) + l / t * t * (1 - h) / h);
        return toNextStart - openCredit;
    }

    /** Mean wait, in ticks, from a random instant to the next hosted window start at a given site. */
    static double pondWaitForNextStartTicks(Params params) {
        double h = hostedPerWindow(params);
        if (h <= 0) return Double.POSITIVE_INFINITY;
        return params.periodTicks() * (1.0 / h - 0.5);
    }

    /** The chance giving {@code targetPerHour} stationary starts, by bisection; NaN when even chance 1 falls short. */
    static double solveChance(long periodTicks, long visitTicks, int radius, double waterShare, double range,
                              double targetPerHour) {
        if (stationaryStartsPerHour(new Params(periodTicks, visitTicks, 1.0, radius), waterShare, range) < targetPerHour) {
            return Double.NaN;
        }
        double lo = 0.0, hi = 1.0;
        for (int i = 0; i < 60; i++) {
            double mid = (lo + hi) / 2;
            double rate = stationaryStartsPerHour(new Params(periodTicks, visitTicks, mid, radius), waterShare, range);
            if (rate < targetPerHour) lo = mid;
            else hi = mid;
        }
        return (lo + hi) / 2;
    }
}
