package grill24.fishtastic.util;

public interface IFishingHookExtension {
    int getLuck();

    /**
     * Whether this bobber is being nibbled right now (vanilla's synced {@code DATA_BITING}), on
     * either side. The companion uses it for the "leans toward the bobber and twitches" reaction
     * (docs/fish-companions.md §5) — the synced flag means it reads the same for every client, so
     * other players see the owner's companion twitch on the same bite they do.
     */
    boolean fishtastic$isBiting();

    /**
     * Dev-only (self-tests): forces this bobber's bite visual without a real bite, so a headless
     * scene can exercise the nibble reaction deterministically. Local only — it never syncs.
     */
    void fishtastic$forceBite();
}
