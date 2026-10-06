package grill24.fishtastic.util;

/**
 * Per-session flavour the server resolves when a minigame starts, which the client maps to a
 * bar/bobber look via {@link FishingBarStyles#select}. Keeping the decision server-side means the
 * art can never disagree with what the player is actually seeing (lava particles, biome), and it
 * travels in {@code StartFishingMinigamePacket} alongside the other per-session data.
 *
 * <p>Deliberately free of client imports so a dedicated server can load it — unlike
 * {@link FishingBarStyles}, which references the client-only renderer.
 */
public enum FishingBarContext {
    /** Ordinary fishing — the default look. */
    DEFAULT,
    /** The bobber is sitting in lava. */
    LAVA,
    /** Legacy-look easter egg: the End, or a mushroom island biome. */
    CLASSIC
}
