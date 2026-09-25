package grill24.fishtastic.data;

/**
 * Portstub (gradle/port-excludes.gradle): temporary stand-in for the real, large {@code FishProfile}
 * (spawn weighting, zones, temperament, animation — pulls in most of the data-registry graph).
 * Keeps only {@code DEFAULT_MEAN_SIZE} and the {@code size()}/{@code SizeParams} shape, both
 * copied verbatim from the real record, for {@link grill24.fishtastic.data.TankCapacity}'s
 * registry-lookup fallback (dead in practice until the registry graph is wired — nothing
 * registers a real {@code FishProfile} yet, so that lookup is always empty regardless of this
 * stub's fidelity). Real file still exists (excluded) at data/FishProfile.java, delete this stub
 * once that graph is ported for real.
 */
public class FishProfile {
    public static final float DEFAULT_MEAN_SIZE = 50.0f;

    public record SizeParams(float mean, float stdDev) {}

    public SizeParams size() {
        return new SizeParams(DEFAULT_MEAN_SIZE, 0f);
    }
}
