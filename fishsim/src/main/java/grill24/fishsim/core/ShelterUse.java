package grill24.fishsim.core;

/**
 * How a species uses shelters (docs/fish-shelters.md §3.3). An explicit per-species authoring
 * choice made on the host side; the engine never derives it.
 */
public enum ShelterUse {
    /** Never uses shelters — the default. */
    NONE,
    /** Occasional unhurried visits: in, linger, out. */
    VISITOR,
    /** Visits occasionally, and bolts for cover when the watcher approaches. */
    SKITTISH,
    /** Claims one shelter as home and rests in its mouth; makes short sorties. */
    LURKER
}
