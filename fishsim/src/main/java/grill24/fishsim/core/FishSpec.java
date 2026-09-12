package grill24.fishsim.core;

/**
 * Everything the engine needs to know about one fish — the adapter maps game data down to this
 * (rendered length via ItemSizeHelper × renderCalibration, locomotion class from the animation
 * config's render pose, the block entity's mirror flag) and the engine never learns where the
 * numbers came from.
 *
 * @param length  rendered body length in block units (the render scale; a flat 0.5 for unmeasured
 *                stacks — never 0, which would let an unmeasured species free-swim when it
 *                shouldn't). Feeds the size gate and nothing else.
 * @param locomotion how this creature moves, if at all. The engine applies the class's own size
 *                gate on top and demotes to {@link Locomotion#STATIC} on failure — for a free
 *                swimmer that is the familiar {@code longestRun >= gateFactor * length}.
 * @param mirrored the tank's per-slot random mirror flag — initial heading for swimmers, and the
 *                static mirror for fish that aren't simulated.
 * @param species opaque species id (the adapter passes the item's registry id) — fish of the same
 *                species school together and tolerate closeness; different species keep the wider
 *                cross-species separation and don't align/cohere with each other. Planar (voxel)
 *                model only; the single-tank binary model ignores it (bitwise parity).
 */
public record FishSpec(float length, Locomotion locomotion, boolean mirrored, int species) {
}
