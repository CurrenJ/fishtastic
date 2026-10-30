package grill24.fishtastic.client.ambient;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The visual species sampler (Fish in World, Phase 5): turns one authoritative species context —
 * ordered {@link Candidate}s with final raw int weights — into the distribution ambient individuals
 * are actually drawn from.
 *
 * <h2>The mixture</h2>
 * The spec (docs/fish-in-world.md, "Species selection and visual weighting") asks for
 * {@code p_visual = 75% · p + 25% · q}, where {@code p_i = w_i / Σw} is the raw catch probability
 * and {@code q_i = sqrt(w_i) / Σsqrt(w)} is its square-root flattening. The mixture keeps the
 * ordering of the raw weights (both terms are increasing in {@code w_i}, so the mixture is too),
 * never adds a species the context did not contain, and is visual-only: the catch path keeps
 * {@code p} and is not touched here.
 *
 * <h2>Restriction before normalisation</h2>
 * The mixture is computed <em>within the representable set</em>: candidates whose species the
 * caller cannot represent (no registered item, a locomotion mismatch, a domain that cannot host
 * it) are removed first, and both sums are taken over the survivors. Removing after normalisation
 * would leave mass on species that can never be drawn; removing first is what makes the remaining
 * shares sum to one. Every removal is recorded in {@link #omitted()} — never silently shrunk,
 * demoted or re-weighted.
 *
 * <h2>One draw, one individual</h2>
 * {@link #sample(AmbientRandom)} consumes exactly one uniform draw per individual slot, from the
 * caller's own stream. There is no "pick a species then a school": the spec's requirement is that
 * each individual is sampled independently, so a rare species' sightings are its own draws.
 *
 * <p>Pure Java: no world, no engine, no rendering — the mixture and its statistical behaviour are
 * pinned by plain JUnit tests.
 */
public final class VisualSampler {

    /** One species of a pool with its final raw weight, in pool order. */
    public record Candidate(Identifier species, int weight) {
    }

    /**
     * One representable candidate, with its weight and all three probabilities in candidate order.
     * {@code species()} is the full {@code namespace:path} id — the same string the runtime's
     * individuals carry — while {@link #id()} is the registry key the visuals resolve with.
     */
    public record Entry(Identifier id, int weight, float rawShare, float flattenedShare, float share) {
        public String species() {
            return id.toString();
        }
    }

    private final List<Entry> entries;
    private final List<String> omitted;
    private final double[] cumulative;

    private VisualSampler(List<Entry> entries, List<String> omitted, double[] cumulative) {
        this.entries = List.copyOf(entries);
        this.omitted = List.copyOf(omitted);
        this.cumulative = cumulative;
    }

    /**
     * Builds the sampler for one context: keep the candidates {@code representable} accepts, in
     * their original order, and compute the mixture over those only.
     *
     * @param candidates   the authoritative pool, in pool order
     * @param representable whether the caller can represent one species id at all
     */
    public static VisualSampler build(List<Candidate> candidates, Predicate<Identifier> representable) {
        List<Candidate> kept = new ArrayList<>(candidates.size());
        List<String> omitted = new ArrayList<>(0);
        for (Candidate candidate : candidates) {
            if (representable.test(candidate.species())) {
                kept.add(candidate);
            } else {
                omitted.add(candidate.species().toString());
            }
        }
        long totalWeight = 0;
        double totalRoot = 0;
        for (Candidate candidate : kept) {
            int weight = Math.max(1, candidate.weight());
            totalWeight += weight;
            totalRoot += Math.sqrt(weight);
        }
        List<Entry> entries = new ArrayList<>(kept.size());
        double[] cumulative = new double[kept.size()];
        double accumulated = 0;
        for (int i = 0; i < kept.size(); i++) {
            Candidate candidate = kept.get(i);
            int weight = Math.max(1, candidate.weight());
            double raw = weight / (double) totalWeight;
            double flattened = totalRoot == 0 ? 0 : Math.sqrt(weight) / totalRoot;
            float share = (float) (AmbientTuning.RAW_SHARE * raw + AmbientTuning.FLATTENED_SHARE * flattened);
            accumulated += share;
            cumulative[i] = accumulated;
            entries.add(new Entry(candidate.species(), weight, (float) raw, (float) flattened, share));
        }
        return new VisualSampler(entries, omitted, cumulative);
    }

    /**
     * One individual's species: exactly one draw from {@code random}, independent of every other
     * call. Returns {@code null} when nothing is representable — the caller must then spawn nothing
     * rather than fall back to something a context did not offer.
     */
    public String sample(AmbientRandom random) {
        return entries.isEmpty() ? null : sampleAt(random.nextDouble());
    }

    /**
     * The species at uniform position {@code u} in [0, 1) of the mixture: the seeded ambient roster
     * draws with a hashed {@code u} per fish, so the draw is a pure function of the fish's ID.
     * Returns {@code null} when nothing is representable.
     */
    public String sampleAt(double u) {
        if (entries.isEmpty()) return null;
        int lo = 0, hi = cumulative.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (cumulative[mid] > u) hi = mid;
            else lo = mid + 1;
        }
        // The last cumulative may sit a float-ulp under 1; a draw in that gap belongs to the last
        // entry, not to no one.
        return entries.get(u >= cumulative[lo] ? entries.size() - 1 : lo).species();
    }

    /** The representable candidates, in candidate order. */
    public List<Entry> entries() {
        return entries;
    }

    /** Species dropped for representability, in candidate order — the caller records these. */
    public List<String> omitted() {
        return omitted;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    /** Whether this sampler can ever draw {@code species}. */
    public boolean contains(String species) {
        for (Entry entry : entries) {
            if (entry.species().equals(species)) return true;
        }
        return false;
    }

    /** The mixture share of one species, or zero when it is not representable. */
    public float shareOf(String species) {
        for (Entry entry : entries) {
            if (entry.species().equals(species)) return entry.share();
        }
        return 0f;
    }
}
