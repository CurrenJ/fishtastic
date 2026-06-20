package grill24.fishtastic.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import grill24.fishtastic.data.FishEncyclopediaEntry;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.Optional;

/**
 * Codec tests for FishEncyclopediaEntry — verifies the unlock-threshold defaulting behaviour
 * and lore optionality, since both are driven entirely by optionalFieldOf() defaults rather
 * than code that GameTestHelper can otherwise exercise.
 * All methods are pure GameTestHelper consumers — no platform annotations here.
 */
public final class FishEncyclopediaEntryGameTests {

    private FishEncyclopediaEntryGameTests() {}

    public static void emptyObjectDecodesToAllDefaults(GameTestHelper helper) {
        JsonObject json = new JsonObject();

        FishEncyclopediaEntry decoded = FishEncyclopediaEntry.CODEC.parse(JsonOps.INSTANCE, json)
            .getOrThrow(error -> new AssertionError("Expected empty object to decode: " + error));

        helper.assertTrue(decoded.lore().isEmpty(), "lore must default to empty when absent");
        helper.assertTrue(
            decoded.thresholds().equals(FishEncyclopediaEntry.UnlockThresholds.DEFAULT),
            "thresholds must default to UnlockThresholds.DEFAULT when absent"
        );
        helper.succeed();
    }

    public static void partialThresholdsFillRemainingDefaults(GameTestHelper helper) {
        JsonObject thresholds = new JsonObject();
        thresholds.addProperty("name_reveal_catches", 2);
        JsonObject json = new JsonObject();
        json.add("thresholds", thresholds);

        FishEncyclopediaEntry decoded = FishEncyclopediaEntry.CODEC.parse(JsonOps.INSTANCE, json)
            .getOrThrow(error -> new AssertionError("Expected partial thresholds to decode: " + error));

        FishEncyclopediaEntry.UnlockThresholds t = decoded.thresholds();
        helper.assertTrue(t.nameRevealCatches() == 2, "explicit nameRevealCatches must override default, got " + t.nameRevealCatches());
        helper.assertTrue(t.statsCatches() == 1, "unset statsCatches must fall back to default 1, got " + t.statsCatches());
        helper.assertTrue(t.typesCatches() == 5, "unset typesCatches must fall back to default 5, got " + t.typesCatches());
        helper.assertTrue(t.spawnConditionsCatches() == 1, "unset spawnConditionsCatches must fall back to default 1, got " + t.spawnConditionsCatches());
        helper.assertTrue(t.loreCatches() == 25, "unset loreCatches must fall back to default 25, got " + t.loreCatches());
        helper.succeed();
    }

    public static void fullEntryRoundTripsThroughJson(GameTestHelper helper) {
        FishEncyclopediaEntry original = new FishEncyclopediaEntry(
            Optional.of("A shy fish that hides in caves."),
            new FishEncyclopediaEntry.UnlockThresholds(1, 2, 3, 4, 5)
        );

        JsonElement encoded = FishEncyclopediaEntry.CODEC.encodeStart(JsonOps.INSTANCE, original)
            .getOrThrow(error -> new AssertionError("Expected entry to encode: " + error));
        FishEncyclopediaEntry decoded = FishEncyclopediaEntry.CODEC.parse(JsonOps.INSTANCE, encoded)
            .getOrThrow(error -> new AssertionError("Expected encoded entry to decode: " + error));

        helper.assertTrue(decoded.lore().equals(original.lore()), "lore must round-trip");
        helper.assertTrue(decoded.thresholds().equals(original.thresholds()), "thresholds must round-trip");
        helper.succeed();
    }
}
