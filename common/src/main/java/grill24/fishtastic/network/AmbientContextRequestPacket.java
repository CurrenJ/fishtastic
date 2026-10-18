package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.codec.BufCodecs;
import grill24.fishtastic.server.AmbientContextServer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Sent <strong>client → server</strong> (Fish in World, Phase 4): "what species context applies at
 * these water origins?" The client names the live patches its ambient runtime is showing; the server
 * answers from its own equipment and world state with a {@link AmbientContextResponsePacket}.
 *
 * <h2>Shape and bounds</h2>
 * At most {@link #MAX_ORIGINS} origins per request (one per shown patch); the codec rejects a
 * larger list on decode, and {@link AmbientContextServer} clamps again on its side. Each
 * {@link Origin} carries:
 * <ul>
 *   <li>{@code dimension} + {@code pos} — where and in which level the client believes there is
 *       water. {@code pos} is a water-surface cell the client's own discovery actually found, never
 *       a guess: the server re-validates it (loaded, still water) before reading anything.</li>
 *   <li>{@code patchKey} — the client's stable water-patch identity (see {@code WaterDiscovery}),
 *       echoed back so the response can be routed to the right patch. Opaque to the server.</li>
 *   <li>{@code geometryGeneration} — a client counter bumped whenever the patch's water geometry
 *       (columns, spans) changed since the last request. A response for an old generation is
 *       discarded by the client.</li>
 *   <li>{@code contextGeneration} — a client counter bumped whenever any other context input
 *       changed (equipment lane, explicit invalidation). A response for an old generation is
 *       discarded, which is what stops an in-flight answer to a pre-equipment-switch request from
 *       overwriting the new one.</li>
 * </ul>
 *
 * <p>The request envelope carries the connection's {@code session} token (minted by the client on
 * every level session) and a monotonically increasing {@code requestGeneration}. Both are echoed —
 * the client uses them to drop anything from a previous session or an older request. None of the
 * tokens need server-side validation: the server echoes them and the client is the only consumer
 * that could be corrupted by a lie.
 *
 * <p>The server is free to not answer at all — it silently drops requests past its per-player rate
 * limit (see {@code AmbientRequestThrottle}). "No response" is a supported state: the client ages a
 * pending patch out and retries on its bounded cadence, without error spam.
 */
public record AmbientContextRequestPacket(
        long session,
        int requestGeneration,
        List<Origin> origins
) implements FishtasticPayload {

    /** Origins per request — one per shown patch ({@code AmbientFishRuntime.MAX_DOMAINS}). */
    public static final int MAX_ORIGINS = 4;

    /** One water origin the client wants an authoritative context for. */
    public record Origin(
            ResourceLocation dimension,
            long patchKey,
            int geometryGeneration,
            int contextGeneration,
            BlockPos pos
    ) {
        public static final BufCodec<Origin> STREAM_CODEC =
                BufCodec.composite(
                        BufCodecs.RESOURCE_LOCATION, Origin::dimension,
                        BufCodecs.VAR_LONG, Origin::patchKey,
                        BufCodecs.VAR_INT, Origin::geometryGeneration,
                        BufCodecs.VAR_INT, Origin::contextGeneration,
                        BufCodecs.BLOCK_POS, Origin::pos,
                        Origin::new
                );
    }

    public static final FishtasticPayload.PayloadType<AmbientContextRequestPacket> TYPE =
            new FishtasticPayload.PayloadType<>(FishtasticPackets.AMBIENT_CONTEXT_REQUEST_ID);

    public static final BufCodec<AmbientContextRequestPacket> STREAM_CODEC =
            BufCodec.composite(
                    BufCodecs.VAR_LONG, AmbientContextRequestPacket::session,
                    BufCodecs.VAR_INT, AmbientContextRequestPacket::requestGeneration,
                    Origin.STREAM_CODEC.apply(BufCodecs.list(MAX_ORIGINS)), AmbientContextRequestPacket::origins,
                    AmbientContextRequestPacket::new
            );

    public AmbientContextRequestPacket {
        origins = List.copyOf(origins);
    }

    @Override
    public FishtasticPayload.PayloadType<AmbientContextRequestPacket> type() {
        return TYPE;
    }

    /** Handles the packet on the server side (main thread, via {@code enqueueWork}). */
    public static void handleClientToServer(AmbientContextRequestPacket packet,
                                            FishtasticPacketHandling.IPacketContext context) {
        context.enqueueWork(() -> {
            if (!(context.getPlayer() instanceof ServerPlayer serverPlayer)) return;
            AmbientContextServer.handle(serverPlayer, packet);
        });
    }
}
