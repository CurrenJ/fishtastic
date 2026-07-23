package grill24.fishtastic.gametest;

import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.block.MarineCompostBlock;
import grill24.fishtastic.block.MarineCompostPhase;
import grill24.fishtastic.blockentity.MarineCompostBlockEntity;
import grill24.fishtastic.component.FishQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * Server-side game tests for MarineCompostBlockEntity.
 * All methods are pure GameTestHelper consumers — no platform annotations here.
 * Platform-specific registration delegates to these static methods.
 *
 * Marine compost is single-use: it's crafted pre-loaded with one fish's quality (see
 * MarineCompostRecipe) and placement (MarineCompostBlock#setPlacedBy) seeds the block entity
 * directly, so these tests seed the entity the same way rather than going through a recipe/placement.
 */
public final class MarineCompostGameTests {

    private static final BlockPos FLOOR = new BlockPos(1, 0, 1);
    private static final BlockPos BIN_POS = new BlockPos(1, 1, 1);

    private MarineCompostGameTests() {}

    private static MarineCompostBlockEntity placeMarineCompost(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(BIN_POS, FishtasticBlocks.MARINE_COMPOST.value());
        return helper.getBlockEntity(BIN_POS, MarineCompostBlockEntity.class);
    }

    private static boolean isConverting(MarineCompostPhase phase) {
        return phase == MarineCompostPhase.DRY || phase == MarineCompostPhase.WET;
    }

    private static void seedConverting(GameTestHelper helper, MarineCompostBlockEntity bin, FishQuality.Quality quality) {
        bin.initialize(quality);
        helper.getLevel().setBlockAndUpdate(
            helper.absolutePos(BIN_POS),
            helper.getBlockState(BIN_POS).setValue(MarineCompostBlock.PHASE, MarineCompostPhase.WET)
        );
    }

    // -------------------------------------------------------------------------
    // Aeration
    // -------------------------------------------------------------------------

    /**
     * canAerate() returns false after 5 aerations (MAX_AERATION_TURNS = 5).
     */
    public static void aerationCapAtFive(GameTestHelper helper) {
        MarineCompostBlockEntity bin = placeMarineCompost(helper);
        for (int i = 0; i < 5; i++) {
            long tick = (long) i * 1200;
            helper.assertTrue(bin.canAerate(tick), "Should be able to aerate turn " + (i + 1));
            bin.aerate(tick);
        }
        helper.assertTrue(!bin.canAerate(6000L), "canAerate() must return false after 5 turns");
        helper.succeed();
    }

    /**
     * A second aeration attempt within 1200 ticks of the first must be rejected.
     */
    public static void aerationCooldownBlocksTooEarlyAttempt(GameTestHelper helper) {
        MarineCompostBlockEntity bin = placeMarineCompost(helper);
        helper.assertTrue(bin.canAerate(0L), "First aeration must be allowed");
        bin.aerate(0L);
        helper.assertTrue(!bin.canAerate(1199L), "Aeration at tick 1199 must be blocked by cooldown");
        helper.succeed();
    }

    /**
     * Aeration is allowed once exactly 1200 ticks have elapsed since the last aeration.
     */
    public static void aerationCooldownAllowsAfterCooldown(GameTestHelper helper) {
        MarineCompostBlockEntity bin = placeMarineCompost(helper);
        bin.aerate(0L);
        helper.assertTrue(bin.canAerate(1200L), "Aeration must be allowed after exactly 1200 ticks");
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Conversion timing
    // -------------------------------------------------------------------------

    /**
     * With 0 aerations the bin must not finish before tick 6000 and must finish at tick 6000.
     */
    public static void conversionTakesBaseTicks(GameTestHelper helper) {
        MarineCompostBlockEntity bin = placeMarineCompost(helper);
        seedConverting(helper, bin, FishQuality.Quality.COMMON);

        helper.runAfterDelay(5999, () ->
            helper.assertTrue(
                isConverting(helper.getBlockState(BIN_POS).getValue(MarineCompostBlock.PHASE)),
                "Bin must still be converting before tick 6000"
            )
        );
        helper.runAfterDelay(6001, () -> {
            helper.assertTrue(
                helper.getBlockState(BIN_POS).getValue(MarineCompostBlock.PHASE) == MarineCompostPhase.READY,
                "Bin must be READY after 6000 ticks with no aeration"
            );
            helper.succeed();
        });
    }

    /**
     * With 5 aerations the bin must finish 5×240 = 1200 ticks early, at tick 4800.
     */
    public static void aerationReducesConversionTime(GameTestHelper helper) {
        MarineCompostBlockEntity bin = placeMarineCompost(helper);
        for (int i = 0; i < 5; i++) bin.aerate((long) i * 1200);
        seedConverting(helper, bin, FishQuality.Quality.COMMON);

        helper.runAfterDelay(4799, () ->
            helper.assertTrue(
                isConverting(helper.getBlockState(BIN_POS).getValue(MarineCompostBlock.PHASE)),
                "Bin must still be converting before tick 4800"
            )
        );
        helper.runAfterDelay(4801, () -> {
            helper.assertTrue(
                helper.getBlockState(BIN_POS).getValue(MarineCompostBlock.PHASE) == MarineCompostPhase.READY,
                "Bin must be READY after 4800 ticks with 5 aerations"
            );
            helper.succeed();
        });
    }

    // -------------------------------------------------------------------------
    // Yield calculation
    // -------------------------------------------------------------------------

    /**
     * A COMMON fish with no aeration yields exactly 1 worm (fixed value in computeYield).
     */
    public static void commonFishYield(GameTestHelper helper) {
        MarineCompostBlockEntity bin = placeMarineCompost(helper);
        seedConverting(helper, bin, FishQuality.Quality.COMMON);
        helper.runAfterDelay(6001, () -> {
            int worms = bin.getPendingWorms();
            helper.assertTrue(worms == 1, "COMMON fish + 0 aerations must yield 1 worm, got " + worms);
            helper.succeed();
        });
    }

    /**
     * A RARE fish with no aeration yields exactly 6 worms.
     */
    public static void rareFishYield(GameTestHelper helper) {
        MarineCompostBlockEntity bin = placeMarineCompost(helper);
        seedConverting(helper, bin, FishQuality.Quality.RARE);
        helper.runAfterDelay(6001, () -> {
            int worms = bin.getPendingWorms();
            helper.assertTrue(worms == 6, "RARE fish + 0 aerations must yield 6 worms, got " + worms);
            helper.succeed();
        });
    }

    /**
     * A LEGENDARY fish with no aeration yields exactly 25 worms.
     */
    public static void legendaryFishYield(GameTestHelper helper) {
        MarineCompostBlockEntity bin = placeMarineCompost(helper);
        seedConverting(helper, bin, FishQuality.Quality.LEGENDARY);
        helper.runAfterDelay(6001, () -> {
            int worms = bin.getPendingWorms();
            helper.assertTrue(worms == 25, "LEGENDARY fish + 0 aerations must yield 25 worms, got " + worms);
            helper.succeed();
        });
    }

    /**
     * Aeration turns are added to total yield: 1 COMMON fish (1) + 5 aerations = 6.
     */
    public static void aerationAddsToYield(GameTestHelper helper) {
        MarineCompostBlockEntity bin = placeMarineCompost(helper);
        for (int i = 0; i < 5; i++) bin.aerate((long) i * 1200);
        seedConverting(helper, bin, FishQuality.Quality.COMMON);
        helper.runAfterDelay(4801, () -> {
            int worms = bin.getPendingWorms();
            helper.assertTrue(worms == 6, "COMMON + 5 aerations must yield 6 worms, got " + worms);
            helper.succeed();
        });
    }

    /**
     * A compost with no quality set (e.g. crafted from a fish lacking the quality component)
     * must default to COMMON's yield rather than erroring.
     */
    public static void noQualityDefaultsToCommonYield(GameTestHelper helper) {
        MarineCompostBlockEntity bin = placeMarineCompost(helper);
        seedConverting(helper, bin, null);
        helper.runAfterDelay(6001, () -> {
            int worms = bin.getPendingWorms();
            helper.assertTrue(worms == 1, "No quality set must default to COMMON's yield (1), got " + worms);
            helper.succeed();
        });
    }
}
