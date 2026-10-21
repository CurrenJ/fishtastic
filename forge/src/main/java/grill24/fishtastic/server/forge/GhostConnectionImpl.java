package grill24.fishtastic.server.forge;

import net.minecraft.network.Connection;

/**
 * The dev ghost owner's fake connection (party spec §28.3).
 *
 * <p>Unlike NeoForge, Forge 64 does not require a negotiated payload channel to send an outgoing
 * custom payload: {@code PayloadChannel#toVanillaPacket} builds the vanilla packet directly from
 * the message's type and codec. The {@link Connection} only has to be usable, which
 * {@code EmbeddedChannel} already guarantees, so there is nothing to install here.
 */
public final class GhostConnectionImpl {
    private GhostConnectionImpl() {}

    public static void configure(Connection connection) {
        // Nothing to do on Forge.
    }
}
