package grill24.fishtastic.item;

import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.component.BaitEffect;
import grill24.fishtastic.component.CharmEffect;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.component.HookEffect;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.util.FishQualityHelper;
import grill24.fishtastic.util.ItemSizeHelper;
import grill24.fishtastic.util.MathUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.storage.loot.LootParams;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public class FishtasticFishItem extends Item {

    public record GaussianModifier(
            float meanOffset, float stdDevOffset,
            float meanMultiplier, float stdDevMultiplier) {}

    public static final Map<FishQuality.Quality, List<GaussianModifier>> QUALITY_SIZE_MODIFIERS = Map.of(
            FishQuality.Quality.COMMON,    List.of(new GaussianModifier(0.0f,  0.0f, 1.0f, 1.0f)),
            FishQuality.Quality.UNCOMMON,  List.of(new GaussianModifier(10.0f, 0.0f, 1.1f, 1.1f)),
            FishQuality.Quality.RARE,      List.of(new GaussianModifier(20.0f, 0.0f, 1.2f, 1.2f)),
            FishQuality.Quality.EPIC,      List.of(new GaussianModifier(45.0f, 0.0f, 1.3f, 1.3f)),
            FishQuality.Quality.LEGENDARY, List.of(new GaussianModifier(75.0f, 0.0f, 1.5f, 1.5f))
    );

    public static final Map<FishQuality.Quality, Integer> QUALITY_WEIGHTS = Map.of(
            FishQuality.Quality.COMMON,    6400,
            FishQuality.Quality.UNCOMMON,  2500,
            FishQuality.Quality.RARE,       750,
            FishQuality.Quality.EPIC,       250,
            FishQuality.Quality.LEGENDARY,  100
    );
    public static final int TOTAL_BASE_WEIGHT = QUALITY_WEIGHTS.values().stream().mapToInt(Integer::intValue).sum();

    public FishtasticFishItem(Properties properties) {
        super(properties);
    }

    protected int getAdditionalWeight(LootParams lootParams) {
        return 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                 Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        BaitEffect baitEffect = BaitEffect.fromStack(stack);
        if (baitEffect != null) {
            baitEffect.tooltipLines().forEach(builder);
        }
        HookEffect hookEffect = stack.get(FishtasticDataComponents.HOOK_EFFECT.value());
        if (hookEffect != null) {
            hookEffect.tooltipLines().forEach(builder);
        }
        CharmEffect charmEffect = stack.get(FishtasticDataComponents.CHARM_EFFECT.value());
        if (charmEffect != null) {
            charmEffect.tooltipLines().forEach(builder);
        }
    }

    // ----- Loot Sampling -----

    public static ItemStack sampleRandomFish(
            RandomSource randomSource,
            LootParams lootParams,
            List<Holder<Item>> fishItems,
            Registry<FishProfile> fishProfileRegistry,
            Holder<Biome> biome,
            FishProfile.TimeOfDay timeOfDay,
            FishProfile.WeatherCondition weather,
            net.minecraft.world.level.MoonPhase moonPhase,
            float qualityBias,
            @Nullable BaitEffect baitEffect,
            @Nullable CharmEffect charmEffect
    ) {
        if (fishItems.isEmpty()) return ItemStack.EMPTY;

        List<Integer> weights = fishItems.stream()
                .map(h -> getFishingLootWeight(h, lootParams, fishProfileRegistry, biome, timeOfDay, weather, moonPhase, baitEffect, charmEffect))
                .toList();
        int totalWeight = weights.stream().mapToInt(Integer::intValue).sum();
        if (totalWeight <= 0) return ItemStack.EMPTY;

        int r = randomSource.nextInt(totalWeight);
        for (int i = 0; i < fishItems.size(); i++) {
            r -= weights.get(i);
            if (r < 0) {
                Holder<Item> fishHolder = fishItems.get(i);
                ItemStack stack = new ItemStack(fishHolder);
                FishQuality.Quality quality = sampleRandomQuality(randomSource, qualityBias);

                float mean = FishProfile.DEFAULT_MEAN_SIZE;
                float stdDev = FishProfile.DEFAULT_STDDEV_SIZE;
                Optional<ResourceKey<Item>> itemKey = fishHolder.unwrapKey();
                if (itemKey.isPresent()) {
                    ResourceKey<FishProfile> profileKey = ResourceKey.create(
                            FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, itemKey.get().identifier());
                    FishProfile profile = fishProfileRegistry.getOptional(profileKey).orElse(null);
                    if (profile != null) {
                        mean = profile.size().mean();
                        stdDev = profile.size().stdDev();
                    }
                }

                float size = getRandomSize(randomSource, quality, mean, stdDev);
                ItemSizeHelper.setSize(stack, size);
                FishQualityHelper.setQuality(stack, quality);
                stack.set(FishtasticDataComponents.FISH_QUALITY.value(), new FishQuality(quality));
                return stack;
            }
        }

        return new ItemStack(fishItems.getFirst());
    }

    public static int getFishingLootWeight(
            Holder<Item> item,
            LootParams lootParams,
            Registry<FishProfile> fishProfileRegistry,
            Holder<Biome> biome,
            FishProfile.TimeOfDay timeOfDay,
            FishProfile.WeatherCondition weather,
            net.minecraft.world.level.MoonPhase moonPhase,
            @Nullable BaitEffect baitEffect,
            @Nullable CharmEffect charmEffect
    ) {
        float nightMult = (charmEffect != null && timeOfDay == FishProfile.TimeOfDay.NIGHT)
                ? charmEffect.nightMultiplierBonus() : 1.0f;
        float charmGroupMult = 1.0f;
        if (charmEffect != null) {
            for (BaitEffect.FishGroupAffinity affinity : charmEffect.fishGroupAffinities()) {
                charmGroupMult *= item.is(affinity.group()) ? affinity.multiplier() : affinity.nonMemberMultiplier();
            }
        }

        if (item.value() instanceof FishtasticFishItem fishItem) {
            int baseWeight = FishProfile.DEFAULT_BASE_WEIGHT + fishItem.getAdditionalWeight(lootParams);
            float environmentMult = 1.0f;

            FishProfile profile = getProfile(item, fishProfileRegistry).orElse(null);
            if (profile != null) {
                baseWeight = profile.baseWeight() + fishItem.getAdditionalWeight(lootParams);
                environmentMult = profile.computeEnvironmentMultiplier(biome, timeOfDay, weather, moonPhase);
            }

            float baitMult = baitEffect != null ? baitEffect.modFishMultiplier() : 1.0f;
            float groupMult = 1.0f;
            float rarityWeight = baseWeight;
            if (baitEffect != null) {
                // Pool-wide counterpart to the per-group flattening below — for generalist baits
                // with no FishGroupAffinity to scope to (e.g. Gummy Worms), applied unconditionally.
                float poolT = baitEffect.rarityFlattening();
                if (poolT != 1.0f) {
                    rarityWeight = (float) (Math.pow(rarityWeight, poolT) * Math.pow(FishProfile.DEFAULT_BASE_WEIGHT, 1.0 - poolT));
                }
                for (BaitEffect.FishGroupAffinity affinity : baitEffect.fishGroupAffinities()) {
                    if (item.is(affinity.group())) {
                        // Temperature-style flattening anchored at DEFAULT_BASE_WEIGHT: at
                        // rarityExponent == 1 this is a no-op (weight^1 * ref^0 == weight); as it
                        // drops toward 0, weights above the anchor get pulled down and weights
                        // below it get pulled up, compressing the group's internal rarity spread
                        // without the group's overall magnitude collapsing toward zero.
                        float t = affinity.rarityExponent();
                        rarityWeight = (float) (Math.pow(rarityWeight, t) * Math.pow(FishProfile.DEFAULT_BASE_WEIGHT, 1.0 - t));
                        groupMult *= affinity.multiplier();
                    } else {
                        groupMult *= affinity.nonMemberMultiplier();
                    }
                }
            }
            return Math.max(1, (int) (rarityWeight * environmentMult * baitMult * groupMult * nightMult * charmGroupMult));
        }

        // Vanilla fish weights — vanilla is excluded from the pool entirely whenever any bait
        // is equipped (see FishingMinigameManager's pool filter), so no vanilla-suppression
        // multiplier is needed here; this branch only ever runs for the bare-hook (NO_BAIT) case.
        if (item.value().equals(Items.COD) || item.value().equals(Items.SALMON)) {
            return Math.max(1, (int) (200 * nightMult * charmGroupMult));
        } else if (item.value().equals(Items.TROPICAL_FISH)) {
            return Math.max(1, (int) (50 * nightMult * charmGroupMult));
        } else if (item.value().equals(Items.PUFFERFISH)) {
            return Math.max(1, (int) (25 * nightMult * charmGroupMult));
        }
        return 1;
    }

    /** Looks up the {@link FishProfile} registered under the same id as this item, if any. */
    public static Optional<FishProfile> getProfile(Holder<Item> item, Registry<FishProfile> fishProfileRegistry) {
        return item.unwrapKey()
                .map(key -> ResourceKey.create(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, key.identifier()))
                .flatMap(fishProfileRegistry::getOptional);
    }

    /**
     * Hard location gate. A fish with no registered profile is treated as zone-unrestricted
     * (fail-open) — this only matters for third-party datapack fish that never had a profile
     * at all; every one of fishtastic's own fish declares an explicit {@code zones} list.
     * {@code currentZones} may contain several simultaneously-applicable zones (see
     * {@link FishProfile.Zone#resolve}); the fish is eligible if any of its declared zones overlap.
     */
    public static boolean isZoneEligible(Holder<Item> item, Registry<FishProfile> fishProfileRegistry, Set<FishProfile.Zone> currentZones) {
        return getProfile(item, fishProfileRegistry)
                .map(profile -> !Collections.disjoint(profile.zones(), currentZones))
                .orElse(true);
    }

    public static float getRandomSize(RandomSource randomSource, FishQuality.Quality quality, float baseMeanSize, float baseStdDevSize) {
        List<GaussianModifier> modifiers = QUALITY_SIZE_MODIFIERS.get(quality);
        for (GaussianModifier modifier : modifiers) {
            baseMeanSize += modifier.meanOffset;
            baseStdDevSize = baseStdDevSize * modifier.meanMultiplier + modifier.stdDevOffset;
        }
        return MathUtil.randomGaussian(randomSource, baseMeanSize, baseStdDevSize);
    }

    public static FishQuality.Quality sampleRandomQuality(RandomSource randomSource, float qualityBias) {
        FishQuality.Quality[] qualities = FishQuality.Quality.values();
        int[] weights = new int[qualities.length];
        int total = 0;
        for (int i = 0; i < qualities.length; i++) {
            weights[i] = (int) (QUALITY_WEIGHTS.get(qualities[i]) * (1.0f + qualityBias * i));
            total += weights[i];
        }

        int r = randomSource.nextInt(Math.max(1, total));
        for (int i = 0; i < qualities.length; i++) {
            r -= weights[i];
            if (r < 0) return qualities[i];
        }
        return FishQuality.Quality.COMMON;
    }

    public static FishQuality.Quality sampleRandomQuality(RandomSource randomSource) {
        return sampleRandomQuality(randomSource, 0.0f);
    }

    public static FishtasticFishItem create(Item.Properties properties) {
        return new FishtasticFishItem(properties);
    }

    public static FishtasticFishItem createDefault(Item.Properties properties) {
        return new FishtasticFishItem(properties);
    }
}
