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
    // Worm Bin tests  (max 6100 ticks — conversion takes up to 6000)
    // -------------------------------------------------------------------------

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void wormBinStartsEmpty(GameTestHelper helper) {
        WormBinGameTests.wormBinStartsEmpty(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void depositFishAddsToList(GameTestHelper helper) {
        WormBinGameTests.depositFishAddsToList(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void depositCapAtFive(GameTestHelper helper) {
        WormBinGameTests.depositCapAtFive(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aerationCapAtFive(GameTestHelper helper) {
        WormBinGameTests.aerationCapAtFive(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 6100)
    public void conversionTakesBaseTicks(GameTestHelper helper) {
        WormBinGameTests.conversionTakesBaseTicks(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 5000)
    public void aerationReducesConversionTime(GameTestHelper helper) {
        WormBinGameTests.aerationReducesConversionTime(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 6100)
    public void commonFishYield(GameTestHelper helper) {
        WormBinGameTests.commonFishYield(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 6100)
    public void legendaryFishYield(GameTestHelper helper) {
        WormBinGameTests.legendaryFishYield(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 5000)
    public void aerationAddsToYield(GameTestHelper helper) {
        WormBinGameTests.aerationAddsToYield(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 6100)
    public void mixedQualityYield(GameTestHelper helper) {
        WormBinGameTests.mixedQualityYield(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 6100)
    public void harvestResetsToEmpty(GameTestHelper helper) {
        WormBinGameTests.harvestResetsToEmpty(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void depositedFishListRetainsOrder(GameTestHelper helper) {
        WormBinGameTests.depositedFishListRetainsOrder(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aerationCooldownBlocksTooEarlyAttempt(GameTestHelper helper) {
        WormBinGameTests.aerationCooldownBlocksTooEarlyAttempt(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aerationCooldownAllowsAfterCooldown(GameTestHelper helper) {
        WormBinGameTests.aerationCooldownAllowsAfterCooldown(helper);
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
    public void addItemFailsWhenAllSlotsFull(GameTestHelper helper) {
        FishTankGameTests.addItemFailsWhenAllSlotsFull(helper);
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
    public void onQuestClaimedOnlyAdvancesOnMatchingTutorialQuestId(GameTestHelper helper) {
        TutorialManagerGameTests.onQuestClaimedOnlyAdvancesOnMatchingTutorialQuestId(helper, helper::makeMockServerPlayerInLevel);
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
}
