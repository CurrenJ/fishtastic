package grill24.fishtastic.companion;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side view of every player's companion party, fed by {@code CompanionPartySyncPacket}.
 * Forge keeps parties in a server-side {@code SavedData}, so unlike the attachment-based loaders it
 * must push the owner's and trackers' parties explicitly.
 */
public final class CompanionPartyClientCache {
    private static final Map<UUID, CompanionParty> PARTIES = new ConcurrentHashMap<>();

    private CompanionPartyClientCache() {}

    public static CompanionParty get(UUID owner) {
        return PARTIES.getOrDefault(owner, CompanionParty.EMPTY);
    }

    public static void apply(UUID owner, CompanionParty party) {
        if (party.isEmpty()) {
            PARTIES.remove(owner);
        } else {
            PARTIES.put(owner, party);
        }
    }

    public static void reset() {
        PARTIES.clear();
    }
}
