package grill24.fishtastic.gametest;

import grill24.fishtastic.data.PhaseRule;
import grill24.fishtastic.util.FishingTarget;
import grill24.fishtastic.util.PhysicsSimulation;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Game tests for FishingTarget — the deterministic, client-shareable state machine
 * behind every fishing minigame target. Pure logic: no block, world, or registry needed.
 */
public final class FishingTargetGameTests {

    private FishingTargetGameTests() {}

    private static FishingTarget newTarget(float difficulty) {
        return new FishingTarget(List.of(new ItemStack(Items.DIAMOND)),
            FishingTarget.TargetCategory.FISH, new Random(12345), 0.5f, difficulty);
    }

    /** Forces a single, explicit MovementPattern via a one-pattern PhaseRule (no random selection). */
    private static FishingTarget newTargetWithPattern(FishingTarget.MovementPattern pattern, List<ItemStack> rewards) {
        PhaseRule phase = new PhaseRule(0f, List.of(pattern),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        return new FishingTarget(rewards, FishingTarget.TargetCategory.FISH, new Random(12345),
            0.5f, 0.5f, List.of(phase));
    }

    /**
     * Repeatedly updating catch progress with full overlap quality must eventually catch the target.
     */
    public static void highOverlapEventuallyCatches(GameTestHelper helper) {
        FishingTarget target = newTarget(0.5f);
        int ticks = 0;
        while (!target.isCaught() && ticks < 1000) {
            target.updateCatchProgress(1.0f);
            ticks++;
        }
        helper.assertTrue(target.isCaught(),
            "Target must be caught after sustained full-overlap progress, catchProgress=" + target.getCatchProgress());
        helper.assertTrue(target.getCatchProgress() >= 1.0f,
            "isCaught() must correspond to catchProgress >= 1.0, got " + target.getCatchProgress());
        helper.succeed();
    }

    /**
     * Repeatedly updating catch progress with zero overlap quality must eventually fail the target.
     */
    public static void zeroOverlapEventuallyFails(GameTestHelper helper) {
        FishingTarget target = newTarget(0.5f);
        int ticks = 0;
        while (!target.hasFailed() && ticks < 1000) {
            target.updateCatchProgress(0.0f);
            ticks++;
        }
        helper.assertTrue(target.hasFailed(),
            "Target must fail after sustained zero-overlap drain, catchProgress=" + target.getCatchProgress());
        helper.assertTrue(target.getCatchProgress() <= 0.0f,
            "hasFailed() must correspond to catchProgress <= 0.0, got " + target.getCatchProgress());
        helper.succeed();
    }

    /**
     * pickRandom(difficulty, roll) is a pure function of its explicit roll parameter: roll=0
     * always lands in the lowest cumulative bucket (DRIFT) and a roll past the combined weight
     * of DRIFT+DART+OSCILLATE always lands in the FLEE remainder bucket, at any difficulty.
     */
    public static void pickRandomBoundaryRolls(GameTestHelper helper) {
        helper.assertTrue(FishingTarget.pickRandom(0f, 0f) == FishingTarget.MovementPattern.DRIFT,
            "roll=0 at difficulty=0 must resolve to DRIFT");
        helper.assertTrue(FishingTarget.pickRandom(1f, 0f) == FishingTarget.MovementPattern.DRIFT,
            "roll=0 at difficulty=1 must resolve to DRIFT");
        helper.assertTrue(FishingTarget.pickRandom(0f, 0.99f) == FishingTarget.MovementPattern.FLEE,
            "roll=0.99 at difficulty=0 must resolve to FLEE (the weight remainder bucket)");
        helper.assertTrue(FishingTarget.pickRandom(1f, 0.99f) == FishingTarget.MovementPattern.FLEE,
            "roll=0.99 at difficulty=1 must resolve to FLEE (the weight remainder bucket)");
        helper.succeed();
    }

    /**
     * Every MovementPattern can be forced via an explicit single-pattern PhaseRule. Ticking any
     * of them must not throw and must keep the target's position within the travel bounds
     * FishingTarget clamps to internally (POSITION_MIN=0.05, POSITION_MAX=0.95).
     */
    public static void allMovementPatternsTickWithoutThrowing(GameTestHelper helper) {
        for (FishingTarget.MovementPattern pattern : FishingTarget.MovementPattern.values()) {
            FishingTarget target = newTargetWithPattern(pattern, List.of(new ItemStack(Items.DIAMOND)));
            helper.assertTrue(target.getMovementPattern() == pattern,
                "Forced single-pattern phase must select " + pattern + ", got " + target.getMovementPattern());

            for (int i = 0; i < 50; i++) {
                target.tick(0.5f, 0.1f);
                helper.assertTrue(target.getPosition() >= 0.05f && target.getPosition() <= 0.95f,
                    pattern + " position must stay within [0.05, 0.95], got " + target.getPosition() + " at tick " + i);
            }
            helper.assertTrue(target.getMovementPattern() == pattern,
                "Pattern must not change within a single unlimited-duration phase, got " + target.getMovementPattern());
        }
        helper.succeed();
    }

    /**
     * startCollectionAnimation creates one PhysicsSimulation per reward item, is not immediately
     * complete, and becomes complete once the success-animation duration elapses.
     */
    public static void collectionAnimationLifecycle(GameTestHelper helper) {
        List<ItemStack> rewards = List.of(
            new ItemStack(Items.DIAMOND), new ItemStack(Items.EMERALD), new ItemStack(Items.GOLD_INGOT));
        FishingTarget target = newTargetWithPattern(FishingTarget.MovementPattern.DRIFT, rewards);

        target.startCollectionAnimation(0.5f, 0.5f);
        List<PhysicsSimulation> sims = target.getPhysicsSimulations();
        helper.assertTrue(sims.size() == rewards.size(),
            "Must create one PhysicsSimulation per reward item, expected " + rewards.size() + " got " + sims.size());
        helper.assertTrue(!target.isAnimationComplete(),
            "Animation must not be complete immediately after starting");

        for (int i = 0; i < 101; i++) {
            target.tick(0.5f, 0.1f);
        }
        helper.assertTrue(target.isAnimationComplete(),
            "Animation must be complete after exceeding the success-animation max duration");
        helper.succeed();
    }

    /**
     * startFailAnimation eventually completes once the fail-animation duration elapses.
     */
    public static void failAnimationLifecycle(GameTestHelper helper) {
        FishingTarget target = newTarget(0.5f);
        target.startFailAnimation();
        helper.assertTrue(!target.isAnimationComplete(),
            "Animation must not be complete immediately after starting");

        for (int i = 0; i < 31; i++) {
            target.tick(0.5f, 0.1f);
        }
        helper.assertTrue(target.isAnimationComplete(),
            "Fail animation must be complete after exceeding the collection-animation duration");
        helper.succeed();
    }
}
