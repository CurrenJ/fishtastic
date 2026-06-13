package grill24.fishtastic.server;

import grill24.fishtastic.Fishtastic;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import static grill24.fishtastic.server.FishCatchSavedData.getOrCreate;

/**
 * Handles server-side game tick events
 */
public class ServerTickHandler {

    private static long lastResetDay = -1;

    public static void onServerTick(MinecraftServer server) {
        // Daily quest reset check
        long currentDay = server.overworld().getGameTime() / 24000L;
        if (currentDay > lastResetDay) {
            lastResetDay = currentDay;
            getOrCreate(server).resetDailyQuestsIfNeeded(server, currentDay);
        }

        // Tick fishing minigame managers for all levels
        for (ServerLevel level : server.getAllLevels()) {
            FishingMinigameManager manager = FishingMinigameManager.get(level);
            if (manager != null) {
                try {
                    manager.tick();
                } catch (Exception e) {
                    Fishtastic.LOGGER.error("Error ticking fishing minigame manager for level {}", level.dimension().identifier(), e);
                }
            }
        }
    }
}
