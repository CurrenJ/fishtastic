package grill24.fishtastic.client.ambient;

import grill24.fishtastic.data.FishProfile;

/**
 * How fast an ambient fish swims in open water (docs/ambient-fish-schools-design-spec.md §5.2):
 * its species' cruise tier times a damped size term. The engines run
 * {@link grill24.fishsim.core.Tunables#OPEN_WATER}; tanks use the tier's own tank spread
 * ({@link FishProfile.CruiseTier#tank}) and no size term.
 */
public final class AmbientMotion {
    private AmbientMotion() {}

    /** Rendered body length (blocks) that cruises at the species' tier speed; about the roster median. */
    static final float REFERENCE_LENGTH = 0.35f;
    /** Size damping: real fish cruise at roughly constant body lengths a second, far too much spread for a game. */
    static final float SIZE_EXPONENT = 0.35f;
    static final float MIN_SIZE_TERM = 0.7f;
    static final float MAX_SIZE_TERM = 1.5f;

    /** One ambient fish's speed scale: its tier times a damped size term. */
    public static float speedScale(FishProfile.CruiseTier tier, float length) {
        return tier.openWater() * sizeTerm(length);
    }

    /** {@code (length / reference)^0.35}, clamped to [0.7, 1.5]: bigger fish are a little faster. */
    static float sizeTerm(float length) {
        if (!(length > 0f)) return 1f;
        float term = (float) Math.pow(length / REFERENCE_LENGTH, SIZE_EXPONENT);
        return Math.max(MIN_SIZE_TERM, Math.min(MAX_SIZE_TERM, term));
    }
}
