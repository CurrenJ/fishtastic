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
    public void globalBestSizeTracksIndependentlyPerFishType(GameTestHelper helper) {
        FishCatchDataGameTests.globalBestSizeTracksIndependentlyPerFishType(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void globalBestSizeTieKeepsSingleEntry(GameTestHelper helper) {
        FishCatchDataGameTests.globalBestSizeTieKeepsSingleEntry(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void globalCatchCountRanksMultiplePlayers(GameTestHelper helper) {
        FishCatchDataGameTests.globalCatchCountRanksMultiplePlayers(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void personalCatchCountSortOrder(GameTestHelper helper) {
        FishCatchDataGameTests.personalCatchCountSortOrder(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void globalBestSizeSortOrder(GameTestHelper helper) {
        FishCatchDataGameTests.globalBestSizeSortOrder(helper);
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

    // -------------------------------------------------------------------------
    // Fish Catch Backup tests  (real file I/O in the test world's data dir)
    // -------------------------------------------------------------------------

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void manualBackupRoundTrips(GameTestHelper helper) throws Exception {
        FishCatchBackupsGameTests.manualBackupRoundTrips(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void labelIsSanitisedIntoFileName(GameTestHelper helper) throws Exception {
        FishCatchBackupsGameTests.labelIsSanitisedIntoFileName(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void findRejectsTraversalAndUnknownNames(GameTestHelper helper) throws Exception {
        FishCatchBackupsGameTests.findRejectsTraversalAndUnknownNames(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void listIsNewestFirstAndIgnoresJunk(GameTestHelper helper) throws Exception {
        FishCatchBackupsGameTests.listIsNewestFirstAndIgnoresJunk(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void loadRejectsFileWithoutDataCompound(GameTestHelper helper) throws Exception {
        FishCatchBackupsGameTests.loadRejectsFileWithoutDataCompound(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void intervalBackupSkipsIdenticalContent(GameTestHelper helper) throws Exception {
        FishCatchBackupsGameTests.intervalBackupSkipsIdenticalContent(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void restorePlayerOnlyTouchesThatPlayer(GameTestHelper helper) throws Exception {
        FishCatchBackupsGameTests.restorePlayerOnlyTouchesThatPlayer(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void restorePlayerAbsentFromBackupRemovesThem(GameTestHelper helper) throws Exception {
        FishCatchBackupsGameTests.restorePlayerAbsentFromBackupRemovesThem(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void restorePlayerRevertsCleanupContribution(GameTestHelper helper) throws Exception {
        FishCatchBackupsGameTests.restorePlayerRevertsCleanupContribution(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void restoreAllReplacesEverything(GameTestHelper helper) throws Exception {
        FishCatchBackupsGameTests.restoreAllReplacesEverything(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void restoreDoesNotAliasSnapshotObjects(GameTestHelper helper) throws Exception {
        FishCatchBackupsGameTests.restoreDoesNotAliasSnapshotObjects(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void summaryAndLookupApi(GameTestHelper helper) throws Exception {
        FishCatchBackupsGameTests.summaryAndLookupApi(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void preCommandHookWritesLabelledBackup(GameTestHelper helper) throws Exception {
        FishCatchBackupsGameTests.preCommandHookWritesLabelledBackup(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void preCommandPoolIsCapped(GameTestHelper helper) throws Exception {
        FishCatchBackupsGameTests.preCommandPoolIsCapped(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void pruneDeletesAncientIntervalFilesOnDisk(GameTestHelper helper) throws Exception {
        FishCatchBackupsGameTests.pruneDeletesAncientIntervalFilesOnDisk(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void schedulerWritesStartThenInterval(GameTestHelper helper) throws Exception {
        FishCatchBackupsGameTests.schedulerWritesStartThenInterval(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void serverConfigWritesDefaultsAndReloads(GameTestHelper helper) throws Exception {
        FishCatchBackupsGameTests.serverConfigWritesDefaultsAndReloads(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void cleanupGoalOnlyResetsOnCompletionNotOverTime(GameTestHelper helper) {
        FishCatchDataGameTests.cleanupGoalOnlyResetsOnCompletionNotOverTime(helper, helper::makeMockServerPlayerInLevel);
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

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void getLifetimeTankPlacementsDefaultsToZero(GameTestHelper helper) {
        PlayerQuestStateGameTests.getLifetimeTankPlacementsDefaultsToZero(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void incrementLifetimeTankPlacementsIncrementsByOnePerCall(GameTestHelper helper) {
        PlayerQuestStateGameTests.incrementLifetimeTankPlacementsIncrementsByOnePerCall(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void lifetimeTankPlacementsPersistThroughCodecRoundTrip(GameTestHelper helper) {
        PlayerQuestStateGameTests.lifetimeTankPlacementsPersistThroughCodecRoundTrip(helper);
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
    public void stackedTanksFormOneColumn(GameTestHelper helper) {
        FishTankGameTests.stackedTanksFormOneColumn(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hangingCosmeticsStackAndRoundTrip(GameTestHelper helper) {
        FishTankGameTests.hangingCosmeticsStackAndRoundTrip(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void openFacesRoundTrip(GameTestHelper helper) {
        FishTankGameTests.openFacesRoundTrip(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sameShapeNeighborsConnect(GameTestHelper helper) {
        FishTankGameTests.sameShapeNeighborsConnect(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void crossShapeNeighborsInSameFamilyConnect(GameTestHelper helper) {
        FishTankGameTests.crossShapeNeighborsInSameFamilyConnect(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void standardAndReinforcedNeighborsConnect(GameTestHelper helper) {
        FishTankGameTests.standardAndReinforcedNeighborsConnect(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void newShapesConnectToStandard(GameTestHelper helper) {
        FishTankGameTests.newShapesConnectToStandard(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void newShapesConnectToEachOther(GameTestHelper helper) {
        FishTankGameTests.newShapesConnectToEachOther(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void honeycombSealedFaceIsBlockedEvenWhenGroupedViaAnotherPath(GameTestHelper helper) {
        FishTankGameTests.honeycombSealedFaceIsBlockedEvenWhenGroupedViaAnotherPath(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void honeycombSealedIsolatedPairFormSeparateGroups(GameTestHelper helper) {
        FishTankGameTests.honeycombSealedIsolatedPairFormSeparateGroups(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void brokenTankDropCarriesShapeAndMaterials(GameTestHelper helper) {
        FishTankGameTests.brokenTankDropCarriesShapeAndMaterials(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void brokenTankDropRestoresShapeWhenReplaced(GameTestHelper helper) {
        FishTankGameTests.brokenTankDropRestoresShapeWhenReplaced(helper);
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

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void tankSnapshotConditionLifetimeDefaultsToFalseWhenAbsent(GameTestHelper helper) {
        QuestTrackerGameTests.tankSnapshotConditionLifetimeDefaultsToFalseWhenAbsent(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void tankSnapshotConditionLifetimeRoundTripsThroughCodec(GameTestHelper helper) {
        QuestTrackerGameTests.tankSnapshotConditionLifetimeRoundTripsThroughCodec(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void tankKeeperSilverCompletesAndUnlocksToothShape(GameTestHelper helper) {
        QuestTrackerGameTests.tankKeeperSilverCompletesAndUnlocksToothShape(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void tankKeeperGoldCompletesAndUnlocksFilmShape(GameTestHelper helper) {
        QuestTrackerGameTests.tankKeeperGoldCompletesAndUnlocksFilmShape(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void lifetimeTankPlacementCounterIsCumulativeAcrossDifferentTanks(GameTestHelper helper) {
        QuestTrackerGameTests.lifetimeTankPlacementCounterIsCumulativeAcrossDifferentTanks(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void lifetimeTankPlacementCounterIgnoresNonFishItems(GameTestHelper helper) {
        QuestTrackerGameTests.lifetimeTankPlacementCounterIgnoresNonFishItems(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void tankStarterCompletesWhenAnyFishIsDisplayedInATank(GameTestHelper helper) {
        QuestTrackerGameTests.tankStarterCompletesWhenAnyFishIsDisplayedInATank(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void goldenShowcaseRequiresLegendaryQualityAndGoldFrameTogether(GameTestHelper helper) {
        QuestTrackerGameTests.goldenShowcaseRequiresLegendaryQualityAndGoldFrameTogether(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void blueToTheGillsCountsMatchingSpeciesLiveAndCannotRegressOnceComplete(GameTestHelper helper) {
        QuestTrackerGameTests.blueToTheGillsCountsMatchingSpeciesLiveAndCannotRegressOnceComplete(helper, helper::makeMockServerPlayerInLevel);
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
    public void gatedEntryIsEarnableViaEveryUnlockQuest(GameTestHelper helper) {
        CapstoneRewardGameTests.gatedEntryIsEarnableViaEveryUnlockQuest(helper);
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

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionFishingEventPacketRoundTrips(GameTestHelper helper) {
        PacketRoundTripGameTests.companionFishingEventPacketRoundTrips(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionScenePacketRoundTrips(GameTestHelper helper) {
        PacketRoundTripGameTests.companionScenePacketRoundTrips(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionSlotPacketsRoundTrip(GameTestHelper helper) {
        PacketRoundTripGameTests.companionSlotPacketsRoundTrip(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionPartyCodecsMigrateAndRoundTrip(GameTestHelper helper) {
        PacketRoundTripGameTests.companionPartyCodecsMigrateAndRoundTrip(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void ambientContextRequestPacketRoundTrips(GameTestHelper helper) {
        PacketRoundTripGameTests.ambientContextRequestPacketRoundTrips(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void ambientContextResponsePacketRoundTrips(GameTestHelper helper) {
        PacketRoundTripGameTests.ambientContextResponsePacketRoundTrips(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void oversizedOriginListIsRejectedOnEncodeAndDecode(GameTestHelper helper) {
        PacketRoundTripGameTests.oversizedOriginListIsRejectedOnEncodeAndDecode(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void oversizedEntryListIsRejectedOnEncodeAndDecode(GameTestHelper helper) {
        PacketRoundTripGameTests.oversizedEntryListIsRejectedOnEncodeAndDecode(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void oversizedCandidateListIsRejectedOnEncodeAndDecode(GameTestHelper helper) {
        PacketRoundTripGameTests.oversizedCandidateListIsRejectedOnEncodeAndDecode(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void unknownStatusIdDecodesToARejectionNotAnError(GameTestHelper helper) {
        PacketRoundTripGameTests.unknownStatusIdDecodesToARejectionNotAnError(helper);
    }

    // -------------------------------------------------------------------------
    // Ambient context exchange (Fish in World, Phase 4) — server-side validation, resolve
    // equality, session-snapshot precedence and the per-player rate limit
    // -------------------------------------------------------------------------

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void authoritativeResolveMatchesServerState(GameTestHelper helper) {
        AmbientContextGameTests.authoritativeResolveMatchesServerState(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void heldRodLaneIsResolvedFromTheServersOwnInventory(GameTestHelper helper) {
        AmbientContextGameTests.heldRodLaneIsResolvedFromTheServersOwnInventory(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void dimensionMismatchIsRejected(GameTestHelper helper) {
        AmbientContextGameTests.dimensionMismatchIsRejected(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void farOriginIsRejectedBeforeTheUnloadedCheck(GameTestHelper helper) {
        AmbientContextGameTests.farOriginIsRejectedBeforeTheUnloadedCheck(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void unloadedOriginIsRejectedWithoutLoadingTheChunk(GameTestHelper helper) {
        AmbientContextGameTests.unloadedOriginIsRejectedWithoutLoadingTheChunk(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void rateLimitDropsRequestsPastTheBurst(GameTestHelper helper) {
        AmbientContextGameTests.rateLimitDropsRequestsPastTheBurst(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sessionSnapshotTakesPrecedenceOverAFreshResolve(GameTestHelper helper) {
        AmbientContextGameTests.sessionSnapshotTakesPrecedenceOverAFreshResolve(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aDifferentPondNearTheCastFallsThroughToAFreshResolve(GameTestHelper helper) {
        AmbientContextGameTests.aDifferentPondNearTheCastFallsThroughToAFreshResolve(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void anEmptyRequestDoesNotConsumeAThrottleToken(GameTestHelper helper) {
        AmbientContextGameTests.anEmptyRequestDoesNotConsumeAThrottleToken(helper, helper::makeMockServerPlayerInLevel);
    }

    // -------------------------------------------------------------------------
    // FishEncyclopediaEntry tests  (pure Codec, no world state needed)
    // -------------------------------------------------------------------------

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void emptyObjectDecodesToAllDefaults(GameTestHelper helper) {
        FishEncyclopediaEntryGameTests.emptyObjectDecodesToAllDefaults(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void legacyThresholdsBlockIsIgnored(GameTestHelper helper) {
        FishEncyclopediaEntryGameTests.legacyThresholdsBlockIsIgnored(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void everyFishGetsDerivedThresholdsWithinCap(GameTestHelper helper) {
        FishEncyclopediaEntryGameTests.everyFishGetsDerivedThresholdsWithinCap(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fullEntryRoundTripsThroughJson(GameTestHelper helper) {
        FishEncyclopediaEntryGameTests.fullEntryRoundTripsThroughJson(helper);
    }

    // -------------------------------------------------------------------------
    // Companion profile data (ground benthics, docs/fish-companions-benthic-follow.md §21)
    // -------------------------------------------------------------------------

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void benthicCompanionDataLoads(GameTestHelper helper) {
        CompanionProfileGameTests.benthicCompanionDataLoads(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void onlyBenthicsDeclareGroundCompanionData(GameTestHelper helper) {
        CompanionProfileGameTests.onlyBenthicsDeclareGroundCompanionData(helper);
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
    public void handleMinigameCompleteDropsRewardAtPlayerFeetWhenInventoryIsFull(GameTestHelper helper) {
        FishingMinigameManagerGameTests.handleMinigameCompleteDropsRewardAtPlayerFeetWhenInventoryIsFull(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void handleMinigameCompleteDropsRewardWhenInventoryIsFullAndAutoPileFishIsActive(GameTestHelper helper) {
        FishingMinigameManagerGameTests.handleMinigameCompleteDropsRewardWhenInventoryIsFullAndAutoPileFishIsActive(helper, helper::makeMockServerPlayerInLevel);
    }

    // Phase 3 (Fish in World) species snapshot tests.

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void startSessionCapturesResolvedSpeciesSnapshot(GameTestHelper helper) {
        FishingMinigameManagerGameTests.startSessionCapturesResolvedSpeciesSnapshot(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void personalEquipmentReadsCarriedDiamondsAndBaselineStaysNeutral(GameTestHelper helper) {
        FishingMinigameManagerGameTests.personalEquipmentReadsCarriedDiamondsAndBaselineStaysNeutral(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void snapshotRecordsMainHandRodWhenBothHandsHoldRods(GameTestHelper helper) {
        FishingMinigameManagerGameTests.snapshotRecordsMainHandRodWhenBothHandsHoldRods(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void testSeededSessionCapturesNoSpeciesSnapshot(GameTestHelper helper) {
        FishingMinigameManagerGameTests.testSeededSessionCapturesNoSpeciesSnapshot(helper, helper::makeMockServerPlayerInLevel);
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

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void getActiveDailyShopTankShapeReplacementRateMatchesConfiguredChance(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopTankShapeReplacementRateMatchesConfiguredChance(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void getActiveDailyShopNeverReplacesWithoutATankShapePool(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopNeverReplacesWithoutATankShapePool(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void getActiveDailyShopHandlesEmptyMainPoolWithTankShapesOnly(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopHandlesEmptyMainPoolWithTankShapesOnly(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void getActiveDailyShopCharmAndTankShapeReplacementsCanCoexist(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopCharmAndTankShapeReplacementsCanCoexist(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void grantRewardsDropsLeftoverWhenInventoryIsFull(GameTestHelper helper) {
        ShopEntryGameTests.grantRewardsDropsLeftoverWhenInventoryIsFull(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void grantRewardsDeliversNormallyWhenInventoryHasSpace(GameTestHelper helper) {
        ShopEntryGameTests.grantRewardsDeliversNormallyWhenInventoryHasSpace(helper, helper::makeMockServerPlayerInLevel);
    }

    // -------------------------------------------------------------------------
    // Fish companions — bond / unequip / storage
    // -------------------------------------------------------------------------

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionMenuClickEquipsAndReturns(GameTestHelper helper) {
        CompanionMenuGameTests.clickEquipsAndReturns(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionMenuCursorSwapExchangesCompanions(GameTestHelper helper) {
        CompanionMenuGameTests.cursorSwapExchangesCompanions(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionMenuNumberKeySwapExchangesAndReturns(GameTestHelper helper) {
        CompanionMenuGameTests.numberKeySwapExchangesAndReturns(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionMenuDragPlacesOneFish(GameTestHelper helper) {
        CompanionMenuGameTests.dragPlacesOneFish(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionMenuShiftClickTakesOneFromAStack(GameTestHelper helper) {
        CompanionMenuGameTests.shiftClickTakesOneFromAStack(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionMenuShiftClickFillsTheFirstEmptySlotOnce(GameTestHelper helper) {
        CompanionMenuGameTests.shiftClickFillsTheFirstEmptySlotOnce(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionMenuShiftClickWithAFullPartyOrInventoryChangesNothing(GameTestHelper helper) {
        CompanionMenuGameTests.shiftClickWithAFullPartyOrInventoryChangesNothing(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionMenuThreeSlotsEquipAndReturn(GameTestHelper helper) {
        CompanionMenuGameTests.threeSlotsEquipAndReturn(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionMenuMovingBetweenSlotsReordersTheParty(GameTestHelper helper) {
        CompanionMenuGameTests.movingBetweenSlotsReordersTheParty(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionMenuLockedFishIsRejected(GameTestHelper helper) {
        CompanionMenuGameTests.lockedFishIsRejected(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionMenuClosingWithAFishOnTheCursorKeepsIt(GameTestHelper helper) {
        CompanionMenuGameTests.closingWithAFishOnTheCursorKeepsIt(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionMenuClickAfterAnOutsideBondSeesTheNewCompanion(GameTestHelper helper) {
        CompanionMenuGameTests.clickAfterAnOutsideBondSeesTheNewCompanion(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionMenuOutsideRenameReachesTheSlot(GameTestHelper helper) {
        CompanionMenuGameTests.outsideRenameReachesTheSlot(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void bondTakesTheFishFromTheInventory(GameTestHelper helper) {
        CompanionGameTests.bondTakesTheFishFromTheInventory(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void bondTakesTheFishFromInsideAPile(GameTestHelper helper) {
        CompanionGameTests.bondTakesTheFishFromInsideAPile(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void bondFromATwoFishPileLeavesTheOtherFishLoose(GameTestHelper helper) {
        CompanionGameTests.bondFromATwoFishPileLeavesTheOtherFishLoose(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void bondWithAFishThePlayerDoesNotCarryChangesNothing(GameTestHelper helper) {
        CompanionGameTests.bondWithAFishThePlayerDoesNotCarryChangesNothing(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void bondRefusesSomethingThatIsNotAFish(GameTestHelper helper) {
        CompanionGameTests.bondRefusesSomethingThatIsNotAFish(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void unequipReturnsTheIdenticalFish(GameTestHelper helper) {
        CompanionGameTests.unequipReturnsTheIdenticalFish(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void unequipWithAFullInventoryDropsTheFish(GameTestHelper helper) {
        CompanionGameTests.unequipWithAFullInventoryDropsTheFish(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void bondingASecondFishFillsTheNextSlot(GameTestHelper helper) {
        CompanionGameTests.bondingASecondFishFillsTheNextSlot(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void bondIntoAFullPartyIsRefused(GameTestHelper helper) {
        CompanionGameTests.bondIntoAFullPartyIsRefused(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void bondIntoANamedSlotReturnsItsFish(GameTestHelper helper) {
        CompanionGameTests.bondIntoANamedSlotReturnsItsFish(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void threeOfOneSpeciesFillTheParty(GameTestHelper helper) {
        CompanionGameTests.threeOfOneSpeciesFillTheParty(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionIsKeptOnDeath(GameTestHelper helper) {
        CompanionGameTests.companionIsKeptOnDeath(helper, helper::makeMockServerPlayerInLevel, (before, after) -> {
            after.restoreFrom(before, false);
            net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.invoker().afterRespawn(before, after, false);
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionLockedSpeciesRefusesBond(GameTestHelper helper) {
        CompanionBondGameTests.lockedSpeciesRefusesBond(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionCompleteEntryUnlocksAndNeverReLocks(GameTestHelper helper) {
        CompanionBondGameTests.completeEntryUnlocksAndNeverReLocks(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionUnlistedNeverUnlocksByCounts(GameTestHelper helper) {
        CompanionBondGameTests.unlistedSpeciesNeverUnlocksByCounts(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionUnlockQueuesOneBanner(GameTestHelper helper) {
        CompanionBondGameTests.unlockQueuesOneBanner(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionFakeDescriptionCannotBond(GameTestHelper helper) {
        CompanionBondGameTests.fakeDescriptionCannotBondAnUncarriedFish(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionRenameSetsAndClearsTheName(GameTestHelper helper) {
        CompanionBondGameTests.renameSetsAndClearsTheName(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionPickerGroupsIdenticalFish(GameTestHelper helper) {
        CarriedFishGameTests.identicalFishCollapseWithTheirTotalCount(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionPickerSplitsAnyComponentDifference(GameTestHelper helper) {
        CarriedFishGameTests.anyComponentDifferenceSplitsTheRow(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionPickerIgnoresEmptyStacks(GameTestHelper helper) {
        CarriedFishGameTests.emptyStacksAreIgnored(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionQuestUnlocksOnlyOnceDone(GameTestHelper helper) {
        CompanionQuestUnlockGameTests.questUnlocksOnlyOnceDone(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionQuestScanReportsEachSpeciesOnce(GameTestHelper helper) {
        CompanionQuestUnlockGameTests.questScanReportsEachSpeciesOnce(helper);
    }

    // ----- Companion pet-to-pet scenes (Phase 6) -----

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionSceneSameSpeciesPairPlaysG1(GameTestHelper helper) {
        CompanionSceneGameTests.sameSpeciesPairPlaysG1AndTellsBothOwners(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionSceneCooldownGatesRepeats(GameTestHelper helper) {
        CompanionSceneGameTests.cooldownGatesRepeats(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionSceneNoSceneWhileOwnerInMinigame(GameTestHelper helper) {
        CompanionSceneGameTests.noSceneWhileOwnerInMinigame(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionSceneNoSceneWhileOwnerHasLineOut(GameTestHelper helper) {
        CompanionSceneGameTests.noSceneWhileOwnerHasALineOut(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionSceneDifferentFreeSwimmersPlayG2(GameTestHelper helper) {
        CompanionSceneGameTests.differentFreeSwimmersPlayG2(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionScenePercherPairPlaysNothing(GameTestHelper helper) {
        CompanionSceneGameTests.percherPairPlaysNothing(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionSceneOutOfRangePairPlaysNothing(GameTestHelper helper) {
        CompanionSceneGameTests.outOfRangePairPlaysNothing(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionSceneOneScenePerCompanion(GameTestHelper helper) {
        CompanionSceneGameTests.oneScenePerCompanion(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionSceneAGroupTakesPartThroughItsLeader(GameTestHelper helper) {
        CompanionSceneGameTests.aGroupTakesPartThroughItsLeader(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionSceneOneScenePerOwnerPairAtATime(GameTestHelper helper) {
        CompanionSceneGameTests.oneScenePerOwnerPairAtATime(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionSceneSameSpeciesBeatsFreeSwimmers(GameTestHelper helper) {
        CompanionSceneGameTests.sameSpeciesPairBeatsFreeSwimmers(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void companionSceneNearbyObserversToldFarOnesNot(GameTestHelper helper) {
        CompanionSceneGameTests.nearbyObserversAreToldAndFarOnesAreNot(helper, helper::makeMockServerPlayerInLevel);
    }

    // -------------------------------------------------------------------------
    // RemoveTankEntryPacket.giveOrDrop — fish tank browser GUI removal delivery
    // -------------------------------------------------------------------------

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void giveOrDropDropsFishWhenInventoryIsFull(GameTestHelper helper) {
        RemoveTankEntryPacketGameTests.giveOrDropDropsFishWhenInventoryIsFull(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void giveOrDropDropsFishWhenInventoryIsFullInCreativeMode(GameTestHelper helper) {
        RemoveTankEntryPacketGameTests.giveOrDropDropsFishWhenInventoryIsFullInCreativeMode(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void giveOrDropDeliversFishNormallyWhenInventoryHasSpace(GameTestHelper helper) {
        RemoveTankEntryPacketGameTests.giveOrDropDeliversFishNormallyWhenInventoryHasSpace(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void giveOrDropPilesASecondFishIntoTheExistingPile(GameTestHelper helper) {
        RemoveTankEntryPacketGameTests.giveOrDropPilesASecondFishIntoTheExistingPile(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void giveOrDropThroughRealMenuAfterPickupWhileOpenDoesNotLoseTheFish(GameTestHelper helper) {
        RemoveTankEntryPacketGameTests.giveOrDropThroughRealMenuAfterPickupWhileOpenDoesNotLoseTheFish(helper, helper::makeMockServerPlayerInLevel);
    }

    // -------------------------------------------------------------------------
    // Fish Tank Assembly refit mode
    // -------------------------------------------------------------------------

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void refitOutputIsBlockedUntilSomethingChanges(GameTestHelper helper) {
        FishTankAssemblyRefitGameTests.refitOutputIsBlockedUntilSomethingChanges(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void refitReplacesOnlyTheStagedMaterialAndRefundsTheOldOne(GameTestHelper helper) {
        FishTankAssemblyRefitGameTests.refitReplacesOnlyTheStagedMaterialAndRefundsTheOldOne(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void refitShapeOnlyChangesShapeAndConsumesNoMaterials(GameTestHelper helper) {
        FishTankAssemblyRefitGameTests.refitShapeOnlyChangesShapeAndConsumesNoMaterials(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void refitCombinedChangesEverythingAndRefundsEachReplacedBlock(GameTestHelper helper) {
        FishTankAssemblyRefitGameTests.refitCombinedChangesEverythingAndRefundsEachReplacedBlock(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void refitPreservesUnrelatedComponents(GameTestHelper helper) {
        FishTankAssemblyRefitGameTests.refitPreservesUnrelatedComponents(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void refitToSandlessShapeIgnoresTheSandSlot(GameTestHelper helper) {
        FishTankAssemblyRefitGameTests.refitToSandlessShapeIgnoresTheSandSlot(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void refitFromSandlessShapeDoesNotRefundPlaceholderSand(GameTestHelper helper) {
        FishTankAssemblyRefitGameTests.refitFromSandlessShapeDoesNotRefundPlaceholderSand(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void craftingWithoutATankIsUnchanged(GameTestHelper helper) {
        FishTankAssemblyRefitGameTests.craftingWithoutATankIsUnchanged(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void tankSlotOnlyAcceptsFishTanks(GameTestHelper helper) {
        FishTankAssemblyRefitGameTests.tankSlotOnlyAcceptsFishTanks(helper, helper::makeMockServerPlayerInLevel);
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

    // -------------------------------------------------------------------------
    // Catch celebration tests  (pure timeline logic, zero rendering)
    // -------------------------------------------------------------------------

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void legendaryResolvesToHeroTier(GameTestHelper helper) {
        CatchCelebrationGameTests.legendaryResolvesToHeroTier(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void ordinaryCatchResolvesToNone(GameTestHelper helper) {
        CatchCelebrationGameTests.ordinaryCatchResolvesToNone(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void undiscoveredResolvesToDiscoveryTier(GameTestHelper helper) {
        CatchCelebrationGameTests.undiscoveredResolvesToDiscoveryTier(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void legendaryOutranksDiscovery(GameTestHelper helper) {
        CatchCelebrationGameTests.legendaryOutranksDiscovery(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void emptyRewardsResolveToNone(GameTestHelper helper) {
        CatchCelebrationGameTests.emptyRewardsResolveToNone(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void heroStackPicksHighestQuality(GameTestHelper helper) {
        CatchCelebrationGameTests.heroStackPicksHighestQuality(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void heroStackPicksNewSpeciesOnDiscovery(GameTestHelper helper) {
        CatchCelebrationGameTests.heroStackPicksNewSpeciesOnDiscovery(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void phaseBoundariesFollowTimings(GameTestHelper helper) {
        CatchCelebrationGameTests.phaseBoundariesFollowTimings(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void clockClampsAtTotal(GameTestHelper helper) {
        CatchCelebrationGameTests.clockClampsAtTotal(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void timeScaleFreezesThenRampsBack(GameTestHelper helper) {
        CatchCelebrationGameTests.timeScaleFreezesThenRampsBack(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void discoveryHasNoSlowMotionHold(GameTestHelper helper) {
        CatchCelebrationGameTests.discoveryHasNoSlowMotionHold(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void heroScaleGrowsToPeakThenReturnsToNormal(GameTestHelper helper) {
        CatchCelebrationGameTests.heroScaleGrowsToPeakThenReturnsToNormal(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void settleReleasesIntoPhysics(GameTestHelper helper) {
        CatchCelebrationGameTests.settleReleasesIntoPhysics(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void settleFinishesOnceItemLeavesScreen(GameTestHelper helper) {
        CatchCelebrationGameTests.settleFinishesOnceItemLeavesScreen(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void settlePhysicsDeceleratesUnderGravity(GameTestHelper helper) {
        CatchCelebrationGameTests.settlePhysicsDeceleratesUnderGravity(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void settleRotationComesFromPhysics(GameTestHelper helper) {
        CatchCelebrationGameTests.settleRotationComesFromPhysics(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void silhouetteDropsAtReveal(GameTestHelper helper) {
        CatchCelebrationGameTests.silhouetteDropsAtReveal(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void revealSwapHappensWhileEdgeOn(GameTestHelper helper) {
        CatchCelebrationGameTests.revealSwapHappensWhileEdgeOn(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void heroIsUnsqueezedOutsideTheTurn(GameTestHelper helper) {
        CatchCelebrationGameTests.heroIsUnsqueezedOutsideTheTurn(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sparkleBurstFiresOnceAtReveal(GameTestHelper helper) {
        CatchCelebrationGameTests.sparkleBurstFiresOnceAtReveal(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void skipJumpsToSettle(GameTestHelper helper) {
        CatchCelebrationGameTests.skipJumpsToSettle(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void skipIgnoredBeforeReveal(GameTestHelper helper) {
        CatchCelebrationGameTests.skipIgnoredBeforeReveal(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void skipAfterRevealKeepsBurstConsumed(GameTestHelper helper) {
        CatchCelebrationGameTests.skipAfterRevealKeepsBurstConsumed(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void skipDoesNotRewind(GameTestHelper helper) {
        CatchCelebrationGameTests.skipDoesNotRewind(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void gameplaySuppressedUntilFinished(GameTestHelper helper) {
        CatchCelebrationGameTests.gameplaySuppressedUntilFinished(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void shakeDecaysThenRebuildsIntoTheReveal(GameTestHelper helper) {
        CatchCelebrationGameTests.shakeDecaysThenRebuildsIntoTheReveal(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void celebrationDiscoveryPolishIsSofterThanHero(GameTestHelper helper) {
        CatchCelebrationGameTests.discoveryPolishIsSofterThanHero(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void celebrationReducedEffectsStripFlashShakeDimAndMotion(GameTestHelper helper) {
        CatchCelebrationGameTests.reducedEffectsStripFlashShakeDimAndMotion(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void celebrationHeroDimBuildsThenReleasesAtReveal(GameTestHelper helper) {
        CatchCelebrationGameTests.heroDimBuildsThenReleasesAtReveal(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void celebrationHeroPunchOvershootsThenSettles(GameTestHelper helper) {
        CatchCelebrationGameTests.heroPunchOvershootsThenSettles(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void celebrationHeroRaysAppearOnRevealAndFadeOut(GameTestHelper helper) {
        CatchCelebrationGameTests.heroRaysAppearOnRevealAndFadeOut(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void celebrationHeroBannerPopsInWithOvershootThenLeaves(GameTestHelper helper) {
        CatchCelebrationGameTests.heroBannerPopsInWithOvershootThenLeaves(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void celebrationHoldsLastSeveralSeconds(GameTestHelper helper) {
        CatchCelebrationGameTests.holdsLastSeveralSeconds(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void celebrationSkipWorksThroughoutTheHold(GameTestHelper helper) {
        CatchCelebrationGameTests.skipWorksThroughoutTheHold(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void celebrationNoneTierIsRejected(GameTestHelper helper) {
        CatchCelebrationGameTests.noneTierIsRejected(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void applyQualityAndSizeSetsBothComponents(GameTestHelper helper) {
        CatchCelebrationGameTests.applyQualityAndSizeSetsBothComponents(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void applyQualityAndSizeOverwritesExistingQuality(GameTestHelper helper) {
        CatchCelebrationGameTests.applyQualityAndSizeOverwritesExistingQuality(helper);
    }
}
