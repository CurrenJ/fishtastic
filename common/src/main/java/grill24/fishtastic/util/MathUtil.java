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

    /**
     * Ease-in-out quadratic function for smooth acceleration and deceleration.
     * Accelerates from 0 to 0.5, then decelerates from 0.5 to 1.
     *
     * @param t The input value (typically between 0.0 and 1.0)
     * @return The eased value
     */
    public static float easeInOutQuad(float t) {
        return t < 0.5f ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2;
    }

    /**
     * Ease-out cubic function for natural deceleration.
     * Starts fast and decelerates towards the end.
     *
     * @param t The input value (typically between 0.0 and 1.0)
     * @return The eased value
     */
    public static float easeOutCubic(float t) {
        return 1 - (float) Math.pow(1 - t, 3);
    }

    /**
     * Applies easing to interpolate between two values.
     *
     * @param start The starting value
     * @param end The ending value
     * @param t The interpolation factor (typically between 0.0 and 1.0)
     * @param easingFunction The easing function to apply
     * @return The eased interpolated value
     */
    public static float easedLerp(float start, float end, float t, java.util.function.Function<Float, Float> easingFunction) {
        float easedT = easingFunction.apply(t);
        return lerp(start, end, easedT);
    }
}

