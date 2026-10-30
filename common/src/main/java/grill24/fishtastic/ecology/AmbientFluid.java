package grill24.fishtastic.ecology;

/**
 * The fluid ambient fish live in. Water everywhere except the Nether, where the natural pool as
 * shipped (nether species) swims in lava: a deliberate easter egg for players with fire resistance
 * (owner decision 2026-10-30). Pure, so the headless tests can pin the rule; the client maps it to a
 * fluid tag.
 */
public enum AmbientFluid {
    WATER, LAVA;

    public static final String NETHER = "minecraft:the_nether";

    /** The fluid for a dimension id such as {@code minecraft:the_nether}. */
    public static AmbientFluid forDimension(String dimensionId) {
        return NETHER.equals(dimensionId) ? LAVA : WATER;
    }
}
