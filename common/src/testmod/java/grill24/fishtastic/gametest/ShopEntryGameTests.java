package grill24.fishtastic.gametest;

import com.mojang.serialization.Lifecycle;
import grill24.FishtasticRegistries;
import grill24.fishtastic.data.ShopEntry;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

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
        return new ShopEntry("", "", 10, List.of(), 0);
    }

    private static Registry<ShopEntry> buildRegistry(int count) {
        MappedRegistry<ShopEntry> registry = new MappedRegistry<>(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY, Lifecycle.stable());
        for (int i = 0; i < count; i++) {
            ResourceKey<ShopEntry> key = ResourceKey.create(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY, Identifier.fromNamespaceAndPath("fishtastic", "entry_" + i));
            registry.register(key, entry(), RegistrationInfo.BUILT_IN);
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
}
