package grill24.fishsim;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.VoxelDomain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.Random;

/**
 * Throwaway measurement probe (docs/fish-tank-group-scaling.md §7) — step cost vs fish count,
 * brute force against the spatial index. Not a correctness test; skipped unless {@code -PsimStdout}
 * is passed, so it never slows the normal suite.
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class StepScalingProbe {

    @Test
    void stepCostVsFishCount() {
        System.out.println("domain   fish   brute ms   grid ms   speedup   us/fish");
        measure(6, 200);
        measure(6, 400);
        measure(6, 864);
        measure(8, 1200);
        measure(8, 1728);
        measure(8, 2048);
        measure(10, 3000);
        measure(12, 4000);

        // Worst realistic density: a small group with every one of its 27 slots filled. This, not
        // the big sparse build, is what sets the fish budget — us/fish rises with local density.
        System.out.println("-- fully-stocked small groups (27 fish per tank) --");
        measure(3, 729);   // 27 tanks
        measure(4, 1024);  // budget check at the densest shape that can hold it
        measure(4, 1728);  // 64 tanks fully stocked — today's cap, unbounded
    }

    private static void measure(int cube, int fish) {
        double brute = time(cube, fish, false);
        double grid = time(cube, fish, true);
        System.out.printf("cube%-3d %5d %10.3f %9.3f %8.1fx %8.2f%n",
                cube, fish, brute, grid, brute / grid, grid * 1000.0 / fish);
    }

    private static double time(int cube, int fish, boolean indexed) {
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        if (!indexed) engine.setSpatialIndexEnabled(false);
        engine.rebuild(specs(fish), 1234L, 0f, 20f,
                new VoxelDomain(VoxelDomainTest.fullGrid(cube, cube, cube)));
        for (int i = 0; i < 200; i++) engine.step();
        int steps = indexed ? 300 : Math.max(30, 300 * 400 / Math.max(400, fish));
        long t0 = System.nanoTime();
        for (int i = 0; i < steps; i++) engine.step();
        return (System.nanoTime() - t0) / 1e6 / steps;
    }

    private static FishSpec[] specs(int n) {
        Random rng = new Random(99L);
        FishSpec[] specs = new FishSpec[n];
        for (int i = 0; i < n; i++) {
            specs[i] = new FishSpec(0.07f + rng.nextFloat() * 0.18f, Locomotion.FREE_SWIM, rng.nextBoolean(), rng.nextInt(3));
        }
        return specs;
    }
}
