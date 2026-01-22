package grill24.fishtastic.server;

import grill24.fishtastic.Fishtastic;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Handles server-side game tick events
 */
public class ServerTickHandler {

    /**
     * Called every server tick
     * @param server The Minecraft server instance
     */
    public static void onServerTick(MinecraftServer server) {
        // Tick fishing minigame managers for all levels
        for (ServerLevel level : server.getAllLevels()) {
            FishingMinigameManager manager = FishingMinigameManager.get(level);
            if (manager != null) {
                try {
                    manager.tick();
                } catch (Exception e) {
                    Fishtastic.LOGGER.error("Error ticking fishing minigame manager for level {}", level.dimension().location(), e);
                }
            }
        }
    }
}
