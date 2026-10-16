package grill24.fishsim.ambient;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Equal-water comparison of the former snapshot path and cached nearest-water distances. */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class WaterDiscoveryCostProbe {
    private static volatile Object sink;

    private static Map<Long, Integer> snapshotDistances(WaterDiscovery discovery, int x, int z) {
        Map<Long, Integer> out = new HashMap<>();
        for (var column : discovery.columns()) {
            int dx = column.x() - x, dz = column.z() - z;
            out.merge(column.patchKey(), dx * dx + dz * dz, Math::min);
        }
        return out;
    }

    @Test
    void settledOceanDistances() {
        SyntheticWater ocean = new SyntheticWater(-24, 24, -24, 24);
        for (int x = -24; x <= 24; x++) for (int z = -24; z <= 24; z++) ocean.water(x, z, 64, 3);
        WaterDiscovery d = new WaterDiscovery();
        d.configure(24, 8, 16, 48, 512);
        for (int tick = 0; tick < 2000; tick++) d.tick(ocean, 0, 65, 0, tick);
        assertEquals(2401, d.columnCount());
        for (int x = -3; x <= 3; x++) assertEquals(snapshotDistances(d, x, 0), d.nearestSquaredDistances(x, 0));
        for (int i = 0; i < 2000; i++) {
            sink = snapshotDistances(d, 0, 0);
            sink = d.nearestSquaredDistances(0, 0);
        }
        int calls = 10000;
        long start = System.nanoTime();
        for (int i = 0; i < calls; i++) sink = snapshotDistances(d, 0, 0);
        long oldNanos = System.nanoTime() - start;
        start = System.nanoTime();
        for (int i = 0; i < calls; i++) sink = d.nearestSquaredDistances(0, 0);
        long newNanos = System.nanoTime() - start;
        System.out.printf(Locale.ROOT, "columns=%d calls=%d snapshotMeanUs=%.3f cachedMeanUs=%.3f equal=true%n",
                d.columnCount(), calls, oldNanos / (1000.0 * calls), newNanos / (1000.0 * calls));
    }
}
