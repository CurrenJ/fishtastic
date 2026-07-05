package grill24.fishtastic.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.fishtastic.component.FishQuality;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;

import java.util.Optional;

public record QuestObjective(
        Optional<ResourceKey<Item>> targetSpecies,
        Optional<TagKey<Item>> targetSpeciesTag,
        int targetCount,
        Optional<FishQuality.Quality> minQuality,
        Optional<Float> minSize,
        Optional<TagKey<Biome>> biomeCondition,
        Optional<FishProfile.TimeOfDay> timeCondition,
        Optional<FishProfile.WeatherCondition> weatherCondition,
        Optional<Integer> minSessionCatches,
        int notificationInterval
) {
    public static final Codec<QuestObjective> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceKey.codec(Registries.ITEM).optionalFieldOf("target_species").forGetter(QuestObjective::targetSpecies),
            TagKey.codec(Registries.ITEM).optionalFieldOf("target_species_tag").forGetter(QuestObjective::targetSpeciesTag),
            Codec.INT.optionalFieldOf("target_count", 1).forGetter(QuestObjective::targetCount),
            FishQuality.Quality.CODEC.optionalFieldOf("min_quality").forGetter(QuestObjective::minQuality),
            Codec.FLOAT.optionalFieldOf("min_size").forGetter(QuestObjective::minSize),
            TagKey.codec(Registries.BIOME).optionalFieldOf("biome_condition").forGetter(QuestObjective::biomeCondition),
            FishProfile.TimeOfDay.CODEC.optionalFieldOf("time_condition").forGetter(QuestObjective::timeCondition),
            FishProfile.WeatherCondition.CODEC.optionalFieldOf("weather_condition").forGetter(QuestObjective::weatherCondition),
            Codec.INT.optionalFieldOf("min_session_catches").forGetter(QuestObjective::minSessionCatches),
            Codec.INT.optionalFieldOf("notification_interval", 1).forGetter(QuestObjective::notificationInterval)
    ).apply(i, QuestObjective::new));
}
