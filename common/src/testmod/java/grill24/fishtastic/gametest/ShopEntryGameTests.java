package grill24.fishtastic.gametest;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import grill24.FishtasticRegistries;
import grill24.fishtastic.data.ShopEntry;
import grill24.fishtastic.network.PurchaseShopEntryPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

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
        return new ShopEntry("", "", 10, weight, List.of(), 0, false, false, List.of());
    }

    private static ShopEntry charmEntry(float weight) {
        return new ShopEntry("", "", 10, weight, List.of(), 0, true, false, List.of());
    }

    private static ShopEntry tankShapeEntry(float weight) {
        return new ShopEntry("", "", 10, weight, List.of(), 0, false, true, List.of());
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

    /** Builds a fixture registry with {@code mainCount} non-charm entries plus {@code charmCount} charm entries, all weight 1.0. */
    private static Registry<ShopEntry> buildRegistryWithCharms(int mainCount, int charmCount) {
        MappedRegistry<ShopEntry> registry = new MappedRegistry<>(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY, Lifecycle.stable());
        for (int i = 0; i < mainCount; i++) {
            ResourceKey<ShopEntry> key = ResourceKey.create(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY, Identifier.fromNamespaceAndPath("fishtastic", "main_" + i));
            registry.register(key, entry(1.0f), RegistrationInfo.BUILT_IN);
        }
        for (int i = 0; i < charmCount; i++) {
            ResourceKey<ShopEntry> key = ResourceKey.create(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY, Identifier.fromNamespaceAndPath("fishtastic", "charm_" + i));
            registry.register(key, charmEntry(1.0f), RegistrationInfo.BUILT_IN);
        }
        return registry;
    }

    /** Builds a fixture registry with {@code mainCount} non-shape entries plus {@code shapeCount} tank-shape entries, all weight 1.0. */
    private static Registry<ShopEntry> buildRegistryWithTankShapes(int mainCount, int shapeCount) {
        MappedRegistry<ShopEntry> registry = new MappedRegistry<>(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY, Lifecycle.stable());
        for (int i = 0; i < mainCount; i++) {
            ResourceKey<ShopEntry> key = ResourceKey.create(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY, Identifier.fromNamespaceAndPath("fishtastic", "main_" + i));
            registry.register(key, entry(1.0f), RegistrationInfo.BUILT_IN);
        }
        for (int i = 0; i < shapeCount; i++) {
            ResourceKey<ShopEntry> key = ResourceKey.create(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY, Identifier.fromNamespaceAndPath("fishtastic", "shape_" + i));
            registry.register(key, tankShapeEntry(1.0f), RegistrationInfo.BUILT_IN);
        }
        return registry;
    }

    private static boolean containsAnyCharm(Set<ResourceKey<ShopEntry>> active) {
        return active.stream().anyMatch(k -> k.identifier().getPath().startsWith("charm_"));
    }

    private static boolean containsAnyTankShape(Set<ResourceKey<ShopEntry>> active) {
        return active.stream().anyMatch(k -> k.identifier().getPath().startsWith("shape_"));
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

    /**
     * With a charm pool present, the fraction of days containing at least one charm should
     * track {@link ShopEntry#CHARM_REPLACE_CHANCE} — this is what a broken roll-order or an
     * accidentally-inverted condition in the replacement branch would silently defeat.
     */
    public static void getActiveDailyShopCharmReplacementRateMatchesConfiguredChance(GameTestHelper helper) {
        Registry<ShopEntry> registry = buildRegistryWithCharms(10, 3);

        int trials = 3000;
        int withCharm = 0;
        for (long day = 0; day < trials; day++) {
            Set<ResourceKey<ShopEntry>> active = ShopEntry.getActiveDailyShop(registry, day);
            helper.assertTrue(active.size() == ShopEntry.DAILY_SHOP_COUNT,
                "Charm replacement must not change the slot count, got " + active.size());
            if (containsAnyCharm(active)) withCharm++;
        }

        double rate = withCharm / (double) trials;
        double expected = ShopEntry.CHARM_REPLACE_CHANCE;
        helper.assertTrue(Math.abs(rate - expected) < 0.05,
            "Charm appearance rate should be close to " + expected + ", got " + rate);
        helper.succeed();
    }

    /** No charm pool at all — the replacement branch must never fire and never crash. */
    public static void getActiveDailyShopNeverReplacesWithoutACharmPool(GameTestHelper helper) {
        Registry<ShopEntry> registry = buildRegistry(10);

        for (long day = 0; day < 200; day++) {
            Set<ResourceKey<ShopEntry>> active = ShopEntry.getActiveDailyShop(registry, day);
            helper.assertTrue(active.size() == ShopEntry.DAILY_SHOP_COUNT,
                "Without a charm pool, the main draw size must be unaffected, got " + active.size());
        }
        helper.succeed();
    }

    /** An empty main pool must not crash the replacement roll's random.nextInt(slots.size()). */
    public static void getActiveDailyShopHandlesEmptyMainPoolWithCharmsOnly(GameTestHelper helper) {
        Registry<ShopEntry> registry = buildRegistryWithCharms(0, 3);

        Set<ResourceKey<ShopEntry>> active = ShopEntry.getActiveDailyShop(registry, 5L);

        helper.assertTrue(active.isEmpty(), "With no main-pool entries, no slots can be drawn or replaced, got " + active.size());
        helper.succeed();
    }

    /**
     * With a tank-shape pool present, the fraction of days containing at least one shape should
     * track {@link ShopEntry#ANY_TANK_REPLACE_CHANCE} — mirrors the charm-rate test above, since
     * this is the same isolation mechanism applied to a second pool. This is what guarantees a
     * shape's appearance rate stays flat no matter how many shapes a player has unlocked, rather
     * than growing as more shape entries join the pool.
     */
    public static void getActiveDailyShopTankShapeReplacementRateMatchesConfiguredChance(GameTestHelper helper) {
        Registry<ShopEntry> registry = buildRegistryWithTankShapes(10, 3);

        int trials = 3000;
        int withShape = 0;
        for (long day = 0; day < trials; day++) {
            Set<ResourceKey<ShopEntry>> active = ShopEntry.getActiveDailyShop(registry, day);
            helper.assertTrue(active.size() == ShopEntry.DAILY_SHOP_COUNT,
                "Tank-shape replacement must not change the slot count, got " + active.size());
            if (containsAnyTankShape(active)) withShape++;
        }

        double rate = withShape / (double) trials;
        double expected = ShopEntry.ANY_TANK_REPLACE_CHANCE;
        helper.assertTrue(Math.abs(rate - expected) < 0.05,
            "Tank-shape appearance rate should be close to " + expected + ", got " + rate);
        helper.succeed();
    }

    /** No tank-shape pool at all — the replacement branch must never fire and never crash. */
    public static void getActiveDailyShopNeverReplacesWithoutATankShapePool(GameTestHelper helper) {
        Registry<ShopEntry> registry = buildRegistry(10);

        for (long day = 0; day < 200; day++) {
            Set<ResourceKey<ShopEntry>> active = ShopEntry.getActiveDailyShop(registry, day);
            helper.assertTrue(active.size() == ShopEntry.DAILY_SHOP_COUNT,
                "Without a tank-shape pool, the main draw size must be unaffected, got " + active.size());
        }
        helper.succeed();
    }

    /** An empty main pool must not crash the shape replacement roll either. */
    public static void getActiveDailyShopHandlesEmptyMainPoolWithTankShapesOnly(GameTestHelper helper) {
        Registry<ShopEntry> registry = buildRegistryWithTankShapes(0, 3);

        Set<ResourceKey<ShopEntry>> active = ShopEntry.getActiveDailyShop(registry, 5L);

        helper.assertTrue(active.isEmpty(), "With no main-pool entries, no slots can be drawn or replaced, got " + active.size());
        helper.succeed();
    }

    /**
     * Charms and shapes are reserved into different slots the same day — a shape roll must never
     * silently overwrite the slot a charm roll just filled (or vice versa), which is exactly what
     * the spent-slot-index bookkeeping in getActiveDailyShop exists to prevent.
     */
    public static void getActiveDailyShopCharmAndTankShapeReplacementsCanCoexist(GameTestHelper helper) {
        MappedRegistry<ShopEntry> registry = new MappedRegistry<>(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY, Lifecycle.stable());
        for (int i = 0; i < 10; i++) {
            registry.register(ResourceKey.create(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY,
                    Identifier.fromNamespaceAndPath("fishtastic", "main_" + i)), entry(1.0f), RegistrationInfo.BUILT_IN);
        }
        for (int i = 0; i < 3; i++) {
            registry.register(ResourceKey.create(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY,
                    Identifier.fromNamespaceAndPath("fishtastic", "charm_" + i)), charmEntry(1.0f), RegistrationInfo.BUILT_IN);
        }
        for (int i = 0; i < 3; i++) {
            registry.register(ResourceKey.create(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY,
                    Identifier.fromNamespaceAndPath("fishtastic", "shape_" + i)), tankShapeEntry(1.0f), RegistrationInfo.BUILT_IN);
        }

        int trials = 3000;
        int withBoth = 0;
        for (long day = 0; day < trials; day++) {
            Set<ResourceKey<ShopEntry>> active = ShopEntry.getActiveDailyShop(registry, day);
            helper.assertTrue(active.size() == ShopEntry.DAILY_SHOP_COUNT,
                "Combined charm+shape replacement must not change the slot count, got " + active.size());
            if (containsAnyCharm(active) && containsAnyTankShape(active)) withBoth++;
        }

        double rate = withBoth / (double) trials;
        double expected = ShopEntry.CHARM_REPLACE_CHANCE * ShopEntry.ANY_TANK_REPLACE_CHANCE;
        helper.assertTrue(Math.abs(rate - expected) < 0.05,
            "Both-present rate should be close to the independent product " + expected + ", got " + rate);
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // PurchaseShopEntryPacket.grantRewards — full-inventory delivery
    // -------------------------------------------------------------------------

    private static ShopEntry entryWithReward(List<ShopEntry.ShopReward> reward) {
        return new ShopEntry("", "", 10, 1.0f, reward, 0, false, false, List.of());
    }

    /**
     * GameTestHelper's mock player is hardcoded to creative (see makeMockServerPlayerInLevel),
     * and creative players bypass the overflow entirely: Inventory.add()'s addResource loop
     * checks player.hasInfiniteMaterials() and force-zeroes an unplaceable stack's count instead
     * of leaving it for the caller to drop, since a creative player doesn't need it back. That
     * would make a full-inventory test misleadingly pass with 0 in inventory and 0 dropped —
     * flip abilities().instabuild off so the mock player exercises the real survival-player path.
     */
    private static void forceSurvivalMode(ServerPlayer player) {
        player.getAbilities().instabuild = false;
    }

    /**
     * GameTestHelper's deprecated mock-player helper drops the player at literal world (0,0,0)
     * rather than inside this test's own structure bounds — a chunk that may not be force-loaded,
     * so an ItemEntity dropped there can inconsistently fail to register in the level's entity
     * storage (observed as flaky drop-detection: player.drop() returns a live entity, but it's
     * absent from a getAllEntities() scan moments later). Relocating into helper.absolutePos(...)
     * puts the player in this test's actively-ticking structure region instead.
     */
    private static void relocateIntoTestStructure(GameTestHelper helper, ServerPlayer player) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    /** Fills every main-inventory slot (0-35) with an unrelated full stack, leaving no room for anything else. */
    private static void fillInventoryCompletely(ServerPlayer player, net.minecraft.world.item.Item fillerItem) {
        for (int i = 0; i < player.getInventory().getNonEquipmentItems().size(); i++) {
            player.getInventory().setItem(i, new ItemStack(fillerItem, fillerItem.getDefaultMaxStackSize()));
        }
    }

    /**
     * getEntitiesOfClass()'s AABB query goes through the level's chunk-based spatial index, which
     * lags a freshly addFreshEntity()'d ItemEntity by up to a tick — invisible from inside the
     * same synchronous test call that just triggered the drop. getAllEntities() reads the level's
     * raw entity storage directly, so it sees the entity immediately. Delta-based (see call sites)
     * because the mock player's fixed (0,0,0) position is shared across every test/run, so stray
     * matching items can already be present in the world before this call.
     */
    private static int countNearbyDroppedItems(ServerPlayer player, net.minecraft.world.item.Item item) {
        int total = 0;
        for (net.minecraft.world.entity.Entity entity : ((net.minecraft.server.level.ServerLevel) player.level()).getAllEntities()) {
            if (entity instanceof ItemEntity itemEntity && itemEntity.getItem().is(item)) {
                total += itemEntity.getItem().getCount();
            }
        }
        return total;
    }

    /**
     * Regression test for the bug where buying a shop entry with a full inventory deducted the
     * player's tokens (via PlayerQuestState.purchase, called before this) but the reward
     * ItemStack silently vanished — Inventory.add() only consumes what fits and leaves the
     * remainder in the stack, which grantRewards must drop at the player's feet instead of
     * discarding.
     */
    public static void grantRewardsDropsLeftoverWhenInventoryIsFull(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        relocateIntoTestStructure(helper, player);
        forceSurvivalMode(player);
        fillInventoryCompletely(player, Items.DIRT);
        // Stray item entities from other tests/prior runs can still share this world region —
        // measure the delta around the grant rather than an absolute nearby count.
        int baselineCount = countNearbyDroppedItems(player, Items.DIAMOND);

        Identifier diamondId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Items.DIAMOND);
        ShopEntry entry = entryWithReward(List.of(new ShopEntry.ShopReward(diamondId, 3)));

        PurchaseShopEntryPacket.grantRewards(player, entry);

        int diamondsInInventory = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(Items.DIAMOND)) diamondsInInventory += stack.getCount();
        }
        helper.assertTrue(diamondsInInventory == 0,
            "Sanity check: a completely full inventory must have no room for the reward, got " + diamondsInInventory);

        // >0 rather than ==3: this shared gametest world persists across runs/tests at the mock
        // player's fixed position, so an unrelated leftover entity from another test can legally
        // despawn in the same instant and shift the exact delta — the regression this guards
        // against is the reward vanishing entirely (delta 0), not the precise leftover count.
        int droppedDelta = countNearbyDroppedItems(player, Items.DIAMOND) - baselineCount;
        helper.assertTrue(droppedDelta > 0,
            "A reward that doesn't fit in a full inventory must be dropped at the player's feet rather than vanishing, got delta " + droppedDelta);
        helper.succeed();
    }

    /** With free space, the reward must go straight into the inventory rather than being dropped. */
    public static void grantRewardsDeliversNormallyWhenInventoryHasSpace(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        int baselineCount = countNearbyDroppedItems(player, Items.DIAMOND);

        Identifier diamondId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Items.DIAMOND);
        ShopEntry entry = entryWithReward(List.of(new ShopEntry.ShopReward(diamondId, 3)));

        PurchaseShopEntryPacket.grantRewards(player, entry);

        int diamondsInInventory = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(Items.DIAMOND)) diamondsInInventory += stack.getCount();
        }
        helper.assertTrue(diamondsInInventory == 3,
            "With room available, the full reward must land in the inventory, got " + diamondsInInventory);
        helper.assertTrue(countNearbyDroppedItems(player, Items.DIAMOND) == baselineCount,
            "With room available, nothing new should be dropped on the ground");
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
