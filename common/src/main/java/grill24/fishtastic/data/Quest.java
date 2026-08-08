package grill24.fishtastic.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.FishtasticRegistries;
import net.minecraft.resources.ResourceKey;

import java.util.Optional;

public record Quest(
        QuestCategory category,
        QuestDifficulty difficulty,
        QuestObjective objective,
        QuestReward reward,
        Optional<ResourceKey<Quest>> prerequisiteQuestId,
        boolean hidden,
        String displayName,
        String description
) {
    public static final Codec<Quest> CODEC = RecordCodecBuilder.create(i -> i.group(
            QuestCategory.CODEC.fieldOf("category").forGetter(Quest::category),
            QuestDifficulty.CODEC.optionalFieldOf("difficulty", QuestDifficulty.BRONZE).forGetter(Quest::difficulty),
            QuestObjective.CODEC.fieldOf("objective").forGetter(Quest::objective),
            QuestReward.CODEC.fieldOf("reward").forGetter(Quest::reward),
            ResourceKey.codec(FishtasticRegistries.QUEST_REGISTRY_KEY).optionalFieldOf("prerequisite").forGetter(Quest::prerequisiteQuestId),
            Codec.BOOL.optionalFieldOf("hidden", false).forGetter(Quest::hidden),
            Codec.STRING.optionalFieldOf("display_name", "").forGetter(Quest::displayName),
            Codec.STRING.optionalFieldOf("description", "").forGetter(Quest::description)
    ).apply(i, Quest::new));

    /**
     * Whether this quest should be kept out of the quest log, given the player's progress on it
     * and on its prerequisite. Lives here rather than on the screen because it is a rule about
     * quest data, not rendering — and because the screen class can't be loaded server-side.
     *
     * <p>{@code hidden} means two different things depending on chain position:
     * <ul>
     *   <li><b>No prerequisite</b> — a genuine secret (the unlisted-fish reveals). It stays out of
     *       the log until its objective is met; progress still tracks silently in the background
     *       ({@code QuestTracker} doesn't check {@code hidden}), so it surfaces already complete
     *       and ready to claim.</li>
     *   <li><b>Has a prerequisite</b> — the next rung of a chain (every mastery tier 2/3). It
     *       becomes visible as soon as that prerequisite is <em>claimed</em>, which is exactly when
     *       {@code QuestTracker} starts crediting catches toward it. Hiding it until completion
     *       instead would mean the player never sees the rung they are climbing, leaving the tiered
     *       progress bars — the whole point of the Mastery category — to appear only after the fact.</li>
     * </ul>
     *
     * <p>Completion always wins, so progress banked before a prerequisite was claimed can never
     * strand a finished quest outside the log.
     */
    public boolean isHiddenFromLog(boolean completed, boolean prerequisiteClaimed) {
        if (!hidden) return false;
        if (completed) return false;
        return prerequisiteQuestId.isEmpty() || !prerequisiteClaimed;
    }
}
