package grill24.fishtastic.block;

import grill24.fishtastic.Fishtastic;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the fish tank customization mode for each player.
 */
public class FishTankCustomizationModeManager {
    private static final Map<UUID, FishTankCustomizationMode> playerModes = new HashMap<>();

    public static FishTankCustomizationMode getMode(UUID playerId) {
        FishTankCustomizationMode mode = playerModes.getOrDefault(playerId, FishTankCustomizationMode.FRAME);
        Fishtastic.LOGGER.info("[CustomizationModeManager.getMode] playerId={}, mode={}, mapSize={}",
                playerId, mode, playerModes.size());
        return mode;
    }

    public static void setMode(UUID playerId, FishTankCustomizationMode mode) {
        Fishtastic.LOGGER.info("[CustomizationModeManager.setMode] playerId={}, newMode={}", playerId, mode);
        playerModes.put(playerId, mode);
    }

    public static FishTankCustomizationMode cycleMode(UUID playerId, boolean forward) {
        FishTankCustomizationMode currentMode = getMode(playerId);
        FishTankCustomizationMode newMode = forward ? currentMode.next() : currentMode.previous();
        Fishtastic.LOGGER.info("[CustomizationModeManager.cycleMode] playerId={}, forward={}, oldMode={}, newMode={}",
                playerId, forward, currentMode, newMode);
        setMode(playerId, newMode);
        return newMode;
    }
}
