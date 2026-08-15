package grill24.fishtastic.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticBiomeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.biome.Biome;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

public record FishProfile(
        int baseWeight,
        SizeParams size,
        List<BiomeWeight> biomeWeights,
        List<TimeWeight> timeWeights,
        List<WeatherWeight> weatherWeights,
        List<MoonWeight> moonWeights,
        List<Zone> zones,
        Optional<ResourceKey<Temperament>> temperament,
        Optional<FishAnimationConfig> animation,
        SwarmConfig swarm,
        float renderCalibration
) {
    public static final int DEFAULT_BASE_WEIGHT = 10;
    public static final float DEFAULT_MEAN_SIZE = 50.0f;
    public static final float DEFAULT_STDDEV_SIZE = 15.0f;

    /**
     * Fallback used when a species hasn't been measured yet (see {@link #renderCalibration}):
     * reproduces the old flat {@code 0.01 + size/100 * 0.8} scale formula's slope, so an
     * uncalibrated fish looks the same as it did before per-species calibration existed rather
     * than snapping to a value tuned for a different texture's padding.
     */
    public static final float DEFAULT_RENDER_CALIBRATION = 0.8f;

    public static final Codec<FishProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("base_weight", DEFAULT_BASE_WEIGHT).forGetter(FishProfile::baseWeight),
            SizeParams.CODEC.optionalFieldOf("size", SizeParams.DEFAULT).forGetter(FishProfile::size),
            BiomeWeight.CODEC.listOf().optionalFieldOf("biome_weights", List.of()).forGetter(FishProfile::biomeWeights),
            TimeWeight.CODEC.listOf().optionalFieldOf("time_weights", List.of()).forGetter(FishProfile::timeWeights),
            WeatherWeight.CODEC.listOf().optionalFieldOf("weather_weights", List.of()).forGetter(FishProfile::weatherWeights),
            MoonWeight.CODEC.listOf().optionalFieldOf("moon_weights", List.of()).forGetter(FishProfile::moonWeights),
            Zone.CODEC.listOf().fieldOf("zones").forGetter(FishProfile::zones),
            ResourceKey.codec(FishtasticRegistries.TEMPERAMENT_REGISTRY_KEY).optionalFieldOf("temperament").forGetter(FishProfile::temperament),
            FishAnimationConfig.CODEC.optionalFieldOf("animation").forGetter(FishProfile::animation),
            SwarmConfig.CODEC.optionalFieldOf("swarm", SwarmConfig.DEFAULT).forGetter(FishProfile::swarm),
            // Per-species multiplier so a fish's rendered length in the tank matches its cm size
            // regardless of animation mode or how much of its texture canvas the art actually
            // fills. Computed from a measured alpha-channel bounding box (diagonal extent for
            // horizontal_swim/upright_float/upright_sit with diagonal_texture, canvas-edge extent
            // otherwise): render_calibration = 1 / (correction * fill_fraction), where correction
            // is sqrt(2) for the diagonal-rotated modes (the 45° roll that lays a diagonally-painted
            // fish flat also widens its square texture quad's on-screen span by sqrt(2)) or 1.0
            // otherwise. See tools/fish-render-calibration.
            Codec.FLOAT.optionalFieldOf("render_calibration", DEFAULT_RENDER_CALIBRATION).forGetter(FishProfile::renderCalibration)
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

    /**
     * {@code biome} accepts either a tag reference ({@code "#namespace:tag"}) or one or more
     * direct biome ids ({@code "namespace:biome"} or a list of them) via
     * {@link RegistryCodecs#homogeneousList}, so an author can target a single specific biome
     * without needing a dedicated tag file for it.
     */
    public record BiomeWeight(HolderSet<Biome> biome, float multiplier, ConditionTier tier) {
        public static final Codec<BiomeWeight> CODEC = RecordCodecBuilder.create(i -> i.group(
                RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("biome").forGetter(BiomeWeight::biome),
                Codec.FLOAT.fieldOf("multiplier").forGetter(BiomeWeight::multiplier),
                ConditionTier.CODEC.optionalFieldOf("tier", ConditionTier.PRIMARY).forGetter(BiomeWeight::tier)
        ).apply(i, BiomeWeight::new));

        /** Full descriptor for debug output: {@code "#namespace:tag"} or comma-joined direct ids. */
        public String describe() {
            return biome.unwrap().map(
                    tag -> "#" + tag.location(),
                    list -> list.stream()
                            .map(h -> h.unwrapKey().map(k -> k.identifier().toString()).orElse("?"))
                            .collect(Collectors.joining(", "))
            );
        }

        /** Path-only display (no namespace/#) for prettified UI labels. */
        public String pathDisplay() {
            return biome.unwrap().map(
                    tag -> tag.location().getPath(),
                    list -> list.stream()
                            .map(h -> h.unwrapKey().map(k -> k.identifier().getPath()).orElse("?"))
                            .collect(Collectors.joining(", "))
            );
        }
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

    /**
     * Hard location gate: a fish is only a loot candidate when the current cast location
     * resolves to at least one of its declared zones. Unlike the other axes below, this is a
     * boolean membership check, not a multiplier — {@link #resolve} returns every Zone that
     * simultaneously applies to a location (water-body type and elevation band are independent
     * axes, e.g. a mountain river is both RIVER and HIGH_ALTITUDE at once), and a fish's
     * {@code zones} list is checked for any overlap upstream of any weight computation
     * (see FishtasticFishItem#isZoneEligible).
     */
    public enum Zone implements StringRepresentable {
        OCEAN, DEEP_OCEAN, RIVER, NETHER, CAVE, HIGH_ALTITUDE;

        // Sea level defaults to 63, so this is roughly y<33 (underground pools/ravine bottoms)
        // and y>93 (mountain lakes/rivers) — ordinary ocean/river fishing stays at the biome-tag zones.
        private static final int BAND_OFFSET = 30;

        public static final Codec<Zone> CODEC = StringRepresentable.fromEnum(Zone::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        /**
         * Elevation (CAVE/HIGH_ALTITUDE) and cave-biome detection layer on top of the water-body
         * zone rather than replacing it, so a fish declaring only RIVER or OCEAN doesn't lose
         * eligibility just because the cast location also happens to be deep underground or high
         * in the mountains. Deep-ocean trenches are exempted from the Y-band check since they
         * commonly sit below {@code seaLevel - BAND_OFFSET} on their own — that's still ocean
         * fishing, not a cave. Explicit cave biomes (lush caves, dripstone caves, deep dark) always
         * grant CAVE regardless of Y, since those biomes can generate above the Y-band cutoff.
         * Peak biomes (jagged peaks, frozen peaks, snowy slopes, grove — the {@code is_snowy_peaks}
         * tag) likewise always grant HIGH_ALTITUDE regardless of Y, since plateaus and passes on
         * those biomes commonly sit below the y+30 cutoff despite being unambiguously "the
         * mountains." Nether is checked unconditionally first since {@code level.getSeaLevel()}
         * isn't meaningful in that dimension, and it never combines with any other zone.
         * <p>
         * {@code minecraft:is_deep_ocean} is a subset of {@code minecraft:is_ocean} in vanilla —
         * every deep ocean biome is also a regular ocean biome — so both are added independently
         * rather than as an if/else-if; otherwise plain OCEAN-zoned fish would be silently barred
         * from every deep ocean biome despite it being ocean. HIGH_ALTITUDE mirrors this: there's
         * no vanilla tag hierarchy making elevated water a subset of {@code is_river}, so RIVER is
         * added explicitly alongside it — an alpine lake/pond that isn't literally a river biome
         * should still draw from the full river pool, with high-altitude-only fish layered on top.
         */
        public static Set<Zone> resolve(Holder<Biome> biome, int y, int seaLevel) {
            if (biome.is(BiomeTags.IS_NETHER)) return EnumSet.of(NETHER);

            EnumSet<Zone> zones = EnumSet.noneOf(Zone.class);
            boolean isDeepOcean = biome.is(BiomeTags.IS_DEEP_OCEAN);
            boolean isOcean = biome.is(BiomeTags.IS_OCEAN);
            if (isOcean) zones.add(OCEAN);
            if (isDeepOcean) zones.add(DEEP_OCEAN);
            if (biome.is(BiomeTags.IS_RIVER)) zones.add(RIVER);

            if (!isDeepOcean && !isOcean) {
                if (y < seaLevel - BAND_OFFSET) zones.add(CAVE);
                if (y > seaLevel + BAND_OFFSET) { zones.add(HIGH_ALTITUDE); zones.add(RIVER); }
            }
            if (biome.is(FishtasticBiomeTags.IS_CAVE_BIOME)) zones.add(CAVE);
            if (biome.is(FishtasticBiomeTags.IS_SNOWY_PEAKS)) { zones.add(HIGH_ALTITUDE); zones.add(RIVER); }

            if (zones.isEmpty()) zones.add(RIVER);
            return zones;
        }
    }

    public record MoonWeight(MoonPhase phase, float multiplier, ConditionTier tier) {
        public static final Codec<MoonWeight> CODEC = RecordCodecBuilder.create(i -> i.group(
                MoonPhase.CODEC.fieldOf("phase").forGetter(MoonWeight::phase),
                Codec.FLOAT.fieldOf("multiplier").forGetter(MoonWeight::multiplier),
                ConditionTier.CODEC.optionalFieldOf("tier", ConditionTier.PRIMARY).forGetter(MoonWeight::tier)
        ).apply(i, MoonWeight::new));
    }

    /**
     * Computes the weight multiplier for this fish given current environmental conditions.
     * Each axis contributes at most one matching entry per tier: PRIMARY entries multiply
     * together (the "combo hunting" play), SECONDARY entries add flat percentages so that
     * stacking several flavor conditions nudges the weight instead of compounding it.
     */
    public float computeEnvironmentMultiplier(Holder<Biome> biome, TimeOfDay timeOfDay, WeatherCondition weather,
                                               MoonPhase moonPhase) {
        boolean moonVisible = timeOfDay == TimeOfDay.NIGHT && weather == WeatherCondition.CLEAR;

        float primaryProduct = 1.0f;
        primaryProduct *= firstMatch(biomeWeights, ConditionTier.PRIMARY, BiomeWeight::tier, bw -> bw.biome().contains(biome), BiomeWeight::multiplier);
        primaryProduct *= firstMatch(timeWeights, ConditionTier.PRIMARY, TimeWeight::tier, tw -> tw.time() == timeOfDay, TimeWeight::multiplier);
        primaryProduct *= firstMatch(weatherWeights, ConditionTier.PRIMARY, WeatherWeight::tier, ww -> ww.weather() == weather, WeatherWeight::multiplier);
        if (moonVisible) {
            primaryProduct *= firstMatch(moonWeights, ConditionTier.PRIMARY, MoonWeight::tier, mw -> mw.phase() == moonPhase, MoonWeight::multiplier);
        }

        float secondaryBonus = 0.0f;
        secondaryBonus += firstMatch(biomeWeights, ConditionTier.SECONDARY, BiomeWeight::tier, bw -> bw.biome().contains(biome), BiomeWeight::multiplier) - 1.0f;
        secondaryBonus += firstMatch(timeWeights, ConditionTier.SECONDARY, TimeWeight::tier, tw -> tw.time() == timeOfDay, TimeWeight::multiplier) - 1.0f;
        secondaryBonus += firstMatch(weatherWeights, ConditionTier.SECONDARY, WeatherWeight::tier, ww -> ww.weather() == weather, WeatherWeight::multiplier) - 1.0f;
        if (moonVisible) {
            secondaryBonus += firstMatch(moonWeights, ConditionTier.SECONDARY, MoonWeight::tier, mw -> mw.phase() == moonPhase, MoonWeight::multiplier) - 1.0f;
        }

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
