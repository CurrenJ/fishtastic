package grill24.fishtastic.client.ambient;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AmbientBudgetTest {
    @Test void offHasNoWorkAndInvalidValuesClamp() {
        assertEquals(AmbientBudget.forDensity(0), AmbientBudget.forDensity(-1));
        var off = AmbientBudget.forDensity(0);
        assertEquals(0, off.cellsPerTick() + off.columnsPerTick() + off.domains() + off.individuals());
        assertEquals(0, off.target(100));
        assertEquals(AmbientBudget.forDensity(100), AmbientBudget.forDensity(101));
    }

    @Test void everyBudgetIsMonotoneAndInsideWireAndSimulationBounds() {
        var previous = AmbientBudget.forDensity(0);
        for (int d = 1; d <= 100; d++) {
            var b = AmbientBudget.forDensity(d);
            assertTrue(b.radius() >= previous.radius() && b.radius() <= 24);
            assertTrue(b.cellsPerTick() >= previous.cellsPerTick() && b.cellsPerTick() <= 512);
            assertTrue(b.columnsPerTick() >= previous.columnsPerTick() && b.columnsPerTick() <= 48);
            assertTrue(b.domains() >= previous.domains() && b.domains() <= 4);
            assertTrue(b.individuals() >= previous.individuals() && b.individuals() <= 64);
            previous = b;
        }
    }

    @Test void tinyWaterHasFewerFishAndDensityScalesIt() {
        assertEquals(0, AmbientBudget.forDensity(100).target(0));
        assertEquals(0, AmbientBudget.forDensity(100).target(Double.NaN));
        assertEquals(1, AmbientBudget.forDensity(100).target(6));
        assertEquals(8, AmbientBudget.forDensity(100).target(100));
        assertEquals(4, AmbientBudget.forDensity(50).target(100));
        assertEquals(64, AmbientBudget.forDensity(100).target(10000));
        assertEquals(32, AmbientBudget.forDensity(50).target(10000));
    }
}
