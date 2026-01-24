package grill24.fishtastic.util;

import grill24.fishtastic.FishtasticItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Represents a target item in the fishing minigame that moves along the bar.
 * Each target has its own position, movement behavior, and associated ItemStack.
 */
public class FishingTarget {
    /**
     * Represents the current state of the target in its lifecycle
     */
    public enum TargetState {
        ACTIVE,                 // Target is actively in play
        ANIMATING_FAIL,   // Target was caught and is playing collection animation
        ANIMATING_SUCCESS,         // Target failed and is playing fail animation
        COMPLETE                // Animation finished, ready to be removed
    }

    /**
     * Category of target for determining display icon
     */
    public enum TargetCategory {
        FISH,       // Shows generic fish icon
        TREASURE    // Shows chest icon
    }

    // Movement constants
    private static final float MIN_MOVE_INTERVAL_TICKS = 20f; // 1 second
    private static final float MAX_MOVE_INTERVAL_TICKS = 120f; // 6 seconds
    private static final float MIN_SPEED = 0.002f; // Minimum interpolation speed per tick
    private static final float MAX_SPEED = 0.02f; // Maximum interpolation speed per tick

    // Position state
    private float currentPosition; // Current position on the bar (0-1)
    private float targetPosition; // Target position we're moving towards (0-1)
    private float previousPosition; // Position from previous tick for interpolation
    private float speed; // Current movement speed

    // Timing state
    private int ticksSinceLastMove;
    private int ticksUntilNextMove;

    // Associated data
    private final List<ItemStack> rewardItems;
    private final TargetCategory category;
    private final Random random;

    // Catch progress for this specific target
    private float catchProgress;
    private static final float CATCH_PROGRESS_GAIN = 0.01f;
    private static final float CATCH_PROGRESS_LOSS = 0.005f;
    private static final float INITIAL_CATCH_PROGRESS = 0.5f;

    // Shaking effect for active capture
    private static final float SHAKE_INTENSITY = 0.002f; // Maximum shake offset
    private int shakeTick; // Counter for shake animation

    // Animation state
    private TargetState state;
    private int animationTick; // Tracks animation progress
    private int previousAnimationTick; // For interpolation

    // Collection animation constants
    private static final int COLLECTION_ANIMATION_DURATION = 30; // 1.5 seconds at 20 TPS

    // Success animation constants
    private static final int SUCCESS_ANIMATION_MAX_DURATION = 100; // Max duration before forced completion

    // Physics simulations for success animation (one per reward item)
    private List<PhysicsSimulation> physicsSimulations;

    /**
     * Creates a new fishing target with a random initial position
     * @param rewardItems The ItemStacks to award when this target is caught
     * @param category The category (FISH or TREASURE) for display purposes
     * @param random Random instance for generating movement patterns
     */
    public FishingTarget(List<ItemStack> rewardItems, TargetCategory category, Random random) {
        this.rewardItems = new ArrayList<>(rewardItems);
        this.category = category;
        this.random = random;
        this.currentPosition = random.nextFloat();
        this.targetPosition = currentPosition;
        this.previousPosition = currentPosition;
        this.speed = 0;
        this.ticksSinceLastMove = 0;
        this.ticksUntilNextMove = getRandomMoveInterval();
        this.catchProgress = INITIAL_CATCH_PROGRESS;
        this.shakeTick = 0;
        this.state = TargetState.ACTIVE;
        this.animationTick = 0;
        this.previousAnimationTick = 0;
        this.physicsSimulations = new ArrayList<>();
    }

    /**
     * Creates a new fishing target with a specific initial position
     * @param rewardItems The ItemStacks to award when this target is caught
     * @param category The category (FISH or TREASURE) for display purposes
     * @param random Random instance for generating movement patterns
     * @param initialPosition Initial position (0-1)
     */
    public FishingTarget(List<ItemStack> rewardItems, TargetCategory category, Random random, float initialPosition) {
        this.rewardItems = new ArrayList<>(rewardItems);
        this.category = category;
        this.random = random;
        this.currentPosition = Math.max(0, Math.min(1, initialPosition));
        this.targetPosition = currentPosition;
        this.previousPosition = currentPosition;
        this.speed = 0;
        this.ticksSinceLastMove = 0;
        this.ticksUntilNextMove = getRandomMoveInterval();
        this.catchProgress = INITIAL_CATCH_PROGRESS;
        this.shakeTick = 0;
        this.state = TargetState.ACTIVE;
        this.animationTick = 0;
        this.previousAnimationTick = 0;
        this.physicsSimulations = new ArrayList<>();
    }

    /**
     * Updates the target's position for one tick
     */
    public void tick() {
        previousPosition = currentPosition;
        previousAnimationTick = animationTick;

        switch (state) {
            case ACTIVE:
                // Normal active target behavior
                ticksSinceLastMove++;
                // Note: shakeTick is now incremented only when being captured, see updateCatchProgress

                // Check if it's time to pick a new target position
                if (ticksSinceLastMove >= ticksUntilNextMove) {
                    pickNewTargetPosition();
                    ticksSinceLastMove = 0;
                    ticksUntilNextMove = getRandomMoveInterval();
                }

                // Move towards target position
                if (currentPosition != targetPosition) {
                    float distance = targetPosition - currentPosition;
                    float movement = Math.signum(distance) * Math.min(Math.abs(distance), speed);
                    currentPosition += movement;

                    // Clamp to valid range
                    currentPosition = Math.max(0, Math.min(1, currentPosition));

                    // Snap to target if very close
                    if (Math.abs(currentPosition - targetPosition) < 0.001f) {
                        currentPosition = targetPosition;
                    }
                }
                break;

            case ANIMATING_FAIL:
                // Collection animation: spin and shrink
                animationTick++;
                if (animationTick >= COLLECTION_ANIMATION_DURATION) {
                    state = TargetState.COMPLETE;
                }
                break;

            case ANIMATING_SUCCESS:
                // Success animation: physics simulation for each item
                animationTick++;

                // Update all physics simulations
                for (PhysicsSimulation simulation : physicsSimulations) {
                    simulation.tick();
                }

                // Check if all items are off-screen or max duration reached
                boolean allOffScreen = physicsSimulations.stream().allMatch(PhysicsSimulation::isOffScreen);
                if (allOffScreen || animationTick >= SUCCESS_ANIMATION_MAX_DURATION) {
                    state = TargetState.COMPLETE;
                }
                break;

            case COMPLETE:
                // Do nothing, waiting to be removed
                break;
        }
    }

    /**
     * Picks a new random target position and speed
     */
    private void pickNewTargetPosition() {
        targetPosition = random.nextFloat();
        speed = MIN_SPEED + random.nextFloat() * (MAX_SPEED - MIN_SPEED);
    }

    /**
     * Gets a random interval between movements (in ticks)
     */
    private int getRandomMoveInterval() {
        return (int) (MIN_MOVE_INTERVAL_TICKS + random.nextFloat() * (MAX_MOVE_INTERVAL_TICKS - MIN_MOVE_INTERVAL_TICKS));
    }

    /**
     * Updates catch progress for this target
     * @param isInBobber Whether this target is currently inside the bobber
     */
    public void updateCatchProgress(boolean isInBobber) {
        if (isInBobber) {
            catchProgress += CATCH_PROGRESS_GAIN;
            catchProgress = Math.min(1.0f, catchProgress);
            shakeTick++; // Increment shake animation only when being captured
        } else {
            catchProgress -= CATCH_PROGRESS_LOSS;
            catchProgress = Math.max(0.0f, catchProgress);
            shakeTick = 0; // Reset shake animation when not being captured
        }
    }

    /**
     * Gets the current position
     * @return Position value between 0 and 1
     */
    public float getPosition() {
        return currentPosition;
    }

    /**
     * Gets the interpolated position for smooth rendering
     * @param partialTick Progress between current and next tick (0-1)
     * @return Interpolated position value between 0 and 1
     */
    public float getInterpolatedPosition(float partialTick) {
        return previousPosition + (currentPosition - previousPosition) * partialTick;
    }

    /**
     * Gets all reward items associated with this target
     * @return List of ItemStacks to award when caught
     */
    public List<ItemStack> getAllRewardItems() {
        return new ArrayList<>(rewardItems);
    }

    /**
     * Gets the physics simulations for rendering success animation
     * @return List of PhysicsSimulation instances
     */
    public List<PhysicsSimulation> getPhysicsSimulations() {
        return physicsSimulations;
    }

    /**
     * Gets the catch progress for this target
     * @return Progress value between 0 (no progress) and 1 (caught)
     */
    public float getCatchProgress() {
        return catchProgress;
    }

    /**
     * Checks if this target has been caught
     * @return true if catch progress is at maximum
     */
    public boolean isCaught() {
        return catchProgress >= 1.0f;
    }

    /**
     * Checks if this target capture has failed
     * @return true if catch progress is at minimum
     */
    public boolean hasFailed() {
        return catchProgress <= 0.0f;
    }

    /**
     * Gets the shake offset for rendering when being actively captured
     * @param partialTick Progress between current and next tick (0-1)
     * @return Shake offset value for Y position
     */
    public float getShakeOffset(float partialTick) {
        // Calculate shake based on tick counter and partial tick
        float totalTick = shakeTick + partialTick;
        // Use sine wave for smooth shaking motion, frequency increases with progress
        return (float) Math.sin(totalTick * 2) * SHAKE_INTENSITY;
    }

    /**
     * Gets the shake angle for rotation when being actively captured
     * @param partialTick Progress between current and next tick (0-1)
     * @param baseFrequency Base oscillation frequency
     * @param frequencyMultiplier Multiplier applied to catch progress for dynamic frequency
     * @param amplitude Maximum rotation amplitude in degrees
     * @return Shake angle in degrees
     */
    public float getShakeAngle(float partialTick, float baseFrequency, float frequencyMultiplier, float amplitude) {
        float totalTick = shakeTick + partialTick;
        float frequency = baseFrequency + catchProgress * frequencyMultiplier;
        return (float) (Math.sin(totalTick * frequency * (Math.PI * 2)) * amplitude);
    }

    /**
     * Resets the target to a new random position with initial progress
     */
    public void reset() {
        this.currentPosition = random.nextFloat();
        this.targetPosition = currentPosition;
        this.previousPosition = currentPosition;
        this.speed = 0;
        this.ticksSinceLastMove = 0;
        this.ticksUntilNextMove = getRandomMoveInterval();
        this.catchProgress = INITIAL_CATCH_PROGRESS;
        this.shakeTick = 0;
    }

    /**
     * Sets the position directly (clamped to valid range)
     * @param position New position (0-1)
     */
    public void setPosition(float position) {
        this.currentPosition = Math.max(0, Math.min(1, position));
        this.previousPosition = currentPosition;
    }

    /**
     * Sets the target position for movement
     * @param position Target position (0-1)
     */
    public void setTargetPosition(float position) {
        this.targetPosition = Math.max(0, Math.min(1, position));
    }

    /**
     * Sets the movement speed
     * @param speed Movement speed per tick
     */
    public void setSpeed(float speed) {
        this.speed = Math.max(0, speed);
    }

    /**
     * Starts the failure animation (called when target is unsuccessfully captured)
     */
    public void startFailAnimation() {
        state = TargetState.ANIMATING_FAIL;
        animationTick = 0;
        previousAnimationTick = 0;
    }

    /**
     * Starts the collection animation (called when target capture succeeds)
     * @param x Starting X position in screen space (normalized 0-1)
     * @param y Starting Y position in screen space (normalized 0-1)
     */
    public void startCollectionAnimation(float x, float y) {
        state = TargetState.ANIMATING_SUCCESS;
        animationTick = 0;
        previousAnimationTick = 0;

        // Initialize physics state
        // Create a physics simulation for each reward item
        physicsSimulations.clear();
        for (ItemStack item : rewardItems) {
            physicsSimulations.add(new PhysicsSimulation(item, 0, 0, random));
        }
    }

    /**
     * Gets the current state of the target
     * @return The target's current state
     */
    public TargetState getState() {
        return state;
    }

    /**
     * Checks if the animation is complete and target can be removed
     * @return true if animation is complete
     */
    public boolean isAnimationComplete() {
        return state == TargetState.COMPLETE;
    }

    /**
     * Gets the collection animation spin angle with interpolation
     * @param partialTick Progress between current and next tick (0-1)
     * @return Rotation angle in degrees (0-720 for two full rotations)
     */
    public float getFailSpinAngle(float partialTick) {
        if (state != TargetState.ANIMATING_FAIL) {
            return 0;
        }

        float interpolatedTick = previousAnimationTick + (animationTick - previousAnimationTick) * partialTick;
        float progress = Math.min(1.0f, interpolatedTick / COLLECTION_ANIMATION_DURATION);

        // Apply easing for smooth spin
        float easedProgress = MathUtil.easeInOutQuad(progress);

        // Two full rotations (720 degrees)
        return easedProgress * 720f;
    }

    /**
     * Gets the collection animation scale with interpolation
     * @param partialTick Progress between current and next tick (0-1)
     * @return Scale value (1.0 to 0.0)
     */
    public float getCollectionScale(float partialTick) {
        if (state != TargetState.ANIMATING_FAIL) {
            return 1.0f;
        }

        float interpolatedTick = previousAnimationTick + (animationTick - previousAnimationTick) * partialTick;
        float progress = Math.min(1.0f, interpolatedTick / COLLECTION_ANIMATION_DURATION);

        // Apply easing for smooth shrink
        float easedProgress = MathUtil.easeInOutQuad(progress);

        // Scale from 1.0 to 0.0
        return 1.0f - easedProgress;
    }

    /**
     * Gets the display ItemStack for a fishing target.
     * For FishtasticFish items in ACTIVE or ANIMATING_FAIL states, returns the generic fish item
     * to hide the actual reward until it's caught.
     * For caught items (ANIMATING_SUCCESS), returns the actual item to reveal the reward.
     *
     * @return The ItemStack to display in the minigame
     */
    public ItemStack getDisplayItemStack() {
        // Only hide FishtasticFish items during ACTIVE and ANIMATING_FAIL states
        // Show the actual item during ANIMATING_SUCCESS (when caught)
        if ((state == FishingTarget.TargetState.ACTIVE || state == FishingTarget.TargetState.ANIMATING_FAIL)) {
            if(category == TargetCategory.FISH) {
                return new ItemStack(FishtasticItems.GENERIC_FISH);
            } else {
                return new ItemStack(Items.CHEST);
            }
        }

        // During success animation, show first reward item (or empty if no rewards)
        return rewardItems.isEmpty() ? ItemStack.EMPTY : rewardItems.get(0);
    }
}
