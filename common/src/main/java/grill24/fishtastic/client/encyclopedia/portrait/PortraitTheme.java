package grill24.fishtastic.client.encyclopedia.portrait;

import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.util.Ids;
import net.minecraft.resources.Identifier;

/**
 * Per-zone look of the Fishopedia portrait plate (docs/encyclopedia-redesign/bubbles/PLAN.md section 3).
 * Every number is copied from {@code THEMES} in {@code prototype_bubbles.py}.
 */
public enum PortraitTheme {
    //            plate         rate  rise  drift fizz  snow  embers  micro-bubble colours (0xRRGGBB)
    OCEAN("ocean",              1.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, new int[]{0xEBF5FF, 0xAAD7FF}),
    RIVER("river",              1.0f, 1.0f, 3.5f, 1.0f, 0.0f, 0.0f, new int[]{0xEBFAF0, 0xB4E6D7}),
    DEEP_OCEAN("deep_ocean",    0.4f, 0.7f, 0.0f, 1.0f, 0.9f, 0.0f, new int[]{0x96BEE6}),
    CAVE("cave",                0.6f, 0.85f, 0.0f, 1.0f, 0.0f, 0.0f, new int[]{0x78AAFF, 0x5A8CE6}),
    HIGH_ALTITUDE("high_altitude", 1.0f, 1.1f, 1.5f, 2.2f, 0.0f, 0.0f, new int[]{0xFAFCFF}),
    NETHER("nether",            0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 3.0f, new int[]{});

    public final String zoneName;
    /** Spawn-rate multiplier for micro/small/medium bubbles and plumes. */
    public final float rate;
    /** Rise-speed multiplier. */
    public final float rise;
    /** Horizontal drift in px/s (river current, altitude breeze). */
    public final float drift;
    /** Extra multiplier on micro-bubble spawn rate only. */
    public final float fizz;
    /** Marine snow spawns per second (0 = none). */
    public final float snow;
    /** Ember spawns per second (0 = none); nether only. */
    public final float embers;
    public final int[] microColours;
    public final Identifier plateTexture;

    PortraitTheme(String zoneName, float rate, float rise, float drift, float fizz, float snow, float embers, int[] microColours) {
        this.zoneName = zoneName;
        this.rate = rate;
        this.rise = rise;
        this.drift = drift;
        this.fizz = fizz;
        this.snow = snow;
        this.embers = embers;
        this.microColours = microColours;
        this.plateTexture = Ids.of("fishtastic", "textures/gui/encyclopedia/plate_" + zoneName + ".png");
    }

    /** True for the lava theme: sparks instead of bubbles, and a different click sound. */
    public boolean isLava() {
        return embers > 0f;
    }

    public static PortraitTheme forZone(FishProfile.Zone zone) {
        if (zone == null) return OCEAN;
        return switch (zone) {
            case OCEAN -> OCEAN;
            case RIVER -> RIVER;
            case DEEP_OCEAN -> DEEP_OCEAN;
            case CAVE -> CAVE;
            case HIGH_ALTITUDE -> HIGH_ALTITUDE;
            case NETHER -> NETHER;
        };
    }
}
