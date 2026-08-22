package grill24.fishsim.domain;

import grill24.fishsim.core.SimMath;

/**
 * The volume fish are simulated within, expressed in tank-local coordinates:
 *
 * <ul>
 *   <li><b>lateral</b> (X) — perpendicular to the tank's facing; the fish's swim axis.</li>
 *   <li><b>vertical</b> (Y) — block units above the tank's item-position baseline.</li>
 *   <li><b>depth</b> (Z) — along the tank's facing; the front-to-back axis.</li>
 * </ul>
 *
 * {@link Box} is a single tank (an axis-aligned box); {@link VoxelDomain} is a locked multi-tank
 * aquarium (a rectilinear union at block resolution with a precomputed distance field — see
 * docs/fish-sim-engine-plan.md §2.2). The engine only ever talks through this interface; in
 * particular the soft wall avoidance and the hard backstop are domain callbacks, so the Box path
 * keeps the exact pre-extraction per-axis float math (bitwise parity) while voxel domains use a
 * field lookup.
 */
public interface FlockDomain {
    // Bounding box of the swim volume (interior — the shape inset is already applied).
    float minLateral();
    float maxLateral();
    float minVertical();
    float maxVertical();
    float minDepth();
    float maxDepth();

    /** Depth planes fish drift back toward — one per depth layer. */
    float[] layerDepths();

    /**
     * The straight-line run the size gate measures against, in blocks — a fish free-swims only
     * when this is at least {@code gateFactor × length}. For a box it's the box's lateral width;
     * for a voxel domain it is the longest horizontal run (either axis) in the occupancy grid.
     */
    float sizeGateRun();

    /** Whether the point lies inside the swimmable interior. */
    boolean contains(float l, float y, float d);

    /**
     * Writes the soft wall-avoidance desired-velocity contribution for this position into
     * {@code out[0..2]} (lateral, vertical, depth) — a unitless proximity direction the engine
     * scales by its wall-avoid speed. Zero in the open interior; ramps up inside the margin so
     * the fish decelerates and turns instead of bouncing.
     */
    void avoidance(float l, float y, float d, float margin, float marginVertical, float[] out);

    /**
     * The hard backstop: writes a position guaranteed inside the domain into {@code pos[0..2]},
     * given that {@code (prevL, prevY, prevD)} was inside. The soft avoidance must keep this a
     * no-op in practice — the invariant tests assert it never engages.
     */
    void constrain(float prevL, float prevY, float prevD, float[] pos);

    /**
     * Distance to the nearest side wall, for metrics (the near-wall calm invariant). The default
     * matches the pre-extraction measure — nearest lateral/depth bounding wall; voxel domains
     * override with their exact field distance so interior walls (the L-bend) count too.
     */
    default float wallDistance(float l, float y, float d) {
        return Math.min(
                Math.min(l - minLateral(), maxLateral() - l),
                Math.min(d - minDepth(), maxDepth() - d));
    }

    /** A single tank: an axis-aligned box in local (lateral, vertical, depth) space. */
    record Box(float halfExtent, float verticalHalf, float[] layerDepths) implements FlockDomain {
        @Override public float minLateral() { return -halfExtent; }
        @Override public float maxLateral() { return halfExtent; }
        @Override public float minVertical() { return -verticalHalf; }
        @Override public float maxVertical() { return verticalHalf; }
        @Override public float minDepth() { return -halfExtent; }
        @Override public float maxDepth() { return halfExtent; }
        @Override public float[] layerDepths() { return layerDepths; }
        @Override public float sizeGateRun() { return halfExtent * 2f; }

        @Override
        public boolean contains(float l, float y, float d) {
            return l >= minLateral() && l <= maxLateral()
                    && y >= minVertical() && y <= maxVertical()
                    && d >= minDepth() && d <= maxDepth();
        }

        // The exact pre-extraction per-axis soft-avoidance math — do not touch (bitwise parity).
        @Override
        public void avoidance(float l, float y, float d, float margin, float marginVertical, float[] out) {
            out[0] = axisAvoidance(l, minLateral(), maxLateral(), margin);
            out[1] = axisAvoidance(y, minVertical(), maxVertical(), marginVertical);
            out[2] = axisAvoidance(d, minDepth(), maxDepth(), margin);
        }

        /** Signed proximity to the nearer wall in [−1, 1]: positive near the low wall, negative near the high wall, 0 in the open. */
        private static float axisAvoidance(float pos, float min, float max, float margin) {
            float lo = min + margin;
            float hi = max - margin;
            if (pos < lo) return (lo - pos) / margin;
            if (pos > hi) return -(pos - hi) / margin;
            return 0f;
        }

        // The exact pre-extraction per-axis hard clamp — do not touch (bitwise parity).
        @Override
        public void constrain(float prevL, float prevY, float prevD, float[] pos) {
            pos[0] = SimMath.clamp(pos[0], minLateral(), maxLateral());
            pos[1] = SimMath.clamp(pos[1], minVertical(), maxVertical());
            pos[2] = SimMath.clamp(pos[2], minDepth(), maxDepth());
        }
    }
}
