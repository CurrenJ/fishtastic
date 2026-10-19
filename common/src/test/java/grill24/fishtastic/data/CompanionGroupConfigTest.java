package grill24.fishtastic.data;

import com.mojang.serialization.JavaOps;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CompanionGroupConfigTest {
    @Test
    void roundTripsSpeciesFormations() {
        for (var formation : FishProfile.CompanionGroupConfig.Formation.values()) {
            var config = new FishProfile.CompanionGroupConfig(3, formation);
            var encoded = FishProfile.CompanionGroupConfig.CODEC.encodeStart(JavaOps.INSTANCE, config);
            assertEquals(config, encoded.flatMap(value -> FishProfile.CompanionGroupConfig.CODEC
                    .parse(JavaOps.INSTANCE, value)).getOrThrow(IllegalStateException::new));
        }
    }

    @Test
    void rejectsInvalidCountsAndFormationNames() {
        for (int count : new int[]{0, -1, 4, 100}) {
            assertTrue(FishProfile.CompanionGroupConfig.CODEC.parse(JavaOps.INSTANCE,
                    Map.of("count", count, "formation", "line")).error().isPresent());
        }
        assertTrue(FishProfile.CompanionGroupConfig.CODEC.parse(JavaOps.INSTANCE,
                Map.of("count", 3, "formation", "school")).error().isPresent());
    }

    @Test
    void parsesGroundGaitsAndRejectsUnknownOnes() {
        assertEquals(FishProfile.CompanionGait.CRAWL,
                FishProfile.CompanionGait.CODEC.parse(JavaOps.INSTANCE, "crawl").getOrThrow(IllegalStateException::new));
        assertEquals(FishProfile.CompanionGait.RIPPLE,
                FishProfile.CompanionGait.CODEC.parse(JavaOps.INSTANCE, "ripple").getOrThrow(IllegalStateException::new));
        assertTrue(FishProfile.CompanionGait.CODEC.parse(JavaOps.INSTANCE, "hop").error().isPresent());
    }

    @Test
    void parsesDriftStylesAndRejectsUnknownOnes() {
        for (var style : FishProfile.CompanionDrift.values()) {
            assertEquals(style, FishProfile.CompanionDrift.CODEC.parse(JavaOps.INSTANCE, style.getSerializedName())
                    .getOrThrow(IllegalStateException::new));
            // The sim's styles are the same names, mapped by name in ClientCompanion.
            assertEquals(style.name(), grill24.fishsim.companion.DriftStyle.valueOf(style.name()).name());
        }
        assertTrue(FishProfile.CompanionDrift.CODEC.parse(JavaOps.INSTANCE, "sail").error().isPresent());
    }
}
