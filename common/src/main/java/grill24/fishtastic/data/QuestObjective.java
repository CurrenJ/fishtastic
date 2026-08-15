package grill24.fishtastic.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.fishtastic.component.FishQuality;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;

import java.util.Optional;

public record QuestObjective(
        Optional<ResourceKey<Item>> targetSpecies,
        Optional<TagKey<Item>> targetSpeciesTag,
        Optional<TagKey<Item>> excludeSpeciesTag,
        boolean distinctSpecies,
        Optional<Integer> targetCount,
        Optional<FishQuality.Quality> minQuality,
        Optional<Float> minSize,
        Optional<TagKey<Biome>> biomeCondition,
        Optional<FishProfile.TimeOfDay> timeCondition,
        Optional<FishProfile.WeatherCondition> weatherCondition,
        /**
         * Gates a match to catches made while the player's hook position actually resolves to this
         * {@link FishProfile.Zone} (checked in {@code QuestTracker#matchesObjective} against the
         * session's real {@code FishProfile.Zone.resolve()} result). Also lights the quest log's
         * status pip (QuestLogScreen) as a UI hint.
         * <p>
         * This re-check is necessary despite {@code target_species_tag} membership already implying
         * zone-eligibility: a fish's {@code zones} list in fish_profile can name more than one zone
         * (e.g. a river+cave fish), so tag membership alone doesn't mean *this* catch happened in
         * the zone the quest cares about — only where {@code Zone.resolve()} actually placed the
         * hook does.
         */
        Optional<FishProfile.Zone> zoneCondition,
        Optional<Integer> minSessionCatches,
        int notificationInterval,
        /**
         * When true, progress is not an incrementing counter but a direct read of the player's
         * <em>lifetime</em> catch totals from {@code FishCatchSavedData} (see
         * {@code QuestTracker#lifetimeProgress}). This is what makes a tiered chain's
         * "Catch 50 X total" literally true: tier 3 reads 50 lifetime catches rather than
         * restarting at 0 and demanding 50 <em>more</em> after tier 2 is claimed.
         * <p>
         * It also removes the claim-timing trap — a lifetime quest is exempt from the
         * prerequisite gate in {@code QuestTracker}, so catches banked before the player got
         * around to claiming the previous tier still count instead of being silently discarded.
         * <p>
         * Only valid on objectives whose conditions are expressible as a species/tag filter:
         * lifetime data records species, size and quality bests, but not the biome, time,
         * weather, or zone a fish was caught under, so those conditions cannot be replayed
         * against it.
         */
        boolean lifetimeCount,
        /**
         * When true, this objective is never matched against a caught fish ({@code QuestTracker}
         * skips it entirely in {@code onCatch}/{@code onCatchBatch}) — instead it's driven by
         * {@code QuestTracker#onDailyQuestClaimed}, which marks it complete the moment every one of
         * that day's active daily quests has been claimed. Lets a quest reward something (e.g. an
         * otherwise-locked tank shape) for clearing the whole daily board in a single day, without
         * needing a generic quest-completion predicate framework.
         */
        boolean allDailiesClaimedToday,
        /**
         * Present iff this objective is a live tank-composition check rather than a per-catch
         * counter. Never matched against a caught fish or a claimed daily — instead it's driven by
         * {@code QuestTracker#onTankChanged}, which re-evaluates it every time a fish is added to a
         * tank as a snapshot of that one tank's current contents (species/tag/quality/size filters,
         * {@link #distinctSpecies} for "one of each simultaneously displayed", and
         * {@link TankSnapshotCondition#material}/{@link TankSnapshotCondition#materialTag} for the
         * tank's frame, e.g. a "gold tank" objective). There is no per-catch counter to increment:
         * completion is "is this true of one tank right now," checked fresh on every insertion. Once
         * it flips to complete it's skipped on all future re-checks like any other completed quest,
         * so dismantling the tank afterward can't un-complete it — the deliberate act is assembling
         * the display, not maintaining it forever.
         * <p>
         * Bundled into one nested optional (rather than three top-level fields) because
         * {@code RecordCodecBuilder}'s {@code group()} tops out at 16 fields, and this record was
         * already at 15.
         */
        Optional<TankSnapshotCondition> tankSnapshot
) {
    /**
     * Material conditions for a {@link #tankSnapshot} objective, checked against the tank's
     * {@code FishTankMaterials#frame()}. Both fields optional and independently checkable, same as
     * {@link #targetSpecies}/{@link #targetSpeciesTag} for fish.
     * <p>
     * When {@link #lifetime} is true, this objective abandons the live per-tank snapshot entirely
     * and instead reads a persistent, ever-incrementing "fish placed into any tank" counter (see
     * {@code PlayerQuestState#getLifetimeTankPlacements}) — the tank-insertion analogue of
     * {@link QuestObjective#lifetimeCount} for catches. {@link #material}/{@link #materialTag} are
     * ignored in this mode since the counter isn't scoped to one tank's frame; kept bundled here
     * rather than as a new top-level {@code QuestObjective} field only because
     * {@code RecordCodecBuilder}'s {@code group()} tops out at 16 fields and that record is already
     * there.
     */
    public record TankSnapshotCondition(Optional<ResourceKey<Block>> material, Optional<TagKey<Block>> materialTag,
            boolean lifetime) {
        public static final Codec<TankSnapshotCondition> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceKey.codec(Registries.BLOCK).optionalFieldOf("material").forGetter(TankSnapshotCondition::material),
                TagKey.codec(Registries.BLOCK).optionalFieldOf("material_tag").forGetter(TankSnapshotCondition::materialTag),
                Codec.BOOL.optionalFieldOf("lifetime", false).forGetter(TankSnapshotCondition::lifetime)
        ).apply(i, TankSnapshotCondition::new));
    }

    /** Back-compat with call sites predating {@link #allDailiesClaimedToday}; defaults it to false. */
    public QuestObjective(Optional<ResourceKey<Item>> targetSpecies, Optional<TagKey<Item>> targetSpeciesTag,
            Optional<TagKey<Item>> excludeSpeciesTag, boolean distinctSpecies, Optional<Integer> targetCount,
            Optional<FishQuality.Quality> minQuality, Optional<Float> minSize, Optional<TagKey<Biome>> biomeCondition,
            Optional<FishProfile.TimeOfDay> timeCondition, Optional<FishProfile.WeatherCondition> weatherCondition,
            Optional<FishProfile.Zone> zoneCondition, Optional<Integer> minSessionCatches, int notificationInterval,
            boolean lifetimeCount) {
        this(targetSpecies, targetSpeciesTag, excludeSpeciesTag, distinctSpecies, targetCount, minQuality, minSize,
                biomeCondition, timeCondition, weatherCondition, zoneCondition, minSessionCatches, notificationInterval,
                lifetimeCount, false, Optional.empty());
    }

    /** Back-compat with call sites predating {@link #tankSnapshot}. */
    public QuestObjective(Optional<ResourceKey<Item>> targetSpecies, Optional<TagKey<Item>> targetSpeciesTag,
            Optional<TagKey<Item>> excludeSpeciesTag, boolean distinctSpecies, Optional<Integer> targetCount,
            Optional<FishQuality.Quality> minQuality, Optional<Float> minSize, Optional<TagKey<Biome>> biomeCondition,
            Optional<FishProfile.TimeOfDay> timeCondition, Optional<FishProfile.WeatherCondition> weatherCondition,
            Optional<FishProfile.Zone> zoneCondition, Optional<Integer> minSessionCatches, int notificationInterval,
            boolean lifetimeCount, boolean allDailiesClaimedToday) {
        this(targetSpecies, targetSpeciesTag, excludeSpeciesTag, distinctSpecies, targetCount, minQuality, minSize,
                biomeCondition, timeCondition, weatherCondition, zoneCondition, minSessionCatches, notificationInterval,
                lifetimeCount, allDailiesClaimedToday, Optional.empty());
    }

    public static final Codec<QuestObjective> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceKey.codec(Registries.ITEM).optionalFieldOf("target_species").forGetter(QuestObjective::targetSpecies),
            TagKey.codec(Registries.ITEM).optionalFieldOf("target_species_tag").forGetter(QuestObjective::targetSpeciesTag),
            TagKey.codec(Registries.ITEM).optionalFieldOf("exclude_species_tag").forGetter(QuestObjective::excludeSpeciesTag),
            Codec.BOOL.optionalFieldOf("distinct_species", false).forGetter(QuestObjective::distinctSpecies),
            Codec.INT.optionalFieldOf("target_count").forGetter(QuestObjective::targetCount),
            FishQuality.Quality.CODEC.optionalFieldOf("min_quality").forGetter(QuestObjective::minQuality),
            Codec.FLOAT.optionalFieldOf("min_size").forGetter(QuestObjective::minSize),
            TagKey.codec(Registries.BIOME).optionalFieldOf("biome_condition").forGetter(QuestObjective::biomeCondition),
            FishProfile.TimeOfDay.CODEC.optionalFieldOf("time_condition").forGetter(QuestObjective::timeCondition),
            FishProfile.WeatherCondition.CODEC.optionalFieldOf("weather_condition").forGetter(QuestObjective::weatherCondition),
            FishProfile.Zone.CODEC.optionalFieldOf("zone_condition").forGetter(QuestObjective::zoneCondition),
            Codec.INT.optionalFieldOf("min_session_catches").forGetter(QuestObjective::minSessionCatches),
            Codec.INT.optionalFieldOf("notification_interval", 1).forGetter(QuestObjective::notificationInterval),
            Codec.BOOL.optionalFieldOf("lifetime_count", false).forGetter(QuestObjective::lifetimeCount),
            Codec.BOOL.optionalFieldOf("all_dailies_claimed_today", false).forGetter(QuestObjective::allDailiesClaimedToday),
            TankSnapshotCondition.CODEC.optionalFieldOf("tank_snapshot").forGetter(QuestObjective::tankSnapshot)
    ).apply(i, QuestObjective::new));

    /**
     * Whether this objective's conditions can be evaluated against lifetime catch records.
     * Lifetime data stores species and counts, not the environment a fish was caught in, so an
     * objective carrying an environmental or per-catch condition can't be replayed against it.
     * Used to fail fast on mis-authored quests rather than silently ignoring the conditions.
     */
    public boolean isLifetimeCompatible() {
        return minQuality.isEmpty() && minSize.isEmpty() && biomeCondition.isEmpty()
                && timeCondition.isEmpty() && weatherCondition.isEmpty() && zoneCondition.isEmpty()
                && minSessionCatches.isEmpty() && !distinctSpecies;
    }

    /**
     * Resolves the quest's completion target. {@code target_count} may be omitted entirely
     * for a "collect one of each" objective ({@code distinct_species} paired with
     * {@code target_species_tag}) — the target is then derived as the number of items
     * currently in the tag (minus any also in {@code exclude_species_tag}), so authored
     * quests never drift out of sync as fish are added to or removed from a zone tag.
     * Any other objective shape falls back to 1 if {@code target_count} is unspecified.
     */
    public int effectiveTargetCount(RegistryAccess registryAccess) {
        if (targetCount.isPresent()) return targetCount.get();
        if (distinctSpecies && targetSpeciesTag.isPresent()) {
            Registry<Item> items = registryAccess.lookupOrThrow(Registries.ITEM);
            int count = 0;
            for (Holder<Item> holder : items.getTagOrEmpty(targetSpeciesTag.get())) {
                if (excludeSpeciesTag.isPresent() && holder.is(excludeSpeciesTag.get())) continue;
                count++;
            }
            return count;
        }
        return 1;
    }
}
