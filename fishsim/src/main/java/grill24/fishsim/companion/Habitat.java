package grill24.fishsim.companion;

import java.util.Map;
import java.util.Set;

/**
 * What one species likes, read from its fish profile by the adapter (docs/fish-companions-sim-plan.md
 * §5 Phase 5, spec §6 "moods respect the species' home"). The raw preferences, uninterpreted: the
 * mood rules decide what an unfamiliar place or a disliked weather does.
 *
 * <p>{@link #NONE} has no preferences at all, so it never moves a mood.
 *
 * @param homes    place tokens (see {@link Ambient}) where the species lives: its zones and its
 *                 {@code biome_weights} biomes and tags
 * @param weather  {@code weather_weights} multipliers; a missing weather is 1, indifferent
 * @param hours    {@code time_weights} multipliers; a missing part of the day is 1
 * @param fullMoon the full-moon {@code moon_weights} multiplier, 1 when it has none
 * @param climate  the climate of its homes, which the adapter reads off their biomes' temperatures
 */
public record Habitat(Set<String> homes, Map<Ambient.Weather, Float> weather, Map<Ambient.DayPart, Float> hours,
                      float fullMoon, Climate climate) {

    /**
     * Where a species is comfortable away from home (spec §6 "cold-water fish are fine in snow").
     * TEMPERATE shivers in snow and wilts in the heat; COLD is fine in snow and wilts in the heat;
     * HOT (the Nether's) is glum anywhere that isn't hot and shivers in snow.
     */
    public enum Climate { TEMPERATE, COLD, HOT }

    public static final Habitat NONE = new Habitat(Set.of(), Map.of(), Map.of(), 1f, Climate.TEMPERATE);

    public Habitat {
        if (climate == null) climate = Climate.TEMPERATE;
        homes = Set.copyOf(homes);
        weather = Map.copyOf(weather);
        hours = Map.copyOf(hours);
    }

    public boolean ownWater(String zone) {
        String token = zone.startsWith("zone:") ? zone : "zone:" + zone.toLowerCase(java.util.Locale.ROOT);
        return homes.contains(token) || token.equals("zone:deep_ocean") && homes.contains("zone:ocean");
    }

    public boolean nightActive() { return hours(Ambient.DayPart.NIGHT) > 1; }

    public float weather(Ambient.Weather w) {
        return weather.getOrDefault(w, 1f);
    }

    public float hours(Ambient.DayPart part) {
        return hours.getOrDefault(part, 1f);
    }

    /** True when any of the place's tokens is one of this species' homes. */
    public boolean atHome(Ambient ambient) {
        for (String token : ambient.place()) if (homes.contains(token)) return true;
        return false;
    }
}
