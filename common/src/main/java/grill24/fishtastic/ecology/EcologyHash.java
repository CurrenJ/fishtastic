package grill24.fishtastic.ecology;

/**
 * The one stable 64-bit hash behind every seeded decision of the world fish ecology (visit windows,
 * ambient fish). Server and client call it with the same inputs and must get the same bits on every
 * platform and Java version, so it is plain integer arithmetic: a Stafford "mix13" finalizer (the
 * SplitMix64 variant) folded over the inputs. No {@code Object.hashCode}, no {@code Random}, no
 * floating point.
 *
 * <p>Changing the mixer, the folding order or a salt re-rolls every world's ecology, so the
 * known-value test in {@code EcologyHashTest} pins them.
 */
public final class EcologyHash {

    /** Fixed, named streams: the same (seed, chunk, window) gives unrelated values per salt. */
    public static final long SALT_VISIT_ACTIVITY = 0x5A17_0001_C0FF_EE01L;
    public static final long SALT_START_CELL = 0x5A17_0002_BADD_CAFEL;
    public static final long SALT_RANK = 0x5A17_0003_FEED_FACEL;
    public static final long SALT_MEMBERS = 0x5A17_0004_D15E_A5E5L;
    public static final long SALT_AMBIENT_SLOTS = 0x5A17_0005_0DD5_EA11L;
    /** Per-site window phase t0 (added in Step 0; appended so the earlier salts keep their values). */
    public static final long SALT_PHASE = 0x5A17_0006_5EED_0F00L;

    private static final long GAMMA = 0x9E3779B97F4A7C15L;

    private EcologyHash() {
    }

    /** Stafford variant 13 of the 64-bit finalizer (the one SplitMix64 uses). A bijection on longs. */
    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** {@code hash(seed, chunkX, chunkZ, window, salt)}: order-sensitive, every input fully avalanched. */
    public static long hash(long seed, int chunkX, int chunkZ, long window, long salt) {
        long h = mix(seed ^ salt);
        h = mix(h + GAMMA + chunkX);
        h = mix(h + GAMMA + chunkZ);
        h = mix(h + GAMMA + window);
        return h;
    }

    /** Uniform double in [0, 1) from the top 53 bits of a hash. */
    public static double toUnitDouble(long hash) {
        return (hash >>> 11) * 0x1.0p-53;
    }

    /** Uniform int in [0, bound) from a hash, by multiply-shift on the high 32 bits (bias below 2^-32 per value). */
    public static int toBoundedInt(long hash, int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive: " + bound);
        }
        return (int) (((hash >>> 32) * (long) bound) >>> 32);
    }
}
