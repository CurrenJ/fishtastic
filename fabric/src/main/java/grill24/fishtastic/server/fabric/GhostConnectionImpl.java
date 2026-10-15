package grill24.fishtastic.server.fabric;

import net.minecraft.network.Connection;

public class GhostConnectionImpl {
    public static void configure(Connection connection) {
        // Fabric sends modded payloads on any connection: nothing to set up.
    }
}
