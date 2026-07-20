package grill24.fishtastic.mcp.fabric;

import net.fabricmc.loader.api.FabricLoader;

public class McpEnvironmentCheckImpl {
    public static boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }
}
