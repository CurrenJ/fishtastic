package grill24.fishtastic.server.neoforge;

import net.minecraft.network.Connection;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

public class GhostConnectionImpl {
    /** As the game tests' mock players: register the modded payload channels the handshake would have. */
    public static void configure(Connection connection) {
        NetworkRegistry.configureMockConnection(connection);
    }
}
