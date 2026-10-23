package grill24.fishsim.companion;

import java.util.Locale;

/**
 * How a drifter companion propels itself (docs/fish-companions-drift.md). Phase 0 plumbs the
 * style through; the body doesn't read it yet, so every style moves as the first-cut bell.
 */
public enum DriftStyle {
    /** A pulsed bell: the flapjack octopus. Strokes sized to arrive, a web-flare brake (§4). */
    BELL,
    /** Fins to hover and turn, mantle jets to travel and escape: the glass squid (§5). */
    JET,
    /** A float that bobs and shoves itself along its sail's heading: the man o' war (§6). */
    BOB;

    /** The style named {@code name} (any case), or {@link #BELL}. */
    public static DriftStyle named(String name) {
        if (name == null) return BELL;
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BELL;
        }
    }
}
