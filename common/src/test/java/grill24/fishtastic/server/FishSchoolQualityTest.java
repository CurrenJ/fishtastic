package grill24.fishtastic.server;

import grill24.fishtastic.component.FishQuality;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A school fish's quality is settled by its reward seed: the trail and the catch must agree. */
class FishSchoolQualityTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void aMembersQualityIsTheSameEveryTimeItIsAsked() {
        Random random = new Random(1);
        for (int i = 0; i < 1000; i++) {
            long seed = random.nextLong();
            assertEquals(FishSchoolSavedData.qualityOf(seed), FishSchoolSavedData.qualityOf(seed));
        }
    }

    @Test
    void qualitiesFollowTheOrdinaryOdds() {
        Map<FishQuality.Quality, Integer> counts = new EnumMap<>(FishQuality.Quality.class);
        Random random = new Random(2);
        int n = 200_000;
        for (int i = 0; i < n; i++) counts.merge(FishSchoolSavedData.qualityOf(random.nextLong()), 1, Integer::sum);
        int total = grill24.fishtastic.item.FishtasticFishItem.TOTAL_BASE_WEIGHT;
        for (FishQuality.Quality q : FishQuality.Quality.values()) {
            double expected = (double) grill24.fishtastic.item.FishtasticFishItem.QUALITY_WEIGHTS.get(q) / total;
            double seen = counts.getOrDefault(q, 0) / (double) n;
            assertTrue(Math.abs(seen - expected) < 0.01 + expected * 0.1, q + ": " + seen + " vs " + expected);
        }
    }
}
