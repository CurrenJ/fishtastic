package grill24.fishtastic.client.ambient;

/**
 * Every Phase 5 tuning value, in one place (Fish in World, Phase 5) — the spec's starting points,
 * so the population policy can be read and changed without hunting through the runtime.
 *
 * <p>Each value carries where it came from and how it is verified:
 * <ul>
 *   <li>{@link #RAW_SHARE}/{@link #FLATTENED_SHARE} — docs/fish-in-world.md "Species selection and
 *       visual weighting": the proposed, deliberately tunable 75/25 mixture. Pinned statistically
 *       by {@code VisualSamplerTest} (many draws match the mixture inside binomial tolerance).</li>
 *   <li>{@link #POPULATION_FADE_TICKS} — the spec's "~2 s eligibility transition" starting point
 *       (40 ticks at 20 tps), used when the density setting or a lifespan end retires a fish.</li>
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
     * Fade length of a roster-driven retirement: a fish the density setting no longer draws, one
     * whose seeded lifespan ended, or one that left its leash. 40 ticks = 2 s at 20 tps.
     *
     * <p>Deliberately separate from the runtime's {@code FADE_TICKS} (24): water-boundary and
     * broken-water retirements are Phase 2 behaviour and keep their measured fade exactly.
     */
    public static final int POPULATION_FADE_TICKS = 40;

    private AmbientTuning() {
    }
}
