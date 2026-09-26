package grill24.fishtastic.client.forge;

import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

public class FishtasticClientConfigImpl {
    public static Path getConfigDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }
}
