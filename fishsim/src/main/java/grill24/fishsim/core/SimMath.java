package grill24.fishsim.core;

/**
 * Float math helpers ported from Minecraft's {@code net.minecraft.util.Mth} with <b>identical
 * float semantics</b> — the bitwise-parity tests (see docs/fish-sim-engine-handoff.md Task 3)
 * depend on every operation here matching vanilla's exactly. Do not "clean up" these expressions:
 * reassociation, {@code Math.fma}, or branch simplification can change the produced bits.
 */
public final class SimMath {

    private SimMath() {}

    /** Port of {@code Mth.clamp(float, float, float)}: {@code value < min ? min : Math.min(value, max)}. */
    public static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    /** Port of {@code Mth.lerp(float, float, float)}: {@code p0 + alpha * (p1 - p0)}. */
    public static float lerp(float alpha, float p0, float p1) {
        return p0 + alpha * (p1 - p0);
    }
}
