package grill24.fishtastic.gametest;

import grill24.fishtastic.util.MathUtil;
import grill24.fishtastic.util.Utility;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

/**
 * Game tests for MathUtil and Utility. All logic is pure, zero Minecraft world dependency.
 */
public final class MathUtilGameTests {

    private MathUtilGameTests() {}

    // -------------------------------------------------------------------------
    // MathUtil.lerp
    // -------------------------------------------------------------------------

    /**
     * Float lerp: t=0 returns start, t=1 returns end, t=0.5 returns the midpoint.
     */
    public static void lerpFloatBoundaries(GameTestHelper helper) {
        helper.assertTrue(MathUtil.lerp(0f, 10f, 0f) == 0f, "t=0 must return start");
        helper.assertTrue(MathUtil.lerp(0f, 10f, 1f) == 10f, "t=1 must return end");
        helper.assertTrue(MathUtil.lerp(0f, 10f, 0.5f) == 5f,
            "lerp(0, 10, 0.5) must be 5, got " + MathUtil.lerp(0f, 10f, 0.5f));
        helper.succeed();
    }

    /**
     * Double lerp: same boundary/midpoint behavior as the float overload.
     */
    public static void lerpDoubleBoundaries(GameTestHelper helper) {
        helper.assertTrue(MathUtil.lerp(0d, 10d, 0d) == 0d, "t=0 must return start");
        helper.assertTrue(MathUtil.lerp(0d, 10d, 1d) == 10d, "t=1 must return end");
        helper.assertTrue(MathUtil.lerp(0d, 10d, 0.5d) == 5d,
            "lerp(0, 10, 0.5) must be 5, got " + MathUtil.lerp(0d, 10d, 0.5d));
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // MathUtil.clamp
    // -------------------------------------------------------------------------

    /**
     * Float clamp: below min clamps to min, above max clamps to max, in-range is unchanged.
     */
    public static void clampFloatBounds(GameTestHelper helper) {
        helper.assertTrue(MathUtil.clamp(-5f, 0f, 10f) == 0f, "Below-min must clamp to min");
        helper.assertTrue(MathUtil.clamp(15f, 0f, 10f) == 10f, "Above-max must clamp to max");
        helper.assertTrue(MathUtil.clamp(5f, 0f, 10f) == 5f, "In-range value must be unchanged");
        helper.succeed();
    }

    /**
     * Double clamp: same boundary behavior as the float overload.
     */
    public static void clampDoubleBounds(GameTestHelper helper) {
        helper.assertTrue(MathUtil.clamp(-5d, 0d, 10d) == 0d, "Below-min must clamp to min");
        helper.assertTrue(MathUtil.clamp(15d, 0d, 10d) == 10d, "Above-max must clamp to max");
        helper.assertTrue(MathUtil.clamp(5d, 0d, 10d) == 5d, "In-range value must be unchanged");
        helper.succeed();
    }

    /**
     * Int clamp: same boundary behavior as the float/double overloads.
     */
    public static void clampIntBounds(GameTestHelper helper) {
        helper.assertTrue(MathUtil.clamp(-5, 0, 10) == 0, "Below-min must clamp to min");
        helper.assertTrue(MathUtil.clamp(15, 0, 10) == 10, "Above-max must clamp to max");
        helper.assertTrue(MathUtil.clamp(5, 0, 10) == 5, "In-range value must be unchanged");
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // MathUtil easing functions
    // -------------------------------------------------------------------------

    /**
     * easeInOutQuad: f(0)=0, f(1)=1, and the documented accelerate-then-decelerate shape
     * (below the linear diagonal before the midpoint, above it after).
     */
    public static void easeInOutQuadShape(GameTestHelper helper) {
        helper.assertTrue(MathUtil.easeInOutQuad(0f) == 0f, "easeInOutQuad(0) must be 0");
        helper.assertTrue(MathUtil.easeInOutQuad(1f) == 1f, "easeInOutQuad(1) must be 1");
        helper.assertTrue(MathUtil.easeInOutQuad(0.5f) == 0.5f,
            "easeInOutQuad(0.5) must be exactly 0.5, got " + MathUtil.easeInOutQuad(0.5f));
        helper.assertTrue(MathUtil.easeInOutQuad(0.25f) < 0.25f,
            "Before the midpoint, easing must lag behind linear (accelerating)");
        helper.assertTrue(MathUtil.easeInOutQuad(0.75f) > 0.75f,
            "After the midpoint, easing must lead linear (decelerating)");
        helper.succeed();
    }

    /**
     * easeOutCubic: f(0)=0, f(1)=1, and fast-start/decelerate-towards-end shape
     * (above the linear diagonal at the midpoint).
     */
    public static void easeOutCubicShape(GameTestHelper helper) {
        helper.assertTrue(MathUtil.easeOutCubic(0f) == 0f, "easeOutCubic(0) must be 0");
        helper.assertTrue(MathUtil.easeOutCubic(1f) == 1f, "easeOutCubic(1) must be 1");
        helper.assertTrue(MathUtil.easeOutCubic(0.5f) == 0.875f,
            "easeOutCubic(0.5) must be 0.875, got " + MathUtil.easeOutCubic(0.5f));
        helper.assertTrue(MathUtil.easeOutCubic(0.5f) > 0.5f,
            "Midpoint must be ahead of linear (fast start, decelerating)");
        helper.succeed();
    }

    /**
     * easedLerp with an identity easing function (t -> t) must equal plain lerp.
     */
    public static void easedLerpIdentityMatchesPlainLerp(GameTestHelper helper) {
        float expected = MathUtil.lerp(0f, 20f, 0.3f);
        float actual = MathUtil.easedLerp(0f, 20f, 0.3f, t -> t);
        helper.assertTrue(actual == expected,
            "easedLerp with identity easing must equal lerp; expected " + expected + ", got " + actual);
        helper.succeed();
    }

    /**
     * easedLerp actually applies the easing function before interpolating, not just t itself.
     */
    public static void easedLerpAppliesEasingFunction(GameTestHelper helper) {
        float withEasing = MathUtil.easedLerp(0f, 10f, 0.25f, MathUtil::easeInOutQuad);
        float withoutEasing = MathUtil.lerp(0f, 10f, 0.25f);
        helper.assertTrue(withEasing != withoutEasing,
            "easedLerp must differ from plain lerp when a non-identity easing function is used");
        helper.assertTrue(withEasing == MathUtil.lerp(0f, 10f, MathUtil.easeInOutQuad(0.25f)),
            "easedLerp must equal lerp(start, end, easingFunction(t))");
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    /**
     * Utility.ft produces an Identifier namespaced to the mod ID, with the given path.
     */
    public static void utilityFtCreatesNamespacedIdentifier(GameTestHelper helper) {
        Identifier id = Utility.ft("foo");
        helper.assertTrue(id.getNamespace().equals("fishtastic"),
            "Namespace must be 'fishtastic', got " + id.getNamespace());
        helper.assertTrue(id.getPath().equals("foo"),
            "Path must be 'foo', got " + id.getPath());
        helper.succeed();
    }

    /**
     * Utility.interpolateColor at t=0/t=1 returns the start/end vectors exactly;
     * at t=0.5 returns the per-component midpoint.
     */
    public static void utilityInterpolateColorBoundaries(GameTestHelper helper) {
        Vector3f start = new Vector3f(0f, 10f, 20f);
        Vector3f end = new Vector3f(10f, 20f, 40f);

        Vector3f atStart = Utility.interpolateColor(start, end, 0f);
        helper.assertTrue(atStart.equals(start), "t=0 must return the start color exactly");

        Vector3f atEnd = Utility.interpolateColor(start, end, 1f);
        helper.assertTrue(atEnd.equals(end), "t=1 must return the end color exactly");

        Vector3f atMid = Utility.interpolateColor(start, end, 0.5f);
        helper.assertTrue(atMid.equals(new Vector3f(5f, 15f, 30f)),
            "t=0.5 must return the per-component midpoint, got " + atMid);
        helper.succeed();
    }
}
