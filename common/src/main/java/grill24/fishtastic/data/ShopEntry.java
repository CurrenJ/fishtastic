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
        List<ShopReward> reward,
        int maxPurchases
) {
    public static final int DAILY_SHOP_COUNT = 4;

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
            ShopReward.CODEC.listOf().optionalFieldOf("reward", List.of()).forGetter(ShopEntry::reward),
            Codec.INT.optionalFieldOf("max_purchases", 0).forGetter(ShopEntry::maxPurchases)
    ).apply(i, ShopEntry::new));

    /** Returns today's active shop entries in a stable, deterministic order. */
    public static Set<ResourceKey<ShopEntry>> getActiveDailyShop(Registry<ShopEntry> registry, long currentDay) {
        List<ResourceKey<ShopEntry>> keys = registry.entrySet().stream()
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing(k -> k.identifier().toString()))
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(keys, new Random(currentDay));
        return new LinkedHashSet<>(keys.subList(0, Math.min(DAILY_SHOP_COUNT, keys.size())));
    }
}
