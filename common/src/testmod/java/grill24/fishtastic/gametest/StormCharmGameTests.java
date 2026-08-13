package grill24.fishtastic.gametest;

import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.item.StormCharmItem;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Covers the Storm Charm — a single-use consumable that summons a real thunderstorm rather than a
 * rod-slot charm that fakes one.
 *
 * <p>The design contract worth guarding: it must be slottable into the rod (players reach for that
 * slot instinctively, and {@code FishingMinigameManager#startSession} fires and consumes it on the
 * next cast), and the storm it creates must be a genuine weather change, so
 * {@link FishProfile.WeatherCondition#fromLevel} reports THUNDER with no quest-side special-casing.
 */
public final class StormCharmGameTests {

    private StormCharmGameTests() {}

    /**
     * It must be accepted by the rod's charm slot — that is where players will instinctively put
     * it, and startSession fires + consumes it from there on the next cast.
     */
    public static void stormCharmIsSlottableIntoTheRod(GameTestHelper helper) {
        ItemStack storm = new ItemStack(FishtasticItems.STORM_CHARM.value());
        helper.assertTrue(storm.is(FishtasticItemTags.FISHING_CHARMS),
                "Storm Charm must be in fishing_charms so the rod accepts it into the charm slot");
        helper.succeed();
    }

    /**
     * Unlike every other slottable charm it carries no CharmEffect — it is consumed on cast rather
     * than modifying the session. If it ever gains one, startSession's storm branch would need to
     * stop clearing the slot.
     */
    public static void stormCharmCarriesNoCharmEffect(GameTestHelper helper) {
        ItemStack storm = new ItemStack(FishtasticItems.STORM_CHARM.value());
        helper.assertTrue(
                storm.get(grill24.fishtastic.FishtasticDataComponents.CHARM_EFFECT.value()) == null,
                "Storm Charm must carry no CharmEffect — it fires and is consumed instead");
        helper.assertTrue(
                new ItemStack(FishtasticItems.LUNA_CHARM.value())
                        .get(grill24.fishtastic.FishtasticDataComponents.CHARM_EFFECT.value()) != null,
                "Luna Charm must still carry one, as the control for this test");
        helper.succeed();
    }

    /** It stacks — a consumable you stockpile, unlike the single durability-bearing rod charms. */
    public static void stormCharmStacksUnlikeRodCharms(GameTestHelper helper) {
        ItemStack storm = new ItemStack(FishtasticItems.STORM_CHARM.value());
        helper.assertTrue(storm.getMaxStackSize() > 1,
                "Storm Charm should stack, was " + storm.getMaxStackSize());
        helper.assertFalse(storm.isDamageableItem(),
                "Storm Charm is consumed on use, so it must not carry durability");
        helper.succeed();
    }

    /**
     * The whole point of doing it for real: setting the weather makes
     * {@code WeatherCondition.fromLevel} report THUNDER on its own, which is what every
     * weather-gated quest already reads. No quest-side special-casing exists or is needed.
     */
    public static void summonedStormIsReadAsThunderByQuestConditions(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var server = level.getServer();

        int clearTime = level.getWeatherData().getClearWeatherTime();
        int rainTime = level.getWeatherData().getRainTime();
        boolean wasRaining = level.isRaining();
        boolean wasThundering = level.isThundering();
        // NeoForge's GameTestServer hard-disables ADVANCE_WEATHER for every test run (deterministic
        // worlds), which trySummonStorm correctly treats as "refuse rather than leave a permanent
        // storm" — force it on for this test only, restoring it after, or the guard rejects the
        // storm and the assertions below fail for reasons unrelated to the charm itself.
        boolean wasAdvancingWeather = level.getGameRules().get(GameRules.ADVANCE_WEATHER);
        level.getGameRules().set(GameRules.ADVANCE_WEATHER, true, server);
        try {
            StormCharmItem.trySummonStorm(level, helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL));

            helper.assertTrue(level.isThundering(),
                    "Summoning a storm must make the level thunder immediately, not after the "
                            + "0.01/tick ramp — the cast that spent the charm has to count");
            helper.assertTrue(
                    FishProfile.WeatherCondition.fromLevel(level, helper.absolutePos(net.minecraft.core.BlockPos.ZERO))
                            == FishProfile.WeatherCondition.THUNDER,
                    "A summoned storm must be reported as THUNDER by the same call quest tracking uses");
        } finally {
            server.setWeatherParameters(clearTime, rainTime, wasRaining, wasThundering);
            level.setRainLevel(wasRaining ? 1.0f : 0.0f);
            level.setThunderLevel(wasThundering ? 1.0f : 0.0f);
            level.getGameRules().set(GameRules.ADVANCE_WEATHER, wasAdvancingWeather, server);
        }
        helper.succeed();
    }

    /**
     * Hand-use is a held charge, not an instant click. The charge must also be genuinely
     * interruptible: releasing early runs releaseUsing, which must neither summon nor consume, so a
     * misclick costs the player nothing.
     */
    public static void handUseChargesUpAndIsFreeToCancel(GameTestHelper helper) {
        ItemStack storm = new ItemStack(FishtasticItems.STORM_CHARM.value());
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);

        helper.assertTrue(storm.getUseDuration(player) == StormCharmItem.CHARGE_TICKS,
                "Storm Charm must charge for " + StormCharmItem.CHARGE_TICKS + " ticks, was "
                        + storm.getUseDuration(player));
        helper.assertTrue(storm.getUseAnimation() != net.minecraft.world.item.ItemUseAnimation.NONE,
                "A charge-up needs a visible use animation");

        // Abandoning the charge partway must be a no-op on both the item and the weather.
        ServerLevel level = helper.getLevel();
        boolean wasThundering = level.isThundering();
        int before = storm.getCount();
        storm.getItem().releaseUsing(storm, level, player, StormCharmItem.CHARGE_TICKS / 2);

        helper.assertTrue(storm.getCount() == before,
                "Releasing the charge early must not consume the charm");
        helper.assertTrue(level.isThundering() == wasThundering,
                "Releasing the charge early must not summon a storm");
        helper.succeed();
    }

    /** The duration must sit inside vanilla's own thunder range so a summoned storm isn't anomalous. */
    public static void stormDurationIsWithinVanillaThunderRange(GameTestHelper helper) {
        helper.assertTrue(StormCharmItem.STORM_DURATION_TICKS >= 3000
                        && StormCharmItem.STORM_DURATION_TICKS <= 15000,
                "Storm duration should sit inside vanilla's 3000-15000 tick thunder range, was "
                        + StormCharmItem.STORM_DURATION_TICKS);
        helper.succeed();
    }
}
