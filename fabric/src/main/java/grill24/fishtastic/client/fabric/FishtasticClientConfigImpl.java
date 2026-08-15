package grill24.fishtastic.client.fabric;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public class FishtasticClientConfigImpl {
    public static Path getConfigDirectory() {
        return FabricLoader.getInstance().getConfigDir();
    }
}
