package grill24.fishtastic.client.ambient;

import grill24.fishsim.ambient.WaterDiscovery;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AmbientPatchSelectionTest {
    private WaterDiscovery.Patch patch(long key, int seedX, int size) {
        return new WaterDiscovery.Patch(key, seedX, 0, seedX, 0, 0, size, 0, 0);
    }
    private WaterDiscovery.Column column(long key, int x) {
        return new WaterDiscovery.Column(x, 0, null, key, 0);
    }

    @Test void largeNearbyWaterIsRankedByItsWaterRatherThanADistantSeed() {
        var ocean = patch(1, -20, 1000);
        var puddle = patch(2, 2, 4);
        var chosen = AmbientPatchSelection.select(List.of(puddle, ocean),
                List.of(column(1, 0), column(2, 2)), Set.of(), 0, 0, 1);
        assertEquals(List.of(ocean), chosen.patches());
        assertEquals(0, chosen.distances().get(1L));
    }

    @Test void ASmallStepDoesNotEvictANearbyOccupiedPatchForAnEmptyOne() {
        var occupied = patch(1, -10, 20);
        var empty = patch(2, 1, 4);
        var water = List.of(column(1, -2), column(2, 1));
        for (int player = 0; player <= 3; player++) {
            assertEquals(List.of(occupied), AmbientPatchSelection.select(List.of(empty, occupied),
                    water, Set.of(1L), player, 0, 1).patches());
        }
        assertEquals(List.of(empty), AmbientPatchSelection.select(List.of(empty, occupied),
                water, Set.of(1L), 9, 0, 1).patches(), "distant water releases the slot");
    }
}
