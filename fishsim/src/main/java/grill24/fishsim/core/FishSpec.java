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
 * @param shelterUse how this fish uses shelters (docs/fish-shelters.md) — {@link ShelterUse#NONE}
 *                unless its species opts in. Planar model only.
 * @param speedScale how fast this fish swims relative to its tunable set (docs/ambient-fish-schools-design-spec.md
 *                §5.2): multiplies its patrol speed, wander drive and speed ceiling. 1 (every
 *                constructor without it) leaves the motion bit-identical. Clamped to
 *                [{@link #MIN_SPEED_SCALE}, {@link #MAX_SPEED_SCALE}].
 */
public record FishSpec(float length, Locomotion locomotion, boolean mirrored, int species,
                       ShelterUse shelterUse, float speedScale) {

    public static final float MIN_SPEED_SCALE = 0.4f;
    public static final float MAX_SPEED_SCALE = 2.5f;

    public FishSpec {
        if (!(speedScale > 0f)) speedScale = 1f;
        speedScale = Math.max(MIN_SPEED_SCALE, Math.min(MAX_SPEED_SCALE, speedScale));
    }

    public FishSpec(float length, Locomotion locomotion, boolean mirrored, int species, ShelterUse shelterUse) {
        this(length, locomotion, mirrored, species, shelterUse, 1f);
    }

    /** A fish that never uses shelters — every species that doesn't opt in. */
    public FishSpec(float length, Locomotion locomotion, boolean mirrored, int species) {
        this(length, locomotion, mirrored, species, ShelterUse.NONE, 1f);
    }

    /** This fish with its species' shelter behaviour. */
    public FishSpec withShelterUse(ShelterUse use) {
        return new FishSpec(length, locomotion, mirrored, species, use, speedScale);
    }

    /** This fish at another speed scale. */
    public FishSpec withSpeedScale(float scale) {
        return new FishSpec(length, locomotion, mirrored, species, shelterUse, scale);
    }
}
