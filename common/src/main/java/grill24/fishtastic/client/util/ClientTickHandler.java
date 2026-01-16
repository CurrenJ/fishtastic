package grill24.fishtastic.client.util;

/**
 * Handles client-side tick tracking for animations and time-based calculations.
 * This class maintains a running total of ticks with partial tick interpolation
 * for smooth animations.
 */
public class ClientTickHandler {

    private static float totalTicks = 0.0f;
    private static float partialTick = 0.0f;

    /**
     * Updates the tick counter. This should be called once per client tick.
     *
     * @param delta The time elapsed since the last tick (typically 1.0f for a full tick)
     */
    public static void tick(float delta) {
        totalTicks += delta;
    }

    /**
     * Updates the partial tick value for smooth interpolation between ticks.
     * This is typically called during rendering.
     *
     * @param partial The partial tick value (0.0 to 1.0)
     */
    public static void setPartialTick(float partial) {
        partialTick = partial;
    }

    /**
     * Gets the total number of ticks elapsed, including the current partial tick.
     * This provides a smooth, continuously increasing value for animations.
     *
     * @return The total ticks with partial tick interpolation
     */
    public static float total() {
        return totalTicks + partialTick;
    }

    /**
     * Gets the total number of ticks elapsed (without partial tick interpolation).
     *
     * @return The total number of full ticks
     */
    public static float totalTicks() {
        return totalTicks;
    }

    /**
     * Gets the current partial tick value.
     *
     * @return The partial tick (0.0 to 1.0)
     */
    public static float partialTick() {
        return partialTick;
    }

    /**
     * Resets the tick counter to zero. Useful for testing or when reloading resources.
     */
    public static void reset() {
        totalTicks = 0.0f;
        partialTick = 0.0f;
    }
}

