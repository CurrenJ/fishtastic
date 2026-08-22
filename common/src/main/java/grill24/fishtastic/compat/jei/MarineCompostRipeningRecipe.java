package grill24.fishtastic.compat.jei;

import grill24.fishtastic.component.FishQuality;

/**
 * Not a real {@code Recipe} — {@link grill24.fishtastic.blockentity.MarineCompostBlockEntity}
 * ripens marine compost into worms purely through block-entity ticking, with no recipe JSON
 * behind it. This is a plain data holder JEI displays one row per {@link FishQuality.Quality}
 * tier, mirroring {@code MarineCompostBlockEntity#computeYield} (aeration bonus omitted — it's
 * the same +1/turn across every tier, called out in the category tooltip instead).
 */
public record MarineCompostRipeningRecipe(FishQuality.Quality quality, int worms) {
    public static MarineCompostRipeningRecipe of(FishQuality.Quality quality) {
        int worms = switch (quality) {
            case COMMON -> 1;
            case UNCOMMON -> 3;
            case RARE -> 6;
            case EPIC -> 12;
            case LEGENDARY -> 25;
        };
        return new MarineCompostRipeningRecipe(quality, worms);
    }
}
