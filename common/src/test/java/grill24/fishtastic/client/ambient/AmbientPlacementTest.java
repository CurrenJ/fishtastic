package grill24.fishtastic.client.ambient;

import grill24.fishsim.core.Locomotion;
import grill24.fishsim.domain.WaterDomain;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AmbientPlacementTest {
    @Test void anchoredSpawnStandsOnTheBedIncludingSlabHeights() {
        for (float bed : new float[]{0, 0.5f, 1.5f}) {
            var columns = new java.util.ArrayList<WaterDomain.Column>();
            for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
                columns.add(new WaterDomain.Column(x, z, new float[]{bed, 4}, false));
            var domain = WaterDomain.build(columns, 0, 0, 0, 0.3f, null);
            float y = AmbientPlacement.height(Locomotion.ANCHORED, domain.bedAt(1.5f, 1.5f),
                    bed + 0.02f, 3, 0.8f);
            assertEquals(bed, y);
            assertTrue(domain.bodyFits(1.5f, y, 1.5f, new WaterDomain.BodyBox(0.2f, 0.4f, 0)));
        }
    }

    @Test void swimmersStillUseTheWaterBand() {
        assertEquals(2, AmbientPlacement.height(Locomotion.FREE_SWIM, 0, 1, 3, 0.5f));
    }
}
