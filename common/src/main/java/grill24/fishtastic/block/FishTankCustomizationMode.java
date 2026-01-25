package grill24.fishtastic.block;

/**
 * Represents the different customization modes for fish tanks.
 * Players can cycle through these modes to change which visual element
 * they're currently customizing.
 */
public enum FishTankCustomizationMode {
    FRAME("Frame"),
    SAND("Sand"),
    GLASS("Glass");

    private final String displayName;

    FishTankCustomizationMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get the next mode in the cycle.
     */
    public FishTankCustomizationMode next() {
        FishTankCustomizationMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    /**
     * Get the previous mode in the cycle.
     */
    public FishTankCustomizationMode previous() {
        FishTankCustomizationMode[] values = values();
        return values[(this.ordinal() - 1 + values.length) % values.length];
    }
}
