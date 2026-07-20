package grill24.fishtastic.mcp.neoforge;

import net.neoforged.fml.loading.FMLEnvironment;

public class McpEnvironmentCheckImpl {
    public static boolean isDevelopmentEnvironment() {
        return !FMLEnvironment.isProduction();
    }
}
