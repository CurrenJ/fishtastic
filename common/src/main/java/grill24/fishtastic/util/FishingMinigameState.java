package grill24.fishtastic.util;

/**
 * Manages the state and physics of the fishing minigame.
 * Tracks bobber position (0-1 range) with gravity, acceleration, and bounciness.
 */
public class FishingMinigameState {
    // Physics constants
    private static final float GRAVITY = 0.007f; // Downward acceleration per tick
    private static final float BOUNCINESS = 0.6f; // Velocity retention on bounce (0-1)
    private static final float IMPULSE_STRENGTH = 0.04f; // Upward impulse when player uses item
    private static final float FRICTION = 0.986f; // Velocity dampening per tick

    // Bobber state
    private float bobberPosition; // 0 = default/bottom, 1 = max/top
    private float bobberVelocity; // Velocity in position units per tick
    private float previousVelocity; // Velocity from previous tick for smooth interpolation

    // Boundaries
    private static final float MIN_POSITION = 0.0f;
    private static final float MAX_POSITION = 1.0f;

    /**
     * Creates a new fishing minigame state with bobber at default position
     */
    public FishingMinigameState() {
        this.bobberPosition = 0.0f;
        this.bobberVelocity = 0.0f;
        this.previousVelocity = 0.0f;
    }

    /**
     * Creates a new fishing minigame state with a specific initial position
     * @param initialPosition Initial bobber position (0-1)
     */
    public FishingMinigameState(float initialPosition) {
        this.bobberPosition = Math.max(MIN_POSITION, Math.min(MAX_POSITION, initialPosition));
        this.bobberVelocity = 0.0f;
        this.previousVelocity = 0.0f;
    }

    /**
     * Updates the bobber physics for one tick
     */
    public void tick() {
        // Save previous velocity for interpolation
        previousVelocity = bobberVelocity;

        // Apply gravity (downward acceleration)
        bobberVelocity -= GRAVITY;

        // Apply friction
        bobberVelocity *= FRICTION;

        // Update position
        bobberPosition += bobberVelocity;

        // Handle boundary collisions with bounce
        if (bobberPosition < MIN_POSITION) {
            bobberPosition = MIN_POSITION;
            bobberVelocity = Math.abs(bobberVelocity) * BOUNCINESS; // Bounce up
            previousVelocity = bobberVelocity; // Update previous velocity to avoid interpolation snap
        } else if (bobberPosition > MAX_POSITION) {
            bobberPosition = MAX_POSITION;
            bobberVelocity = -Math.abs(bobberVelocity) * BOUNCINESS; // Bounce down
            previousVelocity = bobberVelocity; // Update previous velocity to avoid interpolation snap
        }
    }

    /**
     * Applies an upward impulse to the bobber (player interaction)
     */
    public void applyImpulse() {
        // Update previous velocity to current before applying impulse to avoid snap
        previousVelocity = bobberVelocity;
        bobberVelocity += IMPULSE_STRENGTH;
    }

    /**
     * Applies a custom impulse to the bobber
     * @param impulse The impulse strength (positive = upward, negative = downward)
     */
    public void applyImpulse(float impulse) {
        // Update previous velocity to current before applying impulse to avoid snap
        previousVelocity = bobberVelocity;
        bobberVelocity += impulse;
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
        // Interpolate between previous position (at start of tick) and current position (at end of tick)
        // We need to calculate where we were at the start of the tick
        float startPosition = bobberPosition - bobberVelocity;

        // Interpolate between start and current position
        float interpolatedPosition = startPosition + (bobberPosition - startPosition) * partialTick;

        // Clamp to valid range
        return Math.max(MIN_POSITION, Math.min(MAX_POSITION, interpolatedPosition));
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
        this.bobberPosition = Math.max(MIN_POSITION, Math.min(MAX_POSITION, position));
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
        this.previousVelocity = 0.0f;
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
        return bobberPosition >= MAX_POSITION - 0.01f;
    }
}
