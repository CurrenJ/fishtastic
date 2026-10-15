package grill24.fishsim.harness.companion;

import grill24.fishsim.companion.*;
import java.util.*;

/** Shared fixtures for the viewer, headless films and the guard probes. U cycles these in the viewer. */
public final class WorldScenes {
    private WorldScenes() {}
    public static final List<String> NAMES = List.of("water", "light", "jukebox", "notes", "warm", "cold", "glum", "sleepy", "glee", "homecoming", "steam");
    public static CompanionWorld apply(CompanionWorld world, String name, Vec anchor, long tick) {
        if (name == null) return world;
        Vec source = anchor.add(0.7, 0, 2.5);
        Ambient ambient = MoodScenes.PLAINS;
        List<WorldFeature> features = switch(name) {
            case "water" -> List.of(WorldFeature.water(1,source,"ocean"));
            case "light" -> List.of(WorldFeature.light(2,source.add(0,0.8,0),14));
            case "jukebox" -> List.of(WorldFeature.music(3,source,new WorldFeature.Music(WorldFeature.Source.JUKEBOX,0,2400,0,0)));
            case "notes" -> List.of(WorldFeature.music(4,source,new WorldFeature.Music(WorldFeature.Source.NOTE_BLOCK,tick-tick%16,12,tick-tick%16,(tick/16%25)/24f)));
            case "warm" -> List.of(WorldFeature.warmth(5,source.add(0,0.5,1)));
            default -> List.of();
        };
        if (name.equals("light")) ambient = ambient.withTime(Ambient.DayPart.NIGHT,3).withDarkness(0.9f);
        if (name.equals("warm") || name.equals("cold")) ambient = ambient.withPlace(Set.of("minecraft:snowy_plains"),0).withWeather(Ambient.Weather.SNOW);
        if (name.equals("glum") || name.equals("steam")) ambient = ambient.withPlace(Set.of(name.equals("steam") ? "zone:nether" : "minecraft:desert"),2);
        if (name.equals("sleepy")) ambient = ambient.withTime(Ambient.DayPart.NIGHT,3);
        if (name.equals("glee")) ambient = ambient.withWeather(Ambient.Weather.RAIN);
        if (name.equals("homecoming") && tick >= 30) ambient = ambient.withPlace(Set.of("zone:ocean"),0.8f);
        return world.withAmbient(ambient).withFeatures(features).withGameTick(tick);
    }
    public static Habitat habitat(String scene) {
        return new Habitat(Set.of("zone:ocean"),Map.of(),scene.equals("sleepy") ? Map.of(Ambient.DayPart.DAY,1.5f) : Map.of(),1,Habitat.Climate.TEMPERATE);
    }
}
