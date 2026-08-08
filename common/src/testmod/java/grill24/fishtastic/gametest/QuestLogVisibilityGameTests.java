package grill24.fishtastic.gametest;

import grill24.FishtasticRegistries;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.data.QuestCategory;
import grill24.fishtastic.data.QuestDifficulty;
import grill24.fishtastic.data.QuestObjective;
import grill24.fishtastic.data.QuestReward;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.List;
import java.util.Optional;

/**
 * Covers {@link Quest#isHiddenFromLog}, the rule that decides whether a {@code hidden}
 * quest is kept out of the log.
 *
 * <p>Regression guard for the pre-release bug where {@code hidden} meant "invisible until already
 * complete". Every mastery tier-2/3 quest sets {@code hidden: true}, so the entire progression
 * ladder was only ever visible after the fact and the tiered progress bars were dead UI.
 */
public final class QuestLogVisibilityGameTests {

    private QuestLogVisibilityGameTests() {}

    private static Quest quest(boolean hidden, String prerequisite) {
        QuestObjective objective = new QuestObjective(
                Optional.empty(), Optional.empty(), Optional.empty(), false, Optional.of(1),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), 1, false);
        Optional<ResourceKey<Quest>> prereq = prerequisite == null
                ? Optional.empty()
                : Optional.of(ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY,
                        Identifier.parse(prerequisite)));
        return new Quest(QuestCategory.MASTERY, QuestDifficulty.BRONZE, objective, new QuestReward(0, List.of()),
                prereq, hidden, "Test Quest", "");
    }

    /** A quest that isn't hidden is always listed, whatever its progress or chain position. */
    public static void nonHiddenQuestIsAlwaysListed(GameTestHelper helper) {
        Quest visible = quest(false, null);
        Quest visibleInChain = quest(false, "fishtastic:mastery/tier_one");

        helper.assertFalse(visible.isHiddenFromLog(false, false),
                "A non-hidden quest must be listed even with no progress");
        helper.assertFalse(visibleInChain.isHiddenFromLog(false, false),
                "A non-hidden chain quest must be listed even before its prerequisite is claimed");
        helper.succeed();
    }

    /**
     * A hidden quest with no prerequisite is a true secret (the unlisted-fish reveals): it stays
     * out of the log until its objective is met, then surfaces already complete, ready to claim.
     */
    public static void hiddenSecretStaysOutUntilCompleted(GameTestHelper helper) {
        Quest secret = quest(true, null);

        helper.assertTrue(secret.isHiddenFromLog(false, false),
                "An incomplete secret quest must stay out of the log");
        helper.assertFalse(secret.isHiddenFromLog(true, false),
                "A completed secret quest must surface so it can be claimed");
        helper.succeed();
    }

    /**
     * The M4 fix: a hidden quest that sits in a chain appears as soon as its prerequisite is
     * claimed — which is exactly when QuestTracker starts crediting catches toward it — rather
     * than staying invisible until it is already finished.
     */
    public static void hiddenChainQuestAppearsOncePrerequisiteClaimed(GameTestHelper helper) {
        Quest tierTwo = quest(true, "fishtastic:mastery/tier_one");

        helper.assertTrue(tierTwo.isHiddenFromLog(false, false),
                "A chain quest must stay hidden while its prerequisite is unclaimed");
        helper.assertFalse(tierTwo.isHiddenFromLog(false, true),
                "A chain quest must become visible once its prerequisite is claimed, "
                        + "so the player can see the rung they are climbing");
        helper.assertFalse(tierTwo.isHiddenFromLog(true, true),
                "A completed chain quest must remain visible to be claimed");
        helper.succeed();
    }

    /**
     * Completion always wins over the prerequisite gate, so progress that accrued before the
     * prerequisite was claimed can never strand a finished quest outside the log.
     */
    public static void completedChainQuestIsListedEvenIfPrerequisiteUnclaimed(GameTestHelper helper) {
        Quest tierTwo = quest(true, "fishtastic:mastery/tier_one");

        helper.assertFalse(tierTwo.isHiddenFromLog(true, false),
                "A completed chain quest must be listed even if its prerequisite is somehow unclaimed");
        helper.succeed();
    }
}
