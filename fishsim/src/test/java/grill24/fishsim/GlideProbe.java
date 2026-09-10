package grill24.fishsim;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.VoxelDomain;

/**
 * Prints what the glide model actually does, so {@link Tunables#GLIDE}'s numbers can be argued
 * about with measurements instead of adjectives: how fast a ray really travels, how tightly it
 * turns, how far off the sand it rides, and how much of its lean is real.
 *
 * <p>Deliberately a {@code main} and not a test, like {@link BenthicProbe} and {@link DriftProbe}:
 * none of this has a pass condition. {@link GlideTest} holds the properties that do, and per the
 * standing note in docs/fish-swarm-realism.md nothing here should be maximized.
 *
 * <p>{@code java -cp <main>:<test> grill24.fishsim.GlideProbe [ticks]}
 */
public final class GlideProbe {

    private GlideProbe() {}

    public static void main(String[] args) {
        int ticks = args.length > 0 ? Integer.parseInt(args[0]) : 4_000;

        report("single tank (box, 0.3-block band)", boxTank(), ticks);
        report("4x2x2 group (voxel)", groupTank(4, 2, 2), ticks);
        report("5x3x3 group (voxel, deep)", groupTank(5, 3, 3), ticks);
    }

    private static FishSpec[] rays() {
        return new FishSpec[]{
                new FishSpec(0.10f, Locomotion.GLIDE, false, 0),
                new FishSpec(0.12f, Locomotion.GLIDE, false, 0),
                new FishSpec(0.09f, Locomotion.GLIDE, false, 1),
        };
    }

    private static FlockEngine boxTank() {
        FlockEngine engine = new FlockEngine(Tunables.DEFAULT);
        engine.rebuild(rays(), 4242L, 0f, 3, 0.35f, 0.3f, 20f);
        return engine;
    }

    private static FlockEngine groupTank(int lateral, int high, int deep) {
        boolean[][][] occupancy = new boolean[lateral][high][deep];
        for (boolean[][] column : occupancy) {
            for (boolean[] cell : column) java.util.Arrays.fill(cell, true);
        }
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        engine.rebuild(rays(), 4242L, 0f, 20f, new VoxelDomain(occupancy));
        return engine;
    }

    private static void report(String label, FlockEngine engine, int ticks) {
        int n = engine.count();
        float floor = engine.domain().minVertical();
        float headroom = engine.domain().maxVertical() - floor;
        float[] startL = engine.posL().clone(), startD = engine.posD().clone();
        float[] previousYaw = engine.yawDeg.clone();

        double speedSum = 0, heightSum = 0, turnSum = 0, bankSum = 0;
        float worstTurn = 0f, peakBank = 0f, lowest = Float.MAX_VALUE, highest = -Float.MAX_VALUE;
        double pathLength = 0;
        float[] lastL = engine.posL().clone(), lastD = engine.posD().clone();

        for (int tick = 0; tick < ticks; tick++) {
            engine.step();
            for (int i = 0; i < n; i++) {
                speedSum += engine.speed[i];
                float height = engine.posY()[i] - floor;
                heightSum += height;
                lowest = Math.min(lowest, height);
                highest = Math.max(highest, height);

                float turn = Math.abs(wrap(engine.yawDeg[i] - previousYaw[i]));
                turnSum += turn;
                worstTurn = Math.max(worstTurn, turn);
                previousYaw[i] = engine.yawDeg[i];

                float bank = Math.abs(engine.bankFraction(i));
                bankSum += bank;
                peakBank = Math.max(peakBank, bank);

                float dl = engine.posL()[i] - lastL[i], dd = engine.posD()[i] - lastD[i];
                pathLength += Math.sqrt(dl * dl + dd * dd);
                lastL[i] = engine.posL()[i];
                lastD[i] = engine.posD()[i];
            }
        }

        double net = 0;
        for (int i = 0; i < n; i++) {
            float dl = engine.posL()[i] - startL[i], dd = engine.posD()[i] - startD[i];
            net += Math.sqrt(dl * dl + dd * dd);
        }

        int samples = ticks * n;
        double seconds = ticks / 20.0;
        System.out.printf("%n== %s — %d rays, %.0f s, headroom %.2f ==%n", label, n, seconds, headroom);
        System.out.printf("  speed        mean %.4f blocks/s (ceiling %.3f)%n",
                speedSum / samples, Tunables.GLIDE.maxSpeed());
        System.out.printf("  travel       %.2f blocks of path, %.2f net displacement, per ray%n",
                pathLength / n, net / n);
        System.out.printf("  ride height  mean %.3f (%.0f%% of headroom), range %.3f..%.3f%n",
                heightSum / samples, 100 * (heightSum / samples) / headroom, lowest, highest);
        System.out.printf("  turning      mean %.3f deg/tick, worst %.3f (cap %.2f x %.2f jitter)%n",
                turnSum / samples, worstTurn, Tunables.GLIDE.turnRateDegPerTick(),
                1 + Tunables.GLIDE.traitJitter());
        System.out.printf("  bank         mean %.3f of full lean, peak %.3f%n", bankSum / samples, peakBank);
        System.out.printf("  backstop     %d engagements%n", engine.backstopEngagements());
    }

    private static float wrap(float deg) {
        float d = deg % 360f;
        if (d > 180f) d -= 360f;
        if (d <= -180f) d += 360f;
        return d;
    }
}
