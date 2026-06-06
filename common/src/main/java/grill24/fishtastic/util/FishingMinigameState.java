package grill24.fishtastic.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the state and physics of the fishing minigame.
 * Tracks bobber position (0-1 range) with gravity, acceleration, and bounciness.
 * Manages multiple fishing targets that move along the bar.
 */
public class FishingMinigameState {
    // Physics constants
    private static final float GRAVITY = 0.007f; // Downward acceleration per tick
    private static final float BOUNCINESS = 0.6f; // Velocity retention on bounce (0-1)
    private static final float IMPULSE_STRENGTH = 0.04f; // Upward impulse when player uses item
    private static final float FRICTION = 0.986f; // Velocity dampening per tick

    // Catch progress constants
    private static final float CATCH_PROGRESS_GAIN = 0.0025f; // Progress gain per tick when item is in bobber
    private static final float CATCH_PROGRESS_LOSS = 0.0001f; // Progress loss per tick when item is outside bobber

    // Bobber state — size supplied by caller (derived from texture layout, not hardcoded here)
    private final float bobberSize;
    private final float maxPosition;
    private float bobberPosition; // 0 = default/bottom, 1 = max/top
    private float previousBobberPosition; // Position at start of current tick for interpolation
    private float bobberVelocity; // Velocity in position units per tick

    // Catch progress state
    private float catchProgress; // 0 = no progress, 1 = caught (item should be caught)

    // Target management
    private final List<FishingTarget> targets;

    // Boundaries
    private static final float MIN_POSITION = 0.0f;

    /**
     * Creates a new fishing minigame state with bobber at default position.
     * @param bobberSize Bobber height as a fraction of the travel zone (from FishingBarLayout.bobberSize())
     */
    public FishingMinigameState(float bobberSize) {
        this.bobberSize = bobberSize;
        this.maxPosition = 1.0f - bobberSize;
        this.bobberPosition = 0.0f;
        this.previousBobberPosition = 0.0f;
        this.bobberVelocity = 0.0f;
        this.catchProgress = 0.0f;
        this.targets = new ArrayList<>();
    }

    /**
     * Creates a new fishing minigame state with a specific initial position.
     * @param bobberSize      Bobber height as a fraction of the travel zone (from FishingBarLayout.bobberSize())
     * @param initialPosition Initial bobber position (0-1)
     */
    public FishingMinigameState(float bobberSize, float initialPosition) {
        this.bobberSize = bobberSize;
        this.maxPosition = 1.0f - bobberSize;
        this.bobberPosition = Math.max(MIN_POSITION, Math.min(maxPosition, initialPosition));
        this.previousBobberPosition = this.bobberPosition;
        this.bobberVelocity = 0.0f;
        this.catchProgress = 0.0f;
        this.targets = new ArrayList<>();
    }

    /**
     * Updates the bobber physics for one tick
     */
    public void tick() {
        // Snapshot position before physics update so interpolation is impulse-independent
        previousBobberPosition = bobberPosition;

        // Apply gravity (downward acceleration)
        bobberVelocity -= GRAVITY;

        // Apply friction
        bobberVelocity *= FRICTION;

        // Update position
        bobberPosition += bobberVelocity;

        // Handle boundary collisions with bounce
        if (bobberPosition < MIN_POSITION) {
            bobberPosition = MIN_POSITION;
            previousBobberPosition = MIN_POSITION; // Prevent interpolation snap through floor
            bobberVelocity = Math.abs(bobberVelocity) * BOUNCINESS;
        } else if (bobberPosition > maxPosition) {
            bobberPosition = maxPosition;
            previousBobberPosition = maxPosition; // Prevent interpolation snap through ceiling
            bobberVelocity = -Math.abs(bobberVelocity) * BOUNCINESS;
        }

        // Update all targets — pass current bobber state so FLEE targets can react
        for (FishingTarget target : targets) {
            target.tick(bobberPosition, bobberSize);
        }
    }

    /**
     * Applies an upward impulse to the bobber (player interaction)
     */
    public void applyImpulse() {
        bobberVelocity += IMPULSE_STRENGTH;
    }

    /**
     * Applies a custom impulse to the bobber
     * @param impulse The impulse strength (positive = upward, negative = downward)
     */
    public void applyImpulse(float impulse) {
        bobberVelocity += impulse;
    }

    /**
     * Updates catch progress when item is inside the bobber
     */
    public void updateCatchProgress(boolean isItemInBobber) {
        if (isItemInBobber) {
            // Gain progress when item is in bobber
            catchProgress += CATCH_PROGRESS_GAIN;
            catchProgress = Math.min(1.0f, catchProgress);
        } else {
            // Lose progress when item is outside bobber
            catchProgress -= CATCH_PROGRESS_LOSS;
            catchProgress = Math.max(0.0f, catchProgress);
        }
    }

    /**
     * Gets the current catch progress
     * @return Progress value between 0 (no progress) and 1 (caught)
     */
    public float getCatchProgress() {
        return catchProgress;
    }

    /**
     * Checks if the item has been caught (progress reached 1.0)
     * @return true if catch progress is at maximum
     */
    public boolean isItemCaught() {
        return catchProgress >= 1.0f;
    }

    /**
     * Gets the current bobber position
     * @return Position value between 0 (bottom/default) and 1 (top/max)
     */
    public float getBobberPosition() {
        return bobberPosition;
    }

    /**
     * Gets the interpolated bobber position for smooth rendering between ticks.
     * Uses previous velocity to interpolate smoothly even when impulses are applied.
     * @param partialTick Progress between current and next tick (0-1)
     * @return Interpolated position value between 0 and 1
     */
    public float getInterpolatedBobberPosition(float partialTick) {
        return Math.max(MIN_POSITION, Math.min(maxPosition,
                previousBobberPosition + (bobberPosition - previousBobberPosition) * partialTick));
    }

    /**
     * Gets the current bobber velocity
     * @return Velocity in position units per tick (positive = upward, negative = downward)
     */
    public float getBobberVelocity() {
        return bobberVelocity;
    }

    /**
     * Sets the bobber position directly (clamped to valid range)
     * @param position New position (0-1)
     */
    public void setBobberPosition(float position) {
        this.bobberPosition = Math.max(MIN_POSITION, Math.min(maxPosition, position));
    }

    /**
     * Sets the bobber velocity directly
     * @param velocity New velocity
     */
    public void setBobberVelocity(float velocity) {
        this.bobberVelocity = velocity;
    }

    /**
     * Resets the minigame state to initial conditions
     */
    public void reset() {
        this.bobberPosition = 0.0f;
        this.previousBobberPosition = 0.0f;
        this.bobberVelocity = 0.0f;
        this.catchProgress = 0.0f;
        for (FishingTarget target : targets) {
            target.reset();
        }
    }

    /**
     * Checks if the bobber is at the bottom boundary
     * @return true if at or very close to bottom
     */
    public boolean isAtBottom() {
        return bobberPosition <= MIN_POSITION + 0.01f;
    }

    /**
     * Checks if the bobber is at the top boundary
     * @return true if at or very close to top
     */
    public boolean isAtTop() {
        return bobberPosition >= maxPosition - 0.01f;
    }

    public float getBobberSize() {
        return bobberSize;
    }

    /**
     * Adds a target to the minigame
     * @param target The fishing target to add
     */
    public void addTarget(FishingTarget target) {
        targets.add(target);
    }

    /**
     * Removes a target from the minigame
     * @param target The fishing target to remove
     * @return true if the target was removed
     */
    public boolean removeTarget(FishingTarget target) {
        return targets.remove(target);
    }

    /**
     * Gets all targets in the minigame
     * @return List of fishing targets
     */
    public List<FishingTarget> getTargets() {
        return targets;
    }

    /**
     * Clears all targets from the minigame
     */
    public void clearTargets() {
        targets.clear();
    }

    /**
     * Gets the number of targets
     * @return The number of targets in the minigame
     */
    public int getTargetCount() {
        return targets.size();
    }
}
