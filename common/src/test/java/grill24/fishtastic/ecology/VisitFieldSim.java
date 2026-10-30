package grill24.fishtastic.ecology;

import grill24.fishtastic.ecology.VisitWindows.Params;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Monte-Carlo measurements over a hashed chunk field, using the real {@link VisitWindows} and
 * {@link EcologyHash}. Every chunk is a site for suppression (P1); only a separately hashed share of
 * chunks holds water and can be visited. Counterpart of {@link VisitDensityModel}.
 */
final class VisitFieldSim {

    /** Local stream deciding which chunks are water; deliberately not an ecology salt. */
    private static final long SALT_WATER_TEST = 0x7E57_0000_AA7E_0001L;

    /** A hosted visit: its window and the block position of its start cell. */
    record Visit(int chunkX, int chunkZ, long window, long start, long end, double anchorX, double anchorZ) {
    }

    final long seed;
    final Params params;
    final double waterShare;

    VisitFieldSim(long seed, Params params, double waterShare) {
        this.seed = seed;
        this.params = params;
        this.waterShare = waterShare;
    }

    static long seedOf(int index) {
        return EcologyHash.mix(0xD3A5_17E5L + index * 0x9E3779B97F4A7C15L);
    }

    boolean isWater(int chunkX, int chunkZ) {
        return EcologyHash.toUnitDouble(EcologyHash.hash(seed, chunkX, chunkZ, 0L, SALT_WATER_TEST)) < waterShare;
    }

    Visit visit(int chunkX, int chunkZ, long window) {
        long h = EcologyHash.hash(seed, chunkX, chunkZ, window, EcologyHash.SALT_START_CELL);
        double ax = chunkX * 16.0 + EcologyHash.toBoundedInt(h, 16) + 0.5;
        double az = chunkZ * 16.0 + EcologyHash.toBoundedInt(EcologyHash.mix(h), 16) + 0.5;
        long start = VisitWindows.windowStart(seed, chunkX, chunkZ, window, params);
        return new Visit(chunkX, chunkZ, window, start, start + params.visitTicks(), ax, az);
    }

    /** Hosted visits at water chunks in the box whose window overlaps {@code [fromTick, toTick)}. */
    List<Visit> hostedVisits(int minX, int maxX, int minZ, int maxZ, long fromTick, long toTick) {
        List<Visit> out = new ArrayList<>();
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                if (!isWater(cx, cz)) continue;
                long[] range = VisitWindows.windowsOverlapping(seed, cx, cz, fromTick, toTick, params);
                for (long k = range[0]; k <= range[1]; k++) {
                    if (VisitWindows.hostsVisit(seed, cx, cz, k, params)) out.add(visit(cx, cz, k));
                }
            }
        }
        return out;
    }

    // ---- stationary player -------------------------------------------------------------------

    /** Start ticks (sorted) of hosted visits anchored within {@code range} of a fixed point, plus open coverage. */
    record Stationary(long[] starts, double availableFraction) {
    }

    Stationary stationary(double px, double pz, double range, long ticks) {
        int minX = (int) Math.floor((px - range) / 16.0) - 1;
        int maxX = (int) Math.floor((px + range) / 16.0) + 1;
        int minZ = (int) Math.floor((pz - range) / 16.0) - 1;
        int maxZ = (int) Math.floor((pz + range) / 16.0) + 1;
        List<Visit> visits = hostedVisits(minX, maxX, minZ, maxZ, 0, ticks);
        List<long[]> intervals = new ArrayList<>();
        List<Long> starts = new ArrayList<>();
        double r2 = range * range;
        for (Visit v : visits) {
            double dx = v.anchorX() - px, dz = v.anchorZ() - pz;
            if (dx * dx + dz * dz > r2) continue;
            if (v.start() >= 0 && v.start() < ticks) starts.add(v.start());
            intervals.add(new long[]{Math.max(0, v.start()), Math.min(ticks, v.end())});
        }
        intervals.sort((a, b) -> Long.compare(a[0], b[0]));
        long covered = 0, curEnd = Long.MIN_VALUE, curStart = 0;
        for (long[] iv : intervals) {
            if (iv[0] > curEnd) {
                if (curEnd != Long.MIN_VALUE) covered += curEnd - curStart;
                curStart = iv[0];
                curEnd = iv[1];
            } else {
                curEnd = Math.max(curEnd, iv[1]);
            }
        }
        if (curEnd != Long.MIN_VALUE) covered += curEnd - curStart;
        long[] sorted = starts.stream().mapToLong(Long::longValue).sorted().toArray();
        return new Stationary(sorted, covered / (double) ticks);
    }

    // ---- travelling player -------------------------------------------------------------------

    /** Positions in blocks, one per second, of a straight walk along +x from the origin. */
    static double[][] straightRoute(double speed, int seconds) {
        double[][] route = new double[seconds + 1][2];
        for (int s = 0; s <= seconds; s++) route[s][0] = speed * s;
        return route;
    }

    /**
     * A wandering walk: constant speed, heading turned by a Gaussian of {@code turnSigma} radians each
     * second (persistence length about {@code 2 speed / sigma^2} blocks).
     */
    static double[][] meanderingRoute(double speed, int seconds, double turnSigma, long routeSeed) {
        Random random = new Random(routeSeed);
        double[][] route = new double[seconds + 1][2];
        double heading = random.nextDouble() * Math.PI * 2;
        for (int s = 1; s <= seconds; s++) {
            heading += random.nextGaussian() * turnSigma;
            route[s][0] = route[s - 1][0] + Math.cos(heading) * speed;
            route[s][1] = route[s - 1][1] + Math.sin(heading) * speed;
        }
        return route;
    }

    /** Distinct hosted visits whose anchor the route comes within {@code range} of while the visit is open. */
    int visitsPassed(double[][] route, double range) {
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (double[] p : route) {
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minZ = Math.min(minZ, p[1]);
            maxZ = Math.max(maxZ, p[1]);
        }
        int seconds = route.length - 1;
        List<Visit> visits = hostedVisits((int) Math.floor((minX - range) / 16.0) - 1, (int) Math.floor((maxX + range) / 16.0) + 1,
                (int) Math.floor((minZ - range) / 16.0) - 1, (int) Math.floor((maxZ + range) / 16.0) + 1,
                0, seconds * 20L);
        double r2 = range * range;
        int passed = 0;
        for (Visit v : visits) {
            int from = (int) Math.max(0, Math.floorDiv(v.start() + 19, 20L));
            int to = (int) Math.min(seconds, Math.floorDiv(v.end() - 1, 20L));
            for (int s = from; s <= to; s++) {
                double dx = v.anchorX() - route[s][0], dz = v.anchorZ() - route[s][1];
                if (dx * dx + dz * dz <= r2) {
                    passed++;
                    break;
                }
            }
        }
        return passed;
    }

    // ---- ponds, suppression and correlation --------------------------------------------------

    /** Share of active windows that suppression removes, over a square of sites and a few windows. */
    double suppressedShare(int half, int windows) {
        long active = 0, suppressed = 0;
        for (int cx = -half; cx <= half; cx++) {
            for (int cz = -half; cz <= half; cz++) {
                for (int k = 0; k < windows; k++) {
                    if (!VisitWindows.isActive(seed, cx, cz, k, params)) continue;
                    active++;
                    if (VisitWindows.isSuppressed(seed, cx, cz, k, params)) suppressed++;
                }
            }
        }
        return active == 0 ? 0.0 : suppressed / (double) active;
    }

    /**
     * Ticks from {@code fromTick} until the site has a hosted window open or starting. When
     * {@code nextStartOnly}, an already open window does not count: the wait is to the next start.
     */
    long waitTicks(int chunkX, int chunkZ, long fromTick, boolean nextStartOnly) {
        long k = VisitWindows.windowIndex(seed, chunkX, chunkZ, fromTick, params);
        if (nextStartOnly) k++;
        for (;; k++) {
            if (!VisitWindows.hostsVisit(seed, chunkX, chunkZ, k, params)) continue;
            long start = VisitWindows.windowStart(seed, chunkX, chunkZ, k, params);
            if (nextStartOnly) return start - fromTick;
            if (start + params.visitTicks() > fromTick) return Math.max(0, start - fromTick);
        }
    }

    /** Whether the site hosts an open visit at the instant (dry or wet; suppression applies). */
    boolean hostedOpen(int chunkX, int chunkZ, long tick) {
        long w = VisitWindows.windowIndex(seed, chunkX, chunkZ, tick, params);
        return VisitWindows.isOpenAt(seed, chunkX, chunkZ, tick, w, params)
                && VisitWindows.hostsVisit(seed, chunkX, chunkZ, w, params);
    }

    boolean activeOpen(int chunkX, int chunkZ, long tick) {
        long w = VisitWindows.windowIndex(seed, chunkX, chunkZ, tick, params);
        return VisitWindows.isOpenAt(seed, chunkX, chunkZ, tick, w, params)
                && VisitWindows.isActive(seed, chunkX, chunkZ, w, params);
    }
}
