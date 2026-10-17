package grill24.fishtastic.gametest;

import com.mojang.serialization.Lifecycle;
import grill24.FishtasticRegistries;
import grill24.fishtastic.companion.CompanionBondUnlocks;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.data.QuestCategory;
import grill24.fishtastic.data.QuestDifficulty;
import grill24.fishtastic.data.QuestObjective;
import grill24.fishtastic.data.QuestReward;
import grill24.fishtastic.util.Ids;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;

import java.util.List;
import java.util.Optional;

/**
 * The quest half of the companion bond unlock rule (docs/fish-companions.md §8.3): a quest only
 * unlocks its species once it is done, and the scan reports each species once, skipping any that
 * are already unlocked. Runs in the testmod (not a bare unit test) because {@code Quest}'s codec
 * is registry-backed.
 */
public final class CompanionQuestUnlockGameTests {
    private CompanionQuestUnlockGameTests() {}

    private static ResourceLocation id(String path) {
        return Ids.of("fishtastic", path);
    }

    private static Quest quest(Optional<ResourceLocation> unlocks) {
        QuestObjective objective = new QuestObjective(
                Optional.empty(), Optional.empty(), Optional.empty(), false, Optional.of(1),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), 1, false);
        return new Quest(QuestCategory.EXPLORER, QuestDifficulty.BRONZE, objective,
                new QuestReward(0, List.of()), Optional.empty(), false, "Test", "", unlocks);
    }

    public static void questUnlocksOnlyOnceDone(GameTestHelper helper) {
        Quest quest = quest(Optional.of(id("oscar")));

        helper.assertTrue(Quest.companionUnlockedBy(quest, true, false).isPresent(), "completed quest unlocks");
        helper.assertTrue(Quest.companionUnlockedBy(quest, false, true).isPresent(), "claimed quest unlocks");
        helper.assertTrue(Quest.companionUnlockedBy(quest, false, false).isEmpty(), "in-progress quest must not unlock");

        Quest noHook = quest(Optional.empty());
        helper.assertTrue(Quest.companionUnlockedBy(noHook, true, true).isEmpty(), "no hook unlocks nothing");
        helper.succeed();
    }

    public static void questScanReportsEachSpeciesOnce(GameTestHelper helper) {
        MappedRegistry<Quest> registry = new MappedRegistry<>(FishtasticRegistries.QUEST_REGISTRY_KEY, Lifecycle.stable());
        ResourceKey<Quest> done = ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, id("done"));
        ResourceKey<Quest> undone = ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, id("undone"));
        registry.register(done, quest(Optional.of(id("bluegill"))), RegistrationInfo.BUILT_IN);
        registry.register(undone, quest(Optional.of(id("oscar"))), RegistrationInfo.BUILT_IN);
        registry.freeze();

        List<ResourceLocation> unlocked = CompanionBondUnlocks.questUnlockedSpecies(
                registry, key -> key.equals(done), species -> false);
        helper.assertTrue(unlocked.equals(List.of(id("bluegill"))),
                "only the done quest's species may unlock, got " + unlocked);

        List<ResourceLocation> again = CompanionBondUnlocks.questUnlockedSpecies(
                registry, key -> true, species -> species.equals(id("bluegill")));
        helper.assertTrue(again.equals(List.of(id("oscar"))),
                "already-unlocked species must be skipped, got " + again);
        helper.succeed();
    }
}
