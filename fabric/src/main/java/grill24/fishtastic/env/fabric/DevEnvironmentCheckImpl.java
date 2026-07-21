package grill24.fishtastic.env.fabric;

import net.fabricmc.loader.api.FabricLoader;

public class DevEnvironmentCheckImpl {
    public static boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }
}
