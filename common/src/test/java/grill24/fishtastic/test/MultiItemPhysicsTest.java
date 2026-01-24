package grill24.fishtastic.test;

import grill24.fishtastic.util.FishingTarget;
import grill24.fishtastic.util.PhysicsSimulation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Random;

/**
 * Visual test for multi-item physics animation.
 * Run this to see how multiple items animate independently.
 */
public class MultiItemPhysicsTest {

    public static void main(String[] args) {
        // Create a target with 3 reward items
        List<ItemStack> rewards = List.of(
                new ItemStack(Items.DIAMOND, 5),
                new ItemStack(Items.EMERALD, 3),
                new ItemStack(Items.GOLD_INGOT, 10)
        );

        Random random = new Random();
        FishingTarget target = new FishingTarget(
                rewards,
                FishingTarget.TargetCategory.TREASURE,
                random
        );

        // Simulate catching the target
        System.out.println("Catching target...");
        while (!target.isCaught()) {
            target.updateCatchProgress(true);
            target.tick();
        }

        // Start collection animation
        System.out.println("\nStarting collection animation with " + rewards.size() + " items...");
        target.startCollectionAnimation(0.5f, 0.5f);

        // Verify physics simulations were created
        List<PhysicsSimulation> simulations = target.getPhysicsSimulations();
        System.out.println("Created " + simulations.size() + " physics simulations");

        if (simulations.size() != rewards.size()) {
            System.err.println("ERROR: Expected " + rewards.size() + " simulations but got " + simulations.size());
            return;
        }

        // Simulate animation ticks
        System.out.println("\nSimulating animation...");
        int tickCount = 0;
        int maxTicks = 120;

        while (!target.isAnimationComplete() && tickCount < maxTicks) {
            target.tick();
            tickCount++;

            // Print status every 20 ticks
            if (tickCount % 20 == 0) {
                System.out.println("Tick " + tickCount + ":");
                for (int i = 0; i < simulations.size(); i++) {
                    PhysicsSimulation sim = simulations.get(i);
                    ItemStack item = sim.getItemStack();
                    System.out.println("  Item " + i + " (" + item.getItem().getDescriptionId() + "):");
                    System.out.println("    Position: " + sim.getPosition());
                    System.out.println("    Off-screen: " + sim.isOffScreen());
                }
            }
        }

        System.out.println("\nAnimation completed after " + tickCount + " ticks");
        System.out.println("All items off-screen: " + simulations.stream().allMatch(PhysicsSimulation::isOffScreen));

        // Test interpolation
        System.out.println("\nTesting interpolation...");
        float partialTick = 0.5f;
        for (int i = 0; i < simulations.size(); i++) {
            PhysicsSimulation sim = simulations.get(i);
            System.out.println("Item " + i + " interpolated position: " + sim.getInterpolatedPosition(partialTick));
            System.out.println("Item " + i + " interpolated rotation: " + sim.getInterpolatedRotation(partialTick));
        }

        System.out.println("\n✓ Multi-item physics test completed successfully!");
    }
}
