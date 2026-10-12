package grill24.fishsim.companion;

/**
 * Something near the owner a companion might react to (docs/fish-companions-sim-plan.md §3.2).
 *
 * @param kind   what it is
 * @param id     stable while the thing exists (the entity id in game), so the mind can tell a cat
 *               that stayed from a new one and grow used to it
 * @param pos    its centre, or feet for PLAYER, blocks
 * @param vel    blocks/s
 * @param radius rough size, blocks
 */
public record PointOfInterest(Kind kind, int id, Vec pos, Vec vel, double radius) {

    public enum Kind {
        /** A hostile mob: wariness. */
        HOSTILE,
        /** A cat or ocelot: wariness, after a dwell (spec §6). */
        CAT,
        /** An item on the ground: curiosity. */
        DROPPED_ITEM,
        /** Another player's companion: curiosity. */
        COMPANION,
        /** Another player's feet: ground avoidance only, never curiosity or fear. */
        PLAYER
    }
}
