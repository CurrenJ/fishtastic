package grill24.fishtastic.client.neoforge;

import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

public class FishtasticClientConfigImpl {
    public static Path getConfigDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }
}
