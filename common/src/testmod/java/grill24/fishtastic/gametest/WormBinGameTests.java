package grill24.fishtastic.gametest;

import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.block.WormBinBlock;
import grill24.fishtastic.block.WormBinPhase;
import grill24.fishtastic.blockentity.WormBinBlockEntity;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.util.FishQualityHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Server-side game tests for WormBinBlockEntity.
 * All methods are pure GameTestHelper consumers — no platform annotations here.
 * Platform-specific registration delegates to these static methods.
 */
public final class WormBinGameTests {

    private static final BlockPos FLOOR = new BlockPos(1, 0, 1);
    private static final BlockPos BIN_POS = new BlockPos(1, 1, 1);

    private WormBinGameTests() {}

    private static WormBinBlockEntity placeWormBin(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(BIN_POS, FishtasticBlocks.WORM_BIN.value());
        return helper.getBlockEntity(BIN_POS, WormBinBlockEntity.class);
    }

    private static ItemStack commonFish() {
        ItemStack stack = new ItemStack(FishtasticItems.BLUEGILL.value());
        FishQualityHelper.setQuality(stack, FishQuality.Quality.COMMON);
        return stack;
    }

    private static ItemStack fishWithQuality(FishQuality.Quality quality) {
        ItemStack stack = new ItemStack(FishtasticItems.BLUEGILL.value());
        FishQualityHelper.setQuality(stack, quality);
        return stack;
    }

    // -------------------------------------------------------------------------
    // Phase / initial state
    // -------------------------------------------------------------------------

    /**
     * A freshly placed worm bin must be in EMPTY phase.
     */
    public static void wormBinStartsEmpty(GameTestHelper helper) {
        WormBinBlockEntity bin = placeWormBin(helper);
        BlockState state = helper.getBlockState(BIN_POS);
        helper.assertTrue(
            state.getValue(WormBinBlock.PHASE) == WormBinPhase.EMPTY,
            "New worm bin must start in EMPTY phase"
        );
        helper.succeed();
    }

    /**
     * depositFish() adds fish to the internal list and reduces remaining capacity.
     */
    public static void depositFishAddsToList(GameTestHelper helper) {
        WormBinBlockEntity bin = placeWormBin(helper);
        helper.assertTrue(bin.getDepositedFish().isEmpty(), "Fresh bin must have no deposited fish");
        bin.depositFish(commonFish());
        helper.assertTrue(bin.getDepositedFish().size() == 1, "After one deposit fish list must have 1 entry");
        helper.succeed();
    }

    /**
     * Depositing the first fish transitions bin to CONVERTING when the block interaction runs.
     * We simulate this by setting the block state, then verify tick-driven behavior.
     */
    public static void depositCapAtFive(GameTestHelper helper) {
        WormBinBlockEntity bin = placeWormBin(helper);
        for (int i = 0; i < 5; i++) {
            helper.assertTrue(bin.canDeposit(), "Should be able to deposit fish " + (i + 1));
            bin.depositFish(commonFish());
        }
        helper.assertTrue(!bin.canDeposit(), "canDeposit() must return false after 5 fish");
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Aeration
    // -------------------------------------------------------------------------

    /**
     * canAerate() returns false after 5 aerations (MAX_AERATION_TURNS = 5).
     */
    public static void aerationCapAtFive(GameTestHelper helper) {
        WormBinBlockEntity bin = placeWormBin(helper);
        for (int i = 0; i < 5; i++) {
            helper.assertTrue(bin.canAerate(), "Should be able to aerate turn " + (i + 1));
            bin.aerate();
        }
        helper.assertTrue(!bin.canAerate(), "canAerate() must return false after 5 turns");
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Conversion timing
    // -------------------------------------------------------------------------

    private static void setConverting(GameTestHelper helper, WormBinBlockEntity bin) {
        bin.depositFish(commonFish());
        helper.getLevel().setBlockAndUpdate(
            helper.absolutePos(BIN_POS),
            helper.getBlockState(BIN_POS).setValue(WormBinBlock.PHASE, WormBinPhase.CONVERTING)
        );
    }

    /**
     * With 0 aerations the bin must not finish before tick 6000 and must finish at tick 6000.
     */
    public static void conversionTakesBaseTicks(GameTestHelper helper) {
        WormBinBlockEntity bin = placeWormBin(helper);
        setConverting(helper, bin);

        helper.runAfterDelay(5999, () ->
            helper.assertTrue(
                helper.getBlockState(BIN_POS).getValue(WormBinBlock.PHASE) == WormBinPhase.CONVERTING,
                "Bin must still be CONVERTING before tick 6000"
            )
        );
        helper.runAfterDelay(6001, () -> {
            helper.assertTrue(
                helper.getBlockState(BIN_POS).getValue(WormBinBlock.PHASE) == WormBinPhase.READY,
                "Bin must be READY after 6000 ticks with no aeration"
            );
            helper.succeed();
        });
    }

    /**
     * With 5 aerations the bin must finish 5×240 = 1200 ticks early, at tick 4800.
     */
    public static void aerationReducesConversionTime(GameTestHelper helper) {
        WormBinBlockEntity bin = placeWormBin(helper);
        for (int i = 0; i < 5; i++) bin.aerate();
        setConverting(helper, bin);

        helper.runAfterDelay(4799, () ->
            helper.assertTrue(
                helper.getBlockState(BIN_POS).getValue(WormBinBlock.PHASE) == WormBinPhase.CONVERTING,
                "Bin must still be CONVERTING before tick 4800"
            )
        );
        helper.runAfterDelay(4801, () -> {
            helper.assertTrue(
                helper.getBlockState(BIN_POS).getValue(WormBinBlock.PHASE) == WormBinPhase.READY,
                "Bin must be READY after 4800 ticks with 5 aerations"
            );
            helper.succeed();
        });
    }

    // -------------------------------------------------------------------------
    // Yield calculation
    // -------------------------------------------------------------------------

    /**
     * One COMMON fish with no aeration yields exactly 1 worm (fixed value in computeYield).
     */
    public static void commonFishYield(GameTestHelper helper) {
        WormBinBlockEntity bin = placeWormBin(helper);
        bin.depositFish(fishWithQuality(FishQuality.Quality.COMMON));
        helper.getLevel().setBlockAndUpdate(
            helper.absolutePos(BIN_POS),
            helper.getBlockState(BIN_POS).setValue(WormBinBlock.PHASE, WormBinPhase.CONVERTING)
        );
        helper.runAfterDelay(6001, () -> {
            int worms = bin.getPendingWorms();
            helper.assertTrue(worms == 1, "1 COMMON fish + 0 aerations must yield 1 worm, got " + worms);
            helper.succeed();
        });
    }

    /**
     * One LEGENDARY fish with no aeration yields exactly 25 worms.
     */
    public static void legendaryFishYield(GameTestHelper helper) {
        WormBinBlockEntity bin = placeWormBin(helper);
        bin.depositFish(fishWithQuality(FishQuality.Quality.LEGENDARY));
        helper.getLevel().setBlockAndUpdate(
            helper.absolutePos(BIN_POS),
            helper.getBlockState(BIN_POS).setValue(WormBinBlock.PHASE, WormBinPhase.CONVERTING)
        );
        helper.runAfterDelay(6001, () -> {
            int worms = bin.getPendingWorms();
            helper.assertTrue(worms == 25, "1 LEGENDARY fish + 0 aerations must yield 25 worms, got " + worms);
            helper.succeed();
        });
    }

    /**
     * Aeration turns are added to total yield: 5 COMMON fish (5×1) + 5 aerations = 10.
     */
    public static void aerationAddsToYield(GameTestHelper helper) {
        WormBinBlockEntity bin = placeWormBin(helper);
        for (int i = 0; i < 5; i++) bin.depositFish(fishWithQuality(FishQuality.Quality.COMMON));
        for (int i = 0; i < 5; i++) bin.aerate();
        helper.getLevel().setBlockAndUpdate(
            helper.absolutePos(BIN_POS),
            helper.getBlockState(BIN_POS).setValue(WormBinBlock.PHASE, WormBinPhase.CONVERTING)
        );
        helper.runAfterDelay(4801, () -> {
            int worms = bin.getPendingWorms();
            helper.assertTrue(worms == 10, "5 COMMON + 5 aerations must yield 10 worms, got " + worms);
            helper.succeed();
        });
    }

    /**
     * Mixed quality: COMMON(1)+UNCOMMON(3)+RARE(6)+EPIC(12)+LEGENDARY(25) + 0 aeration = 47.
     */
    public static void mixedQualityYield(GameTestHelper helper) {
        WormBinBlockEntity bin = placeWormBin(helper);
        bin.depositFish(fishWithQuality(FishQuality.Quality.COMMON));
        bin.depositFish(fishWithQuality(FishQuality.Quality.UNCOMMON));
        bin.depositFish(fishWithQuality(FishQuality.Quality.RARE));
        bin.depositFish(fishWithQuality(FishQuality.Quality.EPIC));
        bin.depositFish(fishWithQuality(FishQuality.Quality.LEGENDARY));
        helper.getLevel().setBlockAndUpdate(
            helper.absolutePos(BIN_POS),
            helper.getBlockState(BIN_POS).setValue(WormBinBlock.PHASE, WormBinPhase.CONVERTING)
        );
        helper.runAfterDelay(6001, () -> {
            int worms = bin.getPendingWorms();
            int expected = 1 + 3 + 6 + 12 + 25;
            helper.assertTrue(worms == expected, "Mixed quality yield must be " + expected + ", got " + worms);
            helper.succeed();
        });
    }

    // -------------------------------------------------------------------------
    // Harvest + reset
    // -------------------------------------------------------------------------

    /**
     * After conversion, pendingWorms has the correct count; reset() clears everything.
     */
    public static void harvestResetsToEmpty(GameTestHelper helper) {
        WormBinBlockEntity bin = placeWormBin(helper);
        bin.depositFish(fishWithQuality(FishQuality.Quality.EPIC)); // yields 12
        helper.getLevel().setBlockAndUpdate(
            helper.absolutePos(BIN_POS),
            helper.getBlockState(BIN_POS).setValue(WormBinBlock.PHASE, WormBinPhase.CONVERTING)
        );
        helper.runAfterDelay(6001, () -> {
            helper.assertTrue(
                helper.getBlockState(BIN_POS).getValue(WormBinBlock.PHASE) == WormBinPhase.READY,
                "Bin must reach READY before harvest"
            );
            helper.assertTrue(bin.getPendingWorms() == 12, "Pending worms must be 12 (EPIC), got " + bin.getPendingWorms());

            bin.reset();
            helper.getLevel().setBlockAndUpdate(
                helper.absolutePos(BIN_POS),
                helper.getBlockState(BIN_POS).setValue(WormBinBlock.PHASE, WormBinPhase.EMPTY)
            );

            helper.assertTrue(bin.getPendingWorms() == 0, "After reset, pending worms must be 0");
            helper.assertTrue(bin.getDepositedFish().isEmpty(), "After reset, deposited fish must be empty");
            helper.assertTrue(bin.canDeposit(), "After reset, canDeposit() must be true");
            helper.assertTrue(bin.canAerate(), "After reset, canAerate() must be true");
            helper.assertTrue(
                helper.getBlockState(BIN_POS).getValue(WormBinBlock.PHASE) == WormBinPhase.EMPTY,
                "After reset block state must be EMPTY"
            );
            helper.succeed();
        });
    }

    // -------------------------------------------------------------------------
    // Deposited fish list
    // -------------------------------------------------------------------------

    /**
     * getDepositedFish() returns a copy with correct quality ordering.
     */
    public static void depositedFishListRetainsOrder(GameTestHelper helper) {
        WormBinBlockEntity bin = placeWormBin(helper);
        bin.depositFish(fishWithQuality(FishQuality.Quality.RARE));
        bin.depositFish(fishWithQuality(FishQuality.Quality.EPIC));
        bin.aerate();
        bin.aerate();

        List<ItemStack> fish = bin.getDepositedFish();
        helper.assertTrue(fish.size() == 2, "Must have 2 deposited fish");
        helper.assertTrue(
            FishQualityHelper.getQuality(fish.get(0)) == FishQuality.Quality.RARE,
            "First deposited fish must be RARE"
        );
        helper.assertTrue(
            FishQualityHelper.getQuality(fish.get(1)) == FishQuality.Quality.EPIC,
            "Second deposited fish must be EPIC"
        );
        helper.succeed();
    }
}
