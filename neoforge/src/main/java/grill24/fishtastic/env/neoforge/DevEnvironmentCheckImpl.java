package grill24.fishtastic.env.neoforge;

import net.neoforged.fml.loading.FMLEnvironment;

public class DevEnvironmentCheckImpl {
    public static boolean isDevelopmentEnvironment() {
        return !FMLEnvironment.isProduction();
    }
}
