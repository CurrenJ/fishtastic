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
    public static final float IMPULSE_STRENGTH = 0.04f; // Upward impulse when player uses item
    private static final float FRICTION = 0.986f; // Velocity dampening per tick

    // Catch progress constants
    private static final float CATCH_PROGRESS_GAIN = 0.0025f; // Progress gain per tick when item is in bobber
    private static final float CATCH_PROGRESS_LOSS = 0.0001f; // Progress loss per tick when item is outside bobber

    // Bobber state — size supplied by caller (derived from texture layout, not hardcoded here)
    private final float bobberSize;
    private final float maxPosition;
    private float bobberPosition; // 0 = default/bottom, 1 = max/top
    private float bobberVelocity; // Velocity in position units per tick

    // Envelope of bobber positions visited since the last resetSweptRange() call. Bobber physics
    // run once per render frame while catch-progress overlap is only evaluated once per 20 Hz
    // game tick, so a fast bobber can otherwise pass straight through a narrow target between two
    // tick samples without ever registering an overlap. Tracking the swept range lets the tick
    // check "did the bobber pass over this target at any point this tick" instead of just "is the
    // bobber over this target right now".
    private float sweptMinPosition;
    private float sweptMaxPosition;

    // Catch progress state
    private float catchProgress; // 0 = no progress, 1 = caught (item should be caught)

    // Tutorial flags
    private boolean paused = false;
    private boolean noCatchDrain = false;

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
        this.bobberVelocity = 0.0f;
        this.catchProgress = 0.0f;
        this.targets = new ArrayList<>();
        this.sweptMinPosition = this.sweptMaxPosition = this.bobberPosition;
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
        this.bobberVelocity = 0.0f;
        this.catchProgress = 0.0f;
        this.targets = new ArrayList<>();
        this.sweptMinPosition = this.sweptMaxPosition = this.bobberPosition;
    }

    public void setPaused(boolean paused) { this.paused = paused; }
    public boolean isPaused() { return paused; }
    public void setNoCatchDrain(boolean noCatchDrain) { this.noCatchDrain = noCatchDrain; }

    /**
     * Updates all targets for one game tick (20 Hz). Bobber physics are handled per render
     * frame by {@link #updatePhysics(float)}.
     */
    public void tick() {
        if (paused) return;
        for (FishingTarget target : targets) {
            target.tick(bobberPosition, bobberSize);
        }
    }

    /**
     * Integrates bobber physics for one render frame. Called from the render path so the
     * bobber responds to input and moves at the full display frame rate.
     *
     * @param deltaTimeTicks elapsed time since the last render frame, in game-tick units
     *                       (1.0 = one full 50 ms tick; ~0.33 at 60 fps)
     */
    public void updatePhysics(float deltaTimeTicks) {
        if (paused) return;
        bobberVelocity -= GRAVITY * deltaTimeTicks;
        bobberVelocity *= (float) Math.pow(FRICTION, deltaTimeTicks);
        bobberPosition += bobberVelocity * deltaTimeTicks;
        if (bobberPosition < MIN_POSITION) {
            bobberPosition = MIN_POSITION;
            bobberVelocity = Math.abs(bobberVelocity) * BOUNCINESS;
        } else if (bobberPosition > maxPosition) {
            bobberPosition = maxPosition;
            bobberVelocity = -Math.abs(bobberVelocity) * BOUNCINESS;
        }
        sweptMinPosition = Math.min(sweptMinPosition, bobberPosition);
        sweptMaxPosition = Math.max(sweptMaxPosition, bobberPosition);
    }

    /**
     * Lower bound of the bobber positions visited since the last {@link #resetSweptRange()}.
     */
    public float getSweptMinPosition() {
        return sweptMinPosition;
    }

    /**
     * Upper bound of the bobber positions visited since the last {@link #resetSweptRange()}.
     */
    public float getSweptMaxPosition() {
        return sweptMaxPosition;
    }

    /**
     * Collapses the swept range back to the current bobber position. Call once per game tick,
     * after consuming the range for catch-progress overlap checks, so the next tick accumulates
     * a fresh envelope.
     */
    public void resetSweptRange() {
        sweptMinPosition = sweptMaxPosition = bobberPosition;
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
            catchProgress += CATCH_PROGRESS_GAIN;
            catchProgress = Math.min(1.0f, catchProgress);
        } else if (!noCatchDrain) {
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
        this.bobberVelocity = 0.0f;
        this.catchProgress = 0.0f;
        this.sweptMinPosition = this.sweptMaxPosition = this.bobberPosition;
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
