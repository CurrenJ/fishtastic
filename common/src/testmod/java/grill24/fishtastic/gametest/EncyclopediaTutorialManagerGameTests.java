package grill24.fishtastic.gametest;

import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.tutorial.EncyclopediaTutorialManager;
import grill24.fishtastic.tutorial.EncyclopediaTutorialStep;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/**
 * Server-side game tests for EncyclopediaTutorialManager's step-advancement hooks.
 *
 * See TutorialManagerGameTests for why these take a {@code mockPlayer} supplier rather than
 * calling GameTestHelper.makeMockServerPlayerInLevel() directly.
 * All methods are pure GameTestHelper consumers — no platform annotations here.
 */
public final class EncyclopediaTutorialManagerGameTests {

    private EncyclopediaTutorialManagerGameTests() {}

    private static void setStep(GameTestHelper helper, ServerPlayer player, EncyclopediaTutorialStep step) {
        FishCatchSavedData.getOrCreate(helper.getLevel().getServer()).setEncyclopediaTutorialStep(player, step);
    }

    private static EncyclopediaTutorialStep getStep(GameTestHelper helper, ServerPlayer player) {
        return FishCatchSavedData.getOrCreate(helper.getLevel().getServer()).getEncyclopediaTutorialStep(player);
    }

    /** A player who has never opened the encyclopedia before starts at INTRO on first open. */
    public static void openingEncyclopediaFromNotStartedStartsIntro(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        helper.assertTrue(
            getStep(helper, player) == EncyclopediaTutorialStep.NOT_STARTED,
            "An untouched player must default to NOT_STARTED"
        );

        EncyclopediaTutorialManager.onEncyclopediaOpened(player);

        helper.assertTrue(
            getStep(helper, player) == EncyclopediaTutorialStep.INTRO,
            "First-ever open must advance NOT_STARTED to INTRO"
        );
        helper.succeed();
    }

    /** Reopening the screen while still on an unfinished step must not skip or reset it. */
    public static void reopeningEncyclopediaWhileInProgressIsIdempotent(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        EncyclopediaTutorialManager.onEncyclopediaOpened(player);
        helper.assertTrue(getStep(helper, player) == EncyclopediaTutorialStep.INTRO, "Sanity check: first open must reach INTRO");

        EncyclopediaTutorialManager.onEncyclopediaOpened(player);

        helper.assertTrue(
            getStep(helper, player) == EncyclopediaTutorialStep.INTRO,
            "Reopening while still on INTRO must not change the step"
        );
        helper.succeed();
    }

    /** Once the player has finished the encyclopedia tutorial, reopening the screen must not restart it. */
    public static void openingEncyclopediaAfterCompleteDoesNotRestart(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        setStep(helper, player, EncyclopediaTutorialStep.COMPLETE);

        EncyclopediaTutorialManager.onEncyclopediaOpened(player);

        helper.assertTrue(
            getStep(helper, player) == EncyclopediaTutorialStep.COMPLETE,
            "Opening the encyclopedia after COMPLETE must not restart the tutorial"
        );
        helper.succeed();
    }

    /** advanceStep must ignore a client-supplied fromStep that no longer matches the server's current step. */
    public static void advanceStepNoOpWhenFromStepDoesNotMatchCurrent(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        setStep(helper, player, EncyclopediaTutorialStep.INTRO);

        EncyclopediaTutorialManager.advanceStep(player, EncyclopediaTutorialStep.ZONES_AND_REWARDS);

        helper.assertTrue(
            getStep(helper, player) == EncyclopediaTutorialStep.INTRO,
            "advanceStep must not change the step when fromStep doesn't match the player's actual current step"
        );
        helper.succeed();
    }

    /** Walks the entire encyclopedia tutorial from first open through COMPLETE via the public hooks. */
    public static void encyclopediaTutorialWalksFullChainToCompletion(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();

        EncyclopediaTutorialManager.onEncyclopediaOpened(player);
        helper.assertTrue(getStep(helper, player) == EncyclopediaTutorialStep.INTRO, "Expected INTRO after first open");

        EncyclopediaTutorialManager.advanceStep(player, EncyclopediaTutorialStep.INTRO);
        helper.assertTrue(getStep(helper, player) == EncyclopediaTutorialStep.ZONES_AND_REWARDS, "Expected ZONES_AND_REWARDS");

        EncyclopediaTutorialManager.advanceStep(player, EncyclopediaTutorialStep.ZONES_AND_REWARDS);
        helper.assertTrue(getStep(helper, player) == EncyclopediaTutorialStep.COMPLETE, "Expected COMPLETE as the final step");

        helper.succeed();
    }
}
