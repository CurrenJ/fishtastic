package grill24.fishtastic.fabric.datagen;

import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.component.FishTankMaterials;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.data.QuestCategory;
import grill24.fishtastic.data.QuestDifficulty;
import grill24.fishtastic.data.QuestObjective;
import grill24.fishtastic.data.QuestReward;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.IntFunction;
import java.util.function.UnaryOperator;

/**
 * Builds the bronze/silver/gold trio of one daily quest "family" for {@link QuestProvider}.
 * Catch conditions shared by all three tiers (species tag, biome, time, weather, zone) are set
 * once via {@link #objective}; each tier then only states what makes it harder — target count
 * and/or minimum quality — and what it pays out.
 */
public final class DailyQuestFamily {
    private final String id;
    private String displayName = "";
    /** Formats a tier's description from its target count and (if set) minimum quality. */
    private BiFunction<Integer, Optional<FishQuality.Quality>, String> descriptionFn = (count, quality) -> "";
    private Template template = new Template();
    private Tier bronze;
    private Tier silver;
    private Tier gold;

    private DailyQuestFamily(String id) {
        this.id = id;
    }

    public static DailyQuestFamily of(String id) {
        return new DailyQuestFamily(id);
    }

    public DailyQuestFamily displayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    /** {@code template} is formatted with the tier's target count, e.g. {@code "Catch %d fish at dusk."}. */
    public DailyQuestFamily description(String template) {
        this.descriptionFn = (count, quality) -> String.format(template, count);
        return this;
    }

    /** For families whose wording needs more than count substitution (e.g. singular/plural). */
    public DailyQuestFamily description(IntFunction<String> template) {
        this.descriptionFn = (count, quality) -> template.apply(count);
        return this;
    }

    /** For families whose tiers vary by quality rather than (or in addition to) count. */
    public DailyQuestFamily description(BiFunction<Integer, Optional<FishQuality.Quality>, String> descriptionFn) {
        this.descriptionFn = descriptionFn;
        return this;
    }

    public DailyQuestFamily objective(UnaryOperator<Template> config) {
        this.template = config.apply(template);
        return this;
    }

    public DailyQuestFamily bronze(int targetCount, int tokens) {
        this.bronze = new Tier(targetCount, Optional.empty(), tokens, Optional.empty());
        return this;
    }

    public DailyQuestFamily bronze(int targetCount, FishQuality.Quality minQuality, int tokens) {
        this.bronze = new Tier(targetCount, Optional.of(minQuality), tokens, Optional.empty());
        return this;
    }

    public DailyQuestFamily silver(int targetCount, int tokens) {
        this.silver = new Tier(targetCount, Optional.empty(), tokens, Optional.empty());
        return this;
    }

    public DailyQuestFamily silver(int targetCount, FishQuality.Quality minQuality, int tokens) {
        this.silver = new Tier(targetCount, Optional.of(minQuality), tokens, Optional.empty());
        return this;
    }

    public DailyQuestFamily gold(int targetCount, int tokens, Block frame, Block sand, Block glass) {
        this.gold = new Tier(targetCount, Optional.empty(), tokens, Optional.of(goldTank(frame, sand, glass)));
        return this;
    }

    public DailyQuestFamily gold(int targetCount, FishQuality.Quality minQuality, int tokens, Block frame, Block sand, Block glass) {
        this.gold = new Tier(targetCount, Optional.of(minQuality), tokens, Optional.of(goldTank(frame, sand, glass)));
        return this;
    }

    /** Builds the reward's bonus fish tank: a plain {@code fishtastic:fish_tank} carrying a distinctive material combo. */
    private static QuestReward.RewardItem goldTank(Block frame, Block sand, Block glass) {
        DataComponentPatch patch = DataComponentPatch.builder()
                .set(FishtasticDataComponents.FISH_TANK_MATERIALS.value(), new FishTankMaterials(frame, sand, glass))
                .build();
        return new QuestReward.RewardItem(FishtasticBlocks.FISH_TANK.value().asItem(), 1, patch);
    }

    /** Emits the bronze/silver/gold trio, keyed by file name (without the {@code daily/} prefix or extension). */
    public Map<String, Quest> build() {
        if (bronze == null || silver == null || gold == null) {
            throw new IllegalStateException("Daily quest family '" + id + "' is missing a tier definition");
        }
        Map<String, Quest> quests = new LinkedHashMap<>();
        quests.put(id + "_bronze", quest(QuestDifficulty.BRONZE, bronze));
        quests.put(id + "_silver", quest(QuestDifficulty.SILVER, silver));
        quests.put(id + "_gold", quest(QuestDifficulty.GOLD, gold));
        return quests;
    }

    private Quest quest(QuestDifficulty difficulty, Tier tier) {
        QuestObjective objective = new QuestObjective(
                Optional.empty(),
                template.targetSpeciesTag,
                Optional.empty(),
                false,
                Optional.of(tier.targetCount),
                tier.minQuality,
                Optional.empty(),
                template.biomeCondition,
                template.timeCondition,
                template.weatherCondition,
                template.zoneCondition,
                Optional.empty(),
                1,
                false
        );
        List<QuestReward.RewardItem> items = tier.bonusItem.map(List::of).orElse(List.of());
        QuestReward reward = new QuestReward(tier.tokens, items);
        String description = descriptionFn.apply(tier.targetCount, tier.minQuality);
        return new Quest(QuestCategory.DAILY, difficulty, objective, reward, Optional.empty(), false, displayName, description);
    }

    /** Catch conditions shared by all three tiers of a family. */
    public static final class Template {
        private Optional<TagKey<Item>> targetSpeciesTag = Optional.empty();
        private Optional<TagKey<Biome>> biomeCondition = Optional.empty();
        private Optional<FishProfile.TimeOfDay> timeCondition = Optional.empty();
        private Optional<FishProfile.WeatherCondition> weatherCondition = Optional.empty();
        private Optional<FishProfile.Zone> zoneCondition = Optional.empty();

        public Template speciesTag(TagKey<Item> tag) {
            this.targetSpeciesTag = Optional.of(tag);
            return this;
        }

        public Template biome(TagKey<Biome> tag) {
            this.biomeCondition = Optional.of(tag);
            return this;
        }

        public Template time(FishProfile.TimeOfDay time) {
            this.timeCondition = Optional.of(time);
            return this;
        }

        public Template weather(FishProfile.WeatherCondition weather) {
            this.weatherCondition = Optional.of(weather);
            return this;
        }

        public Template zone(FishProfile.Zone zone) {
            this.zoneCondition = Optional.of(zone);
            return this;
        }
    }

    private record Tier(int targetCount, Optional<FishQuality.Quality> minQuality, int tokens,
                         Optional<QuestReward.RewardItem> bonusItem) {
    }
}
