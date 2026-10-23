package grill24.fishtastic.forge.gametest;

import grill24.fishtastic.gametest.ShopEntryGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** One-test Forge holder used when the shared GameTest name filter is set. */
@GameTestHolder("fishtastic")
@PrefixGameTestTemplate(false)
public final class ForgeShopRerollGameTests {
    private ForgeShopRerollGameTests() {}

    @GameTest(template = "fishtastic_empty")
    public static void shopRerollInteractionGrantsOneFreeUsePerDayAndEscalatesPaidCosts(GameTestHelper helper) {
        ShopEntryGameTests.shopRerollInteractionGrantsOneFreeUsePerDayAndEscalatesPaidCosts(helper);
    }
}
