package grill24.fishtastic.item;

import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.util.FishQualityHelper;
import grill24.fishtastic.util.ItemSizeHelper;
import grill24.fishtastic.util.MathUtil;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;

import java.util.List;
import java.util.Map;

public class FishtasticFishItem extends Item {
    public record GaussianModifier(
            float meanOffset, float stdDevOffset,
            float meanMultiplier, float stdDevMultiplier) {}

    public static Map<FishQuality.Quality, List<GaussianModifier>> QUALITY_SIZE_MODIFIERS = Map.of(
            FishQuality.Quality.COMMON, List.of(new GaussianModifier(0.0f, 0.0f, 1.0f, 1.0f)),
            FishQuality.Quality.UNCOMMON, List.of(new GaussianModifier(10.0f, 0.0f, 1.1f, 1.1f)),
            FishQuality.Quality.RARE, List.of(new GaussianModifier(20.0f, 0.0f, 1.2f, 1.2f)),
            FishQuality.Quality.EPIC, List.of(new GaussianModifier(45.0f, 0.0f, 1.3f, 1.3f)),
            FishQuality.Quality.LEGENDARY, List.of(new GaussianModifier(75.0f, 0.0f, 1.5f, 1.5f))
    );

    // ----- Chance Of Each Quality -----
    public static Map<FishQuality.Quality, Integer> QUALITY_WEIGHTS = Map.of(
            FishQuality.Quality.COMMON, 6400,
            FishQuality.Quality.UNCOMMON, 2500,
            FishQuality.Quality.RARE, 750,
            FishQuality.Quality.EPIC, 250,
            FishQuality.Quality.LEGENDARY, 100
    );
    public static int TOTAL_BASE_WEIGHT = QUALITY_WEIGHTS.values().stream().mapToInt(Integer::intValue).sum();

    public FishtasticFishItem(Properties properties) {
        super(properties);
    }

    // ----- Fishing Loot Weight Calculation -----

    protected int getAdditionalWeight(LootParams lootParams) {
        return 0;
    }

    // ----- Size Calculation -----

    public static final float DEFAULT_BASE_MEAN_SIZE = 50.0f;
    public static final float DEFAULT_BASE_STDDEV_SIZE = 15.0f;

    protected float getBaseMeanSize() {
        return DEFAULT_BASE_MEAN_SIZE;
    }
    protected float getBaseStdDevSize() {
        return DEFAULT_BASE_STDDEV_SIZE;
    }

    public static float getRandomSize(RandomSource randomSource, FishQuality.Quality quality, float baseMeanSize, float baseStdDevSize) {
        List<GaussianModifier> modifiers = QUALITY_SIZE_MODIFIERS.get(quality);
        for (GaussianModifier modifier : modifiers) {
            baseMeanSize += modifier.meanOffset;
            baseStdDevSize = baseStdDevSize * modifier.meanMultiplier + modifier.stdDevOffset;
        }

        return MathUtil.randomGaussian(randomSource, baseMeanSize, baseStdDevSize);
    }

    // ----- Loot Sampling Helpers -----

    public static ItemStack sampleRandomFish(RandomSource randomSource, LootParams lootParams, List<Holder<Item>> fishItems) {
        if(fishItems.isEmpty()) return ItemStack.EMPTY;

        // Calculate total weight
        List<Integer> weights = fishItems.stream().map(h -> getFishingLootWeight(h, lootParams)).toList();
        int totalWeight = weights.stream().mapToInt(Integer::intValue).sum();

        // Sample a random fish based on weights
        int r = randomSource.nextInt(totalWeight);
        for(int i = 0; i < fishItems.size(); i++) {
            r -= weights.get(i);
            if(r < 0) {
                ItemStack stack = new ItemStack(fishItems.get(i));
                FishQuality.Quality quality = sampleRandomQuality(randomSource);

                float size;
                if (stack.getItem() instanceof FishtasticFishItem fishItem) {
                    size = getRandomSize(randomSource, quality, fishItem.getBaseMeanSize(), fishItem.getBaseStdDevSize());
                } else {
                    size = getRandomSize(randomSource, quality, DEFAULT_BASE_MEAN_SIZE, DEFAULT_BASE_STDDEV_SIZE);
                }
                ItemSizeHelper.setSize(stack, size);
                FishQualityHelper.setQuality(stack, quality);

                // Set the quality component
                stack.set(FishtasticDataComponents.FISH_QUALITY.value(), new FishQuality(quality));

                return stack;
            }
        }

        // Fallback (should not reach here)
        return new ItemStack(fishItems.getFirst());
    }

    private static int getFishingLootWeight(Holder<Item> item, LootParams lootParams) {
        if(item.value() instanceof FishtasticFishItem fishItem) {
            return 10 + fishItem.getAdditionalWeight(lootParams);
        } else if(item.value().equals(Items.COD) || item.value().equals(Items.SALMON)) {
            return 200;
        } else if(item.value().equals(Items.TROPICAL_FISH)) {
            return 50;
        } else if(item.value().equals(Items.PUFFERFISH)) {
            return 25;
        }
        return 1;
    }

    public static FishQuality.Quality sampleRandomQuality(RandomSource randomSource) {
        int r = randomSource.nextInt(TOTAL_BASE_WEIGHT);
        for(FishQuality.Quality quality : FishQuality.Quality.values()) {
            r -= QUALITY_WEIGHTS.get(quality);
            if(r < 0) {
                return quality;
            }
        }

        // Fallback (should not reach here)
        return FishQuality.Quality.COMMON;
    }
}
