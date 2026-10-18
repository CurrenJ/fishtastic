package grill24.fishtastic.client.ambient;

/**
 * Every Phase 5 tuning value, in one place (Fish in World, Phase 5) — the spec's starting points,
 * so the population policy can be read and changed without hunting through the runtime.
 *
 * <p>Each value carries where it came from and how it is verified:
 * <ul>
 *   <li>{@link #RAW_SHARE}/{@link #FLATTENED_SHARE} — docs/fish-in-world.md "Species selection and
 *       visual weighting": the proposed, deliberately tunable 75/25 mixture. Pinned statistically
 *       by {@code VisualSamplerTest} (many draws match the mixture inside binomial tolerance) and
 *       by the turnover simulation in {@code AmbientTurnoverTest} (long-run occupancy converges to
 *       the mixture, not to the raw weights).</li>
 *   <li>{@link #POPULATION_FADE_TICKS} — the spec's "~2 s eligibility transition" starting point
 *       (40 ticks at 20 tps). The {@code ambientpopulation} scene measures the apply-to-gone
 *       interval against it; the doc records the measured value.</li>
 *   <li>{@link #LIFETIME_MIN_TICKS}/{@link #LIFETIME_MAX_TICKS} — ordinary playtesting requested
 *       continuity over two minutes on average. Weight-only changes follow natural turnover;
 *       exclusions still retire promptly.</li>
 *   <li>{@link #RETENTION_TICKS}/{@link #MAX_RETAINED_PATCHES} — the spec's "bounded period" for
 *       patch/equipment random-state retention: 30 s, at most 16 patches.</li>
 * </ul>
 *
 * <p>Enable and density are client settings; population policy values remain shared constants.
 */
public final class AmbientTuning {

    /** Share of the visual mixture taken by the raw species probability p_i. */
    public static final float RAW_SHARE = 0.75f;
    /** Share of the visual mixture taken by the square-root-flattened probability q_i. */
    public static final float FLATTENED_SHARE = 0.25f;

    /**
     * Fade length of a context-driven retirement — an individual excluded by a new pool, or one
     * whose species-independent lifetime ran out. 40 ticks = 2 s at 20 tps.
     *
     * <p>Deliberately separate from the runtime's {@code FADE_TICKS} (24): water-boundary and
     * broken-water retirements are Phase 2 behaviour and keep their measured fade exactly.
     */
    public static final int POPULATION_FADE_TICKS = 40;

    /** Shortest lifetime any individual gets: 1800 ticks = 90 s. */
    public static final int LIFETIME_MIN_TICKS = 1800;
    /** Longest lifetime any individual gets: 3000 ticks = 150 s. Mean 2400 ticks = 120 s. */
    public static final int LIFETIME_MAX_TICKS = 3000;

    /** How long a dropped patch's sampler state is retained before it is forgotten: 600 ticks = 30 s. */
    public static final int RETENTION_TICKS = 600;
    /** Hard bound on retained patches; the shown set is at most four. */
    public static final int MAX_RETAINED_PATCHES = 16;

    private AmbientTuning() {
    }

    /**
     * One lifetime draw, uniform over {@code [LIFETIME_MIN_TICKS, LIFETIME_MAX_TICKS]}, from the
     * patch's own species stream. Species-independent by construction — there is no species
     * parameter to bias it with — which is what keeps turnover from skewing occupancy: every
     * individual leaves at the same rate whatever it is, so abundance is decided by what replaces
     * it, not by who lives longer.
     */
    public static int lifetimeTicks(AmbientRandom random) {
        return LIFETIME_MIN_TICKS + random.nextInt(LIFETIME_MAX_TICKS - LIFETIME_MIN_TICKS + 1);
    }

    /** Mean lifetime in ticks — the weight-convergence time constant, for documentation and tests. */
    public static double meanLifetimeTicks() {
        return (LIFETIME_MIN_TICKS + LIFETIME_MAX_TICKS) / 2.0;
    }
}
