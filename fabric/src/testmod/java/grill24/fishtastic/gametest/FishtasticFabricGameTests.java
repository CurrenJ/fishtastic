package grill24.fishtastic.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric test registration class.
 * Listed under the "fabric-gametest" entrypoint in the testmod's fabric.mod.json.
 *
 * Each method carries Fabric's {@code @GameTest} annotation and delegates to the
 * shared static test implementations in the sibling classes. This keeps all actual
 * logic platform-agnostic and avoids duplication when NeoForge tests are added.
 *
 * Structure: "fabric-gametest-api-v1:empty" (8×8×8 of air). Tests build their
 * own environment inside the structure bounds using {@code GameTestHelper.setBlock()}.
 *
 * Run via: {@code ./gradlew :fabric:runGametest}
 */
public class FishtasticFabricGameTests {

    // -------------------------------------------------------------------------
    // Marine Compost tests  (max 6100 ticks — conversion takes up to 6000)
    // -------------------------------------------------------------------------

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aerationCapAtFive(GameTestHelper helper) {
        MarineCompostGameTests.aerationCapAtFive(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 6100)
    public void conversionTakesBaseTicks(GameTestHelper helper) {
        MarineCompostGameTests.conversionTakesBaseTicks(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 5000)
    public void aerationReducesConversionTime(GameTestHelper helper) {
        MarineCompostGameTests.aerationReducesConversionTime(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 6100)
    public void commonFishYield(GameTestHelper helper) {
        MarineCompostGameTests.commonFishYield(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 6100)
    public void rareFishYield(GameTestHelper helper) {
        MarineCompostGameTests.rareFishYield(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 6100)
    public void legendaryFishYield(GameTestHelper helper) {
        MarineCompostGameTests.legendaryFishYield(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 5000)
    public void aerationAddsToYield(GameTestHelper helper) {
        MarineCompostGameTests.aerationAddsToYield(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 6100)
    public void noQualityDefaultsToCommonYield(GameTestHelper helper) {
        MarineCompostGameTests.noQualityDefaultsToCommonYield(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aerationCooldownBlocksTooEarlyAttempt(GameTestHelper helper) {
        MarineCompostGameTests.aerationCooldownBlocksTooEarlyAttempt(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aerationCooldownAllowsAfterCooldown(GameTestHelper helper) {
        MarineCompostGameTests.aerationCooldownAllowsAfterCooldown(helper);
    }

    // -------------------------------------------------------------------------
    // Fish Catch Data tests  (pure in-memory, no world state needed)
    // -------------------------------------------------------------------------

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void recordingNonFishIsIgnored(GameTestHelper helper) {
        FishCatchDataGameTests.recordingNonFishIsIgnored(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void recordingZeroSizeIsIgnored(GameTestHelper helper) {
        FishCatchDataGameTests.recordingZeroSizeIsIgnored(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void catchCountIncrements(GameTestHelper helper) {
        FishCatchDataGameTests.catchCountIncrements(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void bestSizeOnlyUpdatesOnImprovement(GameTestHelper helper) {
        FishCatchDataGameTests.bestSizeOnlyUpdatesOnImprovement(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void smallerCatchDoesNotOverrideBest(GameTestHelper helper) {
        FishCatchDataGameTests.smallerCatchDoesNotOverrideBest(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void personalLeaderboardsAreIsolatedByUuid(GameTestHelper helper) {
        FishCatchDataGameTests.personalLeaderboardsAreIsolatedByUuid(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void globalBestSizeTracksHighestAcrossPlayers(GameTestHelper helper) {
        FishCatchDataGameTests.globalBestSizeTracksHighestAcrossPlayers(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void globalCatchCountSumsAllFishTypes(GameTestHelper helper) {
        FishCatchDataGameTests.globalCatchCountSumsAllFishTypes(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void personalBestSizeSortOrder(GameTestHelper helper) {
        FishCatchDataGameTests.personalBestSizeSortOrder(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void unknownPlayerReturnsEmpty(GameTestHelper helper) {
        FishCatchDataGameTests.unknownPlayerReturnsEmpty(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void recordTrashContributionAccumulatesTotal(GameTestHelper helper) {
        FishCatchDataGameTests.recordTrashContributionAccumulatesTotal(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void recordTrashContributionCanCrossMultipleThresholdsAtOnce(GameTestHelper helper) {
        FishCatchDataGameTests.recordTrashContributionCanCrossMultipleThresholdsAtOnce(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void recordTrashContributionIgnoresNonPositiveAmounts(GameTestHelper helper) {
        FishCatchDataGameTests.recordTrashContributionIgnoresNonPositiveAmounts(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void crossingThresholdPaysOutTokensProportionally(GameTestHelper helper) {
        FishCatchDataGameTests.crossingThresholdPaysOutTokensProportionally(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void resetCleanupGoalIfNeededWipesContributionsOnNewWeek(GameTestHelper helper) {
        FishCatchDataGameTests.resetCleanupGoalIfNeededWipesContributionsOnNewWeek(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void getCleanupGoalContributorsListsAllContributors(GameTestHelper helper) {
        FishCatchDataGameTests.getCleanupGoalContributorsListsAllContributors(helper, helper::makeMockServerPlayerInLevel);
    }

    // -------------------------------------------------------------------------
    // Item Component tests  (pure in-memory)
    // -------------------------------------------------------------------------

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void itemSizeSetAndGet(GameTestHelper helper) {
        ItemComponentGameTests.itemSizeSetAndGet(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void itemSizeRemove(GameTestHelper helper) {
        ItemComponentGameTests.itemSizeRemove(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void itemSizeWithSizeCopies(GameTestHelper helper) {
        ItemComponentGameTests.itemSizeWithSizeCopies(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void itemSizeIgnoresEmptyStack(GameTestHelper helper) {
        ItemComponentGameTests.itemSizeIgnoresEmptyStack(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void itemSizeComponentKey(GameTestHelper helper) {
        ItemComponentGameTests.itemSizeComponentKey(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fishQualityAllTiers(GameTestHelper helper) {
        ItemComponentGameTests.fishQualityAllTiers(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fishQualityShouldRenderEffect(GameTestHelper helper) {
        ItemComponentGameTests.fishQualityShouldRenderEffect(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fishQualityEffectIntensity(GameTestHelper helper) {
        ItemComponentGameTests.fishQualityEffectIntensity(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fishQualityNullWhenAbsent(GameTestHelper helper) {
        ItemComponentGameTests.fishQualityNullWhenAbsent(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void baitEffectWormsPreset(GameTestHelper helper) {
        ItemComponentGameTests.baitEffectWormsPreset(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void baitEffectBlazedGrubExclusivePool(GameTestHelper helper) {
        ItemComponentGameTests.baitEffectBlazedGrubExclusivePool(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void baitEffectComponentRoundTrip(GameTestHelper helper) {
        ItemComponentGameTests.baitEffectComponentRoundTrip(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void baitEffectNoBaitDefaults(GameTestHelper helper) {
        ItemComponentGameTests.baitEffectNoBaitDefaults(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void baitEffectTrashChancePresets(GameTestHelper helper) {
        ItemComponentGameTests.baitEffectTrashChancePresets(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void baitEffectTrashChanceComponentRoundTrip(GameTestHelper helper) {
        ItemComponentGameTests.baitEffectTrashChanceComponentRoundTrip(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void rodBaitContentsEmptyIsEmpty(GameTestHelper helper) {
        ItemComponentGameTests.rodBaitContentsEmptyIsEmpty(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void rodBaitContentsNonEmptyStackIsNotEmpty(GameTestHelper helper) {
        ItemComponentGameTests.rodBaitContentsNonEmptyStackIsNotEmpty(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void rodBaitContentsCopyStackIsDistinct(GameTestHelper helper) {
        ItemComponentGameTests.rodBaitContentsCopyStackIsDistinct(helper);
    }

    // -------------------------------------------------------------------------
    // MathUtil / Utility tests  (pure logic, zero Minecraft world dependency)
    // -------------------------------------------------------------------------

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void lerpFloatBoundaries(GameTestHelper helper) {
        MathUtilGameTests.lerpFloatBoundaries(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void lerpDoubleBoundaries(GameTestHelper helper) {
        MathUtilGameTests.lerpDoubleBoundaries(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void clampFloatBounds(GameTestHelper helper) {
        MathUtilGameTests.clampFloatBounds(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void clampDoubleBounds(GameTestHelper helper) {
        MathUtilGameTests.clampDoubleBounds(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void clampIntBounds(GameTestHelper helper) {
        MathUtilGameTests.clampIntBounds(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void easeInOutQuadShape(GameTestHelper helper) {
        MathUtilGameTests.easeInOutQuadShape(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void easeOutCubicShape(GameTestHelper helper) {
        MathUtilGameTests.easeOutCubicShape(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void easedLerpIdentityMatchesPlainLerp(GameTestHelper helper) {
        MathUtilGameTests.easedLerpIdentityMatchesPlainLerp(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void easedLerpAppliesEasingFunction(GameTestHelper helper) {
        MathUtilGameTests.easedLerpAppliesEasingFunction(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void utilityFtCreatesNamespacedIdentifier(GameTestHelper helper) {
        MathUtilGameTests.utilityFtCreatesNamespacedIdentifier(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void utilityInterpolateColorBoundaries(GameTestHelper helper) {
        MathUtilGameTests.utilityInterpolateColorBoundaries(helper);
    }

    // -------------------------------------------------------------------------
    // FishingTarget tests  (pure logic, zero Minecraft world dependency)
    // -------------------------------------------------------------------------

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void highOverlapEventuallyCatches(GameTestHelper helper) {
        FishingTargetGameTests.highOverlapEventuallyCatches(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void zeroOverlapEventuallyFails(GameTestHelper helper) {
        FishingTargetGameTests.zeroOverlapEventuallyFails(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void pickRandomBoundaryRolls(GameTestHelper helper) {
        FishingTargetGameTests.pickRandomBoundaryRolls(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void allMovementPatternsTickWithoutThrowing(GameTestHelper helper) {
        FishingTargetGameTests.allMovementPatternsTickWithoutThrowing(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void collectionAnimationLifecycle(GameTestHelper helper) {
        FishingTargetGameTests.collectionAnimationLifecycle(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void failAnimationLifecycle(GameTestHelper helper) {
        FishingTargetGameTests.failAnimationLifecycle(helper);
    }

    // -------------------------------------------------------------------------
    // PlayerQuestState tests  (pure in-memory, no world state needed)
    // -------------------------------------------------------------------------

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void getProgressDefaultsForUntouchedQuest(GameTestHelper helper) {
        PlayerQuestStateGameTests.getProgressDefaultsForUntouchedQuest(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void incrementCountRaisesCountAndFlipsCompleted(GameTestHelper helper) {
        PlayerQuestStateGameTests.incrementCountRaisesCountAndFlipsCompleted(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void canClaimTrueOnlyBetweenCompletionAndClaim(GameTestHelper helper) {
        PlayerQuestStateGameTests.canClaimTrueOnlyBetweenCompletionAndClaim(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void claimAddsTokensWithoutResettingCount(GameTestHelper helper) {
        PlayerQuestStateGameTests.claimAddsTokensWithoutResettingCount(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void resetDailyIfNeededOnlyOnNewDay(GameTestHelper helper) {
        PlayerQuestStateGameTests.resetDailyIfNeededOnlyOnNewDay(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void purchaseFailsWhenBalanceTooLow(GameTestHelper helper) {
        PlayerQuestStateGameTests.purchaseFailsWhenBalanceTooLow(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void purchaseFailsWhenMaxPurchasesReached(GameTestHelper helper) {
        PlayerQuestStateGameTests.purchaseFailsWhenMaxPurchasesReached(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void purchaseZeroMaxPurchasesIsUnlimited(GameTestHelper helper) {
        PlayerQuestStateGameTests.purchaseZeroMaxPurchasesIsUnlimited(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void purchaseSucceedsDeductsAndIncrementsCount(GameTestHelper helper) {
        PlayerQuestStateGameTests.purchaseSucceedsDeductsAndIncrementsCount(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void resetDailyPurchasesIfNeededOnlyOnNewDay(GameTestHelper helper) {
        PlayerQuestStateGameTests.resetDailyPurchasesIfNeededOnlyOnNewDay(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void snapshotsReflectMutations(GameTestHelper helper) {
        PlayerQuestStateGameTests.snapshotsReflectMutations(helper);
    }

    // -------------------------------------------------------------------------
    // ItemEffect condition tests  (pure ItemStack + registry logic, no datapack needed)
    // -------------------------------------------------------------------------

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void itemTagConditionMatchesRealTag(GameTestHelper helper) {
        ItemEffectConditionGameTests.itemTagConditionMatchesRealTag(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void componentConditionMatchesPresenceOnly(GameTestHelper helper) {
        ItemEffectConditionGameTests.componentConditionMatchesPresenceOnly(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void componentValueConditionMatchesFieldValue(GameTestHelper helper) {
        ItemEffectConditionGameTests.componentValueConditionMatchesFieldValue(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void andConditionSemantics(GameTestHelper helper) {
        ItemEffectConditionGameTests.andConditionSemantics(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void itemEffectMatchesRespectsEnabledAndConditions(GameTestHelper helper) {
        ItemEffectConditionGameTests.itemEffectMatchesRespectsEnabledAndConditions(helper);
    }

    // -------------------------------------------------------------------------
    // Fish Tank tests
    // -------------------------------------------------------------------------

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void addItemIntoEmptyTankSucceeds(GameTestHelper helper) {
        FishTankGameTests.addItemIntoEmptyTankSucceeds(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void addItemMergesIntoExistingStackBeforeNewSlot(GameTestHelper helper) {
        FishTankGameTests.addItemMergesIntoExistingStackBeforeNewSlot(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void addItemFailsOnceSwarmCapReached(GameTestHelper helper) {
        FishTankGameTests.addItemFailsOnceSwarmCapReached(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void extractItemRemovesLastSlotLifoOrder(GameTestHelper helper) {
        FishTankGameTests.extractItemRemovesLastSlotLifoOrder(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void firstItemRotationReflectsSlotZeroInsert(GameTestHelper helper) {
        FishTankGameTests.firstItemRotationReflectsSlotZeroInsert(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void cosmeticsRoundTrip(GameTestHelper helper) {
        FishTankGameTests.cosmeticsRoundTrip(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void openFacesRoundTrip(GameTestHelper helper) {
        FishTankGameTests.openFacesRoundTrip(helper);
    }

    // -------------------------------------------------------------------------
    // TutorialManager tests
    // -------------------------------------------------------------------------

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void craftingRodFromDefaultStepGrantsWormsAndAdvances(GameTestHelper helper) {
        TutorialManagerGameTests.craftingRodFromDefaultStepGrantsWormsAndAdvances(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void craftingRodAgainAfterAdvancingIsNoOp(GameTestHelper helper) {
        TutorialManagerGameTests.craftingRodAgainAfterAdvancingIsNoOp(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void onBaitLoadedOnlyAdvancesFromBaitLoadStep(GameTestHelper helper) {
        TutorialManagerGameTests.onBaitLoadedOnlyAdvancesFromBaitLoadStep(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void onHookCastOnlyAdvancesFromCastableSteps(GameTestHelper helper) {
        TutorialManagerGameTests.onHookCastOnlyAdvancesFromCastableSteps(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void advanceStepNoOpWhenFromStepDoesNotMatchCurrent(GameTestHelper helper) {
        TutorialManagerGameTests.advanceStepNoOpWhenFromStepDoesNotMatchCurrent(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void tutorialWalksFullDocumentedChainToCompletion(GameTestHelper helper) {
        TutorialManagerGameTests.tutorialWalksFullDocumentedChainToCompletion(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void craftingRodViaShiftClickFromResultSlotAdvancesToBaitLoad(GameTestHelper helper) {
        TutorialManagerGameTests.craftingRodViaShiftClickFromResultSlotAdvancesToBaitLoad(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void craftingRodViaSimpleClickFromResultSlotAdvancesToBaitLoad(GameTestHelper helper) {
        TutorialManagerGameTests.craftingRodViaSimpleClickFromResultSlotAdvancesToBaitLoad(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void loadingBaitViaLeftClickInInventoryScreenAdvancesToWaitingForCast(GameTestHelper helper) {
        TutorialManagerGameTests.loadingBaitViaLeftClickInInventoryScreenAdvancesToWaitingForCast(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void revokingRodAdvancementAllowsReTriggeringAfterReset(GameTestHelper helper) {
        TutorialManagerGameTests.revokingRodAdvancementAllowsReTriggeringAfterReset(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void onQuestClaimedOnlyAdvancesOnMatchingTutorialQuestId(GameTestHelper helper) {
        TutorialManagerGameTests.onQuestClaimedOnlyAdvancesOnMatchingTutorialQuestId(helper, helper::makeMockServerPlayerInLevel);
    }

    // -------------------------------------------------------------------------
    // EncyclopediaTutorialManager tests
    // -------------------------------------------------------------------------

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void openingEncyclopediaFromNotStartedStartsIntro(GameTestHelper helper) {
        EncyclopediaTutorialManagerGameTests.openingEncyclopediaFromNotStartedStartsIntro(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void reopeningEncyclopediaWhileInProgressIsIdempotent(GameTestHelper helper) {
        EncyclopediaTutorialManagerGameTests.reopeningEncyclopediaWhileInProgressIsIdempotent(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void openingEncyclopediaAfterCompleteDoesNotRestart(GameTestHelper helper) {
        EncyclopediaTutorialManagerGameTests.openingEncyclopediaAfterCompleteDoesNotRestart(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void encyclopediaAdvanceStepNoOpWhenFromStepDoesNotMatchCurrent(GameTestHelper helper) {
        EncyclopediaTutorialManagerGameTests.advanceStepNoOpWhenFromStepDoesNotMatchCurrent(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void encyclopediaTutorialWalksFullChainToCompletion(GameTestHelper helper) {
        EncyclopediaTutorialManagerGameTests.encyclopediaTutorialWalksFullChainToCompletion(helper, helper::makeMockServerPlayerInLevel);
    }

    // -------------------------------------------------------------------------
    // QuestTracker tests  (pure matching logic + throwaway registry, no player needed)
    // -------------------------------------------------------------------------

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void targetSpeciesGatesMatchWhenPresent(GameTestHelper helper) {
        QuestTrackerGameTests.targetSpeciesGatesMatchWhenPresent(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void targetSpeciesTagGatesMatchWhenPresent(GameTestHelper helper) {
        QuestTrackerGameTests.targetSpeciesTagGatesMatchWhenPresent(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void minQualityIsOrdinalFloor(GameTestHelper helper) {
        QuestTrackerGameTests.minQualityIsOrdinalFloor(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void biomeConditionGatesMatchWhenPresent(GameTestHelper helper) {
        QuestTrackerGameTests.biomeConditionGatesMatchWhenPresent(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void timeConditionGatesMatchWhenPresent(GameTestHelper helper) {
        QuestTrackerGameTests.timeConditionGatesMatchWhenPresent(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void weatherConditionGatesMatchWhenPresent(GameTestHelper helper) {
        QuestTrackerGameTests.weatherConditionGatesMatchWhenPresent(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void zoneConditionGatesMatchWhenPresent(GameTestHelper helper) {
        QuestTrackerGameTests.zoneConditionGatesMatchWhenPresent(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void minSizeIsFloor(GameTestHelper helper) {
        QuestTrackerGameTests.minSizeIsFloor(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void minSessionCatchesDoesNotAffectPerStackMatching(GameTestHelper helper) {
        QuestTrackerGameTests.minSessionCatchesDoesNotAffectPerStackMatching(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void allConditionsMustMatchTogether(GameTestHelper helper) {
        QuestTrackerGameTests.allConditionsMustMatchTogether(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void getActiveDailiesIsStablePerDay(GameTestHelper helper) {
        QuestTrackerGameTests.getActiveDailiesIsStablePerDay(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void getActiveDailiesNeverExceedsCapAndExcludesNonDaily(GameTestHelper helper) {
        QuestTrackerGameTests.getActiveDailiesNeverExceedsCapAndExcludesNonDaily(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void getActiveDailiesCapsAtRegistrySizeWhenSmallerThanCount(GameTestHelper helper) {
        QuestTrackerGameTests.getActiveDailiesCapsAtRegistrySizeWhenSmallerThanCount(helper);
    }

    // -------------------------------------------------------------------------
    // Quest content validation  (runs against the live quest/fish_profile/biome registries)
    // -------------------------------------------------------------------------

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void everyTargetSpeciesQuestIsSatisfiable(GameTestHelper helper) {
        QuestSatisfiabilityGameTests.everyTargetSpeciesQuestIsSatisfiable(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void everyPrerequisiteResolves(GameTestHelper helper) {
        QuestSatisfiabilityGameTests.everyPrerequisiteResolves(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void noPrerequisiteCycles(GameTestHelper helper) {
        QuestSatisfiabilityGameTests.noPrerequisiteCycles(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void nonHiddenQuestIsAlwaysListed(GameTestHelper helper) {
        QuestLogVisibilityGameTests.nonHiddenQuestIsAlwaysListed(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hiddenSecretStaysOutUntilCompleted(GameTestHelper helper) {
        QuestLogVisibilityGameTests.hiddenSecretStaysOutUntilCompleted(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hiddenChainQuestAppearsOncePrerequisiteClaimed(GameTestHelper helper) {
        QuestLogVisibilityGameTests.hiddenChainQuestAppearsOncePrerequisiteClaimed(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void completedChainQuestIsListedEvenIfPrerequisiteUnclaimed(GameTestHelper helper) {
        QuestLogVisibilityGameTests.completedChainQuestIsListedEvenIfPrerequisiteUnclaimed(helper);
    }


    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void gatedEntriesAreAbsentUntilTheirQuestIsClaimed(GameTestHelper helper) {
        CapstoneRewardGameTests.gatedEntriesAreAbsentUntilTheirQuestIsClaimed(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void gatedEntriesCanAppearOnceTheirQuestIsClaimed(GameTestHelper helper) {
        CapstoneRewardGameTests.gatedEntriesCanAppearOnceTheirQuestIsClaimed(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void lockedEntriesDoNotConsumeShopSlots(GameTestHelper helper) {
        CapstoneRewardGameTests.lockedEntriesDoNotConsumeShopSlots(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void gatedEntrySellsTheSameItemItsQuestGranted(GameTestHelper helper) {
        CapstoneRewardGameTests.gatedEntrySellsTheSameItemItsQuestGranted(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void unlockGatesNeverPointAtDailyQuests(GameTestHelper helper) {
        CapstoneRewardGameTests.unlockGatesNeverPointAtDailyQuests(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void capstoneTanksCarryTheirMaterialsComponent(GameTestHelper helper) {
        CapstoneRewardGameTests.capstoneTanksCarryTheirMaterialsComponent(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void displayCountClampsOvershootToTheTarget(GameTestHelper helper) {
        PlayerQuestStateGameTests.displayCountClampsOvershootToTheTarget(helper);
    }


    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void stormCharmIsSlottableIntoTheRod(GameTestHelper helper) {
        StormCharmGameTests.stormCharmIsSlottableIntoTheRod(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void stormCharmCarriesNoCharmEffect(GameTestHelper helper) {
        StormCharmGameTests.stormCharmCarriesNoCharmEffect(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void stormCharmStacksUnlikeRodCharms(GameTestHelper helper) {
        StormCharmGameTests.stormCharmStacksUnlikeRodCharms(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void summonedStormIsReadAsThunderByQuestConditions(GameTestHelper helper) {
        StormCharmGameTests.summonedStormIsReadAsThunderByQuestConditions(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void handUseChargesUpAndIsFreeToCancel(GameTestHelper helper) {
        StormCharmGameTests.handUseChargesUpAndIsFreeToCancel(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void stormDurationIsWithinVanillaThunderRange(GameTestHelper helper) {
        StormCharmGameTests.stormDurationIsWithinVanillaThunderRange(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void dailyPoolIsLargerThanTheDrawAndActuallyRotates(GameTestHelper helper) {
        QuestSatisfiabilityGameTests.dailyPoolIsLargerThanTheDrawAndActuallyRotates(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void lifetimeQuestsCarryNoUnreplayableConditions(GameTestHelper helper) {
        QuestSatisfiabilityGameTests.lifetimeQuestsCarryNoUnreplayableConditions(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void catchCountMatchingScopesToTheRequestedSpecies(GameTestHelper helper) {
        LifetimeQuestProgressGameTests.catchCountMatchingScopesToTheRequestedSpecies(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void lifetimeCountsAreIsolatedPerPlayer(GameTestHelper helper) {
        LifetimeQuestProgressGameTests.lifetimeCountsAreIsolatedPerPlayer(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void oneLifetimeTotalSatisfiesEveryTierItHasPassed(GameTestHelper helper) {
        LifetimeQuestProgressGameTests.oneLifetimeTotalSatisfiesEveryTierItHasPassed(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void nonFishCatchesDoNotAdvanceLifetimeChains(GameTestHelper helper) {
        LifetimeQuestProgressGameTests.nonFishCatchesDoNotAdvanceLifetimeChains(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void lifetimeCompatibilityRejectsEnvironmentalConditions(GameTestHelper helper) {
        LifetimeQuestProgressGameTests.lifetimeCompatibilityRejectsEnvironmentalConditions(helper);
    }

    // -------------------------------------------------------------------------
    // Packet round-trip tests  (pure StreamCodec encode/decode, no player needed)
    // -------------------------------------------------------------------------

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void startFishingMinigamePacketRoundTrips(GameTestHelper helper) {
        PacketRoundTripGameTests.startFishingMinigamePacketRoundTrips(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void purchaseShopEntryPacketRoundTrips(GameTestHelper helper) {
        PacketRoundTripGameTests.purchaseShopEntryPacketRoundTrips(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void questSyncPacketRoundTrips(GameTestHelper helper) {
        PacketRoundTripGameTests.questSyncPacketRoundTrips(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void questSyncPacketCleanupGoalMilestoneRoundTrips(GameTestHelper helper) {
        PacketRoundTripGameTests.questSyncPacketCleanupGoalMilestoneRoundTrips(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fishEncyclopediaSyncPacketRoundTrips(GameTestHelper helper) {
        PacketRoundTripGameTests.fishEncyclopediaSyncPacketRoundTrips(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void requestFishEncyclopediaPacketRoundTrips(GameTestHelper helper) {
        PacketRoundTripGameTests.requestFishEncyclopediaPacketRoundTrips(helper);
    }

    // -------------------------------------------------------------------------
    // FishEncyclopediaEntry tests  (pure Codec, no world state needed)
    // -------------------------------------------------------------------------

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void emptyObjectDecodesToAllDefaults(GameTestHelper helper) {
        FishEncyclopediaEntryGameTests.emptyObjectDecodesToAllDefaults(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void partialThresholdsFillRemainingDefaults(GameTestHelper helper) {
        FishEncyclopediaEntryGameTests.partialThresholdsFillRemainingDefaults(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fullEntryRoundTripsThroughJson(GameTestHelper helper) {
        FishEncyclopediaEntryGameTests.fullEntryRoundTripsThroughJson(helper);
    }

    // -------------------------------------------------------------------------
    // FishEncyclopediaClientCache / FishEncyclopediaClientHelper tests
    // -------------------------------------------------------------------------

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void cacheStartsEmpty(GameTestHelper helper) {
        FishEncyclopediaClientGameTests.cacheStartsEmpty(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void updatePopulatesCatchCountsByFishType(GameTestHelper helper) {
        FishEncyclopediaClientGameTests.updatePopulatesCatchCountsByFishType(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void updateIndexesBestSizesByFishType(GameTestHelper helper) {
        FishEncyclopediaClientGameTests.updateIndexesBestSizesByFishType(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void updateReplacesPriorContentsRatherThanMerging(GameTestHelper helper) {
        FishEncyclopediaClientGameTests.updateReplacesPriorContentsRatherThanMerging(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void resetClearsAllMaps(GameTestHelper helper) {
        FishEncyclopediaClientGameTests.resetClearsAllMaps(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void getEncyclopediaEntryFallsBackToDefaultForUnregisteredFish(GameTestHelper helper) {
        FishEncyclopediaClientGameTests.getEncyclopediaEntryFallsBackToDefaultForUnregisteredFish(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void getAllFishProfilesSortedMatchesRegistryEntrySet(GameTestHelper helper) {
        FishEncyclopediaClientGameTests.getAllFishProfilesSortedMatchesRegistryEntrySet(helper);
    }

    // -------------------------------------------------------------------------
    // FishingMinigameManager tests
    // -------------------------------------------------------------------------

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void startSessionEndToEndReturnsValidSessionId(GameTestHelper helper) {
        FishingMinigameManagerGameTests.startSessionEndToEndReturnsValidSessionId(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void startSessionWhenAlreadyActiveReturnsNegativeOneUnlessCancelled(GameTestHelper helper) {
        FishingMinigameManagerGameTests.startSessionWhenAlreadyActiveReturnsNegativeOneUnlessCancelled(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void cancelSessionRemovesActiveSession(GameTestHelper helper) {
        FishingMinigameManagerGameTests.cancelSessionRemovesActiveSession(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void handleMinigameCompleteAwardsOnlyRewardsForValidIndicesAndIgnoresOthers(GameTestHelper helper) {
        FishingMinigameManagerGameTests.handleMinigameCompleteAwardsOnlyRewardsForValidIndicesAndIgnoresOthers(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void handleMinigameCompleteIsNoOpForUnknownOrMismatchedSession(GameTestHelper helper) {
        FishingMinigameManagerGameTests.handleMinigameCompleteIsNoOpForUnknownOrMismatchedSession(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void handleMinigameCompleteSessionIsSingleUseEvenWhenIndicesAreInvalid(GameTestHelper helper) {
        FishingMinigameManagerGameTests.handleMinigameCompleteSessionIsSingleUseEvenWhenIndicesAreInvalid(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void handleMinigameCompleteGrantsRewardsEvenWhenCompletedInUnderTwentyTicks(GameTestHelper helper) {
        FishingMinigameManagerGameTests.handleMinigameCompleteGrantsRewardsEvenWhenCompletedInUnderTwentyTicks(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void handleMinigameCompleteConsumesBaitOnlyWhenRewardsWereActuallyAwarded(GameTestHelper helper) {
        FishingMinigameManagerGameTests.handleMinigameCompleteConsumesBaitOnlyWhenRewardsWereActuallyAwarded(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void trashChanceOneAlwaysAwardsTrashItems(GameTestHelper helper) {
        FishingMinigameManagerGameTests.trashChanceOneAlwaysAwardsTrashItems(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void treasureChanceOneWithZeroTrashNeverAwardsTrash(GameTestHelper helper) {
        FishingMinigameManagerGameTests.treasureChanceOneWithZeroTrashNeverAwardsTrash(helper, helper::makeMockServerPlayerInLevel);
    }

    // -------------------------------------------------------------------------
    // ShopEntry tests  (pure registry-only logic, no player needed)
    // -------------------------------------------------------------------------

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void getActiveDailyShopIsStablePerDay(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopIsStablePerDay(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void getActiveDailyShopNeverExceedsCap(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopNeverExceedsCap(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void getActiveDailyShopCapsAtRegistrySizeWhenSmallerThanCount(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopCapsAtRegistrySizeWhenSmallerThanCount(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void getActiveDailyShopWeightBiasesSelectionTowardHeavierEntries(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopWeightBiasesSelectionTowardHeavierEntries(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void getActiveDailyShopHandlesNonPositiveWeightWithoutError(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopHandlesNonPositiveWeightWithoutError(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void shopEntryCodecDefaultsWeightToOneWhenAbsent(GameTestHelper helper) {
        ShopEntryGameTests.shopEntryCodecDefaultsWeightToOneWhenAbsent(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void getActiveDailyShopCharmReplacementRateMatchesConfiguredChance(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopCharmReplacementRateMatchesConfiguredChance(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void getActiveDailyShopNeverReplacesWithoutACharmPool(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopNeverReplacesWithoutACharmPool(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void getActiveDailyShopHandlesEmptyMainPoolWithCharmsOnly(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopHandlesEmptyMainPoolWithCharmsOnly(helper);
    }

    // -------------------------------------------------------------------------
    // Creative tab tests
    // -------------------------------------------------------------------------

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void decorationsTabContainsExactlyCosmeticStructuresAndDecorations(GameTestHelper helper) {
        CreativeTabGameTests.decorationsTabContainsExactlyCosmeticStructuresAndDecorations(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void mainTabNoLongerContainsMovedCosmetics(GameTestHelper helper) {
        CreativeTabGameTests.mainTabNoLongerContainsMovedCosmetics(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void advanceStepBaitLoadTransitionsToWaitingForCast(GameTestHelper helper) {
        TutorialManagerGameTests.advanceStepBaitLoadTransitionsToWaitingForCast(helper, helper::makeMockServerPlayerInLevel);
    }
}
