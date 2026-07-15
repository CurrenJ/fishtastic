package grill24.fishtastic.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.FishtasticRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.biome.Biome;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

public record FishProfile(
        int baseWeight,
        SizeParams size,
        List<BiomeWeight> biomeWeights,
        List<TimeWeight> timeWeights,
        List<WeatherWeight> weatherWeights,
        List<MoonWeight> moonWeights,
        List<ElevationWeight> elevationWeights,
        Optional<ResourceKey<Temperament>> temperament,
        Optional<FishAnimationConfig> animation,
        SwarmConfig swarm
) {
    public static final int DEFAULT_BASE_WEIGHT = 10;
    public static final float DEFAULT_MEAN_SIZE = 50.0f;
    public static final float DEFAULT_STDDEV_SIZE = 15.0f;

    public static final Codec<FishProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("base_weight", DEFAULT_BASE_WEIGHT).forGetter(FishProfile::baseWeight),
            SizeParams.CODEC.optionalFieldOf("size", SizeParams.DEFAULT).forGetter(FishProfile::size),
            BiomeWeight.CODEC.listOf().optionalFieldOf("biome_weights", List.of()).forGetter(FishProfile::biomeWeights),
            TimeWeight.CODEC.listOf().optionalFieldOf("time_weights", List.of()).forGetter(FishProfile::timeWeights),
            WeatherWeight.CODEC.listOf().optionalFieldOf("weather_weights", List.of()).forGetter(FishProfile::weatherWeights),
            MoonWeight.CODEC.listOf().optionalFieldOf("moon_weights", List.of()).forGetter(FishProfile::moonWeights),
            ElevationWeight.CODEC.listOf().optionalFieldOf("elevation_weights", List.of()).forGetter(FishProfile::elevationWeights),
            ResourceKey.codec(FishtasticRegistries.TEMPERAMENT_REGISTRY_KEY).optionalFieldOf("temperament").forGetter(FishProfile::temperament),
            FishAnimationConfig.CODEC.optionalFieldOf("animation").forGetter(FishProfile::animation),
            SwarmConfig.CODEC.optionalFieldOf("swarm", SwarmConfig.DEFAULT).forGetter(FishProfile::swarm)
    ).apply(i, FishProfile::new));

    public record SizeParams(float mean, float stdDev) {
        public static final SizeParams DEFAULT = new SizeParams(DEFAULT_MEAN_SIZE, DEFAULT_STDDEV_SIZE);

        public static final Codec<SizeParams> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("mean").forGetter(SizeParams::mean),
                Codec.FLOAT.fieldOf("std_dev").forGetter(SizeParams::stdDev)
        ).apply(i, SizeParams::new));
    }

    public enum TimeOfDay implements StringRepresentable {
        DAWN, DAY, DUSK, NIGHT;

        public static final Codec<TimeOfDay> CODEC = StringRepresentable.fromEnum(TimeOfDay::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static TimeOfDay fromGameTime(long dayTime) {
            long t = dayTime % 24000;
            if (t < 2400) return DAWN;
            if (t < 12000) return DAY;
            if (t < 14000) return DUSK;
            return NIGHT;
        }
    }

    public enum WeatherCondition implements StringRepresentable {
        CLEAR, RAIN, SNOW, THUNDER;

        public static final Codec<WeatherCondition> CODEC = StringRepresentable.fromEnum(WeatherCondition::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        // SNOW requires both active precipitation and a cold-enough biome at pos, not just one or the other.
        public static WeatherCondition fromLevel(Level level, BlockPos pos) {
            if (level.isThundering()) return THUNDER;
            if (level.isRaining()) {
                return level.precipitationAt(pos) == Biome.Precipitation.SNOW ? SNOW : RAIN;
            }
            return CLEAR;
        }
    }

    /**
     * Primary conditions multiply into the weight as before (the "combo hunting" play).
     * Secondary conditions add a flat percentage instead, so stacking many flavor
     * conditions nudges the weight rather than compounding into absurd totals.
     */
    public enum ConditionTier implements StringRepresentable {
        PRIMARY, SECONDARY;

        public static final Codec<ConditionTier> CODEC = StringRepresentable.fromEnum(ConditionTier::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public record BiomeWeight(TagKey<Biome> biome, float multiplier, ConditionTier tier) {
        public static final Codec<BiomeWeight> CODEC = RecordCodecBuilder.create(i -> i.group(
                TagKey.codec(Registries.BIOME).fieldOf("biome").forGetter(BiomeWeight::biome),
                Codec.FLOAT.fieldOf("multiplier").forGetter(BiomeWeight::multiplier),
                ConditionTier.CODEC.optionalFieldOf("tier", ConditionTier.PRIMARY).forGetter(BiomeWeight::tier)
        ).apply(i, BiomeWeight::new));
    }

    public record TimeWeight(TimeOfDay time, float multiplier, ConditionTier tier) {
        public static final Codec<TimeWeight> CODEC = RecordCodecBuilder.create(i -> i.group(
                TimeOfDay.CODEC.fieldOf("time").forGetter(TimeWeight::time),
                Codec.FLOAT.fieldOf("multiplier").forGetter(TimeWeight::multiplier),
                ConditionTier.CODEC.optionalFieldOf("tier", ConditionTier.PRIMARY).forGetter(TimeWeight::tier)
        ).apply(i, TimeWeight::new));
    }

    public record WeatherWeight(WeatherCondition weather, float multiplier, ConditionTier tier) {
        public static final Codec<WeatherWeight> CODEC = RecordCodecBuilder.create(i -> i.group(
                WeatherCondition.CODEC.fieldOf("weather").forGetter(WeatherWeight::weather),
                Codec.FLOAT.fieldOf("multiplier").forGetter(WeatherWeight::multiplier),
                ConditionTier.CODEC.optionalFieldOf("tier", ConditionTier.PRIMARY).forGetter(WeatherWeight::tier)
        ).apply(i, WeatherWeight::new));
    }

    public enum Elevation implements StringRepresentable {
        DEEP, SURFACE, HIGH;

        // Sea level defaults to 63, so this is roughly y<33 (underground pools/ravine bottoms)
        // and y>93 (mountain lakes/rivers) — ordinary ocean/river fishing stays SURFACE.
        private static final int BAND_OFFSET = 30;

        public static final Codec<Elevation> CODEC = StringRepresentable.fromEnum(Elevation::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Elevation fromY(int y, int seaLevel) {
            if (y < seaLevel - BAND_OFFSET) return DEEP;
            if (y > seaLevel + BAND_OFFSET) return HIGH;
            return SURFACE;
        }
    }

    public record MoonWeight(MoonPhase phase, float multiplier, ConditionTier tier) {
        public static final Codec<MoonWeight> CODEC = RecordCodecBuilder.create(i -> i.group(
                MoonPhase.CODEC.fieldOf("phase").forGetter(MoonWeight::phase),
                Codec.FLOAT.fieldOf("multiplier").forGetter(MoonWeight::multiplier),
                ConditionTier.CODEC.optionalFieldOf("tier", ConditionTier.PRIMARY).forGetter(MoonWeight::tier)
        ).apply(i, MoonWeight::new));
    }

    public record ElevationWeight(Elevation band, float multiplier, ConditionTier tier) {
        public static final Codec<ElevationWeight> CODEC = RecordCodecBuilder.create(i -> i.group(
                Elevation.CODEC.fieldOf("band").forGetter(ElevationWeight::band),
                Codec.FLOAT.fieldOf("multiplier").forGetter(ElevationWeight::multiplier),
                ConditionTier.CODEC.optionalFieldOf("tier", ConditionTier.PRIMARY).forGetter(ElevationWeight::tier)
        ).apply(i, ElevationWeight::new));
    }

    /**
     * Computes the weight multiplier for this fish given current environmental conditions.
     * Each axis contributes at most one matching entry per tier: PRIMARY entries multiply
     * together (the "combo hunting" play), SECONDARY entries add flat percentages so that
     * stacking several flavor conditions nudges the weight instead of compounding it.
     */
    public float computeEnvironmentMultiplier(Holder<Biome> biome, TimeOfDay timeOfDay, WeatherCondition weather,
                                               MoonPhase moonPhase, Elevation elevation) {
        boolean moonVisible = timeOfDay == TimeOfDay.NIGHT && weather == WeatherCondition.CLEAR;

        float primaryProduct = 1.0f;
        primaryProduct *= firstMatch(biomeWeights, ConditionTier.PRIMARY, BiomeWeight::tier, bw -> biome.is(bw.biome()), BiomeWeight::multiplier);
        primaryProduct *= firstMatch(timeWeights, ConditionTier.PRIMARY, TimeWeight::tier, tw -> tw.time() == timeOfDay, TimeWeight::multiplier);
        primaryProduct *= firstMatch(weatherWeights, ConditionTier.PRIMARY, WeatherWeight::tier, ww -> ww.weather() == weather, WeatherWeight::multiplier);
        if (moonVisible) {
            primaryProduct *= firstMatch(moonWeights, ConditionTier.PRIMARY, MoonWeight::tier, mw -> mw.phase() == moonPhase, MoonWeight::multiplier);
        }
        primaryProduct *= firstMatch(elevationWeights, ConditionTier.PRIMARY, ElevationWeight::tier, ew -> ew.band() == elevation, ElevationWeight::multiplier);

        float secondaryBonus = 0.0f;
        secondaryBonus += firstMatch(biomeWeights, ConditionTier.SECONDARY, BiomeWeight::tier, bw -> biome.is(bw.biome()), BiomeWeight::multiplier) - 1.0f;
        secondaryBonus += firstMatch(timeWeights, ConditionTier.SECONDARY, TimeWeight::tier, tw -> tw.time() == timeOfDay, TimeWeight::multiplier) - 1.0f;
        secondaryBonus += firstMatch(weatherWeights, ConditionTier.SECONDARY, WeatherWeight::tier, ww -> ww.weather() == weather, WeatherWeight::multiplier) - 1.0f;
        if (moonVisible) {
            secondaryBonus += firstMatch(moonWeights, ConditionTier.SECONDARY, MoonWeight::tier, mw -> mw.phase() == moonPhase, MoonWeight::multiplier) - 1.0f;
        }
        secondaryBonus += firstMatch(elevationWeights, ConditionTier.SECONDARY, ElevationWeight::tier, ew -> ew.band() == elevation, ElevationWeight::multiplier) - 1.0f;

        return primaryProduct * (1.0f + secondaryBonus);
    }

    /**
     * Finds the first entry of the given tier matching {@code condition} and returns its
     * multiplier, or {@code 1.0} if none match. Only one entry per axis+tier ever applies —
     * entries aren't meant to stack against each other within the same tier.
     */
    private static <T> float firstMatch(List<T> weights, ConditionTier tier, Function<T, ConditionTier> tierGetter,
                                         Predicate<T> condition, ToDoubleFunction<T> multiplier) {
        for (T w : weights) {
            if (tierGetter.apply(w) == tier && condition.test(w)) return (float) multiplier.applyAsDouble(w);
        }
        return 1.0f;
    }
}
