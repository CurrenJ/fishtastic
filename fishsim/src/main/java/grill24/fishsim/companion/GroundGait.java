package grill24.fishsim.companion;

/**
 * How a benthic companion gets about on the ground (docs/fish-companions-benthic-follow.md §4).
 */
public enum GroundGait {
    /** Legs or tube feet: crabs, octopus, starfish, sea slugs. Scuttle and pause, a crouch before a hop. */
    CRAWL,
    /**
     * A flatfish (plaice): it doesn't walk, it glides flat on the floor, driven by a ripple running
     * nose to tail. Smoother and steadier than a crawl, no stop-and-turn crouch.
     */
    RIPPLE
}
