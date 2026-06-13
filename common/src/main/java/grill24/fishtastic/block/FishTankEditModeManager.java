package grill24.fishtastic.block;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Server-side registry of which players have fish tank edit mode active. */
public class FishTankEditModeManager {

    private static final Set<UUID> EDIT_MODE_PLAYERS = Collections.synchronizedSet(new HashSet<>());

    public static boolean isInEditMode(UUID playerId) {
        return EDIT_MODE_PLAYERS.contains(playerId);
    }

    public static boolean toggle(UUID playerId) {
        if (EDIT_MODE_PLAYERS.remove(playerId)) {
            return false;
        }
        EDIT_MODE_PLAYERS.add(playerId);
        return true;
    }

    public static void setEditMode(UUID playerId, boolean active) {
        if (active) EDIT_MODE_PLAYERS.add(playerId);
        else EDIT_MODE_PLAYERS.remove(playerId);
    }
}
