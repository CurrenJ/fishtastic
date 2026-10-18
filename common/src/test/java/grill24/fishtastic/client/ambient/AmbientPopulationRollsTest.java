package grill24.fishtastic.client.ambient;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AmbientPopulationRollsTest {
    @Test void briefReentryUsesTheSameChoiceAndOriginalExpiry() {
        var rolls = new AmbientPopulationRolls();
        AtomicInteger draws = new AtomicInteger();
        var first = rolls.choose(0, 1, 0, s -> true, () -> {
            draws.incrementAndGet();
            return new AmbientPopulationRolls.Choice("rare", 500);
        });
        var back = rolls.choose(0, 1, 200, s -> true, () -> fail("must not reroll"));
        assertSame(first, back);
        assertEquals(500, back.expiresTick());
        assertEquals(1, draws.get());
    }

    @Test void togglingPoolsCannotRerollEitherPoolOrReturnAnExcludedSpecies() {
        var rolls = new AmbientPopulationRolls();
        var a = rolls.choose(0, 1, 0, "a"::equals, () -> new AmbientPopulationRolls.Choice("a", 500));
        var b = rolls.choose(0, 2, 10, "b"::equals, () -> new AmbientPopulationRolls.Choice("b", 510));
        for (int tick = 20; tick < 400; tick++) {
            assertSame(a, rolls.choose(0, 1, tick, "a"::equals, () -> fail("rerolled a")));
            assertSame(b, rolls.choose(0, 2, tick, "b"::equals, () -> fail("rerolled b")));
        }
    }

    @Test void weightOnlyChangesCarryEligibleChoicesAndExpiryAllowsNaturalTurnover() {
        var rolls = new AmbientPopulationRolls();
        var a = rolls.choose(0, 1, 0, s -> true, () -> new AmbientPopulationRolls.Choice("a", 500));
        assertSame(a, rolls.choose(0, 2, 100, s -> true, () -> fail("weight-only reroll")));
        var b = rolls.choose(0, 2, 500, s -> true, () -> new AmbientPopulationRolls.Choice("b", 1000));
        assertEquals("b", b.species());
    }
}
