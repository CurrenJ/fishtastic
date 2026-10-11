package grill24.fishsim.companion;

/**
 * What a companion's pose should say about its state, for the animator (Phase 3) to layer over the
 * body's motion: the sim moves the fish, the hint colours how it is drawn (spec §3.1, §4). Never a
 * move of its own — a hint that needs the fish somewhere is a goal that swims it there first.
 */
public enum PoseHint {
    NONE,
    /** A rod in the hand: eager, watching the rod tip. */
    EXCITED,
    /** Bait in the hand: begging. */
    BEG,
    /** Nose to the thing it's inspecting (its own species in the hand, a dropped item). */
    NOSE,
    /** A name tag in the hand: head tilted. */
    TILT,
    /** Food in the hand: mild interest. */
    MILD,
    /** Watching something it can't go to (a percher eyeing an item on the ground). */
    WATCH,
    /** In cover from something it's wary of, keeping an eye on it. */
    HIDE,
    /** An anchored fish pulled into its burrow (spec §3: a hostile within ~3 blocks). */
    DUCK,
    /** Settled and drowsy: slow fins, a low droop. */
    SLEEPY,
    /** Facing down a hostile from in front of its owner, puffed up (aggressive). */
    PUFF,
    /** Snapping at the air: its owner was just hurt (aggressive). */
    BITE,
    /** Peeking out from cover at what scared it (skittish). */
    PEEK,
    /** Nuzzling its owner's cheek (docile). */
    NUZZLE,
    /** A small idle shiver (trembler). */
    TREMBLE
}
