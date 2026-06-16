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
}
