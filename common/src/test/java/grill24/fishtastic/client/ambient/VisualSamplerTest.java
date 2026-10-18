package grill24.fishtastic.client.ambient;

import grill24.fishtastic.network.AmbientContextResponsePacket.Candidate;
import grill24.fishtastic.util.Ids;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the Phase 5 visual sampler: the 75/25 raw/flattened mixture, restriction-before-
 * normalisation, ordering, and the never-sample-an-excluded-species rule. All headless — the
 * sampler is pure Java by construction, and the distribution claim is checked by drawing from it a
 * few hundred thousand times with a fixed seed.
 */
class VisualSamplerTest {

    /** The real cave pool of the self-test world, in candidate order, so the numbers mean something. */
    private static final String[] SPECIES = {
            "neon_tetra", "blind_cave_tetra", "indian_glassy_fish", "glass_catfish",
            "blind_cavefish", "waterfall_climbing_cave_fish", "betta", "black_ghost_knifefish",
            "ornate_bichir", "electric_eel", "royal_garden_eel", "ophisternon_candidum"};
    private static final int[] WEIGHTS = {40, 15, 15, 10, 5, 5, 4, 4, 3, 2, 2, 2};

    private static List<Candidate> pool() {
        List<Candidate> candidates = new ArrayList<>(SPECIES.length);
        for (int i = 0; i < SPECIES.length; i++) {
            candidates.add(new Candidate(Ids.of("fishtastic", SPECIES[i]), WEIGHTS[i]));
        }
        return candidates;
    }

    private static VisualSampler buildAll() {
        return VisualSampler.build(pool(), id -> true);
    }

    /** The mixture recomputed independently of the sampler's own code, over the given weights. */
    private static double expectedShare(int[] weights, int index) {
        long total = 0;
        double root = 0;
        for (int weight : weights) {
            total += Math.max(1, weight);
            root += Math.sqrt(Math.max(1, weight));
        }
        double raw = Math.max(1, weights[index]) / (double) total;
        double flattened = Math.sqrt(Math.max(1, weights[index])) / root;
        return AmbientTuning.RAW_SHARE * raw + AmbientTuning.FLATTENED_SHARE * flattened;
    }

    @Test
    void entriesFollowCandidateOrderAndRecordOmissions() {
        VisualSampler sampler = VisualSampler.build(pool(),
                id -> !id.getPath().equals("ornate_bichir") && !id.getPath().equals("betta"));

        assertEquals(List.of("fishtastic:betta", "fishtastic:ornate_bichir"), sampler.omitted());
        List<String> expected = new ArrayList<>();
        for (String species : SPECIES) {
            if (!species.equals("ornate_bichir") && !species.equals("betta")) {
                expected.add("fishtastic:" + species);
            }
        }
        assertEquals(expected, sampler.entries().stream().map(VisualSampler.Entry::species).toList());
        assertFalse(sampler.contains("fishtastic:betta"));
        assertTrue(sampler.contains("fishtastic:neon_tetra"));
    }

    @Test
    void sharesMatchTheSeventyFiveTwentyFiveMixture() {
        VisualSampler sampler = buildAll();
        for (int i = 0; i < SPECIES.length; i++) {
            VisualSampler.Entry entry = sampler.entries().get(i);
            double expected = expectedShare(WEIGHTS, i);
            assertEquals(expected, entry.share(), 1e-6, SPECIES[i] + " share");
            assertEquals(Math.max(1, WEIGHTS[i]) / 107.0, entry.rawShare(), 1e-6, SPECIES[i] + " raw");
        }
        assertEquals(0.75, AmbientTuning.RAW_SHARE, 1e-9);
        assertEquals(0.25, AmbientTuning.FLATTENED_SHARE, 1e-9);
        assertEquals(0.75 + 0.25, AmbientTuning.RAW_SHARE + AmbientTuning.FLATTENED_SHARE, 1e-6);
    }

    @Test
    void sharesSumToOneAndPreserveTheRawOrdering() {
        VisualSampler sampler = buildAll();
        double sum = 0;
        for (VisualSampler.Entry entry : sampler.entries()) sum += entry.share();
        assertEquals(1.0, sum, 1e-5);

        // Both mixture terms are increasing in the weight, so the mixture preserves its ordering.
        // (Equal weights share a rank; a strict inversion would mean the mixing formula is wrong.)
        List<VisualSampler.Entry> entries = sampler.entries();
        for (int i = 1; i < entries.size(); i++) {
            if (entries.get(i).weight() > entries.get(i - 1).weight()) {
                assertTrue(entries.get(i).share() > entries.get(i - 1).share(),
                        entries.get(i).species() + " outweighs " + entries.get(i - 1).species()
                                + " but does not out-share it");
            }
        }
    }

    /**
     * The mixture is computed <em>within the representable set</em>: excluding a species must
     * renormalise over the survivors, not leave its share floating on something that can never be
     * drawn. Removing the heaviest species visibly moves everyone else's share.
     */
    @Test
    void restrictionRenormalisesOverTheSurvivors() {
        VisualSampler unrestricted = buildAll();
        VisualSampler restricted = VisualSampler.build(pool(), id -> !id.getPath().equals("neon_tetra"));

        assertEquals(List.of("fishtastic:neon_tetra"), restricted.omitted());
        assertEquals(SPECIES.length - 1, restricted.size());
        double sum = 0;
        for (VisualSampler.Entry entry : restricted.entries()) sum += entry.share();
        assertEquals(1.0, sum, 1e-5, "a restricted sampler's shares must still sum to one");

        // blind_cave_tetra was 15 of 107; now it is 15 of 67 and renormalised independently.
        int index = 1;
        int[] survivors = new int[WEIGHTS.length - 1];
        System.arraycopy(WEIGHTS, 1, survivors, 0, WEIGHTS.length - 1);
        double renormalised = expectedShare(survivors, index - 1);
        assertEquals(renormalised, restricted.shareOf("fishtastic:blind_cave_tetra"), 1e-6);
        assertNotEquals(unrestricted.shareOf("fishtastic:blind_cave_tetra"),
                restricted.shareOf("fishtastic:blind_cave_tetra"), 1e-4,
                "removing a shown species must move the remaining shares");
    }

    /**
     * The statistical claim: over many independent draws, the observed frequency of every species
     * matches its 75/25 mixture share inside binomial tolerance — and for the heaviest species it
     * is measurably <em>not</em> the raw catch probability, i.e. the flattening is real and not
     * decoration. A fixed seed makes the tolerance checks deterministic, not flaky.
     */
    @Test
    void manyDrawsMatchTheMixture() {
        VisualSampler sampler = buildAll();
        AmbientRandom random = new AmbientRandom(20261013L);
        int draws = 200_000;
        int[] observed = new int[SPECIES.length];
        for (int i = 0; i < draws; i++) {
            String species = sampler.sample(random);
            boolean found = false;
            for (int k = 0; k < SPECIES.length; k++) {
                if (species.equals("fishtastic:" + SPECIES[k])) {
                    observed[k]++;
                    found = true;
                    break;
                }
            }
            assertTrue(found, "sampled a species the sampler does not contain: " + species);
        }
        for (int k = 0; k < SPECIES.length; k++) {
            double share = expectedShare(WEIGHTS, k);
            double sigma = Math.sqrt(share * (1 - share) / draws);
            double tolerance = Math.max(0.004, 4 * sigma);
            double frequency = observed[k] / (double) draws;
            assertEquals(share, frequency, tolerance, SPECIES[k] + " observed " + frequency
                    + " vs mixture " + share);
        }
        // The 25% term is load-bearing: neon_tetra is 37.4% of a raw pool and 33.1% of a visual one,
        // and 200k draws cannot blur that apart.
        double neon = observed[0] / (double) draws;
        double neonRaw = WEIGHTS[0] / 107.0;
        assertTrue(Math.abs(neon - neonRaw) > 0.03,
                "the visual share must visibly differ from the raw weight (raw=" + neonRaw + " obs=" + neon + ")");
    }

    @Test
    void excludedSpeciesAreNeverSampled() {
        VisualSampler sampler = VisualSampler.build(pool(),
                id -> !id.getPath().equals("electric_eel") && !id.getPath().equals("royal_garden_eel"));
        AmbientRandom random = new AmbientRandom(5L);
        for (int i = 0; i < 50_000; i++) {
            String species = sampler.sample(random);
            assertNotEquals("fishtastic:electric_eel", species);
            assertNotEquals("fishtastic:royal_garden_eel", species);
        }
    }

    @Test
    void sameSeedGivesTheSameIndividualSequence() {
        VisualSampler sampler = buildAll();
        AmbientRandom a = new AmbientRandom(99L);
        AmbientRandom b = new AmbientRandom(99L);
        for (int i = 0; i < 500; i++) {
            assertEquals(sampler.sample(a), sampler.sample(b), "draw " + i);
        }
    }

    @Test
    void oneDrawPerIndividualNotOnePerSpecies() {
        // Fifty draws from the same sampler and stream must show the mixture's own spread — a
        // "sample a species once, then fill a school with it" implementation would produce long
        // identical runs.
        VisualSampler sampler = VisualSampler.build(
                List.of(new Candidate(Ids.of("fishtastic", "a"), 1),
                        new Candidate(Ids.of("fishtastic", "b"), 1)),
                id -> true);
        AmbientRandom random = new AmbientRandom(17L);
        int switches = 0;
        String previous = sampler.sample(random);
        for (int i = 1; i < 50; i++) {
            String next = sampler.sample(random);
            if (!next.equals(previous)) switches++;
            previous = next;
        }
        assertTrue(switches >= 10, "independent draws must alternate (switches=" + switches + ")");
    }

    @Test
    void nothingRepresentableSamplesNothing() {
        VisualSampler sampler = VisualSampler.build(pool(), id -> false);
        assertTrue(sampler.isEmpty());
        assertEquals(0, sampler.size());
        assertNull(sampler.sample(new AmbientRandom(1L)));
        assertEquals(SPECIES.length, sampler.omitted().size());
    }

    @Test
    void nonPositiveWeightsAreFlooredLikeTheResolverFloorsThem() {
        VisualSampler sampler = VisualSampler.build(
                List.of(new Candidate(Ids.of("fishtastic", "zero"), 0),
                        new Candidate(Ids.of("fishtastic", "one"), 1)),
                id -> true);
        assertEquals(sampler.shareOf("fishtastic:zero"), sampler.shareOf("fishtastic:one"), 1e-9);
    }
}
