package grill24.fishsim.companion;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Named mood presets (docs/fish-companions.md Phase 5): an ambient around the owner and
 * the habitat of the species feeling it. Each one sets off one mood at full strength in a plain ocean
 * fish (or the kind of fish that feels it). Shipped, because the mod's dev mood trigger reads them;
 * the harness applies them to headless runs through {@code MoodScenes}.
 */
public final class MoodPresets {

    private MoodPresets() {}

    /** A habitat and ambient; {@code warmAt} places one warm thing relative to the owner's first feet (or null). */
    public record Preset(String name, Habitat habitat, Ambient ambient, Vec warmAt) {

        /** The ambient with the warm thing placed relative to where the owner starts. */
        public Ambient ambientAt(Vec startFeet) {
            return warmAt == null ? ambient : ambient.withWarmth(List.of(startFeet.add(warmAt)));
        }
    }

    /** An ocean fish of a temperate climate with no time, weather or moon preferences. */
    public static final Habitat OCEAN = new Habitat(Set.of("zone:ocean"), Map.of(), Map.of(), 1f, Habitat.Climate.TEMPERATE);
    /** Out on dry land in a temperate biome: no mood at all for {@link #OCEAN}. */
    public static final Ambient PLAINS = new Ambient(Set.of("minecraft:plains"), 0.8f, Ambient.Weather.CLEAR,
            Ambient.DayPart.DAY, 3, List.of());

    public static final List<Preset> ALL = List.of(
            new Preset("cold", OCEAN, PLAINS.withPlace(Set.of("minecraft:snowy_plains"), 0f), null),
            // Cold, with a campfire 3.5 blocks in front of the owner's start: off to bask by it.
            new Preset("warm", OCEAN, PLAINS.withPlace(Set.of("minecraft:snowy_plains"), 0f), new Vec(0.5, 0.5, 3.5)),
            new Preset("glum", OCEAN, PLAINS.withPlace(Set.of("minecraft:desert"), 2f), null),
            new Preset("glee", new Habitat(Set.of("zone:ocean"), Map.of(Ambient.Weather.RAIN, 1.5f), Map.of(), 1f,
                    Habitat.Climate.TEMPERATE), PLAINS.withWeather(Ambient.Weather.RAIN), null),
            new Preset("sleepy", new Habitat(Set.of("zone:ocean"), Map.of(), Map.of(Ambient.DayPart.NIGHT, 1.5f), 1f,
                    Habitat.Climate.TEMPERATE), PLAINS, null),
            new Preset("moon", new Habitat(Set.of("zone:ocean"), Map.of(), Map.of(), 2f, Habitat.Climate.TEMPERATE),
                    PLAINS.withTime(Ambient.DayPart.NIGHT, 0), null));

    public static Preset named(String name) {
        for (Preset p : ALL) if (p.name.equals(name)) return p;
        throw new IllegalArgumentException("no mood preset " + name);
    }
}
