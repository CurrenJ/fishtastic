package grill24.fishtastic.util;

import net.minecraft.world.item.ItemStack;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Handles physics simulation for an animated item in the fishing minigame success animation.
 * Each simulation tracks position, velocity, rotation, and provides interpolation for smooth rendering.
 */
public class PhysicsSimulation {
    // Physics constants
    private static final float GRAVITY = 0.003f;
    private static final float INITIAL_VELOCITY_Y_MIN = 0.02f;
    private static final float INITIAL_VELOCITY_Y_MAX = 0.06f;
    private static final float INITIAL_VELOCITY_X_MIN = -0.03f;
    private static final float INITIAL_VELOCITY_X_MAX = 0.03f;
    private static final float INITIAL_ROT_VEL_MIN = 5f;
    private static final float INITIAL_ROT_VEL_MAX = 15f;

    // Item being animated
    private final ItemStack itemStack;

    // Position state (screen-space, normalized 0-1)
    private Vector2f position;
    private Vector2f previousPosition;
    private Vector2f velocity;

    // Rotation state (degrees on X, Y, Z axes)
    private Vector3f rotation;
    private Vector3f previousRotation;
    private Vector3f rotationalVelocity;

    /**
     * Creates a new physics simulation for an item
     * @param itemStack The item being animated
     * @param startX Starting X position in screen space (normalized 0-1)
     * @param startY Starting Y position in screen space (normalized 0-1)
     * @param random Random instance for generating velocities
     */
    public PhysicsSimulation(ItemStack itemStack, float startX, float startY, Random random) {
        this.itemStack = itemStack;

        // Initialize position
        this.position = new Vector2f(startX, startY);
        this.previousPosition = new Vector2f(startX, startY);

        // Random upward and sideways velocity
        float velocityY = INITIAL_VELOCITY_Y_MIN + random.nextFloat() * (INITIAL_VELOCITY_Y_MAX - INITIAL_VELOCITY_Y_MIN);
        float velocityX = INITIAL_VELOCITY_X_MIN + random.nextFloat() * (INITIAL_VELOCITY_X_MAX - INITIAL_VELOCITY_X_MIN);
        this.velocity = new Vector2f(velocityX, velocityY);

        // Random rotational velocities for all three axes
        float rotVelX = INITIAL_ROT_VEL_MIN + random.nextFloat() * (INITIAL_ROT_VEL_MAX - INITIAL_ROT_VEL_MIN);
        float rotVelY = INITIAL_ROT_VEL_MIN + random.nextFloat() * (INITIAL_ROT_VEL_MAX - INITIAL_ROT_VEL_MIN);
        float rotVelZ = INITIAL_ROT_VEL_MIN + random.nextFloat() * (INITIAL_ROT_VEL_MAX - INITIAL_ROT_VEL_MIN);

        // Randomize direction for each axis
        if (random.nextBoolean()) rotVelX = -rotVelX;
        if (random.nextBoolean()) rotVelY = -rotVelY;
        if (random.nextBoolean()) rotVelZ = -rotVelZ;

        this.rotationalVelocity = new Vector3f(rotVelX, rotVelY, rotVelZ);
        this.rotation = new Vector3f(0, 0, 0);
        this.previousRotation = new Vector3f(0, 0, 0);
    }

    /**
     * Updates the physics simulation for one tick
     */
    public void tick() {
        // Store previous state for interpolation
        previousPosition.set(position);
        previousRotation.set(rotation);

        // Apply gravity
        velocity.y -= GRAVITY;

        // Update position
        position.x += velocity.x;
        position.y += velocity.y;

        // Update rotation on all axes
        rotation.x += rotationalVelocity.x;
        rotation.y += rotationalVelocity.y;
        rotation.z += rotationalVelocity.z;
    }

    /**
     * Gets the interpolated screen position for smooth rendering
     * @param partialTick Progress between current and next tick (0-1)
     * @return Interpolated screen position (normalized 0-1)
     */
    public Vector2f getInterpolatedPosition(float partialTick) {
        float interpX = previousPosition.x + (position.x - previousPosition.x) * partialTick;
        float interpY = previousPosition.y + (position.y - previousPosition.y) * partialTick;
        return new Vector2f(interpX, interpY);
    }

    /**
     * Gets the interpolated rotation angles for smooth rendering
     * @param partialTick Progress between current and next tick (0-1)
     * @return Rotation angles in degrees (X, Y, Z axes)
     */
    public Vector3f getInterpolatedRotation(float partialTick) {
        float interpX = previousRotation.x + (rotation.x - previousRotation.x) * partialTick;
        float interpY = previousRotation.y + (rotation.y - previousRotation.y) * partialTick;
        float interpZ = previousRotation.z + (rotation.z - previousRotation.z) * partialTick;
        return new Vector3f(interpX, interpY, interpZ);
    }

    /**
     * Gets the current position (without interpolation)
     * @return Current screen position
     */
    public Vector2f getPosition() {
        return new Vector2f(position);
    }

    /**
     * Gets the current rotation (without interpolation)
     * @return Current rotation angles
     */
    public Vector3f getRotation() {
        return new Vector3f(rotation);
    }

    /**
     * Gets the ItemStack being animated
     * @return The ItemStack
     */
    public ItemStack getItemStack() {
        return itemStack;
    }

    /**
     * Checks if the item is off-screen (below the visible area)
     * @return true if the item has fallen off-screen
     */
    public boolean isOffScreen() {
        return position.y < -2;
    }
}
