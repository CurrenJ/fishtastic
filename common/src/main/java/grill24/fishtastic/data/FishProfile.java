package grill24.fishtastic.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticBiomeTags;
import grill24.fishtastic.school.VisitShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

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
        float renderCalibration,
        Optional<CompanionGroupConfig> companionGroup,
        CompanionGait companionGait,
        CompanionDrift companionDrift,
        Optional<WorldEncounter> worldEncounter
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
            Codec.FLOAT.optionalFieldOf("render_calibration", DEFAULT_RENDER_CALIBRATION).forGetter(FishProfile::renderCalibration),
            CompanionGroupConfig.CODEC.optionalFieldOf("companion_group").forGetter(FishProfile::companionGroup),
            CompanionGait.CODEC.optionalFieldOf("companion_gait", CompanionGait.CRAWL).forGetter(FishProfile::companionGait),
            CompanionDrift.CODEC.optionalFieldOf("companion_drift", CompanionDrift.BELL).forGetter(FishProfile::companionDrift),
            WorldEncounter.CODEC.optionalFieldOf("world_encounter").forGetter(FishProfile::worldEncounter)
    ).apply(i, FishProfile::new));

    /**
     * How this species arrives as an interactive in-world visit
     * (docs/in-world-fish-encounters-design-spec.md §8). Ecology stays in the weight tables above;
     * this only describes the encounter. A missing shape falls back to {@link VisitShape#fallback}.
     *
     * @param members       how many fish a visit holds, clamped to the shape's own range
     * @param lifetimeScale multiplies the configured visit lifetime
     */
    public record WorldEncounter(Optional<VisitShape> shape, Optional<MemberRange> members, float lifetimeScale) {
        public static final float MIN_LIFETIME_SCALE = 0.25f;
        public static final float MAX_LIFETIME_SCALE = 2.0f;

        public static final Codec<WorldEncounter> CODEC = RecordCodecBuilder.create(i -> i.group(
                VisitShape.CODEC.optionalFieldOf("shape").forGetter(WorldEncounter::shape),
                MemberRange.CODEC.optionalFieldOf("members").forGetter(WorldEncounter::members),
                Codec.floatRange(MIN_LIFETIME_SCALE, MAX_LIFETIME_SCALE).optionalFieldOf("lifetime_scale", 1.0f)
                        .forGetter(WorldEncounter::lifetimeScale)
        ).apply(i, WorldEncounter::new));

        public record MemberRange(int min, int max) {
            public static final Codec<MemberRange> CODEC = RecordCodecBuilder.<MemberRange>create(i -> i.group(
                    Codec.intRange(1, 64).fieldOf("min").forGetter(MemberRange::min),
                    Codec.intRange(1, 64).fieldOf("max").forGetter(MemberRange::max)
            ).apply(i, MemberRange::new)).validate(range -> range.min() <= range.max()
                    ? com.mojang.serialization.DataResult.success(range)
                    : com.mojang.serialization.DataResult.error(() -> "members.min must not exceed members.max"));
        }
    }

    /** The authored visit shape, or the rarity fallback when none is authored. */
    public VisitShape visitShape() {
        return worldEncounter.flatMap(WorldEncounter::shape).orElseGet(() -> VisitShape.fallback(baseWeight));
    }

    /**
     * How a benthic companion gets about on land (docs/fish-companions.md): the
     * scuttle-and-pause crawl, or a flatfish's smooth ripple-glide. Ignored for other classes.
     */
    public enum CompanionGait implements StringRepresentable {
        CRAWL, RIPPLE;
        public static final Codec<CompanionGait> CODEC = StringRepresentable.fromEnum(CompanionGait::values);
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    /**
     * How a drifter companion propels itself (docs/fish-companions-drift.md): a pulsed bell, a
     * squid's fins and mantle jets, or a man o' war's bobbing float. Ignored for other classes.
     */
    public enum CompanionDrift implements StringRepresentable {
        BELL, JET, BOB;
        public static final Codec<CompanionDrift> CODEC = StringRepresentable.fromEnum(CompanionDrift::values);
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    /**
     * How a ground benthic's group walks: in a line or as a crowd. Groups come from the party now
     * (docs/fish-companions.md): two or three of the species bonded together form one.
     */
    public record CompanionGroupConfig(Formation formation) {
        public static final Codec<CompanionGroupConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
                Formation.CODEC.fieldOf("formation").forGetter(CompanionGroupConfig::formation)
        ).apply(i, CompanionGroupConfig::new));

        public enum Formation implements StringRepresentable {
            LINE, CROWD;
            public static final Codec<Formation> CODEC = StringRepresentable.fromEnum(Formation::values);
            @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
        }
    }

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

        // Single source of truth for the DAWN/DAY/DUSK/NIGHT tick boundaries — anything else that
        // needs to know exactly when dawn or dusk starts/ends (e.g. SunsetExtensionHandler) should
        // reference these constants rather than duplicating the boundary values, so they can't drift.
        // Dawn sits at the tail of the 24000-tick cycle (the pre-sunrise sky lightening just before
        // tick 0/6:00 AM), not the head — DAWN_START_TICK > DUSK_END_TICK is intentional.
        public static final long DAWN_START_TICK = 22000L;
        public static final long DAWN_END_TICK = 24000L;
        public static final long DUSK_START_TICK = 12000L;
        public static final long DUSK_END_TICK = 14000L;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static TimeOfDay fromGameTime(long dayTime) {
            long t = dayTime % 24000;
            if (t >= DAWN_START_TICK) return DAWN;
            if (t < DUSK_START_TICK) return DAY;
            if (t < DUSK_END_TICK) return DUSK;
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
                return precipitationAt(level, pos) == Biome.Precipitation.SNOW ? SNOW : RAIN;
            }
            return CLEAR;
        }

        /** 26.1.2's {@code Level#precipitationAt}, which 1.21.1 doesn't have. */
        private static Biome.Precipitation precipitationAt(Level level, BlockPos pos) {
            if (!level.isRaining() || !level.canSeeSky(pos)
                    || level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos).getY() > pos.getY()) {
                return Biome.Precipitation.NONE;
            }
            return level.getBiome(pos).value().getPrecipitationAt(pos);
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
                            .map(h -> h.unwrapKey().map(k -> k.location().toString()).orElse("?"))
                            .collect(Collectors.joining(", "))
            );
        }

        /** Path-only display (no namespace/#) for prettified UI labels. */
        public String pathDisplay() {
            return biome.unwrap().map(
                    tag -> tag.location().getPath(),
                    list -> list.stream()
                            .map(h -> h.unwrapKey().map(k -> k.location().getPath()).orElse("?"))
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

    public record MoonWeight(FishMoonPhase phase, float multiplier, ConditionTier tier) {
        public static final Codec<MoonWeight> CODEC = RecordCodecBuilder.create(i -> i.group(
                FishMoonPhase.CODEC.fieldOf("phase").forGetter(MoonWeight::phase),
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
                                               FishMoonPhase moonPhase) {
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
