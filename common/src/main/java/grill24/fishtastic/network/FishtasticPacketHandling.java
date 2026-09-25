package grill24.fishtastic.network;

import grill24.fishtastic.network.codec.BufCodec;
import net.minecraft.world.entity.player.Player;

/**
 * Platform-agnostic packet registration/handling contracts, split out of {@link FishtasticPackets}
 * (B3.2) so a payload record's {@code handleServerToClient}/{@code handleClientToServer} signature
 * doesn't have to pull in {@code FishtasticPackets} itself — which registers every payload type and
 * so needs the full registration graph before it can compile.
 */
public final class FishtasticPacketHandling {
    private FishtasticPacketHandling() {}

    /**
     * Interface for platform-specific packet registration. {@code payloadClass} is only used by
     * the Forge side: Forge 47's {@code SimpleChannel} (unlike Fabric's id-keyed {@code PacketType}
     * or NeoForge's newer {@code PayloadRegistrar}) dispatches outgoing messages by the runtime
     * class of the message object, so each payload's concrete record class has to be registered
     * explicitly — it can't be recovered from the erased {@code T} at registration time.
     */
    public interface IPacketRegistrar {
        <T extends FishtasticPayload> void registerClientToServer(
                FishtasticPayload.PayloadType<T> type,
                Class<T> payloadClass,
                BufCodec<T> codec,
                IPacketHandler<T> handler
        );

        <T extends FishtasticPayload> void registerServerToClient(
                FishtasticPayload.PayloadType<T> type,
                Class<T> payloadClass,
                BufCodec<T> codec,
                IPacketHandler<T> handler
        );
    }

    /** Platform-agnostic packet handler interface. */
    @FunctionalInterface
    public interface IPacketHandler<T extends FishtasticPayload> {
        void handle(T packet, IPacketContext context);
    }

    /** Context information for packet handling. */
    public interface IPacketContext {
        Player getPlayer();
        void enqueueWork(Runnable runnable);
    }
}
