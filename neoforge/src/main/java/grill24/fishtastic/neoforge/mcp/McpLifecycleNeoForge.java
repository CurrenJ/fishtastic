package grill24.fishtastic.neoforge.mcp;

import grill24.fishtastic.mcp.McpBridgeState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * Releases the MCP bridge's HTTP port on server shutdown, even if the player quit to title without
 * running {@code /fishtastic mcp stop} first.
 */
public class McpLifecycleNeoForge {
    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> {
            if (McpBridgeState.isRunning()) {
                McpBridgeState.getActiveServer().stop();
            }
        });
    }
}
