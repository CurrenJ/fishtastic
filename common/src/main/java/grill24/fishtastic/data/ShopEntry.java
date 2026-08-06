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

public record ShopEntry(
        String displayName,
        String description,
        int cost,
        float weight,
        List<ShopReward> reward,
        int dailyMaxPurchases,
        boolean isCharm
) {
    public static final int DAILY_SHOP_COUNT = 4;
    /** Fixed token cost to manually reroll today's active shop entries — see {@link grill24.fishtastic.network.RefreshShopPacket}. */
    public static final int SHOP_REFRESH_COST = 25;
    /**
     * Chance, per shop draw, that one randomly-chosen slot among the {@link #DAILY_SHOP_COUNT}
     * main-pool picks is swapped out for a weighted draw from the charm-only pool
     * ({@link #isCharm} entries). Charms are excluded from the main draw entirely — see
     * {@link #getActiveDailyShop(Registry, long, int)} — so this is the sole way a charm
     * appears in the shop, giving it an exact, non-diluted appearance rate independent of
     * how many other charms/cosmetics exist in the pool.
     */
    public static final float CHARM_REPLACE_CHANCE = 0.4f;
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
            // Resets to 0 every in-game day alongside the shop rotation (see PlayerQuestState#resetDailyPurchasesIfNeeded) -
            // not a lifetime cap, so it paces stockpiling without ever permanently locking an entry away.
            Codec.INT.optionalFieldOf("daily_max_purchases", 0).forGetter(ShopEntry::dailyMaxPurchases),
            // Charm entries are excluded from the main weighted draw and only appear via the
            // CHARM_REPLACE_CHANCE roll — see getActiveDailyShop.
            Codec.BOOL.optionalFieldOf("is_charm", false).forGetter(ShopEntry::isCharm)
    ).apply(i, ShopEntry::new));

    /**
     * Returns today's active shop entries, deterministic for a given day. {@link #DAILY_SHOP_COUNT}
     * entries are drawn from the non-charm pool without replacement (Efraimidis-Spirakis: rank
     * each entry by {@code random()^(1/weight)} and take the top N). Entries are drawn from a
     * fixed, id-sorted order so the same entry always consumes the same random draw for a given
     * day regardless of registry iteration order. Lower {@link ShopEntry#weight} means less
     * likely to appear, so a family of variant items (e.g. lamp colors) can each carry a small
     * weight and not crowd out the rest of the shop.
     * <p>
     * Separately, with probability {@link #CHARM_REPLACE_CHANCE}, one randomly-chosen slot from
     * that draw is swapped for a weighted pick from the charm-only pool ({@link #isCharm} entries).
     * This keeps charms from diluting/being diluted by the much larger cosmetic/bait pool while
     * giving them a fixed, predictable appearance rate.
     */
    public static Set<ResourceKey<ShopEntry>> getActiveDailyShop(Registry<ShopEntry> registry, long currentDay) {
        return getActiveDailyShop(registry, currentDay, 0);
    }

    /**
     * Same as {@link #getActiveDailyShop(Registry, long)}, but mixes in a per-player
     * {@code refreshNonce} (see {@link grill24.fishtastic.server.PlayerQuestState#getShopRefreshCount()})
     * so a manual reroll changes the drawn set without waiting for the next in-game day.
     */
    public static Set<ResourceKey<ShopEntry>> getActiveDailyShop(Registry<ShopEntry> registry, long currentDay, int refreshNonce) {
        List<ResourceKey<ShopEntry>> sortedKeys = registry.entrySet().stream()
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing(k -> k.identifier().toString()))
                .toList();

        List<ResourceKey<ShopEntry>> mainKeys = new ArrayList<>();
        List<ResourceKey<ShopEntry>> charmKeys = new ArrayList<>();
        for (ResourceKey<ShopEntry> key : sortedKeys) {
            (registry.getValue(key).isCharm() ? charmKeys : mainKeys).add(key);
        }

        long seed = currentDay ^ (refreshNonce * 0x9E3779B97F4A7C15L);
        Random random = new Random(seed);

        List<ResourceKey<ShopEntry>> slots = new ArrayList<>(
                weightedDrawWithoutReplacement(registry, mainKeys, random, DAILY_SHOP_COUNT));

        if (!slots.isEmpty() && !charmKeys.isEmpty() && random.nextFloat() < CHARM_REPLACE_CHANCE) {
            ResourceKey<ShopEntry> charm = weightedDrawWithoutReplacement(registry, charmKeys, random, 1).getFirst();
            slots.set(random.nextInt(slots.size()), charm);
        }

        return new LinkedHashSet<>(slots);
    }

    /** Ranks {@code keys} by {@code random()^(1/weight)} and returns the top {@code count}. */
    private static List<ResourceKey<ShopEntry>> weightedDrawWithoutReplacement(
            Registry<ShopEntry> registry, List<ResourceKey<ShopEntry>> keys, Random random, int count) {
        Map<ResourceKey<ShopEntry>, Double> priority = new LinkedHashMap<>();
        for (ResourceKey<ShopEntry> key : keys) {
            float weight = Math.max(registry.getValue(key).weight(), MIN_WEIGHT);
            priority.put(key, Math.pow(random.nextDouble(), 1.0 / weight));
        }

        return keys.stream()
                .sorted(Comparator.<ResourceKey<ShopEntry>>comparingDouble(priority::get).reversed())
                .limit(count)
                .toList();
    }
}
