package grill24.fishtastic.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.FishtasticRegistries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

public record ShopEntry(
        String displayName,
        String description,
        int cost,
        float weight,
        List<ShopReward> reward,
        int maxPurchases
) {
    public static final int DAILY_SHOP_COUNT = 4;
    /** Floor applied to {@link #weight} so a zero/negative weight can't blow up the 1/weight exponent below. */
    private static final float MIN_WEIGHT = 0.0001f;

    /**
     * A reward entry stored as a raw identifier + count so it can be decoded at
     * registry-load time without needing item data-components to be ready yet.
     * ItemStacks are constructed lazily when the reward is actually granted or displayed.
     */
    public record ShopReward(Identifier itemId, int count) {
        public static final Codec<ShopReward> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("id").forGetter(ShopReward::itemId),
                Codec.INT.optionalFieldOf("count", 1).forGetter(ShopReward::count)
        ).apply(i, ShopReward::new));

        public ItemStack toItemStack() {
            return BuiltInRegistries.ITEM.getOptional(itemId)
                    .map(item -> new ItemStack(item, count))
                    .orElse(ItemStack.EMPTY);
        }
    }

    public static final Codec<ShopEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("display_name", "").forGetter(ShopEntry::displayName),
            Codec.STRING.optionalFieldOf("description", "").forGetter(ShopEntry::description),
            Codec.INT.fieldOf("cost").forGetter(ShopEntry::cost),
            Codec.FLOAT.optionalFieldOf("weight", 1.0f).forGetter(ShopEntry::weight),
            ShopReward.CODEC.listOf().optionalFieldOf("reward", List.of()).forGetter(ShopEntry::reward),
            Codec.INT.optionalFieldOf("max_purchases", 0).forGetter(ShopEntry::maxPurchases)
    ).apply(i, ShopEntry::new));

    /**
     * Returns today's active shop entries, deterministic for a given day and weighted
     * without replacement (Efraimidis-Spirakis: rank each entry by {@code random()^(1/weight)}
     * and take the top {@link #DAILY_SHOP_COUNT}). Entries are drawn from a fixed, id-sorted
     * order so the same entry always consumes the same random draw for a given day regardless
     * of registry iteration order. Lower {@link ShopEntry#weight} means less likely to appear,
     * so a family of variant items (e.g. lamp colors) can each carry a small weight and not
     * crowd out the rest of the shop.
     */
    public static Set<ResourceKey<ShopEntry>> getActiveDailyShop(Registry<ShopEntry> registry, long currentDay) {
        List<ResourceKey<ShopEntry>> keys = registry.entrySet().stream()
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing(k -> k.identifier().toString()))
                .toList();

        Random random = new Random(currentDay);
        Map<ResourceKey<ShopEntry>, Double> priority = new LinkedHashMap<>();
        for (ResourceKey<ShopEntry> key : keys) {
            float weight = Math.max(registry.getValue(key).weight(), MIN_WEIGHT);
            priority.put(key, Math.pow(random.nextDouble(), 1.0 / weight));
        }

        return keys.stream()
                .sorted(Comparator.<ResourceKey<ShopEntry>>comparingDouble(priority::get).reversed())
                .limit(DAILY_SHOP_COUNT)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
