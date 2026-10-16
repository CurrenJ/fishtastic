package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;
import grill24.fishsim.harness.companion.CompanionRun;
import grill24.fishsim.harness.companion.MoodScenes;
import grill24.fishsim.harness.companion.OwnerScript;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 5 moods (docs/fish-companions-sim-plan.md §5, spec §6): the rules that read the ambient
 * against a species' habitat, the reactions they show, and the owner's bar that they stay subtle:
 * seen % and fades no worse than without them on equal seeds.
 */
class MoodTest {

    private static final Vec CHEST = new Vec(0, 65.4, 0);
    private static final Habitat OCEAN = MoodPresets.OCEAN;
    private static final Ambient PLAINS = MoodPresets.PLAINS;

    /** {@code seconds} of updates at one ambient. */
    private static Mood feel(Habitat h, Ambient a, double seconds) {
        Mood m = new Mood();
        for (int i = 0; i < seconds * 20; i++) m.update(a, h, CHEST, CHEST.add(1, 0, 1));
        return m;
    }

    private static Habitat habitat(Habitat.Climate climate, Map<Ambient.Weather, Float> weather,
                                   Map<Ambient.DayPart, Float> hours, float fullMoon, String... homes) {
        return new Habitat(Set.of(homes), weather, hours, fullMoon, climate);
    }

    private static void assertNeutral(Mood m) {
        assertFalse(m.any(), m.describe());
        assertEquals(1f, m.pace);
        assertEquals(1f, m.rest);
        assertEquals(1f, m.dwell);
        assertEquals(1f, m.jitter);
        assertEquals(1f, m.band);
        assertEquals(1f, m.curiosity);
        assertEquals(0f, m.stillness);
        assertEquals(0f, m.lift);
    }

    // ── The rules ──────────────────────────────────────────────────────────────────────────────

    @Test
    void noAmbientOrNoHabitatIsNoMood() {
        assertNeutral(feel(OCEAN, Ambient.NEUTRAL, 30));
        assertNeutral(feel(Habitat.NONE, PLAINS.withPlace(Set.of("minecraft:snowy_plains"), 0f)
                .withWeather(Ambient.Weather.SNOW).withTime(Ambient.DayPart.NIGHT, 0), 30));
        // A plain ocean fish on a temperate day: nothing to feel.
        assertNeutral(feel(OCEAN, PLAINS, 30));
    }

    @Test
    void comfortRespectsTheSpeciesHome() {
        Ambient snow = PLAINS.withPlace(Set.of("minecraft:snowy_plains", "#minecraft:is_snowy"), 0f);
        Ambient desert = PLAINS.withPlace(Set.of("minecraft:desert"), 2f);
        Ambient nether = PLAINS.withPlace(Set.of("minecraft:nether_wastes", "zone:nether"), 2f);
        assertTrue(feel(OCEAN, snow, 10).shivering(), "a temperate fish shivers in snow");
        assertEquals(0f, feel(habitat(Habitat.Climate.TEMPERATE, Map.of(), Map.of(), 1, "#minecraft:is_snowy"), snow, 10).cold,
                "at home nothing grates");
        assertEquals(0f, feel(habitat(Habitat.Climate.COLD, Map.of(), Map.of(), 1, "zone:ocean"), snow, 10).cold,
                "a cold-water fish is fine in snow");
        assertTrue(feel(habitat(Habitat.Climate.COLD, Map.of(), Map.of(), 1, "zone:ocean"), desert, 10).glum > 0.5f);
        assertTrue(feel(OCEAN, desert, 10).glum > 0.5f, "a temperate fish wilts in the heat");
        Habitat lava = habitat(Habitat.Climate.HOT, Map.of(), Map.of(), 1, "zone:nether");
        assertNeutral(feel(lava, nether, 10));
        assertTrue(feel(lava, PLAINS, 10).glum > 0.4f, "a Nether fish is glum out of the heat");
        assertTrue(feel(lava, snow, 10).shivering());
    }

    @Test
    void warmthWarmsAColdFish() {
        Ambient snow = PLAINS.withPlace(Set.of("minecraft:snowy_plains"), 0f);
        Mood away = feel(OCEAN, snow.withWarmth(List.of(CHEST.add(8, 0, 0))), 10);
        assertTrue(away.shivering() && away.wantsWarmth, away.describe());
        Mood by = feel(OCEAN, snow.withWarmth(List.of(CHEST.add(1.5, -0.8, 0))), 10);
        assertEquals(0f, by.cold, "its owner by a fire keeps it warm");
        assertFalse(by.wantsWarmth);
        Mood lava = feel(habitat(Habitat.Climate.HOT, Map.of(), Map.of(), 1, "zone:nether"),
                PLAINS.withWarmth(List.of(CHEST.add(1.5, -0.8, 0))), 10);
        assertEquals(0f, lava.glum, "a Nether fish cheers up by a fire");
        assertTrue(lava.glee > 0.4f);
    }

    @Test
    void rainGladdensMostFish() {
        Ambient rain = PLAINS.withWeather(Ambient.Weather.RAIN);
        assertTrue(feel(OCEAN, rain, 10).glee > 0.6f, "an indifferent fish is glad of rain");
        assertTrue(feel(habitat(Habitat.Climate.TEMPERATE, Map.of(Ambient.Weather.RAIN, 1.5f), Map.of(), 1, "zone:ocean"),
                rain, 10).glee > 0.95f);
        Mood dislikes = feel(habitat(Habitat.Climate.TEMPERATE, Map.of(Ambient.Weather.RAIN, 0.5f), Map.of(), 1, "zone:ocean"), rain, 10);
        assertEquals(0f, dislikes.glee);
        assertTrue(dislikes.glum > 0.4f, "a fish whose profile dislikes rain sulks");
        assertTrue(feel(habitat(Habitat.Climate.HOT, Map.of(), Map.of(), 1, "zone:nether"),
                rain.withPlace(Set.of("zone:nether"), 2f), 10).glum > 0.4f, "rain is no joy to a Nether fish");
        Mood coldRain = feel(OCEAN, rain.withPlace(Set.of("minecraft:snowy_plains"), 0f).withWeather(Ambient.Weather.SNOW), 10);
        assertEquals(0f, coldRain.glee, "shivering beats glad");
        assertTrue(feel(habitat(Habitat.Climate.COLD, Map.of(), Map.of(), 1, "zone:ocean"),
                PLAINS.withPlace(Set.of("minecraft:snowy_plains"), 0f).withWeather(Ambient.Weather.SNOW), 10).glee > 0.6f,
                "a cold-water fish takes snow as rain");
    }

    @Test
    void sleepyInItsOffHoursAndMoonstruckUnderAFullMoon() {
        Habitat nocturnal = habitat(Habitat.Climate.TEMPERATE, Map.of(), Map.of(Ambient.DayPart.NIGHT, 1.5f), 1, "zone:ocean");
        assertTrue(feel(nocturnal, PLAINS.withTime(Ambient.DayPart.DAY, 3), 10).sleepy > 0.95f);
        assertEquals(0f, feel(nocturnal, PLAINS.withTime(Ambient.DayPart.NIGHT, 3), 10).sleepy);
        Habitat dayShy = habitat(Habitat.Climate.TEMPERATE, Map.of(), Map.of(Ambient.DayPart.DAY, 0.1f), 1, "zone:ocean");
        assertTrue(feel(dayShy, PLAINS.withTime(Ambient.DayPart.DAY, 3), 10).sleepy > 0.95f, "a weight under 1 is an off-hour");
        assertEquals(0f, feel(OCEAN, PLAINS.withTime(Ambient.DayPart.NIGHT, 3), 10).sleepy, "no hours, never sleepy");

        Habitat lunar = habitat(Habitat.Climate.TEMPERATE, Map.of(), Map.of(), 2f, "zone:ocean");
        assertTrue(feel(lunar, PLAINS.withTime(Ambient.DayPart.NIGHT, 0), 10).moonstruck());
        assertEquals(0f, feel(lunar, PLAINS.withTime(Ambient.DayPart.NIGHT, 4), 10).moon, "not a full moon");
        assertEquals(0f, feel(lunar, PLAINS.withTime(Ambient.DayPart.DAY, 0), 10).moon, "not at night");
        assertEquals(0f, feel(OCEAN, PLAINS.withTime(Ambient.DayPart.NIGHT, 0), 10).moon, "not a full-moon species");
    }

    @Test
    void moodsEaseInAndOut() {
        Ambient snow = PLAINS.withPlace(Set.of("minecraft:snowy_plains"), 0f);
        Mood m = feel(OCEAN, snow, 1);
        assertTrue(m.cold > 0 && m.cold < 0.3f, "a second in, it's only starting to feel it: " + m.cold);
        for (int i = 0; i < 20 * Mood.EASE_IN_SECONDS; i++) m.update(snow, OCEAN, CHEST, CHEST);
        assertTrue(m.cold >= 0.8f - 1e-6f);
        for (int i = 0; i < 20; i++) m.update(PLAINS, OCEAN, CHEST, CHEST);
        assertTrue(m.cold > 0.5f, "a second after the snow, still cold: " + m.cold);
        for (int i = 0; i < 20 * Mood.EASE_OUT_SECONDS; i++) m.update(PLAINS, OCEAN, CHEST, CHEST);
        assertNeutral(m);
    }

    @Test
    void everyLeverIsCapped() {
        Mood m = new Mood();
        for (float c : new float[]{0, 1}) for (float g : new float[]{0, 1}) for (float j : new float[]{0, 1})
            for (float s : new float[]{0, 1}) {
                m.cold = c; m.glum = g; m.glee = j; m.sleepy = s; m.moon = 1;
                m.update(Ambient.NEUTRAL, Habitat.NONE, CHEST, CHEST); // eases toward 0 by one tick; the levers follow
                String at = m.describe();
                assertTrue(m.pace >= 0.6f && m.pace <= 1.15f, at + " pace " + m.pace);
                assertTrue(m.rest >= 0.55f && m.rest <= 2.01f, at + " rest " + m.rest);
                assertTrue(m.dwell >= 0.75f && m.dwell <= 1.85f, at + " dwell " + m.dwell);
                assertTrue(m.jitter >= 1f && m.jitter <= 1.65f, at + " jitter " + m.jitter);
                assertTrue(m.band >= 0.75f && m.band <= 1.11f, at + " band " + m.band);
                assertTrue(m.curiosity >= 0.25f && m.curiosity <= 1.21f, at + " curiosity " + m.curiosity);
                assertTrue(m.stillness <= 0.41f && m.lift <= 0.51f, at);
            }
    }

    // ── Each fish its own mood ─────────────────────────────────────────────────────────────────

    @Test
    void aMixedPartyCanBeSplit() {
        OwnerScript script = OwnerScript.named("idle_looking", 1);
        script.ambient = PLAINS.withPlace(Set.of("minecraft:snowy_plains"), 0f);
        CompanionWorld w = script.nextWorld();
        CompanionAgent ocean = new CompanionAgent(CompanionTunables.defaults(), 1, Locomotion.FREE_SWIM, 0.4f, w);
        CompanionAgent icy = new CompanionAgent(CompanionTunables.defaults(), 2, Locomotion.FREE_SWIM, 0.4f, w);
        ocean.habitat(OCEAN);
        icy.habitat(new Habitat(Set.of("zone:ocean"), Map.of(Ambient.Weather.SNOW, 1.5f), Map.of(), 1, Habitat.Climate.COLD));
        int oceanShiver = 0, icyShiver = 0;
        for (int i = 0; i < 600; i++) {
            w = script.nextWorld();
            ocean.step(w);
            icy.step(w);
            if (ocean.poseHint() == PoseHint.SHIVER) oceanShiver++;
            if (icy.poseHint() == PoseHint.SHIVER) icyShiver++;
        }
        assertTrue(ocean.mood().cold() > 0.5f && oceanShiver > 200, "the ocean fish shivers: " + ocean.moodDescription());
        assertEquals(0f, icy.mood().cold(), "its cold-water party-mate is content");
        assertEquals(0, icyShiver);
    }

    /**
     * A mixed shoal (party D13) shares one mind, so its motion is the leader's; but each member
     * feels its own species' home and shows it in its pose: split either way round.
     */
    @Test
    void aMixedShoalIsSplitInItsPoses() {
        Habitat icy = new Habitat(Set.of("zone:ocean"), Map.of(Ambient.Weather.SNOW, 1.5f), Map.of(), 1, Habitat.Climate.COLD);
        for (boolean leaderCold : new boolean[]{true, false}) {
            OwnerScript script = OwnerScript.named("idle_looking", 3);
            script.ambient = PLAINS.withPlace(Set.of("minecraft:snowy_plains"), 0f);
            CompanionWorld w = script.nextWorld();
            CompanionGroup school = new CompanionGroup(CompanionTunables.defaults(), 3, new float[]{0.2f, 0.25f, 0.3f},
                    Temperament.BASELINE, w);
            school.leader().habitat(leaderCold ? OCEAN : icy);
            school.follower(0).habitat(leaderCold ? icy : OCEAN);
            school.follower(1).habitat(OCEAN);
            int leaderShiver = 0, icyShiver = 0, oceanShiver = 0;
            for (int i = 0; i < 600; i++) {
                school.step(script.nextWorld());
                PoseHint lead = school.leader().poseFor(school.leader().poseHint());
                PoseHint f0 = school.follower(0).pose(), f1 = school.follower(1).pose();
                if (lead == PoseHint.SHIVER) leaderShiver++;
                if ((leaderCold ? f0 : lead) == PoseHint.SHIVER) icyShiver++;
                if (f1 == PoseHint.SHIVER) oceanShiver++;
            }
            String at = leaderCold ? "ocean leader" : "icy leader";
            assertEquals(0, icyShiver, at + ": the cold-water fish never shivers");
            assertTrue(oceanShiver > 200, at + ": its ocean follower shivers " + oceanShiver);
            if (leaderCold) assertTrue(leaderShiver > 200, at + " shivers " + leaderShiver);
            assertEquals(0f, (leaderCold ? school.follower(0).mood() : school.leader().mood()).cold());
        }
    }

    // ── The reactions, on the idle scripts ────────────────────────────────────────────────────

    private record Watch(int shiver, int moonGaze, int basks, int laps, int restTicks, int firstRest, double speed,
                         double minFireDist, double height) {}

    private static Watch watch(String script, long seed, Locomotion c, String preset) {
        CompanionRun run = new CompanionRun(script, seed, c, CompanionTunables.defaults(), true);
        Vec fire = null;
        if (preset != null) {
            MoodScenes.apply(run, MoodPresets.named(preset), run.owner.feet());
            if (!run.script.ambient.warmth().isEmpty()) fire = run.script.ambient.warmth().get(0);
        }
        int shiver = 0, moon = 0, basks = 0, rest = 0, firstRest = -1;
        double minFire = 99, height = 0, speed = 0;
        boolean basking = false;
        for (int i = 0; i < 2400; i++) {
            run.run(1);
            CompanionAgent a = run.agent;
            if (a.poseHint() == PoseHint.SHIVER) shiver++;
            if (a.poseHint() == PoseHint.MOON_GAZE) moon++;
            if (a.basking() && !basking) basks++;
            basking = a.basking();
            if (a.resting()) rest++;
            if (a.resting() && firstRest < 0) firstRest = i;
            speed += a.velocity().length();
            if (fire != null) minFire = Math.min(minFire, a.position().distanceTo(fire));
            height += a.position().y() - run.owner.chest().y();
        }
        return new Watch(shiver, moon, basks, run.agent.laps(), rest, firstRest, speed / 2400, minFire, height / 2400);
    }

    private static final List<String> IDLE = List.of("idle_fixed", "idle_looking");

    @Test
    void aColdFishShivers() {
        for (String s : IDLE) for (long seed = 1; seed <= 3; seed++) {
            Watch w = watch(s, seed, Locomotion.FREE_SWIM, "cold");
            assertTrue(w.shiver > 2400 * 0.5, s + "#" + seed + " shivered " + w.shiver + " ticks");
        }
    }

    @Test
    void aColdFishGoesToBaskByAFireAndStaysOutOfIt() {
        for (Locomotion c : List.of(Locomotion.FREE_SWIM, Locomotion.DRIFT)) {
            for (long seed = 1; seed <= 3; seed++) {
                Watch w = watch("idle_looking", seed, c, "warm");
                assertTrue(w.basks >= 1, c + "#" + seed + " never basked");
                assertTrue(w.minFireDist >= 0.75, c + "#" + seed + " into the flames: " + w.minFireDist);
                assertTrue(w.shiver < 2400 * 0.25, c + "#" + seed + " shivered on by the fire " + w.shiver);
            }
        }
    }

    @Test
    void aGladFishLapsItsOwner() {
        int laps = 0;
        for (String s : IDLE) for (long seed = 1; seed <= 3; seed++) laps += watch(s, seed, Locomotion.FREE_SWIM, "glee").laps;
        assertTrue(laps >= 6, "laps " + laps);
        for (String s : IDLE) for (long seed = 1; seed <= 3; seed++) assertEquals(0, watch(s, seed, Locomotion.FREE_SWIM, null).laps);
    }

    /** Sleepy: it settles sooner once its owner stands about, and idles slower. */
    @Test
    void aSleepyFishSettlesSoonerAndShowsItsDrowsiness() {
        for (String s : IDLE) for (long seed = 1; seed <= 3; seed++) {
            Watch sleepy = watch(s, seed, Locomotion.FREE_SWIM, "sleepy");
            Watch base = watch(s, seed, Locomotion.FREE_SWIM, null);
            String at = s + "#" + seed;
            assertTrue(sleepy.firstRest >= 0 && sleepy.firstRest < base.firstRest - 6 * 20,
                    at + " settled at " + sleepy.firstRest / 20.0 + "s vs " + base.firstRest / 20.0 + "s");
            // World rework deliberately removed hidden dwell/curiosity/band multipliers.
            CompanionRun look = new CompanionRun(s, seed, Locomotion.FREE_SWIM, CompanionTunables.defaults(), true);
            MoodScenes.apply(look, MoodPresets.named("sleepy"), look.owner.feet());
            look.run(600);
            assertTrue(look.samples.stream().filter(sample -> sample.pose().equals("SLEEPY")).count() > 200,
                    at + " sleepy has its own sustained, drawn pose");
        }
    }

    @Test
    void aMoonstruckFishIdlesHighNoseUp() {
        int gaze = 0;
        double moon = 0, base = 0;
        for (String s : IDLE) for (long seed = 1; seed <= 3; seed++) {
            Watch w = watch(s, seed, Locomotion.FREE_SWIM, "moon");
            gaze += w.moonGaze;
            moon += w.height;
            base += watch(s, seed, Locomotion.FREE_SWIM, null).height;
        }
        assertTrue(gaze > 6 * 2400 * 0.15, "gazing " + gaze);
        assertTrue(moon > base + 6 * 0.15, "height " + moon / 6 + " vs " + base / 6);
    }

    // ── The owner's bar: subtle ────────────────────────────────────────────────────────────────

    /** The 1-wide tunnel: single seeds fade either way at random (plan §5 Phase 5; also CompanionInvariantTest). */
    private static final Set<String> CHAOTIC = Set.of("corridor");
    /** Fades on purpose: the owner outruns the fish. */
    private static final Set<String> FORCED = Set.of("teleport", "elytra", "creative_flight");

    /**
     * Every mood against none on equal seeds, pooled over the swimmer scripts: seen % no more than
     * a point lower, unforced fades no more than a few more. GLIDE is parked (§9.2) and left out.
     */
    @Test
    void seenAndFadesHold() {
        StringBuilder fails = new StringBuilder();
        for (Locomotion c : List.of(Locomotion.FREE_SWIM, Locomotion.BENTHIC, Locomotion.DRIFT)) {
            double[] seen = new double[MoodPresets.ALL.size() + 1];
            long[] fades = new long[MoodPresets.ALL.size() + 1];
            for (int k = 0; k <= MoodPresets.ALL.size(); k++) {
                MoodPresets.Preset p = k == 0 ? null : MoodPresets.ALL.get(k - 1);
                int n = 0;
                for (String s : OwnerScript.NAMES) {
                    for (long seed = 1; seed <= 5; seed++) {
                        CompanionRun run = new CompanionRun(s, seed, c, CompanionTunables.defaults(), true);
                        if (p != null) MoodScenes.apply(run, p, run.owner.feet());
                        run.run(2400);
                        CompanionRun.Metrics m = run.metrics();
                        seen[k] += m.seenFraction();
                        if (!FORCED.contains(s) && !CHAOTIC.contains(s)) fades[k] += m.leashEvents();
                        n++;
                    }
                }
                seen[k] /= n;
            }
            for (int k = 1; k <= MoodPresets.ALL.size(); k++) {
                String name = c + "/" + MoodPresets.ALL.get(k - 1).name();
                if (seen[k] < seen[0] - 0.01) {
                    fails.append(String.format("%s seen %.1f%% vs %.1f%%%n", name, seen[k] * 100, seen[0] * 100));
                }
                if (fades[k] > fades[0] + 3) fails.append(String.format("%s fades %d vs %d%n", name, fades[k], fades[0]));
            }
        }
        assertEquals("", fails.toString());
    }
}
