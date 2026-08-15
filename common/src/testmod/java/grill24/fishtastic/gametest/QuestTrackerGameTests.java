package grill24.fishtastic.gametest;

import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.component.FishTankMaterials;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.data.QuestCategory;
import grill24.fishtastic.data.QuestDifficulty;
import grill24.fishtastic.data.QuestObjective;
import grill24.fishtastic.data.QuestReward;
import grill24.fishtastic.fishtank.FishTankShape;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.server.PlayerQuestState;
import grill24.fishtastic.server.QuestTracker;
import grill24.fishtastic.util.FishQualityHelper;
import grill24.fishtastic.util.ItemSizeHelper;
import grill24.fishtastic.util.Utility;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Server-side game tests for QuestTracker's matching rules and daily-shop-style rotation.
 *
 * matchesObjective() was widened from private to public (QuestTracker.java) specifically so
 * these tests can drive each QuestObjective condition independently with hand-built fixtures —
 * no registry, player, or live quest content needed for this part. This mirrors how
 * WormBinBlockEntity already exposes canDeposit()/canAerate() as public purely for testability.
 * All methods are pure GameTestHelper consumers — no platform annotations here.
 */
public final class QuestTrackerGameTests {

    private QuestTrackerGameTests() {}

    /** Placeholder hook-zone set for tests whose objective doesn't set zoneCondition — irrelevant when absent. */
    private static final Set<FishProfile.Zone> ANY_ZONES = Set.of(FishProfile.Zone.RIVER);

    // -------------------------------------------------------------------------
    // QuestObjective fixtures — each sets exactly one condition, rest wildcarded
    // -------------------------------------------------------------------------

    private static QuestObjective wildcardObjective() {
        return new QuestObjective(Optional.empty(), Optional.empty(), Optional.empty(), false, Optional.of(1), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1, false);
    }

    private static QuestObjective targetSpeciesObjective(ResourceKey<Item> species) {
        return new QuestObjective(Optional.of(species), Optional.empty(), Optional.empty(), false, Optional.of(1), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1, false);
    }

    private static QuestObjective targetSpeciesTagObjective(TagKey<Item> tag) {
        return new QuestObjective(Optional.empty(), Optional.of(tag), Optional.empty(), false, Optional.of(1), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1, false);
    }

    private static QuestObjective minQualityObjective(FishQuality.Quality quality) {
        return new QuestObjective(Optional.empty(), Optional.empty(), Optional.empty(), false, Optional.of(1), Optional.of(quality), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1, false);
    }

    private static QuestObjective minSizeObjective(float size) {
        return new QuestObjective(Optional.empty(), Optional.empty(), Optional.empty(), false, Optional.of(1), Optional.empty(), Optional.of(size), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1, false);
    }

    private static QuestObjective biomeObjective(TagKey<Biome> tag) {
        return new QuestObjective(Optional.empty(), Optional.empty(), Optional.empty(), false, Optional.of(1), Optional.empty(), Optional.empty(), Optional.of(tag), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1, false);
    }

    private static QuestObjective timeObjective(FishProfile.TimeOfDay time) {
        return new QuestObjective(Optional.empty(), Optional.empty(), Optional.empty(), false, Optional.of(1), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(time), Optional.empty(), Optional.empty(), Optional.empty(), 1, false);
    }

    private static QuestObjective weatherObjective(FishProfile.WeatherCondition weather) {
        return new QuestObjective(Optional.empty(), Optional.empty(), Optional.empty(), false, Optional.of(1), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(weather), Optional.empty(), Optional.empty(), 1, false);
    }

    private static QuestObjective zoneObjective(FishProfile.Zone zone) {
        return new QuestObjective(Optional.empty(), Optional.empty(), Optional.empty(), false, Optional.of(1), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(zone), Optional.empty(), 1, false);
    }

    private static ResourceKey<Item> keyOf(Item item) {
        return item.builtInRegistryHolder().unwrapKey().orElseThrow();
    }

    private static Holder<Biome> biome(GameTestHelper helper, ResourceKey<Biome> key) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME).get(key).orElseThrow();
    }

    // -------------------------------------------------------------------------
    // Each QuestObjective field independently gates the match, ignored when absent
    // -------------------------------------------------------------------------

    public static void targetSpeciesGatesMatchWhenPresent(GameTestHelper helper) {
        ItemStack cod = new ItemStack(Items.COD);
        Holder<Biome> anyBiome = biome(helper, Biomes.PLAINS);

        helper.assertTrue(
            QuestTracker.matchesObjective(targetSpeciesObjective(keyOf(Items.COD)), cod, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR, ANY_ZONES),
            "A catch matching the required species must match"
        );
        helper.assertTrue(
            !QuestTracker.matchesObjective(targetSpeciesObjective(keyOf(Items.DIAMOND)), cod, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR, ANY_ZONES),
            "A catch of a different species must not match"
        );
        helper.assertTrue(
            QuestTracker.matchesObjective(wildcardObjective(), cod, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR, ANY_ZONES),
            "An absent targetSpecies condition must not block the match"
        );
        helper.succeed();
    }

    public static void targetSpeciesTagGatesMatchWhenPresent(GameTestHelper helper) {
        ItemStack cod = new ItemStack(Items.COD);
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        Holder<Biome> anyBiome = biome(helper, Biomes.PLAINS);
        QuestObjective fishesTag = targetSpeciesTagObjective(ItemTags.FISHES);

        helper.assertTrue(
            QuestTracker.matchesObjective(fishesTag, cod, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR, ANY_ZONES),
            "Cod must match the vanilla fishes tag condition"
        );
        helper.assertTrue(
            !QuestTracker.matchesObjective(fishesTag, diamond, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR, ANY_ZONES),
            "Diamond must not match the vanilla fishes tag condition"
        );
        helper.succeed();
    }

    /** minQuality is an ordinal floor: one tier below fails, exactly at or above passes. */
    public static void minQualityIsOrdinalFloor(GameTestHelper helper) {
        Holder<Biome> anyBiome = biome(helper, Biomes.PLAINS);
        QuestObjective requiresRare = minQualityObjective(FishQuality.Quality.RARE);

        ItemStack uncommon = new ItemStack(Items.COD);
        FishQualityHelper.setQuality(uncommon, FishQuality.Quality.UNCOMMON);
        helper.assertTrue(
            !QuestTracker.matchesObjective(requiresRare, uncommon, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR, ANY_ZONES),
            "A catch one tier below the required quality must not match"
        );

        ItemStack rare = new ItemStack(Items.COD);
        FishQualityHelper.setQuality(rare, FishQuality.Quality.RARE);
        helper.assertTrue(
            QuestTracker.matchesObjective(requiresRare, rare, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR, ANY_ZONES),
            "A catch exactly at the required quality must match"
        );

        ItemStack epic = new ItemStack(Items.COD);
        FishQualityHelper.setQuality(epic, FishQuality.Quality.EPIC);
        helper.assertTrue(
            QuestTracker.matchesObjective(requiresRare, epic, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR, ANY_ZONES),
            "A catch above the required quality must match"
        );

        ItemStack noQuality = new ItemStack(Items.COD);
        helper.assertTrue(
            !QuestTracker.matchesObjective(requiresRare, noQuality, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR, ANY_ZONES),
            "A catch with no quality component must not match a minQuality requirement"
        );
        helper.succeed();
    }

    public static void biomeConditionGatesMatchWhenPresent(GameTestHelper helper) {
        ItemStack cod = new ItemStack(Items.COD);
        QuestObjective requiresOcean = biomeObjective(BiomeTags.IS_OCEAN);

        helper.assertTrue(
            QuestTracker.matchesObjective(requiresOcean, cod, biome(helper, Biomes.OCEAN), FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR, ANY_ZONES),
            "Catching in an ocean biome must match an is_ocean biome condition"
        );
        helper.assertTrue(
            !QuestTracker.matchesObjective(requiresOcean, cod, biome(helper, Biomes.PLAINS), FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR, ANY_ZONES),
            "Catching in plains must not match an is_ocean biome condition"
        );
        helper.succeed();
    }

    public static void timeConditionGatesMatchWhenPresent(GameTestHelper helper) {
        ItemStack cod = new ItemStack(Items.COD);
        Holder<Biome> anyBiome = biome(helper, Biomes.PLAINS);
        QuestObjective requiresDay = timeObjective(FishProfile.TimeOfDay.DAY);

        helper.assertTrue(
            QuestTracker.matchesObjective(requiresDay, cod, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR, ANY_ZONES),
            "Catching during the required time of day must match"
        );
        helper.assertTrue(
            !QuestTracker.matchesObjective(requiresDay, cod, anyBiome, FishProfile.TimeOfDay.NIGHT, FishProfile.WeatherCondition.CLEAR, ANY_ZONES),
            "Catching at a different time of day must not match"
        );
        helper.succeed();
    }

    public static void weatherConditionGatesMatchWhenPresent(GameTestHelper helper) {
        ItemStack cod = new ItemStack(Items.COD);
        Holder<Biome> anyBiome = biome(helper, Biomes.PLAINS);
        QuestObjective requiresClear = weatherObjective(FishProfile.WeatherCondition.CLEAR);

        helper.assertTrue(
            QuestTracker.matchesObjective(requiresClear, cod, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR, ANY_ZONES),
            "Catching in the required weather must match"
        );
        helper.assertTrue(
            !QuestTracker.matchesObjective(requiresClear, cod, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.RAIN, ANY_ZONES),
            "Catching in different weather must not match"
        );
        helper.succeed();
    }

    /**
     * zoneCondition gates on the hook's actual resolved zone set, not just tag membership — this
     * is what stops a river+cave dual-zone fish from silently completing a cave-only quest while
     * the player was fishing in an ordinary river.
     */
    public static void zoneConditionGatesMatchWhenPresent(GameTestHelper helper) {
        ItemStack cod = new ItemStack(Items.COD);
        Holder<Biome> anyBiome = biome(helper, Biomes.PLAINS);
        QuestObjective requiresCave = zoneObjective(FishProfile.Zone.CAVE);

        helper.assertTrue(
            QuestTracker.matchesObjective(requiresCave, cod, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR, Set.of(FishProfile.Zone.CAVE)),
            "Catching while the hook's zone set contains the required zone must match"
        );
        helper.assertTrue(
            QuestTracker.matchesObjective(requiresCave, cod, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR, Set.of(FishProfile.Zone.RIVER, FishProfile.Zone.CAVE)),
            "A dual-zone catch (e.g. river+cave) must still match as long as the required zone is one of them"
        );
        helper.assertTrue(
            !QuestTracker.matchesObjective(requiresCave, cod, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR, Set.of(FishProfile.Zone.RIVER)),
            "A river-only catch of a fish that also happens to be cave-tagged must not match a cave zone requirement"
        );
        helper.succeed();
    }

    /** minSize is a floor: strictly below fails, exactly at or above passes, missing size data fails. */
    public static void minSizeIsFloor(GameTestHelper helper) {
        Holder<Biome> anyBiome = biome(helper, Biomes.PLAINS);
        QuestObjective requires40 = minSizeObjective(40.0f);

        ItemStack small = new ItemStack(Items.COD);
        ItemSizeHelper.setSize(small, 39.9f);
        helper.assertTrue(
            !QuestTracker.matchesObjective(requires40, small, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR, ANY_ZONES),
            "A catch smaller than the required size must not match"
        );

        ItemStack exact = new ItemStack(Items.COD);
        ItemSizeHelper.setSize(exact, 40.0f);
        helper.assertTrue(
            QuestTracker.matchesObjective(requires40, exact, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR, ANY_ZONES),
            "A catch exactly at the required size must match"
        );

        ItemStack noSize = new ItemStack(Items.COD);
        helper.assertTrue(
            !QuestTracker.matchesObjective(requires40, noSize, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR, ANY_ZONES),
            "A catch with no size component must not match a minSize requirement"
        );
        helper.succeed();
    }

    /**
     * minSessionCatches is a batch-level threshold applied in QuestTracker.onCatchBatch, not a
     * per-stack predicate — matchesObjective must ignore it entirely and match on the other
     * conditions alone, the same as if it were absent.
     */
    public static void minSessionCatchesDoesNotAffectPerStackMatching(GameTestHelper helper) {
        Holder<Biome> anyBiome = biome(helper, Biomes.PLAINS);
        QuestObjective requiresTwoPerSession = new QuestObjective(
            Optional.of(keyOf(Items.COD)), Optional.empty(), Optional.empty(), false, Optional.of(1), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(2), 1, false
        );
        ItemStack cod = new ItemStack(Items.COD);

        helper.assertTrue(
            QuestTracker.matchesObjective(requiresTwoPerSession, cod, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR, ANY_ZONES),
            "A single matching stack must still satisfy matchesObjective regardless of minSessionCatches"
        );
        helper.succeed();
    }

    /** All set conditions must hold together (AND semantics) — failing any single axis fails the whole match. */
    public static void allConditionsMustMatchTogether(GameTestHelper helper) {
        QuestObjective combined = new QuestObjective(
            Optional.of(keyOf(Items.COD)),
            Optional.empty(),
            Optional.empty(),
            false,
            Optional.of(1),
            Optional.of(FishQuality.Quality.RARE),
            Optional.empty(),
            Optional.empty(),
            Optional.of(FishProfile.TimeOfDay.DAY),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            1,
            false
        );
        Holder<Biome> anyBiome = biome(helper, Biomes.PLAINS);

        ItemStack rareCod = new ItemStack(Items.COD);
        FishQualityHelper.setQuality(rareCod, FishQuality.Quality.RARE);

        helper.assertTrue(
            QuestTracker.matchesObjective(combined, rareCod, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR, ANY_ZONES),
            "A catch satisfying every set condition must match"
        );
        helper.assertTrue(
            !QuestTracker.matchesObjective(combined, rareCod, anyBiome, FishProfile.TimeOfDay.NIGHT, FishProfile.WeatherCondition.CLEAR, ANY_ZONES),
            "Failing just the time-of-day axis must fail the whole AND, even though species/quality still match"
        );

        ItemStack uncommonCod = new ItemStack(Items.COD);
        FishQualityHelper.setQuality(uncommonCod, FishQuality.Quality.UNCOMMON);
        helper.assertTrue(
            !QuestTracker.matchesObjective(combined, uncommonCod, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR, ANY_ZONES),
            "Failing just the quality axis must fail the whole AND, even though species/time still match"
        );
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // getActiveDailies — deterministic per day, bounded by ACTIVE_DAILY_COUNT (4)
    // -------------------------------------------------------------------------

    private static Quest dailyQuest() {
        return new Quest(QuestCategory.DAILY, QuestDifficulty.BRONZE, wildcardObjective(), new QuestReward(0, List.of()), Optional.empty(), false, "", "");
    }

    private static Quest tutorialQuest() {
        return new Quest(QuestCategory.TUTORIAL, QuestDifficulty.BRONZE, wildcardObjective(), new QuestReward(0, List.of()), Optional.empty(), false, "", "");
    }

    private static Registry<Quest> buildRegistry(int dailyCount, int tutorialCount) {
        MappedRegistry<Quest> registry = new MappedRegistry<>(FishtasticRegistries.QUEST_REGISTRY_KEY, Lifecycle.stable());
        for (int i = 0; i < dailyCount; i++) {
            ResourceKey<Quest> key = ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, Identifier.fromNamespaceAndPath("fishtastic", "daily_" + i));
            registry.register(key, dailyQuest(), RegistrationInfo.BUILT_IN);
        }
        for (int i = 0; i < tutorialCount; i++) {
            ResourceKey<Quest> key = ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, Identifier.fromNamespaceAndPath("fishtastic", "tutorial_" + i));
            registry.register(key, tutorialQuest(), RegistrationInfo.BUILT_IN);
        }
        return registry;
    }

    public static void getActiveDailiesIsStablePerDay(GameTestHelper helper) {
        Registry<Quest> registry = buildRegistry(10, 3);

        Set<ResourceKey<Quest>> first = QuestTracker.getActiveDailies(registry, 42L);
        Set<ResourceKey<Quest>> second = QuestTracker.getActiveDailies(registry, 42L);

        helper.assertTrue(first.equals(second), "getActiveDailies must be deterministic for the same day");
        helper.succeed();
    }

    public static void getActiveDailiesNeverExceedsCapAndExcludesNonDaily(GameTestHelper helper) {
        Registry<Quest> registry = buildRegistry(10, 5);

        Set<ResourceKey<Quest>> active = QuestTracker.getActiveDailies(registry, 7L);

        helper.assertTrue(active.size() == 4, "With 10 daily quests, exactly ACTIVE_DAILY_COUNT (4) must be selected, got " + active.size());
        for (ResourceKey<Quest> key : active) {
            helper.assertTrue(
                registry.getValue(key).category() == QuestCategory.DAILY,
                "getActiveDailies must never select a non-DAILY quest"
            );
        }
        helper.succeed();
    }

    public static void getActiveDailiesCapsAtRegistrySizeWhenSmallerThanCount(GameTestHelper helper) {
        Registry<Quest> registry = buildRegistry(2, 0);

        Set<ResourceKey<Quest>> active = QuestTracker.getActiveDailies(registry, 1L);

        helper.assertTrue(active.size() == 2, "With fewer daily quests than the cap, all of them must be active, got " + active.size());
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // QuestObjective.TankSnapshotCondition#lifetime — codec wiring
    // -------------------------------------------------------------------------

    /** Existing quests like explorer/tank_starter author "tank_snapshot": {} with no lifetime key — must still decode. */
    public static void tankSnapshotConditionLifetimeDefaultsToFalseWhenAbsent(GameTestHelper helper) {
        JsonObject json = new JsonObject();
        QuestObjective.TankSnapshotCondition decoded = QuestObjective.TankSnapshotCondition.CODEC
            .parse(JsonOps.INSTANCE, json).result()
            .orElseThrow(() -> new IllegalStateException("Failed to decode an empty tank_snapshot object"));

        helper.assertTrue(!decoded.lifetime(), "lifetime must default to false when omitted from JSON");
        helper.succeed();
    }

    public static void tankSnapshotConditionLifetimeRoundTripsThroughCodec(GameTestHelper helper) {
        QuestObjective.TankSnapshotCondition condition =
            new QuestObjective.TankSnapshotCondition(Optional.empty(), Optional.empty(), true);

        var encoded = QuestObjective.TankSnapshotCondition.CODEC.encodeStart(JsonOps.INSTANCE, condition)
            .result().orElseThrow(() -> new IllegalStateException("Failed to encode a lifetime tank_snapshot condition"));
        QuestObjective.TankSnapshotCondition decoded = QuestObjective.TankSnapshotCondition.CODEC
            .parse(JsonOps.INSTANCE, encoded).result()
            .orElseThrow(() -> new IllegalStateException("Failed to decode a lifetime tank_snapshot condition"));

        helper.assertTrue(decoded.lifetime(), "\"lifetime\": true must round-trip through the codec");
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // onTankChanged — lifetime tank-placement counter
    //
    // onTankChanged reads the quest registry straight off the live server
    // (server.registryAccess().lookupOrThrow), unlike getActiveDailies which takes a Registry<Quest>
    // parameter — so there's no throwaway-MappedRegistry fixture available here the way the tests
    // above use one. These drive the real registered explorer/tank_keeper_silver and
    // explorer/tank_keeper_gold quest content directly instead.
    // -------------------------------------------------------------------------

    private static final BlockPos LIFETIME_TANK_POS_A = new BlockPos(1, 1, 1);
    private static final BlockPos LIFETIME_TANK_POS_B = new BlockPos(1, 1, 3);

    private static FishTankBlockEntity placeFishTank(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos.below(), Blocks.STONE);
        helper.setBlock(pos, FishtasticBlocks.FISH_TANK.value());
        return helper.getBlockEntity(pos, FishTankBlockEntity.class);
    }

    private static ItemStack fishStack() {
        return new ItemStack(Items.COD);
    }

    /** Removes a single unit of the first tank slot holding {@code item}, wherever addItem's merge logic put it. */
    private static void removeOneMatchingItem(FishTankBlockEntity tank, Item item) {
        for (int slot = 0; slot < tank.getContainerSize(); slot++) {
            if (tank.getItem(slot).is(item)) {
                tank.removeItem(slot, 1);
                return;
            }
        }
    }

    private static ResourceKey<Quest> ftQuest(String path) {
        return ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, Utility.ft(path));
    }

    /**
     * Places exactly {@code target} fish (one short of, then the target-th) and asserts the quest
     * only completes on the last one — then claims it and confirms {@code shape} flips from locked
     * to unlocked, exactly like a player claiming explorer/tank_keeper_silver or _gold in-game.
     */
    private static void assertLifetimeTankKeeperQuestCompletesAndUnlocksShape(
            GameTestHelper helper, ServerPlayer player, String questPath, FishTankShape shape) {
        MinecraftServer server = helper.getLevel().getServer();
        ResourceKey<Quest> questKey = ftQuest(questPath);
        Registry<Quest> quests = server.registryAccess().lookupOrThrow(FishtasticRegistries.QUEST_REGISTRY_KEY);
        Quest quest = quests.getValue(questKey);
        helper.assertTrue(quest != null, "Quest " + questPath + " must be registered");
        int target = quest.objective().effectiveTargetCount(server.registryAccess());

        FishTankBlockEntity tank = placeFishTank(helper, LIFETIME_TANK_POS_A);
        PlayerQuestState state = FishCatchSavedData.getOrCreate(server).getOrCreateQuestState(player);

        helper.assertTrue(!shape.isUnlockedFor(k -> state.getProgress(k).claimed()),
            shape + " must start locked before " + questPath + " is claimed");

        for (int i = 0; i < target - 1; i++) {
            QuestTracker.onTankChanged(server, player, tank, fishStack());
        }
        helper.assertTrue(!state.getProgress(questKey).completed(),
            questPath + " must not be complete one fish short of the target (" + (target - 1) + "/" + target + ")");

        QuestTracker.onTankChanged(server, player, tank, fishStack());
        PlayerQuestState.QuestProgress progress = state.getProgress(questKey);
        helper.assertTrue(progress.completed(),
            questPath + " must complete at exactly " + target + " lifetime tank placements");
        helper.assertTrue(state.canClaim(questKey, target), questPath + " must be claimable once completed");

        state.claim(questKey, quest.reward().questTokens());
        helper.assertTrue(shape.isUnlockedFor(k -> state.getProgress(k).claimed()),
            shape + " must unlock once " + questPath + " is claimed");
    }

    public static void tankKeeperSilverCompletesAndUnlocksToothShape(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        assertLifetimeTankKeeperQuestCompletesAndUnlocksShape(
            helper, mockPlayer.get(), "explorer/tank_keeper_silver", FishTankShape.TOOTH);
        helper.succeed();
    }

    public static void tankKeeperGoldCompletesAndUnlocksFilmShape(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        assertLifetimeTankKeeperQuestCompletesAndUnlocksShape(
            helper, mockPlayer.get(), "explorer/tank_keeper_gold", FishTankShape.FILM);
        helper.succeed();
    }

    /**
     * The whole point of the "put 100 fish in tanks" design is that it's a lifetime total, not a
     * per-tank one — placing into two different tanks must still add up on the same counter.
     */
    public static void lifetimeTankPlacementCounterIsCumulativeAcrossDifferentTanks(
            GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        MinecraftServer server = helper.getLevel().getServer();
        FishTankBlockEntity tankA = placeFishTank(helper, LIFETIME_TANK_POS_A);
        FishTankBlockEntity tankB = placeFishTank(helper, LIFETIME_TANK_POS_B);
        PlayerQuestState state = FishCatchSavedData.getOrCreate(server).getOrCreateQuestState(player);

        QuestTracker.onTankChanged(server, player, tankA, fishStack());
        QuestTracker.onTankChanged(server, player, tankB, fishStack());

        helper.assertTrue(state.getLifetimeTankPlacements() == 2,
            "Lifetime placements must accumulate across different tanks, not reset per tank, got "
                + state.getLifetimeTankPlacements());
        helper.succeed();
    }

    /** Only fish (ItemTags.FISHES) advance the lifetime placement counter — a plain block/item must not. */
    public static void lifetimeTankPlacementCounterIgnoresNonFishItems(
            GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        MinecraftServer server = helper.getLevel().getServer();
        FishTankBlockEntity tank = placeFishTank(helper, LIFETIME_TANK_POS_A);
        PlayerQuestState state = FishCatchSavedData.getOrCreate(server).getOrCreateQuestState(player);

        QuestTracker.onTankChanged(server, player, tank, new ItemStack(Items.DIAMOND));
        helper.assertTrue(state.getLifetimeTankPlacements() == 0,
            "Placing a non-fish item must not advance the lifetime placement counter");

        QuestTracker.onTankChanged(server, player, tank, fishStack());
        helper.assertTrue(state.getLifetimeTankPlacements() == 1,
            "Placing a fish must advance the counter by exactly 1, got " + state.getLifetimeTankPlacements());
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // onTankChanged — live per-tank tank_snapshot condition (not lifetime)
    //
    // Covers the other real tank_snapshot quests: explorer/tank_starter (no conditions at all),
    // challenge/golden_showcase (min_quality AND a material condition), and
    // explorer/blue_to_the_gills (target_species + target_count, live-recounted per insertion).
    // Same "real registered content, no throwaway registry" constraint as the lifetime tests above.
    // -------------------------------------------------------------------------

    /** explorer/tank_starter has no species/quality/material conditions — any fish at all satisfies it. */
    public static void tankStarterCompletesWhenAnyFishIsDisplayedInATank(
            GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        MinecraftServer server = helper.getLevel().getServer();
        ResourceKey<Quest> questKey = ftQuest("explorer/tank_starter");
        Registry<Quest> quests = server.registryAccess().lookupOrThrow(FishtasticRegistries.QUEST_REGISTRY_KEY);
        Quest quest = quests.getValue(questKey);
        helper.assertTrue(quest != null, "Quest explorer/tank_starter must be registered");

        FishTankBlockEntity tank = placeFishTank(helper, LIFETIME_TANK_POS_A);
        PlayerQuestState state = FishCatchSavedData.getOrCreate(server).getOrCreateQuestState(player);
        helper.assertTrue(!state.getProgress(questKey).completed(), "Must start incomplete before any fish is displayed");

        tank.addItem(fishStack());
        QuestTracker.onTankChanged(server, player, tank, fishStack());

        helper.assertTrue(state.getProgress(questKey).completed(),
            "Displaying any fish in a tank must complete a condition-free tank_snapshot objective");
        helper.succeed();
    }

    /**
     * challenge/golden_showcase requires BOTH a Legendary-quality fish AND a gold-block frame —
     * a live snapshot recomputed on every insertion, so satisfying only one condition must never
     * complete it and no incremental counter can be "topped up" across separate tanks.
     */
    public static void goldenShowcaseRequiresLegendaryQualityAndGoldFrameTogether(
            GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        MinecraftServer server = helper.getLevel().getServer();
        ResourceKey<Quest> questKey = ftQuest("challenge/golden_showcase");
        Registry<Quest> quests = server.registryAccess().lookupOrThrow(FishtasticRegistries.QUEST_REGISTRY_KEY);
        Quest quest = quests.getValue(questKey);
        helper.assertTrue(quest != null, "Quest challenge/golden_showcase must be registered");
        PlayerQuestState state = FishCatchSavedData.getOrCreate(server).getOrCreateQuestState(player);

        // Legendary fish, but the tank frame is the default (not gold) — must not complete.
        FishTankBlockEntity plainTank = placeFishTank(helper, LIFETIME_TANK_POS_A);
        ItemStack legendaryCod = fishStack();
        FishQualityHelper.setQuality(legendaryCod, FishQuality.Quality.LEGENDARY);
        plainTank.addItem(legendaryCod.copy());
        QuestTracker.onTankChanged(server, player, plainTank, legendaryCod);
        helper.assertTrue(!state.getProgress(questKey).completed(),
            "A Legendary fish in a non-gold-framed tank must not complete golden_showcase");

        // Gold-framed tank, but the fish carries no quality component — must not complete.
        FishTankBlockEntity goldTank = placeFishTank(helper, LIFETIME_TANK_POS_B);
        goldTank.setMaterials(new FishTankMaterials(Blocks.GOLD_BLOCK, Blocks.SAND, goldTank.getMaterials().glass()));
        ItemStack plainCod = fishStack();
        goldTank.addItem(plainCod.copy());
        QuestTracker.onTankChanged(server, player, goldTank, plainCod);
        helper.assertTrue(!state.getProgress(questKey).completed(),
            "A fish with no quality in a gold-framed tank must not complete golden_showcase");

        // Both conditions on the same tank at once — must complete.
        ItemStack legendaryCod2 = fishStack();
        FishQualityHelper.setQuality(legendaryCod2, FishQuality.Quality.LEGENDARY);
        goldTank.addItem(legendaryCod2.copy());
        QuestTracker.onTankChanged(server, player, goldTank, legendaryCod2);
        helper.assertTrue(state.getProgress(questKey).completed(),
            "A Legendary fish in a gold-framed tank must complete golden_showcase");
        helper.succeed();
    }

    /**
     * explorer/blue_to_the_gills (target_species Bluegill, target_count 5) is a live recount, not
     * an incrementing counter: a different species contributes nothing, partial counts don't
     * complete it, and — once complete — dismantling the display can never un-complete it (the
     * same regression guard {@link QuestTracker#onTankChanged} documents for every tank_snapshot
     * quest).
     */
    public static void blueToTheGillsCountsMatchingSpeciesLiveAndCannotRegressOnceComplete(
            GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        MinecraftServer server = helper.getLevel().getServer();
        ResourceKey<Quest> questKey = ftQuest("explorer/blue_to_the_gills");
        Registry<Quest> quests = server.registryAccess().lookupOrThrow(FishtasticRegistries.QUEST_REGISTRY_KEY);
        Quest quest = quests.getValue(questKey);
        helper.assertTrue(quest != null, "Quest explorer/blue_to_the_gills must be registered");
        int target = quest.objective().effectiveTargetCount(server.registryAccess());

        FishTankBlockEntity tank = placeFishTank(helper, LIFETIME_TANK_POS_A);
        PlayerQuestState state = FishCatchSavedData.getOrCreate(server).getOrCreateQuestState(player);

        // A different species must not count toward a target_species tank_snapshot objective.
        ItemStack cod = fishStack();
        tank.addItem(cod.copy());
        QuestTracker.onTankChanged(server, player, tank, cod);
        helper.assertTrue(state.getProgress(questKey).currentCount() == 0,
            "A non-Bluegill fish must not progress a Bluegill-only tank_snapshot objective");

        for (int i = 0; i < target - 1; i++) {
            ItemStack bluegill = new ItemStack(FishtasticItems.BLUEGILL.value());
            tank.addItem(bluegill.copy());
            QuestTracker.onTankChanged(server, player, tank, bluegill);
        }
        helper.assertTrue(!state.getProgress(questKey).completed(),
            "Must not be complete one Bluegill short of the target (" + (target - 1) + "/" + target + ")");

        ItemStack lastBluegill = new ItemStack(FishtasticItems.BLUEGILL.value());
        tank.addItem(lastBluegill.copy());
        QuestTracker.onTankChanged(server, player, tank, lastBluegill);
        helper.assertTrue(state.getProgress(questKey).completed(),
            "Must complete at exactly " + target + " Bluegill simultaneously displayed in one tank");

        // Dismantling the display afterward must never un-complete it — onTankChanged skips any
        // quest already completed/claimed before it ever recomputes the live snapshot.
        removeOneMatchingItem(tank, FishtasticItems.BLUEGILL.value());
        QuestTracker.onTankChanged(server, player, tank, ItemStack.EMPTY);
        helper.assertTrue(state.getProgress(questKey).completed(),
            "Removing a fish from the tank after completion must not un-complete the quest");
        helper.succeed();
    }
}
