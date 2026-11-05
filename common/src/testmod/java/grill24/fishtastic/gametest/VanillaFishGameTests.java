package grill24.fishtastic.gametest;

import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.data.QuestObjective;
import grill24.fishtastic.data.VanillaFish;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.server.QuestTracker;
import grill24.fishtastic.util.FishQualityHelper;
import grill24.fishtastic.util.Ids;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Vanilla fish stay catchable but earn no catch records, Fishopedia progress or quest credit. */
public final class VanillaFishGameTests {

    private static final Identifier COD = Ids.withDefaultNamespace("cod");
    private static final Identifier BLUEGILL = Ids.of("fishtastic", "bluegill");

    private VanillaFishGameTests() {}

    private static ItemStack fish(Item item) {
        ItemStack stack = new ItemStack(item);
        ItemSizeHelper.setSize(stack, 42.0f);
        FishQualityHelper.setQuality(stack, FishQuality.Quality.COMMON);
        return stack;
    }

    public static void vanillaCatchesAreNotRecorded(GameTestHelper helper) {
        FishCatchSavedData data = new FishCatchSavedData();
        UUID player = UUID.randomUUID();
        for (Item item : new Item[]{Items.COD, Items.SALMON, Items.TROPICAL_FISH, Items.PUFFERFISH}) {
            helper.assertTrue(!data.recordCatch(player, "tester", fish(item)),
                    "A vanilla catch must never read as a first catch: " + item);
        }
        helper.assertTrue(data.getTotalCatchCount(player) == 0, "Vanilla catches must not add to the lifetime total");
        helper.assertTrue(data.getRecentCatches(player, 10).isEmpty(), "Vanilla catches must not enter the recent history");

        helper.assertTrue(data.recordCatch(player, "tester", fish(FishtasticItems.BLUEGILL.value())),
                "A Fishtastic species must still record");
        helper.succeed();
    }

    /** A save from before this rule holds vanilla records: they read as absent, everywhere. */
    public static void savedVanillaRecordsReadAsAbsent(GameTestHelper helper) {
        FishCatchSavedData data = new FishCatchSavedData();
        UUID player = UUID.randomUUID();
        data.setCatchCount(player, "tester", COD, 9);
        data.setCatchCount(player, "tester", BLUEGILL, 2);

        helper.assertTrue(data.getCatchCount(player, COD) == 0, "A saved vanilla count must read as zero");
        helper.assertTrue(data.getCatchCount(player, BLUEGILL) == 2, "Other species must keep their counts");
        helper.assertTrue(data.getTotalCatchCount(player) == 2, "The lifetime total must leave vanilla out");
        helper.assertTrue(data.getPersonalCatchCounts(player, FishCatchSavedData.PERSONAL_CATCH_COUNT_DESC).stream()
                        .noneMatch(entry -> entry.fishType().equals(COD)),
                "Personal counts must leave vanilla out");
        helper.assertTrue(data.getPersonalBestSizes(player, FishCatchSavedData.PERSONAL_BEST_SIZE_DESC).stream()
                        .noneMatch(entry -> entry.fishType().equals(COD)),
                "Personal bests must leave vanilla out");
        helper.assertTrue(data.getGlobalBestSizes(FishCatchSavedData.GLOBAL_BEST_SIZE_DESC).stream()
                        .noneMatch(entry -> entry.fishType().equals(COD)),
                "Global bests must leave vanilla out");
        helper.assertTrue(data.getGlobalCatchCounts(FishCatchSavedData.GLOBAL_CATCH_COUNT_DESC).get(0).totalCatches() == 2,
                "The catch-count leaderboard must leave vanilla out");
        helper.assertTrue(data.summarizePlayers().get(0).speciesDiscovered() == 1,
                "Species discovered must leave vanilla out");
        helper.succeed();
    }

    /** Even a condition-free "catch any fish" objective refuses vanilla, while other catches still count. */
    public static void vanillaFishEarnNoQuestCredit(GameTestHelper helper) {
        Holder<Biome> biome = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        QuestObjective anyCatch = new QuestObjective(Optional.empty(), Optional.empty(), Optional.empty(), false,
                Optional.of(1), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), 1, false);

        helper.assertTrue(!matches(anyCatch, Items.COD, biome), "Cod must not satisfy a quest");
        helper.assertTrue(matches(anyCatch, FishtasticItems.BLUEGILL.value(), biome), "Bluegill must satisfy the quest");
        helper.assertTrue(matches(anyCatch, Items.DIAMOND, biome), "A non-fish catch must not be refused as vanilla");
        helper.succeed();
    }

    private static boolean matches(QuestObjective objective, Item item, Holder<Biome> biome) {
        return QuestTracker.matchesObjective(objective, new ItemStack(item), biome,
                FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR, Set.of());
    }

    /** Quest checklists and icons list a target tag's members, so no shipped target tag may hold one. */
    public static void noQuestTargetTagHoldsVanillaFish(GameTestHelper helper) {
        var quests = helper.getLevel().registryAccess().lookupOrThrow(FishtasticRegistries.QUEST_REGISTRY_KEY);
        for (var entry : quests.entrySet()) {
            Quest quest = entry.getValue();
            quest.objective().targetSpeciesTag().ifPresent(tag -> {
                for (Holder<Item> member : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                    helper.assertTrue(!VanillaFish.is(member.unwrapKey().orElseThrow().identifier()),
                            entry.getKey().identifier() + " targets " + tag.location() + ", which holds a vanilla fish");
                }
            });
            quest.objective().targetSpecies().ifPresent(species -> helper.assertTrue(!VanillaFish.is(species.identifier()),
                    entry.getKey().identifier() + " targets a vanilla fish"));
        }
        helper.succeed();
    }
}
