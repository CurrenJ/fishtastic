package grill24.fishtastic.block;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the fish tank customization mode for each player.
 */
public class FishTankCustomizationModeManager {
    private static final Map<UUID, FishTankCustomizationMode> playerModes = new HashMap<>();

    public static FishTankCustomizationMode getMode(UUID playerId) {
        return playerModes.getOrDefault(playerId, FishTankCustomizationMode.FRAME);
    }

    public static void setMode(UUID playerId, FishTankCustomizationMode mode) {
        playerModes.put(playerId, mode);
    }

    public static FishTankCustomizationMode cycleMode(UUID playerId, boolean forward) {
        FishTankCustomizationMode currentMode = getMode(playerId);
        FishTankCustomizationMode newMode = forward ? currentMode.next() : currentMode.previous();
        setMode(playerId, newMode);
        return newMode;
    }
}
