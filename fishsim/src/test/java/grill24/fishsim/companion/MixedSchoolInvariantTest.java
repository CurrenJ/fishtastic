package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import grill24.fishsim.harness.companion.OwnerScript;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Party-built schools of mixed sizes (docs/fish-companions.md, D8): every owner script ×
 * three seeds × two minutes. Held hard on every run: followers never in a block, finite poses,
 * swimming like any free swimmer, not drawn through each other, keeping up. Obstacle impacts and
 * contacts are held to the identical-member reference's 8-seed envelope (MixedGroupProbe): today's
 * trio itself reaches 6.4 b/s and 13.8 contacts/min in the forest on seeds 4–8, so
 * {@link TrioInvariantTest}'s 3.0–5.0 b/s and 8/min are a three-seed envelope, not a property.
 */
class MixedSchoolInvariantTest {

    static final Set<String> OBSTACLE_SCRIPTS = Set.of(
            "corridor", "doorway", "forest", "stairwell", "low_ceiling", "pond_dive", "wading");
    static final Set<String> TUNNEL_SCRIPTS = Set.of("corridor");
    static final Set<String> IMPACT_GAPS = Set.of("forest", "stairwell");

    /** The identical-member reference's 8-seed worst outside the tunnel, with a margin (MixedGroupProbe). */
    static final double OBSTACLE_IMPACT = 7.0, OBSTACLE_CONTACTS_PER_MIN = 21.0;
    /** Straggle in obstacle scenes: the reference's 8-seed worst is 14.0 s (big 0.40 trio, corridor). */
    static final double OBSTACLE_STRAGGLE = 18.0;

    static final List<float[]> SCHOOLS = List.of(CompanionRun.SCHOOL_MIXED, new float[]{0.24f, 0.16f, 0.09f},
            CompanionRun.SCHOOL_PAIR, CompanionRun.SCHOOL_BIG);

    @Test
    void mixedSchoolsSwimCleanAndKeepUp() {
        CompanionTunables t = CompanionTunables.defaults();
        double maxAccel = Math.hypot(t.maxLateralAccel, t.maxForce) + 1.5;
        List<String> failures = new ArrayList<>();
        for (float[] lengths : SCHOOLS) {
            for (String script : OwnerScript.NAMES) {
                for (long seed = 1; seed <= 3; seed++) {
                    CompanionRun run = CompanionRun.school(script, seed, lengths, Temperament.BASELINE, t, true);
                    run.run(MixedGroupProbe.TICKS);
                    String key = java.util.Arrays.toString(lengths) + " " + script + "#" + seed;
                    CompanionRun.FollowerMetrics f = run.followerMetrics();
                    String s = " " + f.summary();
                    boolean obstacles = OBSTACLE_SCRIPTS.contains(script);
                    if (f.penetrations() != 0) failures.add(key + " follower in a block" + s);
                    if (f.maxTurnDeg() > t.turnRateDegPerTick + 1e-3) failures.add(key + " turn" + s);
                    if (f.maxAccel() > maxAccel) failures.add(key + " accel" + s);
                    if (f.maxJerk() > 160) failures.add(key + " jerk" + s);
                    if (f.tailFirstFraction() > 0.005) failures.add(key + " tail first" + s);
                    if (!TUNNEL_SCRIPTS.contains(script) && f.bodyOverlapFraction() > 0.015)
                        failures.add(key + " drawn through each other" + s);
                    if (f.longestStraggleSeconds() > (obstacles ? OBSTACLE_STRAGGLE : 5)) failures.add(key + " straggled" + s);
                    if (!obstacles && !Set.of("teleport", "elytra", "creative_flight").contains(script) && f.leashEvents() != 0)
                        failures.add(key + " a follower had to fade to rejoin" + s);
                    if (obstacles && !TUNNEL_SCRIPTS.contains(script)) {
                        double perMinute = f.backstops() / (MixedGroupProbe.TICKS / 20.0 / 60.0) / run.group.followerCount();
                        if (perMinute > OBSTACLE_CONTACTS_PER_MIN) failures.add(key + " " + perMinute + " contacts/min" + s);
                        if (f.maxImpact() > OBSTACLE_IMPACT) failures.add(key + " impact" + s);
                    }
                    for (CompanionRun.FollowerSample[] fs : run.followerSamples) {
                        boolean bad = false;
                        for (CompanionRun.FollowerSample p : fs) {
                            bad |= !Double.isFinite(p.pos().x()) || !Double.isFinite(p.pos().y()) || !Double.isFinite(p.pos().z())
                                    || !Float.isFinite(p.yaw()) || !Float.isFinite(p.pitch());
                        }
                        if (bad) {
                            failures.add(key + " non-finite follower pose");
                            break;
                        }
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.size() + " failures:\n" + String.join("\n", failures));
    }
}
