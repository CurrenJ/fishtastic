package grill24.fishsim;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.VoxelDomain;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A lone tank that gains a shelter and a fish that uses it switches its own engine from the
 * binary model to the planar one (docs/fish-shelters.md §7), and back when either half goes. The
 * switch must not move a fish: the host keeps the engine's turned frame across it precisely so
 * that {@code rebuildPreserving} can carry every fish where it is. A teleporting shoal the moment
 * a log is placed is the failure this guards.
 */
class PromotionCarryTest {

    private static FishSpec[] specs(int n, long seed) {
        Random r = new Random(seed);
        FishSpec[] specs = new FishSpec[n];
        for (int i = 0; i < n; i++) {
            specs[i] = new FishSpec(0.08f + r.nextFloat() * 0.15f, Locomotion.FREE_SWIM, r.nextBoolean(), 0);
        }
        return specs;
    }

    private static int[] identity(int n) {
        int[] carry = new int[n];
        for (int i = 0; i < n; i++) carry[i] = i;
        return carry;
    }

    @Test
    void promotionAndDemotionKeepEveryFishWhereItIs() {
        for (float yaw : new float[]{0f, 90f, 37.5f, -122f}) {
            int n = 8;
            FishSpec[] specs = specs(n, 99L);
            FlockEngine engine = new FlockEngine(Tunables.DEFAULT);
            engine.rebuild(specs, 42L, yaw, 3, 0.22f, 0.25f, 0f);
            for (int t = 0; t < 200; t++) engine.step();
            engine.interpolate(1f);
            float[] x = engine.renderX.clone(), y = engine.renderY.clone(), z = engine.renderZ.clone();

            // Promote: the same engine, the group set, a one-block voxel domain, the same frame.
            engine.setTunables(Tunables.GROUP);
            engine.rebuildPreserving(specs, identity(n), 42L, yaw, 0f,
                    new VoxelDomain(new boolean[][][]{{{true}}}));
            assertTrue(engine.planar(), "promotion did not reach the planar model");
            assertEquals(yaw, engine.frameYawDeg(), 0f);
            engine.interpolate(1f);
            for (int i = 0; i < n; i++) {
                assertEquals(x[i], engine.renderX[i], 1e-6f, yaw + "°: fish " + i + " jumped in x on promotion");
                assertEquals(y[i], engine.renderY[i], 1e-6f, yaw + "°: fish " + i + " jumped in y on promotion");
                assertEquals(z[i], engine.renderZ[i], 1e-6f, yaw + "°: fish " + i + " jumped in z on promotion");
            }

            for (int t = 0; t < 200; t++) engine.step();
            assertEquals(0, engine.backstopEngagements(), yaw + "°: the promoted tank lost containment");
            engine.interpolate(1f);
            x = engine.renderX.clone();
            z = engine.renderZ.clone();

            // Demote: back to the binary model, horizontally where they were. (The binary model
            // re-homes each fish's depth plane and vertical band as it always does on a rebuild.)
            engine.setTunables(Tunables.DEFAULT);
            engine.rebuildPreserving(specs, identity(n), 42L, yaw, 3, 0.22f, 0.25f, 0f);
            assertFalse(engine.planar(), "demotion left the planar model running");
            engine.interpolate(1f);
            for (int i = 0; i < n; i++) {
                assertEquals(x[i], engine.renderX[i], 1e-6f, yaw + "°: fish " + i + " jumped in x on demotion");
                assertEquals(z[i], engine.renderZ[i], 1e-6f, yaw + "°: fish " + i + " jumped in z on demotion");
            }
        }
    }
}
