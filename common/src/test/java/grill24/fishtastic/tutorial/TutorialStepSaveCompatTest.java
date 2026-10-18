package grill24.fishtastic.tutorial;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tutorial steps are persisted by ordinal. Stage 6 appended NOT_STARTED/ENCYCLOPEDIA_* to
 * {@link TutorialStep} and made NOT_STARTED the default, so an older save that stored the
 * original first step must still decode to what it meant.
 */
class TutorialStepSaveCompatTest {

    @Test
    void originalOrdinalsStillDecodeToTheSameSteps() {
        String[] original = {"BAIT_LOAD", "WAITING_FOR_CAST", "HOOK_IN_WATER", "MINIGAME_INTRO", "MINIGAME_CONTROL",
                "MINIGAME_CATCH", "CATCH_RESULT", "QUEST_INTRO", "QUEST_CLAIM", "SHOP_BROWSE", "COMPLETE"};
        for (int i = 0; i < original.length; i++) {
            TutorialStep decoded = TutorialStep.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(i))
                    .result().orElseThrow();
            assertEquals(original[i], decoded.name(), "saved ordinal " + i);
        }
    }

    @Test
    void everyStepRoundTripsThroughTheSaveCodec() {
        for (TutorialStep step : TutorialStep.values()) {
            var json = TutorialStep.CODEC.encodeStart(JsonOps.INSTANCE, step).result().orElseThrow();
            assertEquals(step, TutorialStep.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow());
        }
    }

    @Test
    void companionStepsKeepTheOriginalThreeOrdinals() {
        assertEquals(0, CompanionTutorialStep.NOT_STARTED.ordinal());
        assertEquals(1, CompanionTutorialStep.INTRO.ordinal());
        assertEquals(2, CompanionTutorialStep.COMPLETE.ordinal());
        for (CompanionTutorialStep step : CompanionTutorialStep.values()) {
            var json = CompanionTutorialStep.CODEC.encodeStart(JsonOps.INSTANCE, step).result().orElseThrow();
            assertEquals(step, CompanionTutorialStep.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow());
        }
    }
}
