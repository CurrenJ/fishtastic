package grill24.fishtastic.client.ambient;

import grill24.fishtastic.network.AmbientContextResponsePacket.Candidate;
import grill24.fishtastic.util.Ids;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the Phase 5 population turnover: bounded, species-independent lifetimes, and the claim that
 * following them long enough makes the observed occupancy track the visual mixture — the unit-test
 * half of the plan's "long-run occupancy check" (the scene half proves the same wiring live).
 *
 * <p>The simulation here is the runtime's policy with the world removed: slots hold individuals;
 * each individual gets one lifetime drawn from {@link AmbientTuning#lifetimeTicks} with no species
 * argument anywhere; on expiry the slot is refilled with one independent sampler draw. Whatever
 * occupancy that process settles at is what the runtime will settle at, because the runtime does
 * exactly this.
 */
class AmbientTurnoverTest {

    private static final String[] SPECIES = {
            "neon_tetra", "blind_cave_tetra", "indian_glassy_fish", "glass_catfish",
            "blind_cavefish", "waterfall_climbing_cave_fish", "betta", "black_ghost_knifefish",
            "ornate_bichir", "electric_eel", "royal_garden_eel", "ophisternon_candidum"};
    private static final int[] WEIGHTS = {40, 15, 15, 10, 5, 5, 4, 4, 3, 2, 2, 2};

    private static VisualSampler sampler() {
        List<Candidate> candidates = new ArrayList<>(SPECIES.length);
        for (int i = 0; i < SPECIES.length; i++) {
            candidates.add(new Candidate(Ids.of("fishtastic", SPECIES[i]), WEIGHTS[i]));
        }
        return VisualSampler.build(candidates, id -> true);
    }

    private static double expectedShare(int index) {
        long total = 0;
        double root = 0;
        for (int weight : WEIGHTS) {
            total += Math.max(1, weight);
            root += Math.sqrt(Math.max(1, weight));
        }
        double raw = Math.max(1, WEIGHTS[index]) / (double) total;
        double flattened = Math.sqrt(Math.max(1, WEIGHTS[index])) / root;
        return AmbientTuning.RAW_SHARE * raw + AmbientTuning.FLATTENED_SHARE * flattened;
    }

    private record Turnover(int[] births, double[] occupancyShare, Map<String, double[]> lifetimeBySpecies) {}

    /**
     * One simulated patch: {@code slots} slots, ticked for {@code ticks}; a slot whose individual
     * expires is refilled by one sampler draw and one lifetime draw. Occupancy is the time-averaged
     * census share; lifetimes are accumulated per species for the bias check.
     */
    private static Turnover simulate(VisualSampler sampler, int slots, long ticks, long seed) {
        AmbientRandom random = new AmbientRandom(seed);
        String[] held = new String[slots];
        long[] expiry = new long[slots];
        int[] births = new int[SPECIES.length];
        long[] occupancy = new long[SPECIES.length];
        Map<String, double[]> lifetimes = new LinkedHashMap<>();
        for (int step = 0; step < ticks; step++) {
            for (int slot = 0; slot < slots; slot++) {
                if (step >= expiry[slot]) {
                    String species = sampler.sample(random);
                    int lifetime = AmbientTuning.lifetimeTicks(random);
                    held[slot] = species;
                    expiry[slot] = step + lifetime;
                    int index = indexOf(species);
                    births[index]++;
                    double[] stats = lifetimes.computeIfAbsent(species, k -> new double[2]);
                    stats[0] += 1;
                    stats[1] += lifetime;
                }
                occupancy[indexOf(held[slot])]++;
            }
        }
        long total = (long) slots * ticks;
        double[] share = new double[SPECIES.length];
        for (int i = 0; i < share.length; i++) share[i] = occupancy[i] / (double) total;
        return new Turnover(births, share, lifetimes);
    }

    private static int indexOf(String species) {
        for (int i = 0; i < SPECIES.length; i++) {
            if (species.equals("fishtastic:" + SPECIES[i])) return i;
        }
        throw new IllegalStateException("unknown species " + species);
    }

    @Test
    void lifetimesAreBoundedUniformAndCentredOnTheConvergenceConstant() {
        AmbientRandom random = new AmbientRandom(1234L);
        int draws = 100_000;
        int[] quartile = new int[4];
        long sum = 0;
        for (int i = 0; i < draws; i++) {
            int lifetime = AmbientTuning.lifetimeTicks(random);
            assertTrue(lifetime >= AmbientTuning.LIFETIME_MIN_TICKS && lifetime <= AmbientTuning.LIFETIME_MAX_TICKS,
                    "lifetime " + lifetime + " outside [" + AmbientTuning.LIFETIME_MIN_TICKS + ", "
                            + AmbientTuning.LIFETIME_MAX_TICKS + "]");
            sum += lifetime;
            quartile[(lifetime - AmbientTuning.LIFETIME_MIN_TICKS) * 4
                    / (AmbientTuning.LIFETIME_MAX_TICKS - AmbientTuning.LIFETIME_MIN_TICKS + 1)]++;
        }
        double mean = sum / (double) draws;
        assertEquals(AmbientTuning.meanLifetimeTicks(), mean, 5.0, "mean lifetime");
        // Uniform, not peaked: each quarter of the band holds a quarter of the draws (±4σ).
        for (int q = 0; q < 4; q++) {
            assertEquals(draws / 4.0, quartile[q], 700.0, "quartile " + q);
        }
        assertEquals(2400, AmbientTuning.meanLifetimeTicks(), "two-minute mean at 20 tps");
    }

    /**
     * No species lives longer than another: every species' mean observed lifetime is the same
     * within sampling error, because no draw has a species to bias it with. A species-specific
     * longevity (or a lifetime drawn from the catch weight) would show up here as a spread far
     * outside the per-species sampling error.
     */
    @Test
    void lifetimesCarryNoSpeciesBias() {
        Turnover simulation = simulate(sampler(), 8, 1_200_000, 20261013L);
        double min = Double.MAX_VALUE, max = 0;
        long total = 0, count = 0;
        for (Map.Entry<String, double[]> entry : simulation.lifetimeBySpecies().entrySet()) {
            double perSpeciesMean = entry.getValue()[1] / entry.getValue()[0];
            assertTrue(entry.getValue()[0] > 50, entry.getKey() + " births " + entry.getValue()[0]);
            min = Math.min(min, perSpeciesMean);
            max = Math.max(max, perSpeciesMean);
            total += entry.getValue()[1];
            count += entry.getValue()[0];
        }
        double overall = total / (double) count;
        assertEquals(AmbientTuning.meanLifetimeTicks(), overall, 15.0, "overall mean lifetime");
        assertTrue(max - min < 180,
                "per-species mean lifetimes must agree (min=" + min + " max=" + max + ")");
    }

    /**
     * The long-run occupancy claim: with turnover running, the time-averaged census share tracks
     * the 75/25 visual mixture inside binomial tolerance — and for the heaviest species it is
     * measurably <em>not</em> the raw catch weight, which is what the flattened quarter of the
     * mixture is for (rares appear more often on screen than they do on the hook, while the common
     * species stays common).
     */
    @Test
    void turnoverConvergesToTheVisualMixture() {
        Turnover simulation = simulate(sampler(), 8, 1_200_000, 86420L);
        long births = 0;
        for (int value : simulation.births()) births += value;
        double effective = births + 8;
        assertTrue(births > 2000, "the window must contain real turnover (births=" + births + ")");
        for (int i = 0; i < SPECIES.length; i++) {
            double share = expectedShare(i);
            double sigma = Math.sqrt(share * (1 - share) / effective);
            double tolerance = Math.max(0.01, 4 * sigma);
            assertEquals(share, simulation.occupancyShare()[i], tolerance,
                    SPECIES[i] + " occupancy vs mixture share");
        }
        double neon = simulation.occupancyShare()[0];
        double neonRaw = WEIGHTS[0] / 107.0;
        assertTrue(Math.abs(neon - neonRaw) > 0.03,
                "occupancy must follow the mixture, not the raw weights (raw=" + neonRaw + " obs=" + neon + ")");
    }

}
