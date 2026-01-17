package grill24.fishtastic.util;

import net.minecraft.world.item.ItemStack;

import java.util.Random;

/**
 * Represents a target item in the fishing minigame that moves along the bar.
 * Each target has its own position, movement behavior, and associated ItemStack.
 */
public class FishingTarget {
    // Movement constants
    private static final float MIN_MOVE_INTERVAL_TICKS = 20f; // 1 second
    private static final float MAX_MOVE_INTERVAL_TICKS = 120f; // 6 seconds
    private static final float MIN_SPEED = 0.002f; // Minimum interpolation speed per tick
    private static final float MAX_SPEED = 0.01f; // Maximum interpolation speed per tick

    // Position state
    private float currentPosition; // Current position on the bar (0-1)
    private float targetPosition; // Target position we're moving towards (0-1)
    private float previousPosition; // Position from previous tick for interpolation
    private float speed; // Current movement speed

    // Timing state
    private int ticksSinceLastMove;
    private int ticksUntilNextMove;

    // Associated data
    private final ItemStack itemStack;
    private final Random random;

    // Catch progress for this specific target
    private float catchProgress;
    private static final float CATCH_PROGRESS_GAIN = 0.01f;
    private static final float CATCH_PROGRESS_LOSS = 0.005f;
    private static final float INITIAL_CATCH_PROGRESS = 0.5f;

    // Shaking effect for active capture
    private static final float SHAKE_INTENSITY = 0.002f; // Maximum shake offset
    private int shakeTick; // Counter for shake animation

    /**
     * Creates a new fishing target with a random initial position
     * @param itemStack The ItemStack to display for this target
     * @param random Random instance for generating movement patterns
     */
    public FishingTarget(ItemStack itemStack, Random random) {
        this.itemStack = itemStack;
        this.random = random;
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
     * Creates a new fishing target with a specific initial position
     * @param itemStack The ItemStack to display for this target
     * @param random Random instance for generating movement patterns
     * @param initialPosition Initial position (0-1)
     */
    public FishingTarget(ItemStack itemStack, Random random, float initialPosition) {
        this.itemStack = itemStack;
        this.random = random;
        this.currentPosition = Math.max(0, Math.min(1, initialPosition));
        this.targetPosition = currentPosition;
        this.previousPosition = currentPosition;
        this.speed = 0;
        this.ticksSinceLastMove = 0;
        this.ticksUntilNextMove = getRandomMoveInterval();
        this.catchProgress = INITIAL_CATCH_PROGRESS;
        this.shakeTick = 0;
    }

    /**
     * Updates the target's position for one tick
     */
    public void tick() {
        previousPosition = currentPosition;

        ticksSinceLastMove++;
        shakeTick++; // Increment shake animation counter

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
        } else {
            catchProgress -= CATCH_PROGRESS_LOSS;
            catchProgress = Math.max(0.0f, catchProgress);
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
     * Gets the ItemStack associated with this target
     * @return The ItemStack to render
     */
    public ItemStack getItemStack() {
        return itemStack;
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
}
