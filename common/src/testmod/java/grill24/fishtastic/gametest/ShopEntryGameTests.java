package grill24.fishtastic.gametest;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import grill24.FishtasticRegistries;
import grill24.fishtastic.data.ShopEntry;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Server-side game tests for ShopEntry.getActiveDailyShop — same shape as
 * QuestTracker.getActiveDailies (deterministic per-day shuffle, capped selection),
 * using the same throwaway-MappedRegistry fixture pattern.
 * All methods are pure GameTestHelper consumers — no platform annotations here.
 */
public final class ShopEntryGameTests {

    private ShopEntryGameTests() {}

    private static ShopEntry entry() {
        return entry(1.0f);
    }

    private static ShopEntry entry(float weight) {
        return new ShopEntry("", "", 10, weight, List.of(), 0);
    }

    private static Registry<ShopEntry> buildRegistry(int count) {
        List<Float> weights = new ArrayList<>();
        for (int i = 0; i < count; i++) weights.add(1.0f);
        return buildRegistry(weights);
    }

    /** Builds a fixture registry where entry_<i> carries weights.get(i). */
    private static Registry<ShopEntry> buildRegistry(List<Float> weights) {
        MappedRegistry<ShopEntry> registry = new MappedRegistry<>(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY, Lifecycle.stable());
        for (int i = 0; i < weights.size(); i++) {
            ResourceKey<ShopEntry> key = ResourceKey.create(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY, Identifier.fromNamespaceAndPath("fishtastic", "entry_" + i));
            registry.register(key, entry(weights.get(i)), RegistrationInfo.BUILT_IN);
        }
        return registry;
    }

    public static void getActiveDailyShopIsStablePerDay(GameTestHelper helper) {
        Registry<ShopEntry> registry = buildRegistry(10);

        Set<ResourceKey<ShopEntry>> first = ShopEntry.getActiveDailyShop(registry, 42L);
        Set<ResourceKey<ShopEntry>> second = ShopEntry.getActiveDailyShop(registry, 42L);

        helper.assertTrue(first.equals(second), "getActiveDailyShop must be deterministic for the same day");
        helper.succeed();
    }

    public static void getActiveDailyShopNeverExceedsCap(GameTestHelper helper) {
        Registry<ShopEntry> registry = buildRegistry(10);

        Set<ResourceKey<ShopEntry>> active = ShopEntry.getActiveDailyShop(registry, 7L);

        helper.assertTrue(
            active.size() == ShopEntry.DAILY_SHOP_COUNT,
            "With 10 entries, exactly DAILY_SHOP_COUNT (" + ShopEntry.DAILY_SHOP_COUNT + ") must be selected, got " + active.size()
        );
        helper.succeed();
    }

    public static void getActiveDailyShopCapsAtRegistrySizeWhenSmallerThanCount(GameTestHelper helper) {
        Registry<ShopEntry> registry = buildRegistry(2);

        Set<ResourceKey<ShopEntry>> active = ShopEntry.getActiveDailyShop(registry, 1L);

        helper.assertTrue(active.size() == 2, "With fewer entries than the cap, all of them must be active, got " + active.size());
        helper.succeed();
    }

    /**
     * A single heavily-weighted entry against a crowd of near-zero-weight ones must win
     * the top-DAILY_SHOP_COUNT ranking on the overwhelming majority of days — this is what
     * lets a variant family (e.g. lamp colors) carry a small weight each without crowding
     * out the rest of the shop, and what a broken weight codec/ranking would silently defeat.
     */
    public static void getActiveDailyShopWeightBiasesSelectionTowardHeavierEntries(GameTestHelper helper) {
        List<Float> weights = new ArrayList<>();
        weights.add(1000f);
        for (int i = 0; i < 19; i++) weights.add(0.0001f);
        Registry<ShopEntry> registry = buildRegistry(weights);
        ResourceKey<ShopEntry> heavyKey = ResourceKey.create(
            FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY, Identifier.fromNamespaceAndPath("fishtastic", "entry_0"));

        int trials = 100;
        int heavySelectedCount = 0;
        for (long day = 0; day < trials; day++) {
            if (ShopEntry.getActiveDailyShop(registry, day).contains(heavyKey)) {
                heavySelectedCount++;
            }
        }

        helper.assertTrue(heavySelectedCount >= trials * 0.9,
            "Heavy-weight entry should dominate selection across days, got " + heavySelectedCount + "/" + trials);
        helper.succeed();
    }

    /** weight=0 or negative must not blow up the 1/weight exponent (see ShopEntry.MIN_WEIGHT). */
    public static void getActiveDailyShopHandlesNonPositiveWeightWithoutError(GameTestHelper helper) {
        Registry<ShopEntry> registry = buildRegistry(List.of(0f, -5f, 1f, 2f, 0f));

        Set<ResourceKey<ShopEntry>> active = ShopEntry.getActiveDailyShop(registry, 3L);

        helper.assertTrue(active.size() == Math.min(ShopEntry.DAILY_SHOP_COUNT, 5),
            "Non-positive weights must not throw and selection size must still be capped correctly, got " + active.size());
        helper.succeed();
    }

    /** The 7 shop_entry JSON files with no explicit "weight" key rely on this codec default. */
    public static void shopEntryCodecDefaultsWeightToOneWhenAbsent(GameTestHelper helper) {
        JsonObject json = new JsonObject();
        json.addProperty("cost", 10);

        ShopEntry decoded = ShopEntry.CODEC.parse(JsonOps.INSTANCE, json)
            .result()
            .orElseThrow(() -> new IllegalStateException("Failed to decode ShopEntry missing a weight field"));

        helper.assertTrue(decoded.weight() == 1.0f, "weight must default to 1.0 when omitted from JSON, got " + decoded.weight());
        helper.succeed();
    }
}
