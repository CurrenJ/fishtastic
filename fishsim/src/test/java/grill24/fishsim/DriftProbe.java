package grill24.fishsim;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.VoxelDomain;

/**
 * Prints what the drift model actually does, so the constants in {@code FlockEngine}'s drift
 * section can be argued about with numbers instead of adjectives — the vertical cycle's amplitude
 * and period, how far a drifter is carried, and whether the pulse and the sink balance.
 *
 * <p>Deliberately a {@code main} and not a test, like {@link BenthicProbe}: none of this has a
 * pass condition. {@link DriftTest} holds the properties that do, and per the standing note in
 * docs/fish-swarm-realism.md nothing here should be maximized — a variance number without a
 * rate-of-change number beside it is how the burst "hop" shipped.
 *
 * <p>{@code java -cp <main>:<test> grill24.fishsim.DriftProbe [ticks]}
 */
public final class DriftProbe {

    private DriftProbe() {}

    public static void main(String[] args) {
        int ticks = args.length > 0 ? Integer.parseInt(args[0]) : 4_000;

        report("single tank (box, 0.3-block band)", boxTank(), ticks);
        report("3x2 group (voxel, 1.7-block band)", groupTank(), ticks);
    }

    private static FlockEngine boxTank() {
        FlockEngine engine = new FlockEngine(Tunables.DEFAULT);
        engine.rebuild(new FishSpec[]{
                new FishSpec(0.30f, Locomotion.DRIFT, false, 0),
                new FishSpec(0.36f, Locomotion.DRIFT, false, 0),
                new FishSpec(0.28f, Locomotion.DRIFT, false, 1),
        }, 4242L, 0f, 3, 0.35f, 0.3f, 20f);
        return engine;
    }

    private static FlockEngine groupTank() {
        boolean[][][] occupancy = new boolean[3][2][1];
        for (boolean[][] column : occupancy) {
            column[0][0] = true;
            column[1][0] = true;
        }
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        engine.rebuild(new FishSpec[]{
                new FishSpec(0.30f, Locomotion.DRIFT, false, 0),
                new FishSpec(0.36f, Locomotion.DRIFT, false, 0),
                new FishSpec(0.28f, Locomotion.DRIFT, false, 1),
        }, 4242L, 0f, 20f, new VoxelDomain(occupancy));
        return engine;
    }

    private static void report(String label, FlockEngine engine, int ticks) {
        int n = engine.count();
        float lo = engine.domain().minVertical(), hi = engine.domain().maxVertical();
        float[] startL = engine.posL().clone(), startD = engine.posD().clone();
        float[] prevY = engine.posY().clone();
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE, peakRise = 0f, peakSink = 0f;
        double sumY = 0, pathXZ = 0;
        int reversals = 0;
        boolean rising = false;

        for (int tick = 0; tick < ticks; tick++) {
            float[] beforeL = engine.posL().clone(), beforeD = engine.posD().clone();
            engine.step();
            for (int i = 0; i < n; i++) {
                float y = engine.posY()[i];
                float dy = (y - prevY[i]) / engine.tunables().dt(); // blocks/s
                if (i == 0) {
                    boolean up = dy > 0f;
                    if (up != rising) reversals++;
                    rising = up;
                }
                peakRise = Math.max(peakRise, dy);
                peakSink = Math.min(peakSink, dy);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                sumY += y;
                prevY[i] = y;
                float dl = engine.posL()[i] - beforeL[i], dd = engine.posD()[i] - beforeD[i];
                pathXZ += Math.sqrt(dl * dl + dd * dd);
            }
        }

        float seconds = ticks * engine.tunables().dt();
        float netL = 0f;
        for (int i = 0; i < n; i++) {
            float dl = engine.posL()[i] - startL[i], dd = engine.posD()[i] - startD[i];
            netL = Math.max(netL, (float) Math.sqrt(dl * dl + dd * dd));
        }
        System.out.printf("%n%s — %d drifters, %.0f s%n", label, n, seconds);
        System.out.printf("  band            %.3f .. %.3f (%.3f tall)%n", lo, hi, hi - lo);
        System.out.printf("  Y swept         %.3f .. %.3f (%.0f%% of band)%n",
                minY, maxY, 100f * (maxY - minY) / (hi - lo));
        System.out.printf("  mean Y          %+.4f (band centre %+.4f)%n", sumY / (ticks * n), (lo + hi) * 0.5f);
        System.out.printf("  vertical speed  rise %.4f / sink %.4f blocks/s%n", peakRise, peakSink);
        System.out.printf("  pulse period    %.1f s between reversals (fish 0)%n",
                reversals > 0 ? 2f * seconds / reversals : Float.NaN);
        System.out.printf("  carried         %.2f blocks net, %.2f blocks of path%n", netL, pathXZ / n);
        System.out.printf("  backstop        %d engagements%n", engine.backstopEngagements());
    }
}
