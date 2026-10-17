package grill24.fishtastic.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import grill24.FishtasticRegistries;
import grill24.fishtastic.data.EncyclopediaTiers;
import grill24.fishtastic.data.FishEncyclopediaEntry;
import grill24.fishtastic.data.FishProfile;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.Map;
import java.util.Optional;

/**
 * Tests for FishEncyclopediaEntry's codec (lore only — a legacy {@code thresholds} block must be
 * ignored, since thresholds are derived) and for {@link EncyclopediaTiers}' derivation against the
 * real fish profile registry.
 * All methods are pure GameTestHelper consumers — no platform annotations here.
 */
public final class FishEncyclopediaEntryGameTests {

    private FishEncyclopediaEntryGameTests() {}

    public static void emptyObjectDecodesToAllDefaults(GameTestHelper helper) {
        JsonObject json = new JsonObject();

        FishEncyclopediaEntry decoded = FishEncyclopediaEntry.CODEC.parse(JsonOps.INSTANCE, json)
            .getOrThrow(false, error -> { throw new AssertionError("Expected empty object to decode: " + error); });

        helper.assertTrue(decoded.lore().isEmpty(), "lore must default to empty when absent");
        helper.succeed();
    }

    public static void legacyThresholdsBlockIsIgnored(GameTestHelper helper) {
        JsonObject thresholds = new JsonObject();
        thresholds.addProperty("lore_catches", 999);
        JsonObject json = new JsonObject();
        json.addProperty("lore", "Old entry.");
        json.add("thresholds", thresholds);

        FishEncyclopediaEntry decoded = FishEncyclopediaEntry.CODEC.parse(JsonOps.INSTANCE, json)
            .getOrThrow(false, error -> { throw new AssertionError("Expected an entry with a legacy thresholds block to decode: " + error); });

        helper.assertTrue(decoded.equals(new FishEncyclopediaEntry(Optional.of("Old entry."))),
            "a legacy thresholds block must be ignored, got " + decoded);
        helper.succeed();
    }

    public static void fullEntryRoundTripsThroughJson(GameTestHelper helper) {
        FishEncyclopediaEntry original = new FishEncyclopediaEntry(Optional.of("A shy fish that hides in caves."));

        JsonElement encoded = FishEncyclopediaEntry.CODEC.encodeStart(JsonOps.INSTANCE, original)
            .getOrThrow(false, error -> { throw new AssertionError("Expected entry to encode: " + error); });
        FishEncyclopediaEntry decoded = FishEncyclopediaEntry.CODEC.parse(JsonOps.INSTANCE, encoded)
            .getOrThrow(false, error -> { throw new AssertionError("Expected encoded entry to decode: " + error); });

        helper.assertTrue(decoded.lore().equals(original.lore()), "lore must round-trip");
        helper.succeed();
    }

    /** Every registered fish gets derived thresholds, in a pool, with full completion at most 50 catches. */
    public static void everyFishGetsDerivedThresholdsWithinCap(GameTestHelper helper) {
        RegistryAccess access = helper.getLevel().registryAccess();
        Registry<FishProfile> registry = access.registryOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);
        Map<ResourceLocation, EncyclopediaTiers.Rarity> rarities = EncyclopediaTiers.rarities(access);

        helper.assertTrue(rarities.size() == registry.size(),
            "every fish profile must have a derived rarity, got " + rarities.size() + " of " + registry.size());
        for (ResourceLocation id : registry.keySet()) {
            EncyclopediaTiers.Rarity rarity = rarities.get(id);
            helper.assertTrue(rarity.pool() != null, id + " must fall in at least one location pool");
            FishEncyclopediaEntry.UnlockThresholds t = EncyclopediaTiers.thresholdsFor(access, id);
            helper.assertTrue(t.equals(rarity.tier().thresholds), id + " thresholds must come from its tier");
            helper.assertTrue(t.loreCatches() <= 50, id + " must complete within 50 catches, got " + t.loreCatches());
        }
        helper.succeed();
    }
}
