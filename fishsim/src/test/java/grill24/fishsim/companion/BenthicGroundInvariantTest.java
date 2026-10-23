package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import grill24.fishsim.harness.companion.OwnerScript;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Benthics on the ground (docs/fish-companions.md, Phase 1): the ground scripts
 * ({@link OwnerScript#GROUND_NAMES}) × three seeds × two minutes, for a crab (crawl, 0.3 blocks),
 * a plaice (ripple, 0.35), a sea-slug-sized crawler (0.12) and a spider-crab-sized one (0.6). The
 * swimmers' table ({@link CompanionInvariantTest}) runs BENTHIC on its own scripts too.
 *
 * <p>Like that table these guard specific failures; whether it reads as a creature with somewhere
 * to be is the viewer's and the owner's call.
 */
class BenthicGroundInvariantTest {

    private static final long[] SEEDS = {1, 2, 3};
    private static final long[] SPIDER_SEEDS = {1, 2, 3, 4, 5, 6, 7, 8};
    private static final int TICKS = 2400;

    /** A body to run: its gait and length, and the scripts it runs. */
    private record Config(String name, GroundGait gait, float length, List<String> scripts) {}

    private static final List<Config> CONFIGS = List.of(
            new Config("crab", GroundGait.CRAWL, 0.3f, OwnerScript.GROUND_NAMES),
            new Config("plaice", GroundGait.RIPPLE, 0.35f,
                    List.of("plaice_walk_flat", "walk_flat", "walk_meander", "stairs_up_down", "step_gaps", "parkour_gap")),
            new Config("slug", GroundGait.CRAWL, 0.12f, List.of("walk_flat", "walk_meander", "stairs_up_down", "step_gaps")),
            // Eight seeds guard the former corner stalls and hard perch-hop touchdowns on terrain.
            new Config("spider", GroundGait.CRAWL, 0.6f,
                    List.of("walk_flat", "walk_meander", "sprint_away", "stairs_up_down", "step_gaps")));

    /** Followed on foot the whole way: no fade onto the perch. */
    private static final Set<String> NO_FADE = Set.of(
            "walk_flat", "walk_meander", "stairs_up_down", "step_gaps", "plaice_walk_flat");
    /**
     * The crab and the spider crab keep up with a sprint; the slug and the plaice are slower than
     * a sprint (spec §4, gait by size) and fall back on the fade, as the owner chose (spec §6).
     */
    private static final Set<String> FAST_ENOUGH_FOR_SPRINT = Set.of("crab", "spider");
    /** Somewhere it can't follow on foot (up a ladder): it must fade onto the perch. */
    private static final Set<String> MUST_FADE = Set.of("ladder");
    /**
     * Named Phase 1 gaps, one fade allowed: the walk is greedy (straight lines and the owner's
     * crumbs, no pathfinding), so on a 3-wide boardwalk with block-and-a-half sides it can end up
     * beside the high section with no way up (measured 2 of 8 seeds); a running jump over the
     * trench it can't hop back across (1 of 8); a door shut with it on the far side (2 of 8, the
     * spec's fallback working as meant).
     */
    private static final Set<String> ONE_FADE_ALLOWED = Set.of("slab_path", "parkour_gap", "closed_door");
    /**
     * Named Phase 1 gap: a sea-slug-sized crawler on the terraces meets a riser it braked for too
     * late now and then — 1 run in 8, up to 4.5 b/s. The bound there is 5.
     */
    private static final Set<String> SLUG_TERRACE_CONTACT = Set.of("slug/step_gaps");

    /** Scripts where the owner rides or glides off: it climbs on or fades onto the perch (spec §6). */
    private static final Set<String> MOUNTED = Set.of("ride_off", "elytra_glide");

    private record Result(CompanionRun.Metrics m, CompanionRun.GroundMetrics g, int longestLeftOnGround) {}

    private static Map<String, Result> results;

    private static synchronized Map<String, Result> results() {
        if (results == null) {
            results = new LinkedHashMap<>();
            for (Config c : CONFIGS) {
                for (String script : c.scripts()) {
                    for (long seed : c.name().equals("spider") ? SPIDER_SEEDS : SEEDS) {
                        CompanionRun run = CompanionRun.benthic(script, seed, c.length(), Temperament.BASELINE, c.gait(),
                                CompanionTunables.defaults(), true);
                        run.run(TICKS);
                        results.put(c.name() + "/" + script + "#" + seed,
                                new Result(run.metrics(), run.groundMetrics(), leftOnGround(run)));
                    }
                }
            }
        }
        return results;
    }

    /** The longest run of ticks it was still walking the ground while its owner rode or glided. */
    private static int leftOnGround(CompanionRun run) {
        int run0 = 0, longest = 0;
        for (CompanionRun.Sample s : run.samples) {
            boolean away = s.owner().riding() || s.owner().gliding();
            run0 = away && s.onGround() ? run0 + 1 : 0;
            longest = Math.max(longest, run0);
        }
        return longest;
    }

    private static void all(BiConsumer<String, Result> check) {
        List<Executable> checks = new ArrayList<>();
        results().forEach((k, r) -> checks.add(() -> check.accept(k, r)));
        assertAll(checks);
    }

    private static String script(String key) {
        return key.substring(key.indexOf('/') + 1, key.indexOf('#'));
    }

    private static String config(String key) {
        return key.substring(0, key.indexOf('/'));
    }

    private static String why(String key, Result r) {
        return key + " " + r.g().summary() + " | " + r.m().summary();
    }

    // ── Blocks and the floor ───────────────────────────────────────────────────────────────────

    @Test
    void idleClimbAndTravelDismount() {
        all((k, r) -> {
            String s = script(k);
            if (s.equals("mine_in_place") || s.equals("pace_chest")) {
                assertEquals(1, r.g().climbs(), "idle should settle onto the perch: " + why(k, r));
                assertEquals(0, r.g().dismounts(), "local activity should stay perched: " + why(k, r));
            }
            if (s.equals("long_idle_then_walk")) {
                assertTrue(r.g().climbs() >= 1, "never climbed: " + why(k, r));
                assertTrue(r.g().dismounts() >= 1, "never dismounted: " + why(k, r));
                assertTrue(r.g().firstClimbTick() >= 600 && r.g().firstClimbTick() < 1200,
                        "climb should follow sustained rest: " + why(k, r));
            }
        });
    }

    @Test
    void joltsTumbleOffThePerch() {
        all((k, r) -> {
            if (Set.of("hurt_while_perched", "fall_landing").contains(script(k))) {
                assertTrue(r.g().tumbles() >= 1, "never tumbled: " + why(k, r));
            } else if (Set.of("mine_in_place", "pace_chest", "long_idle_then_walk").contains(script(k))) {
                assertEquals(0, r.g().tumbles(), "ordinary activity is a dismount, not a tumble: " + why(k, r));
            }
        });
    }

    @Test
    void tumbleCooldownAndMountProtection() {
        CompanionRun run = CompanionRun.benthic("long_idle_then_walk", 1, 0.3f, Temperament.BASELINE,
                GroundGait.CRAWL, CompanionTunables.defaults(), true);
        run.run(900);
        CompanionAgent a = run.agent;
        OwnerState o = run.owner;
        assertTrue(a.riding(), "initial idle should climb");
        OwnerState mounted = new OwnerState(o.eye(), Vec.ZERO, o.yawDeg(), o.pitchDeg(), false, false,
                true, o.fovDeg(), false, true, false, 5);
        a.step(new CompanionWorld(mounted, run.scene, List.of(), CompanionWorld.Held.NONE, true));
        assertEquals(0, a.tumbles(), "a rider stays aboard even on a jolt");
        OwnerState shortFall = new OwnerState(o.eye(), Vec.ZERO, o.yawDeg(), o.pitchDeg(), false, false,
                true, o.fovDeg(), false, false, false, 2.9);
        a.step(CompanionWorld.of(shortFall, run.scene));
        assertEquals(0, a.tumbles(), "a short fall is not a hard landing");
        a.step(new CompanionWorld(o, run.scene, List.of(), CompanionWorld.Held.NONE, true));
        assertEquals(1, a.tumbles());
        a.devPerch();
        for (int i = 0; i < 190; i++) {
            a.step(new CompanionWorld(o, run.scene, List.of(), CompanionWorld.Held.NONE, i % 20 == 0));
        }
        assertTrue(a.riding(), "it should regain its perch during cooldown: " + a.landMode()
                + " " + a.position() + " close=" + a.comingClose() + " climbs=" + a.climbs());
        assertEquals(1, a.tumbles(), "repeated hits must not bounce it straight back off");
        for (int i = 0; i < 10; i++) a.step(CompanionWorld.of(o, run.scene));
        a.step(new CompanionWorld(o, run.scene, List.of(), CompanionWorld.Held.NONE, true));
        assertEquals(2, a.tumbles(), "another jolt can tumble it once cooldown expires");
    }

    @Test
    void neverInsideABlock() {
        all((k, r) -> assertEquals(0, r.m().solidPenetrations(), why(k, r)));
    }

    @Test
    void feetOnTheFloorOutsideHops() {
        // Walking, crouching or landing, its underside is on the floor under it — never floating,
        // never sunk into a slab or a carpet.
        all((k, r) -> assertEquals(0, r.g().floorErrorTicks(), why(k, r)));
    }

    @Test
    void blocksMetRarelyAndGently() {
        all((k, r) -> {
            double perMinute = r.m().backstops() / (r.m().ticks() / 20.0 / 60.0);
            assertTrue(perMinute <= 8, k + " " + perMinute + " contacts/min " + why(k, r));
            double bound = SLUG_TERRACE_CONTACT.contains(config(k) + "/" + script(k)) ? 5.0 : 4.0;
            assertTrue(r.m().maxImpact() <= bound, k + " impact " + why(k, r));
        });
    }

    // ── The body ───────────────────────────────────────────────────────────────────────────────

    @Test
    void bodyStaysInsideItsLimits() {
        CompanionTunables t = CompanionTunables.defaults();
        double swimAccel = Math.hypot(t.maxLateralAccel, t.maxForce) + 1.5;
        double hopAccel = PerchBody.GRAVITY + PerchBody.AIR_CONTROL + 2;
        all((k, r) -> {
            assertTrue(r.m().maxTurnDeg() <= t.turnRateDegPerTick + 1e-3, k + " turn " + why(k, r));
            // Launches and touchdowns (and steps onto a carpet) are impulses, counted apart.
            assertTrue(r.m().maxAccel() <= Math.max(swimAccel, hopAccel), k + " accel " + why(k, r));
            assertTrue(r.m().maxJerk() <= 800, k + " jerk " + why(k, r));
            // A leap onto a moving owner's hand is the biggest: measured up to ~14 b/s.
            assertTrue(r.g().maxImpulse() <= 16, k + " impulse " + why(k, r));
        });
    }

    @Test
    void walksHeadFirst() {
        all((k, r) -> {
            assertTrue(r.m().tailFirstFraction() <= 0.005, k + " tail first " + why(k, r));
            assertTrue(r.m().maxFacingErrorDeg() < 1.0, k + " crabbing " + why(k, r));
        });
    }

    // ── Keeping up ─────────────────────────────────────────────────────────────────────────────

    @Test
    void neverStuckLong() {
        // Stuck: on the ground, far from its owner and not moving. The fallback (spec §6) fades it
        // onto its perch after 3 s with no way on.
        all((k, r) -> assertTrue(r.g().longestStuckSeconds() <= 3.5, k + " stuck " + why(k, r)));
    }

    @Test
    void fadesOnlyWhereItCantFollow() {
        all((k, r) -> {
            String s = script(k);
            long fades = r.g().fades();
            // With hand visits rare (HandClock) a walker on the stairs rides up less; the owner
            // accepted one fade there (2026-10-14).
            if (s.equals("stairs_up_down")) {
                assertTrue(fades <= 1, k + " faded more than once where it could follow " + why(k, r));
            } else if (NO_FADE.contains(s) || (s.equals("sprint_away") && FAST_ENOUGH_FOR_SPRINT.contains(config(k)))) {
                assertEquals(0, fades, k + " faded where it could follow " + why(k, r));
            } else if (MUST_FADE.contains(s)) {
                assertTrue(fades >= 1, k + " should have faded onto its perch " + why(k, r));
            } else if (ONE_FADE_ALLOWED.contains(s)) {
                assertTrue(fades <= 1, k + " " + why(k, r));
            }
        });
    }

    @Test
    void ridesWhenTheOwnerRidesOrGlides() {
        // Spec §6 (owner): no chase after a horse or elytra — it climbs on as they go, or fades
        // onto the perch within the 1.5 s it waits for the hop.
        all((k, r) -> {
            if (!MOUNTED.contains(script(k))) return;
            assertTrue(r.longestLeftOnGround() <= 40, k + " left on the ground " + r.longestLeftOnGround() + " ticks " + why(k, r));
        });
    }

    @Test
    void keepsUpOnAWalk() {
        all((k, r) -> {
            if (!script(k).equals("walk_flat") || !config(k).equals("crab")) return;
            assertTrue(r.g().maxLag() <= 8, k + " fell behind " + why(k, r));
        });
    }

    // ── The owner ──────────────────────────────────────────────────────────────────────────────

    @Test
    void staysOutFromUnderfoot() {
        all((k, r) -> {
            double inside = r.m().insideOwnerTicks() / (double) r.m().ticks();
            double clip = r.m().nearClipTicks() / (double) r.m().ticks();
            assertTrue(inside <= 0.01, k + " underfoot " + inside + " " + why(k, r));
            assertTrue(clip <= 0.01, k + " near clip " + clip + " " + why(k, r));
        });
    }

    @Test
    void ownerSeesItRegularly() {
        all((k, r) -> {
            assertTrue(r.m().longestUnseenSeconds() <= CompanionInvariantTest.PERCHER_UNSEEN_SECONDS,
                    k + " unseen " + why(k, r));
            assertTrue(r.m().seenFraction() >= CompanionInvariantTest.PERCHER_SEEN_FLOOR, k + " barely seen " + why(k, r));
        });
    }

    @Test
    void goalsAreCommittedTo() {
        all((k, r) -> {
            int shortest = r.m().shortestNonEmergencyGoalTicks();
            assertTrue(shortest < 0 || shortest >= 20, k + " dithered " + why(k, r));
        });
    }
}
