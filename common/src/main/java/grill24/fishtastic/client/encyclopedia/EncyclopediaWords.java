package grill24.fishtastic.client.encyclopedia;

import grill24.fishtastic.data.FishProfile;

import java.util.Locale;

/**
 * Plain-word names for the raw odds multipliers on a fish profile, so the page never shows an
 * "x1.8". Pure: no Minecraft client singletons, unit-tested in {@code EncyclopediaWordsTest}.
 *
 * <p>The bands sit in the gaps of the shipped data (docs/encyclopedia-redesign/README.md, "Plain-word
 * odds"): boosts ship at 1.2-1.4, 1.5-1.7 and 1.8-2.2, penalties at 0.5-0.6 and 0.1-0.4, so a
 * band edge never lands on a real value.
 */
public final class EncyclopediaWords {
    private EncyclopediaWords() {}

    /** How strongly one condition moves a fish's odds, in words. */
    public enum Effect {
        GREAT, GOOD, SLIGHT, POOR, BAD;

        /** Lang key of the word, e.g. {@code screen.fishtastic.encyclopedia.effect.great}. */
        public String langKey() {
            return "screen.fishtastic.encyclopedia.effect." + name().toLowerCase(Locale.ROOT);
        }

        /** True when the condition raises the odds. */
        public boolean helps() {
            return this == GREAT || this == GOOD || this == SLIGHT;
        }
    }

    /** The overall verdict on the player's current spot for one fish. */
    public enum Spot {
        GREAT, GOOD, FAIR, POOR;

        /** Lang key of the verdict word, e.g. {@code screen.fishtastic.encyclopedia.verdict.great}. */
        public String langKey() {
            return "screen.fishtastic.encyclopedia.verdict." + name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Word for one condition's multiplier. A SECONDARY condition is a flat bonus (never a
     * multiplier), so a boost there is always only slight whatever its number.
     */
    public static Effect effect(float multiplier, FishProfile.ConditionTier tier) {
        if (tier == FishProfile.ConditionTier.SECONDARY && multiplier > 1.0f) return Effect.SLIGHT;
        if (multiplier >= 1.75f) return Effect.GREAT;
        if (multiplier >= 1.45f) return Effect.GOOD;
        if (multiplier > 1.0f) return Effect.SLIGHT;
        if (multiplier >= 0.45f) return Effect.POOR;
        return Effect.BAD;
    }

    /** Verdict for the combined environment multiplier ({@link FishProfile#computeEnvironmentMultiplier}). */
    public static Spot spot(float combined) {
        if (combined >= 1.75f) return Spot.GREAT;
        if (combined >= 1.2f) return Spot.GOOD;
        if (combined > 0.8f) return Spot.FAIR;
        return Spot.POOR;
    }
}
