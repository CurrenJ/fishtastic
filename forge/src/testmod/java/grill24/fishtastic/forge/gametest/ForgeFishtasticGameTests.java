package grill24.fishtastic.forge.gametest;

import grill24.fishtastic.gametest.*;
import net.minecraftforge.gametest.GameTest;
import net.minecraftforge.gametest.GameTestDontPrefix;
import net.minecraftforge.gametest.GameTestNamespace;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Forge test registration class, gathered by {@code ForgeGameTestHooks.gatherTests}.
 *
 * Each method carries Forge's {@code @GameTest} annotation and delegates to the
 * shared static test implementations in the sibling classes. This keeps all actual
 * logic platform-agnostic and shares the suite with Fabric and NeoForge.
 *
 * Structure: "fishtastic:empty_testarea" (7×7×7 of air). Tests build their
 * own environment inside the structure bounds using {@code GameTestHelper.setBlock()}.
 *
 * Run via: {@code ./gradlew :forge:runGametest}
 */
@GameTestNamespace("fishtastic")
@GameTestDontPrefix
public class ForgeFishtasticGameTests {

    // -------------------------------------------------------------------------
    // Marine Compost tests  (max 6100 ticks — conversion takes up to 6000)
    // -------------------------------------------------------------------------

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void aerationCapAtFive(GameTestHelper helper) {
        MarineCompostGameTests.aerationCapAtFive(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea", maxTicks = 6100)
    public static void conversionTakesBaseTicks(GameTestHelper helper) {
        MarineCompostGameTests.conversionTakesBaseTicks(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea", maxTicks = 5000)
    public static void aerationReducesConversionTime(GameTestHelper helper) {
        MarineCompostGameTests.aerationReducesConversionTime(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void commonFishYield(GameTestHelper helper) {
        MarineCompostGameTests.commonFishYield(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void rareFishYield(GameTestHelper helper) {
        MarineCompostGameTests.rareFishYield(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void legendaryFishYield(GameTestHelper helper) {
        MarineCompostGameTests.legendaryFishYield(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void aerationAddsToYield(GameTestHelper helper) {
        MarineCompostGameTests.aerationAddsToYield(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void noQualityDefaultsToCommonYield(GameTestHelper helper) {
        MarineCompostGameTests.noQualityDefaultsToCommonYield(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void aerationCooldownBlocksTooEarlyAttempt(GameTestHelper helper) {
        MarineCompostGameTests.aerationCooldownBlocksTooEarlyAttempt(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void aerationCooldownAllowsAfterCooldown(GameTestHelper helper) {
        MarineCompostGameTests.aerationCooldownAllowsAfterCooldown(helper);
    }

    // -------------------------------------------------------------------------
    // Fish Catch Data tests  (pure in-memory, no world state needed)
    // -------------------------------------------------------------------------

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void recordingNonFishIsIgnored(GameTestHelper helper) {
        FishCatchDataGameTests.recordingNonFishIsIgnored(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void recordingZeroSizeIsIgnored(GameTestHelper helper) {
        FishCatchDataGameTests.recordingZeroSizeIsIgnored(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void catchCountIncrements(GameTestHelper helper) {
        FishCatchDataGameTests.catchCountIncrements(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void bestSizeOnlyUpdatesOnImprovement(GameTestHelper helper) {
        FishCatchDataGameTests.bestSizeOnlyUpdatesOnImprovement(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void smallerCatchDoesNotOverrideBest(GameTestHelper helper) {
        FishCatchDataGameTests.smallerCatchDoesNotOverrideBest(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void personalLeaderboardsAreIsolatedByUuid(GameTestHelper helper) {
        FishCatchDataGameTests.personalLeaderboardsAreIsolatedByUuid(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void globalBestSizeTracksHighestAcrossPlayers(GameTestHelper helper) {
        FishCatchDataGameTests.globalBestSizeTracksHighestAcrossPlayers(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void globalCatchCountSumsAllFishTypes(GameTestHelper helper) {
        FishCatchDataGameTests.globalCatchCountSumsAllFishTypes(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void personalBestSizeSortOrder(GameTestHelper helper) {
        FishCatchDataGameTests.personalBestSizeSortOrder(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void unknownPlayerReturnsEmpty(GameTestHelper helper) {
        FishCatchDataGameTests.unknownPlayerReturnsEmpty(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void globalBestSizeTracksIndependentlyPerFishType(GameTestHelper helper) {
        FishCatchDataGameTests.globalBestSizeTracksIndependentlyPerFishType(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void globalBestSizeTieKeepsSingleEntry(GameTestHelper helper) {
        FishCatchDataGameTests.globalBestSizeTieKeepsSingleEntry(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void globalCatchCountRanksMultiplePlayers(GameTestHelper helper) {
        FishCatchDataGameTests.globalCatchCountRanksMultiplePlayers(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void personalCatchCountSortOrder(GameTestHelper helper) {
        FishCatchDataGameTests.personalCatchCountSortOrder(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void globalBestSizeSortOrder(GameTestHelper helper) {
        FishCatchDataGameTests.globalBestSizeSortOrder(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void recordTrashContributionAccumulatesTotal(GameTestHelper helper) {
        FishCatchDataGameTests.recordTrashContributionAccumulatesTotal(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void recordTrashContributionCanCrossMultipleThresholdsAtOnce(GameTestHelper helper) {
        FishCatchDataGameTests.recordTrashContributionCanCrossMultipleThresholdsAtOnce(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void recordTrashContributionIgnoresNonPositiveAmounts(GameTestHelper helper) {
        FishCatchDataGameTests.recordTrashContributionIgnoresNonPositiveAmounts(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void crossingThresholdPaysOutTokensProportionally(GameTestHelper helper) {
        FishCatchDataGameTests.crossingThresholdPaysOutTokensProportionally(helper, helper::makeMockServerPlayerInLevel);
    }

    // -------------------------------------------------------------------------
    // Fish Catch Backup tests  (real file I/O in the test world's data dir)
    // -------------------------------------------------------------------------

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void manualBackupRoundTrips(GameTestHelper helper) throws Exception {
        BackupTestFixture.run(helper, () -> FishCatchBackupsGameTests.manualBackupRoundTrips(helper));
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void backupFixtureRestoresSharedStateAndFiles(GameTestHelper helper) throws Exception {
        BackupTestFixture.restoresSharedStateAndFiles(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void labelIsSanitisedIntoFileName(GameTestHelper helper) throws Exception {
        BackupTestFixture.run(helper, () -> FishCatchBackupsGameTests.labelIsSanitisedIntoFileName(helper));
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void findRejectsTraversalAndUnknownNames(GameTestHelper helper) throws Exception {
        BackupTestFixture.run(helper, () -> FishCatchBackupsGameTests.findRejectsTraversalAndUnknownNames(helper));
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void listIsNewestFirstAndIgnoresJunk(GameTestHelper helper) throws Exception {
        BackupTestFixture.run(helper, () -> FishCatchBackupsGameTests.listIsNewestFirstAndIgnoresJunk(helper));
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void loadRejectsFileWithoutDataCompound(GameTestHelper helper) throws Exception {
        BackupTestFixture.run(helper, () -> FishCatchBackupsGameTests.loadRejectsFileWithoutDataCompound(helper));
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void intervalBackupSkipsIdenticalContent(GameTestHelper helper) throws Exception {
        BackupTestFixture.run(helper, () -> FishCatchBackupsGameTests.intervalBackupSkipsIdenticalContent(helper));
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void restorePlayerOnlyTouchesThatPlayer(GameTestHelper helper) throws Exception {
        BackupTestFixture.run(helper, () -> FishCatchBackupsGameTests.restorePlayerOnlyTouchesThatPlayer(helper));
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void restorePlayerAbsentFromBackupRemovesThem(GameTestHelper helper) throws Exception {
        BackupTestFixture.run(helper, () -> FishCatchBackupsGameTests.restorePlayerAbsentFromBackupRemovesThem(helper));
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void restorePlayerRevertsCleanupContribution(GameTestHelper helper) throws Exception {
        BackupTestFixture.run(helper, () -> FishCatchBackupsGameTests.restorePlayerRevertsCleanupContribution(helper, helper::makeMockServerPlayerInLevel));
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void restoreAllReplacesEverything(GameTestHelper helper) throws Exception {
        BackupTestFixture.run(helper, () -> FishCatchBackupsGameTests.restoreAllReplacesEverything(helper));
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void restoreDoesNotAliasSnapshotObjects(GameTestHelper helper) throws Exception {
        BackupTestFixture.run(helper, () -> FishCatchBackupsGameTests.restoreDoesNotAliasSnapshotObjects(helper));
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void summaryAndLookupApi(GameTestHelper helper) throws Exception {
        BackupTestFixture.run(helper, () -> FishCatchBackupsGameTests.summaryAndLookupApi(helper));
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void preCommandHookWritesLabelledBackup(GameTestHelper helper) throws Exception {
        BackupTestFixture.run(helper, () -> FishCatchBackupsGameTests.preCommandHookWritesLabelledBackup(helper));
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void preCommandPoolIsCapped(GameTestHelper helper) throws Exception {
        BackupTestFixture.run(helper, () -> FishCatchBackupsGameTests.preCommandPoolIsCapped(helper));
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void pruneDeletesAncientIntervalFilesOnDisk(GameTestHelper helper) throws Exception {
        BackupTestFixture.run(helper, () -> FishCatchBackupsGameTests.pruneDeletesAncientIntervalFilesOnDisk(helper));
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void schedulerWritesStartThenInterval(GameTestHelper helper) throws Exception {
        BackupTestFixture.run(helper, () -> FishCatchBackupsGameTests.schedulerWritesStartThenInterval(helper));
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void serverConfigWritesDefaultsAndReloads(GameTestHelper helper) throws Exception {
        BackupTestFixture.run(helper, () -> FishCatchBackupsGameTests.serverConfigWritesDefaultsAndReloads(helper));
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void cleanupGoalOnlyResetsOnCompletionNotOverTime(GameTestHelper helper) {
        FishCatchDataGameTests.cleanupGoalOnlyResetsOnCompletionNotOverTime(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void getCleanupGoalContributorsListsAllContributors(GameTestHelper helper) {
        FishCatchDataGameTests.getCleanupGoalContributorsListsAllContributors(helper, helper::makeMockServerPlayerInLevel);
    }

    // -------------------------------------------------------------------------
    // Item Component tests  (pure in-memory)
    // -------------------------------------------------------------------------

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void itemSizeSetAndGet(GameTestHelper helper) {
        ItemComponentGameTests.itemSizeSetAndGet(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void itemSizeRemove(GameTestHelper helper) {
        ItemComponentGameTests.itemSizeRemove(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void itemSizeWithSizeCopies(GameTestHelper helper) {
        ItemComponentGameTests.itemSizeWithSizeCopies(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void itemSizeIgnoresEmptyStack(GameTestHelper helper) {
        ItemComponentGameTests.itemSizeIgnoresEmptyStack(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void itemSizeComponentKey(GameTestHelper helper) {
        ItemComponentGameTests.itemSizeComponentKey(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void fishQualityAllTiers(GameTestHelper helper) {
        ItemComponentGameTests.fishQualityAllTiers(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void fishQualityShouldRenderEffect(GameTestHelper helper) {
        ItemComponentGameTests.fishQualityShouldRenderEffect(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void fishQualityEffectIntensity(GameTestHelper helper) {
        ItemComponentGameTests.fishQualityEffectIntensity(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void fishQualityNullWhenAbsent(GameTestHelper helper) {
        ItemComponentGameTests.fishQualityNullWhenAbsent(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void baitEffectWormsPreset(GameTestHelper helper) {
        ItemComponentGameTests.baitEffectWormsPreset(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void baitEffectBlazedGrubExclusivePool(GameTestHelper helper) {
        ItemComponentGameTests.baitEffectBlazedGrubExclusivePool(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void baitEffectComponentRoundTrip(GameTestHelper helper) {
        ItemComponentGameTests.baitEffectComponentRoundTrip(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void baitEffectNoBaitDefaults(GameTestHelper helper) {
        ItemComponentGameTests.baitEffectNoBaitDefaults(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void baitEffectTrashChancePresets(GameTestHelper helper) {
        ItemComponentGameTests.baitEffectTrashChancePresets(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void baitEffectTrashChanceComponentRoundTrip(GameTestHelper helper) {
        ItemComponentGameTests.baitEffectTrashChanceComponentRoundTrip(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void rodBaitContentsEmptyIsEmpty(GameTestHelper helper) {
        ItemComponentGameTests.rodBaitContentsEmptyIsEmpty(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void rodBaitContentsNonEmptyStackIsNotEmpty(GameTestHelper helper) {
        ItemComponentGameTests.rodBaitContentsNonEmptyStackIsNotEmpty(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void rodBaitContentsCopyStackIsDistinct(GameTestHelper helper) {
        ItemComponentGameTests.rodBaitContentsCopyStackIsDistinct(helper);
    }

    // -------------------------------------------------------------------------
    // MathUtil / Utility tests  (pure logic, zero Minecraft world dependency)
    // -------------------------------------------------------------------------

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void lerpFloatBoundaries(GameTestHelper helper) {
        MathUtilGameTests.lerpFloatBoundaries(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void lerpDoubleBoundaries(GameTestHelper helper) {
        MathUtilGameTests.lerpDoubleBoundaries(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void clampFloatBounds(GameTestHelper helper) {
        MathUtilGameTests.clampFloatBounds(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void clampDoubleBounds(GameTestHelper helper) {
        MathUtilGameTests.clampDoubleBounds(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void clampIntBounds(GameTestHelper helper) {
        MathUtilGameTests.clampIntBounds(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void easeInOutQuadShape(GameTestHelper helper) {
        MathUtilGameTests.easeInOutQuadShape(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void easeOutCubicShape(GameTestHelper helper) {
        MathUtilGameTests.easeOutCubicShape(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void easedLerpIdentityMatchesPlainLerp(GameTestHelper helper) {
        MathUtilGameTests.easedLerpIdentityMatchesPlainLerp(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void easedLerpAppliesEasingFunction(GameTestHelper helper) {
        MathUtilGameTests.easedLerpAppliesEasingFunction(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void utilityFtCreatesNamespacedIdentifier(GameTestHelper helper) {
        MathUtilGameTests.utilityFtCreatesNamespacedIdentifier(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void utilityInterpolateColorBoundaries(GameTestHelper helper) {
        MathUtilGameTests.utilityInterpolateColorBoundaries(helper);
    }

    // -------------------------------------------------------------------------
    // FishingTarget tests  (pure logic, zero Minecraft world dependency)
    // -------------------------------------------------------------------------

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void highOverlapEventuallyCatches(GameTestHelper helper) {
        FishingTargetGameTests.highOverlapEventuallyCatches(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void zeroOverlapEventuallyFails(GameTestHelper helper) {
        FishingTargetGameTests.zeroOverlapEventuallyFails(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void pickRandomBoundaryRolls(GameTestHelper helper) {
        FishingTargetGameTests.pickRandomBoundaryRolls(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void allMovementPatternsTickWithoutThrowing(GameTestHelper helper) {
        FishingTargetGameTests.allMovementPatternsTickWithoutThrowing(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void collectionAnimationLifecycle(GameTestHelper helper) {
        FishingTargetGameTests.collectionAnimationLifecycle(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void failAnimationLifecycle(GameTestHelper helper) {
        FishingTargetGameTests.failAnimationLifecycle(helper);
    }

    // -------------------------------------------------------------------------
    // PlayerQuestState tests  (pure in-memory, no world state needed)
    // -------------------------------------------------------------------------

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void getProgressDefaultsForUntouchedQuest(GameTestHelper helper) {
        PlayerQuestStateGameTests.getProgressDefaultsForUntouchedQuest(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void incrementCountRaisesCountAndFlipsCompleted(GameTestHelper helper) {
        PlayerQuestStateGameTests.incrementCountRaisesCountAndFlipsCompleted(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void canClaimTrueOnlyBetweenCompletionAndClaim(GameTestHelper helper) {
        PlayerQuestStateGameTests.canClaimTrueOnlyBetweenCompletionAndClaim(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void claimAddsTokensWithoutResettingCount(GameTestHelper helper) {
        PlayerQuestStateGameTests.claimAddsTokensWithoutResettingCount(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void resetDailyIfNeededOnlyOnNewDay(GameTestHelper helper) {
        PlayerQuestStateGameTests.resetDailyIfNeededOnlyOnNewDay(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void purchaseFailsWhenBalanceTooLow(GameTestHelper helper) {
        PlayerQuestStateGameTests.purchaseFailsWhenBalanceTooLow(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void purchaseFailsWhenMaxPurchasesReached(GameTestHelper helper) {
        PlayerQuestStateGameTests.purchaseFailsWhenMaxPurchasesReached(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void purchaseZeroMaxPurchasesIsUnlimited(GameTestHelper helper) {
        PlayerQuestStateGameTests.purchaseZeroMaxPurchasesIsUnlimited(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void purchaseSucceedsDeductsAndIncrementsCount(GameTestHelper helper) {
        PlayerQuestStateGameTests.purchaseSucceedsDeductsAndIncrementsCount(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void resetDailyPurchasesIfNeededOnlyOnNewDay(GameTestHelper helper) {
        PlayerQuestStateGameTests.resetDailyPurchasesIfNeededOnlyOnNewDay(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void snapshotsReflectMutations(GameTestHelper helper) {
        PlayerQuestStateGameTests.snapshotsReflectMutations(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void getLifetimeTankPlacementsDefaultsToZero(GameTestHelper helper) {
        PlayerQuestStateGameTests.getLifetimeTankPlacementsDefaultsToZero(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void incrementLifetimeTankPlacementsIncrementsByOnePerCall(GameTestHelper helper) {
        PlayerQuestStateGameTests.incrementLifetimeTankPlacementsIncrementsByOnePerCall(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void lifetimeTankPlacementsPersistThroughCodecRoundTrip(GameTestHelper helper) {
        PlayerQuestStateGameTests.lifetimeTankPlacementsPersistThroughCodecRoundTrip(helper);
    }

    // -------------------------------------------------------------------------
    // ItemEffect condition tests  (pure ItemStack + registry logic, no datapack needed)
    // -------------------------------------------------------------------------

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void itemTagConditionMatchesRealTag(GameTestHelper helper) {
        ItemEffectConditionGameTests.itemTagConditionMatchesRealTag(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void componentConditionMatchesPresenceOnly(GameTestHelper helper) {
        ItemEffectConditionGameTests.componentConditionMatchesPresenceOnly(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void componentValueConditionMatchesFieldValue(GameTestHelper helper) {
        ItemEffectConditionGameTests.componentValueConditionMatchesFieldValue(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void andConditionSemantics(GameTestHelper helper) {
        ItemEffectConditionGameTests.andConditionSemantics(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void itemEffectMatchesRespectsEnabledAndConditions(GameTestHelper helper) {
        ItemEffectConditionGameTests.itemEffectMatchesRespectsEnabledAndConditions(helper);
    }

    // -------------------------------------------------------------------------
    // Fish Tank tests
    // -------------------------------------------------------------------------

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void addItemIntoEmptyTankSucceeds(GameTestHelper helper) {
        FishTankGameTests.addItemIntoEmptyTankSucceeds(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void addItemMergesIntoExistingStackBeforeNewSlot(GameTestHelper helper) {
        FishTankGameTests.addItemMergesIntoExistingStackBeforeNewSlot(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void addItemFailsOnceSwarmCapReached(GameTestHelper helper) {
        FishTankGameTests.addItemFailsOnceSwarmCapReached(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void extractItemRemovesLastSlotLifoOrder(GameTestHelper helper) {
        FishTankGameTests.extractItemRemovesLastSlotLifoOrder(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void firstItemRotationReflectsSlotZeroInsert(GameTestHelper helper) {
        FishTankGameTests.firstItemRotationReflectsSlotZeroInsert(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void cosmeticsRoundTrip(GameTestHelper helper) {
        FishTankGameTests.cosmeticsRoundTrip(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void stackedTanksFormOneColumn(GameTestHelper helper) {
        FishTankGameTests.stackedTanksFormOneColumn(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void hangingCosmeticsStackAndRoundTrip(GameTestHelper helper) {
        FishTankGameTests.hangingCosmeticsStackAndRoundTrip(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void openFacesRoundTrip(GameTestHelper helper) {
        FishTankGameTests.openFacesRoundTrip(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void sameShapeNeighborsConnect(GameTestHelper helper) {
        FishTankGameTests.sameShapeNeighborsConnect(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void crossShapeNeighborsInSameFamilyConnect(GameTestHelper helper) {
        FishTankGameTests.crossShapeNeighborsInSameFamilyConnect(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void standardAndReinforcedNeighborsConnect(GameTestHelper helper) {
        FishTankGameTests.standardAndReinforcedNeighborsConnect(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void newShapesConnectToStandard(GameTestHelper helper) {
        FishTankGameTests.newShapesConnectToStandard(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void newShapesConnectToEachOther(GameTestHelper helper) {
        FishTankGameTests.newShapesConnectToEachOther(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void honeycombSealedFaceIsBlockedEvenWhenGroupedViaAnotherPath(GameTestHelper helper) {
        FishTankGameTests.honeycombSealedFaceIsBlockedEvenWhenGroupedViaAnotherPath(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void honeycombSealedIsolatedPairFormSeparateGroups(GameTestHelper helper) {
        FishTankGameTests.honeycombSealedIsolatedPairFormSeparateGroups(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void brokenTankDropCarriesShapeAndMaterials(GameTestHelper helper) {
        FishTankGameTests.brokenTankDropCarriesShapeAndMaterials(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void brokenTankDropRestoresShapeWhenReplaced(GameTestHelper helper) {
        FishTankGameTests.brokenTankDropRestoresShapeWhenReplaced(helper);
    }

    // -------------------------------------------------------------------------
    // TutorialManager tests
    // -------------------------------------------------------------------------

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionTutorialRealMenuWalksAllLessons(GameTestHelper helper) {
        CompanionTutorialManagerGameTests.firstEquipCompletesCompanionLesson(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionTutorialShortcutsAndShiftReturns(GameTestHelper helper) {
        CompanionTutorialManagerGameTests.shortcutEquipCompletesCompanionLesson(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionTutorialClosingWithCursorFish(GameTestHelper helper) {
        CompanionTutorialManagerGameTests.dismissWithCarriedFishPreservesInventory(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionTutorialRejectsStaleAndUnearnedAdvances(GameTestHelper helper) {
        CompanionTutorialManagerGameTests.stalePacketsCannotReplaceCompanionActions(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionTutorialSkipAndReplayPreserveParty(GameTestHelper helper) {
        CompanionTutorialManagerGameTests.skipAndReplayKeepEarnedPartyAndUnlocks(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionTutorialEquipFromAPile(GameTestHelper helper) {
        CompanionTutorialManagerGameTests.pileFishCanCompleteTheEquipLesson(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void interruptedPracticeResumesAtCastWithoutNewStarterItems(GameTestHelper helper) {
        TutorialManagerGameTests.interruptedPracticeResumesAtCastWithoutNewStarterItems(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void craftingRodFromDefaultStepGrantsWormsAndAdvances(GameTestHelper helper) {
        TutorialManagerGameTests.craftingRodFromDefaultStepGrantsWormsAndAdvances(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void craftingRodAgainAfterAdvancingIsNoOp(GameTestHelper helper) {
        TutorialManagerGameTests.craftingRodAgainAfterAdvancingIsNoOp(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void onBaitLoadedOnlyAdvancesFromBaitLoadStep(GameTestHelper helper) {
        TutorialManagerGameTests.onBaitLoadedOnlyAdvancesFromBaitLoadStep(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void onHookCastOnlyAdvancesFromCastableSteps(GameTestHelper helper) {
        TutorialManagerGameTests.onHookCastOnlyAdvancesFromCastableSteps(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void advanceStepNoOpWhenFromStepDoesNotMatchCurrent(GameTestHelper helper) {
        TutorialManagerGameTests.advanceStepNoOpWhenFromStepDoesNotMatchCurrent(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void tutorialWalksFullDocumentedChainToCompletion(GameTestHelper helper) {
        TutorialManagerGameTests.tutorialWalksFullDocumentedChainToCompletion(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void craftingRodViaShiftClickFromResultSlotAdvancesToBaitLoad(GameTestHelper helper) {
        TutorialManagerGameTests.craftingRodViaShiftClickFromResultSlotAdvancesToBaitLoad(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void craftingRodViaSimpleClickFromResultSlotAdvancesToBaitLoad(GameTestHelper helper) {
        TutorialManagerGameTests.craftingRodViaSimpleClickFromResultSlotAdvancesToBaitLoad(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void loadingBaitViaLeftClickInInventoryScreenAdvancesToWaitingForCast(GameTestHelper helper) {
        TutorialManagerGameTests.loadingBaitViaLeftClickInInventoryScreenAdvancesToWaitingForCast(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void revokingRodAdvancementAllowsReTriggeringAfterReset(GameTestHelper helper) {
        TutorialManagerGameTests.revokingRodAdvancementAllowsReTriggeringAfterReset(helper, helper::makeMockServerPlayerInLevel);
    }

    // -------------------------------------------------------------------------
    // EncyclopediaTutorialManager tests
    // -------------------------------------------------------------------------

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void openingEncyclopediaFromNotStartedStartsIntro(GameTestHelper helper) {
        EncyclopediaTutorialManagerGameTests.openingEncyclopediaFromNotStartedStartsIntro(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void reopeningEncyclopediaWhileInProgressIsIdempotent(GameTestHelper helper) {
        EncyclopediaTutorialManagerGameTests.reopeningEncyclopediaWhileInProgressIsIdempotent(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void openingEncyclopediaAfterCompleteDoesNotRestart(GameTestHelper helper) {
        EncyclopediaTutorialManagerGameTests.openingEncyclopediaAfterCompleteDoesNotRestart(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void encyclopediaAdvanceStepNoOpWhenFromStepDoesNotMatchCurrent(GameTestHelper helper) {
        EncyclopediaTutorialManagerGameTests.advanceStepNoOpWhenFromStepDoesNotMatchCurrent(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void encyclopediaTutorialWalksFullChainToCompletion(GameTestHelper helper) {
        EncyclopediaTutorialManagerGameTests.encyclopediaTutorialWalksFullChainToCompletion(helper, helper::makeMockServerPlayerInLevel);
    }

    // -------------------------------------------------------------------------
    // QuestTracker tests  (pure matching logic + throwaway registry, no player needed)
    // -------------------------------------------------------------------------

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void targetSpeciesGatesMatchWhenPresent(GameTestHelper helper) {
        QuestTrackerGameTests.targetSpeciesGatesMatchWhenPresent(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void targetSpeciesTagGatesMatchWhenPresent(GameTestHelper helper) {
        QuestTrackerGameTests.targetSpeciesTagGatesMatchWhenPresent(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void minQualityIsOrdinalFloor(GameTestHelper helper) {
        QuestTrackerGameTests.minQualityIsOrdinalFloor(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void biomeConditionGatesMatchWhenPresent(GameTestHelper helper) {
        QuestTrackerGameTests.biomeConditionGatesMatchWhenPresent(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void timeConditionGatesMatchWhenPresent(GameTestHelper helper) {
        QuestTrackerGameTests.timeConditionGatesMatchWhenPresent(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void weatherConditionGatesMatchWhenPresent(GameTestHelper helper) {
        QuestTrackerGameTests.weatherConditionGatesMatchWhenPresent(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void zoneConditionGatesMatchWhenPresent(GameTestHelper helper) {
        QuestTrackerGameTests.zoneConditionGatesMatchWhenPresent(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void minSizeIsFloor(GameTestHelper helper) {
        QuestTrackerGameTests.minSizeIsFloor(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void minSessionCatchesDoesNotAffectPerStackMatching(GameTestHelper helper) {
        QuestTrackerGameTests.minSessionCatchesDoesNotAffectPerStackMatching(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void allConditionsMustMatchTogether(GameTestHelper helper) {
        QuestTrackerGameTests.allConditionsMustMatchTogether(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void getActiveDailiesIsStablePerDay(GameTestHelper helper) {
        QuestTrackerGameTests.getActiveDailiesIsStablePerDay(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void getActiveDailiesNeverExceedsCapAndExcludesNonDaily(GameTestHelper helper) {
        QuestTrackerGameTests.getActiveDailiesNeverExceedsCapAndExcludesNonDaily(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void getActiveDailiesCapsAtRegistrySizeWhenSmallerThanCount(GameTestHelper helper) {
        QuestTrackerGameTests.getActiveDailiesCapsAtRegistrySizeWhenSmallerThanCount(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void tankSnapshotConditionLifetimeDefaultsToFalseWhenAbsent(GameTestHelper helper) {
        QuestTrackerGameTests.tankSnapshotConditionLifetimeDefaultsToFalseWhenAbsent(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void tankSnapshotConditionLifetimeRoundTripsThroughCodec(GameTestHelper helper) {
        QuestTrackerGameTests.tankSnapshotConditionLifetimeRoundTripsThroughCodec(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void tankKeeperSilverCompletesAndUnlocksToothShape(GameTestHelper helper) {
        QuestTrackerGameTests.tankKeeperSilverCompletesAndUnlocksToothShape(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void tankKeeperGoldCompletesAndUnlocksFilmShape(GameTestHelper helper) {
        QuestTrackerGameTests.tankKeeperGoldCompletesAndUnlocksFilmShape(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void lifetimeTankPlacementCounterIsCumulativeAcrossDifferentTanks(GameTestHelper helper) {
        QuestTrackerGameTests.lifetimeTankPlacementCounterIsCumulativeAcrossDifferentTanks(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void lifetimeTankPlacementCounterIgnoresNonFishItems(GameTestHelper helper) {
        QuestTrackerGameTests.lifetimeTankPlacementCounterIgnoresNonFishItems(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void tankStarterCompletesWhenAnyFishIsDisplayedInATank(GameTestHelper helper) {
        QuestTrackerGameTests.tankStarterCompletesWhenAnyFishIsDisplayedInATank(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void goldenShowcaseRequiresLegendaryQualityAndGoldFrameTogether(GameTestHelper helper) {
        QuestTrackerGameTests.goldenShowcaseRequiresLegendaryQualityAndGoldFrameTogether(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void blueToTheGillsCountsMatchingSpeciesLiveAndCannotRegressOnceComplete(GameTestHelper helper) {
        QuestTrackerGameTests.blueToTheGillsCountsMatchingSpeciesLiveAndCannotRegressOnceComplete(helper, helper::makeMockServerPlayerInLevel);
    }

    // -------------------------------------------------------------------------
    // Quest content validation  (runs against the live quest/fish_profile/biome registries)
    // -------------------------------------------------------------------------

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void everyTargetSpeciesQuestIsSatisfiable(GameTestHelper helper) {
        QuestSatisfiabilityGameTests.everyTargetSpeciesQuestIsSatisfiable(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void everyPrerequisiteResolves(GameTestHelper helper) {
        QuestSatisfiabilityGameTests.everyPrerequisiteResolves(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void noPrerequisiteCycles(GameTestHelper helper) {
        QuestSatisfiabilityGameTests.noPrerequisiteCycles(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void nonHiddenQuestIsAlwaysListed(GameTestHelper helper) {
        QuestLogVisibilityGameTests.nonHiddenQuestIsAlwaysListed(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void hiddenSecretStaysOutUntilCompleted(GameTestHelper helper) {
        QuestLogVisibilityGameTests.hiddenSecretStaysOutUntilCompleted(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void hiddenChainQuestAppearsOncePrerequisiteClaimed(GameTestHelper helper) {
        QuestLogVisibilityGameTests.hiddenChainQuestAppearsOncePrerequisiteClaimed(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void completedChainQuestIsListedEvenIfPrerequisiteUnclaimed(GameTestHelper helper) {
        QuestLogVisibilityGameTests.completedChainQuestIsListedEvenIfPrerequisiteUnclaimed(helper);
    }


    @GameTest(structure = "fishtastic:empty_testarea")
    public static void gatedEntriesAreAbsentUntilTheirQuestIsClaimed(GameTestHelper helper) {
        CapstoneRewardGameTests.gatedEntriesAreAbsentUntilTheirQuestIsClaimed(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void gatedEntriesCanAppearOnceTheirQuestIsClaimed(GameTestHelper helper) {
        CapstoneRewardGameTests.gatedEntriesCanAppearOnceTheirQuestIsClaimed(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void lockedEntriesDoNotConsumeShopSlots(GameTestHelper helper) {
        CapstoneRewardGameTests.lockedEntriesDoNotConsumeShopSlots(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void gatedEntryIsEarnableViaEveryUnlockQuest(GameTestHelper helper) {
        CapstoneRewardGameTests.gatedEntryIsEarnableViaEveryUnlockQuest(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void unlockGatesNeverPointAtDailyQuests(GameTestHelper helper) {
        CapstoneRewardGameTests.unlockGatesNeverPointAtDailyQuests(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void capstoneTanksCarryTheirMaterialsComponent(GameTestHelper helper) {
        CapstoneRewardGameTests.capstoneTanksCarryTheirMaterialsComponent(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void displayCountClampsOvershootToTheTarget(GameTestHelper helper) {
        PlayerQuestStateGameTests.displayCountClampsOvershootToTheTarget(helper);
    }


    @GameTest(structure = "fishtastic:empty_testarea")
    public static void stormCharmIsSlottableIntoTheRod(GameTestHelper helper) {
        StormCharmGameTests.stormCharmIsSlottableIntoTheRod(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void stormCharmCarriesNoCharmEffect(GameTestHelper helper) {
        StormCharmGameTests.stormCharmCarriesNoCharmEffect(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void stormCharmStacksUnlikeRodCharms(GameTestHelper helper) {
        StormCharmGameTests.stormCharmStacksUnlikeRodCharms(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void summonedStormIsReadAsThunderByQuestConditions(GameTestHelper helper) {
        StormCharmGameTests.summonedStormIsReadAsThunderByQuestConditions(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void handUseChargesUpAndIsFreeToCancel(GameTestHelper helper) {
        StormCharmGameTests.handUseChargesUpAndIsFreeToCancel(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void stormDurationIsWithinVanillaThunderRange(GameTestHelper helper) {
        StormCharmGameTests.stormDurationIsWithinVanillaThunderRange(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void dailyPoolIsLargerThanTheDrawAndActuallyRotates(GameTestHelper helper) {
        QuestSatisfiabilityGameTests.dailyPoolIsLargerThanTheDrawAndActuallyRotates(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void lifetimeQuestsCarryNoUnreplayableConditions(GameTestHelper helper) {
        QuestSatisfiabilityGameTests.lifetimeQuestsCarryNoUnreplayableConditions(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void catchCountMatchingScopesToTheRequestedSpecies(GameTestHelper helper) {
        LifetimeQuestProgressGameTests.catchCountMatchingScopesToTheRequestedSpecies(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void lifetimeCountsAreIsolatedPerPlayer(GameTestHelper helper) {
        LifetimeQuestProgressGameTests.lifetimeCountsAreIsolatedPerPlayer(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void oneLifetimeTotalSatisfiesEveryTierItHasPassed(GameTestHelper helper) {
        LifetimeQuestProgressGameTests.oneLifetimeTotalSatisfiesEveryTierItHasPassed(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void nonFishCatchesDoNotAdvanceLifetimeChains(GameTestHelper helper) {
        LifetimeQuestProgressGameTests.nonFishCatchesDoNotAdvanceLifetimeChains(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void lifetimeCompatibilityRejectsEnvironmentalConditions(GameTestHelper helper) {
        LifetimeQuestProgressGameTests.lifetimeCompatibilityRejectsEnvironmentalConditions(helper);
    }

    // -------------------------------------------------------------------------
    // Packet round-trip tests  (pure StreamCodec encode/decode, no player needed)
    // -------------------------------------------------------------------------

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void startFishingMinigamePacketRoundTrips(GameTestHelper helper) {
        PacketRoundTripGameTests.startFishingMinigamePacketRoundTrips(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void purchaseShopEntryPacketRoundTrips(GameTestHelper helper) {
        PacketRoundTripGameTests.purchaseShopEntryPacketRoundTrips(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void questSyncPacketRoundTrips(GameTestHelper helper) {
        PacketRoundTripGameTests.questSyncPacketRoundTrips(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void questSyncPacketCleanupGoalMilestoneRoundTrips(GameTestHelper helper) {
        PacketRoundTripGameTests.questSyncPacketCleanupGoalMilestoneRoundTrips(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void fishEncyclopediaSyncPacketRoundTrips(GameTestHelper helper) {
        PacketRoundTripGameTests.fishEncyclopediaSyncPacketRoundTrips(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void requestFishEncyclopediaPacketRoundTrips(GameTestHelper helper) {
        PacketRoundTripGameTests.requestFishEncyclopediaPacketRoundTrips(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionScenePacketRoundTrips(GameTestHelper helper) {
        PacketRoundTripGameTests.companionScenePacketRoundTrips(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionSlotPacketsRoundTrip(GameTestHelper helper) {
        PacketRoundTripGameTests.companionSlotPacketsRoundTrip(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionPartyCodecsRoundTrip(GameTestHelper helper) {
        PacketRoundTripGameTests.companionPartyCodecsRoundTrip(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void ambientContextRequestPacketRoundTrips(GameTestHelper helper) {
        PacketRoundTripGameTests.ambientContextRequestPacketRoundTrips(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void ambientContextResponsePacketRoundTrips(GameTestHelper helper) {
        PacketRoundTripGameTests.ambientContextResponsePacketRoundTrips(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void oversizedOriginListIsRejectedOnEncodeAndDecode(GameTestHelper helper) {
        PacketRoundTripGameTests.oversizedOriginListIsRejectedOnEncodeAndDecode(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void oversizedEntryListIsRejectedOnEncodeAndDecode(GameTestHelper helper) {
        PacketRoundTripGameTests.oversizedEntryListIsRejectedOnEncodeAndDecode(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void oversizedCandidateListIsRejectedOnEncodeAndDecode(GameTestHelper helper) {
        PacketRoundTripGameTests.oversizedCandidateListIsRejectedOnEncodeAndDecode(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void unknownStatusIdDecodesToARejectionNotAnError(GameTestHelper helper) {
        PacketRoundTripGameTests.unknownStatusIdDecodesToARejectionNotAnError(helper);
    }

    // -------------------------------------------------------------------------
    // Ambient context exchange (Fish in World, Phase 4) — server-side validation, resolve
    // equality, session-snapshot precedence and the per-player rate limit
    // -------------------------------------------------------------------------

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void authoritativeResolveMatchesServerState(GameTestHelper helper) {
        AmbientContextGameTests.authoritativeResolveMatchesServerState(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void heldRodLaneIsResolvedFromTheServersOwnInventory(GameTestHelper helper) {
        AmbientContextGameTests.heldRodLaneIsResolvedFromTheServersOwnInventory(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void dimensionMismatchIsRejected(GameTestHelper helper) {
        AmbientContextGameTests.dimensionMismatchIsRejected(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void farOriginIsRejectedBeforeTheUnloadedCheck(GameTestHelper helper) {
        AmbientContextGameTests.farOriginIsRejectedBeforeTheUnloadedCheck(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void unloadedOriginIsRejectedWithoutLoadingTheChunk(GameTestHelper helper) {
        AmbientContextGameTests.unloadedOriginIsRejectedWithoutLoadingTheChunk(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void rateLimitDropsRequestsPastTheBurst(GameTestHelper helper) {
        AmbientContextGameTests.rateLimitDropsRequestsPastTheBurst(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void sessionSnapshotTakesPrecedenceOverAFreshResolve(GameTestHelper helper) {
        AmbientContextGameTests.sessionSnapshotTakesPrecedenceOverAFreshResolve(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void aDifferentPondNearTheCastFallsThroughToAFreshResolve(GameTestHelper helper) {
        AmbientContextGameTests.aDifferentPondNearTheCastFallsThroughToAFreshResolve(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void anEmptyRequestDoesNotConsumeAThrottleToken(GameTestHelper helper) {
        AmbientContextGameTests.anEmptyRequestDoesNotConsumeAThrottleToken(helper, helper::makeMockServerPlayerInLevel);
    }

    // -------------------------------------------------------------------------
    // FishEncyclopediaEntry tests  (pure Codec, no world state needed)
    // -------------------------------------------------------------------------

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void emptyObjectDecodesToAllDefaults(GameTestHelper helper) {
        FishEncyclopediaEntryGameTests.emptyObjectDecodesToAllDefaults(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void legacyThresholdsBlockIsIgnored(GameTestHelper helper) {
        FishEncyclopediaEntryGameTests.legacyThresholdsBlockIsIgnored(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void everyFishGetsDerivedThresholdsWithinCap(GameTestHelper helper) {
        FishEncyclopediaEntryGameTests.everyFishGetsDerivedThresholdsWithinCap(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void fullEntryRoundTripsThroughJson(GameTestHelper helper) {
        FishEncyclopediaEntryGameTests.fullEntryRoundTripsThroughJson(helper);
    }

    // -------------------------------------------------------------------------
    // Companion profile data (ground benthics, docs/fish-companions.md)
    // -------------------------------------------------------------------------

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void benthicCompanionDataLoads(GameTestHelper helper) {
        CompanionProfileGameTests.benthicCompanionDataLoads(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void onlyBenthicsDeclareGroundCompanionData(GameTestHelper helper) {
        CompanionProfileGameTests.onlyBenthicsDeclareGroundCompanionData(helper);
    }

    // -------------------------------------------------------------------------
    // FishEncyclopediaClientCache / FishEncyclopediaClientHelper tests
    // -------------------------------------------------------------------------

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void cacheStartsEmpty(GameTestHelper helper) {
        FishEncyclopediaClientGameTests.cacheStartsEmpty(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void updatePopulatesCatchCountsByFishType(GameTestHelper helper) {
        FishEncyclopediaClientGameTests.updatePopulatesCatchCountsByFishType(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void updateIndexesBestSizesByFishType(GameTestHelper helper) {
        FishEncyclopediaClientGameTests.updateIndexesBestSizesByFishType(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void updateReplacesPriorContentsRatherThanMerging(GameTestHelper helper) {
        FishEncyclopediaClientGameTests.updateReplacesPriorContentsRatherThanMerging(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void resetClearsAllMaps(GameTestHelper helper) {
        FishEncyclopediaClientGameTests.resetClearsAllMaps(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void getEncyclopediaEntryFallsBackToDefaultForUnregisteredFish(GameTestHelper helper) {
        FishEncyclopediaClientGameTests.getEncyclopediaEntryFallsBackToDefaultForUnregisteredFish(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void getAllFishProfilesSortedMatchesRegistryEntrySet(GameTestHelper helper) {
        FishEncyclopediaClientGameTests.getAllFishProfilesSortedMatchesRegistryEntrySet(helper);
    }

    // -------------------------------------------------------------------------
    // FishingMinigameManager tests
    // -------------------------------------------------------------------------

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void startSessionEndToEndReturnsValidSessionId(GameTestHelper helper) {
        FishingMinigameManagerGameTests.startSessionEndToEndReturnsValidSessionId(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void startSessionWhenAlreadyActiveReturnsNegativeOneUnlessCancelled(GameTestHelper helper) {
        FishingMinigameManagerGameTests.startSessionWhenAlreadyActiveReturnsNegativeOneUnlessCancelled(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void cancelSessionRemovesActiveSession(GameTestHelper helper) {
        FishingMinigameManagerGameTests.cancelSessionRemovesActiveSession(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void handleMinigameCompleteAwardsOnlyRewardsForValidIndicesAndIgnoresOthers(GameTestHelper helper) {
        FishingMinigameManagerGameTests.handleMinigameCompleteAwardsOnlyRewardsForValidIndicesAndIgnoresOthers(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void handleMinigameCompleteIsNoOpForUnknownOrMismatchedSession(GameTestHelper helper) {
        FishingMinigameManagerGameTests.handleMinigameCompleteIsNoOpForUnknownOrMismatchedSession(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void handleMinigameCompleteSessionIsSingleUseEvenWhenIndicesAreInvalid(GameTestHelper helper) {
        FishingMinigameManagerGameTests.handleMinigameCompleteSessionIsSingleUseEvenWhenIndicesAreInvalid(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void handleMinigameCompleteGrantsRewardsEvenWhenCompletedInUnderTwentyTicks(GameTestHelper helper) {
        FishingMinigameManagerGameTests.handleMinigameCompleteGrantsRewardsEvenWhenCompletedInUnderTwentyTicks(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void handleMinigameCompleteConsumesBaitOnlyWhenRewardsWereActuallyAwarded(GameTestHelper helper) {
        FishingMinigameManagerGameTests.handleMinigameCompleteConsumesBaitOnlyWhenRewardsWereActuallyAwarded(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void trashChanceOneAlwaysAwardsTrashItems(GameTestHelper helper) {
        FishingMinigameManagerGameTests.trashChanceOneAlwaysAwardsTrashItems(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void handleMinigameCompleteDropsRewardAtPlayerFeetWhenInventoryIsFull(GameTestHelper helper) {
        FishingMinigameManagerGameTests.handleMinigameCompleteDropsRewardAtPlayerFeetWhenInventoryIsFull(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void handleMinigameCompleteDropsRewardWhenInventoryIsFullAndAutoPileFishIsActive(GameTestHelper helper) {
        FishingMinigameManagerGameTests.handleMinigameCompleteDropsRewardWhenInventoryIsFullAndAutoPileFishIsActive(helper, helper::makeMockServerPlayerInLevel);
    }

    // Phase 3 (Fish in World) species snapshot tests.

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void startSessionCapturesResolvedSpeciesSnapshot(GameTestHelper helper) {
        FishingMinigameManagerGameTests.startSessionCapturesResolvedSpeciesSnapshot(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void personalEquipmentReadsCarriedDiamondsAndBaselineStaysNeutral(GameTestHelper helper) {
        FishingMinigameManagerGameTests.personalEquipmentReadsCarriedDiamondsAndBaselineStaysNeutral(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void snapshotRecordsMainHandRodWhenBothHandsHoldRods(GameTestHelper helper) {
        FishingMinigameManagerGameTests.snapshotRecordsMainHandRodWhenBothHandsHoldRods(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void testSeededSessionCapturesNoSpeciesSnapshot(GameTestHelper helper) {
        FishingMinigameManagerGameTests.testSeededSessionCapturesNoSpeciesSnapshot(helper, helper::makeMockServerPlayerInLevel);
    }

    // -------------------------------------------------------------------------
    // Interactive fish school tests
    // -------------------------------------------------------------------------

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void caught_school_target_depletes_once(GameTestHelper helper) {
        FishSchoolGameTests.caughtSchoolTargetDepletesOnce(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void missed_school_target_recovers_and_retry_keeps_reward(GameTestHelper helper) {
        FishSchoolGameTests.missedTargetRecoversAndRetryKeepsReward(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea", maxTicks = 360)
    public static void live_school_approaches_and_triggers_owned_bite(GameTestHelper helper) {
        FishSchoolGameTests.liveSchoolApproachesAndTriggersOwnedBite(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea", maxTicks = 360)
    public static void school_stays_in_its_water_and_steps_once_per_tick(GameTestHelper helper) {
        FishSchoolGameTests.schoolStaysInItsWaterAndStepsOncePerTick(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void school_transient_reservations_recover_across_save_reload(GameTestHelper helper) {
        FishSchoolGameTests.transientReservationsRecoverAcrossSaveReload(helper);
    }

    // -------------------------------------------------------------------------
    // ShopEntry tests  (pure registry-only logic, no player needed)
    // -------------------------------------------------------------------------

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void getActiveDailyShopIsStablePerDay(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopIsStablePerDay(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void shopRerollInteractionGrantsOneFreeUsePerDayAndEscalatesPaidCosts(GameTestHelper helper) {
        ShopEntryGameTests.shopRerollInteractionGrantsOneFreeUsePerDayAndEscalatesPaidCosts(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void getActiveDailyShopNeverExceedsCap(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopNeverExceedsCap(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void getActiveDailyShopCapsAtRegistrySizeWhenSmallerThanCount(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopCapsAtRegistrySizeWhenSmallerThanCount(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void getActiveDailyShopWeightBiasesSelectionTowardHeavierEntries(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopWeightBiasesSelectionTowardHeavierEntries(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void getActiveDailyShopHandlesNonPositiveWeightWithoutError(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopHandlesNonPositiveWeightWithoutError(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void shopEntryCodecDefaultsWeightToOneWhenAbsent(GameTestHelper helper) {
        ShopEntryGameTests.shopEntryCodecDefaultsWeightToOneWhenAbsent(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void getActiveDailyShopCharmReplacementRateMatchesConfiguredChance(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopCharmReplacementRateMatchesConfiguredChance(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void getActiveDailyShopNeverReplacesWithoutACharmPool(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopNeverReplacesWithoutACharmPool(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void getActiveDailyShopHandlesEmptyMainPoolWithCharmsOnly(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopHandlesEmptyMainPoolWithCharmsOnly(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void getActiveDailyShopTankShapeReplacementRateMatchesConfiguredChance(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopTankShapeReplacementRateMatchesConfiguredChance(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void getActiveDailyShopNeverReplacesWithoutATankShapePool(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopNeverReplacesWithoutATankShapePool(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void getActiveDailyShopHandlesEmptyMainPoolWithTankShapesOnly(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopHandlesEmptyMainPoolWithTankShapesOnly(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void getActiveDailyShopCharmAndTankShapeReplacementsCanCoexist(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopCharmAndTankShapeReplacementsCanCoexist(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void grantRewardsDropsLeftoverWhenInventoryIsFull(GameTestHelper helper) {
        ShopEntryGameTests.grantRewardsDropsLeftoverWhenInventoryIsFull(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void grantRewardsDeliversNormallyWhenInventoryHasSpace(GameTestHelper helper) {
        ShopEntryGameTests.grantRewardsDeliversNormallyWhenInventoryHasSpace(helper, helper::makeMockServerPlayerInLevel);
    }

    // -------------------------------------------------------------------------
    // Fish companions — bond / unequip / storage
    // -------------------------------------------------------------------------

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionMenuClickEquipsAndReturns(GameTestHelper helper) {
        CompanionMenuGameTests.clickEquipsAndReturns(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionMenuCursorSwapExchangesCompanions(GameTestHelper helper) {
        CompanionMenuGameTests.cursorSwapExchangesCompanions(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionMenuNumberKeySwapExchangesAndReturns(GameTestHelper helper) {
        CompanionMenuGameTests.numberKeySwapExchangesAndReturns(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionMenuDragPlacesOneFish(GameTestHelper helper) {
        CompanionMenuGameTests.dragPlacesOneFish(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionMenuShiftClickTakesOneFromAStack(GameTestHelper helper) {
        CompanionMenuGameTests.shiftClickTakesOneFromAStack(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionMenuShiftClickFillsTheFirstEmptySlotOnce(GameTestHelper helper) {
        CompanionMenuGameTests.shiftClickFillsTheFirstEmptySlotOnce(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionMenuShiftClickWithAFullPartyOrInventoryChangesNothing(GameTestHelper helper) {
        CompanionMenuGameTests.shiftClickWithAFullPartyOrInventoryChangesNothing(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionMenuThreeSlotsEquipAndReturn(GameTestHelper helper) {
        CompanionMenuGameTests.threeSlotsEquipAndReturn(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionMenuMovingBetweenSlotsReordersTheParty(GameTestHelper helper) {
        CompanionMenuGameTests.movingBetweenSlotsReordersTheParty(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionMenuLockedFishIsRejected(GameTestHelper helper) {
        CompanionMenuGameTests.lockedFishIsRejected(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionMenuClosingWithAFishOnTheCursorKeepsIt(GameTestHelper helper) {
        CompanionMenuGameTests.closingWithAFishOnTheCursorKeepsIt(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionMenuClickAfterAnOutsideBondSeesTheNewCompanion(GameTestHelper helper) {
        CompanionMenuGameTests.clickAfterAnOutsideBondSeesTheNewCompanion(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionMenuOutsideRenameReachesTheSlot(GameTestHelper helper) {
        CompanionMenuGameTests.outsideRenameReachesTheSlot(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionMenuPileFishGoesToTheCursor(GameTestHelper helper) {
        CompanionMenuGameTests.pileFishGoesToTheCursor(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionMenuPileLeftWithOneFishBecomesThatFish(GameTestHelper helper) {
        CompanionMenuGameTests.pileLeftWithOneFishBecomesThatFish(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionMenuPileTakeNeedsAnEmptyCursor(GameTestHelper helper) {
        CompanionMenuGameTests.pileTakeNeedsAnEmptyCursor(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionMenuPileTakeRefusesAStaleDescription(GameTestHelper helper) {
        CompanionMenuGameTests.pileTakeRefusesAStaleDescription(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionMenuRenameSecondSlotLeavesTheLead(GameTestHelper helper) {
        CompanionMenuGameTests.renameSecondSlotLeavesTheLead(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void bondTakesTheFishFromTheInventory(GameTestHelper helper) {
        CompanionGameTests.bondTakesTheFishFromTheInventory(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void bondTakesTheFishFromInsideAPile(GameTestHelper helper) {
        CompanionGameTests.bondTakesTheFishFromInsideAPile(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void bondFromATwoFishPileLeavesTheOtherFishLoose(GameTestHelper helper) {
        CompanionGameTests.bondFromATwoFishPileLeavesTheOtherFishLoose(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void bondWithAFishThePlayerDoesNotCarryChangesNothing(GameTestHelper helper) {
        CompanionGameTests.bondWithAFishThePlayerDoesNotCarryChangesNothing(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void bondRefusesSomethingThatIsNotAFish(GameTestHelper helper) {
        CompanionGameTests.bondRefusesSomethingThatIsNotAFish(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void unequipReturnsTheIdenticalFish(GameTestHelper helper) {
        CompanionGameTests.unequipReturnsTheIdenticalFish(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void unequipWithAFullInventoryDropsTheFish(GameTestHelper helper) {
        CompanionGameTests.unequipWithAFullInventoryDropsTheFish(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void bondingASecondFishFillsTheNextSlot(GameTestHelper helper) {
        CompanionGameTests.bondingASecondFishFillsTheNextSlot(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void bondIntoAFullPartyIsRefused(GameTestHelper helper) {
        CompanionGameTests.bondIntoAFullPartyIsRefused(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void bondIntoANamedSlotReturnsItsFish(GameTestHelper helper) {
        CompanionGameTests.bondIntoANamedSlotReturnsItsFish(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void threeOfOneSpeciesFillTheParty(GameTestHelper helper) {
        CompanionGameTests.threeOfOneSpeciesFillTheParty(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionIsKeptOnDeath(GameTestHelper helper) {
        CompanionGameTests.companionIsKeptOnDeath(helper, helper::makeMockServerPlayerInLevel, (before, after) -> {
            // Forge keeps parties in world SavedData keyed by player UUID, so a real respawn keeps
            // the same UUID and needs no copy. The harness's synthetic "after" player has a
            // different UUID, so move the party to mirror what a respawn preserves.
            grill24.fishtastic.companion.CompanionStorage.setParty(after,
                    new grill24.fishtastic.companion.CompanionParty(
                            grill24.fishtastic.companion.CompanionStorage.party(before).slots()));
        });
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionLockedSpeciesRefusesBond(GameTestHelper helper) {
        CompanionBondGameTests.lockedSpeciesRefusesBond(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionCompleteEntryUnlocksAndNeverReLocks(GameTestHelper helper) {
        CompanionBondGameTests.completeEntryUnlocksAndNeverReLocks(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionUnlistedNeverUnlocksByCounts(GameTestHelper helper) {
        CompanionBondGameTests.unlistedSpeciesNeverUnlocksByCounts(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionUnlockQueuesOneBanner(GameTestHelper helper) {
        CompanionBondGameTests.unlockQueuesOneBanner(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionFakeDescriptionCannotBond(GameTestHelper helper) {
        CompanionBondGameTests.fakeDescriptionCannotBondAnUncarriedFish(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionRenameSetsAndClearsTheName(GameTestHelper helper) {
        CompanionBondGameTests.renameSetsAndClearsTheName(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionPickerGroupsIdenticalFish(GameTestHelper helper) {
        CarriedFishGameTests.identicalFishCollapseWithTheirTotalCount(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionPickerSplitsAnyComponentDifference(GameTestHelper helper) {
        CarriedFishGameTests.anyComponentDifferenceSplitsTheRow(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionPickerIgnoresEmptyStacks(GameTestHelper helper) {
        CarriedFishGameTests.emptyStacksAreIgnored(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionQuestUnlocksOnlyOnceDone(GameTestHelper helper) {
        CompanionQuestUnlockGameTests.questUnlocksOnlyOnceDone(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionQuestScanReportsEachSpeciesOnce(GameTestHelper helper) {
        CompanionQuestUnlockGameTests.questScanReportsEachSpeciesOnce(helper);
    }

    // ----- Companion party grouping (party spec §3) -----

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionGroupsFreeSwimmerDuplicatesSchool(GameTestHelper helper) {
        CompanionGroupsGameTests.freeSwimmerDuplicatesSchool(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionGroupsGroundBenthicsFormTheirFormation(GameTestHelper helper) {
        CompanionGroupsGameTests.groundBenthicsFormTheirFormation(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionGroupsNonGroupingDuplicatesStaySeparate(GameTestHelper helper) {
        CompanionGroupsGameTests.nonGroupingDuplicatesStaySeparate(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionGroupsMixedPartyIsAllSolos(GameTestHelper helper) {
        CompanionGroupsGameTests.mixedPartyIsAllSolos(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionGroupsMixedFreeSwimmersShoal(GameTestHelper helper) {
        CompanionGroupsGameTests.mixedFreeSwimmersShoal(helper);
    }

    // ----- Companion pet-to-pet scenes (Phase 6) -----

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionSceneSameSpeciesPairPlaysG1(GameTestHelper helper) {
        CompanionSceneGameTests.sameSpeciesPairPlaysG1AndTellsBothOwners(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionSceneCooldownGatesRepeats(GameTestHelper helper) {
        CompanionSceneGameTests.cooldownGatesRepeats(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionSceneNoSceneWhileOwnerInMinigame(GameTestHelper helper) {
        CompanionSceneGameTests.noSceneWhileOwnerInMinigame(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionSceneNoSceneWhileOwnerHasLineOut(GameTestHelper helper) {
        CompanionSceneGameTests.noSceneWhileOwnerHasALineOut(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionSceneDifferentFreeSwimmersPlayG2(GameTestHelper helper) {
        CompanionSceneGameTests.differentFreeSwimmersPlayG2(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionScenePercherPairPlaysNothing(GameTestHelper helper) {
        CompanionSceneGameTests.percherPairPlaysNothing(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionSceneSameSpeciesPercherPairPlaysNothing(GameTestHelper helper) {
        CompanionSceneGameTests.sameSpeciesPercherPairPlaysNothing(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionSceneSameSpeciesDrifterPairPlaysNothing(GameTestHelper helper) {
        CompanionSceneGameTests.sameSpeciesDrifterPairPlaysNothing(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionSceneFreeSwimmersTooCloseForG2(GameTestHelper helper) {
        CompanionSceneGameTests.freeSwimmersTooCloseForG2(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionSceneOutOfRangePairPlaysNothing(GameTestHelper helper) {
        CompanionSceneGameTests.outOfRangePairPlaysNothing(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionSceneOneScenePerCompanion(GameTestHelper helper) {
        CompanionSceneGameTests.oneScenePerCompanion(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionSceneAGroupTakesPartThroughItsLeader(GameTestHelper helper) {
        CompanionSceneGameTests.aGroupTakesPartThroughItsLeader(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionSceneOneScenePerOwnerPairAtATime(GameTestHelper helper) {
        CompanionSceneGameTests.oneScenePerOwnerPairAtATime(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionSceneSameSpeciesBeatsFreeSwimmers(GameTestHelper helper) {
        CompanionSceneGameTests.sameSpeciesPairBeatsFreeSwimmers(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void companionSceneNearbyObserversToldFarOnesNot(GameTestHelper helper) {
        CompanionSceneGameTests.nearbyObserversAreToldAndFarOnesAreNot(helper, helper::makeMockServerPlayerInLevel);
    }

    // -------------------------------------------------------------------------
    // RemoveTankEntryPacket.giveOrDrop — fish tank browser GUI removal delivery
    // -------------------------------------------------------------------------

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void giveOrDropDropsFishWhenInventoryIsFull(GameTestHelper helper) {
        RemoveTankEntryPacketGameTests.giveOrDropDropsFishWhenInventoryIsFull(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void giveOrDropDropsFishWhenInventoryIsFullInCreativeMode(GameTestHelper helper) {
        RemoveTankEntryPacketGameTests.giveOrDropDropsFishWhenInventoryIsFullInCreativeMode(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void giveOrDropDeliversFishNormallyWhenInventoryHasSpace(GameTestHelper helper) {
        RemoveTankEntryPacketGameTests.giveOrDropDeliversFishNormallyWhenInventoryHasSpace(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void giveOrDropPilesASecondFishIntoTheExistingPile(GameTestHelper helper) {
        RemoveTankEntryPacketGameTests.giveOrDropPilesASecondFishIntoTheExistingPile(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void giveOrDropThroughRealMenuAfterPickupWhileOpenDoesNotLoseTheFish(GameTestHelper helper) {
        RemoveTankEntryPacketGameTests.giveOrDropThroughRealMenuAfterPickupWhileOpenDoesNotLoseTheFish(helper, helper::makeMockServerPlayerInLevel);
    }

    // -------------------------------------------------------------------------
    // Fish Tank Assembly refit mode
    // -------------------------------------------------------------------------

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void refitOutputIsBlockedUntilSomethingChanges(GameTestHelper helper) {
        FishTankAssemblyRefitGameTests.refitOutputIsBlockedUntilSomethingChanges(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void refitReplacesOnlyTheStagedMaterialAndRefundsTheOldOne(GameTestHelper helper) {
        FishTankAssemblyRefitGameTests.refitReplacesOnlyTheStagedMaterialAndRefundsTheOldOne(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void refitShapeOnlyChangesShapeAndConsumesNoMaterials(GameTestHelper helper) {
        FishTankAssemblyRefitGameTests.refitShapeOnlyChangesShapeAndConsumesNoMaterials(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void refitCombinedChangesEverythingAndRefundsEachReplacedBlock(GameTestHelper helper) {
        FishTankAssemblyRefitGameTests.refitCombinedChangesEverythingAndRefundsEachReplacedBlock(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void refitPreservesUnrelatedComponents(GameTestHelper helper) {
        FishTankAssemblyRefitGameTests.refitPreservesUnrelatedComponents(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void refitToSandlessShapeIgnoresTheSandSlot(GameTestHelper helper) {
        FishTankAssemblyRefitGameTests.refitToSandlessShapeIgnoresTheSandSlot(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void refitFromSandlessShapeDoesNotRefundPlaceholderSand(GameTestHelper helper) {
        FishTankAssemblyRefitGameTests.refitFromSandlessShapeDoesNotRefundPlaceholderSand(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void craftingWithoutATankIsUnchanged(GameTestHelper helper) {
        FishTankAssemblyRefitGameTests.craftingWithoutATankIsUnchanged(helper, helper::makeMockServerPlayerInLevel);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void tankSlotOnlyAcceptsFishTanks(GameTestHelper helper) {
        FishTankAssemblyRefitGameTests.tankSlotOnlyAcceptsFishTanks(helper, helper::makeMockServerPlayerInLevel);
    }

    // -------------------------------------------------------------------------
    // Creative tab tests
    // -------------------------------------------------------------------------

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void decorationsTabContainsExactlyCosmeticStructuresAndDecorations(GameTestHelper helper) {
        CreativeTabGameTests.decorationsTabContainsExactlyCosmeticStructuresAndDecorations(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void mainTabNoLongerContainsMovedCosmetics(GameTestHelper helper) {
        CreativeTabGameTests.mainTabNoLongerContainsMovedCosmetics(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void advanceStepBaitLoadTransitionsToWaitingForCast(GameTestHelper helper) {
        TutorialManagerGameTests.advanceStepBaitLoadTransitionsToWaitingForCast(helper, helper::makeMockServerPlayerInLevel);
    }

    // -------------------------------------------------------------------------
    // Catch celebration tests  (pure timeline logic, zero rendering)
    // -------------------------------------------------------------------------

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void legendaryResolvesToHeroTier(GameTestHelper helper) {
        CatchCelebrationGameTests.legendaryResolvesToHeroTier(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void ordinaryCatchResolvesToNone(GameTestHelper helper) {
        CatchCelebrationGameTests.ordinaryCatchResolvesToNone(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void undiscoveredResolvesToDiscoveryTier(GameTestHelper helper) {
        CatchCelebrationGameTests.undiscoveredResolvesToDiscoveryTier(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void legendaryOutranksDiscovery(GameTestHelper helper) {
        CatchCelebrationGameTests.legendaryOutranksDiscovery(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void emptyRewardsResolveToNone(GameTestHelper helper) {
        CatchCelebrationGameTests.emptyRewardsResolveToNone(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void heroStackPicksHighestQuality(GameTestHelper helper) {
        CatchCelebrationGameTests.heroStackPicksHighestQuality(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void heroStackPicksNewSpeciesOnDiscovery(GameTestHelper helper) {
        CatchCelebrationGameTests.heroStackPicksNewSpeciesOnDiscovery(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void phaseBoundariesFollowTimings(GameTestHelper helper) {
        CatchCelebrationGameTests.phaseBoundariesFollowTimings(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void clockClampsAtTotal(GameTestHelper helper) {
        CatchCelebrationGameTests.clockClampsAtTotal(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void timeScaleFreezesThenRampsBack(GameTestHelper helper) {
        CatchCelebrationGameTests.timeScaleFreezesThenRampsBack(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void discoveryHasNoSlowMotionHold(GameTestHelper helper) {
        CatchCelebrationGameTests.discoveryHasNoSlowMotionHold(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void heroScaleGrowsToPeakThenReturnsToNormal(GameTestHelper helper) {
        CatchCelebrationGameTests.heroScaleGrowsToPeakThenReturnsToNormal(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void settleReleasesIntoPhysics(GameTestHelper helper) {
        CatchCelebrationGameTests.settleReleasesIntoPhysics(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void settleFinishesOnceItemLeavesScreen(GameTestHelper helper) {
        CatchCelebrationGameTests.settleFinishesOnceItemLeavesScreen(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void settlePhysicsDeceleratesUnderGravity(GameTestHelper helper) {
        CatchCelebrationGameTests.settlePhysicsDeceleratesUnderGravity(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void settleRotationComesFromPhysics(GameTestHelper helper) {
        CatchCelebrationGameTests.settleRotationComesFromPhysics(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void silhouetteDropsAtReveal(GameTestHelper helper) {
        CatchCelebrationGameTests.silhouetteDropsAtReveal(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void revealSwapHappensWhileEdgeOn(GameTestHelper helper) {
        CatchCelebrationGameTests.revealSwapHappensWhileEdgeOn(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void heroIsUnsqueezedOutsideTheTurn(GameTestHelper helper) {
        CatchCelebrationGameTests.heroIsUnsqueezedOutsideTheTurn(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void sparkleBurstFiresOnceAtReveal(GameTestHelper helper) {
        CatchCelebrationGameTests.sparkleBurstFiresOnceAtReveal(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void skipJumpsToSettle(GameTestHelper helper) {
        CatchCelebrationGameTests.skipJumpsToSettle(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void skipIgnoredBeforeReveal(GameTestHelper helper) {
        CatchCelebrationGameTests.skipIgnoredBeforeReveal(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void skipAfterRevealKeepsBurstConsumed(GameTestHelper helper) {
        CatchCelebrationGameTests.skipAfterRevealKeepsBurstConsumed(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void skipDoesNotRewind(GameTestHelper helper) {
        CatchCelebrationGameTests.skipDoesNotRewind(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void gameplaySuppressedUntilFinished(GameTestHelper helper) {
        CatchCelebrationGameTests.gameplaySuppressedUntilFinished(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void shakeDecaysThenRebuildsIntoTheReveal(GameTestHelper helper) {
        CatchCelebrationGameTests.shakeDecaysThenRebuildsIntoTheReveal(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void celebrationDiscoveryPolishIsSofterThanHero(GameTestHelper helper) {
        CatchCelebrationGameTests.discoveryPolishIsSofterThanHero(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void celebrationReducedEffectsStripFlashShakeDimAndMotion(GameTestHelper helper) {
        CatchCelebrationGameTests.reducedEffectsStripFlashShakeDimAndMotion(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void celebrationHeroDimBuildsThenReleasesAtReveal(GameTestHelper helper) {
        CatchCelebrationGameTests.heroDimBuildsThenReleasesAtReveal(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void celebrationHeroPunchOvershootsThenSettles(GameTestHelper helper) {
        CatchCelebrationGameTests.heroPunchOvershootsThenSettles(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void celebrationHeroRaysAppearOnRevealAndFadeOut(GameTestHelper helper) {
        CatchCelebrationGameTests.heroRaysAppearOnRevealAndFadeOut(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void celebrationHeroBannerPopsInWithOvershootThenLeaves(GameTestHelper helper) {
        CatchCelebrationGameTests.heroBannerPopsInWithOvershootThenLeaves(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void celebrationHoldsLastSeveralSeconds(GameTestHelper helper) {
        CatchCelebrationGameTests.holdsLastSeveralSeconds(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void celebrationSkipWorksThroughoutTheHold(GameTestHelper helper) {
        CatchCelebrationGameTests.skipWorksThroughoutTheHold(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void celebrationNoneTierIsRejected(GameTestHelper helper) {
        CatchCelebrationGameTests.noneTierIsRejected(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void applyQualityAndSizeSetsBothComponents(GameTestHelper helper) {
        CatchCelebrationGameTests.applyQualityAndSizeSetsBothComponents(helper);
    }

    @GameTest(structure = "fishtastic:empty_testarea")
    public static void applyQualityAndSizeOverwritesExistingQuality(GameTestHelper helper) {
        CatchCelebrationGameTests.applyQualityAndSizeOverwritesExistingQuality(helper);
    }
}
