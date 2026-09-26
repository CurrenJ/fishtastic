package grill24.fishtastic.env.forge;

import net.minecraftforge.fml.loading.FMLEnvironment;

public class DevEnvironmentCheckImpl {
    public static boolean isDevelopmentEnvironment() {
        return !FMLEnvironment.production;
    }
}
