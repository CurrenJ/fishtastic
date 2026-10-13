package grill24.fishtastic.client.ambient;

/**
 * The ambient population's own deterministic random stream (Fish in World, Phase 5).
 *
 * <p>SplitMix64, chosen for the one property {@link java.util.Random} cannot offer: its whole state
 * is a single {@code long}, so a stream can be snapshotted and restored. That is what makes the
 * per-patch retention policy possible and testable — "the same patch resumes the same stream where
 * it stopped" is a plain JUnit fact, not something only a running client could show.
 *
 * <p>Deliberately not a {@link java.util.Random} subclass, and never shared with the catch path:
 * ambient sampling must not be able to consume or perturb reward RNG (docs/fish-in-world.md,
 * "no effect on catch rolls").
 */
public final class AmbientRandom {

    /** The SplitMix64 increment: the golden-ratio odd constant. */
    private static final long GAMMA = 0x9E3779B97F4A7C15L;

    private long state;

    public AmbientRandom(long seed) {
        this.state = seed;
    }

    /**
     * The whole stream state. Retain this and {@link #restore} it later to resume the stream
     * exactly where it stopped — the mechanism behind bounded per-patch random-state retention.
     */
    public long state() {
        return state;
    }

    /** Resumes a retained stream. */
    public void restore(long state) {
        this.state = state;
    }

    public long nextLong() {
        long z = (state += GAMMA);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Uniform in {@code [0, bound)}; {@code bound} must be positive. */
    public int nextInt(int bound) {
        if (bound <= 0) throw new IllegalArgumentException("bound must be positive: " + bound);
        return Math.floorMod(nextLong(), bound);
    }

    /** Uniform in {@code [0, 1)}. */
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    /** Uniform in {@code [0, 1)}. */
    public float nextFloat() {
        return (nextLong() >>> 40) * 0x1.0p-24f;
    }
}
