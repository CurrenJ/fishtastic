package grill24.fishtastic.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.FishtasticRegistries;
import net.minecraft.resources.ResourceKey;

import java.util.Optional;

public record Quest(
        QuestCategory category,
        QuestObjective objective,
        QuestReward reward,
        Optional<ResourceKey<Quest>> prerequisiteQuestId,
        boolean hidden,
        String displayName,
        String description
) {
    public static final Codec<Quest> CODEC = RecordCodecBuilder.create(i -> i.group(
            QuestCategory.CODEC.fieldOf("category").forGetter(Quest::category),
            QuestObjective.CODEC.fieldOf("objective").forGetter(Quest::objective),
            QuestReward.CODEC.fieldOf("reward").forGetter(Quest::reward),
            ResourceKey.codec(FishtasticRegistries.QUEST_REGISTRY_KEY).optionalFieldOf("prerequisite").forGetter(Quest::prerequisiteQuestId),
            Codec.BOOL.optionalFieldOf("hidden", false).forGetter(Quest::hidden),
            Codec.STRING.optionalFieldOf("display_name", "").forGetter(Quest::displayName),
            Codec.STRING.optionalFieldOf("description", "").forGetter(Quest::description)
    ).apply(i, Quest::new));
}
