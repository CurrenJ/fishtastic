package grill24.fishsim.companion;

import java.util.List;
import java.util.Set;

/**
 * The world around the owner as moods see it (docs/fish-companions-sim-plan.md §5 Phase 5, spec
 * §6): where they are, the weather, the time and the moon, and warm things nearby. One per tick,
 * shared by every companion; each fish reads it against its own species' {@link Habitat}, so a
 * mixed party can be split.
 *
 * <p>Every axis can be unknown, and {@link #NEUTRAL} is unknown on all of them: no mood at all,
 * which keeps every headless run without an ambient bitwise as it was.
 *
 * <p>Place tokens, shared with {@link Habitat#homes()}: {@code "zone:ocean"} for a fishtastic
 * zone, {@code "minecraft:plains"} for the biome itself, {@code "#minecraft:is_ocean"} for each
 * tag the biome is in, {@code "dim:minecraft:the_nether"} for the dimension.
 *
 * @param place       place tokens at the owner; empty when unknown
 * @param temperature the biome's temperature at the owner on Minecraft's scale (below 0.15 it
 *                    snows); NaN when unknown
 * @param weather     the weather at the owner, as fish profiles name it
 * @param dayPart     the part of the day, or null when unknown
 * @param moonPhase   0 (full) to 7 as in vanilla's cycle, or -1 when unknown
 * @param warmth      warm things near the owner (campfires, fire, lava), block centres
 */
public record Ambient(Set<String> place, float temperature, Weather weather, DayPart dayPart, int moonPhase,
                      List<Vec> warmth) {

    /** As fish profiles' {@code weather_weights} name it. SNOW is rain where it's cold enough to snow. */
    public enum Weather { CLEAR, RAIN, SNOW, THUNDER }

    /** As fish profiles' {@code time_weights} name it. The adapter maps day time with the game's boundaries. */
    public enum DayPart { DAWN, DAY, DUSK, NIGHT }

    public static final Ambient NEUTRAL = new Ambient(Set.of(), Float.NaN, Weather.CLEAR, null, -1, List.of());

    public Ambient {
        place = Set.copyOf(place);
        warmth = List.copyOf(warmth);
    }

    public boolean fullMoon() {
        return moonPhase == 0;
    }

    public Ambient withPlace(Set<String> place, float temperature) {
        return new Ambient(place, temperature, weather, dayPart, moonPhase, warmth);
    }

    public Ambient withWeather(Weather weather) {
        return new Ambient(place, temperature, weather, dayPart, moonPhase, warmth);
    }

    public Ambient withTime(DayPart dayPart, int moonPhase) {
        return new Ambient(place, temperature, weather, dayPart, moonPhase, warmth);
    }

    public Ambient withWarmth(List<Vec> warmth) {
        return new Ambient(place, temperature, weather, dayPart, moonPhase, warmth);
    }
}
