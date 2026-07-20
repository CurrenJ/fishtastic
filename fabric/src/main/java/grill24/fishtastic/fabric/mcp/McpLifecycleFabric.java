package grill24.fishtastic.fabric.mcp;

import grill24.fishtastic.mcp.McpBridgeState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * Releases the MCP bridge's HTTP port on server shutdown, even if the player quit to title without
 * running {@code /fishtastic mcp stop} first.
 */
public class McpLifecycleFabric {
    public static void register() {
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (McpBridgeState.isRunning()) {
                McpBridgeState.getActiveServer().stop();
            }
        });
    }
}
