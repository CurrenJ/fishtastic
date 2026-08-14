package grill24.fishtastic.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.FishtasticRegistries;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.IntStream;

public record ShopEntry(
        String displayName,
        String description,
        int cost,
        float weight,
        List<ShopReward> reward,
        int dailyMaxPurchases,
        boolean isCharm,
        /**
         * True for the nine shape-shifting tank entries (trimmed, reinforced, ... shaggy). Excluded
         * from the main weighted draw and only appears via {@link #ANY_TANK_REPLACE_CHANCE} —
         * mirrors {@link #isCharm}'s isolation, so unlocking more shapes over time never dilutes
         * (or gets diluted by) the rest of the shop. See {@link #getActiveDailyShop(Registry, long, int)}.
         */
        boolean isTankShape,
        /**
         * When non-empty, this entry is invisible and unbuyable until *any* of the named quests has
         * been claimed. Backs two different uses: capstone exclusives (a single quest — the capstone
         * grants its reward item once on claim, and the matching gated entry then appears in the
         * rotation so the player can buy further copies at a premium, keeping the item genuinely
         * exclusive while giving the economy a high-value sink); and shape entries (several quests —
         * a shape can be earned from any one of a handful of quests, mirroring
         * {@link grill24.fishtastic.fishtank.FishTankShape#unlockQuests}, so a cool shape isn't
         * hard-gated behind one specific grind).
         */
        List<ResourceKey<Quest>> unlockQuests
) {
    public static final int DAILY_SHOP_COUNT = 4;
    /** Cost of the first reroll in a rotation — see {@link #refreshCost(int)}. */
    public static final int SHOP_REFRESH_BASE_COST = 25;
    /** Doublings applied to {@link #SHOP_REFRESH_BASE_COST} before the price stops climbing (25 → 200). */
    public static final int SHOP_REFRESH_MAX_DOUBLINGS = 3;

    /**
     * Token cost to manually reroll the active shop entries, given how many rerolls the player has
     * already bought this rotation ({@link grill24.fishtastic.server.PlayerQuestState#getShopRefreshCount()},
     * which resets alongside the daily purchase counts).
     *
     * <p>The cost doubles per reroll — 25, 50, 100, 200 — rather than staying flat. At a flat 25 a
     * player with a healthy balance could simply brute-force the rotation until the item they
     * wanted appeared, which made the shop's 4-slot draw decorative and turned a large token
     * balance into "buy anything on demand". Escalating it makes rerolling a real decision and
     * gives the economy a repeatable sink that scales with how much the player is hoarding.
     *
     * <p>Capped at {@link #SHOP_REFRESH_MAX_DOUBLINGS} so the price plateaus instead of running
     * away to unaffordable numbers within a single rotation.
     */
    public static int refreshCost(int refreshesThisRotation) {
        int doublings = Math.min(Math.max(refreshesThisRotation, 0), SHOP_REFRESH_MAX_DOUBLINGS);
        return SHOP_REFRESH_BASE_COST << doublings;
    }
    /**
     * Chance, per shop draw, that one randomly-chosen slot among the {@link #DAILY_SHOP_COUNT}
     * main-pool picks is swapped out for a weighted draw from the charm-only pool
     * ({@link #isCharm} entries). Charms are excluded from the main draw entirely — see
     * {@link #getActiveDailyShop(Registry, long, int)} — so this is the sole way a charm
     * appears in the shop, giving it an exact, non-diluted appearance rate independent of
     * how many other charms/cosmetics exist in the pool.
     * <p>
     * With seven charms sharing the pool uniformly, this sets the wait for one *specific* charm at
     * {@code 1 / (chance / 7)} rotations. At the old 0.4 that was ~17 rotations (~5.8 h of play) to
     * see the charm you actually wanted, which made charms feel like a slot machine rather than a
     * purchase — especially since a charm only lasts 64 casts. 0.55 brings it to ~13 rotations.
     */
    public static final float CHARM_REPLACE_CHANCE = 0.55f;
    /**
     * Chance, per shop draw, of a single coin-flip that decides whether *any one* shape tank appears
     * today at all — not a per-shape chance. On a hit, exactly one randomly-chosen slot is swapped
     * for a weighted draw from the whole tank-shape-only pool ({@link #isTankShape} entries, already
     * filtered to unlocked shapes), so unlocking a 10th shape doesn't raise the odds a shape shows up
     * — it only changes which shape wins that single roll. Same isolation mechanism as
     * {@link #CHARM_REPLACE_CHANCE}, applied to the shape-tank category instead of charms. Shapes are
     * excluded from the main draw entirely (see {@link #getActiveDailyShop(Registry, long, int, Predicate)}),
     * so this is the only way a shape tank appears in the shop — without it, unlocking more shapes
     * over a playthrough would mean shapes collectively eat a growing share of the main pool's slots,
     * the exact "floods the shop as you unlock more" problem this constant exists to avoid. Set below
     * {@link #CHARM_REPLACE_CHANCE} since shapes are a rarer, higher-value unlock than a consumable
     * charm.
     */
    public static final float ANY_TANK_REPLACE_CHANCE = 0.35f;
    /** Floor applied to {@link #weight} so a zero/negative weight can't blow up the 1/weight exponent below. */
    private static final float MIN_WEIGHT = 0.0001f;

    /**
     * A reward entry stored as a raw identifier + count so it can be decoded at
     * registry-load time without needing item data-components to be ready yet.
     * ItemStacks are constructed lazily when the reward is actually granted or displayed.
     */
    public record ShopReward(Identifier itemId, int count, DataComponentPatch components) {
        public static final Codec<ShopReward> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("id").forGetter(ShopReward::itemId),
                Codec.INT.optionalFieldOf("count", 1).forGetter(ShopReward::count),
                // Kept as a raw patch for the same bootstrap reason the id is a raw Identifier —
                // it must not touch an item's default component map at decode time. Lets a gated
                // capstone entry sell back the exact configured item the quest granted (e.g. a
                // fish tank carrying its fishtastic:fish_tank_materials), rather than a plain one.
                DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY)
                        .forGetter(ShopReward::components)
        ).apply(i, ShopReward::new));

        public ShopReward(Identifier itemId, int count) {
            this(itemId, count, DataComponentPatch.EMPTY);
        }

        public ItemStack toItemStack() {
            return BuiltInRegistries.ITEM.getOptional(itemId)
                    .map(item -> {
                        ItemStack stack = new ItemStack(item, count);
                        stack.applyComponents(components);
                        return stack;
                    })
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
            Codec.BOOL.optionalFieldOf("is_charm", false).forGetter(ShopEntry::isCharm),
            // Shape entries are excluded from the main weighted draw and only appear via the
            // ANY_TANK_REPLACE_CHANCE roll — see getActiveDailyShop.
            Codec.BOOL.optionalFieldOf("is_tank_shape", false).forGetter(ShopEntry::isTankShape),
            ResourceKey.codec(FishtasticRegistries.QUEST_REGISTRY_KEY).listOf()
                    .optionalFieldOf("unlock_quests", List.of()).forGetter(ShopEntry::unlockQuests)
    ).apply(i, ShopEntry::new));

    /**
     * Whether this entry is available to a player, given a lookup of whether a quest has been
     * claimed. Ungated entries (no listed quests) are always available; gated entries need only
     * one of their listed quests claimed.
     */
    public boolean isUnlockedFor(Predicate<ResourceKey<Quest>> questClaimed) {
        return unlockQuests.isEmpty() || unlockQuests.stream().anyMatch(questClaimed);
    }

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
     * that draw is swapped for a weighted pick from the charm-only pool ({@link #isCharm} entries),
     * and — independently, in a different slot — with probability {@link #ANY_TANK_REPLACE_CHANCE}
     * another slot is swapped for a weighted pick from the tank-shape-only pool ({@link #isTankShape}
     * entries, filtered to unlocked shapes first). Both keep their pool from diluting/being diluted
     * by the much larger cosmetic/bait pool while giving each a fixed, predictable appearance rate
     * that doesn't grow as the player unlocks more charms or shapes.
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
        return getActiveDailyShop(registry, currentDay, refreshNonce, questKey -> false);
    }

    /**
     * Same again, but with the player's claimed-quest lookup so {@link #unlockQuests} entries can
     * join the draw. Locked entries are filtered out <em>before</em> the weighted pick rather than
     * after, so they never silently consume one of the {@link #DAILY_SHOP_COUNT} slots and leave a
     * player who hasn't unlocked anything staring at a short shop.
     */
    public static Set<ResourceKey<ShopEntry>> getActiveDailyShop(Registry<ShopEntry> registry, long currentDay,
            int refreshNonce, Predicate<ResourceKey<Quest>> questClaimed) {
        List<ResourceKey<ShopEntry>> sortedKeys = registry.entrySet().stream()
                .filter(e -> e.getValue().isUnlockedFor(questClaimed))
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing(k -> k.identifier().toString()))
                .toList();

        List<ResourceKey<ShopEntry>> mainKeys = new ArrayList<>();
        List<ResourceKey<ShopEntry>> charmKeys = new ArrayList<>();
        List<ResourceKey<ShopEntry>> tankShapeKeys = new ArrayList<>();
        for (ResourceKey<ShopEntry> key : sortedKeys) {
            ShopEntry entry = registry.getValue(key);
            if (entry.isCharm()) {
                charmKeys.add(key);
            } else if (entry.isTankShape()) {
                tankShapeKeys.add(key);
            } else {
                mainKeys.add(key);
            }
        }

        long seed = currentDay ^ (refreshNonce * 0x9E3779B97F4A7C15L);
        Random random = new Random(seed);

        List<ResourceKey<ShopEntry>> slots = new ArrayList<>(
                weightedDrawWithoutReplacement(registry, mainKeys, random, DAILY_SHOP_COUNT));

        // Reserved-slot indices already spent by a replacement this draw, so the charm and shape
        // rolls below can't clobber each other's pick — each pool keeps its own independent,
        // undiluted appearance rate.
        Set<Integer> spentSlotIndices = new HashSet<>();

        if (!slots.isEmpty() && !charmKeys.isEmpty() && random.nextFloat() < CHARM_REPLACE_CHANCE) {
            ResourceKey<ShopEntry> charm = weightedDrawWithoutReplacement(registry, charmKeys, random, 1).getFirst();
            int index = random.nextInt(slots.size());
            slots.set(index, charm);
            spentSlotIndices.add(index);
        }

        if (!slots.isEmpty() && !tankShapeKeys.isEmpty() && random.nextFloat() < ANY_TANK_REPLACE_CHANCE) {
            List<Integer> availableIndices = IntStream.range(0, slots.size())
                    .filter(index -> !spentSlotIndices.contains(index))
                    .boxed()
                    .toList();
            if (!availableIndices.isEmpty()) {
                ResourceKey<ShopEntry> tankShape =
                        weightedDrawWithoutReplacement(registry, tankShapeKeys, random, 1).getFirst();
                slots.set(availableIndices.get(random.nextInt(availableIndices.size())), tankShape);
            }
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
