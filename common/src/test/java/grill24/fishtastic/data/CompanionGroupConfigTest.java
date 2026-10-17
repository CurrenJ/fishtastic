package grill24.fishtastic.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompanionGroupConfigTest {
    @Test
    void roundTripsSpeciesFormations() {
        for (var formation : FishProfile.CompanionGroupConfig.Formation.values()) {
            var config = new FishProfile.CompanionGroupConfig(formation);
            var encoded = FishProfile.CompanionGroupConfig.CODEC.encodeStart(JsonOps.INSTANCE, config);
            assertEquals(config, encoded.flatMap(value -> FishProfile.CompanionGroupConfig.CODEC
                    .parse(JsonOps.INSTANCE, value)).getOrThrow(false, msg -> { throw new IllegalStateException(msg); }));
        }
    }

    @Test
    void rejectsUnknownFormationNames() {
        assertTrue(FishProfile.CompanionGroupConfig.CODEC.parse(JsonOps.INSTANCE,
                unknownFormation()).error().isPresent());
    }

    @Test
    void parsesGroundGaitsAndRejectsUnknownOnes() {
        assertEquals(FishProfile.CompanionGait.CRAWL,
                FishProfile.CompanionGait.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("crawl")).getOrThrow(false, msg -> { throw new IllegalStateException(msg); }));
        assertEquals(FishProfile.CompanionGait.RIPPLE,
                FishProfile.CompanionGait.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("ripple")).getOrThrow(false, msg -> { throw new IllegalStateException(msg); }));
        assertTrue(FishProfile.CompanionGait.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("hop")).error().isPresent());
    }

    @Test
    void parsesDriftStylesAndRejectsUnknownOnes() {
        for (var style : FishProfile.CompanionDrift.values()) {
            assertEquals(style, FishProfile.CompanionDrift.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(style.getSerializedName()))
                    .getOrThrow(false, msg -> { throw new IllegalStateException(msg); }));
            // The sim's styles are the same names, mapped by name in ClientCompanion.
            assertEquals(style.name(), grill24.fishsim.companion.DriftStyle.valueOf(style.name()).name());
        }
        assertTrue(FishProfile.CompanionDrift.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("sail")).error().isPresent());
    }

    private static JsonObject unknownFormation() {
        JsonObject obj = new JsonObject();
        obj.addProperty("formation", "school");
        return obj;
    }
}
