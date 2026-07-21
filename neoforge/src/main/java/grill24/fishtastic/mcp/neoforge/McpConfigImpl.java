package grill24.fishtastic.mcp.neoforge;

import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

public class McpConfigImpl {
    public static Path getConfigDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }
}
