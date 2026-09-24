package grill24.fishtastic.gametest;

import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.item.StormCharmItem;
import grill24.fishtastic.server.SunsetExtensionHandler;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.ServerLevelData;

import java.util.function.Supplier;

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
                FishtasticItemData.get(storm, grill24.fishtastic.FishtasticDataComponents.CHARM_EFFECT) == null,
                "Storm Charm must carry no CharmEffect — it fires and is consumed instead");
        helper.assertTrue(
                FishtasticItemData.get(new ItemStack(FishtasticItems.LUNA_CHARM.value()),
                        grill24.fishtastic.FishtasticDataComponents.CHARM_EFFECT) != null,
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

        // 1.21.1 has no ServerLevel#getWeatherData(), and ServerLevel.serverLevelData is private;
        // the level data object is a ServerLevelData at runtime.
        ServerLevelData data = (ServerLevelData) level.getLevelData();
        int clearTime = data.getClearWeatherTime();
        int rainTime = data.getRainTime();
        boolean wasRaining = level.isRaining();
        boolean wasThundering = level.isThundering();
        // NeoForge's GameTestServer hard-disables ADVANCE_WEATHER for every test run (deterministic
        // worlds), which trySummonStorm correctly treats as "refuse rather than leave a permanent
        // storm" — force it on for this test only, restoring it after, or the guard rejects the
        // storm and the assertions below fail for reasons unrelated to the charm itself.
        boolean wasAdvancingWeather = level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(true, server);
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
            level.setWeatherParameters(clearTime, rainTime, wasRaining, wasThundering);
            level.setRainLevel(wasRaining ? 1.0f : 0.0f);
            level.setThunderLevel(wasThundering ? 1.0f : 0.0f);
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(wasAdvancingWeather, server);
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
        helper.assertTrue(storm.getUseAnimation() != net.minecraft.world.item.UseAnim.NONE,
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

    /**
     * The Sunset Postcard Charm slows the day-time rate while it sits in a player's inventory
     * during the dawn and dusk windows (A2.8.c). 1.21.1 has no world clocks, so
     * {@code ServerLevelTickTimeMixin} applies the rate through a fractional accumulator that
     * advances day time by its whole part each tick; this measures that the accumulator genuinely
     * moves day time at the applied rate.
     *
     * <p>The rate is derived from data - the charm's {@code sunset_extension_seconds} against the
     * window's length at rate 1.0, with a floor - so the assertion reads the live rate rather than
     * hard-coding one, and tolerates the accumulator's single-tick remainder.
     */
    public static void dayTimeAdvancesAtTheAppliedRate(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = mockPlayer.get();
        ItemStack charm = new ItemStack(FishtasticItems.SUNSET_POSTCARD_CHARM.value());
        player.getInventory().add(charm);

        // Park day time at the start of the dawn window, where the handler applies a slowed rate.
        long dayTimeBefore = level.getDayTime();
        level.setDayTime(FishProfile.TimeOfDay.DAWN_START_TICK);

        // Let a server tick recompute the rate now that the charm is in the player's inventory.
        helper.runAfterDelay(2L, () -> {
            float rate = SunsetExtensionHandler.currentRate();
            boolean slowed = rate < 1.0f;
            long before = level.getDayTime();
            int span = 200;

            helper.runAfterDelay(span, () -> {
                long advanced = level.getDayTime() - before;
                long expected = Math.round(rate * span);

                // Restore first, so a failed assertion cannot leave the world slowed for other tests.
                player.getInventory().removeItem(charm);
                level.setDayTime(dayTimeBefore);

                helper.assertTrue(slowed,
                        "A Sunset Postcard during the dawn window must slow day time, but the rate was " + rate);
                helper.assertTrue(Math.abs(advanced - expected) <= 1,
                        "Day time must advance at the applied rate: over " + span + " ticks at rate " + rate
                                + " expected " + expected + " (+/-1 for the accumulator's remainder), got " + advanced);
                helper.succeed();
            });
        });
    }
}
