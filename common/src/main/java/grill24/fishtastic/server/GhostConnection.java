package grill24.fishtastic.server;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.network.Connection;

/**
 * Loader-specific set-up for the dev ghost owner's fake connection (party spec §28.3, D31). NeoForge
 * rejects modded payloads on a connection that never went through the configuration handshake, so
 * it must be configured as a mock first; Fabric needs nothing.
 */
public final class GhostConnection {
    private GhostConnection() {}

    @ExpectPlatform
    public static void configure(Connection connection) {
        throw new AssertionError();
    }
}
