package grill24.fishtastic.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.FishtasticRegistries;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.biome.Biome;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public record FishProfile(
        int baseWeight,
        SizeParams size,
        List<BiomeWeight> biomeWeights,
        List<TimeWeight> timeWeights,
        List<WeatherWeight> weatherWeights,
        Optional<ResourceKey<Temperament>> temperament,
        Optional<FishAnimationConfig> animation
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
            ResourceKey.codec(FishtasticRegistries.TEMPERAMENT_REGISTRY_KEY).optionalFieldOf("temperament").forGetter(FishProfile::temperament),
            FishAnimationConfig.CODEC.optionalFieldOf("animation").forGetter(FishProfile::animation)
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
        CLEAR, RAIN, THUNDER;

        public static final Codec<WeatherCondition> CODEC = StringRepresentable.fromEnum(WeatherCondition::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public record BiomeWeight(TagKey<Biome> biome, float multiplier) {
        public static final Codec<BiomeWeight> CODEC = RecordCodecBuilder.create(i -> i.group(
                TagKey.codec(Registries.BIOME).fieldOf("biome").forGetter(BiomeWeight::biome),
                Codec.FLOAT.fieldOf("multiplier").forGetter(BiomeWeight::multiplier)
        ).apply(i, BiomeWeight::new));
    }

    public record TimeWeight(TimeOfDay time, float multiplier) {
        public static final Codec<TimeWeight> CODEC = RecordCodecBuilder.create(i -> i.group(
                TimeOfDay.CODEC.fieldOf("time").forGetter(TimeWeight::time),
                Codec.FLOAT.fieldOf("multiplier").forGetter(TimeWeight::multiplier)
        ).apply(i, TimeWeight::new));
    }

    public record WeatherWeight(WeatherCondition weather, float multiplier) {
        public static final Codec<WeatherWeight> CODEC = RecordCodecBuilder.create(i -> i.group(
                WeatherCondition.CODEC.fieldOf("weather").forGetter(WeatherWeight::weather),
                Codec.FLOAT.fieldOf("multiplier").forGetter(WeatherWeight::multiplier)
        ).apply(i, WeatherWeight::new));
    }

    /**
     * Computes the weight multiplier for this fish given current environmental conditions.
     */
    public float computeEnvironmentMultiplier(Holder<Biome> biome, TimeOfDay timeOfDay, WeatherCondition weather) {
        float multiplier = 1.0f;

        for (BiomeWeight bw : biomeWeights) {
            if (biome.is(bw.biome())) multiplier *= bw.multiplier();
        }

        for (TimeWeight tw : timeWeights) {
            if (tw.time() == timeOfDay) { multiplier *= tw.multiplier(); break; }
        }

        for (WeatherWeight ww : weatherWeights) {
            if (ww.weather() == weather) { multiplier *= ww.multiplier(); break; }
        }

        return multiplier;
    }
}
