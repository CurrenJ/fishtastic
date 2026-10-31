package grill24.fishtastic.client.encyclopedia;

import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.client.FishEncyclopediaClientCache;
import grill24.fishtastic.client.FishEncyclopediaClientHelper;
import grill24.fishtastic.component.BaitEffect;
import grill24.fishtastic.data.EncyclopediaRewardSection;
import grill24.fishtastic.data.EncyclopediaTiers;
import grill24.fishtastic.data.FishEncyclopediaEntry;
import grill24.fishtastic.data.FishMoonPhase;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.data.Temperament;
import grill24.fishtastic.network.LeaderboardEntry;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable view of one species for the encyclopedia info page: everything the page shows,
 * resolved once from {@link FishEncyclopediaClientCache}, the derived tiers and the registries,
 * so the page itself never touches game state. Section gating (name, size, bait, habits, notes)
 * is resolved here too: a section is open once the catch count reaches its threshold, or for
 * good once its reward has been claimed (a claimed section never re-locks if the derived
 * thresholds move).
 *
 * @param fishId         the species id (item id and fish profile id are the same)
 * @param stack          the fish item to draw
 * @param caught         at least one catch
 * @param observed       seen through a spyglass (E7): name, portrait and broad habitat open without a catch
 * @param nameRevealed   the name section is open
 * @param displayName    the species name, or the "???" placeholder until revealed
 * @param catches        this player's catch count
 * @param thresholds     the catch counts at which each section opens
 * @param milestones     the five sections sorted by this species' thresholds, with reward state
 * @param unclaimedCoins coins waiting on open, unclaimed sections
 * @param complete       every section is open
 * @param rarity         base odds in the species' best water, or null if it fits no pool
 * @param sizeMean       average size in cm
 * @param sizeStdDev     size spread in cm
 * @param sizeUnlocked   the size section is open and the species has been caught
 * @param personalBest   this player's best catch, if any
 * @param serverBest     the server's best catch, if any
 * @param fight          the temperament summary; present only once caught and size is open
 * @param baits          every bait that raises this species' odds
 * @param baitUnlocked   the bait section is open and the species has been caught
 * @param habitsUnlocked the habits section is open and the species has been caught
 * @param biomes         biome conditions, in profile order
 * @param times          time-of-day conditions (first entry per key; shipped data has one)
 * @param weathers       weather conditions (as above)
 * @param moons          moon-phase conditions (as above)
 * @param lore           the field notes text, if authored
 * @param loreUnlocked   the notes section is open
 * @param zones          the zones the species can be caught in (empty = anywhere)
 * @param themeZone      the first zone (RIVER when unrestricted); picks the portrait theme
 */
public record FishPageModel(
        ResourceLocation fishId,
        ItemStack stack,
        boolean caught,
        boolean observed,
        boolean nameRevealed,
        Component displayName,
        int catches,
        FishEncyclopediaEntry.UnlockThresholds thresholds,
        List<Milestone> milestones,
        int unclaimedCoins,
        boolean complete,
        Rarity rarity,
        float sizeMean,
        float sizeStdDev,
        boolean sizeUnlocked,
        Optional<PersonalBest> personalBest,
        Optional<ServerBest> serverBest,
        Optional<Fight> fight,
        List<ItemStack> baits,
        boolean baitUnlocked,
        boolean habitsUnlocked,
        List<BiomeCondition> biomes,
        Map<FishProfile.TimeOfDay, Condition> times,
        Map<FishProfile.WeatherCondition, Condition> weathers,
        Map<FishMoonPhase, Condition> moons,
        Optional<String> lore,
        boolean loreUnlocked,
        List<FishProfile.Zone> zones,
        FishProfile.Zone themeZone
) {
    /** One reward section on the progress track. {@code reached} also holds once claimed. */
    public record Milestone(EncyclopediaRewardSection section, int threshold, Component label, int coins,
                            boolean reached, boolean claimed) {}

    /** Rarity gems 1 (abundant) to 5 (very rare), and the "about 1 in N" odds in the best water, named by {@code pool}. */
    public record Rarity(int gems, int oneInN, Component pool) {}

    public record PersonalBest(float size, Component quality) {}

    public record ServerBest(float size, Component holder) {}

    /** Temperament name and its mean difficulty (the midpoint of the temperament's difficulty range). */
    public record Fight(Component temperament, float difficulty) {}

    /** One biome condition: a display name for the tag or biome list, its multiplier and tier. */
    public record BiomeCondition(Component name, float multiplier, FishProfile.ConditionTier tier) {}

    /** The multiplier and tier of one time, weather or moon condition. */
    public record Condition(float multiplier, FishProfile.ConditionTier tier) {}

    /** Builds the model, or null when the species has no fish profile in {@code access}. */
    public static FishPageModel build(RegistryAccess access, ResourceKey<FishProfile> key) {
        FishProfile profile = access.registryOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY).getOptional(key).orElse(null);
        if (profile == null) return null;
        ResourceLocation fishId = key.location();
        Item item = BuiltInRegistries.ITEM.getOptional(fishId).orElse(Items.COD);
        ItemStack stack = new ItemStack(item);

        int catches = FishEncyclopediaClientCache.getCatchCount(fishId);
        boolean caught = catches > 0;
        FishEncyclopediaEntry.UnlockThresholds thresholds = EncyclopediaTiers.thresholdsFor(access, key);

        List<Milestone> milestones = new ArrayList<>();
        int unclaimedCoins = 0;
        boolean complete = true;
        for (EncyclopediaRewardSection section : unlockOrder(thresholds)) {
            boolean claimed = FishEncyclopediaClientCache.isRewardClaimed(fishId, section);
            boolean reached = catches >= section.threshold(thresholds) || claimed;
            milestones.add(new Milestone(section, section.threshold(thresholds), Component.translatable(milestoneKey(section)),
                    section.coinReward(), reached, claimed));
            if (reached && !claimed) unclaimedCoins += section.coinReward();
            if (!reached) complete = false;
        }

        boolean observed = FishEncyclopediaClientCache.isObserved(fishId);
        boolean nameRevealed = nameRevealed(observed, catches, thresholds.nameRevealCatches(),
                FishEncyclopediaClientCache.isRewardClaimed(fishId, EncyclopediaRewardSection.NAME_REVEAL));
        Component displayName = nameRevealed
                ? stack.getHoverName()
                : Component.translatable("screen.fishtastic.encyclopedia.locked_placeholder");

        boolean statsOpen = unlocked(fishId, EncyclopediaRewardSection.STATS, catches, thresholds);
        boolean sizeUnlocked = statsOpen && caught;
        boolean baitUnlocked = unlocked(fishId, EncyclopediaRewardSection.TYPES, catches, thresholds) && caught;
        boolean habitsUnlocked = unlocked(fishId, EncyclopediaRewardSection.SPAWN_CONDITIONS, catches, thresholds) && caught;
        boolean loreUnlocked = unlocked(fishId, EncyclopediaRewardSection.LORE, catches, thresholds);

        Map<FishProfile.TimeOfDay, Condition> times = new EnumMap<>(FishProfile.TimeOfDay.class);
        for (FishProfile.TimeWeight w : profile.timeWeights()) times.putIfAbsent(w.time(), new Condition(w.multiplier(), w.tier()));
        Map<FishProfile.WeatherCondition, Condition> weathers = new EnumMap<>(FishProfile.WeatherCondition.class);
        for (FishProfile.WeatherWeight w : profile.weatherWeights()) weathers.putIfAbsent(w.weather(), new Condition(w.multiplier(), w.tier()));
        Map<FishMoonPhase, Condition> moons = new EnumMap<>(FishMoonPhase.class);
        for (FishProfile.MoonWeight w : profile.moonWeights()) moons.putIfAbsent(w.phase(), new Condition(w.multiplier(), w.tier()));
        List<BiomeCondition> biomes = new ArrayList<>();
        for (FishProfile.BiomeWeight w : profile.biomeWeights()) biomes.add(new BiomeCondition(biomeName(w), w.multiplier(), w.tier()));

        return new FishPageModel(fishId, stack, caught, observed, nameRevealed, displayName, catches, thresholds,
                List.copyOf(milestones), unclaimedCoins, complete,
                rarity(access, fishId), profile.size().mean(), profile.size().stdDev(), sizeUnlocked,
                personalBest(fishId), serverBest(fishId),
                sizeUnlocked ? fight(access, profile) : Optional.empty(),
                baitsFor(item), baitUnlocked, habitsUnlocked,
                List.copyOf(biomes), times, weathers, moons,
                FishEncyclopediaClientHelper.getEncyclopediaEntry(access, key).lore(), loreUnlocked,
                List.copyOf(profile.zones()), profile.zones().isEmpty() ? FishProfile.Zone.RIVER : profile.zones().get(0));
    }

    /** Whether the species is known at all: caught, or seen through a spyglass. Drives the portrait silhouette. */
    public boolean discovered() {
        return caught || observed;
    }

    /**
     * Whether the name is shown: always once observed (E7, E19), otherwise once the name section
     * has opened for a species that has been caught. Pure.
     */
    public static boolean nameRevealed(boolean observed, int catches, int nameThreshold, boolean nameClaimed) {
        return observed || (catches > 0 && (catches >= nameThreshold || nameClaimed));
    }

    /**
     * The five sections in the order they open for these thresholds: sorted by threshold, ties in
     * enum order (the sort is stable). Pure.
     */
    public static List<EncyclopediaRewardSection> unlockOrder(FishEncyclopediaEntry.UnlockThresholds thresholds) {
        List<EncyclopediaRewardSection> order = new ArrayList<>(List.of(EncyclopediaRewardSection.values()));
        order.sort(Comparator.comparingInt(section -> section.threshold(thresholds)));
        return order;
    }

    /**
     * The zone that names a pool: DEEP_OCEAN, CAVE, HIGH_ALTITUDE and NETHER are more specific
     * than the OCEAN or RIVER they are layered on, so they win. Null for an empty pool.
     */
    public static FishProfile.Zone poolZone(Set<FishProfile.Zone> pool) {
        for (FishProfile.Zone specific : List.of(FishProfile.Zone.DEEP_OCEAN, FishProfile.Zone.CAVE,
                FishProfile.Zone.HIGH_ALTITUDE, FishProfile.Zone.NETHER)) {
            if (pool.contains(specific)) return specific;
        }
        if (pool.contains(FishProfile.Zone.OCEAN)) return FishProfile.Zone.OCEAN;
        if (pool.contains(FishProfile.Zone.RIVER)) return FishProfile.Zone.RIVER;
        return null;
    }

    /**
     * Display name of a biome condition: a tag by its friendly name
     * ({@code fishtastic.biome_tag.<ns>.<path>}), a direct biome list by its first biome's
     * vanilla name ({@code biome.<ns>.<path>}).
     */
    public static Component biomeName(FishProfile.BiomeWeight weight) {
        return weight.biome().unwrap().map(
                tag -> Component.translatable("fishtastic.biome_tag." + tag.location().getNamespace() + "." + tag.location().getPath()),
                list -> {
                    for (Holder<?> holder : list) {
                        var biomeKey = holder.unwrapKey();
                        if (biomeKey.isPresent()) {
                            ResourceLocation id = biomeKey.get().location();
                            return Component.translatable("biome." + id.getNamespace() + "." + id.getPath());
                        }
                    }
                    return Component.literal("?");
                });
    }

    private static String milestoneKey(EncyclopediaRewardSection section) {
        String name = switch (section) {
            case NAME_REVEAL -> "name";
            case STATS -> "size";
            case TYPES -> "bait";
            case SPAWN_CONDITIONS -> "habits";
            case LORE -> "notes";
        };
        return "screen.fishtastic.encyclopedia.milestone." + name;
    }

    private static boolean unlocked(ResourceLocation fishId, EncyclopediaRewardSection section, int catches,
                                    FishEncyclopediaEntry.UnlockThresholds thresholds) {
        return catches >= section.threshold(thresholds) || FishEncyclopediaClientCache.isRewardClaimed(fishId, section);
    }

    private static Rarity rarity(RegistryAccess access, ResourceLocation fishId) {
        EncyclopediaTiers.Rarity rarity = EncyclopediaTiers.rarities(access).get(fishId);
        if (rarity == null || rarity.pool() == null || rarity.share() <= 0f) return null;
        FishProfile.Zone zone = poolZone(rarity.pool());
        if (zone == null) return null;
        return new Rarity(5 - rarity.tier().ordinal(), Math.round(1f / rarity.share()),
                Component.translatable("screen.fishtastic.encyclopedia.pool." + zone.getSerializedName()));
    }

    private static Optional<PersonalBest> personalBest(ResourceLocation fishId) {
        LeaderboardEntry best = FishEncyclopediaClientCache.getPersonalBest(fishId);
        return best == null ? Optional.empty() : Optional.of(new PersonalBest(best.size(), best.quality().getTranslatableName()));
    }

    private static Optional<ServerBest> serverBest(ResourceLocation fishId) {
        LeaderboardEntry best = FishEncyclopediaClientCache.getGlobalBest(fishId);
        if (best == null) return Optional.empty();
        Component holder = best.playerName().<Component>map(Component::literal)
                .orElseGet(() -> Component.translatable("screen.fishtastic.encyclopedia.unknown_player"));
        return Optional.of(new ServerBest(best.size(), holder));
    }

    private static Optional<Fight> fight(RegistryAccess access, FishProfile profile) {
        if (profile.temperament().isEmpty()) return Optional.empty();
        ResourceKey<Temperament> temperamentKey = profile.temperament().get();
        Registry<Temperament> registry;
        try {
            registry = access.registryOrThrow(FishtasticRegistries.TEMPERAMENT_REGISTRY_KEY);
        } catch (Exception e) {
            return Optional.empty();
        }
        return registry.getOptional(temperamentKey).map(t -> new Fight(
                Component.translatable("screen.fishtastic.encyclopedia.temperament." + temperamentKey.location().getPath()),
                (t.difficultyMin() + t.difficultyMax()) / 2f));
    }

    /**
     * Every registered bait that raises this species' odds: one with an affinity group
     * (multiplier above 1) containing the fish, or a required/exclusive pool containing it.
     * Registry order.
     */
    private static List<ItemStack> baitsFor(Item fish) {
        List<ItemStack> baits = new ArrayList<>();
        for (Item candidate : BuiltInRegistries.ITEM) {
            BaitEffect effect = candidate.components().get(FishtasticDataComponents.BAIT_EFFECT.value());
            if (effect == null) continue;
            boolean helps = effect.exclusiveFishPool().map(tag -> fish.builtInRegistryHolder().is(tag)).orElse(false);
            for (BaitEffect.FishGroupAffinity affinity : effect.fishGroupAffinities()) {
                if (affinity.multiplier() > 1.0f && fish.builtInRegistryHolder().is(affinity.group())) helps = true;
            }
            if (helps) baits.add(new ItemStack(candidate));
        }
        return List.copyOf(baits);
    }
}
