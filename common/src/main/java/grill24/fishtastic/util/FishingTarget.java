package grill24.fishtastic.util;

import grill24.fishtastic.Fishtastic;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector2f;
import org.joml.Vector3f;

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
        ANIMATING_COLLECTION,   // Target was caught and is playing collection animation
        ANIMATING_FAIL,         // Target failed and is playing fail animation
        COMPLETE                // Animation finished, ready to be removed
    }

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

    // Animation state
    private TargetState state;
    private int animationTick; // Tracks animation progress
    private int previousAnimationTick; // For interpolation

    // Collection animation constants
    private static final int COLLECTION_ANIMATION_DURATION = 30; // 1.5 seconds at 20 TPS

    // Fail animation constants and physics state
    private static final int FAIL_ANIMATION_MAX_DURATION = 100; // Max duration before forced completion
    private static final float FAIL_GRAVITY = 0.003f;
    private static final float FAIL_INITIAL_VELOCITY_Y_MIN = 0.02f;
    private static final float FAIL_INITIAL_VELOCITY_Y_MAX = 0.06f;
    private static final float FAIL_INITIAL_VELOCITY_X_MIN = -0.03f;
    private static final float FAIL_INITIAL_VELOCITY_X_MAX = 0.03f;
    private static final float FAIL_INITIAL_ROT_VEL_MIN = 5f;
    private static final float FAIL_INITIAL_ROT_VEL_MAX = 15f;

    private Vector2f failPosition; // Screen-space position for fail animation (normalized 0-1)
    private Vector2f previousFailPosition; // Previous position for interpolation
    private Vector2f failVelocity; // Velocity vector
    private Vector3f failRotation; // Current rotation angles in degrees (X, Y, Z axes)
    private Vector3f failRotationalVelocity; // Rotation speeds in degrees per tick (X, Y, Z axes)
    private Vector3f previousFailRotation; // For interpolation

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
        this.state = TargetState.ACTIVE;
        this.animationTick = 0;
        this.previousAnimationTick = 0;
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
        this.state = TargetState.ACTIVE;
        this.animationTick = 0;
        this.previousAnimationTick = 0;
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

            case ANIMATING_COLLECTION:
                // Collection animation: spin and shrink
                animationTick++;
                if (animationTick >= COLLECTION_ANIMATION_DURATION) {
                    state = TargetState.COMPLETE;
                }
                break;

            case ANIMATING_FAIL:
                // Fail animation: physics simulation
                if (failRotation != null) {
                    if (previousFailRotation == null) {
                        previousFailRotation = new Vector3f(failRotation);
                    } else {
                        previousFailRotation.set(failRotation);
                    }
                }
                if (failPosition != null) {
                    if (previousFailPosition == null) {
                        previousFailPosition = new Vector2f(failPosition);
                    } else {
                        previousFailPosition.set(failPosition);
                    }
                }
                animationTick++;

                // Apply gravity
                failVelocity.y -= FAIL_GRAVITY;

                // Update position
                failPosition.x += failVelocity.x;
                failPosition.y += failVelocity.y;

                // Update rotation on all axes
                failRotation.x += failRotationalVelocity.x;
                failRotation.y += failRotationalVelocity.y;
                failRotation.z += failRotationalVelocity.z;

                // Check if off-screen or max duration reached
                if (failPosition.y() < -2 || animationTick >= FAIL_ANIMATION_MAX_DURATION) {
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
     * Starts the collection animation (called when target is caught)
     */
    public void startCollectionAnimation() {
        state = TargetState.ANIMATING_COLLECTION;
        animationTick = 0;
        previousAnimationTick = 0;
    }

    /**
     * Starts the fail animation (called when target capture fails)
     * @param x Starting X position in screen space (normalized 0-1)
     * @param y Starting Y position in screen space (normalized 0-1)
     */
    public void startFailAnimation(float x, float y) {
        state = TargetState.ANIMATING_FAIL;
        animationTick = 0;
        previousAnimationTick = 0;

        // Initialize physics state
        // Start at current bar position
        failPosition = new Vector2f(x, y);
        previousFailPosition = new Vector2f(x, y); // Initialize previous position

        // Random upward and sideways velocity
        float velocityY = FAIL_INITIAL_VELOCITY_Y_MIN + random.nextFloat() * (FAIL_INITIAL_VELOCITY_Y_MAX - FAIL_INITIAL_VELOCITY_Y_MIN);
        float velocityX = FAIL_INITIAL_VELOCITY_X_MIN + random.nextFloat() * (FAIL_INITIAL_VELOCITY_X_MAX - FAIL_INITIAL_VELOCITY_X_MIN);
        failVelocity = new Vector2f(velocityX, velocityY);

        // Random rotational velocities for all three axes
        float rotVelX = FAIL_INITIAL_ROT_VEL_MIN + random.nextFloat() * (FAIL_INITIAL_ROT_VEL_MAX - FAIL_INITIAL_ROT_VEL_MIN);
        float rotVelY = FAIL_INITIAL_ROT_VEL_MIN + random.nextFloat() * (FAIL_INITIAL_ROT_VEL_MAX - FAIL_INITIAL_ROT_VEL_MIN);
        float rotVelZ = FAIL_INITIAL_ROT_VEL_MIN + random.nextFloat() * (FAIL_INITIAL_ROT_VEL_MAX - FAIL_INITIAL_ROT_VEL_MIN);

        // Randomize direction for each axis
        if (random.nextBoolean()) rotVelX = -rotVelX;
        if (random.nextBoolean()) rotVelY = -rotVelY;
        if (random.nextBoolean()) rotVelZ = -rotVelZ;

        failRotationalVelocity = new Vector3f(rotVelX, rotVelY, rotVelZ);

        failRotation = new Vector3f(0, 0, 0);
        previousFailRotation = new Vector3f(0, 0, 0);
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
    public float getCollectionSpinAngle(float partialTick) {
        if (state != TargetState.ANIMATING_COLLECTION) {
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
        if (state != TargetState.ANIMATING_COLLECTION) {
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
     * Gets the fail animation screen position with interpolation
     * @param partialTick Progress between current and next tick (0-1)
     * @return Interpolated screen position (normalized 0-1)
     */
    public Vector2f getFailScreenPosition(float partialTick) {
        if (state == TargetState.ANIMATING_FAIL && failPosition != null && previousFailPosition != null) {
            // Interpolate between previous and current position for smooth rendering
            float interpX = previousFailPosition.x + (failPosition.x - previousFailPosition.x) * partialTick;
            float interpY = previousFailPosition.y + (failPosition.y - previousFailPosition.y) * partialTick;
            return new Vector2f(interpX, interpY);
        }
        return failPosition != null ? new Vector2f(failPosition) : new Vector2f(0.5f, currentPosition);
    }

    /**
     * Gets the fail animation rotation angles with interpolation on all axes
     * @param partialTick Progress between current and next tick (0-1)
     * @return Rotation angles in degrees (X, Y, Z axes)
     */
    public Vector3f getFailRotation(float partialTick) {
        if (state != TargetState.ANIMATING_FAIL || failRotation == null || previousFailRotation == null) {
            return new Vector3f(0, 0, 0);
        }

        float interpX = previousFailRotation.x + (failRotation.x - previousFailRotation.x) * partialTick;
        float interpY = previousFailRotation.y + (failRotation.y - previousFailRotation.y) * partialTick;
        float interpZ = previousFailRotation.z + (failRotation.z - previousFailRotation.z) * partialTick;

        return new Vector3f(interpX, interpY, interpZ);
    }
}
