package grill24.fishtastic.util;

/**
 * Utility class for mathematical operations used throughout the mod.
 */
public class MathUtil {

    /**
     * Linear interpolation (lerp) between two values.
     *
     * @param start The starting value
     * @param end The ending value
     * @param t The interpolation factor (typically between 0.0 and 1.0)
     * @return The interpolated value between start and end
     */
    public static float lerp(float start, float end, float t) {
        return start + (end - start) * t;
    }

    /**
     * Linear interpolation (lerp) between two double values.
     *
     * @param start The starting value
     * @param end The ending value
     * @param t The interpolation factor (typically between 0.0 and 1.0)
     * @return The interpolated value between start and end
     */
    public static double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }

    /**
     * Clamps a value between a minimum and maximum.
     *
     * @param value The value to clamp
     * @param min The minimum value
     * @param max The maximum value
     * @return The clamped value
     */
    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Clamps a value between a minimum and maximum.
     *
     * @param value The value to clamp
     * @param min The minimum value
     * @param max The maximum value
     * @return The clamped value
     */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Clamps a value between a minimum and maximum.
     *
     * @param value The value to clamp
     * @param min The minimum value
     * @param max The maximum value
     * @return The clamped value
     */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

