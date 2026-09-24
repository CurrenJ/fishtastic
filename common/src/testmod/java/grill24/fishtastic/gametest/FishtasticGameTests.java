package grill24.fishtastic.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The shared gametest harness: every Fishtastic game test, annotated with <b>vanilla</b>
 * {@code net.minecraft.gametest.framework.GameTest} so one class serves both loaders
 * (decision D10, docs/backport-pass2/track-a-1.21.1.md A6.1). Each method delegates to a
 * platform-agnostic body in a sibling class; those bodies touch no loader API.
 *
 * <p>Registered on Fabric by the {@code fabric-gametest} entrypoint (Fabric derives the mod id
 * from the entrypoint and uses {@code template} verbatim - see its {@code TestFunctionsMixin}),
 * and on NeoForge by {@code RegisterGameTestsEvent.register(FishtasticGameTests.class)}.
 *
 * <p>{@code @GameTestHolder} and {@code @PrefixGameTestTemplate(false)} are NeoForge's, and are
 * required <em>on this class</em>: the holder supplies the template namespace its enabled-namespaces
 * filter matches on, and without the prefix annotation NeoForge prepends the class's simple name to
 * the template. They are compiled against PORT-ONLY stubs that are on the testmod compile classpath
 * only; at runtime NeoForge's own annotation classes are the ones loaded, and on Fabric the
 * annotation types are simply absent and ignored. See "A6.1 as built" in the track doc.
 *
 * <p>The template is a <em>bare path</em> because the two loaders read it differently: NeoForge
 * builds the id as {@code getTemplateNamespace(method) + ":" + template}, so {@code fishtastic_empty}
 * becomes {@code fishtastic:fishtastic_empty}, while Fabric parses the template verbatim, so the same
 * bare path becomes {@code minecraft:fishtastic_empty}. The all-air 8x8x8 is therefore shipped twice,
 * at {@code data/fishtastic/structure/fishtastic_empty.nbt} and
 * {@code data/minecraft/structure/fishtastic_empty.nbt}. Both live in src/testmod/resources, which
 * only the testmod source sets see, so neither ships, and the mod-prefixed name cannot collide with
 * a vanilla or datapack id. Tests build their own environment inside the bounds with
 * {@code GameTestHelper.setBlock()}.
 *
 * <p>Tests that need a player take a supplier and are handed
 * {@link FishtasticTestSupport#playerSupplier}: the two loaders cannot share vanilla's
 * {@code makeMockServerPlayerInLevel}, because NeoForge's mock connection skips the
 * configuration handshake that registers its payload channels.
 */
@GameTestHolder("fishtastic")
@PrefixGameTestTemplate(false)
public class FishtasticGameTests {

    // -------------------------------------------------------------------------
    // Marine Compost tests  (max 6100 ticks — conversion takes up to 6000)
    // -------------------------------------------------------------------------

    @GameTest(template = "fishtastic_empty")
    public void aerationCapAtFive(GameTestHelper helper) {
        MarineCompostGameTests.aerationCapAtFive(helper);
    }

    @GameTest(template = "fishtastic_empty", timeoutTicks = 6100)
    public void conversionTakesBaseTicks(GameTestHelper helper) {
        MarineCompostGameTests.conversionTakesBaseTicks(helper);
    }

    @GameTest(template = "fishtastic_empty", timeoutTicks = 5000)
    public void aerationReducesConversionTime(GameTestHelper helper) {
        MarineCompostGameTests.aerationReducesConversionTime(helper);
    }

    @GameTest(template = "fishtastic_empty", timeoutTicks = 6100)
    public void commonFishYield(GameTestHelper helper) {
        MarineCompostGameTests.commonFishYield(helper);
    }

    @GameTest(template = "fishtastic_empty", timeoutTicks = 6100)
    public void rareFishYield(GameTestHelper helper) {
        MarineCompostGameTests.rareFishYield(helper);
    }

    @GameTest(template = "fishtastic_empty", timeoutTicks = 6100)
    public void legendaryFishYield(GameTestHelper helper) {
        MarineCompostGameTests.legendaryFishYield(helper);
    }

    @GameTest(template = "fishtastic_empty", timeoutTicks = 5000)
    public void aerationAddsToYield(GameTestHelper helper) {
        MarineCompostGameTests.aerationAddsToYield(helper);
    }

    @GameTest(template = "fishtastic_empty", timeoutTicks = 6100)
    public void noQualityDefaultsToCommonYield(GameTestHelper helper) {
        MarineCompostGameTests.noQualityDefaultsToCommonYield(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void aerationCooldownBlocksTooEarlyAttempt(GameTestHelper helper) {
        MarineCompostGameTests.aerationCooldownBlocksTooEarlyAttempt(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void aerationCooldownAllowsAfterCooldown(GameTestHelper helper) {
        MarineCompostGameTests.aerationCooldownAllowsAfterCooldown(helper);
    }

    // -------------------------------------------------------------------------
    // Fish Catch Data tests  (pure in-memory, no world state needed)
    // -------------------------------------------------------------------------

    @GameTest(template = "fishtastic_empty")
    public void recordingNonFishIsIgnored(GameTestHelper helper) {
        FishCatchDataGameTests.recordingNonFishIsIgnored(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void recordingZeroSizeIsIgnored(GameTestHelper helper) {
        FishCatchDataGameTests.recordingZeroSizeIsIgnored(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void catchCountIncrements(GameTestHelper helper) {
        FishCatchDataGameTests.catchCountIncrements(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void bestSizeOnlyUpdatesOnImprovement(GameTestHelper helper) {
        FishCatchDataGameTests.bestSizeOnlyUpdatesOnImprovement(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void smallerCatchDoesNotOverrideBest(GameTestHelper helper) {
        FishCatchDataGameTests.smallerCatchDoesNotOverrideBest(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void personalLeaderboardsAreIsolatedByUuid(GameTestHelper helper) {
        FishCatchDataGameTests.personalLeaderboardsAreIsolatedByUuid(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void globalBestSizeTracksHighestAcrossPlayers(GameTestHelper helper) {
        FishCatchDataGameTests.globalBestSizeTracksHighestAcrossPlayers(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void globalCatchCountSumsAllFishTypes(GameTestHelper helper) {
        FishCatchDataGameTests.globalCatchCountSumsAllFishTypes(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void personalBestSizeSortOrder(GameTestHelper helper) {
        FishCatchDataGameTests.personalBestSizeSortOrder(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void unknownPlayerReturnsEmpty(GameTestHelper helper) {
        FishCatchDataGameTests.unknownPlayerReturnsEmpty(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void globalBestSizeTracksIndependentlyPerFishType(GameTestHelper helper) {
        FishCatchDataGameTests.globalBestSizeTracksIndependentlyPerFishType(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void globalBestSizeTieKeepsSingleEntry(GameTestHelper helper) {
        FishCatchDataGameTests.globalBestSizeTieKeepsSingleEntry(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void globalCatchCountRanksMultiplePlayers(GameTestHelper helper) {
        FishCatchDataGameTests.globalCatchCountRanksMultiplePlayers(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void personalCatchCountSortOrder(GameTestHelper helper) {
        FishCatchDataGameTests.personalCatchCountSortOrder(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void globalBestSizeSortOrder(GameTestHelper helper) {
        FishCatchDataGameTests.globalBestSizeSortOrder(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void recordTrashContributionAccumulatesTotal(GameTestHelper helper) {
        FishCatchDataGameTests.recordTrashContributionAccumulatesTotal(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void recordTrashContributionCanCrossMultipleThresholdsAtOnce(GameTestHelper helper) {
        FishCatchDataGameTests.recordTrashContributionCanCrossMultipleThresholdsAtOnce(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void recordTrashContributionIgnoresNonPositiveAmounts(GameTestHelper helper) {
        FishCatchDataGameTests.recordTrashContributionIgnoresNonPositiveAmounts(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void crossingThresholdPaysOutTokensProportionally(GameTestHelper helper) {
        FishCatchDataGameTests.crossingThresholdPaysOutTokensProportionally(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    // -------------------------------------------------------------------------
    // Fish Catch Backup tests  (real file I/O in the test world's data dir)
    // -------------------------------------------------------------------------

    @GameTest(template = "fishtastic_empty")
    public void manualBackupRoundTrips(GameTestHelper helper) throws Exception {
        FishCatchBackupsGameTests.manualBackupRoundTrips(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void labelIsSanitisedIntoFileName(GameTestHelper helper) throws Exception {
        FishCatchBackupsGameTests.labelIsSanitisedIntoFileName(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void findRejectsTraversalAndUnknownNames(GameTestHelper helper) throws Exception {
        FishCatchBackupsGameTests.findRejectsTraversalAndUnknownNames(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void listIsNewestFirstAndIgnoresJunk(GameTestHelper helper) throws Exception {
        FishCatchBackupsGameTests.listIsNewestFirstAndIgnoresJunk(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void loadRejectsFileWithoutDataCompound(GameTestHelper helper) throws Exception {
        FishCatchBackupsGameTests.loadRejectsFileWithoutDataCompound(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void intervalBackupSkipsIdenticalContent(GameTestHelper helper) throws Exception {
        FishCatchBackupsGameTests.intervalBackupSkipsIdenticalContent(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void restorePlayerOnlyTouchesThatPlayer(GameTestHelper helper) throws Exception {
        FishCatchBackupsGameTests.restorePlayerOnlyTouchesThatPlayer(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void restorePlayerAbsentFromBackupRemovesThem(GameTestHelper helper) throws Exception {
        FishCatchBackupsGameTests.restorePlayerAbsentFromBackupRemovesThem(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void restorePlayerRevertsCleanupContribution(GameTestHelper helper) throws Exception {
        FishCatchBackupsGameTests.restorePlayerRevertsCleanupContribution(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void restoreAllReplacesEverything(GameTestHelper helper) throws Exception {
        FishCatchBackupsGameTests.restoreAllReplacesEverything(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void restoreDoesNotAliasSnapshotObjects(GameTestHelper helper) throws Exception {
        FishCatchBackupsGameTests.restoreDoesNotAliasSnapshotObjects(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void summaryAndLookupApi(GameTestHelper helper) throws Exception {
        FishCatchBackupsGameTests.summaryAndLookupApi(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void preCommandHookWritesLabelledBackup(GameTestHelper helper) throws Exception {
        FishCatchBackupsGameTests.preCommandHookWritesLabelledBackup(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void preCommandPoolIsCapped(GameTestHelper helper) throws Exception {
        FishCatchBackupsGameTests.preCommandPoolIsCapped(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void pruneDeletesAncientIntervalFilesOnDisk(GameTestHelper helper) throws Exception {
        FishCatchBackupsGameTests.pruneDeletesAncientIntervalFilesOnDisk(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void schedulerWritesStartThenInterval(GameTestHelper helper) throws Exception {
        FishCatchBackupsGameTests.schedulerWritesStartThenInterval(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void serverConfigWritesDefaultsAndReloads(GameTestHelper helper) throws Exception {
        FishCatchBackupsGameTests.serverConfigWritesDefaultsAndReloads(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void cleanupGoalOnlyResetsOnCompletionNotOverTime(GameTestHelper helper) {
        FishCatchDataGameTests.cleanupGoalOnlyResetsOnCompletionNotOverTime(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void getCleanupGoalContributorsListsAllContributors(GameTestHelper helper) {
        FishCatchDataGameTests.getCleanupGoalContributorsListsAllContributors(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    // -------------------------------------------------------------------------
    // Item Component tests  (pure in-memory)
    // -------------------------------------------------------------------------

    @GameTest(template = "fishtastic_empty")
    public void itemSizeSetAndGet(GameTestHelper helper) {
        ItemComponentGameTests.itemSizeSetAndGet(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void itemSizeRemove(GameTestHelper helper) {
        ItemComponentGameTests.itemSizeRemove(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void itemSizeWithSizeCopies(GameTestHelper helper) {
        ItemComponentGameTests.itemSizeWithSizeCopies(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void itemSizeIgnoresEmptyStack(GameTestHelper helper) {
        ItemComponentGameTests.itemSizeIgnoresEmptyStack(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void itemSizeComponentKey(GameTestHelper helper) {
        ItemComponentGameTests.itemSizeComponentKey(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void fishQualityAllTiers(GameTestHelper helper) {
        ItemComponentGameTests.fishQualityAllTiers(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void fishQualityShouldRenderEffect(GameTestHelper helper) {
        ItemComponentGameTests.fishQualityShouldRenderEffect(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void fishQualityEffectIntensity(GameTestHelper helper) {
        ItemComponentGameTests.fishQualityEffectIntensity(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void fishQualityNullWhenAbsent(GameTestHelper helper) {
        ItemComponentGameTests.fishQualityNullWhenAbsent(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void baitEffectWormsPreset(GameTestHelper helper) {
        ItemComponentGameTests.baitEffectWormsPreset(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void baitEffectBlazedGrubExclusivePool(GameTestHelper helper) {
        ItemComponentGameTests.baitEffectBlazedGrubExclusivePool(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void baitEffectComponentRoundTrip(GameTestHelper helper) {
        ItemComponentGameTests.baitEffectComponentRoundTrip(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void baitEffectNoBaitDefaults(GameTestHelper helper) {
        ItemComponentGameTests.baitEffectNoBaitDefaults(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void baitEffectTrashChancePresets(GameTestHelper helper) {
        ItemComponentGameTests.baitEffectTrashChancePresets(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void baitEffectTrashChanceComponentRoundTrip(GameTestHelper helper) {
        ItemComponentGameTests.baitEffectTrashChanceComponentRoundTrip(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void rodBaitContentsEmptyIsEmpty(GameTestHelper helper) {
        ItemComponentGameTests.rodBaitContentsEmptyIsEmpty(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void rodBaitContentsNonEmptyStackIsNotEmpty(GameTestHelper helper) {
        ItemComponentGameTests.rodBaitContentsNonEmptyStackIsNotEmpty(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void rodBaitContentsCopyStackIsDistinct(GameTestHelper helper) {
        ItemComponentGameTests.rodBaitContentsCopyStackIsDistinct(helper);
    }

    // -------------------------------------------------------------------------
    // MathUtil / Utility tests  (pure logic, zero Minecraft world dependency)
    // -------------------------------------------------------------------------

    @GameTest(template = "fishtastic_empty")
    public void lerpFloatBoundaries(GameTestHelper helper) {
        MathUtilGameTests.lerpFloatBoundaries(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void lerpDoubleBoundaries(GameTestHelper helper) {
        MathUtilGameTests.lerpDoubleBoundaries(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void clampFloatBounds(GameTestHelper helper) {
        MathUtilGameTests.clampFloatBounds(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void clampDoubleBounds(GameTestHelper helper) {
        MathUtilGameTests.clampDoubleBounds(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void clampIntBounds(GameTestHelper helper) {
        MathUtilGameTests.clampIntBounds(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void easeInOutQuadShape(GameTestHelper helper) {
        MathUtilGameTests.easeInOutQuadShape(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void easeOutCubicShape(GameTestHelper helper) {
        MathUtilGameTests.easeOutCubicShape(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void easedLerpIdentityMatchesPlainLerp(GameTestHelper helper) {
        MathUtilGameTests.easedLerpIdentityMatchesPlainLerp(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void easedLerpAppliesEasingFunction(GameTestHelper helper) {
        MathUtilGameTests.easedLerpAppliesEasingFunction(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void utilityFtCreatesNamespacedIdentifier(GameTestHelper helper) {
        MathUtilGameTests.utilityFtCreatesNamespacedIdentifier(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void utilityInterpolateColorBoundaries(GameTestHelper helper) {
        MathUtilGameTests.utilityInterpolateColorBoundaries(helper);
    }

    // -------------------------------------------------------------------------
    // FishingTarget tests  (pure logic, zero Minecraft world dependency)
    // -------------------------------------------------------------------------

    @GameTest(template = "fishtastic_empty")
    public void highOverlapEventuallyCatches(GameTestHelper helper) {
        FishingTargetGameTests.highOverlapEventuallyCatches(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void zeroOverlapEventuallyFails(GameTestHelper helper) {
        FishingTargetGameTests.zeroOverlapEventuallyFails(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void pickRandomBoundaryRolls(GameTestHelper helper) {
        FishingTargetGameTests.pickRandomBoundaryRolls(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void allMovementPatternsTickWithoutThrowing(GameTestHelper helper) {
        FishingTargetGameTests.allMovementPatternsTickWithoutThrowing(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void collectionAnimationLifecycle(GameTestHelper helper) {
        FishingTargetGameTests.collectionAnimationLifecycle(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void failAnimationLifecycle(GameTestHelper helper) {
        FishingTargetGameTests.failAnimationLifecycle(helper);
    }

    // -------------------------------------------------------------------------
    // PlayerQuestState tests  (pure in-memory, no world state needed)
    // -------------------------------------------------------------------------

    @GameTest(template = "fishtastic_empty")
    public void getProgressDefaultsForUntouchedQuest(GameTestHelper helper) {
        PlayerQuestStateGameTests.getProgressDefaultsForUntouchedQuest(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void incrementCountRaisesCountAndFlipsCompleted(GameTestHelper helper) {
        PlayerQuestStateGameTests.incrementCountRaisesCountAndFlipsCompleted(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void canClaimTrueOnlyBetweenCompletionAndClaim(GameTestHelper helper) {
        PlayerQuestStateGameTests.canClaimTrueOnlyBetweenCompletionAndClaim(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void claimAddsTokensWithoutResettingCount(GameTestHelper helper) {
        PlayerQuestStateGameTests.claimAddsTokensWithoutResettingCount(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void resetDailyIfNeededOnlyOnNewDay(GameTestHelper helper) {
        PlayerQuestStateGameTests.resetDailyIfNeededOnlyOnNewDay(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void purchaseFailsWhenBalanceTooLow(GameTestHelper helper) {
        PlayerQuestStateGameTests.purchaseFailsWhenBalanceTooLow(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void purchaseFailsWhenMaxPurchasesReached(GameTestHelper helper) {
        PlayerQuestStateGameTests.purchaseFailsWhenMaxPurchasesReached(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void purchaseZeroMaxPurchasesIsUnlimited(GameTestHelper helper) {
        PlayerQuestStateGameTests.purchaseZeroMaxPurchasesIsUnlimited(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void purchaseSucceedsDeductsAndIncrementsCount(GameTestHelper helper) {
        PlayerQuestStateGameTests.purchaseSucceedsDeductsAndIncrementsCount(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void resetDailyPurchasesIfNeededOnlyOnNewDay(GameTestHelper helper) {
        PlayerQuestStateGameTests.resetDailyPurchasesIfNeededOnlyOnNewDay(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void snapshotsReflectMutations(GameTestHelper helper) {
        PlayerQuestStateGameTests.snapshotsReflectMutations(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void getLifetimeTankPlacementsDefaultsToZero(GameTestHelper helper) {
        PlayerQuestStateGameTests.getLifetimeTankPlacementsDefaultsToZero(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void incrementLifetimeTankPlacementsIncrementsByOnePerCall(GameTestHelper helper) {
        PlayerQuestStateGameTests.incrementLifetimeTankPlacementsIncrementsByOnePerCall(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void lifetimeTankPlacementsPersistThroughCodecRoundTrip(GameTestHelper helper) {
        PlayerQuestStateGameTests.lifetimeTankPlacementsPersistThroughCodecRoundTrip(helper);
    }

    // -------------------------------------------------------------------------
    // ItemEffect condition tests  (pure ItemStack + registry logic, no datapack needed)
    // -------------------------------------------------------------------------

    @GameTest(template = "fishtastic_empty")
    public void itemTagConditionMatchesRealTag(GameTestHelper helper) {
        ItemEffectConditionGameTests.itemTagConditionMatchesRealTag(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void componentConditionMatchesPresenceOnly(GameTestHelper helper) {
        ItemEffectConditionGameTests.componentConditionMatchesPresenceOnly(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void componentValueConditionMatchesFieldValue(GameTestHelper helper) {
        ItemEffectConditionGameTests.componentValueConditionMatchesFieldValue(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void andConditionSemantics(GameTestHelper helper) {
        ItemEffectConditionGameTests.andConditionSemantics(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void itemEffectMatchesRespectsEnabledAndConditions(GameTestHelper helper) {
        ItemEffectConditionGameTests.itemEffectMatchesRespectsEnabledAndConditions(helper);
    }

    // -------------------------------------------------------------------------
    // Fish Tank tests
    // -------------------------------------------------------------------------

    @GameTest(template = "fishtastic_empty")
    public void addItemIntoEmptyTankSucceeds(GameTestHelper helper) {
        FishTankGameTests.addItemIntoEmptyTankSucceeds(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void addItemMergesIntoExistingStackBeforeNewSlot(GameTestHelper helper) {
        FishTankGameTests.addItemMergesIntoExistingStackBeforeNewSlot(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void addItemFailsOnceSwarmCapReached(GameTestHelper helper) {
        FishTankGameTests.addItemFailsOnceSwarmCapReached(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void extractItemRemovesLastSlotLifoOrder(GameTestHelper helper) {
        FishTankGameTests.extractItemRemovesLastSlotLifoOrder(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void firstItemRotationReflectsSlotZeroInsert(GameTestHelper helper) {
        FishTankGameTests.firstItemRotationReflectsSlotZeroInsert(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void cosmeticsRoundTrip(GameTestHelper helper) {
        FishTankGameTests.cosmeticsRoundTrip(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void openFacesRoundTrip(GameTestHelper helper) {
        FishTankGameTests.openFacesRoundTrip(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void sameShapeNeighborsConnect(GameTestHelper helper) {
        FishTankGameTests.sameShapeNeighborsConnect(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void crossShapeNeighborsInSameFamilyConnect(GameTestHelper helper) {
        FishTankGameTests.crossShapeNeighborsInSameFamilyConnect(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void standardAndReinforcedNeighborsConnect(GameTestHelper helper) {
        FishTankGameTests.standardAndReinforcedNeighborsConnect(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void newShapesConnectToStandard(GameTestHelper helper) {
        FishTankGameTests.newShapesConnectToStandard(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void newShapesConnectToEachOther(GameTestHelper helper) {
        FishTankGameTests.newShapesConnectToEachOther(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void honeycombSealedFaceIsBlockedEvenWhenGroupedViaAnotherPath(GameTestHelper helper) {
        FishTankGameTests.honeycombSealedFaceIsBlockedEvenWhenGroupedViaAnotherPath(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void honeycombSealedIsolatedPairFormSeparateGroups(GameTestHelper helper) {
        FishTankGameTests.honeycombSealedIsolatedPairFormSeparateGroups(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void brokenTankDropCarriesShapeAndMaterials(GameTestHelper helper) {
        FishTankGameTests.brokenTankDropCarriesShapeAndMaterials(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void brokenTankDropRestoresShapeWhenReplaced(GameTestHelper helper) {
        FishTankGameTests.brokenTankDropRestoresShapeWhenReplaced(helper);
    }

    // -------------------------------------------------------------------------
    // TutorialManager tests
    // -------------------------------------------------------------------------

    @GameTest(template = "fishtastic_empty")
    public void craftingRodFromDefaultStepGrantsWormsAndAdvances(GameTestHelper helper) {
        TutorialManagerGameTests.craftingRodFromDefaultStepGrantsWormsAndAdvances(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void craftingRodAgainAfterAdvancingIsNoOp(GameTestHelper helper) {
        TutorialManagerGameTests.craftingRodAgainAfterAdvancingIsNoOp(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void onBaitLoadedOnlyAdvancesFromBaitLoadStep(GameTestHelper helper) {
        TutorialManagerGameTests.onBaitLoadedOnlyAdvancesFromBaitLoadStep(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void onHookCastOnlyAdvancesFromCastableSteps(GameTestHelper helper) {
        TutorialManagerGameTests.onHookCastOnlyAdvancesFromCastableSteps(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void advanceStepNoOpWhenFromStepDoesNotMatchCurrent(GameTestHelper helper) {
        TutorialManagerGameTests.advanceStepNoOpWhenFromStepDoesNotMatchCurrent(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void tutorialWalksFullDocumentedChainToCompletion(GameTestHelper helper) {
        TutorialManagerGameTests.tutorialWalksFullDocumentedChainToCompletion(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void craftingRodViaShiftClickFromResultSlotAdvancesToBaitLoad(GameTestHelper helper) {
        TutorialManagerGameTests.craftingRodViaShiftClickFromResultSlotAdvancesToBaitLoad(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void craftingRodViaSimpleClickFromResultSlotAdvancesToBaitLoad(GameTestHelper helper) {
        TutorialManagerGameTests.craftingRodViaSimpleClickFromResultSlotAdvancesToBaitLoad(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void loadingBaitViaLeftClickInInventoryScreenAdvancesToWaitingForCast(GameTestHelper helper) {
        TutorialManagerGameTests.loadingBaitViaLeftClickInInventoryScreenAdvancesToWaitingForCast(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void revokingRodAdvancementAllowsReTriggeringAfterReset(GameTestHelper helper) {
        TutorialManagerGameTests.revokingRodAdvancementAllowsReTriggeringAfterReset(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void onQuestClaimedOnlyAdvancesOnMatchingTutorialQuestId(GameTestHelper helper) {
        TutorialManagerGameTests.onQuestClaimedOnlyAdvancesOnMatchingTutorialQuestId(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    // -------------------------------------------------------------------------
    // EncyclopediaTutorialManager tests
    // -------------------------------------------------------------------------

    @GameTest(template = "fishtastic_empty")
    public void openingEncyclopediaFromNotStartedStartsIntro(GameTestHelper helper) {
        EncyclopediaTutorialManagerGameTests.openingEncyclopediaFromNotStartedStartsIntro(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void reopeningEncyclopediaWhileInProgressIsIdempotent(GameTestHelper helper) {
        EncyclopediaTutorialManagerGameTests.reopeningEncyclopediaWhileInProgressIsIdempotent(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void openingEncyclopediaAfterCompleteDoesNotRestart(GameTestHelper helper) {
        EncyclopediaTutorialManagerGameTests.openingEncyclopediaAfterCompleteDoesNotRestart(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void encyclopediaAdvanceStepNoOpWhenFromStepDoesNotMatchCurrent(GameTestHelper helper) {
        EncyclopediaTutorialManagerGameTests.advanceStepNoOpWhenFromStepDoesNotMatchCurrent(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void encyclopediaTutorialWalksFullChainToCompletion(GameTestHelper helper) {
        EncyclopediaTutorialManagerGameTests.encyclopediaTutorialWalksFullChainToCompletion(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    // -------------------------------------------------------------------------
    // QuestTracker tests  (pure matching logic + throwaway registry, no player needed)
    // -------------------------------------------------------------------------

    @GameTest(template = "fishtastic_empty")
    public void targetSpeciesGatesMatchWhenPresent(GameTestHelper helper) {
        QuestTrackerGameTests.targetSpeciesGatesMatchWhenPresent(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void targetSpeciesTagGatesMatchWhenPresent(GameTestHelper helper) {
        QuestTrackerGameTests.targetSpeciesTagGatesMatchWhenPresent(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void minQualityIsOrdinalFloor(GameTestHelper helper) {
        QuestTrackerGameTests.minQualityIsOrdinalFloor(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void biomeConditionGatesMatchWhenPresent(GameTestHelper helper) {
        QuestTrackerGameTests.biomeConditionGatesMatchWhenPresent(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void timeConditionGatesMatchWhenPresent(GameTestHelper helper) {
        QuestTrackerGameTests.timeConditionGatesMatchWhenPresent(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void weatherConditionGatesMatchWhenPresent(GameTestHelper helper) {
        QuestTrackerGameTests.weatherConditionGatesMatchWhenPresent(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void zoneConditionGatesMatchWhenPresent(GameTestHelper helper) {
        QuestTrackerGameTests.zoneConditionGatesMatchWhenPresent(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void minSizeIsFloor(GameTestHelper helper) {
        QuestTrackerGameTests.minSizeIsFloor(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void minSessionCatchesDoesNotAffectPerStackMatching(GameTestHelper helper) {
        QuestTrackerGameTests.minSessionCatchesDoesNotAffectPerStackMatching(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void allConditionsMustMatchTogether(GameTestHelper helper) {
        QuestTrackerGameTests.allConditionsMustMatchTogether(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void getActiveDailiesIsStablePerDay(GameTestHelper helper) {
        QuestTrackerGameTests.getActiveDailiesIsStablePerDay(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void getActiveDailiesNeverExceedsCapAndExcludesNonDaily(GameTestHelper helper) {
        QuestTrackerGameTests.getActiveDailiesNeverExceedsCapAndExcludesNonDaily(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void getActiveDailiesCapsAtRegistrySizeWhenSmallerThanCount(GameTestHelper helper) {
        QuestTrackerGameTests.getActiveDailiesCapsAtRegistrySizeWhenSmallerThanCount(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void tankSnapshotConditionLifetimeDefaultsToFalseWhenAbsent(GameTestHelper helper) {
        QuestTrackerGameTests.tankSnapshotConditionLifetimeDefaultsToFalseWhenAbsent(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void tankSnapshotConditionLifetimeRoundTripsThroughCodec(GameTestHelper helper) {
        QuestTrackerGameTests.tankSnapshotConditionLifetimeRoundTripsThroughCodec(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void tankKeeperSilverCompletesAndUnlocksToothShape(GameTestHelper helper) {
        QuestTrackerGameTests.tankKeeperSilverCompletesAndUnlocksToothShape(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void tankKeeperGoldCompletesAndUnlocksFilmShape(GameTestHelper helper) {
        QuestTrackerGameTests.tankKeeperGoldCompletesAndUnlocksFilmShape(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void lifetimeTankPlacementCounterIsCumulativeAcrossDifferentTanks(GameTestHelper helper) {
        QuestTrackerGameTests.lifetimeTankPlacementCounterIsCumulativeAcrossDifferentTanks(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void lifetimeTankPlacementCounterIgnoresNonFishItems(GameTestHelper helper) {
        QuestTrackerGameTests.lifetimeTankPlacementCounterIgnoresNonFishItems(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void tankStarterCompletesWhenAnyFishIsDisplayedInATank(GameTestHelper helper) {
        QuestTrackerGameTests.tankStarterCompletesWhenAnyFishIsDisplayedInATank(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void goldenShowcaseRequiresLegendaryQualityAndGoldFrameTogether(GameTestHelper helper) {
        QuestTrackerGameTests.goldenShowcaseRequiresLegendaryQualityAndGoldFrameTogether(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void blueToTheGillsCountsMatchingSpeciesLiveAndCannotRegressOnceComplete(GameTestHelper helper) {
        QuestTrackerGameTests.blueToTheGillsCountsMatchingSpeciesLiveAndCannotRegressOnceComplete(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    // -------------------------------------------------------------------------
    // Quest content validation  (runs against the live quest/fish_profile/biome registries)
    // -------------------------------------------------------------------------

    @GameTest(template = "fishtastic_empty")
    public void everyTargetSpeciesQuestIsSatisfiable(GameTestHelper helper) {
        QuestSatisfiabilityGameTests.everyTargetSpeciesQuestIsSatisfiable(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void everyPrerequisiteResolves(GameTestHelper helper) {
        QuestSatisfiabilityGameTests.everyPrerequisiteResolves(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void noPrerequisiteCycles(GameTestHelper helper) {
        QuestSatisfiabilityGameTests.noPrerequisiteCycles(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void nonHiddenQuestIsAlwaysListed(GameTestHelper helper) {
        QuestLogVisibilityGameTests.nonHiddenQuestIsAlwaysListed(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void hiddenSecretStaysOutUntilCompleted(GameTestHelper helper) {
        QuestLogVisibilityGameTests.hiddenSecretStaysOutUntilCompleted(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void hiddenChainQuestAppearsOncePrerequisiteClaimed(GameTestHelper helper) {
        QuestLogVisibilityGameTests.hiddenChainQuestAppearsOncePrerequisiteClaimed(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void completedChainQuestIsListedEvenIfPrerequisiteUnclaimed(GameTestHelper helper) {
        QuestLogVisibilityGameTests.completedChainQuestIsListedEvenIfPrerequisiteUnclaimed(helper);
    }


    @GameTest(template = "fishtastic_empty")
    public void gatedEntriesAreAbsentUntilTheirQuestIsClaimed(GameTestHelper helper) {
        CapstoneRewardGameTests.gatedEntriesAreAbsentUntilTheirQuestIsClaimed(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void gatedEntriesCanAppearOnceTheirQuestIsClaimed(GameTestHelper helper) {
        CapstoneRewardGameTests.gatedEntriesCanAppearOnceTheirQuestIsClaimed(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void lockedEntriesDoNotConsumeShopSlots(GameTestHelper helper) {
        CapstoneRewardGameTests.lockedEntriesDoNotConsumeShopSlots(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void gatedEntryIsEarnableViaEveryUnlockQuest(GameTestHelper helper) {
        CapstoneRewardGameTests.gatedEntryIsEarnableViaEveryUnlockQuest(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void unlockGatesNeverPointAtDailyQuests(GameTestHelper helper) {
        CapstoneRewardGameTests.unlockGatesNeverPointAtDailyQuests(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void capstoneTanksCarryTheirMaterialsComponent(GameTestHelper helper) {
        CapstoneRewardGameTests.capstoneTanksCarryTheirMaterialsComponent(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void displayCountClampsOvershootToTheTarget(GameTestHelper helper) {
        PlayerQuestStateGameTests.displayCountClampsOvershootToTheTarget(helper);
    }


    @GameTest(template = "fishtastic_empty")
    public void stormCharmIsSlottableIntoTheRod(GameTestHelper helper) {
        StormCharmGameTests.stormCharmIsSlottableIntoTheRod(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void stormCharmCarriesNoCharmEffect(GameTestHelper helper) {
        StormCharmGameTests.stormCharmCarriesNoCharmEffect(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void stormCharmStacksUnlikeRodCharms(GameTestHelper helper) {
        StormCharmGameTests.stormCharmStacksUnlikeRodCharms(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void summonedStormIsReadAsThunderByQuestConditions(GameTestHelper helper) {
        StormCharmGameTests.summonedStormIsReadAsThunderByQuestConditions(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void handUseChargesUpAndIsFreeToCancel(GameTestHelper helper) {
        StormCharmGameTests.handUseChargesUpAndIsFreeToCancel(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void stormDurationIsWithinVanillaThunderRange(GameTestHelper helper) {
        StormCharmGameTests.stormDurationIsWithinVanillaThunderRange(helper);
    }

    // The Sunset Postcard's day-time rate, whose gametest A2.8.c deferred to this harness. It needs
    // its own timeout because it measures day time across 200 server ticks.
    @GameTest(template = "fishtastic_empty", timeoutTicks = 600)
    public void dayTimeAdvancesAtTheAppliedRate(GameTestHelper helper) {
        StormCharmGameTests.dayTimeAdvancesAtTheAppliedRate(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void dailyPoolIsLargerThanTheDrawAndActuallyRotates(GameTestHelper helper) {
        QuestSatisfiabilityGameTests.dailyPoolIsLargerThanTheDrawAndActuallyRotates(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void lifetimeQuestsCarryNoUnreplayableConditions(GameTestHelper helper) {
        QuestSatisfiabilityGameTests.lifetimeQuestsCarryNoUnreplayableConditions(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void catchCountMatchingScopesToTheRequestedSpecies(GameTestHelper helper) {
        LifetimeQuestProgressGameTests.catchCountMatchingScopesToTheRequestedSpecies(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void lifetimeCountsAreIsolatedPerPlayer(GameTestHelper helper) {
        LifetimeQuestProgressGameTests.lifetimeCountsAreIsolatedPerPlayer(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void oneLifetimeTotalSatisfiesEveryTierItHasPassed(GameTestHelper helper) {
        LifetimeQuestProgressGameTests.oneLifetimeTotalSatisfiesEveryTierItHasPassed(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void nonFishCatchesDoNotAdvanceLifetimeChains(GameTestHelper helper) {
        LifetimeQuestProgressGameTests.nonFishCatchesDoNotAdvanceLifetimeChains(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void lifetimeCompatibilityRejectsEnvironmentalConditions(GameTestHelper helper) {
        LifetimeQuestProgressGameTests.lifetimeCompatibilityRejectsEnvironmentalConditions(helper);
    }

    // -------------------------------------------------------------------------
    // Packet round-trip tests  (pure StreamCodec encode/decode, no player needed)
    // -------------------------------------------------------------------------

    @GameTest(template = "fishtastic_empty")
    public void startFishingMinigamePacketRoundTrips(GameTestHelper helper) {
        PacketRoundTripGameTests.startFishingMinigamePacketRoundTrips(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void purchaseShopEntryPacketRoundTrips(GameTestHelper helper) {
        PacketRoundTripGameTests.purchaseShopEntryPacketRoundTrips(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void questSyncPacketRoundTrips(GameTestHelper helper) {
        PacketRoundTripGameTests.questSyncPacketRoundTrips(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void questSyncPacketCleanupGoalMilestoneRoundTrips(GameTestHelper helper) {
        PacketRoundTripGameTests.questSyncPacketCleanupGoalMilestoneRoundTrips(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void fishEncyclopediaSyncPacketRoundTrips(GameTestHelper helper) {
        PacketRoundTripGameTests.fishEncyclopediaSyncPacketRoundTrips(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void requestFishEncyclopediaPacketRoundTrips(GameTestHelper helper) {
        PacketRoundTripGameTests.requestFishEncyclopediaPacketRoundTrips(helper);
    }

    // -------------------------------------------------------------------------
    // FishEncyclopediaEntry tests  (pure Codec, no world state needed)
    // -------------------------------------------------------------------------

    @GameTest(template = "fishtastic_empty")
    public void emptyObjectDecodesToAllDefaults(GameTestHelper helper) {
        FishEncyclopediaEntryGameTests.emptyObjectDecodesToAllDefaults(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void partialThresholdsFillRemainingDefaults(GameTestHelper helper) {
        FishEncyclopediaEntryGameTests.partialThresholdsFillRemainingDefaults(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void fullEntryRoundTripsThroughJson(GameTestHelper helper) {
        FishEncyclopediaEntryGameTests.fullEntryRoundTripsThroughJson(helper);
    }

    // -------------------------------------------------------------------------
    // FishEncyclopediaClientCache / FishEncyclopediaClientHelper tests
    // -------------------------------------------------------------------------

    @GameTest(template = "fishtastic_empty")
    public void cacheStartsEmpty(GameTestHelper helper) {
        FishEncyclopediaClientGameTests.cacheStartsEmpty(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void updatePopulatesCatchCountsByFishType(GameTestHelper helper) {
        FishEncyclopediaClientGameTests.updatePopulatesCatchCountsByFishType(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void updateIndexesBestSizesByFishType(GameTestHelper helper) {
        FishEncyclopediaClientGameTests.updateIndexesBestSizesByFishType(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void updateReplacesPriorContentsRatherThanMerging(GameTestHelper helper) {
        FishEncyclopediaClientGameTests.updateReplacesPriorContentsRatherThanMerging(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void resetClearsAllMaps(GameTestHelper helper) {
        FishEncyclopediaClientGameTests.resetClearsAllMaps(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void getEncyclopediaEntryFallsBackToDefaultForUnregisteredFish(GameTestHelper helper) {
        FishEncyclopediaClientGameTests.getEncyclopediaEntryFallsBackToDefaultForUnregisteredFish(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void getAllFishProfilesSortedMatchesRegistryEntrySet(GameTestHelper helper) {
        FishEncyclopediaClientGameTests.getAllFishProfilesSortedMatchesRegistryEntrySet(helper);
    }

    // -------------------------------------------------------------------------
    // FishingMinigameManager tests
    // -------------------------------------------------------------------------

    @GameTest(template = "fishtastic_empty")
    public void startSessionEndToEndReturnsValidSessionId(GameTestHelper helper) {
        FishingMinigameManagerGameTests.startSessionEndToEndReturnsValidSessionId(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void startSessionWhenAlreadyActiveReturnsNegativeOneUnlessCancelled(GameTestHelper helper) {
        FishingMinigameManagerGameTests.startSessionWhenAlreadyActiveReturnsNegativeOneUnlessCancelled(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void cancelSessionRemovesActiveSession(GameTestHelper helper) {
        FishingMinigameManagerGameTests.cancelSessionRemovesActiveSession(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void handleMinigameCompleteAwardsOnlyRewardsForValidIndicesAndIgnoresOthers(GameTestHelper helper) {
        FishingMinigameManagerGameTests.handleMinigameCompleteAwardsOnlyRewardsForValidIndicesAndIgnoresOthers(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void handleMinigameCompleteIsNoOpForUnknownOrMismatchedSession(GameTestHelper helper) {
        FishingMinigameManagerGameTests.handleMinigameCompleteIsNoOpForUnknownOrMismatchedSession(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void handleMinigameCompleteSessionIsSingleUseEvenWhenIndicesAreInvalid(GameTestHelper helper) {
        FishingMinigameManagerGameTests.handleMinigameCompleteSessionIsSingleUseEvenWhenIndicesAreInvalid(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void handleMinigameCompleteGrantsRewardsEvenWhenCompletedInUnderTwentyTicks(GameTestHelper helper) {
        FishingMinigameManagerGameTests.handleMinigameCompleteGrantsRewardsEvenWhenCompletedInUnderTwentyTicks(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void handleMinigameCompleteConsumesBaitOnlyWhenRewardsWereActuallyAwarded(GameTestHelper helper) {
        FishingMinigameManagerGameTests.handleMinigameCompleteConsumesBaitOnlyWhenRewardsWereActuallyAwarded(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void trashChanceOneAlwaysAwardsTrashItems(GameTestHelper helper) {
        FishingMinigameManagerGameTests.trashChanceOneAlwaysAwardsTrashItems(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void handleMinigameCompleteDropsRewardAtPlayerFeetWhenInventoryIsFull(GameTestHelper helper) {
        FishingMinigameManagerGameTests.handleMinigameCompleteDropsRewardAtPlayerFeetWhenInventoryIsFull(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void handleMinigameCompleteDropsRewardWhenInventoryIsFullAndAutoPileFishIsActive(GameTestHelper helper) {
        FishingMinigameManagerGameTests.handleMinigameCompleteDropsRewardWhenInventoryIsFullAndAutoPileFishIsActive(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    // -------------------------------------------------------------------------
    // ShopEntry tests  (pure registry-only logic, no player needed)
    // -------------------------------------------------------------------------

    @GameTest(template = "fishtastic_empty")
    public void getActiveDailyShopIsStablePerDay(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopIsStablePerDay(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void getActiveDailyShopNeverExceedsCap(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopNeverExceedsCap(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void getActiveDailyShopCapsAtRegistrySizeWhenSmallerThanCount(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopCapsAtRegistrySizeWhenSmallerThanCount(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void getActiveDailyShopWeightBiasesSelectionTowardHeavierEntries(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopWeightBiasesSelectionTowardHeavierEntries(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void getActiveDailyShopHandlesNonPositiveWeightWithoutError(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopHandlesNonPositiveWeightWithoutError(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void shopEntryCodecDefaultsWeightToOneWhenAbsent(GameTestHelper helper) {
        ShopEntryGameTests.shopEntryCodecDefaultsWeightToOneWhenAbsent(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void getActiveDailyShopCharmReplacementRateMatchesConfiguredChance(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopCharmReplacementRateMatchesConfiguredChance(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void getActiveDailyShopNeverReplacesWithoutACharmPool(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopNeverReplacesWithoutACharmPool(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void getActiveDailyShopHandlesEmptyMainPoolWithCharmsOnly(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopHandlesEmptyMainPoolWithCharmsOnly(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void getActiveDailyShopTankShapeReplacementRateMatchesConfiguredChance(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopTankShapeReplacementRateMatchesConfiguredChance(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void getActiveDailyShopNeverReplacesWithoutATankShapePool(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopNeverReplacesWithoutATankShapePool(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void getActiveDailyShopHandlesEmptyMainPoolWithTankShapesOnly(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopHandlesEmptyMainPoolWithTankShapesOnly(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void getActiveDailyShopCharmAndTankShapeReplacementsCanCoexist(GameTestHelper helper) {
        ShopEntryGameTests.getActiveDailyShopCharmAndTankShapeReplacementsCanCoexist(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void grantRewardsDropsLeftoverWhenInventoryIsFull(GameTestHelper helper) {
        ShopEntryGameTests.grantRewardsDropsLeftoverWhenInventoryIsFull(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void grantRewardsDeliversNormallyWhenInventoryHasSpace(GameTestHelper helper) {
        ShopEntryGameTests.grantRewardsDeliversNormallyWhenInventoryHasSpace(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    // -------------------------------------------------------------------------
    // RemoveTankEntryPacket.giveOrDrop — fish tank browser GUI removal delivery
    // -------------------------------------------------------------------------

    @GameTest(template = "fishtastic_empty")
    public void giveOrDropDropsFishWhenInventoryIsFull(GameTestHelper helper) {
        RemoveTankEntryPacketGameTests.giveOrDropDropsFishWhenInventoryIsFull(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void giveOrDropDropsFishWhenInventoryIsFullInCreativeMode(GameTestHelper helper) {
        RemoveTankEntryPacketGameTests.giveOrDropDropsFishWhenInventoryIsFullInCreativeMode(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void giveOrDropDeliversFishNormallyWhenInventoryHasSpace(GameTestHelper helper) {
        RemoveTankEntryPacketGameTests.giveOrDropDeliversFishNormallyWhenInventoryHasSpace(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void giveOrDropPilesASecondFishIntoTheExistingPile(GameTestHelper helper) {
        RemoveTankEntryPacketGameTests.giveOrDropPilesASecondFishIntoTheExistingPile(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    @GameTest(template = "fishtastic_empty")
    public void giveOrDropThroughRealMenuAfterPickupWhileOpenDoesNotLoseTheFish(GameTestHelper helper) {
        RemoveTankEntryPacketGameTests.giveOrDropThroughRealMenuAfterPickupWhileOpenDoesNotLoseTheFish(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    // -------------------------------------------------------------------------
    // Creative tab tests
    // -------------------------------------------------------------------------

    @GameTest(template = "fishtastic_empty")
    public void decorationsTabContainsExactlyCosmeticStructuresAndDecorations(GameTestHelper helper) {
        CreativeTabGameTests.decorationsTabContainsExactlyCosmeticStructuresAndDecorations(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void mainTabNoLongerContainsMovedCosmetics(GameTestHelper helper) {
        CreativeTabGameTests.mainTabNoLongerContainsMovedCosmetics(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void advanceStepBaitLoadTransitionsToWaitingForCast(GameTestHelper helper) {
        TutorialManagerGameTests.advanceStepBaitLoadTransitionsToWaitingForCast(helper, FishtasticTestSupport.playerSupplier(helper));
    }

    // -------------------------------------------------------------------------
    // Catch celebration tests  (pure timeline logic, zero rendering)
    // -------------------------------------------------------------------------

    @GameTest(template = "fishtastic_empty")
    public void legendaryResolvesToHeroTier(GameTestHelper helper) {
        CatchCelebrationGameTests.legendaryResolvesToHeroTier(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void ordinaryCatchResolvesToNone(GameTestHelper helper) {
        CatchCelebrationGameTests.ordinaryCatchResolvesToNone(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void undiscoveredResolvesToDiscoveryTier(GameTestHelper helper) {
        CatchCelebrationGameTests.undiscoveredResolvesToDiscoveryTier(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void legendaryOutranksDiscovery(GameTestHelper helper) {
        CatchCelebrationGameTests.legendaryOutranksDiscovery(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void emptyRewardsResolveToNone(GameTestHelper helper) {
        CatchCelebrationGameTests.emptyRewardsResolveToNone(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void heroStackPicksHighestQuality(GameTestHelper helper) {
        CatchCelebrationGameTests.heroStackPicksHighestQuality(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void heroStackPicksNewSpeciesOnDiscovery(GameTestHelper helper) {
        CatchCelebrationGameTests.heroStackPicksNewSpeciesOnDiscovery(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void phaseBoundariesFollowTimings(GameTestHelper helper) {
        CatchCelebrationGameTests.phaseBoundariesFollowTimings(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void clockClampsAtTotal(GameTestHelper helper) {
        CatchCelebrationGameTests.clockClampsAtTotal(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void timeScaleFreezesThenRampsBack(GameTestHelper helper) {
        CatchCelebrationGameTests.timeScaleFreezesThenRampsBack(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void discoveryHasNoSlowMotionHold(GameTestHelper helper) {
        CatchCelebrationGameTests.discoveryHasNoSlowMotionHold(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void heroScaleGrowsToPeakThenReturnsToNormal(GameTestHelper helper) {
        CatchCelebrationGameTests.heroScaleGrowsToPeakThenReturnsToNormal(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void settleReleasesIntoPhysics(GameTestHelper helper) {
        CatchCelebrationGameTests.settleReleasesIntoPhysics(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void settleFinishesOnceItemLeavesScreen(GameTestHelper helper) {
        CatchCelebrationGameTests.settleFinishesOnceItemLeavesScreen(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void settlePhysicsDeceleratesUnderGravity(GameTestHelper helper) {
        CatchCelebrationGameTests.settlePhysicsDeceleratesUnderGravity(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void settleRotationComesFromPhysics(GameTestHelper helper) {
        CatchCelebrationGameTests.settleRotationComesFromPhysics(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void silhouetteDropsAtReveal(GameTestHelper helper) {
        CatchCelebrationGameTests.silhouetteDropsAtReveal(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void revealSwapHappensWhileEdgeOn(GameTestHelper helper) {
        CatchCelebrationGameTests.revealSwapHappensWhileEdgeOn(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void heroIsUnsqueezedOutsideTheTurn(GameTestHelper helper) {
        CatchCelebrationGameTests.heroIsUnsqueezedOutsideTheTurn(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void sparkleBurstFiresOnceAtReveal(GameTestHelper helper) {
        CatchCelebrationGameTests.sparkleBurstFiresOnceAtReveal(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void skipJumpsToSettle(GameTestHelper helper) {
        CatchCelebrationGameTests.skipJumpsToSettle(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void skipIgnoredBeforeReveal(GameTestHelper helper) {
        CatchCelebrationGameTests.skipIgnoredBeforeReveal(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void skipAfterRevealKeepsBurstConsumed(GameTestHelper helper) {
        CatchCelebrationGameTests.skipAfterRevealKeepsBurstConsumed(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void skipDoesNotRewind(GameTestHelper helper) {
        CatchCelebrationGameTests.skipDoesNotRewind(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void gameplaySuppressedUntilFinished(GameTestHelper helper) {
        CatchCelebrationGameTests.gameplaySuppressedUntilFinished(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void shakeDecaysThenRebuildsIntoTheReveal(GameTestHelper helper) {
        CatchCelebrationGameTests.shakeDecaysThenRebuildsIntoTheReveal(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void celebrationNoneTierIsRejected(GameTestHelper helper) {
        CatchCelebrationGameTests.noneTierIsRejected(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void applyQualityAndSizeSetsBothComponents(GameTestHelper helper) {
        CatchCelebrationGameTests.applyQualityAndSizeSetsBothComponents(helper);
    }

    @GameTest(template = "fishtastic_empty")
    public void applyQualityAndSizeOverwritesExistingQuality(GameTestHelper helper) {
        CatchCelebrationGameTests.applyQualityAndSizeOverwritesExistingQuality(helper);
    }
}
