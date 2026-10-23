package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import grill24.fishsim.harness.companion.OwnerScript;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The trio's floor (docs/fish-companions.md): every owner script × three seeds × two
 * minutes. The leader is a free swimmer and {@link CompanionInvariantTest} already covers that
 * body; these check what only a school can get wrong — followers in blocks, swimming backwards,
 * passing through each other, or being left behind.
 */
class TrioInvariantTest {

    private static final long[] SEEDS = {1, 2, 3};
    private static final int TICKS = 2400;

    /** Scenes with blocks in the way: a follower may trail further and touch walls, gently. */
    private static final Set<String> OBSTACLE_SCRIPTS = Set.of(
            "corridor", "doorway", "forest", "stairwell", "low_ceiling", "pond_dive", "wading");
    /** A 1-wide tunnel: three fish in single file can't keep clear of each other. */
    private static final Set<String> TUNNEL_SCRIPTS = Set.of("corridor");

    /**
     * Named gap (plan §9.5): sprinting to rejoin through trunks or up a stairwell, a follower bumps
     * a block at up to ~4 b/s (the bound elsewhere is 3).
     */
    private static final Set<String> IMPACT_GAPS = Set.of("forest", "stairwell");

    private record Result(CompanionRun.Metrics leader, CompanionRun.FollowerMetrics followers) {}

    private static Map<String, Result> results;

    private static synchronized Map<String, Result> results() {
        if (results == null) {
            results = new LinkedHashMap<>();
            for (String script : OwnerScript.NAMES) {
                for (long seed : SEEDS) {
                    CompanionRun run = CompanionRun.trio(script, seed, CompanionTunables.defaults(), true);
                    run.run(TICKS);
                    results.put(script + "#" + seed, new Result(run.metrics(), run.followerMetrics()));
                }
            }
        }
        return results;
    }

    private static void all(BiConsumer<String, CompanionRun.FollowerMetrics> check) {
        assertAll(results().entrySet().stream()
                .map(e -> (Executable) () -> check.accept(e.getKey(), e.getValue().followers())));
    }

    private static String script(String key) {
        return key.substring(0, key.indexOf('#'));
    }

    @Test
    void followersNeverInsideABlock() {
        all((key, f) -> assertEquals(0, f.penetrations(), key + " " + f.summary()));
    }

    @Test
    void followersSwimLikeAnyFreeSwimmer() {
        CompanionTunables t = CompanionTunables.defaults();
        double maxAccel = Math.hypot(t.maxLateralAccel, t.maxForce) + 1.5;
        all((key, f) -> {
            assertTrue(f.maxTurnDeg() <= t.turnRateDegPerTick + 1e-3, key + " turn " + f.summary());
            assertTrue(f.maxAccel() <= maxAccel, key + " accel " + f.summary());
            assertTrue(f.maxJerk() <= 160, key + " jerk " + f.summary());
            assertTrue(f.tailFirstFraction() <= 0.005, key + " tail first " + f.summary());
        });
    }

    @Test
    void followersDontPassThroughEachOther() {
        // Measured on the bodies as drawn (long and thin), not as spheres: two fish flank to flank
        // are close, not through each other.
        all((key, f) -> {
            if (TUNNEL_SCRIPTS.contains(script(key))) return;
            assertTrue(f.bodyOverlapFraction() <= 0.015, key + " overlapping " + f.summary());
        });
    }

    @Test
    void followersKeepUp() {
        all((key, f) -> {
            boolean obstacles = OBSTACLE_SCRIPTS.contains(script(key));
            assertTrue(f.longestStraggleSeconds() <= (obstacles ? 15 : 5), key + " straggled " + f.summary());
            if (!obstacles && !Set.of("teleport", "elytra", "creative_flight").contains(script(key))) {
                assertEquals(0, f.leashEvents(), key + " a follower had to fade to rejoin " + f.summary());
            }
            if (obstacles && !TUNNEL_SCRIPTS.contains(script(key))) {
                double perMinute = f.backstops() / (TICKS / 20.0 / 60.0) / 2; // per follower
                assertTrue(perMinute <= 8, key + " " + perMinute + " contacts/min " + f.summary());
                double bound = IMPACT_GAPS.contains(script(key)) ? 5.0 : 3.0;
                assertTrue(f.maxImpact() <= bound, key + " impact " + f.maxImpact() + " " + f.summary());
            }
        });
    }
}
