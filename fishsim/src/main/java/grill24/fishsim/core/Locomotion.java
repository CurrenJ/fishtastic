package grill24.fishsim.core;

/**
 * How a creature moves through the tank volume — deliberately separate from how its sprite is
 * posed and idle-animated, which is the Minecraft side's business alone (see
 * docs/fish-sim-locomotion.md §2). The two used to be one enum on the MC side
 * ({@code FishAnimationConfig.mode}), which is why anything that was not a horizontal swimmer
 * could not be simulated at all: the engine's only question was "is this a horizontal swimmer",
 * and every other pose answered no.
 *
 * <p>The adapter maps each render pose to a default class, so no data file has to say anything;
 * a pose and a locomotion are independent choices (a flounder is a horizontal-swim pose with
 * {@link #BENTHIC} locomotion).
 *
 * <p><b>Status.</b> {@link #FREE_SWIM} (the existing, bitwise-locked flocking code),
 * {@link #BENTHIC} (the floor walk) and {@link #DRIFT} (the pulse-and-sink) have motion models today. The rest are declared and plumbed
 * but not yet stepped, so they hold their scatter position exactly as they always have; each
 * gains its model in its own phase (docs/fish-sim-locomotion.md §5). {@link FlockEngine#step}
 * is the single place that decides.
 */
public enum Locomotion {

    /**
     * Free-swimming flocker: the full boid model, the size gate, and the animation coupling.
     * The single-tank binary 2.5D path and the planar voxel-domain path both live here and are
     * locked by {@code GoldenTrajectoryTest} / {@code ParityTest}.
     */
    FREE_SWIM,

    /**
     * Large, slow, solitary glider — rays and the like. A parameter set over the free-swim planar
     * model rather than a distinct integrator: hard turn-rate cap, wide separation, near-zero
     * alignment and cohesion, a floor-hugging vertical bias, and engine-driven banking.
     */
    GLIDE,

    /**
     * Passive drifter — jellyfish and their kin. Essentially no forward drive; horizontal motion
     * is band-limited wander (reads as being carried by a current) and the vertical axis carries a
     * pulse-and-sink cycle. Never aligns; coheres weakly with its own species.
     */
    DRIFT,

    /**
     * Floor-walker — crabs, octopus, nudibranchs, starfish. A 2D walk constrained to the domain's
     * floor surface, with a footprint-sized separation radius and a stop-and-turn dwell cycle.
     * Needs the domain floor field, which is why it is the first phase with real prerequisites.
     */
    BENTHIC,

    /**
     * Fixed to a floor footprint chosen at rebuild — garden eels. Sways in place and retracts when
     * something large passes, but its footprint never moves for the life of the fish.
     */
    ANCHORED,

    /**
     * Not simulated: holds the position the rebuild scattered it to and is animated open-loop by
     * the renderer against game time. This is both the explicit "creature that does not move"
     * choice and the state any other class is demoted to when it fails its size gate — one state
     * rather than the two overlapping ones the {@code canSwim} boolean used to produce.
     */
    STATIC;

    /** Whether {@link FlockEngine#step} has a motion model for this class today. */
    public boolean simulated() {
        return this == FREE_SWIM || this == BENTHIC || this == DRIFT;
    }
}
