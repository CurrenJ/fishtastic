package grill24.fishsim.core;

/**
 * Everything the engine needs to know about one fish — the adapter maps game data down to this
 * (rendered length via ItemSizeHelper × renderCalibration, swim capability from the animation
 * config, the block entity's mirror flag) and the engine never learns where the numbers came from.
 *
 * @param length  rendered body length in block units (the render scale; a flat 0.5 for unmeasured
 *                stacks — never 0, which would let an unmeasured species free-swim when it
 *                shouldn't). Feeds the size gate and nothing else.
 * @param canSwim whether this species is a free-swimming horizontal swimmer at all (not
 *                floor-anchored, animation mode is horizontal swim). The engine still applies the
 *                size gate on top: {@code swims = canSwim && longestRun >= gateFactor * length}.
 * @param mirrored the tank's per-slot random mirror flag — initial heading for swimmers, and the
 *                static mirror for hover-path (gate-failing) fish.
 * @param species opaque species id (the adapter passes the item's registry id) — fish of the same
 *                species school together and tolerate closeness; different species keep the wider
 *                cross-species separation and don't align/cohere with each other. Planar (voxel)
 *                model only; the single-tank binary model ignores it (bitwise parity).
 */
public record FishSpec(float length, boolean canSwim, boolean mirrored, int species) {
}
