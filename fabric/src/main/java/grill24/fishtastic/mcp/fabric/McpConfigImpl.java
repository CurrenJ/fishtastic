package grill24.fishtastic.mcp.fabric;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public class McpConfigImpl {
    public static Path getConfigDirectory() {
        return FabricLoader.getInstance().getConfigDir();
    }
}
