package grill24.fishsim;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.Shelter;
import grill24.fishsim.domain.VoxelDomain;
import grill24.fishsim.harness.Scenarios;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A shelter kept for big fish (docs/fish-shelters.md §12.9): {@code minLength} turns away every
 * fish shorter than it — visitors and lurkers alike — and changes nothing for the fish it admits.
 */
class ShelterMinLengthTest {

    private static final int TICKS = 6_000;

    /** The domain's shelters, each kept for fish at least {@code minLength} long. */
    private static VoxelDomain domain(String name, float minLength) {
        VoxelDomain domain = Scenarios.domain(name);
        List<Shelter> kept = new ArrayList<>();
        for (Shelter s : domain.shelters()) {
            kept.add(new Shelter(s.hull(), s.interior(), s.mouths(), s.capacity(), s.interiorRun(), s.kind(), minLength));
        }
        domain.rebuildShelters(kept);
        return domain;
    }

    /** Fish-ticks spent using a shelter (approach excluded) over the run. */
    private static int useTicks(VoxelDomain domain, FishSpec[] specs, long seed) {
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        engine.rebuild(specs, seed, 0f, 0f, domain);
        int used = 0;
        for (int t = 0; t < TICKS; t++) {
            engine.step();
            for (int i = 0; i < engine.count(); i++) if (engine.shelterUsing(i) >= 0) used++;
        }
        return used;
    }

    private static float shortest(FishSpec[] specs) {
        float min = Float.MAX_VALUE;
        for (FishSpec spec : specs) min = Math.min(min, spec.length());
        return min;
    }

    private static float longest(FishSpec[] specs) {
        float max = 0f;
        for (FishSpec spec : specs) max = Math.max(max, spec.length());
        return max;
    }

    @Test
    void visitorsShorterThanTheFloorPassItBy() {
        FishSpec[] specs = Scenarios.cast("loaches", 8, 12345L);
        assertTrue(useTicks(domain("3x1x1+log", 0f), specs, 12345L) > 0, "the log is visited at all");
        assertEquals(0, useTicks(domain("3x1x1+log", longest(specs) + 0.01f), specs, 12345L));
    }

    @Test
    void aFloorBelowEveryFishChangesNothing() {
        FishSpec[] specs = Scenarios.cast("loaches", 8, 777L);
        assertEquals(useTicks(domain("3x1x1+log", 0f), specs, 777L),
                useTicks(domain("3x1x1+log", shortest(specs) - 0.001f), specs, 777L));
    }

    @Test
    void aLurkerTooSmallClaimsNoHome() {
        FishSpec[] specs = Scenarios.cast("lurker", 6, 31337L);
        assertTrue(useTicks(domain("4x1x2+log", 0f), specs, 31337L) > 0, "the lurker takes the log");
        assertEquals(0, useTicks(domain("4x1x2+log", longest(specs) + 0.01f), specs, 31337L));
    }
}
